// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
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
    private static final String RESAMPLE_TO_24_48 = "ResampleTo2448";

    private ComboBox<String>    outputEngineBox;
    private CheckBox            resampleTo2448CheckBox;

    private OutputEngine        outputEngine      = OutputEngine.MULTI;
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
        this.resampleTo2448CheckBox.setSelected (config.getBoolean (this.prefix + RESAMPLE_TO_24_48, true));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (this.prefix + OUTPUT_ENGINE, this.outputEngineBox.getSelectionModel ().getSelectedIndex ());
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
        this.resampleTo2448 = this.resampleTo2448CheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        this.outputEngine = parseEngine (parameters.remove (this.prefix + OUTPUT_ENGINE));
        this.resampleTo2448 = "1".equals (parameters.remove (this.prefix + RESAMPLE_TO_24_48));
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + OUTPUT_ENGINE);
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
     * Should samples be re-sampled to 24bit and 48kHz?
     *
     * @return True if re-sampling should be applied
     */
    public boolean resampleTo2448 ()
    {
        return this.resampleTo2448;
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
