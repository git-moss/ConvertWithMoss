// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.util.zip.CRC32;


/**
 * Some helper functions.
 *
 * @author Jürgen Moßgraber
 */
public class Utils
{
    /**
     * Helper class.
     */
    private Utils ()
    {
        // Intentionally empty
    }


    /**
     * Limit the given value to the minimum/maximum range including minimum/maximum values.
     *
     * @param value The value to clamp
     * @param minimum The minimum value
     * @param maximum The maximum value
     * @return The value clamped to the minimum/maximum range
     */
    public static double clamp (final double value, final double minimum, final double maximum)
    {
        return Math.max (minimum, Math.min (value, maximum));
    }


    /**
     * Limit the given value to the minimum/maximum range including minimum/maximum values.
     *
     * @param value The value to clamp
     * @param minimum The minimum value
     * @param maximum The maximum value
     * @return The value clamped to the minimum/maximum range
     */
    public static int clamp (final int value, final int minimum, final int maximum)
    {
        return Math.max (minimum, Math.min (value, maximum));
    }


    /**
     * Convert continuous double value to dB.
     *
     * @param x The value to convert
     * @return The value converted to dB
     */
    public static double valueToDb (final double x)
    {
        if (x < 0.0000000298023223876953125)
            return -150;
        return Math.max (-150.0, Math.log (x) * 8.6858896380650365530225783783321);
    }


    /**
     * Calculate a CRC-32 of the given data.
     *
     * @param data The data to hash
     * @return The CRC-32 value
     */
    public static long calcCRC32 (final byte [] data)
    {
        final CRC32 crc32 = new CRC32 ();
        crc32.update (data);
        return crc32.getValue ();
    }
}
