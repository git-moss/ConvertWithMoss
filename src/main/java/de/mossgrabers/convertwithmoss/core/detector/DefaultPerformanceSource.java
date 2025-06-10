// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;


/**
 * Holds the data of a performance source.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultPerformanceSource extends DefaultSource implements IPerformanceSource
{
    private final List<IInstrumentSource> instruments = new ArrayList<> ();


    /**
     * Constructor.
     */
    public DefaultPerformanceSource ()
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public List<IInstrumentSource> getInstruments ()
    {
        return this.instruments;
    }


    /**
     * Create and add an instrument source to the performance source.
     *
     * @param multisampleSource The multi-sample source
     * @param midiChannel The MIDI channel
     */
    public void addInstrument (final IMultisampleSource multisampleSource, final int midiChannel)
    {
        this.instruments.add (new DefaultInstrumentSource (multisampleSource, midiChannel));
    }
}
