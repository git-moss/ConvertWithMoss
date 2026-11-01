// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * Base class for the chunks of AKP and AKM files. The first data byte of each chunk contains the
 * version of the chunk structure. Newer versions extend some chunks by a few bytes but keep the
 * position of the existing parameters.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractAkpChunk extends AbstractSpecificRIFFChunk
{
    private final int minimumSize;
    protected int     version;


    /**
     * Constructor.
     *
     * @param riffID The RIFF ID
     * @param minimumSize The size of the oldest version of the chunk
     * @param version The default version of the chunk structure
     */
    protected AbstractAkpChunk (final RiffChunkId riffID, final int minimumSize, final int version)
    {
        super (riffID, minimumSize);

        this.minimumSize = minimumSize;
        this.version = version;
    }


    /**
     * Get the version of the chunk structure.
     *
     * @return The version
     */
    public int getVersion ()
    {
        return this.version;
    }


    /**
     * Reads the data from a chunk.
     *
     * @param chunk The chunk to read
     * @throws ParseException Could not read the data
     */
    public void read (final RawRIFFChunk chunk) throws ParseException
    {
        final byte [] data = chunk.getData ();
        if (data.length < this.minimumSize)
            throw new ParseException ("Unexpected size of '" + RiffChunkId.toASCII (chunk.getId ().getFourCC ()) + "' chunk: " + data.length);

        this.version = Byte.toUnsignedInt (data[0]);
        this.readData (data);
    }


    /**
     * Read the parameters from the chunk data. The size of the data is already checked.
     *
     * @param data The data of the chunk
     */
    protected abstract void readData (final byte [] data);


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        // Not used
        return "";
    }


    /**
     * Get an unsigned byte value.
     *
     * @param data The data
     * @param offset The offset of the byte
     * @return The value in the range of [0..255]
     */
    protected static int getUnsigned (final byte [] data, final int offset)
    {
        return Byte.toUnsignedInt (data[offset]);
    }


    /**
     * Get a signed byte value.
     *
     * @param data The data
     * @param offset The offset of the byte
     * @return The value in the range of [-128..127]
     */
    protected static int getSigned (final byte [] data, final int offset)
    {
        return data[offset];
    }


    /**
     * Get a boolean byte value.
     *
     * @param data The data
     * @param offset The offset of the byte
     * @return True if the value is not 0
     */
    protected static boolean getBoolean (final byte [] data, final int offset)
    {
        return data[offset] != 0;
    }
}
