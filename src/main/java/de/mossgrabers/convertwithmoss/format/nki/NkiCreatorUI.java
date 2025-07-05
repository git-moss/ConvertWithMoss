// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

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
 * Settings for the NkiCreator.
 *
 * @author Jürgen Moßgraber
 */
public class NkiCreatorUI extends WavChunkSettingsUI
{
    private static final String NKI_OUTPUT_FORMAT = "NkiOutputFormat";

    private ToggleGroup         outputFormatGroup;
    private boolean             outputFormat;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public NkiCreatorUI (final String prefix)
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
        final RadioButton order1 = panel.createRadioButton ("@IDS_NKI_KONTAKT_1");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_OUTPUT_FORMAT"));
        order1.setToggleGroup (this.outputFormatGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_NKI_KONTAKT_6_8");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_OUTPUT_FORMAT"));
        order2.setToggleGroup (this.outputFormatGroup);
        // TODO remove if implementation is finished
        order2.setDisable (true);

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        final int formatIndex = config.getInteger (NKI_OUTPUT_FORMAT, 0);
        final ObservableList<Toggle> toggles = this.outputFormatGroup.getToggles ();
        this.outputFormatGroup.selectToggle (toggles.get (formatIndex < toggles.size () ? formatIndex : 0));

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
                config.setInteger (NKI_OUTPUT_FORMAT, i);
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

        this.outputFormat = this.outputFormatGroup.getToggles ().get (0).isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (INotifier notifier, Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        // TODO Currently only Kontakt 1 supported...
        @SuppressWarnings("unused")
        String value = parameters.remove (NKI_OUTPUT_FORMAT);
        this.outputFormat = true;

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (NKI_OUTPUT_FORMAT);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should Kontakt 1 format be created?
     * 
     * @return True to create Kontakt 1
     */
    public boolean isKontakt1 ()
    {
        return this.outputFormat;
    }
}
