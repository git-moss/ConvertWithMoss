// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;


/**
 * An Akai ADSR envelope.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiEnvelope
{
    // 0..99
    private byte attack;
    // 0..99
    private byte decay;
    // 0..99
    private byte sustain;
    // 0..99
    private byte release;
    // -50..50
    @SuppressWarnings("unused")
    private byte velocityToAttack;
    // -50..50
    @SuppressWarnings("unused")
    private byte velocityToRelease;
    // -50..50
    @SuppressWarnings("unused")
    private byte offVelocityToRelease;
    // -50..50
    @SuppressWarnings("unused")
    private byte keyToDecayAndRelease;


    /**
     * Constructor.
     * 
     * @param disk The Akai disk from which to read
     * @throws IOException Could not read the envelope
     */
    public AkaiEnvelope (final IAkaiImage disk) throws IOException
    {
        this.attack = disk.readInt8 ();
        this.decay = disk.readInt8 ();
        this.sustain = disk.readInt8 ();
        this.release = disk.readInt8 ();
        this.velocityToAttack = disk.readInt8 ();
        this.velocityToRelease = disk.readInt8 ();
        this.offVelocityToRelease = disk.readInt8 ();
        this.keyToDecayAndRelease = disk.readInt8 ();
    }


    /**
     * Get the attack value.
     * 
     * @return The attack value in the range of [0..99]
     */
    public int getAttack ()
    {
        return this.attack & 0xFF;
    }


    /**
     * Get the decay value.
     * 
     * @return The decay value in the range of [0..99]
     */
    public byte getDecay ()
    {
        return this.decay;
    }


    /**
     * Get the sustain value.
     * 
     * @return The sustain value in the range of [0..99]
     */
    public byte getSustain ()
    {
        return this.sustain;
    }


    /**
     * Get the release value.
     * 
     * @return The release value in the range of [0..99]
     */
    public byte getRelease ()
    {
        return this.release;
    }
}