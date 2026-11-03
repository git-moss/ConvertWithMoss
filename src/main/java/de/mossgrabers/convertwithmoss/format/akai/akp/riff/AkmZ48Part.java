// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A Z4/Z8 part chunk of a multi. It is a sub-chunk of the 'z48 ' chunk. The format is described in
 * documentation/design/AKAI_AKP_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class AkmZ48Part extends AbstractAkpChunk
{
    /** The priority NORMAL. */
    public static final int  PRIORITY_NORMAL = 1;
    /** The priority HIGH. */
    public static final int  PRIORITY_HIGH   = 2;
    /** The priority HOLD. */
    public static final int  PRIORITY_HOLD   = 3;

    /** The length of the Part structure. */
    private static final int LENGTH_PART     = 0x31;
    /** The maximum length of the preset name. */
    private static final int MAX_NAME_LENGTH = 32;

    private String           presetName      = "";
    private int              tune            = 0;
    private int              midiChannel     = 0;
    private double           level           = 0;
    private int              panning         = 0;
    private int              output          = 0;
    private int              effects         = 0;
    private double           effectsSend     = -40;
    private int              lowKey          = 21;
    private int              highKey         = 127;
    private int              priority        = PRIORITY_NORMAL;
    private boolean          isMuted         = false;


    /**
     * Default constructor.
     */
    public AkmZ48Part ()
    {
        super (AkmRiffChunkId.PART_ID, LENGTH_PART, 1);
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
     * Get the tuning of the part.
     *
     * @return The tuning in the range of [-3600..3600] cents
     */
    public int getTune ()
    {
        return this.tune;
    }


    /**
     * Set the tuning of the part.
     *
     * @param tune The tuning in the range of [-3600..3600] cents
     */
    public void setTune (final int tune)
    {
        this.tune = tune;
    }


    /**
     * Get the MIDI channel of the part.
     *
     * @return The channel in the range of [0..31] = 1A-16A, 1B-16B
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
     * Get the level of the part.
     *
     * @return The level in the range of [-60..6] dB, stored with a resolution of 0.1dB
     */
    public double getLevel ()
    {
        return this.level;
    }


    /**
     * Set the level of the part.
     *
     * @param level The level in the range of [-60..6] dB, stored with a resolution of 0.1dB
     */
    public void setLevel (final double level)
    {
        this.level = level;
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
     * Get the output of the part.
     *
     * @return The output: 0 = L-R, 1-4 = 1-2..7-8, 5 = L, 6 = R, 7-14 = 1..8
     */
    public int getOutput ()
    {
        return this.output;
    }


    /**
     * Set the output of the part.
     *
     * @param output The output: 0 = L-R, 1-4 = 1-2..7-8, 5 = L, 6 = R, 7-14 = 1..8
     */
    public void setOutput (final int output)
    {
        this.output = output;
    }


    /**
     * Get the effects routing of the part.
     *
     * @return The effects: 0 = OFF, 1 = A, 2 = B, 3 = C, 4 = D, 5 = AB, 6 = CD
     */
    public int getEffects ()
    {
        return this.effects;
    }


    /**
     * Set the effects routing of the part.
     *
     * @param effects The effects: 0 = OFF, 1 = A, 2 = B, 3 = C, 4 = D, 5 = AB, 6 = CD
     */
    public void setEffects (final int effects)
    {
        this.effects = effects;
    }


    /**
     * Get the effects send level of the part.
     *
     * @return The send level in the range of [-40..6] dB, stored with a resolution of 0.1dB
     */
    public double getEffectsSend ()
    {
        return this.effectsSend;
    }


    /**
     * Set the effects send level of the part.
     *
     * @param effectsSend The send level in the range of [-40..6] dB, stored with a resolution of
     *            0.1dB
     */
    public void setEffectsSend (final double effectsSend)
    {
        this.effectsSend = effectsSend;
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
     * Get the voice priority of the part.
     *
     * @return The priority: 1 = NORMAL, 2 = HIGH, 3 = HOLD
     */
    public int getPriority ()
    {
        return this.priority;
    }


    /**
     * Set the voice priority of the part.
     *
     * @param priority The priority: 1 = NORMAL, 2 = HIGH, 3 = HOLD
     */
    public void setPriority (final int priority)
    {
        this.priority = priority;
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
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0x01; i < 0x01 + MAX_NAME_LENGTH && data[i] != 0; i++)
            sb.append ((char) getUnsigned (data, i));
        this.presetName = sb.toString ();

        this.tune = getSigned16 (data, 0x21);
        this.midiChannel = getUnsigned (data, 0x24);
        this.level = getSigned16 (data, 0x25) / 10.0;
        this.panning = getSigned (data, 0x27);
        this.output = getUnsigned (data, 0x28);
        this.effects = getUnsigned (data, 0x29);
        this.effectsSend = getSigned16 (data, 0x2A) / 10.0;
        this.lowKey = getUnsigned (data, 0x2C);
        this.highKey = getUnsigned (data, 0x2D);
        this.priority = getUnsigned (data, 0x2E);
        this.isMuted = getBoolean (data, 0x30);
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        final int nameLength = Math.min (this.presetName.length (), MAX_NAME_LENGTH);
        for (int i = 0; i < MAX_NAME_LENGTH; i++)
            setByte (data, 0x01 + i, i < nameLength ? this.presetName.charAt (i) : 0);

        setSigned16 (data, 0x21, this.tune);
        setByte (data, 0x24, this.midiChannel);
        setSigned16 (data, 0x25, (int) Math.round (this.level * 10.0));
        setByte (data, 0x27, this.panning);
        setByte (data, 0x28, this.output);
        setByte (data, 0x29, this.effects);
        setSigned16 (data, 0x2A, (int) Math.round (this.effectsSend * 10.0));
        setByte (data, 0x2C, this.lowKey);
        setByte (data, 0x2D, this.highKey);
        setByte (data, 0x2E, this.priority);
        setBoolean (data, 0x30, this.isMuted);
    }


    /**
     * Get a signed 16-bit little-endian value.
     *
     * @param data The data
     * @param offset The offset of the first byte
     * @return The value in the range of [-32768..32767]
     */
    private static int getSigned16 (final byte [] data, final int offset)
    {
        return (short) (getUnsigned (data, offset) | getUnsigned (data, offset + 1) << 8);
    }


    /**
     * Set a signed 16-bit little-endian value.
     *
     * @param data The data
     * @param offset The offset of the first byte
     * @param value The value in the range of [-32768..32767]
     */
    private static void setSigned16 (final byte [] data, final int offset, final int value)
    {
        setByte (data, offset, value & 0xFF);
        setByte (data, offset + 1, value >> 8 & 0xFF);
    }
}
