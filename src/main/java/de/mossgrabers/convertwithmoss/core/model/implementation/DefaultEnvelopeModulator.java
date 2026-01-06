// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.util.Objects;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;


/**
 * Default implementation of an envelope modulator.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultEnvelopeModulator extends DefaultModulator implements IEnvelopeModulator
{
    private IEnvelope source = new DefaultEnvelope ();


    /**
     * Constructor.
     *
     * @param depth The modulation depth in the range of [-1,1]. Filter and Amplitude envelope
     *            modulation should be 1, pitch envelope modulation 0
     */
    public DefaultEnvelopeModulator (final double depth)
    {
        super (depth);
    }


    /** {@inheritDoc} */
    @Override
    public IEnvelope getSource ()
    {
        if (this.source == null)
            this.source = new DefaultEnvelope ();
        return this.source;
    }


    /** {@inheritDoc} */
    @Override
    public void setSource (final IEnvelope source)
    {
        this.source = source;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = super.hashCode ();
        result = prime * result + Objects.hash (this.source);
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (!super.equals (obj) || this.getClass () != obj.getClass ())
            return false;
        final DefaultEnvelopeModulator other = (DefaultEnvelopeModulator) obj;
        return Objects.equals (this.source, other.source);
    }
}
