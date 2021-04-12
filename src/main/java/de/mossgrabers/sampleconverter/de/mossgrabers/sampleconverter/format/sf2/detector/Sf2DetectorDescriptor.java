// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sf2.detector;

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
 * Descriptor for SoundFont 2 files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Sf2DetectorDescriptor extends AbstractDetectorDescriptor<Sf2DetectorTask>
{
    /**
     * Constructor.
     */
    public Sf2DetectorDescriptor ()
    {
        super ("SoundFont 2");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        final Sf2DetectorTask detector = new Sf2DetectorTask ();
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
