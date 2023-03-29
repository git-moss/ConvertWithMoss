// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.ui.MetadataPane;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;


/**
 * Base class for detector descriptors with an added metadata pane.
 *
 * @param <T> The type of the descriptor task
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractDetectorWithMetadataPane<T extends AbstractDetectorTask> extends AbstractDetector<T>
{
    protected final MetadataPane metadataPane;


    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param notifier The notifier
     * @param prefix The prefix to use for the metadata properties tags
     */
    protected AbstractDetectorWithMetadataPane (final String name, final INotifier notifier, final String prefix)
    {
        super (name, notifier);

        this.metadataPane = new MetadataPane (prefix);
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
