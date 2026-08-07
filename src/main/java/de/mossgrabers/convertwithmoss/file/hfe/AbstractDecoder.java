// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

/**
 * Base class for (M)FM decoders.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractDecoder implements IDecoder
{
    protected static final int            IDAM           = 0xFE;
    protected static final int            DAM            = 0xFB;
    protected static final int            DDAM           = 0xF8;

    // Try different bit reading modes
    protected static final BitReadMode [] BIT_READ_MODES =
    {
        BitReadMode.LSB_FIRST,
        BitReadMode.MSB_FIRST,
        BitReadMode.BYTE_SWAPPED_MSB,
        BitReadMode.BYTE_SWAPPED_LSB
    };


    /**
     * Constructor.
     */
    protected AbstractDecoder ()
    {
        // Intentionally empty
    }


    /**
     * Calculate CRC-CCITT (IBM format). Polynomial: 0x1021, Init: 0xFFFF. Same algorithm as used
     * for MFM, since both FM and MFM IBM-format sectors use the identical CRC.
     *
     * @param data The data over which to calculate the CRC
     * @return The CRC
     */
    protected static int calculateCrc (final byte [] data)
    {
        int crc = 0xFFFF;

        for (final byte b: data)
        {
            crc ^= (b & 0xFF) << 8;

            for (int i = 0; i < 8; i++)
                if ((crc & 0x8000) != 0)
                    crc = crc << 1 ^ 0x1021;
                else
                    crc = crc << 1;
        }

        return crc & 0xFFFF;
    }
}
