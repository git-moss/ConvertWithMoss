// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 auxiliary envelope chunk (the 3rd env chunk). It is a 4 stage envelope with rates
 * and levels.
 *
 * @author Jürgen Moßgraber
 */
public class AkpAuxEnvelope extends AbstractAkpChunk
{
    /** The length of the Envelope structure. */
    private static final int LENGTH_ENV          = 18;

    private final int []     rates               = new int []
    {
        0,
        50,
        50,
        15
    };
    private final int []     levels              = new int []
    {
        100,
        100,
        100,
        0
    };
    private int              velocityToRate1     = 0;
    private int              keyboardToRate2And4 = 0;
    private int              velocityToRate4     = 0;
    private int              offVelocityToRate4  = 0;
    private int              velocityToOutLevel  = 0;


    /**
     * Default constructor.
     */
    public AkpAuxEnvelope ()
    {
        super (AkpRiffChunkId.KGRP_ENV_ID, LENGTH_ENV, 1);
    }


    /**
     * Get the rate of a stage.
     *
     * @param stage The stage in the range of [0..3]
     * @return The rate in the range of [0..100]
     */
    public int getRate (final int stage)
    {
        return this.rates[stage];
    }


    /**
     * Set the rate of a stage.
     *
     * @param stage The index of the stage [0..3]
     * @param rate The rate in the range of [0..100]
     */
    public void setRate (final int stage, final int rate)
    {
        this.rates[stage] = rate;
    }


    /**
     * Get the level of a stage.
     *
     * @param stage The stage in the range of [0..3]
     * @return The level in the range of [0..100]
     */
    public int getLevel (final int stage)
    {
        return this.levels[stage];
    }


    /**
     * Set the level of a stage.
     *
     * @param stage The index of the stage [0..3]
     * @param level The level in the range of [0..100]
     */
    public void setLevel (final int stage, final int level)
    {
        this.levels[stage] = level;
    }


    /**
     * Get the velocity to rate 1 modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getVelocityToRate1 ()
    {
        return this.velocityToRate1;
    }


    /**
     * Set the velocity to rate 1 modulation in the range of [-100..100].
     *
     * @param velocityToRate1 The velocity to rate 1 modulation in the range of [-100..100]
     */
    public void setVelocityToRate1 (final int velocityToRate1)
    {
        this.velocityToRate1 = velocityToRate1;
    }


    /**
     * Get the keyboard to rate 2 and 4 modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getKeyboardToRate2And4 ()
    {
        return this.keyboardToRate2And4;
    }


    /**
     * Set the keyboard to rate 2 and 4 modulation in the range of [-100..100].
     *
     * @param keyboardToRate2And4 The keyboard to rate 2 and 4 modulation in the range of
     *            [-100..100]
     */
    public void setKeyboardToRate2And4 (final int keyboardToRate2And4)
    {
        this.keyboardToRate2And4 = keyboardToRate2And4;
    }


    /**
     * Get the note-on velocity to rate 4 modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getVelocityToRate4 ()
    {
        return this.velocityToRate4;
    }


    /**
     * Get the note-off velocity to rate 4 modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getOffVelocityToRate4 ()
    {
        return this.offVelocityToRate4;
    }


    /**
     * Get the velocity to output level modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getVelocityToOutLevel ()
    {
        return this.velocityToOutLevel;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        for (int i = 0; i < 4; i++)
        {
            this.rates[i] = getUnsigned (data, 0x01 + i);
            this.levels[i] = getUnsigned (data, 0x05 + i);
        }
        this.velocityToRate1 = getSigned (data, 0x0A);
        this.keyboardToRate2And4 = getSigned (data, 0x0C);
        this.velocityToRate4 = getSigned (data, 0x0E);
        this.offVelocityToRate4 = getSigned (data, 0x0F);
        this.velocityToOutLevel = getSigned (data, 0x10);
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        for (int i = 0; i < 4; i++)
        {
            setByte (data, 0x01 + i, this.rates[i]);
            setByte (data, 0x05 + i, this.levels[i]);
        }
        setByte (data, 0x0A, this.velocityToRate1);
        setByte (data, 0x0C, this.keyboardToRate2And4);
        setByte (data, 0x0E, this.velocityToRate4);
        setByte (data, 0x0F, this.offVelocityToRate4);
        setByte (data, 0x10, this.velocityToOutLevel);
    }
}
