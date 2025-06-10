// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;


/**
 * Holds the data of an instrument source.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultInstrumentSource extends DefaultSource implements IInstrumentSource
{
    private final IMultisampleSource multisampleSource;
    private int                midiChannel;


    /**
     * Constructor.
     *
     * @param multisampleSource The multi-sample source
     * @param midiChannel The MIDI channel in the range of [0..15], -1 and all other values are
     *            considered omni/all
     */
    public DefaultInstrumentSource (final IMultisampleSource multisampleSource, final int midiChannel)
    {
        this.multisampleSource = multisampleSource;
        this.midiChannel = midiChannel;
    }


    /** {@inheritDoc} */
    @Override
    public IMultisampleSource getMultisampleSource ()
    {
        return this.multisampleSource;
    }


    /** {@inheritDoc} */
    @Override
    public int getMidiChannel ()
    {
        return this.midiChannel;
    }


    /**
     * Set the MIDI channel.
     *
     * @param midiChannel The MIDI channel in the range of [0..15], -1 and all other values are
     *            considered omni/all
     */
    public void setMidiChannel (final int midiChannel)
    {
        this.midiChannel = midiChannel;
    }
}
