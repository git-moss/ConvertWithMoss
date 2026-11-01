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
     * Get the decay.
     *
     * @return The decay in the range of [0..100]
     */
    public int getDecay ()
    {
        return this.decay;
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
     * Get the sustain.
     *
     * @return The sustain in the range of [0..100]
     */
    public int getSustain ()
    {
        return this.sustain;
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
     * Get the velocity to attack modulation.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getVelocityToAttack ()
    {
        return this.velocityToAttack;
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
}
