package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEOutputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.RandomAccessMemoryInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class CDImage
{
    public static final int SECTOR_SIZE = 512;
    private final CDHeader  header      = new CDHeader ();
    private byte []         disc;


    public CDImage ()
    {
        // nothing
    }


    public CDImage (byte [] disc) throws IOException
    {
        this.disc = disc;
        try (RandomAccessMemoryInputStream in = new RandomAccessMemoryInputStream (disc))
        {
            this.header.read (in);
        }
    }


    public CDSoundDirectory getSoundDirectory ()
    {
        return this.header.getSoundDirectory ();
    }


    public String getVirtualFloppyName (int i)
    {
        return this.getSoundDirectory ().get (i).getName ().trim ();
    }


    public String getVirtualFloppyShortName (int i)
    {
        return this.getSoundDirectory ().get (i).getName ().trim ();
    }


    public int getVirtualFloppyCount ()
    {
        return this.getSoundDirectory ().getCount ();
    }


    public CDVirtualFloppy getFloppy (int i) throws IOException
    {
        if (this.disc == null)
            throw new IllegalStateException ("no disc data available");
        if (i >= this.getVirtualFloppyCount ())
            throw new IndexOutOfBoundsException ("only " + this.getVirtualFloppyCount () + " virtual floppies available");

        CDVirtualFloppy floppy = new CDVirtualFloppy ();
        try (RandomAccessMemoryInputStream in = new RandomAccessMemoryInputStream (this.disc); WordInputStream win = new BEInputStream (in))
        {
            CDSoundDirectoryEntry floppyMetadata = this.getSoundDirectory ().get (i);
            int start = floppyMetadata.getOffset () * CDImage.SECTOR_SIZE;
            in.seek (start);
            int end = in.tell ();
            int size = end - start;
            assert size <= floppyMetadata.getSize () * CDImage.SECTOR_SIZE : "invalid size: " + size;
            floppy.read (win);
        }

        return floppy;
    }


    private static String sanitizePath (String name)
    {
        StringBuilder buf = new StringBuilder (name.length ());
        char last = 0;
        for (char c: name.toCharArray ())
        {
            if (c >= 'A' && c <= 'Z')
                buf.append (c);
            else if (c >= 'a' && c <= 'z')
                buf.append (c);
            else if (c >= '0' && c <= '9')
                buf.append (c);
            else if (c == ' ')
            {
                if (last == ' ')
                {
                    // nothing
                }
                else
                    buf.append (c);
            }
            else
                switch (c)
                {
                    case '_':
                    case '&':
                    case '+':
                    case '-':
                    case '.':
                    case ',':
                    case ';':
                    case '=':
                    case '$':
                    case '!':
                    case '\'':
                    case '#':
                    case '%':
                    case '(':
                    case ')':
                    case '[':
                    case ']':
                    case '{':
                    case '}':
                    case '^':
                    case '°':
                    case '~':
                        buf.append (c);
                        break;
                    default:
                        buf.append ('_');
                        break;
                }
            last = c;
        }
        return buf.toString ().trim ();
    }


    private static String fmt (int id, int width)
    {
        String s = Integer.toString (id);
        if (s.length () < width)
        {
            StringBuilder buf = new StringBuilder (width);
            for (int i = s.length (); i < width; i++)
                buf.append ('0');
            buf.append (s);
            return buf.toString ();
        }
        else
            return s;
    }


    public void extractAll (Path basePath, String prefix) throws IOException
    {
        int count = this.getVirtualFloppyCount ();
        SystemProgram systemProgram = new SystemProgram ();
        for (int i = 0; i < count; i++)
        {
            String name = this.getVirtualFloppyName (i);
            String id = fmt (i, 3);
            String filename = prefix + "_" + id + "_" + sanitizePath (name) + ".img";
            Path path = basePath.resolve (filename);
            System.out.printf ("extracting %s to %s...\n", name, path.toString ());
            CDVirtualFloppy floppy = this.getFloppy (i);
            try (WordOutputStream out = new BEOutputStream (new BufferedOutputStream (new FileOutputStream (path.toFile ()))))
            {
                floppy.writeFloppy (out, systemProgram);
            }
        }
    }


    private static void extract (String iso, String folder, String prefix) throws IOException
    {
        byte [] disc = Files.readAllBytes (Paths.get (iso));
        CDImage cd = new CDImage (disc);
        CDSoundDirectory dir = cd.getSoundDirectory ();
        System.out.printf ("%d entries in SoundDirectory\n", dir.getCount ());
        cd.extractAll (Paths.get (folder), prefix);
    }


    public static void write (short [] data, int sampleRate, String path) throws IOException
    {
        // RiffWave out = new RiffWave ();
        // out.set (new WaveFormatChunk ());
        // out.set (new DataChunk ());
        // out.setSampleRate (sampleRate);
        // out.setSampleFormat (WaveFormatChunk.WAVE_FORMAT_PCM);
        // out.setChannels (1);
        // out.setBitsPerSample (16);
        // out.set16bitSamples (data);
        // try (BufferedOutputStream wav = new BufferedOutputStream (new FileOutputStream (path)))
        // {
        // out.write (wav);
        // }
    }


    public static void main (String [] args) throws Exception
    {
        extract ("Y:\\Sample CDs\\Roland Sxx\\S-550\\Universe of Sounds - Vol. 1 [Roland S-5xx].iso", "Y:\\Sample CDs\\Roland Sxx\\S-550\\extracted", "UOS1");
        extract ("Y:\\Sample CDs\\Roland Sxx\\S-550\\Universe of Sounds Vol 2 [s500].iso", "Y:\\Sample CDs\\Roland Sxx\\S-550\\extracted", "UOS2");
        extract ("Y:\\Sample CDs\\Roland Sxx\\S-550\\Club50 Master Performance Vol. 1.iso", "Y:\\Sample CDs\\Roland Sxx\\S-550\\extracted", "C50MPV1");
        extract ("Y:\\Sample CDs\\Roland Sxx\\S-550\\L-CD1 S-50 Factory Library.iso", "Y:\\Sample CDs\\Roland Sxx\\S-550\\extracted", "LCD1");
    }
}
