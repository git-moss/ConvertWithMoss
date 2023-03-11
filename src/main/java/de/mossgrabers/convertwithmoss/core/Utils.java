// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

/**
 * Some helper functions.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Utils
{
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
}
