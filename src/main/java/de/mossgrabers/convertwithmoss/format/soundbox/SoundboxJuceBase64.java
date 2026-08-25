// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

import java.io.IOException;


/**
 * Encoder/decoder for the Base64 variant of the JUCE framework (MemoryBlock::toBase64Encoding). The
 * encoded text is the decimal number of bytes followed by a '.' and the data encoded with a custom
 * 64 character alphabet. Character i holds the bits i*6..i*6+5 of the data where bit n is bit (n
 * &amp; 7) of byte (n &gt;&gt; 3).
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxJuceBase64
{
    private static final String TABLE = ".ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+";


    /**
     * Private constructor since this is a utility class.
     */
    private SoundboxJuceBase64 ()
    {
        // Intentionally empty
    }


    /**
     * Decodes a JUCE Base64 text.
     *
     * @param text The text to decode
     * @return The decoded bytes
     * @throws IOException If the text is not correctly formatted
     */
    public static byte [] decode (final String text) throws IOException
    {
        final int dotPosition = text.indexOf ('.');
        if (dotPosition < 1)
            throw new IOException ("Missing size prefix in JUCE Base64 text.");

        final int size;
        try
        {
            size = Integer.parseInt (text.substring (0, dotPosition));
        }
        catch (final NumberFormatException ex)
        {
            throw new IOException ("Malformed size prefix in JUCE Base64 text.", ex);
        }

        final byte [] data = new byte [size];
        final int numBits = size * 8;
        for (int i = dotPosition + 1; i < text.length (); i++)
        {
            final int value = TABLE.indexOf (text.charAt (i));
            if (value < 0)
                throw new IOException ("Illegal character in JUCE Base64 text.");
            final int bitPosition = (i - dotPosition - 1) * 6;
            for (int bit = 0; bit < 6; bit++)
            {
                if (bitPosition + bit >= numBits)
                    break;
                if ((value & 1 << bit) != 0)
                    data[bitPosition + bit >> 3] |= (byte) (1 << (bitPosition + bit & 7));
            }
        }
        return data;
    }


    /**
     * Encodes data as a JUCE Base64 text.
     *
     * @param data The data to encode
     * @return The encoded text
     */
    public static String encode (final byte [] data)
    {
        final int numBits = data.length * 8;
        final int numChars = (numBits + 5) / 6;
        final StringBuilder sb = new StringBuilder (12 + numChars);
        sb.append (data.length).append ('.');
        for (int i = 0; i < numChars; i++)
        {
            int value = 0;
            for (int bit = 0; bit < 6; bit++)
            {
                final int bitPosition = i * 6 + bit;
                if (bitPosition < numBits && (data[bitPosition >> 3] & 1 << (bitPosition & 7)) != 0)
                    value |= 1 << bit;
            }
            sb.append (TABLE.charAt (value));
        }
        return sb.toString ();
    }
}
