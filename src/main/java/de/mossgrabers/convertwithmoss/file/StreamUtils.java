package de.mossgrabers.convertwithmoss.file;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.tools.ui.Functions;


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
     * Reads and converts 2 bytes to an signed integer.
     *
     * @param in The input stream
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readSigned16 (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (in.readNBytes (2));
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort ();
    }


    /**
     * Reads and converts 2 bytes to an signed integer from the given array.
     *
     * @param array The input array
     * @param offset The offset into the array
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readSigned16 (final byte [] array, final int offset, final boolean isBigEndian) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (array, offset, 2);
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort ();
    }


    /**
     * Reads and converts 2 bytes to an unsigned integer.
     *
     * @param in The input stream
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readUnsigned16 (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final byte [] bytes = in.readNBytes (2);
        if (isBigEndian)
            return bytes[1] & 0xFF | (bytes[0] & 0xFF) << 8;
        return bytes[0] & 0xFF | (bytes[1] & 0xFF) << 8;
    }


    /**
     * Reads and converts 2 bytes to an signed integer.
     *
     * @param fileAccess The random access file to read from
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readSigned16 (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (readNBytes (fileAccess, 2));
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort ();
    }


    /**
     * Reads and converts 2 bytes to an unsigned integer.
     *
     * @param fileAccess The random access file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readUnsigned16 (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        final byte [] bytes = readNBytes (fileAccess, 2);
        if (isBigEndian)
            return bytes[1] & 0xFF | (bytes[0] & 0xFF) << 8;
        return bytes[0] & 0xFF | (bytes[1] & 0xFF) << 8;
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
        writeUnsigned (out, value, 16, isBigEndian);
    }


    /**
     * Writes an integer as 2 bytes. Same method as writing unsigned but for clarity.
     *
     * @param out The output stream
     * @param value The value to write
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian (least
     *            significant bytes first)
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static void writeSigned16 (final OutputStream out, final int value, final boolean isBigEndian) throws IOException
    {
        writeUnsigned16 (out, value, isBigEndian);
    }


    /**
     * Writes an integer as 3 bytes.
     *
     * @param out The output stream
     * @param value The value to write
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian (least
     *            significant bytes first)
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static void writeUnsigned24 (final OutputStream out, final int value, final boolean isBigEndian) throws IOException
    {
        writeUnsigned (out, value, 24, isBigEndian);
    }


    /**
     * Reads and converts 4 bytes to a signed integer.
     *
     * @param in The input stream
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readSigned32 (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (in.readNBytes (4));
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt ();
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
    public static long readUnsigned32 (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final byte [] bytes = in.readNBytes (4);
        if (isBigEndian)
            return bytes[3] & 0xFF | (bytes[2] & 0xFF) << 8 | (bytes[1] & 0xFF) << 16 | (long) (bytes[0] & 0xFF) << 24;
        return bytes[0] & 0xFF | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (long) (bytes[3] & 0xFF) << 24;
    }


    /**
     * Writes an integer as 4 bytes. Same method as writing unsigned but for clarity.
     *
     * @param out The output stream
     * @param value The value to write
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian (least
     *            significant bytes first)
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static void writeSigned32 (final OutputStream out, final int value, final boolean isBigEndian) throws IOException
    {
        writeUnsigned32 (out, value, isBigEndian);
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
    public static void writeUnsigned32 (final OutputStream out, final long value, final boolean isBigEndian) throws IOException
    {
        writeUnsigned (out, value, 32, isBigEndian);
    }


    /**
     * Writes an integer as N bits, where N can be 8, 16, 24 or 32.
     *
     * @param out The output stream
     * @param value The value to write
     * @param numBits The number of bits to write
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian (least
     *            significant bytes first)
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static void writeUnsigned (final OutputStream out, final long value, final int numBits, final boolean isBigEndian) throws IOException
    {
        if (isBigEndian)
            for (int offset = numBits - 8; offset >= 0; offset -= 8)
                out.write ((int) (value >> offset & 0xFF));
        else
            for (int offset = 0; offset < numBits; offset += 8)
                out.write ((int) (value >> offset & 0xFF));
    }


    /**
     * Reads and converts 4 bytes to a signed integer.
     *
     * @param fileAccess The random access file to read from
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static int readSigned32 (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (readNBytes (fileAccess, 4));
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt ();
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
    public static long readUnsigned32 (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        final byte [] bytes = readNBytes (fileAccess, 4);
        if (isBigEndian)
            return bytes[3] & 0xFF | (bytes[2] & 0xFF) << 8 | (bytes[1] & 0xFF) << 16 | (long) (bytes[0] & 0xFF) << 24;
        return bytes[0] & 0xFF | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (long) (bytes[3] & 0xFF) << 24;
    }


    /**
     * Reads and converts 8 bytes to an unsigned integer.
     *
     * @param fileAccess The random access file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @return The converted integer
     * @throws IOException The stream has been closed and the contained input stream does not
     *             support reading after close, or another I/O error occurs.
     */
    public static long readUnsigned64 (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (StreamUtils.readNBytes (fileAccess, 8));
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getLong () & 0xFFFFFFFFFFFFFFFFL;
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
        if (blockSize < 8 || blockSize > Integer.MAX_VALUE)
            throw new IOException (Functions.getMessage ("IDS_ERR_DATA_TOO_LARGE"));
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
     * @throws IOException Data could not be written
     */
    public static void writeBlock64 (final OutputStream out, final byte [] data, final boolean isBigEndian) throws IOException
    {
        writeUnsigned64 (out, data.length + 8L, isBigEndian);
        out.write (data);
    }


    /**
     * Write an LSB 7 bit of a flexible number of bytes.
     *
     * @param out The output stream to write to
     * @param value The value to write
     * @throws IOException Data could not be written
     */
    public static void write7bitNumberLSB (final OutputStream out, final int value) throws IOException
    {
        int number = value;

        while (number > 0)
        {
            int val = number & 0x7F;
            number = number >> 7;

            if (number != 0)
                val |= 0x80;

            out.write (val);
        }
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
     * Reads and converts a 4 byte float value.
     *
     * @param in The input stream to read from
     * @return The float value
     * @throws IOException Data could not be read
     */
    public static float readFloatLE (final InputStream in) throws IOException
    {
        return readFloatLE (in.readNBytes (4));
    }


    /**
     * Converts and writes a 4 byte float value.
     *
     * @param out The output stream to write to
     * @param value The float value
     * @throws IOException Data could not be written
     */
    public static void writeFloatLE (final OutputStream out, final float value) throws IOException
    {
        out.write (ByteBuffer.allocate (4).order (ByteOrder.LITTLE_ENDIAN).putFloat (value).array ());
    }


    /**
     * Converts a N byte float value.
     *
     * @param data The N byte array
     * @return The float value
     */
    public static float readFloatLE (final byte [] data)
    {
        return ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN).getFloat ();
    }


    /**
     * Converts and writes a 4 byte float value.
     *
     * @param out The output stream to write to
     * @param value The float value
     * @param isBigEndian True if bytes of the size number are stored big-endian otherwise
     *            little-endian (least significant bytes first)
     * @throws IOException Data could not be written
     */
    public static void writeDouble (final OutputStream out, final double value, final boolean isBigEndian) throws IOException
    {
        out.write (ByteBuffer.allocate (8).order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN).putDouble (value).array ());
    }


    /**
     * Converts a 8 byte double value.
     *
     * @param in The input stream to read from
     * @return The double value
     * @param isBigEndian True if bytes of the size number are stored big-endian otherwise
     *            little-endian (least significant bytes first)
     * @throws IOException Data could not be read
     */
    public static double readDouble (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final byte [] data = in.readNBytes (8);
        return ByteBuffer.wrap (data).order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN).getDouble ();
    }


    /**
     * Read an LSB 7 bit of a flexible number of bytes.
     *
     * @param in The input stream to read from
     * @return Could not read next byte
     * @throws IOException Could not read the number
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
     * Reads all bytes from an input stream and interprets it as UTF-8 text.
     *
     * @param in The stream to read from
     * @return The read text
     * @throws IOException Could not read
     */
    public static String readUTF8 (final InputStream in) throws IOException
    {
        String content = new String (in.readAllBytes (), StandardCharsets.UTF_8);
        // Remove UTF-8 BOM
        if (content.startsWith ("\uFEFF"))
            content = content.substring (1);
        return content;
    }


    /**
     * Reads a number of bytes from an input stream and interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @param length The length of the text
     * @return The read text
     * @throws IOException Could not read
     */
    public static String readASCII (final InputStream in, final int length) throws IOException
    {
        final byte [] buffer = new byte [length];
        final int resultLength = in.read (buffer);
        if (resultLength != length)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ASCII_LENGTH_TOO_SHORT", Integer.toBinaryString (length), Integer.toBinaryString (resultLength)));
        return new String (buffer, StandardCharsets.US_ASCII);
    }


    /**
     * Reads bytes from an input stream until a zero appears and interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @return The read text
     * @throws IOException Could not read
     */
    public static String readNullTerminatedASCII (final InputStream in) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        int b;
        while ((b = in.read ()) > 0)
            out.write (b);
        return new String (out.toByteArray (), StandardCharsets.US_ASCII);
    }


    /**
     * Writes the bytes of an ASCII text and appends them with a null byte.
     *
     * @param out The stream to write to
     * @param text The text to write
     * @throws IOException Could not write
     */
    public static void writeNullTerminatedASCII (final OutputStream out, final String text) throws IOException
    {
        out.write (text.getBytes (StandardCharsets.US_ASCII));
        out.write (0);
    }


    /**
     * Reads bytes from an input stream until a zero appears or the maximum length is reached and
     * interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @param maxLength The maximum number of ASCII characters to read
     * @return The read text
     * @throws IOException Could not read
     */
    public static String readNullTerminatedASCIIMax (final InputStream in, final int maxLength) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        int b;
        while ((b = in.read ()) != 0)
        {
            out.write (b);
            if (out.size () == maxLength)
                break;
        }
        return new String (out.toByteArray (), StandardCharsets.US_ASCII);
    }


    /**
     * Reads a fixed number of bytes from an input stream and interprets it as ASCII text.
     *
     * @param in The stream to read from
     * @param length The length of the text
     * @return The read text
     * @throws IOException Could not read
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
     * @throws IOException Could not read
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
     * @throws IOException Could not read
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
     * @throws IOException Could not read
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
     * Writes an ASCII text to an output stream. If it is shorter than the given length it is
     * appended with zeroes.
     *
     * @param out The stream to write to
     * @param text The text to write
     * @param length The length of the text
     * @throws IOException Could not write
     */
    public static void writeASCII (final OutputStream out, final String text, final int length) throws IOException
    {
        writeASCII (out, text, length, false);
    }


    /**
     * Writes an ASCII text to an output stream. If it is shorter than the given length it is
     * appended with zeroes.
     *
     * @param out The stream to write to
     * @param text The text to write
     * @param length The length of the text
     * @param reverse Reverses the text if true
     * @throws IOException Could not write
     */
    public static void writeASCII (final OutputStream out, final String text, final int length, final boolean reverse) throws IOException
    {
        final byte [] textData = text.getBytes (StandardCharsets.US_ASCII);
        final byte [] buffer = new byte [length];
        Arrays.fill (buffer, (byte) 0);
        System.arraycopy (textData, 0, buffer, 0, Math.min (textData.length, length));
        if (reverse)
            reverseArray (buffer);
        out.write (buffer);
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
     * @param data An array containing the 2-byte characters
     * @return The read string
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     */
    public static String readUTF16 (final byte [] data, final boolean isBigEndian)
    {
        final StringBuilder sb = new StringBuilder (data.length / 2);

        for (int i = 0; i < data.length; i += 2)
        {
            final byte first = isBigEndian ? data[i + 1] : data[i];
            final byte second = isBigEndian ? data[i] : data[i + 1];
            if (first == 0)
                break;
            sb.append (new String (new byte []
            {
                first,
                second
            }, StandardCharsets.UTF_16LE));
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
        final int size = (int) readUnsigned32 (in, false);
        return new String (in.readNBytes (size * 2), StandardCharsets.UTF_16LE);
    }


    /**
     * Writes an UTF-16 string. The length of the string is stored in the first 4 bytes
     * (little-endian). There are no null termination bytes.
     *
     * @param out The output stream to write to
     * @param text The read string
     * @throws IOException Could not read the string
     */
    public static void writeWithLengthUTF16 (final OutputStream out, final String text) throws IOException
    {
        writeUnsigned32 (out, text.length (), false);
        out.write (text.getBytes (StandardCharsets.UTF_16LE));
    }


    /**
     * Reads an ASCII string. The first read byte indicates the length of the string.
     *
     * @param in The input stream to read from
     * @return The read ASCII string
     * @throws IOException Could not read
     */
    public static String readWith1ByteLengthAscii (final InputStream in) throws IOException
    {
        final int blocklength = in.read ();
        final byte [] blockData = in.readNBytes (blocklength);
        return new String (blockData);
    }


    /**
     * Reads an ASCII string. The first four read bytes (LE) indicate the length of the string.
     *
     * @param in The input stream to read from
     * @return The read ASCII string
     * @throws IOException Could not read
     */
    public static String readWith4ByteLengthAscii (final InputStream in) throws IOException
    {
        final int blockLength = (int) StreamUtils.readUnsigned32 (in, false);
        try
        {
            final byte [] blockData = in.readNBytes (blockLength);
            if (blockData.length != blockLength)
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_COULD_NOT_READ_STRING", Integer.toString (blockLength), Integer.toString (blockData.length)));
            return new String (blockData);
        }
        catch (final IllegalArgumentException ex)
        {
            throw new IOException (ex);
        }
    }


    /**
     * Interprets the byte array as ASCII characters and compares them with the given tag.
     *
     * @param tag The tag
     * @param bytes The byte array
     * @throws FormatException One or more characters do not match
     */
    public static void checkTag (final String tag, final byte [] bytes) throws FormatException
    {
        for (int i = 0; i < bytes.length; i++)
            if ((char) bytes[i] != tag.charAt (i))
                throw new FormatException (tag);
    }


    /**
     * Compares the tag with the string.
     *
     * @param tag The tag
     * @param text The text for comparison
     * @throws FormatException One or more characters do not match
     */
    public static void checkTag (final String tag, final String text) throws FormatException
    {
        if (!text.equals (tag))
            throw new FormatException (tag);
    }


    /**
     * Reads a 4 byte (LSB) Unix timestamp UTC+1, e.g. "0B 0B 64 4D" is 1298402059 is "22.02.2011
     * 20:14:19".
     *
     * @param in The stream to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @return The timestamp as a date
     * @throws IOException Could not read
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
     * @throws IOException Could not read the timestamp
     */
    public static Date readTimestamp (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        return new Date (readUnsigned32 (fileAccess, isBigEndian) * 1000L);
    }


    /**
     * Writes a 4 byte (LSB) Unix timestamp UTC+1, e.g. "0B 0B 64 4D" is 1298402059 is "22.02.2011
     * 20:14:19".
     *
     * @param out The stream to write to
     * @param timestamp The timestamp as a date
     * @param isBigEndian True if bytes are stored big-endian
     * @throws IOException Could not read
     */
    public static void writeTimestamp (final OutputStream out, final Date timestamp, final boolean isBigEndian) throws IOException
    {
        writeUnsigned32 (out, (int) (timestamp.getTime () / 1000L), isBigEndian);
    }


    /**
     * Writes a number of zero bytes to the given output stream.
     *
     * @param out The stream to write to
     * @param numBytes The number of bytes to write
     * @throws IOException Could not read
     */
    public static void padBytes (final OutputStream out, final int numBytes) throws IOException
    {
        padBytes (out, numBytes, 0);
    }


    /**
     * Writes a number of identical bytes to the given output stream.
     *
     * @param out The stream to write to
     * @param numBytes The number of bytes to write
     * @param value The value of all bytes
     * @throws IOException Could not read
     */
    public static void padBytes (final OutputStream out, final int numBytes, final int value) throws IOException
    {
        for (int i = 0; i < numBytes; i++)
            out.write (value);
    }


    /**
     * Reads a certain number of bytes from the input stream. The number of bytes is determined from
     * the first 4 bytes (32-bit value).
     *
     * @param in The input stream to read from
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @return The data block without the size bytes
     * @throws IOException Could not read
     */
    public static byte [] readDataBlock (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final int size = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        return in.readNBytes (size);
    }


    /**
     * Reads a certain number of bytes from the input stream. The number of bytes is determined from
     * the first 4 bytes (32-bit value).
     *
     * @param out The output stream to write to
     * @param data The data block without the size bytes
     * @param isBigEndian True if bytes are stored big-endian otherwise little-endian
     * @throws IOException Could not read
     */
    public static void writeDataBlock (final OutputStream out, final byte [] data, final boolean isBigEndian) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, data.length, isBigEndian);
        out.write (data);
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
     * @throws IOException Could not read
     */
    public static byte [] peek (final RandomAccessFile fileAccess, final int numBytes, final String error) throws IOException
    {
        final byte [] buffer = new byte [numBytes];
        if (fileAccess.read (buffer) != numBytes)
            throw new IOException (error);
        fileAccess.seek (fileAccess.getFilePointer () - numBytes);
        return buffer;
    }


    /**
     * Write N null bytes into the output stream.
     *
     * @param out The output stream
     * @param count The number of empty bytes to write
     * @throws IOException Could not write
     */
    public static void writeEmpty (final OutputStream out, final int count) throws IOException
    {
        for (int i = 0; i < count; i++)
            out.write (0);
    }
}
