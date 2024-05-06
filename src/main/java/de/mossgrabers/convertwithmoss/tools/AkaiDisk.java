package de.mossgrabers.convertwithmoss.tools;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;


public class AkaiDisk
{
    private static final int                BLOCK_SIZE             = 0x2000;
    private static final int                MAX_FILE_ENTRIES_S1000 = 125;
    private static final int                MAX_FILE_ENTRIES_S3000 = 509;
    private static final int                FILE_ENTRY_SIZE        = 24;
    private static final int                MAX_DIR_ENTRIES        = 100;
    private static final int                DIR_ENTRY_OFFSET       = 0xca;
    private static final int                DIR_ENTRY_SIZE         = 16;
    private static final int                FAT_OFFSET             = 0x70a;
    private static final int                FAT_MARK_RESERVED      = 0x4000;
    private static final int                FAT_MARK_RESERVED2     = 0x8000;
    private static final int                FAT_MARK_FREE          = 0;
    private static final int                FAT_MARK_END           = 0xc000;
    private static final int                ROOT_ENTRY_OFFSET      = 0;
    private static final int                TYPE_FREE              = 0;
    private static final int                TYPE_DIR_S1000         = 1;
    private static final int                TYPE_DIR_S3000         = 3;
    private static final int                MAX_PART_SIZE          = 60;
    private static final String             DISK_HEADER            = "0000050d0a1a0f27143419411e4e235b28682d753282378f3c9c419ca946b64bc350d5532ea5ffb640e1e5ee";
    private static final String             PART_HEADER            = "00000f271e4e2d753c9c4bc35ae969117838875f9686a5adb4d4c3fbd222e149f070ff970ebf1de62c0d3b344a5b598268a977d086f7951ea4a45b36cc293d1bae0e1ef08fe2f0d571c7e2ba53acc49f3581a67417668858f94b6a3ddb204c12bd052df79eea0fdc70cef1b162a3d396448b5752b266d975008427934ea275b19cc0c3cfea1ded38fc5f0b871aae29d538fc4723564a6571749883bf92e6a10db034bf5bce82dda9ecd0fbf70a1f1946286d37946bb55e2640973308257917ea0a5afccbef3cd1adca0c41eb68fa8f09b718de2705362c4553547a63a172c881ef90169f3dae64bd8bccb2dbd9ea00f927084f1776269d35c444eb531262397160";

    private final File                      file;
    private final RandomAccessFile          randomAccessFile;
    private int                             part;
    private long                            offset;
    private int                             size;
    private final int                       capacity;

    private final HashMap<Integer, Integer> fatCache               = new HashMap<> ();


    public AkaiDisk (final String filename, final int part) throws IOException
    {
        this.file = new File (filename);
        this.randomAccessFile = new RandomAccessFile (this.file, "rw");
        this.part = part;

        this.offset = 0;
        while (true)
        {
            this.randomAccessFile.seek (this.offset);
            final byte [] buffer = new byte [2];
            this.randomAccessFile.readFully (buffer);
            this.size = buffer[0] & 0xFF | (buffer[1] & 0xFF) << 8;
            if (this.size == 0x8000)
                throw new IOException ("Partition not found");
            if (this.part <= 1)
                break;
            this.offset += this.size * BLOCK_SIZE;
            this.part--;
        }

        this.capacity = this.size; // TODO: Rewrite this logic
    }


    public void close () throws IOException
    {
        this.randomAccessFile.close ();
    }


    public void seek (final long pos) throws IOException
    {
        this.randomAccessFile.seek (this.offset + pos);
    }


    public void read (final byte [] buffer) throws IOException
    {
        this.randomAccessFile.readFully (buffer);
    }


    public void write (final byte [] buffer) throws IOException
    {
        this.randomAccessFile.write (buffer);
    }


    public static String asciiToAkai (String str)
    {
        str = String.format ("%-12s", str.toUpperCase ());
        str = str.replaceAll ("[0-9A-Z#+\\-./]", "[\\x00-\\x28]");
        str = str.replaceAll ("[\\x29-\\xff]", " ");
        return str;
    }


    public static String akaiToAscii (String str)
    {
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < str.length (); i++)
            sb.append (mapNumberToAscii (str.charAt (i)));
        return sb.toString ().trim ();

        // str = str.replaceAll ("[\\x00-\\x28]", "0-9A-Z#+\\-./");
        // str = str.replaceAll ("\\s+$", "");
        // return str;
    }


    private final HashMap<Integer, HashMap<Integer, HashMap<String, Object>>> entryCache = new HashMap<> ();


    public HashMap<String, Object> readEntry (final int block, final int pos) throws IOException
    {
        if (this.entryCache.containsKey (block) && this.entryCache.get (block).containsKey (pos))
            return new HashMap<> (this.entryCache.get (block).get (pos));

        String name;
        int type, start, size;
        if (block == ROOT_ENTRY_OFFSET)
        {
            this.seek (DIR_ENTRY_OFFSET + pos * DIR_ENTRY_SIZE);
            final byte [] buffer = new byte [DIR_ENTRY_SIZE];
            this.read (buffer);
            name = akaiToAscii (new String (buffer, 0, 12));
            System.out.println (name);
            type = buffer[12] & 0xFF;
            start = buffer[13] & 0xFF | (buffer[14] & 0xFF) << 8;
            size = 0;
        }
        else
        {
            if (pos < 341)
                this.seek (block * BLOCK_SIZE + pos * FILE_ENTRY_SIZE);
            else
            {
                final int fatEntry = this.readFAT (block);
                this.seek (fatEntry * BLOCK_SIZE + (pos - 341) * FILE_ENTRY_SIZE);
            }
            final byte [] buffer = new byte [FILE_ENTRY_SIZE];
            this.read (buffer);
            name = akaiToAscii (new String (buffer, 0, 12));
            type = buffer[16] & 0xFF;
            size = buffer[20] & 0xFF | (buffer[21] & 0xFF) << 8 | (buffer[22] & 0xFF) << 16;
            start = buffer[24] & 0xFF | (buffer[25] & 0xFF) << 8;
        }

        final HashMap<String, Object> entry = new HashMap<> ();
        if (type != 0)
        {
            entry.put ("name", name);
            entry.put ("type", type);
            entry.put ("start", start);
            entry.put ("size", size);
            entry.put ("where", block);
            entry.put ("at", pos);
        }
        this.entryCache.computeIfAbsent (block, k -> new HashMap<> ()).put (pos, entry);

        return new HashMap<> (entry);
    }


    public Map<String, Map<String, Object>> readAllEntries (final int block) throws IOException
    {
        final Map<String, Map<String, Object>> entries = new HashMap<> ();
        int nentries;
        if (block == ROOT_ENTRY_OFFSET)
            nentries = MAX_DIR_ENTRIES;
        else
        {
            final int fatEntry = this.readFAT (block);
            nentries = fatEntry == FAT_MARK_RESERVED ? MAX_FILE_ENTRIES_S1000 : MAX_FILE_ENTRIES_S3000;
        }
        for (int i = 0; i < nentries; i++)
        {
            final HashMap<String, Object> entry = this.readEntry (block, i);
            if (entry.containsKey ("type"))
                entries.put ((String) entry.get ("name"), new HashMap<> (entry));
        }
        return entries;
    }


    public int readFAT (final int block) throws IOException
    {
        if (this.fatCache.containsKey (block))
            return this.fatCache.get (block);

        this.seek (FAT_OFFSET + block * 2);
        final byte [] buffer = new byte [2];
        this.read (buffer);
        final int val = buffer[0] & 0xFF | (buffer[1] & 0xFF) << 8;
        this.fatCache.put (block, val);

        return val;
    }


    public static void main (final String [] args)
    {
        try
        {
            final AkaiDisk akaiDisk = new AkaiDisk ("C:\\Users\\mos\\Desktop\\Akai CD-ROM Sound Library\\Akai CD-ROM Sound Library Volume 1.iso", 1);
            final Map<String, Map<String, Object>> allEntries = akaiDisk.readAllEntries (0);
            akaiDisk.close ();
        }
        catch (final IOException e)
        {
            e.printStackTrace ();
        }
    }


    private static final char [] mapping =
    {
        '0',
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9',
        'A',
        'B',
        'C',
        'D',
        'E',
        'F',
        'G',
        'H',
        'I',
        'J',
        'K',
        'L',
        'M',
        'N',
        'O',
        'P',
        'Q',
        'R',
        'S',
        'T',
        'U',
        'V',
        'W',
        'X',
        'Y',
        'Z',
        '#',
        '+',
        '-',
        '.',
        '/'
    };


    public static char mapNumberToAscii (int number)
    {
        if (number >= 0x00 && number <= 0x28)
            return mapping[number];

        throw new IllegalArgumentException ("Number out of range");
    }
}