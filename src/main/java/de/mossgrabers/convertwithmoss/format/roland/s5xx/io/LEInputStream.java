package de.mossgrabers.convertwithmoss.format.roland.s5xx.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;


public class LEInputStream extends WordInputStream
{
    private boolean debug = false;


    public LEInputStream (InputStream parent)
    {
        super (parent);
    }


    public LEInputStream (InputStream parent, long offset)
    {
        super (parent, offset);
    }


    @Override
    public short read16bit () throws IOException
    {
        byte [] buf = new byte [2];
        int n = read (buf);
        if (n == -1 || n != buf.length)
        {
            throw new EOFException ();
        }
        if (debug)
        {
            short r = Endianess.get16bitLE (buf);
            System.out.println ("u16: " + Short.toUnsignedInt (r) + " (s16: " + r + "; bin: " + Integer.toString (Short.toUnsignedInt (r), 2) + ")");
            return r;
        }
        return Endianess.get16bitLE (buf);
    }


    @Override
    public int read32bit () throws IOException
    {
        byte [] buf = new byte [4];
        int n = read (buf);
        if (n == -1 || n != buf.length)
        {
            throw new EOFException ();
        }
        if (debug)
        {
            int r = Endianess.get32bitLE (buf);
            System.out.println ("u32: " + Integer.toUnsignedString (r) + " (s32: " + r + "; bin: " + Integer.toUnsignedString (r, 2) + ")");
            return r;
        }
        return Endianess.get32bitLE (buf);
    }


    @Override
    public long read64bit () throws IOException
    {
        byte [] buf = new byte [8];
        int n = read (buf);
        if (n == -1 || n != buf.length)
        {
            throw new EOFException ();
        }
        if (debug)
        {
            long r = Endianess.get64bitLE (buf);
            System.out.println ("u64: " + Long.toUnsignedString (r) + " (s64: " + r + "; bin: " + Long.toUnsignedString (r, 2) + ")");
            return r;
        }
        return Endianess.get64bitLE (buf);
    }
}
