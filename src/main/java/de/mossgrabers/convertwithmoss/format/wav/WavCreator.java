// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import java.io.File;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;


/**
 * Only stores WAV files. There is no file and all related samples are stored in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class WavCreator extends AbstractWavCreator<WavChunkSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WavCreator (final INotifier notifier)
    {
        super ("Sample Files (WAV)", "Wav", notifier, new WavChunkSettingsUI ("Wav", true, true, true, true));
    }


    /**
     * Constructor.
     *
     * @param name The name of the creator.
     * @param prefix The prefix to use
     * @param notifier The notifier
     * @param settingsConfiguration The configuration of the settings
     */
    protected WavCreator (final String name, final String prefix, final INotifier notifier, final WavChunkSettingsUI settingsConfiguration)
    {
        super (name, prefix, notifier, settingsConfiguration);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
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