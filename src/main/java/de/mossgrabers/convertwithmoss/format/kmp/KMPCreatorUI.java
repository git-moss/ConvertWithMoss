// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;


/**
 * The settings for the Korg Multisample (KMP) creator.
 *
 * @author Jürgen Moßgraber
 */
public class KMPCreatorUI implements ICoreTaskSettings
{
    private static final String KMP_WRITE_GROUP_KMPS = "KMPWriteGroupKmps";
    private static final String KMP_GAIN_PLUS_12     = "KMPGainPlus12";
    private static final String KMP_MAXIMIZE_VOLUME  = "KMPMaximizeVolume";

    private CheckBox            writeGroupKmpsCheckBox;
    private CheckBox            gainPlus12CheckBox;
    private CheckBox            maximizeVolumeCheckBox;

    private boolean             writeGroupKmps;
    private boolean             gainPlus12;
    private boolean             maximizeVolume;


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.createSeparator ("@IDS_KMP_OPTIONS");
        this.writeGroupKmpsCheckBox = panel.createCheckBox ("@IDS_KMP_WRITE_KMP_FOR_EACH_GROUP", "@IDS_KMP_WRITE_KMP_FOR_EACH_GROUP_TOOLTIP");
        this.gainPlus12CheckBox = panel.createCheckBox ("@IDS_KMP_GAIN_12DB");
        this.maximizeVolumeCheckBox = panel.createCheckBox ("@IDS_KMP_MAXIMIZE_VOLUME", "@IDS_KMP_MAXIMIZE_VOLUME_TOOLTIP");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.writeGroupKmpsCheckBox.setSelected (config.getBoolean (KMP_WRITE_GROUP_KMPS, false));
        this.gainPlus12CheckBox.setSelected (config.getBoolean (KMP_GAIN_PLUS_12, false));
        this.maximizeVolumeCheckBox.setSelected (config.getBoolean (KMP_MAXIMIZE_VOLUME, false));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (KMP_WRITE_GROUP_KMPS, this.writeGroupKmpsCheckBox.isSelected ());
        config.setBoolean (KMP_GAIN_PLUS_12, this.gainPlus12CheckBox.isSelected ());
        config.setBoolean (KMP_MAXIMIZE_VOLUME, this.maximizeVolumeCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.writeGroupKmps = this.writeGroupKmpsCheckBox.isSelected ();
        this.maximizeVolume = this.maximizeVolumeCheckBox.isSelected ();
        this.gainPlus12 = this.gainPlus12CheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        String value = parameters.remove (KMP_WRITE_GROUP_KMPS);
        this.writeGroupKmps = "1".equals (value);

        value = parameters.remove (KMP_GAIN_PLUS_12);
        this.gainPlus12 = "1".equals (value);

        value = parameters.remove (KMP_MAXIMIZE_VOLUME);
        this.maximizeVolume = "1".equals (value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            KMP_WRITE_GROUP_KMPS,
            KMP_MAXIMIZE_VOLUME,
            KMP_GAIN_PLUS_12
        };
    }


    /**
     * Should groups be written as separate KMPs?
     *
     * @return True if they should be written as separate files
     */
    public boolean writeGroupKmps ()
    {
        return this.writeGroupKmps;
    }


    /**
     * Should the volume be maximized?
     *
     * @return True if volume should be set to maximum
     */
    public boolean maximizeVolume ()
    {
        return this.maximizeVolume;
    }


    /**
     * Should the gain +12dB be activated?
     *
     * @return True if it should be activated
     */
    public boolean gainPlus12 ()
    {
        return this.gainPlus12;
    }
}
