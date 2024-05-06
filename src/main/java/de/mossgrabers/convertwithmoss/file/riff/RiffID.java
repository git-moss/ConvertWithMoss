// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.file.IChunk;


/**
 * Enumeration for known RIFF IDs.
 *
 * @author Jürgen Moßgraber
 */
@SuppressWarnings("javadoc")
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
    JUNK_ID("JUNK", "JUNK"),
    /** ID for junk chunk. */
    JUNK2_ID("junk", "junk"),
    /** Unsupported ID. */
    UNSUPPORTED("Unsupported", null),

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

    /** ID for INFO chunk. */
    INFO_ID("Info", "INFO"),

    /** Info chunks. */
    INFO_IARL("Archival Location", "IARL"),
    INFO_IART("Artist", "IART"),
    INFO_IBSU("BaseURL", "IBSU"),
    INFO_ICNM("Cinematographer", "ICNM"),
    INFO_CMNT("Comment", "CMNT"),
    INFO_ICMT("Comment2", "ICMT"),
    INFO_COMM("Comments", "COMM"),
    INFO_ICMS("Commissioned", "ICMS"),
    INFO_ICOP("Copyright", "ICOP"),
    INFO_ICDS("Costume Designer", "ICDS"),
    INFO_ICNT("Country", "ICNT"),
    INFO_ICRP("Cropped", "ICRP"),
    INFO_ICRD("Date Created", "ICRD"),
    INFO_IDIT("Date TimeOriginal", "IDIT"),
    INFO_ICAS("Default AudioStream", "ICAS"),
    INFO_IDIM("Dimension", "IDIM"),
    INFO_DIRC("Directory", "DIRC"),
    INFO_IDST("Distributed By", "IDST"),
    INFO_IDPI("Dots Per Inch", "IDPI"),
    INFO_IEDT("Edited By", "IEDT"),
    INFO_IAS8("Eighth Language", "IAS8"),
    INFO_CODE("Encoded By", "CODE"),
    INFO_TCDO("End Timecode", "TCDO"),
    INFO_IENG("Engineer", "IENG"),
    INFO_IAS5("Fifth Language", "IAS5"),
    INFO_IAS1("First Language", "IAS1"),
    INFO_IAS4("Fourth Language", "IAS4"),
    INFO_GENR("Genre", "GENR"),
    INFO_IKEY("Keywords", "IKEY"),
    INFO_LANG("Language", "LANG"),
    INFO_TLEN("Length", "TLEN"),
    INFO_ILGT("Lightness", "ILGT"),
    INFO_LOCA("Location", "LOCA"),
    INFO_ILIU("Logo Icon URL", "ILIU"),
    INFO_ILGU("Logo URL", "ILGU"),
    INFO_IMED("Medium", "IMED"),
    INFO_IMBI("More Info Banner Image", "IMBI"),
    INFO_IMBU("More Info Banner URL", "IMBU"),
    INFO_IMIT("More Info Text", "IMIT"),
    INFO_IMIU("More Info URL", "IMIU"),
    INFO_IMUS("Music By", "IMUS"),
    INFO_IAS9("Ninth Language", "IAS9"),
    INFO_PRT2("Number Of Parts", "PRT2"),
    INFO_TORG("Organisation", "TORG"),
    INFO_PRT1("Part", "PRT1"),
    INFO_IPRO("Produced By", "IPRO"),
    INFO_IPRD("Product Name", "IPRD"),
    INFO_IPDS("Production Designer", "IPDS"),
    INFO_ISDT("Production Studio", "ISDT"),
    INFO_RATE("Rate", "RATE"),
    INFO_AGES("Rated", "AGES"),
    INFO_IRTD("Rating", "IRTD"),
    INFO_IRIP("Ripped By", "IRIP"),
    INFO_ISGN("Secondary Genre", "ISGN"),
    INFO_IAS2("Second Language", "IAS2"),
    INFO_IAS7("Seventh Language", "IAS7"),
    INFO_ISHP("Sharpness", "ISHP"),
    INFO_IAS6("Sixth Language", "IAS6"),
    INFO_ISFT("Software", "ISFT"),
    INFO_DISP("Sound Scheme Title", "DISP"),
    INFO_ISRC("Source", "ISRC"),
    INFO_ISRF("Source From", "ISRF"),
    INFO_ISTR("Starring", "ISTR"),
    INFO_STAR("Starring2", "STAR"),
    INFO_TCOD("Start Timecode", "TCOD"),
    INFO_STAT("Statistics", "STAT"),
    INFO_ISBJ("Subject", "ISBJ"),
    INFO_TAPE("Tape Name", "TAPE"),
    INFO_ITCH("Technician", "ITCH"),
    INFO_IAS3("Third Language", "IAS3"),
    INFO_ISMP("Time Code", "ISMP"),
    INFO_INAM("Title", "INAM"),
    INFO_IPRT("Track No", "IPRT"),
    INFO_TRCK("Track Number", "TRCK"),
    INFO_TURL("URL", "TURL"),
    INFO_VMAJ("Vegas Version Major", "VMAJ"),
    INFO_VMIN("Vegas Version Minor", "VMIN"),
    INFO_TVER("Version", "TVER"),
    INFO_IWMU("Watermark URL", "IWMU"),
    INFO_IWRI("Written By", "IWRI"),
    INFO_YEAR("Year", "YEAR"),

    /**
     * Apple software often creates WAVE files with a non-standard (but RIFF specification conform)
     * "FLLR" sub-chunk after the format sub-chunk and before the data sub-chunk. "FLLR" stands for
     * "filler", and the purpose of the sub-chunk is to enable some sort of data alignment
     * optimization. The sub-chunk is usually about 4000 bytes long, but its actual length can vary
     * depending on the length of the data preceding it.
     */
    FILLER_ID("Apple Filler", "FLLR"),

    /** MD5 checksum. */
    MD5_ID("MD5 Checksum", "MD5 "),

    /** Broadcast Audio Extension Chunk. **/
    BEXT_ID("Broadcast Audio Extension", "bext"),

    ////////////////////////////////////////////////////
    // SoundFont 2 RIFF IDs

    /** ID for SoundFont 2 chunk. */
    SF_SFBK_ID("SoundFont 2", "sfbk"),

    /** ID for IFIL chunk. */
    SF_IFIL_ID("SoundFont Specification Version Level", "ifil"),
    /** ID for ISNG chunk. */
    SF_ISNG_ID("SoundFont Wavetable Sound Engine", "isng"),
    /** ID for IROM chunk. */
    SF_IROM_ID("Wavetable Sound Data ROM", "irom"),
    /** ID for IVER chunk. */
    SF_IVER_ID("Wavetable Sound Data ROM Revision", "iver"),

    /** ID for SoundFont Data list chunk. */
    SF_DATA_ID("SoundFont Data list", "sdta"),
    /** ID for Sample Data 24bit chunk. */
    SF_SM24_ID("Sample Data 24bit", "sm24"),

    /** ID for list chunk containing the Preset, Instrument, and Sample Header data. */
    SF_PDTA_ID("The Preset, Instrument, and Sample Header data list", "pdta"),
    /** ID for Preset chunk. */
    SF_PHDR_ID("Preset", "phdr"),
    /** ID for Preset Zone chunk. */
    SF_PBAG_ID("Preset Zone", "pbag"),
    /** ID for preset Zone Generator chunk. */
    SF_PGEN_ID("Preset Zone Generators", "pgen"),
    /** ID for preset Zone Generator chunk. */
    SF_PMOD_ID("Preset Zone Modulators", "pmod"),

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
     * @param asciiID The ASCII representation of the ID
     */
    private RiffID (final String name, final String asciiID)
    {
        this.name = name;
        this.ascii = asciiID;
        this.id = asciiID == null ? -1 : IChunk.toId (asciiID);
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
        return riffID == null ? UNSUPPORTED : riffID;
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
