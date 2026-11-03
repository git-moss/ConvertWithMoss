// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 part chunk of a multi. The format is described in
 * documentation/design/AKAI_AKP_FORMAT.md.
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
    private int              transpose       = 0;
    private int              lowKey          = 21;
    private int              highKey         = 127;
    private int              volume          = 100;
    private int              panning         = 0;
    private int              fineTune        = 0;
    private int              effectsChannel  = 0;
    private int              effectsSend     = 0;
    private int              output          = 0;
    private boolean          isMuted         = false;


    /**
     * Default constructor.
     */
    public AkmPart ()
    {
        super (AkmRiffChunkId.PART_ID, LENGTH_PART, 3);
    }


    /**
     * Is the part used, which means that a preset is assigned?
     *
     * @return True if used
     */
    public boolean isUsed ()
    {
        return !this.presetName.isBlank ();
    }


    /**
     * Get the name of the referenced preset.
     *
     * @return The name, empty if the part is not used
     */
    public String getPresetName ()
    {
        return this.presetName;
    }


    /**
     * Set the name of the referenced preset.
     *
     * @param presetName The name, empty if the part is not used, maximum 32 characters
     */
    public void setPresetName (final String presetName)
    {
        this.presetName = presetName;
    }


    /**
     * Get the MIDI channel of the part.
     *
     * @return The channel in the range of [0..31] = 1A-16A, 1B-16B, no Omni-Mode!
     */
    public int getMidiChannel ()
    {
        return this.midiChannel;
    }


    /**
     * Set the MIDI channel of the part.
     *
     * @param midiChannel The channel in the range of [0..31] = 1A-16A, 1B-16B
     */
    public void setMidiChannel (final int midiChannel)
    {
        this.midiChannel = midiChannel;
    }


    /**
     * Get the transposition of the part.
     *
     * @return The transposition in the range of [-36..36] semi-tones
     */
    public int getTranspose ()
    {
        return this.transpose;
    }


    /**
     * Set the transposition of the part.
     *
     * @param transpose The transposition in the range of [-36..36] semi-tones
     */
    public void setTranspose (final int transpose)
    {
        this.transpose = transpose;
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
     * Set the lower key bound.
     *
     * @param lowKey The lower key in the range of [0..127]
     */
    public void setLowKey (final int lowKey)
    {
        this.lowKey = lowKey;
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
     * Set the upper key bound.
     *
     * @param highKey The upper key in the range of [0..127]
     */
    public void setHighKey (final int highKey)
    {
        this.highKey = highKey;
    }


    /**
     * Get the volume of the part.
     *
     * @return The volume in the range of [0..101], roughly a linear gain factor with 100 = 0dB
     */
    public int getVolume ()
    {
        return this.volume;
    }


    /**
     * Set the volume of the part.
     *
     * @param volume The volume in the range of [0..101], roughly a linear gain factor with 100 =
     *            0dB
     */
    public void setVolume (final int volume)
    {
        this.volume = volume;
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


    /**
     * Set the panning of the part.
     *
     * @param panning The panning in the range of [-50..50] (L50–MID–R50)
     */
    public void setPanning (final int panning)
    {
        this.panning = panning;
    }


    /**
     * Get the fine tuning of the part.
     *
     * @return The fine tuning in the range of [-50..50] cents
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Set the fine tuning of the part.
     *
     * @param fineTune The fine tuning in the range of [-50..50] cents
     */
    public void setFineTune (final int fineTune)
    {
        this.fineTune = fineTune;
    }


    /**
     * Get the effects channel of the part.
     *
     * @return The effects channel: 0 = OFF, 1 = FX1, 2 = FX2, 3 = RV3, 4 = RV4
     */
    public int getEffectsChannel ()
    {
        return this.effectsChannel;
    }


    /**
     * Set the effects channel of the part.
     *
     * @param effectsChannel The effects channel: 0 = OFF, 1 = FX1, 2 = FX2, 3 = RV3, 4 = RV4
     */
    public void setEffectsChannel (final int effectsChannel)
    {
        this.effectsChannel = effectsChannel;
    }


    /**
     * Get the effects send level of the part.
     *
     * @return The send level in the range of [0..100]
     */
    public int getEffectsSend ()
    {
        return this.effectsSend;
    }


    /**
     * Set the effects send level of the part.
     *
     * @param effectsSend The send level in the range of [0..100]
     */
    public void setEffectsSend (final int effectsSend)
    {
        this.effectsSend = effectsSend;
    }


    /**
     * Get the output of the part.
     *
     * @return The output: 0 = L-R, 1-4 = 1-2..7-8, 8 = L, 9 = R, 10-17 = 1..8
     */
    public int getOutput ()
    {
        return this.output;
    }


    /**
     * Set the output of the part.
     *
     * @param output The output: 0 = L-R, 1-4 = 1-2..7-8, 8 = L, 9 = R, 10-17 = 1..8
     */
    public void setOutput (final int output)
    {
        this.output = output;
    }


    /**
     * Is the part muted?
     *
     * @return True if muted
     */
    public boolean isMuted ()
    {
        return this.isMuted;
    }


    /**
     * Mute the part.
     *
     * @param isMuted True to mute
     */
    public void setMuted (final boolean isMuted)
    {
        this.isMuted = isMuted;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        this.presetName = readPresetName (data);
        this.midiChannel = getUnsigned (data, 0x22);
        this.transpose = getSigned (data, 0x23);
        this.lowKey = getUnsigned (data, 0x24);
        this.highKey = getUnsigned (data, 0x25);
        this.volume = getUnsigned (data, 0x26);
        this.panning = getSigned (data, 0x27);

        // The minimum size of the chunk ends with the panning
        if (data.length < LENGTH_PART_2)
            return;
        this.fineTune = getSigned (data, 0x29);
        this.effectsChannel = getUnsigned (data, 0x2A);
        this.effectsSend = getUnsigned (data, 0x2B);
        this.output = getUnsigned (data, 0x2C);

        // The mute state is only present in version 3
        if (data.length > 0x2E)
            this.isMuted = getBoolean (data, 0x2E);
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
        setByte (data, 0x23, this.transpose);
        setByte (data, 0x24, this.lowKey);
        setByte (data, 0x25, this.highKey);
        setByte (data, 0x26, this.volume);
        setByte (data, 0x27, this.panning);
        setByte (data, 0x29, this.fineTune);
        setByte (data, 0x2A, this.effectsChannel);
        setByte (data, 0x2B, this.effectsSend);
        setByte (data, 0x2C, this.output);
        if (data.length > 0x2E)
            setBoolean (data, 0x2E, this.isMuted);
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
