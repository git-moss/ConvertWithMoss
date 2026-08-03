// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.cmi3;

import java.util.Locale;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the Fairlight CMI voice creator.
 *
 * @author Jürgen Moßgraber
 */
public class FairlightCmi3CreatorUI implements ICoreTaskSettings
{
    /** The voice file dialect to write. */
    public enum TargetFormat
    {
        /** A Series III voice file with sub-voices. */
        SERIES_III,
        /** An 8-bit CMI I/II/IIx voice file with its control (CO) file. */
        SERIES_IIX,
        /** The native 16-bit voice format of the QasarBeach recreation. */
        QASAR_BEACH
    }


    private static final String CMI3_TARGET_FORMAT = "CMI3TargetFormat";

    private ComboBox<String>    targetFormatBox;
    private TargetFormat        targetFormat       = TargetFormat.SERIES_III;


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_CMI3_TARGET_FORMAT");
        this.targetFormatBox = new ComboBox<> ();
        this.targetFormatBox.getItems ().addAll (Functions.getText ("@IDS_CMI3_FORMAT_SERIES_III"), Functions.getText ("@IDS_CMI3_FORMAT_SERIES_IIX"), Functions.getText ("@IDS_CMI3_FORMAT_QASAR_BEACH"));
        this.targetFormatBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.targetFormatBox);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.targetFormatBox.getSelectionModel ().select (Math.clamp (config.getInteger (CMI3_TARGET_FORMAT, 0), 0, TargetFormat.values ().length - 1));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (CMI3_TARGET_FORMAT, this.targetFormatBox.getSelectionModel ().getSelectedIndex ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.targetFormat = TargetFormat.values ()[Math.clamp (this.targetFormatBox.getSelectionModel ().getSelectedIndex (), 0, TargetFormat.values ().length - 1)];
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String formatValue = parameters.remove (CMI3_TARGET_FORMAT);
        if (formatValue == null)
        {
            this.targetFormat = TargetFormat.SERIES_III;
            return true;
        }

        switch (formatValue.trim ().toLowerCase (Locale.US))
        {
            case "iii", "3", "seriesiii":
                this.targetFormat = TargetFormat.SERIES_III;
                break;
            case "iix", "2x", "2":
                this.targetFormat = TargetFormat.SERIES_IIX;
                break;
            case "qasarbeach", "qb", "qbv2":
                this.targetFormat = TargetFormat.QASAR_BEACH;
                break;
            default:
                notifier.logError ("IDS_CLI_UNKNOWN_OUTPUT_FORMAT", formatValue);
                return false;
        }
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            CMI3_TARGET_FORMAT
        };
    }


    /**
     * Get the voice file dialect to write.
     *
     * @return The target format
     */
    public TargetFormat getTargetFormat ()
    {
        return this.targetFormat;
    }
}
