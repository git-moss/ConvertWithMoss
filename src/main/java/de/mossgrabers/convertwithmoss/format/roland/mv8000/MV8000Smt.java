// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mv8000;

/**
 * One of the 4 sample-mix-table (SMT) slots of a MV-8000 partial. Reads/writes the bit-packed
 * fields inside the 210 bit slot frame of a partial record.
 *
 * @author Jürgen Moßgraber
 */
public class MV8000Smt
{
    /** The size of a SMT slot frame in bits. */
    public static final int      SIZE_BITS          = 210;

    private static final int     OFFSET_SAMPLE_ID   = 9;
    private static final int     WIDTH_SAMPLE_ID    = 14;
    private static final int     OFFSET_SWITCH      = 23;
    private static final int     OFFSET_KEY_FOLLOW  = 25;
    private static final int     OFFSET_LEVEL       = 31;
    private static final int     OFFSET_PAN         = 38;
    private static final int     OFFSET_COARSE_TUNE = 45;
    private static final int     OFFSET_FINE_TUNE   = 52;
    private static final int     OFFSET_VELO_LOW    = 59;
    private static final int     OFFSET_FADE_LOW    = 66;
    private static final int     OFFSET_VELO_HIGH   = 73;
    private static final int     OFFSET_FADE_HIGH   = 80;
    private static final int     OFFSET_PLAY_MODE   = 207;

    /** Pitch key-follow value for +100% (normal chromatic tracking). */
    public static final int      KEY_FOLLOW_NORMAL  = 40;
    /** Pitch key-follow value for 0% (fixed pitch). */
    public static final int      KEY_FOLLOW_OFF     = 32;

    private final MV8000BitArray bits;
    private final int            baseOffset;


    /**
     * Constructor.
     *
     * @param bits The bit array of the partial record
     * @param baseOffset The bit offset of the slot frame inside of the record
     */
    public MV8000Smt (final MV8000BitArray bits, final int baseOffset)
    {
        this.bits = bits;
        this.baseOffset = baseOffset;
    }


    /**
     * Get the ID of the referenced sample.
     *
     * @return The sample ID, 0 if the slot is not used
     */
    public int getSampleId ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_SAMPLE_ID, WIDTH_SAMPLE_ID);
    }


    /**
     * Set the ID of the referenced sample.
     *
     * @param sampleId The sample ID, 0 if the slot is not used
     */
    public void setSampleId (final int sampleId)
    {
        this.bits.setBits (this.baseOffset + OFFSET_SAMPLE_ID, WIDTH_SAMPLE_ID, sampleId);

        // Switch bit which is set on all used slots of the factory patches
        if (sampleId > 0)
            this.bits.setBits (this.baseOffset + OFFSET_SWITCH, 1, 1);
    }


    /**
     * Get the pitch key-follow. The 6 bit value covers -200% to +200% in 12.5% steps: 32 is 0%
     * (fixed pitch), 40 is +100% (normal chromatic tracking).
     *
     * @return The key-follow in the range of 16..48
     */
    public int getKeyFollow ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_KEY_FOLLOW, 6);
    }


    /**
     * Set the pitch key-follow.
     *
     * @param keyFollow The key-follow in the range of 16..48, 32 is 0%, 40 is +100%
     */
    public void setKeyFollow (final int keyFollow)
    {
        this.bits.setBits (this.baseOffset + OFFSET_KEY_FOLLOW, 6, keyFollow);
    }


    /**
     * Get the level of the slot.
     *
     * @return The level in the range of 0..127
     */
    public int getLevel ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_LEVEL, 7);
    }


    /**
     * Set the level of the slot.
     *
     * @param level The level in the range of 0..127
     */
    public void setLevel (final int level)
    {
        this.bits.setBits (this.baseOffset + OFFSET_LEVEL, 7, level);
    }


    /**
     * Get the panning. The left/right halves of factory stereo samples use 32/96.
     *
     * @return The panning in the range of 32 (left) .. 96 (right), 64 is center
     */
    public int getPanning ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_PAN, 7);
    }


    /**
     * Set the panning.
     *
     * @param panning The panning in the range of 32 (left) .. 96 (right), 64 is center
     */
    public void setPanning (final int panning)
    {
        this.bits.setBits (this.baseOffset + OFFSET_PAN, 7, panning);
    }


    /**
     * Get the coarse tuning.
     *
     * @return The coarse tuning in the range of 16..112, 64 is center (+-48 semitones)
     */
    public int getCoarseTune ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_COARSE_TUNE, 7);
    }


    /**
     * Set the coarse tuning.
     *
     * @param coarseTune The coarse tuning in the range of 16..112, 64 is center
     */
    public void setCoarseTune (final int coarseTune)
    {
        this.bits.setBits (this.baseOffset + OFFSET_COARSE_TUNE, 7, coarseTune);
    }


    /**
     * Get the fine tuning.
     *
     * @return The fine tuning in the range of 14..114, 64 is center (+- 50 cent)
     */
    public int getFineTune ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_FINE_TUNE, 7);
    }


    /**
     * Set the fine tuning.
     *
     * @param fineTune The fine tuning in the range of 14..114, 64 is center (+- 50 cent)
     */
    public void setFineTune (final int fineTune)
    {
        this.bits.setBits (this.baseOffset + OFFSET_FINE_TUNE, 7, fineTune);
    }


    /**
     * Get the lower bound of the velocity range.
     *
     * @return The lower bound in the range of 1..127
     */
    public int getVelocityLow ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_VELO_LOW, 7);
    }


    /**
     * Set the lower bound of the velocity range.
     *
     * @param velocityLow The lower bound in the range of 1..127
     */
    public void setVelocityLow (final int velocityLow)
    {
        this.bits.setBits (this.baseOffset + OFFSET_VELO_LOW, 7, velocityLow);
    }


    /**
     * Get the velocity fade width below the lower bound.
     *
     * @return The fade width in the range of 0..127
     */
    public int getVelocityFadeLow ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_FADE_LOW, 7);
    }


    /**
     * Set the velocity fade width below the lower bound.
     *
     * @param fadeLow The fade width in the range of 0..127
     */
    public void setVelocityFadeLow (final int fadeLow)
    {
        this.bits.setBits (this.baseOffset + OFFSET_FADE_LOW, 7, fadeLow);
    }


    /**
     * Get the upper bound of the velocity range.
     *
     * @return The upper bound in the range of 1..127
     */
    public int getVelocityHigh ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_VELO_HIGH, 7);
    }


    /**
     * Set the upper bound of the velocity range.
     *
     * @param velocityHigh The upper bound in the range of 1..127
     */
    public void setVelocityHigh (final int velocityHigh)
    {
        this.bits.setBits (this.baseOffset + OFFSET_VELO_HIGH, 7, velocityHigh);
    }


    /**
     * Get the velocity fade width above the upper bound.
     *
     * @return The fade width in the range of 0..127
     */
    public int getVelocityFadeHigh ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_FADE_HIGH, 7);
    }


    /**
     * Set the velocity fade width above the upper bound.
     *
     * @param fadeHigh The fade width in the range of 0..127
     */
    public void setVelocityFadeHigh (final int fadeHigh)
    {
        this.bits.setBits (this.baseOffset + OFFSET_FADE_HIGH, 7, fadeHigh);
    }


    /**
     * Get the play mode. 0: loop forward, 1: one-shot (ignores the loop, plays the sample until its
     * end), 2/3/4: rarely used alternative loop/one-shot modes (uneven values do not loop).
     *
     * @return The play mode in the range of 0..4
     */
    public int getPlayMode ()
    {
        return this.bits.getBits (this.baseOffset + OFFSET_PLAY_MODE, 3);
    }


    /**
     * Set the play mode.
     *
     * @param playMode The play mode in the range of 0..4
     */
    public void setPlayMode (final int playMode)
    {
        this.bits.setBits (this.baseOffset + OFFSET_PLAY_MODE, 3, playMode);
    }


    /**
     * Is the slot played as a one-shot (ignores the loop and plays the sample until its end)?
     *
     * @return True if played as one-shot
     */
    public boolean isOneShot ()
    {
        return this.getPlayMode () % 2 == 1;
    }
}
