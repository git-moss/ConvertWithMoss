// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

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
 * Settings for the Elektron emulti creators.
 *
 * @author Jürgen Moßgraber
 */
public class ElektronMultiCreatorUI extends WavChunkSettingsUI
{
    private static final String RESAMPLE_TO_24_48 = "ResampleTo2448";

    private CheckBox            resampleTo2448CheckBox;

    private boolean             resampleTo2448;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public ElektronMultiCreatorUI (final String prefix)
    {
        super (prefix, true, true, true, true);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_ELEKTRON_RESAMPLE");

        this.resampleTo2448CheckBox = panel.createCheckBox ("@IDS_ELEKTRON_CONVERT_TO_24_48");

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.resampleTo2448CheckBox.setSelected (config.getBoolean (this.prefix + RESAMPLE_TO_24_48, true));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (this.prefix + RESAMPLE_TO_24_48, this.resampleTo2448CheckBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.resampleTo2448 = this.resampleTo2448CheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (this.prefix + RESAMPLE_TO_24_48);
        this.resampleTo2448 = "1".equals (value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + RESAMPLE_TO_24_48);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should sample be re-sampled to 24bit and 48kHz?
     *
     * @return True if re-sampling should be applied
     */
    public boolean resampleTo2448 ()
    {
        return this.resampleTo2448;
    }
}
