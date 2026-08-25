// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s1xx;

/**
 * A contiguous decoded sample region.
 *
 * S-10 sample words are 12-bit unsigned values (two's complement) transported as:
 *
 * byte 0: 0aaa aaaa byte 1: 0bbb bb00
 *
 * word = byte0 * 32 + byte1 / 4
 *
 * @author Jürgen Moßgraber
 */
public class SampleBlock
{
    /**
     * S-10 start address.
     *
     * <ul>
     * <li>02 00 00 = Bank A
     * <li>06 00 00 = Bank B
     * <li>0A 00 00 = Bank C
     * <li>0E 00 00 = Bank D
     * </ul>
     */
    public final int    startAddress;

    /** Unsigned 12-bit sample words, each in the range 0..4095. */
    public final int [] sampleWords;


    /**
     * Creates a sample block covering a contiguous range of sample words.
     *
     * @param startAddress The S-10 start address of the block, e.g. 0A 00 00 for sample bank C
     * @param sampleWords The unsigned 12-bit sample words, each in the range 0..4095
     */
    public SampleBlock (final int startAddress, final int [] sampleWords)
    {
        this.startAddress = startAddress;
        this.sampleWords = sampleWords;

        for (final int word: this.sampleWords)
            if (word < 0 || word > 0x0FFF)
                throw new IllegalArgumentException ("Sample words must be in the range 0..4095.");
    }


    /**
     * Encodes all sample words of this block into their two-byte transport representation.
     *
     * @return The encoded transport bytes; the array is twice as long as {@link #sampleWords}
     */
    public int [] toTransportBytes ()
    {
        final int [] result = new int [this.sampleWords.length * 2];

        for (int i = 0; i < this.sampleWords.length; i++)
        {
            final int [] encoded = encodeSampleWord (this.sampleWords[i]);
            result[i * 2] = encoded[0];
            result[i * 2 + 1] = encoded[1];
        }

        return result;
    }


    /**
     * Encodes one unsigned 12-bit sample word.
     *
     * @param word The unsigned 12-bit sample word to encode, in the range 0..4095
     * @return A two-element array containing the encoded transport bytes (byte 0, byte 1)
     */
    public static int [] encodeSampleWord (final int word)
    {
        if (word < 0 || word > 0x0FFF)
            throw new IllegalArgumentException ("Sample word must be in the range 0..4095.");

        return new int []
        {
            (word >>> 5) & 0x7F,
            (word & 0x1F) << 2
        };
    }


    /**
     * Format the address as 3 hex bytes.
     *
     * @return The formatted string
     */
    public String formatAddress ()
    {
        return String.format ("%02X %02X %02X", Integer.valueOf (SysExMessage.addressByte0 (this.startAddress)), Integer.valueOf (SysExMessage.addressByte1 (this.startAddress)), Integer.valueOf (SysExMessage.addressByte2 (this.startAddress)));
    }
}
