// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.aiff;

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
public class AiffDetector extends AbstractSampleFileDetector<AiffDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AiffDetector (final INotifier notifier)
    {
        super ("AIFF", notifier, "Aiff");
    }


    /** {@inheritDoc} */
    @Override
    protected AiffDetectorTask createDetectorTask (final File folder, final Consumer<IMultisampleSource> consumer, final boolean isAscending, final String [] groupPatterns, final String [] monoSplitPatterns, final String [] postfixTexts, int crossfadeNotes, int crossfadeVelocities)
    {
        return new AiffDetectorTask (this.notifier, consumer, folder, groupPatterns, isAscending, monoSplitPatterns, postfixTexts, crossfadeNotes, crossfadeVelocities, this.metadataPane);
    }
}
