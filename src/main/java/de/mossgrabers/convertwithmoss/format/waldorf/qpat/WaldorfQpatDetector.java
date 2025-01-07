// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;


/**
 * Descriptor for Waldorf QPAT files detector (Quantum/Iridium).
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatDetector extends AbstractDetector<WaldorfQpatDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WaldorfQpatDetector (final INotifier notifier)
    {
        super ("Waldorf Quantum/Iridium", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new WaldorfQpatDetectorTask (this.notifier, consumer, folder));
    }
}
