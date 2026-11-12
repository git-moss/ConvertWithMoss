// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Interface to a low frequency oscillator modulator. The modulation depth of the pitch modulation
 * and of the filter cutoff modulation maps to -{@link IEnvelope#MAX_ENVELOPE_DEPTH}..
 * {@link IEnvelope#MAX_ENVELOPE_DEPTH} cent, which is the same range as the one of the pitch and
 * cutoff envelope modulators. The modulation depth of the volume modulation maps to -96..96 dB.
 * The depth can additionally be controlled by the modulation wheel, see
 * {@link #getDepthModWheelModulator()}.
 *
 * @author Jürgen Moßgraber
 */
public interface ILfoModulator extends IModulator
{
    /** The maximum depth of a volume modulation in dB. */
    int MAX_VOLUME_DEPTH = 96;


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


    /**
     * Get the modulator which controls the depth of the LFO with the modulation wheel. Its depth
     * (-1..1, same units as the LFO depth) is added to the depth of this LFO modulator when the
     * modulation wheel is fully up. With the modulation wheel fully down only the depth of this LFO
     * modulator is applied.
     *
     * @return The modulator, never null
     */
    IModulator getDepthModWheelModulator ();


    /**
     * Check if the LFO has an effect, which is the case if the depth or the modulation wheel depth
     * is not 0.
     *
     * @return True if active
     */
    default boolean isActive ()
    {
        return this.getDepth () != 0 || this.getDepthModWheelModulator ().getDepth () != 0;
    }
}
