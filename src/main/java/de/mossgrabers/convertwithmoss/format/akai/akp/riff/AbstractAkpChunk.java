// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import java.util.Optional;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * Base class for the chunks of AKP and AKM files. The first data byte of each chunk contains the
 * version of the chunk structure. Newer versions extend some chunks by a few bytes but keep the
 * position of the existing parameters. The data of a read chunk is kept, therefore writing the
 * chunk in the same version only replaces the known parameters and keeps all undocumented bytes.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractAkpChunk extends AbstractSpecificRIFFChunk
{
    /** The generation which is used for writing chunks which were not read from a file. */
    public static final AkpGeneration DEFAULT_GENERATION = AkpGeneration.GENERATION_3;

    private final int                 minimumSize;
    protected int                     version;
    private byte []                   originalData       = null;


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
        this.originalData = data.clone ();
        this.rawRiffChunk.setData (data.clone ());
    }


    /**
     * Updates the data of the chunk with the current parameters. Needs to be called before the
     * chunk is written. If the chunk was read and is written in the same version, the read data is
     * used as the basis, otherwise the default data of the requested version.
     *
     * @param generation The generation in which to write the chunk, if empty a read chunk keeps its
     *            version and a new chunk uses the default generation
     */
    public void updateData (final Optional<AkpGeneration> generation)
    {
        final int targetVersion;
        if (generation.isPresent ())
            targetVersion = generation.get ().getVersion (this.getId (), this.version);
        else if (this.originalData != null)
            targetVersion = this.version;
        else
            targetVersion = DEFAULT_GENERATION.getVersion (this.getId (), this.version);

        final byte [] data;
        if (this.originalData != null && targetVersion == this.version)
            data = this.originalData.clone ();
        else
        {
            data = new byte [this.getSize (targetVersion)];
            this.writeDefaults (data, targetVersion);
        }
        data[0] = (byte) targetVersion;
        this.writeData (data, targetVersion);
        this.rawRiffChunk.setData (data);
    }


    /**
     * Read the parameters from the chunk data. The size of the data is already checked.
     *
     * @param data The data of the chunk
     */
    protected abstract void readData (final byte [] data);


    /**
     * Write the parameters into the chunk data. The data either contains the read data (if the
     * version is the same) or the default data of the version.
     *
     * @param data The data of the chunk
     * @param targetVersion The version of the chunk structure which is written
     */
    protected abstract void writeData (final byte [] data, final int targetVersion);


    /**
     * Get the size of the chunk data in the given version. Overwrite for chunks which were extended
     * in newer versions.
     *
     * @param targetVersion The version of the chunk structure
     * @return The size in bytes
     */
    protected int getSize (final int targetVersion)
    {
        return this.minimumSize;
    }


    /**
     * Set the undocumented bytes of a new chunk to their default values. The parameters are written
     * afterwards with writeData. Overwrite if a chunk contains undocumented bytes which are not 0.
     *
     * @param data The data of the chunk which is filled with zeros
     * @param targetVersion The version of the chunk structure which is written
     */
    protected void writeDefaults (final byte [] data, final int targetVersion)
    {
        // Intentionally empty
    }


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


    /**
     * Set a byte value. Works for signed and unsigned values.
     *
     * @param data The data
     * @param offset The offset of the byte
     * @param value The value in the range of [-128..255]
     */
    protected static void setByte (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) value;
    }


    /**
     * Set a boolean byte value.
     *
     * @param data The data
     * @param offset The offset of the byte
     * @param value True to set 1 otherwise 0
     */
    protected static void setBoolean (final byte [] data, final int offset, final boolean value)
    {
        data[offset] = (byte) (value ? 1 : 0);
    }
}
