// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.iso;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;


/**
 * Constants for different CD/Disk formats.
 *
 * @author Jürgen Moßgraber
 */
public class IsoFormatIdentifier
{
    /** The minimum number of bytes the need to be handed to the identifyIso method. */
    public static int            MINIMUM_NUMBER_OF_REQUIRED_BYTES = 0x8010;

    private static final byte [] MPC2000_MAGIC                    =
    {
        (byte) 0xEB,
        (byte) 0xFE,
        (byte) 0x90,
        0x4D,                                                              // M
        0x50,                                                              // P
        0x43,                                                              // C
        0x32,                                                              // 2
        0x30,                                                              // 0
        0x30,                                                              // 0
        0x30,                                                              // 0
        0x20,
        0x00
    };

    private static final byte [] MPC2000XL_MAGIC                  =
    {
        (byte) 0xEB,
        (byte) 0xFE,
        (byte) 0x90,
        0x4D,                                                              // M
        0x50,                                                              // P
        0x43,                                                              // C
        0x32,                                                              // 2
        0x4B,                                                              // K
        0x58,                                                              // X
        0x4C,                                                              // L
        0x20,
        0x00
    };


    /**
     * Identify the format of an ISO file.
     * 
     * @param sourceFile The file to identify
     * @return The detected format
     */
    public static IsoFormat identifyIso (final File sourceFile)
    {
        try (final FileInputStream in = new FileInputStream (sourceFile))
        {
            return IsoFormatIdentifier.identifyIso (in.readNBytes (IsoFormatIdentifier.MINIMUM_NUMBER_OF_REQUIRED_BYTES));
        }
        catch (final IOException ex)
        {
            return IsoFormat.UNKNOWN;
        }
    }


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

        // ISO / AKAI family (first byte == 0x00)
        if (data[0] == 0x00)
        {
            // ISO 9660
            final String isoTag = readString (data, 0x8001, 5);
            if ("CD001".equals (isoTag))
                return IsoFormat.ISO_9660;

            // AKAI S3000
            final String tag = readString (data, 17920, 4);
            if ("TAGS".equals (tag))
                return IsoFormat.AKAI_S3000;

            // Roland S-7xx
            final String rolandVersion = readString (data, 5, 3);
            if ("770".equals (rolandVersion))
                return IsoFormat.ROLAND_S7XX;

            // AKAI S1000/S1100
            return IsoFormat.AKAI_S1000_S1100;
        }

        // Akai MPC2000
        if (equalsArray (data, MPC2000_MAGIC))
            return IsoFormat.AKAI_MPC2000;

        // Akai MPC2000XL
        if (equalsArray (data, MPC2000XL_MAGIC))
            return IsoFormat.AKAI_MPC2000XL;

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


    private static boolean equalsArray (final byte [] data, final byte [] magic)
    {
        return Arrays.compare (data, 0, magic.length, magic, 0, magic.length) == 0;
    }
}
