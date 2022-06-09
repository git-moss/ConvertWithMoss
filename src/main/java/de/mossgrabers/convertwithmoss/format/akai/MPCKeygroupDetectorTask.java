// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultVelocityLayer;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


/**
 * Detects recursively MPC Keygroup files in folders. Files must end with <i>.xpm</i>.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class MPCKeygroupDetectorTask extends AbstractDetectorTask
{
    private static final String BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";

    private static final double MINUS_12_DB       = 0.353000;
    private static final double PLUS_6_DB         = 1.0;
    private static final double VALUE_RANGE       = PLUS_6_DB - MINUS_12_DB;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    protected MPCKeygroupDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ".xpm");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final String content = this.loadTextFile (file).trim ();
            return this.parseMetadataFile (file, content);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param file The file
     * @param content The XML content to parse
     * @return The result
     */
    private List<IMultisampleSource> parseMetadataFile (final File file, final String content)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseDescription (file, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (BAD_METADATA_FILE, ex);
        }
        catch (final FileNotFoundException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Process the multisample metadata file and the related wave files.
     *
     * @param file The file which contained the XML document
     * @param document The metadata XML document
     * @return The parsed multisample source
     * @throws FileNotFoundException The WAV file could not be found
     */
    private List<IMultisampleSource> parseDescription (final File file, final Document document) throws FileNotFoundException
    {
        final Element top = document.getDocumentElement ();

        if (!MPCKeygroupTag.ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final Element programElement = XMLUtils.getChildElementByName (top, MPCKeygroupTag.ROOT_PROGRAM);
        if (programElement == null)
        {
            this.notifier.logError (BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        String type = programElement.getAttribute (MPCKeygroupTag.PROGRAM_TYPE);
        if (type == null)
            type = MPCKeygroupTag.TYPE_KEYGROUP;
        final boolean isKeygroup = type.equals (MPCKeygroupTag.TYPE_KEYGROUP);
        final boolean isDrum = type.equals (MPCKeygroupTag.TYPE_DRUM);
        if (!isKeygroup && !isDrum)
        {
            this.notifier.logError ("IDS_MPC_UNSUPPORTED_TYPE", type);
            return Collections.emptyList ();
        }

        final Element programNameElement = XMLUtils.getChildElementByName (programElement, MPCKeygroupTag.PROGRAM_NAME);
        final String name = programNameElement == null ? FileUtils.getNameWithoutType (file) : programNameElement.getTextContent ();
        final String n = this.metadata.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = createPathParts (file.getParentFile (), this.sourceFolder, n);
        final MultisampleSource multisampleSource = new MultisampleSource (file, parts, name, this.subtractPaths (this.sourceFolder, file));

        // Use same guessing on the filename...
        multisampleSource.setCreator (TagDetector.detect (parts, this.metadata.getCreatorTags (), this.metadata.getCreatorName ()));
        multisampleSource.setCategory (isDrum ? "Drums" : TagDetector.detectCategory (parts));
        multisampleSource.setKeywords (TagDetector.detectKeywords (parts));

        final Element instrumentsElement = XMLUtils.getChildElementByName (programElement, MPCKeygroupTag.PROGRAM_INSTRUMENTS);
        if (instrumentsElement == null)
            return Collections.emptyList ();

        final int numKeygroups = XMLUtils.getChildElementIntegerContent (programElement, MPCKeygroupTag.PROGRAM_NUM_KEYGROUPS, 128);

        final Element [] instrumentElements = XMLUtils.getChildElementsByName (instrumentsElement, MPCKeygroupTag.INSTRUMENTS_INSTRUMENT);
        final List<IVelocityLayer> velocityLayers = this.parseVelocityLayers (file.getParentFile (), numKeygroups, instrumentElements, isDrum);

        if (isDrum)
            this.applyPadNoteMap (programElement, velocityLayers);

        multisampleSource.setVelocityLayers (velocityLayers);

        final double pitchBendRange = XMLUtils.getChildElementDoubleContent (programElement, MPCKeygroupTag.PROGRAM_PITCHBEND_RANGE, 0);
        if (pitchBendRange != 0)
        {
            final int pitchBend = (int) Math.round (pitchBendRange * 1200.0);
            for (final IVelocityLayer layer: velocityLayers)
            {
                for (final ISampleMetadata sample: layer.getSampleMetadata ())
                {
                    sample.setBendUp (pitchBend);
                    sample.setBendDown (-pitchBend);
                }
            }
        }

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parses all velocity layers (= regions in SFZ).
     *
     * @param basePath The path where the XPM file is located, this is the base path for samples
     * @param numKeygroups The number of valid keygroups
     * @param instrumentsElements The instrument elements
     * @param isDrum True, if it is a drum type (not a keygroup)
     * @return The parsed velocity layers
     * @throws FileNotFoundException The WAV file could not be found
     */
    private List<IVelocityLayer> parseVelocityLayers (final File basePath, final int numKeygroups, final Element [] instrumentsElements, final boolean isDrum) throws FileNotFoundException
    {
        final List<DefaultSampleMetadata> samples = new ArrayList<> ();

        for (final Element instrumentElement: instrumentsElements)
        {
            final int instrumentNumber = XMLUtils.getIntegerAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_NUMBER, 0);
            if (instrumentNumber > numKeygroups)
                continue;

            int keyLow = instrumentNumber;
            int keyHigh = instrumentNumber;
            if (!isDrum)
            {
                keyLow = XMLUtils.getChildElementIntegerContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_LOW_NOTE, 0);
                keyHigh = XMLUtils.getChildElementIntegerContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_HIGH_NOTE, 0);
            }

            PlayLogic zonePlay;
            try
            {
                final String zonePlayStr = XMLUtils.getChildElementContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_ZONE_PLAY);
                zonePlay = zonePlayStr == null || zonePlayStr.isBlank () ? PlayLogic.ALWAYS : ZonePlay.values ()[Integer.parseInt (zonePlayStr)].to ();
            }
            catch (final RuntimeException ex)
            {
                zonePlay = PlayLogic.ALWAYS;
                this.notifier.logError ("IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY");
            }

            final String ignoreBaseNoteStr = XMLUtils.getChildElementContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_IGNORE_BASE_NOTE);
            final boolean ignoreBaseNote = ignoreBaseNoteStr != null && MPCKeygroupTag.TRUE.equals (ignoreBaseNoteStr);

            final String oneShotStr = XMLUtils.getChildElementContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_ONE_SHOT);
            final boolean isOneShot = oneShotStr == null || MPCKeygroupTag.TRUE.equalsIgnoreCase (oneShotStr);

            final IFilter filter = parseFilter (instrumentElement);

            final double volumeAttack = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_ATTACK, 0, 30, 0);
            final double volumeHold = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_HOLD, 0, 30, 0);
            final double volumeDecay = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_DECAY, 0, 30, 0);
            final double volumeSustain = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_SUSTAIN, 0, 1, 1);
            final double volumeRelease = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_RELEASE, 0, 30, 0.63);

            final double pitchAttack = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ATTACK, 0, 30, 0);
            final double pitchHold = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_HOLD, 0, 30, 0);
            final double pitchDecay = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_DECAY, 0, 30, 0);
            final double pitchSustain = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_SUSTAIN, 0, 1, 1);
            final double pitchRelease = getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_RELEASE, 0, 30, 0.63);

            final Element layersElement = XMLUtils.getChildElementByName (instrumentElement, MPCKeygroupTag.INSTRUMENT_LAYERS);
            if (layersElement != null)
            {
                final Element [] layerElements = XMLUtils.getChildElementsByName (layersElement, MPCKeygroupTag.LAYERS_LAYER);
                for (final Element layerElement: layerElements)
                {
                    final int velStart = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_VEL_START, 0);
                    final int velEnd = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_VEL_END, 0);

                    final DefaultSampleMetadata sampleMetadata = this.parseSampleData (layerElement, basePath, keyLow, keyHigh, velStart, velEnd, zonePlay, ignoreBaseNote);
                    if (sampleMetadata == null)
                        continue;
                    samples.add (sampleMetadata);

                    final IEnvelope amplitudeEnvelope = sampleMetadata.getAmplitudeEnvelope ();
                    amplitudeEnvelope.setAttack (volumeAttack);
                    amplitudeEnvelope.setHold (volumeHold);
                    amplitudeEnvelope.setDecay (volumeDecay);
                    amplitudeEnvelope.setSustain (volumeSustain);
                    amplitudeEnvelope.setRelease (volumeRelease);

                    final double pitchEnvAmount = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ENV_AMOUNT, 0.5);
                    if (pitchEnvAmount != 0.5)
                    {
                        final int cents = (int) Math.min (3600, Math.max (-3600, Math.round ((pitchEnvAmount - 0.5) * 2.0 * 3600.0)));
                        sampleMetadata.setPitchEnvelopeDepth (cents);

                        final IEnvelope pitchEnvelope = sampleMetadata.getPitchEnvelope ();
                        pitchEnvelope.setAttack (pitchAttack);
                        pitchEnvelope.setHold (pitchHold);
                        pitchEnvelope.setDecay (pitchDecay);
                        pitchEnvelope.setSustain (pitchSustain);
                        pitchEnvelope.setRelease (pitchRelease);
                    }

                    // No loop if it is a one-shot
                    if (!isOneShot)
                        parseLoop (layerElement, sampleMetadata);

                    sampleMetadata.setFilter (filter);

                    this.readMissingData (isDrum, isOneShot, sampleMetadata);
                }
            }
        }

        return groupIntoLayers (samples);
    }


    /**
     * Parse the filter settings from the instrument element.
     *
     * @param instrumentElement The instrument element
     * @return The filter or null
     */
    private static IFilter parseFilter (final Element instrumentElement)
    {
        final int filterID = XMLUtils.getChildElementIntegerContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_TYPE, -1);
        if (filterID <= 0)
            return null;
        final double cutoff = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_CUTOFF, 1);
        final double resonance = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_RESONANCE, 0);
        final MPCFilter filter = new MPCFilter (filterID, cutoff, resonance);
        if (filter.getType () == null)
            return null;

        final double filterAmount = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_ENV_AMOUNT, 0);
        if (filterAmount > 0)
        {
            filter.setEnvelopeDepth ((int) Math.round (filterAmount * IFilter.MAX_ENVELOPE_DEPTH));

            final IEnvelope filterEnvelope = filter.getEnvelope ();
            filterEnvelope.setAttack (getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_ATTACK, 0, 30, 0));
            filterEnvelope.setHold (getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_HOLD, 0, 30, 0));
            filterEnvelope.setDecay (getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_DECAY, 0, 30, 0));
            filterEnvelope.setSustain (getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_SUSTAIN, 0, 1, 1));
            filterEnvelope.setRelease (getEnvelopeAttribute (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_RELEASE, 0, 30, 0.63));
        }
        return filter;
    }


    /**
     * Parse the loop settings from the layer element.
     *
     * @param layerElement The layer element
     * @param basePath The path where the XPM file is located, this is the base path for samples
     * @param keyLow The lower key of the sample
     * @param keyHigh The upper key of the sample
     * @param velStart The lower velocity of the sample
     * @param velEnd The upper velocity of the sample
     * @param zonePlay The zone play
     * @param ignoreBaseNote The ignore base note setting
     * @return The sample metadata or null
     */
    private DefaultSampleMetadata parseSampleData (final Element layerElement, final File basePath, final int keyLow, final int keyHigh, final int velStart, final int velEnd, final PlayLogic zonePlay, final boolean ignoreBaseNote)
    {
        final Element sampleNameElement = XMLUtils.getChildElementByName (layerElement, MPCKeygroupTag.LAYER_SAMPLE_NAME);
        if (sampleNameElement == null)
            return null;

        // Use this method to preserve whitespace since some filenames end with a space!
        final String sampleName = sampleNameElement.getTextContent ();
        if (sampleName.isBlank ())
            return null;

        final DefaultSampleMetadata sampleMetadata = new DefaultSampleMetadata (sampleName + ".wav", new File (basePath, sampleName + ".WAV"));

        sampleMetadata.setKeyLow (keyLow);
        sampleMetadata.setKeyHigh (keyHigh);
        try
        {
            sampleMetadata.setPlayLogic (zonePlay);
        }
        catch (final RuntimeException ex)
        {
            this.notifier.logError ("IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY");
        }

        sampleMetadata.setVelocityLow (velStart);
        sampleMetadata.setVelocityHigh (velEnd);

        final String activeStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_ACTIVE);
        if (activeStr != null && !activeStr.equalsIgnoreCase (MPCKeygroupTag.TRUE))
            return sampleMetadata;

        final String volumeStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_VOLUME);
        if (volumeStr != null && !volumeStr.isBlank ())
            sampleMetadata.setGain (convertGain (Double.parseDouble (volumeStr)));

        final String pitchStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_PITCH);
        if (pitchStr != null && !pitchStr.isBlank ())
            sampleMetadata.setTune (Double.parseDouble (pitchStr));
        else
        {
            double pitch = XMLUtils.getChildElementDoubleContent (layerElement, MPCKeygroupTag.LAYER_COARSE_TUNE, 0);
            pitch += XMLUtils.getChildElementDoubleContent (layerElement, MPCKeygroupTag.LAYER_FINE_TUNE, 0);
            sampleMetadata.setTune (pitch);
        }

        // The root note is strangely one more then the lower upper keys!
        final String rootNoteStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_ROOT_NOTE);
        if (rootNoteStr != null && !rootNoteStr.isBlank ())
            sampleMetadata.setKeyRoot (Integer.parseInt (rootNoteStr) - 1);

        final String keyTrackStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_KEY_TRACK);
        if (keyTrackStr != null && !keyTrackStr.isBlank () && ignoreBaseNote)
            sampleMetadata.setKeyTracking (MPCKeygroupTag.TRUE.equals (keyTrackStr) ? 1.0 : 0.0);

        final String sliceStartStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_SLICE_START);
        if (sliceStartStr != null && !sliceStartStr.isBlank ())
            sampleMetadata.setStart (Integer.parseInt (sliceStartStr));
        final String sliceEndStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_SLICE_END);
        if (sliceEndStr != null && !sliceEndStr.isBlank ())
            sampleMetadata.setStop (Integer.parseInt (sliceEndStr));

        return sampleMetadata;
    }


    /**
     * Read missing metadata from the WAV file if necessary.
     *
     * @param isDrum True if it is a Drum patch
     * @param isOneShot If it is a one shot there is no need to read loop data
     * @param sampleMetadata Where to store the data
     * @throws FileNotFoundException The WAV file does not exist
     */
    private void readMissingData (final boolean isDrum, final boolean isOneShot, final DefaultSampleMetadata sampleMetadata) throws FileNotFoundException
    {
        if (sampleMetadata.getStop () <= 0 || sampleMetadata.getKeyRoot () < 0 || !isOneShot && sampleMetadata.getLoops ().isEmpty ())
        {
            try
            {
                sampleMetadata.addMissingInfoFromWaveFile (!isDrum, !isDrum && !isOneShot);
            }
            catch (final FileNotFoundException ex)
            {
                throw ex;
            }
            catch (final IOException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex.getMessage ());
            }
        }
    }


    /**
     * Parse the loop settings from the layer element.
     *
     * @param layerElement THe layer element
     * @param sampleMetadata Where to store the data
     */
    private static void parseLoop (final Element layerElement, final DefaultSampleMetadata sampleMetadata)
    {
        // There might be no loop, forward or reverse
        final int sliceLoop = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP, -1);
        if (sliceLoop <= 0)
            return;

        // There is a loop, is it reversed?
        if (sliceLoop == 3)
            sampleMetadata.setReversed (true);

        final DefaultSampleLoop sampleLoop = new DefaultSampleLoop ();
        final int sliceLoopStart = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP_START, -1);
        if (sliceLoopStart >= 0)
            sampleLoop.setStart (sliceLoopStart);
        sampleLoop.setEnd (sampleMetadata.getStop ());
        sampleLoop.setCrossfade (XMLUtils.getChildElementDoubleContent (layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP_CROSSFADE, 0));

        sampleMetadata.getLoops ().add (sampleLoop);
    }


    /**
     * Create velocity layers from the velocity values of the individual samples.
     *
     * @param samples The samples to group into velocity layers
     * @return The layers
     */
    private static List<IVelocityLayer> groupIntoLayers (final List<DefaultSampleMetadata> samples)
    {
        final Map<String, IVelocityLayer> layerMap = new HashMap<> ();

        int count = 1;

        for (final DefaultSampleMetadata sampleMetadata: samples)
        {
            final String id = sampleMetadata.getVelocityLow () + "-" + sampleMetadata.getVelocityHigh ();
            final IVelocityLayer velocityLayer = layerMap.computeIfAbsent (id, key -> new DefaultVelocityLayer ());

            if (velocityLayer.getName () == null)
            {
                velocityLayer.setName ("Layer " + count);
                count++;
            }

            velocityLayer.addSampleMetadata (sampleMetadata);
        }

        return new ArrayList<> (layerMap.values ());
    }


    /**
     * Update the low/high key and root note if not set with the note found in the pad note mapping.
     *
     * @param programElement The program element which contains the pad note map
     * @param velocityLayers The velocity layers which contain the samples to be updated
     */
    private void applyPadNoteMap (final Element programElement, final List<IVelocityLayer> velocityLayers)
    {
        final Element padNoteMapElement = XMLUtils.getChildElementByName (programElement, MPCKeygroupTag.PROGRAM_PAD_NOTE_MAP);
        if (padNoteMapElement == null)
            return;

        final Map<Integer, Integer> padNoteMap = new HashMap<> (128);
        for (final Element padNoteElement: XMLUtils.getChildElementsByName (padNoteMapElement, MPCKeygroupTag.PAD_NOTE_MAP_PAD_NOTE))
        {
            final int padNumber = XMLUtils.getIntegerAttribute (padNoteElement, MPCKeygroupTag.PAD_NOTE_NUMBER, 0);
            if (padNumber < 1 || padNumber > 128)
            {
                this.notifier.logError ("IDS_MPC_PAD_OUT_OF_RANGE", Integer.toString (padNumber));
                return;
            }

            final int note = XMLUtils.getChildElementIntegerContent (padNoteElement, MPCKeygroupTag.PAD_NOTE_NOTE, -1);
            if (note >= 0)
                padNoteMap.put (Integer.valueOf (padNumber), Integer.valueOf (note));
        }

        for (final IVelocityLayer layer: velocityLayers)
        {
            for (final ISampleMetadata sampleMetadata: layer.getSampleMetadata ())
            {
                final Integer noteNumber = padNoteMap.get (Integer.valueOf (sampleMetadata.getKeyLow ()));
                if (noteNumber == null)
                {
                    this.notifier.logError ("IDS_MPC_PAD_OUT_OF_RANGE", "null");
                    return;
                }

                final int nn = noteNumber.intValue ();
                sampleMetadata.setKeyLow (nn);
                sampleMetadata.setKeyHigh (nn);
                if (sampleMetadata.getKeyRoot () < 0)
                    sampleMetadata.setKeyRoot (nn);
            }
        }
    }


    /**
     * Convert a volume in the range of [0..1] which represent [-Inf..6dB] to a range of
     * [-12dB..12dB].
     *
     * @param volume The volume to convert
     * @return The converted volume DB
     */
    private static double convertGain (final double volume)
    {
        final double result = volume - MINUS_12_DB;
        return result * 18.0 / VALUE_RANGE - 12;
    }


    private static double getEnvelopeAttribute (final Element element, final String attribute, final double minimum, final double maximum, final double defaultValue)
    {
        final double value = XMLUtils.getChildElementDoubleContent (element, attribute, defaultValue);
        return value < 0 ? defaultValue : denormalizeValue (value, minimum, maximum);
    }
}
