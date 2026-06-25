// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the Omnisphere detector.
 *
 * @author Jürgen Moßgraber
 */
public class OmnisphereDetectorUI extends MetadataSettingsUI
{
    private static final String OMNISPHERE_USE_PRESETS = "OmnisphereUsePresets";

    private CheckBox            usePresetFilesCheckBox;
    private boolean             usePresetFiles;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public OmnisphereDetectorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        //////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_OMNISPHERE_OPTIONS");

        this.usePresetFilesCheckBox = panel.createCheckBox ("@IDS_OMNISPHERE_USE_PRESETS");
        // Otherwise the underscore does not show up
        this.usePresetFilesCheckBox.setMnemonicParsing (false);

        //////////////////////////////////////////////
        // Metadata

        this.addTo (panel);
        this.getSeparator ().getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        super.saveSettings (config);

        config.setBoolean (OMNISPHERE_USE_PRESETS, this.usePresetFilesCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        this.usePresetFilesCheckBox.setSelected (config.getBoolean (OMNISPHERE_USE_PRESETS, true));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.usePresetFiles = this.usePresetFilesCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (OMNISPHERE_USE_PRESETS);
        this.usePresetFiles = "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (OMNISPHERE_USE_PRESETS);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should Presets (.prt_omn) instead of ZMAP files be used as the input?
     *
     * @return True if they should be used
     */
    public boolean usePresetFiles ()
    {
        return this.usePresetFiles;
    }
}
