// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mv8000;

/**
 * Read/write access to bit fields in a byte array (MSB first). The MV-8000 patch parameter block is
 * a densely bit-packed structure with 7-bit ASCII characters and parameter fields of varying
 * widths.
 *
 * @author Jürgen Moßgraber
 */
public class MV8000BitArray
{
    private final byte [] data;


    /**
     * Constructor.
     *
     * @param data The data to wrap
     */
    public MV8000BitArray (final byte [] data)
    {
        this.data = data;
    }


    /**
     * Get the wrapped data.
     *
     * @return The data
     */
    public byte [] getData ()
    {
        return this.data;
    }


    /**
     * Read a bit field.
     *
     * @param bitOffset The offset of the first (most significant) bit
     * @param count The number of bits to read (1-32)
     * @return The unsigned value of the bit field
     */
    public int getBits (final int bitOffset, final int count)
    {
        int value = 0;
        for (int i = 0; i < count; i++)
        {
            final int bit = bitOffset + i;
            final int b = this.data[bit / 8] >> 7 - bit % 8 & 1;
            value = value << 1 | b;
        }
        return value;
    }


    /**
     * Write a bit field.
     *
     * @param bitOffset The offset of the first (most significant) bit
     * @param count The number of bits to write (1-32)
     * @param value The unsigned value to write
     */
    public void setBits (final int bitOffset, final int count, final int value)
    {
        for (int i = 0; i < count; i++)
        {
            final int bit = bitOffset + i;
            final int index = bit / 8;
            final int mask = 1 << 7 - bit % 8;
            if ((value >> count - 1 - i & 1) == 1)
                this.data[index] |= (byte) mask;
            else
                this.data[index] &= (byte) ~mask;
        }
    }


    /**
     * Read a text of 7-bit ASCII characters.
     *
     * @param bitOffset The offset of the first bit
     * @param length The number of characters to read
     * @return The text
     */
    public String getText (final int bitOffset, final int length)
    {
        final StringBuilder sb = new StringBuilder (length);
        for (int i = 0; i < length; i++)
        {
            final int c = this.getBits (bitOffset + i * 7, 7);
            sb.append (c >= 32 && c < 127 ? (char) c : ' ');
        }
        return sb.toString ();
    }


    /**
     * Write a text as 7-bit ASCII characters. The text is padded with spaces or shortened to the
     * given length. Non-ASCII characters are replaced with spaces.
     *
     * @param bitOffset The offset of the first bit
     * @param length The number of characters to write
     * @param text The text to write
     */
    public void setText (final int bitOffset, final int length, final String text)
    {
        for (int i = 0; i < length; i++)
        {
            int c = i < text.length () ? text.charAt (i) : ' ';
            if (c < 32 || c >= 127)
                c = ' ';
            this.setBits (bitOffset + i * 7, 7, c);
        }
    }
}
