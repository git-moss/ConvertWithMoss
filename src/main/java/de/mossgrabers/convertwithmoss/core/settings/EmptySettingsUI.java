// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.settings;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;


/**
 * No settings at all.
 *
 * @author Jürgen Moßgraber
 */
public class EmptySettingsUI implements ICoreTaskSettings
{
    /** Singleton. */
    public static final EmptySettingsUI INSTANCE = new EmptySettingsUI ();


    /**
     * Constructor.
     */
    private EmptySettingsUI ()
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        return new BoxPanel (Orientation.VERTICAL).getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig configuration)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig configuration)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String [0];
    }
}
