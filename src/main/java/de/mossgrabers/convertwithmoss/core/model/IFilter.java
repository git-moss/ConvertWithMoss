// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;


/**
 * Interface to a filters' settings.
 *
 * @author Jürgen Moßgraber
 */
public interface IFilter
{
    /** The maximum filter cutoff frequency. */
    public static final double MAX_FREQUENCY = 20000;
    /** The maximum resonance volume in dB. */
    public static final double MAX_RESONANCE = 40;


    /**
     * Get the type of filter.
     *
     * @return The type
     */
    FilterType getType ();


    /**
     * Get the number of poles, if any. A simple filter (1-pole) has a slope of 6 dB per octave. A
     * two-pole has a slope of 12 dB/octave, and 4-pole 24 dB/octave.
     *
     * @return The number of poles
     */
    int getPoles ();


    /**
     * The cutoff in Hertz.
     *
     * @return The cutoff
     */
    double getCutoff ();


    /**
     * The resonance in the range of [0..1] where 1 represents 40dB.
     *
     * @return The resonance
     */
    double getResonance ();


    /**
     * Get the filter cutoff modulator.
     *
     * @return The modulator
     */
    IModulator getCutoffModulator ();
}
