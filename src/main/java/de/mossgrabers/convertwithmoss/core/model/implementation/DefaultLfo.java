// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.util.Objects;

import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;


/**
 * Default implementation of a low frequency oscillator. All values are unset by default, which
 * means that a format which does not fill them writes nothing.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultLfo implements ILfo
{
    private LfoWaveform waveform   = LfoWaveform.TRIANGLE;
    private double      rate       = -1;
    private double      delay      = -1;
    private double      fadeIn     = -1;
    private double      startPhase = -1;
    private boolean     isKeySync  = false;


    /** {@inheritDoc} */
    @Override
    public LfoWaveform getWaveform ()
    {
        return this.waveform;
    }


    /** {@inheritDoc} */
    @Override
    public void setWaveform (final LfoWaveform waveform)
    {
        this.waveform = waveform;
    }


    /** {@inheritDoc} */
    @Override
    public double getRate ()
    {
        return this.rate;
    }


    /** {@inheritDoc} */
    @Override
    public void setRate (final double rate)
    {
        this.rate = rate;
    }


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
    public double getFadeIn ()
    {
        return this.fadeIn;
    }


    /** {@inheritDoc} */
    @Override
    public void setFadeIn (final double fadeIn)
    {
        this.fadeIn = fadeIn;
    }


    /** {@inheritDoc} */
    @Override
    public double getStartPhase ()
    {
        return this.startPhase;
    }


    /** {@inheritDoc} */
    @Override
    public void setStartPhase (final double startPhase)
    {
        this.startPhase = startPhase;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isKeySync ()
    {
        return this.isKeySync;
    }


    /** {@inheritDoc} */
    @Override
    public void setKeySync (final boolean isKeySync)
    {
        this.isKeySync = isKeySync;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isSet ()
    {
        return this.rate >= 0;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return Objects.hash (Double.valueOf (this.delay), Double.valueOf (this.fadeIn), Boolean.valueOf (this.isKeySync), Double.valueOf (this.rate), Double.valueOf (this.startPhase), this.waveform);
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final DefaultLfo other = (DefaultLfo) obj;
        return Double.doubleToLongBits (this.delay) == Double.doubleToLongBits (other.delay) && Double.doubleToLongBits (this.fadeIn) == Double.doubleToLongBits (other.fadeIn) && this.isKeySync == other.isKeySync && Double.doubleToLongBits (this.rate) == Double.doubleToLongBits (other.rate) && Double.doubleToLongBits (this.startPhase) == Double.doubleToLongBits (other.startPhase) && this.waveform == other.waveform;
    }
}
