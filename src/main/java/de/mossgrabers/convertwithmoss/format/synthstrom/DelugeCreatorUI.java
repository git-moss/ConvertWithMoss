// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synthstrom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the Synthstrom Deluge creator. Adds the choice to write a synth (sound) preset or a
 * drum kit to the shared WAV chunk options.
 *
 * @author Jürgen Moßgraber
 */
public class DelugeCreatorUI extends WavChunkSettingsUI
{
    /** The type of Deluge instrument to write. */
    public enum OutputType
    {
        /** Write a synth (sound) preset. */
        SOUND,
        /** Write a drum kit. */
        KIT
    }


    private static final String OUTPUT_TYPE = "OutputType";

    private ComboBox<String>    outputTypeBox;
    private OutputType          outputType  = OutputType.SOUND;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public DelugeCreatorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_DELUGE_OUTPUT_TYPE");
        this.outputTypeBox = new ComboBox<> ();
        this.outputTypeBox.getItems ().addAll (Functions.getText ("@IDS_DELUGE_TYPE_SOUND"), Functions.getText ("@IDS_DELUGE_TYPE_KIT"));
        this.outputTypeBox.setMaxWidth (Double.MAX_VALUE);
        panel.addComponent (this.outputTypeBox);

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputTypeBox.getSelectionModel ().select (config.getInteger (this.prefix + OUTPUT_TYPE, 0));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (this.prefix + OUTPUT_TYPE, this.outputTypeBox.getSelectionModel ().getSelectedIndex ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.outputType = indexToType (this.outputTypeBox.getSelectionModel ().getSelectedIndex ());
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        this.outputType = parseType (parameters.remove (this.prefix + OUTPUT_TYPE));
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + OUTPUT_TYPE);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the selected output type.
     *
     * @return The output type
     */
    public OutputType getOutputType ()
    {
        return this.outputType;
    }


    private static OutputType indexToType (final int index)
    {
        return index == 1 ? OutputType.KIT : OutputType.SOUND;
    }


    private static OutputType parseType (final String value)
    {
        if (value == null)
            return OutputType.SOUND;
        return "kit".equals (value.trim ().toLowerCase ()) ? OutputType.KIT : OutputType.SOUND;
    }
}
