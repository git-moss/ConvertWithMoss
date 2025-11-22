// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.creator;

import java.io.File;
import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.ICoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;


/**
 * Creates and stores a multi-sample file.
 *
 * @param <T> The type of the settings
 *
 * @author Jürgen Moßgraber
 */
public interface ICreator<T extends ICoreTaskSettings> extends ICoreTask<T>
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
    void createPresetLibrary (File destinationFolder, List<IMultisampleSource> multisampleSources, String libraryName) throws IOException;


    /**
     * Combines several multi-samples input files and stores them into one library file.
     *
     * @param destinationFolder Where to store the created file
     * @param performanceSource The performance source from which to create
     * @throws IOException Could not store the file
     */
    void createPerformance (File destinationFolder, IPerformanceSource performanceSource) throws IOException;


    /**
     * Combines several performance input files and stores them into one library file.
     *
     * @param destinationFolder Where to store the created file
     * @param performanceSources The performance sources from which to create
     * @param libraryName The name to use for the library file
     * @throws IOException Could not store the file
     */
    void createPerformanceLibrary (File destinationFolder, List<IPerformanceSource> performanceSources, String libraryName) throws IOException;


    /**
     * Check if the creator supports to combine several multi-samples into one file.
     *
     * @return Returns true if the creator can combine several files
     */
    boolean supportsPresetLibraries ();


    /**
     * Check if the creator supports to convert a performance source into a performance.
     *
     * @return Returns true if supported
     */
    boolean supportsPerformances ();


    /**
     * Check if the creator supports to combine several performances into one file.
     *
     * @return Returns true if the creator can combine several files
     */
    boolean supportsPerformanceLibraries ();


    /**
     * Clears the cancelled state. Call before each run.
     */
    void clearCancelled ();
}
