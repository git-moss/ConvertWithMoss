// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;


/**
 * Descriptor for Yamaha YSFC files detector used by many Yamaha workstations like Motif, Montage,
 * MOXF and MODX.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcDetector extends AbstractDetectorWithMetadataPane<YamahaYsfcDetectorTask>
{
    private static final String YSFC_SOURCE_TYPE = "YsfcSourceType";

    private ToggleGroup         sourceTypeGroup;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public YamahaYsfcDetector (final INotifier notifier)
    {
        super ("Yamaha YSFC", notifier, "YamahaYsfc");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new YamahaYsfcDetectorTask (this.notifier, consumer, folder, this.metadataPane, this.isSourceTypePerformance ()));
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_YSFC_SOURCE_TYPE");

        this.sourceTypeGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_YSFC_SOURCE_TYPE_WAVEFORMS");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_YSFC_SOURCE_TYPE"));
        order1.setToggleGroup (this.sourceTypeGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_YSFC_SOURCE_TYPE_PERFORMANCES");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_YSFC_SOURCE_TYPE"));
        order2.setToggleGroup (this.sourceTypeGroup);

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
    public void loadSettings (final BasicConfig config)
    {
        this.sourceTypeGroup.selectToggle (this.sourceTypeGroup.getToggles ().get (config.getBoolean (YSFC_SOURCE_TYPE, true) ? 1 : 0));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (YSFC_SOURCE_TYPE, this.isSourceTypePerformance ());

        super.saveSettings (config);
    }


    private boolean isSourceTypePerformance ()
    {
        return this.sourceTypeGroup.getToggles ().get (1).isSelected ();
    }
}
