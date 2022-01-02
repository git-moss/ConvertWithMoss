// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui;

/**
 * Interface to metadata configuration parameters.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IMetadataConfig
{
    /**
     * Should the folder name be favored for detecting tags instead of the file name?
     *
     * @return True if folder name should be used
     */
    boolean isPreferFolderName ();


    /**
     * Get the default creator name to use.
     *
     * @return The default creator
     */
    String getCreatorName ();


    /**
     * Get several tags which should be set as creator if found in the file name.
     *
     * @return The tags
     */
    String [] getCreatorTags ();
}
