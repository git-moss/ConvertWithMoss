// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;


/**
 * Descriptor for SFZ multisample files detector.
 *
 * @author Jürgen Moßgraber
 */
public class SfzDetector extends AbstractDetectorWithMetadataPane<SfzDetectorTask>
{
    private static final String LOG_OPCODES = "LogSupportedOpcodes";

    private CheckBox            logUnsupportedOpcodes;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SfzDetector (final INotifier notifier)
    {
        super ("SFZ", notifier, "Sfz");
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Naming

        panel.createSeparator ("@IDS_SFZ_OPTIONS");

        this.logUnsupportedOpcodes = panel.createCheckBox ("@IDS_SFZ_LOG_UNSUPPORTED_OPCODES");

        ////////////////////////////////////////////////////////////
        // Metadata

        this.metadataPane.addTo (panel);
        this.metadataPane.getSeparator ().getStyleClass ().add ("titled-separator-pane");

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.metadataPane.saveSettings (config);

        config.setBoolean (this.prefix + LOG_OPCODES, this.logUnsupportedOpcodes.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.metadataPane.loadSettings (config);

        this.logUnsupportedOpcodes.setSelected (config.getBoolean (this.prefix + LOG_OPCODES, false));
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new SfzDetectorTask (this.notifier, consumer, folder, this.metadataPane, this.logUnsupportedOpcodes.isSelected ()));
    }
}
