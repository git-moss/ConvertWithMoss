// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the Synclavier V creator.
 *
 * @author Jürgen Moßgraber
 */
public class SynclavierVCreatorUI implements ICoreTaskSettings
{
    private static final String    SYNCLAVIER_V_WRITE_SAMPLE_POOL = "SynclavierVWriteSamplePool";
    private static final String [] CLI_PARAMETER_NAMES            =
    {
        SYNCLAVIER_V_WRITE_SAMPLE_POOL
    };

    private CheckBox               writeSamplePoolCheckBox;
    private boolean                writeSamplePool;


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.createSeparator ("@IDS_SYNCLAVIER_V_SEPARATOR");
        this.writeSamplePoolCheckBox = panel.createCheckBox ("@IDS_SYNCLAVIER_V_WRITE_SAMPLE_POOL");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.writeSamplePoolCheckBox.setSelected (config.getBoolean (SYNCLAVIER_V_WRITE_SAMPLE_POOL, false));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (SYNCLAVIER_V_WRITE_SAMPLE_POOL, this.writeSamplePoolCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.writeSamplePool = this.writeSamplePoolCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String value = parameters.remove (SYNCLAVIER_V_WRITE_SAMPLE_POOL);
        this.writeSamplePool = "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return CLI_PARAMETER_NAMES;
    }


    /**
     * Should the samples additionally be written as plain WAV files in the layout of the Arturia
     * sample pool?
     *
     * @return True to write them
     */
    public boolean isWriteSamplePool ()
    {
        return this.writeSamplePool;
    }
}
