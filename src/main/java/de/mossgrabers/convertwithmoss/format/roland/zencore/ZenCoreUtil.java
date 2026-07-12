// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * Helper functions for reading and writing the fixed-width fields of the Roland FANTOM binary
 * structures from and to memory blocks.
 *
 * @author Jürgen Moßgraber
 */
public final class ZenCoreUtil
{
    /**
     * Constructor. Private due to utility class.
     */
    private ZenCoreUtil ()
    {
        // Intentionally empty
    }


    /**
     * Read an unsigned 16 bit integer from a memory block.
     *
     * @param data The data
     * @param offset The offset to read from
     * @param isBigEndian True to read big-endian, otherwise little-endian
     * @return The value in the range of 0..65535
     */
    public static int readUnsigned16 (final byte [] data, final int offset, final boolean isBigEndian)
    {
        final ByteBuffer buffer = ByteBuffer.wrap (data, offset, 2);
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort () & 0xFFFF;
    }


    /**
     * Read an unsigned 32 bit integer from a memory block.
     *
     * @param data The data
     * @param offset The offset to read from
     * @param isBigEndian True to read big-endian, otherwise little-endian
     * @return The value
     */
    public static long readUnsigned32 (final byte [] data, final int offset, final boolean isBigEndian)
    {
        final ByteBuffer buffer = ByteBuffer.wrap (data, offset, 4);
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt () & 0xFFFFFFFFL;
    }


    /**
     * Write an unsigned 32 bit integer into a memory block.
     *
     * @param data The data
     * @param offset The offset to write to
     * @param value The value to write
     * @param isBigEndian True to write big-endian, otherwise little-endian
     */
    public static void writeUnsigned32 (final byte [] data, final int offset, final long value, final boolean isBigEndian)
    {
        final ByteBuffer buffer = ByteBuffer.wrap (data, offset, 4);
        buffer.order (isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        buffer.putInt ((int) value);
    }


    /**
     * Read a space- or zero-padded ASCII name from a memory block.
     *
     * @param data The data
     * @param offset The offset to read from
     * @param length The maximum length
     * @return The trimmed name
     */
    public static String readName (final byte [] data, final int offset, final int length)
    {
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < length && offset + i < data.length; i++)
        {
            final int c = data[offset + i] & 0xFF;
            if (c == 0)
                break;
            sb.append ((char) c);
        }
        return sb.toString ().trim ();
    }


    /**
     * Convert a name to a fixed-length, space-padded ASCII block - the device's convention for the
     * <i>PATa</i> tone name only. Do not use for sample names, see {@link #padNameZero}.
     *
     * @param text The name
     * @param length The fixed length
     * @return The padded bytes
     */
    public static byte [] padName (final String text, final int length)
    {
        final byte [] result = new byte [length];
        for (int i = 0; i < length; i++)
            result[i] = (byte) (i < text.length () ? text.charAt (i) : ' ');
        return result;
    }


    /**
     * Convert a name to a fixed-length, zero-padded ASCII block - the convention of the sample name
     * fields (<i>USPa</i>, <i>SMPd</i>, <i>MSPa</i>) in every device-written file. This matters: a
     * sample whose name field is space-padded instead imports without an error, but the device never
     * binds its wave data - the multisample shows the sample with an empty waveform display and the
     * tone plays silent on every key.
     *
     * @param text The name
     * @param length The fixed length
     * @return The zero-padded bytes
     */
    public static byte [] padNameZero (final String text, final int length)
    {
        final byte [] result = new byte [length];
        for (int i = 0; i < Math.min (text.length (), length); i++)
            result[i] = (byte) text.charAt (i);
        return result;
    }
}
