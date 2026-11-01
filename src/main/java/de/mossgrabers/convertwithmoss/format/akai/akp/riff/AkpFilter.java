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
     * Get the cutoff frequency.
     *
     * @return The cutoff in the range of [0..100]
     */
    public int getCutoff ()
    {
        return this.cutoff;
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
     * Get the keyboard tracking of the cutoff.
     *
     * @return The tracking in the range of [-36..36], +12 tracks the cutoff octave by octave
     */
    public int getKeyboardTrack ()
    {
        return this.keyboardTrack;
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
}
