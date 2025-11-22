// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2.Maschine2Format;


/**
 * Creator for Native Instruments Maschine files.
 *
 * @author Jürgen Moßgraber
 */
public class MaschineCreator extends AbstractWavCreator<MaschineCreatorUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MaschineCreator (final INotifier notifier)
    {
        super ("Maschine (mxsnd)", "Maschine", notifier, new MaschineCreatorUI ("Maschine"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        if (multisampleSource.getNonEmptyGroups (false).isEmpty ())
        {
            this.notifier.logError ("IDS_ERR_NO_GROUPS_IN_SOURCE");
            return;
        }

        final int maschineFormat = this.settingsConfiguration.getDestinationVersion ();
        if (maschineFormat > 1)
        {
            final IMaschineFormat maschineType = new Maschine2Format (this.notifier);

            final String multisampleName = createSafeFilename (multisampleSource.getName ());
            final File multiFile = this.createUniqueFilename (destinationFolder, multisampleName, "mxsnd");
            this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

            // First, store all samples
            final String safeSampleFolderName = multisampleName + FOLDER_POSTFIX;
            final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
            safeCreateDirectory (sampleFolder);
            final List<File> sampleFiles = this.writeSamples (sampleFolder, multisampleSource);

            try (final FileOutputStream out = new FileOutputStream (multiFile))
            {
                maschineType.writeSound (out, safeSampleFolderName, multisampleSource, calculateSampleSize (sampleFiles), maschineFormat);
            }
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Calculates the size of all samples.
     *
     * @param sampleFiles The sample files (must be all WAV files).
     * @return The summed size
     * @throws IOException Could not read a file
     */
    private static int calculateSampleSize (final List<File> sampleFiles) throws IOException
    {
        int size = 0;
        try
        {
            for (final File file: sampleFiles)
                size += new WaveFile (file, true).getDataChunk ().getData ().length;
        }
        catch (final ParseException ex)
        {
            throw new IOException (ex);
        }
        return size;
    }
}