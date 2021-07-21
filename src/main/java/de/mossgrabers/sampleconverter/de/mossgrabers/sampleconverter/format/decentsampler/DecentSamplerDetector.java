// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.decentsampler;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetector;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for DecentSampler dspreset and dslibrary files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DecentSamplerDetector extends AbstractDetector<DecentSamplerDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DecentSamplerDetector (final INotifier notifier)
    {
        super ("DecentSampler", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new DecentSamplerDetectorTask (this.notifier, consumer, folder));
    }
}
