// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;


/**
 * Descriptor for TAL Sampler files detector.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerDetector extends AbstractDetectorWithMetadataPane<TALSamplerDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TALSamplerDetector (final INotifier notifier)
    {
        super ("TAL Sampler", notifier, "TALSampler");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new TALSamplerDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
