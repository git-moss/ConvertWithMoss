package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class CDSectionHeader extends Struct
{
    private final RawString name = new RawString (16);
    private int             offset;
    private int             size;


    public int getOffset ()
    {
        return this.offset;
    }


    public int getSize ()
    {
        return this.size;
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        this.name.read (in);
        this.offset = in.read32bit ();
        this.size = in.read32bit ();
        in.skip (8);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        this.name.write (out);
        out.write32bit (this.offset);
        out.write32bit (this.size);
        byte [] unknown = new byte [8];
        out.write (unknown);
    }
}
