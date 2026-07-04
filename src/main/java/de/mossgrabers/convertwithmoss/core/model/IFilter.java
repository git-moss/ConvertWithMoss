// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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
     * Get the cutoff frequency.
     *
     * @return The cutoff in Hertz
     */
    double getCutoff ();


    /**
     * The resonance in the range of [0..1] where 1 represents 40dB.
     *
     * @return The resonance
     */
    double getResonance ();


    /**
     * Get the velocity modulator for filter cutoff.
     *
     * @return The modulator
     */
    IModulator getCutoffVelocityModulator ();


    /**
     * Get the envelope modulator for filter cutoff.
     *
     * @return The modulator
     */
    IEnvelopeModulator getCutoffEnvelopeModulator ();


    /**
     * Get the keyboard tracking amount applied to the filter cutoff. A value of 0 means no tracking,
     * 1 means the cutoff follows the played note one-to-one (one semitone per semitone, +100%) and
     * -1 means inverse tracking (-100%).
     *
     * @return The key-tracking amount in the range of [-1..1]
     */
    double getCutoffKeyTracking ();


    /**
     * Set the keyboard tracking amount applied to the filter cutoff.
     *
     * @param keyTracking The key-tracking amount in the range of [-1..1] (0 = no tracking)
     */
    void setCutoffKeyTracking (double keyTracking);
}
