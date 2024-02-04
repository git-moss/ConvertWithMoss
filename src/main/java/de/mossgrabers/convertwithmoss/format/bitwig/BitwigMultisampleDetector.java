// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for Bitwig multisample files detector.
 *
 * @author Jürgen Moßgraber
 */
public class BitwigMultisampleDetector extends AbstractDetector<BitwigMultisampleDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public BitwigMultisampleDetector (final INotifier notifier)
    {
        super ("Bitwig Multisample", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new BitwigMultisampleDetectorTask (this.notifier, consumer, folder));
    }
}
