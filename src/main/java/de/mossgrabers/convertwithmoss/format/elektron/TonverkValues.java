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
 * documented approximations: a power curve for times and an exponential curve for the filter cut-off.
 * They are chosen so that a Tonverk-to-Tonverk round-trip is loss-less (the inverse functions are
 * exact), while a conversion to/from a unit-based format (e.g. seconds) is a close approximation. All
 * range constants are gathered here so they can be tuned in one place.
 *
 * @author Jürgen Moßgraber
 */
public final class TonverkValues
{
    /** Maximum attack time in seconds (normalized value 1.0). */
    private static final double ATTACK_MAX_SECONDS  = 8.0;
    /** Maximum hold time in seconds (normalized value 1.0). */
    private static final double HOLD_MAX_SECONDS    = 8.0;
    /** Maximum decay time in seconds (normalized value 1.0). */
    private static final double DECAY_MAX_SECONDS   = 24.0;
    /** Maximum release time in seconds (normalized value 1.0). */
    private static final double RELEASE_MAX_SECONDS = 24.0;
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
