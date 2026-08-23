// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.directwave;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for DirectWave programs. A program is always written as a monolithic DWP file, which
 * carries all of its samples as FLAC compressed audio and is therefore self-contained. The file is
 * created from the block structure of a real monolithic DirectWave export, see
 * DIRECTWAVE_DWP_FORMAT.md in the design documentation.
 *
 * @author Jürgen Moßgraber
 */
public class DirectWaveCreator extends AbstractCreator<EmptySettingsUI>
{
    /** FLAC supports at maximum a resolution of 24 bit. */
    private static final DestinationAudioFormat    EMBEDDED_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        8,
        16,
        24
    }, -1, false);

    private static final Map<LfoWaveform, Integer> LFO_WAVEFORMS         = new EnumMap<> (LfoWaveform.class);
    static
    {
        LFO_WAVEFORMS.put (LfoWaveform.SINE, Integer.valueOf (DirectWaveTag.LFO_WAVEFORM_SINE));
        LFO_WAVEFORMS.put (LfoWaveform.TRIANGLE, Integer.valueOf (DirectWaveTag.LFO_WAVEFORM_TRIANGLE));
        LFO_WAVEFORMS.put (LfoWaveform.SQUARE, Integer.valueOf (DirectWaveTag.LFO_WAVEFORM_SQUARE));
        LFO_WAVEFORMS.put (LfoWaveform.SAWTOOTH_UP, Integer.valueOf (DirectWaveTag.LFO_WAVEFORM_SAW));
        LFO_WAVEFORMS.put (LfoWaveform.SAWTOOTH_DOWN, Integer.valueOf (DirectWaveTag.LFO_WAVEFORM_INV_SAW));
        LFO_WAVEFORMS.put (LfoWaveform.RANDOM, Integer.valueOf (DirectWaveTag.LFO_WAVEFORM_RANDOM));
    }

    private static final Map<FilterType, Integer> FILTER_TYPES = new EnumMap<> (FilterType.class);
    static
    {
        FILTER_TYPES.put (FilterType.LOW_PASS, Integer.valueOf (DirectWaveTag.FILTER_TYPE_LOW_PASS));
        FILTER_TYPES.put (FilterType.HIGH_PASS, Integer.valueOf (DirectWaveTag.FILTER_TYPE_HIGH_PASS));
        FILTER_TYPES.put (FilterType.BAND_PASS, Integer.valueOf (DirectWaveTag.FILTER_TYPE_BAND_PASS));
        FILTER_TYPES.put (FilterType.BAND_REJECTION, Integer.valueOf (DirectWaveTag.FILTER_TYPE_NOTCH));
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DirectWaveCreator (final INotifier notifier)
    {
        super ("FL Studio DirectWave", "DirectWave", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final File multiFile = this.createUniqueFilename (destinationFolder, FileUtils.createSafeFilename (multisampleSource.getName ()), "dwp");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.filterRoundRobinZones (multisampleSource);
        final String name = FileUtils.getNameWithoutType (multiFile);
        renameZonesToConvention (name, multisampleSource);

        Files.write (multiFile.toPath (), createDwpContent (name, multisampleSource));

        this.progress.notifyDone ();
    }


    /**
     * Create the content of the DWP file.
     *
     * @param name The name of the instrument
     * @param multisampleSource The multi-sample source
     * @return The bytes of the DWP file
     * @throws IOException Could not create the content
     */
    private static byte [] createDwpContent (final String name, final IMultisampleSource multisampleSource) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_INSTRUMENT_NAME, name);
        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_INSTRUMENT_PATH, "D:\\" + name + ".dwp");
        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_SHADOW_NAME, DirectWaveTag.SHADOW_NAME_LENGTH);
        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_SHADOW_PATH, DirectWaveTag.TEMPLATE_PROGRAM_1);
        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_METADATA_1, DirectWaveTag.TEMPLATE_PROGRAM_2);
        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_METADATA_2, DirectWaveTag.TEMPLATE_PROGRAM_3);
        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_METADATA_3, DirectWaveTag.TEMPLATE_PROGRAM_4);
        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_METADATA_3, DirectWaveTag.TEMPLATE_PROGRAM_4);
        for (int i = 0; i < 4; i++)
            DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_METADATA_4, 4);

        for (int i = 0; i < DirectWaveTag.NUM_PARAMETER_SLOTS; i++)
        {
            final byte [] slot = new byte [13];
            DirectWaveChunk.writeIntLE (slot, 0, i);
            DirectWaveChunk.writeFloatLE (slot, 5, 1.0f);
            DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_PARAMETER_SLOT, slot);
        }

        int sampleCount = 0;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_SAMPLE_CONTAINER, createSampleContainer (name, zone));
                sampleCount++;
            }

        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_TERMINATOR, 0);

        // Assemble the file and patch the size and sample count fields in the preamble
        final byte [] blockStream = out.toByteArray ();
        final byte [] content = new byte [DirectWaveTag.TEMPLATE_PREAMBLE.length + blockStream.length];
        System.arraycopy (DirectWaveTag.TEMPLATE_PREAMBLE, 0, content, 0, DirectWaveTag.TEMPLATE_PREAMBLE.length);
        System.arraycopy (blockStream, 0, content, DirectWaveTag.TEMPLATE_PREAMBLE.length, blockStream.length);
        DirectWaveChunk.writeIntLE (content, DirectWaveTag.PREAMBLE_SIZE_OFFSET, content.length - DirectWaveTag.PREAMBLE_SIZE_DELTA);
        DirectWaveChunk.writeIntLE (content, DirectWaveTag.PREAMBLE_COUNT_OFFSET, sampleCount);
        return content;
    }


    /**
     * Create the content of one sample container block. All blocks are copied from the container
     * template of a factory program; only the zone mapping, the name, the path and the audio format
     * (frame count, channels, sample rate and loop) are filled from the zone.
     *
     * @param instrumentName The name of the instrument
     * @param zone The sample zone
     * @return The bytes of the container
     * @throws IOException Could not create the content
     */
    private static byte [] createSampleContainer (final String instrumentName, final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException ("Empty sample data in zone: " + zone.getName ());
        final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
        final String sampleName = FileUtils.createSafeFilename (zone.getName ());
        // Round the length up to a full audio block, see EMBEDDED_AUDIO_BLOCK
        final int blockSize = DirectWaveTag.EMBEDDED_AUDIO_BLOCK;
        final int frames = (audioMetadata.getNumberOfSamples () + blockSize - 1) / blockSize * blockSize;

        // Zone LFO 1 modulates the pitch, Zone LFO 2 the volume; both are routed in the modulation
        // matrix, which is why an LFO block is only written together with its matrix slot
        final ILfo [] lfoSources = new ILfo [2];
        final List<byte []> modulations = new ArrayList<> ();
        final ILfoModulator pitchModulator = zone.getPitchLfoModulator ();
        if (isModulating (pitchModulator))
        {
            lfoSources[0] = pitchModulator.getSource ();
            // The depth of the model is relative to MAX_ENVELOPE_DEPTH cent, DirectWave reaches
            // PITCH_MODULATION_RANGE cent at its full strength
            modulations.add (createModulation (DirectWaveTag.MODULATION_SOURCE_LFO1, DirectWaveTag.MODULATION_TARGET_PITCH, pitchModulator.getDepth () * IEnvelope.MAX_ENVELOPE_DEPTH / DirectWaveTag.PITCH_MODULATION_RANGE));
        }
        final ILfoModulator volumeModulator = zone.getAmplitudeLfoModulator ();
        if (isModulating (volumeModulator))
        {
            lfoSources[1] = volumeModulator.getSource ();
            modulations.add (createModulation (DirectWaveTag.MODULATION_SOURCE_LFO2, DirectWaveTag.MODULATION_TARGET_GAIN, volumeStrength (volumeModulator.getDepth ())));
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        final AtomicBoolean isFirstFilter = new AtomicBoolean (true);
        final int [] lfoIndex = new int [1];
        final Optional<List<DirectWaveChunk>> chunksOpt = DirectWaveChunk.parseAll (DirectWaveTag.TEMPLATE_CONTAINER, 0);
        if (chunksOpt.isEmpty ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_BAD_METADATA_FILE", "DirectWaveTag.TEMPLATE_CONTAINER"));
        for (final DirectWaveChunk chunk: chunksOpt.get ())
            switch (chunk.getTag ())
            {
                case DirectWaveTag.TAG_ZONE_MAPPING:
                    final byte [] mapping = chunk.getPayload ().clone ();
                    mapping[DirectWaveTag.MAPPING_ROOT_KEY] = (byte) getAdjustedRootKey (zone);
                    mapping[DirectWaveTag.MAPPING_LOW_KEY] = (byte) Math.clamp (limitToDefault (zone.getKeyLow (), 0), 0, 127);
                    mapping[DirectWaveTag.MAPPING_HIGH_KEY] = (byte) Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127);
                    final int velocityLow = Math.clamp (limitToDefault (zone.getVelocityLow (), 1), 1, 127);
                    mapping[DirectWaveTag.MAPPING_LOW_VELOCITY] = (byte) (velocityLow <= 1 ? 0 : velocityLow);
                    mapping[DirectWaveTag.MAPPING_HIGH_VELOCITY] = (byte) Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 1, 127);
                    // The gain float is a linear amplitude; values above 0 dB are clamped since
                    // only values up to 1.0 were observed. This mutes the layers which E-mu
                    // presets park at -96 dB.
                    DirectWaveChunk.writeFloatLE (mapping, DirectWaveTag.MAPPING_GAIN, (float) Math.clamp (Math.pow (10, zone.getGain () / 20.0), 0, 1));
                    DirectWaveChunk.writeFloatLE (mapping, DirectWaveTag.MAPPING_PANNING, (float) Math.clamp (zone.getPanning () / 2.0 + 0.5, 0, 1));
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_ZONE_MAPPING, mapping);
                    break;

                case DirectWaveTag.TAG_SAMPLE_NAME:
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_SAMPLE_NAME, sampleName);
                    break;

                case DirectWaveTag.TAG_SAMPLE_PATH:
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_SAMPLE_PATH, "D:\\" + instrumentName + "\\" + sampleName + ".wav");
                    break;

                case DirectWaveTag.TAG_AUDIO_FORMAT:
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_AUDIO_FORMAT, createAudioFormat (chunk.getPayload (), zone, audioMetadata, frames));
                    break;

                case DirectWaveTag.TAG_AMP_ENVELOPE:
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_AMP_ENVELOPE, createAmplitudeEnvelope (chunk.getPayload (), zone));
                    break;

                case DirectWaveTag.TAG_FILTER:
                    // Only the first of the two filter blocks is filled
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_FILTER, isFirstFilter.getAndSet (false) ? createFilter (chunk.getPayload (), zone) : chunk.getPayload ());
                    break;

                case DirectWaveTag.TAG_LFO:
                    final ILfo lfo = lfoIndex[0] < lfoSources.length ? lfoSources[lfoIndex[0]] : null;
                    lfoIndex[0]++;
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_LFO, lfo == null ? chunk.getPayload () : createLfo (chunk.getPayload (), lfo));
                    break;

                case DirectWaveTag.TAG_MODULATION:
                    // Fill the unused slots of the matrix, the used ones are program defaults
                    final byte [] slot = chunk.getPayload ();
                    final boolean isFree = DirectWaveChunk.readIntLE (slot, DirectWaveTag.MODULATION_SOURCE) == 0;
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_MODULATION, isFree && !modulations.isEmpty () ? modulations.remove (0) : slot);
                    break;

                case DirectWaveTag.TAG_SAMPLE_TERMINATOR:
                    // The embedded audio is the last block of the container
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_EMBEDDED_AUDIO, createEmbeddedAudio (sampleData.get (), frames));
                    DirectWaveChunk.writeChunk (out, chunk.getTag (), chunk.getPayload ());
                    break;

                default:
                    DirectWaveChunk.writeChunk (out, chunk.getTag (), chunk.getPayload ());
                    break;
            }
        return out.toByteArray ();
    }


    /**
     * Create the payload of the embedded audio block: the length of the audio data as a 32-bit
     * integer, 4 unused bytes and the audio as a FLAC stream.
     *
     * @param sampleData The sample data to embed
     * @param frames The number of frames of the sample, a multiple of the audio block size; the
     *            audio is padded with silence to that length and put between the guard frames
     * @return The payload
     * @throws IOException Could not compress the audio
     */
    private static byte [] createEmbeddedAudio (final ISampleData sampleData, final int frames) throws IOException
    {
        final byte [] flac = AudioFileUtils.compressToFLAC (addGuard (sampleData, frames));
        final byte [] payload = new byte [DirectWaveTag.EMBEDDED_AUDIO_OFFSET + flac.length];
        DirectWaveChunk.writeIntLE (payload, 0, flac.length);
        System.arraycopy (flac, 0, payload, DirectWaveTag.EMBEDDED_AUDIO_OFFSET, flac.length);
        return payload;
    }


    /**
     * Put the audio into the buffer layout of DirectWave: half of a guard block of silence, the
     * audio extended with silence to the given number of frames, and the other half of the guard
     * block, which is what the plug-in itself writes. DirectWave reads the embedded audio in
     * blocks - a sample which does not fill its last block crashes the plug-in when the program is
     * loaded - and plays the sample and addresses its loop points behind the leading guard, see
     * the design document.
     *
     * @param sampleData The sample data to extend
     * @param frames The number of frames of the sample without the guard
     * @return The extended sample data
     * @throws IOException Could not read the sample data
     */
    private static ISampleData addGuard (final ISampleData sampleData, final int frames) throws IOException
    {
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData, EMBEDDED_AUDIO_FORMAT);
        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final int frameSize = formatChunk.getNumberOfChannels () * (formatChunk.getSignificantBitsPerSample () / 8);
        final byte [] data = waveFile.getDataChunk ().getData ();
        final int guard = DirectWaveTag.EMBEDDED_AUDIO_BLOCK / 2 * frameSize;
        final byte [] padded = new byte [guard + frames * frameSize + guard];
        System.arraycopy (data, 0, padded, guard, Math.min (data.length, frames * frameSize));
        return new WavFileSampleData (new WaveFile (formatChunk, new DataChunk (formatChunk, padded)));
    }


    /**
     * Fill the amplitude envelope block with the envelope of the zone. The four floats are the
     * attack, decay, sustain and release knob positions; the time knobs are mapped with the
     * provisional cubic law described in the design document. Fields which are not set in the
     * source keep the template default.
     *
     * @param template The template payload of the amplitude envelope block
     * @param zone The sample zone
     * @return The filled block payload
     */
    private static byte [] createAmplitudeEnvelope (final byte [] template, final ISampleZone zone)
    {
        final byte [] payload = template.clone ();
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        writeEnvelopeTime (payload, 0, envelope.getAttackTime ());
        writeEnvelopeTime (payload, 4, envelope.getDecayTime ());
        if (envelope.getSustainLevel () >= 0)
            DirectWaveChunk.writeFloatLE (payload, 8, (float) Math.clamp (envelope.getSustainLevel (), 0, 1));
        writeEnvelopeTime (payload, 12, envelope.getReleaseTime ());
        return payload;
    }


    private static void writeEnvelopeTime (final byte [] payload, final int offset, final double time)
    {
        if (time >= 0)
            DirectWaveChunk.writeFloatLE (payload, offset, (float) Math.clamp (Math.pow (time / DirectWaveTag.ENVELOPE_MAX_TIME, 1.0 / DirectWaveTag.ENVELOPE_TIME_EXPONENT), 0, 1));
    }


    /**
     * Fill the filter block from the filter of the zone. The type is a 32-bit integer, the cutoff
     * and the resonance are knob positions.
     *
     * @param template The template payload of the filter block
     * @param zone The sample zone
     * @return The filled block payload
     */
    private static byte [] createFilter (final byte [] template, final ISampleZone zone)
    {
        final Optional<IFilter> filterOpt = zone.getFilter ();
        if (filterOpt.isEmpty ())
            return template;
        final IFilter filter = filterOpt.get ();
        final Integer type = FILTER_TYPES.get (filter.getType ());
        if (type == null)
            return template;

        final byte [] payload = template.clone ();
        DirectWaveChunk.writeIntLE (payload, DirectWaveTag.FILTER_TYPE, type.intValue ());
        DirectWaveChunk.writeFloatLE (payload, DirectWaveTag.FILTER_CUTOFF, frequencyToKnob (filter.getCutoff ()));
        DirectWaveChunk.writeFloatLE (payload, DirectWaveTag.FILTER_RESONANCE, (float) Math.clamp (filter.getResonance () / IFilter.MAX_RESONANCE, 0, 1));
        return payload;
    }


    /**
     * Convert a frequency into a cutoff knob position, see
     * {@link DirectWaveTag#FILTER_MIN_FREQUENCY}.
     *
     * @param frequency The frequency in Hertz
     * @return The knob position (0-1)
     */
    private static float frequencyToKnob (final double frequency)
    {
        final double range = IFilter.MAX_FREQUENCY / DirectWaveTag.FILTER_MIN_FREQUENCY;
        final double position = Math.log (Math.max (DirectWaveTag.FILTER_MIN_FREQUENCY, frequency) / DirectWaveTag.FILTER_MIN_FREQUENCY) / Math.log (range);
        return (float) Math.clamp (position, 0, 1);
    }


    /**
     * Check if a modulator has a depth and a source which is set.
     *
     * @param modulator The modulator to check
     * @return True if it modulates
     */
    private static boolean isModulating (final ILfoModulator modulator)
    {
        return modulator != null && Math.abs (modulator.getDepth ()) > 0.0001 && modulator.getSource () != null && modulator.getSource ().isSet ();
    }


    /**
     * Fill the LFO block with the waveform and the rate of the given LFO. The rate knob follows
     * <code>Hertz = 20 * position^2</code>, see the design document.
     *
     * @param template The template payload of the LFO block
     * @param lfo The LFO
     * @return The filled block payload
     */
    private static byte [] createLfo (final byte [] template, final ILfo lfo)
    {
        final byte [] payload = template.clone ();
        final Integer waveform = LFO_WAVEFORMS.get (lfo.getWaveform ());
        DirectWaveChunk.writeIntLE (payload, DirectWaveTag.LFO_WAVEFORM, waveform == null ? DirectWaveTag.LFO_WAVEFORM_SINE : waveform.intValue ());
        final double rate = lfo.getRate ();
        if (rate >= 0)
            DirectWaveChunk.writeFloatLE (payload, DirectWaveTag.LFO_RATE, (float) Math.clamp (Math.sqrt (rate / DirectWaveTag.LFO_MAX_RATE), 0, 1));
        return payload;
    }


    /**
     * Create the payload of one modulation matrix slot. The amount is bipolar around
     * {@link DirectWaveTag#MODULATION_NEUTRAL} and the strength is the cube of the distance from
     * it, see the design document.
     *
     * @param source The modulation source
     * @param target The modulation target
     * @param strength The modulation strength in the range of [-1..1]
     * @return The payload
     */
    private static byte [] createModulation (final int source, final int target, final double strength)
    {
        final byte [] payload = new byte [8];
        DirectWaveChunk.writeShortLE (payload, DirectWaveTag.MODULATION_SOURCE, source);
        DirectWaveChunk.writeShortLE (payload, DirectWaveTag.MODULATION_TARGET, target);
        final double clamped = Math.clamp (strength, -1, 1);
        final double distance = Math.cbrt (Math.abs (clamped)) / 2.0;
        DirectWaveChunk.writeFloatLE (payload, DirectWaveTag.MODULATION_AMOUNT, (float) (DirectWaveTag.MODULATION_NEUTRAL + Math.signum (clamped) * distance));
        return payload;
    }


    /**
     * Convert a volume modulation depth of the model into the modulation strength of DirectWave,
     * which modulates the linear gain: a strength of 1 lets the gain reach zero.
     *
     * @param depth The depth in the range of [-1..1], 1 is {@link ILfoModulator#MAX_VOLUME_DEPTH}
     * @return The strength in the range of [-1..1]
     */
    private static double volumeStrength (final double depth)
    {
        final double decibels = Math.abs (depth) * ILfoModulator.MAX_VOLUME_DEPTH;
        return Math.signum (depth) * (1.0 - Math.pow (10, -decibels / 20.0));
    }


    /**
     * Fill the audio format block with the frame count, channels, sample rate and the loop of the
     * zone.
     *
     * @param template The template payload of the audio format block
     * @param zone The sample zone
     * @param audioMetadata The metadata of the audio of the zone
     * @param frames The number of frames of the audio, rounded up to a full audio block
     * @return The filled block payload
     */
    private static byte [] createAudioFormat (final byte [] template, final ISampleZone zone, final IAudioMetadata audioMetadata, final int frames)
    {
        final byte [] audioFormat = template.clone ();
        DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_FRAME_COUNT, frames);
        DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_CHANNELS, audioMetadata.getChannels ());
        // The field at offset 12 is not bytes-per-frame in the factory files but a power of two
        // which loosely follows the frame count (a waveform cache stride?); values below 4 were
        // never observed, therefore stay inside the observed value population
        final int blocks = (frames + 8191) / 8192;
        int stride = Integer.highestOneBit (blocks);
        if (stride < blocks)
            stride <<= 1;
        DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_BYTES_PER_FRAME, Math.max (4, stride));
        DirectWaveChunk.writeFloatLE (audioFormat, DirectWaveTag.FORMAT_SAMPLE_RATE, audioMetadata.getSampleRate ());

        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
        {
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_MODE, zone.isOneShot () ? DirectWaveTag.LOOP_MODE_ONE_SHOT : DirectWaveTag.LOOP_MODE_DISABLED);
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_START, 0);
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_END, 0);
        }
        else
        {
            final ISampleLoop loop = loops.get (0);
            final int loopMode;
            if (loop.getType () == LoopType.ALTERNATING)
                loopMode = DirectWaveTag.LOOP_MODE_BOUNCE;
            else
                loopMode = loop.isLoopUntilRelease () ? DirectWaveTag.LOOP_MODE_SUSTAINED : DirectWaveTag.LOOP_MODE_FORWARD;
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_MODE, loopMode);
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_START, Math.clamp (loop.getStart (), 0, frames));
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_END, Math.clamp (loop.getEnd (), 0, frames));
        }
        return audioFormat;
    }


    /**
     * Rename all zones to the naming convention which DirectWave uses itself when saving a program:
     * <i>InstrumentName_Note_Velocity</i>. This makes sure that the file names, the names in the
     * DWP file and the names which DirectWave expects are identical.
     *
     * @param name The name of the instrument
     * @param multisampleSource The multi-sample source
     */
    private static void renameZonesToConvention (final String name, final IMultisampleSource multisampleSource)
    {
        final Set<String> usedNames = new HashSet<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final String baseName = name + "_" + DirectWaveFileNameParser.formatNote (getAdjustedRootKey (zone)) + "_" + Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 1, 127);
                String zoneName = baseName;
                int counter = 2;
                while (!usedNames.add (zoneName))
                {
                    zoneName = baseName + "_" + counter;
                    counter++;
                }
                zone.setName (zoneName);
            }
    }


    /**
     * Get the root key of the zone with the semitones of the zone tuning applied. DirectWave does
     * not have a documented tuning field, therefore the tuning is baked into the root key as good
     * as possible (the cents rest is lost).
     *
     * @param zone The zone
     * @return The adjusted root key
     */
    private static int getAdjustedRootKey (final ISampleZone zone)
    {
        final int rootKey = limitToDefault (zone.getKeyRoot (), Math.clamp (limitToDefault (zone.getKeyLow (), 60), 0, 127));
        return Math.clamp (rootKey - Math.round (zone.getTuning ()), 0, 127);
    }


    /**
     * Remove all zones which belong to alternating play-back (round-robin or random) except those
     * of the first cycle. DirectWave supports trigger groups but their location in the DWP
     * structure is not known; keeping the zones would make them all play at the same time.
     *
     * @param multisampleSource The multi-sample source to filter
     */
    private void filterRoundRobinZones (final IMultisampleSource multisampleSource)
    {
        final List<IGroup> keptGroups = new ArrayList<> ();
        int dropped = 0;
        boolean firstCycleGroupSeen = false;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
        {
            final DefaultGroup keptGroup = new DefaultGroup (group.getName ());
            boolean groupHasUnsequencedZones = false;
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (zone.getPlayLogic () == PlayLogic.ALWAYS)
                {
                    keptGroup.addSampleZone (zone);
                    continue;
                }

                // Alternating zones with a sequence position keep only the first cycle; without
                // one, each group is one cycle and only the first such group is kept
                final int sequencePosition = zone.getSequencePosition ();
                if (sequencePosition == 1 || sequencePosition < 1 && (!firstCycleGroupSeen || groupHasUnsequencedZones))
                {
                    keptGroup.addSampleZone (zone);
                    if (sequencePosition < 1)
                        groupHasUnsequencedZones = true;
                }
                else
                    dropped++;
            }
            if (groupHasUnsequencedZones)
                firstCycleGroupSeen = true;
            if (!keptGroup.getSampleZones ().isEmpty ())
                keptGroups.add (keptGroup);
        }

        if (dropped == 0)
            return;
        multisampleSource.setGroups (keptGroups);
        this.notifier.log ("IDS_DWP_DROPPED_ROUND_ROBIN", Integer.toString (dropped));
    }
}
