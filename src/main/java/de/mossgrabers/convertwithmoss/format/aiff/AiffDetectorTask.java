// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.aiff;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractSampleFileDetectorTask;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.aiff.AiffFileSampleData;
import de.mossgrabers.convertwithmoss.file.aiff.AiffInstrumentChunk;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;


/**
 * Detects recursively AIFF files in folders, which can be the source for a multi-sample. Files must
 * end with <i>.aiff</i> or <i>.aif</i>. All sample files in a folder are considered to belong to
 * one multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public class AiffDetectorTask extends AbstractSampleFileDetectorTask
{
    /**
     * Configure the detector.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param groupPatterns Detection patterns for groups
     * @param isAscending Are groups ordered ascending?
     * @param monoSplitPatterns Detection pattern for mono splits (to be combined to stereo files)
     * @param postfixTexts Post-fix text to remove
     * @param crossfadeNotes Number of notes to cross-fade
     * @param crossfadeVelocities The number of velocity steps to cross-fade ranges
     * @param metadata Additional metadata configuration parameters
     */
    public AiffDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final String [] groupPatterns, final boolean isAscending, final String [] monoSplitPatterns, final String [] postfixTexts, final int crossfadeNotes, final int crossfadeVelocities, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, groupPatterns, isAscending, monoSplitPatterns, postfixTexts, crossfadeNotes, crossfadeVelocities, metadata, ".aif", ".aiff");
    }


    /** {@inheritDoc} */
    @Override
    protected void fillInstrumentData (final ISampleZone zone, final IFileBasedSampleData sampleData)
    {
        if (sampleData instanceof final AiffFileSampleData sd)
        {
            final AiffInstrumentChunk instrumentChunk = sd.getAiffFile ().getInstrumentChunk ();
            zone.setKeyRoot (instrumentChunk.getBaseNote ());
            zone.setKeyLow (instrumentChunk.getLowNote ());
            zone.setKeyHigh (instrumentChunk.getHighNote ());
            zone.setVelocityLow (instrumentChunk.getLowVelocity ());
            zone.setVelocityHigh (instrumentChunk.getHighVelocity ());
            zone.setGain (instrumentChunk.getGain ());
            zone.setTune (instrumentChunk.getDetune () / 100.0);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected boolean hasInstrumentData (final List<IFileBasedSampleData> sampleData)
    {
        for (final IFileBasedSampleData sampleFileData: sampleData)
            if (sampleFileData instanceof final AiffFileSampleData sd && sd.getAiffFile ().getInstrumentChunk () == null)
                return false;
        return true;
    }
}
