// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;


/**
 * Default implementation for a filters' settings.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DefaultFilter implements IFilter
{
    protected FilterType type;
    protected int        poles;
    protected double     cutoff;
    protected double     resonance;
    protected int        envelopeDepth;
    protected IEnvelope  envelope = new DefaultEnvelope ();


    /**
     * Constructor.
     *
     * @param type The type of the filter
     * @param poles The number of poles of the filter, if any
     * @param cutoff The cutoff frequency
     * @param resonance The resonance
     */
    public DefaultFilter (final FilterType type, final int poles, final double cutoff, final double resonance)
    {
        this.type = type;
        this.poles = poles;
        this.cutoff = cutoff;
        this.resonance = resonance;
    }


    /** {@inheritDoc} */
    @Override
    public FilterType getType ()
    {
        return this.type;
    }


    /** {@inheritDoc} */
    @Override
    public double getCutoff ()
    {
        return this.cutoff;
    }


    /** {@inheritDoc} */
    @Override
    public double getResonance ()
    {
        return this.resonance;
    }


    /** {@inheritDoc} */
    @Override
    public int getPoles ()
    {
        return this.poles;
    }


    /** {@inheritDoc} */
    @Override
    public IEnvelope getEnvelope ()
    {
        return this.envelope;
    }


    /** {@inheritDoc} */
    @Override
    public void setEnvelopeDepth (final int envelopeDepth)
    {
        this.envelopeDepth = envelopeDepth;
    }


    /** {@inheritDoc} */
    @Override
    public int getEnvelopeDepth ()
    {
        return this.envelopeDepth;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits (this.cutoff);
        result = prime * result + (int) (temp ^ temp >>> 32);
        result = prime * result + (this.envelope == null ? 0 : this.envelope.hashCode ());
        result = prime * result + this.envelopeDepth;
        result = prime * result + this.poles;
        temp = Double.doubleToLongBits (this.resonance);
        result = prime * result + (int) (temp ^ temp >>> 32);
        result = prime * result + (this.type == null ? 0 : this.type.hashCode ());
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final DefaultFilter other = (DefaultFilter) obj;
        if (Double.doubleToLongBits (this.cutoff) != Double.doubleToLongBits (other.cutoff))
            return false;
        if (this.envelope == null)
        {
            if (other.envelope != null)
                return false;
        }
        else if (!this.envelope.equals (other.envelope))
            return false;
        if (this.envelopeDepth != other.envelopeDepth || this.poles != other.poles || Double.doubleToLongBits (this.resonance) != Double.doubleToLongBits (other.resonance))
            return false;
        return this.type == other.type;
    }
}
