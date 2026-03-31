// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;


/**
 * Base class for all Akai format disk elements (e.g. program or key-group).
 *
 * @author Jürgen Moßgraber
 */
public abstract class AkaiDiskElement
{
    /** Size of a block. */
    public static final int    AKAI_BLOCK_SIZE        = 0x2000;

    /** Offset to the directory root entry. */
    protected static final int AKAI_ROOT_ENTRY_OFFSET = 0x00;
    /** Offset to the FAT on the partition. */
    protected static final int AKAI_FAT_OFFSET        = 0x70A;
    /** The offset to the directory entries. */
    protected static final int AKAI_DIR_ENTRY_OFFSET  = 0xCA;
    /** The size of a directory entry. */
    protected static final int AKAI_DIR_ENTRY_SIZE    = 16;
    /** The size of a file entry in the directory. */
    protected static final int AKAI_FILE_ENTRY_SIZE   = 24;

    /** Type ID for an empty/unused volume. */
    protected static final int AKAI_VOLUME_NOT_USED   = 0;
    /** Type ID for S1000 format. */
    protected static final int AKAI_VOLUME_TYPE_S1000 = 1;
    /** Type ID for S3000 format. */
    protected static final int AKAI_VOLUME_TYPE_S3000 = 7;

    /** ID for a program structure. */
    protected static final int AKAI_PROGRAM_ID        = 1;
    /** ID for a key-group structure. */
    protected static final int AKAI_KEYGROUP_ID       = 2;
    /** ID for a sample structure. */
    protected static final int AKAI_SAMPLE_ID         = 3;

    private int                offset;


    /**
     * Constructor.
     */
    public AkaiDiskElement ()
    {
        this (0);
    }


    /**
     * Constructor.
     * 
     * @param offset The offset of the element on the disk
     */
    public AkaiDiskElement (final int offset)
    {
        this.offset = offset;
    }


    /**
     * Get the offset.
     * 
     * @return The offset
     */
    public int getOffset ()
    {
        return this.offset;
    }


    /**
     * Set the offset.
     * 
     * @param offset The offset
     */
    protected void setOffset (final int offset)
    {
        this.offset = offset;
    }


    protected int readFAT (final AkaiS1000DiskImage disk, final AkaiPartition partition, final int block) throws IOException
    {
        disk.setPos (partition.getOffset () + AKAI_FAT_OFFSET + block * 2, AkaiStreamWhence.START);
        return disk.readInt16 ();
    }


    protected boolean readDirEntry (final AkaiS1000DiskImage disk, final AkaiPartition partition, final AkaiDirEntry entry, final int block, final int pos) throws IOException
    {
        if (block == AKAI_ROOT_ENTRY_OFFSET)
        {
            disk.setPos (partition.getOffset () + AKAI_DIR_ENTRY_OFFSET + pos * AKAI_DIR_ENTRY_SIZE, AkaiStreamWhence.START);
            entry.setName (disk.readText ());
            entry.setType (disk.readInt16 ());
            entry.setStart (disk.readInt16 ());
            entry.setSize (0);
            return true;
        }

        if (pos < 341)
            disk.setPos (block * AKAI_BLOCK_SIZE + pos * AKAI_FILE_ENTRY_SIZE + partition.getOffset (), AkaiStreamWhence.START);
        else
        {
            final int temp = this.readFAT (disk, partition, block);
            disk.setPos (partition.getOffset () + temp * AKAI_BLOCK_SIZE + (pos - 341) * AKAI_FILE_ENTRY_SIZE, AkaiStreamWhence.START);
        }

        entry.setName (disk.readText ());

        disk.setPos (4, AkaiStreamWhence.CURRENT_POSITION);
        final byte t1 = disk.readInt8 ();
        entry.setType (t1 & 0xFF);

        final byte t2 = disk.readInt8 ();
        final byte t3 = disk.readInt8 ();
        final byte t4 = disk.readInt8 ();
        entry.setSize (t2 & 0xFF | (t3 & 0xFF) << 8 | (t4 & 0xFF) << 16);

        entry.setStart (disk.readInt16 ());
        return true;
    }
}