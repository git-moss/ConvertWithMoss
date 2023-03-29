// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
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
    public static final double MAX_FREQUENCY      = 20000;
    /** The maximum filter envelope depth. */
    public static final int    MAX_ENVELOPE_DEPTH = 12000;


    /**
     * Get the type of filter.
     *
     * @return The type
     */
    FilterType getType ();


    /**
     * Get the number of poles, if any.
     *
     * @return The number of poles
     */
    int getPoles ();


    /**
     * The cutoff in hertz.
     *
     * @return The cutoff
     */
    double getCutoff ();


    /**
     * The resonance in dB.
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
