// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 amplitude or filter envelope chunk (env). Both have the same layout, only the
 * filter envelope uses the depth parameter.
 *
 * @author Jürgen Moßgraber
 */
public class AkpEnvelope extends AbstractAkpChunk
{
    /** The length of the Envelope structure. */
    private static final int LENGTH_ENV           = 18;

    private int              attack               = 0;
    private int              decay                = 50;
    private int              release              = 15;
    private int              sustain              = 100;
    private int              depth                = 0;
    private int              velocityToAttack     = 0;
    private int              keyscale             = 0;
    private int              onVelocityToRelease  = 0;
    private int              offVelocityToRelease = 0;


    /**
     * Default constructor.
     */
    public AkpEnvelope ()
    {
        super (AkpRiffChunkId.KGRP_ENV_ID, LENGTH_ENV, 1);
    }


    /**
     * Get the attack.
     *
     * @return The attack in the range of [0..100]
     */
    public int getAttack ()
    {
        return this.attack;
    }


    /**
     * Set the attack in the range of [0..100].
     *
     * @param attack The attack in the range of [0..100]
     */
    public void setAttack (final int attack)
    {
        this.attack = attack;
    }


    /**
     * Get the decay.
     *
     * @return The decay in the range of [0..100]
     */
    public int getDecay ()
    {
        return this.decay;
    }


    /**
     * Set the decay in the range of [0..100].
     *
     * @param decay The decay in the range of [0..100]
     */
    public void setDecay (final int decay)
    {
        this.decay = decay;
    }


    /**
     * Get the release.
     *
     * @return The release in the range of [0..100]
     */
    public int getRelease ()
    {
        return this.release;
    }


    /**
     * Set the release in the range of [0..100].
     *
     * @param release The release in the range of [0..100]
     */
    public void setRelease (final int release)
    {
        this.release = release;
    }


    /**
     * Get the sustain.
     *
     * @return The sustain in the range of [0..100]
     */
    public int getSustain ()
    {
        return this.sustain;
    }


    /**
     * Set the sustain level in the range of [0..100].
     *
     * @param sustain The sustain level in the range of [0..100]
     */
    public void setSustain (final int sustain)
    {
        this.sustain = sustain;
    }


    /**
     * Get the envelope depth. Only used by the filter envelope.
     *
     * @return The depth in the range of [-100..100]
     */
    public int getDepth ()
    {
        return this.depth;
    }


    /**
     * Set the depth (only filter envelope) in the range of [-100..100].
     *
     * @param depth The depth (only filter envelope) in the range of [-100..100]
     */
    public void setDepth (final int depth)
    {
        this.depth = depth;
    }


    /**
     * Get the velocity to attack modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getVelocityToAttack ()
    {
        return this.velocityToAttack;
    }


    /**
     * Set the velocity to attack modulation in the range of [-100..100].
     *
     * @param velocityToAttack The velocity to attack modulation in the range of [-100..100]
     */
    public void setVelocityToAttack (final int velocityToAttack)
    {
        this.velocityToAttack = velocityToAttack;
    }


    /**
     * Get the key-scale modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getKeyscale ()
    {
        return this.keyscale;
    }


    /**
     * Set the keyboard scaling of the times in the range of [-100..100].
     *
     * @param keyscale The keyboard scaling of the times in the range of [-100..100]
     */
    public void setKeyscale (final int keyscale)
    {
        this.keyscale = keyscale;
    }


    /**
     * Get the note-on velocity to release modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getOnVelocityToRelease ()
    {
        return this.onVelocityToRelease;
    }


    /**
     * Get the note-off velocity to release modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getOffVelocityToRelease ()
    {
        return this.offVelocityToRelease;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        this.attack = getUnsigned (data, 0x01);
        this.decay = getUnsigned (data, 0x03);
        this.release = getUnsigned (data, 0x04);
        this.sustain = getUnsigned (data, 0x07);
        this.depth = getSigned (data, 0x09);
        this.velocityToAttack = getSigned (data, 0x0A);
        this.keyscale = getSigned (data, 0x0C);
        this.onVelocityToRelease = getSigned (data, 0x0E);
        this.offVelocityToRelease = getSigned (data, 0x0F);
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        setByte (data, 0x01, this.attack);
        setByte (data, 0x03, this.decay);
        setByte (data, 0x04, this.release);
        setByte (data, 0x07, this.sustain);
        setByte (data, 0x09, this.depth);
        setByte (data, 0x0A, this.velocityToAttack);
        setByte (data, 0x0C, this.keyscale);
        setByte (data, 0x0E, this.onVelocityToRelease);
        setByte (data, 0x0F, this.offVelocityToRelease);
    }
}
