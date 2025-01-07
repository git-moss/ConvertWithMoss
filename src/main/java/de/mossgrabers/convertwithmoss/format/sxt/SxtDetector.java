// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sxt;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;


/**
 * Descriptor for Propellerhead Software Reason NN-XT (*.sxt) files detector.
 *
 * @author Jürgen Moßgraber
 */
public class SxtDetector extends AbstractDetectorWithMetadataPane<SxtDetectorTask>
{
    private static final String DIRECTORY_SEARCH = "DirectorySearch";

    private ComboBox<Integer>   directorySearch;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SxtDetector (final INotifier notifier)
    {
        super ("Reason NN-XT", notifier, "SXT");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        super.saveSettings (config);

        config.setProperty (this.prefix + DIRECTORY_SEARCH, this.directorySearch.getSelectionModel ().getSelectedItem ().toString ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        final String value = config.getProperty (this.prefix + DIRECTORY_SEARCH, "1");
        this.directorySearch.getSelectionModel ().select (Integer.decode (value));
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_SAMPLE_FILE_SEARCH");
        this.directorySearch = panel.createComboBox ("@IDS_DIRECTORY_SEARCH", Integer.valueOf (0), Integer.valueOf (1), Integer.valueOf (2), Integer.valueOf (3));
        this.directorySearch.getSelectionModel ().select (Integer.valueOf (1));

        this.metadataPane.addTo (panel);

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new SxtDetectorTask (this.notifier, consumer, folder, this.metadataPane, this.directorySearch));
    }
}
