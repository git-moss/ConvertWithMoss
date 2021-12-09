// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model;

/**
 * Access to envelopes.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class EnvelopeAccess implements IEnvelopeAccess
{
    private final IEnvelope amplitudeEnvelope = new Envelope ();


    /** {@inheritDoc} */
    @Override
    public IEnvelope getAmplitudeEnvelope ()
    {
        return this.amplitudeEnvelope;
    }
}
