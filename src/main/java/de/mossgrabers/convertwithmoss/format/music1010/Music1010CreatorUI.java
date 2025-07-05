// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

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
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;


/**
 * Settings for the 1010music creator.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010CreatorUI extends WavChunkSettingsUI
{
    private static final String MUSIC_1010_INTERPOLATION_QUALITY = "1010musicInterpolationQuality";
    private static final String MUSIC_1010_RESAMPLE_TO_24_48     = "1010musicResampleTo2448";
    private static final String MUSIC_1010_TRIM_START_TO_END     = "1010musicTrimStartToEnd";

    private ToggleGroup         interpolationQualityGroup;
    private CheckBox            resampleTo2448CheckBox;
    private CheckBox            trimStartToEndCheckBox;

    private boolean             interpolationQuality;
    private boolean             resampleTo2448;
    private boolean             trimStartToEnd;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public Music1010CreatorUI (final String prefix)
    {
        super (prefix, true, true, true, true);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_1010_MUSIC_INTER_QUALITY");

        this.interpolationQualityGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_1010_MUSIC_INTERPOLATION_QUALITY_NORMAL");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_1010_MUSIC_INTER_QUALITY"));
        order1.setToggleGroup (this.interpolationQualityGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_1010_MUSIC_INTERPOLATION_QUALITY_HIGH");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_1010_MUSIC_INTER_QUALITY"));
        order2.setToggleGroup (this.interpolationQualityGroup);

        this.resampleTo2448CheckBox = panel.createCheckBox ("@IDS_1010_MUSIC_CONVERT_TO_24_48");
        this.trimStartToEndCheckBox = panel.createCheckBox ("@IDS_1010_MUSIC_TRIM_START_TO_END");

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.interpolationQualityGroup.selectToggle (this.interpolationQualityGroup.getToggles ().get (config.getBoolean (MUSIC_1010_INTERPOLATION_QUALITY, false) ? 1 : 0));
        this.resampleTo2448CheckBox.setSelected (config.getBoolean (MUSIC_1010_RESAMPLE_TO_24_48, true));
        this.trimStartToEndCheckBox.setSelected (config.getBoolean (MUSIC_1010_TRIM_START_TO_END, true));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (MUSIC_1010_INTERPOLATION_QUALITY, this.interpolationQualityGroup.getToggles ().get (1).isSelected ());
        config.setBoolean (MUSIC_1010_RESAMPLE_TO_24_48, this.resampleTo2448CheckBox.isSelected ());
        config.setBoolean (MUSIC_1010_TRIM_START_TO_END, this.trimStartToEndCheckBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.interpolationQuality = this.interpolationQualityGroup.getToggles ().get (1).isSelected ();
        this.resampleTo2448 = this.resampleTo2448CheckBox.isSelected ();
        this.trimStartToEnd = this.trimStartToEndCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (INotifier notifier, Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        String value = parameters.remove (MUSIC_1010_INTERPOLATION_QUALITY);
        this.interpolationQuality = "1".equals (value);

        value = parameters.remove (MUSIC_1010_RESAMPLE_TO_24_48);
        this.resampleTo2448 = "1".equals (value);

        value = parameters.remove (MUSIC_1010_TRIM_START_TO_END);
        this.trimStartToEnd = "1".equals (value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (MUSIC_1010_INTERPOLATION_QUALITY);
        parameterNames.add (MUSIC_1010_RESAMPLE_TO_24_48);
        parameterNames.add (MUSIC_1010_TRIM_START_TO_END);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should high interpolation quality be set?
     *
     * @return True if high quality should be set
     */
    public boolean isInterpolationQualityHigh ()
    {
        return this.interpolationQuality;
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


    /**
     * Should the sample be trimmed?
     * 
     * @return True if it should be trimmed
     */
    public boolean trimStartToEnd ()
    {
        return this.trimStartToEnd;
    }
}
