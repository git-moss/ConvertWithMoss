// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiDirEntry;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiDiskImage;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiPartition;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.IAkaiVolume;
import de.mossgrabers.tools.StringUtils;


/**
 * An AKAI volume is a further subdivision of an AKAI disk partition. An AKAI volume actually
 * provides access to the list of instruments (programs) and samples. Samples referenced by an
 * instrument (program) are always part of the same volume.
 */
public class AkaiS1000Volume implements IAkaiVolume
{
    /** Maximum number of directory file entries on Akai S1000 series. */
    public static final int              AKAI_MAX_FILE_ENTRIES_S1000 = 125;
    /** Maximum number of directory file entries on Akai S3000 series. */
    public static final int              AKAI_MAX_FILE_ENTRIES_S3000 = 509;

    private String                       name;
    private final List<AkaiS1000Program> programs                    = new ArrayList<> ();
    private final List<AkaiS1000Sample>  samples                     = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param disk The disk from which to read the volume
     * @param partition The partition which contains the volume
     * @param dirEntry The directory entry of the volume
     * @param isS3000 If it is an extended S3000 program (otherwise shorter S1000)
     * @throws IOException Could not read the volume
     */
    public AkaiS1000Volume (final AkaiDiskImage disk, final AkaiPartition partition, final AkaiDirEntry dirEntry, final boolean isS3000) throws IOException
    {
        this.name = dirEntry.getName ();

        final int hmm = partition.readFAT (dirEntry.getStart ());
        System.out.println (StringUtils.formatHexStr (hmm));

        final int maxFiles = isS3000 ? AKAI_MAX_FILE_ENTRIES_S3000 : AKAI_MAX_FILE_ENTRIES_S1000;
        for (int i = 0; i < maxFiles; i++)
        {
            final AkaiDirEntry entry = partition.readDirEntry (dirEntry.getStart (), i);
            final int dataPosition = partition.getOffset () + entry.getStart () * AkaiDiskImage.AKAI_BLOCK_SIZE;

            int type = entry.getType ();
            if (type >= 128 && isS3000)
                type -= 128;
            switch (type)
            {
                case 'p':
                    this.programs.add (new AkaiS1000Program (disk, dataPosition, isS3000));
                    break;
                case 's':
                    this.samples.add (new AkaiS1000Sample (disk, dataPosition));
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
        return this.name;
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasContent ()
    {
        return this.programs.size () + this.samples.size () > 0;
    }


    /**
     * Get all programs of the volume.
     *
     * @return The programs
     */
    public List<AkaiS1000Program> getPrograms ()
    {
        return this.programs;
    }


    /**
     * Get all samples of the volume.
     *
     * @return The samples
     */
    public List<AkaiS1000Sample> getSamples ()
    {
        return this.samples;
    }
}