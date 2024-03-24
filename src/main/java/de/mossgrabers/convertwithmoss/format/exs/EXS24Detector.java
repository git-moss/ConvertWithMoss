// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;


/**
 * Descriptor for EXS24 files detector.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24Detector extends AbstractDetectorWithMetadataPane<EXS24DetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EXS24Detector (final INotifier notifier)
    {
        super ("Logic EXS24", notifier, "EXS24");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new EXS24DetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
