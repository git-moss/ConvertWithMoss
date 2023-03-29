// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for MPC keygroup files detector.
 *
 * @author Jürgen Moßgraber
 */
public class MPCKeygroupDetector extends AbstractDetectorWithMetadataPane<MPCKeygroupDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MPCKeygroupDetector (final INotifier notifier)
    {
        super ("Akai MPC Keygroup", notifier, "MPC");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new MPCKeygroupDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
