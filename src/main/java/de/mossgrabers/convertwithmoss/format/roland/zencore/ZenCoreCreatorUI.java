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

    /**
     * The full 16-byte SVZ header per target device in radio-button order (magic "SVZa" + version[2]
     * + model tag[5] + flag + reserved[4]); index 0 (FANTOM / KY019) is the default. The version and
     * flag bytes differ per device, so the whole header, not only the model tag, is selected. The
     * ZENOLOGY plug-in (RC001) is deliberately not offered: it imports only the tone from a .svz and
     * never the user samples (verified with the FANTOM's own user-sample exports as well), so a
     * multi-sample written for it would always play silent.
     */
    private static final byte [] []  HEADERS             =
    {
        // FANTOM / FANTOM-0 / FANTOM EX / Juno-X / Jupiter-X / Jupiter-Xm (device-confirmed)
        {
            'S', 'V', 'Z', 'a', 0x05, 0x04, 'K', 'Y', '0', '1', '9', 0x24, 0, 0, 0, 0
        },
        // GAIA-2 (model tag firmware-derived; version + flag reuse the FANTOM values - unverified)
        {
            'S', 'V', 'Z', 'a', 0x05, 0x04, 'M', 'I', '0', '8', '5', 0x24, 0, 0, 0, 0
        }
    };
    /** The 5-character model tag per device, used to match the CLI parameter value. */
    private static final String []   TAGS                =
    {
        "KY019",
        "MI085"
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
        for (int i = 0; i < HEADERS.length; i++)
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
        // Clamp: configurations saved by older versions could hold the index of the since-removed
        // ZENOLOGY option.
        final int stored = config.getInteger (ZENCORE_TARGET_DEVICE, 0);
        Functions.setSelectedToggleIndex (this.targetDeviceToggleGroup, stored < 0 || stored >= HEADERS.length ? 0 : stored);
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
        for (int i = 0; i < TAGS.length; i++)
            if (TAGS[i].equals (tag))
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
     * Get the full 16-byte SVZ header for the selected target device (magic + version + model tag +
     * flag + reserved).
     *
     * @return A copy of the 16-byte header
     */
    public byte [] getHeader ()
    {
        return HEADERS[this.targetDevice].clone ();
    }
}
