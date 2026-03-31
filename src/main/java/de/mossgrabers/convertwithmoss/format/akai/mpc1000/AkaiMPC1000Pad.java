// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc1000;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;


/**
 * An Akai MPC1000 pad. One pad can have assigned up to 4 samples.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC1000Pad
{
    private final List<AkaiMPC1000Sample> samples;

    /** 0="Poly", 1="Mono" */
    @SuppressWarnings("unused")
    private final int                     voiceOverlap;
    /** 0="Off", 1 to 32 */
    @SuppressWarnings("unused")
    private final int                     muteGroup;
    private final int                     attack;
    private final int                     decay;
    private final int                     decayMode;
    private final int                     velocityToLevel;
    private final int                     filter1Type;
    private final int                     filter1Freq;
    private final int                     filter1Res;
    private final int                     filter1VelocityToFrequency;

    @SuppressWarnings("unused")
    private final int                     filter2Type;
    @SuppressWarnings("unused")
    private final int                     filter2Freq;
    @SuppressWarnings("unused")
    private final int                     filter2Res;
    @SuppressWarnings("unused")
    private final int                     filter2VelocityToFrequency;

    private final int                     mixerLevel;
    private final int                     mixerPan;

    /** 0="Stereo", 1="1-2", 2="3-4" */
    @SuppressWarnings("unused")
    private final int                     output;
    /** 0="Off", 1="1", 2="2" */
    @SuppressWarnings("unused")
    private final int                     fxSend;
    /** [0..100] */
    @SuppressWarnings("unused")
    private final int                     fxSendLevel;
    /** 0="0dB", 1="-6dB", 2="-12dB" */
    @SuppressWarnings("unused")
    private final int                     filterAttenuation;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @param samples The samples of the pad
     * @throws IOException Could not read
     */
    public AkaiMPC1000Pad (final InputStream input, final List<AkaiMPC1000Sample> samples) throws IOException
    {
        this.samples = samples;

        // Padding
        input.skipNBytes (2);

        this.voiceOverlap = input.read ();
        this.muteGroup = input.read ();

        // Padding
        input.read ();
        // Unknown, always 0x01
        input.read ();

        this.attack = input.read ();
        this.decay = input.read ();
        this.decayMode = input.read ();

        // Padding
        input.skipNBytes (2);

        this.velocityToLevel = input.read ();

        // Padding
        input.skipNBytes (5);

        this.filter1Type = input.read ();
        this.filter1Freq = input.read ();
        this.filter1Res = input.read ();
        // Padding
        input.skipNBytes (4);
        this.filter1VelocityToFrequency = input.read ();

        this.filter2Type = input.read ();
        this.filter2Freq = input.read ();
        this.filter2Res = input.read ();
        // Padding
        input.skipNBytes (4);
        this.filter2VelocityToFrequency = input.read ();

        // Padding
        input.skipNBytes (14);

        this.mixerLevel = input.read ();
        this.mixerPan = input.read ();
        this.output = input.read ();
        this.fxSend = input.read ();
        this.fxSendLevel = input.read ();
        this.filterAttenuation = input.read ();

        // Padding
        input.skipNBytes (15);
    }


    /**
     * Get the samples.
     * 
     * @return The samples
     */
    public List<AkaiMPC1000Sample> getSamples ()
    {
        return this.samples;
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
     * Get the decay mode.
     * 
     * @return 0=End, 1=Start
     */
    public int getDecayMode ()
    {
        return this.decayMode;
    }


    /**
     * Get the level modulation by velocity.
     * 
     * @return The intensity in the range of [0..100]
     */
    public int getVelocityToLevel ()
    {
        return this.velocityToLevel;
    }


    /**
     * Get the type of filter 1.
     * 
     * @return 0=Off, 1=Lowpass, 2=Bandpass, 3=Highpass
     */
    public int getFilter1Type ()
    {
        return this.filter1Type;
    }


    /**
     * Get the frequency of filter 1.
     * 
     * @return The frequency in the range of [0..100]
     */
    public int getFilter1Freq ()
    {
        return this.filter1Freq;
    }


    /**
     * Get the resonance of filter 1.
     * 
     * @return The resonance in the range of [0..100]
     */
    public int getFilter1Res ()
    {
        return this.filter1Res;
    }


    /**
     * Get the frequency of filter 1 modulation by velocity.
     * 
     * @return The intensity in the range of [0..100]
     */
    public int getFilter1VelocityToFrequency ()
    {
        return this.filter1VelocityToFrequency;
    }


    /**
     * Get the mixer level.
     * 
     * @return The level in the range of [0..100]
     */
    public int getMixerLevel ()
    {
        return this.mixerLevel;
    }


    /**
     * Get the mixer panning.
     * 
     * @return The panning: 0 to 49=Left, 50=Center, 51 to 100=Right
     */
    public int getMixerPan ()
    {
        return this.mixerPan;
    }
}
