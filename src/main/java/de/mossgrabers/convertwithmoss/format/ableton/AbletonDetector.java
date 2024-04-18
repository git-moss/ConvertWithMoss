// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;


/**
 * Descriptor for Ableton ADV/ADG files detector.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonDetector extends AbstractDetectorWithMetadataPane<AbletonDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AbletonDetector (final INotifier notifier)
    {
        super ("Ableton Sampler/Simpler", notifier, "Ableton");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new AbletonDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
