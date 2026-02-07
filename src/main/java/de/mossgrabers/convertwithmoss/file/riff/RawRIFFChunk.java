// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.exception.NoDataInChunkException;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * RIFF Chunks form the building blocks of a RIFF file.
 *
 * @author Jürgen Moßgraber
 */
public class RawRIFFChunk implements IRiffChunk
{
    private final RiffChunkId                     id;
    private int                                   type;
    private long                                  size;
    private byte []                               data;
    private File                                  dataFile;

    private final Map<RawRIFFChunk, RawRIFFChunk> propertyChunks   = new HashMap<> ();
    private final List<RawRIFFChunk>              collectionChunks = new ArrayList<> ();

    private String                                parserMessage;
    private boolean                               tooLarge         = false;


    /**
     * Constructor.
     *
     * @param type The type of the chunk
     * @param id The chunk ID
     */
    public RawRIFFChunk (final int type, final RiffChunkId id)
    {
        this (type, id, -1);
    }


    /**
     * Constructor.
     *
     * @param type The type of the chunk
     * @param id The chunk ID
     * @param size The size of the chunk
     */
    public RawRIFFChunk (final int type, final RiffChunkId id, final long size)
    {
        this (type, id, size, null);
    }


    /**
     * Constructor. Use for sub-class wrappers
     *
     * @param riffID The RIFF ID
     * @param data The data of the chunk
     * @param size The expected size of the chunk
     */
    protected RawRIFFChunk (final RiffChunkId riffID, final byte [] data, final int size)
    {
        this (0, riffID, size);

        this.setData (data);
    }


    /**
     * Constructor.
     *
     * @param type The type of the chunk
     * @param id The chunk ID
     * @param size The size of the chunk
     * @param propGroup The property group chunk
     */
    public RawRIFFChunk (final int type, final RiffChunkId id, final long size, final RawRIFFChunk propGroup)
    {
        this.id = id;
        this.type = type;
        this.size = size;

        if (propGroup == null)
            return;
        this.propertyChunks.putAll (propGroup.propertyChunks);
        this.collectionChunks.addAll (propGroup.collectionChunks);
    }


    /** {@inheritDoc} */
    @Override
    public RiffChunkId getId ()
    {
        return this.id;
    }


    /**
     * Get the type of the chunk.
     *
     * @return Type of chunk
     */
    public int getType ()
    {
        return this.type;
    }


    /**
     * Get the size of the chunk.
     *
     * @return The size
     */
    public long getSize ()
    {
        return this.size;
    }


    /**
     * Add a property chunk.
     *
     * @param chunk The chunk
     */
    public void putPropertyChunk (final RawRIFFChunk chunk)
    {
        this.propertyChunks.put (chunk, chunk);
    }


    /**
     * Get a property chunk for the given ID.
     *
     * @param id The ID
     * @return The property chunk
     */
    public RawRIFFChunk getPropertyChunk (final RiffChunkId id)
    {
        final RawRIFFChunk chunk = new RawRIFFChunk (this.type, id);
        return this.propertyChunks.get (chunk);
    }


    /**
     * Get all property chunks.
     *
     * @return The chunks
     */
    public Set<RawRIFFChunk> propertyChunks ()
    {
        return this.propertyChunks.keySet ();
    }


    /**
     * Add a collection chunk.
     *
     * @param chunk The chunk to add
     */
    public void addCollectionChunk (final RawRIFFChunk chunk)
    {
        this.collectionChunks.add (chunk);
    }


    /**
     * Get all collection chunks for an ID.
     *
     * @param id The ID
     * @return The chunks
     */
    public List<RawRIFFChunk> getCollectionChunks (final RiffChunkId id)
    {
        final List<RawRIFFChunk> array = new ArrayList<> ();
        for (final RawRIFFChunk chunk: this.collectionChunks)
            if (chunk.getId ().getFourCC () == id.getFourCC ())
                array.add (chunk);
        return array;
    }


    /**
     * Iterator to all collection chunks.
     *
     * @return The iterator
     */
    public Iterator<RawRIFFChunk> collectionChunks ()
    {
        return this.collectionChunks.iterator ();
    }


    /**
     * Sets the data.
     *
     * Note: The array will not be cloned for performance reasons.
     *
     * @param data The data to set
     */
    public void setData (final byte [] data)
    {
        // Check expected length
        if (this.id.getFourCC () != CommonRiffChunkId.LIST_ID.getFourCC () && data.length < this.size)
            throw new IllegalArgumentException (Functions.getMessage ("IDS_WAV_ERR_IN_CHUNK", this.id.getDescription (), RiffChunkId.toASCII (this.id.getFourCC ()), Long.toString (this.size), Integer.toString (data.length)));

        this.data = data;
    }


    /**
     * Sets the data via a file in case it is larger than 2GB.
     *
     * @param largeFile A temporary
     */
    public void setData (final File largeFile)
    {
        this.dataFile = largeFile;

    }


    /** {@inheritDoc} */
    @Override
    public long getDataSize ()
    {
        this.checkValidity ();
        return this.data == null ? this.dataFile.length () : this.data.length;
    }


    /**
     * Get the data from the array. Only use this for small chunks with fixed data size!
     *
     * @return The data
     */
    public byte [] getData ()
    {
        this.checkValidity ();
        return this.data;
    }


    private void checkValidity ()
    {
        if (this.id.getFourCC () != CommonRiffChunkId.LIST_ID.getFourCC () && this.data == null && this.dataFile == null)
        {
            if (this.tooLarge)
                throw new NoDataInChunkException ("Chunk contains no data since it was too large to be loaded.");
            throw new NoDataInChunkException ("Chunk contains no data.");
        }
    }


    /**
     * Convert 4 bytes to an integer. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @return The integer value
     */
    public long getFourBytesAsLong (final int offset)
    {
        final byte [] d = this.getData ();
        return Byte.toUnsignedLong (d[offset + 3]) << 24 | Byte.toUnsignedLong (d[offset + 2]) << 16 | Byte.toUnsignedLong (d[offset + 1]) << 8 | Byte.toUnsignedLong (d[offset + 0]);
    }


    /**
     * Convert 4 bytes to an integer. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @return The integer value
     */
    public int getFourBytesAsInt (final int offset)
    {
        final byte [] d = this.getData ();
        return Byte.toUnsignedInt (d[offset + 3]) << 24 | Byte.toUnsignedInt (d[offset + 2]) << 16 | Byte.toUnsignedInt (d[offset + 1]) << 8 | Byte.toUnsignedInt (d[offset + 0]);
    }


    /**
     * Convert 2 bytes to an integer MSB is first byte.
     *
     * @param offset The offset into the data array
     * @return The integer value
     */
    public int getTwoBytesAsInt (final int offset)
    {
        final byte [] d = this.getData ();
        return Byte.toUnsignedInt (d[offset + 1]) << 8 | Byte.toUnsignedInt (d[offset]);
    }


    /**
     * Convert one byte to an integer.
     *
     * @param offset The offset into the data array
     * @return The integer value
     */
    public int getByteAsUnsignedInt (final int offset)
    {
        final byte [] d = this.getData ();
        return Byte.toUnsignedInt (d[offset]);
    }


    /**
     * Convert one byte to an integer.
     *
     * @param offset The offset into the data array
     * @return The integer value
     */
    public int getByteAsSignedInt (final int offset)
    {
        final byte [] d = this.getData ();
        return d[offset];
    }


    /**
     * Interprets the data of the chunk as an ASCII string.
     *
     * @return The string
     */
    public String getASCIIString ()
    {
        return new String (this.getData (), StandardCharsets.US_ASCII);
    }


    /**
     * Read a null terminated string from the data chunk.
     *
     * @param offset The offset into the data array
     * @param defaultValue The value to return if no string is found
     * @return The string
     */
    public String getNullTerminatedString (final int offset, final String defaultValue)
    {
        return this.getNullTerminatedString (offset, -1, defaultValue);
    }


    /**
     * Read a null terminated string from the data chunk.
     *
     * @param offset The offset into the data array
     * @param maxLength Reads up to this number of bytes; the null termination is optional if all
     *            bytes are filled
     * @param defaultValue The value to return if no string is found
     * @return The string
     */
    public String getNullTerminatedString (final int offset, final int maxLength, final String defaultValue)
    {
        final StringBuilder sb = new StringBuilder ();

        int counter = offset;
        final byte [] d = this.getData ();
        while (counter < d.length)
        {
            if (d[counter] == 0)
                return sb.toString ();
            sb.append (Character.valueOf ((char) d[counter]));
            counter++;
            if (maxLength > 0 && counter - offset == maxLength)
                return sb.toString ();
        }

        // No null terminator detected, return the default value
        return defaultValue;
    }


    /**
     * Write a null terminated string in the data chunk.
     *
     * @param offset The offset into the data array
     * @param maxLength Writes up to this number of bytes, if the text is short 0s are written
     * @param text The text to write
     */
    public void setNullTerminatedString (final int offset, final int maxLength, final String text)
    {
        final byte [] d = this.getData ();
        final int textLength = text.length ();
        for (int i = 0; i < maxLength; i++)
            d[offset + i] = i < textLength ? (byte) text.charAt (i) : 0;
    }


    /**
     * Convert a long into 4 bytes. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @param value The long to convert
     */
    public void setLongAsFourBytes (final int offset, final long value)
    {
        final byte [] d = this.getData ();
        d[offset] = (byte) value;
        d[offset + 1] = (byte) (value >> 8);
        d[offset + 2] = (byte) (value >> 16);
        d[offset + 3] = (byte) (value >> 24);
    }


    /**
     * Convert an integer into 4 bytes. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @param value The integer to convert
     */
    public void setIntAsFourBytes (final int offset, final int value)
    {
        final byte [] d = this.getData ();
        d[offset] = (byte) value;
        d[offset + 1] = (byte) (value >> 8);
        d[offset + 2] = (byte) (value >> 16);
        d[offset + 3] = (byte) (value >> 24);
    }


    /**
     * Convert an integer into 2 bytes. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @param value The integer to convert
     */
    public void setIntAsTwoBytes (final int offset, final int value)
    {
        final byte [] d = this.getData ();
        d[offset] = (byte) value;
        d[offset + 1] = (byte) (value >> 8);
    }


    /**
     * Convert an unsigned integer into 1 byte.
     *
     * @param offset The offset into the data array
     * @param value The integer to convert
     */
    public void setUnsignedIntAsByte (final int offset, final int value)
    {
        final byte [] d = this.getData ();
        d[offset] = (byte) (value & 0xFF);
    }


    /**
     * Convert a signed integer into 1 byte.
     *
     * @param offset The offset into the data array
     * @param value The integer to convert
     */
    public void setSignedIntAsByte (final int offset, final int value)
    {
        final byte [] d = this.getData ();
        d[offset] = (byte) value;
    }


    /**
     * Set if the parser runs into a problem but can continue parsing.
     *
     * @param message The notification message to store
     */
    public void setParserMessage (final String message)
    {
        this.parserMessage = message;
    }


    /**
     * Get the notification message, if any.
     *
     * @return The message or null if not set
     */
    public String getParserMessage ()
    {
        return this.parserMessage;
    }


    /** {@inheritDoc} */
    @Override
    public void writeData (final OutputStream out) throws IOException
    {
        if (this.usesDataFile ())
            Files.copy (this.dataFile.toPath (), out);
        else
            out.write (this.getData ());
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.id.getFourCC (), true);

        final long length = this.getDataSize ();
        final boolean needsPadByte = length % 2 == 1;
        final long dataSize = needsPadByte ? length + 1 : length;
        StreamUtils.writeUnsigned32 (out, dataSize, false);

        this.writeData (out);

        if (needsPadByte)
            out.write (0);
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object another)
    {
        if (another instanceof final RawRIFFChunk that)
            return that.getId ().getFourCC () == this.id.getFourCC () && that.type == this.type;
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return this.id.getFourCC ();
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return super.toString () + "{" + RiffChunkId.toASCII (this.getType ()) + "," + RiffChunkId.toASCII (this.id.getFourCC ()) + "}";
    }


    /**
     * Marks the chunk as too large to be load.
     */
    public void markTooLarge ()
    {
        this.tooLarge = true;
    }


    /**
     * Returns true if a data file is used (instead of a simple array).
     *
     * @return True if a data file is used
     */
    public boolean usesDataFile ()
    {
        return this.dataFile != null;
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        return "Unknown Data";
    }
}
