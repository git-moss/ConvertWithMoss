// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.diskformat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * A partition of an AKAI disk media or of an AKAI disk image file which contains several volumes.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiPartition
{
    /** Maximum number of directory entries. */
    private static final int        AKAI_MAX_DIR_ENTRIES   = 100;
    /** Offset to the directory root entry. */
    private static final int        AKAI_ROOT_ENTRY_OFFSET = 0x00;
    /** Offset to the FAT on the partition. */
    private static final int        AKAI_FAT_OFFSET        = 0x70A;
    /** The offset to the directory entries. */
    private static final int        AKAI_DIR_ENTRY_OFFSET  = 0xCA;
    /** The size of a directory entry. */
    private static final int        AKAI_DIR_ENTRY_SIZE    = 16;
    /** The size of a file entry in the directory. */
    private static final int        AKAI_FILE_ENTRY_SIZE   = 24;

    private final AkaiDiskImage     disk;
    private final int               offset;
    private final String            name;
    private final List<IAkaiVolume> volumes                = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param disk The Akai disk
     * @param offset The offset on the disk to the partition
     * @param partitionIndex The index of the partition
     * @throws IOException Could not read the volumes
     */
    public AkaiPartition (final AkaiDiskImage disk, final int offset, final int partitionIndex) throws IOException
    {
        this.disk = disk;
        this.offset = offset;

        this.name = String.valueOf ((char) ('A' + partitionIndex));

        for (int i = 0; i < AKAI_MAX_DIR_ENTRIES; i++)
        {
            final AkaiDirEntry dirEntry = this.readDirEntry (AKAI_ROOT_ENTRY_OFFSET, i);
            final Optional<IAkaiVolume> volumeOpt = disk.readVolume (this, dirEntry);
            if (volumeOpt.isEmpty ())
                continue;
            final IAkaiVolume volume = volumeOpt.get ();
            if (volume.hasContent ())
                this.volumes.add (volume);
        }
    }


    /**
     * Read from the File Allocation Table (FAT).
     *
     * @param block The staring block
     * @return The FAT value
     * @throws IOException Could not read
     */
    public int readFAT (final int block) throws IOException
    {
        this.disk.setPosition (this.offset + AKAI_FAT_OFFSET + block * 2, AkaiStreamWhence.START);
        return this.disk.readInt16 ();
    }


    /**
     * Read a directory entry.
     *
     * @param block The starting block which contains the entry
     * @param pos The index of the entry
     * @return The entry
     * @throws IOException Could not read the entry
     */
    public AkaiDirEntry readDirEntry (final int block, final int pos) throws IOException
    {
        final AkaiDirEntry entry = new AkaiDirEntry ();
        entry.setIndex (pos);

        if (block == AKAI_ROOT_ENTRY_OFFSET)
        {
            this.disk.setPosition (this.offset + AKAI_DIR_ENTRY_OFFSET + pos * AKAI_DIR_ENTRY_SIZE, AkaiStreamWhence.START);
            entry.setName (this.disk.readText ());
            entry.setType (this.disk.readInt16 ());
            entry.setStart (this.disk.readInt16 ());
            entry.setSize (0);
            return entry;
        }

        if (pos < 341)
            this.disk.setPosition (this.offset + block * AkaiDiskImage.AKAI_BLOCK_SIZE + pos * AKAI_FILE_ENTRY_SIZE, AkaiStreamWhence.START);
        else
        {
            final int temp = this.readFAT (block);
            this.disk.setPosition (this.offset + temp * AkaiDiskImage.AKAI_BLOCK_SIZE + (pos - 341) * AKAI_FILE_ENTRY_SIZE, AkaiStreamWhence.START);
        }

        entry.setName (this.disk.readText ());

        this.disk.setPosition (4, AkaiStreamWhence.CURRENT_POSITION);
        final byte t1 = this.disk.readInt8 ();
        entry.setType (t1 & 0xFF);

        final byte t2 = this.disk.readInt8 ();
        final byte t3 = this.disk.readInt8 ();
        final byte t4 = this.disk.readInt8 ();
        entry.setSize (t2 & 0xFF | (t3 & 0xFF) << 8 | (t4 & 0xFF) << 16);

        entry.setStart (this.disk.readInt16 ());
        return entry;
    }


    /**
     * Get the name of the partition.
     *
     * @return A, B, C, ...
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get all volumes of the partition.
     *
     * @return The volumes
     */
    public List<IAkaiVolume> getVolumes ()
    {
        return this.volumes;
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
}