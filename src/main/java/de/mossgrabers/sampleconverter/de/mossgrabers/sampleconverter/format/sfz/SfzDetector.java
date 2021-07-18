// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sfz;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetector;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for SFZ multisample files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzDetector extends AbstractDetector<SfzDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SfzDetector (final INotifier notifier)
    {
        super ("SFZ", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new SfzDetectorTask (this.notifier, consumer, folder));
    }
}
