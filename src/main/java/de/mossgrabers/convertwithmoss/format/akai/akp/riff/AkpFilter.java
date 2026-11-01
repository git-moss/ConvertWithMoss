// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 key-group filter chunk (filt).
 *
 * @author Jürgen Moßgraber
 */
public class AkpFilter extends AbstractAkpChunk
{
    /** The length of the Filter structure. */
    private static final int LENGTH_FILT   = 10;

    private int              mode          = 0;
    private int              cutoff        = 100;
    private int              resonance     = 0;
    private int              keyboardTrack = 0;
    private final int []     modInputs     = new int [3];
    private int              headroom      = 0;


    /**
     * Default constructor.
     */
    public AkpFilter ()
    {
        super (AkpRiffChunkId.KGRP_FILTER_ID, LENGTH_FILT, 1);
    }


    /**
     * Get the filter mode.
     *
     * @return 0 = 2-POLE LP, 1 = 4-POLE LP, 2 = 2-POLE LP+, 3 = 2-POLE BP, 4 = 4-POLE BP, 5 =
     *         2-POLE BP+, 6 = 1-POLE HP, 7 = 2-POLE HP, 8 = 1-POLE HP+, 9 = LO<>HI, 10 =
     *         LO<>BAND, 11 = BAND<>HI, 12 = NOTCH 1, 13 = NOTCH 2, 14 = NOTCH 3, 15 = WIDE NOTCH,
     *         16 = BI-NOTCH, 17 = PEAK 1, 18 = PEAK 2, 19 = PEAK 3, 20 = WIDE PEAK, 21 = BI-PEAK,
     *         22 = PHASER 1, 23 = PHASER 2, 24 = BI-PHASE, 25 = VOWELISER
     */
    public int getMode ()
    {
        return this.mode;
    }


    /**
     * Set the filter mode [0..25].
     *
     * @param mode The filter mode [0..25]
     */
    public void setMode (final int mode)
    {
        this.mode = mode;
    }


    /**
     * Get the cutoff frequency.
     *
     * @return The cutoff in the range of [0..100]
     */
    public int getCutoff ()
    {
        return this.cutoff;
    }


    /**
     * Set the cutoff in the range of [0..100].
     *
     * @param cutoff The cutoff in the range of [0..100]
     */
    public void setCutoff (final int cutoff)
    {
        this.cutoff = cutoff;
    }


    /**
     * Get the resonance.
     *
     * @return The resonance in the range of [0..12]
     */
    public int getResonance ()
    {
        return this.resonance;
    }


    /**
     * Set the resonance in the range of [0..12].
     *
     * @param resonance The resonance in the range of [0..12]
     */
    public void setResonance (final int resonance)
    {
        this.resonance = resonance;
    }


    /**
     * Get the keyboard tracking of the cutoff.
     *
     * @return The tracking in the range of [-36..36], +12 tracks the cutoff octave by octave
     */
    public int getKeyboardTrack ()
    {
        return this.keyboardTrack;
    }


    /**
     * Set the keyboard tracking in the range of [-36..36].
     *
     * @param keyboardTrack The keyboard tracking in the range of [-36..36]
     */
    public void setKeyboardTrack (final int keyboardTrack)
    {
        this.keyboardTrack = keyboardTrack;
    }


    /**
     * Get the amount of a modulation input. The sources are set in the modulations chunk.
     *
     * @param index The index of the input in the range of [0..2]
     * @return The amount in the range of [-100..100]
     */
    public int getModInput (final int index)
    {
        return this.modInputs[index];
    }


    /**
     * Set the amount of a modulation input.
     *
     * @param index The index of the modulation input [0..2]
     * @param amount The amount in the range of [-100..100]
     */
    public void setModInput (final int index, final int amount)
    {
        this.modInputs[index] = amount;
    }


    /**
     * Get the headroom.
     *
     * @return 0 = 0dB, 1 = 6dB, 2 = 12dB, 3 = 18dB, 4 = 24dB, 5 = 30dB
     */
    public int getHeadroom ()
    {
        return this.headroom;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        this.mode = getUnsigned (data, 0x01);
        this.cutoff = getUnsigned (data, 0x02);
        this.resonance = getUnsigned (data, 0x03);
        this.keyboardTrack = getSigned (data, 0x04);
        for (int i = 0; i < this.modInputs.length; i++)
            this.modInputs[i] = getSigned (data, 0x05 + i);
        this.headroom = getUnsigned (data, 0x08);
    }


    /** {@inheritDoc} */
    @Override
    protected void writeDefaults (final byte [] data, final int targetVersion)
    {
        // Undocumented but always 5 since version 2
        if (targetVersion >= 2)
            data[0x09] = 5;
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        setByte (data, 0x01, this.mode);
        setByte (data, 0x02, this.cutoff);
        setByte (data, 0x03, this.resonance);
        setByte (data, 0x04, this.keyboardTrack);
        for (int i = 0; i < this.modInputs.length; i++)
            setByte (data, 0x05 + i, this.modInputs[i]);
        setByte (data, 0x08, this.headroom);
    }
}
