// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


/**
 * A RIFF primitives input stream lets an application read primitive data types in the Microsoft
 * Resource Interchange File Format (RIFF) format from an underlying input stream.
 *
 * @author Jürgen Moßgraber
 */
public class RIFFPrimitivesInputStream extends FilterInputStream
{
    private long position;
    private long mark;


    /**
     * Constructor.
     *
     * @param in The input stream, which provides the data.
     */
    public RIFFPrimitivesInputStream (final InputStream in)
    {
        super (in);
    }


    /**
     * Read 1 byte from the input stream and interpret them as an 8 Bit unsigned UBYTE value.
     *
     * @return The read byte
     * @throws IOException Error reading from the file
     */
    public int readUBYTE () throws IOException
    {
        final int b0 = this.in.read ();

        if (b0 == -1)
            throw new EOFException ();

        this.position += 1;
        return b0 & 0xff;
    }


    /**
     * Read 2 bytes from the input stream and interpret them as a 16 Bit signed short value.
     *
     * @return The read word
     * @throws IOException Error reading from the file
     */
    public short readWORD () throws IOException
    {
        final int b0 = this.in.read ();
        final int b1 = this.in.read ();

        if (b1 == -1)
            throw new EOFException ();

        this.position += 2;
        return (short) (b0 & 0xff | (b1 & 0xff) << 8);
    }


    /**
     * Read 2 bytes from the input stream and interpret them as a 16 Bit unsigned integer value.
     *
     * @return The read word
     * @throws IOException Error reading from the file
     */
    public int readUWORD () throws IOException
    {
        return this.readWORD () & 0xffff;
    }


    /**
     * Read 4 bytes from the input stream and interpret them as a 32 Bit signed integer value.
     *
     * @return The read 32 bit value
     * @throws IOException Error reading from the file
     */
    public int readDWORD () throws IOException
    {
        final int b0 = this.in.read ();
        final int b1 = this.in.read ();
        final int b2 = this.in.read ();
        final int b3 = this.in.read ();

        if (b3 == -1)
            throw new EOFException ();

        this.position += 4;

        return (b0 & 0xff) + ((b1 & 0xff) << 8) + ((b2 & 0xff) << 16) + ((b3 & 0xff) << 24);
    }


    /**
     * Read 4 Bytes from the input Stream and interpret them as an unsigned Integer value of type
     * ULONG.
     *
     * @return The long value
     * @throws IOException Error reading from the file
     */
    public long readUDWORD () throws IOException
    {
        final int value = this.readDWORD ();
        return value & 0xffffffffL;
    }


    /**
     * Read 4 bytes from the input stream and interpret them as a four byte character code.
     *
     * Cited from Referenced "AVI RIFF File Reference": "A FOURCC (four-character code) is a 32-bit
     * unsigned integer created by concatenating four ASCII characters. For example, the FOURCC
     * 'abcd' is represented on a Little-Endian system as 0x64636261. FOURCCs can contain space
     * characters, so ' abc' is a valid FOURCC. The AVI file format uses FOURCC codes to identify
     * stream types, data chunks, index entries, and other information."
     *
     * @return The value
     * @throws IOException Error reading from the file
     */
    public int readFourCC () throws IOException
    {
        final int b3 = this.in.read ();
        final int b2 = this.in.read ();
        final int b1 = this.in.read ();
        final int b0 = this.in.read ();

        if (b0 == -1)
            throw new EOFException ();

        this.position += 4;
        return (b0 & 0xff) + ((b1 & 0xff) << 8) + ((b2 & 0xff) << 16) + ((b3 & 0xff) << 24);
    }


    /**
     * Read 4 bytes from the input stream and interpret them as a four byte character code.
     *
     * Cited from Referenced "AVI RIFF File Reference": "A FOURCC (four-character code) is a 32-bit
     * unsigned integer created by concatenating four ASCII characters. For example, the FOURCC
     * 'abcd' is represented on a Little-Endian system as 0x64636261. FOURCCs can contain space
     * characters, so ' abc' is a valid FOURCC. The AVI file format uses FOURCC codes to identify
     * stream types, data chunks, index entries, and other information."
     *
     * @return The read value
     * @throws IOException Error reading from the file
     */
    public String readFourCCString () throws IOException
    {
        final byte [] buf = new byte [4];
        this.readFully (buf, 0, 4);
        return new String (buf, StandardCharsets.US_ASCII);
    }


    /**
     * Align to an even byte position in the input stream. This will skip one byte in the stream if
     * the current read position is not even.
     *
     * @throws IOException Error reading from the file
     */
    public void align () throws IOException
    {
        if (this.position % 2 == 0)
            return;

        if (this.in.skip (1) != 1)
            throw new EOFException ();
        this.position++;
    }


    /**
     * Get the current read position within the file.
     *
     * @return The position
     */
    public long getPosition ()
    {
        return this.position;
    }


    /** {@inheritDoc} */
    @Override
    public int read () throws IOException
    {
        final int data = this.in.read ();
        if (data != -1)
            this.position++;
        return data;
    }


    /**
     * Reads a sequence of bytes.
     *
     * @param b Where to store the read bytes
     * @param offset The offset into the array
     * @param length The number of bytes to read
     * @throws IOException Could not read the requested number of bytes
     */
    public void readFully (final byte [] b, final int offset, final int length) throws IOException
    {
        if (this.read (b, offset, length) != length)
            throw new EOFException ();
    }


    /** {@inheritDoc} */
    @Override
    public int read (final byte [] b, final int offset, final int length) throws IOException
    {
        int count = 0;
        while (count < length)
        {
            final int result = this.in.read (b, offset + count, length - count);
            if (result == -1)
                return -1;
            count += result;
        }
        this.position += count;
        return count;
    }


    /** {@inheritDoc} */
    @Override
    public synchronized void mark (final int readlimit)
    {
        this.in.mark (readlimit);
        this.mark = this.position;
    }


    /** {@inheritDoc} */
    @Override
    public synchronized void reset () throws IOException
    {
        this.in.reset ();
        this.position = this.mark;
    }


    /** {@inheritDoc} */
    @Override
    public long skip (final long n) throws IOException
    {
        final long skipped = this.in.skip (n);
        this.position += skipped;
        return skipped;
    }


    /**
     * Skips over and discards n bytes of data from this input stream.
     *
     * @param n the number of bytes to be skipped.
     * @throws IOException If this input stream reaches the end before skipping all the bytes.
     */
    public void skipFully (final long n) throws IOException
    {
        if (n == 0)
            return;

        long total = 0;
        long cur = 0;

        while (total < n && (cur = this.in.skip (n - total)) > 0)
            total += cur;
        if (cur == 0)
            throw new EOFException ();
        this.position += total;
    }
}