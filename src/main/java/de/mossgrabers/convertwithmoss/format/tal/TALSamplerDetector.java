// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for TAL Sampler files detector.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerDetector extends AbstractDetector<TALSamplerDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TALSamplerDetector (final INotifier notifier)
    {
        super ("TAL Sampler", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new TALSamplerDetectorTask (this.notifier, consumer, folder));
    }
}
