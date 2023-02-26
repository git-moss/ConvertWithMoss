// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for SoundFont 2 files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Sf2Detector extends AbstractDetector<Sf2DetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Sf2Detector (final INotifier notifier)
    {
        super ("SoundFont 2", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new Sf2DetectorTask (this.notifier, consumer, folder));
    }
}
