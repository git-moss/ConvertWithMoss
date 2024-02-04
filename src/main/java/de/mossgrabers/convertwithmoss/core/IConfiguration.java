// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

/**
 * Interface to the configuration settings.
 *
 * @author Jürgen Moßgraber
 */
public interface IConfiguration
{
    /**
     * Only analyze or also convert?
     *
     * @return True if only the analyze part should be run
     */
    boolean isOnlyAnalyse ();


    /**
     * Get the source folder from which to read samples.
     *
     * @return The source folder
     */
    String getSourceFolder ();


    /**
     * Get the output folder where to store the created multi-samples.
     *
     * @return The output folder
     */
    String getOutputFolder ();


    /**
     * Get the group patterns.
     *
     * @return The patterns
     */
    String getGroupPatterns ();


    /**
     * Get if the groups are numbered in ascending order.
     *
     * @return True if the groups are numbered in ascending order
     */
    boolean isVelLayersOrderAscending ();


    /**
     * Get the left channel detection patterns for mono splits.
     *
     * @return The patterns
     */
    String getMonoSplitsLeftChannelPattern ();


    /**
     * Get the name of the creator to write into the multi-samples.
     *
     * @return The creator
     */
    String getCreator ();


    /**
     * Recreate the source folder structure in the output folder.
     *
     * @return If true, recreate the folder structure
     */
    boolean isCreateStructure ();


    /**
     * The number of notes to crossfade between two samples.
     *
     * @return The number of notes
     */
    int getCrossfadeNotes ();


    /**
     * A static text to remove from the end of multi-sample names.
     *
     * @return A comma separated list of static texts
     */
    String getPostfixText ();


    /**
     * Get the creator tags.
     *
     * @return The tags, never null
     */
    String [] getCreatorTags ();


    /**
     * Prefer to use the folder name which contains the samples instead of inferring it from the
     * sample names.
     *
     * @return Prefer the sample folder if true
     */
    boolean isPreferFolderName ();
}
