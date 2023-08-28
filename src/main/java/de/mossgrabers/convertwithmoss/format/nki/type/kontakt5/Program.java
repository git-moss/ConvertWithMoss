// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.core.Utils;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.KontaktIcon;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * A Kontakt 5+ program.
 *
 * @author Jürgen Moßgraber
 */
public class Program
{
    private static final String NULL_ENTRY = "(null)";

    private String              name;
    private String              instrumentIconName;
    private String              instrumentAuthor;
    private String              instrumentURL;
    private float               instrumentVolume;
    private float               instrumentPan;
    private float               instrumentTune;
    private final List<Group>   groups     = new ArrayList<> ();
    private final List<Zone>    zones      = new ArrayList<> ();
    private final List<String>  filePaths;


    /**
     * Constructor.
     *
     * @param filePaths The list of file paths for external audio samples referenced from the
     *            program.
     */
    public Program (final List<String> filePaths)
    {
        this.filePaths = filePaths;
    }


    /**
     * Parse the program data from a Program preset chunk.
     *
     * @param chunk The chunk from which to read the program data
     * @param filePaths The list with all referenced files
     * @throws IOException Could not read the program
     */
    public void parse (final PresetChunk chunk, final List<String> filePaths) throws IOException
    {
        if (chunk.getId () != PresetChunkID.PROGRAM)
            throw new IOException ("Not a program chunk!");

        this.readProgramData (chunk.getPublicData ());

        for (final PresetChunk presetChunk: chunk.getChildren ())
        {
            final int id = presetChunk.getId ();
            switch (id)
            {
                case PresetChunkID.GROUP_LIST:
                    this.parseGroupList (presetChunk);
                    break;
                case PresetChunkID.ZONE_LIST:
                    this.parseZoneList (presetChunk);
                    break;
                case PresetChunkID.VOICE_GROUPS:
                    // Not used
                    break;
                case PresetChunkID.PARAMETER_ARRAY_8:
                    // Not used
                    break;
                case PresetChunkID.PAR_SCRIPT, PresetChunkID.PAR_MOD_BASE, PresetChunkID.INSERT_BUS, PresetChunkID.QUICK_BROWSE_DATA:
                    // Not used
                    break;

                default:
                    throw new IOException ("Unsupported child ID: " + id);
            }
        }

        // Known versions:
        // 0x80: 4.2.x
        // 0xA5: 5.3.0
        // 0xA8: 5.4.3 - 5.5.2
        // 0xAB: 5.6.8 - 5.8.1
        // 0xAE: 6.5.2 - 6.8.0
        // 0xAF: 7.1.3
        final int version = chunk.getVersion ();
        if (version > 0xAF)
            throw new IOException ("Unsupported Program Version: " + Integer.toHexString (version).toUpperCase ());
    }


    /**
     * Parses the Program data.
     *
     * @param data The data to parse
     * @throws IOException Could not read the data
     */
    private void readProgramData (final byte [] data) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        this.name = StreamUtils.readWithLengthUTF16 (in);

        // The size of all samples
        StreamUtils.readDoubleLE (in.readNBytes (8));

        // MIDI transpose -24..24, not really helpful
        in.read ();

        this.instrumentVolume = StreamUtils.readFloatLE (in.readNBytes (4));
        this.instrumentPan = StreamUtils.readFloatLE (in.readNBytes (4));
        this.instrumentTune = StreamUtils.readFloatLE (in.readNBytes (4));

        // Global clipping: Low velocity, high velocity, low key, high key - not really helpful
        in.read ();
        in.read ();
        in.read ();
        in.read ();

        // Default Key Switch
        in.readNBytes (2);

        // DFD Channel Pre-load Size
        in.readNBytes (4);

        // Library ID
        in.readNBytes (4);

        // Fingerprint
        in.readNBytes (4);

        // Loading Flags
        in.readNBytes (4);

        // Group Solo
        in.read ();

        this.instrumentIconName = KontaktIcon.getName (StreamUtils.readUnsigned32 (in, false));

        // Ignore instrument credits
        StreamUtils.readWithLengthUTF16 (in);
        this.instrumentAuthor = StreamUtils.readWithLengthUTF16 (in);
        this.instrumentURL = StreamUtils.readWithLengthUTF16 (in);
        if (this.instrumentURL.isBlank () || NULL_ENTRY.equals (this.instrumentURL))
            this.instrumentURL = null;

        // Instrument Category 1 - where is the lookup table for these?
        in.readNBytes (2);
        // Instrument Category 2
        in.readNBytes (2);
        // Instrument Category 3
        in.readNBytes (2);

        // Note: 8 (v5.3+), 20 (5.4+), or 24 (v7) more bytes available
    }


    /**
     * Read all groups from the group list.
     *
     * @param presetChunk The chunk which contains the group list
     * @throws IOException Could not read the groups
     */
    private void parseGroupList (final PresetChunk presetChunk) throws IOException
    {
        for (final PresetChunk groupChunk: presetChunk.getChildren ())
        {
            final Group group = new Group ();
            group.parse (groupChunk.getPublicData (), groupChunk.getVersion ());
            this.groups.add (group);
        }
    }


    /**
     * Read all zones from the zone list.
     *
     * @param presetChunk The chunk which contains the zone list
     * @throws IOException Could not read the zones
     */
    private void parseZoneList (final PresetChunk presetChunk) throws IOException
    {
        this.zones.clear ();

        for (final PresetChunk zoneChunk: presetChunk.getChildren ())
        {
            final Zone zone = new Zone (zoneChunk.getId ());
            zone.parse (zoneChunk.getPublicData (), zoneChunk.getVersion ());
            this.zones.add (zone);

            for (final PresetChunk zoneChildChunk: zoneChunk.getChildren ())
            {
                if (zoneChildChunk.getId () == PresetChunkID.LOOP_ARRAY)
                    parseLoops (zone, zoneChildChunk.getPublicData ());
            }
        }
    }


    /**
     * Parse a loop list.
     *
     * @param zone The zone to which to add the loops
     * @param data The loop list data
     * @throws IOException Could not read the data
     */
    private static void parseLoops (final Zone zone, final byte [] data) throws IOException
    {
        if (data.length < 2)
            return;

        final ByteArrayInputStream childDataIn = new ByteArrayInputStream (data);
        final int loopEnablement = StreamUtils.readUnsigned16 (childDataIn, false);
        final int u1 = StreamUtils.readUnsigned16 (childDataIn, false);
        if (u1 != 0x60)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_LOOP_VALUE", Integer.toHexString (u1).toUpperCase ()));

        for (int i = 0; i < 8; i++)
        {
            final int j = (int) Math.pow (2, i);
            if ((loopEnablement & j) > 0)
            {
                final ZoneLoop loop = new ZoneLoop ();
                loop.parse (childDataIn);
                zone.addLoop (loop);
            }
        }
    }


    /**
     * Fill the given multisample instance with data from the program.
     *
     * @param source The multisample to fill
     * @throws IOException Error finding samples
     */
    public void fillInto (final MultisampleSource source) throws IOException
    {
        this.setMetadata (source);

        final Map<Integer, Pair<DefaultGroup, Group>> indexedGroups = this.createGroups ();

        for (final Zone zone: this.zones)
        {
            final Integer groupIndex = Integer.valueOf (zone.getGroupIndex ());
            final Pair<DefaultGroup, Group> groupPair = indexedGroups.get (groupIndex);
            if (groupPair == null)
                throw new IOException (Functions.getMessage ("IDS_NKI5_MISSING_GROUP", groupIndex.toString ()));

            final DefaultGroup defaultGroup = groupPair.getKey ();
            final Group group = groupPair.getValue ();

            final DefaultSampleMetadata sampleMetadata = new DefaultSampleMetadata (this.getFilename (source, zone));
            defaultGroup.addSampleMetadata (sampleMetadata);

            sampleMetadata.setStart (zone.getSampleStart ());
            sampleMetadata.setStop (zone.getNumFrames () - zone.getSampleEnd ());

            sampleMetadata.setKeyLow (zone.getLowKey ());
            sampleMetadata.setKeyHigh (zone.getHighKey ());
            final int rootKey = zone.getRootKey ();
            sampleMetadata.setKeyRoot (rootKey);

            final float volume = this.instrumentVolume + zone.getZoneVolume ();
            sampleMetadata.setGain (Utils.valueToDb (volume));
            sampleMetadata.setPanorama (Utils.clamp (this.instrumentPan + zone.getZonePan (), -1, 1));

            final int rootNote = zone.getRootNote ();
            final int offset = rootNote == 0 ? 0 : (rootNote - rootKey) * 100;

            sampleMetadata.setTune ((offset + calculateTune (zone.getZoneTune (), group.getTune (), this.instrumentTune)) / 1000);

            sampleMetadata.setVelocityLow (zone.getLowVelocity ());
            sampleMetadata.setVelocityHigh (zone.getHighVelocity ());

            sampleMetadata.setNoteCrossfadeLow (zone.getFadeLowKey ());
            sampleMetadata.setNoteCrossfadeHigh (zone.getFadeHighKey ());
            sampleMetadata.setVelocityCrossfadeLow (zone.getFadeLowKey ());
            sampleMetadata.setVelocityCrossfadeHigh (zone.getFadeHighVelocity ());

            // Only on a group level...
            sampleMetadata.setReversed (group.isReverse ());

            // TODO fill missing info
            // sampleMetadata.setBendUp ();
            // sampleMetadata.setBendDown ();
            // sampleMetadata.setFilter ();
            // sampleMetadata.getAmplitudeModulator ()
            // sampleMetadata.getPitchModulator ()

            for (final ZoneLoop zoneLoop: zone.getLoops ())
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                if (zoneLoop.getAlternatingLoop () > 0)
                    loop.setType (LoopType.ALTERNATING);
                loop.setStart (zoneLoop.getLoopStart ());
                final int loopLength = zoneLoop.getLoopLength ();
                loop.setEnd (zoneLoop.getLoopStart () + loopLength);
                loop.setCrossfade (zoneLoop.getCrossfadeLength () / (double) loopLength);
                sampleMetadata.addLoop (loop);
            }
        }

        final List<IGroup> defaultGroups = new ArrayList<> ();
        for (final Pair<DefaultGroup, Group> pair: indexedGroups.values ())
            defaultGroups.add (pair.getKey ());
        source.setGroups (defaultGroups);
    }


    /**
     * Creates all groups.
     *
     * @return The indexed groups
     */
    private Map<Integer, Pair<DefaultGroup, Group>> createGroups ()
    {
        final Map<Integer, Pair<DefaultGroup, Group>> map = new TreeMap<> ();
        for (int i = 0; i < this.groups.size (); i++)
        {
            final Group group = this.groups.get (i);
            final DefaultGroup defaultGroup = new DefaultGroup ();
            defaultGroup.setName (group.getName ());
            if (group.isReleaseTrigger ())
                defaultGroup.setTrigger (TriggerType.RELEASE);
            map.put (Integer.valueOf (i), new Pair<> (defaultGroup, group));
        }
        return map;
    }


    private File getFilename (final MultisampleSource source, final Zone zone) throws IOException
    {
        final int filenameId = zone.getFilenameId ();
        if (filenameId < 0 || filenameId >= this.filePaths.size ())
            throw new IOException (Functions.getMessage ("IDS_NKI5_NO_WRONG_FILE_INDEX", Integer.toString (filenameId)));

        final String filename = this.filePaths.get (filenameId);
        if (filename.endsWith (".ncw"))
            throw new IOException (Functions.getMessage ("IDS_NKI5_COMPRESSED_SAMPLES_NOT_SUPPORTED"));

        return new File (source.getSourceFile ().getParent (), filename);
    }


    private void setMetadata (final MultisampleSource source)
    {
        source.setName (this.name);

        if (this.instrumentAuthor != null && !this.instrumentAuthor.isBlank ())
            source.setCreator (this.instrumentAuthor);
        if (this.instrumentURL != null && !this.instrumentURL.isBlank ())
            source.setDescription (this.instrumentURL);
        source.setCategory (this.instrumentIconName);
    }


    private static double calculateTune (final double zoneTune, final double groupTune, final double progTune)
    {
        // All three tune values are stored logarithmically
        return 0.12d * Math.log (zoneTune * groupTune * progTune) / Math.log (2);
    }
}