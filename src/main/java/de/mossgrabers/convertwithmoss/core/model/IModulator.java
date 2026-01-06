// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Interface to a modulator.
 *
 * @author Jürgen Moßgraber
 */
public interface IModulator
{
    /**
     * Get the modulation depth.
     *
     * @return The depth in the range of [-1..1]
     */
    double getDepth ();


    /**
     * Set the modulation depth.
     *
     * @param depth The modulation depth in the range of [-1..1]
     */
    void setDepth (double depth);
}
