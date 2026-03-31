// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * A partition of an AKAI disk media or of an AKAI disk image file which contains several volumes.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiPartition extends AkaiDiskElement
{
    /** Maximum number of directory entries. */
    public static final int        AKAI_MAX_DIR_ENTRIES = 100;

    private String                 name;
    private final List<AkaiVolume> volumes              = new ArrayList<> ();


    /**
     * Constructor.
     * 
     * @param disk The Akai disk
     * @param partitionIndex The index of the partition
     * @throws IOException Could not read the volumes
     */
    public AkaiPartition (final AkaiS1000DiskImage disk, final int partitionIndex) throws IOException
    {
        this.name = String.valueOf ((char) ('A' + partitionIndex));

        for (int i = 0; i < AKAI_MAX_DIR_ENTRIES; i++)
        {
            final AkaiDirEntry dirEntry = new AkaiDirEntry ();
            this.readDirEntry (disk, this, dirEntry, AKAI_ROOT_ENTRY_OFFSET, i);
            dirEntry.setIndex (i);

            final int type = dirEntry.getType ();
            switch (type)
            {
                case AKAI_VOLUME_TYPE_S1000:
                case AKAI_VOLUME_TYPE_S3000:
                    final AkaiVolume volume = new AkaiVolume (disk, this, dirEntry);
                    if (!volume.isEmpty ())
                        this.volumes.add (volume);
                    break;

                case AKAI_VOLUME_NOT_USED:
                    // Ignore empty volumes
                    break;

                default:
                    // Ignore
                    break;
            }
        }
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
    public List<AkaiVolume> getVolumes ()
    {
        return this.volumes;
    }
}