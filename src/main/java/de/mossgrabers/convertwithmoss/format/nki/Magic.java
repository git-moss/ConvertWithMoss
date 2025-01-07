// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

/**
 * Magic numbers used to identify Native Instruments files or sections in these files.
 *
 * @author Jürgen Moßgraber
 */
public class Magic
{
    /** ID for Kontakt 1 NKI files (little-endian). */
    public static final int     KONTAKT1_INSTRUMENT_LE           = 0x5EE56EB3;
    /** ID for Kontakt 1 NKI files (big-endian). */
    public static final int     KONTAKT1_INSTRUMENT_BE           = 0xB36EE55E;
    /** ID for Kontakt 1 NKM files (little-endian). */
    public static final int     KONTAKT1_MULTI_LE                = 0x5AE5D6A4;
    /** ID for Kontakt 1 NKM files (big-endian). */
    public static final int     KONTAKT1_MULTI_BE                = 0xA4D6E55A;
    /** ID for Kontakt 2 as well as 1.5 NKI files (little-endian). */
    public static final int     KONTAKT2_INSTRUMENT_LE           = 0x1290A87F;
    /** ID for Kontakt 2 as well as 1.5 NKI files (big-endian). */
    public static final int     KONTAKT2_INSTRUMENT_BE           = 0x7FA89012;
    /** ID for Kontakt 2 as well as 1.5 NKM files (little-endian). */
    public static final int     KONTAKT2_MULTI_LE                = 0x01EF85AB;
    /** ID for Kontakt 2 as well as 1.5 NKM files (big-endian). */
    public static final int     KONTAKT2_MULTI_BE                = 0xAB85EF01;

    /** ID for Kontakt 2 header (little-endian). */
    public static final int     KONTAKT2_INSTRUMENT_HEADER_LE    = 0x722A013E;
    /** ID for Kontakt 2 header (big-endian). */
    public static final int     KONTAKT2_INSTRUMENT_HEADER_BE    = 0x3E012A72;
    /** ID for Kontakt 2 header (little-endian). */
    public static final int     KONTAKT42_INSTRUMENT_HEADER_LE   = 0x1A6337EA;
    /** ID for Kontakt 2 header (big-endian). */
    public static final int     KONTAKT42_INSTRUMENT_HEADER_BE   = 0xEA37631A;

    /** The resource header in a Kontakt monolith. */
    public static final int     KONTAKT2_NKR_HEADER_ID           = 0x5E70AC54;
    /** The wallpaper header in a Kontakt monolith. */
    public static final int     KONTAKT2_NKR_WALLPAPER_ID        = 0x2AE905FA;
    /** The NKI header in a Kontakt monolith. */
    public static final int     KONTAKT2_NKR_NKI_ID              = 0x4916E63C;
    /** The sample header in a Kontakt monolith. */
    public static final int     KONTAKT2_NKR_SAMPLE_ID           = 0x16CCF80A;
    /** The sample header in a Kontakt monolith. */
    public static final int     KONTAKT2_NKR_SAMPLE_RAW_ID       = 0x0040179F;

    /** The header of a sound info XML structure. */
    public static final int     SOUNDINFO_HEADER_LE              = 0xB00EE1AE;
    /** The only known version of the sound info header. */
    public static final int     SOUNDINFO_HEADER_VERSION_LE      = 0x000C0101;

    /** ID for a Kontakt 5 NKI monolith file (always little-endian). */
    public static final int     KONTAKT5_MONOLITH                = 0x2F5C204E;

    /** File container magic tag. */
    public static final String  FILE_CONTAINER_HEADER            = "/\\ NI FC MTD  /\\";
    /** File container table of contents magic tag. */
    public static final String  FILE_CONTAINER_TABLE_OF_CONTENTS = "/\\ NI FC TOC  /\\";
    /** File container end of table of contents magic numbers. */
    public static final byte [] FILE_CONTAINER_HEADER_END        =
    {
        (byte) 0xF0,
        (byte) 0xF0,
        (byte) 0xF0,
        (byte) 0xF0,
        (byte) 0xF0,
        (byte) 0xF0,
        (byte) 0xF0,
        (byte) 0xF0
    };
    /** File container end of table of files section magic numbers. */
    public static final byte [] FILE_CONTAINER_FILES_END         =
    {
        (byte) 0xF1,
        (byte) 0xF1,
        (byte) 0xF1,
        (byte) 0xF1,
        (byte) 0xF1,
        (byte) 0xF1,
        (byte) 0xF1,
        (byte) 0xF1
    };


    /**
     * Private since this is a utility class.
     */
    private Magic ()
    {
        // Intentionally empty
    }
}
