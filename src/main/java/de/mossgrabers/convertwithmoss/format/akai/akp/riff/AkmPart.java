// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


/**
 * A S5000/S6000 part chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkmPart extends AbstractAkpChunk
{
    /** The length of the Part structure. There are 2 versions found: 0x28 and 0x30! */
    private static final int LENGTH_PART = 0x28;

    private String           presetName;
    private int              midiChannel;
    private int              lowKey;
    private int              highKey;
    private int              panning;
    private int              volume;


    /**
     * Default constructor.
     */
    public AkmPart ()
    {
        super (AkmRiffChunkId.PART_ID, LENGTH_PART, 3);
    }


    /**
     * Get the name of the referenced preset.
     *
     * @return The name
     */
    public String getPresetName ()
    {
        return this.presetName;
    }


    /**
     * Get the MIDI of the part.
     *
     * @return The index, 1A-16A, 1B-16B, no Omni-Mode!
     */
    public int getMidiChannel ()
    {
        return this.midiChannel;
    }


    /**
     * Get the lower key bound.
     *
     * @return The lower key
     */
    public int getLowKey ()
    {
        return this.lowKey;
    }


    /**
     * Get the upper key bound.
     *
     * @return The upper key
     */
    public int getHighKey ()
    {
        return this.highKey;
    }


    /**
     * Get the volume of the part.
     *
     * @return The volume in the range of [0..100] (–60.0 to +6.0dB)
     */
    public int getVolume ()
    {
        return this.volume;
    }


    /**
     * Get the panning of the part.
     *
     * @return The panning in the range of [-50..50] (L50–MID–R50)
     */
    public int getPanning ()
    {
        return this.panning;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        // Byte 1 contains the length of the name in version 3 but the name is also zero
        // terminated, which is used since older versions are unknown
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0x02; i < 0x22 && data[i] != 0; i++)
            sb.append ((char) getUnsigned (data, i));
        this.presetName = sb.toString ();

        // 1A-16A, 1B-16B, no Omni-Mode!
        this.midiChannel = getUnsigned (data, 0x22);
        this.panning = getSigned (data, 0x23);
        this.lowKey = getUnsigned (data, 0x24);
        this.highKey = getUnsigned (data, 0x25);
        this.volume = getUnsigned (data, 0x26);

        // TODO identify values:
        // 0x27: -30, -25, -20, -15, -10, 0, 10, 20, 25, 30 - Fine tune?
        // 0x28: 0 - Semi-tones (Transpose) or Mute/Solo?
        // 0x29: 0 - Semi-tones (Transpose) or Mute/Solo?
        // 0x2A: 0, 1, 2, 3, 4 - Output? - 0 = L/R; 14 = op1/2op7/8; 514 = L, R, op1-op8
        // 0x2B: 0 .. 60 ? PartLevel in 10×dB
    }


    /**
     * Write the data to a stream.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final ByteArrayOutputStream out) throws IOException
    {
        // Not used
    }
}
