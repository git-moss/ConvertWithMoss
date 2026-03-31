// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;


/**
 * Parameters for the Akai key-group.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiKeygroup
{
    // 24..127
    private byte                        lowKey;
    // 24..127
    private byte                        highKey;
    // -128..127 (-50..+50 cents)
    private byte                        tuneCents;
    // -50..50
    private byte                        tuneSemitones;
    // 0..99
    private byte                        filter;
    // 0..24 semi-tone/octave
    @SuppressWarnings("unused")
    private byte                        keyToFilter;
    // -50..50
    private byte                        velocityToFilter;
    // -50..50
    @SuppressWarnings("unused")
    private byte                        pressureToFilter;
    // -50..50
    private byte                        envelope2ToFilter;

    private final AkaiEnvelope          amplitudeEnvelope;
    private final AkaiEnvelope          auxEnvelope;

    // -50..50
    @SuppressWarnings("unused")
    private byte                        velocityToEnvelope2ToFilter;
    // -50..50
    private byte                        envelope2ToPitch;
    @SuppressWarnings("unused")
    private boolean                     velocityZoneCrossfade;
    @SuppressWarnings("unused")
    private int                         velocityZoneUsed;
    private final AkaiKeygroupSample [] samples;

    // -50..50
    private byte                        beatDetune;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private boolean                     holdAttackUntilLoop;
    // Sample 1-4 key tracking : 0=TRACK 1=FIXED
    private final boolean []            sampleKeyTracking      = new boolean [4];
    // Sample 1-4 AUX out offset 0..7
    private final byte []               sampleAuxOutOffset     = new byte [4];
    // -9999..9999 (16-bit signed)
    private final short []              velocityToSampleStart  = new short [4];
    // -50..50
    private final byte []               velocityToVolumeOffset = new byte [4];


    /**
     * Constructor.
     * 
     * @param disk The disk to read from
     * @throws IOException Could not read the key-group
     */
    public AkaiKeygroup (final IAkaiImage disk) throws IOException
    {
        if (disk.readInt8 () != AkaiDiskElement.AKAI_KEYGROUP_ID)
            throw new IOException ("Not a key-group.");

        // Next key-group address
        disk.readInt16 ();

        this.lowKey = disk.readInt8 ();
        this.highKey = disk.readInt8 ();
        this.tuneCents = disk.readInt8 ();
        this.tuneSemitones = disk.readInt8 ();
        this.filter = disk.readInt8 ();
        this.keyToFilter = disk.readInt8 ();
        this.velocityToFilter = disk.readInt8 ();
        this.pressureToFilter = disk.readInt8 ();
        this.envelope2ToFilter = disk.readInt8 ();

        this.amplitudeEnvelope = new AkaiEnvelope (disk);
        this.auxEnvelope = new AkaiEnvelope (disk);

        this.velocityToEnvelope2ToFilter = disk.readInt8 ();
        this.envelope2ToPitch = disk.readInt8 ();
        this.velocityZoneCrossfade = disk.readInt8 () != 0;
        this.velocityZoneUsed = disk.readInt8 () & 0xFF;
        // FF FF
        disk.readInt8 ();
        disk.readInt8 ();

        this.samples = new AkaiKeygroupSample [4];
        for (int i = 0; i < 4; i++)
            this.samples[i] = new AkaiKeygroupSample (disk);

        this.beatDetune = disk.readInt8 ();
        this.holdAttackUntilLoop = disk.readInt8 () != 0;

        for (int i = 0; i < 4; i++)
            this.sampleKeyTracking[i] = disk.readInt8 () == 0;

        for (int i = 0; i < 4; i++)
            this.sampleAuxOutOffset[i] = disk.readInt8 ();

        for (int i = 0; i < 4; i++)
            this.velocityToSampleStart[i] = disk.readInt8 ();

        for (int i = 0; i < 4; i++)
            this.velocityToVolumeOffset[i] = disk.readInt8 ();
    }


    /**
     * Get the lower key of the key-range.
     *
     * @return The lower key in the range of [0..127]
     */
    public int getLowKey ()
    {
        return this.lowKey;
    }


    /**
     * Get the upper key of the key-range.
     *
     * @return The upper key in the range of [0..127]
     */
    public int getHighKey ()
    {
        return this.highKey;
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
     * Used to create a fixed offset to the original pitch. Unlike the TUNE parameter, this tuning
     * offset is constant, no matter what the played pitch of the sample.
     * 
     * @return The beat de-tune value in the range of [-50..+50]
     */
    public byte getBeatDetune ()
    {
        return this.beatDetune;
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
     * The intensity of the keyboard velocity on the filter cutoff.
     * 
     * @return The intensity in the range of [-50..+50]
     */
    public byte getVelocityToFilter ()
    {
        return this.velocityToFilter;
    }


    /**
     * Get the amplitude envelope (envelope 1).
     * 
     * @return The envelope
     */
    public AkaiEnvelope getAmplitudeEnvelope ()
    {
        return this.amplitudeEnvelope;
    }


    /**
     * Get the auxiliary envelope (envelope 2) which might be used for filter cutoff or pitch.
     * 
     * @return The envelope
     */
    public AkaiEnvelope getAuxEnvelope ()
    {
        return this.auxEnvelope;
    }


    /**
     * The intensity of the 2nd envelope on the filter cutoff.
     * 
     * @return The intensity in the range of [-50..+50]
     */
    public byte getEnvelope2ToFilter ()
    {
        return this.envelope2ToFilter;
    }


    /**
     * The intensity of the 2nd envelope on the pitch.
     * 
     * @return The intensity in the range of [-50..+50]
     */
    public byte getEnvelope2ToPitch ()
    {
        return this.envelope2ToPitch;
    }


    /**
     * Get the max. 4 sample descriptions.
     *
     * @return The sample descriptions
     */
    public AkaiKeygroupSample [] getSamples ()
    {
        return this.samples;
    }


    /**
     * Get the sample key tracking for all 4 samples.
     * 
     * @return True if tracking is enabled
     */
    public boolean [] getSampleKeyTracking ()
    {
        return this.sampleKeyTracking;
    }
}