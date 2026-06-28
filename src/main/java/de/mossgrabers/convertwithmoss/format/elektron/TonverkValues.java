// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

/**
 * Conversions between the normalized parameter values stored in a Tonverk preset (most continuous
 * parameters are stored as a floating point number in the range [0..1]) and the physical units used
 * by the ConvertWithMoss model (envelope times in seconds, filter cut-off in Hertz).
 * <p>
 * The Tonverk firmware uses internal, non-published curves to map these normalized values to times
 * and frequencies. Since those curves are not contained in the preset files, the mappings below are
 * a power curve for times and an exponential curve for the filter cut-off. The envelope time maxima
 * were calibrated against hardware resamples (see the constants below); the firmware was found to
 * follow the same power curve, so the seconds mapping is close, not merely a guess. A
 * Tonverk-to-Tonverk round-trip is loss-less either way (the inverse functions are exact). All range
 * constants are gathered here so they can be tuned in one place.
 *
 * @author Jürgen Moßgraber
 */
public final class TonverkValues
{
    // Envelope time maxima (the seconds reached at normalized value 1.0). Hardware-calibrated
    // 2026-06-27 against Tonverk resamples of presets with known normalized values: the firmware
    // follows the same seconds = max * normalized^3 cube law the writer uses, so each maximum
    // below is the device's measured time at normalized 1.0. Attack is a linear ramp topping out
    // near 1.6 s (two probes agree: norm 0.63 -> 0.40 s and norm 0.79 -> 0.78 s, both implying
    // max ~1.58 s). Decay/release are an exponential fade whose time to -60 dB tops out near 22 s
    // (norm 0.63 -> ~5.5 s). Earlier guesses (attack 8 s, release 24 s) made attacks 5x too fast.

    /** Maximum attack time in seconds (normalized value 1.0). */
    private static final double ATTACK_MAX_SECONDS  = 1.6;
    /** Maximum hold time in seconds (normalized value 1.0); shares the attack scale (unprobed). */
    private static final double HOLD_MAX_SECONDS    = 1.6;
    /** Maximum decay time in seconds (normalized value 1.0); shares the release scale (unprobed). */
    private static final double DECAY_MAX_SECONDS   = 22.0;
    /** Maximum release time in seconds (normalized value 1.0). */
    private static final double RELEASE_MAX_SECONDS = 22.0;
    /** Maximum delay time in seconds (normalized value 1.0). */
    private static final double DELAY_MAX_SECONDS   = 4.0;
    /**
     * The exponent of the power curve used for all envelope times. A value &gt; 1 gives finer control
     * for short times. <code>seconds = max * normalized^curve</code>.
     */
    private static final double TIME_CURVE          = 3.0;

    /** Minimum filter cut-off frequency in Hertz (normalized value 0.0). */
    private static final double MIN_CUTOFF_HZ       = 20.0;
    /** Maximum filter cut-off frequency in Hertz (normalized value 1.0). */
    private static final double MAX_CUTOFF_HZ       = 20000.0;


    private TonverkValues ()
    {
        // Utility class
    }


    /**
     * Convert a normalized envelope delay value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToDelayTime (final double normalized)
    {
        return normalizedToTime (normalized, DELAY_MAX_SECONDS);
    }


    /**
     * Convert an envelope delay time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double delayTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, DELAY_MAX_SECONDS);
    }


    /**
     * Convert a normalized envelope attack value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToAttackTime (final double normalized)
    {
        return normalizedToTime (normalized, ATTACK_MAX_SECONDS);
    }


    /**
     * Convert an envelope attack time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double attackTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, ATTACK_MAX_SECONDS);
    }


    /**
     * Convert a normalized envelope hold value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToHoldTime (final double normalized)
    {
        return normalizedToTime (normalized, HOLD_MAX_SECONDS);
    }


    /**
     * Convert an envelope hold time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double holdTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, HOLD_MAX_SECONDS);
    }


    /**
     * Convert a normalized envelope decay value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToDecayTime (final double normalized)
    {
        return normalizedToTime (normalized, DECAY_MAX_SECONDS);
    }


    /**
     * Convert an envelope decay time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double decayTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, DECAY_MAX_SECONDS);
    }


    /**
     * Convert a normalized envelope release value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToReleaseTime (final double normalized)
    {
        return normalizedToTime (normalized, RELEASE_MAX_SECONDS);
    }


    /**
     * Convert an envelope release time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double releaseTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, RELEASE_MAX_SECONDS);
    }


    /**
     * Convert a normalized filter cut-off value to a frequency in Hertz.
     *
     * @param normalized The normalized value [0..1]
     * @return The frequency in Hertz
     */
    public static double normalizedToCutoff (final double normalized)
    {
        final double clamped = clampNormalized (normalized);
        return MIN_CUTOFF_HZ * Math.pow (MAX_CUTOFF_HZ / MIN_CUTOFF_HZ, clamped);
    }


    /**
     * Convert a filter cut-off frequency in Hertz to a normalized value.
     *
     * @param hertz The frequency in Hertz
     * @return The normalized value [0..1]
     */
    public static double cutoffToNormalized (final double hertz)
    {
        if (hertz <= MIN_CUTOFF_HZ)
            return 0;
        if (hertz >= MAX_CUTOFF_HZ)
            return 1;
        return Math.log (hertz / MIN_CUTOFF_HZ) / Math.log (MAX_CUTOFF_HZ / MIN_CUTOFF_HZ);
    }


    /**
     * Clamp a value to the normalized range [0..1].
     *
     * @param normalized The value
     * @return The clamped value
     */
    public static double clampNormalized (final double normalized)
    {
        return Math.clamp (normalized, 0.0, 1.0);
    }


    private static double normalizedToTime (final double normalized, final double maxSeconds)
    {
        return maxSeconds * Math.pow (clampNormalized (normalized), TIME_CURVE);
    }


    private static double timeToNormalized (final double seconds, final double maxSeconds)
    {
        if (seconds <= 0)
            return 0;
        if (seconds >= maxSeconds)
            return 1;
        return Math.pow (seconds / maxSeconds, 1.0 / TIME_CURVE);
    }
}
