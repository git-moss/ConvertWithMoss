// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.ui.MetadataPane;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for SFZ multisample files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzDetector extends AbstractDetector<SfzDetectorTask>
{
    private MetadataPane metadataPane = new MetadataPane ("Sfz");


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SfzDetector (final INotifier notifier)
    {
        super ("SFZ", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new SfzDetectorTask (this.notifier, consumer, folder, this.metadataPane));
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
