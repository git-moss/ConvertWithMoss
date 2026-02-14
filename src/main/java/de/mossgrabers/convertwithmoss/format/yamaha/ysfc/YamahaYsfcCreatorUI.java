// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;


/**
 * Settings for the YSFC creator.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcCreatorUI implements ICoreTaskSettings
{
    private static final String               YSFC_OUTPUT_FORMAT_LIBRARY = "YsfcOutputFormatPreset";
    private static final String               YSFC_CREATE_ONLY_WAVEFORMS = "YsfcCreateOnlyWaveforms";

    private static final Map<String, Integer> OUTPUT_FORMAT_BY_NAME      = new HashMap<> ();
    static
    {
        OUTPUT_FORMAT_BY_NAME.put ("X7U", Integer.valueOf (0));
        OUTPUT_FORMAT_BY_NAME.put ("X7L", Integer.valueOf (1));
        OUTPUT_FORMAT_BY_NAME.put ("X8U", Integer.valueOf (2));
        OUTPUT_FORMAT_BY_NAME.put ("X8L", Integer.valueOf (3));
        // IMPROVE MOXF - Activate when MOXF writing is fixed
        // OUTPUT_FORMAT_BY_NAME.put ("X6W", Integer.valueOf (4));
    }

    private ToggleGroup outputFormatToggleGroup;
    private CheckBox    createOnlyWaveformsCheckBox;

    private int         outputFormat;
    private boolean     createOnlyWaveforms;


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_YSFC_LIBRARY_FORMAT");
        this.outputFormatToggleGroup = new ToggleGroup ();
        for (int i = 0; i < OUTPUT_FORMAT_BY_NAME.size (); i++)
        {
            final RadioButton order = panel.createRadioButton ("@IDS_YSFC_OUTPUT_FORMAT_OPTION" + i);
            order.setAccessibleHelp (Functions.getMessage ("IDS_YSFC_LIBRARY_FORMAT"));
            order.setToggleGroup (this.outputFormatToggleGroup);
        }

        this.createOnlyWaveformsCheckBox = panel.createCheckBox ("@IDS_YSFC_DESTINATION_TYPE_WAVEFORMS");
        this.createOnlyWaveformsCheckBox.getStyleClass ().add ("paddingTop");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        Functions.setSelectedToggleIndex (this.outputFormatToggleGroup, config.getInteger (YSFC_OUTPUT_FORMAT_LIBRARY, 1));
        this.createOnlyWaveformsCheckBox.setSelected (config.getBoolean (YSFC_CREATE_ONLY_WAVEFORMS, false));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        final int selected = Functions.getSelectedToggleIndex (this.outputFormatToggleGroup);
        config.setInteger (YSFC_OUTPUT_FORMAT_LIBRARY, selected < 0 ? 1 : selected);
        config.setBoolean (YSFC_CREATE_ONLY_WAVEFORMS, this.createOnlyWaveformsCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        final int selected = Functions.getSelectedToggleIndex (this.outputFormatToggleGroup);
        this.outputFormat = selected < 0 ? 1 : selected;
        this.createOnlyWaveforms = this.createOnlyWaveformsCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String value = parameters.remove (YSFC_OUTPUT_FORMAT_LIBRARY);
        if (value == null)
            this.outputFormat = 0;
        else
        {
            final Integer formatIndex = OUTPUT_FORMAT_BY_NAME.get (value.toUpperCase ());
            if (formatIndex == null)
            {
                notifier.logError ("IDS_CLI_UNKNOWN_OUTPUT_FORMAT", value);
                return false;
            }
            this.outputFormat = formatIndex.intValue ();
        }
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            YSFC_OUTPUT_FORMAT_LIBRARY,
            YSFC_CREATE_ONLY_WAVEFORMS
        };
    }


    /**
     * Get the output format.
     *
     * @return The index of the output format
     */
    public int selectedOutputFormat ()
    {
        return this.outputFormat;
    }


    /**
     * Should only waveforms be created (no performances)?
     *
     * @return Create only waveforms if true
     */
    public boolean createOnlyWaveforms ()
    {
        return this.createOnlyWaveforms;
    }
}
