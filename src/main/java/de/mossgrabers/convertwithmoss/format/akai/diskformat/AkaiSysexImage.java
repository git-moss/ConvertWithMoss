// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.diskformat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * Provides access to an AKAI data structure stored in a sysex-message. 1 byte is stored in 2 bytes:
 * the first byte contains the lower 4 bits and the second byte the upper bits, shifted left by 4.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiSysexImage extends AbstractAkaiImage
{
    private final ByteArrayInputStream inputStream;


    /**
     * Open a data structure from the content of a sysex-message.
     *
     * @param messageContent The content bytes
     * @throws IOException If file cannot be opened or partitions could not be loaded
     */
    public AkaiSysexImage (final byte [] messageContent) throws IOException
    {
        this.inputStream = new ByteArrayInputStream (messageContent);
    }


    /** {@inheritDoc} */
    @Override
    public void close () throws IOException
    {
        this.inputStream.close ();
    }


    /** {@inheritDoc} */
    @Override
    public int available ()
    {
        return this.inputStream.available () / 2;
    }


    /** {@inheritDoc} */
    @Override
    public String readText () throws IOException
    {
        return this.readText (MAX_TEXT_LENGTH);
    }


    /** {@inheritDoc} */
    @Override
    public String readText (final int length) throws IOException
    {
        final byte [] buffer = this.readNBytes (length);
        IAkaiImage.akaiToAscii (buffer, length);
        return new String (buffer, 0, length).trim ();
    }


    /** {@inheritDoc} */
    @Override
    public byte readInt8 () throws IOException
    {
        return this.readByte ();
    }


    /** {@inheritDoc} */
    @Override
    public void readInt8 (final byte [] data, final int wordCount) throws IOException
    {
        for (int i = 0; i < wordCount; i++)
            data[i] = this.readByte ();
    }


    /** {@inheritDoc} */
    @Override
    public short readInt16 () throws IOException
    {
        final byte [] word = this.readNBytes (2);
        return ByteBuffer.wrap (word).order (ByteOrder.BIG_ENDIAN).getShort ();
    }


    /** {@inheritDoc} */
    @Override
    public void readInt16 (final short [] data, final int wordCount) throws IOException
    {
        for (int i = 0; i < wordCount; i++)
            data[i] = this.readInt16 ();
    }


    /** {@inheritDoc} */
    @Override
    public int readInt32 () throws IOException
    {
        final byte [] word = this.readNBytes (4);
        return ByteBuffer.wrap (word).order (ByteOrder.BIG_ENDIAN).getInt ();
    }


    private byte [] readNBytes (final int numberOfBytes) throws IOException
    {
        final byte [] bytes = new byte [numberOfBytes];
        for (int i = 0; i < numberOfBytes; i++)
            bytes[i] = this.readByte ();
        return bytes;
    }


    private byte readByte () throws IOException
    {
        final byte [] bytes = this.inputStream.readNBytes (2);
        return (byte) combineNibbles (bytes[0], bytes[1]);
    }


    private static int combineNibbles (final byte lowByte, final byte highByte)
    {
        return (highByte & 0x0F) << 4 | lowByte & 0x0F;
    }
}