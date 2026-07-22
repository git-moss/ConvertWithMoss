// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively MPC Keygroup (in XML and JSON format) files in folders. Files must end with
 * <i>.xpm</i>. Also detects recursively MPC Key-group tracks v3. Files must end with <i>.xty</i>. A
 * track file is an MPC3-specific file that saves all settings, samples, macros, FX and MIDI data
 * associated with a track. A track consists of two elements; the track file itself and a trackData
 * folder containing the samples used within the track. It's a complete snapshot of an entire
 * sequencer track, and reloading this to a track will exactly recreate the original track.
 *
 * @author Jürgen Moßgraber
 */
public class MPCModernDetector extends AbstractDetector<MPCKeygroupDetectorUI>
{
    private static final String IDS_MPC_NOT_A_PROJECT_OR_TRACK_FILE = "IDS_MPC_NOT_A_PROJECT_OR_TRACK_FILE";
    private static final String IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY   = "IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY";
    private static final String BAD_METADATA_FILE                   = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";

    private final ObjectMapper  mapper                              = new ObjectMapper ();


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MPCModernDetector (final INotifier notifier)
    {
        super ("Akai MPC Modern", "MPC", notifier, new MPCKeygroupDetectorUI ("MPC"), ".xpm", ".xpj", ".xty");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        // Old Key-group XML format?
        if (sourceFile.getName ().toLowerCase ().endsWith (".xpm"))
            try
            {
                boolean isXML = false;
                try (final FileInputStream input = new FileInputStream (sourceFile))
                {
                    isXML = "<?xml".equals (StreamUtils.readAscii (input, 5));
                }
                if (isXML)
                {
                    final String content = this.loadTextFile (sourceFile).trim ();
                    return this.readXmlFile (sourceFile, content);
                }
            }
            catch (final IOException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
                return Collections.emptyList ();
            }

        return this.readJsonPresetFile (sourceFile);
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param file The file
     * @param content The XML content to parse
     * @return The result
     */
    private List<IMultisampleSource> readXmlFile (final File file, final String content)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseXml (file, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (BAD_METADATA_FILE, ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Process the multi-sample metadata file and the related wave files.
     *
     * @param sourceFile The file which contained the XML document
     * @param document The metadata XML document
     * @return The parsed multi-sample source
     */
    private List<IMultisampleSource> parseXml (final File sourceFile, final Document document)
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
        final String name = programNameElement == null ? FileUtils.getNameWithoutType (sourceFile) : programNameElement.getTextContent ();

        final Element instrumentsElement = XMLUtils.getChildElementByName (programElement, MPCKeygroupTag.PROGRAM_INSTRUMENTS);
        if (instrumentsElement == null)
            return Collections.emptyList ();
        final List<Element> instrumentElements = XMLUtils.getChildElementsByName (instrumentsElement, MPCKeygroupTag.INSTRUMENTS_INSTRUMENT);
        final int numKeygroups = XMLUtils.getChildElementIntegerContent (programElement, MPCKeygroupTag.PROGRAM_NUM_KEYGROUPS, 128);
        final List<IGroup> groups = this.parseGroups (sourceFile.getParentFile (), numKeygroups, instrumentElements, isDrum);

        if (isDrum)
            this.applyDrumPadNoteMap (programElement, groups);

        final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, name, groups);
        if (isDrum)
            multisampleSource.getMetadata ().setCategory ("Drums");

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

        this.notifier.logError (BAD_METADATA_FILE, "Unknown Root");
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
     */
    private List<IGroup> parseGroups (final File basePath, final int numKeygroups, final List<Element> instrumentElements, final boolean isDrum)
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
            catch (final RuntimeException _)
            {
                zonePlay = PlayLogic.ALWAYS;
                this.notifier.logError (IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY);
            }

            boolean isOneShot = false;
            // A missing attribute is treated as a one-shot for the loop handling below but is too
            // weak an evidence to store it in the model: a wrongly set one-shot means that a
            // note-off is ignored in the destination format and therefore notes never stop
            boolean hasExplicitOneShot = false;
            final int triggerMode = XMLUtils.getChildElementIntegerContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_TRIGGER_MODE, -1);
            TriggerType triggerType = TriggerType.ATTACK;
            if (triggerMode < 0)
            {
                final String oneShotStr = XMLUtils.getChildElementContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_ONE_SHOT);
                isOneShot = oneShotStr == null || MPCKeygroupTag.TRUE.equalsIgnoreCase (oneShotStr);
                hasExplicitOneShot = oneShotStr != null && isOneShot;
            }
            else
                switch (triggerMode)
                {
                    // One-Shot
                    case 0:
                        isOneShot = true;
                        hasExplicitOneShot = true;
                        break;
                    case 1:
                        triggerType = TriggerType.RELEASE;
                        break;
                    default:
                    case 2:
                        // Attack
                        break;
                }

            this.readZones (basePath, isDrum, zones, instrumentElement, keyLow, keyHigh, zonePlay, isOneShot, triggerType);
        }

        return groupIntoLayers (zones);
    }


    private void readZones (final File basePath, final boolean isDrum, final List<ISampleZone> zones, final Element instrumentElement, final int keyLow, final int keyHigh, final PlayLogic zonePlay, final boolean isOneShot, final TriggerType triggerType)
    {
        final String ignoreBaseNoteStr = XMLUtils.getChildElementContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_IGNORE_BASE_NOTE);
        final boolean ignoreBaseNote = ignoreBaseNoteStr != null && MPCKeygroupTag.TRUE.equals (ignoreBaseNoteStr);
        final Optional<IFilter> filter = parseFilter (instrumentElement);
        final IEnvelope volumeEnvelope = parseEnvelope (instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_ATTACK, MPCKeygroupTag.INSTRUMENT_VOLUME_HOLD, MPCKeygroupTag.INSTRUMENT_VOLUME_DECAY, MPCKeygroupTag.INSTRUMENT_VOLUME_SUSTAIN, MPCKeygroupTag.INSTRUMENT_VOLUME_RELEASE, MPCKeygroupTag.INSTRUMENT_VOLUME_ATTACK_CURVE, MPCKeygroupTag.INSTRUMENT_VOLUME_DECAY_CURVE, MPCKeygroupTag.INSTRUMENT_VOLUME_RELEASE_CURVE);
        final IEnvelope pitchEnvelope = parseEnvelope (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ATTACK, MPCKeygroupTag.INSTRUMENT_PITCH_HOLD, MPCKeygroupTag.INSTRUMENT_PITCH_DECAY, MPCKeygroupTag.INSTRUMENT_PITCH_SUSTAIN, MPCKeygroupTag.INSTRUMENT_PITCH_RELEASE, MPCKeygroupTag.INSTRUMENT_PITCH_ATTACK_CURVE, MPCKeygroupTag.INSTRUMENT_PITCH_DECAY_CURVE, MPCKeygroupTag.INSTRUMENT_PITCH_RELEASE_CURVE);

        final Element layersElement = XMLUtils.getChildElementByName (instrumentElement, MPCKeygroupTag.INSTRUMENT_LAYERS);
        if (layersElement == null)
            return;

        for (final Element layerElement: XMLUtils.getChildElementsByName (layersElement, MPCKeygroupTag.LAYERS_LAYER))
        {
            final int velStart = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_VEL_START, 0);
            final int velEnd = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_VEL_END, 0);

            final Optional<ISampleZone> zoneOpt = this.parseSampleZone (layerElement, basePath, keyLow, keyHigh, velStart, velEnd, zonePlay, ignoreBaseNote, triggerType);
            if (zoneOpt.isEmpty ())
                continue;
            final ISampleZone zone = zoneOpt.get ();
            zones.add (zone);

            //
            // Amplitude

            final IEnvelopeModulator amplitudeModulator = zone.getAmplitudeEnvelopeModulator ();
            amplitudeModulator.setDepth (1.0);
            amplitudeModulator.getSource ().set (volumeEnvelope);

            final double ampVelocityAmount = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_VELOCITY_TO_AMP_AMOUNT, 0);
            if (ampVelocityAmount > 0)
                zone.getAmplitudeVelocityModulator ().setDepth (ampVelocityAmount);

            //
            // Pitch

            final double pitchEnvAmount = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ENV_AMOUNT, 0.5);
            if (pitchEnvAmount != 0.5)
            {
                final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
                pitchModulator.setDepth ((pitchEnvAmount - 0.5) * 2.0);
                pitchModulator.getSource ().set (pitchEnvelope);
            }

            zone.setOneShot (isOneShot);
            // No loop if it is a one-shot
            if (!isOneShot)
                this.parseLoop (layerElement, zone);

            if (filter.isPresent ())
                zone.setFilter (filter.get ());

            this.readMissingData (isDrum, zone);
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
    private static Optional<IFilter> parseFilter (final Element instrumentElement)
    {
        final int filterID = XMLUtils.getChildElementIntegerContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_TYPE, -1);
        if (filterID <= 0)
            return Optional.empty ();
        final double cutoff = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_CUTOFF, 1);
        final double resonance = XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_RESONANCE, 0);
        final MPCFilter filter = new MPCFilter (filterID, cutoff, resonance);
        if (filter.getType () == null)
            return Optional.empty ();

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

        filter.setCutoffKeyTracking (XMLUtils.getChildElementDoubleContent (instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_KEYTRACK, 0));

        return Optional.of (filter);
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
    private Optional<ISampleZone> parseSampleZone (final Element layerElement, final File basePath, final int keyLow, final int keyHigh, final int velStart, final int velEnd, final PlayLogic zonePlay, final boolean ignoreBaseNote, final TriggerType triggerType)
    {
        final Element sampleNameElement = XMLUtils.getChildElementByName (layerElement, MPCKeygroupTag.LAYER_SAMPLE_NAME);
        if (sampleNameElement == null)
            return Optional.empty ();

        // Use this method to preserve whitespace since some filenames end with a space!
        final String sampleName = sampleNameElement.getTextContent ();
        if (sampleName.isBlank ())
            return Optional.empty ();

        final ISampleData sampleData;
        try
        {
            sampleData = new WavFileSampleData (new File (basePath, sampleName + ".WAV"));
        }
        catch (final IOException _)
        {
            this.notifier.logError (IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY);
            return Optional.empty ();
        }
        final ISampleZone zone = new DefaultSampleZone (sampleName, sampleData);

        zone.setKeyLow (keyLow);
        zone.setKeyHigh (keyHigh);
        try
        {
            zone.setPlayLogic (zonePlay);
        }
        catch (final RuntimeException _)
        {
            this.notifier.logError (IDS_MPC_COULD_NOT_PARSE_ZONE_PLAY);
        }
        if (triggerType != TriggerType.ATTACK)
            zone.setTrigger (triggerType);

        zone.setVelocityLow (velStart);
        zone.setVelocityHigh (velEnd);

        final String activeStr = XMLUtils.getChildElementContent (layerElement, MPCKeygroupTag.LAYER_ACTIVE);
        if (activeStr != null && !activeStr.equalsIgnoreCase (MPCKeygroupTag.TRUE))
            return Optional.of (zone);

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

        return Optional.of (zone);
    }


    /**
     * Parse the loop settings from the layer element.
     *
     * @param layerElement THe layer element
     * @param zone Where to store the data
     */
    private void parseLoop (final Element layerElement, final ISampleZone zone)
    {
        if (this.settingsConfiguration.ignoreLoops ())
            return;

        // There might be no loop, forward or reverse
        final int sliceLoop = XMLUtils.getChildElementIntegerContent (layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP, -1);
        if (sliceLoop <= 0)
            return;

        // There is a loop, is it reversed?
        if (sliceLoop == 3)
            zone.setReversed (true);

        final ISampleLoop sampleLoop = new DefaultSampleLoop ();
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
     * Read missing metadata from the WAV file if necessary.
     *
     * @param isDrum True if it is a Drum patch
     * @param zone Where to store the data
     */
    private void readMissingData (final boolean isDrum, final ISampleZone zone)
    {
        final boolean needsUpdate = zone.getStop () > 0;
        final boolean needsRootKey = !isDrum && zone.getKeyRoot () < 0;

        try
        {
            if (needsUpdate || needsRootKey)
            {
                final Optional<ISampleData> sampleData = zone.getSampleData ();
                if (sampleData.isPresent ())
                    sampleData.get ().addZoneData (zone, needsRootKey, false);
            }
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
    private void applyDrumPadNoteMap (final Element programElement, final List<IGroup> groups)
    {
        final Element padNoteMapElement = XMLUtils.getChildElementByName (programElement, MPCKeygroupTag.PROGRAM_PAD_NOTE_MAP);
        if (padNoteMapElement == null)
            return;

        final Map<Integer, Integer> padNoteMap = HashMap.newHashMap (128);
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
        return logarithmic ? denormalizeLogarithmicEnvTimeValue (value, minimum, maximum) : MathUtils.denormalizeValue (value, minimum, maximum);
    }


    private static double denormalizeLogarithmicEnvTimeValue (final double value, final double minimum, final double maximum)
    {
        return minimum * Math.exp (Math.clamp (value, 0, 1) * Math.log (maximum / minimum));
    }


    private List<IMultisampleSource> readJsonPresetFile (final File sourceFile)
    {
        try (final BufferedReader reader = new BufferedReader (new InputStreamReader (new GZIPInputStream (new FileInputStream (sourceFile)), StandardCharsets.UTF_8)))
        {
            final String [] header = new String [5];
            for (int i = 0; i < 5; i++)
                header[i] = reader.readLine ();

            if (!"ACVS".equals (header[0]))
            {
                this.notifier.logError (IDS_MPC_NOT_A_PROJECT_OR_TRACK_FILE, header[0]);
                return Collections.emptyList ();
            }

            if (!"json".equals (header[3]))
            {
                this.notifier.logError (IDS_MPC_NOT_A_PROJECT_OR_TRACK_FILE, "Encoding is '" + header[3] + "'");
                return Collections.emptyList ();
            }

            JSONFormat jsonFormat;
            switch (header[2])
            {
                case "SerialisableProjectData" -> jsonFormat = JSONFormat.PROJECT;
                case "SerialisableTrackData" -> jsonFormat = JSONFormat.TRACK;
                case "SerialisableProgramData" -> jsonFormat = JSONFormat.PROGRAM;
                default -> {
                    this.notifier.logError (IDS_MPC_NOT_A_PROJECT_OR_TRACK_FILE, header[2]);
                    return Collections.emptyList ();
                }
            }

            final String version = header[1];
            final String operatingSystem = header[4];
            this.notifier.log ("IDS_MPC_TRACK_OR_PROJECT_VERSION", jsonFormat.getName (), version, operatingSystem);

            if (this.waitForDelivery ())
                return Collections.emptyList ();

            final String jsonCode = reader.readAllAsString ();

            if (this.waitForDelivery ())
                return Collections.emptyList ();

            return this.readJsonData (sourceFile, this.getContent (jsonCode), jsonFormat);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Parse the JSON description file.
     *
     * @param multiSampleFile The file
     * @param root The root node of the JSON structure
     * @param jsonFormat The JSON format
     * @return The result
     * @throws IOException Error reading the file
     */
    private List<IMultisampleSource> readJsonData (final File multiSampleFile, final JsonNode root, final JSONFormat jsonFormat) throws IOException
    {
        final JsonNode dataNode = root.get ("data");
        if (dataNode == null)
        {
            this.notifier.logError ("IDS_MPC_DATA_MISSING");
            return Collections.emptyList ();
        }

        final List<IMultisampleSource> results = new ArrayList<> ();
        for (final JsonNode programNode: collectProgramNodes (dataNode, jsonFormat))
        {
            // Further interesting track values in case multiple-instruments are loaded from a
            // project: volume, pan, transposition

            final String programName = programNode.get ("name").asText ();
            final String n = this.settingsConfiguration.isPreferFolderName () ? this.sourceFolder.getName () : programName;
            final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, n);
            final IGroup group = new DefaultGroup ();
            final IMultisampleSource multisampleSource = createMultisampleSource (this.settingsConfiguration, multiSampleFile, parts, programName, Collections.singletonList (group));

            final double programTranspose = programNode.get ("transpose").asDouble ();

            // -------------------------------------------------------------------------------
            // Read all sample info
            final Iterator<JsonNode> sampleNodes = dataNode.get ("samples").elements ();
            final Map<String, SampleInfo> sampleInfos = new HashMap<> ();
            while (sampleNodes.hasNext ())
            {
                final JsonNode sampleNode = sampleNodes.next ();
                final SampleInfo sampleInfo = new SampleInfo ();
                final String sampleName = sampleNode.get ("name").asText ();
                // "path" attribute appears again in the sample zone, so no need to store it here
                final JsonNode metadata = sampleNode.get ("metadata");
                if (metadata != null)
                {
                    sampleInfo.rootNote = metadata.get ("rootNote").asInt ();
                    sampleInfo.tuning = metadata.get ("tune").asDouble ();
                    sampleInfos.put (sampleName, sampleInfo);
                }
            }

            // -------------------------------------------------------------------------------
            // Read key-group parameters
            final JsonNode keygroupNode = programNode.get ("keygroup");
            double keygroupTranspose = 0;
            int pitchBendUp = 0;
            int pitchBendDown = 0;
            MPCEnvelopesAndFilter globalEnvelopesAndFilter = null;
            if (keygroupNode != null)
            {
                keygroupTranspose = programTranspose + keygroupNode.get ("transpose").asDouble ();
                pitchBendUp = keygroupNode.get ("pitchBendPositiveRange").asInt ();
                pitchBendDown = keygroupNode.get ("pitchBendNegativeRange").asInt ();
                final JsonNode synthSectionNode = keygroupNode.get ("synthSection");
                globalEnvelopesAndFilter = new MPCEnvelopesAndFilter (synthSectionNode, true);
            }

            // -------------------------------------------------------------------------------
            // Read all layers - strangely all key-group settings seem to be under drum
            final JsonNode drumNode = programNode.get ("drum");
            final Iterator<JsonNode> instrumentsNodes = drumNode.get ("instruments").elements ();
            while (instrumentsNodes.hasNext ())
            {
                final JsonNode instrumentNode = instrumentsNodes.next ();
                final int coarseTune = instrumentNode.get ("coarseTune").asInt ();
                final int fineTune = instrumentNode.get ("fineTune").asInt ();
                final double tuning = keygroupTranspose + coarseTune + fineTune / 100.0;

                final int lowNote = instrumentNode.get ("lowNote").asInt ();
                final int highNote = instrumentNode.get ("highNote").asInt ();

                final Iterator<JsonNode> layersNodes = instrumentNode.get ("layersv").elements ();
                while (layersNodes.hasNext ())
                {
                    final Optional<ISampleZone> sampleZone = this.readJsonSampleZone (multiSampleFile, instrumentNode, layersNodes.next (), lowNote, highNote, pitchBendUp, pitchBendDown, tuning, sampleInfos, globalEnvelopesAndFilter, jsonFormat);
                    if (sampleZone.isPresent ())
                        group.addSampleZone (sampleZone.get ());
                }
            }

            results.add (multisampleSource);
        }

        return results;
    }


    private static List<JsonNode> collectProgramNodes (final JsonNode dataNode, final JSONFormat jsonFormat)
    {
        if (jsonFormat == JSONFormat.PROGRAM)
            return Collections.singletonList (dataNode);

        if (jsonFormat == JSONFormat.TRACK)
        {
            final JsonNode programNode = dataNode.get ("program");
            // 1 == key-group program
            return programNode != null && programNode.get ("type").asInt () == 1 ? Collections.singletonList (programNode) : Collections.emptyList ();
        }

        // jsonFormat == JSONFormat.PROJECT
        final JsonNode tracksNode = dataNode.get ("tracks");
        if (tracksNode == null)
            return Collections.emptyList ();

        final List<JsonNode> programNodes = new ArrayList<> ();
        final Iterator<JsonNode> trackNodes = tracksNode.elements ();
        while (trackNodes.hasNext ())
        {
            final JsonNode programNode = trackNodes.next ().get ("program");
            // 1 == key-group program
            if (programNode != null && programNode.get ("type").asInt () == 1)
                programNodes.add (programNode);
        }
        return programNodes;
    }


    private Optional<ISampleZone> readJsonSampleZone (final File multiSampleFile, final JsonNode instrumentNode, final JsonNode layerNode, final int lowNote, final int highNote, final int pitchBendUp, final int pitchBendDown, final double tuning, final Map<String, SampleInfo> sampleInfos, final MPCEnvelopesAndFilter globalEnvelopesAndFilter, final JSONFormat jsonFormat) throws IOException
    {
        if (!layerNode.get ("active").asBoolean ())
            return Optional.empty ();

        final String sampleName = layerNode.get ("sampleName").asText ();
        final String sampleFileName = layerNode.get ("sampleFile").asText ();
        if (sampleName.isBlank () || sampleFileName.isBlank ())
            return Optional.empty ();

        final String sampleFolderName = FileUtils.getNameWithoutType (multiSampleFile);
        final File path = new File (multiSampleFile.getParentFile (), sampleFolderName + "_[" + jsonFormat.getName () + "Data]");
        final File file = new File (path, sampleFileName);
        if (!file.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", file.getAbsolutePath ());
            return Optional.empty ();
        }

        final ISampleData sampleData = new WavFileSampleData (file);
        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, sampleData);
        sampleZone.setBendUp (pitchBendUp);
        sampleZone.setBendDown (-pitchBendDown);

        final SampleInfo sampleInfo = sampleInfos.get (sampleName);
        final int rootNote = layerNode.get ("rootNote").asInt ();
        if (rootNote > 0)
            sampleZone.setKeyRoot (rootNote);
        else if (sampleInfo != null)
            sampleZone.setKeyRoot (sampleInfo.rootNote);

        final int layerCoarseTune = instrumentNode.get ("coarseTune").asInt ();
        final int layerFineTune = instrumentNode.get ("fineTune").asInt ();
        final double layerTuning = layerCoarseTune + layerFineTune / 100.0;
        sampleZone.setTuning (layerTuning + tuning + (sampleInfo != null ? sampleInfo.tuning : 0));

        sampleZone.setPanning (layerNode.get ("pan").asDouble () * 2.0 - 1.0);
        sampleZone.setKeyLow (lowNote);
        sampleZone.setKeyHigh (highNote);
        sampleZone.setVelocityLow (layerNode.get ("velocityStart").asInt ());
        sampleZone.setVelocityHigh (layerNode.get ("velocityEnd").asInt ());
        // -9999..+9999 -> negative values currently not supported
        final int offset = Math.max (0, layerNode.get ("offset").asInt ());
        final int start = offset + layerNode.get ("sampleStart").asInt ();
        if (start > 0)
            sampleZone.setStart (start);
        final int playbackEnd = layerNode.get ("sampleEnd").asInt ();
        if (playbackEnd > 0)
            sampleZone.setStop (playbackEnd);

        sampleZone.setReversed (layerNode.get ("direction").asInt () > 0);

        sampleZone.setKeyTracking (layerNode.get ("keyTrackEnable").asBoolean () ? 1.0 : 0);

        final JsonNode overrideSliceLoopModeNode = layerNode.get ("layerLoopModeOverridesSliceLoopMode");
        if (overrideSliceLoopModeNode != null && overrideSliceLoopModeNode.asBoolean ())
        {
            final int loopMode = layerNode.get ("loopMode").asInt ();
            final int stop = layerNode.get ("loopEnd").asInt ();
            if (loopMode > 0 && stop > 0)
            {
                final ISampleLoop sampleLoop = new DefaultSampleLoop ();
                sampleLoop.setStart (layerNode.get ("loopStart").asInt ());
                sampleLoop.setEnd (stop);
                final int loopCrossfade = layerNode.get ("loopCrossfadeLength").asInt ();
                sampleLoop.setCrossfade (loopCrossfade / (double) sampleLoop.getLength ());
                sampleZone.getLoops ().add (sampleLoop);
            }
        }
        else
        {
            final JsonNode sliceInfoNode = layerNode.get ("sliceInfo");
            final int loopMode = sliceInfoNode.get ("LoopMode").asInt ();
            final int stop = sliceInfoNode.get ("End").asInt ();
            if (loopMode > 0 && stop > 0)
            {
                final ISampleLoop sampleLoop = new DefaultSampleLoop ();
                sampleLoop.setStart (sliceInfoNode.get ("LoopStart").asInt ());
                sampleLoop.setEnd (stop);
                final int loopCrossfade = sliceInfoNode.get ("LoopCrossfadeLength").asInt ();
                sampleLoop.setCrossfade (loopCrossfade / (double) sampleLoop.getLength ());
                sampleZone.getLoops ().add (sampleLoop);
            }
        }

        final JsonNode synthSectionNode = instrumentNode.get ("synthSection");
        final MPCEnvelopesAndFilter envelopesAndFilter = new MPCEnvelopesAndFilter (synthSectionNode, false);

        // Set global filter and envelopes, if present or sample zone settings
        if (globalEnvelopesAndFilter != null && globalEnvelopesAndFilter.getFilter () != null)
            sampleZone.setFilter (globalEnvelopesAndFilter.getFilter ());
        else if (envelopesAndFilter.getFilter () != null)
            sampleZone.setFilter (envelopesAndFilter.getFilter ());
        setModulator (sampleZone.getAmplitudeEnvelopeModulator (), globalEnvelopesAndFilter != null ? globalEnvelopesAndFilter.getAmpEnvelopeModulator () : null, envelopesAndFilter.getAmpEnvelopeModulator ());
        setModulator (sampleZone.getPitchEnvelopeModulator (), globalEnvelopesAndFilter != null ? globalEnvelopesAndFilter.getPitchEnvelopeModulator () : null, envelopesAndFilter.getPitchEnvelopeModulator ());

        return Optional.of (sampleZone);
    }


    private static void setModulator (final IEnvelopeModulator destinationEnvelopeModulator, final IEnvelopeModulator sourceGlobalEnvelopeModulator, final IEnvelopeModulator sourceEnvelopeModulator)
    {
        if (sourceGlobalEnvelopeModulator != null)
        {
            destinationEnvelopeModulator.setDepth (sourceGlobalEnvelopeModulator.getDepth ());
            destinationEnvelopeModulator.setSource (sourceGlobalEnvelopeModulator.getSource ());
        }
        else if (sourceEnvelopeModulator != null)
        {
            destinationEnvelopeModulator.setDepth (sourceEnvelopeModulator.getDepth ());
            destinationEnvelopeModulator.setSource (sourceEnvelopeModulator.getSource ());
        }
    }


    /**
     * Get and parse the JSON content of an information message
     *
     * @param content The JSON code
     * @return The root node of the JSON structure
     * @throws IOException Could not parse the JSON code
     */
    private JsonNode getContent (final String content) throws IOException
    {
        try
        {
            return this.mapper.readValue (content, JsonNode.class);
        }
        catch (final JsonProcessingException ex)
        {
            throw new IOException ("Could not parse JSON information.", ex);
        }
    }


    private class SampleInfo
    {
        int    rootNote = -1;
        double tuning;
    }


    private enum JSONFormat
    {
        PROJECT("Project"),
        TRACK("Track"),
        PROGRAM("Program");


        private final String name;


        private JSONFormat (final String name)
        {
            this.name = name;
        }


        /**
         * Get the name of the format.
         *
         * @return The name
         */
        public String getName ()
        {
            return this.name;
        }
    }
}
