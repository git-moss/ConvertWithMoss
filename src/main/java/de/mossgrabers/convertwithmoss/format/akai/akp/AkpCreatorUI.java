// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AbstractAkpChunk;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpGeneration;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;


/**
 * Settings for the AKP creator.
 *
 * @author Jürgen Moßgraber
 */
public class AkpCreatorUI extends WavChunkSettingsUI
{
    private static final String AKP_GENERATION = "Generation";

    private final String        generationProperty;
    private ToggleGroup         generationGroup;
    private AkpGeneration       generation     = AbstractAkpChunk.DEFAULT_GENERATION;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public AkpCreatorUI (final String prefix)
    {
        super (prefix, false, false, true, false);

        this.generationProperty = prefix + AKP_GENERATION;
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_AKP_GENERATION");
        this.generationGroup = new ToggleGroup ();
        for (final String label: new String []
        {
            "@IDS_AKP_GENERATION_1",
            "@IDS_AKP_GENERATION_2",
            "@IDS_AKP_GENERATION_3"
        })
        {
            final RadioButton button = panel.createRadioButton (label);
            button.setAccessibleHelp (Functions.getMessage ("IDS_AKP_GENERATION"));
            button.setToggleGroup (this.generationGroup);
        }

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        final int index = Math.clamp (config.getInteger (this.generationProperty, AbstractAkpChunk.DEFAULT_GENERATION.ordinal () + 1) - 1, 0, AkpGeneration.values ().length - 1);
        this.generationGroup.selectToggle (this.generationGroup.getToggles ().get (index));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (this.generationProperty, this.getSelectedGeneration ().ordinal () + 1);

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.generation = this.getSelectedGeneration ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (this.generationProperty);
        if (value == null || value.isBlank ())
        {
            this.generation = AbstractAkpChunk.DEFAULT_GENERATION;
            return true;
        }

        final AkpGeneration [] generations = AkpGeneration.values ();
        try
        {
            final int index = Integer.parseInt (value.trim ()) - 1;
            if (index >= 0 && index < generations.length)
            {
                this.generation = generations[index];
                return true;
            }
        }
        catch (final NumberFormatException _)
        {
            // Reported below
        }
        notifier.logError ("IDS_AKP_CLI_GENERATION", this.generationProperty);
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.generationProperty);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the generation of the AKP format to write.
     *
     * @return The generation
     */
    public AkpGeneration getGeneration ()
    {
        return this.generation;
    }


    /**
     * Get the generation which is selected in the user interface.
     *
     * @return The generation
     */
    private AkpGeneration getSelectedGeneration ()
    {
        final List<Toggle> toggles = this.generationGroup.getToggles ();
        for (int i = 0; i < toggles.size (); i++)
            if (toggles.get (i).isSelected ())
                return AkpGeneration.values ()[i];
        return AbstractAkpChunk.DEFAULT_GENERATION;
    }
}
