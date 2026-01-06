// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;


/**
 * Default implementation for a filters' settings.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultFilter implements IFilter
{
    protected FilterType         type;
    protected int                poles;
    protected double             cutoff;
    protected double             resonance;
    protected int                envelopeDepth;
    protected IEnvelopeModulator cutoffEnvelopeModulator = new DefaultEnvelopeModulator (0);
    protected IModulator         cutoffVelocityModulator = new DefaultModulator (0);


    /**
     * Constructor.
     *
     * @param type The type of the filter
     * @param poles The number of poles of the filter, if any
     * @param cutoff The cutoff frequency in Hertz
     * @param resonance The resonance in the range of [0..1] where 1 represents 40dB.
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
    public IModulator getCutoffVelocityModulator ()
    {
        return this.cutoffVelocityModulator;
    }


    /** {@inheritDoc} */
    @Override
    public IEnvelopeModulator getCutoffEnvelopeModulator ()
    {
        return this.cutoffEnvelopeModulator;
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
        result = prime * result + (this.cutoffVelocityModulator == null ? 0 : this.cutoffVelocityModulator.hashCode ());
        result = prime * result + (this.cutoffEnvelopeModulator == null ? 0 : this.cutoffEnvelopeModulator.hashCode ());
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
        if (this.cutoffVelocityModulator == null)
        {
            if (other.cutoffVelocityModulator != null)
                return false;
        }
        else if (!this.cutoffVelocityModulator.equals (other.cutoffVelocityModulator))
            return false;
        if (this.cutoffVelocityModulator == null)
        {
            if (other.cutoffVelocityModulator != null)
                return false;
        }
        else if (!this.cutoffEnvelopeModulator.equals (other.cutoffEnvelopeModulator))
            return false;
        if (this.envelopeDepth != other.envelopeDepth || this.poles != other.poles || Double.doubleToLongBits (this.resonance) != Double.doubleToLongBits (other.resonance))
            return false;
        return this.type == other.type;
    }
}
