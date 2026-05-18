package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class RawString extends Struct
{
    private final byte [] value;


    public RawString (int size)
    {
        this.value = new byte [size];
    }


    public void set (String s)
    {
        if (s.length () <= this.value.length)
        {
            int i;
            for (i = 0; i < s.length (); i++)
                this.value[i] = (byte) s.charAt (i);
            for (; i < this.value.length; i++)
                this.value[i] = ' ';
        }
        else
            throw new IllegalArgumentException ("String too long");
    }


    public String get ()
    {
        char [] chars = new char [this.value.length];
        for (int i = 0; i < this.value.length; i++)
            chars[i] = (char) (this.value[i] & 0xFF);
        return new String (chars);
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        in.read (this.value);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        out.write (this.value);
    }


    @Override
    public String toString ()
    {
        return "[" + this.get () + "]";
    }
}
