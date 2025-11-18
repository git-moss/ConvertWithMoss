// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

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
 * Settings for the MPC Keygroup creator.
 *
 * @author Jürgen Moßgraber
 */
public class MPCKeygroupCreatorUI extends WavChunkSettingsUI
{
    private static final String MPC_LAYER_LIMIT_USE_8 = "MPCLayerLimitUse8";

    private ToggleGroup         layerLimitGroup;
    private int                 layerLimit;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public MPCKeygroupCreatorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_MPC_LAYER_LIMIT");

        this.layerLimitGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_MPC_LAYER_LIMIT_4");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_MPC_LAYER_LIMIT"));
        order1.setToggleGroup (this.layerLimitGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_MPC_LAYER_LIMIT_8");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_MPC_LAYER_LIMIT"));
        order2.setToggleGroup (this.layerLimitGroup);

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.layerLimitGroup.selectToggle (this.layerLimitGroup.getToggles ().get (config.getBoolean (MPC_LAYER_LIMIT_USE_8, true) ? 1 : 0));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (MPC_LAYER_LIMIT_USE_8, this.getLayerLimit () == 8);

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.layerLimit = this.layerLimitGroup.getToggles ().get (1).isSelected () ? 8 : 4;
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
            String value = parameters.remove (MPC_LAYER_LIMIT_USE_8);
            if (value == null || value.isBlank ())
                this.layerLimit = 8;
            else
            {
                this.layerLimit = Integer.parseInt (value);
                if (this.layerLimit != 4 && this.layerLimit != 8)
                {
                    notifier.logError ("IDS_CLI_LAYER_LIMIT_NOT_4_OR_8");
                    return false;
                }
            }
        }
        catch (final NumberFormatException ex)
        {
            notifier.logError ("IDS_CLI_VALUE_MUST_BE_INTEGER", MPC_LAYER_LIMIT_USE_8);
            return false;
        }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (MPC_LAYER_LIMIT_USE_8);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the limit for the number of layers in key-groups.
     *
     * @return 8 or 4
     */
    public int getLayerLimit ()
    {
        return this.layerLimit;
    }
}
