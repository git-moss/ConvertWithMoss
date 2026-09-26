// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil.pc3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ShortNameSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the Kurzweil PC3/Forte creator: the target device, which selects the file extension,
 * the program layout and the sample object type of the written file.
 *
 * @author Jürgen Moßgraber
 */
public class PC3CreatorUI extends ShortNameSettingsUI
{
    /**
     * The target device. Every device of the family loads the files of the others; the selection
     * sets the file extension, the program layout and the sample object type of the device.
     */
    public enum TargetDevice
    {
        /** Kurzweil PC3K. */
        PC3K("p3k", PC3Program.Layout.PC3K, false),
        /** Kurzweil Forte and Forte 7. */
        FORTE("for", PC3Program.Layout.FORTE, true),
        /** Kurzweil Forte SE. */
        FORTE_SE("fse", PC3Program.Layout.FORTE, true),
        /** Kurzweil PC4. */
        PC4("pc4", PC3Program.Layout.K2700, true),
        /** Kurzweil PC4 SE. */
        PC4_SE("p4s", PC3Program.Layout.K2700, true),
        /** Kurzweil K2700. */
        K2700("k27", PC3Program.Layout.K2700, true);


        private final String            extension;
        private final PC3Program.Layout layout;
        private final boolean           hasExtendedSamples;


        TargetDevice (final String extension, final PC3Program.Layout layout, final boolean hasExtendedSamples)
        {
            this.extension = extension;
            this.layout = layout;
            this.hasExtendedSamples = hasExtendedSamples;
        }


        /**
         * Get the file extension of the device.
         *
         * @return The extension without a dot
         */
        public String getExtension ()
        {
            return this.extension;
        }


        /**
         * Get the program layout of the device generation.
         *
         * @return The layout
         */
        public PC3Program.Layout getLayout ()
        {
            return this.layout;
        }


        /**
         * Does the device store its samples in the sample object of the Forte generation (64-bit
         * positions) instead of the one of the PC3K?
         *
         * @return True for the Forte generation
         */
        public boolean hasExtendedSamples ()
        {
            return this.hasExtendedSamples;
        }
    }


    private static final String PC3_TARGET_DEVICE = "PC3TargetDevice";

    private ComboBox<String>    targetDeviceBox;
    private TargetDevice        targetDevice      = TargetDevice.PC3K;


    /**
     * Constructor.
     */
    public PC3CreatorUI ()
    {
        super ("PC3");
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_PC3_TARGET_DEVICE");
        this.targetDeviceBox = new ComboBox<> ();
        this.targetDeviceBox.getItems ().addAll (Functions.getText ("@IDS_PC3_DEVICE_PC3K"), Functions.getText ("@IDS_PC3_DEVICE_FORTE"), Functions.getText ("@IDS_PC3_DEVICE_FORTE_SE"), Functions.getText ("@IDS_PC3_DEVICE_PC4"), Functions.getText ("@IDS_PC3_DEVICE_PC4_SE"), Functions.getText ("@IDS_PC3_DEVICE_K2700"));
        this.targetDeviceBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.targetDeviceBox);

        this.addTo (panel);
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        this.targetDeviceBox.getSelectionModel ().select (Math.clamp (config.getInteger (PC3_TARGET_DEVICE, 0), 0, TargetDevice.values ().length - 1));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        super.saveSettings (config);

        config.setInteger (PC3_TARGET_DEVICE, this.targetDeviceBox.getSelectionModel ().getSelectedIndex ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        final int selected = this.targetDeviceBox.getSelectionModel ().getSelectedIndex ();
        this.targetDevice = TargetDevice.values ()[Math.clamp (selected, 0, TargetDevice.values ().length - 1)];
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (PC3_TARGET_DEVICE);
        if (value == null)
        {
            this.targetDevice = TargetDevice.PC3K;
            return true;
        }

        switch (value.trim ().toLowerCase (Locale.US))
        {
            case "pc3k", "p3k":
                this.targetDevice = TargetDevice.PC3K;
                break;
            case "forte", "for":
                this.targetDevice = TargetDevice.FORTE;
                break;
            case "fortese", "fse":
                this.targetDevice = TargetDevice.FORTE_SE;
                break;
            case "pc4":
                this.targetDevice = TargetDevice.PC4;
                break;
            case "pc4se", "p4s":
                this.targetDevice = TargetDevice.PC4_SE;
                break;
            case "k2700", "k27":
                this.targetDevice = TargetDevice.K2700;
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
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (PC3_TARGET_DEVICE);
        return parameterNames.toArray (new String [parameterNames.size ()]);
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
