// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.iff;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.exception.NoDataInChunkException;


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
     * Gets the data.
     *
     * @return The data array. The array will not be cloned for performance reasons and is expected
     *         to be modified from wrapper classes!
     */
    public byte [] getData ()
    {
        if (this.data == null)
            throw new NoDataInChunkException ("Chunk contains no data.");
        return this.data;
    }


    /**
     * Get the local chunk or group type ID.
     *
     * @return The ID
     */
    public String getId ()
    {
        return this.id;
    }
}
