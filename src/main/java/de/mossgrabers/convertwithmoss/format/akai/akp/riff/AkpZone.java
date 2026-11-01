// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 velocity zone chunk (zone). Version 1 and 2 have 46 bytes, version 3 has 48 bytes.
 *
 * @author Jürgen Moßgraber
 */
public class AkpZone extends AbstractAkpChunk
{
    /** Playback: NO LOOPING. */
    public static final int  PLAYBACK_NO_LOOPING         = 0;
    /** Playback: ONE SHOT. */
    public static final int  PLAYBACK_ONE_SHOT           = 1;
    /** Playback: LOOP IN REL. */
    public static final int  PLAYBACK_LOOP_IN_RELEASE    = 2;
    /** Playback: LOOP UNTIL REL. */
    public static final int  PLAYBACK_LOOP_UNTIL_RELEASE = 3;
    /** Playback: AS SAMPLE in version 1 of the zone chunk. */
    public static final int  PLAYBACK_AS_SAMPLE_V1       = 4;
    /** Playback: AS SAMPLE since version 2 of the zone chunk. */
    public static final int  PLAYBACK_AS_SAMPLE_V2       = 6;

    /** The length of the Zone structure (version 1). */
    private static final int LENGTH_ZONE                 = 46;
    /** The maximum length of the sample name. */
    private static final int MAX_NAME_LENGTH             = 20;
    /** The output value of the first mono output. */
    private static final int OUTPUT_MONO_FIRST           = 9;
    /** The output value of the last mono output. */
    private static final int OUTPUT_MONO_LAST            = 24;

    private String           sampleName                  = "";
    private int              lowVelocity                 = 0;
    private int              highVelocity                = 127;
    private int              fineTune                    = 0;
    private int              semitoneTune                = 0;
    private int              filter                      = 0;
    private int              panning                     = 0;
    private int              playback                    = PLAYBACK_AS_SAMPLE_V1;
    private int              output                      = 0;
    private int              level                       = 0;
    private boolean          isKeyboardTrack             = true;
    private int              velocityToStart             = 0;


    /**
     * Default constructor.
     */
    public AkpZone ()
    {
        super (AkpRiffChunkId.KGRP_ZONE_ID, LENGTH_ZONE, 1);
    }


    /**
     * Is a sample assigned to the zone?
     *
     * @return True if a sample is assigned
     */
    public boolean isUsed ()
    {
        return !this.sampleName.isEmpty ();
    }


    /**
     * Get the name of the assigned sample.
     *
     * @return The name of the sample file without the file ending, empty if no sample is assigned
     */
    public String getSampleName ()
    {
        return this.sampleName;
    }


    /**
     * Get the lower bound of the velocity range.
     *
     * @return The velocity in the range of [0..127]
     */
    public int getLowVelocity ()
    {
        return this.lowVelocity;
    }


    /**
     * Get the upper bound of the velocity range.
     *
     * @return The velocity in the range of [0..127]
     */
    public int getHighVelocity ()
    {
        return this.highVelocity;
    }


    /**
     * Get the fine tuning of the zone.
     *
     * @return The tuning in cents in the range of [-50..50]
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Get the semitone tuning of the zone.
     *
     * @return The tuning in the range of [-36..36]
     */
    public int getSemitoneTune ()
    {
        return this.semitoneTune;
    }


    /**
     * Get the filter cutoff offset of the zone.
     *
     * @return The offset in the range of [-100..100]
     */
    public int getFilter ()
    {
        return this.filter;
    }


    /**
     * Get the panning (or balance) of the zone.
     *
     * @return The panning in the range of [-50..50] (L50..R50)
     */
    public int getPanning ()
    {
        return this.panning;
    }


    /**
     * Get the playback mode. The value of AS SAMPLE depends on the version of the zone chunk, use
     * isPlaybackAsSample to check for it.
     *
     * @return The playback mode, see the PLAYBACK_* constants
     */
    public int getPlayback ()
    {
        return this.playback;
    }


    /**
     * Check if the playback mode is set to AS SAMPLE which uses the loop settings of the sample.
     *
     * @return True if AS SAMPLE
     */
    public boolean isPlaybackAsSample ()
    {
        return this.playback == (this.version < 2 ? PLAYBACK_AS_SAMPLE_V1 : PLAYBACK_AS_SAMPLE_V2);
    }


    /**
     * Get the output.
     *
     * @return 0 = MULTI, 1-8 = stereo outputs 1/2 to 15/16, 9-24 = mono outputs 1 to 16
     */
    public int getOutput ()
    {
        return this.output;
    }


    /**
     * Check if the zone is routed to one of the mono outputs. The panning of the zone has no
     * effect in that case.
     *
     * @return True if routed to one of the mono outputs 1 to 16
     */
    public boolean isMonoOutput ()
    {
        return this.output >= OUTPUT_MONO_FIRST && this.output <= OUTPUT_MONO_LAST;
    }


    /**
     * Get the level of the zone.
     *
     * @return The level in the range of [-100..100]
     */
    public int getLevel ()
    {
        return this.level;
    }


    /**
     * Does the pitch of the zone track the keyboard?
     *
     * @return True if enabled
     */
    public boolean isKeyboardTrack ()
    {
        return this.isKeyboardTrack;
    }


    /**
     * Get the velocity to sample start modulation.
     *
     * @return The amount in the range of [-9999..9999]
     */
    public int getVelocityToStart ()
    {
        return this.velocityToStart;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        // Unused zones contain random data on newer models, only the length of the sample name is
        // reliable
        final int nameLength = Math.min (getUnsigned (data, 0x01), MAX_NAME_LENGTH);
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < nameLength; i++)
            sb.append ((char) getUnsigned (data, 0x02 + i));
        this.sampleName = sb.toString ().trim ();

        this.lowVelocity = getUnsigned (data, 0x22);
        this.highVelocity = getUnsigned (data, 0x23);
        this.fineTune = getSigned (data, 0x24);
        this.semitoneTune = getSigned (data, 0x25);
        this.filter = getSigned (data, 0x26);
        this.panning = getSigned (data, 0x27);
        this.playback = getUnsigned (data, 0x28);
        this.output = getUnsigned (data, 0x29);
        this.level = getSigned (data, 0x2A);
        this.isKeyboardTrack = getBoolean (data, 0x2B);
        this.velocityToStart = (short) (getUnsigned (data, 0x2C) | getUnsigned (data, 0x2D) << 8);
    }
}
