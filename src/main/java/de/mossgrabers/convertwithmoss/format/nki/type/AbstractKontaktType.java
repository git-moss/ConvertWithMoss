// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import de.mossgrabers.convertwithmoss.core.INotifier;


/**
 * Abstract base class for handling NKI files in a specific format.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractKontaktType implements IKontaktFormat
{
    protected final INotifier notifier;
    protected boolean         isBigEndian;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    protected AbstractKontaktType (final INotifier notifier)
    {
        this.notifier = notifier;
    }
}
