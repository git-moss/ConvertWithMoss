// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s3000;

import java.io.IOException;


/**
 * Parameters for the Akai key-group sample description.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiKeygroupSample extends AkaiDiskElement
{
    // AKAI character set
    private String name;
    // 0..127
    private byte   lowVelocity;
    // 0..127
    private byte   highVelocity;
    // -128..127 (-50..50 cents)
    private byte   tuneCents;
    // -50..+50
    private byte   tuneSemitones;
    // -50..+50
    private byte   loudness;
    // -50..+50
    private byte   filter;
    // -50..+50
    private byte   pan;
    // 0=AS_SAMPLE 1=LOOP_IN_REL, 2=LOOP_UNTIL_REL 3=NO_LOOP, 4=PLAY_TO_END
    private byte   loopMode;


    /**
     * Constructor.
     * 
     * @param disk The disk to read from
     * @throws IOException Could not read the key-group
     */
    public AkaiKeygroupSample (final AkaiDiskImage disk) throws IOException
    {
        this.name = disk.readText ();

        this.lowVelocity = disk.readInt8 ();
        this.highVelocity = disk.readInt8 ();
        this.tuneCents = disk.readInt8 ();
        this.tuneSemitones = disk.readInt8 ();
        this.loudness = disk.readInt8 ();
        this.filter = disk.readInt8 ();
        this.pan = disk.readInt8 ();
        this.loopMode = disk.readInt8 ();

        // Unused
        disk.readInt8 ();
        disk.readInt8 ();
        disk.readInt16 ();
    }


    /**
     * Get the name of the sample.
     *
     * @return The name of the sample
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the low velocity of the sample range.
     * 
     * @return The low velocity
     */
    public byte getLowVelocity ()
    {
        return this.lowVelocity;
    }


    /**
     * Get the upper velocity of the sample range.
     * 
     * @return The low velocity
     */
    public byte getHighVelocity ()
    {
        return this.highVelocity;
    }


    /**
     * Get the fine-tuning.
     *
     * @return The tuning by in the range of [-128..127], needs to be scaled to [-50..+50]!
     */
    public int getTuneCents ()
    {
        return this.tuneCents;
    }


    /**
     * Get the semi-tone tuning.
     *
     * @return The tuning in the range of [-50..+50]
     */
    public int getTuneSemitones ()
    {
        return this.tuneSemitones;
    }


    /**
     * Get the loudness adjustment.
     *
     * @return The loudness in the range of [-50..+50]
     */
    public byte getLoudness ()
    {
        return this.loudness;
    }


    /**
     * The cutoff of the fixed 18dB/octave low-pass filter.
     * 
     * @return The cutoff value in the range of [0..99], 0 is fully closed, 99 is fully open
     */
    public byte getFilter ()
    {
        return this.filter;
    }


    /**
     * Get the panning.
     * 
     * @return The panning in the range of [-50..50], -50 is full left, +50 full right
     */
    public byte getPan ()
    {
        return this.pan;
    }


    /**
     * Get the loop mode.
     * 
     * @return The loop mode: 0=AS_SAMPLE 1=LOOP_IN_REL, 2=LOOP_UNTIL_REL 3=NO_LOOP, 4=PLAY_TO_END
     */
    public byte getLoopMode ()
    {
        return this.loopMode;
    }
}