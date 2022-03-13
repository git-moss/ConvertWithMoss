// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for Korg Multisample (KMP) files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class KMPDetector extends AbstractDetectorWithMetadataPane<KMPDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KMPDetector (final INotifier notifier)
    {
        super ("KMP/KSF", notifier, "KMP");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new KMPDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }
}
