// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for DecentSampler dspreset and dslibrary files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DecentSamplerDetector extends AbstractDetectorWithMetadataPane<DecentSamplerDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DecentSamplerDetector (final INotifier notifier)
    {
        super ("DecentSampler", notifier, "DecentSampler");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new DecentSamplerDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
