// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for 1010music preset files detector.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010Detector extends AbstractDetectorWithMetadataPane<Music1010DetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Music1010Detector (final INotifier notifier)
    {
        super ("1010music", notifier, "1010music");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new Music1010DetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
