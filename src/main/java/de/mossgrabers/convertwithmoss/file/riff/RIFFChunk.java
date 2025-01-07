// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.exception.NoDataInChunkException;
import de.mossgrabers.convertwithmoss.file.IChunk;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * RIFF Chunks form the building blocks of a RIFF file.
 *
 * @author Jürgen Moßgraber
 */
public class RIFFChunk implements IChunk
{
    private final RiffID                    riffID;
    private final int                       id;
    private int                             type;
    private long                            size;
    private byte []                         data;

    private final Map<RIFFChunk, RIFFChunk> propertyChunks   = new HashMap<> ();
    private final List<RIFFChunk>           collectionChunks = new ArrayList<> ();

    private String                          parserMessage;
    private boolean                         tooLarge         = false;


    /**
     * Constructor.
     *
     * @param type The type of the chunk
     * @param id The chunk ID
     */
    public RIFFChunk (final int type, final int id)
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
    public RIFFChunk (final int type, final int id, final long size)
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
    protected RIFFChunk (final RiffID riffID, final byte [] data, final int size)
    {
        this (0, riffID.getId (), size);

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
    public RIFFChunk (final int type, final int id, final long size, final RIFFChunk propGroup)
    {
        this.id = id;
        this.riffID = RiffID.fromId (id);
        this.type = type;
        this.size = size;

        if (propGroup == null)
            return;
        this.propertyChunks.putAll (propGroup.propertyChunks);
        this.collectionChunks.addAll (propGroup.collectionChunks);
    }


    /**
     * Get the RIFF ID.
     *
     * @return The RIFF ID
     */
    public RiffID getRiffID ()
    {
        return this.riffID;
    }


    /** {@inheritDoc} */
    @Override
    public int getId ()
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
     * @return Size of chunk
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
    public void putPropertyChunk (final RIFFChunk chunk)
    {
        this.propertyChunks.put (chunk, chunk);
    }


    /**
     * Get a property chunk for the given ID.
     *
     * @param id The ID
     * @return The property chunk
     */
    public RIFFChunk getPropertyChunk (final int id)
    {
        final RIFFChunk chunk = new RIFFChunk (this.type, id);
        return this.propertyChunks.get (chunk);
    }


    /**
     * Get all property chunks.
     *
     * @return The chunks
     */
    public Set<RIFFChunk> propertyChunks ()
    {
        return this.propertyChunks.keySet ();
    }


    /**
     * Add a collection chunk.
     *
     * @param chunk The chunk to add
     */
    public void addCollectionChunk (final RIFFChunk chunk)
    {
        this.collectionChunks.add (chunk);
    }


    /**
     * Get all collection chunks for an ID.
     *
     * @param id The ID
     * @return The chunks
     */
    public List<RIFFChunk> getCollectionChunks (final int id)
    {
        final List<RIFFChunk> array = new ArrayList<> ();
        for (final RIFFChunk chunk: this.collectionChunks)
            if (chunk.id == id)
                array.add (chunk);
        return array;
    }


    /**
     * Iterator to all collection chunks.
     *
     * @return The iterator
     */
    public Iterator<RIFFChunk> collectionChunks ()
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
        if (this.id != RiffID.LIST_ID.getId () && data.length < this.size)
            throw new IllegalArgumentException (Functions.getMessage ("IDS_WAV_ERR_IN_CHUNK", this.getRiffID ().getName (), RiffID.toASCII (this.id), Long.toString (this.size), Integer.toString (data.length)));

        this.data = data;
    }


    /** {@inheritDoc} */
    @Override
    public byte [] getData ()
    {
        if (this.id != RiffID.LIST_ID.getId () && this.data == null)
        {
            if (this.tooLarge)
                throw new NoDataInChunkException ("Chunk contains no data since it was too large to be loaded.");
            throw new NoDataInChunkException ("Chunk contains no data.");
        }
        return this.data;
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
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.id, true);

        final byte [] currentData = this.getData ();
        final boolean needsPadByte = currentData.length % 2 == 1;
        final int dataSize = needsPadByte ? currentData.length + 1 : currentData.length;
        StreamUtils.writeUnsigned32 (out, dataSize, false);
        out.write (currentData);

        if (needsPadByte)
            out.write (0);
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object another)
    {
        if (another instanceof final RIFFChunk that)
            return that.id == this.id && that.type == this.type;
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return this.id;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return super.toString () + "{" + RiffID.toASCII (this.getType ()) + "," + RiffID.toASCII (this.getId ()) + "}";
    }


    /**
     * Marks the chunk as too large to be load.
     */
    public void markTooLarge ()
    {
        this.tooLarge = true;
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        return "Unknown Data";
    }
}
