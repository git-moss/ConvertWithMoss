// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.KontaktIcon;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A Kontakt 5+ program.
 *
 * @author Jürgen Moßgraber
 */
public class Program
{
    //@formatter:off
    private static final byte [] GROUP_PRIVATE_DATA       = { 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1, 0, 18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 1, 3, 0, 0, 0, 0, 0, -128, 63, 0, 1, 0, 0, 0, 0, 0, 0, -128, -65, 0, 0, 0, 0, 0, 0, -128, 63, 0, 0, 0, 0, 0, -1, -1, -1, -1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1 };
    private static final byte [] QUICK_BROWSE_PUBLIC_DATA = { 1, 0, 0, 0, 0, 0 };
    private static final byte [] RAW_OBJECT_PUBLIC_DATA   = { 0, 16, 0, 0, -1, -1, -1, -1, 0, 0, 0, 0 };
    private static final byte [] ZONE_PRIVATE_DATA        = { -1, -1, -1, -1, 0, 0, 0, 0, 1, 16, 4, 4, 0, 0, -128, 64, -51, -52, -52, 62, 0, 0, 1, 0, 0, 0, 0, 0, -128, 63, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0 };
    //@formatter:on

    private static final String  NULL_ENTRY               = "(null)";
    private static final int     CHUNK_VERSION_6_8_0      = 0x95;
    private static final int     ZONE_CHUNK_VERSION_6_8_0 = 0x9A;

    private String               name;
    private int                  midiTranspose            = 0;
    private int                  clipLowVelocity          = 0;
    private int                  clipHighVelocity         = 127;
    private int                  clipLowKey               = 0;
    private int                  clipHighKey              = 127;
    private int                  defaultKeySwitch         = 0xFFFF;
    private long                 dfdChannelPreloadSize    = 0xF000;
    private long                 libraryID                = 0;
    private long                 fingerprint              = 0;
    private long                 loadingFlags             = 32;
    private boolean              groupSolo                = false;
    private int                  instrumentCategory1      = 0;
    private int                  instrumentCategory2      = 0;
    private int                  instrumentCategory3      = 0;
    private String               instrumentIconName       = "";
    private String               instrumentCredits        = null;
    private String               instrumentAuthor         = null;
    private String               instrumentURL            = null;
    private float                instrumentVolume         = 0.5f;
    private float                instrumentPan            = 0.0f;
    private float                instrumentTune           = 1.0f;
    private double               sizeOfAllSamples;

    //@formatter:off
    private byte []              additionalData        = { -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1 };
    //@formatter:on

    private final List<Group>    groups                   = new ArrayList<> ();
    private final List<Zone>     zones                    = new ArrayList<> ();


    /**
     * Get the name of the program.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
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
    public void read (final KontaktPresetChunk chunk) throws IOException
    {
        final int chunkId = chunk.getId ();
        if (chunkId != KontaktPresetChunkID.PROGRAM && chunkId != 0)
            throw new IOException (Functions.getMessage ("IDS_NKI5_NOT_A_PROGRAM_CHUNK"));

        this.readProgramData (chunk.getPublicData ());

        for (final KontaktPresetChunk presetChunk: chunk.getChildren ())
        {
            final int id = presetChunk.getId ();
            switch (id)
            {
                case KontaktPresetChunkID.GROUP_LIST:
                    this.parseGroupList (presetChunk);
                    break;
                case KontaktPresetChunkID.ZONE_LIST:
                    this.readZoneList (presetChunk);
                    break;
                case KontaktPresetChunkID.VOICE_GROUPS:
                    // Not used
                    break;
                case KontaktPresetChunkID.PARAMETER_ARRAY_8:
                    // Not used
                    break;
                case KontaktPresetChunkID.PAR_SCRIPT, KontaktPresetChunkID.PAR_MOD_BASE, KontaktPresetChunkID.INSERT_BUS, KontaktPresetChunkID.QUICK_BROWSE_DATA:
                    // Not used
                    break;

                default:
                    throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_CHILD_ID", Integer.toString (id)));
            }
        }
    }


    /**
     * Writes the program into the given preset chunk.
     *
     * @param presetChunk The top Program preset chunk to fill
     * @throws IOException Could not create the chunk
     */
    public void write (final KontaktPresetChunk presetChunk) throws IOException
    {
        presetChunk.setId (KontaktPresetChunkID.PROGRAM);

        this.writeGroupList (presetChunk);
        this.writeZoneList (presetChunk);

        presetChunk.setPublicData (this.writeProgramData ());
    }


    /**
     * Parses the Program data.
     *
     * @param data The data to parse
     * @throws IOException Could not read the data
     */
    public void readProgramData (final byte [] data) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        this.name = StreamUtils.readWithLengthUTF16 (in);

        this.sizeOfAllSamples = StreamUtils.readDouble (in, false);

        // MIDI transpose -24..24
        this.midiTranspose = in.read ();
        if (this.midiTranspose > 128)
            this.midiTranspose -= 256;

        this.setInstrumentVolume (StreamUtils.readFloatLE (in));
        this.setInstrumentPan (StreamUtils.readFloatLE (in));
        this.setInstrumentTune (StreamUtils.readFloatLE (in));

        // Global clipping: Low velocity, high velocity, low key, high key - not really helpful
        this.clipLowVelocity = in.read ();
        this.clipHighVelocity = in.read ();
        this.clipLowKey = in.read ();
        this.clipHighKey = in.read ();

        this.defaultKeySwitch = StreamUtils.readUnsigned16 (in, false);
        this.dfdChannelPreloadSize = StreamUtils.readUnsigned32 (in, false);
        this.libraryID = StreamUtils.readUnsigned32 (in, false);
        this.fingerprint = StreamUtils.readUnsigned32 (in, false);
        this.loadingFlags = StreamUtils.readUnsigned32 (in, false);
        this.groupSolo = in.read () > 0;

        this.setInstrumentIconName (KontaktIcon.getName ((int) StreamUtils.readUnsigned32 (in, false)));
        this.instrumentCredits = StreamUtils.readWithLengthUTF16 (in);
        this.setInstrumentAuthor (StreamUtils.readWithLengthUTF16 (in));
        this.setInstrumentURL (StreamUtils.readWithLengthUTF16 (in));
        if (this.getInstrumentURL ().isBlank () || NULL_ENTRY.equals (this.getInstrumentURL ()))
            this.setInstrumentURL (null);

        // Where is the lookup table for these?
        this.instrumentCategory1 = StreamUtils.readUnsigned16 (in, false);
        this.instrumentCategory2 = StreamUtils.readUnsigned16 (in, false);
        this.instrumentCategory3 = StreamUtils.readUnsigned16 (in, false);

        // Note: 8 (v5.3+), 20 (5.4+), or 24 (v7) more bytes available
        this.additionalData = in.readAllBytes ();
    }


    /**
     * Creates the Program data.
     *
     * @return The created data
     * @throws IOException Could not read the data
     */
    private byte [] writeProgramData () throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        StreamUtils.writeWithLengthUTF16 (out, this.name);

        // The size of all samples
        this.sizeOfAllSamples = 0;
        for (final Zone zone: this.getZones ())
            this.sizeOfAllSamples += zone.getSampleSize ();
        StreamUtils.writeDouble (out, this.sizeOfAllSamples, false);

        out.write (this.midiTranspose < 0 ? this.midiTranspose + 256 : this.midiTranspose);

        StreamUtils.writeFloatLE (out, this.getInstrumentVolume ());
        StreamUtils.writeFloatLE (out, this.getInstrumentPan ());
        StreamUtils.writeFloatLE (out, this.getInstrumentTune ());

        // Global clipping
        out.write (this.clipLowVelocity);
        out.write (this.clipHighVelocity);
        out.write (this.clipLowKey);
        out.write (this.clipHighKey);

        StreamUtils.writeUnsigned16 (out, this.defaultKeySwitch, false);
        StreamUtils.writeUnsigned32 (out, this.dfdChannelPreloadSize, false);
        StreamUtils.writeUnsigned32 (out, this.libraryID, false);
        StreamUtils.writeUnsigned32 (out, this.fingerprint, false);
        StreamUtils.writeUnsigned32 (out, this.loadingFlags, false);
        out.write (this.groupSolo ? 1 : 0);

        StreamUtils.writeUnsigned32 (out, KontaktIcon.getID (this.getInstrumentIconName ()), false);

        StreamUtils.writeWithLengthUTF16 (out, this.instrumentCredits);
        StreamUtils.writeWithLengthUTF16 (out, this.getInstrumentAuthor ());
        StreamUtils.writeWithLengthUTF16 (out, this.getInstrumentURL () == null ? NULL_ENTRY : this.getInstrumentURL ());

        StreamUtils.writeUnsigned16 (out, this.instrumentCategory1, false);
        StreamUtils.writeUnsigned16 (out, this.instrumentCategory2, false);
        StreamUtils.writeUnsigned16 (out, this.instrumentCategory3, false);

        out.write (this.additionalData);

        return out.toByteArray ();
    }


    /**
     * Read all groups from the group list.
     *
     * @param groupListChunk The chunk which contains the group list
     * @throws IOException Could not read the groups
     */
    private void parseGroupList (final KontaktPresetChunk groupListChunk) throws IOException
    {
        for (final KontaktPresetChunk groupChunk: groupListChunk.getChildren ())
        {
            final Group group = new Group ();
            group.parse (groupChunk);
            this.getGroups ().add (group);
        }
    }


    /**
     * Create the group list from the groups of the program.
     *
     * @param programPresetChunk The chunk which contains the group list
     * @throws IOException Could not read the groups
     */
    private void writeGroupList (final KontaktPresetChunk programPresetChunk) throws IOException
    {
        final List<KontaktPresetChunk> groupChunks = new ArrayList<> ();

        final KontaktPresetChunk groupListChunk = findChunk (programPresetChunk.getChildren (), KontaktPresetChunkID.GROUP_LIST);
        if (groupListChunk == null)
            return;
        final KontaktPresetChunk templateGroupChunk = findChunk (groupListChunk.getChildren (), KontaktPresetChunkID.GROUP);
        if (templateGroupChunk == null)
            return;

        for (final Group group: this.getGroups ())
        {
            final KontaktPresetChunk groupChunk = new KontaktPresetChunk ();
            groupChunk.setId (KontaktPresetChunkID.GROUP);
            groupChunk.setVersion (CHUNK_VERSION_6_8_0);
            groupChunk.setPrivateData (GROUP_PRIVATE_DATA);
            groupChunk.setPublicData (group.create (CHUNK_VERSION_6_8_0));
            groupChunk.setChildren (templateGroupChunk.getChildren ());
            groupChunks.add (groupChunk);
        }

        groupListChunk.setChildren (groupChunks);
    }


    /**
     * Read all zones from the zone list.
     *
     * @param presetChunk The chunk which contains the zone list
     * @throws IOException Could not read the zones
     */
    private void readZoneList (final KontaktPresetChunk presetChunk) throws IOException
    {
        this.getZones ().clear ();

        for (final KontaktPresetChunk zoneChunk: presetChunk.getChildren ())
        {
            final Zone zone = new Zone (zoneChunk.getId ());
            zone.read (zoneChunk.getPublicData (), zoneChunk.getVersion ());
            this.getZones ().add (zone);

            for (final KontaktPresetChunk zoneChildChunk: zoneChunk.getChildren ())
                if (zoneChildChunk.getId () == KontaktPresetChunkID.LOOP_ARRAY)
                    parseLoops (zone, zoneChildChunk.getPublicData ());
        }
    }


    /**
     * Create the zone list chunk from the program zone.
     *
     * @param programPresetChunk The chunk which contains the zone list
     * @throws IOException Could not write the zones
     */
    private void writeZoneList (final KontaktPresetChunk programPresetChunk) throws IOException
    {
        final KontaktPresetChunk zoneListChunk = findChunk (programPresetChunk.getChildren (), KontaktPresetChunkID.ZONE_LIST);

        final List<KontaktPresetChunk> zoneChunks = new ArrayList<> ();

        for (int z = 0; z < this.getZones ().size (); z++)
        {
            final Zone zone = this.getZones ().get (z);

            final KontaktPresetChunk zoneChunk = new KontaktPresetChunk ();
            zoneChunks.add (zoneChunk);
            zoneChunk.setId (0);
            zoneChunk.setVersion (ZONE_CHUNK_VERSION_6_8_0);

            final byte [] privateData = new byte [ZONE_PRIVATE_DATA.length];

            // TODO
            final byte [] privateData2 = ZONE_PRIVATE_DATA;// zoneListChunk.getChildren ().get
            // (z).getPrivateData ();
            System.out.println (StringUtils.formatArray (privateData2));

            System.arraycopy (privateData2, 0, privateData, 0, ZONE_PRIVATE_DATA.length);

            privateData[20] = (byte) (z == 0 ? 1 : 0);
            // privateData[21] = (byte) 1; // Selected -> somehow relates to the private data of the
            // program!

            // TODO fix this
            // relevant
            // privateData[14] = (byte) -128;
            // privateData[22] = (byte) 1;
            // privateData[23] = (byte) 0;
            // privateData[79] = (byte) 1;
            // privateData[80] = (byte) 0;

            privateData[ZONE_PRIVATE_DATA.length - 4] = (byte) z;
            System.out.println (StringUtils.formatArray (privateData) + "\n");
            zoneChunk.setPrivateData (privateData);

            // System.out.println (StringUtils.formatArray (privateData));
            // zoneChunk.setPrivateData (privateData2);

            zoneChunk.setPublicData (zone.write (ZONE_CHUNK_VERSION_6_8_0));

            final List<KontaktPresetChunk> zoneChildren = new ArrayList<> ();

            // Create the loop-sub-chunks
            final List<ZoneLoop> loops = zone.getLoops ();
            final ByteArrayOutputStream loopDataOut = new ByteArrayOutputStream ();
            if (loops.isEmpty ())
                loopDataOut.write (0);
            else
            {
                // Create the bit-flags with active loops (up to eight)
                int loopEnablement = 0;
                final int numLoops = Math.min (8, loops.size ());
                for (int i = 0; i < numLoops; i++)
                    loopEnablement += (int) Math.pow (2, i);
                StreamUtils.writeUnsigned16 (loopDataOut, loopEnablement, false);
                for (int i = 0; i < numLoops; i++)
                {
                    StreamUtils.writeUnsigned16 (loopDataOut, 0x60, false);
                    loops.get (i).write (loopDataOut, i + 1 == numLoops);
                }
            }

            final KontaktPresetChunk loopArrayChunk = new KontaktPresetChunk ();
            loopArrayChunk.setId (KontaktPresetChunkID.LOOP_ARRAY);
            loopArrayChunk.setPublicData (loopDataOut.toByteArray ());
            zoneChildren.add (loopArrayChunk);

            // Add unused but necessary chunks

            final KontaktPresetChunk quickBrowseChunk = new KontaktPresetChunk ();
            quickBrowseChunk.setId (KontaktPresetChunkID.QUICK_BROWSE_DATA);
            quickBrowseChunk.setPublicData (QUICK_BROWSE_PUBLIC_DATA);
            zoneChildren.add (quickBrowseChunk);

            final KontaktPresetChunk rawDataChunk = new KontaktPresetChunk ();
            rawDataChunk.setId (KontaktPresetChunkID.PRIVATE_RAW_OBJECT);
            rawDataChunk.setPublicData (RAW_OBJECT_PUBLIC_DATA);
            zoneChildren.add (rawDataChunk);

            zoneChunk.setChildren (zoneChildren);
        }

        zoneListChunk.setChildren (zoneChunks);
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

        for (int i = 0; i < 8; i++)
        {
            final int j = (int) Math.pow (2, i);
            if ((loopEnablement & j) > 0)
            {
                final int u1 = StreamUtils.readUnsigned16 (childDataIn, false);
                if (u1 != 0x60)
                    throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_LOOP_VALUE", Integer.toHexString (u1).toUpperCase ()));

                final ZoneLoop loop = new ZoneLoop ();
                loop.read (childDataIn);
                zone.addLoop (loop);
            }
        }
    }


    /**
     * Fill the program attributes from the given multi-sample instance.
     *
     * @param source The multi-sample to read from
     */
    public void fillFrom (final IMultisampleSource source)
    {
        // TODO this.setMetadata (source, parts);

        // final Map<Integer, Pair<IGroup, Group>> indexedGroups = this.createGroups ();
        //
        // for (final Zone kontaktZone: this.zones)
        // {
        // // Zones without a sample file might be present
        // if (kontaktZone.getFilenameId () < 0)
        // continue;
        //
        // final Integer groupIndex = Integer.valueOf (kontaktZone.getGroupIndex ());
        // final Pair<IGroup, Group> groupPair = indexedGroups.get (groupIndex);
        // if (groupPair == null)
        // throw new IOException (Functions.getMessage ("IDS_NKI5_MISSING_GROUP",
        // groupIndex.toString ()));
        //
        // final IGroup group = groupPair.getKey ();
        // final Group kontaktGroup = groupPair.getValue ();
        //
        // final ISampleZone zone = this.createZone (source, kontaktZone);
        // group.addSampleZone (zone);
        //
        // zone.setStart (kontaktZone.getSampleStart ());
        // zone.setStop (kontaktZone.getNumFrames () - kontaktZone.getSampleEnd ());
        //
        // zone.setKeyLow (kontaktZone.getLowKey ());
        // zone.setKeyHigh (kontaktZone.getHighKey ());
        // final int rootKey = kontaktZone.getRootKey ();
        // zone.setKeyRoot (rootKey);
        //
        // final float volume = this.instrumentVolume * kontaktGroup.getVolume () *
        // kontaktZone.getZoneVolume ();
        // zone.setGain (MathUtils.valueToDb (volume));
        // zone.setPanorama (Math.clamp (this.instrumentPan + kontaktGroup.getPan () +
        // kontaktZone.getZonePan (), -1, 1));
        //
        // zone.setTune (calculateTune (kontaktZone.getZoneTune (), kontaktGroup.getTune (),
        // this.instrumentTune));
        // zone.setKeyTracking (kontaktGroup.isKeyTracking () ? 1 : 0);
        //
        // zone.setVelocityLow (kontaktZone.getLowVelocity ());
        // zone.setVelocityHigh (kontaktZone.getHighVelocity ());
        //
        // zone.setNoteCrossfadeLow (kontaktZone.getFadeLowKey ());
        // zone.setNoteCrossfadeHigh (kontaktZone.getFadeHighKey ());
        // zone.setVelocityCrossfadeLow (kontaktZone.getFadeLowKey ());
        // zone.setVelocityCrossfadeHigh (kontaktZone.getFadeHighVelocity ());
        //
        // // Only on a group level...
        // zone.setReversed (kontaktGroup.isReverse ());
        //
        // // TODO Fill missing info, when understood where it is stored
        // // Bend Up / Down, Filter, Amplitude and Pitch Modulator
        //
        // for (final ZoneLoop zoneLoop: kontaktZone.getLoops ())
        // {
        // final ISampleLoop loop = new DefaultSampleLoop ();
        // final int loopMode = zoneLoop.getMode ();
        // if (loopMode == ZoneLoop.MODE_UNTIL_END || loopMode == ZoneLoop.MODE_UNTIL_RELEASE)
        // {
        // loop.setType (zoneLoop.isAlternating () > 0 ? LoopType.ALTERNATING : LoopType.FORWARDS);
        // loop.setStart (zoneLoop.getLoopStart ());
        // loop.setEnd (zoneLoop.getLoopStart () + zoneLoop.getLoopLength ());
        // loop.setCrossfadeInSamples (zoneLoop.getCrossfadeLength ());
        // zone.addLoop (loop);
        // }
        // }
        // }
        //
        // final List<IGroup> sampleGroups = new ArrayList<> ();
        // for (final Pair<IGroup, Group> pair: indexedGroups.values ())
        // sampleGroups.add (pair.getKey ());
        // source.setGroups (sampleGroups);
    }


    private static KontaktPresetChunk findChunk (final List<KontaktPresetChunk> chunks, final int id)
    {
        for (final KontaktPresetChunk chunk: chunks)
            if (chunk.getId () == id)
                return chunk;
        return null;
    }


    /**
     * Get all zones.
     * 
     * @return The zones
     */
    public List<Zone> getZones ()
    {
        return this.zones;
    }


    /**
     * Get the volume of the instrument.
     * 
     * @return The volume in the range of [0..1]
     */
    public float getInstrumentVolume ()
    {
        return this.instrumentVolume;
    }


    /**
     * Set the volume of the instrument.
     * 
     * @param instrumentVolume The volume in the range of [0..1]
     */
    public void setInstrumentVolume (final float instrumentVolume)
    {
        this.instrumentVolume = instrumentVolume;
    }


    /**
     * Get the instruments panning.
     * 
     * @return The panning in the range of [-1..1]
     */
    public float getInstrumentPan ()
    {
        return this.instrumentPan;
    }


    /**
     * Set the instruments panning.
     * 
     * @param instrumentPan The panning in the range of [-1..1]
     */
    public void setInstrumentPan (final float instrumentPan)
    {
        this.instrumentPan = instrumentPan;
    }


    /**
     * Get the instruments tuning.
     * 
     * @return The tuning in the range of [0..1]
     */
    public float getInstrumentTune ()
    {
        return this.instrumentTune;
    }


    /**
     * Set the instruments tuning.
     * 
     * @param instrumentTune The tuning in the range of [0..1]
     */
    public void setInstrumentTune (final float instrumentTune)
    {
        this.instrumentTune = instrumentTune;
    }


    /**
     * Get the instruments author.
     * 
     * @return The author
     */
    public String getInstrumentAuthor ()
    {
        return this.instrumentAuthor;
    }


    /**
     * Set the instruments author.
     * 
     * @param instrumentAuthor The author
     */
    public void setInstrumentAuthor (final String instrumentAuthor)
    {
        this.instrumentAuthor = instrumentAuthor;
    }


    /**
     * Get the instruments URL.
     * 
     * @return The URL
     */
    public String getInstrumentURL ()
    {
        return this.instrumentURL;
    }


    /**
     * Set the instruments URL.
     * 
     * @param instrumentURL The URL
     */
    public void setInstrumentURL (final String instrumentURL)
    {
        this.instrumentURL = instrumentURL;
    }


    /**
     * Get the instruments icon name.
     * 
     * @return The icon name
     */
    public String getInstrumentIconName ()
    {
        return this.instrumentIconName;
    }


    /**
     * Get the instruments icon name.
     * 
     * @param instrumentIconName The icon name
     */
    public void setInstrumentIconName (final String instrumentIconName)
    {
        this.instrumentIconName = instrumentIconName;
    }


    /**
     * Get all groups.
     * 
     * @return The groups
     */
    public List<Group> getGroups ()
    {
        return this.groups;
    }
}