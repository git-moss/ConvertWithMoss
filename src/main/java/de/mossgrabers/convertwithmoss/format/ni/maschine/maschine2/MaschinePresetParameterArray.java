// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Handles the access to a Boost based parameter array.
 *
 * @author Jürgen Moßgraber
 */
public class MaschinePresetParameterArray
{
    private static final String BOOST_ARCHIVE_MAGIC  = "serialization::archive";
    private static final String NI_MASCHINE_DATA_TAG = "NI::MASCHINE::DATA::";

    private final int []        version;
    private boolean             isOldFormat;
    private final List<byte []> parameterArray;


    /**
     * Constructor.
     *
     * @param data The data to read from
     * @throws IOException Could not read the array
     */
    public MaschinePresetParameterArray (final byte [] data) throws IOException
    {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream (data))
        {
            final long size = StreamUtils.readUnsigned32 (inputStream, false);
            if (size != inputStream.available ())
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Wrong data size"));

            inputStream.skipNBytes (1);
            final String magic = StreamUtils.readWith1ByteLengthAscii (inputStream);
            if (!BOOST_ARCHIVE_MAGIC.equals (magic))
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Magic boost bytes not found."));

            final byte [] versionBytes = inputStream.readNBytes (7);
            this.version = readIntegers (new ByteArrayInputStream (versionBytes), 4);

            // Always 0, 0, 0, 1, 2, 1
            inputStream.skipNBytes (6);

            this.parameterArray = readArray (inputStream);
        }

        this.isOldFormat = this.version[0] < 0x0D;
    }


    /**
     * Serializes the parameter array to a byte array.
     *
     * @return The byte array
     * @throws IOException Could not serialize the parameter array
     */
    public byte [] serialize () throws IOException
    {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream (); final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream ())
        {
            arrayOutputStream.write (1);
            StreamUtils.writeWith1ByteLengthAscii (arrayOutputStream, BOOST_ARCHIVE_MAGIC);
            writeIntegers (arrayOutputStream, this.version);
            arrayOutputStream.write (new byte []
            {
                0,
                0,
                0,
                1,
                2,
                1
            });
            writeArray (arrayOutputStream, this.parameterArray);
            final byte [] byteArray = arrayOutputStream.toByteArray ();

            StreamUtils.writeUnsigned32 (outputStream, byteArray.length, false);
            outputStream.write (byteArray);
            return outputStream.toByteArray ();
        }
    }


    /**
     * Get the array version.
     *
     * @return The version
     */
    public int [] getVersion ()
    {
        return this.version;
    }


    /**
     * Returns true if the version is less than 0x0D.
     *
     * @return True if the version is less than 0x0D
     */
    public boolean isOldFormat ()
    {
        return this.isOldFormat;
    }


    /**
     * Get the parameter rows.
     *
     * @return The rows
     */
    public List<byte []> getRawData ()
    {
        return this.parameterArray;
    }


    /**
     * Find the next occurrence of the tag 'NI::MASCHINE::DATA::' in the parameter array starting at
     * the given offset.
     *
     * @param startOffset The offset in the array to start the search
     * @return The index at which the tag occurs or -1 if not found
     */
    public Pair<Integer, String> findNextMaschineDevice (final int startOffset)
    {
        for (int offsetDeviceInfo = startOffset; offsetDeviceInfo < this.parameterArray.size (); offsetDeviceInfo++)
        {
            if (this.parameterArray.get (offsetDeviceInfo).length < NI_MASCHINE_DATA_TAG.length ())
                continue;

            final byte [] data = this.parameterArray.get (offsetDeviceInfo);
            final int position = indexOf (data, NI_MASCHINE_DATA_TAG.getBytes ());
            if (position > 0)
            {
                final String deviceID = new String (data, position, data[position - 1]);
                return new Pair<> (Integer.valueOf (offsetDeviceInfo), deviceID.substring (NI_MASCHINE_DATA_TAG.length ()));
            }
        }

        return new Pair<> (Integer.valueOf (-1), "");
    }


    /**
     * Reads several parameters from a parameter row.
     *
     * @param offset The row offset
     * @param parameterTypes The types of the parameters to read; use 'i' for integers, 'f' for
     *            floats and 's' for a string
     * @return The read parameters
     * @throws IOException Could not read the parameters
     */
    public List<Object> readParameters (final int offset, final char [] parameterTypes) throws IOException
    {
        final ByteArrayInputStream in = this.createByteArrayInputStream (offset);
        final List<Object> params = new ArrayList<> ();
        for (char c: parameterTypes)
            switch (c)
            {
                case 'i':
                    params.add (Integer.valueOf (StreamUtils.readVariableLengthNumberLE (in)));
                    break;
                case 'f':
                    params.add (Float.valueOf (StreamUtils.readFloatLE (in)));
                    break;
                case 's':
                    final int length = StreamUtils.readVariableLengthNumberLE (in);
                    if (length < 0)
                        throw new IOException ("Negative string length.");
                    params.add (StreamUtils.readASCII (in, length));
                    break;
                default:
                    throw new IOException ("Unknown parameter type.");
            }
        return params;
    }


    /**
     * Reads the first integer value from an entry in the array.
     *
     * @param offset The index of the entry in the array
     * @return The read integer value
     * @throws IOException Could not read the value
     */
    public int readInteger (final int offset) throws IOException
    {
        return StreamUtils.readVariableLengthNumberLE (this.createByteArrayInputStream (offset));
    }


    /**
     * Write an integer value to the given output stream.
     *
     * @param out The stream to write to
     * @param value The integer to write
     * @throws IOException Could not write the values
     */
    public static void writeInteger (final ByteArrayOutputStream out, final int value) throws IOException
    {
        StreamUtils.writeVariableLengthNumberLE (out, value);
    }


    /**
     * Reads the first N integer values from an entry in the array.
     *
     * @param offset The index of the entry in the array
     * @param numberCount The number of integers to read
     * @return The read integer values
     * @throws IOException Could not read the values
     */
    public int [] readIntegers (final int offset, final int numberCount) throws IOException
    {
        return readIntegers (this.createByteArrayInputStream (offset), numberCount);
    }


    /**
     * Reads the first N integer values from an entry in the array.
     *
     * @param in The stream to read from
     * @param numberCount The number of integers to read
     * @return The read integer values
     * @throws IOException Could not read the values
     */
    private static int [] readIntegers (final ByteArrayInputStream in, final int numberCount) throws IOException
    {
        final int [] result = new int [numberCount];
        for (int i = 0; i < numberCount; i++)
            result[i] = StreamUtils.readVariableLengthNumberLE (in);
        return result;
    }


    /**
     * Write N integer values to an entry in the parameter array.
     *
     * @param offset The offset of the entry in the array to write to
     * @param values The integers to write
     * @throws IOException Could not write the values
     */
    public void writeIntegers (final int offset, final int... values) throws IOException
    {
        writeIntegers (offset, this.parameterArray, values);
    }


    /**
     * Write N integer values to an entry in the given array.
     *
     * @param offset The offset of the entry in the array to write to
     * @param array The array into which to write
     * @param values The integers to write
     * @throws IOException Could not write the values
     */
    public static void writeIntegers (final int offset, final List<byte []> array, final int... values) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        writeIntegers (out, values);
        array.set (offset, out.toByteArray ());
    }


    /**
     * Write N integer values to the given output stream.
     *
     * @param out The stream to the given output stream
     * @param values The integers to write
     * @throws IOException Could not write the values
     */
    public static void writeIntegers (final ByteArrayOutputStream out, final int... values) throws IOException
    {
        for (final int value: values)
            StreamUtils.writeVariableLengthNumberLE (out, value);
    }


    /**
     * Reads the first float value from an entry in the array.
     *
     * @param offset The index of the entry in the array
     * @return The read float value
     * @throws IOException Could not read the value
     */
    public float readFloat (final int offset) throws IOException
    {
        return StreamUtils.readFloatLE (this.createByteArrayInputStream (offset));
    }


    /**
     * Reads the first N float values from an entry in the array.
     *
     * @param offset The index of the entry in the array
     * @param numberCount The number of floats to read
     * @return The read float values
     * @throws IOException Could not read the values
     */
    public float [] readFloat (final int offset, final int numberCount) throws IOException
    {
        final ByteArrayInputStream in = this.createByteArrayInputStream (offset);
        float [] result = new float [numberCount];
        for (int i = 0; i < numberCount; i++)
            result[i] = StreamUtils.readFloatLE (in);
        return result;
    }


    /**
     * Write N integer values to an entry in the given array.
     *
     * @param offset The offset of the entry in the array to write to
     * @param array The array into which to write
     * @param values The integers to write
     * @throws IOException Could not write the values
     */
    public static void writeFloats (final int offset, final List<byte []> array, final float... values) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        writeFloats (out, values);
        array.set (offset, out.toByteArray ());
    }


    /**
     * Reads the first float value from an entry in the array.
     *
     * @param out The stream to write to
     * @param values The float values to write
     * @throws IOException Could not write the values
     */
    public static void writeFloats (final ByteArrayOutputStream out, final float... values) throws IOException
    {
        for (final float value: values)
            StreamUtils.writeFloatLE (out, value);
    }


    /**
     * Write an ASCII string to a row in the array.
     *
     * @param offset The offset of the row
     * @param text The text to write
     * @throws IOException Could not write the text
     */
    public void writeString (final int offset, final String text) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.write (0);
        writeString (out, text);
        this.parameterArray.set (offset, out.toByteArray ());
    }


    /**
     * Write an ASCII string to a row in the array.
     *
     * @param out The stream to write to
     * @param text The text to write
     * @throws IOException Could not write the text
     */
    public static void writeString (final ByteArrayOutputStream out, final String text) throws IOException
    {
        writeInteger (out, text.length ());
        StreamUtils.writeASCII (out, text, text.length ());
    }


    private ByteArrayInputStream createByteArrayInputStream (final int offset)
    {
        final byte [] data = this.parameterArray.get (offset);
        // Ignores the first 00
        return new ByteArrayInputStream (data, this.isOldFormat ? 0 : 1, this.isOldFormat ? data.length : data.length - 1);
    }


    /**
     * Splits the data into an array of bytes. Each array entry starts with its index, stored in the
     * number representation (they start with 1 byte which contains the number of bytes of which the
     * number consists followed by the bytes in little-endian order).
     *
     * @param inputStream The input stream to read from
     * @return The parameter array
     * @throws IOException Could not read/split the data
     */
    private static List<byte []> readArray (final InputStream inputStream) throws IOException
    {
        final List<byte []> chunks = new ArrayList<> ();
        int expectedIndex = 0;

        // Read the first index, must be 0
        int firstIndex = StreamUtils.readVariableLengthNumberLE (inputStream);
        if (firstIndex != expectedIndex)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Array index does not match"));

        int nextIndex;
        do
        {
            nextIndex = -1;
            expectedIndex++;

            final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream ();
            while (true)
            {
                inputStream.mark (3);
                int header = inputStream.read ();
                if (header == -1)
                    break;

                // Is this a 1 byte index?
                if (header == 1)
                {
                    int b0 = inputStream.read ();
                    if (b0 == -1)
                    {
                        inputStream.reset ();
                        break;
                    }

                    int indexVal = b0;
                    if (indexVal == expectedIndex)
                    {
                        nextIndex = indexVal;
                        break;
                    }
                }
                // Is this a 2 byte index?
                else if (header == 2)
                {
                    int b0 = inputStream.read ();
                    int b1 = inputStream.read ();
                    if (b0 == -1 || b1 == -1)
                    {
                        inputStream.reset ();
                        break;
                    }
                    int indexVal = b1 << 8 | b0;
                    if (indexVal == expectedIndex)
                    {
                        nextIndex = indexVal;
                        break;
                    }
                }

                // No index byte - add to current data
                inputStream.reset ();
                dataBuffer.write (inputStream.read ());
            }

            chunks.add (dataBuffer.toByteArray ());

        } while (nextIndex != -1);

        return chunks;
    }


    /**
     * Writes an array of bytes. Each array entry starts with its index, stored in the number
     * representation (they start with 1 byte which contains the number of bytes of which the number
     * consists followed by the bytes in little-endian order).
     *
     * @param outputStream The output stream to write to
     * @param array The parameter array to write
     * @throws IOException Could not write the data
     */
    private static void writeArray (final OutputStream outputStream, List<byte []> array) throws IOException
    {
        // First needs to be written manually to get 2 bytes!
        outputStream.write (1);
        outputStream.write (0);
        for (int i = 1; i < array.size (); i++)
        {
            StreamUtils.writeVariableLengthNumberLE (outputStream, i);
            outputStream.write (array.get (i));
        }
    }


    private static int indexOf (final byte [] data, final byte [] pattern)
    {
        int n = data.length;
        int m = pattern.length;
        if (m == 0 || n < m)
            return -1;

        outer: for (int i = 0; i <= n - m; i++)
        {
            for (int j = 0; j < m; j++)
                if (data[i + j] != pattern[j])
                    continue outer;
            return i; // Found match
        }
        return -1; // Not found
    }
}
