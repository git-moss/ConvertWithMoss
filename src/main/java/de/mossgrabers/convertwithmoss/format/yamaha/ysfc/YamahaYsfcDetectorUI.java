// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;


/**
 * Settings for the YSFC detector.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcDetectorUI extends MetadataSettingsUI
{
    private static final String YSFC_SOURCE_TYPE = "YsfcSourceType";

    private ToggleGroup         sourceTypeToggleGroup;
    private boolean             sourceType;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public YamahaYsfcDetectorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_YSFC_SOURCE_TYPE");

        this.sourceTypeToggleGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_YSFC_SOURCE_TYPE_WAVEFORMS");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_YSFC_SOURCE_TYPE"));
        order1.setToggleGroup (this.sourceTypeToggleGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_YSFC_SOURCE_TYPE_PERFORMANCES");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_YSFC_SOURCE_TYPE"));
        order2.setToggleGroup (this.sourceTypeToggleGroup);

        ////////////////////////////////////////////////////////////
        // Metadata

        this.addTo (panel);
        this.getSeparator ().getStyleClass ().add ("titled-separator-pane");

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.sourceTypeToggleGroup.selectToggle (this.sourceTypeToggleGroup.getToggles ().get (config.getBoolean (YSFC_SOURCE_TYPE, true) ? 1 : 0));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (YSFC_SOURCE_TYPE, this.sourceTypeToggleGroup.getToggles ().get (1).isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.sourceType = this.sourceTypeToggleGroup.getToggles ().get (1).isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        String value = parameters.remove (YSFC_SOURCE_TYPE);
        this.sourceType = "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (YSFC_SOURCE_TYPE);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Is the source type a performance?
     *
     * @return True if it is performance
     */
    public boolean isSourceTypePerformance ()
    {
        return this.sourceType;
    }
}
