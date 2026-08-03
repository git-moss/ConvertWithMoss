// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.tools.ui.BasicConfig;


/**
 * A history which manages paths with a conversion type.
 *
 * @author Jürgen Moßgraber
 */
public class PathHistory
{
    private static final int   NUMBER_OF_DIRECTORIES = 20;

    private final String       propertyPrefix;
    private final List<String> paths                 = new ArrayList<> ();
    private final List<String> types                 = new ArrayList<> ();


    /**
     * Constructor.
     * 
     * @param propertyPrefix The prefix to use for reading/writing the history
     */
    public PathHistory (final String propertyPrefix)
    {
        this.propertyPrefix = propertyPrefix;
    }


    /**
     * Get all paths.
     * 
     * @return The paths
     */
    public List<String> getPaths ()
    {
        return Collections.unmodifiableList (this.paths);
    }


    /**
     * Loads the history from the given configuration.
     * 
     * @param config The configuration from which to load the history
     */
    public void load (final BasicConfig config)
    {
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            final String path = config.getProperty (this.propertyPrefix + i);
            if (path != null && !path.isBlank () && !this.paths.contains (path))
            {
                this.paths.add (path);
                final String type = config.getProperty (this.propertyPrefix + "_FORMAT" + i);
                this.types.add (type == null || type.isBlank () ? "" : type);
            }
        }
    }


    /**
     * Saves the history to the given configuration.
     * 
     * @param config The configuration to which to save the history
     */
    public void save (final BasicConfig config)
    {
        final int size = this.paths.size ();
        for (int i = 0; i < NUMBER_OF_DIRECTORIES; i++)
        {
            config.setProperty (this.propertyPrefix + i, i < size ? this.paths.get (i) : "");
            config.setProperty (this.propertyPrefix + "_FORMAT" + i, i < size ? this.types.get (i) : "");
        }
    }


    /**
     * Updates (or adds if not present) the given path with the given type. The path is moved
     * to/inserted at the first position.
     * 
     * @param path The path to add/update
     * @param type The conversion type (full description label)
     */
    public void update (final String path, final String type)
    {
        final int index = this.paths.indexOf (path);
        if (index >= 0)
        {
            this.paths.remove (index);
            this.types.remove (index);
        }

        this.paths.add (0, path);
        this.types.add (0, type);
    }


    /**
     * Get the type for the given path.
     * 
     * @param path The path for which to get the type
     * @return The type if one is stored
     */
    public Optional<String> getType (final String path)
    {
        final int index = this.paths.indexOf (path);
        if (index < 0)
            return Optional.empty ();
        final String type = this.types.get (index);
        return type == null || type.isBlank () ? Optional.empty () : Optional.of (type);
    }
}
