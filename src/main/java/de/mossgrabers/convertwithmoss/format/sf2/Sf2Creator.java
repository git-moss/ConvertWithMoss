// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.riff.InfoRiffChunkId;
import de.mossgrabers.convertwithmoss.file.sf2.Generator;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2File;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2Instrument;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2InstrumentZone;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2Modulator;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2Preset;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2PresetZone;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2RiffChunkId;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2SampleDescriptor;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.InfoChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for SoundFont 2 files.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Creator extends AbstractCreator<Sf2CreatorUI>
{
    private static final DestinationAudioFormat DESTINATION_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16,
        24
    }, -1, true);

    private static final int                    PADDING                  = 46;

    /** The minimum of all envelope time generators of the SoundFont specification. */
    private static final int                    MIN_ENVELOPE_TIMECENTS   = -12000;

    private static final Set<Integer>           SUPPORTED_BIT_DEPTHS     = new HashSet<> ();
    static
    {
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (16));
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (24));
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Sf2Creator (final INotifier notifier)
    {
        super ("SoundFont 2", "Sf2", notifier, new Sf2CreatorUI ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        this.storeMultisample (Collections.singletonList (multisampleSource), destinationFolder, sampleName);
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        this.storeMultisample (multisampleSources, destinationFolder, libraryName);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        if (detectSettings.reduceBitDepth <= 0 || SUPPORTED_BIT_DEPTHS.contains (Integer.valueOf (detectSettings.reduceBitDepth)))
            return true;
        this.notifier.log ("IDS_PROCESSING_REDUCE_BIT_DEPTH_NOT_SUPPORTED", Integer.toString (detectSettings.reduceBitDepth), "16, 24");
        return false;
    }


    /**
     * Create all SF2 chunks and store the file.
     *
     * @param multisampleSources The multi-sample sources
     * @param destinationFolder The folder into which to store the sf2 file
     * @param name The name of the sf2 file without the extension
     * @throws IOException Could not store the file
     */
    private void storeMultisample (final List<IMultisampleSource> multisampleSources, final File destinationFolder, final String name) throws IOException
    {
        final File multiFile = this.createUniqueFilename (destinationFolder, name, "sf2");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final Sf2File sf2File = new Sf2File ();
        storeMetadata (multisampleSources, sf2File.getInfoChunk (), name);

        // Create the preset
        final List<Sf2Preset> presets = sf2File.getPresets ();

        final GlobalCounters globalcounters = new GlobalCounters ();

        int programIndex = 0;
        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (this.isCancelled ())
                return;

            final Optional<Sf2Preset> sf2Preset = this.createSf2Preset (programIndex, multisampleSource, globalcounters, globalcounters.instrumentCounts);
            if (sf2Preset.isPresent ())
            {
                final Sf2Preset preset = sf2Preset.get ();
                preset.setProgramNumber (programIndex % 128);
                final int bankNumber = programIndex / 128;
                // No more than 16129 presets
                if (bankNumber > 127)
                    break;
                preset.setBankNumber (bankNumber);
                presets.add (preset);

                programIndex++;
            }
        }

        // Add the final empty preset
        final Sf2Preset finalPreset = new Sf2Preset ("EOP");
        finalPreset.setBankNumber (0xFF);
        finalPreset.setProgramNumber (0xFF);
        presets.add (finalPreset);

        for (final Sf2Preset preset: presets)
            preset.updateCounts (globalcounters.presetCounts);

        sf2File.createPresetDataChunks ();

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            sf2File.write (out);
        }

        this.progress.notifyDone ();
    }


    /**
     * Fill the info chunks with metadata.
     *
     * @param multisampleSources The source which contains the metadata
     * @param infoChunk The info chunk object which handles the different info-sub-chunks
     * @param name The name to set
     */
    private static void storeMetadata (final List<IMultisampleSource> multisampleSources, final InfoChunk infoChunk, final String name)
    {
        final Set<String> creators = new HashSet<> ();
        final Set<String> descriptions = new HashSet<> ();
        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            final IMetadata metadata = multisampleSource.getMetadata ();
            final String creator = metadata.getCreator ();
            if (!creator.isBlank ())
                creators.add (creator);
            final String description = metadata.getDescription ();
            if (!description.isBlank ())
                descriptions.add (description);
        }

        // Version number
        infoChunk.addInfoField (Sf2RiffChunkId.IFIL_ID, new byte []
        {
            2,
            0,
            1,
            0
        });

        // Mandatory info fields
        // Wave-table sound engine
        infoChunk.addInfoTextField (Sf2RiffChunkId.ISNG_ID, "EMU8000", 256);
        infoChunk.addInfoTextField (InfoRiffChunkId.INFO_INAM, StringUtils.fixASCII (name), 256);

        // Optional info fields
        infoChunk.addCreationDate (multisampleSources.get (0).getMetadata ().getCreationDateTime ());
        final String creator = String.join (", ", creators);
        if (!creator.isBlank ())
            infoChunk.addInfoTextField (InfoRiffChunkId.INFO_IENG, StringUtils.fixASCII (creator), 256);
        final String description = String.join ("\n", descriptions);
        if (!description.isBlank ())
            infoChunk.addInfoTextField (InfoRiffChunkId.INFO_ICMT, StringUtils.fixASCII (description), 65536);
    }


    /**
     * Create one SF2 preset for the multi-sample source.
     *
     * @param programIndex The index of the program
     * @param multisampleSource The multi-sample source
     * @param globalcounters Contains all counters for numbering which are global to the sf2 file
     * @param counts Counter for instrument generators and modulators
     * @return The created SF2 preset
     * @throws IOException Could not create the preset
     */
    private Optional<Sf2Preset> createSf2Preset (final int programIndex, final IMultisampleSource multisampleSource, final GlobalCounters globalcounters, final Pair<Integer, Integer> counts) throws IOException
    {
        final String name = multisampleSource.getName ();
        final String message = Functions.getMessage ("IDS_NOTIFY_ADDING", programIndex / 128 + ":" + programIndex % 128 + " " + name);
        this.notifier.logText (message);
        int writtenProgress = message.length ();

        // Note: If multiple sources might be combined into one SF2 in the future, the program
        // number needs to be set here as well as the first preset zone index
        final Sf2Preset preset = new Sf2Preset (name);

        // Create a SF2 instrument for each group
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
        {
            final Sf2Instrument instrument = new Sf2Instrument ();
            instrument.setName (group.getName ());
            final Sf2PresetZone presetZone = new Sf2PresetZone (instrument, globalcounters.instrumentIndex);
            globalcounters.instrumentIndex++;
            preset.addZone (presetZone);

            for (final ISampleZone sampleZone: group.getSampleZones ())
            {
                this.notifier.log ("IDS_NOTIFY_PROGRESS");
                writtenProgress++;
                if (writtenProgress > 0 && writtenProgress % 80 == 0)
                    this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                if (this.isCancelled ())
                    return Optional.empty ();

                // Ensure that the WAV is 16 or 24 bit
                final Optional<ISampleData> sampleData = sampleZone.getSampleData ();
                if (sampleData.isEmpty ())
                {
                    this.notifier.logError ("IDS_ERR_NO_SAMPLE_DATA", sampleZone.getName ());
                    continue;
                }

                final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_AUDIO_FORMAT);
                final FormatChunk formatChunk = waveFile.getFormatChunk ();
                final int numberOfChannels = formatChunk.getNumberOfChannels ();
                if (numberOfChannels > 2)
                    throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), sampleZone.getName ()));

                final DataChunk dataChunk = waveFile.getDataChunk ();
                int numSamples;
                try
                {
                    numSamples = dataChunk.calculateLength (formatChunk);
                }
                catch (final CompressionNotSupportedException ex)
                {
                    throw new IOException (ex);
                }

                final byte [] data = dataChunk.getData ();

                final boolean is24Bit = formatChunk.getSignificantBitsPerSample () == 24;
                final boolean isStereo = formatChunk.getNumberOfChannels () == 2;
                final boolean shouldDownsample = is24Bit && this.settingsConfiguration.isDownsampleTo16Bit ();

                final List<byte []> sampleDataList = convertData (data, numSamples, is24Bit, isStereo, shouldDownsample);
                if (isStereo)
                {
                    final Sf2SampleDescriptor leftDesc = createSf2SampleDescriptor (Sf2SampleDescriptor.LEFT, globalcounters.sampleIndex, globalcounters.sampleStartPosition, sampleZone, formatChunk, numSamples, sampleDataList.get (0), sampleDataList.get (1));
                    globalcounters.sampleStartPosition += numSamples + PADDING;
                    final Sf2SampleDescriptor rightDesc = createSf2SampleDescriptor (Sf2SampleDescriptor.RIGHT, globalcounters.sampleIndex + 1, globalcounters.sampleStartPosition, sampleZone, formatChunk, numSamples, sampleDataList.get (2), sampleDataList.get (3));
                    globalcounters.sampleStartPosition += numSamples + PADDING;
                    leftDesc.setLinkedSample (globalcounters.sampleIndex + 1);
                    rightDesc.setLinkedSample (globalcounters.sampleIndex);
                    createInstrumentZone (instrument, leftDesc, sampleZone);
                    createInstrumentZone (instrument, rightDesc, sampleZone);
                    globalcounters.sampleIndex += 2;
                }
                else
                {
                    final Sf2SampleDescriptor desc = createSf2SampleDescriptor (Sf2SampleDescriptor.MONO, globalcounters.sampleIndex, globalcounters.sampleStartPosition, sampleZone, formatChunk, numSamples, sampleDataList.get (0), sampleDataList.get (1));
                    globalcounters.sampleStartPosition += numSamples + PADDING;
                    createInstrumentZone (instrument, desc, sampleZone);
                    globalcounters.sampleIndex++;
                }
            }

            instrument.setFirstZoneIndex (globalcounters.firstZoneIndex);
            globalcounters.firstZoneIndex += instrument.getZoneCount ();

            instrument.updateCounts (counts);
        }

        this.notifier.log ("IDS_NOTIFY_LINE_FEED");

        return Optional.of (preset);
    }


    /**
     * Create an SF2 instrument zone.
     *
     * @param instrument The SF2 instrument to which to add the zone
     * @param sampleDescriptor The SF2 sample descriptor
     * @param sampleZone The sample zone
     */
    private static void createInstrumentZone (final Sf2Instrument instrument, final Sf2SampleDescriptor sampleDescriptor, final ISampleZone sampleZone)
    {
        final Sf2InstrumentZone instrumentZone = new Sf2InstrumentZone ();
        instrumentZone.setSample (sampleDescriptor);
        instrument.addZone (instrumentZone);

        // Set pitch bend modulator
        final int bendUp = sampleZone.getBendUp ();
        instrumentZone.addModulator (Sf2Modulator.MODULATOR_PITCH_BEND.intValue (), Generator.FINE_TUNE, bendUp, 0x10, 0);

        // Set panning
        double pan = sampleZone.getPanning ();
        final int sampleType = sampleDescriptor.getSampleType ();
        if (sampleType == Sf2SampleDescriptor.LEFT)
            pan = -1;
        else if (sampleType == Sf2SampleDescriptor.RIGHT)
            pan = 1;
        instrumentZone.addSignedGenerator (Generator.PANNING, (int) Math.round (pan * 500.0));

        // Set the pitch
        instrumentZone.addGenerator (Generator.OVERRIDING_ROOT_KEY, sampleZone.getKeyRoot ());

        final double tune = sampleZone.getTuning ();
        final int coarse = (int) Math.round (tune);
        instrumentZone.addSignedGenerator (Generator.COARSE_TUNE, coarse);
        instrumentZone.addSignedGenerator (Generator.FINE_TUNE, (int) Math.round ((tune - coarse) * 100.0));
        instrumentZone.addGenerator (Generator.SCALE_TUNE, (int) Math.round (sampleZone.getKeyTracking () * 100.0));

        // Set the key & velocity range
        instrumentZone.addGenerator (Generator.KEY_RANGE, limitToDefault (sampleZone.getKeyLow (), 0), limitToDefault (sampleZone.getKeyHigh (), 127));
        instrumentZone.addGenerator (Generator.VELOCITY_RANGE, limitToDefault (sampleZone.getVelocityLow (), 1), limitToDefault (sampleZone.getVelocityHigh (), 127));

        // Set the exclusive group. Only written if set since 0 (= no exclusive class) is the
        // default value of the generator
        final int exclusiveGroup = sampleZone.getExclusiveGroup ();
        if (exclusiveGroup > 0)
            instrumentZone.addGenerator (Generator.EXCLUSIVE_CLASS, Math.clamp (exclusiveGroup, 1, 127));

        // Set loop, if any: mode 1 loops continuously, mode 3 is a sustain loop (loops until the
        // key is released and then plays the remainder of the sample)
        instrumentZone.addGenerator (Generator.SAMPLE_MODES, getLoopMode (sampleZone));

        // Gain
        instrumentZone.addGenerator (Generator.INITIAL_ATTENUATION, (int) Math.round (-sampleZone.getGain () * 10.0));

        final double ampDepth = sampleZone.getAmplitudeVelocityModulator ().getDepth ();
        if (ampDepth != 0)
            instrumentZone.addModulator (Sf2Modulator.MODULATOR_VELOCITY.intValue (), Generator.INITIAL_ATTENUATION, (int) Math.round (ampDepth * 960), 0, 0);

        // Volume envelope
        final IEnvelope amplitudeEnvelope = sampleZone.getAmplitudeEnvelopeModulator ().getSource ();

        setEnvelopeTime (instrumentZone, Generator.VOL_ENV_DELAY, amplitudeEnvelope.getDelayTime ());
        setEnvelopeTime (instrumentZone, Generator.VOL_ENV_ATTACK, amplitudeEnvelope.getAttackTime ());
        setEnvelopeTime (instrumentZone, Generator.VOL_ENV_HOLD, amplitudeEnvelope.getHoldTime ());
        setEnvelopeTime (instrumentZone, Generator.VOL_ENV_DECAY, amplitudeEnvelope.getDecayTime ());
        setEnvelopeTime (instrumentZone, Generator.VOL_ENV_RELEASE, amplitudeEnvelope.getReleaseTime ());
        setEnvelopeLevel (instrumentZone, Generator.VOL_ENV_SUSTAIN, amplitudeEnvelope.getSustainLevel ());
        setEnvelopeKeyTracking (instrumentZone, Generator.KEYNUM_TO_VOL_ENV_HOLD, Generator.KEYNUM_TO_VOL_ENV_DECAY, amplitudeEnvelope.getTimeKeyTracking ());

        // Set the pitch envelope. It might be overwritten in the filter section since Sf2 only
        // supports one modulation envelope
        final IEnvelopeModulator pitchModulator = sampleZone.getPitchEnvelopeModulator ();
        final double pitchModDepth = pitchModulator.getDepth ();
        if (pitchModDepth > 0)
        {
            instrumentZone.addSignedGenerator (Generator.MOD_ENV_TO_PITCH, (int) Math.round (pitchModDepth * IEnvelope.MAX_ENVELOPE_DEPTH));
            final IEnvelope pitchEnvelope = pitchModulator.getSource ();
            setEnvelopeTime (instrumentZone, Generator.MOD_ENV_DELAY, pitchEnvelope.getDelayTime ());
            setEnvelopeTime (instrumentZone, Generator.MOD_ENV_ATTACK, pitchEnvelope.getAttackTime ());
            setEnvelopeTime (instrumentZone, Generator.MOD_ENV_HOLD, pitchEnvelope.getHoldTime ());
            setEnvelopeTime (instrumentZone, Generator.MOD_ENV_DECAY, pitchEnvelope.getDecayTime ());
            setEnvelopeTime (instrumentZone, Generator.MOD_ENV_RELEASE, pitchEnvelope.getReleaseTime ());
            setEnvelopeLevel (instrumentZone, Generator.MOD_ENV_SUSTAIN, pitchEnvelope.getSustainLevel ());
            setEnvelopeKeyTracking (instrumentZone, Generator.KEYNUM_TO_MOD_ENV_HOLD, Generator.KEYNUM_TO_MOD_ENV_DECAY, pitchEnvelope.getTimeKeyTracking ());
        }

        // Set the vibrato low frequency oscillator from the pitch modulation. The depth is given in
        // cent like the pitch envelope, the fade-in and the waveform have no equivalent in Sf2.
        final ILfoModulator pitchLfoModulator = sampleZone.getPitchLfoModulator ();
        final double vibLfoDepth = pitchLfoModulator.getDepth ();
        if (vibLfoDepth != 0)
        {
            instrumentZone.addSignedGenerator (Generator.VIB_LFO_TO_PITCH, (int) Math.round (vibLfoDepth * IEnvelope.MAX_ENVELOPE_DEPTH));
            final ILfo pitchLfo = pitchLfoModulator.getSource ();
            final double rate = pitchLfo.getRate ();
            if (rate > 0)
            {
                // The frequency is stored in absolute cents, see the filter cutoff below
                final double frequencyCents = Math.log (rate / 8.176) * 1200.0 / Math.log (2);
                instrumentZone.addSignedGenerator (Generator.FREQ_VIB_LFO, (int) Math.round (frequencyCents));
            }
            final double delay = pitchLfo.getDelay ();
            if (delay >= 0)
                instrumentZone.addSignedGenerator (Generator.DELAY_VIB_LFO, convertEnvelopeTime (delay));
        }

        // Filter settings
        final Optional<IFilter> filterOpt = sampleZone.getFilter ();
        if (filterOpt.isPresent ())
        {
            final IFilter filter = filterOpt.get ();
            if (filter.getType () == FilterType.LOW_PASS)
            {
                final double frequency = filter.getCutoff ();
                final double resonance = filter.getResonance () * IFilter.MAX_RESONANCE;

                // Convert cents to Hertz: f2 is the minimum supported frequency, cents is
                // always a relation of two frequencies, 1200 cents are one octave:
                // cents = 1200 * log2 (f1 / f2), f2 = 8.176 => f1 = f2 * 2^(cents / 1200)
                final double initialCutoff = Math.log (frequency / 8.176) * 1200.0 / Math.log (2);
                instrumentZone.addGenerator (Generator.INITIAL_FILTER_CUTOFF, (int) Math.clamp (initialCutoff, 1500, 13500));
                // The resonance is stored in centi-bel (dB * 10) in the range of [0..960]
                instrumentZone.addSignedGenerator (Generator.INITIAL_FILTER_RESONANCE, Math.clamp (Math.round (resonance * 10.0), 0, 960));

                final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                final double cutoffModDepth = cutoffModulator.getDepth ();
                if (cutoffModDepth > 0)
                {
                    instrumentZone.addSignedGenerator (Generator.MOD_ENV_TO_FILTER_CUTOFF, (int) (cutoffModDepth * IEnvelope.MAX_ENVELOPE_DEPTH));
                    final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                    setEnvelopeTime (instrumentZone, Generator.MOD_ENV_DELAY, filterEnvelope.getDelayTime ());
                    setEnvelopeTime (instrumentZone, Generator.MOD_ENV_ATTACK, filterEnvelope.getAttackTime ());
                    setEnvelopeTime (instrumentZone, Generator.MOD_ENV_HOLD, filterEnvelope.getHoldTime ());
                    setEnvelopeTime (instrumentZone, Generator.MOD_ENV_DECAY, filterEnvelope.getDecayTime ());
                    setEnvelopeTime (instrumentZone, Generator.MOD_ENV_RELEASE, filterEnvelope.getReleaseTime ());
                    setEnvelopeLevel (instrumentZone, Generator.MOD_ENV_SUSTAIN, filterEnvelope.getSustainLevel ());
                    setEnvelopeKeyTracking (instrumentZone, Generator.KEYNUM_TO_MOD_ENV_HOLD, Generator.KEYNUM_TO_MOD_ENV_DECAY, filterEnvelope.getTimeKeyTracking ());
                }

                final double cutoffDepth = filter.getCutoffVelocityModulator ().getDepth ();
                if (cutoffDepth != 0)
                    instrumentZone.addModulator (Sf2Modulator.MODULATOR_VELOCITY.intValue (), Generator.INITIAL_FILTER_CUTOFF, (int) Math.round (cutoffDepth * -2400), 0, 0);
            }
        }

        // Sample reference needs to be last
        instrumentZone.addGenerator (Generator.SAMPLE_ID, sampleDescriptor.getSampleIndex ());
    }


    /**
     * Splits the given data array into left/right data arrays for 16-bit and additional 24-bit byte
     * arrays. Arrays are padded with PADDING number of zero samples at the end.
     *
     * @param data The interleaved data array
     * @param numSamples The number of samples
     * @param is24Bit Is it a 24-bit?
     * @param isStereo Is it stereo?
     * @param shouldDownsample If true, re-sample 24-bit to 16-bit
     * @return The up to 2 (mono) or 4 (stereo) arrays
     */
    private static List<byte []> convertData (final byte [] data, final int numSamples, final boolean is24Bit, final boolean isStereo, final boolean shouldDownsample)
    {
        final int numPaddedSamples = numSamples + PADDING;
        final byte [] sampleLeftData = new byte [numPaddedSamples * 2];
        final byte [] sampleLeft24Data = new byte [is24Bit && !shouldDownsample ? numPaddedSamples : 0];
        final byte [] sampleRightData = new byte [numPaddedSamples * 2];
        final byte [] sampleRight24Data = new byte [is24Bit && !shouldDownsample ? numPaddedSamples : 0];

        int pos = -1;
        for (int i = 0; i < numSamples; i++)
        {
            final int offset = 2 * i;

            if (is24Bit)
            {
                if (shouldDownsample)
                {
                    // Re-sample 24-bit to 16-bit
                    final byte lsb = data[++pos];
                    final byte msb = data[++pos];
                    final byte hsb = data[++pos];

                    // Convert to 24-bit integer
                    int sample24 = (hsb & 0xFF) << 16 | (msb & 0xFF) << 8 | lsb & 0xFF;
                    // Sign extend if negative
                    if ((sample24 & 0x800000) != 0)
                        sample24 |= 0xFF000000;

                    // Clean, predictable conversion
                    final short sample16 = (short) Math.round (sample24 / 256.0);

                    // Store as 16-bit little-endian
                    sampleLeftData[offset] = (byte) (sample16 & 0xFF);
                    sampleLeftData[offset + 1] = (byte) (sample16 >> 8 & 0xFF);
                }
                else
                {
                    // Keep original 24-bit data
                    sampleLeft24Data[i] = data[++pos];
                    sampleLeftData[offset] = data[++pos];
                    sampleLeftData[offset + 1] = data[++pos];
                }
            }
            else
            {
                // 16-bit data, just copy
                sampleLeftData[offset] = data[++pos];
                sampleLeftData[offset + 1] = data[++pos];
            }

            if (isStereo)
                if (is24Bit)
                {
                    if (shouldDownsample)
                    {
                        // Re-sample 24-bit to 16-bit
                        final byte lsb = data[++pos];
                        final byte msb = data[++pos];
                        final byte hsb = data[++pos];

                        // Convert to 24-bit integer
                        int sample24 = (hsb & 0xFF) << 16 | (msb & 0xFF) << 8 | lsb & 0xFF;
                        // Sign extend if negative
                        if ((sample24 & 0x800000) != 0)
                            sample24 |= 0xFF000000;

                        // Clean, predictable conversion
                        final short sample16 = (short) Math.round (sample24 / 256.0);

                        // Store as 16-bit little-endian
                        sampleRightData[offset] = (byte) (sample16 & 0xFF);
                        sampleRightData[offset + 1] = (byte) (sample16 >> 8 & 0xFF);
                    }
                    else
                    {
                        // Keep original 24-bit data
                        sampleRight24Data[i] = data[++pos];
                        sampleRightData[offset] = data[++pos];
                        sampleRightData[offset + 1] = data[++pos];
                    }
                }
                else
                {
                    // 16-bit data, just copy
                    sampleRightData[offset] = data[++pos];
                    sampleRightData[offset + 1] = data[++pos];
                }
        }

        final List<byte []> sampleList = new ArrayList<> (4);
        Collections.addAll (sampleList, sampleLeftData, sampleLeft24Data);
        if (isStereo)
            Collections.addAll (sampleList, sampleRightData, sampleRight24Data);
        return sampleList;
    }


    /**
     * Create an SF2 sample descriptor.
     *
     * @param sampleType The type of the sample: monoSample = 1, rightSample = 2, leftSample = 4
     * @param sampleIndex The index of the sample
     * @param sampleStartPosition The start position of the sample in the big sample data blob
     * @param sampleZone The sample zone
     * @param formatChunk The WAV format chunk
     * @param numSamples The number of samples (frames) of the sample
     * @param sampleData The 16-bit sample data
     * @param sample24Data The additional 24-bit bytes
     * @return The SF2 sample descriptor
     */
    private static Sf2SampleDescriptor createSf2SampleDescriptor (final int sampleType, final int sampleIndex, final long sampleStartPosition, final ISampleZone sampleZone, final FormatChunk formatChunk, final int numSamples, final byte [] sampleData, final byte [] sample24Data)
    {
        final Sf2SampleDescriptor sampleDescriptor = new Sf2SampleDescriptor (sampleIndex, sampleData, sample24Data);

        sampleDescriptor.setSampleType (sampleType);
        sampleDescriptor.setSampleRate (formatChunk.getSampleRate ());
        sampleDescriptor.setOriginalPitch (Math.clamp (sampleZone.getKeyRoot (), 0, 127));
        // The tuning is applied by the COARSE_TUNE and FINE_TUNE generators of the instrument
        // zone. A synthesizer sums up the generators and the pitch correction of the sample,
        // therefore it must not be applied here a second time. Furthermore, the pitch correction
        // is only a signed byte and could not store more than +-127 cents anyway.
        sampleDescriptor.setPitchCorrection (0);

        String name = sampleZone.getName ();
        if (name.endsWith ("-") || name.endsWith ("_"))
            name = name.substring (0, name.length () - 1);
        if (sampleType == Sf2SampleDescriptor.LEFT)
            name += "_L";
        else if (sampleType == Sf2SampleDescriptor.RIGHT)
            name += "_R";
        sampleDescriptor.setName (name);
        sampleDescriptor.setStart (sampleStartPosition);
        sampleDescriptor.setEnd (sampleStartPosition + numSamples);

        final List<ISampleLoop> loops = sampleZone.getLoops ();
        long loopStart = sampleStartPosition;
        long loopEnd = sampleStartPosition;
        if (!loops.isEmpty ())
        {
            final ISampleLoop sampleLoop = loops.get (0);
            loopStart += sampleLoop.getStart ();
            loopEnd += sampleLoop.getEnd ();
        }
        sampleDescriptor.setLoopStart (loopStart);
        sampleDescriptor.setLoopEnd (loopEnd);

        return sampleDescriptor;
    }


    private static int convertEnvelopeTime (final double time)
    {
        // The logarithm of zero is negative infinity. -12000 time-cents (about 1 millisecond) is
        // the minimum of all envelope time generators in the SoundFont specification, a value of 0
        // would mean 1 second!
        if (time <= 0)
            return MIN_ENVELOPE_TIMECENTS;
        return (int) Math.max (MIN_ENVELOPE_TIMECENTS, Math.round (Math.log (time) * 1200.0 / Math.log (2)));
    }


    private static int convertEnvelopeVolume (final double value)
    {
        if (value < 0)
            return 0;
        // Attenuation is in centi-bel (dB / 10), so 0 is maximum volume, about 1000 is off
        // This is likely not correct but since there is also no documentation what the percentage
        // volume values mean in dB it is the best we can do...
        return (int) Math.round (Math.clamp ((1.0 - value) * 1000.0, 0, 1000));
    }


    /**
     * Set the key tracking of the envelope times. SoundFont 2 has a dedicated generator for the
     * hold and for the decay phase, both are given in time-cents per key number and are specified
     * in the range of [-1200..1200]. Since the model scales all times of an envelope with one
     * value, both generators are set to the same amount. Nothing is written if there is no key
     * tracking, which is the default of both generators.
     *
     * @param instrumentZone The zone to which to add the generators
     * @param holdGenerator The ID of the key number to envelope hold generator
     * @param decayGenerator The ID of the key number to envelope decay generator
     * @param keyTracking The key tracking in the range of [-1..1]
     */
    private static void setEnvelopeKeyTracking (final Sf2InstrumentZone instrumentZone, final int holdGenerator, final int decayGenerator, final double keyTracking)
    {
        if (keyTracking == 0)
            return;
        final int value = (int) Math.round (Math.clamp (keyTracking, -1.0, 1.0) * Generator.MAX_KEYNUM_TO_ENV);
        instrumentZone.addSignedGenerator (holdGenerator, value);
        instrumentZone.addSignedGenerator (decayGenerator, value);
    }


    private static void setEnvelopeTime (final Sf2InstrumentZone instrumentZone, final int generator, final double time)
    {
        if (time >= 0)
            instrumentZone.addSignedGenerator (generator, convertEnvelopeTime (time));
    }


    private static void setEnvelopeLevel (final Sf2InstrumentZone instrumentZone, final int generator, final double level)
    {
        if (level >= 0)
            instrumentZone.addSignedGenerator (generator, convertEnvelopeVolume (level));
    }


    private static int getLoopMode (final ISampleZone sampleZone)
    {
        final List<ISampleLoop> sampleLoops = sampleZone.getLoops ();
        if (sampleLoops.isEmpty ())
            return 0;
        return sampleLoops.get (0).isLoopUntilRelease () ? 3 : 1;
    }


    /** Contains all counters for numbering which are global to the sf2 file. */
    private class GlobalCounters
    {
        int                          firstZoneIndex      = 0;
        int                          instrumentIndex     = 0;
        int                          sampleIndex         = 0;
        long                         sampleStartPosition = 0;

        final Pair<Integer, Integer> presetCounts;
        final Pair<Integer, Integer> instrumentCounts;


        GlobalCounters ()
        {
            this.presetCounts = new Pair<> (Integer.valueOf (0), Integer.valueOf (0));
            this.instrumentCounts = new Pair<> (Integer.valueOf (0), Integer.valueOf (0));
        }
    }
}