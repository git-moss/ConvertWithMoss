package de.mossgrabers.convertwithmoss.file;

import de.mossgrabers.tools.ui.Functions;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;


/**
 * Helper class for dealing reading from streams and data inputs.
 *
 * @author Jürgen Moßgraber
 */
public class StreamUtils
{
    /**
     * Helper class.
     */
    private StreamUtils ()
    {
        // Intentionally empty
    }


    /**
     * Reads and converts 2 bytes to an unsigned integer with least significant bytes first.
     *
     * @param in The input stream
     * @param isBigEndian True if bytes are stored big-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readUnsigned16 (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final int ch1 = in.read ();
        final int ch2 = in.read ();
        if ((ch1 | ch2) < 0)
            throw new EOFException ();
        if (isBigEndian)
            return (ch1 << 8) + ch2;
        return (ch2 << 8) + ch1;
    }


    /**
     * Reads and converts 2 bytes to an unsigned integer with least significant bytes first.
     *
     * @param fileAccess The random access file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readUnsigned16 (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        final int ch1 = fileAccess.read ();
        final int ch2 = fileAccess.read ();
        if ((ch1 | ch2) < 0)
            throw new EOFException ();
        if (isBigEndian)
            return (ch1 << 8) + ch2;
        return (ch2 << 8) + ch1;
    }


    /**
     * Writes an integer as 2 bytes.
     *
     * @param out The output stream
     * @param value The value to write
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian (least
     *            significant bytes first)
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static void writeUnsigned16 (final OutputStream out, final int value, final boolean isBigEndian) throws IOException
    {
        if (isBigEndian)
        {
            out.write (value >> 8 & 0xFF);
            out.write (value & 0xFF);
        }
        else
        {
            out.write (value & 0xFF);
            out.write (value >> 8 & 0xFF);
        }
    }


    /**
     * Reads and converts 4 bytes to an unsigned integer.
     *
     * @param in The input stream
     * @param isBigEndian True if bytes are stored big-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readUnsigned32 (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final int ch1 = in.read ();
        final int ch2 = in.read ();
        final int ch3 = in.read ();
        final int ch4 = in.read ();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException ();
        if (isBigEndian)
            return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4;
        return (ch4 << 24) + (ch3 << 16) + (ch2 << 8) + ch1;
    }


    /**
     * Writes an integer as 4 bytes.
     *
     * @param out The output stream
     * @param value The value to write
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian (least
     *            significant bytes first)
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static void writeUnsigned32 (final OutputStream out, final int value, final boolean isBigEndian) throws IOException
    {
        if (isBigEndian)
        {
            out.write (value >> 24 & 0xFF);
            out.write (value >> 16 & 0xFF);
            out.write (value >> 8 & 0xFF);
            out.write (value & 0xFF);
        }
        else
        {
            out.write (value & 0xFF);
            out.write (value >> 8 & 0xFF);
            out.write (value >> 16 & 0xFF);
            out.write (value >> 24 & 0xFF);
        }
    }


    /**
     * Reads and converts 4 bytes to an unsigned integer with least significant bytes first.
     *
     * @param fileAccess The random access file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readUnsigned32 (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        final int ch1 = fileAccess.read ();
        final int ch2 = fileAccess.read ();
        final int ch3 = fileAccess.read ();
        final int ch4 = fileAccess.read ();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException ();
        if (isBigEndian)
            return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4;
        return (ch4 << 24) + (ch3 << 16) + (ch2 << 8) + ch1;
    }


    /**
     * Reads and converts 8 bytes to an unsigned integer.
     *
     * @param in The input stream
     * @param isBigEndian True if bytes are stored big-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static long readUnsigned64 (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (in.readNBytes (8));
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getLong () & 0xFFFFFFFFFFFFFFFFL;
    }


    /**
     * Writes an integer as 4 bytes.
     *
     * @param out The output stream
     * @param value The value to write
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian (least
     *            significant bytes first)
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static void writeUnsigned64 (final OutputStream out, final long value, final boolean isBigEndian) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.allocate (8);
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        buffer.putLong (value);
        out.write (buffer.array ());
    }


    /**
     * First reads an 8 byte unsigned number which indicates the length of the block. The number can
     * be either in big- or little-endian but actually only 4 bytes (up to 4GB) are supported
     * (otherwise an exception is thrown). After that the number of bytes (minus the already read 8
     * byte) are read and returned.
     *
     * @param in The input stream to read from
     * @param isBigEndian True if bytes of the size number are stored big-endian otherwise
     *            little-endian (least significant bytes first)
     * @return The read bytes of the data block
     * @throws IOException Data could not be read
     */
    public static byte [] readBlock64 (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final long blockSize = readUnsigned64 (in, isBigEndian);
        if (blockSize > Integer.MAX_VALUE)
            throw new IOException ("Data is larger than 4GB and cannot be read.");
        return in.readNBytes ((int) blockSize - 8);
    }


    /**
     * Writes the size of the block as an 8 byte unsigned number either in big- or little-endian but
     * actually only 4 bytes (up to 4GB) are supported which is the maximum length of an array in
     * Java. After that the given bytes are written.
     *
     * @param out The input stream to read from
     * @param data The data of the block to write
     * @param isBigEndian True if bytes of the size number are stored big-endian otherwise
     *            little-endian (least significant bytes first)
     * @throws IOException Data could not be read
     */
    public static void writeBlock64 (final OutputStream out, final byte [] data, final boolean isBigEndian) throws IOException
    {
        writeUnsigned64 (out, data.length + 8L, isBigEndian);
        out.write (data);
    }


    /**
     * Converts a number of bytes to an unsigned integer with least significant bytes first.
     *
     * @param data The data to convert
     * @return The converted integer
     */
    public static int fromBytesLE (final byte [] data)
    {
        int number = 0;
        for (int i = 0; i < data.length; i++)
            number |= (data[i] & 0xFF) << 8 * i;
        return number;
    }


    /**
     * Converts a 4 byte float value.
     *
     * @param data The 4 byte array
     * @return The float value
     */
    public static float readFloat32LE (final byte [] data)
    {
        return ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN).getFloat ();
    }


    /**
     * Read an LSB 7 bit of a flexible number of bytes.
     *
     * @param in The input stream to read from
     * @return Could not read next byte
     * @throws IOException
     */
    public static int [] read7bitNumberLE (final InputStream in) throws IOException
    {
        int number = 0;
        int count = 0;

        byte [] value;

        while ((value = in.readNBytes (1)).length > 0)
        {
            final int val = value[0] & 0x7F;
            final int shift = 7 * count;
            number = val << shift | number;

            if ((value[0] & 0x80) == 0)
                break;

            count++;
        }

        return new int []
        {
            number,
            count + 1
        };
    }


    /**
     * Reads a number of bytes from an input stream and interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @param length The length of the text
     * @return The read text
     * @throws IOException
     */
    public static String readASCII (final InputStream in, final int length) throws IOException
    {
        final byte [] buffer = new byte [length];
        if (in.read (buffer) != length)
            throw new IOException ();
        return new String (buffer, StandardCharsets.US_ASCII);
    }


    /**
     * Reads a fixed number of bytes from an input stream and interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @param length The length of the text
     * @return The read text
     * @throws IOException
     */
    public static String readASCII (final DataInput in, final int length) throws IOException
    {
        return readASCII (in, length, StandardCharsets.US_ASCII);
    }


    /**
     * Reads a fixed number of bytes from an input stream and interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @param length The length of the text
     * @param reverse Reverses the text if true
     * @return The read text
     * @throws IOException
     */
    public static String readASCII (final DataInput in, final int length, final boolean reverse) throws IOException
    {
        return readASCII (in, length, StandardCharsets.US_ASCII, reverse);
    }


    /**
     * Reads a fixed number of bytes from an input stream and interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @param length The length of the text
     * @param charset The character set to use
     * @return The read text
     * @throws IOException
     */
    public static String readASCII (final DataInput in, final int length, final Charset charset) throws IOException
    {
        return readASCII (in, length, charset, false);
    }


    /**
     * Reads a fixed number of bytes from an input stream and interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @param length The length of the text
     * @param charset The character set to use
     * @param reverse Reverses the text if true
     * @return The read text
     * @throws IOException
     */
    public static String readASCII (final DataInput in, final int length, final Charset charset, final boolean reverse) throws IOException
    {
        final byte [] buffer = new byte [length];
        in.readFully (buffer);
        if (reverse)
            reverseArray (buffer);
        return new String (buffer, charset);
    }


    /**
     * Reverses the content of the given array.
     *
     * @param buffer The array
     */
    public static void reverseArray (final byte [] buffer)
    {
        for (int i = 0; i < buffer.length / 2; i++)
        {
            final byte temp = buffer[i];
            buffer[i] = buffer[buffer.length - i - 1];
            buffer[buffer.length - i - 1] = temp;
        }
    }


    /**
     * Reads an UTF-16 string.
     *
     * @param fileAccess The random access file to read from
     * @return The read string
     * @throws IOException Could not read the string
     */
    public static String readUTF16 (final RandomAccessFile fileAccess) throws IOException
    {
        final StringBuilder sb = new StringBuilder ();
        final byte [] buffer = new byte [2];
        while (fileAccess.read (buffer) == 2)
        {
            if (buffer[0] == 0)
                break;
            sb.append (new String (buffer, StandardCharsets.UTF_16LE));
        }
        return sb.toString ();
    }


    /**
     * Reads an UTF-16 string. The length of the string is stored in the first 4 bytes
     * (little-endian). There are no null termination bytes.
     *
     * @param in The input stream to read from
     * @return The read string
     * @throws IOException Could not read the string
     */
    public static String readWithLengthUTF16 (final InputStream in) throws IOException
    {
        final int size = readUnsigned32 (in, false);
        final byte [] wideStringBytes = in.readNBytes (size * 2);
        return new String (wideStringBytes, StandardCharsets.UTF_16LE);
    }


    /**
     * Reads a 4 byte (LSB) Unix timestamp UTC+1, e.g. "0B 0B 64 4D" is 1298402059 is "22.02.2011
     * 20:14:19".
     *
     * @param in The stream to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @return The timestamp as a date
     * @throws IOException
     */
    public static Date readTimestamp (final InputStream in, final boolean isBigEndian) throws IOException
    {
        return new Date (readUnsigned32 (in, isBigEndian) * 1000L);
    }


    /**
     * Reads a 4 byte (LSB) Unix timestamp UTC+1, e.g. "0B 0B 64 4D" is 1298402059 (seconds) is
     * "22.02.2011 20:14:19".
     *
     * @param fileAccess The random access file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @return The timestamp as a date
     * @throws IOException
     */
    public static Date readTimestamp (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        return new Date (readUnsigned32 (fileAccess, isBigEndian) * 1000L);
    }


    /**
     * Skip exactly N bytes.
     *
     * @param fileAccess The random access file to read from
     * @param numBytes The number of bytes to skip
     * @throws IOException Could not skip the bytes
     */
    public static void skipNBytes (final RandomAccessFile fileAccess, final int numBytes) throws IOException
    {
        if (fileAccess.skipBytes (numBytes) != numBytes)
            throw new IOException (Functions.getMessage ("IDS_ERR_FILE_CORRUPTED"));
    }


    /**
     * Read exactly N bytes.
     *
     * @param fileAccess The random access file to read from
     * @param numBytes The number of bytes to read
     * @return The read bytes
     * @throws IOException Could not read the bytes
     */
    public static byte [] readNBytes (final RandomAccessFile fileAccess, final int numBytes) throws IOException
    {
        final byte [] buffer = new byte [numBytes];
        if (fileAccess.read (buffer) != numBytes)
            throw new IOException (Functions.getMessage ("IDS_ERR_FILE_CORRUPTED"));
        return buffer;
    }


    /**
     * Reads a number of bytes and then moves the file pointer back to the beginning of the read.
     *
     * @param fileAccess The random access file to read from
     * @param numBytes The number of bytes to read
     * @param error An error text to include into the exception text in case of an error
     * @return The read bytes
     * @throws IOException
     */
    public static byte [] peek (final RandomAccessFile fileAccess, final int numBytes, final String error) throws IOException
    {
        final byte [] buffer = new byte [numBytes];
        if (fileAccess.read (buffer) != numBytes)
            throw new IOException (error);
        fileAccess.seek (fileAccess.getFilePointer () - numBytes);
        return buffer;
    }
}
