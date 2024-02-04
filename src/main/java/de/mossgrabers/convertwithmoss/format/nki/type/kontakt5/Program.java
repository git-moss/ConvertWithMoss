// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.core.Utils;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.ncw.NcwFileSampleData;
import de.mossgrabers.convertwithmoss.format.nki.type.KontaktIcon;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
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
     * Known versions (chunk.getVersion()):
     * <ul>
     * <li>0x80: 4.2.x
     * <li>0xA5: 5.3.0
     * <li>0xA8: 5.4.3 - 5.5.2
     * <li>0xAB: 5.6.8 - 5.8.1
     * <li>0xAE: 6.5.2 - 6.8.0
     * <li>0xAF: 7.1.3 - 7.5.1
     * <li>0xB1: 7.6.0 - 7.6.1
     * </ul>
     *
     * @param chunk The chunk from which to read the program data
     * @throws IOException Could not read the program
     */
    public void parse (final PresetChunk chunk) throws IOException
    {
        final int chunkId = chunk.getId ();
        if (chunkId != PresetChunkID.PROGRAM && chunkId != 0)
            throw new IOException (Functions.getMessage ("IDS_NKI5_NOT_A_PROGRAM_CHUNK"));

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
                    throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_CHILD_ID", Integer.toString (id)));
            }
        }
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

        this.instrumentIconName = KontaktIcon.getName ((int) StreamUtils.readUnsigned32 (in, false));

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
    public void fillInto (final DefaultMultisampleSource source) throws IOException
    {
        this.setMetadata (source);

        final Map<Integer, Pair<DefaultGroup, Group>> indexedGroups = this.createGroups ();

        for (final Zone kontaktZone: this.zones)
        {
            // Zones without a sample file might be present
            if (kontaktZone.getFilenameId () < 0)
                continue;

            final Integer groupIndex = Integer.valueOf (kontaktZone.getGroupIndex ());
            final Pair<DefaultGroup, Group> groupPair = indexedGroups.get (groupIndex);
            if (groupPair == null)
                throw new IOException (Functions.getMessage ("IDS_NKI5_MISSING_GROUP", groupIndex.toString ()));

            final DefaultGroup defaultGroup = groupPair.getKey ();
            final Group group = groupPair.getValue ();

            final ISampleZone zone = this.createZone (source, kontaktZone);
            defaultGroup.addSampleMetadata (zone);

            zone.setStart (kontaktZone.getSampleStart ());
            zone.setStop (kontaktZone.getNumFrames () - kontaktZone.getSampleEnd ());

            zone.setKeyLow (kontaktZone.getLowKey ());
            zone.setKeyHigh (kontaktZone.getHighKey ());
            final int rootKey = kontaktZone.getRootKey ();
            zone.setKeyRoot (rootKey);

            final float volume = this.instrumentVolume + kontaktZone.getZoneVolume ();
            zone.setGain (Utils.valueToDb (volume));
            zone.setPanorama (Utils.clamp (this.instrumentPan + kontaktZone.getZonePan (), -1, 1));

            zone.setTune (calculateTune (kontaktZone.getZoneTune (), group.getTune (), this.instrumentTune));

            zone.setVelocityLow (kontaktZone.getLowVelocity ());
            zone.setVelocityHigh (kontaktZone.getHighVelocity ());

            zone.setNoteCrossfadeLow (kontaktZone.getFadeLowKey ());
            zone.setNoteCrossfadeHigh (kontaktZone.getFadeHighKey ());
            zone.setVelocityCrossfadeLow (kontaktZone.getFadeLowKey ());
            zone.setVelocityCrossfadeHigh (kontaktZone.getFadeHighVelocity ());

            // Only on a group level...
            zone.setReversed (group.isReverse ());

            // TODO fill missing info
            // sampleMetadata.setBendUp ();
            // sampleMetadata.setBendDown ();
            // sampleMetadata.setFilter ();
            // sampleMetadata.getAmplitudeModulator ()
            // sampleMetadata.getPitchModulator ()

            for (final ZoneLoop zoneLoop: kontaktZone.getLoops ())
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                if (zoneLoop.getAlternatingLoop () > 0)
                    loop.setType (LoopType.ALTERNATING);
                loop.setStart (zoneLoop.getLoopStart ());
                final int loopLength = zoneLoop.getLoopLength ();
                loop.setEnd (zoneLoop.getLoopStart () + loopLength);
                loop.setCrossfade (zoneLoop.getCrossfadeLength () / (double) loopLength);
                zone.addLoop (loop);
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


    private ISampleZone createZone (final DefaultMultisampleSource source, final Zone zone) throws IOException
    {
        final int filenameId = zone.getFilenameId ();
        if (filenameId < 0 || filenameId >= this.filePaths.size ())
            throw new IOException (Functions.getMessage ("IDS_NKI5_WRONG_FILE_INDEX", Integer.toString (filenameId)));

        // Check if it is an absolute path, try to find the sample file...
        final String filename = this.filePaths.get (filenameId);
        File sampleFile = new File (filename);
        if (sampleFile.isAbsolute ())
        {
            if (!sampleFile.exists ())
                sampleFile = new File (source.getSourceFile ().getParent (), sampleFile.getName ());
        }
        else
            sampleFile = new File (source.getSourceFile ().getParent (), filename);

        final ISampleData sampleData;
        // Ignore non-existing files since it might be in a monolith
        if (!sampleFile.exists ())
            sampleData = null;
        else if (filename.toLowerCase ().endsWith (".ncw"))
            sampleData = new NcwFileSampleData (sampleFile);
        else
            sampleData = new WavFileSampleData (sampleFile);
        return new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
    }


    private void setMetadata (final DefaultMultisampleSource source)
    {
        source.setName (this.name);

        final IMetadata metadata = source.getMetadata ();

        if (this.instrumentAuthor != null && !this.instrumentAuthor.isBlank ())
            metadata.setCreator (this.instrumentAuthor);
        if (this.instrumentURL != null && !this.instrumentURL.isBlank ())
            metadata.setDescription (this.instrumentURL);
        metadata.setCategory (this.instrumentIconName);
    }


    private static double calculateTune (final double zoneTune, final double groupTune, final double progTune)
    {
        // All three tune values are stored logarithmically
        final double value = 12.0 * Math.log (zoneTune * groupTune * progTune) / Math.log (2);
        return Math.round (value * 100000) / 100000.0;
    }
}