// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;


/**
 * A detected source.
 *
 * @author Jürgen Moßgraber
 */
public interface ISource
{
    /**
     * Get the metadata description for the source.
     *
     * @return The metadata
     */
    IMetadata getMetadata ();


    /**
     * Get the name of the source.
     *
     * @return The name
     */
    String getName ();


    /**
     * Set the name of the source.
     *
     * @param name The name
     */
    void setName (String name);
}
