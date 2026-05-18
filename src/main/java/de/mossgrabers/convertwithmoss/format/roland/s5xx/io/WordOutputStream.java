package de.mossgrabers.convertwithmoss.format.roland.s5xx.io;

import java.io.IOException;
import java.io.OutputStream;


public abstract class WordOutputStream extends OutputStream
{
    private OutputStream parent;
    private long         offset;


    public WordOutputStream (OutputStream parent)
    {
        this.parent = parent;
        this.offset = 0;
    }


    @Override
    public void close () throws IOException
    {
        parent.close ();
    }


    @Override
    public void flush () throws IOException
    {
        parent.flush ();
    }


    @Override
    public void write (int value) throws IOException
    {
        parent.write (value);
        offset++;
    }


    @Override
    public void write (byte [] buffer) throws IOException
    {
        parent.write (buffer);
        offset += buffer.length;
    }


    @Override
    public void write (byte [] buffer, int off, int length) throws IOException
    {
        parent.write (buffer, off, length);
        offset += length;
    }


    public void pad (int boundary, byte filler) throws IOException
    {
        long mod = offset % boundary;
        long missing = boundary - mod;
        if (mod == 0)
            return;
        byte [] pad = new byte [(int) missing];
        for (int i = 0; i < pad.length; i++)
            pad[i] = filler;
        write (pad);
    }


    public void pad32 () throws IOException
    {
        pad32 (0);
    }


    public void pad32 (int filler) throws IOException
    {
        pad (32, (byte) filler);
    }


    public long tell ()
    {
        return offset;
    }


    public void write (byte value, int n) throws IOException
    {
        if (n < 0)
        {
            throw new IllegalArgumentException ("cannot write negative amount of bytes");
        }
        else if (n == 0)
        {
            return;
        }
        else if (n == 1)
        {
            write (value);
        }
        else
        {
            byte [] buf = new byte [n];
            for (int i = 0; i < n; i++)
            {
                buf[i] = value;
            }
            write (buf);
        }
    }


    public abstract void write8bit (byte value) throws IOException;


    public abstract void write16bit (short value) throws IOException;


    public abstract void write32bit (int value) throws IOException;


    public abstract void write32bit (float value) throws IOException;


    public abstract void write64bit (long value) throws IOException;


    public abstract void write64bit (double value) throws IOException;
}
