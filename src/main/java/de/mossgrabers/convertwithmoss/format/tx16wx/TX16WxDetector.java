// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tx16wx;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;


/**
 * Descriptor for TX16Wx txprog files detector.
 *
 * @author Jürgen Moßgraber
 */
public class TX16WxDetector extends AbstractDetectorWithMetadataPane<TX16WxDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TX16WxDetector (final INotifier notifier)
    {
        super ("TX16Wx", notifier, "TX16Wx");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new TX16WxDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
