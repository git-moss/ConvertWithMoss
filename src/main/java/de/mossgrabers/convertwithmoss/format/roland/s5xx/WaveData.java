package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class WaveData extends Struct
{
    public static final int SAMPLES_PER_SEGMENT = 12288;

    private final byte []   data                = new byte [SAMPLES_PER_SEGMENT * 3 / 2];


    public byte [] getData ()
    {
        return this.data;
    }


    public short [] getSamples ()
    {
        short [] result = new short [SAMPLES_PER_SEGMENT];
        for (int i = 0; i < result.length; i++)
        {
            int idx = i * 3 / 2;
            byte lo = this.data[idx];
            byte hi = this.data[idx + 1];
            if (i % 2 == 0)
                // even
                result[i] = (short) (Byte.toUnsignedInt (lo) << 8 | hi & 0xF0);
            else
                // odd
                result[i] = (short) (Byte.toUnsignedInt (hi) << 8 | (lo & 0x0F) << 4);
        }
        return result;
    }


    public void setSamples (short [] samples)
    {
        if (samples.length != SAMPLES_PER_SEGMENT)
            throw new IllegalArgumentException ("invalid sample count");
        for (int i = 0; i < samples.length; i++)
        {
            int idx = i * 3 / 2;
            if (i % 2 == 0)
            {
                // even
                this.data[idx + 0] = (byte) (samples[i] >> 8);
                this.data[idx + 1] = (byte) (this.data[idx + 1] & 0x0F | samples[i] & 0xF0);
            }
            else
            {
                // odd
                this.data[idx + 0] = (byte) (this.data[idx] & 0xF0 | samples[i] >> 4 & 0x0F);
                this.data[idx + 1] = (byte) (samples[i] >> 8);
            }
        }
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        in.read (this.data);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        out.write (this.data);
    }
}
