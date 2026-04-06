// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.iso;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiDiskImage;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiPartition;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.IAkaiVolume;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000Program;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000ProgramConverter;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000Sample;
import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000Volume;


/**
 * Detects recursively ISO files in folders. Files must end with <i>.ISO</i>.
 *
 * @param <T> The type of the settings
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractIsoDetector<T extends MetadataSettingsUI> extends AbstractDetector<T>
{
    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     * @param userInterface The user interface
     * @param fileEndings The file endings to search for
     */
    public AbstractIsoDetector (final String name, final String prefix, final INotifier notifier, final T userInterface, final String... fileEndings)
    {
        super (name, prefix, notifier, userInterface, fileEndings);
    }


    /** {@inheritDoc} */
    @Override
    protected abstract List<IMultisampleSource> readPresetFile (final File sourceFile);


    /**
     * Process an ISO file which was detected as Akai S1000 format.
     *
     * @param sourceFile The ISO file to process
     * @return The converted multi-samples
     */
    protected List<IMultisampleSource> processAkaiS1000Disk (final File sourceFile)
    {
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        final AkaiS1000ProgramConverter converter = new AkaiS1000ProgramConverter (this.notifier, this.settingsConfiguration);

        try (final AkaiDiskImage disk = new AkaiDiskImage (sourceFile))
        {
            final int partitionCount = disk.getPartitionCount ();

            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, sourceFile.getName ());
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++)
            {
                final AkaiPartition partition = disk.getPartition (partitionIndex);
                this.notifier.log ("IDS_ISO_PROCESSING_PARTITION", partition.getName ());

                for (final IAkaiVolume volume: partition.getVolumes ())
                {
                    if (volume instanceof AkaiS1000Volume s1000Volume)
                    {
                        final List<AkaiS1000Sample> samples = s1000Volume.getSamples ();
                        for (final AkaiS1000Program program: s1000Volume.getPrograms ())
                            multiSampleSources.add (converter.createMultiSample (sourceFile, parts, samples, program, s1000Volume.getName ()));
                    }
                }
            }

            this.notifier.log ("IDS_NOTIFY_LINE_FEED");
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_ISO_COULD_NOT_PROCESS", ex);
        }

        return multiSampleSources;
    }
}
