// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

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
    public static double clamp (final int value, final int minimum, final int maximum)
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
}
