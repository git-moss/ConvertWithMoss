// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.IOException;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.IChunk;
import de.mossgrabers.convertwithmoss.file.iff.IffChunk;


/**
 * Base class for an AIFF chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AiffChunk implements IChunk
{
    protected final IffChunk chunk;


    /**
     * Constructor.
     *
     * @param chunk The IFF chunk which contains the data
     */
    protected AiffChunk (final IffChunk chunk)
    {
        this.chunk = chunk;
    }


    /**
     * Constructor.
     *
     * @param chunk The IFF chunk which contains the data
     * @param chunkSize The size of the data
     * @throws ParseException Length of data does not match the expected chunk size
     */
    protected AiffChunk (final IffChunk chunk, final int chunkSize) throws ParseException
    {
        this.chunk = chunk;

        // Check expected length
        if (chunk.getData ().length < chunkSize)
            throw new ParseException (chunk.getId () + " chunk too short. Corrupted file?!");
    }


    /**
     * Get the name (the ID in text form) of the chunk.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.chunk.getId ();
    }


    /** {@inheritDoc} */
    @Override
    public int getId ()
    {
        return IChunk.toId (this.chunk.getId ());
    }


    /** {@inheritDoc} */
    @Override
    public long getDataSize ()
    {
        return this.chunk.getDataSize ();
    }


    /**
     * Get the data.
     *
     * @return The data
     */
    public byte [] getData ()
    {
        return this.chunk.getData ();
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        return "Date Size: " + this.getDataSize () + " Bytes";
    }


    /** {@inheritDoc} */
    @Override
    public void writeData (final OutputStream out) throws IOException
    {
        // Not used
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        // Not used
    }
}
