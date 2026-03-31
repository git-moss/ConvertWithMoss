// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * An AKAI volume is a further subdivision of an AKAI disk partition. An AKAI volume actually
 * provides access to the list of instruments (programs) and samples. Samples referenced by an
 * instrument (program) are always part of the same volume.
 */
public class AkaiVolume extends AkaiDiskElement
{
    /** Maximum number of directory file entries on Akai S1000 series. */
    public static final int         AKAI_MAX_FILE_ENTRIES_S1000 = 125;
    /** Maximum number of directory file entries on Akai S3000 series. */
    public static final int         AKAI_MAX_FILE_ENTRIES_S3000 = 509;

    private final AkaiDirEntry      dirEntry;
    private final AkaiPartition     partition;

    private final List<AkaiProgram> programs                    = new ArrayList<> ();
    private final List<AkaiSample>  samples                     = new ArrayList<> ();


    /**
     * Constructor.
     * 
     * @param disk The disk from which to read the volume
     * @param partition The partition which contains the volume
     * @param dirEntry The directory entry of the volume
     * @throws IOException Could not read the volume
     */
    AkaiVolume (final AkaiS1000DiskImage disk, final AkaiPartition partition, final AkaiDirEntry dirEntry) throws IOException
    {
        this.partition = partition;
        this.dirEntry = dirEntry;

        final boolean isS3000 = disk.isS3000 ();

        this.readFAT (disk, partition, this.dirEntry.getStart ());
        final int maxFiles = isS3000 ? AKAI_MAX_FILE_ENTRIES_S3000 : AKAI_MAX_FILE_ENTRIES_S1000;
        for (int i = 0; i < maxFiles; i++)
        {
            final AkaiDirEntry entry = new AkaiDirEntry ();
            this.readDirEntry (disk, partition, entry, this.dirEntry.getStart (), i);
            entry.setIndex (i);

            int type = entry.getType ();
            if (type >= 128 && isS3000)
                type -= 128;
            switch (type)
            {
                case 'p':
                    this.programs.add (new AkaiProgram (disk, this, entry));
                    break;
                case 's':
                    this.samples.add (new AkaiSample (disk, this, entry));
                    break;
                case 0:
                    // Empty
                    break;
                default:
                    // Unused S3000 content types: q, x, d
                    break;
            }
        }
    }


    /**
     * Get the name of the volume.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.dirEntry.getName ();
    }


    /**
     * Get the partition.
     * 
     * @return The partition
     */
    public AkaiPartition getPartition ()
    {
        return this.partition;
    }


    /**
     * Check if the volume contains at least a program or a sample.
     * 
     * @return True if there is content
     */
    public boolean isEmpty ()
    {
        return this.programs.size () + this.samples.size () == 0;
    }


    /**
     * Get all programs of the volume.
     * 
     * @return The programs
     */
    public List<AkaiProgram> getPrograms ()
    {
        return this.programs;
    }


    /**
     * Get all samples of the volume.
     * 
     * @return The samples
     */
    public List<AkaiSample> getSamples ()
    {
        return this.samples;
    }
}