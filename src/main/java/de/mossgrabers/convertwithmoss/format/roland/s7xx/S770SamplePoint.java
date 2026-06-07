// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 sample point (u4, little-endian).
 *
 * @author Jürgen Moßgraber
 */
public class S770SamplePoint
{
    private final long rawValue;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public S770SamplePoint (final InputStream input) throws IOException
    {
        this.rawValue = StreamUtils.readUnsigned32 (input, false);
    }


    /**
     * Get the raw value.
     *
     * @return The raw value
     */
    public long getRawValue ()
    {
        return this.rawValue;
    }


    /**
     * Get the Fine tuning component (bits 0-7).
     *
     * @return The fine tuning
     */
    public int getFine ()
    {
        return (int) (this.rawValue & 0xFF);
    }


    /**
     * Get the Sample address (bits 8-31).
     *
     * @return The address
     */
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