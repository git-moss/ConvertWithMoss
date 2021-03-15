// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.wav;

import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.riff.IChunk;
import de.mossgrabers.sampleconverter.file.riff.RIFFChunk;
import de.mossgrabers.sampleconverter.file.riff.RiffID;


/**
 * Base class for a wave chunk.
 *
 * @author J&uuml;rgen Mo&szlig;graber
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


    /**
     * Convert 4 bytes to an integer. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @return The integer value
     */
    protected int fourBytesAsInt (final int offset)
    {
        final byte [] data = this.getData ();
        return Byte.toUnsignedInt (data[offset + 3]) << 24 | Byte.toUnsignedInt (data[offset + 2]) << 16 | Byte.toUnsignedInt (data[offset + 1]) << 8 | Byte.toUnsignedInt (data[offset + 0]);
    }


    /**
     * Convert 2 bytes to an integer MSB is first byte.
     *
     * @param offset The offset into the data array
     * @return The integer value
     */
    protected int twoBytesAsInt (final int offset)
    {
        final byte [] data = this.getData ();
        return Byte.toUnsignedInt (data[offset + 1]) << 8 | Byte.toUnsignedInt (data[offset + 0]);
    }


    /**
     * Convert an integer into 4 bytes. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @param value The integer to convert
     */
    protected void intAsFourBytes (final int offset, final int value)
    {
        final byte [] data = this.getData ();
        data[offset + 0] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        data[offset + 3] = (byte) (value >> 24);
    }


    /**
     * Convert an integer into 2 bytes. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @param value The integer to convert
     */
    protected void intAsTwoBytes (final int offset, final int value)
    {
        final byte [] data = this.getData ();
        data[offset + 0] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
    }
}
