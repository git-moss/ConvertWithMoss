// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.io.InputStream;


/**
 * The wave data. The 12-bit samples are stored in 1.5 bytes.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxWaveData
{
    /** The number samples contained in one Wave Data segment. */
    public static final int  SAMPLES_PER_SEGMENT    = 12288;

    private static final int RAW_SAMPLE_DATA_LENGTH = SAMPLES_PER_SEGMENT * 3 / 2;

    private final byte []    data                   = new byte [RAW_SAMPLE_DATA_LENGTH];


    /**
     * Constructor.
     *
     * @param input The input stream from which to read the wave data
     * @throws IOException Could not read the data
     */
    public S5xxWaveData (final InputStream input) throws IOException
    {
        input.read (this.data);
    }


    /**
     * Get the extracted samples.
     *
     * @return The samples, each array entry contains a 12-bit sample
     */
    public short [] getSamples ()
    {
        final short [] result = new short [SAMPLES_PER_SEGMENT];
        for (int i = 0; i < result.length; i++)
        {
            final int idx = i * 3 / 2;
            final byte lo = this.data[idx];
            final byte hi = this.data[idx + 1];
            if (i % 2 == 0)
                result[i] = (short) (Byte.toUnsignedInt (lo) << 8 | hi & 0xF0);
            else
                result[i] = (short) (Byte.toUnsignedInt (hi) << 8 | (lo & 0x0F) << 4);
        }
        return result;
    }


    /**
     * Set the samples.
     *
     * @param samples The samples to store (12 bit)
     */
    public void setSamples (final short [] samples)
    {
        if (samples.length != SAMPLES_PER_SEGMENT)
            throw new IllegalArgumentException ("invalid sample count");
        for (int i = 0; i < samples.length; i++)
        {
            final int idx = i * 3 / 2;
            if (i % 2 == 0)
            {
                this.data[idx + 0] = (byte) (samples[i] >> 8);
                this.data[idx + 1] = (byte) (this.data[idx + 1] & 0x0F | samples[i] & 0xF0);
            }
            else
            {
                this.data[idx + 0] = (byte) (this.data[idx] & 0xF0 | samples[i] >> 4 & 0x0F);
                this.data[idx + 1] = (byte) (samples[i] >> 8);
            }
        }
    }
}
