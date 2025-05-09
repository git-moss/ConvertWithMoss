// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.creator;

import java.io.File;
import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.ICoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;


/**
 * Creates and stores a multi-sample file.
 *
 * @author Jürgen Moßgraber
 */
public interface ICreator extends ICoreTask
{
    /**
     * Create and store a multi-sample file.
     *
     * @param destinationFolder Where to store the created file
     * @param multisampleSource The multi-sample source from which to create
     * @throws IOException Could not store the file
     */
    void createPreset (File destinationFolder, IMultisampleSource multisampleSource) throws IOException;


    /**
     * Combines several multi-samples input files and stores them into one library file.
     *
     * @param destinationFolder Where to store the created file
     * @param multisampleSources The multi-sample sources from which to create
     * @param libraryName The name to use for the library file
     * @throws IOException Could not store the file
     */
    void createLibrary (File destinationFolder, List<IMultisampleSource> multisampleSources, String libraryName) throws IOException;


    /**
     * Check if the creator supports to combine several multi-samples into one file.
     *
     * @return Returns true if the creator can combine several files
     */
    boolean supportsLibraries ();


    /**
     * Check if the creator supports to convert a performance source into a performance.
     *
     * @return Returns true if supported
     */
    boolean supportsPerformances ();


    /**
     * Clears the cancelled state. Call before each run.
     */
    void clearCancelled ();
}
