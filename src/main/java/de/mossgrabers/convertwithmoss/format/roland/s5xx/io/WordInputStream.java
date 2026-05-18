package de.mossgrabers.convertwithmoss.format.roland.s5xx.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;


public abstract class WordInputStream extends InputStream
{
    private InputStream parent;
    private boolean     eof    = false;
    private long        offset = 0;

    protected boolean   debug  = false;


    public WordInputStream (InputStream parent)
    {
        this.parent = parent;
    }


    public WordInputStream (InputStream parent, long offset)
    {
        this.parent = parent;
        this.offset = offset;
    }


    @Override
    public void close () throws IOException
    {
        parent.close ();
    }


    @Override
    public int available () throws IOException
    {
        return parent.available ();
    }


    @Override
    public long skip (long n) throws IOException
    {
        offset += n;
        return parent.skip (n);
    }


    @Override
    public synchronized void mark (int readlimit)
    {
        parent.mark (readlimit);
    }


    @Override
    public synchronized void reset () throws IOException
    {
        parent.reset ();
    }


    @Override
    public boolean markSupported ()
    {
        return parent.markSupported ();
    }


    public boolean isEOF ()
    {
        return eof;
    }


    @Override
    public int read () throws IOException
    {
        if (eof)
            throw new EOFException ();
        int result = parent.read ();
        if (result == -1)
        {
            eof = true;
            throw new EOFException ();
        }
        offset++;
        return result;
    }


    @Override
    public int read (byte [] buffer) throws IOException
    {
        if (eof)
            throw new EOFException ();
        int result = parent.read (buffer);
        if (result == -1)
        {
            eof = true;
            throw new EOFException ();
        }
        if (result != buffer.length)
        {
            throw new EOFException ();
        }
        offset += result;
        return result;
    }


    @Override
    public int read (byte [] buffer, int off, int length) throws IOException
    {
        if (eof)
            throw new EOFException ();
        int result = parent.read (buffer, off, length);
        if (result == -1)
        {
            eof = true;
            throw new EOFException ();
        }
        if (result != length)
        {
            throw new EOFException ();
        }
        offset += result;
        return result;
    }


    public byte [] read (int length) throws IOException
    {
        byte [] buf = new byte [length];
        read (buf);
        return buf;
    }


    public long tell ()
    {
        return offset;
    }


    public byte read8bit () throws IOException
    {
        if (debug)
        {
            int r = read ();
            if (r == -1)
            {
                throw new EOFException ();
            }
            else
            {
                System.out.println ("u8: " + Byte.toUnsignedInt ((byte) r) + " (s8: " + (byte) r + "; bin: " + Integer.toString (r, 2) + ")");
                return (byte) r;
            }
        }
        int val = read ();
        if (val == -1)
        {
            throw new EOFException ();
        }
        else
        {
            return (byte) val;
        }
    }


    public abstract short read16bit () throws IOException;


    public abstract int read32bit () throws IOException;


    public abstract long read64bit () throws IOException;


    public void debug ()
    {
        this.debug = true;
    }


    public boolean isDebug ()
    {
        return debug;
    }
}
