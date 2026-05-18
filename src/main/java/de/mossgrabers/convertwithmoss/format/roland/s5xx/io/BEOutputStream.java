package de.mossgrabers.convertwithmoss.format.roland.s5xx.io;

import java.io.IOException;
import java.io.OutputStream;


public class BEOutputStream extends WordOutputStream
{
    public BEOutputStream (OutputStream parent)
    {
        super (parent);
    }


    @Override
    public void write8bit (byte value) throws IOException
    {
        write (value);
    }


    @Override
    public void write16bit (short value) throws IOException
    {
        write (Endianess.set16bitBE (new byte [2], value));
    }


    @Override
    public void write32bit (int value) throws IOException
    {
        write (Endianess.set32bitBE (new byte [4], value));
    }


    @Override
    public void write32bit (float value) throws IOException
    {
        write (Endianess.set32bitBE (new byte [4], value));
    }


    @Override
    public void write64bit (long value) throws IOException
    {
        write (Endianess.set64bitBE (new byte [8], value));
    }


    @Override
    public void write64bit (double value) throws IOException
    {
        write (Endianess.set64bitBE (new byte [8], value));
    }
}
