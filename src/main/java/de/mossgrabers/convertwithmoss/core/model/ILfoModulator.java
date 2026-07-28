// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Interface to a low frequency oscillator modulator. The modulation depth of the pitch modulation
 * maps to -4800..4800 cent, which is the same range as the one of the pitch envelope modulator. The
 * modulation depth of the volume modulation maps to -96..96 dB.
 *
 * @author Jürgen Moßgraber
 */
public interface ILfoModulator extends IModulator
{
    /** The maximum depth of a volume modulation in dB. */
    public static final int MAX_VOLUME_DEPTH = 96;


    /**
     * Get the modulation source.
     *
     * @return The modulation source, never null
     */
    ILfo getSource ();


    /**
     * Set the modulation source.
     *
     * @param source The modulation source
     */
    void setSource (ILfo source);
}
