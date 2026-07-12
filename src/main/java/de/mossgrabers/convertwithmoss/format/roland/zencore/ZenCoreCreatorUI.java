// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;


/**
 * Settings for the ZEN-Core creator: the target device whose 5-byte model tag is stamped into the
 * <i>.svz</i> header (bytes 6-10). ZEN-Core shares one SVZ/tone format across the range, but the
 * header tag selects which device accepts the file: <i>KY019</i> is the shared interchange tag
 * present in the FANTOM, FANTOM-0, FANTOM EX, Juno-X and Jupiter-X/Xm firmwares; the GAIA-2 carries
 * only <i>MI085</i>; the ZENOLOGY plug-in uses <i>RC001</i>. Default is <i>KY019</i>, which leaves
 * the FANTOM/Juno/Jupiter output unchanged.
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreCreatorUI implements ICoreTaskSettings
{
    private static final String    ZENCORE_TARGET_DEVICE = "ZenCoreTargetDevice";

    /** The 5-byte model tags in radio-button order; index 0 (KY019) is the default. */
    private static final String [] MODEL_TAGS            =
    {
        "KY019", // FANTOM / FANTOM-0 / FANTOM EX / Juno-X / Jupiter-X / Jupiter-Xm
        "MI085", // GAIA-2
        "RC001"  // ZENOLOGY plug-in
    };

    private ToggleGroup            targetDeviceToggleGroup;
    private int                    targetDevice;


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_ZENCORE_TARGET_DEVICE");
        this.targetDeviceToggleGroup = new ToggleGroup ();
        for (int i = 0; i < MODEL_TAGS.length; i++)
        {
            final RadioButton device = panel.createRadioButton ("@IDS_ZENCORE_TARGET_DEVICE_OPTION" + i);
            device.setAccessibleHelp (Functions.getMessage ("IDS_ZENCORE_TARGET_DEVICE"));
            device.setToggleGroup (this.targetDeviceToggleGroup);
        }

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        Functions.setSelectedToggleIndex (this.targetDeviceToggleGroup, config.getInteger (ZENCORE_TARGET_DEVICE, 0));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        final int selected = Functions.getSelectedToggleIndex (this.targetDeviceToggleGroup);
        config.setInteger (ZENCORE_TARGET_DEVICE, selected < 0 ? 0 : selected);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        final int selected = Functions.getSelectedToggleIndex (this.targetDeviceToggleGroup);
        this.targetDevice = selected < 0 ? 0 : selected;
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String value = parameters.remove (ZENCORE_TARGET_DEVICE);
        if (value == null)
        {
            this.targetDevice = 0;
            return true;
        }
        final String tag = value.trim ().toUpperCase ();
        for (int i = 0; i < MODEL_TAGS.length; i++)
            if (MODEL_TAGS[i].equals (tag))
            {
                this.targetDevice = i;
                return true;
            }
        notifier.logError ("IDS_CLI_UNKNOWN_OUTPUT_FORMAT", value);
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            ZENCORE_TARGET_DEVICE
        };
    }


    /**
     * Get the selected target device's 5-byte model tag written into the SVZ header.
     *
     * @return The model tag (e.g. {@code KY019})
     */
    public String getModelTag ()
    {
        return MODEL_TAGS[this.targetDevice];
    }
}
