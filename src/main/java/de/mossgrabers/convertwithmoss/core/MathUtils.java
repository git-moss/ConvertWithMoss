// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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
     * Converts a signed integer into a two complement short value.
     *
     * @param value The signed integer
     * @return The 2 byte two complement value
     */
    public static int toSignedComplement (final int value)
    {
        if (value >= 0)
            return value & 0x7FFF;
        final int v = (~Math.abs (value) & 0x7FFF) + 1;
        return v | 0x8000;
    }


    /**
     * Converts a two complement short value into a signed integer.
     *
     * @param value The 2 byte two complement value
     * @return The signed integer
     */
    public static int fromSignedComplement (final int value)
    {
        if ((value & 0x8000) > 0)
        {
            final int i = ~(value - 1) & 0x7FFF;
            return -i;
        }
        return value & 0x7FFF;
    }


    /**
     * Convert continuous double value to dB.
     *
     * @param value The value to convert in the range of [0..1]
     * @return The value converted to dB in the range of [-150..0]
     */
    public static double valueToDb (final double value)
    {
        if (value < 0.0000000298023223876953125)
            return -150;
        return Math.max (-150.0, Math.log (value) * 8.6858896380650365530225783783321);
    }


    /**
     * Converts a dB value in the range of -Infinity to 0dB to a double range of [0..1].
     *
     * @param dBValue The dB value to convert
     * @return The double value
     */
    public static double dBToDouble (final double dBValue)
    {
        if (dBValue <= Double.NEGATIVE_INFINITY)
            return 0.0;
        if (dBValue >= 0.0)
            return 1.0;
        return Math.pow (10, dBValue / 20);
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
        return Math.clamp (log2 (frequency) / log2 (maxFrequency), 0, 1);
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
        return Math.clamp ((log2 (cutoffInHertz / (2 * 440.0)) * 12.0 + 57) / 140.0, 0, 1);
    }


    /**
     * De-normalizes a cutoff frequency in the range of [0..1] to Hertz.
     *
     * @param normalizedValue The normalized value
     * @return The cutoff frequency
     */
    public static double denormalizeCutoff (final double normalizedValue)
    {
        return Math.clamp (2.0 * 440.0 * Math.pow (2, (normalizedValue * 140.0 - 57.0) / 12.0), 32.7, 106300);
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
        return Math.clamp (value, minimum, maximum) / maximum;
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
        return Math.clamp (value * maximum, minimum, maximum);
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
     * Converts a time in seconds into a normalized logarithmic value in the range [0..1000].
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
     * Converts a time in seconds into a normalized logarithmic value in the range [0..1000],
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
        final double clamped = Math.clamp (value, 0, maxValue);
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


    /**
     * Normalizes an integer to the range of [-1..1] with different amounts in the negative and
     * positive direction. E.g. a value might be in the range of [-64..+63] but is stored as
     * [0..127]. The method might then be called with normalizeIntegerRange(value,-64,63,64).
     *
     * @param value The value to normalize
     * @param negativeMinimum The negative minimum of the range
     * @param positiveMaximum The negative maximum of the range
     * @param offset An offset to remove first from the value
     * @return The normalized value
     */
    public static double normalizeIntegerRange (final int value, final int negativeMinimum, final int positiveMaximum, final int offset)
    {
        return normalizeIntegerRange (value - offset, negativeMinimum, positiveMaximum);
    }


    /**
     * Normalizes an integer to the range of [-1..1] with different amounts in the negative and
     * positive direction.
     *
     * @param value The integer value to normalize
     * @param negativeMinimum The negative minimum of the integer range
     * @param positiveMaximum The negative maximum of the integer range
     * @return The normalized double value
     */
    public static double normalizeIntegerRange (final int value, final int negativeMinimum, final int positiveMaximum)
    {
        final double result = value < 0 ? -(value / (double) negativeMinimum) : value / (double) positiveMaximum;
        return Math.clamp (result, -1, 1);
    }


    /**
     * De-normalizes a double in the range of [-1..1] to an integer range with different amounts in
     * the negative and positive direction.
     *
     * @param value The value to de-normalize
     * @param negativeMinimum The negative minimum of the integer range
     * @param positiveMaximum The negative maximum of the integer range
     * @param offset An offset to add to the de-normalized integer value
     * @return The normalized value
     */
    public static int denormalizeIntegerRange (final double value, final int negativeMinimum, final int positiveMaximum, final int offset)
    {
        return denormalizeIntegerRange (value, negativeMinimum, positiveMaximum) + offset;
    }


    /**
     * De-normalizes a double in the range of [-1..1] to an integer range with different amounts in
     * the negative and positive direction.
     *
     * @param value The double value to de-normalize
     * @param negativeMinimum The negative minimum of the integer range
     * @param positiveMaximum The negative maximum of the integer range
     * @return The de-normalized integer value
     */
    public static int denormalizeIntegerRange (final double value, final int negativeMinimum, final int positiveMaximum)
    {
        if (value < 0)
            return Math.clamp (-Math.round (value * negativeMinimum), negativeMinimum, 0);
        return Math.clamp (Math.round (value * positiveMaximum), 0, positiveMaximum);
    }
}
