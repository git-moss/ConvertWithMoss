// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

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
     * Get the folder which contains the multisample source or the file itself.
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
     * @return The descriptions
     */
    List<IVelocityLayer> getSampleMetadata ();


    /**
     * Get the name of the multi sample.
     *
     * @return The name
     */
    String getName ();


    /**
     * Get a description of the multi sample.
     *
     * @return The description
     */
    String getDescription ();


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


    /**
     * Set the description.
     *
     * @param description The description
     */
    void setDescription (String description);


    /**
     * Set the creator (author).
     *
     * @param creator The creator (author) of the multi sample
     */
    void setCreator (String creator);


    /**
     * Set the category.
     *
     * @param category The sound category of the multi-sample
     */
    void setCategory (String category);


    /**
     *
     *
     * @param keywords The keywords of the multi-sample
     */
    void setKeywords (String [] keywords);


    /**
     * Set the sample data.
     *
     * @param sampleMetadata The sample file information
     */
    void setVelocityLayers (List<IVelocityLayer> sampleMetadata);
}
