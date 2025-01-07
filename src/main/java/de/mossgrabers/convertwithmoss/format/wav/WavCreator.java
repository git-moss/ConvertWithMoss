// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import java.io.File;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;


/**
 * Only stores WAV files. There is no file and all related samples are stored in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class WavCreator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WavCreator (final INotifier notifier)
    {
        this ("WAV", notifier);
    }


    /**
     * Constructor.
     *
     * @param name The name of the creator.
     * @param notifier The notifier
     */
    protected WavCreator (final String name, final INotifier notifier)
    {
        super (name, notifier);

        this.configureWavChunkUpdates (true, true, true, true);
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