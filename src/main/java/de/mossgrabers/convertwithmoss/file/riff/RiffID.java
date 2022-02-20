// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


/**
 * Enumeration for known RIFF IDs.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public enum RiffID
{
    /** ID for FormGroupExpression. */
    RIFF_ID("RIFF", "RIFF"),
    /** ID for ListGroupExpression. */
    LIST_ID("List", "LIST"),
    /** ID for NULL chunk. */
    NULL_ID("Null", "    "),
    /** ID for NULL chunk. */
    NULL_NUL_ID("Null Nul", "\0\0\0\0"),
    /** ID for JUNK chunk. */
    JUNK_ID("Junk", "JUNK"),
    /** Unknown ID. */
    UNKNOWN("Unknown", null),

    ////////////////////////////////////////////////////
    // Wave RIFF IDs

    /** ID for WAVE chunk. */
    WAVE_ID("Wave", "WAVE"),
    /** ID for "inst" chunk. */
    INST_ID("Instrument", "inst"),
    /** ID for "smpl" chunk. */
    SMPL_ID("Sample", "smpl"),
    /** ID for "fmt " chunk. */
    FMT_ID("Format", "fmt "),
    /** ID for "data" chunk. */
    DATA_ID("Data", "data"),

    ////////////////////////////////////////////////////
    // SoundFont 2 RIFF IDs

    /** ID for SoundFont 2 chunk. */
    SF_SFBK_ID("SoundFont 2", "sfbk"),
    /** ID for INFO chunk. */
    SF_INFO_ID("Info", "INFO"),
    /** ID for IFIL chunk. */
    SF_IFIL_ID("SoundFont Specification Version Level", "ifil"),
    /** ID for ISNG chunk. */
    SF_ISNG_ID("SoundFont Wavetable Sound Engine", "isng"),
    /** ID for INAM chunk. */
    SF_INAM_ID("SoundFont Compatible Bank", "INAM"),
    /** ID for IROM chunk. */
    SF_IROM_ID("Wavetable Sound Data ROM", "irom"),
    /** ID for IVER chunk. */
    SF_IVER_ID("Wavetable Sound Data ROM Revision", "iver"),
    /** ID for ICRD chunk. */
    SF_ICRD_ID("Creation Date", "ICRD"),
    /** ID for IENG chunk. */
    SF_IENG_ID("Sound Designer", "IENG"),
    /** ID for IPRD chunk. */
    SF_IPRD_ID("Intended Product", "IPRD"),
    /** ID for ICOP chunk. */
    SF_ICOP_ID("Copyright", "ICOP"),
    /** ID for ICMT chunk. */
    SF_ICMT_ID("Comments", "ICMT"),
    /** ID for ISFT chunk. */
    SF_ISFT_ID("Creation Tool", "ISFT"),

    /** ID for SoundFont Data chunk. */
    SF_DATA_ID("SoundFont Data", "sdta"),
    /** ID for Sample Data 24bit chunk. */
    SF_SM24_ID("Sample Data 24bit", "sm24"),

    /** ID for Articulation chunk. */
    SF_PDTA_ID("Articulation", "pdta"),
    /** ID for Preset chunk. */
    SF_PHDR_ID("Preset", "phdr"),
    /** ID for Preset Zone chunk. */
    SF_PBAG_ID("Preset Zone", "pbag"),
    /** ID for preset Zone Generator chunk. */
    SF_PGEN_ID("Preset Zone Generators", "pgen"),
    /** ID for preset Zone Generator chunk. */
    SF_PMOD_ID("Preset Zone Modulators", "pmod"),
    /** ID for instrument chunk. */
    SF_INST_ID("Instrument", "inst"),
    /** ID for instrument zones chunk. */
    SF_IBAG_ID("Instrument Zones", "ibag"),
    /** ID for instrument zone modulators chunk. */
    SF_IMOD_ID("Instrument Zone Modulators", "imod"),
    /** ID for instrument zone generators chunk. */
    SF_IGEN_ID("Instrument Zone Generators", "igen"),
    /** ID for sample descriptors chunk. */
    SF_SHDR_ID("Sample Descriptors", "shdr");


    private static final Map<Integer, RiffID> ID_LOOKUP = new HashMap<> ();

    static
    {
        final RiffID [] values = RiffID.values ();
        for (final RiffID value: values)
            ID_LOOKUP.put (Integer.valueOf (value.getId ()), value);
    }

    private final String name;
    private final int    id;
    private final String ascii;


    /**
     * Constructor.
     *
     * @param name A name for the chunk
     * @param ascii The ASCII representation of the ID
     */
    private RiffID (final String name, final String ascii)
    {
        this.name = name;
        this.ascii = ascii;
        this.id = ascii == null ? -1 : toId (ascii);
    }


    /**
     * Get the name of the chunk.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the RIFF ID.
     *
     * @return The ID
     */
    public int getId ()
    {
        return this.id;
    }


    /**
     * Get the RIFF ID as ASCII.
     *
     * @return The ID as ASCII text
     */
    public String asASCII ()
    {
        return this.ascii;
    }


    /**
     * Test if the given ID is presented by this enumeration.
     *
     * @param riffId The ID to match
     * @return True if matched
     */
    public boolean matches (final int riffId)
    {
        return this.id == riffId;
    }


    /**
     * Lookup the matching enumeration for the given RIFF ID.
     *
     * @param id The id to lookup
     * @return The enumeration or null if not defined
     */
    public static RiffID fromId (final int id)
    {
        final RiffID riffID = ID_LOOKUP.get (Integer.valueOf (id));
        return riffID == null ? UNKNOWN : riffID;
    }


    /**
     * Convert an integer RIFF identifier to an ASCII text.
     *
     * @param id ID to be converted.
     * @return Text representation of the ID
     */
    public static String toASCII (final int id)
    {
        return new String (new byte []
        {
            (byte) (id >>> 24),
            (byte) (id >>> 16),
            (byte) (id >>> 8),
            (byte) (id >>> 0)
        }, StandardCharsets.US_ASCII);
    }


    /**
     * Converts the first four letters of the string into an RIFF Identifier.
     *
     * @param text The string to be converted
     * @return ID representation of the string
     */
    private static int toId (final String text)
    {
        final byte [] bytes = text.getBytes ();
        return bytes[0] << 24 | bytes[1] << 16 | bytes[2] << 8 | bytes[3];
    }


    /**
     * Checks if the argument represents a valid RIFF ID.
     *
     * @param id Chunk ID to be checked
     * @return True when the ID is a valid IFF chunk ID
     */
    public static boolean isValidId (final int id)
    {
        final int c0 = id >> 24;
        final int c1 = id >> 16 & 0xff;
        final int c2 = id >> 8 & 0xff;
        final int c3 = id & 0xff;
        return NULL_NUL_ID.matches (id) || c0 >= 0x20 && c0 <= 0x7e && c1 >= 0x20 && c1 <= 0x7e && c2 >= 0x20 && c2 <= 0x7e && c3 >= 0x20 && c3 <= 0x7e;
    }


    /**
     * Checks whether the argument represents a valid RIFF Group ID.
     *
     * @param id Chunk ID to be checked
     * @return True when the chunk ID is a valid Group ID
     */
    public static boolean isGroupId (final int id)
    {
        return LIST_ID.matches (id) || RIFF_ID.matches (id);
    }
}
