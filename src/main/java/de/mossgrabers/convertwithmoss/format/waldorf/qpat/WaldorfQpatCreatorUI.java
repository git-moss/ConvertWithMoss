// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

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
 * Settings for the Waldorf QPAT creator.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatCreatorUI extends WavChunkSettingsUI
{
    private static final String QPAT_LIMIT_TO_16_441 = "QPATLimitTo16441";

    private CheckBox            limitTo16441CheckBox;
    private boolean             limitTo16441;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public WaldorfQpatCreatorUI (final String prefix)
    {
        super (prefix, true, false, false, false);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_QPAT_SEPARATOR");
        this.limitTo16441CheckBox = panel.createCheckBox ("@IDS_QPAT_RESAMPLE_TO_16_441");

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.limitTo16441CheckBox.setSelected (config.getBoolean (QPAT_LIMIT_TO_16_441, true));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (QPAT_LIMIT_TO_16_441, this.limitTo16441CheckBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.limitTo16441 = this.limitTo16441CheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (INotifier notifier, Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        String value = parameters.remove (QPAT_LIMIT_TO_16_441);
        this.limitTo16441 = "1".equals (value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (QPAT_LIMIT_TO_16_441);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should the output samples be limited to a maximum of 16bit / 44.1kHz?
     * 
     * @return True to limit
     */
    public boolean limitTo16441 ()
    {
        return this.limitTo16441;
    }
}
