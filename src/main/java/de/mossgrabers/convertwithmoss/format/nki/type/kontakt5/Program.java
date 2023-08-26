// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.core.Utils;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultVelocityLayer;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.KontaktIcon;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * A Kontakt 5+ program.
 *
 * @author Jürgen Moßgraber
 */
public class Program
{
    private static final String     NULL_ENTRY = "(null)";

    private String                  name;
    private String                  instrumentIconName;
    private String                  instrumentAuthor;
    private String                  instrumentURL;
    private final List<PresetChunk> children   = new ArrayList<> ();
    private List<Zone>              zones      = new ArrayList<> ();
    private final List<String>      filePaths;

    private float                   instrumentVolume;

    private float                   instrumentPan;

    private float                   instrumentTune;


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
     * @param chunk
     * @param filePaths
     * @throws IOException
     */
    public void parse (final PresetChunk chunk, final List<String> filePaths) throws IOException
    {
        if (chunk.getId () != PresetChunkID.PROGRAM)
            throw new IOException ("Not a program chunk!");

        this.readPublicData (chunk.getPublicData ());

        for (final PresetChunk presetChunk: chunk.getChildren ())
        {
            int id = presetChunk.getId ();
            switch (id)
            {
                case PresetChunkID.GROUP_LIST:
                    // TODO
                    break;
                case PresetChunkID.ZONE_LIST:
                    this.parseZoneList (presetChunk);
                    break;
                case PresetChunkID.VOICE_GROUPS:
                    // TODO
                    break;
                case PresetChunkID.PARAMETER_ARRAY_8, PresetChunkID.PAR_SCRIPT, PresetChunkID.INSERT_BUS, PresetChunkID.QUICK_BROWSE_DATA:
                    // Not used
                    break;
                default:
                    throw new IOException ("Unsupported child ID: " + id);
            }
        }

        // Known versions:
        // 4.2.x 0x80
        // 5.3.0 0xA5
        // 5.4.3 0xA8
        // 5.5.2 0xA8
        // 5.6.8 0xAB
        // 5.8.1 0xAB
        // 6.5.2 0xAE
        // 6.6.0 0xAE
        // 6.7.1 0xAE
        // 6.8.0 0xAE
        // 7.1.3 0xAF
        final int version = chunk.getVersion ();
        if (version > 0xAF)
            throw new IOException ("Unsupported Program Version: " + Integer.toHexString (version).toUpperCase ());
    }


    private void parseZoneList (final PresetChunk presetChunk) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (presetChunk.getPublicData ());

        final int arrayLength = StreamUtils.readUnsigned32 (in, false);

        this.zones.clear ();

        for (int zoneIndex = 0; zoneIndex < arrayLength; zoneIndex++)
        {
            final Zone zone = new Zone ();

            // Number of children
            StreamUtils.readUnsigned32 (in, false);
            // Is data structured
            in.read ();
            final int version = StreamUtils.readUnsigned16 (in, false);

            final int privateDataSize = StreamUtils.readUnsigned32 (in, false);
            // The private data
            in.readNBytes (privateDataSize);

            final int publicDataSize = StreamUtils.readUnsigned32 (in, false);
            final byte [] publicData = in.readNBytes (publicDataSize);

            final int childrenDataSize = StreamUtils.readUnsigned32 (in, false);
            final byte [] childrenData = in.readNBytes (childrenDataSize);
            final ByteArrayInputStream inChildren = new ByteArrayInputStream (childrenData);
            while (inChildren.available () > 0)
            {
                // TODO contains LoopArray 0x39
                final PresetChunk object = new PresetChunk ();
                object.parse (inChildren);
                this.children.add (object);
            }

            zone.parse (publicData, version);
            this.zones.add (zone);
        }
    }


    /**
     * Parses the public data block of a Program chunk.
     *
     * @param data The data to parse
     * @throws IOException Could not read the data
     */
    private void readPublicData (final byte [] data) throws IOException
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
     * Fill the given multisample instance with data from the program.
     *
     * @param source The multisample to fill
     * @throws IOException Error finding samples
     */
    public void fillInto (final MultisampleSource source) throws IOException
    {
        source.setName (this.name);

        if (this.instrumentAuthor != null && !this.instrumentAuthor.isBlank ())
            source.setCreator (this.instrumentAuthor);
        if (this.instrumentURL != null && !this.instrumentURL.isBlank ())
            source.setDescription (this.instrumentURL);
        source.setCategory (this.instrumentIconName);

        final List<ISampleMetadata> samples = new ArrayList<> ();

        for (final Zone zone: this.zones)
        {
            final int filenameId = zone.getFilenameId ();
            if (filenameId < 0 || filenameId >= this.filePaths.size ())
                throw new IOException (Functions.getMessage ("IDS_NKI5_NO_WRONG_FILE_INDEX", Integer.toString (filenameId)));

            final File sampleFile = new File (source.getSourceFile ().getParent (), this.filePaths.get (filenameId));
            final DefaultSampleMetadata sampleMetadata = new DefaultSampleMetadata (sampleFile);
            samples.add (sampleMetadata);

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

            // TODO set the group tune
            sampleMetadata.setTune (offset + calculateTune (zone.getZoneTune (), 0, this.instrumentTune));

            sampleMetadata.setVelocityLow (zone.getLowVelocity ());
            sampleMetadata.setVelocityHigh (zone.getHighVelocity ());

            sampleMetadata.setNoteCrossfadeLow (zone.getFadeLowKey ());
            sampleMetadata.setNoteCrossfadeHigh (zone.getFadeHighKey ());
            sampleMetadata.setVelocityCrossfadeLow (zone.getFadeLowKey ());
            sampleMetadata.setVelocityCrossfadeHigh (zone.getFadeHighVelocity ());

            // Only on a group level...
            // sampleMetadata.setPlayLogic (group.getPlayLogic ());
            // sampleMetadata.setReversed ();
            // sampleMetadata.setTrigger (TriggerType.RELEASE);

            // TODO fill missing info
            // sampleMetadata.setBendUp ();
            // sampleMetadata.setBendDown ();
            // sampleMetadata.setFilter ();
            // sampleMetadata.getAmplitudeModulator ()
            // sampleMetadata.getPitchModulator ()

            // addLoop (final ISampleLoop loop)
        }

        // TODO Group by velocity layer

        source.setVelocityLayers (Collections.singletonList (new DefaultVelocityLayer (samples)));
    }


    private static double calculateTune (final double zoneTune, final double groupTune, final double progTune)
    {
        // All three tune values are stored logarithmically
        return 0.12d * Math.log (zoneTune * groupTune * progTune) / Math.log (2);
    }
}
