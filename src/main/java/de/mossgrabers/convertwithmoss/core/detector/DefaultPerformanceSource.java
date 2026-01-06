// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
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
     * @param instrumentSource The instrument source to add
     */
    public void addInstrument (final IInstrumentSource instrumentSource)
    {
        this.instruments.add (instrumentSource);
    }
}
