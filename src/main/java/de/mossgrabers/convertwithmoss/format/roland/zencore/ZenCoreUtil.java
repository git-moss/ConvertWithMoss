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
     * The FANTOM envelope-time law, hardware-calibrated on a FANTOM-0: a bank of tones with exact,
     * patched TVA release values was recorded on the device and the exponential fades fitted. Each
     * anchor pair is the time value and its measured audible stage span (the time to reach -40 dB;
     * the separately measured attack-rise anchors agree within ~25%). Stage times interpolate
     * log-linearly between the anchors; the last pair extrapolates the measured curve to full
     * scale.
     */
    private static final int []    TIME_VALUE_ANCHORS  =
    {
        0,
        8,
        32,
        75,
        129,
        256,
        512,
        800,
        1023
    };
    /** The measured stage time in seconds per anchor of {@link #TIME_VALUE_ANCHORS}. */
    private static final double [] TIME_SECOND_ANCHORS =
    {
        0.010,
        0.020,
        0.060,
        0.120,
        0.200,
        0.390,
        1.240,
        6.190,
        21.5
    };


    /**
     * Convert an envelope time in seconds to the FANTOM 0-1023 time value with the
     * hardware-calibrated law (see {@link #TIME_VALUE_ANCHORS}).
     *
     * @param seconds The time in seconds
     * @return The 0-1023 time value
     */
    public static int timeToValue (final double seconds)
    {
        if (seconds <= TIME_SECOND_ANCHORS[0])
            return 0;
        final int last = TIME_SECOND_ANCHORS.length - 1;
        if (seconds >= TIME_SECOND_ANCHORS[last])
            return TIME_VALUE_ANCHORS[last];
        int i = 1;
        while (TIME_SECOND_ANCHORS[i] < seconds)
            i++;
        final double s0 = TIME_SECOND_ANCHORS[i - 1];
        final double s1 = TIME_SECOND_ANCHORS[i];
        final int v0 = TIME_VALUE_ANCHORS[i - 1];
        final int v1 = TIME_VALUE_ANCHORS[i];
        return (int) Math.round (v0 + (v1 - v0) * Math.log (seconds / s0) / Math.log (s1 / s0));
    }


    /**
     * Convert a FANTOM 0-1023 envelope time value to seconds with the hardware-calibrated law -
     * the inverse of {@link #timeToValue}.
     *
     * @param value The 0-1023 time value
     * @return The time in seconds
     */
    public static double valueToTime (final int value)
    {
        if (value <= TIME_VALUE_ANCHORS[0])
            return TIME_SECOND_ANCHORS[0];
        final int last = TIME_VALUE_ANCHORS.length - 1;
        if (value >= TIME_VALUE_ANCHORS[last])
            return TIME_SECOND_ANCHORS[last];
        int i = 1;
        while (TIME_VALUE_ANCHORS[i] < value)
            i++;
        final double s0 = TIME_SECOND_ANCHORS[i - 1];
        final double s1 = TIME_SECOND_ANCHORS[i];
        final int v0 = TIME_VALUE_ANCHORS[i - 1];
        final int v1 = TIME_VALUE_ANCHORS[i];
        return s0 * Math.pow (s1 / s0, (value - v0) / (double) (v1 - v0));
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
     * sample whose name field is space-padded instead imports without an error, but the device
     * never binds its wave data - the multisample shows the sample with an empty waveform display
     * and the tone plays silent on every key.
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
