// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.util.Objects;

import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IModulator;


/**
 * Default implementation of a low frequency oscillator modulator.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultLfoModulator extends DefaultModulator implements ILfoModulator
{
    private ILfo             source                 = new DefaultLfo ();
    private final IModulator depthModWheelModulator = new DefaultModulator (0);


    /**
     * Constructor.
     *
     * @param depth The modulation depth in the range of [-1,1]. A depth of 0 means that there is no
     *            modulation, which is the default for all formats which do not support it
     */
    public DefaultLfoModulator (final double depth)
    {
        super (depth);
    }


    /** {@inheritDoc} */
    @Override
    public ILfo getSource ()
    {
        if (this.source == null)
            this.source = new DefaultLfo ();
        return this.source;
    }


    /** {@inheritDoc} */
    @Override
    public void setSource (final ILfo source)
    {
        this.source = source;
    }


    /** {@inheritDoc} */
    @Override
    public IModulator getDepthModWheelModulator ()
    {
        return this.depthModWheelModulator;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = super.hashCode ();
        result = prime * result + Objects.hash (this.source, this.depthModWheelModulator);
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
        final DefaultLfoModulator other = (DefaultLfoModulator) obj;
        return Objects.equals (this.source, other.source) && Objects.equals (this.depthModWheelModulator, other.depthModWheelModulator);
    }
}
