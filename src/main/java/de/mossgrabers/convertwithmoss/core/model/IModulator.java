// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Interface to a modulator. Currently, the modulation source is always an envelope.
 *
 * @author Jürgen Moßgraber
 */
public interface IModulator
{
    /**
     * Get the modulation depth.
     *
     * @return The depth
     */
    double getDepth ();


    /**
     * Set the modulation depth.
     *
     * @param depth The modulation depth
     */
    void setDepth (double depth);


    /**
     * Get the modulation source.
     *
     * @return The modulation source
     */
    IEnvelope getSource ();


    /**
     * Set the modulation source.
     *
     * @param source The modulation source
     */
    void setSource (IEnvelope source);
}
