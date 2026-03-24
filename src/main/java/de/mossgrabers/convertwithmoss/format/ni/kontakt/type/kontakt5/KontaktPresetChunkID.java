// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.util.HashMap;
import java.util.Map;


/**
 * All available types of preset chunks.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktPresetChunkID
{
    /** Parameter Modulation. */
    public static final int                   PAR_MOD_BASE         = 0x00;
    /** A bank of several programs. */
    public static final int                   BANK                 = 0x03;
    /** A group of a program. */
    public static final int                   GROUP                = 0x04;
    /** Scripting. */
    public static final int                   PAR_SCRIPT           = 0x06;
    /** Send levels. */
    public static final int                   PAR_FX_SEND_LEVELS   = 0x17;
    /** External modulator like Velocity. */
    public static final int                   PAR_EXTERNAL_MOD     = 0x0C;
    /** External modulator like envelopes. */
    public static final int                   PAR_INTERNAL_MOD     = 0x0D;
    /** FX. */
    public static final int                   PAR_FX               = 0x25;
    /** A program. */
    public static final int                   PROGRAM              = 0x28;
    /** A program container. */
    public static final int                   PROGRAM_CONTAINER    = 0x29;
    /** A zone of a program. */
    public static final int                   ZONE                 = 0x2C;
    /** The list with all voice groups. */
    public static final int                   VOICE_GROUPS         = 0x32;
    /** The list with all groups. */
    public static final int                   GROUP_LIST           = 0x33;
    /** The list with all zones. */
    public static final int                   ZONE_LIST            = 0x34;
    /** A raw object. */
    public static final int                   PRIVATE_RAW_OBJECT   = 0x35;
    /** The list of programs in a slot. */
    public static final int                   PROGRAM_LIST         = 0x36;
    /** The list of slots in a bank. */
    public static final int                   SLOT_LIST            = 0x37;
    /** StarCritList. */
    public static final int                   STAR_CRIT_LIST       = 0x38;
    /** An array with up to 8 Loops. */
    public static final int                   LOOP_ARRAY           = 0x39;
    /** A parameter array with 8 entries. */
    public static final int                   PARAMETER_ARRAY_8    = 0x3A;
    /** A parameter array with 16 entries. */
    public static final int                   PARAMETER_ARRAY_16   = 0x3B;
    /** A parameter array with 32 entries. */
    public static final int                   PARAMETER_ARRAY_32   = 0x3C;
    /** The list with all file names. */
    public static final int                   FILENAME_LIST        = 0x3D;
    /** The output configuration. */
    public static final int                   OUTPUT_CONFIGURATION = 0x3E;
    /** Insert bus settings */
    public static final int                   INSERT_BUS           = 0x45;
    /** Save settings. */
    public static final int                   SAVE_SETTINGS        = 0x47;
    /** Multi configuration for NKMs. */
    public static final int                   MULTI_CONFIGURATION  = 0x48;
    /** ParGroupDynamics. */
    public static final int                   PAR_GROUP_DYNAMICS   = 0x4A;
    /** The list with all file names (more recent version). */
    public static final int                   FILENAME_LIST_EX     = 0x4B;
    /** Quick browse info. */
    public static final int                   QUICK_BROWSE_DATA    = 0x4E;

    private static final Map<Integer, String> CHUNK_NAMES          = new HashMap<> ();
    static
    {
        CHUNK_NAMES.put (Integer.valueOf (PAR_MOD_BASE), "PAR_MOD_BASE");
        CHUNK_NAMES.put (Integer.valueOf (BANK), "BANK");
        CHUNK_NAMES.put (Integer.valueOf (GROUP), "GROUP");
        CHUNK_NAMES.put (Integer.valueOf (PAR_SCRIPT), "PAR_SCRIPT");
        CHUNK_NAMES.put (Integer.valueOf (PAR_FX_SEND_LEVELS), "PAR_FX_SEND_LEVELS");
        CHUNK_NAMES.put (Integer.valueOf (PAR_INTERNAL_MOD), "PAR_INTERNAL_MOD");
        CHUNK_NAMES.put (Integer.valueOf (PAR_FX), "PAR_FX");
        CHUNK_NAMES.put (Integer.valueOf (PROGRAM), "PROGRAM");
        CHUNK_NAMES.put (Integer.valueOf (PROGRAM_CONTAINER), "PROGRAM_CONTAINER");
        CHUNK_NAMES.put (Integer.valueOf (VOICE_GROUPS), "VOICE_GROUPS");
        CHUNK_NAMES.put (Integer.valueOf (PROGRAM_LIST), "PROGRAM_LIST");
        CHUNK_NAMES.put (Integer.valueOf (GROUP_LIST), "GROUP_LIST");
        CHUNK_NAMES.put (Integer.valueOf (ZONE_LIST), "ZONE_LIST");
        CHUNK_NAMES.put (Integer.valueOf (ZONE), "ZONE");
        CHUNK_NAMES.put (Integer.valueOf (PROGRAM_LIST), "PROGRAM_LIST");
        CHUNK_NAMES.put (Integer.valueOf (PRIVATE_RAW_OBJECT), "PRIVATE_RAW_OBJECT");
        CHUNK_NAMES.put (Integer.valueOf (SLOT_LIST), "SLOT_LIST");
        CHUNK_NAMES.put (Integer.valueOf (STAR_CRIT_LIST), "STAR_CRIT_LIST");
        CHUNK_NAMES.put (Integer.valueOf (LOOP_ARRAY), "LOOP_ARRAY");
        CHUNK_NAMES.put (Integer.valueOf (PARAMETER_ARRAY_8), "PARAMETER_ARRAY_8");
        CHUNK_NAMES.put (Integer.valueOf (PARAMETER_ARRAY_16), "PARAMETER_ARRAY_16");
        CHUNK_NAMES.put (Integer.valueOf (PARAMETER_ARRAY_32), "PARAMETER_ARRAY_32");
        CHUNK_NAMES.put (Integer.valueOf (FILENAME_LIST), "FILENAME_LIST");
        CHUNK_NAMES.put (Integer.valueOf (INSERT_BUS), "INSERT_BUS");
        CHUNK_NAMES.put (Integer.valueOf (SAVE_SETTINGS), "SAVE_SETTINGS");
        CHUNK_NAMES.put (Integer.valueOf (MULTI_CONFIGURATION), "MULTI_CONFIGURATION");
        CHUNK_NAMES.put (Integer.valueOf (PAR_GROUP_DYNAMICS), "PAR_GROUP_DYNAMICS");
        CHUNK_NAMES.put (Integer.valueOf (FILENAME_LIST_EX), "FILENAME_LIST_EX");
        CHUNK_NAMES.put (Integer.valueOf (OUTPUT_CONFIGURATION), "OUTPUT_CONFIGURATION");
        CHUNK_NAMES.put (Integer.valueOf (QUICK_BROWSE_DATA), "QUICK_BROWSE_DATA");
    }


    /**
     * Get the name of an ID.
     *
     * @param id The ID for which to get the name
     * @return The name
     */
    public static String getName (final int id)
    {
        return CHUNK_NAMES.getOrDefault (Integer.valueOf (id), "Unknown: " + Integer.toHexString (id).toUpperCase ());
    }


    /**
     * Hide constructor in helper class.
     */
    private KontaktPresetChunkID ()
    {
        // Intentionally empty
    }
}
