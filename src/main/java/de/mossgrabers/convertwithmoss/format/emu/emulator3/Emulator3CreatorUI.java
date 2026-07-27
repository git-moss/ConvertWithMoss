// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator3;

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
 * Settings for the E-mu Emulator III creator. The EIII, the EIIIX and the ESI samplers all read
 * their own bank format; the EIIIX format is the most compatible one since the ESI samplers load it
 * as well.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator3CreatorUI implements ICoreTaskSettings
{
    private static final String       TARGET_DEVICE  = "TargetDevice";

    /** The bank formats which can be written. */
    private static final Emulator3BankFormat [] TARGET_FORMATS =
    {
        Emulator3BankFormat.EMULATOR_3X,
        Emulator3BankFormat.ESI_32_V3
    };

    private final String              prefix;
    private ComboBox<String>          targetDeviceBox;
    private Emulator3BankFormat       targetFormat   = Emulator3BankFormat.EMULATOR_3X;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public Emulator3CreatorUI (final String prefix)
    {
        this.prefix = prefix;
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_EIII_TARGET_DEVICE");
        this.targetDeviceBox = new ComboBox<> ();
        this.targetDeviceBox.getItems ().addAll (Functions.getText ("@IDS_EIII_DEVICE_EMULATOR_3X"), Functions.getText ("@IDS_EIII_DEVICE_ESI"));
        this.targetDeviceBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.targetDeviceBox);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.targetDeviceBox.getSelectionModel ().select (Math.clamp (config.getInteger (this.prefix + TARGET_DEVICE, 0), 0, TARGET_FORMATS.length - 1));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (this.prefix + TARGET_DEVICE, this.targetDeviceBox.getSelectionModel ().getSelectedIndex ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.targetFormat = TARGET_FORMATS[Math.clamp (this.targetDeviceBox.getSelectionModel ().getSelectedIndex (), 0, TARGET_FORMATS.length - 1)];
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String value = parameters.remove (this.prefix + TARGET_DEVICE);
        if (value == null)
        {
            this.targetFormat = Emulator3BankFormat.EMULATOR_3X;
            return true;
        }

        switch (value.trim ().toLowerCase (Locale.US))
        {
            case "eiiix", "e3x", "emulator3x":
                this.targetFormat = Emulator3BankFormat.EMULATOR_3X;
                break;
            case "esi", "esi32", "esi2000", "esi4000":
                this.targetFormat = Emulator3BankFormat.ESI_32_V3;
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
            this.prefix + TARGET_DEVICE
        };
    }


    /**
     * Get the bank format to write.
     *
     * @return The bank format
     */
    public Emulator3BankFormat getTargetFormat ()
    {
        return this.targetFormat;
    }
}
