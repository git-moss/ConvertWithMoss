// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;


/**
 * Several descriptive fields for a multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public interface IMetadata
{
    /**
     * Get a description of the multi-sample.
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
     * @param creator The creator (author) of the multi-sample
     */
    void setCreator (String creator);


    /**
     * Set the category.
     *
     * @param category The sound category of the multi-sample
     */
    void setCategory (String category);


    /**
     * Set the keywords.
     *
     * @param keywords The keywords of the multi-sample
     */
    void setKeywords (String [] keywords);


    /**
     * Detect metadata (creator, category, keywords) from the given text parts.
     *
     * @param config Some configuration settings
     * @param parts The text parts
     */
    void detectMetadata (IMetadataConfig config, String [] parts);


    /**
     * Detect metadata (creator, category, keywords) from the given text parts.
     *
     * @param config Some configuration settings
     * @param parts The text parts
     * @param category If the category is not null, it is assigned and not detected
     */
    void detectMetadata (IMetadataConfig config, String [] parts, String category);
}
