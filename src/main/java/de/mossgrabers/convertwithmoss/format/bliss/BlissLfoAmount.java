// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bliss;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;


/**
 * Converts the depth of an LFO modulator of the model to the amount of a Bliss LFO route
 * (mod_lfoN_amt) and back. The mapping depends on the destination of the route:
 * <ul>
 * <li>Amp: unipolar, depth = value * 0.5, the gain swings between 1 and 1 - 2 * depth, therefore
 * 1.0 is a full tremolo.</li>
 * <li>Pitch: centre-biased, depth = value - 0.5, the LFO adds lfo * depth * 4 octaves.</li>
 * <li>Filter cutoff: centre-biased, depth = value - 0.5, the LFO adds lfo * depth to the
 * normalized cutoff.</li>
 * </ul>
 * The modulation wheel morphs the normalized value of a parameter: effective = clamp (value +
 * sense * modwheel, 0, 1), the sense is stored in the range of [-1..1].
 *
 * @author Jürgen Moßgraber
 */
public final class BlissLfoAmount
{
    /** The destination of an LFO route: amplifier. */
    public static final int     DESTINATION_AMP    = 1;
    /** The destination of an LFO route: pitch. */
    public static final int     DESTINATION_PITCH  = 2;
    /** The destination of an LFO route: filter 1 cutoff frequency. */
    public static final int     DESTINATION_CUTOFF = 3;

    private static final double PITCH_RANGE_CENTS  = 4 * 1200.0;
    private static final double MIN_CUTOFF         = 20.0;
    private static final double MAX_CUTOFF         = 22050.0;


    /**
     * Private constructor for utility class.
     */
    private BlissLfoAmount ()
    {
        // Intentionally empty
    }


    /**
     * Convert the depth of an LFO modulator to the normalized amount of the route.
     *
     * @param destination The destination of the route
     * @param depth The depth of the modulator in the range of [-1..1]
     * @param cutoff The cutoff frequency of the filter in Hertz, only used for the cutoff
     * @return The normalized amount in the range of [0..1]
     */
    public static double toValue (final int destination, final double depth, final double cutoff)
    {
        switch (destination)
        {
            case DESTINATION_AMP:
                final double decibels = Math.min (Math.abs (depth), 1.0) * ILfoModulator.MAX_VOLUME_DEPTH;
                return 1.0 - Math.pow (10.0, -decibels / 20.0);

            case DESTINATION_PITCH:
                return Math.clamp (0.5 + depth * IEnvelope.MAX_ENVELOPE_DEPTH / PITCH_RANGE_CENTS, 0, 1);

            case DESTINATION_CUTOFF:
                final double cents = Math.min (Math.abs (depth), 1.0) * IEnvelope.MAX_ENVELOPE_DEPTH;
                return Math.clamp (0.5 + Math.signum (depth) * getCutoffSwing (cutoff, cents), 0, 1);

            default:
                return 0;
        }
    }


    /**
     * Convert the normalized amount of the route to the depth of an LFO modulator.
     *
     * @param destination The destination of the route
     * @param value The normalized amount in the range of [0..1]
     * @param cutoff The cutoff frequency of the filter in Hertz, only used for the cutoff
     * @return The depth of the modulator in the range of [-1..1]
     */
    public static double toDepth (final int destination, final double value, final double cutoff)
    {
        switch (destination)
        {
            case DESTINATION_AMP:
                final double swing = Math.clamp (value, 0, 1);
                if (swing == 0)
                    return 0;
                final double decibels = swing >= 1.0 ? ILfoModulator.MAX_VOLUME_DEPTH : Math.min (-20.0 * Math.log10 (1.0 - swing), ILfoModulator.MAX_VOLUME_DEPTH);
                return decibels / ILfoModulator.MAX_VOLUME_DEPTH;

            case DESTINATION_PITCH:
                return Math.clamp ((value - 0.5) * PITCH_RANGE_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH, -1, 1);

            case DESTINATION_CUTOFF:
                final double depth = value - 0.5;
                return Math.signum (depth) * getCutoffCents (cutoff, Math.abs (depth)) / IEnvelope.MAX_ENVELOPE_DEPTH;

            default:
                return 0;
        }
    }


    /**
     * Get the morph amount (sense) which results in the depth of the modulator plus the depth which
     * the modulation wheel adds at its maximum.
     *
     * @param destination The destination of the route
     * @param modulator The LFO modulator
     * @param cutoff The cutoff frequency of the filter in Hertz, only used for the cutoff
     * @return The sense in the range of [-1..1]
     */
    public static double toSense (final int destination, final ILfoModulator modulator, final double cutoff)
    {
        final double depth = modulator.getDepth ();
        final double modWheelDepth = modulator.getDepthModWheelModulator ().getDepth ();
        if (modWheelDepth == 0)
            return 0;
        return Math.clamp (toValue (destination, depth + modWheelDepth, cutoff) - toValue (destination, depth, cutoff), -1, 1);
    }


    /**
     * Get the depth which the modulation wheel adds by morphing the amount of the route.
     *
     * @param destination The destination of the route
     * @param value The normalized amount in the range of [0..1]
     * @param sense The morph amount in the range of [-1..1]
     * @param cutoff The cutoff frequency of the filter in Hertz, only used for the cutoff
     * @return The depth which the modulation wheel adds
     */
    public static double toModWheelDepth (final int destination, final double value, final double sense, final double cutoff)
    {
        if (sense == 0)
            return 0;
        return toDepth (destination, Math.clamp (value + sense, 0, 1), cutoff) - toDepth (destination, value, cutoff);
    }


    /**
     * Get the normalized value of a cutoff frequency. The normalized value is squared and linearly
     * mapped to 20 Hz - 22050 Hz.
     *
     * @param cutoff The cutoff frequency in Hertz
     * @return The normalized value
     */
    public static double normalizeCutoff (final double cutoff)
    {
        return Math.sqrt (Math.clamp ((cutoff - MIN_CUTOFF) / (MAX_CUTOFF - MIN_CUTOFF), 0, 1));
    }


    /**
     * The LFO adds to the normalized cutoff, therefore the symmetric swing of a depth in cents is
     * the average of the swings upwards and downwards.
     *
     * @param cutoff The cutoff frequency in Hertz
     * @param cents The depth in cents
     * @return The swing of the normalized cutoff
     */
    private static double getCutoffSwing (final double cutoff, final double cents)
    {
        final double factor = Math.pow (2, cents / 1200.0);
        return (normalizeCutoff (cutoff * factor) - normalizeCutoff (cutoff / factor)) / 2.0;
    }


    /**
     * Get the depth in cents of a swing of the normalized cutoff, the inverse of getCutoffSwing.
     *
     * @param cutoff The cutoff frequency in Hertz
     * @param swing The swing of the normalized cutoff
     * @return The depth in cents
     */
    private static double getCutoffCents (final double cutoff, final double swing)
    {
        double low = 0;
        double high = IEnvelope.MAX_ENVELOPE_DEPTH;
        if (swing <= 0)
            return 0;
        if (getCutoffSwing (cutoff, high) <= swing)
            return high;
        for (int i = 0; i < 60; i++)
        {
            final double middle = (low + high) / 2.0;
            if (getCutoffSwing (cutoff, middle) < swing)
                low = middle;
            else
                high = middle;
        }
        return (low + high) / 2.0;
    }
}
