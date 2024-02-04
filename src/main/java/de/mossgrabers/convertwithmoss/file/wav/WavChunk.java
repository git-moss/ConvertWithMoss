// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.IChunk;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * Base class for a wave chunk.
 *
 * @author Jürgen Moßgraber
 */
public class WavChunk implements IChunk
{
    protected final RiffID    riffId;
    protected final RIFFChunk chunk;


    /**
     * Constructor.
     *
     * @param riffId The RIFF id
     * @param chunk The RIFF chunk which contains the data
     */
    protected WavChunk (final RiffID riffId, final RIFFChunk chunk)
    {
        this.riffId = riffId;
        this.chunk = chunk;
    }


    /**
     * Constructor.
     *
     * @param riffId The RIFF id
     * @param chunk The RIFF chunk which contains the data
     * @param chunkSize The size of the data
     * @throws ParseException Length of data does not match the expected chunk size
     */
    protected WavChunk (final RiffID riffId, final RIFFChunk chunk, final int chunkSize) throws ParseException
    {
        this.riffId = riffId;
        this.chunk = chunk;

        // Check expected length
        if (chunk.getData ().length < chunkSize)
            throw new ParseException (riffId.getName () + " chunk too short. Corrupted file?!");
    }


    /** {@inheritDoc} */
    @Override
    public int getId ()
    {
        return this.riffId.getId ();
    }


    /** {@inheritDoc} */
    @Override
    public byte [] getData ()
    {
        return this.chunk.getData ();
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        return "Date Size: " + this.getData ().length + " Bytes";
    }
}
