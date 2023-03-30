// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;


/**
 * Abstract base class for handling NKI files in a specific format.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractKontaktType implements IKontaktType
{
    protected final IMetadataConfig metadataConfig;
    protected final INotifier       notifier;


    /**
     * Constructor.
     *
     * @param metadataConfig Default metadata
     * @param notifier Where to report errors
     */
    protected AbstractKontaktType (final IMetadataConfig metadataConfig, final INotifier notifier)
    {
        this.metadataConfig = metadataConfig;
        this.notifier = notifier;
    }
}
