// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.iso;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.akai.s1000s3000.AkaiDiskImage;
import de.mossgrabers.convertwithmoss.format.akai.s1000s3000.AkaiPartition;
import de.mossgrabers.convertwithmoss.format.akai.s1000s3000.AkaiProgram;
import de.mossgrabers.convertwithmoss.format.akai.s1000s3000.AkaiProgramConverter;
import de.mossgrabers.convertwithmoss.format.akai.s1000s3000.AkaiSample;
import de.mossgrabers.convertwithmoss.format.akai.s1000s3000.AkaiVolume;


/**
 * Detects recursively ISO files in folders. Files must end with <i>.ISO</i>.
 *
 * @author Jürgen Moßgraber
 */
public class IsoDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public IsoDetector (final INotifier notifier)
    {
        super ("ISO file", "ISO", notifier, new MetadataSettingsUI ("ISO"), ".iso");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        final IsoFormat isoFormat = identifyIso (sourceFile);
        switch (isoFormat)
        {
            case AKAI_S1000_S1100:
            case AKAI_S3000:
                this.notifier.log ("IDS_ISO_PROCESSING_FORMAT", IsoFormat.getName (isoFormat));
                return this.processAkaiS1000OrS3000 (sourceFile, isoFormat == IsoFormat.AKAI_S3000);

            case ROLAND_S550_W30_DJ70:
            case ROLAND_S7XX:
            case UNKNOWN:
            default:
                this.notifier.logError ("IDS_ISO_UNSUPPORTED_FORMAT", IsoFormat.getName (isoFormat));
                return Collections.emptyList ();
        }
    }


    /**
     * Process an ISO file which was detected as Akai S1000 format.
     * 
     * @param sourceFile The ISO file to process
     * @param isS3000 True if it is a S3000 series image otherwise S1000 series
     * @return The converted multi-samples
     */
    private List<IMultisampleSource> processAkaiS1000OrS3000 (final File sourceFile, final boolean isS3000)
    {
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        final AkaiProgramConverter converter = new AkaiProgramConverter (this.notifier, this.settingsConfiguration);

        try (final AkaiDiskImage disk = new AkaiDiskImage (sourceFile, isS3000))
        {
            final int partitionCount = disk.getPartitionCount ();

            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, sourceFile.getName ());
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++)
            {
                final AkaiPartition partition = disk.getPartition (partitionIndex);
                this.notifier.log ("IDS_ISO_PROCESSING_PARTITION", partition.getName ());

                for (final AkaiVolume volume: partition.getVolumes ())
                {
                    final List<AkaiSample> samples = volume.getSamples ();
                    for (final AkaiProgram program: volume.getPrograms ())
                        multiSampleSources.add (converter.createMultiSample (sourceFile, parts, samples, program, volume.getName ()));
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


    private static IsoFormat identifyIso (final File sourceFile)
    {
        try (final FileInputStream in = new FileInputStream (sourceFile))
        {
            return IsoFormatIdentifier.identifyIso (in.readNBytes (IsoFormatIdentifier.MINIMUM_NUMBER_OF_REQUIRED_BYTES));
        }
        catch (final IOException ex)
        {
            return IsoFormat.UNKNOWN;
        }
    }
}
