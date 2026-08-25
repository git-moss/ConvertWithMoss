// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emax;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.FileUtils;


/**
 * Creates a sound bank of the E-mu Emax or Emax II. A bank is a dump of the whole memory of the
 * sampler, so all converted multi-samples end up in one file: each of them becomes one of the up to
 * 100 presets and their samples share the sample memory of the machine - 512 KB of companded bytes
 * on the Emax, 2 MB of 16 bit frames on the Emax II.
 * <p>
 * The parameters which the format carries but which are not decoded are taken from a template which
 * is the most frequent setting of the factory sound library, so that a written bank plays with the
 * defaults of the sampler instead of with zeroes. See documentation/design/EMAX1_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class EmaxCreator extends AbstractCreator<EmaxCreatorUI>
{
    /** A loop shorter than this does not survive the companding of the audio. */
    private static final int     MINIMUM_LOOP_LENGTH = 8;

    /**
     * The preset parameters at offset 0x0C of a preset record, which are not decoded. This is the
     * most frequent setting of the factory sound library.
     */
    private static final byte [] PRESET_TEMPLATE     =
    {
        0x41,
        0x00,
        0x00,
        0x63,
        0x00,
        0x6F,
        0x21,
        0x02,
        0x22,
        0x00,
        0x00,
        0x00,
        (byte) 0xE0,
        0x2E,
        0x05,
        0x00,
        0x0B,
        0x7F,
        0x57,
        0x00,
        0x04,
        0x06,
        0x00
    };

    /**
     * A voice record, which is the most frequent setting of the factory sound library. The fields
     * which are decoded - the original key, the sample, the cutoff, the panning and the chorus -
     * are overwritten with the values of the zone.
     */
    private static final byte [] VOICE_TEMPLATE      =
    {
        0x02,
        0x7C,
        0x60,
        (byte) 0xB0,
        0x00,
        0x00,
        0x00,
        0x00,
        0x30,
        0x00,
        (byte) 0xFF,
        0x00,
        0x00,
        0x00,
        0x00,
        0x07,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x7C,
        0x60,
        0x00,
        0x08,
        0x00,
        0x40,
        (byte) 0xE0,
        (byte) 0x96,
        (byte) 0xB7,
        0x3D,
        (byte) 0x96
    };

    private static final int []  ALLOWED_BIT_DEPTHS  =
    {
        16
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EmaxCreator (final INotifier notifier)
    {
        super ("E-mu Emax", "Emax", notifier, new EmaxCreatorUI ("Emax"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.writeBank (destinationFolder, List.of (multisampleSource), multisampleSource.getName ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (!multisampleSources.isEmpty ())
            this.writeBank (destinationFolder, multisampleSources, libraryName);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        if (detectSettings.reduceBitDepth <= 0 || detectSettings.reduceBitDepth == 16)
            return true;
        this.notifier.log ("IDS_PROCESSING_REDUCE_BIT_DEPTH_NOT_SUPPORTED", Integer.toString (detectSettings.reduceBitDepth), "16");
        return false;
    }


    /**
     * Write all given sources into one bank file.
     *
     * @param destinationFolder Where to create the file
     * @param multisampleSources The sources to convert
     * @param name The name of the bank
     * @throws IOException Could not write the bank
     */
    private void writeBank (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String name) throws IOException
    {
        final EmaxModel model = this.settingsConfiguration.getTargetModel ();
        final File outputFile = this.createUniqueFilename (destinationFolder, FileUtils.createSafeFilename (name), model.getFileEnding ());
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());

        final Optional<byte []> bank = this.createBankData (multisampleSources, model);
        if (bank.isEmpty ())
            return;

        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (outputFile.toPath ())))
        {
            // Every EM1 file starts with this fixed string, which is what the tools of the Emax
            // community expect in front of the bank; an EB2 file is the bare bank
            if (model == EmaxModel.EMAX)
                out.write (EmaxConstants.SIGNATURE);
            out.write (bank.get ());
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the memory image of one bank which holds all given sources.
     *
     * @param multisampleSources The sources to convert
     * @param model The sampler to write the bank for
     * @return The bank or empty if none of the sources could be converted
     * @throws IOException Could not convert the sample data
     */
    private Optional<byte []> createBankData (final List<IMultisampleSource> multisampleSources, final EmaxModel model) throws IOException
    {
        final BankBuilder builder = new BankBuilder (model);
        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (builder.presets.size () >= EmaxConstants.NUM_PRESET_SLOTS)
            {
                this.notifier.logError ("IDS_EMAX_TOO_MANY_PRESETS", Integer.toString (EmaxConstants.NUM_PRESET_SLOTS), multisampleSource.getName ());
                break;
            }
            builder.mark ();
            final Preset preset = this.createPresetData (multisampleSource, builder);
            if (preset == null)
            {
                builder.rollback ();
                continue;
            }
            if (EmaxConstants.PRESET_HEAP + builder.usedParameterMemory + preset.getSize () + builder.samples.size () * EmaxConstants.SAMPLE_ENTRY_SIZE > EmaxConstants.PARAMETER_SIZE)
            {
                // The samples of the preset are dropped as well, otherwise they would take the
                // memory of the sample directory away from the presets which did fit
                builder.rollback ();
                this.notifier.logError ("IDS_EMAX_PARAMETER_MEMORY_FULL", multisampleSource.getName ());
                break;
            }
            builder.usedParameterMemory += preset.getSize ();
            builder.presets.add (preset);
        }

        if (builder.presets.isEmpty ())
            return Optional.empty ();
        return Optional.of (writeBankData (builder));
    }


    /**
     * Convert one multi-sample source into a preset.
     *
     * @param multisampleSource The source to convert
     * @param builder The bank which is being built
     * @return The preset or null if none of its zones could be converted
     * @throws IOException Could not convert the sample data
     */
    private Preset createPresetData (final IMultisampleSource multisampleSource, final BankBuilder builder) throws IOException
    {
        this.checkTransposition (multisampleSource);

        // Collect the zones which each key plays; the first one becomes the primary voice of its
        // key area and the second one the secondary voice, which is the Dual Voice of the sampler
        final List<List<ISampleZone>> zonesByKey = new ArrayList<> (EmaxConstants.NUM_KEYS);
        for (int key = 0; key < EmaxConstants.NUM_KEYS; key++)
            zonesByKey.add (new ArrayList<> ());
        int dropped = 0;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int lowKey = Math.max (0, zone.getKeyLow () - EmaxConstants.KEY_OFFSET);
                final int highKey = Math.min (EmaxConstants.NUM_KEYS - 1, zone.getKeyHigh () - EmaxConstants.KEY_OFFSET);
                for (int key = lowKey; key <= highKey; key++)
                    if (zonesByKey.get (key).size () < 2)
                        zonesByKey.get (key).add (zone);
                    else
                        dropped++;
            }
        if (dropped > 0)
            this.notifier.logError ("IDS_EMAX_TOO_MANY_LAYERS", multisampleSource.getName ());

        final Preset preset = new Preset ();
        preset.name = multisampleSource.getName ();
        final Map<ISampleZone, Integer> voiceIndices = new HashMap<> ();
        ISampleZone lastPrimary = null;
        ISampleZone lastSecondary = null;
        for (int key = 0; key < EmaxConstants.NUM_KEYS; key++)
        {
            final List<ISampleZone> zones = zonesByKey.get (key);
            final ISampleZone primary = zones.isEmpty () ? null : zones.get (0);
            final ISampleZone secondary = zones.size () > 1 ? zones.get (1) : null;
            if (primary == null)
            {
                preset.keyMap[key] = EmaxConstants.KEY_UNMAPPED;
                lastPrimary = null;
                lastSecondary = null;
                continue;
            }

            if (primary != lastPrimary || secondary != lastSecondary)
            {
                final int primaryVoice = this.getVoice (primary, preset, voiceIndices, builder);
                if (primaryVoice < 0)
                {
                    preset.keyMap[key] = EmaxConstants.KEY_UNMAPPED;
                    lastPrimary = null;
                    lastSecondary = null;
                    continue;
                }
                int secondaryVoice = EmaxConstants.VOICE_NONE;
                if (secondary != null)
                {
                    final int voice = this.getVoice (secondary, preset, voiceIndices, builder);
                    if (voice >= 0)
                        secondaryVoice = voice;
                }
                preset.keyAreas.add (new int []
                {
                    primaryVoice,
                    secondaryVoice
                });
                lastPrimary = primary;
                lastSecondary = secondary;
            }
            preset.keyMap[key] = preset.keyAreas.size () - 1;
        }

        return preset.keyAreas.isEmpty () ? null : preset;
    }


    /**
     * Get the voice of a zone, creating it and its sample when the zone is used for the first time.
     *
     * @param zone The zone
     * @param preset The preset which is being built
     * @param voiceIndices The voices which were already created for this preset
     * @param builder The bank which is being built
     * @return The index of the voice or -1 if the zone holds no audio which still fits
     * @throws IOException Could not convert the sample data
     */
    private int getVoice (final ISampleZone zone, final Preset preset, final Map<ISampleZone, Integer> voiceIndices, final BankBuilder builder) throws IOException
    {
        final Integer existing = voiceIndices.get (zone);
        if (existing != null)
            return existing.intValue ();

        final int sampleIndex = this.addSample (zone, builder);
        if (sampleIndex < 0)
            return -1;

        final Voice voice = new Voice ();
        fillVoice (voice.voiceRecord, zone, sampleIndex);

        final int index = preset.voices.size ();
        preset.voices.add (voice);
        voiceIndices.put (zone, Integer.valueOf (index));
        return index;
    }


    /**
     * Fill the 32 bytes of a voice record from a zone. The parameters which are not decoded keep
     * the value of the template.
     *
     * @param voiceRecord The voice record to fill
     * @param zone The zone
     * @param sampleIndex The number of the sample which the voice plays
     */
    private static void fillVoice (final byte [] voiceRecord, final ISampleZone zone, final int sampleIndex)
    {
        voiceRecord[EmaxConstants.VOICE_ORIGINAL_KEY] = (byte) Math.clamp (zone.getKeyRoot () - (long) EmaxConstants.KEY_OFFSET, 0, EmaxConstants.NUM_KEYS - 1);
        voiceRecord[EmaxConstants.VOICE_SAMPLE] = (byte) sampleIndex;

        // The panning nibble runs the other way round than the model: 1 is fully right and 15 is
        // fully left
        final int panning = EmaxConstants.PANNING_CENTER - (int) Math.round (zone.getPanning () * (EmaxConstants.PANNING_CENTER - 1));
        EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_PANNING_BITS, 4, Math.clamp (panning, 1, 15));

        EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_ATTENUATION, 5, Math.clamp ((int) Math.round (-zone.getGain () / EmaxConstants.ATTENUATION_DB_PER_STEP), 0, 31));
        EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_TUNE, 5, Math.clamp ((int) Math.round (zone.getTuning () * 100.0 / EmaxConstants.TUNE_CENTS_PER_STEP), -16, 15) & 0x1F);
        EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_NON_TRANSPOSE, 1, zone.getKeyTracking () < 0.5 ? 1 : 0);

        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        writeEnvelope (voiceRecord, EmaxConstants.VOICE_AMP_ATTACK, amplitudeEnvelope);
        if (amplitudeEnvelope.getDelayTime () >= 0)
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_DELAY, 6, Math.clamp (EmaxConstants.getVoiceDelayValue (amplitudeEnvelope.getDelayTime ()), 0, 63));
        final double velocityDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
        if (velocityDepth > 0)
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_VELOCITY_TO_LEVEL, 4, EmaxConstants.getVelocityToLevelValue (velocityDepth * ILfoModulator.MAX_VOLUME_DEPTH));

        writeLfo (voiceRecord, zone);
        writeFilter (voiceRecord, zone);
    }


    /**
     * Write the five stages of an envelope into the bit stream of a voice record.
     *
     * @param voiceRecord The voice record
     * @param offset The offset of the attack stage in the bit stream
     * @param envelope The envelope
     */
    private static void writeEnvelope (final byte [] voiceRecord, final int offset, final IEnvelope envelope)
    {
        writeStage (voiceRecord, offset, EnvelopeStage.ATTACK, envelope.getAttackTime ());
        writeStage (voiceRecord, offset + 5, EnvelopeStage.HOLD, envelope.getHoldTime ());
        writeStage (voiceRecord, offset + 10, EnvelopeStage.DECAY, envelope.getDecayTime ());
        if (envelope.getSustainLevel () >= 0)
            EmaxConstants.writeVoiceField (voiceRecord, offset + 15, 5, EmaxConstants.getEnvelopeSustainValue (envelope.getSustainLevel ()));
        writeStage (voiceRecord, offset + 20, EnvelopeStage.DECAY, envelope.getReleaseTime ());
    }


    /**
     * Write one stage of an envelope, leaving the template value in place when the source does not
     * set the stage.
     *
     * @param voiceRecord The voice record
     * @param offset The offset of the stage in the bit stream
     * @param stage The envelope stage
     * @param time The time in seconds, negative if the source does not set it
     */
    private static void writeStage (final byte [] voiceRecord, final int offset, final EnvelopeStage stage, final double time)
    {
        if (time >= 0)
            EmaxConstants.writeVoiceField (voiceRecord, offset, 5, stage.toValue (time));
    }


    /** The three time tables which an envelope stage can use. */
    private enum EnvelopeStage
    {
        ATTACK,
        HOLD,
        DECAY;


        /**
         * Get the value of this stage which comes closest to a time.
         *
         * @param seconds The time in seconds
         * @return The value, 0 to 31
         */
        int toValue (final double seconds)
        {
            return switch (this)
            {
                case ATTACK -> EmaxConstants.getEnvelopeAttackValue (seconds);
                case HOLD -> EmaxConstants.getEnvelopeHoldValue (seconds);
                default -> EmaxConstants.getEnvelopeDecayValue (seconds);
            };
        }
    }


    /**
     * Write the LFO of a voice. The sampler has one LFO which it routes to the pitch and the level
     * at the same time, so the rate and the delay of whichever the source carries are used.
     *
     * @param voiceRecord The voice record
     * @param zone The zone
     */
    private static void writeLfo (final byte [] voiceRecord, final ISampleZone zone)
    {
        final ILfoModulator pitchLfo = zone.getPitchLfoModulator ();
        final ILfoModulator amplitudeLfo = zone.getAmplitudeLfoModulator ();
        final ILfoModulator source = pitchLfo.getDepth () > 0 ? pitchLfo : amplitudeLfo;
        if (source.getDepth () <= 0)
            return;

        if (source.getSource ().getRate () >= 0)
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_LFO_RATE, 7, EmaxConstants.getLfoRateValue (source.getSource ().getRate ()));
        if (source.getSource ().getDelay () >= 0)
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_LFO_DELAY, 6, Math.clamp (EmaxConstants.getLfoDelayValue (source.getSource ().getDelay ()), 0, 63));
        if (pitchLfo.getDepth () > 0)
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_LFO_TO_PITCH, 4, Math.clamp ((int) Math.round (pitchLfo.getDepth () * IEnvelope.MAX_ENVELOPE_DEPTH / EmaxConstants.LFO_PITCH_CENTS_PER_STEP), 0, 15));
        if (amplitudeLfo.getDepth () > 0)
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_LFO_TO_VOLUME, 4, Math.clamp ((int) Math.round (amplitudeLfo.getDepth () * ILfoModulator.MAX_VOLUME_DEPTH / EmaxConstants.LFO_VOLUME_DB_PER_STEP), 0, 15));
    }


    /**
     * Write the low pass filter of a voice with its envelope, its keyboard tracking and its
     * velocity modulation.
     *
     * @param voiceRecord The voice record
     * @param zone The zone
     */
    private static void writeFilter (final byte [] voiceRecord, final ISampleZone zone)
    {
        final IFilter filter = zone.getFilter ().orElse (null);
        if (filter == null)
        {
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_CUTOFF_BITS, 7, EmaxConstants.FILTER_CUTOFF_MAX);
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_RESONANCE, 7, 0);
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_ENV_AMOUNT, 7, 0);
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_TRACKING, 4, 0);
            return;
        }

        EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_CUTOFF_BITS, 7, EmaxConstants.getCutoffValue (filter.getCutoff ()));
        EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_RESONANCE, 7, EmaxConstants.getResonanceValue (filter.getResonance () * IFilter.MAX_RESONANCE));
        EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_TRACKING, 4, Math.clamp ((int) Math.round (filter.getCutoffKeyTracking () * 100.0 / EmaxConstants.FILTER_TRACKING_PER_STEP), 0, 15));

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final int amount = (int) Math.round (cutoffModulator.getDepth () * IEnvelope.MAX_ENVELOPE_DEPTH / EmaxConstants.FILTER_ENV_CENTS_PER_STEP);
        EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_ENV_AMOUNT, 7, Math.clamp (amount, -50, 50) & 0x7F);
        if (amount != 0)
            writeEnvelope (voiceRecord, EmaxConstants.VOICE_FILTER_ATTACK, cutoffModulator.getSource ());

        final double velocityDepth = filter.getCutoffVelocityModulator ().getDepth ();
        if (velocityDepth > 0)
            EmaxConstants.writeVoiceField (voiceRecord, EmaxConstants.VOICE_VELOCITY_TO_CUTOFF, 4, Math.clamp ((int) Math.round (velocityDepth * 15.0), 0, 15));
    }


    /**
     * Convert the audio of a zone and add it to the samples of the bank, re-using a sample with
     * identical content which was already added.
     *
     * @param zone The zone
     * @param builder The bank which is being built
     * @return The index of the sample or -1 if the zone holds no audio which still fits
     * @throws IOException Could not convert the sample data
     */
    private int addSample (final ISampleZone zone, final BankBuilder builder) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA", zone.getName ());
            return -1;
        }

        final int sourceRate = sampleData.get ().getAudioMetadata ().getSampleRate ();
        final int sampleRate = this.getSampleRate (sourceRate);
        final DestinationAudioFormat destinationFormat = new DestinationAudioFormat (ALLOWED_BIT_DEPTHS, sampleRate, true);
        this.logResampling (zone, destinationFormat);
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), destinationFormat);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        final byte [] wavData = waveFile.getDataChunk ().getData ();
        final int numFrames = wavData.length / (2 * numChannels);
        if (numFrames <= 0)
            return -1;

        final Sample sample = new Sample ();
        sample.sampleRate = sampleRate;
        sample.numFrames = numFrames;
        sample.audio = encode (wavData, numFrames, numChannels, builder.model);

        // The audio was re-sampled to the rate of the sampler, so the loop moves with it
        final double rateRatio = sampleRate / (double) sourceRate;
        for (final ISampleLoop loop: zone.getLoops ())
            if (loop.getType () == LoopType.FORWARDS || loop.getType () == LoopType.ALTERNATING)
            {
                final int loopEnd = Math.clamp ((int) Math.round (loop.getEnd () * rateRatio), 0, numFrames);
                final int loopStart = Math.clamp ((int) Math.round (loop.getStart () * rateRatio), 0, Math.max (0, loopEnd - MINIMUM_LOOP_LENGTH));
                if (loopEnd - loopStart >= MINIMUM_LOOP_LENGTH)
                {
                    sample.hasLoop = true;
                    sample.loopStart = loopStart;
                    sample.loopEnd = loopEnd;
                }
                break;
            }

        // Re-use a sample with identical content, e.g. when the same audio is mapped to several key
        // ranges or is shared by the layers of a preset
        final Object contentKey = List.of (ByteBuffer.wrap (sample.audio), Integer.valueOf (sampleRate), Boolean.valueOf (sample.hasLoop), Integer.valueOf (sample.loopStart), Integer.valueOf (sample.loopEnd));
        final Integer existingIndex = builder.sampleIndicesByContent.get (contentKey);
        if (existingIndex != null)
            return existingIndex.intValue ();

        if (builder.usedSampleMemory + sample.numFrames > builder.model.getMemoryFrames ())
        {
            this.notifier.logError ("IDS_EMAX_SAMPLE_MEMORY_FULL", zone.getName ());
            return -1;
        }

        sample.start = builder.usedSampleMemory;
        builder.usedSampleMemory += sample.numFrames;
        final int index = builder.samples.size ();
        builder.samples.add (sample);
        builder.sampleIndicesByContent.put (contentKey, Integer.valueOf (index));
        return index;
    }


    /**
     * Get the sample rate at which the audio of a source is stored. The Emax cannot transpose a
     * sample far upwards and how far depends on its rate, so the automatic setting picks the
     * highest rate which still covers the transposition which the zones of the source need.
     *
     * @param sourceRate The source rate
     * @return The sample rate in Hertz
     */
    private int getSampleRate (final int sourceRate)
    {
        final int configured = this.settingsConfiguration.getSampleRateIndex ();
        if (configured >= 0)
            return EmaxConstants.SAMPLE_RATES[configured];
        // Never up-sample: that costs memory of the bank without adding anything to the audio
        return EmaxConstants.SAMPLE_RATES[EmaxConstants.getSampleRateIndex (sourceRate)];
    }


    /**
     * Report a source whose zones need to be transposed further upwards than the sampler manages at
     * the rate its audio is stored with. The rate is not lowered for this: the factory banks map
     * their top sample up to the end of the 88 key map and would all end up at 10 kHz, so this is
     * left to the sample rate setting.
     *
     * @param multisampleSource The source to check
     * @throws IOException Could not read the sample data
     */
    private void checkTransposition (final IMultisampleSource multisampleSource) throws IOException
    {
        int neededTransposition = 0;
        int limit = Integer.MAX_VALUE;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                neededTransposition = Math.max (neededTransposition, zone.getKeyHigh () - zone.getKeyRoot ());
                final Optional<ISampleData> sampleData = zone.getSampleData ();
                if (sampleData.isPresent ())
                    limit = Math.min (limit, EmaxConstants.MAX_UPWARD_TRANSPOSE[EmaxConstants.getSampleRateIndex (this.getSampleRate (sampleData.get ().getAudioMetadata ().getSampleRate ()))]);
            }
        if (limit != Integer.MAX_VALUE && limit < neededTransposition)
            this.notifier.log ("IDS_EMAX_TRANSPOSITION_TOO_LARGE", multisampleSource.getName (), Integer.toString (neededTransposition), Integer.toString (limit));
    }


    /**
     * Mix the audio down to one channel and encode it the way the sampler stores it: the Emax
     * compands each frame into one byte, the Emax II keeps the 16 bit frame.
     *
     * @param wavData The 16 bit audio
     * @param numFrames The number of frames
     * @param numChannels The number of channels of the audio
     * @param model The sampler to write the audio for
     * @return The encoded audio
     */
    private static byte [] encode (final byte [] wavData, final int numFrames, final int numChannels, final EmaxModel model)
    {
        final byte [] audio = new byte [numFrames * model.getBytesPerFrame ()];
        for (int frame = 0; frame < numFrames; frame++)
        {
            int sum = 0;
            for (int channel = 0; channel < numChannels; channel++)
            {
                final int offset = (frame * numChannels + channel) * 2;
                sum += (short) (wavData[offset] & 0xFF | (wavData[offset + 1] & 0xFF) << 8);
            }
            final int value = sum / numChannels;
            if (model == EmaxModel.EMAX_2)
            {
                audio[frame * 2] = (byte) (value & 0xFF);
                audio[frame * 2 + 1] = (byte) (value >> 8 & 0xFF);
            }
            else
                audio[frame] = (byte) EmaxConstants.compand (value);
        }
        return audio;
    }


    /**
     * Write the memory image of the bank.
     *
     * @param builder The bank which was built
     * @return The bank
     */
    private static byte [] writeBankData (final BankBuilder builder)
    {
        final EmaxModel model = builder.model;
        // The Emax always writes its whole sample memory, while a bank of the Emax II holds only
        // the audio which is in use - which is what EMXP calls an EB2 file
        final int audioBytes = model == EmaxModel.EMAX ? model.getMemoryFrames () : builder.usedSampleMemory * model.getBytesPerFrame ();
        final byte [] bank = new byte [EmaxConstants.PARAMETER_SIZE + audioBytes];

        // The samples, which are stored from the bottom of the sample memory upwards, and their
        // directory, which grows downwards from the end of the parameter memory
        for (int index = 0; index < builder.samples.size (); index++)
        {
            final Sample sample = builder.samples.get (index);
            System.arraycopy (sample.audio, 0, bank, EmaxConstants.PARAMETER_SIZE + sample.start * model.getBytesPerFrame (), sample.audio.length);

            final int entry = EmaxConstants.PARAMETER_SIZE - EmaxConstants.SAMPLE_ENTRY_SIZE * (index + 1);
            final int end = sample.start + sample.numFrames;
            final int loopStart = sample.hasLoop ? sample.start + sample.loopStart : sample.start + 2;
            final int loopEnd = sample.hasLoop ? sample.start + sample.loopEnd : Math.max (loopStart + 1, end - 2);
            writeInt32 (bank, entry + EmaxConstants.SAMPLE_START, sample.start);
            writeInt32 (bank, entry + EmaxConstants.SAMPLE_END, end);
            writeInt32 (bank, entry + EmaxConstants.SAMPLE_LOOP_START, loopStart);
            writeInt32 (bank, entry + EmaxConstants.SAMPLE_LOOP_END, loopEnd);
            writeInt32 (bank, entry + EmaxConstants.SAMPLE_RELEASE_LOOP_START, loopStart);
            writeInt32 (bank, entry + EmaxConstants.SAMPLE_RELEASE_LOOP_END, loopEnd);
            bank[entry + EmaxConstants.SAMPLE_FLAGS] = (byte) (sample.hasLoop ? EmaxConstants.SAMPLE_FLAG_LOOP | EmaxConstants.SAMPLE_FLAG_LOOP_RELEASE : 0);
            bank[entry + EmaxConstants.SAMPLE_RATE_INDEX] = (byte) EmaxConstants.getSampleRateIndex (sample.sampleRate);
        }

        // The presets, which are allocated from the start of the preset heap upwards
        int address = EmaxConstants.PRESET_HEAP;
        for (int index = 0; index < builder.presets.size (); index++)
        {
            final Preset preset = builder.presets.get (index);
            writeInt16 (bank, index * 2, EmaxConstants.CPU_BASE + address);
            writePreset (bank, address, preset);
            address += preset.getSize ();
        }
        // Unused preset slots point behind the last record and each one is one higher than the one
        // before, which is how the sampler keeps them apart
        for (int index = builder.presets.size (); index < EmaxConstants.NUM_PRESET_SLOTS; index++)
            writeInt16 (bank, index * 2, EmaxConstants.CPU_BASE + address + index - builder.presets.size ());

        writeInt16 (bank, EmaxConstants.HEAP_POINTER, EmaxConstants.CPU_BASE + address);
        writeInt32 (bank, EmaxConstants.BANK_UNKNOWN, 1);
        writeInt32 (bank, EmaxConstants.SELECTED_PRESET, 0);
        // An unused sequence slot holds the number of frames of the sample memory, which is what
        // tells a reader which of the two samplers the bank belongs to
        for (int index = 0; index < EmaxConstants.NUM_SEQUENCE_SLOTS; index++)
            writeInt32 (bank, EmaxConstants.SEQUENCE_TABLE + index * 4, model.getMemoryFrames ());
        writeInt32 (bank, EmaxConstants.SAMPLE_DIRECTORY_BOTTOM, EmaxConstants.CPU_END - builder.samples.size () * EmaxConstants.SAMPLE_ENTRY_SIZE);
        writeInt32 (bank, EmaxConstants.SAMPLE_DIRECTORY_TOP, EmaxConstants.CPU_END - EmaxConstants.SAMPLE_ENTRY_SIZE);
        writeInt32 (bank, EmaxConstants.SAMPLE_MEMORY_USED, builder.usedSampleMemory);
        return bank;
    }


    /**
     * Write one preset record.
     *
     * @param bank The bank to write into
     * @param address The position of the record
     * @param preset The preset
     */
    private static void writePreset (final byte [] bank, final int address, final Preset preset)
    {
        final byte [] name = pad (preset.name, EmaxConstants.PRESET_NAME_LENGTH);
        System.arraycopy (name, 0, bank, address, EmaxConstants.PRESET_NAME_LENGTH);
        System.arraycopy (PRESET_TEMPLATE, 0, bank, address + EmaxConstants.PRESET_NAME_LENGTH, PRESET_TEMPLATE.length);
        bank[address + EmaxConstants.PRESET_KEY_AREA_COUNT] = (byte) preset.keyAreas.size ();
        for (int key = 0; key < EmaxConstants.NUM_KEYS; key++)
            bank[address + EmaxConstants.PRESET_KEY_MAP + key] = (byte) preset.keyMap[key];

        for (int keyArea = 0; keyArea < preset.keyAreas.size (); keyArea++)
        {
            final int [] voices = preset.keyAreas.get (keyArea);
            final int entry = address + EmaxConstants.PRESET_VOICE_TABLE + keyArea * EmaxConstants.VOICE_TABLE_ENTRY_SIZE;
            bank[entry + EmaxConstants.VOICE_TABLE_MODE] = (byte) (voices[1] == EmaxConstants.VOICE_NONE ? 0 : EmaxConstants.VOICE_TABLE_MODE_DUAL);
            bank[entry + EmaxConstants.VOICE_TABLE_PRIMARY] = (byte) voices[0];
            bank[entry + EmaxConstants.VOICE_TABLE_SECONDARY] = (byte) voices[1];
        }

        final int voiceBase = address + EmaxConstants.PRESET_VOICE_TABLE + preset.keyAreas.size () * EmaxConstants.VOICE_TABLE_ENTRY_SIZE;
        for (int index = 0; index < preset.voices.size (); index++)
        {
            final Voice voice = preset.voices.get (index);
            System.arraycopy (voice.voiceRecord, 0, bank, voiceBase + index * EmaxConstants.VOICE_SIZE, EmaxConstants.VOICE_SIZE);
        }
    }


    /**
     * Convert a name into the fixed length ASCII field of the format.
     *
     * @param name The name
     * @param length The length of the field
     * @return The padded name
     */
    private static byte [] pad (final String name, final int length)
    {
        final byte [] field = new byte [length];
        final byte [] ascii = name.replaceAll ("[^\\x20-\\x7E]", " ").getBytes (StandardCharsets.US_ASCII);
        for (int i = 0; i < length; i++)
            field[i] = i < ascii.length ? ascii[i] : (byte) ' ';
        return field;
    }


    /**
     * Write a 16 bit little-endian value.
     *
     * @param data The data to write into
     * @param offset The position of the value
     * @param value The value
     */
    private static void writeInt16 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    /**
     * Write a 32 bit little-endian value.
     *
     * @param data The data to write into
     * @param offset The position of the value
     * @param value The value
     */
    private static void writeInt32 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
        data[offset + 2] = (byte) (value >> 16 & 0xFF);
        data[offset + 3] = (byte) (value >> 24 & 0xFF);
    }


    /** Everything which is collected while a bank is built. */
    private static class BankBuilder
    {
        final EmaxModel            model;
        final List<Sample>         samples                = new ArrayList<> ();
        final Map<Object, Integer> sampleIndicesByContent = new HashMap<> ();
        final List<Preset>         presets                = new ArrayList<> ();
        int                        usedSampleMemory       = 0;
        int                        usedParameterMemory    = 0;
        private int                markedSamples          = 0;
        private int                markedSampleMemory     = 0;


        BankBuilder (final EmaxModel model)
        {
            this.model = model;
        }


        /** Remember the state before a preset is built. */
        void mark ()
        {
            this.markedSamples = this.samples.size ();
            this.markedSampleMemory = this.usedSampleMemory;
        }


        /** Drop the samples which were added since {@link #mark()}. */
        void rollback ()
        {
            while (this.samples.size () > this.markedSamples)
                this.samples.remove (this.samples.size () - 1);
            this.usedSampleMemory = this.markedSampleMemory;
            this.sampleIndicesByContent.values ().removeIf (index -> index.intValue () >= this.markedSamples);
        }
    }


    /** One sample to be written into the bank. */
    private static class Sample
    {
        byte [] audio;
        int     numFrames;
        int     start;
        int     sampleRate;
        boolean hasLoop;
        int     loopStart;
        int     loopEnd;
    }


    /** One preset to be written into the bank. */
    private static class Preset
    {
        String             name;
        final int []       keyMap   = new int [EmaxConstants.NUM_KEYS];
        final List<int []> keyAreas = new ArrayList<> ();
        final List<Voice>  voices   = new ArrayList<> ();


        /**
         * Get the size of the record of this preset.
         *
         * @return The size in bytes
         */
        int getSize ()
        {
            return EmaxConstants.PRESET_VOICE_TABLE + this.keyAreas.size () * EmaxConstants.VOICE_TABLE_ENTRY_SIZE + this.voices.size () * EmaxConstants.VOICE_SIZE;
        }
    }


    /** One voice of a preset. */
    private static class Voice
    {
        final byte [] voiceRecord = VOICE_TEMPLATE.clone ();
    }
}
