// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.directwave;

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
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the DirectWave creator.
 *
 * @author Jürgen Moßgraber
 */
public class DirectWaveCreatorUI extends WavChunkSettingsUI
{
    private static final String MONOLITHIC = "Monolithic";

    private CheckBox            monolithicCheckBox;

    private boolean             monolithic;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public DirectWaveCreatorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_DWP_OUTPUT");

        this.monolithicCheckBox = panel.createCheckBox ("@IDS_DWP_MONOLITHIC", "@IDS_DWP_MONOLITHIC_TOOLTIP");

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.monolithicCheckBox.setSelected (config.getBoolean (this.prefix + MONOLITHIC, true));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (this.prefix + MONOLITHIC, this.monolithicCheckBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.monolithic = this.monolithicCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (this.prefix + MONOLITHIC);
        this.monolithic = value == null || !"0".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + MONOLITHIC);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should all samples be embedded into the DWP file?
     *
     * @return True to write one monolithic file instead of a folder with the samples
     */
    public boolean isMonolithic ()
    {
        return this.monolithic;
    }
}
