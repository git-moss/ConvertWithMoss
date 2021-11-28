// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Holds the data of a multi-sample source.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class MultisampleSource implements IMultisampleSource
{
    private final File           folder;
    private final String []      subPath;
    private String               name;
    private final String         mappingName;
    private String               description    = "";
    private String               creator        = "";
    private String               category       = "";
    private String []            keywords       = new String [0];
    private List<IVelocityLayer> sampleMetadata = Collections.emptyList ();


    /**
     * Constructor.
     *
     * @param folder The folder
     * @param subPath The names of the sub folders which contain the samples
     * @param name The name of the multi-sample
     * @param mappingName The name to display for the mapping process.
     */
    public MultisampleSource (final File folder, final String [] subPath, final String name, final String mappingName)
    {
        this.folder = folder;
        this.subPath = subPath;
        this.name = name;
        this.mappingName = mappingName;
    }


    /** {@inheritDoc} */
    @Override
    public File getFolder ()
    {
        return this.folder;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getSubPath ()
    {
        return this.subPath;
    }


    /** {@inheritDoc} */
    @Override
    public List<IVelocityLayer> getLayers ()
    {
        return this.sampleMetadata;
    }


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return this.name;
    }


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
    public void setName (final String name)
    {
        this.name = name;
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
    public void setVelocityLayers (final List<IVelocityLayer> sampleMetadata)
    {
        this.sampleMetadata = new ArrayList<> (sampleMetadata);
    }


    /** {@inheritDoc} */
    @Override
    public String getMappingName ()
    {
        return this.mappingName;
    }
}
