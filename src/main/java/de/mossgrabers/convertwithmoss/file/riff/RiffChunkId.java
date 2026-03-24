// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.nio.charset.StandardCharsets;


/**
 * Interface for RIFF IDs.
 *
 * @author Jürgen Moßgraber
 */
public interface RiffChunkId
{
    /**
     * Get the four character ID as an integer.
     *
     * @return The ID as a value
     */
    int getFourCC ();


    /**
     * Get the description of the chunk.
     *
     * @return The name
     */
    String getDescription ();


    /**
     * Converts the first four letters of the string into an RIFF Identifier.
     *
     * @param text The string to be converted
     * @return ID representation of the string
     */
    public static int toFourCC (final String text)
    {
        final byte [] bytes = text.getBytes ();
        return bytes[0] << 24 | bytes[1] << 16 | bytes[2] << 8 | bytes[3];
    }


    /**
     * Convert an integer RIFF identifier to an ASCII text.
     *
     * @param id ID to be converted.
     * @return Text representation of the ID
     */
    public static String toASCII (final int id)
    {
        return new String (new byte []
        {
            (byte) (id >>> 24),
            (byte) (id >>> 16),
            (byte) (id >>> 8),
            (byte) (id >>> 0)
        }, StandardCharsets.US_ASCII);
    }
}
