// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;

import javafx.geometry.Orientation;
import javafx.scene.Node;

import java.io.File;
import java.io.IOException;


/**
 * Only stores WAV files. There is no file and all related samples are stored in a separate folder.
 *
 * @author Jürgen Moßgraber
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
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        this.addWavChunkOptions (panel);

        return panel.getPane ();
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
        this.writeSamples (sampleFolder, multisampleSource, this.getChunkSettings ());

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.loadWavChunkSettings (config, "Wav");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.saveWavChunkSettings (config, "Wav");
    }
}