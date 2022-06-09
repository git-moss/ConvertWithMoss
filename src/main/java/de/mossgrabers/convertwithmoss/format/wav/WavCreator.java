// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;

import java.io.File;
import java.io.IOException;


/**
 * Only stores WAV files. There is no file and all related samples are stored in a separate folder.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class WavCreator extends AbstractCreator
{
    private static final String FOLDER_POSTFIX = " Samples";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WavCreator (final INotifier notifier)
    {
        super ("WAV", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final String safeSampleFolderName = sampleName + FOLDER_POSTFIX;

        this.notifier.log ("IDS_NOTIFY_STORING", safeSampleFolderName);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }
}