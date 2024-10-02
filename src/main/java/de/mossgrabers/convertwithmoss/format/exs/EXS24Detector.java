// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

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
 * Descriptor for EXS24 files detector.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24Detector extends AbstractDetectorWithMetadataPane<EXS24DetectorTask>
{
    private static final String DIRECTORY_SEARCH = "DirectorySearch";

    private ComboBox<Integer>   directorySearch;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EXS24Detector (final INotifier notifier)
    {
        super ("Logic EXS24", notifier, "EXS24");
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
        this.directorySearch = panel.createComboBox ("@IDS_DIRECTORY_SEARCH", Integer.valueOf (0), Integer.valueOf (1), Integer.valueOf (2), Integer.valueOf (3), Integer.valueOf (4), Integer.valueOf (5), Integer.valueOf (6));
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
        this.startDetection (new EXS24DetectorTask (this.notifier, consumer, folder, this.metadataPane, this.directorySearch));
    }
}
