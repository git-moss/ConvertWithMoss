// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;


/**
 * Settings for the Elektron Tonverk preset (*.tvpst) creator.
 *
 * @author Jürgen Moßgraber
 */
public class TonverkPresetCreatorUI extends WavChunkSettingsUI
{
    /** The Tonverk generator machine to write. */
    public enum OutputEngine
    {
        /** Write a Multi (multi-sample) machine preset. */
        MULTI,
        /** Write a Drum machine preset. */
        DRUM,
        /** Choose the machine automatically from the source. */
        AUTO
    }


    private static final String OUTPUT_ENGINE     = "OutputEngine";
    private static final String RESAMPLE_TO_24_48   = "ResampleTo2448";
    private static final String REMOVE_CATEGORY_TAG = "RemoveCategoryTag";
    private static final String FIRST_NUMBER        = "FirstPresetNumber";
    private static final int    MAX_NUMBER          = 999;

    private ComboBox<String>    outputEngineBox;
    private CheckBox            removeCategoryTagCheckBox;
    private TextField           firstNumberField;
    private CheckBox            resampleTo2448CheckBox;

    private OutputEngine        outputEngine        = OutputEngine.MULTI;
    private boolean             removeCategoryTag;
    private int                 nextNumber          = -1;
    private boolean             resampleTo2448;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public TonverkPresetCreatorUI (final String prefix)
    {
        // Only the sample chunk is enabled by default: the Tonverk factory WAV files contain only
        // 'fmt ', 'data' and 'smpl' chunks and the Tonverk WAV parser is strict
        super (prefix, false, false, true, true);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_TONVERK_OUTPUT_ENGINE");
        this.outputEngineBox = new ComboBox<> ();
        this.outputEngineBox.getItems ().addAll (Functions.getText ("@IDS_TONVERK_ENGINE_MULTI"), Functions.getText ("@IDS_TONVERK_ENGINE_DRUM"), Functions.getText ("@IDS_TONVERK_ENGINE_AUTO"));
        this.outputEngineBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.outputEngineBox);

        final TitledSeparator nameSeparator = panel.createSeparator ("@IDS_TONVERK_PRESET_NAME");
        nameSeparator.getStyleClass ().add ("titled-separator-pane");
        this.removeCategoryTagCheckBox = panel.createCheckBox ("@IDS_TONVERK_REMOVE_CATEGORY_TAG");
        this.firstNumberField = panel.createField ("@IDS_TONVERK_FIRST_PRESET_NUMBER");

        final TitledSeparator resampleSeparator = panel.createSeparator ("@IDS_ELEKTRON_RESAMPLE");
        resampleSeparator.getStyleClass ().add ("titled-separator-pane");
        this.resampleTo2448CheckBox = panel.createCheckBox ("@IDS_ELEKTRON_CONVERT_TO_24_48");

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputEngineBox.getSelectionModel ().select (config.getInteger (this.prefix + OUTPUT_ENGINE, 0));
        this.removeCategoryTagCheckBox.setSelected (config.getBoolean (this.prefix + REMOVE_CATEGORY_TAG, false));
        this.firstNumberField.setText (config.getProperty (this.prefix + FIRST_NUMBER, ""));
        this.resampleTo2448CheckBox.setSelected (config.getBoolean (this.prefix + RESAMPLE_TO_24_48, true));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (this.prefix + OUTPUT_ENGINE, this.outputEngineBox.getSelectionModel ().getSelectedIndex ());
        config.setBoolean (this.prefix + REMOVE_CATEGORY_TAG, this.removeCategoryTagCheckBox.isSelected ());
        config.setProperty (this.prefix + FIRST_NUMBER, this.firstNumberField.getText ().trim ());
        config.setBoolean (this.prefix + RESAMPLE_TO_24_48, this.resampleTo2448CheckBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.outputEngine = indexToEngine (this.outputEngineBox.getSelectionModel ().getSelectedIndex ());
        this.removeCategoryTag = this.removeCategoryTagCheckBox.isSelected ();
        this.resampleTo2448 = this.resampleTo2448CheckBox.isSelected ();
        return this.parseFirstNumber (notifier, this.firstNumberField.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        this.outputEngine = parseEngine (parameters.remove (this.prefix + OUTPUT_ENGINE));
        this.removeCategoryTag = ICoreTaskSettings.parseBoolean (parameters.remove (this.prefix + REMOVE_CATEGORY_TAG), false);
        this.resampleTo2448 = ICoreTaskSettings.parseBoolean (parameters.remove (this.prefix + RESAMPLE_TO_24_48), true);
        return this.parseFirstNumber (notifier, parameters.remove (this.prefix + FIRST_NUMBER));
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + OUTPUT_ENGINE);
        parameterNames.add (this.prefix + REMOVE_CATEGORY_TAG);
        parameterNames.add (this.prefix + FIRST_NUMBER);
        parameterNames.add (this.prefix + RESAMPLE_TO_24_48);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the selected output engine.
     *
     * @return The output engine
     */
    public OutputEngine getOutputEngine ()
    {
        return this.outputEngine;
    }


    /**
     * Should a category tag at the start of the name (e.g. 'PAD' in 'PAD Solina') be removed from
     * the preset name?
     *
     * @return True to remove it
     */
    public boolean removeCategoryTag ()
    {
        return this.removeCategoryTag;
    }


    /**
     * Get the number for the next preset name and count it up. The numbering starts with the
     * first number of the settings with every conversion.
     *
     * @return The number or -1 if the presets are not numbered
     */
    public int nextPresetNumber ()
    {
        if (this.nextNumber < 0)
            return -1;
        final int number = this.nextNumber;
        if (this.nextNumber < MAX_NUMBER)
            this.nextNumber++;
        return number;
    }


    /**
     * Should samples be re-sampled to 24bit and 48kHz?
     *
     * @return True if re-sampling should be applied
     */
    public boolean resampleTo2448 ()
    {
        return this.resampleTo2448;
    }


    private boolean parseFirstNumber (final INotifier notifier, final String value)
    {
        this.nextNumber = -1;
        if (value == null || value.isBlank ())
            return true;
        try
        {
            final int number = Integer.parseInt (value.trim ());
            if (number >= 0 && number <= MAX_NUMBER)
            {
                this.nextNumber = number;
                return true;
            }
        }
        catch (final NumberFormatException _)
        {
            // Fall through
        }
        notifier.logError ("IDS_TONVERK_ERR_FIRST_PRESET_NUMBER", value.trim ());
        return false;
    }


    private static OutputEngine indexToEngine (final int index)
    {
        return switch (index)
        {
            case 1 -> OutputEngine.DRUM;
            case 2 -> OutputEngine.AUTO;
            default -> OutputEngine.MULTI;
        };
    }


    private static OutputEngine parseEngine (final String value)
    {
        if (value == null)
            return OutputEngine.MULTI;
        return switch (value.trim ().toLowerCase ())
        {
            case "drum" -> OutputEngine.DRUM;
            case "auto" -> OutputEngine.AUTO;
            default -> OutputEngine.MULTI;
        };
    }
}
