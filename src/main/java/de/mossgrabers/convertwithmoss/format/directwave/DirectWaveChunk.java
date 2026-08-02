// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.directwave;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * One block of the DWP block stream. Each block consists of a 12 byte header (tag, payload length,
 * reserved - all unsigned 32 bit little-endian) followed by the payload. Both the top level of a
 * DWP file (after the fixed preamble) and the payload of a sample container block are such block
 * streams.
 *
 * @author Jürgen Moßgraber
 */
public class DirectWaveChunk
{
    private final int     tag;
    private final byte [] payload;


    /**
     * Constructor.
     *
     * @param tag The tag (ID) of the chunk
     * @param payload The payload bytes
     */
    public DirectWaveChunk (final int tag, final byte [] payload)
    {
        this.tag = tag;
        this.payload = payload;
    }


    /**
     * Get the tag of the chunk.
     *
     * @return The tag
     */
    public int getTag ()
    {
        return this.tag;
    }


    /**
     * Get the payload of the chunk.
     *
     * @return The payload bytes
     */
    public byte [] getPayload ()
    {
        return this.payload;
    }


    /**
     * Get the payload interpreted as a text.
     *
     * @return The text
     */
    public String getPayloadAsText ()
    {
        return new String (this.payload, StandardCharsets.ISO_8859_1);
    }


    /**
     * Parse all chunks of a block stream.
     *
     * @param data The data to parse
     * @param offset The offset at which the block stream starts
     * @return The parsed chunks or null if the data is not a valid block stream which accounts for
     *         all bytes of the data
     */
    public static Optional<List<DirectWaveChunk>> parseAll (final byte [] data, final int offset)
    {
        final List<DirectWaveChunk> chunks = new ArrayList<> ();
        int cursor = offset;
        while (cursor + 12 <= data.length)
        {
            final int tag = readIntLE (data, cursor);
            final int length = readIntLE (data, cursor + 4);
            final int payloadStart = cursor + 12;
            if (length < 0 || payloadStart + length > data.length)
                return Optional.empty ();
            final byte [] payload = new byte [length];
            System.arraycopy (data, payloadStart, payload, 0, length);
            chunks.add (new DirectWaveChunk (tag, payload));
            cursor = payloadStart + length;
        }
        return cursor == data.length ? Optional.of (chunks) : Optional.empty ();
    }


    /**
     * Write one chunk (12 byte header plus payload) to the output stream.
     *
     * @param out The stream to write to
     * @param tag The tag of the chunk
     * @param payload The payload of the chunk
     * @throws IOException Could not write
     */
    public static void writeChunk (final OutputStream out, final int tag, final byte [] payload) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, tag, false);
        StreamUtils.writeUnsigned32 (out, payload.length, false);
        StreamUtils.writeUnsigned32 (out, 0, false);
        out.write (payload);
    }


    /**
     * Write one chunk with a text payload to the output stream.
     *
     * @param out The stream to write to
     * @param tag The tag of the chunk
     * @param text The text payload of the chunk
     * @throws IOException Could not write
     */
    public static void writeChunk (final OutputStream out, final int tag, final String text) throws IOException
    {
        writeChunk (out, tag, text.getBytes (StandardCharsets.ISO_8859_1));
    }


    /**
     * Write one chunk with a zeroed payload of the given length to the output stream.
     *
     * @param out The stream to write to
     * @param tag The tag of the chunk
     * @param length The length of the zeroed payload
     * @throws IOException Could not write
     */
    public static void writeZeroedChunk (final OutputStream out, final int tag, final int length) throws IOException
    {
        writeChunk (out, tag, new byte [length]);
    }


    /**
     * Read a signed 32 bit little-endian integer from a byte array.
     *
     * @param data The data to read from
     * @param offset The offset at which to read
     * @return The value
     */
    public static int readIntLE (final byte [] data, final int offset)
    {
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24;
    }


    /**
     * Write a signed 32 bit little-endian integer into a byte array.
     *
     * @param data The data to write to
     * @param offset The offset at which to write
     * @param value The value to write
     */
    public static void writeIntLE (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
        data[offset + 2] = (byte) (value >> 16 & 0xFF);
        data[offset + 3] = (byte) (value >> 24 & 0xFF);
    }


    /**
     * Read an unsigned 16 bit little-endian integer from a byte array.
     *
     * @param data The data to read from
     * @param offset The offset at which to read
     * @return The value
     */
    public static int readShortLE (final byte [] data, final int offset)
    {
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
    }


    /**
     * Write an unsigned 16 bit little-endian integer into a byte array.
     *
     * @param data The data to write to
     * @param offset The offset at which to write
     * @param value The value to write
     */
    public static void writeShortLE (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    /**
     * Write a 32 bit little-endian float into a byte array.
     *
     * @param data The data to write to
     * @param offset The offset at which to write
     * @param value The value to write
     */
    public static void writeFloatLE (final byte [] data, final int offset, final float value)
    {
        writeIntLE (data, offset, Float.floatToIntBits (value));
    }


    /**
     * Read a 32 bit little-endian float from a byte array.
     *
     * @param data The data to read from
     * @param offset The offset at which to read
     * @return The value
     */
    public static float readFloatLE (final byte [] data, final int offset)
    {
        return Float.intBitsToFloat (readIntLE (data, offset));
    }


    /**
     * Serialize a list of chunks into a byte array.
     *
     * @param chunks The chunks to serialize
     * @return The serialized bytes
     * @throws IOException Could not serialize
     */
    public static byte [] serialize (final List<DirectWaveChunk> chunks) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        for (final DirectWaveChunk chunk: chunks)
            writeChunk (out, chunk.getTag (), chunk.getPayload ());
        return out.toByteArray ();
    }
}
