// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.AbstractKontaktFormat;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.KontaktIcon;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2.Kontakt2Header;
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

    private static final String         NULL_ENTRY               = "(null)";
    private static final int            VOICE_GROUP_VERSION      = 0x60;
    // Kontakt supports up to 1000 voices
    private static final int            MAX_VOICES               = 1000;
    private static final int            CHUNK_VERSION_6_8_0      = 0x95;
    private static final int            ZONE_CHUNK_VERSION_6_8_0 = 0x9A;
    private static final int            DEFAULT_MAX_VOICES       = 64;
    // Offset of the integer in the additional data which is -1 for programs in a multi
    private static final int            ADDITIONAL_DATA_MULTI    = 12;
    // There are up to 128 voice groups
    private static final int            MAX_VOICE_GROUPS         = 128;
    private static final String         INSTRUMENT_VOICE_GROUP   = "<instrument>";
    // Unknown 3 bytes of a voice group entry, found in all analysed files
    private static final byte []        VOICE_GROUP_FLAGS        =
    {
        1,
        0,
        1
    };
    // The fade time of a voice group in milliseconds
    private static final int            VOICE_GROUP_FADE_TIME    = 10;
    private static final int            LOOP_VERSION             = 0x60;
    // The offset of the zone index (32 bit) and the 'selected' flag in the private zone data
    private static final int            ZONE_PRIVATE_INDEX       = ZONE_PRIVATE_DATA.length - 4;
    private static final int            ZONE_PRIVATE_SELECTED    = 20;

    private int                         slotIndex                = -1;
    private String                      name;
    private int                         midiTranspose            = 0;
    private int                         clipLowVelocity          = 0;
    private int                         clipHighVelocity         = 127;
    private int                         clipLowKey               = 0;
    private int                         clipHighKey              = 127;
    private int                         defaultKeySwitch         = 0xFFFF;
    private long                        dfdChannelPreloadSize    = 0xF000;
    private long                        libraryID                = 0;
    private long                        fingerprint              = 0;
    private long                        loadingFlags             = 32;
    private boolean                     groupSolo                = false;
    private int                         maxVoices                = 0;
    private int                         instrumentCategory1      = 0;
    private int                         instrumentCategory2      = 0;
    private int                         instrumentCategory3      = 0;
    private String                      instrumentIconName       = "";
    private String                      instrumentCredits        = null;
    private String                      instrumentAuthor         = null;
    private String                      instrumentURL            = null;
    private float                       instrumentVolume         = 0.5f;
    private float                       instrumentPan            = 0.0f;
    private float                       instrumentTune           = 1.0f;
    private double                      sizeOfAllSamples;

    //@formatter:off
    private byte []              additionalData        = { -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1 };
    //@formatter:on

    private final List<Group>           groups                   = new ArrayList<> ();
    private final List<Zone>            zones                    = new ArrayList<> ();
    // The voice groups to write, the key is the index of the voice group, the value the number of
    // voices
    private final Map<Integer, Integer> voiceGroups              = new HashMap<> ();
    // The zones from which to take the modulation settings of the groups (one for each group)
    private final List<ISampleZone>     groupSettingZones        = new ArrayList<> ();


    /**
     * Get the 3 instrument categories. These are indices into the same lists as the categories of
     * the Kontakt 2 header.
     *
     * @return The 3 indices, 0 is 'Other'
     */
    public int [] getInstrumentCategories ()
    {
        return new int []
        {
            this.instrumentCategory1,
            this.instrumentCategory2,
            this.instrumentCategory3
        };
    }


    /**
     * Get the credits of the instrument.
     *
     * @return The credits, might be null
     */
    public String getInstrumentCredits ()
    {
        return this.instrumentCredits;
    }


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
     * Get the MIDI transposition of the instrument.
     *
     * @return The transposition in semi-tones in the range of [-24..24]
     */
    public int getMidiTranspose ()
    {
        return this.midiTranspose;
    }


    /**
     * The lower note which should limit the key-range (this note should still sound).
     *
     * @return The note [0..127]
     */
    public int getClipKeyLow ()
    {
        return this.clipLowKey;
    }


    /**
     * The upper note which should limit the key-range (this note should still sound).
     *
     * @return The note [0..127]
     */
    public int getClipKeyHigh ()
    {
        return this.clipHighKey;
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
                    this.readVoiceGroups (presetChunk.getPublicData ());
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

        this.writeVoiceGroups (presetChunk);
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

        this.name = StreamUtils.readUtf16WithLength (in);

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
        this.instrumentCredits = StreamUtils.readUtf16WithLength (in);
        this.setInstrumentAuthor (fixKontakt4Umlauts (StreamUtils.readUtf16WithLength (in)));
        this.setInstrumentURL (StreamUtils.readUtf16WithLength (in));
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
     * Reads the maximum number of voices of the instrument from the voice groups. The data starts
     * with the instrument entry followed by a 128 bit mask of the used voice groups and the entries
     * of the used voice groups (each preceded by a zero byte). Each entry contains: version (16
     * bit, 0x60), name (UTF-16 with length), 3 unknown bytes, the maximum number of voices (32
     * bit), 10 (32 bit, fade time?) and the exclusive group (32 bit, -1 for none).
     *
     * @param data The data of the voice groups chunk
     */
    private void readVoiceGroups (final byte [] data)
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);
        try
        {
            if (StreamUtils.readUnsigned16 (in, false) != VOICE_GROUP_VERSION)
                return;
            StreamUtils.readUtf16WithLength (in);
            in.skipNBytes (3);
            final long voices = StreamUtils.readUnsigned32 (in, false);
            if (voices > 0 && voices <= MAX_VOICES)
                this.maxVoices = (int) voices;
        }
        catch (final IOException _)
        {
            // Unknown format, ignore
        }
    }


    /**
     * Get the maximum number of voices of the instrument.
     *
     * @return The number of voices, 0 if unknown
     */
    public int getMaxVoices ()
    {
        return this.maxVoices;
    }


    /**
     * Replaces the content of the voice groups chunk with the instrument polyphony and the voice
     * groups which are used for exclusive groups.
     *
     * @param programPresetChunk The chunk which contains the voice groups chunk
     * @throws IOException Could not write the voice groups
     */
    private void writeVoiceGroups (final KontaktPresetChunk programPresetChunk) throws IOException
    {
        final KontaktPresetChunk voiceGroupsChunk = findChunk (programPresetChunk.getChildren (), KontaktPresetChunkID.VOICE_GROUPS);
        if (voiceGroupsChunk == null)
            return;

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        writeVoiceGroupEntry (out, INSTRUMENT_VOICE_GROUP, this.maxVoices > 0 ? this.maxVoices : DEFAULT_MAX_VOICES);

        // Bit mask of the used voice groups, the first bit is voice group 1 since 0 is the
        // instrument
        final byte [] usedVoiceGroups = new byte [MAX_VOICE_GROUPS / 8];
        final TreeSet<Integer> indices = new TreeSet<> (this.voiceGroups.keySet ());
        for (final Integer index: indices)
        {
            final int bit = index.intValue () - 1;
            usedVoiceGroups[bit / 8] |= (byte) (1 << bit % 8);
        }
        out.write (usedVoiceGroups);

        // Each voice group entry is preceded by an unknown zero byte
        for (final Integer index: indices)
        {
            out.write (0);
            writeVoiceGroupEntry (out, "Exclusive Group " + index, this.voiceGroups.get (index).intValue ());
        }

        voiceGroupsChunk.setPublicData (out.toByteArray ());
    }


    private static void writeVoiceGroupEntry (final ByteArrayOutputStream out, final String name, final int voices) throws IOException
    {
        StreamUtils.writeUnsigned16 (out, VOICE_GROUP_VERSION, false);
        StreamUtils.writeUtf16WithLength (out, name);
        out.write (VOICE_GROUP_FLAGS);
        StreamUtils.writeUnsigned32 (out, voices, false);
        StreamUtils.writeUnsigned32 (out, VOICE_GROUP_FADE_TIME, false);
        // No exclusion group
        StreamUtils.writeSigned32 (out, -1, false);
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

        StreamUtils.writeUtf16WithLength (out, this.name);

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

        StreamUtils.writeUtf16WithLength (out, this.instrumentCredits == null ? "" : this.instrumentCredits);
        StreamUtils.writeUtf16WithLength (out, this.getInstrumentAuthor () == null ? NULL_ENTRY : this.getInstrumentAuthor ());
        StreamUtils.writeUtf16WithLength (out, this.getInstrumentURL () == null ? NULL_ENTRY : this.getInstrumentURL ());

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

        final List<Group> programGroups = this.getGroups ();
        for (int i = 0; i < programGroups.size (); i++)
        {
            final Group group = programGroups.get (i);
            final KontaktPresetChunk groupChunk = new KontaktPresetChunk ();
            groupChunk.setId (KontaktPresetChunkID.GROUP);
            groupChunk.setVersion (CHUNK_VERSION_6_8_0);
            groupChunk.setPublicData (group.create (CHUNK_VERSION_6_8_0));
            final List<KontaktPresetChunk> templateChildren = templateGroupChunk.getChildren ();
            if (i < this.groupSettingZones.size ())
            {
                // The filter is stored as an insert effect in the private data
                final ISampleZone settingZone = this.groupSettingZones.get (i);
                groupChunk.setPrivateData (GroupModulators.createPrivateData (GROUP_PRIVATE_DATA, settingZone));
                groupChunk.setChildren (createGroupChildren (templateChildren, settingZone));
            }
            else
            {
                groupChunk.setPrivateData (GROUP_PRIVATE_DATA);
                groupChunk.setChildren (templateChildren);
            }
            groupChunks.add (groupChunk);
        }

        groupListChunk.setChildren (groupChunks);
    }


    /**
     * Creates the children of a group chunk: the modulators and the start criteria. The children of
     * the template group are used as the templates for the modulators.
     *
     * @param templateChildren The children of the template group
     * @param zone The zone from which to take the settings
     * @return The children
     */
    private static List<KontaktPresetChunk> createGroupChildren (final List<KontaktPresetChunk> templateChildren, final ISampleZone zone)
    {
        final List<KontaktPresetChunk> children = new ArrayList<> ();
        for (final KontaktPresetChunk child: templateChildren)
            switch (child.getId ())
            {
                case KontaktPresetChunkID.PARAMETER_ARRAY_16:
                    // Contains the AHDSR envelope which modulates the volume
                    children.add (child.getChildren ().isEmpty () ? child : GroupModulators.createInternalModulators (child.getChildren ().get (0), zone));
                    break;

                case KontaktPresetChunkID.PARAMETER_ARRAY_32:
                    // Contains velocity to volume and pitch-bend
                    final List<KontaktPresetChunk> externalModulators = child.getChildren ();
                    children.add (externalModulators.size () < 2 ? child : GroupModulators.createExternalModulators (externalModulators.get (0), externalModulators.get (1), zone));
                    break;

                case KontaktPresetChunkID.STAR_CRIT_LIST:
                    children.add (GroupModulators.createStartCriteria (zone));
                    break;

                default:
                    children.add (child);
                    break;
            }
        return children;
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
        if (zoneListChunk == null)
            throw new IOException ("Zone List Chunk not found.");

        final List<KontaktPresetChunk> zoneChunks = new ArrayList<> ();

        for (int z = 0; z < this.getZones ().size (); z++)
        {
            final Zone zone = this.getZones ().get (z);

            // The reference of a zone in the zone list is the index of its group
            final KontaktPresetChunk zoneChunk = new KontaktPresetChunk ();
            zoneChunks.add (zoneChunk);
            zoneChunk.setId (zone.getGroupIndex ());
            zoneChunk.setVersion (ZONE_CHUNK_VERSION_6_8_0);

            // The private data is not understood, only the 'selected' flag and the index of the
            // zone are set
            final byte [] privateData = ZONE_PRIVATE_DATA.clone ();
            privateData[ZONE_PRIVATE_SELECTED] = (byte) (z == 0 ? 1 : 0);
            privateData[ZONE_PRIVATE_INDEX] = (byte) (z & 0xFF);
            privateData[ZONE_PRIVATE_INDEX + 1] = (byte) (z >> 8 & 0xFF);
            privateData[ZONE_PRIVATE_INDEX + 2] = (byte) (z >> 16 & 0xFF);
            privateData[ZONE_PRIVATE_INDEX + 3] = (byte) (z >> 24 & 0xFF);
            zoneChunk.setPrivateData (privateData);

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
                    loopEnablement |= 1 << i;
                StreamUtils.writeUnsigned16 (loopDataOut, loopEnablement, false);
                for (int i = 0; i < numLoops; i++)
                {
                    StreamUtils.writeUnsigned16 (loopDataOut, LOOP_VERSION, false);
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
                if (u1 != LOOP_VERSION)
                    throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_LOOP_VALUE", Integer.toHexString (u1).toUpperCase ()));

                final ZoneLoop loop = new ZoneLoop ();
                loop.read (childDataIn);
                zone.addLoop (loop);
            }
        }
    }


    /**
     * Fill the program attributes from the given multi-sample instance. All existing groups and
     * zones are removed.
     *
     * @param source The multi-sample to read from
     * @return The names of the sample files, the index in the list is the file name ID of the zones
     * @throws IOException Could not read the sample information
     */
    public List<String> fillFrom (final IMultisampleSource source) throws IOException
    {
        this.groups.clear ();
        this.zones.clear ();
        this.voiceGroups.clear ();
        this.groupSettingZones.clear ();

        this.name = source.getName ();
        this.midiTranspose = 0;
        // The gain, panning and tuning are stored in the zones
        this.instrumentVolume = 1.0f;
        this.instrumentPan = 0;
        this.instrumentTune = 1.0f;
        final int polyphony = source.getPolyphony ();
        this.maxVoices = polyphony > 0 ? Math.min (polyphony, MAX_VOICES) : DEFAULT_MAX_VOICES;

        final IMetadata metadata = source.getMetadata ();
        final String category = metadata.getCategory ();
        this.instrumentIconName = category == null ? "" : category;
        final String creator = metadata.getCreator ();
        this.instrumentAuthor = creator == null || creator.isBlank () ? NULL_ENTRY : creator;
        final String description = metadata.getDescription ();
        this.instrumentCredits = description == null ? "" : description;
        this.instrumentURL = null;
        final String [] keywords = metadata.getKeywords ();
        this.instrumentCategory1 = Kontakt2Header.getCategory1Index (category);
        this.instrumentCategory2 = Kontakt2Header.getCategory2Index (keywords);
        this.instrumentCategory3 = Kontakt2Header.getCategory3Index (keywords);

        final List<String> sampleFileNames = new ArrayList<> ();
        final List<IGroup> sourceGroups = source.getNonEmptyGroups (false);
        for (int groupIndex = 0; groupIndex < sourceGroups.size (); groupIndex++)
        {
            final IGroup sourceGroup = sourceGroups.get (groupIndex);
            final List<ISampleZone> sampleZones = sourceGroup.getSampleZones ();

            String groupName = sourceGroup.getName ();
            if (groupName == null || groupName.isBlank ())
                groupName = "Group " + (groupIndex + 1);

            // These parameters can only be set on the group, the first zone is used
            final ISampleZone firstZone = sampleZones.get (0);
            final boolean keyTracking = firstZone.getKeyTracking () > 0;
            final boolean isReleaseTrigger = firstZone.getTrigger () == TriggerType.RELEASE;

            // The voice group can only be set on the group, therefore all zones need to use the
            // same exclusive group. A voice group with 1 voice is created for it. Voice group 0 is
            // the instrument, therefore the exclusive group number (the model uses 0 for 'no
            // group') can be used as the index.
            int voiceGroupIdx = -1;
            final int exclusiveGroup = getUniformExclusiveGroup (sampleZones);
            if (exclusiveGroup > 0 && exclusiveGroup <= MAX_VOICE_GROUPS)
            {
                voiceGroupIdx = exclusiveGroup;
                this.voiceGroups.put (Integer.valueOf (voiceGroupIdx), Integer.valueOf (1));
            }

            this.groups.add (new Group (groupName, keyTracking, firstZone.isReversed (), isReleaseTrigger, voiceGroupIdx));
            this.groupSettingZones.add (firstZone);

            for (final ISampleZone sampleZone: sampleZones)
            {
                final String sampleFileName = SafeFileNames.create (sampleZone.getName ()) + ".wav";
                int filenameId = sampleFileNames.indexOf (sampleFileName);
                if (filenameId < 0)
                {
                    filenameId = sampleFileNames.size ();
                    sampleFileNames.add (sampleFileName);
                }
                this.zones.add (new Zone (groupIndex, sampleZone, filenameId));
            }
        }

        return sampleFileNames;
    }


    /**
     * Set the values of the instrument in a multi.
     *
     * @param instrumentSource The instrument source from which to take the values
     */
    public void setInstrumentValues (final IInstrumentSource instrumentSource)
    {
        this.instrumentVolume = (float) AbstractKontaktFormat.dbToValue (instrumentSource.getGain ());
        this.instrumentPan = (float) Math.clamp (instrumentSource.getPanning (), -1.0, 1.0);
        this.midiTranspose = Math.clamp (instrumentSource.getTranspose (), -24, 24);
        this.clipLowKey = Math.clamp (instrumentSource.getClipKeyLow (), 0, 127);
        this.clipHighKey = Math.clamp (instrumentSource.getClipKeyHigh (), 0, 127);

        // Programs in a multi have -1 in the 4th integer of the additional data (0 in NKIs),
        // Kontakt does not load the multi otherwise
        if (this.additionalData.length >= ADDITIONAL_DATA_MULTI + 4)
            Arrays.fill (this.additionalData, ADDITIONAL_DATA_MULTI, ADDITIONAL_DATA_MULTI + 4, (byte) -1);
    }


    /**
     * Changes the sample file indices of all zones. Required if several programs share one file
     * list.
     *
     * @param mapping Maps the current index to the new one
     */
    public void mapFilenameIds (final int [] mapping)
    {
        for (final Zone zone: this.zones)
        {
            final int filenameId = zone.getFilenameId ();
            if (filenameId >= 0 && filenameId < mapping.length)
                zone.setFilenameId (mapping[filenameId]);
        }
    }


    /**
     * Get the exclusive group which is shared by all given zones.
     *
     * @param zones The zones to check
     * @return The exclusive group or 0 if there are no zones or they do not all use the same one
     */
    private static int getUniformExclusiveGroup (final List<ISampleZone> zones)
    {
        if (zones.isEmpty ())
            return 0;
        final int exclusiveGroup = zones.get (0).getExclusiveGroup ();
        for (final ISampleZone zone: zones)
            if (zone.getExclusiveGroup () != exclusiveGroup)
                return 0;
        return exclusiveGroup;
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
     * Is the solo of groups active? The soloed state of the individual groups is only in effect if
     * this is enabled.
     *
     * @return True if group solo is active
     */
    public boolean isGroupSolo ()
    {
        return this.groupSolo;
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


    /**
     * Set the slot index.
     *
     * @param slotIndex The slot index [0..63]
     */
    public void setSlotIndex (final int slotIndex)
    {
        this.slotIndex = slotIndex;
    }


    /**
     * Get the slot index. This is the slot in a performance in which this program is stored.
     *
     * @return The slot index [0..63]
     */
    public int getSlotIndex ()
    {
        return this.slotIndex;
    }


    private static String fixKontakt4Umlauts (final String text)
    {
        final int length = text.length ();
        final StringBuilder sb = new StringBuilder (length);
        for (int i = 0; i < length; i++)
        {
            final char c = text.charAt (i);
            sb.append (c == '\ufffc' ? 'ü' : c);
        }
        return sb.toString ();
    }
}
