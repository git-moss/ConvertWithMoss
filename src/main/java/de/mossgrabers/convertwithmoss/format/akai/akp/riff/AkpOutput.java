// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


/**
 * A S5000/S6000 output chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkpOutput extends AbstractAkpChunk
{
    /** The length of the Output structure. */
    private static final int LENGTH_OUT          = 8;

    private int              loudness            = 85;
    private int              ampMod1             = 0;
    private int              ampMod2             = 0;
    private int              panMod1             = 0;
    private int              panMod2             = 0;
    private int              panMod3             = 0;
    private int              velocitySensitivity = 25;


    /**
     * Default constructor.
     */
    public AkpOutput ()
    {
        super (AkpRiffChunkId.OUT_ID, LENGTH_OUT, 1);
    }


    /**
     * Get the loudness.
     *
     * @return The loudness in the range of [0..100]
     */
    public int getLoudness ()
    {
        return this.loudness;
    }


    /**
     * Set the loudness.
     *
     * @param loudness The loudness in the range of [0..100]
     */
    public void setLoudness (final int loudness)
    {
        this.loudness = loudness;
    }


    /**
     * Get the amplitude modulation 1 amount.
     *
     * @return The amplitude modulation amount in the range of [-100..100]
     */
    public int getAmpMod1 ()
    {
        return this.ampMod1;
    }


    /**
     * Get the amplitude modulation 2 amount.
     *
     * @return The amplitude modulation amount in the range of [-100..100]
     */
    public int getAmpMod2 ()
    {
        return this.ampMod2;
    }


    /**
     * Get the panning modulation 1 amount.
     *
     * @return The panning modulation amount in the range of [-100..100]
     */
    public int getPanMod1 ()
    {
        return this.panMod1;
    }


    /**
     * Get the panning modulation 2 amount.
     *
     * @return The panning modulation amount in the range of [-100..100]
     */
    public int getPanMod2 ()
    {
        return this.panMod2;
    }


    /**
     * Get the panning modulation 3 amount.
     *
     * @return The panning modulation amount in the range of [-100..100]
     */
    public int getPanMod3 ()
    {
        return this.panMod3;
    }


    /**
     * Get the velocity sensitivity.
     *
     * @return The velocity sensitivity in the range of [-100..100]
     */
    public int getVelocitySensitivity ()
    {
        return this.velocitySensitivity;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        this.loudness = getUnsigned (data, 1);
        // The specification states 0..100 for the modulation amounts but negative values are
        // used as well
        this.ampMod1 = getSigned (data, 2);
        this.ampMod2 = getSigned (data, 3);
        this.panMod1 = getSigned (data, 4);
        this.panMod2 = getSigned (data, 5);
        this.panMod3 = getSigned (data, 6);
        this.velocitySensitivity = getSigned (data, 7);
    }


    /**
     * Write the data to a stream.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final ByteArrayOutputStream out) throws IOException
    {
        out.write (0);
        out.write (this.loudness);
        out.write (this.ampMod1);
        out.write (this.ampMod2);
        out.write (this.panMod1);
        out.write (this.panMod2);
        out.write (this.panMod3);
        out.write (this.velocitySensitivity);
    }
}
