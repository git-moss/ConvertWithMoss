package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public abstract class Struct
{
    protected static byte [] getArray (byte [] data)
    {
        return Arrays.copyOf (data, data.length);
    }


    protected static void setArray (byte [] param, byte [] member)
    {
        if (param.length != member.length)
            throw new IllegalArgumentException ("invalid length");
        System.arraycopy (param, 0, member, 0, member.length);
    }


    public abstract void read (WordInputStream in) throws IOException;


    public abstract void write (WordOutputStream out) throws IOException;
}
