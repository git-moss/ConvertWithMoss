// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively MPC Keygroup files in folders. Files must end with <i>.xpm</i>.
 *
 * @author Jürgen Moßgraber
 */
public class MPCKeygroupDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MPCKeygroupDetector (final INotifier notifier)
    {
        super ("Akai MPC Keygroup", "MPC", notifier, new MetadataSettingsUI ("MPC"), ".xpm");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
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
     * Process the multi-sample metadata file and the related wave files.
     *
     * @param file The file which contained the XML document
     * @param document The metadata XML document
     * @return The parsed multi-sample source
     * @throws FileNotFoundException The WAV file could not be found
     */
    private List<IMultisampleSource> parseDescription (final File file, final Document document) throws FileNotFoundException
    {
        final Optional<Element> programElementOpt = this.getProgramElement (document);
        if (programElementOpt.isEmpty ())
            return Collections.emptyList ();

        final Element programElement = programElementOpt.get ();
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
        final String n = this.settingsConfiguration.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, n);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

        final Element instrumentsElement = XMLUtils.getChildElementByName (programElement, MPCKeygroupTag.PROGRAM_INSTRUMENTS);
        if (instrumentsElement == null)
            return Collections.emptyList ();
        final List<Element> instrumentElements = XMLUtils.getChildElementsByName (instrumentsElement, MPCKeygroupTag.INSTRUMENTS_INSTRUMENT);
        final int numKeygroups = XMLUtils.getChildElementIntegerContent (programElement, MPCKeygroupTag.PROGRAM_NUM_KEYGROUPS, 128);
        final List<IGroup> groups = this.parseGroups (file.getParentFile (), numKeygroups, instrumentElements, isDrum);

        if (isDrum)
            this.applyPadNoteMap (programElement, groups);

        multisampleSource.setGroups (groups);
        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (groups), parts, isDrum ? "Drums" : null);

        applyPitchbend (programElement, groups);

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Get the top program element and issues some checks.
     * 
     * @param document The XML document from which to get the program element
     * @return The program element if present
     */
    private Optional<Element> getProgramElement (final Document document)
    {
        final Element top = document.getDocumentElement ();
        if (MPCKeygroupTag.ROOT.equals (top.getNodeName ()))
        {
            final Element versionElement = XMLUtils.getChildElementByName (top, MPCKeygroupTag.ROOT_VERSION);
            if (versionElement != null)
            {
                final String fileVersion = XMLUtils.getChildElementContent (versionElement, MPCKeygroupTag.VERSION_FILE_VERSION);
                final String platform = XMLUtils.getChildElementContent (versionElement, MPCKeygroupTag.VERSION_PLATFORM);
                this.notifier.log ("IDS_MPC_VERSION", fileVersion, platform);

                final Element programElement = XMLUtils.getChildElementByName (top, MPCKeygroupTag.ROOT_PROGRAM);
                if (programElement != null)
                    return Optional.of (programElement);
            }
        }

        this.notifier.logError (BAD_METADATA_FILE);
        return Optional.empty ();
    }


    /**
     * Parses all groups (= regions in SFZ).
     *
     * @param basePath The path where the XPM file is located, this is the base path for samples
     * @param numKeygroups The number of valid key-groups
     * @param instrumentElements The instrument elements
     * @param isDrum True, if it is a drum type (not a key-group)
     * @return The parsed groups
     * @throws FileNotFoundException The WAV file could not be found
     */
    private List<IGroup> parseGroups (final File basePath, final int numKeygroups, final List<Element> instrumentElements, final boolean isDrum) throws FileNotFoundException
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final Element instrumentElement: instrumentElements)
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

            boolean isOneShot = false;
            final int triggerMode = XMLUtils.getChildElementIntegerContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_TRIGGER_MODE, -1);
            TriggerType triggerType = TriggerType.ATTACK;
            if (triggerMode < 0)
            {
                final String oneShotStr = XMLUtils.getChildElementContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_ONE_SHOT);
                isOneShot = oneShotStr == null || MPCKeygroupTag.TRUE.equalsIgnoreCase (oneShotStr);
            }
            else
            {
                switch (triggerMode)
                {
                    // One-Shot
                    case 0:
                        isOneShot = true;
                        break;
                    case 1:
                        triggerType = TriggerType.RELEASE;
                        break;
                    default:
                    case 2:
                        // Attack
                        break;
                }
            }

            this.readZones (basePath, isDrum, zones, instrumentElement, keyLow, keyHigh, zonePlay, isOneShot, triggerType);
        }

        return groupIntoLayers (zones);
    }


    private void readZones (final File basePath, final boolean isDrum, final List<ISampleZone> zones, final Element instrumentElement, int keyLow, int keyHigh, PlayLogic zonePlay, boolean isOneShot, TriggerType triggerType) throws FileNotFoundException
    {
        final String ignoreBaseNoteStr = XMLUtils.getChildElementContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_IGNORE_BASE_NOTE);
        final boolean ignoreBaseNote = ignoreBaseNoteStr != null && MPCKeygroupTag.TRUE.equals (ignoreBaseNoteStr);
        final IFilter filter = parseFilter (instrumentElement);
        final IEnvelope volumeEnvelope = parseEnvelope (instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_ATTACK, MPCKeygroupTag.INSTRUMENT_VOLUME_HOLD, MPCKeygroupTag.INSTRUMENT_VOLUME_DECAY, MPCKeygroupTag.INSTRUMENT_VOLUME_SUSTAIN, MPCKeygroupTag.INSTRUMENT_VOLUME_RELEASE, MPCKeygroupTag.INSTRUMENT_VOLUME_ATTACK_CURVE, MPCKeygroupTag.INSTRUMENT_VOLUME_DECAY_CURVE, MPCKeygroupTag.INSTRUMENT_VOLUME_RELEASE_CURVE);
        final IEnvelope pitchEnvelope = parseEnvelope (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ATTACK, MPCKeygroupTag.INSTRUMENT_PITCH_HOLD, MPCKeygroupTag.INSTRUMENT_PITCH_DECAY, MPCKeygroupTag.INSTRUMENT_PITCH_SUSTAIN, MPCKeygroupTag.INSTRUMENT_PITCH_RELEASE, MPCKeygroupTag.INSTRUMENT_PITCH_ATTACK_CURVE, MPCKeygroupTag.INSTRUMENT_PITCH_DECAY_CURVE, MPCKeygroupTag.INSTRUMENT_PITCH_RELEASE_CURVE);

        final Element layersElement = XMLUtils.getChildElementByName (instrumentElement, MPCKeygroupTag.INSTRUMENT_LAYERS);
        if (layersElement == null)
            return;

        for (final Element layerElement: XMLUtils.getChildElementsByName (layersElement, MPCKeygroupTag.LAYERS_LAYER))
        {
            final int velStart = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_VEL_START, 0);
            final int velEnd = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_VEL_END, 0);

            final ISampleZone zone = this.parseSampleZone (layerElement, basePath, keyLow, keyHigh, velStart, velEnd, zonePlay, ignoreBaseNote, triggerType);
            if (zone == null)
                continue;
            zones.add (zone);

            /////////////////////////////////////////////////////////////
            // Amplitude

            final IEnvelopeModulator amplitudeModulator = zone.getAmplitudeEnvelopeModulator ();
            amplitudeModulator.setDepth (1.0);
            amplitudeModulator.getSource ().set (volumeEnvelope);

            final double ampVelocityAmount = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_VELOCITY_TO_AMP_AMOUNT, 0);
            if (ampVelocityAmount > 0)
                zone.getAmplitudeVelocityModulator ().setDepth (ampVelocityAmount);

            /////////////////////////////////////////////////////////////
            // Pitch

            final double pitchEnvAmount = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ENV_AMOUNT, 0.5);
            if (pitchEnvAmount != 0.5)
            {
                final IEnvelopeModulator pitchModulator = zone.getPitchModulator ();
                pitchModulator.setDepth ((pitchEnvAmount - 0.5) * 2.0);
                pitchModulator.getSource ().set (pitchEnvelope);
            }

            // No loop if it is a one-shot
            if (!isOneShot)
                parseLoop (layerElement, zone);

            zone.setFilter (filter);

            this.readMissingData (isDrum, zone, isOneShot);
        }
    }


    private static void applyPitchbend (final Element programElement, final List<IGroup> groups)
    {
        final double pitchBendRange = XMLUtils.getChildElementDoubleContent (programElement, MPCKeygroupTag.PROGRAM_PITCHBEND_RANGE, 0);
        if (pitchBendRange == 0)
            return;

        final int pitchBend = (int) Math.round (pitchBendRange * 1200.0);
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setBendUp (pitchBend);
                zone.setBendDown (-pitchBend);
            }
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
            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            cutoffModulator.setDepth (filterAmount);
            cutoffModulator.getSource ().set (parseEnvelope (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_ATTACK, MPCKeygroupTag.INSTRUMENT_FILTER_HOLD, MPCKeygroupTag.INSTRUMENT_FILTER_DECAY, MPCKeygroupTag.INSTRUMENT_FILTER_SUSTAIN, MPCKeygroupTag.INSTRUMENT_FILTER_RELEASE, MPCKeygroupTag.INSTRUMENT_FILTER_ATTACK_CURVE, MPCKeygroupTag.INSTRUMENT_FILTER_DECAY_CURVE, MPCKeygroupTag.INSTRUMENT_FILTER_RELEASE_CURVE));
        }

        final double filterCutoffVelocityAmount = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_VELOCITY_TO_FILTER_AMOUNT, 0);
        if (filterCutoffVelocityAmount > 0)
            filter.getCutoffVelocityModulator ().setDepth (filterCutoffVelocityAmount);

        return filter;
    }


    /**
     * Parse the parameters of a sample zone from the layer element.
     *
     * @param layerElement The layer element
     * @param basePath The path where the XPM file is located, this is the base path for samples
     * @param keyLow The lower key of the sample
     * @param keyHigh The upper key of the sample
     * @param velStart The lower velocity of the sample
     * @param velEnd The upper velocity of the sample
     * @param zonePlay The zone play
     * @param ignoreBaseNote The ignore base note setting
     * @param triggerType The trigger type
     * @return The sample metadata or null
     */
    private ISampleZone parseSampleZone (final Element layerElement, final File basePath, final int keyLow, final int keyHigh, final int velStart, final int velEnd, final PlayLogic zonePlay, final boolean ignoreBaseNote, final TriggerType triggerType)
    {
        final Element sampleNameElement = XMLUtils.getChildElementByName (layerElement, MPCKeygroupTag.LAYER_SAMPLE_NAME);
        if (sampleNameElement == null)
            return null;

        // Use this method to preserve whitespace since some filenames end with a space!
        final String sampleName = sampleNameElement.getTextContent ();
        if (sampleName.isBlank ())
            return null;

        final ISampleData sampleData;
        try
        {
            sampleData = new WavFileSampleData (new File (basePath, sampleName + ".WAV"));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY");
            return null;
        }
        final ISampleZone zone = new DefaultSampleZone (sampleName, sampleData);

        zone.setKeyLow (keyLow);
        zone.setKeyHigh (keyHigh);
        try
        {
            zone.setPlayLogic (zonePlay);
        }
        catch (final RuntimeException ex)
        {
            this.notifier.logError ("IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY");
        }
        if (triggerType != TriggerType.ATTACK)
            zone.setTrigger (triggerType);

        zone.setVelocityLow (velStart);
        zone.setVelocityHigh (velEnd);

        final String activeStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_ACTIVE);
        if (activeStr != null && !activeStr.equalsIgnoreCase (MPCKeygroupTag.TRUE))
            return zone;

        final String volumeStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_VOLUME);
        if (volumeStr != null && !volumeStr.isBlank ())
            zone.setGain (convertGain (Double.parseDouble (volumeStr)));

        final String panStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_PAN);
        if (panStr != null && !panStr.isBlank ())
            zone.setPanning (Math.clamp (Double.parseDouble (panStr) * 2.0d - 1.0d, -1.0d, 1.0d));

        final String pitchStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_PITCH);
        if (pitchStr != null && !pitchStr.isBlank ())
            zone.setTuning (Double.parseDouble (pitchStr));
        else
        {
            double pitch = XMLUtils.getChildElementDoubleContent (layerElement, MPCKeygroupTag.LAYER_COARSE_TUNE, 0);
            pitch += XMLUtils.getChildElementDoubleContent (layerElement, MPCKeygroupTag.LAYER_FINE_TUNE, 0);
            zone.setTuning (pitch);
        }

        // The root note is strangely one more then the lower upper keys!
        final String rootNoteStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_ROOT_NOTE);
        if (rootNoteStr != null && !rootNoteStr.isBlank ())
            zone.setKeyRoot (Integer.parseInt (rootNoteStr) - 1);

        final String keyTrackStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_KEY_TRACK);
        if (keyTrackStr != null && !keyTrackStr.isBlank () && ignoreBaseNote)
            zone.setKeyTracking (MPCKeygroupTag.TRUE.equals (keyTrackStr) ? 1.0 : 0.0);

        // Play-back start/end
        final String sliceStartStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_SLICE_START);
        if (sliceStartStr != null && !sliceStartStr.isBlank ())
            zone.setStart (Integer.parseInt (sliceStartStr));
        final String sliceEndStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_SLICE_END);
        if (sliceEndStr != null && !sliceEndStr.isBlank ())
            zone.setStop (Integer.parseInt (sliceEndStr));

        return zone;
    }


    /**
     * Read missing metadata from the WAV file if necessary.
     *
     * @param isDrum True if it is a Drum patch
     * @param zone Where to store the data
     * @param isOneShot True if it is a one-shot
     * @throws FileNotFoundException The WAV file does not exist
     */
    private void readMissingData (final boolean isDrum, final ISampleZone zone, boolean isOneShot) throws FileNotFoundException
    {
        final boolean needsUpdate = zone.getStop () > 0;
        final boolean needsRootKey = !isDrum && zone.getKeyRoot () >= 0;
        final boolean needsLoop = !isOneShot && zone.getLoops ().isEmpty ();

        try
        {
            if (needsUpdate || needsRootKey || needsLoop)
                zone.getSampleData ().addZoneData (zone, needsRootKey, needsLoop);
        }
        catch (final FileNotFoundException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex);
        }
    }


    /**
     * Parse the loop settings from the layer element.
     *
     * @param layerElement THe layer element
     * @param zone Where to store the data
     */
    private static void parseLoop (final Element layerElement, final ISampleZone zone)
    {
        // There might be no loop, forward or reverse
        final int sliceLoop = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP, -1);
        if (sliceLoop <= 0)
            return;

        // There is a loop, is it reversed?
        if (sliceLoop == 3)
            zone.setReversed (true);

        final DefaultSampleLoop sampleLoop = new DefaultSampleLoop ();
        final int sliceLoopStart = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP_START, -1);
        if (sliceLoopStart >= 0)
            sampleLoop.setStart (sliceLoopStart);
        final int stop = zone.getStop ();
        if (stop <= 0)
            return;
        sampleLoop.setEnd (stop);
        final int loopCrossfade = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP_CROSSFADE, 0);
        sampleLoop.setCrossfade (loopCrossfade / (double) sampleLoop.getLength ());

        zone.getLoops ().add (sampleLoop);
    }


    /**
     * Create groups from the velocity values of the individual samples.
     *
     * @param zones The sample zones to group into groups
     * @return The layers
     */
    private static List<IGroup> groupIntoLayers (final List<ISampleZone> zones)
    {
        final Map<String, IGroup> layerMap = new HashMap<> ();

        int count = 1;

        for (final ISampleZone zone: zones)
        {
            final String id = zone.getVelocityLow () + "-" + zone.getVelocityHigh ();
            final IGroup group = layerMap.computeIfAbsent (id, _ -> new DefaultGroup ());

            if (group.getName () == null)
            {
                group.setName ("Layer " + count);
                count++;
            }

            group.addSampleZone (zone);
        }

        return new ArrayList<> (layerMap.values ());
    }


    /**
     * Update the low/high key and root note if not set with the note found in the pad note mapping.
     *
     * @param programElement The program element which contains the pad note map
     * @param groups The groups which contain the samples to be updated
     */
    private void applyPadNoteMap (final Element programElement, final List<IGroup> groups)
    {
        final Element padNoteMapElement = XMLUtils.getChildElementByName (programElement, MPCKeygroupTag.PROGRAM_PAD_NOTE_MAP);
        if (padNoteMapElement == null)
            return;

        final Map<Integer, Integer> padNoteMap = new HashMap<> (128);
        for (final Element padNoteElement: XMLUtils.getChildElementsByName (padNoteMapElement, MPCKeygroupTag.PAD_NOTE_MAP_PAD_NOTE, false))
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

        for (final IGroup layer: groups)
            for (final ISampleZone zone: layer.getSampleZones ())
            {
                final Integer noteNumber = padNoteMap.get (Integer.valueOf (zone.getKeyLow ()));
                if (noteNumber == null)
                {
                    this.notifier.logError ("IDS_MPC_PAD_OUT_OF_RANGE", "null");
                    return;
                }

                final int nn = noteNumber.intValue ();
                zone.setKeyLow (nn);
                zone.setKeyHigh (nn);
                if (zone.getKeyRoot () < 0)
                    zone.setKeyRoot (nn);
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
        final double result = volume - MPCKeygroupConstants.MINUS_12_DB;
        return result * 18.0 / MPCKeygroupConstants.VALUE_RANGE - 12;
    }


    private static IEnvelope parseEnvelope (final Element element, final String attackTag, final String holdTag, final String decayTag, final String sustainTag, final String releaseTag, final String attackCurveTag, final String decayCurveTag, final String releaseCurveTag)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (getEnvelopeAttribute (element, attackTag, MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
        envelope.setHoldTime (getEnvelopeAttribute (element, holdTag, MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
        envelope.setDecayTime (getEnvelopeAttribute (element, decayTag, MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
        envelope.setReleaseTime (getEnvelopeAttribute (element, releaseTag, MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0.63, true));

        envelope.setSustainLevel (getEnvelopeAttribute (element, sustainTag, 0, 1, 1, false));

        envelope.setAttackSlope (Math.clamp (XMLUtils.getChildElementDoubleContent (element, attackCurveTag, 0.5) * 2.0 - 1.0, -1.0, 1.0));
        envelope.setDecaySlope (Math.clamp (XMLUtils.getChildElementDoubleContent (element, decayCurveTag, 0.5) * 2.0 - 1.0, -1.0, 1.0));
        envelope.setReleaseSlope (Math.clamp (XMLUtils.getChildElementDoubleContent (element, releaseCurveTag, 0.5) * 2.0 - 1.0, -1.0, 1.0));

        return envelope;
    }


    private static double getEnvelopeAttribute (final Element element, final String attribute, final double minimum, final double maximum, final double defaultValue, final boolean logarithmic)
    {
        final double value = XMLUtils.getChildElementDoubleContent (element, attribute, defaultValue);
        if (value < 0)
            return defaultValue;
        return logarithmic ? denormalizeLogarithmicEnvTimeValue (value, minimum, maximum) : denormalizeValue (value, minimum, maximum);
    }


    private static double denormalizeLogarithmicEnvTimeValue (final double value, final double minimum, final double maximum)
    {
        return minimum * Math.exp (Math.clamp (value, 0, 1) * Math.log (maximum / minimum));
    }
}
