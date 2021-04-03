// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sfz.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetectorDescriptor;
import de.mossgrabers.sampleconverter.ui.tools.BasicConfig;
import de.mossgrabers.sampleconverter.ui.tools.panel.BoxPanel;

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
public class SfzDetectorDescriptor extends AbstractDetectorDescriptor<SfzDetectorTask>
{
    /**
     * Constructor.
     */
    public SfzDetectorDescriptor ()
    {
        super ("SFZ");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        final SfzDetectorTask detector = new SfzDetectorTask ();
        detector.configure (consumer, folder);
        this.startDetection (detector);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        // Add parameters here, if necessary

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }
}
