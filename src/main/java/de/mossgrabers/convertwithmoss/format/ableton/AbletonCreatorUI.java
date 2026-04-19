// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

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
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;


/**
 * Settings for the Ableton Sampler creator.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonCreatorUI extends WavChunkSettingsUI
{
    private static final String ABLETON_VERSION = "AbletonVersion";

    private ToggleGroup         abletonVersionGroup;
    private int                 abletonVersion;


    /**
     * Constructor.
     */
    public AbletonCreatorUI ()
    {
        super ("Ableton");
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_ADV_ABLETON_VERSION");

        this.abletonVersionGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_ADV_ABLETON_VERSION_11");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_ADV_ABLETON_VERSION"));
        order1.setToggleGroup (this.abletonVersionGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_ADV_ABLETON_VERSION_12");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_ADV_ABLETON_VERSION"));
        order2.setToggleGroup (this.abletonVersionGroup);

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        final int abletonVersion = config.getInteger (ABLETON_VERSION, 12);
        this.abletonVersionGroup.selectToggle (this.abletonVersionGroup.getToggles ().get (abletonVersion == 12 ? 1 : 0));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (ABLETON_VERSION, this.getAbletonVersion ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.abletonVersion = this.abletonVersionGroup.getToggles ().get (0).isSelected () ? 11 : 12;
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        try
        {
            final String value = parameters.remove (ABLETON_VERSION);
            if (value == null || value.isBlank ())
                this.abletonVersion = 11;
            else
            {
                this.abletonVersion = Integer.parseInt (value);
                if (this.abletonVersion != 11 && this.abletonVersion != 12)
                {
                    notifier.logError ("IDS_ADV_ABLETON_VERSION_NOT_SUPPORTED", Integer.toString (this.abletonVersion));
                    return false;
                }
            }
        }
        catch (final NumberFormatException ex)
        {
            notifier.logError ("IDS_CLI_VALUE_MUST_BE_INTEGER", ABLETON_VERSION);
            return false;
        }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (ABLETON_VERSION);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the Ableton version to support.
     *
     * @return 11 or 12
     */
    public int getAbletonVersion ()
    {
        return this.abletonVersion;
    }
}
