// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.ICoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;


/**
 * Detects all potential multi-sample source files (or aggregates multiple required ones depending
 * on the source format).
 *
 * @author Jürgen Moßgraber
 */
public interface IDetector extends ICoreTask
{
    /**
     * Start the detection.
     *
     * @param folder The folder where to start the detection
     * @param multisampleSourceConsumer Where to report the found multi-samples
     */
    void detect (File folder, Consumer<IMultisampleSource> multisampleSourceConsumer);


    /**
     * Start the detection.
     *
     * @param folder The folder where to start the detection
     * @param multisampleSourceConsumer Where to report the found multi-samples sources
     * @param performanceSourceConsumer Where to report the found performance sources
     * @param detectPerformances If true, performances are detected otherwise presets
     */
    void detect (File folder, Consumer<IMultisampleSource> multisampleSourceConsumer, Consumer<IPerformanceSource> performanceSourceConsumer, boolean detectPerformances);


    /**
     * Validate the source parameters.
     *
     * @return Returns true if all parameters are valid
     */
    boolean validateParameters ();


    /**
     * Check if the detector supports performance sources.
     *
     * @return Returns true if supported
     */
    boolean supportsPerformances ();
}
