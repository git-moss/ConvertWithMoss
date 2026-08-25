// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.format.TagDetector;


/**
 * Holds the data of the metadata for a multi-sample source.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultMetadata implements IMetadata
{
    private String             description  = "";
    private String             creator      = "";
    private Date               creationTime = null;
    private String             category     = "";
    private final List<String> keywords     = new ArrayList<> ();


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
    public Date getCreationDateTime ()
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
        return this.keywords.toArray (new String [this.keywords.size ()]);
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
    public void setCreationDateTime (final Date time)
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
    public void setKeywords (final String... keywords)
    {
        this.keywords.clear ();
        Collections.addAll (this.keywords, keywords);
    }


    /** {@inheritDoc} */
    @Override
    public void addKeyword (final String keyword)
    {
        this.keywords.add (keyword);
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
        if (this.creator.isBlank ())
            this.setCreator (TagDetector.detect (parts, config.getCreatorTags (), config.getCreatorName ()));
        if (this.category.isBlank () || TagDetector.CATEGORY_UNKNOWN.equals (this.category))
            this.setCategory (category == null || category.isBlank () ? TagDetector.detectCategory (parts, config.isCategoryFromNamePrefix ()) : category);
        if (this.keywords.isEmpty ())
            this.setKeywords (TagDetector.detectKeywords (parts));
    }
}
