// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

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
 * Descriptor for DecentSampler dspreset and dslibrary files detector.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerDetector extends AbstractDetectorWithMetadataPane<DecentSamplerDetectorTask>
{
    private static final String LOG_UNSUPPORTED_ATTRIBUTES = "LogUnsupportedAttributes";

    private CheckBox            logUnsupportedAttributes;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DecentSamplerDetector (final INotifier notifier)
    {
        super ("DecentSampler", notifier, "DecentSampler");
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_DS_OPTIONS");

        this.logUnsupportedAttributes = panel.createCheckBox ("@IDS_DS_LOG_UNSUPPORTED_ATTRIBUTES");

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

        config.setBoolean (this.prefix + LOG_UNSUPPORTED_ATTRIBUTES, this.logUnsupportedAttributes.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.metadataPane.loadSettings (config);

        this.logUnsupportedAttributes.setSelected (config.getBoolean (this.prefix + LOG_UNSUPPORTED_ATTRIBUTES, false));
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new DecentSamplerDetectorTask (this.notifier, consumer, folder, this.metadataPane, this.logUnsupportedAttributes.isSelected ()));
    }
}
