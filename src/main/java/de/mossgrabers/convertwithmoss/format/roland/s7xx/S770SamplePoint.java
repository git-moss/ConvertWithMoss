// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 sample point (u4, little-endian). fine = raw_value &amp; 0xFF (lower 8 bits) address
 * = raw_value &gt;&gt; 8 (upper 24 bits)
 *
 * @author Jürgen Moßgraber
 */
public class S770SamplePoint
{
    private final long rawValue;


    public S770SamplePoint (final InputStream in) throws IOException
    {
        this.rawValue = StreamUtils.readUnsigned32 (in, false);
    }


    public long getRawValue ()
    {
        return this.rawValue;
    }


    /** Fine tuning component (bits 0-7). */
    public int getFine ()
    {
        return (int) (this.rawValue & 0xFF);
    }


    /** Sample address (bits 8-31). */
    public long getAddress ()
    {
        return this.rawValue >> 8;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770SamplePoint [rawValue=" + this.rawValue + ", address=" + this.getAddress () + ", fine=" + this.getFine () + "]";
    }
}