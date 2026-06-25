// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synthstrom;

/**
 * Helper methods to convert between the values of the Synthstrom Deluge format and the model used in
 * ConvertWithMoss.
 * <p>
 * The Deluge stores all modulation/parameter values (envelopes, filters, volumes, ...) as signed
 * 32-bit integers written as upper-case hexadecimal strings (e.g. <code>0x7FFFFFFF</code>). The full
 * range <code>0x80000000 .. 0x7FFFFFFF</code> maps to a knob/menu position of <code>0 .. 50</code>.
 * Times and filter cut-off frequencies are non-linear (table driven) on the device; they are
 * approximated here with an exponential curve which is good enough for a faithful musical result.
 * <p>
 * Sample pitch is stored as a <i>transpose</i> (semitones) plus <i>cents</i> offset relative to the
 * reference note 60. The original root note of a sample therefore is
 * <code>60 - transpose - cents / 100</code>.
 *
 * @author Jürgen Moßgraber
 */
public final class DelugeValues
{
    /** The reference MIDI note to which transpose/cents are relative. */
    public static final int     REFERENCE_NOTE   = 60;
    /** The minimum parameter value (0x80000000). */
    public static final int     PARAM_MIN        = Integer.MIN_VALUE;
    /** The maximum parameter value (0x7FFFFFFF). */
    public static final int     PARAM_MAX        = Integer.MAX_VALUE;
    /** A patch cable amount which corresponds to a fully open modulation (user value 50). */
    public static final int     PATCH_CABLE_FULL = 0x3FFFFFE8;

    /** The maximum user/knob value. */
    private static final int    MAX_USER_VALUE   = 50;
    /** The scaling factor of one user value step (see firmware getParamFromUserValue). */
    private static final long   USER_VALUE_STEP  = 85899345L;

    private static final double MIN_ENV_TIME     = 0.001;
    private static final double MAX_ENV_TIME     = 20.0;
    private static final double MIN_FREQUENCY    = 20.0;
    private static final double MAX_FREQUENCY    = 20000.0;


    /**
     * Constructor. Hidden, since this is a utility class.
     */
    private DelugeValues ()
    {
        // Intentionally empty
    }


    /**
     * Format an integer parameter value as the upper-case hexadecimal string expected by the Deluge.
     *
     * @param value The value
     * @return The formatted string e.g. <code>0x7FFFFFFF</code>
     */
    public static String formatHex (final int value)
    {
        return String.format ("0x%08X", Integer.valueOf (value));
    }


    /**
     * Parse a Deluge parameter value. Values are normally upper-case hexadecimal (prefixed with
     * <code>0x</code>) but plain decimal values (as written by some older firmware/tools) are
     * supported as well. Additional hexadecimal digits (automation nodes) are ignored.
     *
     * @param text The text to parse
     * @param defaultValue The value to return if the text is empty or cannot be parsed
     * @return The parsed value
     */
    public static int parseValue (final String text, final int defaultValue)
    {
        if (text == null)
            return defaultValue;
        final String trimmed = text.trim ();
        if (trimmed.isEmpty ())
            return defaultValue;

        try
        {
            if (trimmed.length () > 2 && (trimmed.charAt (0) == '0') && (trimmed.charAt (1) == 'x' || trimmed.charAt (1) == 'X'))
            {
                final int end = Math.min (10, trimmed.length ());
                return (int) Long.parseLong (trimmed.substring (2, end), 16);
            }
            return (int) Long.parseLong (trimmed);
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    /**
     * Convert a user/knob value (0..50) to a 32-bit parameter value (linear).
     *
     * @param userValue The user value in the range of [0..50]
     * @return The parameter value
     */
    public static int userValueToParam (final int userValue)
    {
        return (int) ((long) clampUserValue (userValue) * USER_VALUE_STEP - 2147483648L);
    }


    /**
     * Convert a 32-bit parameter value to a user/knob value (0..50, linear).
     *
     * @param param The parameter value
     * @return The user value in the range of [0..50]
     */
    public static int paramToUserValue (final int param)
    {
        return clampUserValue ((int) Math.round ((param + 2147483648.0) / USER_VALUE_STEP));
    }


    /**
     * Convert a level in the range of [0..1] to a 32-bit parameter value.
     *
     * @param level The level in the range of [0..1]
     * @return The parameter value
     */
    public static int levelToParam (final double level)
    {
        return userValueToParam ((int) Math.round (Math.clamp (level, 0, 1) * MAX_USER_VALUE));
    }


    /**
     * Convert a 32-bit parameter value to a level in the range of [0..1].
     *
     * @param param The parameter value
     * @return The level in the range of [0..1]
     */
    public static double paramToLevel (final int param)
    {
        return paramToUserValue (param) / (double) MAX_USER_VALUE;
    }


    /**
     * Convert an envelope time in seconds to a 32-bit parameter value (approximated).
     *
     * @param seconds The time in seconds, a negative value is interpreted as the fastest time
     * @return The parameter value
     */
    public static int timeToParam (final double seconds)
    {
        if (seconds <= MIN_ENV_TIME)
            return PARAM_MIN;
        final double userValue = MAX_USER_VALUE * Math.log (seconds / MIN_ENV_TIME) / Math.log (MAX_ENV_TIME / MIN_ENV_TIME);
        return userValueToParam ((int) Math.round (userValue));
    }


    /**
     * Convert a 32-bit envelope time parameter value to seconds (approximated).
     *
     * @param param The parameter value
     * @return The time in seconds
     */
    public static double paramToTime (final int param)
    {
        final int userValue = paramToUserValue (param);
        return MIN_ENV_TIME * Math.pow (MAX_ENV_TIME / MIN_ENV_TIME, userValue / (double) MAX_USER_VALUE);
    }


    /**
     * Convert a filter cut-off frequency in Hertz to a 32-bit parameter value (approximated).
     *
     * @param hertz The frequency in Hertz
     * @return The parameter value
     */
    public static int cutoffToParam (final double hertz)
    {
        final double clamped = Math.clamp (hertz, MIN_FREQUENCY, MAX_FREQUENCY);
        final double userValue = MAX_USER_VALUE * Math.log (clamped / MIN_FREQUENCY) / Math.log (MAX_FREQUENCY / MIN_FREQUENCY);
        return userValueToParam ((int) Math.round (userValue));
    }


    /**
     * Convert a 32-bit filter cut-off parameter value to a frequency in Hertz (approximated).
     *
     * @param param The parameter value
     * @return The frequency in Hertz
     */
    public static double paramToCutoff (final int param)
    {
        final int userValue = paramToUserValue (param);
        return MIN_FREQUENCY * Math.pow (MAX_FREQUENCY / MIN_FREQUENCY, userValue / (double) MAX_USER_VALUE);
    }


    /**
     * Calculate the root note of a sample from the Deluge transpose and cents values.
     *
     * @param transpose The transpose value in semitones
     * @param cents The cents value
     * @return The root note (might be fractional)
     */
    public static double rootNoteFromTranspose (final int transpose, final int cents)
    {
        return REFERENCE_NOTE - transpose - cents / 100.0;
    }


    /**
     * Calculate the Deluge transpose and cents values from a root note.
     *
     * @param rootNote The root note (might be fractional)
     * @return An array with the transpose value at index 0 and the cents value at index 1
     */
    public static int [] transposeCentsFromRootNote (final double rootNote)
    {
        final double semitones = REFERENCE_NOTE - rootNote;
        int transpose = (int) semitones;
        int cents = (int) Math.round ((semitones - transpose) * 100.0);
        if (cents <= -100)
        {
            transpose -= 1;
            cents += 100;
        }
        else if (cents >= 100)
        {
            transpose += 1;
            cents -= 100;
        }
        return new int []
        {
            transpose,
            cents
        };
    }


    private static int clampUserValue (final int userValue)
    {
        return Math.clamp (userValue, 0, MAX_USER_VALUE);
    }
}
