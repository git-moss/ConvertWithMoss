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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the Synthstrom Deluge creator. Adds the choice to write a synth (sound) preset or a
 * drum kit and how many oscillators of a synth play samples to the shared WAV chunk options.
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


    /** How many of the two oscillators of a synth (sound) play samples. */
    public enum OscillatorMode
    {
        /** The first oscillator plays the loudest layer, the second one is silent. */
        SINGLE,
        /**
         * Both oscillators play samples: two layers which sound together or the softest and the
         * loudest velocity layer.
         */
        DUAL
    }


    private static final String OUTPUT_TYPE      = "OutputType";
    private static final String OSCILLATOR_MODE  = "OscillatorMode";
    private static final String CONSOLIDATE_KIT  = "ConsolidateKit";
    private static final String SHORTEN_KIT_NAME = "ShortenKitName";

    private ComboBox<String>    outputTypeBox;
    private ComboBox<String>    oscillatorModeBox;
    private CheckBox            consolidateKitBox;
    private CheckBox            shortenKitNameBox;
    private OutputType          outputType       = OutputType.SOUND;
    private OscillatorMode      oscillatorMode   = OscillatorMode.SINGLE;
    private boolean             consolidateKit;
    private boolean             shortenKitName;


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
        this.oscillatorModeBox = panel.createComboBox ("@IDS_DELUGE_OSCILLATOR_MODE", List.of (Functions.getText ("@IDS_DELUGE_OSCILLATOR_MODE_SINGLE"), Functions.getText ("@IDS_DELUGE_OSCILLATOR_MODE_DUAL")));
        this.consolidateKitBox = panel.createCheckBox ("@IDS_DELUGE_CONSOLIDATE_KIT", "@IDS_DELUGE_CONSOLIDATE_KIT_TOOLTIP");
        this.shortenKitNameBox = panel.createCheckBox ("@IDS_DELUGE_SHORTEN_KIT_NAME", "@IDS_DELUGE_SHORTEN_KIT_NAME_TOOLTIP");

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputTypeBox.getSelectionModel ().select (config.getInteger (this.prefix + OUTPUT_TYPE, 0));
        this.oscillatorModeBox.getSelectionModel ().select (config.getInteger (this.prefix + OSCILLATOR_MODE, 0));
        this.consolidateKitBox.setSelected (config.getBoolean (this.prefix + CONSOLIDATE_KIT, false));
        this.shortenKitNameBox.setSelected (config.getBoolean (this.prefix + SHORTEN_KIT_NAME, false));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (this.prefix + OUTPUT_TYPE, this.outputTypeBox.getSelectionModel ().getSelectedIndex ());
        config.setInteger (this.prefix + OSCILLATOR_MODE, this.oscillatorModeBox.getSelectionModel ().getSelectedIndex ());
        config.setBoolean (this.prefix + CONSOLIDATE_KIT, this.consolidateKitBox.isSelected ());
        config.setBoolean (this.prefix + SHORTEN_KIT_NAME, this.shortenKitNameBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.outputType = indexToType (this.outputTypeBox.getSelectionModel ().getSelectedIndex ());
        this.oscillatorMode = indexToOscillatorMode (this.oscillatorModeBox.getSelectionModel ().getSelectedIndex ());
        this.consolidateKit = this.consolidateKitBox.isSelected ();
        this.shortenKitName = this.shortenKitNameBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        this.outputType = parseType (parameters.remove (this.prefix + OUTPUT_TYPE));
        this.oscillatorMode = parseOscillatorMode (parameters.remove (this.prefix + OSCILLATOR_MODE));
        this.consolidateKit = "1".equals (parameters.remove (this.prefix + CONSOLIDATE_KIT));
        this.shortenKitName = "1".equals (parameters.remove (this.prefix + SHORTEN_KIT_NAME));
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + OUTPUT_TYPE);
        parameterNames.add (this.prefix + OSCILLATOR_MODE);
        parameterNames.add (this.prefix + CONSOLIDATE_KIT);
        parameterNames.add (this.prefix + SHORTEN_KIT_NAME);
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


    /**
     * Get the selected oscillator mode, which applies to a synth (sound) preset. A drum of a kit
     * always plays a single sample.
     *
     * @return The oscillator mode
     */
    public OscillatorMode getOscillatorMode ()
    {
        return this.oscillatorMode;
    }


    /**
     * Should a drum kit be consolidated to one drum per type, ordered by drum role?
     *
     * @return True to consolidate
     */
    public boolean isConsolidateKit ()
    {
        return this.consolidateKit;
    }


    /**
     * Should a kit be given a short name ("NNN &lt;last name segment&gt;") for the device display?
     *
     * @return True to shorten the kit name
     */
    public boolean isShortenKitName ()
    {
        return this.shortenKitName;
    }


    private static OutputType indexToType (final int index)
    {
        return index == 1 ? OutputType.KIT : OutputType.SOUND;
    }


    private static OutputType parseType (final String value)
    {
        return value != null && "kit".equalsIgnoreCase (value.trim ()) ? OutputType.KIT : OutputType.SOUND;
    }


    private static OscillatorMode indexToOscillatorMode (final int index)
    {
        return index == 1 ? OscillatorMode.DUAL : OscillatorMode.SINGLE;
    }


    private static OscillatorMode parseOscillatorMode (final String value)
    {
        return value != null && "dual".equalsIgnoreCase (value.trim ()) ? OscillatorMode.DUAL : OscillatorMode.SINGLE;
    }
}
