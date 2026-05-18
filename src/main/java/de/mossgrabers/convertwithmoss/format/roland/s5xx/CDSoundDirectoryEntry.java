package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


/*
 * struct dirent { u8 name[32]; u8 shortname[16]; u32 offset; // multiply by 0x200 u32 size; //
 * multiply by 0x200 u8 unknown; u8 group; u8 pad[6]; };
 */
public class CDSoundDirectoryEntry extends Struct
{
    public static final int SIZE      = 64;

    private final RawString name      = new RawString (32);
    private final RawString shortname = new RawString (16);
    private int             offset;
    private int             size;
    private byte            group;


    public String getName ()
    {
        return this.name.get ();
    }


    public String getShortName ()
    {
        return this.shortname.get ();
    }


    public int getOffset ()
    {
        return this.offset;
    }


    public int getSize ()
    {
        return this.size;
    }


    public byte getGroup ()
    {
        return this.group;
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        this.name.read (in);
        this.shortname.read (in);
        this.offset = in.read32bit ();
        this.size = in.read32bit ();
        in.skip (1);
        this.group = in.read8bit ();
        in.skip (6);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        this.name.write (out);
        this.shortname.write (out);
        out.write32bit (this.offset);
        out.write32bit (this.size);
        out.write (0x41);
        out.write (this.group);
        byte [] pad =
        {
            -1,
            -1,
            -1,
            -1,
            -1,
            -1
        };
        out.write (pad);
    }


    @Override
    public String toString ()
    {
        return "SoundEntry[name=\"" + this.getName () + "\",shortname=\"" + this.getShortName () + "\",offset=" + this.getOffset () + ",size=" + this.getSize () + ",group=" + this.getGroup () + "]";
    }
}
