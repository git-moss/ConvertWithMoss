// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

/**
 * Bit reading modes for different HFE encodings.
 *
 * @author Jürgen Moßgraber
 */
enum BitReadMode
{
    MSB_FIRST, // Standard: MSB first within byte
    LSB_FIRST, // LSB first within byte
    BYTE_SWAPPED_MSB, // Byte-swapped 16-bit words, MSB first
    BYTE_SWAPPED_LSB // Byte-swapped 16-bit words, LSB first
}


/**
 * Helper class for reading MFM-encoded bit streams with different bit orders.
 *
 * @author Jürgen Moßgraber
 */
public class BitStream
{
    private final byte []     data;
    private int               bitPosition;
    private final BitReadMode mode;


    /**
     * Constructor.
     * 
     * @param data The data to 'stream'
     * @param mode The read mode to use
     */
    public BitStream (final byte [] data, final BitReadMode mode)
    {
        this.data = data;
        this.bitPosition = 0;
        this.mode = mode;
    }


    /**
     * Are there more bits?
     * 
     * @return True if there are more bits
     */
    public boolean hasRemaining ()
    {
        return this.bitPosition < this.data.length * 8 - 32; // Safety margin
    }


    /**
     * Get the current bit position.
     * 
     * @return The position
     */
    public int getBitPosition ()
    {
        return this.bitPosition;
    }


    /**
     * Read a single bit according to the current mode.
     * 
     * @return The bit 0/1
     */
    private int readBit ()
    {
        if (this.bitPosition >= this.data.length * 8)
            return 0;

        int byteIndex = this.bitPosition / 8;
        int bitIndex;

        switch (this.mode)
        {
            case LSB_FIRST:
                bitIndex = this.bitPosition % 8; // LSB first
                break;

            case BYTE_SWAPPED_MSB:
                // Swap bytes in 16-bit words, then MSB first
                byteIndex = byteIndex & ~1 | 1 - (byteIndex & 1);
                bitIndex = 7 - this.bitPosition % 8;
                break;

            case BYTE_SWAPPED_LSB:
                // Swap bytes in 16-bit words, then LSB first
                byteIndex = byteIndex & ~1 | 1 - (byteIndex & 1);
                bitIndex = this.bitPosition % 8;
                break;

            case MSB_FIRST:
            default:
                bitIndex = 7 - this.bitPosition % 8; // MSB first
                break;
        }

        if (byteIndex >= this.data.length)
            return 0;

        final int bit = this.data[byteIndex] >> bitIndex & 1;
        this.bitPosition++;
        return bit;
    }


    /**
     * Peek at a 16-bit word without advancing.
     * 
     * @return The word value
     */
    public int peekWord ()
    {
        final int savedPos = this.bitPosition;
        int word = 0;

        for (int i = 0; i < 16; i++)
            word = word << 1 | this.readBit ();

        this.bitPosition = savedPos;
        return word;
    }


    /**
     * Skip bits.
     * 
     * @param count The number of bits to skip
     */
    public void skipBits (final int count)
    {
        this.bitPosition += count;
    }


    /**
     * Read one MFM-encoded byte (16 bits -> 8 data bits).
     * 
     * @return The read byte
     */
    public int readMfmByte ()
    {
        int result = 0;
        for (int i = 0; i < 8; i++)
        {
            this.readBit (); // Skip clock bit
            final int dataBit = this.readBit (); // Read data bit
            result = result << 1 | dataBit;
        }
        return result & 0xFF;
    }


    /**
     * Peek MFM byte without advancing.
     * 
     * @return The byte value
     */
    public int peekMfmByte ()
    {
        final int savedPos = this.bitPosition;
        final int value = this.readMfmByte ();
        this.bitPosition = savedPos;
        return value;
    }
}