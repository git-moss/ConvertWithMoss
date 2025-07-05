// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;


/**
 * Base interface for creators and detectors.
 *
 * @param <T> The type of the settings
 * 
 * @author Jürgen Moßgraber
 */
public interface ICoreTask<T extends ICoreTaskSettings>
{
    /**
     * Get the descriptive name of the task.
     *
     * @return The name
     */
    String getName ();


    /**
     * Get the prefix to use for the metadata properties tags.
     *
     * @return The short name
     */
    String getPrefix ();


    /**
     * Get the interface to the settings.
     *
     * @return The settings
     */
    T getSettings ();


    /**
     * Shutdown the task. Execute necessary cleanup.
     */
    void shutdown ();


    /**
     * Cancel the task.
     */
    void cancel ();
}
