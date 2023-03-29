// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IModulator;


/**
 * Default implementation of a modulator.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultModulator implements IModulator
{
    private double    depth  = -1;
    private IEnvelope source = new DefaultEnvelope ();


    /** {@inheritDoc} */
    @Override
    public double getDepth ()
    {
        return this.depth;
    }


    /** {@inheritDoc} */
    @Override
    public void setDepth (final double depth)
    {
        this.depth = depth;
    }


    /** {@inheritDoc} */
    @Override
    public IEnvelope getSource ()
    {
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
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits (this.depth);
        result = prime * result + (int) (temp ^ temp >>> 32);
        result = prime * result + (this.source == null ? 0 : this.source.hashCode ());
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if ((obj == null) || (this.getClass () != obj.getClass ()))
            return false;
        final DefaultModulator other = (DefaultModulator) obj;
        if (Double.doubleToLongBits (this.depth) != Double.doubleToLongBits (other.depth))
            return false;
        if (this.source == null)
        {
            if (other.source != null)
                return false;
        }
        else if (!this.source.equals (other.source))
            return false;
        return true;
    }
}
