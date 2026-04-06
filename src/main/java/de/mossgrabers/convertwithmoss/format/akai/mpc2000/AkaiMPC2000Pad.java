// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * An Akai MPC2000 pad. One pad can have assigned up to 4 samples.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC2000Pad
{
    private final boolean isMPC3000;

    private final int     sampleNumber;
    private final int     mode;
    private final int     velSw1;
    private final int     velNote1;
    private final int     velSw2;
    private final int     velNote2;
    @SuppressWarnings("unused")
    private final int     voiceOverlap;
    @SuppressWarnings("unused")
    private final int     polyNote1;
    @SuppressWarnings("unused")
    private final int     polyNote2;
    private final int     tune;
    private final int     attack;
    private final int     decay;
    private final int     decayMode;
    private final int     filterFreq;
    private final int     filterRes;
    private final int     filterAttack;
    private final int     filterDecay;
    private final int     filterAmount;
    private final int     veloAttackLevel;
    @SuppressWarnings("unused")
    private final int     veloAttackAttack;
    @SuppressWarnings("unused")
    private final int     veloAttackStart;
    private final int     veloFilterFreq;
    @SuppressWarnings("unused")
    private final int     sliderData;
    @SuppressWarnings("unused")
    private final int     veloTunePitch;

    @SuppressWarnings("unused")
    private int           fxLevel;
    @SuppressWarnings("unused")
    private int           fxSend;
    private int           stereoLevel;
    private int           pan;
    private int           soloLevel;
    @SuppressWarnings("unused")
    private int           soloOutput;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @param isMPC3000 True if it a MPC3000 file
     * @throws IOException Could not read
     */
    public AkaiMPC2000Pad (final InputStream input, final boolean isMPC3000) throws IOException
    {
        this.isMPC3000 = isMPC3000;

        // 25 Bytes in total
        this.sampleNumber = input.read ();
        this.mode = input.read ();
        this.velSw1 = input.read ();
        this.velNote1 = input.read ();
        this.velSw2 = input.read ();
        this.velNote2 = input.read ();
        this.voiceOverlap = input.read ();
        this.polyNote1 = input.read ();
        this.polyNote2 = input.read ();
        this.tune = StreamUtils.readUnsigned16 (input, false);
        this.attack = input.read ();
        this.decay = input.read ();
        this.decayMode = input.read ();
        this.filterFreq = input.read ();
        this.filterRes = input.read ();
        this.filterAttack = input.read ();
        this.filterDecay = input.read ();
        this.filterAmount = input.read ();
        this.veloAttackLevel = input.read ();
        this.veloAttackAttack = input.read ();
        this.veloAttackStart = input.read ();
        this.veloFilterFreq = input.read ();
        this.sliderData = input.read ();
        this.veloTunePitch = isMPC3000 ? 0 : input.read ();
    }


    /**
     * Read the additional mixer parameters.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public void readMixer (final InputStream input) throws IOException
    {
        if (this.isMPC3000)
        {
            this.stereoLevel = input.read ();
            this.pan = input.read ();
            this.fxSend = input.read ();
            this.fxLevel = input.read ();
        }
        else
        {
            this.fxLevel = input.read ();
            this.fxSend = input.read ();
            this.stereoLevel = input.read ();
            this.pan = input.read ();
            this.soloLevel = input.read ();
            this.soloOutput = input.read ();
        }
    }


    /**
     * Get the sample number.
     *
     * @return The sample number (index into the sample name list)
     */
    public int getSampleNumber ()
    {
        return this.sampleNumber;
    }


    /**
     * Get the tuning.
     *
     * @return Tuning in cents (1 semitone = 100 cents) in the range of [-3600..3600]
     */
    public double getTuning ()
    {
        return this.tune;
    }


    /**
     * Get the mixer level.
     *
     * @return The level in the range of [0..100]
     */
    public int getMixerLevel ()
    {
        return this.stereoLevel;
    }


    /**
     * Get the level.
     *
     * @return The level in the range of [0..100]
     */
    public int getLevel ()
    {
        return this.soloLevel;
    }


    /**
     * Get the mixer panning.
     *
     * @return The panning: 0 to 49=Left, 50=Center, 51 to 100=Right
     */
    public int getMixerPan ()
    {
        return this.pan;
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
    public double getVelocityToLevel ()
    {
        return this.veloAttackLevel;
    }


    /**
     * Get the frequency of the filter.
     *
     * @return The frequency in the range of [0..100]
     */
    public int getFilterFreq ()
    {
        return this.filterFreq;
    }


    /**
     * Get the resonance of the filter.
     *
     * @return The resonance in the range of [0..100]
     */
    public int getFilterRes ()
    {
        return this.filterRes;
    }


    /**
     * Get the filter envelope attack.
     *
     * @return The attack in the range of [0..100]
     */
    public int getFilterAttack ()
    {
        return this.filterAttack;
    }


    /**
     * Get the filter envelope decay.
     *
     * @return The decay in the range of [0..100]
     */
    public int getFilterDecay ()
    {
        return this.filterDecay;
    }


    /**
     * Get the filter envelope amount.
     *
     * @return The amount in the range of [0..100]
     */
    public int getFilterAmount ()
    {
        return this.filterAmount;
    }


    /**
     * Get the frequency of the filter modulation by velocity.
     *
     * @return The intensity in the range of [0..100]
     */
    public double getFilterVelocityToFrequency ()
    {
        return this.veloFilterFreq;
    }


    /**
     * Get the pad play mode. Up to 3 samples can be played. NORMAL: Only 1 sample plays
     * (sampleNumber), SIMULT: Up to 3 samples play together (samples 2+3 are in velocityNote1 and
     * velocityNote2), VEL SW: 3 velocity layers (notes as in SIMULT and velSw1/2 define the
     * velocity splits), DCY SW: split is in the release phase (e.g. for HiHats)
     *
     * @return 0=NORMAL, 1=SIMULT, 2=VEL SW, 3=DCY SW
     */
    public int getVelocitySwitchMode ()
    {
        return this.mode;
    }


    /**
     * Get the lower velocity value.
     *
     * @return The lower velocity value, in the range of [0..velocityRangeUpper]
     */
    public int getVelocitySwitch1 ()
    {
        return this.velSw1;
    }


    /**
     * Get the play-note of sample 2.
     *
     * @return The 2nd sample to play
     */
    public int getVelocityNote1 ()
    {
        return this.velNote1;
    }


    /**
     * Get the upper velocity value.
     *
     * @return The upper velocity value, in the range of [velocityRangeLower..127]
     */
    public int getVelocitySwitch2 ()
    {
        return this.velSw2;
    }


    /**
     * Get the play-note of sample 3.
     *
     * @return The 3rd sample to play
     */
    public int getVelocityNote2 ()
    {
        return this.velNote2;
    }
}
