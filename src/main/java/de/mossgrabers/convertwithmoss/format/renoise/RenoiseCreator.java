// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.renoise;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipOutputStream;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.XMLUtils;


/**
 * Creator for Renoise instrument files (XRNI). Such a file is a renamed ZIP file with the ending
 * "xrni" which contains an <i>Instrument.xml</i> description file plus all samples (in FLAC format)
 * in a <i>SampleData</i> folder.
 *
 * @author Jürgen Moßgraber
 */
public class RenoiseCreator extends AbstractCreator<EmptySettingsUI>
{
    /** The document version to write. 33 (Renoise 3.3) is used instead of the latest version since
     * the written structure is valid for it and it loads in newer Renoise versions as well as in
     * Renoise Redux. */
    private static final String DOC_VERSION         = "33";
    private static final String SAMPLE_DATA_FOLDER  = "SampleData";

    /** The neutral (mid-position) value for a mixer cutoff/resonance when there is no filter. */
    private static final double                 DEFAULT_MIXER_VALUE      = 64.0;

    // Used when a loop cross-fade needs to be baked into the audio. 32-bit float samples are
    // reduced to 24 bit; the sample rate is kept unchanged.
    private static final DestinationAudioFormat DESTINATION_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16,
        24
    }, -1, false);

    private int                                 padWidth                 = 2;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public RenoiseCreator (final INotifier notifier)
    {
        super ("Renoise", "Renoise", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.padWidth = calcPadWidth (multisampleSource);

        final Optional<String> metadata = this.createMetadata (multisampleSource);
        if (metadata.isEmpty ())
            return;

        final File multiFile = this.createUniqueFilename (destinationFolder, createSafeFilename (multisampleSource.getName ()), "xrni");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            zos.setMethod (ZipOutputStream.STORED);
            AbstractCreator.storeTextFile (zos, "Instrument.xml", metadata.get (), multisampleSource.getMetadata ().getCreationDateTime ());
            this.storeSampleFiles (zos, null, multisampleSource);
        }

        this.progress.notifyDone ();
    }


    /**
     * Create the text of the description file.
     *
     * @param multisampleSource The multi-sample
     * @return The XML structure
     */
    private Optional<String> createMetadata (final IMultisampleSource multisampleSource)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement (RenoiseTag.ROOT);
        rootElement.setAttribute (RenoiseTag.ATTR_DOC_VERSION, DOC_VERSION);
        document.appendChild (rootElement);

        XMLUtils.addTextElement (document, rootElement, RenoiseTag.NAME, multisampleSource.getName ());

        // Global properties (master volume + the description as comment lines)
        final Element globalProperties = XMLUtils.addElement (document, rootElement, RenoiseTag.GLOBAL_PROPERTIES);
        globalProperties.setAttribute (RenoiseTag.ATTR_TYPE, "InstrumentGlobalProperties");
        XMLUtils.addTextElement (document, globalProperties, RenoiseTag.VOLUME, "1.0");
        final IMetadata metadata = multisampleSource.getMetadata ();
        final String description = metadata.getDescription ();
        if (description != null && !description.isBlank ())
        {
            final Element commentsElement = XMLUtils.addElement (document, globalProperties, RenoiseTag.COMMENTS);
            for (final String line: description.split ("\\R"))
                XMLUtils.addTextElement (document, commentsElement, RenoiseTag.COMMENT, line);
        }

        final Element sampleGenerator = XMLUtils.addElement (document, rootElement, RenoiseTag.SAMPLE_GENERATOR);
        final Element samplesElement = XMLUtils.addElement (document, sampleGenerator, RenoiseTag.SAMPLES);
        samplesElement.setAttribute (RenoiseTag.ATTR_TYPE, "SampleList");
        final Element modulationSetsElement = document.createElement (RenoiseTag.MODULATION_SETS);

        boolean hasRoundRobin = false;
        int zoneIndex = 0;
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                this.createSampleElement (document, samplesElement, zoneIndex, zone);
                createModulationSet (document, modulationSetsElement, zoneIndex, zone);
                hasRoundRobin = hasRoundRobin || zone.getPlayLogic () == PlayLogic.ROUND_ROBIN;
                zoneIndex++;
            }

        sampleGenerator.appendChild (modulationSetsElement);
        XMLUtils.addTextElement (document, sampleGenerator, RenoiseTag.KEYZONE_OVERLAPPING_MODE, hasRoundRobin ? RenoiseTag.OVERLAP_CYCLE : RenoiseTag.OVERLAP_PLAY_ALL);

        return this.createXMLString (document);
    }


    /**
     * Create the XML for one sample.
     *
     * @param document The XML document
     * @param samplesElement The element to which to add the sample
     * @param zoneIndex The global index of the zone (used to reference the sample file and the
     *            modulation set)
     * @param zone The zone from which to read the sample information
     */
    private void createSampleElement (final Document document, final Element samplesElement, final int zoneIndex, final ISampleZone zone)
    {
        final Element sampleElement = XMLUtils.addElement (document, samplesElement, RenoiseTag.SAMPLE);

        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.NAME, zone.getName ());
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.FILE_NAME, this.sampleBaseName (zoneIndex, zone));
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.VOLUME, formatFloat (RenoiseValueConverter.gainToVolume (zone.getGain ())));
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.PANNING, formatFloat (RenoiseValueConverter.panningToRenoise (zone.getPanning ())));

        final double tuning = zone.getTuning ();
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.TRANSPOSE, Integer.toString (RenoiseValueConverter.tuningToTranspose (tuning)));
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.FINETUNE, Integer.toString (RenoiseValueConverter.tuningToFinetune (tuning)));
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.NEW_NOTE_ACTION, RenoiseTag.NNA_NOTE_OFF);
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.INTERPOLATION, "Cubic");

        // Loop
        final List<ISampleLoop> loops = zone.getLoops ();
        final ISampleLoop loop = loops.isEmpty () ? null : loops.get (0);
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.LOOP_MODE, loopMode (loop));
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.LOOP_RELEASE, "false");
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.LOOP_START, Integer.toString (loop == null ? 0 : Math.max (0, loop.getStart ())));
        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.LOOP_END, Integer.toString (loop == null ? 0 : Math.max (0, loop.getEnd ())));

        XMLUtils.addTextElement (document, sampleElement, RenoiseTag.MODULATION_SET_INDEX, Integer.toString (zoneIndex));

        // Key/velocity mapping
        final Element mappingElement = XMLUtils.addElement (document, sampleElement, RenoiseTag.MAPPING);
        XMLUtils.addTextElement (document, mappingElement, RenoiseTag.LAYER, zone.getTrigger () == TriggerType.RELEASE ? RenoiseTag.LAYER_NOTE_OFF : RenoiseTag.LAYER_NOTE_ON);
        XMLUtils.addTextElement (document, mappingElement, RenoiseTag.BASE_NOTE, Integer.toString (RenoiseValueConverter.clampNote (zone.getKeyRoot ())));
        XMLUtils.addTextElement (document, mappingElement, RenoiseTag.NOTE_START, Integer.toString (RenoiseValueConverter.clampNote (zone.getKeyLow ())));
        XMLUtils.addTextElement (document, mappingElement, RenoiseTag.NOTE_END, Integer.toString (RenoiseValueConverter.clampNote (limitToDefault (zone.getKeyHigh (), RenoiseValueConverter.MAX_NOTE))));
        XMLUtils.addTextElement (document, mappingElement, RenoiseTag.MAP_KEY_TO_PITCH, "true");
        XMLUtils.addTextElement (document, mappingElement, RenoiseTag.VELOCITY_START, Integer.toString (Math.clamp (zone.getVelocityLow (), 0, 127)));
        XMLUtils.addTextElement (document, mappingElement, RenoiseTag.VELOCITY_END, Integer.toString (Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 0, 127)));
        XMLUtils.addTextElement (document, mappingElement, RenoiseTag.MAP_VELOCITY_TO_VOLUME, "true");
    }


    /**
     * Create the modulation set for one zone. It holds the amplitude envelope and - if present - the
     * sampler filter (type, cutoff, resonance and filter envelope) and the pitch envelope.
     *
     * @param document The XML document
     * @param modulationSetsElement The element to which to add the modulation set
     * @param zoneIndex The global index of the zone
     * @param zone The zone from which to read the modulation information
     */
    private static void createModulationSet (final Document document, final Element modulationSetsElement, final int zoneIndex, final ISampleZone zone)
    {
        final Element modulationSetElement = XMLUtils.addElement (document, modulationSetsElement, RenoiseTag.MODULATION_SET);
        XMLUtils.addTextElement (document, modulationSetElement, RenoiseTag.SELECTED_PRESET_NAME, "Init");
        XMLUtils.addTextElement (document, modulationSetElement, RenoiseTag.SELECTED_PRESET_LIBRARY, "Bundled Content");
        XMLUtils.addTextElement (document, modulationSetElement, RenoiseTag.SELECTED_PRESET_MODIFIED, "true");
        final Element devicesElement = XMLUtils.addElement (document, modulationSetElement, RenoiseTag.DEVICES);

        // Determine the filter (if any) to set the base cutoff/resonance values of the mixer device
        int filterTypeIndex = RenoiseFilterType.INDEX_NONE;
        double cutoff = DEFAULT_MIXER_VALUE;
        double resonance = DEFAULT_MIXER_VALUE;
        IEnvelope cutoffEnvelope = null;
        final Optional<IFilter> optionalFilter = zone.getFilter ();
        if (optionalFilter.isPresent ())
        {
            final IFilter filter = optionalFilter.get ();
            filterTypeIndex = RenoiseFilterType.toFilterTypeIndex (filter.getType ());
            cutoff = RenoiseValueConverter.cutoffToMixer (filter.getCutoff ());
            resonance = RenoiseValueConverter.resonanceToMixer (filter.getResonance ());
            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            if (cutoffModulator.getDepth () != 0)
                cutoffEnvelope = cutoffModulator.getSource ();
        }

        // The mixer device holds the base input values and is required for the filter view to be
        // built; without it Renoise crashes when a filter is active
        createMixerDevice (document, devicesElement, cutoff, resonance);

        // The amplitude envelope is always present
        createAhdsrDevice (document, devicesElement, RenoiseTag.TARGET_VOLUME, RenoiseTag.OP_MULTIPLY, false, zone.getAmplitudeEnvelopeModulator ().getSource ());

        if (cutoffEnvelope != null)
            createAhdsrDevice (document, devicesElement, RenoiseTag.TARGET_CUTOFF, RenoiseTag.OP_ADD, false, cutoffEnvelope);

        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
        if (pitchModulator.getDepth () != 0)
            createAhdsrDevice (document, devicesElement, RenoiseTag.TARGET_PITCH, RenoiseTag.OP_ADD, true, pitchModulator.getSource ());

        XMLUtils.addTextElement (document, modulationSetElement, RenoiseTag.NAME, "Set " + (zoneIndex + 1));
        XMLUtils.addTextElement (document, modulationSetElement, RenoiseTag.FILTER_TYPE, Integer.toString (filterTypeIndex));
        XMLUtils.addTextElement (document, modulationSetElement, RenoiseTag.FILTER_BANK_VERSION, Integer.toString (RenoiseFilterType.FILTER_BANK_VERSION));
    }


    /**
     * Create the mixer modulation device which holds the base input values (volume, panning, pitch,
     * cutoff, resonance, drive). This device is required by Renoise to build the modulation input
     * view.
     *
     * @param document The XML document
     * @param devicesElement The devices element to which to add the device
     * @param cutoff The base cutoff value (0..127)
     * @param resonance The base resonance value (0..127)
     */
    private static void createMixerDevice (final Document document, final Element devicesElement, final double cutoff, final double resonance)
    {
        final Element deviceElement = XMLUtils.addElement (document, devicesElement, RenoiseTag.MIXER_DEVICE);
        deviceElement.setAttribute (RenoiseTag.ATTR_TYPE, RenoiseTag.MIXER_DEVICE);

        addParameter (document, deviceElement, RenoiseTag.IS_ACTIVE, 1.0);
        addParameter (document, deviceElement, RenoiseTag.VOLUME, 1.0);
        addParameter (document, deviceElement, RenoiseTag.PANNING, 0.0);
        addParameter (document, deviceElement, RenoiseTag.PITCH, 0.0);
        XMLUtils.addTextElement (document, deviceElement, RenoiseTag.PITCH_MODULATION_RANGE, "12");
        addParameter (document, deviceElement, RenoiseTag.CUTOFF, cutoff);
        addParameter (document, deviceElement, RenoiseTag.RESONANCE, resonance);
        addParameter (document, deviceElement, RenoiseTag.DRIVE, 0.0);
    }


    /**
     * Create an AHDSR modulation device.
     *
     * @param document The XML document
     * @param devicesElement The devices element to which to add the device
     * @param target The modulation target (Volume, Cutoff, Pitch)
     * @param operator The modulation operator
     * @param bipolar True for a bipolar modulation
     * @param envelope The envelope to write
     */
    private static void createAhdsrDevice (final Document document, final Element devicesElement, final String target, final String operator, final boolean bipolar, final IEnvelope envelope)
    {
        final Element deviceElement = XMLUtils.addElement (document, devicesElement, RenoiseTag.AHDSR_DEVICE);
        deviceElement.setAttribute (RenoiseTag.ATTR_TYPE, RenoiseTag.AHDSR_DEVICE);

        addParameter (document, deviceElement, RenoiseTag.IS_ACTIVE, 1.0);
        XMLUtils.addTextElement (document, deviceElement, RenoiseTag.TARGET, target);
        XMLUtils.addTextElement (document, deviceElement, RenoiseTag.OPERATOR, operator);
        XMLUtils.addTextElement (document, deviceElement, RenoiseTag.BIPOLAR, Boolean.toString (bipolar));
        XMLUtils.addTextElement (document, deviceElement, RenoiseTag.TEMPO_SYNCED, "false");

        addParameter (document, deviceElement, RenoiseTag.ATTACK, RenoiseValueConverter.timeToRenoise (envelope.getAttackTime ()));
        addParameter (document, deviceElement, RenoiseTag.HOLD, RenoiseValueConverter.timeToRenoise (Math.max (0, envelope.getHoldTime ())));
        addParameter (document, deviceElement, RenoiseTag.DECAY, RenoiseValueConverter.timeToRenoise (envelope.getDecayTime ()));
        final double sustain = envelope.getSustainLevel ();
        addParameter (document, deviceElement, RenoiseTag.SUSTAIN, sustain < 0 ? 1.0 : sustain);
        addParameter (document, deviceElement, RenoiseTag.RELEASE, RenoiseValueConverter.timeToRenoise (envelope.getReleaseTime ()));
    }


    /**
     * Add a device parameter element (a {@code Value} plus {@code Visualization}).
     *
     * @param document The XML document
     * @param deviceElement The device element to which to add the parameter
     * @param name The name of the parameter element
     * @param value The value
     */
    private static void addParameter (final Document document, final Element deviceElement, final String name, final double value)
    {
        final Element parameterElement = XMLUtils.addElement (document, deviceElement, name);
        XMLUtils.addTextElement (document, parameterElement, RenoiseTag.VALUE, formatFloat (value));
        XMLUtils.addTextElement (document, parameterElement, RenoiseTag.VISUALIZATION, "Device only");
    }


    /** {@inheritDoc} */
    @Override
    protected boolean requiresRewrite (final DestinationAudioFormat destinationFormat)
    {
        // Always rewrite to produce FLAC encoded samples
        return true;
    }


    /** {@inheritDoc} */
    @Override
    protected void rewriteFile (final IMultisampleSource multisampleSource, final ISampleZone zone, final OutputStream outputStream, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        final ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
            return;

        // It is important to write to a file otherwise the FLAC header is broken! 32-bit float
        // samples are reduced to a FLAC compatible resolution by compressToFLAC.
        final Path tempFile = Files.createTempFile ("CWM-Renoise-", ".flac");
        try
        {
            ISampleData flacSource = sampleData;

            // Renoise has no loop cross-fade parameter. By default the loop is written exactly as it
            // is (faithful). Only if a cross-fade is requested - e.g. via the loop cross-fade
            // processing option - it is baked into the sample audio here.
            final ISampleLoop loop = getCrossfadeLoop (zone);
            if (loop != null)
            {
                final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData, DESTINATION_AUDIO_FORMAT);
                applyLoopCrossfade (waveFile, loop.getStart (), loop.getEnd (), loop.getCrossfadeInSamples ());
                flacSource = new WavFileSampleData (waveFile);
            }

            AudioFileUtils.compressToFLAC (flacSource, FLAC_TARGET_FORMAT, tempFile.toFile ());
            Files.copy (tempFile, outputStream);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }
        finally
        {
            Files.deleteIfExists (tempFile);
        }
    }


    /**
     * Get the (first) loop of a zone if a cross-fade should be baked into its audio. Only forward
     * loops with a cross-fade greater than zero are considered.
     *
     * @param zone The zone
     * @return The loop or null if no cross-fade should be applied
     */
    private static ISampleLoop getCrossfadeLoop (final ISampleZone zone)
    {
        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
            return null;
        final ISampleLoop loop = loops.get (0);
        if (loop.getType () == LoopType.FORWARDS && loop.getCrossfade () > 0 && loop.getStart () >= 0 && loop.getEnd () > loop.getStart ())
            return loop;
        return null;
    }


    /**
     * Bake a loop cross-fade into the audio of a forward loop. The cross-fade region at the end of
     * the loop is blended with the audio preceding the loop start so that the loop wraps seamlessly.
     *
     * @param waveFile The WAV file whose audio data is modified in place
     * @param loopStart The loop start frame
     * @param loopEnd The loop end frame (exclusive)
     * @param crossfadeSamples The requested cross-fade length in frames
     */
    private static void applyLoopCrossfade (final WaveFile waveFile, final int loopStart, final int loopEnd, final int crossfadeSamples)
    {
        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final DataChunk dataChunk = waveFile.getDataChunk ();
        if (formatChunk == null || dataChunk == null || crossfadeSamples <= 0)
            return;

        final int channels = formatChunk.getNumberOfChannels ();
        final int bytesPerFrame = formatChunk.calculateBytesPerSample ();
        final int bytesPerChannel = channels == 0 ? 0 : bytesPerFrame / channels;
        if (bytesPerChannel != 2 && bytesPerChannel != 3)
            return;

        final byte [] data = dataChunk.getData ();
        final int totalFrames = data.length / bytesPerFrame;
        final int end = Math.min (loopEnd, totalFrames);

        // The cross-fade can neither be longer than the loop nor reach before the start of the sample
        final int crossfade = Math.min (crossfadeSamples, Math.min (end - loopStart, loopStart));
        if (crossfade <= 0)
            return;

        for (int j = 0; j < crossfade; j++)
        {
            final int endFrame = end - crossfade + j;
            final int preFrame = loopStart - crossfade + j;
            final double mix = (j + 1.0) / crossfade;
            for (int c = 0; c < channels; c++)
            {
                final int endPos = endFrame * bytesPerFrame + c * bytesPerChannel;
                final int prePos = preFrame * bytesPerFrame + c * bytesPerChannel;
                final int blended = (int) Math.round (readSample (data, endPos, bytesPerChannel) * (1.0 - mix) + readSample (data, prePos, bytesPerChannel) * mix);
                writeSample (data, endPos, bytesPerChannel, blended);
            }
        }

        dataChunk.setData (data);
    }


    /**
     * Read a little-endian signed PCM sample.
     *
     * @param data The audio data
     * @param pos The byte position
     * @param bytesPerChannel The number of bytes per channel sample (2 or 3)
     * @return The signed sample value
     */
    private static int readSample (final byte [] data, final int pos, final int bytesPerChannel)
    {
        if (bytesPerChannel == 2)
            return (short) ((data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8));
        return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8) | (data[pos + 2] << 16);
    }


    /**
     * Write a little-endian signed PCM sample.
     *
     * @param data The audio data
     * @param pos The byte position
     * @param bytesPerChannel The number of bytes per channel sample (2 or 3)
     * @param value The sample value
     */
    private static void writeSample (final byte [] data, final int pos, final int bytesPerChannel, final int value)
    {
        data[pos] = (byte) (value & 0xFF);
        data[pos + 1] = (byte) ((value >> 8) & 0xFF);
        if (bytesPerChannel == 3)
            data[pos + 2] = (byte) ((value >> 16) & 0xFF);
    }


    /** {@inheritDoc} */
    @Override
    protected String createFileName (final int zoneIndex, final ISampleZone zone)
    {
        return SAMPLE_DATA_FOLDER + FORWARD_SLASH + this.sampleBaseName (zoneIndex, zone) + ".flac";
    }


    /**
     * Create the base name (without folder and extension) of a sample file following the Renoise
     * naming convention {@code SampleNN (name)}.
     *
     * @param zoneIndex The global index of the zone
     * @param zone The zone
     * @return The base name
     */
    private String sampleBaseName (final int zoneIndex, final ISampleZone zone)
    {
        return String.format (Locale.US, "Sample%0" + this.padWidth + "d (%s)", Integer.valueOf (zoneIndex), createSafeFilename (zone.getName ()));
    }


    /**
     * Calculate the zero-padding width of the sample index so that the alphabetically sorted file
     * names stay in play order.
     *
     * @param multisampleSource The multi-sample
     * @return The padding width (at least 2)
     */
    private static int calcPadWidth (final IMultisampleSource multisampleSource)
    {
        int count = 0;
        for (final IGroup group: multisampleSource.getGroups ())
            count += group.getSampleZones ().size ();
        return Math.max (2, Integer.toString (Math.max (0, count - 1)).length ());
    }


    /**
     * Get the Renoise loop mode for a loop.
     *
     * @param loop The loop (may be null)
     * @return The Renoise loop mode
     */
    private static String loopMode (final ISampleLoop loop)
    {
        if (loop == null)
            return RenoiseTag.LOOP_OFF;
        return switch (loop.getType ())
        {
            case BACKWARDS -> RenoiseTag.LOOP_BACKWARD;
            case ALTERNATING -> RenoiseTag.LOOP_PING_PONG;
            default -> RenoiseTag.LOOP_FORWARD;
        };
    }


    /**
     * Format a floating point value the way Renoise does (a dot as the decimal separator).
     *
     * @param value The value
     * @return The formatted value
     */
    private static String formatFloat (final double value)
    {
        return String.format (Locale.US, "%.9f", Double.valueOf (value));
    }
}
