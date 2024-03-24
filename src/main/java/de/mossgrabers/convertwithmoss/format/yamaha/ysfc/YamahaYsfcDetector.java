// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;


/**
 * Descriptor for Yamaha YSFC files detector used by many Yamaha workstations like Motif, Montage,
 * MOXF and MODX.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcDetector extends AbstractDetectorWithMetadataPane<YamahaYsfcDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public YamahaYsfcDetector (final INotifier notifier)
    {
        super ("Yamaha YSFC", notifier, "YamahaYsfc");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new YamahaYsfcDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
