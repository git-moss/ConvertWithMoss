// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import de.mossgrabers.sampleconverter.util.KeyMapping;

import java.io.File;
import java.util.List;


/**
 * A detected source for a multi-sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IMultisampleSource
{
    /**
     * Get the folder which contains the multisample source.
     *
     * @return The folder
     */
    File getFolder ();


    /**
     * Get the sub-folders which contain the samples up to the source path
     *
     * @return The sub folders
     */
    String [] getSubPath ();


    /**
     * Get the description of the samples which belong to the multi-sample.
     *
     * @return The descriptions in an ordered map
     */
    List<List<ISampleMetadata>> getSampleMetadata ();


    /**
     * Get the key mapping, which is how the samples are mapped to the keys.
     *
     * @return The key mapping
     */
    KeyMapping getKeyMapping ();


    /**
     * Get the name of the multi sample.
     *
     * @return The name
     */
    String getName ();


    /**
     * Get the creator (author) of the multi-sample.
     *
     * @return The creator
     */
    String getCreator ();


    /**
     * Get the sound category of the multi-sample.
     *
     * @return The category
     */
    String getCategory ();


    /**
     * Get the keywords of the multi-sample.
     *
     * @return The keywords
     */
    String [] getKeywords ();
}
