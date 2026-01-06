// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.IOException;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.IChunk;
import de.mossgrabers.tools.ui.Functions;


/**
 * Base class for a specific RIFF chunk which encapsulates a raw RIFF chunk.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractSpecificRIFFChunk implements IChunk
{
    protected final RawRIFFChunk rawRiffChunk;


    /**
     * Constructor.
     *
     * @param riffID The RIFF ID, the given chunk is checked for this id
     * @param chunk The raw RIFF chunk which contains the data
     * @throws ParseException The raw chunk is not of the specific type or the length of data does
     *             not match the expected chunk size
     */
    protected AbstractSpecificRIFFChunk (final RiffID riffID, final RawRIFFChunk chunk) throws ParseException
    {
        if (riffID != chunk.getRiffID ())
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_WRONG_CHUNK_ID", riffID.toString (), chunk.getRiffID ().toString ()));
        this.rawRiffChunk = chunk;
    }


    /**
     * Constructor.
     *
     * @param riffID The RIFF ID
     * @param size The expected size of the chunk
     */
    protected AbstractSpecificRIFFChunk (final RiffID riffID, final int size)
    {
        this (riffID, new byte [size]);
    }


    /**
     * Constructor.
     *
     * @param riffID The RIFF ID
     * @param data The data of the chunk
     */
    protected AbstractSpecificRIFFChunk (final RiffID riffID, final byte [] data)
    {
        this.rawRiffChunk = new RawRIFFChunk (riffID, data, data.length);
    }


    /** {@inheritDoc} */
    @Override
    public long getDataSize ()
    {
        return this.rawRiffChunk.getDataSize ();
    }


    /** {@inheritDoc} */
    @Override
    public int getId ()
    {
        return this.rawRiffChunk.getId ();
    }


    /**
     * Get the data.
     *
     * @return The data
     */
    public byte [] getData ()
    {
        return this.rawRiffChunk.getData ();
    }


    /** {@inheritDoc} */
    @Override
    public void writeData (final OutputStream out) throws IOException
    {
        this.rawRiffChunk.writeData (out);
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.rawRiffChunk.write (out);
    }
}
