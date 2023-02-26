// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for Native Instruments Kontakt Instrument (NKI/NKM) files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class NkiDetector extends AbstractDetectorWithMetadataPane<NkiDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public NkiDetector (final INotifier notifier)
    {
        super ("Kontakt NKI/NKM", notifier, "Nki");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new NkiDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
