// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.iso;

/**
 * Constants for different CD formats.
 *
 * @author Jürgen Moßgraber
 */
public class IsoFormatIdentifier
{
    /** The minimum number of bytes the need to be handed to the identifyIso method. */
    public static int MINIMUM_NUMBER_OF_REQUIRED_BYTES = 17924;


    /**
     * Identify the format of an ISO file.
     * 
     * @param data The data of the ISO file, needs to be at least the first 17924 bytes (use
     *            MINIMUM_NUMBER_OF_REQUIRED_BYTES)
     * @return The identified format or UNKNOWN
     */
    public static IsoFormat identifyIso (final byte [] data)
    {
        if (data == null || data.length < MINIMUM_NUMBER_OF_REQUIRED_BYTES)
            return IsoFormat.UNKNOWN;

        // AKAI family (first byte == 0x00)
        if (data[0] == 0x00)
        {
            // AKAI S3000
            final String tag = readString (data, MINIMUM_NUMBER_OF_REQUIRED_BYTES - 4, 4);
            if ("TAGS".equals (tag))
                return IsoFormat.AKAI_S3000;

            // Roland S-7xx
            final String rolandVersion = readString (data, 5, 3);
            if ("770".equals (rolandVersion))
                return IsoFormat.ROLAND_S7XX;

            // AKAI S1000/S1100
            return IsoFormat.AKAI_S1000_S1100;
        }

        // Roland S-550 / W-30 / DJ-70MKII
        final String offset2 = readString (data, 2, 6);
        if ("ROLAND".equals (offset2))
            return IsoFormat.ROLAND_S550_W30_DJ70;

        return IsoFormat.UNKNOWN;
    }


    private static String readString (final byte [] data, final int offset, final int length)
    {
        return offset + length > data.length ? "" : new String (data, offset, length);
    }
}
