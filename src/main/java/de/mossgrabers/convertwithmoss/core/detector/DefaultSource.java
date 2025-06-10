// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import de.mossgrabers.convertwithmoss.core.ISource;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultMetadata;


/**
 * Holds the data of a source.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultSource implements ISource
{
    protected String          name;
    protected final IMetadata metadata = new DefaultMetadata ();


    /**
     * Constructor. Values must be set by setters!
     */
    public DefaultSource ()
    {
        this (null);
    }


    /**
     * Constructor.
     *
     * @param name The name of the source
     */
    public DefaultSource (final String name)
    {
        this.name = name;
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
    public IMetadata getMetadata ()
    {
        return this.metadata;
    }
}
