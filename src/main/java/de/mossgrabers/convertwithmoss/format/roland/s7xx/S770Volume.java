// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 volume entry.
 *
 * @author Jürgen Moßgraber
 */
public class S770Volume
{
    private final String name;
    private final int [] performancePointers;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read the volume
     */
    public S770Volume (final InputStream input) throws IOException
    {
        this.name = StreamUtils.readAscii (input, 16);
        input.skipNBytes (16);
        this.performancePointers = new int [64];
        for (int i = 0; i < 64; i++)
            this.performancePointers[i] = StreamUtils.readUnsigned16 (input, false);
        input.skipNBytes (0x60);
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


    /**
     * Get the indices of the referenced performances.
     *
     * @return The part indices
     */
    public int [] getPerformancePointers ()
    {
        return this.performancePointers;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770VolumeEntry [name='" + this.name.trim () + "', performancePtrs=" + Arrays.toString (this.performancePointers) + "]";
    }
}