// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;


/**
 * Descriptor for SoundFont 2 files detector.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Detector extends AbstractDetectorWithMetadataPane<Sf2DetectorTask>
{
    private static final String LOG_UNSUPPORTED_ATTRIBUTES = "LogUnsupportedAttributes";
    private static final String ADD_FILE_NAME_TAG          = "AddFileName";
    private static final String ADD_PROGRAM_NUMBER_TAG     = "AddProgramNumber";

    private CheckBox            logUnsupportedAttributes;
    private CheckBox            addFileName;
    private CheckBox            addProgramNumber;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Sf2Detector (final INotifier notifier)
    {
        super ("SoundFont 2", notifier, "Sf2");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new Sf2DetectorTask (this.notifier, consumer, folder, this.metadataPane, this.addFileName.isSelected (), this.addProgramNumber.isSelected (), this.logUnsupportedAttributes.isSelected ()));
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_SF2_OPTIONS");
        this.logUnsupportedAttributes = panel.createCheckBox ("@IDS_SF2_LOG_UNSUPPORTED_ATTRIBUTES");

        ////////////////////////////////////////////////////////////
        // Naming

        final TitledSeparator separator = panel.createSeparator ("@IDS_SF2_NAMING");
        separator.getStyleClass ().add ("titled-separator-pane");

        this.addFileName = panel.createCheckBox ("@IDS_SF2_NAMING_ADD_FILE_NAME");
        this.addProgramNumber = panel.createCheckBox ("@IDS_SF2_NAMING_ADD_PROGRAM_NUMBER");

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
        config.setBoolean (this.prefix + ADD_FILE_NAME_TAG, this.addFileName.isSelected ());
        config.setBoolean (this.prefix + ADD_PROGRAM_NUMBER_TAG, this.addProgramNumber.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.metadataPane.loadSettings (config);

        this.logUnsupportedAttributes.setSelected (config.getBoolean (this.prefix + LOG_UNSUPPORTED_ATTRIBUTES, false));
        this.addFileName.setSelected (config.getBoolean (this.prefix + ADD_FILE_NAME_TAG, false));
        this.addProgramNumber.setSelected (config.getBoolean (this.prefix + ADD_PROGRAM_NUMBER_TAG, false));
    }
}
