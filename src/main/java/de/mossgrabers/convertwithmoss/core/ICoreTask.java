// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import de.mossgrabers.tools.ui.BasicConfig;

import javafx.scene.Node;


/**
 * Base interface for creators and detectors providing some descriptive metadata.
 *
 * @author Jürgen Moßgraber
 */
public interface ICoreTask
{
    /**
     * Get the name of the object.
     *
     * @return The name
     */
    String getName ();


    /**
     * Get the pane with the edit widgets.
     *
     * @return The pane
     */
    Node getEditPane ();


    /**
     * Save the settings of the detector.
     *
     * @param configuration Where to store to
     */
    void saveSettings (BasicConfig configuration);


    /**
     * Load the settings of the detector.
     *
     * @param configuration Where to load from
     */
    void loadSettings (BasicConfig configuration);


    /**
     * Shutdown the task. Execute some necessary cleanup.
     */
    void shutdown ();


    /**
     * Cancel the task.
     */
    void cancel ();
}
