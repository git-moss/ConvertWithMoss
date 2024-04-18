// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
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
     * Get the modulation depth. The range maps to the 3 envelope types as follow:
     * <ul>
     * <li>Amplitude: 0..1
     * <li>Filter: -12000..12000 cent
     * <li>Pitch: -48000..48000 cent
     * </ul>
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
