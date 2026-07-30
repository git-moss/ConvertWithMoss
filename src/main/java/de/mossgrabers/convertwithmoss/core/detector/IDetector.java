// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.ICoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;


/**
 * Detects all potential multi-sample source files (or aggregates multiple required ones depending
 * on the source format).
 *
 * @param <T> The type of the settings
 *
 * @author Jürgen Moßgraber
 */
public interface IDetector<T extends ICoreTaskSettings> extends ICoreTask<T>, Runnable
{
    /**
     * Start the detection.
     *
     * @param sourceFolder The folder where to start the detection
     * @param sourceFiles If not empty, only these files are processed instead of searching the
     *            source folder, which is then only the reference for all sub-path calculations
     * @param multisampleSourceConsumer Where to report the found multi-samples sources
     * @param performanceSourceConsumer Where to report the found performance sources
     * @param detectPerformances If true, performances are detected otherwise presets
     */
    void detect (File sourceFolder, List<File> sourceFiles, Consumer<IMultisampleSource> multisampleSourceConsumer, Consumer<IPerformanceSource> performanceSourceConsumer, boolean detectPerformances);


    /**
     * Read one single source of a file, which was found by a previous detection run. In contrast to
     * {@link #detect(File, List, Consumer, Consumer, boolean)}, which runs in the background and
     * reports everything it finds, this reads only the one requested source and does so on the
     * calling thread. Must not be called while a detection run is in progress.
     *
     * @param sourceFolder The folder which is the reference for all sub-path calculations
     * @param sourceFile The file which contains the source
     * @param indexInFile The index of the source inside of its file
     * @param detectPerformances If true, a performance is read otherwise a preset
     * @return The source, empty if the file contains no source with that index
     */
    Optional<IMultisampleSource> readSource (File sourceFolder, File sourceFile, int indexInFile, boolean detectPerformances);


    /**
     * Check if the detector supports performance sources.
     *
     * @return Returns true if supported
     */
    boolean supportsPerformances ();
}
