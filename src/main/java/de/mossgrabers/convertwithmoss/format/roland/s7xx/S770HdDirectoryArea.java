// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * Roland S-770 CD-Rom/HD Directory Area (0x6D000 bytes).
 *
 * @author Jürgen Moßgraber
 */
public class S770HdDirectoryArea
{
    private final List<S770DirectoryEntry> volumeDirectories;
    private final List<S770DirectoryEntry> performanceDirectories;
    private final List<S770DirectoryEntry> patchDirectories;
    private final List<S770DirectoryEntry> partialDirectories;
    private final List<S770DirectoryEntry> sampleDirectories;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read the directory
     */
    public S770HdDirectoryArea (final InputStream input) throws IOException
    {
        // 0x001000 → volume_directories (0x80 = 128 entries × 32 B)
        this.volumeDirectories = readDirectoryList (input, 0x0080);
        // 0x004000 → performance_directories (0x200 = 512 entries × 32 B)
        this.performanceDirectories = readDirectoryList (input, 0x0200);
        // 0x008000 → patch_directories (0x400 = 1024 entries × 32 B)
        this.patchDirectories = readDirectoryList (input, 0x0400);
        // 0x020000 → partial_directories (0x1000 = 4096 entries × 32 B)
        this.partialDirectories = readDirectoryList (input, 0x1000);
        // 0x040000 → sample_directories (0x2000 = 8192 entries × 32 B)
        this.sampleDirectories = readDirectoryList (input, 0x2000);
    }


    private static List<S770DirectoryEntry> readDirectoryList (final InputStream input, final int numEntries) throws IOException
    {
        final List<S770DirectoryEntry> directories = new ArrayList<> (numEntries);
        for (int i = 0; i < numEntries; i++)
            directories.add (new S770DirectoryEntry (input));
        return directories;
    }


    /**
     * Get the volume directories.
     *
     * @return The volume directories
     */
    public List<S770DirectoryEntry> getVolumeDirectories ()
    {
        return this.volumeDirectories;
    }


    /**
     * Get the performance directories.
     *
     * @return The performance directories
     */
    public List<S770DirectoryEntry> getPerformanceDirectories ()
    {
        return this.performanceDirectories;
    }


    /**
     * Get the patch directories.
     *
     * @return The patch directories
     */
    public List<S770DirectoryEntry> getPatchDirectories ()
    {
        return this.patchDirectories;
    }


    /**
     * Get the partial directories.
     *
     * @return The partial directories
     */
    public List<S770DirectoryEntry> getPartialDirectories ()
    {
        return this.partialDirectories;
    }


    /**
     * Get the sample directories.
     *
     * @return The sample directories
     */
    public List<S770DirectoryEntry> getSampleDirectories ()
    {
        return this.sampleDirectories;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770DirectoryArea [\n" + "  volumeDirectories=" + this.volumeDirectories + "\n" + "  performanceDirectories=" + this.performanceDirectories + "\n" + "  patchDirectories=" + this.patchDirectories + "\n" + "  partialDirectories=" + this.partialDirectories + "\n" + "  sampleDirectories=" + this.sampleDirectories + "\n]";
    }
}