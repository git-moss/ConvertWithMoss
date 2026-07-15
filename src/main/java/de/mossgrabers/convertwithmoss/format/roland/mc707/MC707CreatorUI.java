// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mc707;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the MC-707/MC-101 creator: how to write multi-zone sources. By default they become
 * user drum kits (one sample per key with the transposition baked into the per-key pitch), the
 * pattern of Roland's own user-sample preset kits and therefore the device-verified choice. The
 * project format also contains a multisample key-map table (the analog of the FANTOM's, whose tone
 * record layout the MC shares), which represents a melodic multi-zone source faithfully as one
 * chromatic multisample tone - but no Roland-authored project uses that table, so it could not be
 * verified against device behavior yet.
 *
 * @author Jürgen Moßgraber
 */
public class MC707CreatorUI implements ICoreTaskSettings
{
    private static final String MC707_MULTISAMPLE_TONES = "MC707MultisampleTones";

    private CheckBox            multisampleTonesCheckBox;
    private boolean             multisampleTones        = false;


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.createSeparator ("@IDS_MC707_SEPARATOR");
        this.multisampleTonesCheckBox = panel.createCheckBox ("@IDS_MC707_MULTISAMPLE_TONES");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.multisampleTonesCheckBox.setSelected (config.getBoolean (MC707_MULTISAMPLE_TONES, false));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (MC707_MULTISAMPLE_TONES, this.multisampleTonesCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.multisampleTones = this.multisampleTonesCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String value = parameters.remove (MC707_MULTISAMPLE_TONES);
        this.multisampleTones = "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            MC707_MULTISAMPLE_TONES
        };
    }


    /**
     * Should melodic multi-zone sources be written as multisample tones instead of drum kits?
     *
     * @return True to write multisample tones
     */
    public boolean isMultisampleTones ()
    {
        return this.multisampleTones;
    }
}
