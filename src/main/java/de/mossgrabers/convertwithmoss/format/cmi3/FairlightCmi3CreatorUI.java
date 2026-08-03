// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.cmi3;

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
 * Settings for the Fairlight CMI voice creator.
 *
 * @author Jürgen Moßgraber
 */
public class FairlightCmi3CreatorUI implements ICoreTaskSettings
{
    /** The voice file dialect to write. */
    public enum TargetFormat
    {
        /** A Series III voice file with sub-voices. */
        SERIES_III,
        /** An 8-bit CMI I/II/IIx voice file, as read by QasarBeach and the Arturia CMI V. */
        SERIES_IIX
    }


    private static final String    CMI3_TARGET_FORMAT   = "CMI3TargetFormat";
    private static final String    CMI3_IIX_SAMPLE_RATE = "CMI3IIxSampleRate";

    /** Sample rates the CMI I/II/IIx supported (2.1-32 kHz, 14080 Hz is the documented default). */
    private static final Integer [] IIX_SAMPLE_RATES    =
    {
        Integer.valueOf (14080),
        Integer.valueOf (16000),
        Integer.valueOf (22050),
        Integer.valueOf (30208),
        Integer.valueOf (32000),
        Integer.valueOf (8000)
    };

    private ComboBox<String>       targetFormatBox;
    private ComboBox<Integer>      iixSampleRateBox;
    private TargetFormat           targetFormat         = TargetFormat.SERIES_III;
    private int                    iixSampleRate        = IIX_SAMPLE_RATES[0].intValue ();


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_CMI3_TARGET_FORMAT");
        this.targetFormatBox = new ComboBox<> ();
        this.targetFormatBox.getItems ().addAll (Functions.getText ("@IDS_CMI3_FORMAT_SERIES_III"), Functions.getText ("@IDS_CMI3_FORMAT_SERIES_IIX"));
        this.targetFormatBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.targetFormatBox);

        panel.createSeparator ("@IDS_CMI3_IIX_SAMPLE_RATE");
        this.iixSampleRateBox = new ComboBox<> ();
        this.iixSampleRateBox.getItems ().addAll (IIX_SAMPLE_RATES);
        this.iixSampleRateBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.iixSampleRateBox);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.targetFormatBox.getSelectionModel ().select (Math.clamp (config.getInteger (CMI3_TARGET_FORMAT, 0), 0, TargetFormat.values ().length - 1));
        this.iixSampleRateBox.getSelectionModel ().select (Integer.valueOf (config.getInteger (CMI3_IIX_SAMPLE_RATE, IIX_SAMPLE_RATES[0].intValue ())));
        if (this.iixSampleRateBox.getSelectionModel ().getSelectedIndex () < 0)
            this.iixSampleRateBox.getSelectionModel ().select (IIX_SAMPLE_RATES[0]);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (CMI3_TARGET_FORMAT, this.targetFormatBox.getSelectionModel ().getSelectedIndex ());
        final Integer sampleRate = this.iixSampleRateBox.getSelectionModel ().getSelectedItem ();
        config.setInteger (CMI3_IIX_SAMPLE_RATE, sampleRate == null ? IIX_SAMPLE_RATES[0].intValue () : sampleRate.intValue ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.targetFormat = TargetFormat.values ()[Math.clamp (this.targetFormatBox.getSelectionModel ().getSelectedIndex (), 0, TargetFormat.values ().length - 1)];
        final Integer sampleRate = this.iixSampleRateBox.getSelectionModel ().getSelectedItem ();
        this.iixSampleRate = sampleRate == null ? IIX_SAMPLE_RATES[0].intValue () : sampleRate.intValue ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String formatValue = parameters.remove (CMI3_TARGET_FORMAT);
        if (formatValue == null)
            this.targetFormat = TargetFormat.SERIES_III;
        else
            switch (formatValue.trim ().toLowerCase (Locale.US))
            {
                case "iii", "3", "seriesiii":
                    this.targetFormat = TargetFormat.SERIES_III;
                    break;
                case "iix", "2x", "2", "qasarbeach":
                    this.targetFormat = TargetFormat.SERIES_IIX;
                    break;
                default:
                    notifier.logError ("IDS_CLI_UNKNOWN_OUTPUT_FORMAT", formatValue);
                    return false;
            }

        final String rateValue = parameters.remove (CMI3_IIX_SAMPLE_RATE);
        if (rateValue == null)
        {
            this.iixSampleRate = IIX_SAMPLE_RATES[0].intValue ();
            return true;
        }
        try
        {
            this.iixSampleRate = Math.clamp (Integer.parseInt (rateValue.trim ()), 2100, 96000);
        }
        catch (final NumberFormatException ex)
        {
            notifier.logError ("IDS_CLI_VALUE_MUST_BE_INTEGER", CMI3_IIX_SAMPLE_RATE);
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
            CMI3_TARGET_FORMAT,
            CMI3_IIX_SAMPLE_RATE
        };
    }


    /**
     * Get the voice file dialect to write.
     *
     * @return The target format
     */
    public TargetFormat getTargetFormat ()
    {
        return this.targetFormat;
    }


    /**
     * Get the sample rate at which IIx voice files are written. The IIx format does not store a
     * sample rate, therefore this is the reference rate of the written audio data.
     *
     * @return The sample rate in Hertz
     */
    public int getIIxSampleRate ()
    {
        return this.iixSampleRate;
    }
}
