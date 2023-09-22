// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

/**
 * Magic numbers used to identify Native Instruments files or sections in these files.
 *
 * @author Jürgen Moßgraber
 */
public class Magic
{
    /** ID for Kontakt 1 NKI files. */
    public static final int     KONTAKT1_INSTRUMENT              = 0x5EE56EB3;
    /** ID for Kontakt 1 NKM files. */
    public static final int     KONTAKT1_MULTI                   = 0x5AE5D6A4;
    /** ID for Kontakt 2 NKI files (little-endian). */
    public static final int     KONTAKT2_LITTLE_ENDIAN           = 0x1290A87F;
    /** ID for Kontakt 2 NKI files (big-endian). */
    public static final int     KONTAKT2_BIG_ENDIAN              = 0x7FA89012;
    /** ID for a Kontakt 5 NKI monolith file. */
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
