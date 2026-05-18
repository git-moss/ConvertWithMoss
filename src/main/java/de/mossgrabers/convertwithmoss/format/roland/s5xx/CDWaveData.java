package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class CDWaveData extends Struct
{
    public static final int SAMPLES_PER_SEGMENT = 12288;

    private final byte []   data                = new byte [SAMPLES_PER_SEGMENT * 2];


    public byte [] getData ()
    {
        return this.data;
    }


    public short [] getSamples ()
    {
        short [] result = new short [SAMPLES_PER_SEGMENT];
        for (int i = 0; i < result.length; i++)
            result[i] = (short) (Byte.toUnsignedInt (this.data[i * 2]) | Byte.toUnsignedInt (this.data[i * 2 + 1]) << 8);
        return result;
    }


    public void setSamples (short [] samples)
    {
        if (samples.length != SAMPLES_PER_SEGMENT)
            throw new IllegalArgumentException ("invalid sample count");
        for (int i = 0; i < samples.length; i++)
        {
            this.data[i * 2 + 0] = (byte) samples[i];
            this.data[i * 2 + 1] = (byte) (samples[i] >> 8);
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
