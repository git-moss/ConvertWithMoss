// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.tools.ui.Functions;


/**
 * Builds the JSON document of a MPC 3 project file (*.xpj) from a template which carries the full
 * 'SerialisableProjectData' structure of an empty project written by MPC software 3.10: one
 * key-group track followed by the bus tracks (8 sub-mixes, 4 returns and 16 outputs). Each
 * instrument of a performance becomes one key-group track, a copy of the template's key-group
 * track which is filled like a track file (see {@link MPC3TrackFile}). The track settings of the
 * instrument are stored in the track (MIDI input channel) and in its program (mixer volume and
 * panorama, key-group transposition).
 *
 * @author Jürgen Moßgraber
 */
public class MPC3ProjectFile
{
    /** The data type identifier in the third line of the file header. */
    public static final String   HEADER_DATA_TYPE    = "SerialisableProjectData";
    /** The suffix of the folder which holds the samples of a project. */
    public static final String   PROJECT_DATA_SUFFIX = "_[ProjectData]";
    /** The maximum number of tracks of a project. */
    public static final int      MAX_TRACKS          = 128;
    /** The mixer volume of 0dB. */
    public static final double   VOLUME_0_DB         = 0.7079457640647888;

    private static final String  TEMPLATE            = "de/mossgrabers/convertwithmoss/templates/mpc/project-template.json";
    /** Projects require MPC 3.10, all versions since 3.4 support 8 layers in a key-group. */
    private static final int     LAYER_LIMIT         = 8;
    /** The smallest mixer volume which is converted to dB, avoids an infinite value. */
    private static final double  MIN_VOLUME          = 0.00001;

    /**
     * The colours of the tracks. The first 3 are the ones which the MPC software assigns to new
     * tracks, the others are the colours of the clip rows of a project, all from the palette of the
     * MPC.
     */
    private static final int []  TRACK_COLOURS       =
    {
        0xA2FF00,
        0x0022FF,
        0xB200FF,
        0x6F00FF,
        0xFF00BB,
        0xFF8800,
        0x00FF33,
        0x00F7FF
    };

    private final ObjectMapper   mapper              = new ObjectMapper ();
    private final MPC3TrackFile  trackFile;
    private final ObjectNode     root;
    private final ObjectNode     dataNode;
    private final ObjectNode     trackPrototype;
    private final ObjectNode     samplePrototype;
    private final List<String>   busTrackNames       = new ArrayList<> ();
    private final List<JsonNode> tracks              = new ArrayList<> ();
    private final ArrayNode      samplesNode;
    private final Set<String>    sampleNames         = new HashSet<> ();
    private final Set<String>    usedTrackNames      = new HashSet<> ();


    /**
     * Constructor.
     *
     * @throws IOException Could not load or parse the templates
     */
    public MPC3ProjectFile () throws IOException
    {
        this.trackFile = new MPC3TrackFile (LAYER_LIMIT);
        this.samplePrototype = this.trackFile.createSamplePrototype ();

        this.root = (ObjectNode) this.mapper.readTree (Functions.textFileFor (TEMPLATE));
        this.dataNode = (ObjectNode) this.root.get ("data");
        this.samplesNode = (ArrayNode) this.dataNode.get ("samples");
        this.samplesNode.removeAll ();

        // The first track of the template is the key-group track, all others are the bus tracks
        // which every project contains
        final ArrayNode tracksNode = (ArrayNode) this.dataNode.get ("tracks");
        this.trackPrototype = (ObjectNode) tracksNode.remove (0);
        for (final JsonNode trackNode: tracksNode)
        {
            final String trackName = trackNode.get ("name").asText ();
            this.busTrackNames.add (trackName);
            this.usedTrackNames.add (trackName);
        }
    }


    /**
     * Add a key-group track for an instrument. The samples of the instrument need to be stored
     * afterwards with the names of its zones, which are prefixed with the name of the track.
     *
     * @param instrumentSource The instrument
     * @return The number of created key-groups
     */
    public int addTrack (final IInstrumentSource instrumentSource)
    {
        final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();
        final String instrumentName = instrumentSource.getName ();
        final String trackName = this.createUniqueTrackName (instrumentName == null || instrumentName.isBlank () ? multisampleSource.getName () : instrumentName);

        // There is no fine tuning of a track, it is added to the zones
        final int tuning = instrumentSource.getTuning ();
        if (tuning != 0)
            for (final IGroup group: multisampleSource.getGroups ())
                for (final ISampleZone zone: group.getSampleZones ())
                    zone.setTuning (zone.getTuning () + tuning / 100.0);

        // The tracks contain no sample list, the project lists the samples of all tracks
        final ObjectNode trackNode = this.trackPrototype.deepCopy ();
        final ArrayNode trackSamplesNode = this.mapper.createArrayNode ();
        final int numKeygroups = this.trackFile.fillTrack (trackNode, trackSamplesNode, this.samplePrototype, multisampleSource, SafeFileNames.create (trackName));
        for (final JsonNode sampleNode: trackSamplesNode)
            if (this.sampleNames.add (sampleNode.get ("name").asText ()))
                this.samplesNode.add (sampleNode);
        trackNode.put ("name", trackName);
        trackNode.put ("colour", TRACK_COLOURS[this.tracks.size () % TRACK_COLOURS.length]);
        // Only the first track is armed for recording, as in a new project
        trackNode.put ("recordArm", this.tracks.isEmpty ());
        ((ObjectNode) trackNode.get ("midiInputRoute")).put ("inputChannel", toInputChannel (instrumentSource.getMidiChannel ()));

        final ObjectNode programNode = (ObjectNode) trackNode.get ("program");
        programNode.put ("name", trackName);
        final ObjectNode mixableNode = (ObjectNode) programNode.get ("mixable");
        mixableNode.put ("volume", dbToVolume (instrumentSource.getGain ()));
        mixableNode.put ("pan", (Math.clamp (instrumentSource.getPanning (), -1.0, 1.0) + 1.0) / 2.0);
        ((ObjectNode) programNode.get ("keygroup")).put ("transpose", instrumentSource.getTranspose ());

        this.tracks.add (trackNode);
        return numKeygroups;
    }


    /**
     * Get the number of added key-group tracks.
     *
     * @return The number of tracks
     */
    public int getNumTracks ()
    {
        return this.tracks.size ();
    }


    /**
     * Create the JSON document of the project with all added tracks.
     *
     * @return The JSON code
     */
    public String createJson ()
    {
        // The key-group tracks are in front of the bus tracks
        final ArrayNode tracksNode = (ArrayNode) this.dataNode.get ("tracks");
        for (int i = 0; i < this.tracks.size (); i++)
            tracksNode.insert (i, this.tracks.get (i));

        // The clip player references all tracks by their names, sorted by name
        final ArrayNode transportMapNode = (ArrayNode) this.dataNode.get ("clipPlayerData").get ("trackClipTransportMap");
        final JsonNode transportPrototype = transportMapNode.get (0);
        final Map<String, JsonNode> transportEntries = new HashMap<> ();
        for (final JsonNode entryNode: transportMapNode)
            transportEntries.put (entryNode.get ("key").asText (), entryNode);
        final List<String> trackNames = new ArrayList<> (this.busTrackNames);
        for (final JsonNode trackNode: this.tracks)
            trackNames.add (trackNode.get ("name").asText ());
        trackNames.sort (null);
        final List<JsonNode> sortedEntries = new ArrayList<> ();
        for (final String trackName: trackNames)
        {
            JsonNode entryNode = transportEntries.get (trackName);
            if (entryNode == null)
            {
                final ObjectNode newEntryNode = transportPrototype.deepCopy ();
                newEntryNode.put ("key", trackName);
                entryNode = newEntryNode;
            }
            sortedEntries.add (entryNode);
        }
        transportMapNode.removeAll ();
        transportMapNode.addAll (sortedEntries);

        return this.root.toPrettyString ();
    }


    /**
     * Convert the volume of the mixer of a program to dB. The volume is the square root of the gain
     * relative to the maximum of +6dB, e.g. 0.708 is 0dB and 0.497 is -6.15dB.
     *
     * @param volume The volume in the range of [0..1]
     * @return The volume in dB rounded to 2 decimal places
     */
    public static double volumeToDb (final double volume)
    {
        return Math.round ((40.0 * Math.log10 (Math.max (volume, MIN_VOLUME)) + 6.0) * 100.0) / 100.0;
    }


    /**
     * Convert a volume in dB to the volume of the mixer of a program.
     *
     * @param volumeDB The volume in dB
     * @return The volume in the range of [0..1], values above +6dB are limited
     */
    public static double dbToVolume (final double volumeDB)
    {
        return Math.clamp (Math.pow (10.0, (volumeDB - 6.0) / 40.0), 0.0, 1.0);
    }


    /**
     * Convert the MIDI input channel of a track to a MIDI channel of an instrument.
     *
     * @param inputChannel The input channel of the track: 0 is all channels, 1-16 the channels
     * @return The MIDI channel in the range of [0..15] or MIDI_CHANNEL_OMNI
     */
    public static int toMidiChannel (final int inputChannel)
    {
        return inputChannel >= 1 && inputChannel <= 16 ? inputChannel - 1 : IInstrumentSource.MIDI_CHANNEL_OMNI;
    }


    /**
     * Convert a MIDI channel of an instrument to the MIDI input channel of a track.
     *
     * @param midiChannel The MIDI channel in the range of [0..15], all other values are converted
     *            to all channels
     * @return The input channel of the track: 0 is all channels, 1-16 the channels
     */
    public static int toInputChannel (final int midiChannel)
    {
        return midiChannel >= 0 && midiChannel <= 15 ? midiChannel + 1 : 0;
    }


    /**
     * Create a track name which is not yet in use by adding a number to the given name. The clip
     * player identifies the tracks by their names.
     *
     * @param baseName The name
     * @return The unique name
     */
    private String createUniqueTrackName (final String baseName)
    {
        final String trimmedName = baseName == null || baseName.isBlank () ? "Keygroup" : baseName.trim ();
        String name = trimmedName;
        for (int counter = 2; this.usedTrackNames.contains (name); counter++)
            name = trimmedName + " " + counter;
        this.usedTrackNames.add (name);
        return name;
    }
}
