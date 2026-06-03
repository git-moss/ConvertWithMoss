// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 volume entry (256 bytes). 16 name 16 (pad) u2×64 performance_ptrs 0x60 (pad)
 *
 * @author Jürgen Moßgraber
 */
public class S770Volume
{
    private final String name;
    private final int [] performancePtrs;


    public S770Volume (final InputStream in) throws IOException
    {
        this.name = StreamUtils.readAscii (in, 16);
        in.skipNBytes (16);
        this.performancePtrs = new int [64];
        for (int i = 0; i < 64; i++)
            this.performancePtrs[i] = StreamUtils.readUnsigned16 (in, false);
        in.skipNBytes (0x60);
    }


    public String getName ()
    {
        return this.name;
    }


    public int [] getPerformancePtrs ()
    {
        return this.performancePtrs;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770VolumeEntry [name='" + this.name.trim () + "', performancePtrs=" + Arrays.toString (this.performancePtrs) + "]";
    }
}