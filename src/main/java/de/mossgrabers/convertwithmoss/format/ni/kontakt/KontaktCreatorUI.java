// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;


/**
 * Settings for the NkiCreator.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktCreatorUI extends WavChunkSettingsUI
{
    /** The supported output formats in the order of the radio buttons. */
    public enum OutputFormat
    {
        /** Kontakt 1 (NiSS). */
        KONTAKT_1,
        /** Kontakt 2 (K2). */
        KONTAKT_2,
        /** Kontakt 6.8. */
        KONTAKT_6_8
    }


    private static final String NKI_OUTPUT_FORMAT = "NkiOutputFormat";

    private ToggleGroup         outputFormatGroup;
    private OutputFormat        outputFormat      = OutputFormat.KONTAKT_1;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public KontaktCreatorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_OUTPUT_FORMAT");

        this.outputFormatGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_NKI_KONTAKT_1");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_OUTPUT_FORMAT"));
        order1.setToggleGroup (this.outputFormatGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_NKI_KONTAKT_2");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_OUTPUT_FORMAT"));
        order2.setToggleGroup (this.outputFormatGroup);
        final RadioButton order3 = panel.createRadioButton ("@IDS_NKI_KONTAKT_6_8");
        order3.setAccessibleHelp (Functions.getMessage ("IDS_OUTPUT_FORMAT"));
        order3.setToggleGroup (this.outputFormatGroup);

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        final int formatIndex = config.getInteger (NKI_OUTPUT_FORMAT, 0);
        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        final Toggle toggle = toggles.get (formatIndex >= 0 && formatIndex < toggles.size () ? formatIndex : 0);
        // Do not select a format which is not available
        this.outputFormatGroup.selectToggle (toggle instanceof final RadioButton radioButton && radioButton.isDisabled () ? toggles.get (0) : toggle);

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        for (int i = 0; i < toggles.size (); i++)
            if (toggles.get (i).isSelected ())
            {
                config.setInteger (NKI_OUTPUT_FORMAT, i);
                break;
            }

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        final int selected = Functions.getSelectedToggleIndex (this.outputFormatGroup);
        final OutputFormat [] formats = OutputFormat.values ();
        this.outputFormat = selected < 0 || selected >= formats.length ? OutputFormat.KONTAKT_1 : formats[selected];
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        // The output format is the Kontakt version: 1, 2 or 6.8, Kontakt 1 is the default
        final String value = parameters.remove (NKI_OUTPUT_FORMAT);
        if (value == null || value.isBlank () || "1".equals (value.trim ()))
            this.outputFormat = OutputFormat.KONTAKT_1;
        else if ("2".equals (value.trim ()))
            this.outputFormat = OutputFormat.KONTAKT_2;
        else if ("6.8".equals (value.trim ()))
            this.outputFormat = OutputFormat.KONTAKT_6_8;
        else
        {
            notifier.logError ("IDS_CLI_UNKNOWN_OUTPUT_FORMAT", value);
            return false;
        }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (NKI_OUTPUT_FORMAT);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the format to create.
     *
     * @return The output format
     */
    public OutputFormat getOutputFormat ()
    {
        return this.outputFormat;
    }
}
