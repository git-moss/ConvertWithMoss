// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

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
    protected T               settingsConfiguration;
    protected String []       fileEndings;


    /**
     * Constructor.
     *
     * @param name The descriptive name of the task
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     * @param settingsConfiguration The configuration of the settings
     * @param fileEndings The file ending(s) which is written by this creator
     */
    protected AbstractCoreTask (final String name, final String prefix, final INotifier notifier, final T settingsConfiguration, final String... fileEndings)
    {
        this.name = name;
        this.prefix = prefix;
        this.notifier = notifier;
        this.settingsConfiguration = settingsConfiguration;

        this.fileEndings = fileEndings;
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
    public void setSettings (final T settings)
    {
        this.settingsConfiguration = settings;
    }


    /** {@inheritDoc} */
    @Override
    public Set<String> getFileEndings ()
    {
        final Set<String> endings = new TreeSet<> ();
        this.configureFileEndings (false);
        endings.addAll (Arrays.asList (this.fileEndings));
        this.configureFileEndings (true);
        endings.addAll (Arrays.asList (this.fileEndings));
        return endings;
    }


    /**
     * Overwrite in case that file endings need to be set dynamically.
     *
     * @param detectPerformances If true, performances are detected otherwise presets
     */
    protected void configureFileEndings (final boolean detectPerformances)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        // Intentionally empty
    }
}
