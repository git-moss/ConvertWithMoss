// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Interface to an envelope modulator. The modulation depth ranges map to the 3 envelope types as
 * follow:
 * <ul>
 * <li>Amplitude: 0..1
 * <li>Filter: -12000..12000 cent
 * <li>Pitch: -48000..48000 cent
 * </ul>
 *
 * @author Jürgen Moßgraber
 */
public interface IEnvelopeModulator extends IModulator
{
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
