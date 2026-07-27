// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator4;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the E-mu Emulator IV creator.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator4CreatorUI implements ICoreTaskSettings
{
    private static final String WRITE_CD_IMAGE = "WriteCdImage";

    private final String        prefix;
    private CheckBox            writeCdImageCheckBox;
    private boolean             writeCdImage   = false;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public Emulator4CreatorUI (final String prefix)
    {
        this.prefix = prefix;
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.createSeparator ("@IDS_E4B_OUTPUT_FORMAT");
        this.writeCdImageCheckBox = panel.createCheckBox ("@IDS_E4B_WRITE_CD_IMAGE");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.writeCdImageCheckBox.setSelected (config.getBoolean (this.prefix + WRITE_CD_IMAGE, false));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (this.prefix + WRITE_CD_IMAGE, this.writeCdImageCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.writeCdImage = this.writeCdImageCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        this.writeCdImage = "1".equals (parameters.remove (this.prefix + WRITE_CD_IMAGE));
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            this.prefix + WRITE_CD_IMAGE
        };
    }


    /**
     * Should a CD-ROM image be written instead of a plain bank file?
     *
     * @return True to write a CD-ROM image
     */
    public boolean writeCdImage ()
    {
        return this.writeCdImage;
    }
}
