// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import de.mossgrabers.convertwithmoss.exception.NoDataInChunkException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * RIFF Chunks form the building blocks of a RIFF file.
 *
 * @author Jürgen Moßgraber
 */
public class RIFFChunk implements IChunk
{
    private int                             id;
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
     * Constructor.
     *
     * @param type The type of the chunk
     * @param id The chunk ID
     * @param size The size of the chunk
     * @param propGroup
     */
    public RIFFChunk (final int type, final int id, final long size, final RIFFChunk propGroup)
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
        {
            if (chunk.id == id)
                array.add (chunk);
        }
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
        this.data = data;
    }


    /** {@inheritDoc} */
    @Override
    public byte [] getData ()
    {
        if (this.data == null)
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
    public int fourBytesAsInt (final int offset)
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
    public int twoBytesAsInt (final int offset)
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
    public int byteAsUnsignedInt (final int offset)
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
    public int byteAsSignedInt (final int offset)
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
        final StringBuilder sb = new StringBuilder ();

        int counter = offset;
        final byte [] d = this.getData ();
        while (counter < d.length)
        {
            if (d[counter] == 0)
                return sb.toString ();
            sb.append (Character.valueOf ((char) d[counter]));
            counter++;
        }

        // No null terminator detected, return the default value
        return defaultValue;
    }


    /**
     * Convert an integer into 4 bytes. MSB is first byte.
     *
     * @param offset The offset into the data array
     * @param value The integer to convert
     */
    public void intAsFourBytes (final int offset, final int value)
    {
        final byte [] d = this.getData ();
        d[offset + 0] = (byte) value;
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
    public void intAsTwoBytes (final int offset, final int value)
    {
        final byte [] d = this.getData ();
        d[offset + 0] = (byte) value;
        d[offset + 1] = (byte) (value >> 8);
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
}
