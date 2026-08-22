// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s900;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A S900/S950 program key-group. A key-group has 2 samples which are split by a velocity value and
 * can be cross-faded.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS900Keygroup
{
    /** Bit 0: Transpose: 0 = enable, 1 = disable. */
    public static final int        FLAG_TRANSPOSE          = 1;
    /** Bit 1: Velocity crossfade: 0 = enable, 1 = disable. */
    public static final int        FLAG_CROSSFADE          = 2;
    /** Bit 2: Vibrato desync: 0 = off, 1 = on. */
    public static final int        FLAG_VIBRATO_DESYNC     = 4;
    /** Bit 3: One shot trigger mode: 0 = off, 1 = on. */
    public static final int        FLAG_ONE_SHOT           = 8;
    /** Bit 4: Velocity release from note-off or note-on: 0 = Note-off, 1 = Note-on. */
    public static final int        FLAG_VELOCITY_RELEASE   = 16;
    /** Bit 5: Velocity crossfade curve modification, 0 = disable, 1 = enable. */
    public static final int        FLAG_VELOCITY_CROSSFADE = 32;

    private final int              keyHigh;
    private final int              keyLow;
    private final int              velocitySwitchValue;
    private final int              flags;
    private final int              release;
    private final int              sustain;
    private final int              decay;
    private final int              attack;
    private final int              filterVelocityInteraction;

    private final int              filterkeyTracking;
    private final int              attackVelocityInteraction;
    private final int              velocityReleaseInteraction;
    private final int              loudnessVelocityInteraction;
    private final int              pitchWarpVelocityInteraction;
    private final int              pitchWarpInitialOffset;
    private final int              pitchWarpRecoveryTime;
    private final int              lfoBuildUpTime;
    private final int              lfoVibratoRate;
    private final int              lfoVibratoDepth;
    private final int              outputAssign;
    private final int              midiChannelOffset;
    private final int              aftertouchDepthModulation;
    private final int              modulationWheelLfoDepthModulation;
    private final int              envelopeFilterFrequencyModulation;

    private final KeygroupLayer [] layers                  = new KeygroupLayer [2];


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public AkaiS900Keygroup (final InputStream input) throws IOException
    {
        this.keyHigh = StreamUtils.readUnsigned8 (input);
        this.keyLow = StreamUtils.readUnsigned8 (input);
        this.velocitySwitchValue = StreamUtils.readUnsigned8 (input);

        this.attack = StreamUtils.readUnsigned8 (input);
        this.decay = StreamUtils.readUnsigned8 (input);
        this.sustain = StreamUtils.readUnsigned8 (input);
        this.release = StreamUtils.readUnsigned8 (input);

        this.filterVelocityInteraction = StreamUtils.readUnsigned8 (input);
        this.filterkeyTracking = StreamUtils.readUnsigned8 (input);

        this.attackVelocityInteraction = StreamUtils.readUnsigned8 (input);
        this.velocityReleaseInteraction = StreamUtils.readSigned8 (input);
        this.loudnessVelocityInteraction = StreamUtils.readUnsigned8 (input);

        this.pitchWarpVelocityInteraction = StreamUtils.readUnsigned8 (input);
        this.pitchWarpInitialOffset = StreamUtils.readSigned8 (input);
        this.pitchWarpRecoveryTime = StreamUtils.readUnsigned8 (input);

        this.lfoBuildUpTime = StreamUtils.readUnsigned8 (input);
        this.lfoVibratoRate = StreamUtils.readUnsigned8 (input);
        this.lfoVibratoDepth = StreamUtils.readUnsigned8 (input);

        this.flags = StreamUtils.readUnsigned8 (input);

        this.outputAssign = StreamUtils.readUnsigned8 (input);
        this.midiChannelOffset = StreamUtils.readUnsigned8 (input);

        this.aftertouchDepthModulation = StreamUtils.readUnsigned8 (input);
        this.modulationWheelLfoDepthModulation = StreamUtils.readUnsigned8 (input);
        this.envelopeFilterFrequencyModulation = StreamUtils.readSigned8 (input);

        this.layers[0] = new KeygroupLayer (input);
        this.layers[1] = new KeygroupLayer (input);

        // Address of next key-group (updated by sampler)
        input.skipNBytes (2);
    }


    /**
     * Get the 2 velocity layers. Index 0 contains the soft velocity layer and index 1 the loud one.
     *
     * @return The 2 velocity layers
     */
    public KeygroupLayer [] getVelocityLayers ()
    {
        return this.layers;
    }


    /**
     * Get the upper key-range note.
     *
     * @return The note, 24-127
     */
    public int getKeyHigh ()
    {
        return this.keyHigh;
    }


    /**
     * Get the lower key-range note.
     *
     * @return The note, 24-127
     */
    public int getKeyLow ()
    {
        return this.keyLow;
    }


    /**
     * Get the value which switches between the soft and the loud sample.
     *
     * @return The velocity switch value [0..128], 0 = Only the loud sample will play, 128 = Only
     *         the soft sample will play
     */
    public int getVelocitySwitchValue ()
    {
        return this.velocitySwitchValue;
    }


    /**
     * Get the flags.
     *
     * @return 0x01 constant pitch enable, 0x02 velocity cross-fade enable, 0x08 one-shot trigger
     *         mode enable
     */
    public int getFlags ()
    {
        return this.flags;
    }


    /**
     * Get the attack value.
     *
     * @return Attack in the range of [0..99], default 0
     */
    public int getAttack ()
    {
        return this.attack;
    }


    /**
     * Get the decay value.
     *
     * @return Decay in the range of [0..99], default 80
     */
    public int getDecay ()
    {
        return this.decay;
    }


    /**
     * Get the sustain value. 0.375dB per Step, 0=-96dB,
     *
     * @return Sustain in the range of [0..99], default 99
     */
    public int getSustain ()
    {
        return this.sustain;
    }


    /**
     * Get the release value.
     *
     * @return Release in the range of [0..99], default 30
     */
    public int getRelease ()
    {
        return this.release;
    }


    /**
     * Get the Filter Velocity Interaction.
     *
     * @return The filter velocity interaction in the range of [0..99], default 10
     */
    public int getFilterVelocityInteraction ()
    {
        return this.filterVelocityInteraction;
    }


    /**
     * Get the Filter key-tracking.
     *
     * @return the filter key-tracking in the range of [0..99], default 50, 50 gives 1 Octave/Octave
     */
    public int getFilterkeyTracking ()
    {
        return this.filterkeyTracking;
    }


    /**
     * Get the Attack-velocity interaction.
     *
     * @return The attack Velocity Interaction in the range of [0..99], default 0
     */
    public int getAttackVelocityInteraction ()
    {
        return this.attackVelocityInteraction;
    }


    /**
     * Get the Velocity release interaction. If positive, greater note-off velocity gives faster
     * release.
     *
     * @return The velocity release interaction in the range of [-50--50], default 0.
     */
    public int getVelocityReleaseInteraction ()
    {
        return this.velocityReleaseInteraction;
    }


    /**
     * Get the loudness-velocity interaction.
     *
     * @return The loudness velocity interaction in the range of [0..99], default 30. 0=No dynamics
     */
    public int getLoudnessVelocityInteraction ()
    {
        return this.loudnessVelocityInteraction;
    }


    /**
     * Get the pitch warp-velocity interaction.
     *
     * @return Get the pitch warp velocity interaction in the range of [0..99], default 0
     */
    public int getPitchWarpVelocityInteraction ()
    {
        return this.pitchWarpVelocityInteraction;
    }


    /**
     * Get the pitch warp initial offset.
     *
     * @return The pitch warp initial offset in the range of [-50..50], default 0
     */
    public int getPitchWarpInitialOffset ()
    {
        return this.pitchWarpInitialOffset;
    }


    /**
     * Get the pitch warp recovery time.
     *
     * @return The pitch warp recovery time in the range of [0..99], default 99. 99 is the slowest
     */
    public int getPitchWarpRecoveryTime ()
    {
        return this.pitchWarpRecoveryTime;
    }


    /**
     * Get the LFO build-up time (delay) for vibrato (pitch modulation).
     *
     * @return The LFO build up time in the range of [0..99], default 64
     */
    public int getLfoBuildUpTime ()
    {
        return this.lfoBuildUpTime;
    }


    /**
     * Get the LFO rate for vibrato.
     *
     * @return The LFO vibrato rate in the range of [0..99], default 42
     */
    public int getLfoVibratoRate ()
    {
        return this.lfoVibratoRate;
    }


    /**
     * Get the LFO depth for vibrato.
     *
     * @return The LFO vibrato depth in the range of [0..99], default 0
     */
    public int getLfoVibratoDepth ()
    {
        return this.lfoVibratoDepth;
    }


    /**
     * Get the output assignment.
     *
     * @return The output assignment
     */
    public int getOutputAssign ()
    {
        return this.outputAssign;
    }


    /**
     * Get the after-touch depth modulation.
     *
     * @return The after-touch depth modulation in the range of [0..99], default 0
     */
    public int getAftertouchDepthModulation ()
    {
        return this.aftertouchDepthModulation;
    }


    /**
     * Get the modulation wheel LFO depth modulation.
     *
     * @return The modulation wheel LFO depth modulation in the range of [0..99}], default 50. +-3
     *         semi-tones
     */
    public int getModulationWheelLfoDepthModulation ()
    {
        return this.modulationWheelLfoDepthModulation;
    }


    /**
     * Get the amount of ADSR envelope applied to VCF filter frequency.
     *
     * @return The envelope filter frequency modulation in the range of [-50..50], default 0
     */
    public int getEnvelopeFilterFrequencyModulation ()
    {
        return this.envelopeFilterFrequencyModulation;
    }


    /**
     * Get the MIDI channel offset. This will be added to the Basic MIDI Channel in the overall
     * settings.
     *
     * @return The MIDI channel offset
     */
    public int getMidiChannelOffset ()
    {
        return this.midiChannelOffset;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Flags:\n");
        sb.append ("  Transpose         : ").append ((this.flags & FLAG_TRANSPOSE) > 0 ? "disable" : "enable").append ('\n');
        sb.append ("  Crossfade         : ").append ((this.flags & FLAG_CROSSFADE) > 0 ? "disable" : "enable").append ('\n');
        sb.append ("  Vibrato desync    : ").append ((this.flags & FLAG_VIBRATO_DESYNC) > 0 ? "on" : "off").append ('\n');
        sb.append ("  One-shot          : ").append ((this.flags & FLAG_ONE_SHOT) > 0 ? "on" : "off").append ('\n');
        sb.append ("  Velocity Release  : ").append ((this.flags & FLAG_VELOCITY_RELEASE) > 0 ? "Note-on" : "Note-off").append ('\n');
        sb.append ("  Velocity Crossfade: ").append ((this.flags & FLAG_VELOCITY_CROSSFADE) > 0 ? "Enable" : "Disable").append ('\n');
        sb.append ("Layers:\n");
        sb.append ("  Soft: ").append (this.layers[0].sample).append ("\n");
        sb.append ("  Hard: ").append (this.layers[1].sample).append ("\n");

        return sb.toString ();
    }


    /**
     * Helper class for the 2 velocity layers.
     */
    public class KeygroupLayer
    {
        private final String sample;
        private final int    filterAttack;
        private final int    filterDecay;
        private final int    filterSustain;
        private final int    filterRelease;
        private final int    sampleHeaderAddress;
        private final int    tuning;
        private final int    filter;
        private final int    loudnessOffset;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public KeygroupLayer (final InputStream input) throws IOException
        {
            this.sample = StreamUtils.readAscii (input, 10).trim ();

            this.filterAttack = StreamUtils.readUnsigned8 (input);
            this.filterDecay = StreamUtils.readUnsigned8 (input);
            this.filterSustain = StreamUtils.readUnsigned8 (input);
            this.filterRelease = StreamUtils.readUnsigned8 (input);

            // Velocity value at which loud-soft mixture is 50% in velocity crossfade type sample.
            // Will be ignored if Bit 5 in flags is 0, 0..127, default 64
            StreamUtils.readUnsigned8 (input);

            // Undefined
            input.skipNBytes (1);

            this.sampleHeaderAddress = StreamUtils.readUnsigned16 (input, false);
            this.tuning = StreamUtils.readUnsigned16 (input, false);
            this.filter = StreamUtils.readUnsigned8 (input);
            this.loudnessOffset = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the sample name.
         *
         * @return The sample name
         */
        public String getSample ()
        {
            return this.sample;
        }


        /**
         * Address of sample header for the sample (updated by sampler). Note: the difference to the
         * next value is 0x46 which is the size of a key-group and therefore this points more likely
         * to the key-group itself or the sample-name in the key-group
         *
         * @return The address
         */
        public int getSampleHeaderAddress ()
        {
            return this.sampleHeaderAddress;
        }


        /**
         * Get the tuning offset (transpose) for the sample.
         *
         * @return The tuning in 1/16 semi-tones (signed) in the range of [-50..50]
         */
        public int getTuning ()
        {
            return this.tuning;
        }


        /**
         * Get the Filter for the sample. Units of 0.375dB
         *
         * @return The cutoff in the range of [0..99], default 99
         */
        public int getFilter ()
        {
            return this.filter;
        }


        /**
         * Get the filter attack value.
         *
         * @return Attack in the range of [0..99], default 20
         */
        public int getFilterAttack ()
        {
            return this.filterAttack;
        }


        /**
         * Get the filter decay value.
         *
         * @return Decay in the range of [0..99], default 20
         */
        public int getFilterDecay ()
        {
            return this.filterDecay;
        }


        /**
         * Get the filter sustain value.
         *
         * @return Sustain in the range of [0..99], default 20
         */
        public int getFilterSustain ()
        {
            return this.filterSustain;
        }


        /**
         * Get the filter release value.
         *
         * @return Release in the range of [0..99], default 20
         */
        public int getFilterRelease ()
        {
            return this.filterRelease;
        }


        /**
         * Loudness offset (signed) for the sample.
         *
         * @return The value in the range of [-50..50]
         */
        public int getLoudnessOffset ()
        {
            return this.loudnessOffset;
        }
    }
}
