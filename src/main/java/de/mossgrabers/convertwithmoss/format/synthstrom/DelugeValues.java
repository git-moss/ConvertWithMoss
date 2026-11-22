// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synthstrom;

/**
 * Helper methods to convert between the values of the Synthstrom Deluge format and the model used
 * in ConvertWithMoss.
 * <p>
 * The Deluge stores all modulation/parameter values (envelopes, filters, volumes, ...) as signed
 * 32-bit integers written as upper-case hexadecimal strings (e.g. <code>0x7FFFFFFF</code>). The
 * full range <code>0x80000000 .. 0x7FFFFFFF</code> maps to a knob/menu position of
 * <code>0 .. 50</code>. Times and filter cut-off frequencies are non-linear (table driven) on the
 * device; they are approximated here with an exponential curve which is good enough for a faithful
 * musical result.
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
    public static final int     REFERENCE_NOTE     = 60;
    /** The minimum parameter value (0x80000000). */
    public static final int     PARAM_MIN          = Integer.MIN_VALUE;
    /** The maximum parameter value (0x7FFFFFFF). */
    public static final int     PARAM_MAX          = Integer.MAX_VALUE;
    /** A patch cable amount which corresponds to a fully open modulation (user value 50). */
    public static final int     PATCH_CABLE_FULL   = 0x3FFFFFE8;

    /** The maximum user/knob value. */
    private static final int    MAX_USER_VALUE     = 50;
    /** The scaling factor of one user value step (see firmware getParamFromUserValue). */
    private static final long   USER_VALUE_STEP    = 85899345L;

    /** The rate of an LFO at the neutral value of its rate parameter (121739 / 2^32 * 44100). */
    private static final double LFO_NEUTRAL_HERTZ  = 121739.0 / 4294967296.0 * 44100.0;
    /** The rate parameter difference which doubles the rate of an LFO. */
    private static final double LFO_RATE_OCTAVE    = 268435456.0;
    /** The value of an exponential parameter which equals one octave. */
    private static final double EXP_PER_OCTAVE     = 67108864.0;
    /** The scaling of the squared amount of a patch cable to the pitch (about 1/sqrt(2)). */
    private static final double PITCH_AMOUNT_SCALE = 1518500250.0 / 2147483648.0;
    /** The smallest amplitude factor of a volume modulation to avoid an infinite dB value. */
    private static final double MIN_AMPLITUDE      = 0.001;

    private static final double MIN_FREQUENCY      = 20.0;
    private static final double MAX_FREQUENCY      = 20000.0;

    /** The envelope stage length threshold (2^23) used by the firmware envelope engine. */
    private static final int    ENV_STAGE_LENGTH   = 8388608;
    /** The device sample rate in Hz at which the envelope rate tables are defined. */
    private static final double DEVICE_RATE        = 44100.0;

    /**
     * The attack rate table of the firmware indexed by the knob position (0..50). The actual attack
     * time is <code>ENV_STAGE_LENGTH / (rate * DEVICE_RATE)</code> seconds (about 0.7 ms .. 3.0 s).
     */
    private static final int [] ATTACK_RATE_TABLE  =
    {
        262144,
        221969,
        187951,
        159147,
        134757,
        114105,
        96618,
        81811,
        69273,
        58656,
        49667,
        42055,
        35610,
        30153,
        25532,
        21619,
        18306,
        15500,
        13125,
        11113,
        9410,
        7968,
        6747,
        5713,
        4837,
        4096,
        3468,
        2937,
        2487,
        2106,
        1783,
        1510,
        1278,
        1082,
        917,
        776,
        657,
        556,
        471,
        399,
        338,
        286,
        242,
        205,
        174,
        147,
        124,
        105,
        89,
        76,
        64
    };

    /**
     * The decay/release rate table of the firmware indexed by the knob position (0..50). Decay and
     * release share this table. The actual time is
     * <code>ENV_STAGE_LENGTH / (rate * DEVICE_RATE)</code> seconds (about 5.8 ms .. 5.9 s).
     */
    private static final int [] RELEASE_RATE_TABLE =
    {
        32691,
        4604,
        2444,
        1648,
        1234,
        980,
        809,
        685,
        592,
        519,
        460,
        412,
        372,
        338,
        309,
        283,
        261,
        241,
        224,
        208,
        194,
        181,
        169,
        159,
        149,
        140,
        132,
        124,
        117,
        110,
        104,
        98,
        93,
        88,
        83,
        78,
        74,
        70,
        66,
        62,
        59,
        56,
        53,
        50,
        47,
        44,
        41,
        39,
        36,
        34,
        32
    };


    /**
     * Constructor. Hidden, since this is a utility class.
     */
    private DelugeValues ()
    {
        // Intentionally empty
    }


    /**
     * Format an integer parameter value as the upper-case hexadecimal string expected by the
     * Deluge.
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
            if (trimmed.length () > 2 && trimmed.charAt (0) == '0' && (trimmed.charAt (1) == 'x' || trimmed.charAt (1) == 'X'))
            {
                final int end = Math.min (10, trimmed.length ());
                return (int) Long.parseLong (trimmed.substring (2, end), 16);
            }
            return (int) Long.parseLong (trimmed);
        }
        catch (final NumberFormatException _)
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
        return (int) (clampUserValue (userValue) * USER_VALUE_STEP - 2147483648L);
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
     * Convert a 32-bit volume parameter value to a gain in decibels. The value is interpreted as a
     * linear amplitude level (see {@link #paramToLevel}), so the gain is
     * <code>20 * log10(level)</code> relative to the full volume, which maps to 0 dB. Lower volumes
     * attenuate; the maximum never boosts. A muted volume is clamped to a very low but finite gain
     * to avoid negative infinity.
     *
     * @param param The 32-bit parameter value
     * @return The gain in decibels (0 or negative)
     */
    public static double paramToGainDecibels (final int param)
    {
        final double level = paramToLevel (param);
        return level <= 0 ? -120.0 : 20.0 * Math.log10 (level);
    }


    /**
     * Convert a gain relative to the full volume of an oscillator into the 32-bit value of its
     * volume parameter (oscAVolume, oscBVolume). The firmware squares the volume of an oscillator:
     * the parameter value p, scaled to the range of [-1..1], plays the oscillator with the
     * amplitude (1 + p)^2. The full volume therefore plays it 4 times as loud as the middle
     * position (user value 25, -12 dB), and half of the full amplitude (-6 dB) is p = sqrt(2) - 1.
     *
     * @param gain The linear gain relative to the full volume in the range of [0..1]
     * @return The parameter value
     */
    public static int gainToOscillatorVolume (final double gain)
    {
        final double position = 2.0 * Math.sqrt (Math.clamp (gain, 0, 1)) - 1.0;
        return Math.clamp (Math.round (position * 2147483648.0), PARAM_MIN, PARAM_MAX);
    }


    /**
     * Convert an attack time in seconds to a 32-bit parameter value using the firmware attack rate
     * table.
     *
     * @param seconds The time in seconds
     * @return The parameter value
     */
    public static int attackTimeToParam (final double seconds)
    {
        return timeToParam (seconds, ATTACK_RATE_TABLE);
    }


    /**
     * Convert a 32-bit attack parameter value to a time in seconds.
     *
     * @param param The parameter value
     * @return The time in seconds
     */
    public static double paramToAttackTime (final int param)
    {
        return paramToTime (param, ATTACK_RATE_TABLE);
    }


    /**
     * Convert a decay or release time in seconds to a 32-bit parameter value. Decay and release
     * share the same firmware rate table.
     *
     * @param seconds The time in seconds
     * @return The parameter value
     */
    public static int releaseTimeToParam (final double seconds)
    {
        return timeToParam (seconds, RELEASE_RATE_TABLE);
    }


    /**
     * Convert a 32-bit decay or release parameter value to a time in seconds.
     *
     * @param param The parameter value
     * @return The time in seconds
     */
    public static double paramToReleaseTime (final int param)
    {
        return paramToTime (param, RELEASE_RATE_TABLE);
    }


    private static double rateToSeconds (final int rate)
    {
        return rate <= 0 ? Double.MAX_VALUE : ENV_STAGE_LENGTH / (rate * DEVICE_RATE);
    }


    private static int timeToParam (final double seconds, final int [] rateTable)
    {
        final int last = rateTable.length - 1;
        if (seconds <= rateToSeconds (rateTable[0]))
            return PARAM_MIN;
        if (seconds >= rateToSeconds (rateTable[last]))
            return PARAM_MAX;
        for (int i = 0; i < last; i++)
        {
            final double t0 = rateToSeconds (rateTable[i]);
            final double t1 = rateToSeconds (rateTable[i + 1]);
            if (seconds >= t0 && seconds <= t1)
            {
                final double userValue = i + (t1 <= t0 ? 0.0 : (seconds - t0) / (t1 - t0));
                return userValueToParam (userValue);
            }
        }
        return PARAM_MAX;
    }


    private static double paramToTime (final int param, final int [] rateTable)
    {
        final int last = rateTable.length - 1;
        final double userValue = Math.clamp ((param + 2147483648.0) / USER_VALUE_STEP, 0, MAX_USER_VALUE);
        final int index = (int) Math.floor (userValue);
        if (index >= last)
            return rateToSeconds (rateTable[last]);
        final double t0 = rateToSeconds (rateTable[index]);
        final double t1 = rateToSeconds (rateTable[index + 1]);
        return t0 + (t1 - t0) * (userValue - index);
    }


    private static int userValueToParam (final double userValue)
    {
        return (int) Math.round (Math.clamp (userValue, 0, MAX_USER_VALUE) * USER_VALUE_STEP - 2147483648.0);
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
     * Convert a Deluge patch-cable amount (e.g. the <i>note</i> to <i>lpfFrequency</i> modulation
     * used for filter keyboard tracking, or <i>envelope2</i>/<i>velocity</i> to a filter/amplitude
     * destination) into a normalized modulation depth in the range of [-1..1]. A patch amount of
     * {@link #PATCH_CABLE_FULL} (a fully open modulation) is mapped to full positive depth (+1,
     * i.e. +100%). This is an approximation of the Deluge's modulation depth; the reference amount
     * is the single place to calibrate it.
     *
     * @param amount The patch-cable amount
     * @return The modulation depth in the range of [-1..1]
     */
    public static double patchAmountToModulationDepth (final int amount)
    {
        return Math.clamp (amount / (double) PATCH_CABLE_FULL, -1.0, 1.0);
    }


    /**
     * Inverse of patchAmountToModulationDepth.
     *
     * @param modDepth The modulation depth in the range of [-1..1]
     * @return The patch-cable amount
     */
    public static long modulationDepthToPatchAmount (final double modDepth)
    {
        return (long) Math.clamp (modDepth * PATCH_CABLE_FULL, -PATCH_CABLE_FULL, PATCH_CABLE_FULL);
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


    /**
     * Convert an LFO rate parameter to Hertz. The rate is an exponential parameter of the firmware
     * (getExp in src/deluge/util/functions.cpp): its neutral value 121739 is the phase increment
     * per sample at the center of the knob (1.25 Hz at 44.1 kHz) and the parameter range of 2^30
     * scales the knob so that 2^28 equals one octave - 0.005 to 320 Hz over the full range.
     *
     * @param param The rate parameter value
     * @return The rate in Hertz
     */
    public static double lfoRateToHertz (final int param)
    {
        return LFO_NEUTRAL_HERTZ * Math.pow (2, param / LFO_RATE_OCTAVE);
    }


    /**
     * Inverse of lfoRateToHertz.
     *
     * @param hertz The rate in Hertz
     * @return The rate parameter value
     */
    public static int hertzToLfoRate (final double hertz)
    {
        if (hertz <= 0)
            return PARAM_MIN;
        final double param = Math.log (hertz / LFO_NEUTRAL_HERTZ) / Math.log (2) * LFO_RATE_OCTAVE;
        return (int) Math.round (Math.clamp (param, PARAM_MIN, PARAM_MAX));
    }


    /**
     * Calculate the rate of a tempo synchronized LFO at the given tempo. The sync level 1 is a
     * whole note per cycle, each following level halves the length of a cycle.
     *
     * @param syncLevel The sync level in the range of 1 (whole note) to 9 (256th note)
     * @param syncType The sync type: 0 = even, 1 = triplet, 2 = dotted
     * @param tempo The tempo in beats per minute
     * @return The rate in Hertz
     */
    public static double syncedLfoRateToHertz (final int syncLevel, final int syncType, final double tempo)
    {
        double hertz = tempo / 60.0 / 4.0 * Math.pow (2, syncLevel - 1.0);
        if (syncType == 1)
            hertz = hertz * 3.0 / 2.0;
        else if (syncType == 2)
            hertz = hertz * 2.0 / 3.0;
        return hertz;
    }


    /**
     * Convert the amount of a patch cable to the pitch into the modulation depth in cents at the
     * peak of a bipolar source. The firmware squares the amount of all cables to the pitch for a
     * finer control of small values and scales it by 1/sqrt(2), which limits the range to almost 3
     * octaves (getModifiedPatchCableAmount in patch_cable_set.cpp). The cable value - the source
     * value (+-2^31) multiplied by the amount and shifted right by 32 - is added to the exponential
     * pitch parameter in which 2^26 is one octave.
     *
     * @param amount The patch cable amount
     * @return The depth in cents
     */
    public static double pitchAmountToCents (final int amount)
    {
        final double squared = (double) (amount >> 15) * (amount >> 16);
        return Math.signum (amount) * squared * PITCH_AMOUNT_SCALE / EXP_PER_OCTAVE / 2.0 * 1200.0;
    }


    /**
     * Inverse of pitchAmountToCents.
     *
     * @param cents The depth in cents
     * @return The patch cable amount
     */
    public static int centsToPitchAmount (final double cents)
    {
        final double squared = Math.abs (cents) / 1200.0 * 2.0 * EXP_PER_OCTAVE / PITCH_AMOUNT_SCALE;
        final double amount = Math.min (PATCH_CABLE_FULL, Math.sqrt (squared * 2147483648.0));
        return (int) Math.round (Math.signum (cents) * amount);
    }


    /**
     * Convert the amount of a patch cable to a filter frequency into the modulation depth in cents
     * at the peak of a bipolar source. The frequency is an exponential parameter in which 2^26 is
     * one octave, so a full amount modulates the cutoff by +-8 octaves.
     *
     * @param amount The patch cable amount
     * @return The depth in cents
     */
    public static double cutoffAmountToCents (final int amount)
    {
        return amount / 2.0 / EXP_PER_OCTAVE * 1200.0;
    }


    /**
     * Inverse of cutoffAmountToCents.
     *
     * @param cents The depth in cents
     * @return The patch cable amount
     */
    public static int centsToCutoffAmount (final double cents)
    {
        return (int) Math.round (Math.clamp (cents / 1200.0 * EXP_PER_OCTAVE * 2.0, -PATCH_CABLE_FULL, PATCH_CABLE_FULL));
    }


    /**
     * Convert the amount of a patch cable to the volume into the attenuation in dB at the negative
     * peak of a bipolar source. The firmware multiplies the volume by 1 + amount / 2^30 times the
     * source value (combineCablesLinear in patcher.cpp) and squares the result to get the amplitude
     * (getFinalParameterValueVolume), so a full amount reduces the level down to silence.
     *
     * @param amount The patch cable amount
     * @return The attenuation in dB, negative for a negative amount
     */
    public static double volumeAmountToDb (final int amount)
    {
        final double depth = Math.min (1.0, Math.abs (amount) / (double) PATCH_CABLE_FULL);
        return Math.signum (amount) * -40.0 * Math.log10 (Math.max (MIN_AMPLITUDE, 1.0 - depth));
    }


    /**
     * Inverse of volumeAmountToDb.
     *
     * @param db The attenuation in dB, negative for a negative amount
     * @return The patch cable amount
     */
    public static int dbToVolumeAmount (final double db)
    {
        final double depth = 1.0 - Math.pow (10.0, -Math.abs (db) / 40.0);
        return (int) Math.round (Math.signum (db) * Math.min (1.0, depth) * PATCH_CABLE_FULL);
    }


    private static int clampUserValue (final int userValue)
    {
        return Math.clamp (userValue, 0, MAX_USER_VALUE);
    }
}
