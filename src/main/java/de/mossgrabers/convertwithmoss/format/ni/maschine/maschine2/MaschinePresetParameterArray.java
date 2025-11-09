// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private final byte []       version;
    private boolean             isOldFormat;
    private final List<byte []> parameterArray;
    private final int           deviceRowMinLength;


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

            this.version = inputStream.readNBytes (7);
            inputStream.skipNBytes (6);

            this.parameterArray = readArray (inputStream);
        }

        this.isOldFormat = this.version[1] < 0x0E;

        // 9 integers which might be 0, last one must contain the string length
        // TODO check if old version is 1 shorter!
        this.deviceRowMinLength = 11 + NI_MASCHINE_DATA_TAG.length ();
    }


    /**
     * Returns true if the version is less than 0x0E.
     *
     * @return True if the version is less than 0x0E
     */
    public boolean isOldFormat ()
    {
        return this.isOldFormat;
    }


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
            if (this.deviceRowMinLength >= this.parameterArray.get (offsetDeviceInfo).length)
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


    public List<Object> readParameterValues (final int offset, final char [] parameterTypes) throws IOException
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
    public int readIntegerValue (final int offset) throws IOException
    {
        return StreamUtils.readVariableLengthNumberLE (this.createByteArrayInputStream (offset));
    }


    /**
     * Reads the first N integer values from an entry in the array.
     *
     * @param offset The index of the entry in the array
     * @param numberCount The number of integers to read
     * @return The read integer values
     * @throws IOException Could not read the values
     */
    public int [] readIntegerValues (final int offset, final int numberCount) throws IOException
    {
        final ByteArrayInputStream in = this.createByteArrayInputStream (offset);
        final int [] result = new int [numberCount];
        for (int i = 0; i < numberCount; i++)
            result[i] = StreamUtils.readVariableLengthNumberLE (in);
        return result;
    }


    /**
     * Reads the first float value from an entry in the array.
     *
     * @param offset The index of the entry in the array
     * @return The read float value
     * @throws IOException Could not read the value
     */
    public float readFloatValue (final int offset) throws IOException
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
    public float [] readFloatValues (final int offset, final int numberCount) throws IOException
    {
        final ByteArrayInputStream in = this.createByteArrayInputStream (offset);
        float [] result = new float [numberCount];
        for (int i = 0; i < numberCount; i++)
            result[i] = StreamUtils.readFloatLE (in);
        return result;
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
        int firstIndex = readStrictIndex (inputStream);
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
     * Reads the next index. An index starts with the number of index-bytes (1 or 2) followed by the
     * index bytes in little-endian order.
     *
     * @param inputStream The input stream to read from
     * @return The read index
     * @throws IOException Could not read the index
     */
    private static int readStrictIndex (final InputStream inputStream) throws IOException
    {
        inputStream.mark (3);

        // Read the number of index bytes to follow
        int header = inputStream.read ();
        if (header == -1)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Unexpected end of stream when reading first index"));

        if (header > 2)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Invalid index header at stream start: " + header));

        int b0 = inputStream.read ();
        if (b0 == -1)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Incomplete index"));

        // There is one index byte
        if (header == 1)
            return b0;

        // There are two index bytes as little-endian
        int b1 = inputStream.read ();
        if (b1 == -1)
            throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_READ_ERROR", "Incomplete index"));
        return b1 << 8 | b0;
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
