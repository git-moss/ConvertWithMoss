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
 * Roland S-770 diskette parameter area.
 *
 * Reads exactly as many entries as reported in the ID area, using the same fixed binary entry
 * formats as the CD-ROM.
 * 
 * @author Jürgen Moßgraber
 */
public class S770DisketteParameterArea
{
    private final List<S770Performance>  performances;
    private final List<S770Patch>   patches;
    private final List<S770Partial> partials;
    private final List<S770Sample>  samples;


    /**
     * Reads all parameter entries for each object type.
     *
     * @param in Stream positioned at the first parameter entry
     * @param numVolumes Number of volume entries to read
     * @param numPerformances Number of performance entries to read
     * @param numPatches Number of patch entries to read
     * @param numPartials Number of partial entries to read
     * @param numSamples Number of sample entries to read
     * @throws IOException on read error
     */
    public S770DisketteParameterArea (final InputStream in, final int numVolumes, final int numPerformances, final int numPatches, final int numPartials, final int numSamples) throws IOException
    {
        // Performance entries
        final List<S770Performance> perfs = new ArrayList<> (numPerformances);
        for (int i = 0; i < numPerformances; i++)
            perfs.add (new S770Performance (in));
        this.performances = Collections.unmodifiableList (perfs);

        // Patch entries
        final List<S770Patch> patches = new ArrayList<> (numPatches);
        for (int i = 0; i < numPatches; i++)
            patches.add (new S770Patch (in));
        this.patches = Collections.unmodifiableList (patches);

        // Partial entries
        final List<S770Partial> partials = new ArrayList<> (numPartials);
        for (int i = 0; i < numPartials; i++)
            partials.add (new S770Partial (in));
        this.partials = Collections.unmodifiableList (partials);

        // Sample entries
        final List<S770Sample> samples = new ArrayList<> (numSamples);
        for (int i = 0; i < numSamples; i++)
            samples.add (new S770Sample (in));
        this.samples = Collections.unmodifiableList (samples);
    }


    /**
     * Get the performances.
     * 
     * @return The performances
     */
    public List<S770Performance> getPerformanceEntries ()
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
        sb.append ("S770DisketteParameterArea [\n");
        sb.append ("  performanceEntries=").append (this.performances.size ()).append ('\n');
        sb.append ("  patchEntries=").append (this.patches.size ()).append ('\n');
        sb.append ("  partialEntries=").append (this.partials.size ()).append ('\n');
        sb.append ("  sampleEntries=").append (this.samples.size ()).append ('\n');
        sb.append (']');
        return sb.toString ();
    }
}