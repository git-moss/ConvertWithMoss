// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

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
 * Settings for the Kurzweil creator.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilCreatorUI implements ICoreTaskSettings
{
    /**
     * The target device family. Since the created files use only K2000 features, the selection only
     * sets the matching file extension.
     */
    public enum TargetDevice
    {
        /** Kurzweil K2000. */
        K2000("krz"),
        /** Kurzweil K2500. */
        K2500("k25"),
        /** Kurzweil K2600. */
        K2600("k26");


        private final String extension;


        private TargetDevice (final String extension)
        {
            this.extension = extension;
        }


        /**
         * Get the file extension of the device family.
         *
         * @return The extension without a dot
         */
        public String getExtension ()
        {
            return this.extension;
        }
    }


    private static final String KURZWEIL_TARGET_DEVICE = "KurzweilTargetDevice";

    private ComboBox<String>    targetDeviceBox;
    private TargetDevice        targetDevice           = TargetDevice.K2000;


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_KURZWEIL_TARGET_DEVICE");
        this.targetDeviceBox = new ComboBox<> ();
        this.targetDeviceBox.getItems ().addAll (Functions.getText ("@IDS_KURZWEIL_DEVICE_K2000"), Functions.getText ("@IDS_KURZWEIL_DEVICE_K2500"), Functions.getText ("@IDS_KURZWEIL_DEVICE_K2600"));
        this.targetDeviceBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.targetDeviceBox);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.targetDeviceBox.getSelectionModel ().select (Math.clamp (config.getInteger (KURZWEIL_TARGET_DEVICE, 0), 0, TargetDevice.values ().length - 1));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (KURZWEIL_TARGET_DEVICE, this.targetDeviceBox.getSelectionModel ().getSelectedIndex ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        final int selected = this.targetDeviceBox.getSelectionModel ().getSelectedIndex ();
        this.targetDevice = TargetDevice.values ()[Math.clamp (selected, 0, TargetDevice.values ().length - 1)];
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String value = parameters.remove (KURZWEIL_TARGET_DEVICE);
        if (value == null)
        {
            this.targetDevice = TargetDevice.K2000;
            return true;
        }

        switch (value.trim ().toLowerCase (Locale.US))
        {
            case "k2000", "krz":
                this.targetDevice = TargetDevice.K2000;
                break;
            case "k2500", "k25":
                this.targetDevice = TargetDevice.K2500;
                break;
            case "k2600", "k26":
                this.targetDevice = TargetDevice.K2600;
                break;
            default:
                notifier.logError ("IDS_CLI_UNKNOWN_OUTPUT_FORMAT", value);
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
            KURZWEIL_TARGET_DEVICE
        };
    }


    /**
     * Get the selected target device.
     *
     * @return The target device
     */
    public TargetDevice getTargetDevice ()
    {
        return this.targetDevice;
    }
}
