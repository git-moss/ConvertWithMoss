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
 * and frequencies. They are not contained in the preset files, so the mappings below were
 * reverse-engineered by resampling probe presets on real hardware and measuring the resulting
 * envelopes. The firmware maps a normalized value to a time with a <b>warped exponential</b>:
 *
 * <pre>
 * seconds = floor * (ceiling / floor) ^ (normalized ^ warp)
 * </pre>
 *
 * i.e. a logarithmic interpolation between a per-stage floor (the time at normalized 0) and ceiling
 * (the time at normalized 1), with the control value first warped by a power. This is the same
 * exponential form already used for the filter cut-off (where <code>warp = 1</code>), so the two
 * mappings are consistent. The inverse is exact, hence a Tonverk-to-Tonverk round-trip is
 * loss-less.
 * <p>
 * Calibration (2026-06-28) against Tonverk resamples of probe presets with known normalized values:
 * <ul>
 * <li>Attack is a linear amplitude ramp. Three probes (norm 0.25 -&gt; 0.056 s, 0.50 -&gt; 0.256 s,
 * 0.97 -&gt; 3.77 s) fit floor ~0.01 s, ceiling ~4.46 s, warp ~0.91 (within 0.2 %).</li>
 * <li>Decay and release are an exponential fade; their time is measured to -60 dB. They were found
 * to share one curve (decay and release both gave 4.64 s at norm 0.60). Three probes (norm 0.30
 * -&gt; 0.72 s, 0.60 -&gt; 4.65 s, 0.90 -&gt; 19.96 s) fit floor ~0.01 s, ceiling ~30.8 s, warp
 * ~0.53.</li>
 * <li>Hold and delay were not probed; they reuse the attack curve (delay keeps its 4 s
 * ceiling).</li>
 * </ul>
 * Earlier guesses (a cube law with attack 1.6 s / 8 s and release 22 s / 24 s ceilings) had both
 * the wrong shape and the wrong ceilings - the hardware demonstrably takes 3.77 s at attack norm
 * 0.97, already past the old 1.6 s maximum. All range constants are gathered here so they can be
 * tuned in one place.
 *
 * @author Jürgen Moßgraber
 */
public final class TonverkValues
{
    // Per-stage envelope time curves: the floor (seconds at normalized 0), the ceiling (seconds at
    // normalized 1) and the warp exponent applied to the normalized value. See the class comment
    // for
    // the calibration measurements.

    /** Delay time floor in seconds (normalized 0); unprobed, reuses the attack shape. */
    private static final double DELAY_FLOOR_SECONDS     = 0.010;
    /** Delay time ceiling in seconds (normalized 1); unprobed, reuses the attack shape. */
    private static final double DELAY_CEILING_SECONDS   = 4.0;
    /** Delay time warp exponent; unprobed, reuses the attack shape. */
    private static final double DELAY_WARP              = 0.911;

    /** Attack time floor in seconds (normalized 0); hardware-calibrated. */
    private static final double ATTACK_FLOOR_SECONDS    = 0.010;
    /** Attack time ceiling in seconds (normalized 1); hardware-calibrated. */
    private static final double ATTACK_CEILING_SECONDS  = 4.46;
    /** Attack time warp exponent; hardware-calibrated. */
    private static final double ATTACK_WARP             = 0.911;

    /** Hold time floor in seconds (normalized 0); unprobed, reuses the attack shape. */
    private static final double HOLD_FLOOR_SECONDS      = 0.010;
    /** Hold time ceiling in seconds (normalized 1); unprobed, reuses the attack shape. */
    private static final double HOLD_CEILING_SECONDS    = 4.46;
    /** Hold time warp exponent; unprobed, reuses the attack shape. */
    private static final double HOLD_WARP               = 0.911;

    /** Decay time floor in seconds (normalized 0); shares the release curve. */
    private static final double DECAY_FLOOR_SECONDS     = 0.011;
    /** Decay time ceiling in seconds (normalized 1); shares the release curve. */
    private static final double DECAY_CEILING_SECONDS   = 30.8;
    /** Decay time warp exponent; shares the release curve. */
    private static final double DECAY_WARP              = 0.533;

    /** Release time floor in seconds (normalized 0); hardware-calibrated. */
    private static final double RELEASE_FLOOR_SECONDS   = 0.011;
    /** Release time ceiling in seconds (normalized 1); hardware-calibrated. */
    private static final double RELEASE_CEILING_SECONDS = 30.8;
    /** Release time warp exponent; hardware-calibrated. */
    private static final double RELEASE_WARP            = 0.533;

    /** Minimum filter cut-off frequency in Hertz (normalized value 0.0). */
    private static final double MIN_CUTOFF_HZ           = 20.0;
    /** Maximum filter cut-off frequency in Hertz (normalized value 1.0). */
    private static final double MAX_CUTOFF_HZ           = 20000.0;


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
        return normalizedToTime (normalized, DELAY_FLOOR_SECONDS, DELAY_CEILING_SECONDS, DELAY_WARP);
    }


    /**
     * Convert an envelope delay time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double delayTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, DELAY_FLOOR_SECONDS, DELAY_CEILING_SECONDS, DELAY_WARP);
    }


    /**
     * Convert a normalized envelope attack value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToAttackTime (final double normalized)
    {
        return normalizedToTime (normalized, ATTACK_FLOOR_SECONDS, ATTACK_CEILING_SECONDS, ATTACK_WARP);
    }


    /**
     * Convert an envelope attack time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double attackTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, ATTACK_FLOOR_SECONDS, ATTACK_CEILING_SECONDS, ATTACK_WARP);
    }


    /**
     * Convert a normalized envelope hold value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToHoldTime (final double normalized)
    {
        return normalizedToTime (normalized, HOLD_FLOOR_SECONDS, HOLD_CEILING_SECONDS, HOLD_WARP);
    }


    /**
     * Convert an envelope hold time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double holdTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, HOLD_FLOOR_SECONDS, HOLD_CEILING_SECONDS, HOLD_WARP);
    }


    /**
     * Convert a normalized envelope decay value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToDecayTime (final double normalized)
    {
        return normalizedToTime (normalized, DECAY_FLOOR_SECONDS, DECAY_CEILING_SECONDS, DECAY_WARP);
    }


    /**
     * Convert an envelope decay time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double decayTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, DECAY_FLOOR_SECONDS, DECAY_CEILING_SECONDS, DECAY_WARP);
    }


    /**
     * Convert a normalized envelope release value to seconds.
     *
     * @param normalized The normalized value [0..1]
     * @return The time in seconds
     */
    public static double normalizedToReleaseTime (final double normalized)
    {
        return normalizedToTime (normalized, RELEASE_FLOOR_SECONDS, RELEASE_CEILING_SECONDS, RELEASE_WARP);
    }


    /**
     * Convert an envelope release time in seconds to a normalized value.
     *
     * @param seconds The time in seconds
     * @return The normalized value [0..1]
     */
    public static double releaseTimeToNormalized (final double seconds)
    {
        return timeToNormalized (seconds, RELEASE_FLOOR_SECONDS, RELEASE_CEILING_SECONDS, RELEASE_WARP);
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


    /**
     * Map a normalized value to a time using the firmware's warped-exponential curve:
     * <code>seconds = floor * (ceiling / floor) ^ (normalized ^ warp)</code>.
     *
     * @param normalized The normalized value [0..1]
     * @param floorSeconds The time in seconds at normalized 0
     * @param ceilingSeconds The time in seconds at normalized 1
     * @param warp The exponent applied to the normalized value before the exponential
     * @return The time in seconds
     */
    private static double normalizedToTime (final double normalized, final double floorSeconds, final double ceilingSeconds, final double warp)
    {
        final double clamped = clampNormalized (normalized);
        return floorSeconds * Math.pow (ceilingSeconds / floorSeconds, Math.pow (clamped, warp));
    }


    /**
     * Inverse of {@link #normalizedToTime}: map a time back to a normalized value. Times at or
     * below the floor map to 0, times at or above the ceiling map to 1.
     *
     * @param seconds The time in seconds
     * @param floorSeconds The time in seconds at normalized 0
     * @param ceilingSeconds The time in seconds at normalized 1
     * @param warp The exponent applied to the normalized value before the exponential
     * @return The normalized value [0..1]
     */
    private static double timeToNormalized (final double seconds, final double floorSeconds, final double ceilingSeconds, final double warp)
    {
        if (seconds <= floorSeconds)
            return 0;
        if (seconds >= ceilingSeconds)
            return 1;
        final double exponent = Math.log (seconds / floorSeconds) / Math.log (ceilingSeconds / floorSeconds);
        return Math.pow (exponent, 1.0 / warp);
    }
}
