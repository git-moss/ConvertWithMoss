package de.mossgrabers.convertwithmoss.format.roland.s5xx.io;

public class Endianess
{
    public static short get16bitBE (byte [] data)
    {
        return get16bitBE (data, 0);
    }


    public static short get16bitBE (byte [] data, int offset)
    {
        return (short) (Byte.toUnsignedInt (data[offset]) << 8 | Byte.toUnsignedInt (data[offset + 1]));
    }


    public static int get16bitBEu (byte [] data)
    {
        return Short.toUnsignedInt (get16bitBE (data));
    }


    public static int get16bitBEu (byte [] data, int offset)
    {
        return Short.toUnsignedInt (get16bitBE (data, offset));
    }


    public static int get24bitBE (byte [] data)
    {
        return get24bitBE (data, 0);
    }


    public static int get24bitBE (byte [] data, int offset)
    {
        return Byte.toUnsignedInt (data[offset]) << 16 | Byte.toUnsignedInt (data[offset + 1]) << 8 | Byte.toUnsignedInt (data[offset + 2]);
    }


    public static int get32bitBE (byte [] data)
    {
        return get32bitBE (data, 0);
    }


    public static long get32bitBEu (byte [] data)
    {
        return Integer.toUnsignedLong (get32bitBE (data));
    }


    public static int get32bitBE (byte [] data, int offset)
    {
        return Byte.toUnsignedInt (data[offset]) << 24 | Byte.toUnsignedInt (data[offset + 1]) << 16 | Byte.toUnsignedInt (data[offset + 2]) << 8 | Byte.toUnsignedInt (data[offset + 3]);
    }


    public static long get32bitBEu (byte [] data, int offset)
    {
        return Integer.toUnsignedLong (get32bitBE (data, offset));
    }


    public static long get48bitBE (byte [] data)
    {
        return get48bitBE (data, 0);
    }


    public static long get48bitBE (byte [] data, int offset)
    {
        return Byte.toUnsignedLong (data[offset]) << 40 | Byte.toUnsignedLong (data[offset + 1]) << 32 | Byte.toUnsignedLong (data[offset + 2]) << 24 | Byte.toUnsignedLong (data[offset + 3]) << 16 | Byte.toUnsignedLong (data[offset + 4]) << 8 | Byte.toUnsignedLong (data[offset + 5]);
    }


    public static long get56bitBE (byte [] data)
    {
        return get56bitBE (data, 0);
    }


    public static long get56bitBE (byte [] data, int offset)
    {
        return Byte.toUnsignedLong (data[offset]) << 48 | Byte.toUnsignedLong (data[offset + 1]) << 40 | Byte.toUnsignedLong (data[offset + 2]) << 32 | Byte.toUnsignedLong (data[offset + 3]) << 24 | Byte.toUnsignedLong (data[offset + 4]) << 16 | Byte.toUnsignedLong (data[offset + 5]) << 8 | Byte.toUnsignedLong (data[offset + 6]);
    }


    public static long get64bitBE (byte [] data)
    {
        return get64bitBE (data, 0);
    }


    public static long get64bitBE (byte [] data, int offset)
    {
        return Byte.toUnsignedLong (data[offset]) << 56 | Byte.toUnsignedLong (data[offset + 1]) << 48 | Byte.toUnsignedLong (data[offset + 2]) << 40 | Byte.toUnsignedLong (data[offset + 3]) << 32 | Byte.toUnsignedLong (data[offset + 4]) << 24 | Byte.toUnsignedLong (data[offset + 5]) << 16 | Byte.toUnsignedLong (data[offset + 6]) << 8 | Byte.toUnsignedLong (data[offset + 7]);
    }


    public static short get16bitLE (byte [] data)
    {
        return get16bitLE (data, 0);
    }


    public static short get16bitLE (byte [] data, int offset)
    {
        return (short) (Byte.toUnsignedInt (data[offset]) | Byte.toUnsignedInt (data[offset + 1]) << 8);
    }


    public static int get24bitLE (byte [] data)
    {
        return get24bitLE (data, 0);
    }


    public static int get24bitLE (byte [] data, int offset)
    {
        return Byte.toUnsignedInt (data[offset]) | Byte.toUnsignedInt (data[offset + 1]) << 8 | Byte.toUnsignedInt (data[offset + 2]) << 16;
    }


    public static int gets24bitLE (byte [] data, int offset)
    {
        int sign = data[offset + 2] << 23 >> 7;
        return Byte.toUnsignedInt (data[offset]) | Byte.toUnsignedInt (data[offset + 1]) << 8 | Byte.toUnsignedInt (data[offset + 2]) << 16 | sign;
    }


    public static int get32bitLE (byte [] data)
    {
        return get32bitLE (data, 0);
    }


    public static int get32bitLE (byte [] data, int offset)
    {
        return Byte.toUnsignedInt (data[offset]) | Byte.toUnsignedInt (data[offset + 1]) << 8 | Byte.toUnsignedInt (data[offset + 2]) << 16 | Byte.toUnsignedInt (data[offset + 3]) << 24;
    }


    public static long get48bitLE (byte [] data)
    {
        return get48bitLE (data, 0);
    }


    public static long get48bitLE (byte [] data, int offset)
    {
        return Byte.toUnsignedLong (data[offset]) | Byte.toUnsignedLong (data[offset + 1]) << 8 | Byte.toUnsignedLong (data[offset + 2]) << 16 | Byte.toUnsignedLong (data[offset + 3]) << 24 | Byte.toUnsignedLong (data[offset + 4]) << 32 | Byte.toUnsignedLong (data[offset + 5]) << 40;
    }


    public static long get56bitLE (byte [] data)
    {
        return get56bitLE (data, 0);
    }


    public static long get56bitLE (byte [] data, int offset)
    {
        return Byte.toUnsignedLong (data[offset]) | Byte.toUnsignedLong (data[offset + 1]) << 8 | Byte.toUnsignedLong (data[offset + 2]) << 16 | Byte.toUnsignedLong (data[offset + 3]) << 24 | Byte.toUnsignedLong (data[offset + 4]) << 32 | Byte.toUnsignedLong (data[offset + 5]) << 40 | Byte.toUnsignedLong (data[offset + 6]) << 48;
    }


    public static long get64bitLE (byte [] data)
    {
        return get64bitLE (data, 0);
    }


    public static long get64bitLE (byte [] data, int offset)
    {
        return Byte.toUnsignedLong (data[offset]) | Byte.toUnsignedLong (data[offset + 1]) << 8 | Byte.toUnsignedLong (data[offset + 2]) << 16 | Byte.toUnsignedLong (data[offset + 3]) << 24 | Byte.toUnsignedLong (data[offset + 4]) << 32 | Byte.toUnsignedLong (data[offset + 5]) << 40 | Byte.toUnsignedLong (data[offset + 6]) << 48 | Byte.toUnsignedLong (data[offset + 7]) << 56;
    }


    public static byte [] set16bitBE (byte [] data, int offset, short value)
    {
        data[offset] = (byte) (value >> 8);
        data[offset + 1] = (byte) value;
        return data;
    }


    public static byte [] set24bitBE (byte [] data, int offset, int value)
    {
        data[offset] = (byte) (value >> 16);
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) value;
        return data;
    }


    public static byte [] set32bitBE (byte [] data, int offset, int value)
    {
        data[offset] = (byte) (value >> 24);
        data[offset + 1] = (byte) (value >> 16);
        data[offset + 2] = (byte) (value >> 8);
        data[offset + 3] = (byte) value;
        return data;
    }


    public static byte [] set32bitBE (byte [] data, int offset, float value)
    {
        int bits = Float.floatToRawIntBits (value);
        return set32bitBE (data, offset, bits);
    }


    public static byte [] set64bitBE (byte [] data, int offset, long value)
    {
        data[offset] = (byte) (value >> 56);
        data[offset + 1] = (byte) (value >> 48);
        data[offset + 2] = (byte) (value >> 40);
        data[offset + 3] = (byte) (value >> 32);
        data[offset + 4] = (byte) (value >> 24);
        data[offset + 5] = (byte) (value >> 16);
        data[offset + 6] = (byte) (value >> 8);
        data[offset + 7] = (byte) value;
        return data;
    }


    public static byte [] set64bitBE (byte [] data, int offset, double value)
    {
        long bits = Double.doubleToRawLongBits (value);
        return set64bitBE (data, offset, bits);
    }


    public static byte [] set16bitLE (byte [] data, int offset, short value)
    {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        return data;
    }


    public static byte [] set24bitLE (byte [] data, int offset, int value)
    {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        return data;
    }


    public static byte [] set32bitLE (byte [] data, int offset, int value)
    {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        data[offset + 3] = (byte) (value >> 24);
        return data;
    }


    public static byte [] set32bitLE (byte [] data, int offset, float value)
    {
        int bits = Float.floatToRawIntBits (value);
        return set32bitLE (data, offset, bits);
    }


    public static byte [] set64bitLE (byte [] data, int offset, long value)
    {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        data[offset + 3] = (byte) (value >> 24);
        data[offset + 4] = (byte) (value >> 32);
        data[offset + 5] = (byte) (value >> 40);
        data[offset + 6] = (byte) (value >> 48);
        data[offset + 7] = (byte) (value >> 56);
        return data;
    }


    public static byte [] set64bitLE (byte [] data, int offset, double value)
    {
        long bits = Double.doubleToRawLongBits (value);
        return set64bitLE (data, offset, bits);
    }


    public static byte [] set16bitBE (byte [] data, short value)
    {
        return set16bitBE (data, 0, value);
    }


    public static byte [] set24bitBE (byte [] data, int value)
    {
        return set24bitBE (data, 0, value);
    }


    public static byte [] set32bitBE (byte [] data, int value)
    {
        return set32bitBE (data, 0, value);
    }


    public static byte [] set32bitBE (byte [] data, float value)
    {
        return set32bitBE (data, 0, value);
    }


    public static byte [] set64bitBE (byte [] data, long value)
    {
        return set64bitBE (data, 0, value);
    }


    public static byte [] set64bitBE (byte [] data, double value)
    {
        return set64bitBE (data, 0, value);
    }


    public static byte [] set16bitLE (byte [] data, short value)
    {
        return set16bitLE (data, 0, value);
    }


    public static byte [] set24bitLE (byte [] data, int value)
    {
        return set24bitLE (data, 0, value);
    }


    public static byte [] set32bitLE (byte [] data, int value)
    {
        return set32bitLE (data, 0, value);
    }


    public static byte [] set32bitLE (byte [] data, float value)
    {
        return set32bitLE (data, 0, value);
    }


    public static byte [] set64bitLE (byte [] data, long value)
    {
        return set64bitLE (data, 0, value);
    }


    public static byte [] set64bitLE (byte [] data, double value)
    {
        return set64bitLE (data, 0, value);
    }
}
