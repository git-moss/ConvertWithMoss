// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.akai;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetector;
import de.mossgrabers.sampleconverter.ui.MetadataPane;
import de.mossgrabers.sampleconverter.ui.tools.BasicConfig;
import de.mossgrabers.sampleconverter.ui.tools.panel.BoxPanel;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for MPC keygroup files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class MPCKeygroupDetector extends AbstractDetector<MPCKeygroupDetectorTask>
{
    private MetadataPane metadataPane = new MetadataPane ("MPC");


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MPCKeygroupDetector (final INotifier notifier)
    {
        super ("MPC Keygroup", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new MPCKeygroupDetectorTask (this.notifier, consumer, folder, this.metadataPane));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.metadataPane.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.metadataPane.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.metadataPane.addTo (panel);

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }
}
