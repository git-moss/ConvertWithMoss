// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for SFZ multisample files detector.
 *
 * @author Jürgen Moßgraber
 */
public class SfzDetector extends AbstractDetectorWithMetadataPane<SfzDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SfzDetector (final INotifier notifier)
    {
        super ("SFZ", notifier, "Sfz");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new SfzDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
