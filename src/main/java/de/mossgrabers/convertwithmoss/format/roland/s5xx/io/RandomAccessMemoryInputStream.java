package de.mossgrabers.convertwithmoss.format.roland.s5xx.io;

import java.io.IOException;
import java.io.InputStream;


public class RandomAccessMemoryInputStream extends InputStream
{
    private final byte [] data;
    private int           offset;


    public RandomAccessMemoryInputStream (byte [] data)
    {
        this.data = data;
        offset = 0;
    }


    public void seek (int pos)
    {
        if (offset < 0 || offset >= data.length)
        {
            throw new IndexOutOfBoundsException ("invalid offset");
        }
        else
        {
            offset = pos;
        }
    }


    public int tell ()
    {
        return offset;
    }


    @Override
    public int read () throws IOException
    {
        if (offset == data.length)
        {
            offset = -1;
            return -1;
        }
        else if (offset > data.length || offset < 0)
        {
            return -1; // throw new EOFException();
        }
        else
        {
            return Byte.toUnsignedInt (data[offset++]);
        }
    }


    @Override
    public int read (byte [] b, int off, int len) throws IOException
    {
        if (b == null)
        {
            throw new NullPointerException ("b is null");
        }
        else if (off < 0 || len < 0 || off + len > b.length)
        {
            throw new IndexOutOfBoundsException ("invalid offset/length");
        }
        else if (offset < 0 || offset >= data.length)
        {
            return -1;
        }
        else
        {
            int rem = data.length - offset;
            if (rem >= len)
            {
                System.arraycopy (data, offset, b, off, len);
                offset += len;
                return len;
            }
            else
            {
                System.arraycopy (data, offset, b, off, rem);
                offset += rem;
                return rem;
            }
        }
    }
}
