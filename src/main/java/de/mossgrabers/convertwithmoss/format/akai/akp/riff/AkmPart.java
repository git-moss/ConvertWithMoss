// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;


/**
 * A S5000/S6000 part chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkmPart extends AbstractAkpChunk
{
    /** The minimum length of the Part structure. */
    private static final int LENGTH_PART     = 0x28;
    /** The length of the Part structure in version 2. */
    private static final int LENGTH_PART_2   = 0x2E;
    /** The length of the Part structure in version 3. */
    private static final int LENGTH_PART_3   = 0x30;
    /** The maximum length of the preset name. */
    private static final int MAX_NAME_LENGTH = 32;

    private String           presetName      = "";
    private int              midiChannel     = 0;
    private int              lowKey          = 21;
    private int              highKey         = 127;
    private int              panning         = 0;
    private int              volume          = 100;


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
        this.presetName = readPresetName (data);

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


    /** {@inheritDoc} */
    @Override
    protected int getSize (final int targetVersion)
    {
        return targetVersion >= 3 ? LENGTH_PART_3 : LENGTH_PART_2;
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        // Only write the name if it is different to keep the content of unused parts
        if (!this.presetName.equals (readPresetName (data)))
        {
            final int nameLength = Math.min (this.presetName.length (), MAX_NAME_LENGTH);
            setByte (data, 0x01, nameLength);
            for (int i = 0; i < MAX_NAME_LENGTH; i++)
                setByte (data, 0x02 + i, i < nameLength ? this.presetName.charAt (i) : 0);
        }

        setByte (data, 0x22, this.midiChannel);
        setByte (data, 0x23, this.panning);
        setByte (data, 0x24, this.lowKey);
        setByte (data, 0x25, this.highKey);
        setByte (data, 0x26, this.volume);
    }


    /**
     * Read the preset name from the chunk data. Byte 1 contains the length of the name in version 3
     * but the name is also zero terminated, which is used since older versions are unknown.
     *
     * @param data The data of the chunk
     * @return The name, empty if the part is not used
     */
    private static String readPresetName (final byte [] data)
    {
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0x02; i < 0x02 + MAX_NAME_LENGTH && data[i] != 0; i++)
            sb.append ((char) getUnsigned (data, i));
        return sb.toString ();
    }
}
