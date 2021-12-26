// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model;

/**
 * Interface to envelopes.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IEnvelopeAccess
{
    /**
     * Get the amplitude envelope.
     *
     * @return The envelope
     */
    IEnvelope getAmplitudeEnvelope ();


    /**
     * Get the pitch envelope.
     *
     * @return The envelope
     */
    IEnvelope getPitchEnvelope ();


    /**
     * Set the modulation depth of the pitch envelope.
     *
     * @param depth The depth in the range of [-12000..12000] cents
     */
    void setPitchEnvelopeDepth (int depth);


    /**
     * Get the modulation depth of the pitch envelope.
     *
     * @return The depth in the range of [-12000..12000] cents
     */
    int getPitchEnvelopeDepth ();
}
