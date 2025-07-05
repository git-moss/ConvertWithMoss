// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.settings;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.tools.ui.BasicConfig;
import javafx.scene.Node;


/**
 * Interface for configuring a task.
 *
 * @author Jürgen Moßgraber
 */
public interface ICoreTaskSettings
{
    /**
     * Get the pane with the edit widgets.
     *
     * @return The pane
     */
    Node getEditPane ();


    /**
     * Save the settings of the task.
     *
     * @param configuration Where to store to
     */
    void saveSettings (BasicConfig configuration);


    /**
     * Load the settings of the task.
     *
     * @param configuration Where to load from
     */
    void loadSettings (BasicConfig configuration);


    /**
     * Check if the settings which are required for the execution of the task are correct, when set
     * from the user interface.
     * 
     * @param notifier Where to report errors
     * @return True if correct and the task can be executed
     */
    boolean checkSettingsUI (INotifier notifier);


    /**
     * Check if the settings which are required for the execution of the task are correct, when set
     * from the command line interface.
     * 
     * @param notifier Where to report errors
     * @param parameters The parameters from which to configure the settings
     * @return True if correct and the task can be executed
     */
    boolean checkSettingsCLI (INotifier notifier, Map<String, String> parameters);


    /**
     * Get all parameter names which are used by these settings.
     * 
     * @return The names
     */
    String [] getCLIParameterNames ();
}
