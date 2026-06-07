// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.tools.ui.Functions;


/**
 * Reads Roland S-770 CD-ROM or hard-disk images.
 *
 * <h3>On-disk layout (CD-ROM / HD)</h3> <pre>
 *   Offset      Size        Content
 *   0x000000    0x000200    ID Area        (512 B)
 *   0x000200    0x000600    Reserved Area  (1 536 B – skipped)
 *   0x000800    0x080000    Program Text   (512 KB – skipped)
 *   0x080800    0x020000    FAT Area       (128 KB – skipped)
 *   0x0A0800    0x06D000    Directory Area
 *   0x10D800    0x1A8000    Parameter Area
 * </pre>
 *
 * @author Jürgen Moßgraber
 */
public class S770Hd implements IS770Image
{
    private static final long         SIZE_RESERVED           = 0x600L;
    private static final long         SIZE_PROGRAM_TEXT       = 0x80000L;
    private static final long         SIZE_FAT                = 0x20000L;

    /** Number of volume entries on a Roland S-770 disk. */
    public static final int           NUM_VOLUME_ENTRIES      = 128;
    /** Number of performance entries on a Roland S-770 disk. */
    public static final int           NUM_PERFORMANCE_ENTRIES = 512;
    /** Number of patch entries on a Roland S-770 disk. */
    public static final int           NUM_PATCH_ENTRIES       = 1024;
    /** Number of partial entries on a Roland S-770 disk. */
    public static final int           NUM_PARTIAL_ENTRIES     = 4096;
    /** Number of sample entries on a Roland S-770 disk. */
    public static final int           NUM_SAMPLE_ENTRIES      = 8192;

    private static final int          SAMPLE_BLOCK_SIZE       = 0x2400;

    private final S770Header          header;
    private final S770HdDirectoryArea directoryArea;

    private List<S770Volume>          volumes;
    private List<S770Performance>     performances;
    private List<S770Patch>           patches;
    private List<S770Partial>         partials;
    private List<S770Sample>          samples;


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
        this.directoryArea = parseDirectoryArea (in);

        this.readParameterArea (in);
    }


    /**
     * Skips the three large non-parsed regions (Reserved / Program-Text / FAT) that sit between the
     * ID area and the directory area, then reads the directory area.
     *
     * @param in Stream positioned immediately after the ID area (offset 0x200)
     * @return The parsed area
     * @throws IOException on read error
     */
    private static S770HdDirectoryArea parseDirectoryArea (final InputStream in) throws IOException
    {
        in.skipNBytes (SIZE_RESERVED + SIZE_PROGRAM_TEXT + SIZE_FAT);
        return new S770HdDirectoryArea (in);
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
     * Reads the parameter area.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read the parameters
     */
    private void readParameterArea (final InputStream input) throws IOException
    {
        // Volume entries
        final List<S770Volume> volumes = new ArrayList<> (NUM_VOLUME_ENTRIES);
        for (int i = 0; i < NUM_VOLUME_ENTRIES; i++)
            volumes.add (new S770Volume (input));
        this.volumes = Collections.unmodifiableList (volumes);

        // Performance entries
        final List<S770Performance> perfs = new ArrayList<> (NUM_PERFORMANCE_ENTRIES);
        final int numPerformances = this.header.getNumPerformances ();
        for (int i = 0; i < NUM_PERFORMANCE_ENTRIES; i++)
        {
            final S770Performance performance = new S770Performance (input, false);
            if (i < numPerformances)
                perfs.add (performance);
        }
        this.performances = Collections.unmodifiableList (perfs);

        final List<S770Patch> patches = new ArrayList<> (NUM_PATCH_ENTRIES);
        final int numPatches = this.header.getNumPatches ();
        for (int i = 0; i < NUM_PATCH_ENTRIES; i++)
        {
            final S770Patch patch = new S770Patch (input, false);
            if (i < numPatches)
                patches.add (patch);
        }
        this.patches = Collections.unmodifiableList (patches);

        final List<S770Partial> partials = new ArrayList<> (NUM_PARTIAL_ENTRIES);
        final int numPartials = this.header.getNumPartials ();
        for (int i = 0; i < NUM_PARTIAL_ENTRIES; i++)
        {
            final S770Partial partial = new S770Partial (input);
            if (i < numPartials)
                partials.add (partial);
        }
        this.partials = Collections.unmodifiableList (partials);

        final List<S770Sample> samples = new ArrayList<> (NUM_SAMPLE_ENTRIES);
        final int numSamples = this.header.getNumSamples ();
        for (int i = 0; i < NUM_SAMPLE_ENTRIES; i++)
        {
            final S770Sample sample = new S770Sample (input);
            if (i < numSamples)
                samples.add (sample);
        }
        this.samples = Collections.unmodifiableList (samples);

        // Wave data starts at 0x3B6000, currently we are at 0x2B5800
        input.skipNBytes (0x100800);
        this.loadWaveData (input);
    }


    private void loadWaveData (final InputStream input) throws IOException
    {
        final byte [] fullSampleData = input.readAllBytes ();

        int sampleStart = 0;

        for (final S770Sample sample: this.samples)
        {
            // Segment top is always 0, therefore only read by length

            if (sampleStart > fullSampleData.length)
                throw new IOException (Functions.getMessage ("IDS_S7XX_SAMPLE_DATA_MISSING"));

            final int sampleLength = sample.getSegmentLength () * SAMPLE_BLOCK_SIZE;
            if (sampleStart + sampleLength > fullSampleData.length)
                throw new IOException (Functions.getMessage ("IDS_S7XX_SAMPLE_DATA_MISSING"));

            final byte [] sampleData = new byte [sampleLength];
            System.arraycopy (fullSampleData, sampleStart, sampleData, 0, sampleLength);
            sample.setWaveData (sampleData);

            sampleStart += sampleLength;
        }
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

        sb.append ("\nS770PerformanceEntries [").append (this.performances.size ()).append (" entries]\n");
        for (int i = 0; i < this.performances.size (); i++)
            sb.append (" [").append (i).append ("] ").append (this.performances.get (i)).append ('\n');

        sb.append ("\nS770VolumeEntries [").append (this.volumes.size ()).append (" entries]\n");
        for (int i = 0; i < this.volumes.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.volumes.get (i)).append ('\n');

        sb.append ("\nS770PatchEntries [").append (this.patches.size ()).append (" entries]\n");
        for (int i = 0; i < this.patches.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.patches.get (i)).append ('\n');

        sb.append ("\nS770PartialEntries [").append (this.partials.size ()).append (" entries]\n");
        for (int i = 0; i < this.partials.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.partials.get (i)).append ('\n');

        sb.append ("\nS770SampleEntries [").append (this.samples.size ()).append (" entries]\n");
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