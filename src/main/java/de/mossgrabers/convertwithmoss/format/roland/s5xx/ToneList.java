package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


/*
 * struct ToneList { u8 name[8]; u8 unknown1; u8 orgSubTone; u8 unknown2; u8 unknown3; u8 rootKey;
 * u8 unknown4; u8 unknown5; u8 unknown6; };
 */
public class ToneList extends Struct
{
    private final RawString name = new RawString (8);
    private byte            unknown1;
    private byte            orgSubTone;
    private byte            unknown2;
    private byte            unknown3;
    private byte            rootKey;
    private byte            unknown4;
    private byte            unknown5;
    private byte            unknown6;


    public void setName (String name)
    {
        this.name.set (name);
    }


    public String getName ()
    {
        return this.name.get ();
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        this.name.read (in);
        this.unknown1 = in.read8bit ();
        this.orgSubTone = in.read8bit ();
        this.unknown2 = in.read8bit ();
        this.unknown3 = in.read8bit ();
        this.rootKey = in.read8bit ();
        this.unknown4 = in.read8bit ();
        this.unknown5 = in.read8bit ();
        this.unknown6 = in.read8bit ();
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        this.name.write (out);
        out.write8bit (this.unknown1);
        out.write8bit (this.orgSubTone);
        out.write8bit (this.unknown2);
        out.write8bit (this.unknown3);
        out.write8bit (this.rootKey);
        out.write8bit (this.unknown4);
        out.write8bit (this.unknown5);
        out.write8bit (this.unknown6);
    }


    @Override
    public String toString ()
    {
        return "ToneList[" + this.getName () + "]";
    }
}
