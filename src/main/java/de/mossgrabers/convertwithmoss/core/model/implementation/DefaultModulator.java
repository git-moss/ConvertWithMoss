// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.IModulator;


/**
 * Default implementation of a modulator.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultModulator implements IModulator
{
    protected double depth = 0;


    /**
     * Constructor.
     *
     * @param depth The modulation depth in the range of [-1,1]
     */
    public DefaultModulator (final double depth)
    {
        this.depth = Math.clamp (depth, -1, 1);
    }


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
        this.depth = Math.clamp (depth, -1.0, 1.0);
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
        final DefaultModulator other = (DefaultModulator) obj;
        return Double.doubleToLongBits (this.depth) == Double.doubleToLongBits (other.depth);
    }
}
