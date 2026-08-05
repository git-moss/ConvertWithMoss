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
 * Reads Roland S-770 CD-ROM or hard-disk images.
 *
 * <h3>On-disk layout (CD-ROM / HD)</h3> <pre>
 *   Offset      Size        Content
 *   0x000000    0x000200    ID Area        (512 B)
 *   0x000200    0x000600    Reserved Area  (1 536 B – skipped)
 *   0x000800    0x080000    Program Text   (512 KB – skipped)
 *   0x080800    0x020000    FAT Area       (128 KB)
 *   0x0A0800    0x06D000    Directory Area
 *   0x10D800    0x1A8000    Parameter Area
 *   0x2B5800    -           Audio Area
 * </pre>
 *
 * The audio area is addressed through the FAT: each FAT index references one segment of 0x2400
 * bytes, index 2 is the first segment of the audio area (index 0 holds the FAT ID, index 1 the
 * number of free segments). The FAT index of the first segment of a sample is stored in its
 * directory entry, the following segments are found by walking the FAT chain. On disks formatted
 * by an S-750/S-770 the audio area therefore normally starts at 0x2B5800 while on S-760 formatted
 * disks the first 114 segments hold additional OS data and audio starts at 0x3B6000.
 *
 * @author Jürgen Moßgraber
 */
public class S770Hd implements IS770Image
{
    private static final String         ENTRIES                 = " entries]\n";

    private static final long           SIZE_RESERVED           = 0x600L;
    private static final long           SIZE_PROGRAM_TEXT       = 0x80000L;

    /** Number of volume entries on a Roland S-770 disk. */
    public static final int             NUM_VOLUME_ENTRIES      = 128;
    /** Number of performance entries on a Roland S-770 disk. */
    public static final int             NUM_PERFORMANCE_ENTRIES = 512;
    /** Number of patch entries on a Roland S-770 disk. */
    public static final int             NUM_PATCH_ENTRIES       = 1024;
    /** Number of partial entries on a Roland S-770 disk. */
    public static final int             NUM_PARTIAL_ENTRIES     = 4096;
    /** Number of sample entries on a Roland S-770 disk. */
    public static final int             NUM_SAMPLE_ENTRIES      = 8192;

    private static final int            SAMPLE_BLOCK_SIZE       = 0x2400;

    /** The number of 16-bit entries in the FAT area. */
    private static final int            NUM_FAT_ENTRIES         = 0x10000;
    /** The first FAT index which references audio data. */
    private static final int            FIRST_DATA_FAT_INDEX    = 2;
    /** The highest FAT index which references audio data. */
    private static final int            LAST_DATA_FAT_INDEX     = 0xFFF5;
    /** FAT values from this one upwards mark the end of a segment chain. */
    private static final int            FAT_END_OF_CHAIN        = 0xFFF8;

    private final S770Header            header;
    private final S770HdDirectoryArea   directoryArea;
    private final int []                fatEntries              = new int [NUM_FAT_ENTRIES];
    private int                         numIncompleteSampleChains;

    private final List<S770Volume>      volumes                 = new ArrayList<> (NUM_VOLUME_ENTRIES);
    private final List<S770Performance> performances            = new ArrayList<> (NUM_PERFORMANCE_ENTRIES);
    private final List<S770Patch>       patches                 = new ArrayList<> (NUM_PATCH_ENTRIES);
    private final List<S770Partial>     partials                = new ArrayList<> (NUM_PARTIAL_ENTRIES);
    private final List<S770Sample>      samples                 = new ArrayList<> (NUM_SAMPLE_ENTRIES);


    /**
     * Parses a Roland S-770 CD-ROM / HD image from an already-open {@link InputStream}. The stream
     * must be positioned at byte 0 (start of the disk image). The caller is responsible for closing
     * the stream.
     *
     * @param in Stream positioned at the beginning of the disk image
     * @param header The already read header of the disk
     * @throws IOException if the stream cannot be read or is not a CD-ROM/HD format image
     */
    public S770Hd (final InputStream in, final S770Header header) throws IOException
    {
        this.header = header;

        // Skip the two non-parsed regions (Reserved / Program-Text) between the ID area and the
        // FAT area
        in.skipNBytes (SIZE_RESERVED + SIZE_PROGRAM_TEXT);
        for (int i = 0; i < NUM_FAT_ENTRIES; i++)
            this.fatEntries[i] = StreamUtils.readUnsigned16 (in, false);

        this.directoryArea = new S770HdDirectoryArea (in);

        this.readParameterArea (in);
    }


    /** {@inheritDoc} */
    @Override
    public S770Header getHeader ()
    {
        return this.header;
    }


    /**
     * @return The parsed directory area (volume / performance / patch / partial / sample
     *         directories).
     */
    public S770HdDirectoryArea getDirectoryArea ()
    {
        return this.directoryArea;
    }


    /**
     * Reads the parameter area. All slots are kept even if unused since patches reference partials
     * and partials reference samples by their absolute slot index. On images taken from a used
     * hard-disk deleted entries may sit between the entries in use, therefore the entry counts
     * from the header cannot be used as a range. Instead, the matching directory entry marks a
     * slot as free/deleted.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read the parameters
     */
    private void readParameterArea (final InputStream input) throws IOException
    {
        // Volume entries
        for (int i = 0; i < NUM_VOLUME_ENTRIES; i++)
            this.volumes.add (new S770Volume (input));

        // Performance entries
        for (int i = 0; i < NUM_PERFORMANCE_ENTRIES; i++)
            this.performances.add (new S770Performance (input, false));

        // Patch entries
        final List<S770DirectoryEntry> patchDirectories = this.directoryArea.getPatchDirectories ();
        for (int i = 0; i < NUM_PATCH_ENTRIES; i++)
        {
            final S770Patch patch = new S770Patch (input, false);
            patch.setActive (!patchDirectories.get (i).isFree ());
            this.patches.add (patch);
        }

        // Partial entries
        for (int i = 0; i < NUM_PARTIAL_ENTRIES; i++)
            this.partials.add (new S770Partial (input));

        // Sample entries
        for (int i = 0; i < NUM_SAMPLE_ENTRIES; i++)
            this.samples.add (new S770Sample (input));

        // The stream is now positioned at the start of the audio area (0x2B5800)
        this.loadWaveData (input);
    }


    private void loadWaveData (final InputStream input) throws IOException
    {
        final byte [] audioData = input.readAllBytes ();

        final List<S770DirectoryEntry> sampleDirectories = this.directoryArea.getSampleDirectories ();
        for (int i = 0; i < NUM_SAMPLE_ENTRIES; i++)
        {
            final S770DirectoryEntry directoryEntry = sampleDirectories.get (i);
            if (directoryEntry.isFree () || directoryEntry.getFileType () != S770FileType.SAMPLE)
                continue;
            this.samples.get (i).setWaveData (this.readSegmentChain (directoryEntry, audioData));
        }
    }


    /**
     * Collects the audio data of one sample by walking its segment chain in the FAT.
     *
     * @param directoryEntry The directory entry of the sample which references the first segment
     * @param audioData The audio area of the disk, the first byte belongs to FAT index 2
     * @return The collected audio data, shorter than announced by the directory entry if the chain
     *         is broken or the image is truncated
     */
    private byte [] readSegmentChain (final S770DirectoryEntry directoryEntry, final byte [] audioData)
    {
        // Limit a bogus segment count to the size of the audio area to prevent huge allocations
        final int maxSegments = (audioData.length + SAMPLE_BLOCK_SIZE - 1) / SAMPLE_BLOCK_SIZE;
        final int numSegments = Math.min (directoryEntry.getNumClusters (), maxSegments);

        final byte [] chainData = new byte [numSegments * SAMPLE_BLOCK_SIZE];
        int writePosition = 0;
        int fatIndex = directoryEntry.getFatEntry ();
        for (int i = 0; i < numSegments; i++)
        {
            if (fatIndex < FIRST_DATA_FAT_INDEX || fatIndex > LAST_DATA_FAT_INDEX)
                break;

            final int offset = (fatIndex - FIRST_DATA_FAT_INDEX) * SAMPLE_BLOCK_SIZE;
            if (offset >= audioData.length)
                break;

            // The last segment of an image might be cut short
            final int length = Math.min (SAMPLE_BLOCK_SIZE, audioData.length - offset);
            System.arraycopy (audioData, offset, chainData, writePosition, length);
            writePosition += length;
            if (length < SAMPLE_BLOCK_SIZE)
                break;

            final int nextIndex = this.fatEntries[fatIndex];
            if (nextIndex >= FAT_END_OF_CHAIN)
                break;
            fatIndex = nextIndex;
        }

        if (writePosition == chainData.length)
            return chainData;

        this.numIncompleteSampleChains++;
        final byte [] shortenedData = new byte [writePosition];
        System.arraycopy (chainData, 0, shortenedData, 0, writePosition);
        return shortenedData;
    }


    /**
     * Get the number of samples for which the audio data could not be fully read because their
     * segment chain was broken or the image is truncated.
     *
     * @return The number of affected samples
     */
    public int getNumIncompleteSampleChains ()
    {
        return this.numIncompleteSampleChains;
    }


    /**
     * Get the volumes.
     *
     * @return The volumes
     */
    public List<S770Volume> getVolumes ()
    {
        return this.volumes;
    }


    /** {@inheritDoc} */
    @Override
    public List<S770Performance> getPerformances ()
    {
        return this.performances;
    }


    /** {@inheritDoc} */
    @Override
    public List<S770Patch> getPatches ()
    {
        return this.patches;
    }


    /** {@inheritDoc} */
    @Override
    public List<S770Partial> getPartials ()
    {
        return this.partials;
    }


    /** {@inheritDoc} */
    @Override
    public List<S770Sample> getSamples ()
    {
        return this.samples;
    }


    private String parameterAreatoString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("S770ParameterArea [\n  volumeEntries=" + this.volumes.size () + " entries\n  performanceEntries=" + this.performances.size () + " entries\n  patchEntries=" + this.patches.size () + " entries\n  partialEntries=" + this.partials.size () + " entries\n  sampleEntries=" + this.samples.size () + " entries\n]");

        sb.append ("\nS770PerformanceEntries [").append (this.performances.size ()).append (ENTRIES);
        for (int i = 0; i < this.performances.size (); i++)
            sb.append (" [").append (i).append ("] ").append (this.performances.get (i)).append ('\n');

        sb.append ("\nS770VolumeEntries [").append (this.volumes.size ()).append (ENTRIES);
        for (int i = 0; i < this.volumes.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.volumes.get (i)).append ('\n');

        sb.append ("\nS770PatchEntries [").append (this.patches.size ()).append (ENTRIES);
        for (int i = 0; i < this.patches.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.patches.get (i)).append ('\n');

        sb.append ("\nS770PartialEntries [").append (this.partials.size ()).append (ENTRIES);
        for (int i = 0; i < this.partials.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.partials.get (i)).append ('\n');

        sb.append ("\nS770SampleEntries [").append (this.samples.size ()).append (ENTRIES);
        for (int i = 0; i < this.samples.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.samples.get (i)).append ('\n');

        return sb.toString ();
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "RolandS770 {\n\n" + this.header + "\n\n" + this.directoryArea + "\n\n" + this.parameterAreatoString () + "\n}";
    }
}