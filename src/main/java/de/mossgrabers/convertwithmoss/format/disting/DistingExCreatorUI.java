// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.disting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;


/**
 * Settings for the DistingEx creator.
 *
 * @author Jürgen Moßgraber
 */
public class DistingExCreatorUI extends WavChunkSettingsUI
{
    private static final String DEX_LIMIT_TO_16_441   = "distingEXLimitTo16441";
    private static final String DEX_TRIM_START_TO_END = "distingEXTrimStartToEnd";

    private CheckBox            limitTo16441CheckBox;
    private CheckBox            trimStartToEndCheckBox;
    private boolean             limitTo16441;
    private boolean             trimStartToEnd;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public DistingExCreatorUI (final String prefix)
    {
        super (prefix, true, true, true, true);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_DEX_SEPARATOR");

        this.limitTo16441CheckBox = panel.createCheckBox ("@IDS_DEX_RESAMPLE_TO_16_441");
        this.trimStartToEndCheckBox = panel.createCheckBox ("@IDS_DEX_TRIM_START_TO_END");

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.limitTo16441CheckBox.setSelected (config.getBoolean (DEX_LIMIT_TO_16_441, true));
        this.trimStartToEndCheckBox.setSelected (config.getBoolean (DEX_TRIM_START_TO_END, true));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (DEX_LIMIT_TO_16_441, this.limitTo16441CheckBox.isSelected ());
        config.setBoolean (DEX_TRIM_START_TO_END, this.trimStartToEndCheckBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.limitTo16441 = this.limitTo16441CheckBox.isSelected ();
        this.trimStartToEnd = this.trimStartToEndCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        String value = parameters.remove (DEX_LIMIT_TO_16_441);
        this.limitTo16441 = "1".equals (value);

        value = parameters.remove (DEX_TRIM_START_TO_END);
        this.trimStartToEnd = "1".equals (value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (DEX_LIMIT_TO_16_441);
        parameterNames.add (DEX_TRIM_START_TO_END);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should the samples be trimmed?
     *
     * @return True to trim the samples
     */
    public boolean trimStartToEnd ()
    {
        return this.trimStartToEnd;
    }


    /**
     * Should the samples be limited to a maximum of 16bit / 44.1kHz?
     *
     * @return True to limit
     */
    public boolean limitTo16441 ()
    {
        return this.limitTo16441;
    }
}
