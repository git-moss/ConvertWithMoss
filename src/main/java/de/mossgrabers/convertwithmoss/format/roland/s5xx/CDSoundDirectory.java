package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class CDSoundDirectory
{
    public static final byte []               EMPTY   =
    {
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1
    };
    private final List<CDSoundDirectoryEntry> entries = new ArrayList<> ();


    public int getCount ()
    {
        return this.entries.size ();
    }


    public int getSize ()
    {
        int bytes = this.entries.size () * CDSoundDirectoryEntry.SIZE;
        int sectors = bytes / CDImage.SECTOR_SIZE;
        if (bytes % CDImage.SECTOR_SIZE != 0)
            sectors++;
        return sectors;
    }


    public CDSoundDirectoryEntry get (int i)
    {
        return this.entries.get (i);
    }


    public void add (CDSoundDirectoryEntry entry)
    {
        this.entries.add (entry);
    }


    public void remove (int i)
    {
        this.entries.remove (i);
    }


    public void read (WordInputStream in, int size) throws IOException
    {
        int count = size * CDImage.SECTOR_SIZE / CDSoundDirectoryEntry.SIZE;
        for (int i = 0; i < count; i++)
        {
            CDSoundDirectoryEntry entry = new CDSoundDirectoryEntry ();
            entry.read (in);
            if (entry.getName ().charAt (0) == 0xFF)
                continue;
            else
                this.entries.add (entry);
        }
    }


    public void write (WordOutputStream out) throws IOException
    {
        int cnt = this.entries.size ();
        int count = this.getSize () * CDImage.SECTOR_SIZE / CDSoundDirectoryEntry.SIZE;
        int i;
        for (i = 0; i < cnt; i++)
            this.entries.get (i).write (out);
        for (; i < count; i++)
            out.write (EMPTY);
    }
}
