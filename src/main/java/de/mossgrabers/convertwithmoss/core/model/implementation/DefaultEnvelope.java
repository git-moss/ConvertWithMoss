// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;


/**
 * Interface to an envelope e.g. volume, filter and pitch.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DefaultEnvelope implements IEnvelope
{
    private double delay   = -1;
    private double start   = -1;
    private double attack  = -1;
    private double hold    = -1;
    private double decay   = -1;
    private double sustain = -1;
    private double release = -1;


    /** {@inheritDoc} */
    @Override
    public double getDelay ()
    {
        return this.delay;
    }


    /** {@inheritDoc} */
    @Override
    public void setDelay (final double delay)
    {
        this.delay = delay;
    }


    /** {@inheritDoc} */
    @Override
    public double getStart ()
    {
        return this.start;
    }


    /** {@inheritDoc} */
    @Override
    public void setStart (final double start)
    {
        this.start = start;
    }


    /** {@inheritDoc} */
    @Override
    public double getAttack ()
    {
        return this.attack;
    }


    /** {@inheritDoc} */
    @Override
    public void setAttack (final double attack)
    {
        this.attack = attack;
    }


    /** {@inheritDoc} */
    @Override
    public double getHold ()
    {
        return this.hold;
    }


    /** {@inheritDoc} */
    @Override
    public void setHold (final double hold)
    {
        this.hold = hold;
    }


    /** {@inheritDoc} */
    @Override
    public double getDecay ()
    {
        return this.decay;
    }


    /** {@inheritDoc} */
    @Override
    public void setDecay (final double decay)
    {
        this.decay = decay;
    }


    /** {@inheritDoc} */
    @Override
    public double getSustain ()
    {
        return this.sustain;
    }


    /** {@inheritDoc} */
    @Override
    public void setSustain (final double sustain)
    {
        this.sustain = sustain;
    }


    /** {@inheritDoc} */
    @Override
    public double getRelease ()
    {
        return this.release;
    }


    /** {@inheritDoc} */
    @Override
    public void setRelease (final double release)
    {
        this.release = release;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits (this.attack);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.decay);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.delay);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.hold);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.release);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.start);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.sustain);
        result = prime * result + (int) (temp ^ temp >>> 32);
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
        final DefaultEnvelope other = (DefaultEnvelope) obj;
        if (Double.doubleToLongBits (this.attack) != Double.doubleToLongBits (other.attack))
            return false;
        if (Double.doubleToLongBits (this.decay) != Double.doubleToLongBits (other.decay))
            return false;
        if (Double.doubleToLongBits (this.delay) != Double.doubleToLongBits (other.delay))
            return false;
        if (Double.doubleToLongBits (this.hold) != Double.doubleToLongBits (other.hold))
            return false;
        if (Double.doubleToLongBits (this.release) != Double.doubleToLongBits (other.release))
            return false;
        if (Double.doubleToLongBits (this.start) != Double.doubleToLongBits (other.start))
            return false;
        return Double.doubleToLongBits (this.sustain) == Double.doubleToLongBits (other.sustain);
    }
}
