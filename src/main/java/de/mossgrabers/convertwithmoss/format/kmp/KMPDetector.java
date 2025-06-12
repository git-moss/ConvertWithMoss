// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

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
 * Descriptor for Korg Multisample (KMP) files detector.
 *
 * @author Jürgen Moßgraber
 */
public class KMPDetector extends AbstractDetectorWithMetadataPane<KMPDetectorTask>
{
    private static final String USE_KSC = "UseKsc";

    private CheckBox            useKscFilesCheckBox;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KMPDetector (final INotifier notifier)
    {
        super ("Korg KSC/KMP/KSF", notifier, "KMP");
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_KMP_OPTIONS");

        this.useKscFilesCheckBox = panel.createCheckBox ("@IDS_KMP_USE_KSC");

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
        super.saveSettings (config);

        config.setBoolean (this.prefix + USE_KSC, this.useKscFilesCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        this.useKscFilesCheckBox.setSelected (config.getBoolean (this.prefix + USE_KSC, false));
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new KMPDetectorTask (this.notifier, consumer, folder, this.metadataPane, this.useKscFilesCheckBox.isSelected ()));
    }
}
