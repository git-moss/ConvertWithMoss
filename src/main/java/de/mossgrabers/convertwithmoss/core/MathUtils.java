// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.util.zip.CRC32;


/**
 * Some mathematics helper functions.
 *
 * @author Jürgen Moßgraber
 */
public class MathUtils
{
    /**
     * Helper class.
     */
    private MathUtils ()
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
     * Normalizes a frequency to the range of [0..1].
     *
     * @param frequency The frequency in Hertz
     * @param maxFrequency The maximum frequency
     * @return The normalized value
     */
    public static double normalizeFrequency (final double frequency, final double maxFrequency)
    {
        return clamp (log2 (frequency) / log2 (maxFrequency), 0, 1);
    }


    /**
     * De-normalizes a frequency in the range of [0..1].
     *
     * @param normalizedFrequency The normalized value
     * @param maxFrequency The maximum frequency
     * @return The frequency in Hertz
     */
    public static double denormalizeFrequency (final double normalizedFrequency, final double maxFrequency)
    {
        return Math.pow (2, normalizedFrequency * log2 (maxFrequency));
    }


    /**
     * Normalizes a cutoff frequency in Hertz to the range of [0..1].
     *
     * @param cutoffInHertz The cutoff frequency
     * @return The normalized value
     */
    public static double normalizeCutoff (final double cutoffInHertz)
    {
        return MathUtils.clamp ((log2 (cutoffInHertz / (2 * 440.0)) * 12.0 + 57) / 140.0, 0, 1);
    }


    /**
     * De-normalizes a cutoff frequency in the range of [0..1] to Hertz.
     *
     * @param normalizedValue The normalized value
     * @return The cutoff frequency
     */
    public static double denormalizeCutoff (final double normalizedValue)
    {
        return MathUtils.clamp (2.0 * 440.0 * Math.pow (2, (normalizedValue * 140.0 - 57.0) / 12.0), 32.7, 106300);
    }


    /**
     * Normalizes the given value to the range of [0..1].
     *
     * @param value The value to normalize
     * @param maximum The maximum that value can be
     * @return The normalized value
     */
    public static double normalize (final double value, final double maximum)
    {
        return normalize (value, 0, maximum);
    }


    /**
     * Normalizes the given value to the range of [0..1].
     *
     * @param value The value to normalize
     * @param minimum The minimum that value can be
     * @param maximum The maximum that value can be
     * @return The normalized value
     */
    public static double normalize (final double value, final double minimum, final double maximum)
    {
        return clamp (value, minimum, maximum) / maximum;
    }


    /**
     * De-normalizes the given value to the range of [0..1].
     *
     * @param value The value to normalize
     * @param minimum The minimum that value can be
     * @param maximum The maximum that value can be
     * @return The normalized value
     */
    public static double denormalize (final double value, final double minimum, final double maximum)
    {
        return clamp (value * maximum, minimum, maximum);
    }


    /**
     * Calculates a logarithm to the base 2.
     *
     * @param n The value for which to calculate the logarithm
     * @return The logarithmic value
     */
    public static double log2 (final double n)
    {
        return Math.log (n) / Math.log (2);
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


    /**
     * Converts a time in seconds into a normalized logarithmic value in the range [0..1].
     *
     * @param value The value in seconds
     * @param maxValue The maximum for value
     * @return The converted value formatted as a string
     */
    public static String normalizeTimeFormattedAsInt (final double value, final double maxValue)
    {
        return Integer.toString (normalizeTimeAsInt (value, maxValue));
    }


    /**
     * Converts a time in seconds into a normalized logarithmic value in the range [0..1],
     * multiplies it with 1000 and converts it to an integer.
     *
     * @param value The value in seconds
     * @param maxValue The maximum for value
     * @return The converted logarithmic value as an integer
     */
    public static int normalizeTimeAsInt (final double value, final double maxValue)
    {
        return (int) Math.round (normalizeTime (value, maxValue) * 1000.0);
    }


    /**
     * Converts a time in seconds into a normalized logarithmic value in the range [0..1].
     *
     * @param value The value in seconds
     * @param maxValue The maximum for value in seconds
     * @return The converted logarithmic value
     */
    public static double normalizeTime (final double value, final double maxValue)
    {
        // value is negative if not set but 0 is fine then!
        final double clamped = MathUtils.clamp (value, 0, maxValue);
        return Math.log (clamped + 1) / Math.log (maxValue + 1);
    }


    /**
     * Converts a normalized logarithmic value which is scaled to an integer by the factor 1000 to
     * seconds.
     *
     * @param intLogValue The logarithmic value as an integer
     * @param maxValue The maximum for value in seconds
     * @return The value in seconds
     */
    public static double denormalizeTime (final int intLogValue, final double maxValue)
    {
        return denormalizeTime (intLogValue / 1000.0, maxValue);
    }


    /**
     * Converts a normalized logarithmic value in the range [0..1] to seconds.
     *
     * @param logValue The logarithmic value
     * @param maxValue The maximum for value in seconds
     * @return The value in seconds
     */
    public static double denormalizeTime (final double logValue, final double maxValue)
    {
        return Math.exp (logValue * Math.log (maxValue + 1)) - 1;
    }
}
