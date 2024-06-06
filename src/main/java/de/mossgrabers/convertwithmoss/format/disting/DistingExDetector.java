// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.disting;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;


/**
 * Descriptor for DistingEX files detector.
 *
 * @author Jürgen Moßgraber
 */
public class DistingExDetector extends AbstractDetectorWithMetadataPane<DistingExDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DistingExDetector (final INotifier notifier)
    {
        super ("Expert Sleepers Disting EX", notifier, "dex");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new DistingExDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
