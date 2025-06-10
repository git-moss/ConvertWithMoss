// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.util.List;


/**
 * A detected source for a performance. A performance contains several instruments.
 *
 * @author Jürgen Moßgraber
 */
public interface IPerformanceSource extends ISource
{
    /**
     * Get the instruments of the performance.
     *
     * @return The instruments
     */
    List<IInstrumentSource> getInstruments ();
}
