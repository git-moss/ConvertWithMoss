// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Several settings for the detection process.
 *
 * @author Jürgen Moßgraber
 */
public class DetectSettings
{
    /** The folder where to start the detection process. */
    public File                          sourceFolder;
    /**
     * If not empty, only these files are converted instead of searching the source folder, which is
     * then only the reference for all sub-path calculations.
     */
    public final List<File>              sourceFiles        = new ArrayList<> ();
    /**
     * If not empty, only the listed sources of the listed files are converted. A source is
     * addressed by its index inside of its file, which is deterministic and therefore identical in
     * the contents run which created the selection. Its files narrow down the files to read even
     * further, since all other files contain nothing which was selected.
     */
    public final Map<File, Set<Integer>> selectedSources    = new HashMap<> ();
    /** Where to write the result to. */
    public File                          outputFolder;
    /** The name to use in case that a library will be created. */
    public String                        libraryName;
    /** True, if all files should be returned at once. */
    public boolean                       wantsMultipleFiles;
    /** True, if the source folder structure should be replicated in the output folder. */
    public boolean                       createFolderStructure;
    /** True, if an analysis run should log the details of every found source. */
    public boolean                       logAnalysisDetails;

    // Parameters for Processing

    /** Enable overall processing. */
    public boolean                       enableProcessing;
    /** Enable normalizing samples. */
    public boolean                       enableNormalize;
    /** Enable making all samples mono. */
    public boolean                       enableMakeMono;
    /** Enable to trim sample start and end. */
    public boolean                       enableTrimSample;
    /** The maximum number of samples to limit to. */
    public int                           maxNumberOfSamples;
    /** The bit depth to reduce to. 0 is off. */
    public int                           reduceBitDepth     = 0;
    /** The frequency to reduce to. 0 is off. */
    public int                           reduceFrequency    = 0;
    /** Does up-sampling as well. */
    public boolean                       alwaysResample     = false;
    /** The fixed loop cross-fade. 0 is off. */
    public int                           loopCrossfades     = 0;
    /** Snap forward loop boundaries to the nearest zero-crossing to avoid loop clicks. */
    public boolean                       snapLoopsToZero    = false;
    /** Transpose playback by this number of semitones by moving the sample root keys. 0 is off. */
    public int                           transposeSemitones = 0;


    /**
     * Check if processing is enabled and at least one processing option is enabled as well.
     *
     * @return True if processing is necessary
     */
    public boolean needsProcessing ()
    {
        return this.enableProcessing && (this.maxNumberOfSamples > 0 || this.enableMakeMono || this.enableTrimSample || this.reduceBitDepth > 0 || this.reduceFrequency > 0 || this.enableNormalize || this.loopCrossfades > 0 || this.snapLoopsToZero || this.transposeSemitones != 0);
    }
}
