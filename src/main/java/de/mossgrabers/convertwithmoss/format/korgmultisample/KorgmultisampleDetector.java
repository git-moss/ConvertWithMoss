// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.korgmultisample;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for Korgmultisample (used by the Wavestate / Modwave) files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class KorgmultisampleDetector extends AbstractDetector<KorgmultisampleDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KorgmultisampleDetector (final INotifier notifier)
    {
        super ("Korg Wavestate/Modwave", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new KorgmultisampleDetectorTask (this.notifier, consumer, folder));
    }
}
