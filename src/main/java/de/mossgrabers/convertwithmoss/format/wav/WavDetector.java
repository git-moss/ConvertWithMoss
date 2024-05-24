// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractSampleFileDetector;


/**
 * Descriptor for WAV files detector.
 *
 * @author Jürgen Moßgraber
 */
public class WavDetector extends AbstractSampleFileDetector<WavDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WavDetector (final INotifier notifier)
    {
        super ("WAV", notifier, "Wav");
    }


    /** {@inheritDoc} */
    @Override
    protected WavDetectorTask createDetectorTask (final File folder, final Consumer<IMultisampleSource> consumer, final boolean isAscending, final String [] groupPatterns, final String [] monoSplitPatterns, final String [] postfixTexts, final int crossfadeNotes, final int crossfadeVelocities)
    {
        return new WavDetectorTask (this.notifier, consumer, folder, groupPatterns, isAscending, monoSplitPatterns, postfixTexts, crossfadeNotes, crossfadeVelocities, this.metadataPane);
    }
}
