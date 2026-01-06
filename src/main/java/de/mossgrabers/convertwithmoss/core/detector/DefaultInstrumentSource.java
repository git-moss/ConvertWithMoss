// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * Holds the data of an instrument source.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultInstrumentSource extends DefaultSource implements IInstrumentSource
{
    private IMultisampleSource multisampleSource;
    private int                midiChannel;
    private int                clipKeyLow  = 0;
    private int                clipKeyHigh = 127;


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
    public IMetadata getMetadata ()
    {
        return this.multisampleSource.getMetadata ();
    }


    /** {@inheritDoc} */
    @Override
    public IMultisampleSource getMultisampleSource ()
    {
        return this.multisampleSource;
    }


    /**
     * Set the multi-sample source.
     *
     * @param multisampleSource The source
     */
    public void setMultisampleSource (final IMultisampleSource multisampleSource)
    {
        this.multisampleSource = multisampleSource;
    }


    /** {@inheritDoc} */
    @Override
    public int getMidiChannel ()
    {
        return this.midiChannel;
    }


    /** {@inheritDoc} */
    @Override
    public void setMidiChannel (final int midiChannel)
    {
        this.midiChannel = midiChannel;
    }


    /** {@inheritDoc} */
    @Override
    public int getClipKeyLow ()
    {
        return this.clipKeyLow;
    }


    /** {@inheritDoc} */
    @Override
    public void setClipKeyLow (final int clipKeyLow)
    {
        this.clipKeyLow = clipKeyLow;
    }


    /** {@inheritDoc} */
    @Override
    public int getClipKeyHigh ()
    {
        return this.clipKeyHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void setClipKeyHigh (int clipKeyHigh)
    {
        this.clipKeyHigh = clipKeyHigh;
    }


    /** {@inheritDoc} */
    @Override
    public void clipKeyRange ()
    {
        // Nothing to do?
        if (this.clipKeyLow == 0 && this.clipKeyHigh == 127)
            return;

        for (final IGroup group: this.multisampleSource.getGroups ())
        {
            final List<ISampleZone> filteredZones = new ArrayList<> ();
            for (final ISampleZone zone: group.getSampleZones ())
            {
                int keyLow = zone.getKeyLow ();
                int keyHigh = zone.getKeyHigh ();
                // Fully outside -> remove
                if (keyLow > this.clipKeyHigh || keyHigh < this.clipKeyLow)
                    continue;

                // Clip lower and upper range
                if (keyLow < this.clipKeyLow)
                    zone.setKeyLow (this.clipKeyLow);
                if (keyHigh > this.clipKeyHigh)
                    zone.setKeyHigh (this.clipKeyHigh);
                filteredZones.add (zone);
            }
            group.setSampleZones (filteredZones);
        }
    }
}
