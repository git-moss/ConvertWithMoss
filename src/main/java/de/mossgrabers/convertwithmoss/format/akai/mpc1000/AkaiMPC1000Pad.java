// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc1000;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


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
    private final int                     muteGroup;
    private final int                     attack;
    private final int                     decay;
    private final int                     decayMode;
    private final int                     velocityToLevel;
    private final int                     filter1Type;
    private final int                     filter1Freq;
    private final int                     filter1Res;
    private final int                     filter1VelocityToFrequency;
    /** [0..100] */
    private final int                     filter1EnvelopeTime;
    /** [-50..50] */
    private final int                     filter1EnvelopeAmount;

    @SuppressWarnings("unused")
    private final int                     filter2Type;
    @SuppressWarnings("unused")
    private final int                     filter2Freq;
    @SuppressWarnings("unused")
    private final int                     filter2Res;
    @SuppressWarnings("unused")
    private final int                     filter2VelocityToFrequency;

    /** Rate and delay in ticks, 96 ticks = 1 beat */
    private final int                     lfoRate;
    private final int                     lfoDelay;
    /** 0=Triangle, 1=Sine, 2=Square, 3=Saw, 4=Saw Down, 5=Random */
    private final int                     lfoWaveform;
    /** The LFO amounts in the range of [0..100] */
    private final int                     lfoPitch;
    private final int                     lfoFilter1;
    @SuppressWarnings("unused")
    private final int                     lfoFilter2;
    private final int                     lfoLevel;

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
        // Overlap x-fade in the Amp Envelope window, always 1 in all test files
        input.read ();

        this.attack = input.read ();
        this.decay = input.read ();
        this.decayMode = input.read ();

        // Velocity to attack and velocity to start, not supported
        input.skipNBytes (2);

        this.velocityToLevel = input.read ();

        // Padding
        input.skipNBytes (5);

        this.filter1Type = input.read ();
        this.filter1Freq = input.read ();
        this.filter1Res = input.read ();
        this.filter1EnvelopeTime = input.read ();
        final int filEnvAmount = input.read ();
        this.filter1EnvelopeAmount = filEnvAmount < 0 ? 0 : (byte) filEnvAmount;
        // Velocity to envelope time and velocity to envelope amount, not supported
        input.skipNBytes (2);
        this.filter1VelocityToFrequency = input.read ();

        this.filter2Type = input.read ();
        this.filter2Freq = input.read ();
        this.filter2Res = input.read ();
        // Filter envelope: time, amount, velocity to time and velocity to amount
        input.skipNBytes (4);
        this.filter2VelocityToFrequency = input.read ();

        // Padding
        input.read ();

        // The LFO was added with MPC1000 OS 2.0 in previously unused bytes, therefore files of
        // older versions contain 0 which turns the LFO off
        this.lfoRate = StreamUtils.readUnsigned16 (input, false);
        this.lfoDelay = StreamUtils.readUnsigned16 (input, false);
        this.lfoWaveform = input.read ();
        this.lfoPitch = input.read ();
        this.lfoFilter1 = input.read ();
        this.lfoFilter2 = input.read ();
        this.lfoLevel = input.read ();

        // Padding, the last byte is unknown
        input.skipNBytes (4);

        this.mixerLevel = input.read ();
        this.mixerPan = input.read ();
        this.output = input.read ();
        this.fxSend = input.read ();
        this.fxSendLevel = input.read ();
        this.filterAttenuation = input.read ();

        // 2 unused bytes, 4 mute target pads (0x97 to 0x9A) and 9 unused bytes
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
     * Get the mute group. All sounding notes of the same group are stopped when a note of that
     * group is started.
     *
     * @return The mute group: 0 = Off, 1 to 32
     */
    public int getMuteGroup ()
    {
        return this.muteGroup;
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
     * Get the time of the filter 1 envelope. It is the time from the start value (the cutoff
     * frequency plus the envelope amount) back to the cutoff frequency.
     *
     * @return The time in the range of [0..100]
     */
    public int getFilter1EnvelopeTime ()
    {
        return this.filter1EnvelopeTime;
    }


    /**
     * Get the amount of the filter 1 envelope.
     *
     * @return The amount in the range of [-50..50]
     */
    public int getFilter1EnvelopeAmount ()
    {
        return this.filter1EnvelopeAmount;
    }


    /**
     * Get the LFO rate.
     *
     * @return The length of one cycle in ticks (96 ticks = 1 beat), 0 if the LFO is not used
     */
    public int getLfoRate ()
    {
        return this.lfoRate;
    }


    /**
     * Get the LFO delay.
     *
     * @return The time before the LFO starts in ticks (96 ticks = 1 beat)
     */
    public int getLfoDelay ()
    {
        return this.lfoDelay;
    }


    /**
     * Get the LFO waveform.
     *
     * @return 0=Triangle, 1=Sine, 2=Square, 3=Saw, 4=Saw Down, 5=Random
     */
    public int getLfoWaveform ()
    {
        return this.lfoWaveform;
    }


    /**
     * Get the LFO pitch amount.
     *
     * @return The amount in the range of [0..100]
     */
    public int getLfoPitch ()
    {
        return this.lfoPitch;
    }


    /**
     * Get the LFO amount for the cutoff of filter 1.
     *
     * @return The amount in the range of [0..100]
     */
    public int getLfoFilter1 ()
    {
        return this.lfoFilter1;
    }


    /**
     * Get the LFO level amount.
     *
     * @return The amount in the range of [0..100]
     */
    public int getLfoLevel ()
    {
        return this.lfoLevel;
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
