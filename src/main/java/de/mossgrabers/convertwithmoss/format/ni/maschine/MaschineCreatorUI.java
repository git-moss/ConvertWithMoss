// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;


/**
 * Settings for the MaschineCreator.
 *
 * @author Jürgen Moßgraber
 */
public class MaschineCreatorUI extends WavChunkSettingsUI
{
    private static final String MASCHINE_OUTPUT_FORMAT = "MaschineOutputFormat";

    private ToggleGroup         outputFormatGroup;
    private int                 outputFormat           = 2;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public MaschineCreatorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_OUTPUT_FORMAT");

        this.outputFormatGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_MASCHINE_MASCHINE_1");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_OUTPUT_FORMAT"));
        order1.setToggleGroup (this.outputFormatGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_MASCHINE_MASCHINE_2");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_OUTPUT_FORMAT"));
        order2.setToggleGroup (this.outputFormatGroup);
        final RadioButton order3 = panel.createRadioButton ("@IDS_MASCHINE_MASCHINE_3");
        order3.setAccessibleHelp (Functions.getMessage ("IDS_OUTPUT_FORMAT"));
        order3.setToggleGroup (this.outputFormatGroup);

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        int formatIndex = config.getInteger (MASCHINE_OUTPUT_FORMAT, 1);
        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        this.outputFormatGroup.selectToggle (toggles.get (formatIndex < toggles.size () ? formatIndex : 1));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        for (int i = 0; i < toggles.size (); i++)
            if (toggles.get (i).isSelected ())
            {
                config.setInteger (MASCHINE_OUTPUT_FORMAT, i);
                break;
            }

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        for (int i = 0; i < toggles.size (); i++)
            if (toggles.get (i).isSelected ())
            {
                this.outputFormat = i + 1;
                break;
            }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (MASCHINE_OUTPUT_FORMAT);
        try
        {
            this.outputFormat = Integer.parseInt (value);
        }
        catch (final NumberFormatException ex)
        {
            notifier.logError ("IDS_NI_MASCHINE_ONLY_V1_NOT_SUPPORTED");
            return false;
        }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (MASCHINE_OUTPUT_FORMAT);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Which output format should be created?
     *
     * @return 1-3 for Maschine 1-3 file format
     */
    public int getDestinationVersion ()
    {
        return this.outputFormat;
    }
}
