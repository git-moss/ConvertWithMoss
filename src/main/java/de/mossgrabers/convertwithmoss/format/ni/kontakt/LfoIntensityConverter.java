// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt;

import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;


/**
 * Converts the depth of an oscillator modulator of the model to the intensity of a Kontakt
 * modulator and back. The volume intensity of Kontakt is the amplitude reduction at the minimum of
 * the oscillator (1 = silence), the depth of the model is relative to
 * {@link ILfoModulator#MAX_VOLUME_DEPTH} dB. All other intensities are linear.
 *
 * @author Jürgen Moßgraber
 */
public final class LfoIntensityConverter
{
    /** The converter for the volume. */
    public static final LfoIntensityConverter VOLUME = new LfoIntensityConverter (true, 1.0);

    private final boolean                     isVolume;
    private final double                      depthPerIntensity;


    /**
     * Get a linear converter.
     *
     * @param depthPerIntensity The depth of the model which equals an intensity of 1
     * @return The converter
     */
    public static LfoIntensityConverter linear (final double depthPerIntensity)
    {
        return new LfoIntensityConverter (false, depthPerIntensity);
    }


    /**
     * Constructor.
     *
     * @param isVolume True to convert decibels to an amplitude
     * @param depthPerIntensity The depth of the model which equals an intensity of 1 (only linear)
     */
    private LfoIntensityConverter (final boolean isVolume, final double depthPerIntensity)
    {
        this.isVolume = isVolume;
        this.depthPerIntensity = depthPerIntensity;
    }


    /**
     * Convert a depth of the model to a Kontakt intensity.
     *
     * @param depth The depth in the range of [-1..1]
     * @return The intensity in the range of [-1..1]
     */
    public double toIntensity (final double depth)
    {
        if (!this.isVolume)
            return Math.clamp (depth / this.depthPerIntensity, -1.0, 1.0);
        final double decibels = Math.min (Math.abs (depth), 1.0) * ILfoModulator.MAX_VOLUME_DEPTH;
        return Math.signum (depth) * (1.0 - Math.pow (10.0, -decibels / 20.0));
    }


    /**
     * Convert a Kontakt intensity to a depth of the model.
     *
     * @param intensity The intensity in the range of [-1..1]
     * @return The depth in the range of [-1..1]
     */
    public double toDepth (final double intensity)
    {
        if (!this.isVolume)
            return Math.clamp (intensity * this.depthPerIntensity, -1.0, 1.0);
        final double amplitude = Math.min (Math.abs (intensity), 1.0);
        if (amplitude == 0)
            return 0;
        final double decibels = amplitude >= 1.0 ? ILfoModulator.MAX_VOLUME_DEPTH : Math.min (-20.0 * Math.log10 (1.0 - amplitude), ILfoModulator.MAX_VOLUME_DEPTH);
        return Math.signum (intensity) * decibels / ILfoModulator.MAX_VOLUME_DEPTH;
    }


    /**
     * Convert the depth of the modulation wheel modulator of the model to the Kontakt intensity of
     * the external modulator. Kontakt adds this intensity to the one of the oscillator.
     *
     * @param depth The depth of the oscillator modulator
     * @param modWheelDepth The depth which the modulation wheel adds
     * @return The intensity of the external modulator
     */
    public double toModWheelIntensity (final double depth, final double modWheelDepth)
    {
        return this.toIntensity (depth + modWheelDepth) - this.toIntensity (depth);
    }


    /**
     * Convert the Kontakt intensity of the external modulator of the modulation wheel to the depth
     * of the modulation wheel modulator of the model.
     *
     * @param intensity The intensity of the oscillator modulator
     * @param modWheelIntensity The intensity which the modulation wheel adds
     * @return The depth of the modulation wheel modulator
     */
    public double toModWheelDepth (final double intensity, final double modWheelIntensity)
    {
        return this.toDepth (intensity + modWheelIntensity) - this.toDepth (intensity);
    }
}
