// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.epsasr;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Represents a single file entry in an Ensoniq disk image directory.
 *
 * @author Jürgen Moßgraber
 */
public class EnsoniqFile
{
    /** Empty file reference. */
    public static final int                   TYPE_UNUSED      = 0;
    /** Operating System file. */
    public static final int                   TYPE_OS          = 1;
    /** Sub-directory. */
    public static final int                   TYPE_SUBDIR      = 2;
    /** EPS Instrument file. */
    public static final int                   TYPE_EPS_INST    = 3;
    /** EPS Bank file. */
    public static final int                   TYPE_EPS_BANK    = 4;
    /** EPS Sequence file. */
    public static final int                   TYPE_EPS_SEQ     = 5;
    /** EPS Song file. */
    public static final int                   TYPE_EPS_SONG    = 6;
    /** EPS System Exclusive file. */
    public static final int                   TYPE_EPS_SYSEX   = 7;
    /** Points to the parent folder. */
    public static final int                   TYPE_PARENT_PTR  = 8;
    /** EPS Macro file. */
    public static final int                   TYPE_EPS_MACRO   = 9;

    // Additional SD / VFX-SD types: 10–22 (omitted for brevity)

    /** EPS 16+ Bank file. */
    public static final int                   TYPE_16PLUS_BANK = 23;
    /** EPS 16+ FX file. */
    public static final int                   TYPE_16PLUS_FX   = 24;
    /** EPS 16+ Sequence file. */
    public static final int                   TYPE_16PLUS_SEQ  = 25;
    /** EPS 16+ Song file. */
    public static final int                   TYPE_16PLUS_SONG = 26;
    /** EPS 16+ OS file. */
    public static final int                   TYPE_16PLUS_OS   = 27;
    /** ASR Sequence file. */
    public static final int                   TYPE_ASR_SEQ     = 28;
    /** ASR Song file. */
    public static final int                   TYPE_ASR_SONG    = 29;
    /** ASR Bank file. */
    public static final int                   TYPE_ASR_BANK    = 30;
    /** ASR Track file. */
    public static final int                   TYPE_ASR_TRACK   = 31;
    /** ASR OS file. */
    public static final int                   TYPE_ASR_OS      = 32;
    /** ASR FX file. */
    public static final int                   TYPE_ASR_FX      = 33;
    /** ASR Macro file. */
    public static final int                   TYPE_ASR_MACRO   = 34;

    /** The instrument file types. */
    public static final List<Integer>         INSTRUMENT_TYPES = Collections.singletonList (Integer.valueOf (TYPE_EPS_INST));
    /** The bank file types. */
    public static final List<Integer>         BANK_TYPES       = Arrays.asList (Integer.valueOf (TYPE_EPS_BANK), Integer.valueOf (TYPE_16PLUS_BANK), Integer.valueOf (TYPE_ASR_BANK));
    /** The song file types. */
    public static final List<Integer>         SONG_TYPES       = Arrays.asList (Integer.valueOf (TYPE_EPS_SONG), Integer.valueOf (TYPE_16PLUS_SONG), Integer.valueOf (TYPE_ASR_SONG));
    /** The sequence file types. */
    public static final List<Integer>         SEQUENCE_TYPES   = Arrays.asList (Integer.valueOf (5), Integer.valueOf (17), Integer.valueOf (18), Integer.valueOf (19), Integer.valueOf (25), Integer.valueOf (28));

    private static final Map<Integer, String> TYPE_MAP;
    static
    {
        final Map<Integer, String> m = new LinkedHashMap<> ();
        m.put (Integer.valueOf (0), "UNUSED");
        m.put (Integer.valueOf (1), "EPS OS");
        m.put (Integer.valueOf (2), "SUBDIR");
        m.put (Integer.valueOf (3), "EPS INST");
        m.put (Integer.valueOf (4), "EPS BANK");
        m.put (Integer.valueOf (5), "EPS SEQ");
        m.put (Integer.valueOf (6), "EPS SONG");
        m.put (Integer.valueOf (7), "EPS SYSEX");
        m.put (Integer.valueOf (8), "PTR PRNT");
        m.put (Integer.valueOf (9), "EPS MACRO");
        m.put (Integer.valueOf (17), "SD SEQ");
        m.put (Integer.valueOf (18), "SD SEQ");
        m.put (Integer.valueOf (19), "SD SEQ");
        m.put (Integer.valueOf (23), "16+ BANK");
        m.put (Integer.valueOf (24), "16+ FX");
        m.put (Integer.valueOf (25), "16+ SEQ");
        m.put (Integer.valueOf (26), "16+ SONG");
        m.put (Integer.valueOf (27), "16+ OS");
        m.put (Integer.valueOf (28), "ASR SEQ");
        m.put (Integer.valueOf (29), "ASR SONG");
        m.put (Integer.valueOf (30), "ASR BANK");
        m.put (Integer.valueOf (31), "ASR TRACK");
        m.put (Integer.valueOf (32), "ASR OS");
        m.put (Integer.valueOf (33), "ASR FX");
        m.put (Integer.valueOf (34), "ASR MACRO");
        TYPE_MAP = Collections.unmodifiableMap (m);
    }

    private final EnsoniqDisk         disk;
    private final int                 fileNumber;
    private final String              name;
    private final int                 type;
    private final int                 sizeBlocks;
    private final int                 contiguousBlocks;
    private final int                 firstBlock;
    private final EnsoniqFile         parent;
    private Map<Integer, EnsoniqFile> children;


    /**
     * Constructor.
     *
     * @param disk The disk which contains the file
     * @param fileNumber The number of the file, 0-based
     * @param name The name of the file
     * @param type The type of the file, see the TYPE_* constants
     * @param sizeBlocks The number of blocks of the file
     * @param contiguousBlocks The number of contiguous blocks of the file
     * @param firstBlock The first block of the file
     * @param parent The parent directory of the file
     */
    public EnsoniqFile (final EnsoniqDisk disk, final int fileNumber, final String name, final int type, final int sizeBlocks, final int contiguousBlocks, final int firstBlock, final EnsoniqFile parent)
    {
        this.disk = disk;
        this.fileNumber = fileNumber;
        this.name = name;
        this.type = type;
        this.sizeBlocks = sizeBlocks;
        this.contiguousBlocks = contiguousBlocks;
        this.firstBlock = firstBlock;
        this.parent = parent;
        if (type == TYPE_SUBDIR)
            this.children = new TreeMap<> ();
    }


    /**
     * Set the children of a directory.
     *
     * @param children The children
     */
    public void setChildren (final Map<Integer, EnsoniqFile> children)
    {
        if (this.type != TYPE_SUBDIR)
            throw new IllegalStateException ("setChildren on non-directory: " + this.name);
        this.children = children;
    }


    /**
     * Reads this file's raw bytes from the disk image.
     *
     * @return The data
     * @throws IOException Could not read the data
     */
    public byte [] readData () throws IOException
    {
        return this.disk.readEnsoniqFile (this);
    }


    /**
     * Returns a space-separated path of file numbers with the disk label as root. Example:
     * {@code "DSK001 03 07"}
     *
     * @return The path of the file
     */
    public String getPath ()
    {
        if (this.parent == null)
            return this.disk.getDiskLabel () != null ? this.disk.getDiskLabel () : "";
        final String pp = this.parent.getPath ();
        final String me = String.format ("%02d", Integer.valueOf (this.fileNumber));
        return pp.isEmpty () ? me : pp + " " + me;
    }


    /**
     * Get the file type as a readable text.
     *
     * @return The text
     */
    public String getTypeAsText ()
    {
        return TYPE_MAP.getOrDefault (Integer.valueOf (this.type), String.format ("UNKWN %03d", Integer.valueOf (this.type)));
    }


    /**
     * Get the number of the file in the directory.
     *
     * @return The file number
     */
    public int getFileNumber ()
    {
        return this.fileNumber;
    }


    /**
     * Get the name of the file.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the type of the file.
     *
     * @return The type, see the TYPE_* constants
     */
    public int getType ()
    {
        return this.type;
    }


    /**
     * Get the number of blocks over which the file stretches.
     *
     * @return The number of blocks
     */
    public int getNumberOfBlocks ()
    {
        return this.sizeBlocks;
    }


    /**
     * Get the number of contiguous blocks.
     *
     * @return The number of contiguous blocks
     */
    public int getContiguousBlocks ()
    {
        return this.contiguousBlocks;
    }


    /**
     * Get the first block of the file.
     *
     * @return The index of the first block
     */
    public int getFirstBlock ()
    {
        return this.firstBlock;
    }


    /**
     * Get the parent directory of the file.
     *
     * @return The parent directory
     */
    public EnsoniqFile getParent ()
    {
        return this.parent;
    }


    /**
     * Get the children of the file if it is a directory.
     *
     * @return The children or an empty map if it is not a directory
     */
    public Map<Integer, EnsoniqFile> getChildren ()
    {
        return this.children != null ? Collections.unmodifiableMap (this.children) : Collections.emptyMap ();
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return this.getPath () + " - " + this.getTypeAsText () + " - " + this.name;
    }
}