// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Roland S-770 parameter area (0x1A8000 bytes).
 *
 * <pre>
 *   Offset      Size      Content
 *   0x000000    0x008000  Volume entries      (128  × 256 B)
 *   0x008000    0x040000  Performance entries (512  × 512 B)
 *   0x048000    0x080000  Patch entries       (1024 × 512 B)
 *   0x0C8000    0x080000  Partial entries     (4096 × 128 B)
 *   0x148000    0x060000  Sample entries      (8192 ×  48 B)
 * </pre>
 *
 * @author Jürgen Moßgraber
 */
public class S770ParameterArea
{
    /** Number of volume entries on a Roland S-770 disk. */
    public static final int              NUM_VOLUME_ENTRIES      = 128;
    /** Number of performance entries on a Roland S-770 disk. */
    public static final int              NUM_PERFORMANCE_ENTRIES = 512;
    /** Number of patch entries on a Roland S-770 disk. */
    public static final int              NUM_PATCH_ENTRIES       = 1024;
    /** Number of partial entries on a Roland S-770 disk. */
    public static final int              NUM_PARTIAL_ENTRIES     = 4096;
    /** Number of sample entries on a Roland S-770 disk. */
    public static final int              NUM_SAMPLE_ENTRIES      = 8192;

    private final List<S770Volume>  volumes;
    private final List<S770Performance>  performances;
    private final List<S770Patch>   patches;
    private final List<S770Partial> partials;
    private final List<S770Sample>  samples;


    /**
     * Constructor.
     * 
     * @param input The input stream to read from
     * @throws IOException Could not read the parameters
     */
    public S770ParameterArea (final InputStream input) throws IOException
    {
        // Volume entries
        final List<S770Volume> volumes = new ArrayList<> (NUM_VOLUME_ENTRIES);
        for (int i = 0; i < NUM_VOLUME_ENTRIES; i++)
            volumes.add (new S770Volume (input));
        this.volumes = Collections.unmodifiableList (volumes);

        // Performance entries
        final List<S770Performance> perfs = new ArrayList<> (NUM_PERFORMANCE_ENTRIES);
        for (int i = 0; i < NUM_PERFORMANCE_ENTRIES; i++)
            perfs.add (new S770Performance (input));
        this.performances = Collections.unmodifiableList (perfs);

        final List<S770Patch> patches = new ArrayList<> (NUM_PATCH_ENTRIES);
        for (int i = 0; i < NUM_PATCH_ENTRIES; i++)
            patches.add (new S770Patch (input));
        this.patches = Collections.unmodifiableList (patches);

        final List<S770Partial> partials = new ArrayList<> (NUM_PARTIAL_ENTRIES);
        for (int i = 0; i < NUM_PARTIAL_ENTRIES; i++)
            partials.add (new S770Partial (input));
        this.partials = Collections.unmodifiableList (partials);

        final List<S770Sample> list = new ArrayList<> (NUM_SAMPLE_ENTRIES);
        for (int i = 0; i < NUM_SAMPLE_ENTRIES; i++)
            list.add (new S770Sample (input));
        this.samples = Collections.unmodifiableList (list);
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


    /**
     * Get the performances.
     * 
     * @return The performances
     */
    public List<S770Performance> getPerformances ()
    {
        return this.performances;
    }


    /**
     * Get the patches.
     * 
     * @return The patches
     */
    public List<S770Patch> getPatches ()
    {
        return this.patches;
    }


    /**
     * Get the partials.
     * 
     * @return The patches
     */
    public List<S770Partial> getPartials ()
    {
        return this.partials;
    }


    /**
     * Get the samples.
     * 
     * @return The samples
     */
    public List<S770Sample> getSamples ()
    {
        return this.samples;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("S770ParameterArea [\n" + "  volumeEntries=" + this.volumes.size () + " entries\n" + "  performanceEntries=" + this.performances.size () + " entries\n" + "  patchEntries=" + this.patches.size () + " entries\n" + "  partialEntries=" + this.partials.size () + " entries\n" + "  sampleEntries=" + this.samples.size () + " entries\n]");

        sb.append ("S770PerformanceEntries [").append (this.performances.size ()).append (" entries]\n");
        for (int i = 0; i < this.performances.size (); i++)
            sb.append (" [").append (i).append ("] ").append (this.performances.get (i)).append ('\n');

        sb.append ("S770VolumeEntries [").append (this.volumes.size ()).append (" entries]\n");
        for (int i = 0; i < this.volumes.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.volumes.get (i)).append ('\n');

        sb.append ("S770PatchEntries [").append (this.patches.size ()).append (" entries]\n");
        for (int i = 0; i < this.patches.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.patches.get (i)).append ('\n');

        sb.append ("S770PartialEntries [").append (this.partials.size ()).append (" entries]\n");
        for (int i = 0; i < this.partials.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.partials.get (i)).append ('\n');

        sb.append ("S770SampleEntries [").append (this.samples.size ()).append (" entries]\n");
        for (int i = 0; i < this.samples.size (); i++)
            sb.append ("  [").append (i).append ("] ").append (this.samples.get (i)).append ('\n');

        return sb.toString ();
    }
}