// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.bitwig;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetector;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for Bitwig multisample files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
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
