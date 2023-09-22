// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultMetadata;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Holds the data of a multi-sample source.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultMultisampleSource implements IMultisampleSource
{
    private File            sourceFile;
    private String []       subPath;
    private String          name;
    private final String    mappingName;
    private List<IGroup>    groups   = Collections.emptyList ();
    private final IMetadata metadata = new DefaultMetadata ();


    /**
     * Constructor. Values must be set by setters!
     */
    public DefaultMultisampleSource ()
    {
        this (null, null, null, null);
    }


    /**
     * Constructor.
     *
     * @param sourceFile The folder (contains the multi-sample source or the file itself)
     * @param subPath The names of the sub folders which contain the samples
     * @param name The name of the multi-sample
     * @param mappingName The name to display for the mapping process.
     */
    public DefaultMultisampleSource (final File sourceFile, final String [] subPath, final String name, final String mappingName)
    {
        this.sourceFile = sourceFile;
        this.subPath = subPath;
        this.name = name;
        this.mappingName = mappingName;
    }


    /** {@inheritDoc} */
    @Override
    public IMetadata getMetadata ()
    {
        return this.metadata;
    }


    /** {@inheritDoc} */
    @Override
    public File getSourceFile ()
    {
        return this.sourceFile;
    }


    /** {@inheritDoc} */
    @Override
    public void setFolder (final File folder)
    {
        this.sourceFile = folder;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getSubPath ()
    {
        return this.subPath;
    }


    /** {@inheritDoc} */
    @Override
    public void setSubPath (final String [] subFolders)
    {
        this.subPath = subFolders;
    }


    /** {@inheritDoc} */
    @Override
    public List<IGroup> getGroups ()
    {
        return this.groups;
    }


    /** {@inheritDoc} */
    @Override
    public List<IGroup> getNonEmptyGroups (final boolean filterReleaseTriggers)
    {
        final List<IGroup> cleanedGroups = new ArrayList<> ();
        for (final IGroup group: this.groups)
        {
            final List<ISampleMetadata> sampleMetadata = group.getSampleMetadata ();
            if (sampleMetadata.isEmpty ())
                continue;

            if (filterReleaseTriggers)
            {
                // There needs to be at least one sample with a normal attack trigger
                for (final ISampleMetadata sample: sampleMetadata)
                {
                    if (sample.getTrigger () != TriggerType.RELEASE)
                    {
                        cleanedGroups.add (group);
                        break;
                    }
                }
            }
            else
                cleanedGroups.add (group);
        }
        return cleanedGroups;
    }


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return this.name;
    }


    /** {@inheritDoc} */
    @Override
    public void setName (final String name)
    {
        this.name = name;
    }


    /** {@inheritDoc} */
    @Override
    public void setGroups (final List<IGroup> groups)
    {
        this.groups = new ArrayList<> (groups);
    }


    /** {@inheritDoc} */
    @Override
    public String getMappingName ()
    {
        return this.mappingName;
    }


    /** {@inheritDoc} */
    @Override
    public Optional<IFilter> getGlobalFilter ()
    {
        IFilter globalFilter = null;
        for (final IGroup group: this.groups)
        {
            for (final ISampleMetadata sampleMetadata: group.getSampleMetadata ())
            {
                final Optional<IFilter> optFilter = sampleMetadata.getFilter ();
                if (optFilter.isEmpty ())
                    return Optional.empty ();

                final IFilter filter = optFilter.get ();
                if (globalFilter == null)
                    globalFilter = filter;
                else if (!globalFilter.equals (filter))
                    return Optional.empty ();
            }
        }
        return Optional.ofNullable (globalFilter);
    }


    /** {@inheritDoc} */
    @Override
    public void setGlobalFilter (final IFilter filter)
    {
        for (final IGroup group: this.groups)
        {
            for (final ISampleMetadata sampleMetadata: group.getSampleMetadata ())
                sampleMetadata.setFilter (filter);
        }
    }
}
