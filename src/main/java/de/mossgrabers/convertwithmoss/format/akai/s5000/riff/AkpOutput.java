// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * A S5000/S6000 output chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkpOutput extends AbstractSpecificRIFFChunk
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
        super (AkpRiffChunkId.OUT_ID, LENGTH_OUT);
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
     * @param loudness The loudness
     */
    public void setProgramNumber (final int loudness)
    {
        this.loudness = loudness;
    }


    /**
     * Get the panning modulation 1.
     * 
     * @return The panning modulation
     */
    public int getPanMod1 ()
    {
        return this.panMod1;
    }


    /**
     * Get the panning modulation 2.
     * 
     * @return The panning modulation
     */
    public int getPanMod2 ()
    {
        return this.panMod1;
    }


    /**
     * Get the panning modulation 3.
     * 
     * @return The panning modulation
     */
    public int getPanMod3 ()
    {
        return this.panMod1;
    }


    /**
     * Get the velocity sensitivity.
     * 
     * @return The velocity sensitivity
     */
    public int getVelocitySensitivity ()
    {
        return this.velocitySensitivity;
    }


    /**
     * Reads the data from a chunk.
     *
     * @param chunk The chunk to read
     * @throws ParseException Could not read the data
     */
    public void read (final RawRIFFChunk chunk) throws ParseException
    {
        if (chunk.getSize () < LENGTH_OUT)
            throw new ParseException ("Unexpected size of Output chunk: " + chunk.getSize ());

        this.loudness = chunk.getByteAsUnsignedInt (1);
        this.ampMod1 = chunk.getByteAsUnsignedInt (2);
        this.ampMod2 = chunk.getByteAsUnsignedInt (3);
        this.panMod1 = chunk.getByteAsUnsignedInt (4);
        this.panMod2 = chunk.getByteAsUnsignedInt (5);
        this.panMod3 = chunk.getByteAsUnsignedInt (6);
        this.velocitySensitivity = chunk.getByteAsSignedInt (7);
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


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        // Not used
        return "";
    }
}
