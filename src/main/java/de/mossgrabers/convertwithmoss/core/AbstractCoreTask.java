// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;


/**
 * Base class for creator and detector classes.
 *
 * @param <T> The type of the settings
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractCoreTask<T extends ICoreTaskSettings> implements ICoreTask<T>
{
    protected final String    name;
    protected final String    prefix;
    protected final INotifier notifier;
    protected final T         settingsConfiguration;


    /**
     * Constructor.
     *
     * @param name The descriptive name of the task
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     * @param settingsConfiguration The configuration of the settings
     */
    protected AbstractCoreTask (final String name, final String prefix, final INotifier notifier, final T settingsConfiguration)
    {
        this.name = name;
        this.prefix = prefix;
        this.notifier = notifier;
        this.settingsConfiguration = settingsConfiguration;
    }


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return this.name;
    }


    /** {@inheritDoc} */
    @Override
    public String getPrefix ()
    {
        return this.prefix;
    }


    /** {@inheritDoc} */
    @Override
    public T getSettings ()
    {
        return this.settingsConfiguration;
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        // Intentionally empty
    }


    protected void notifyProgress ()
    {
        this.notifier.log ("IDS_NOTIFY_PROGRESS");
    }


    protected void notifyNewline ()
    {
        this.notifier.log ("IDS_NOTIFY_LINE_FEED");
    }


    protected void notifyNewline (final int count)
    {
        if (count > 0 && count % 80 == 0)
            this.notifyNewline ();
    }
}
