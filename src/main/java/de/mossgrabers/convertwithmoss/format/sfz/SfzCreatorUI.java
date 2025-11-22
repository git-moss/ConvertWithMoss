// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;


/**
 * Settings for the SFZ creator.
 *
 * @author Jürgen Moßgraber
 */
public class SfzCreatorUI extends WavChunkSettingsUI
{
    private static final String SFZ_CONVERT_TO_FLAC = "SFZConvertToFlac";

    private CheckBox            convertToFlacCheckBox;
    private boolean             convertToFlac;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public SfzCreatorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.createSeparator ("@IDS_OUTPUT_FORMAT");
        this.convertToFlacCheckBox = panel.createCheckBox ("@IDS_SFZ_CONVERT_TO_FLAC");
        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.convertToFlacCheckBox.setSelected (config.getBoolean (SFZ_CONVERT_TO_FLAC, false));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (SFZ_CONVERT_TO_FLAC, this.convertToFlacCheckBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.convertToFlac = this.convertToFlacCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        String value = parameters.remove (SFZ_CONVERT_TO_FLAC);
        this.convertToFlac = "1".equals (value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (SFZ_CONVERT_TO_FLAC);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should the output samples be converted to FLAC?
     *
     * @return True to convert
     */
    public boolean convertToFlac ()
    {
        return this.convertToFlac;
    }
}
