// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;


/**
 * Settings for the SF2 creator.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2CreatorUI implements ICoreTaskSettings
{
    private static final String    SF2_DOWNSAMPLE_TO_16BIT = "Sf2DownsampleTo16Bit";
    private static final String [] CLI_PARAMETER_NAMES     =
    {
        SF2_DOWNSAMPLE_TO_16BIT
    };

    private CheckBox               resampleTo16BitCheckBox;
    private boolean                resampleTo16Bit;


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.createSeparator ("@IDS_OUTPUT_FORMAT");
        this.resampleTo16BitCheckBox = panel.createCheckBox ("@IDS_SF2_RESAMPLE_TO_16BIT");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.resampleTo16BitCheckBox.setSelected (config.getBoolean (SF2_DOWNSAMPLE_TO_16BIT, false));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (SF2_DOWNSAMPLE_TO_16BIT, this.resampleTo16BitCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.resampleTo16Bit = this.resampleTo16BitCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (INotifier notifier, Map<String, String> parameters)
    {
        String value = parameters.remove (SF2_DOWNSAMPLE_TO_16BIT);
        this.resampleTo16Bit = "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return CLI_PARAMETER_NAMES;
    }


    /**
     * Should 24-bit audio be re-sampled to 16-bit?
     *
     * @return True to re-sample
     */
    public boolean isDownsampleTo16Bit ()
    {
        return this.resampleTo16Bit;
    }
}