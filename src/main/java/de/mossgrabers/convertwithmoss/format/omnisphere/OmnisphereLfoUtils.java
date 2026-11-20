// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;


/**
 * Some helper functions for converting LFO settings and their modulation amounts. The modulation
 * matrix adds the LFO signal multiplied by the amount to the normalized value [0..1] of the target
 * parameter. Therefore, an amount of 0.5 sweeps a target which is set to its center value over its
 * full range.
 *
 * @author Jürgen Moßgraber
 */
public class OmnisphereLfoUtils
{
    /** The pitch modulation of an amount of 1, coarse tuning ranges from -4800 to 4800 cents. */
    public static final double  PITCH_RANGE_CENTS    = 9600.0;
    /**
     * The default amplitude of a layer, which is used as the maximum. Amplitude modulation is not
     * audible above it.
     */
    public static final double  MAX_AMPLITUDE        = 0.75;

    private static final double MAX_RATE_HZ          = 60.0;
    // The waveforms are stored as (index + 1) * 0.02 in the order Sine, Triangle, Square, Rounded
    // Square, Saw, Reverse Saw, Sample and Hold, Heart Beat and Random. Sine is stored as 0.
    private static final double WAVEFORM_STEP        = 0.02;
    private static final int    WAVEFORM_SINE        = 1;
    private static final int    WAVEFORM_TRIANGLE    = 2;
    private static final int    WAVEFORM_SQUARE      = 3;
    private static final int    WAVEFORM_ROUNDED_SQ  = 4;
    private static final int    WAVEFORM_SAW         = 5;
    private static final int    WAVEFORM_REVERSE_SAW = 6;
    private static final int    WAVEFORM_SAMPLE_HOLD = 7;
    private static final int    WAVEFORM_RANDOM      = 9;
    private static final double MIN_AMPLITUDE_FACTOR = 0.00001;


    /**
     * Private due to utility class.
     */
    private OmnisphereLfoUtils ()
    {
        // Intentionally empty
    }


    /**
     * Maps the normalized LFO rate to Hertz.
     *
     * @param normalizedRate The normalized rate in the range of [0..1]
     * @return The rate in Hertz
     */
    public static double normalizedToHertz (final double normalizedRate)
    {
        final double rate = Math.clamp (normalizedRate, 0.0, 1.0);
        return MAX_RATE_HZ * rate * rate * rate;
    }


    /**
     * Maps a rate in Hertz to the normalized LFO rate.
     *
     * @param hertz The rate in Hertz
     * @return The normalized rate in the range of [0..1]
     */
    public static double hertzToNormalized (final double hertz)
    {
        return Math.cbrt (Math.clamp (hertz, 0.0, MAX_RATE_HZ) / MAX_RATE_HZ);
    }


    /**
     * Get the waveform of the given type value.
     *
     * @param type The type value of the LFO
     * @return The waveform, Heart Beat is mapped to Sine
     */
    public static LfoWaveform getWaveform (final double type)
    {
        return switch ((int) Math.round (type / WAVEFORM_STEP))
        {
            case WAVEFORM_SINE -> LfoWaveform.SINE;
            case WAVEFORM_TRIANGLE -> LfoWaveform.TRIANGLE;
            case WAVEFORM_SQUARE, WAVEFORM_ROUNDED_SQ -> LfoWaveform.SQUARE;
            // Saw falls from the highest to the lowest value
            case WAVEFORM_SAW -> LfoWaveform.SAWTOOTH_DOWN;
            case WAVEFORM_REVERSE_SAW -> LfoWaveform.SAWTOOTH_UP;
            case WAVEFORM_SAMPLE_HOLD, WAVEFORM_RANDOM -> LfoWaveform.RANDOM;
            default -> LfoWaveform.SINE;
        };
    }


    /**
     * Get the type value for the given waveform.
     *
     * @param waveform The waveform
     * @return The type value
     */
    public static double getType (final LfoWaveform waveform)
    {
        return switch (waveform)
        {
            case TRIANGLE -> WAVEFORM_TRIANGLE * WAVEFORM_STEP;
            case SQUARE -> WAVEFORM_SQUARE * WAVEFORM_STEP;
            case SAWTOOTH_DOWN -> WAVEFORM_SAW * WAVEFORM_STEP;
            case SAWTOOTH_UP -> WAVEFORM_REVERSE_SAW * WAVEFORM_STEP;
            case RANDOM -> WAVEFORM_SAMPLE_HOLD * WAVEFORM_STEP;
            default -> 0;
        };
    }


    /**
     * Get the filter cutoff modulation in cents. Since the frequency of the cutoff is not mapped
     * exponentially to the normalized value, the upwards modulation from the given cutoff is used.
     *
     * @param normalizedCutoff The normalized value of the cutoff in the range of [0..1]
     * @param amount The modulation amount in the range of [0..1]
     * @return The modulation in cents
     */
    public static double cutoffAmountToCents (final double normalizedCutoff, final double amount)
    {
        final double cutoff = Math.clamp (normalizedCutoff, 0.0, 1.0);
        final double baseHertz = OmnisphereFilterUtils.normalizedToHertz (cutoff);
        final double maxHertz = OmnisphereFilterUtils.normalizedToHertz (Math.min (1.0, cutoff + amount));
        return 1200.0 * Math.log (maxHertz / baseHertz) / Math.log (2.0);
    }


    /**
     * Get the modulation amount which modulates the cutoff upwards by the given number of cents.
     *
     * @param cutoffHertz The cutoff frequency in Hertz
     * @param cents The modulation in cents
     * @return The modulation amount in the range of [0..1]
     */
    public static double centsToCutoffAmount (final double cutoffHertz, final double cents)
    {
        final double maxHertz = cutoffHertz * Math.pow (2.0, cents / 1200.0);
        final double amount = OmnisphereFilterUtils.hertzToNormalized (maxHertz) - OmnisphereFilterUtils.hertzToNormalized (cutoffHertz);
        return Math.clamp (amount, 0.0, 1.0);
    }


    /**
     * Get the volume modulation in dB. The amplitude is assumed to be linear, the downwards
     * modulation from the given amplitude, which is limited to the maximum amplitude, is used.
     *
     * @param amplitude The amplitude in the range of [0..1]
     * @param amount The modulation amount in the range of [0..1]
     * @return The modulation in dB
     */
    public static double amplitudeAmountToDb (final double amplitude, final double amount)
    {
        final double limitedAmplitude = Math.min (amplitude, MAX_AMPLITUDE);
        if (limitedAmplitude <= 0)
            return 0;
        final double factor = Math.max (MIN_AMPLITUDE_FACTOR, 1.0 - amount / limitedAmplitude);
        return -20.0 * Math.log10 (factor);
    }


    /**
     * Get the modulation amount which lowers the given amplitude by the given number of dB.
     *
     * @param amplitude The amplitude in the range of [0..1]
     * @param db The modulation in dB
     * @return The modulation amount in the range of [0..1]
     */
    public static double dbToAmplitudeAmount (final double amplitude, final double db)
    {
        return Math.clamp (amplitude * (1.0 - Math.pow (10.0, -db / 20.0)), 0.0, 1.0);
    }
}
