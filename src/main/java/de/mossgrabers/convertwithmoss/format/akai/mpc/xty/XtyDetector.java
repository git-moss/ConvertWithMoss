// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc.xty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.akai.mpc.MPCFilter;
import de.mossgrabers.convertwithmoss.format.akai.mpc.MPCKeygroupConstants;
import de.mossgrabers.convertwithmoss.format.akai.mpc.xpm.MPCKeygroupDetectorUI;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively MPC Key-group tracks v3. Files must end with <i>.xty</i>. A track file is an
 * MPC3-specific file that saves all settings, samples, macros, FX and MIDI data associated with a
 * track. A track consists of two elements; the track file itself and a trackData folder containing
 * the samples used within the track. It's a complete snapshot of an entire sequencer track, and
 * reloading this to a track will exactly recreate the original track.
 *
 * @author Jürgen Moßgraber
 */
public class XtyDetector extends AbstractDetector<MPCKeygroupDetectorUI>
{
    private final ObjectMapper mapper = new ObjectMapper ();

    private String             version;
    private String             operatingSystem;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public XtyDetector (final INotifier notifier)
    {
        super ("Akai MPC Project/Track", "XTY", notifier, new MPCKeygroupDetectorUI ("XTY"), ".xpj", ".xty");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        try (final BufferedReader reader = new BufferedReader (new InputStreamReader (new GZIPInputStream (new FileInputStream (file)), StandardCharsets.UTF_8)))
        {
            final String [] header = new String [5];
            for (int i = 0; i < 5; i++)
                header[i] = reader.readLine ();

            if (!"ACVS".equals (header[0]))
            {
                this.notifier.logError ("IDS_MPC_NOT_A_PROJECT_OR_TRACK_FILE", header[0]);
                return Collections.emptyList ();
            }

            if (!"json".equals (header[3]))
            {
                this.notifier.logError ("IDS_MPC_NOT_A_PROJECT_OR_TRACK_FILE", "Encoding is '" + header[3] + "'");
                return Collections.emptyList ();
            }

            final boolean isProject = "SerialisableProjectData".equals (header[2]);
            final boolean isTrack = "SerialisableTrackData".equals (header[2]);
            if (!isProject && !isTrack)
            {
                this.notifier.logError ("IDS_MPC_NOT_A_PROJECT_OR_TRACK_FILE", header[2]);
                return Collections.emptyList ();
            }

            this.version = header[1];
            this.operatingSystem = header[4];
            this.notifier.log ("IDS_MPC_TRACK_OR_PROJECT_VERSION", isProject ? "project" : "track", this.version, this.operatingSystem);

            if (this.waitForDelivery ())
                return Collections.emptyList ();

            final String jsonCode = reader.readAllAsString ();

            if (this.waitForDelivery ())
                return Collections.emptyList ();

            return this.parseJsonData (file, this.getContent (jsonCode), isProject);
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
     * @param isProject True if this is a project file (XPJ) otherwise it is a track file (XTY)
     * @return The result
     * @throws IOException Error reading the file
     */
    private List<IMultisampleSource> parseJsonData (final File multiSampleFile, final JsonNode root, boolean isProject) throws IOException
    {
        final JsonNode dataNode = root.get ("data");
        if (dataNode == null)
            return Collections.emptyList ();

        final List<IMultisampleSource> results = new ArrayList<> ();
        for (final JsonNode programNode: collectProgramNodes (dataNode, isProject))
        {
            // Further interesting track values in case multiple-instruments are loaded from a
            // project: volume, pan, transposition

            final String programName = programNode.get ("name").asText ();

            final String n = this.settingsConfiguration.isPreferFolderName () ? this.sourceFolder.getName () : programName;
            final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, n);
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, programName, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));

            final IGroup group = new DefaultGroup ();

            multisampleSource.setName (programName);
            final double programTranspose = programNode.get ("transpose").asDouble ();

            //////////////////////////////////////////////////////////////////////////////
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

            //////////////////////////////////////////////////////////////////////////////
            // Read key-group parameters
            final JsonNode keygroupNode = programNode.get ("keygroup");
            final double keygroupTranspose = programTranspose + keygroupNode.get ("transpose").asDouble ();

            // Double ?
            final int pitchBendUp = keygroupNode.get ("pitchBendPositiveRange").asInt ();
            final int pitchBendDown = keygroupNode.get ("pitchBendNegativeRange").asInt ();

            final JsonNode synthSectionNode = keygroupNode.get ("synthSection");
            final EnvelopesAndFilter globalEnvelopesAndFilter = new EnvelopesAndFilter (synthSectionNode, true);

            //////////////////////////////////////////////////////////////////////////////
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
                    final ISampleZone sampleZone = this.readSampleZone (multiSampleFile, instrumentNode, layersNodes.next (), lowNote, highNote, pitchBendUp, pitchBendDown, tuning, sampleInfos, globalEnvelopesAndFilter, isProject);
                    if (sampleZone != null)
                        group.addSampleZone (sampleZone);
                }
            }

            final List<IGroup> groups = Collections.singletonList (group);
            multisampleSource.setGroups (groups);
            final boolean isDrum = false;
            this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (groups), parts, isDrum ? "Drums" : null);
            multisampleSource.setMappingName (multiSampleFile.getName () + " : " + multisampleSource.getName ());
            results.add (multisampleSource);
        }

        return results;
    }


    private static List<JsonNode> collectProgramNodes (final JsonNode dataNode, final boolean isProject)
    {
        final List<JsonNode> programNodes = new ArrayList<> ();

        if (isProject)
        {
            final JsonNode tracksNode = dataNode.get ("tracks");
            if (tracksNode != null)
            {
                final Iterator<JsonNode> trackNodes = tracksNode.elements ();
                while (trackNodes.hasNext ())
                {
                    final JsonNode programNode = trackNodes.next ().get ("program");
                    // 1 == key-group program
                    if (programNode != null && programNode.get ("type").asInt () == 1)
                        programNodes.add (programNode);
                }
            }
        }

        final JsonNode programNode = dataNode.get ("program");
        // 1 == key-group program
        if (programNode != null && programNode.get ("type").asInt () == 1)
            programNodes.add (programNode);

        return programNodes;

    }


    private ISampleZone readSampleZone (final File multiSampleFile, final JsonNode instrumentNode, final JsonNode layerNode, final int lowNote, final int highNote, final int pitchBendUp, final int pitchBendDown, final double tuning, final Map<String, SampleInfo> sampleInfos, final EnvelopesAndFilter globalEnvelopesAndFilter, final boolean isProject) throws IOException
    {
        if (!layerNode.get ("active").asBoolean ())
            return null;

        final String sampleName = layerNode.get ("sampleName").asText ();
        final String sampleFileName = layerNode.get ("sampleFile").asText ();
        if (sampleName.isBlank () || sampleFileName.isBlank ())
            return null;

        final String sampleFolderName = FileUtils.getNameWithoutType (multiSampleFile);
        final File path = new File (multiSampleFile.getParentFile (), sampleFolderName + (isProject ? "_[ProjectData]" : "_[TrackData]"));
        final File file = new File (path, sampleFileName);
        if (!file.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", file.getAbsolutePath ());
            return null;
        }

        final ISampleData sampleData = new WavFileSampleData (file);
        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, sampleData);
        sampleZone.setBendUp (pitchBendUp);
        sampleZone.setBendDown (pitchBendDown);

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
        final EnvelopesAndFilter envelopesAndFilter = new EnvelopesAndFilter (synthSectionNode, false);

        // Set global filter and envelopes, if present or sample zone settings
        if (globalEnvelopesAndFilter.filter != null)
            sampleZone.setFilter (globalEnvelopesAndFilter.filter);
        else if (envelopesAndFilter.filter != null)
            sampleZone.setFilter (envelopesAndFilter.filter);
        setModulator (sampleZone.getAmplitudeEnvelopeModulator (), globalEnvelopesAndFilter.ampEnvelopeModulator, envelopesAndFilter.ampEnvelopeModulator);
        setModulator (sampleZone.getPitchEnvelopeModulator (), globalEnvelopesAndFilter.pitchEnvelopeModulator, envelopesAndFilter.pitchEnvelopeModulator);

        return sampleZone;
    }


    private static void setModulator (final IEnvelopeModulator destinationEnvelopeModulator, final IEnvelopeModulator sourceGlobalEnvelopeModulator, final IEnvelopeModulator sourceEnvelopeModulator)
    {
        if (sourceGlobalEnvelopeModulator != null)
        {
            destinationEnvelopeModulator.setDepth (sourceGlobalEnvelopeModulator.getDepth ());
            destinationEnvelopeModulator.setSource (sourceGlobalEnvelopeModulator.getSource ());
        }
        else
        {
            destinationEnvelopeModulator.setDepth (sourceEnvelopeModulator.getDepth ());
            destinationEnvelopeModulator.setSource (sourceEnvelopeModulator.getSource ());
        }
    }


    private static Optional<IEnvelope> parseEnvelope (final JsonNode synthSectionElement, final String envelopeName)
    {
        final JsonNode envelopeElement = synthSectionElement.get (envelopeName);
        if (envelopeElement == null || getEnvelopeAttributeAsBoolean (envelopeElement, "OneShot", false))
            return Optional.empty ();

        final IEnvelope envelope = new DefaultEnvelope ();

        if (getEnvelopeAttributeAsBoolean (envelopeElement, "AD", false))
            envelope.setSustainLevel (0);
        else
        {
            envelope.setDelayTime (getEnvelopeAttributeAsDouble (envelopeElement, "Delay", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
            envelope.setHoldTime (getEnvelopeAttributeAsDouble (envelopeElement, "Hold", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
            envelope.setSustainLevel (getEnvelopeAttributeAsDouble (envelopeElement, "Sustain", 0, 1, 1, false));
        }

        envelope.setAttackTime (getEnvelopeAttributeAsDouble (envelopeElement, "Attack", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
        envelope.setDecayTime (getEnvelopeAttributeAsDouble (envelopeElement, "Decay", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
        envelope.setAttackSlope (Math.clamp (getEnvelopeAttributeAsDouble (envelopeElement, "AttackCurve", 0.5, 0, 1, false) * 2.0 - 1.0, -1.0, 1.0));
        envelope.setDecaySlope (Math.clamp (getEnvelopeAttributeAsDouble (envelopeElement, "DecayCurve", 0.5, 0, 1, false) * 2.0 - 1.0, -1.0, 1.0));
        envelope.setReleaseTime (getEnvelopeAttributeAsDouble (envelopeElement, "Release", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0.63, true));
        envelope.setReleaseSlope (Math.clamp (getEnvelopeAttributeAsDouble (envelopeElement, "ReleaseCurve", 0.5, 0, 1, false) * 2.0 - 1.0, -1.0, 1.0));
        return Optional.of (envelope);
    }


    private static boolean getEnvelopeAttributeAsBoolean (final JsonNode node, final String attribute, final boolean defaultValue)
    {
        final JsonNode attributeNode = node.get (attribute);
        if (attributeNode == null)
            return defaultValue;
        final JsonNode valueNode = attributeNode.get ("value0");
        return valueNode == null ? defaultValue : valueNode.asBoolean ();
    }


    private static double getEnvelopeAttributeAsDouble (final JsonNode node, final String attribute, final double minimum, final double maximum, final double defaultValue, final boolean logarithmic)
    {
        final JsonNode attributeNode = node.get (attribute);
        if (attributeNode == null)
            return defaultValue;
        final JsonNode valueNode = attributeNode.get ("value0");
        if (valueNode == null)
            return defaultValue;
        final double value = valueNode.asDouble ();
        return logarithmic ? denormalizeLogarithmicEnvTimeValue (value, minimum, maximum) : denormalizeValue (value, minimum, maximum);
    }


    private static double denormalizeLogarithmicEnvTimeValue (final double value, final double minimum, final double maximum)
    {
        return minimum * Math.exp (Math.clamp (value, 0, 1) * Math.log (maximum / minimum));
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


    /**
     * Interpret the content of an integer array as an ASCII text.
     *
     * @param start At which index to start to convert the ASCII text in the array
     * @param length The number of integers to convert
     * @param data The integer array
     * @return The converted ASCII string
     */
    public static String integerArrayToString (final int start, final int length, final int [] data)
    {
        final StringBuilder sb = new StringBuilder (length);
        for (int i = 0; i < length; i++)
            sb.append ((char) data[start + i]);
        return sb.toString ();
    }


    private class SampleInfo
    {
        int    rootNote = -1;
        double tuning;
    }


    // Helper class to read and store all envelopes and filter settings
    private class EnvelopesAndFilter
    {
        IFilter            filter                  = null;
        IEnvelopeModulator ampEnvelopeModulator    = null;
        IEnvelopeModulator filterEnvelopeModulator = null;
        IEnvelopeModulator pitchEnvelopeModulator  = null;


        EnvelopesAndFilter (final JsonNode synthSectionNode, final boolean isGlobal)
        {
            if (!isGlobal || getEnvelopeAttributeAsBoolean (synthSectionNode, "ampEnvelopeGlobal", false))
            {
                final Optional<IEnvelope> ampEnvelopeOpt = parseEnvelope (synthSectionNode, "ampEnvelope");
                if (ampEnvelopeOpt.isPresent ())
                {
                    this.ampEnvelopeModulator = new DefaultEnvelopeModulator (1);
                    this.ampEnvelopeModulator.setSource (ampEnvelopeOpt.get ());
                }
            }
            if (!isGlobal || getEnvelopeAttributeAsBoolean (synthSectionNode, "pitchEnvelopeGlobal", false))
            {
                final Optional<IEnvelope> pitchEnvelopeOpt = parseEnvelope (synthSectionNode, "pitchEnvelope");
                if (pitchEnvelopeOpt.isPresent ())
                {
                    final double pitchEnvelopeAmount = synthSectionNode.get ("pitchEnvelopeAmount").asDouble ();
                    this.pitchEnvelopeModulator = new DefaultEnvelopeModulator (pitchEnvelopeAmount * 2.0 - 1.0);
                    this.pitchEnvelopeModulator.setSource (pitchEnvelopeOpt.get ());
                }
            }

            if (!isGlobal || getEnvelopeAttributeAsBoolean (synthSectionNode, "filterEnvelopeGlobal", false))
            {
                final JsonNode filterDataNode = synthSectionNode.get ("filterData");
                if (filterDataNode == null)
                    return;

                final JsonNode valueNode = filterDataNode.get ("value0");
                final int filterID = valueNode.get ("filterType").asInt ();
                if (filterID <= 0)
                    return;

                final Optional<IEnvelope> filterEnvelopeOpt = parseEnvelope (synthSectionNode, "filterEnvelope");
                if (filterEnvelopeOpt.isPresent ())
                {
                    // Filter envelope depth will be set below...
                    this.filterEnvelopeModulator = new DefaultEnvelopeModulator (1);
                    this.filterEnvelopeModulator.setSource (filterEnvelopeOpt.get ());
                }

                final double cutoff = valueNode.get ("filterCutoff").asDouble ();
                final double resonance = valueNode.get ("filterResonance").asDouble ();
                this.filter = new MPCFilter (filterID, cutoff, resonance);
                if (this.filter.getType () == null)
                    return;

                final double filterAmount = valueNode.get ("filterEnvelopeAmount").asDouble ();
                if (filterAmount > 0 && this.filterEnvelopeModulator != null)
                {
                    final IEnvelopeModulator cutoffModulator = this.filter.getCutoffEnvelopeModulator ();
                    cutoffModulator.setDepth (filterAmount);
                    cutoffModulator.getSource ().set (this.filterEnvelopeModulator.getSource ());
                }

                final double filterCutoffVelocityAmount = valueNode.get ("filterVelocity").asDouble ();
                if (filterCutoffVelocityAmount > 0)
                    this.filter.getCutoffVelocityModulator ().setDepth (filterCutoffVelocityAmount);
            }
        }
    }
}
