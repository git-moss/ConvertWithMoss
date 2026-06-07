// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 diskette directory area.
 *
 * @author Jürgen Moßgraber
 */
public class S770DisketteDirectoryArea
{
    private static final int               ENTRIES_SIZE           = 0x400;
    private static final int               NAMES_PERFORMANCE_SIZE = 0x400;
    private static final int               NAMES_PATCHES_SIZE     = 0x800;
    private static final int               NAMES_PARTIALS_SIZE    = 0x1000;
    private static final int               NAMES_SAMPLES_SIZE     = 0x2000;

    private final List<S770DirectoryEntry> performanceDirectories;
    private final List<S770DirectoryEntry> patchDirectories;
    private final List<S770DirectoryEntry> partialDirectories;
    private final List<S770DirectoryEntry> sampleDirectories;


    /**
     * Reads all directory entries for each object type.
     *
     * @param input Stream positioned at the first directory entry (0x200)
     * @param numPerformances Number of performance directory entries to read
     * @param numPatches Number of patch directory entries to read
     * @param numPartials Number of partial directory entries to read
     * @param numSamples Number of sample directory entries to read
     * @throws IOException on read error
     */
    public S770DisketteDirectoryArea (final InputStream input, final int numPerformances, final int numPatches, final int numPartials, final int numSamples) throws IOException
    {
        final int [] performanceEntries = readEntries (input, numPerformances);
        final int [] patchEntries = readEntries (input, numPatches);
        final int [] partialEntries = readEntries (input, numPartials);
        final int [] sampleEntries = readEntries (input, numSamples);

        this.performanceDirectories = readEntryNames (input, numPerformances, S770FileType.PERFORMANCE, performanceEntries, NAMES_PERFORMANCE_SIZE);
        this.patchDirectories = readEntryNames (input, numPatches, S770FileType.PATCH, patchEntries, NAMES_PATCHES_SIZE);
        this.partialDirectories = readEntryNames (input, numPartials, S770FileType.PARTIAL, partialEntries, NAMES_PARTIALS_SIZE);
        this.sampleDirectories = readEntryNames (input, numSamples, S770FileType.SAMPLE, sampleEntries, NAMES_SAMPLES_SIZE);
    }


    private static int [] readEntries (final InputStream input, final int count) throws IOException
    {
        final int [] result = new int [count];
        for (int i = 0; i < count; i++)
            result[i] = StreamUtils.readUnsigned16 (input, false);
        input.skipNBytes (ENTRIES_SIZE - 2 * count);
        return result;
    }


    private static List<S770DirectoryEntry> readEntryNames (final InputStream input, final int count, final S770FileType fileType, final int [] entries, final int size) throws IOException
    {
        final List<S770DirectoryEntry> result = new ArrayList<> (count);
        for (int i = 0; i < count; i++)
            result.add (new S770DirectoryEntry (input, fileType, entries[i]));
        input.skipNBytes (size - count * 16);
        return result;
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
        final StringBuilder sb = new StringBuilder ();
        sb.append ("S770DisketteDirectoryArea [\n");
        appendEntries (sb, "performances", this.performanceDirectories);
        appendEntries (sb, "patches", this.patchDirectories);
        appendEntries (sb, "partials", this.partialDirectories);
        appendEntries (sb, "samples", this.sampleDirectories);
        sb.append (']');
        return sb.toString ();
    }


    private static void appendEntries (final StringBuilder sb, final String label, final List<S770DirectoryEntry> entries)
    {
        sb.append ("  ").append (label).append (": ").append (entries.size ()).append (" entries\n");
        for (int i = 0; i < entries.size (); i++)
            sb.append ("    [").append (i).append ("] ").append (entries.get (i)).append ('\n');
    }
}