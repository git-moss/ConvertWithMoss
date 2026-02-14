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
 * A S5000/S6000 part chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkmPart extends AbstractSpecificRIFFChunk
{
    /** The length of the Part structure. */
    private static final int LENGTH_OUT = 0x30;

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
        super (AkmRiffChunkId.PART_ID, LENGTH_OUT);
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
     * @return The volume in the range of [0..100]
     */
    public int getVolume ()
    {
        return this.volume;
    }


    /**
     * Get the panning of the part.
     *
     * @return The panning in the range of [-50..50]
     */
    public int getPanning ()
    {
        return this.panning;
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
            throw new ParseException ("Unexpected size of Part chunk: " + chunk.getSize ());

        this.presetName = chunk.getNullTerminatedString (2, 32, "");

        // 1A-16A, 1B-16B, no Omni-Mode!
        this.midiChannel = chunk.getByteAsUnsignedInt (0x22);
        this.panning = chunk.getByteAsSignedInt (0x23);
        this.lowKey = chunk.getByteAsUnsignedInt (0x24);
        this.highKey = chunk.getByteAsUnsignedInt (0x25);
        this.volume = chunk.getByteAsUnsignedInt (0x26);

        // -30, -25, -20, -15, -10, 0, 10, 20, 25 - Fine tune?
        chunk.getByteAsSignedInt (0x27);

        // 0, 1, 2, 3, 4 - Output?
        chunk.getByteAsSignedInt (0x2A);
        // 0 .. 60 ?
        chunk.getByteAsSignedInt (0x2B);

        // 0 - Semi-tones (Transpose) or Mute?
        chunk.getByteAsSignedInt (0x28);
        // 0 - Semi-tones (Transpose) or Mute?
        chunk.getByteAsSignedInt (0x29);
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


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        // Not used
        return "";
    }
}
