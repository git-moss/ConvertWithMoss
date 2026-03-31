// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;


/**
 * Interface for reading Akai S1000/S3000 data structures from an image.
 *
 * @author Jürgen Moßgraber
 */
public interface IAkaiImage
{
    /**
     * Reads a text in 12-byte Akai format.
     *
     * @return The read text, trimmed ASCII
     * @throws IOException Could not read the text
     */
    String readText () throws IOException;


    /**
     * Reads a text in 12-byte Akai format.
     * 
     * @param length THe length of the text to read
     * @return The read text, trimmed ASCII
     * @throws IOException Could not read the text
     */
    String readText (int length) throws IOException;


    /**
     * Read single 8-bit value.
     *
     * @return The value
     * @throws IOException Could not read the value
     */
    byte readInt8 () throws IOException;


    /**
     * Read single 16-bit value (little-endian).
     *
     * @return The value
     * @throws IOException Could not read the value
     */
    short readInt16 () throws IOException;


    /**
     * Read single 32-bit value (little-endian).
     *
     * @return The value
     * @throws IOException Could not read the value
     */
    int readInt32 () throws IOException;


    /**
     * Reads an array of 8-bit values.
     *
     * @param data The array in which to store the read data
     * @param wordCount Number of words to read
     * @throws IOException Could not read the value
     */
    void readInt8 (final byte [] data, final int wordCount) throws IOException;


    /**
     * Read array of 16-bit values (little-endian).
     *
     * @param data The array in which to store the read data
     * @param wordCount Number of words to read
     * @throws IOException Could not read the value
     */
    void readInt16 (final short [] data, final int wordCount) throws IOException;


    /**
     * Get the number of available bytes from the current read position.
     *
     * @return The number of available bytes
     */
    int available ();


    /**
     * Convert AKAI text encoding to plain ASCII.
     * 
     * @param buffer The buffer in AKAI format
     * @param length The length of the text to convert
     */
    default void akaiToAscii (final byte [] buffer, final int length)
    {
        for (int i = 0; i < length; i++)
        {
            final int b = buffer[i] & 0xFF;
            // 0-9
            if (b >= 0 && b <= 9)
                buffer[i] = (byte) (b + 48);
            // Space
            else if (b == 10)
                buffer[i] = 32;
            // A-Z
            else if (b >= 11 && b <= 36)
                buffer[i] = (byte) (64 + b - 10);
            else if (b == 37)
                buffer[i] = (byte) '#';
            else if (b == 38)
                buffer[i] = (byte) '+';
            else if (b == 39)
                buffer[i] = (byte) '-';
            else if (b == 40)
                buffer[i] = (byte) '.';
            else
                buffer[i] = 32;
        }
    }
}
