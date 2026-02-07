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
import java.util.zip.GZIPInputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.akai.mpc.xpm.MPCKeygroupDetectorUI;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Detects recursively MPC Key-groups v3. Files must end with <i>.xty</i>.
 *
 * @author Jürgen Moßgraber
 */
public class XtyDetector extends AbstractDetector<MPCKeygroupDetectorUI>
{
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";

    private final ObjectMapper  mapper                = new ObjectMapper ();

    private String              version;
    private String              operatingSystem;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public XtyDetector (final INotifier notifier)
    {
        super ("Akai MPC Keygroup v3", "XTY", notifier, new MPCKeygroupDetectorUI ("XTY"), ".xty");
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

            if (!"ACVS".equals (header[0]) || !"SerialisableTrackData".equals (header[2]) || !"json".equals (header[3]))
                this.notifier.logError (ERR_BAD_METADATA_FILE);
            this.version = header[1];
            this.operatingSystem = header[4];
            this.notifier.log ("IDS_MPC_VERSION", this.version, this.operatingSystem);

            if (this.waitForDelivery ())
                return Collections.emptyList ();

            final String jsonCode = reader.readAllAsString ();

            if (this.waitForDelivery ())
                return Collections.emptyList ();

            return this.parseJsonData (file, this.getContent (jsonCode));
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
     * @return The result
     * @throws IOException Error reading the file
     */
    private List<IMultisampleSource> parseJsonData (final File multiSampleFile, final JsonNode root) throws IOException
    {
        final List<IMultisampleSource> results = new ArrayList<> ();

        // Further interesting track values in case multiple-instruments are loaded from a project:
        // name, volume, pan, transposition

        final JsonNode dataNode = root.get ("data");
        final JsonNode programNode = dataNode.get ("program");
        if (programNode.get ("type").asInt () == 1)
        {
            final String programName = programNode.get ("name").asText ();

            final String n = this.settingsConfiguration.isPreferFolderName () ? this.sourceFolder.getName () : programName;
            final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, n);
            final IMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, programName, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));

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
                sampleInfo.sampleFileName = sampleNode.get ("path").asText ();
                final JsonNode metadata = sampleNode.get ("metadata");
                sampleInfo.rootNote = metadata.get ("rootNote").asInt ();
                sampleInfo.tuning = metadata.get ("tune").asDouble ();
                sampleInfos.put (sampleName, sampleInfo);
            }

            //////////////////////////////////////////////////////////////////////////////
            // Read key-group parameters
            final JsonNode keygroupNode = programNode.get ("keygroup");
            final double keygroupTranspose = programTranspose + keygroupNode.get ("transpose").asDouble ();

            // Double ?
            final int pitchBendUp = keygroupNode.get ("pitchBendPositiveRange").asInt ();
            final int pitchBendDown = keygroupNode.get ("pitchBendNegativeRange").asInt ();

            final JsonNode synthSectionNode = keygroupNode.get ("synthSection");
            // TODO synthSectionNode -> filterData, filterEnvelope, ampEnvelope, pitchEnvelope

            //////////////////////////////////////////////////////////////////////////////
            // Read all layers - strangely all key-group settings seem to be under drum
            final JsonNode drumNode = programNode.get ("drum");
            final Iterator<JsonNode> instrumentsNodes = drumNode.get ("instruments").elements ();
            while (instrumentsNodes.hasNext ())
            {
                final JsonNode instrumentNode = instrumentsNodes.next ();
                final int coarseTune = instrumentNode.get ("coarseTune").asInt ();
                final int fineTune = instrumentNode.get ("fineTune").asInt ();
                // TODO check if that is correct
                final double tuning = coarseTune + fineTune / 100.0;

                final int lowNote = instrumentNode.get ("lowNote").asInt ();
                final int highNote = instrumentNode.get ("highNote").asInt ();

                final Iterator<JsonNode> layersNodes = instrumentNode.get ("layersv").elements ();
                while (layersNodes.hasNext ())
                {
                    final JsonNode layerNode = layersNodes.next ();
                    if (layerNode.get ("active").asBoolean ())
                    {
                        final String sampleName = layerNode.get ("sampleName").asText ();
                        final String sampleFileName = layerNode.get ("sampleFile").asText ();
                        if (!sampleName.isBlank () && !sampleFileName.isBlank ())
                        {
                            final File path = new File (multiSampleFile.getParentFile (), programName + "_[TrackData]");
                            final File file = new File (path, sampleFileName);
                            if (!file.exists ())
                                this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", file.getAbsolutePath ());
                            else
                            {
                                final SampleInfo sampleInfo = sampleInfos.get (sampleName);
                                if (sampleInfo != null)
                                {
                                    final ISampleData sampleData = new WavFileSampleData (file);
                                    final ISampleZone sampleZone = new DefaultSampleZone (sampleName, sampleData);
                                    sampleZone.setBendUp (pitchBendUp);
                                    sampleZone.setBendDown (pitchBendDown);

                                    final int rootNote = layerNode.get ("rootNote").asInt ();
                                    sampleZone.setKeyRoot (rootNote == 0 ? sampleInfo.rootNote : rootNote);

                                    // TODO check if correct and add
                                    // "pitch": 0.0,
                                    final int layerCoarseTune = instrumentNode.get ("coarseTune").asInt ();
                                    final int layerFineTune = instrumentNode.get ("fineTune").asInt ();
                                    // TODO check if that is correct
                                    final double layerTuning = layerCoarseTune + layerFineTune / 100.0;

                                    sampleZone.setTuning (layerTuning + tuning + sampleInfo.tuning);

                                    sampleZone.setPanning (layerNode.get ("pan").asDouble ());
                                    sampleZone.setKeyLow (lowNote);
                                    sampleZone.setKeyHigh (highNote);
                                    sampleZone.setVelocityLow (layerNode.get ("velocityStart").asInt ());
                                    sampleZone.setVelocityHigh (layerNode.get ("velocityEnd").asInt ());
                                    sampleZone.setStart (layerNode.get ("sampleStart").asInt ());
                                    sampleZone.setStop (layerNode.get ("sampleEnd").asInt ());

                                    // TODO Cannot find it in the UI...
                                    layerNode.get ("direction").asInt ();

                                    sampleZone.setKeyTracking (layerNode.get ("keyTrackEnable").asBoolean () ? 1.0 : 0);

                                    // TODO "offset" ???

                                    if (layerNode.get ("layerLoopModeOverridesSliceLoopMode").asBoolean ())
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
                                    sampleData.addZoneData (sampleZone, false, sampleZone.getLoops ().isEmpty ());

                                    group.addSampleZone (sampleZone);
                                }
                            }
                        }
                    }
                }
            }

            final List<IGroup> groups = Collections.singletonList (group);
            multisampleSource.setGroups (groups);
            final boolean isDrum = false;
            this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (groups), parts, isDrum ? "Drums" : null);
            results.add (multisampleSource);
        }

        return results;
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
        String sampleFileName;
        int    rootNote;
        double tuning;
    }
}
