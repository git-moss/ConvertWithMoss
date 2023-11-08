// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;


/**
 * The loop of a sample.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultSampleLoop implements ISampleLoop
{
    private LoopType loopType  = LoopType.FORWARD;
    private int      loopStart = -1;
    private int      loopEnd   = -1;
    private double   crossfade = 0;


    /** {@inheritDoc} */
    @Override
    public LoopType getType ()
    {
        return this.loopType;
    }


    /** {@inheritDoc} */
    @Override
    public void setType (final LoopType type)
    {
        this.loopType = type;
    }


    /** {@inheritDoc} */
    @Override
    public int getStart ()
    {
        return this.loopStart;
    }


    /** {@inheritDoc} */
    @Override
    public void setStart (final int loopStart)
    {
        this.loopStart = loopStart;
    }


    /** {@inheritDoc} */
    @Override
    public int getEnd ()
    {
        return this.loopEnd;
    }


    /** {@inheritDoc} */
    @Override
    public void setEnd (final int loopEnd)
    {
        this.loopEnd = loopEnd;
    }


    /** {@inheritDoc} */
    @Override
    public double getCrossfade ()
    {
        return this.crossfade;
    }


    /** {@inheritDoc} */
    @Override
    public void setCrossfade (final double crossfade)
    {
        this.crossfade = crossfade;
    }


    /** {@inheritDoc} */
    @Override
    public Object clone () throws CloneNotSupportedException
    {
        return super.clone ();
    }
}
