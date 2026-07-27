// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.settings;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;


/**
 * Encapsulates the option to shorten the name of a multi-sample, which is used by the destinations
 * whose name field only holds few characters. Since the dropped part of the name is not stored
 * anywhere else, the option is disabled by default.
 *
 * @author Jürgen Moßgraber
 */
public class ShortNameSettingsUI implements ICoreTaskSettings
{
    private static final String SHORTEN_NAME = "ShortenName";

    protected final String      prefix;

    private CheckBox            shortenNameCheckBox;
    private boolean             shortenName;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public ShortNameSettingsUI (final String prefix)
    {
        this.prefix = prefix;
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.addTo (panel);
        return panel.getPane ();
    }


    /**
     * Add the widgets to the given panel.
     *
     * @param panel The panel
     */
    public void addTo (final BoxPanel panel)
    {
        panel.createSeparator ("@IDS_OUTPUT_NAMING");
        this.shortenNameCheckBox = panel.createCheckBox ("@IDS_SHORTEN_NAME", "@IDS_SHORTEN_NAME_TOOLTIP");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (this.prefix + SHORTEN_NAME, this.shortenNameCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.shortenNameCheckBox.setSelected (config.getBoolean (this.prefix + SHORTEN_NAME, false));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.shortenName = this.shortenNameCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        this.shortenName = "1".equals (parameters.remove (this.prefix + SHORTEN_NAME));
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            this.prefix + SHORTEN_NAME
        };
    }


    /**
     * Should the name be shortened to its last segment for the display of the device?
     *
     * @return True to shorten the name
     */
    public boolean isShortenName ()
    {
        return this.shortenName;
    }
}
