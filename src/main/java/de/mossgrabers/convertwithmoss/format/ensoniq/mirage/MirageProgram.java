// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.mirage;

import java.io.IOException;
import java.io.InputStream;


/**
 * The parameters of one program.
 *
 * @author Jürgen Moßgraber
 */
public class MirageProgram
{
    /**
     * Parameter #28. Each Mirage key is assigned two oscillators. When this switching parameter is
     * OFF, the two oscillators will use the same wavesample. When this switch is ON, the
     * oscillators will use consecutive wavesamples - one to each oscillator. For the MIX MODE to
     * work, odd numbered wavesamples (1, 3, 5, 7) should be from a different sound source than the
     * even numbered wavesamples. The balance between the two oscillators can be controlled by
     * parameters 34 and 35. One use of the MIX MODE is to have a soft sound on the odd wavesamples
     * and a hard sound on the even wavesamples. By using key velocity to control the balance (PARAM
     * 35), a soft sound will be heard when keys are depressed slowly and a hard sound will be heard
     * when keys are depressed firmly. Range: 0..1.
     */
    public int mixModeSwitch;

    /**
     * Parameter #29. A switching parameter to choose between polyphonic keyboard and mono-phonic
     * keyboard operation. With the switch OFF, the Mirage will play in its usual eight-voice
     * polyphonic splendor. With the switch ON, one voice will be assigned to each keyboard half. If
     * a key is played while another is still down, the voice will be "stolen" from the first key,
     * but the envelope will not be re- triggered. Range: 0..1
     */
    public int monoModeSwitch;

    /**
     * Parameter #31. The LOW FREQUENCY OSCILLATOR SPEED parameter will change the frequency of the
     * LFO. It is adjustable from .5 Hz (VALUE 0) to 40 Hz (VALUE 99). The LFO affects pitch only.
     * Range: 0..99.
     */
    public int lfoFrequency;

    /**
     * Parameter #32: The LOW FREQUENCY OSCILLATOR DEPTH parameter controls the amplitude of the
     * LFO. Higher values will result in greater LFO depth (more vibrato). A value of 0 will assign
     * the LFO depth to the Mod Wheel. Range: 0..99.
     */
    public int lfoDepth;

    /**
     * Parameter #33. The DIGITAL OSCILLATOR DETUNE parameter controls the frequency difference
     * between two oscillators used on the same key. It will cause the second oscillator to play
     * sharp in small increments. To hear the effect as chorusing, set parameter 34 to a middle
     * value, such as 32. Range: 0..99.
     */
    public int oscDetune;

    /**
     * Parameter #34. The DIGITAL OSCILLATOR BALANCE controls the relative volume level of the two
     * oscillators in the MIX MODE (parameter 28) or for chorusing. A value of 0 results in only
     * Oscillator 1 playing the sound; with a value of 63 only Oscillator 2 will play. A value of 32
     * results in an even mix of the two oscillators. Range: 0..252
     */
    public int oscMix;

    /**
     * Parameter #35. The VELOCITY SENSITIVE BALANCE parameter makes the digital oscillator balance
     * dependent on key velocity. A soft touch will yield the mix set by parameter 34, and a hard
     * touch will favor oscillator 2. The D.O. BALANCE (parameter 34) will set the initial balance
     * for a soft touch. Parameter 35 controls how much the balance changes with velocity, with one
     * exception. When the value is '0' the balance between oscillators is controlled by the
     * MODULATION WHEEL instead. Since each oscillator can play a different wave-sample, it is
     * possible to mix between two totally different sounds. Range: 0..124
     */
    public int mixVelocitySensitivity;

    /**
     * Parameter #36. The FILTER CUTOFF FREQUENCY parameter adjusts the initial cutoff frequency of
     * the low pass filter, The filter is adjustable from 50 Hz (VALUE 0) TO 15KHz (VALUE 99) and
     * features a 24 db/octave roll-off slope. The effects of the filter envelope, and the filter
     * keyboard tracking are added to the level set here. Range: 0..198
     */
    public int filterCutoffFreq;

    /**
     * Parameter #37. The FILTER RESONANCE (Q) controls the amplitude of the resonant peak of the
     * filter. It is adjustable from no peak (VALUE 0) to just below oscillation (VALUE 40). Range:
     * 0..198.
     */
    public int resonance;

    /**
     * Parameter #38. The FILTER KEYBOARD TRACKING parameter allows the note played on the keyboard
     * to determine the cutoff frequency of the filter - lower notes will have a lower cutoff,
     * higher notes a higher cutoff. It is adjustable from no tracking (VALUE 0) to one octave in
     * filter cutoff per octave on keyboard (VALUE 4). Range: 0..4.
     */
    public int filterKybdTracking;

    /**
     * Parameter #27. This parameter will select the first wave-sample number used by the lowest key
     * of a keyboard half. Except in certain advanced sampling situations, this parameter is usually
     * set to 0. It can be used to make each program of a keyboard half play an entirely different
     * sound Range: 0..7.
     */
    public int initialWavesample;

    /**
     * Parameter #40. The FILTER ATTACK parameter controls the rate at which the filter frequency
     * will increase from the initial CUTOFF FREQUENCY (parameter 36) to the peak frequency. It is
     * adjustable from instantaneous (VALUE 0) to 30 seconds (VALUE 31). Note that the peak level
     * has no effect on the rate of the attack. Range: 0..31.
     */
    public int filterEnvelopeAttack;

    /**
     * Parameter #41. Controls the maximum cutoff frequency the filter will reach at the top of the
     * attack slope. It is adjustable from zero (VALUE 0) to a maximum of 15KHz (VALUE 31). Note
     * that the peak level has no effect on the rate of the attack. Range: 0..31.
     */
    public int filterEnvelopePeak;

    /**
     * Parameter #42. Controls the rate at which the filter cutoff frequency will descend from the
     * PEAK value to the SUSTAIN value. At VALUE 31, it will take 30 seconds to reach the sustain
     * level. At VALUE 0, it will drop instantly to the SUSTAIN level. Range: 0..31.
     */
    public int filterEnvelopeDecay;

    /**
     * Parameter #43. Controls the cutoff frequency that the filter will hold as long as the key is
     * depressed. At VALUE 0 it will be the same as the initial cutoff frequency (parameter 36). At
     * VALUE 31 it will be maximum. Range: 0..31.
     */
    public int filterEnvelopeSustain;

    /**
     * Parameter #44. Controls the rate at which the filter frequency will descend from the sustain
     * value to the initial cutoff frequency (parameter 36) after the key is released. At VALUE 31
     * it will take 30 seconds to reach the minimum level and at VALUE 0 it will release instantly.
     * Range: 0..31.
     */
    public int filterEnvelopeRelease;

    /**
     * Parameter #45. The VELOCITYSENSITIVEFILTERATTACK parameter makes the filter attack rate
     * dependent on key velocity. At VALUE 0, key velocity will not effect the attack rate.
     * Increasing the value will make the attack rate more sensitive to keyboard velocity (increased
     * key velocity will speed up the attack rate). Range: 0..124
     */
    public int filterEnvelopeAttackVelocity;

    /**
     * Parameter #46. The VELOCITY SENSISTIVE FILTER PEAK parameter makes the maximum filter
     * frequency peak level dependent on the key velocity. At value 0, key velocity will not affect
     * the peak. Increasing the value will make the peak level more sensitive to key velocity
     * (increased key velocity will raise the peak frequency). Range: 0..124.
     */
    public int filterEnvelopePeakVelocity;

    /**
     * Parameter #47. A parameter that makes the decay rate of the filter envelope dependent on the
     * location of the key. This permits the filter to have a longer decay rate on lower notes than
     * on higher notes. At VALUE 0, it will have no effect on the decay setting. Increasing the
     * value number will shorten the decay time of higher notes. Range: 0..124
     */
    public int filterEnvelopeDecayVelocity;

    /**
     * Parameter #48. The VELOCITY SENSITIVE FILTER SUSTAIN parameter makes the sustain level
     * dependent on key velocity. At value 0, key velocity will not effect the sustain frequency.
     * Increasing the value will make the filter sustain level more sensitive to key velocity
     * (increased key velocity will raise the sustain frequency). Range: 0..124
     */
    public int filterEnvelopeSustainVelocity;

    /**
     * Parameter #49. The VELOCITY SENSITIVE FILTER RELEASE parameter makes the release rate of the
     * filter frequency dependent on key-up velocity (how fast you let up the key). Increasing the
     * value will make the release rate more sensitive to key-up velocity (increased key-up velocity
     * will shorten the release rate.). Range: 0..124
     */
    public int filterEnvelopeReleaseVelocity;

    /**
     * Parameter #50. Controls the rate at which the volume will rise to the peak when a key is
     * played. At VALUE 0 it is instant and at VALUE 31 the amplitude will take 30 seconds to reach
     * the peak. Range: 0..31.
     */
    public int ampEnvelopeAttack;

    /**
     * Parameter #51. Controls the maximum amplitude that will be reached at the top of the attack
     * slope. It is adjustable from no sound at VALUE 0 to maximum output at VALUE 31. Range: 0..31.
     */
    public int ampEnvelopePeak;

    /**
     * Parameter #52. Controls the rate at which the amplitude descends from the peak level to the
     * sustain level. At VALUE 31 it will take 30 seconds for the amplitude to fall to the sustain
     * level. At VALUE 0 it will drop instantly to the SUSTAIN level. Range: 0..31.
     */
    public int ampEnvelopeDecay;

    /**
     * Parameter #53. Sets the amplitude level that the note will play at between the end of the
     * decay segment and the time you release the key. At VALUE 0 there will be no output after the
     * decay and at VALUE 31 the sustain will be at the maximum level. Range: 0..31.
     */
    public int ampEnvelopeSustain;

    /**
     * Parameter #54. Controls the rate at which the amplitude descends to 0 after the key is
     * released. At VALUE 31 it will take 30 seconds to reach 0 and at VALUE 0 the amplitude will
     * fall instantly to 0. Range: 0..31.
     */
    public int ampEnvelopeRelease;

    /**
     * Parameter #55. Makes the ATTACK RATE dependent on key velocity. At VALUE 0 key velocity will
     * not effect the attack rate. Increasing the value will make the attack rate more sensitive to
     * keyboard velocity (increased key velocity will shorten the attack rate). Range: 0..124.
     */
    public int ampEnvelopeAttackVelocity;

    /**
     * Parameter #56. The VELOCITY SENSITIVE PEAK LEVEL parameter makes the maximum peak level
     * dependent on key velocity. At VALUE 0, key velocity will not effect the peak level.
     * Increasing the value will make the peak level more sensitive to key velocity (increased
     * velocity will raise the peak level). Range: 0..124.
     */
    public int ampEnvelopePeakVelocity;

    /**
     * Parameter #57. A parameter that makes the decay rate of the amplitude envelope dependent on
     * the location of the key. This permits the note to have a longer decay on lower notes than on
     * higher notes. At VALUE 0 it will have no effect on the decay setting. Increasing the value
     * will shorten the decay time of higher notes. Range: 0..124.
     */
    public int ampEnvelopeDecayVelocity;

    /**
     * Parameter #58. The VELOCITY SENSITIVE SUSTAIN LEVEL parameter makes the sustain level
     * dependent on key velocity. At VALUE 0, key velocity will have no effect on sustain level.
     * Increasing the value will make the sustain level more sensitive to key velocity (increased
     * velocity will raise the sustain level). Range: 0..124.
     */
    public int ampEnvelopeSustainVelocity;

    /**
     * Parameter #59. The VELOCITY SENSITIVE RELEASE RATE parameter makes the release rate dependent
     * on release velocity. At VALUE 0 velocity will have no effect on release rate. Increasing the
     * value will make the release rate more sensitive to key-up velocity (increased key-up velocity
     * will shorten the release rate). Range: 0..124.
     */
    public int ampEnvelopeReleaseVelocity;


    /**
     * Constructor.
     * 
     * @param input The input from which to read the program parameters
     * @throws IOException Could not read the program parameters
     */
    public MirageProgram (final InputStream input) throws IOException
    {
        this.monoModeSwitch = input.read ();
        this.lfoFrequency = input.read ();
        this.lfoDepth = input.read ();
        this.oscDetune = input.read ();
        this.oscMix = input.read ();
        this.mixVelocitySensitivity = input.read ();
        this.filterCutoffFreq = input.read ();
        this.resonance = input.read ();
        this.filterKybdTracking = input.read ();

        // Reserved
        input.skipNBytes (1);

        this.initialWavesample = input.read ();
        this.mixModeSwitch = input.read ();
        this.filterEnvelopeAttack = input.read ();
        this.filterEnvelopePeak = input.read ();
        this.filterEnvelopeDecay = input.read ();
        this.filterEnvelopeSustain = input.read ();
        this.filterEnvelopeRelease = input.read ();
        this.filterEnvelopeAttackVelocity = input.read ();
        this.filterEnvelopePeakVelocity = input.read ();
        this.filterEnvelopeDecayVelocity = input.read ();
        this.filterEnvelopeSustainVelocity = input.read ();
        this.filterEnvelopeReleaseVelocity = input.read ();
        this.ampEnvelopeAttack = input.read ();
        this.ampEnvelopePeak = input.read ();
        this.ampEnvelopeDecay = input.read ();
        this.ampEnvelopeSustain = input.read ();
        this.ampEnvelopeRelease = input.read ();
        this.ampEnvelopeAttackVelocity = input.read ();
        this.ampEnvelopePeakVelocity = input.read ();
        this.ampEnvelopeDecayVelocity = input.read ();
        this.ampEnvelopeSustainVelocity = input.read ();
        this.ampEnvelopeReleaseVelocity = input.read ();

        // Reserved
        input.skipNBytes (4);
    }
}
