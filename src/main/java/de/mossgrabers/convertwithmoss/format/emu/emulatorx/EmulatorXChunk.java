// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulatorx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * One chunk of an Emulator X file. A chunk is a 4 character tag, a 32 bit big-endian size and the
 * payload of that size. A chunk with the tag 'LIST' starts its payload with a 4 character list type
 * followed by its child chunks. The chunks which the table of contents points to additionally carry
 * a 16 bit index in front of their payload, which is not part of the payload seen here.
 *
 * This class keeps a reference to the buffer it was parsed from instead of copying the payload.
 *
 * @author Jürgen Moßgraber
 */
public class EmulatorXChunk
{
    private static final int HEADER_SIZE = 8;

    private final byte []    data;
    private final String     tag;
    private final int        offset;
    private final int        size;


    /**
     * Constructor.
     *
     * @param data The buffer which contains the chunk
     * @param tag The tag of the chunk
     * @param offset The offset of the payload of the chunk in the buffer
     * @param size The size of the payload
     */
    private EmulatorXChunk (final byte [] data, final String tag, final int offset, final int size)
    {
        this.data = data;
        this.tag = tag;
        this.offset = offset;
        this.size = size;
    }


    /**
     * Parse the chunks in a section of a buffer. Parsing stops at the first malformed chunk, which
     * makes the result of a truncated file the part which could be read.
     *
     * @param data The buffer to parse
     * @param offset The offset to start at
     * @param length The number of bytes to parse
     * @return The chunks
     */
    public static List<EmulatorXChunk> parse (final byte [] data, final int offset, final int length)
    {
        final List<EmulatorXChunk> chunks = new ArrayList<> ();
        final int end = Math.min (offset + length, data.length);
        int position = offset;
        while (position + HEADER_SIZE <= end)
        {
            final int size = (int) EmulatorXConstants.getU32BE (data, position + 4);
            if (size < 0 || position + HEADER_SIZE + size > end)
                break;
            chunks.add (new EmulatorXChunk (data, new String (data, position, 4, StandardCharsets.US_ASCII), position + HEADER_SIZE, size));
            position += HEADER_SIZE + size;
        }
        return chunks;
    }


    /**
     * Wrap a section of a buffer into a chunk. This is used for the chunks which the table of
     * contents points at, whose payload starts behind their 16 bit index instead of behind their
     * size.
     *
     * @param data The buffer which contains the chunk
     * @param tag The tag of the chunk
     * @param offset The offset of the payload in the buffer
     * @param size The size of the payload
     * @return The chunk
     */
    public static EmulatorXChunk wrap (final byte [] data, final String tag, final int offset, final int size)
    {
        return new EmulatorXChunk (data, tag, offset, size);
    }


    /**
     * Get the tag of the chunk.
     *
     * @return The tag
     */
    public String getTag ()
    {
        return this.tag;
    }


    /**
     * Get the buffer which contains the chunk.
     *
     * @return The buffer
     */
    public byte [] getData ()
    {
        return this.data;
    }


    /**
     * Get the offset of the payload of the chunk in the buffer.
     *
     * @return The offset
     */
    public int getOffset ()
    {
        return this.offset;
    }


    /**
     * Get the size of the payload of the chunk.
     *
     * @return The size
     */
    public int getSize ()
    {
        return this.size;
    }


    /**
     * Test whether this chunk has the given tag.
     *
     * @param expectedTag The tag to test
     * @return True if the tags match
     */
    public boolean is (final String expectedTag)
    {
        return this.tag.equals (expectedTag);
    }


    /**
     * Test whether this chunk is a list of the given type.
     *
     * @param listType The list type to test
     * @return True if this is a matching list
     */
    public boolean isList (final String listType)
    {
        return this.is (EmulatorXConstants.LIST_TAG) && this.size >= 4 && EmulatorXConstants.hasTag (this.data, this.offset, listType);
    }


    /**
     * Get the child chunks of this chunk. For a list the 4 character list type is skipped.
     *
     * @return The child chunks
     */
    public List<EmulatorXChunk> getChildren ()
    {
        final int skip = this.is (EmulatorXConstants.LIST_TAG) ? 4 : 0;
        return parse (this.data, this.offset + skip, this.size - skip);
    }


    /**
     * Get the first child chunk with the given tag.
     *
     * @param childTag The tag to look for
     * @return The chunk or null if there is none
     */
    public EmulatorXChunk getChild (final String childTag)
    {
        for (final EmulatorXChunk child: this.getChildren ())
            if (child.is (childTag))
                return child;
        return null;
    }


    /**
     * Get the first child chunk which is a list of the given type.
     *
     * @param listType The list type to look for
     * @return The chunk or null if there is none
     */
    public EmulatorXChunk getList (final String listType)
    {
        for (final EmulatorXChunk child: this.getChildren ())
            if (child.isList (listType))
                return child;
        return null;
    }


    /**
     * Read an unsigned byte from the payload.
     *
     * @param position The position in the payload
     * @return The value or 0 if the payload is too short
     */
    public int getByte (final int position)
    {
        return position < 0 || position >= this.size ? 0 : this.data[this.offset + position] & 0xFF;
    }


    /**
     * Read a signed byte from the payload.
     *
     * @param position The position in the payload
     * @return The value or 0 if the payload is too short
     */
    public int getSignedByte (final int position)
    {
        return position < 0 || position >= this.size ? 0 : this.data[this.offset + position];
    }


    /**
     * Read an unsigned 16 bit big-endian value from the payload.
     *
     * @param position The position in the payload
     * @return The value or 0 if the payload is too short
     */
    public int getU16 (final int position)
    {
        return position < 0 || position + 2 > this.size ? 0 : EmulatorXConstants.getU16BE (this.data, this.offset + position);
    }


    /**
     * Read a 32 bit big-endian float from the payload.
     *
     * @param position The position in the payload
     * @return The value or 0 if the payload is too short
     */
    public float getFloat (final int position)
    {
        return position < 0 || position + 4 > this.size ? 0 : EmulatorXConstants.getFloatBE (this.data, this.offset + position);
    }


    /**
     * Write a chunk with the given tag and payload.
     *
     * @param out Where to write to
     * @param tag The tag of the chunk
     * @param payload The payload of the chunk
     * @throws IOException Could not write
     */
    public static void write (final OutputStream out, final String tag, final byte [] payload) throws IOException
    {
        out.write (tag.getBytes (StandardCharsets.US_ASCII));
        final byte [] size = new byte [4];
        EmulatorXConstants.putU32BE (size, 0, payload.length);
        out.write (size);
        out.write (payload);
    }


    /**
     * Create a list chunk from the given children.
     *
     * @param listType The 4 character list type
     * @param children The already assembled child chunks
     * @return The complete list chunk including its header
     * @throws IOException Could not assemble the chunk
     */
    public static byte [] createList (final String listType, final List<byte []> children) throws IOException
    {
        final ByteArrayOutputStream payload = new ByteArrayOutputStream ();
        payload.write (listType.getBytes (StandardCharsets.US_ASCII));
        for (final byte [] child: children)
            payload.write (child);
        return create (EmulatorXConstants.LIST_TAG, payload.toByteArray ());
    }


    /**
     * Create a chunk from the given tag and payload.
     *
     * @param tag The tag of the chunk
     * @param payload The payload of the chunk
     * @return The complete chunk including its header
     * @throws IOException Could not assemble the chunk
     */
    public static byte [] create (final String tag, final byte [] payload) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream (payload.length + HEADER_SIZE);
        write (out, tag, payload);
        return out.toByteArray ();
    }
}
