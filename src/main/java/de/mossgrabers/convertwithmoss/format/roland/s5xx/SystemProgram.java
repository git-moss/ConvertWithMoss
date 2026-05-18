package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.ResourceLoader;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class SystemProgram extends Struct
{
    public static final byte [] OS_V1_13;

    private final byte []       data = new byte [0176000];

    static
    {
        try
        {
            OS_V1_13 = ResourceLoader.load (SystemProgram.class, "resources/osv113.img");
        }
        catch (IOException e)
        {
            throw new RuntimeException (e);
        }
    }


    public SystemProgram ()
    {
        this.setData (OS_V1_13);
    }


    public byte [] getData ()
    {
        return this.data;
    }


    public void setData (byte [] data)
    {
        if (data.length != this.data.length)
            throw new IllegalArgumentException ("invalid length");
        System.arraycopy (data, 0, this.data, 0, this.data.length);
    }


    public String getInfo ()
    {
        return new String (this.data, 4, 10, StandardCharsets.US_ASCII).trim ();
    }


    public String getVersion ()
    {
        return new String (this.data, 32, 31, StandardCharsets.US_ASCII).trim ();
    }


    public String getOSVersion ()
    {
        return new String (this.data, 96, 31, StandardCharsets.US_ASCII).trim ();
    }


    public String getROMVersion ()
    {
        return new String (this.data, 128, 31, StandardCharsets.US_ASCII).trim ();
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        in.read (this.data);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        out.write (this.data);
    }


    @Override
    public String toString ()
    {
        return "SystemProgram[" + this.getInfo () + ", " + this.getVersion () + "; " + this.getOSVersion () + "; " + this.getROMVersion () + "]";
    }
}
