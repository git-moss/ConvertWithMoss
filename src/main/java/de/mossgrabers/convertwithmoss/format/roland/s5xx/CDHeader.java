package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.RandomAccessMemoryInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;


public class CDHeader
{
    public static final byte []    MAGIC_DISC             =
    {
        '*',
        ' ',
        'R',
        'O',
        'L',
        'A',
        'N',
        'D',
        ' ',
        'S',
        '-',
        '5',
        '5',
        '0',
        ' ',
        '*',
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
    public static final byte []    MAGIC_INSTRUMENT_GROUP =
    {
        'I',
        'n',
        's',
        't',
        'r',
        'u',
        'm',
        'e',
        'n',
        't',
        ' ',
        'G',
        'r',
        'o',
        'u',
        'p'
    };
    public static final byte []    MAGIC_SOUND_DIRECTORY  =
    {
        'S',
        'o',
        'u',
        'n',
        'd',
        ' ',
        'D',
        'i',
        'r',
        'e',
        'c',
        't',
        'o',
        'r',
        'y',
        ' '
    };
    public static final byte []    MAGIC_INSTRUMENT_MAP   =
    {
        'm',
        'a',
        'p',
        '1',
        ' ',
        'I',
        'n',
        's',
        't',
        'r',
        'u',
        'm',
        'e',
        'n',
        't',
        ' '
    };

    private final RawString []     info;
    private final CDSectionHeader  instrumentGroup        = new CDSectionHeader ();
    private final CDSectionHeader  soundDirectory         = new CDSectionHeader ();
    private final CDSectionHeader  map1Instrument         = new CDSectionHeader ();
    private final CDSoundDirectory soundDirectoryData     = new CDSoundDirectory ();


    public CDHeader ()
    {
        this.info = new RawString [7];
        for (int i = 0; i < this.info.length; i++)
            this.info[i] = new RawString (32);
    }


    public String [] getInfo ()
    {
        String [] result = new String [this.info.length];
        for (int i = 0; i < this.info.length; i++)
            result[i] = this.info[i].get ();
        return result;
    }


    public CDSoundDirectory getSoundDirectory ()
    {
        return this.soundDirectoryData;
    }


    public void read (RandomAccessMemoryInputStream in) throws IOException
    {
        in.seek (0);
        WordInputStream win = new BEInputStream (in);
        byte [] magic = new byte [32];
        in.read (magic);
        if (!Arrays.equals (magic, MAGIC_DISC))
            throw new IOException ("Invalid disc magic");
        for (final RawString element: this.info)
            element.read (win);

        this.instrumentGroup.read (win);
        this.soundDirectory.read (win);
        this.map1Instrument.read (win);

        in.seek (this.soundDirectory.getOffset () * CDImage.SECTOR_SIZE);
        this.soundDirectoryData.read (win, this.soundDirectory.getSize ());
    }


    @Override
    public String toString ()
    {
        return String.format ("CD[igrp=0x%x,0x%x;sd=0x%x,0x%x;map1=0x%x,0x%x]", this.instrumentGroup.getOffset (), this.instrumentGroup.getSize (), this.soundDirectory.getOffset (), this.soundDirectory.getSize (), this.map1Instrument.getOffset (), this.map1Instrument.getSize ());
    }
}
