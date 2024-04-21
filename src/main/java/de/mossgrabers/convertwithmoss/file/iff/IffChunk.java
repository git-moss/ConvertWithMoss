// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.iff;

import java.io.ByteArrayInputStream;
import java.io.InputStream;


/**
 * Data of an IFF chunk.
 *
 * @author Jürgen Moßgraber
 */
public class IffChunk
{
    private final String id;
    final byte []        data;


    /**
     * Constructor.
     * 
     * @param id The local chunk or group type ID
     * @param data The data stored in the chunk
     */
    public IffChunk (final String id, final byte [] data)
    {
        this.id = id;
        this.data = data;
    }


    /**
     * Returns an input stream to read the data.
     * 
     * @return The input stream
     */
    public InputStream streamData ()
    {
        return new ByteArrayInputStream (this.data);
    }


    /**
     * Get the local chunk or group type ID.
     * 
     * @return The ID
     */
    public String getID ()
    {
        return this.id;
    }
}
