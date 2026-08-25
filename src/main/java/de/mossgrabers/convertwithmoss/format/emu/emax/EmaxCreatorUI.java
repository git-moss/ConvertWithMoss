// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emax;

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
 * Settings for the E-mu Emax creator. The samplers have little sample memory - 512 KB on the Emax
 * and 2 MB on the Emax II - which is why the sample rate is worth choosing: it decides how much
 * audio fits into a bank and how far the sampler can transpose a sample upwards.
 *
 * @author Jürgen Moßgraber
 */
public class EmaxCreatorUI implements ICoreTaskSettings
{
    private static final String TARGET_DEVICE         = "TargetDevice";
    private static final String SAMPLE_RATE           = "SampleRate";
    /** The setting which picks the highest rate that still allows the necessary transposition. */
    private static final int    SAMPLE_RATE_AUTOMATIC = -1;

    private final String        prefix;
    private ComboBox<String>    targetDeviceBox;
    private ComboBox<String>    sampleRateBox;
    private EmaxModel           targetModel           = EmaxModel.EMAX;
    private int                 sampleRateIndex       = SAMPLE_RATE_AUTOMATIC;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public EmaxCreatorUI (final String prefix)
    {
        this.prefix = prefix;
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_EMAX_TARGET_DEVICE");
        this.targetDeviceBox = new ComboBox<> ();
        this.targetDeviceBox.getItems ().addAll (Functions.getText ("@IDS_EMAX_DEVICE_EMAX"), Functions.getText ("@IDS_EMAX_DEVICE_EMAX_2"));
        this.targetDeviceBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.targetDeviceBox);

        panel.createSeparator ("@IDS_EMAX_SAMPLE_RATE");
        this.sampleRateBox = new ComboBox<> ();
        this.sampleRateBox.getItems ().add (Functions.getText ("@IDS_EMAX_SAMPLE_RATE_AUTOMATIC"));
        for (final int sampleRate: EmaxConstants.SAMPLE_RATES)
            this.sampleRateBox.getItems ().add (Integer.toString (sampleRate));
        this.sampleRateBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.sampleRateBox);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.targetDeviceBox.getSelectionModel ().select (Math.clamp (config.getInteger (this.prefix + TARGET_DEVICE, 0), 0, 1));
        this.sampleRateBox.getSelectionModel ().select (Math.clamp (config.getInteger (this.prefix + SAMPLE_RATE, 0), 0, EmaxConstants.SAMPLE_RATES.length));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (this.prefix + TARGET_DEVICE, this.targetDeviceBox.getSelectionModel ().getSelectedIndex ());
        config.setInteger (this.prefix + SAMPLE_RATE, this.sampleRateBox.getSelectionModel ().getSelectedIndex ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.targetModel = this.targetDeviceBox.getSelectionModel ().getSelectedIndex () == 1 ? EmaxModel.EMAX_2 : EmaxModel.EMAX;
        this.sampleRateIndex = Math.clamp (this.sampleRateBox.getSelectionModel ().getSelectedIndex (), 0, EmaxConstants.SAMPLE_RATES.length) - 1;
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String device = parameters.remove (this.prefix + TARGET_DEVICE);
        this.targetModel = EmaxModel.EMAX;
        if (device != null)
            switch (device.trim ().toLowerCase (Locale.US))
            {
                case "emax", "emax1", "emaxi", "1":
                    // this.targetModel is already EmaxModel.EMAX;
                    break;
                case "emax2", "emaxii", "2":
                    this.targetModel = EmaxModel.EMAX_2;
                    break;
                default:
                    notifier.logError ("IDS_CLI_UNKNOWN_OUTPUT_FORMAT", device);
                    return false;
            }

        final String value = parameters.remove (this.prefix + SAMPLE_RATE);
        if (value == null)
        {
            this.sampleRateIndex = SAMPLE_RATE_AUTOMATIC;
            return true;
        }

        final String trimmed = value.trim ().toLowerCase (Locale.US);
        if ("auto".equals (trimmed) || "automatic".equals (trimmed))
        {
            this.sampleRateIndex = SAMPLE_RATE_AUTOMATIC;
            return true;
        }

        for (int i = 0; i < EmaxConstants.SAMPLE_RATES.length; i++)
            if (Integer.toString (EmaxConstants.SAMPLE_RATES[i]).equals (trimmed))
            {
                this.sampleRateIndex = i;
                return true;
            }

        notifier.logError ("IDS_EMAX_UNKNOWN_SAMPLE_RATE", value);
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            this.prefix + TARGET_DEVICE,
            this.prefix + SAMPLE_RATE
        };
    }


    /**
     * Get the sampler for which the bank is written.
     *
     * @return The sampler
     */
    public EmaxModel getTargetModel ()
    {
        return this.targetModel;
    }


    /**
     * Get the index of the sample rate at which all samples should be stored.
     *
     * @return The index into {@link EmaxConstants#SAMPLE_RATES} or -1 to pick it automatically
     */
    public int getSampleRateIndex ()
    {
        return this.sampleRateIndex;
    }
}
