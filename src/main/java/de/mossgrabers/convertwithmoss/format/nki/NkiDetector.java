// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;


/**
 * Descriptor for Native Instruments Kontakt Instrument (NKI/NKM) files detector.
 *
 * @author Jürgen Moßgraber
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
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> multisampleSourceConsumer)
    {
        this.detect (folder, multisampleSourceConsumer, null, false);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> multisampleSourceConsumer, final Consumer<IPerformanceSource> performanceSourceConsumer, final boolean detectPerformances)
    {
        this.startDetection (new NkiDetectorTask (this.notifier, multisampleSourceConsumer, performanceSourceConsumer, folder, this.metadataPane, detectPerformances));
    }
}
