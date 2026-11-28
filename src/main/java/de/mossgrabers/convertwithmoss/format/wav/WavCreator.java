// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import java.io.File;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;


/**
 * Only stores the sample files of the multi-sample in one of several audio file formats (WAV, AIFF,
 * CAF or FLAC). There is no preset file and all related samples are stored in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class WavCreator extends AbstractWavCreator<WavCreatorUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WavCreator (final INotifier notifier)
    {
        super ("Sample Files", "Wav", notifier, new WavCreatorUI ("Wav"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = SafeFileNames.create (multisampleSource.getName ());
        final String safeSampleFolderName = sampleName + FOLDER_POSTFIX;

        this.notifier.log ("IDS_NOTIFY_STORING", safeSampleFolderName);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource);

        this.progress.notifyDone ();
    }
}
