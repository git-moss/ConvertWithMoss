// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;

import de.mossgrabers.convertwithmoss.file.CSVRenameFile;


/**
 * Several settings for the detection process.
 *
 * @author Jürgen Moßgraber
 */
public class DetectSettings
{
    /** The folder where to start the detection process. */
    public File          sourceFolder;
    /** Where to write the result to. */
    public File          outputFolder;
    /** If renaming is required. */
    public CSVRenameFile csvRenameFile;
    /** The name to use in case that a library will be created. */
    public String        libraryName;
    /** True, if all files should be returned at once. */
    public boolean       wantsMultipleFiles;
    /** True, if the source folder structure should be replicated in the output folder. */
    public boolean       createFolderStructure;

    // Parameters for Processing

    /** Enable overall processing. */
    public boolean       enableProcessing;
    /** Enable normalizing samples. */
    public boolean       enableNormalize;
    /** Enable making all samples mono. */
    public boolean       enableMakeMono;
    /** Enable to trim sample start and end. */
    public boolean       enableTrimSample;
    /** The maximum number of samples to limit to. */
    public int           maxNumberOfSamples;
    /** The bit depth to reduce to. */
    public int           reduceBitDepth  = -1;
    /** The frequency to reduce to. */
    public int           reduceFrequency = -1;


    /**
     * Check if processing is enabled and at least one processing option is enabled as well.
     * 
     * @return True if processing is necessary
     */
    public boolean needsProcessing ()
    {
        return this.enableProcessing && (this.maxNumberOfSamples > 0 || this.enableMakeMono || this.enableTrimSample || this.reduceBitDepth > 0 || this.reduceFrequency > 0 || this.enableNormalize);
    }
}
