// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import de.mossgrabers.sampleconverter.core.model.IFilter;
import de.mossgrabers.sampleconverter.core.model.IVelocityLayer;

import java.io.File;
import java.util.List;
import java.util.Optional;


/**
 * A detected source for a multi-sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IMultisampleSource
{
    /**
     * Get the folder which contains the multi-sample source or the file itself.
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
     * Get the description of the layers which belong to the multi-sample.
     *
     * @return The descriptions
     */
    List<IVelocityLayer> getLayers ();


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
     * Set the name of the multi sample.
     *
     * @param name The name
     */
    void setName (String name);


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


    /**
     * Get the name to display for the mapping process.
     *
     * @return The name, usually the source file
     */
    String getMappingName ();


    /**
     * Checks all samples in all layers for filter settings. Only if all samples contain the same
     * filter settings a result is returned.
     *
     * @return The filter if a global filter setting is found
     */
    Optional<IFilter> getGlobalFilter ();


    /**
     * Sets a filter on all samples in all layers.
     *
     * @param filter The filter to set
     */
    void setGlobalFilter (IFilter filter);
}
