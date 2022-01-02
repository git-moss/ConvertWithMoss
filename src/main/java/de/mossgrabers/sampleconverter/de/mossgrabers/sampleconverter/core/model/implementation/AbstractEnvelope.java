// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model.implementation;

import de.mossgrabers.sampleconverter.core.model.IEnvelope;
import de.mossgrabers.sampleconverter.core.model.IEnvelopeAccess;


/**
 * Access to envelopes.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractEnvelope implements IEnvelopeAccess
{
    private final IEnvelope amplitudeEnvelope  = new DefaultEnvelope ();
    private final IEnvelope pitchEnvelope      = new DefaultEnvelope ();
    private int             pitchEnvelopeDepth = 0;


    /** {@inheritDoc} */
    @Override
    public IEnvelope getAmplitudeEnvelope ()
    {
        return this.amplitudeEnvelope;
    }


    /** {@inheritDoc} */
    @Override
    public IEnvelope getPitchEnvelope ()
    {
        return this.pitchEnvelope;
    }


    /** {@inheritDoc} */
    @Override
    public void setPitchEnvelopeDepth (final int depth)
    {
        this.pitchEnvelopeDepth = depth;
    }


    /** {@inheritDoc} */
    @Override
    public int getPitchEnvelopeDepth ()
    {
        return this.pitchEnvelopeDepth;
    }
}
