// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import java.io.File;
import java.util.function.Consumer;


/**
 * Detects all potential multi-sample source files (or aggregates multiple required ones depending
 * on the source format).
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IDetector
{
    /**
     * Set the notifier for information and error logging.
     *
     * @param notifier The notifier
     */
    void configure (INotifier notifier);


    /**
     * Start the detection.
     *
     * @param folder The folder where to start the detection
     * @param consumer Where to report the found multi-samples
     */
    void detect (File folder, Consumer<IMultisampleSource> consumer);
}
