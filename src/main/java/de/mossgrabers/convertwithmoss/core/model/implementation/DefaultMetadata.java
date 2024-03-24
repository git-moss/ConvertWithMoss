// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.util.Date;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;


/**
 * Holds the data of the metadata for a multi-sample source.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultMetadata implements IMetadata
{
    private String    description  = "";
    private String    creator      = "";
    private Date      creationTime = new Date ();
    private String    category     = "";
    private String [] keywords     = new String [0];


    /** {@inheritDoc} */
    @Override
    public String getDescription ()
    {
        return this.description;
    }


    /** {@inheritDoc} */
    @Override
    public String getCreator ()
    {
        return this.creator;
    }


    /** {@inheritDoc} */
    @Override
    public Date getCreationTime ()
    {
        return this.creationTime;
    }


    /** {@inheritDoc} */
    @Override
    public String getCategory ()
    {
        return this.category;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getKeywords ()
    {
        return this.keywords;
    }


    /** {@inheritDoc} */
    @Override
    public void setDescription (final String description)
    {
        this.description = description;
    }


    /** {@inheritDoc} */
    @Override
    public void setCreator (final String creator)
    {
        if (creator != null)
            this.creator = creator;
    }


    /** {@inheritDoc} */
    @Override
    public void setCreationTime (final Date time)
    {
        if (time != null)
            this.creationTime = time;
    }


    /** {@inheritDoc} */
    @Override
    public void setCategory (final String category)
    {
        this.category = category;
    }


    /** {@inheritDoc} */
    @Override
    public void setKeywords (final String [] keywords)
    {
        this.keywords = keywords;
    }


    /** {@inheritDoc} */
    @Override
    public void detectMetadata (final IMetadataConfig config, final String [] parts)
    {
        this.detectMetadata (config, parts, null);
    }


    /** {@inheritDoc} */
    @Override
    public void detectMetadata (final IMetadataConfig config, final String [] parts, final String category)
    {
        this.setCreator (TagDetector.detect (parts, config.getCreatorTags (), config.getCreatorName ()));
        this.setCategory (category != null ? category : TagDetector.detectCategory (parts));
        this.setKeywords (TagDetector.detectKeywords (parts));
    }
}
