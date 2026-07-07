// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 partial parameter (128 bytes).
 *
 * @author Jürgen Moßgraber
 */
public class S770Partial
{
    private final String           partialName;
    private final int              outputAssign8;
    private final int              stereoMixLevel;
    private final int              partialLevel;
    private final int              outputAssign6;
    private final int              pan;
    private final int              coarseTune;
    private final int              fineTune;
    private final int              breathCtrl;
    private final TvfSection       tvf;
    private final TvaSection       tva;
    private final LfoSection       lfoGenerator;
    private final SampleSection [] samples = new SampleSection [4];


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public S770Partial (final InputStream input) throws IOException
    {
        this.partialName = StreamUtils.readAscii (input, 16);

        this.samples[0] = new SampleSection (input);
        input.skipNBytes (1);
        this.outputAssign8 = StreamUtils.readUnsigned8 (input);
        this.stereoMixLevel = StreamUtils.readUnsigned8 (input);
        this.partialLevel = StreamUtils.readUnsigned8 (input);
        this.outputAssign6 = StreamUtils.readUnsigned8 (input);

        this.samples[1] = new SampleSection (input);
        input.skipNBytes (1);
        this.pan = StreamUtils.readSigned8 (input);
        this.coarseTune = StreamUtils.readSigned8 (input);
        this.fineTune = StreamUtils.readSigned8 (input);
        this.breathCtrl = StreamUtils.readUnsigned8 (input);

        this.samples[2] = new SampleSection (input);
        input.skipNBytes (5);

        this.samples[3] = new SampleSection (input);
        this.tvf = new TvfSection (input);
        this.tva = new TvaSection (input);
        this.lfoGenerator = new LfoSection (input);
        input.skipNBytes (7);
    }


    /**
     * Get the name of the partial.
     *
     * @return The name
     */
    public String getPartialName ()
    {
        return this.partialName;
    }


    /**
     * Get the parameters of the 4 samples.
     *
     * @return The samples
     */
    public SampleSection [] getSamples ()
    {
        return this.samples;
    }


    /**
     * Get the 8-out output assignment.
     *
     * @return The assignments
     */
    public int getOutputAssign8 ()
    {
        return this.outputAssign8;
    }


    /**
     * Get the stereo mix level.
     *
     * @return The level
     */
    public int getStereoMixLevel ()
    {
        return this.stereoMixLevel;
    }


    /**
     * Get the partial level.
     *
     * @return The level
     */
    public int getPartialLevel ()
    {
        return this.partialLevel;
    }


    /**
     * Get the 6-out output assignment.
     *
     * @return The assignments
     */
    public int getOutputAssign6 ()
    {
        return this.outputAssign6;
    }


    /**
     * Get the panning for the STEREO mix.
     *
     * @return The panning, -32..-1: L32-L1, 0: Center, 1-32: R1..R32
     */
    public int getPan ()
    {
        return this.pan;
    }


    /**
     * Get the coarse tune.
     *
     * @return The tuning in the range of -48..48 semi-tones
     */
    public int getCoarseTune ()
    {
        return this.coarseTune;
    }


    /**
     * Get the fine tune.
     *
     * @return The tuning in the range of -50..50 cents
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Get the breath control.
     *
     * @return The breath control
     */
    public int getBreathCtrl ()
    {
        return this.breathCtrl;
    }


    /**
     * Get the TVF section.
     *
     * @return The TVF section
     */
    public TvfSection getTvf ()
    {
        return this.tvf;
    }


    /**
     * Get the TVA section.
     *
     * @return The TVA section
     */
    public TvaSection getTva ()
    {
        return this.tva;
    }


    /**
     * Get the LFO section.
     *
     * @return The LFO section
     */
    public LfoSection getLfoGenerator ()
    {
        return this.lfoGenerator;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770PartialParameter [\n" + "  partialName='" + this.partialName.trim () + "'\n" + "  sample1=" + this.samples[0] + "\n" + "  outputAssign8=" + this.outputAssign8 + "\n" + "  stereoMixLevel=" + this.stereoMixLevel + "\n" + "  partialLevel=" + this.partialLevel + "\n" + "  outputAssign6=" + this.outputAssign6 + "\n" + "  sample2=" + this.samples[1] + "\n" + "  pan=" + this.pan + "\n" + "  coarseTune=" + this.coarseTune + "\n" + "  fineTune=" + this.fineTune + "\n" + "  breathCtrl=" + this.breathCtrl + "\n" + "  sample3=" + this.samples[2] + "\n" + "  sample4=" + this.samples[3] + "\n" + "  tvf=" + this.tvf + "\n" + "  tva=" + this.tva + "\n" + "  lfoGenerator=" + this.lfoGenerator + "\n]";
    }


    /** Sample section (11 bytes). */
    public static class SampleSection
    {
        private final int sampleSelection;
        private final int pitchKf;
        private final int sampleLevel;
        private final int pan;
        private final int coarseTune;
        private final int fineTune;
        private final int smtVelocityLower;
        private final int smtFadeWidthLower;
        private final int smtVelocityUpper;
        private final int smtFadeWidthUpper;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public SampleSection (final InputStream input) throws IOException
        {
            this.sampleSelection = StreamUtils.readSigned16 (input, false);
            this.pitchKf = StreamUtils.readSigned8 (input);
            this.sampleLevel = StreamUtils.readUnsigned8 (input);
            this.pan = StreamUtils.readSigned8 (input);
            this.coarseTune = StreamUtils.readSigned8 (input);
            this.fineTune = StreamUtils.readSigned8 (input);
            this.smtVelocityLower = StreamUtils.readUnsigned8 (input);
            this.smtFadeWidthLower = StreamUtils.readUnsigned8 (input);
            this.smtVelocityUpper = StreamUtils.readUnsigned8 (input);
            this.smtFadeWidthUpper = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the selected sample.
         *
         * @return 0-511, -1: Off
         */
        public int getSampleSelection ()
        {
            return this.sampleSelection;
        }


        /**
         * Get the pitch key-follow. For example, if the ratio is 16/8, playing two keys a semi-tone
         * apart will result in two notes sounding a whole tone apart; playing two notes a minor
         * third (three semi-tones) apart will sound a diminished fifth (six semi-tones); and
         * playing two notes an octave apart will result in two notes two octaves apart. Going the
         * other way, if the ratio is 1/8, a semi-tone on the keyboard will result in a pitch change
         * of only 1/16th- tone, while a minor sixth (eight semi-tones) on the keyboard will sound a
         * semi-tone. A two-octave spread will sound a minor third. (You will notice that Norm is
         * equivalent to a ratio of "8/8".)
         *
         * @return The pitch key-follow in the range of -16..16 (where the value represents value/8)
         */
        public int getPitchKf ()
        {
            return this.pitchKf;
        }


        /**
         * Get the level.
         *
         * @return The level
         */
        public int getSampleLevel ()
        {
            return this.sampleLevel;
        }


        /**
         * Get the panning.
         *
         * @return The panning, -32..-1: L32-L1, 0: Center, 1-32: R1..R32, 33:Random, 34: Key+, 35:
         *         Key-
         */
        public int getPan ()
        {
            return this.pan;
        }


        /**
         * Get the coarse tune.
         *
         * @return The tuning in the range of -48..48 semi-tones
         */
        public int getCoarseTune ()
        {
            return this.coarseTune;
        }


        /**
         * Get the fine tune.
         *
         * @return The tuning in the range of -50..50 cents
         */
        public int getFineTune ()
        {
            return this.fineTune;
        }


        /**
         * Get the SMT velocity lower.
         *
         * @return The SMT velocity lower in the range of 0..127
         */
        public int getSmtVelocityLower ()
        {
            return this.smtVelocityLower;
        }


        /**
         * Get the SMT velocity lower fade.
         *
         * @return The SMT velocity lower fade in the range of 0..127
         */
        public int getSmtFadeWidthLower ()
        {
            return this.smtFadeWidthLower;
        }


        /**
         * Get the SMT velocity upper.
         *
         * @return The SMT velocity upper in the range of 0..127
         */
        public int getSmtVelocityUpper ()
        {
            return this.smtVelocityUpper;
        }


        /**
         * Get the SMT velocity upper fade.
         *
         * @return The SMT velocity upper fade in the range of 0..127
         */
        public int getSmtFadeWidthUpper ()
        {
            return this.smtFadeWidthUpper;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "SampleSection [sampleSelection=" + this.sampleSelection + ", pitchKf=" + this.pitchKf + ", sampleLevel=" + this.sampleLevel + ", pan=" + this.pan + ", coarseTune=" + this.coarseTune + ", fineTune=" + this.fineTune + ", smtVelocityLower=" + this.smtVelocityLower + ", smtFadeWithLower=" + this.smtFadeWidthLower + ", smtVelocityUpper=" + this.smtVelocityUpper + ", smtFadeWithUpper=" + this.smtFadeWidthUpper + "]";
        }
    }


    /** TVF section (21 bytes). */
    public static class TvfSection
    {
        private final int    filterMode;
        private final int    cutoff;
        private final int    resonance;
        private final int    velocityCurveType;
        private final int    velocityCurveRatio;
        private final int    timeVelocitySensitivity;
        private final int    cutoffVelocitySens;
        private final int [] levels;
        private final int [] times;
        private final int    envTvfDepth;
        private final int    envPitchDepth;
        private final int    tvfKfPoint;
        private final int    envTimeKf;
        private final int    envDepthKf;
        private final int    cutoffKf;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public TvfSection (final InputStream input) throws IOException
        {
            this.filterMode = StreamUtils.readUnsigned8 (input);
            this.cutoff = StreamUtils.readUnsigned8 (input);
            this.resonance = StreamUtils.readUnsigned8 (input);
            this.velocityCurveType = StreamUtils.readUnsigned8 (input);
            this.velocityCurveRatio = StreamUtils.readSigned8 (input);
            this.timeVelocitySensitivity = StreamUtils.readUnsigned8 (input);
            this.cutoffVelocitySens = StreamUtils.readUnsigned8 (input);
            this.levels = new int [4];
            for (int i = 0; i < 4; i++)
                this.levels[i] = StreamUtils.readUnsigned8 (input);
            this.times = new int [4];
            for (int i = 0; i < 4; i++)
                this.times[i] = StreamUtils.readUnsigned8 (input);
            this.envTvfDepth = StreamUtils.readUnsigned8 (input);
            this.envPitchDepth = StreamUtils.readUnsigned8 (input);
            this.tvfKfPoint = StreamUtils.readUnsigned8 (input);
            this.envTimeKf = StreamUtils.readUnsigned8 (input);
            this.envDepthKf = StreamUtils.readUnsigned8 (input);
            this.cutoffKf = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the filter mode.
         *
         * @return The filter mode, -1: Off, 0: LPF, 1: BPF, 2: HPF
         */
        public int getFilterMode ()
        {
            return this.filterMode;
        }


        /**
         * Get the cutoff.
         *
         * @return The cutoff value in the range of 0..127
         */
        public int getCutoff ()
        {
            return this.cutoff;
        }


        /**
         * Get the resonance.
         *
         * @return The resonance value in the range of 0..127
         */
        public int getResonance ()
        {
            return this.resonance;
        }


        /**
         * Get the velocity curve type.
         *
         * @return The curve type in the range of 0..3
         */
        public int getVelocityCurveType ()
        {
            return this.velocityCurveType;
        }


        /**
         * Get the velocity curve ratio.
         *
         * @return The curve ratio in the range of -63..63
         */
        public int getVelocityCurveRatio ()
        {
            return this.velocityCurveRatio;
        }


        /**
         * Get the time velocity sensitivity.
         *
         * @return The velocity sensitivity
         */
        public int getTimeVelocitySensitivity ()
        {
            return this.timeVelocitySensitivity;
        }


        /**
         * Get the cutoff velocity sensitivity.
         *
         * @return The velocity sensitivity in the range of -63..63
         */
        public int getCutoffVelocitySens ()
        {
            return this.cutoffVelocitySens;
        }


        /**
         * Get the TVF envelope levels 1-4.
         *
         * @return The TVF envelope levels in the range of 0..127
         */
        public int [] getLevels ()
        {
            return this.levels;
        }


        /**
         * Get the TVF envelope times 1-4.
         *
         * @return The TVF envelope times in the range of 0..127
         */
        public int [] getTimes ()
        {
            return this.times;
        }


        /**
         * Get the TVF envelope depth.
         *
         * @return The envelope depth in the range of -63..63
         */
        public int getEnvTvfDepth ()
        {
            return this.envTvfDepth;
        }


        /**
         * Get the envelope pitch depth.
         *
         * @return The envelope pitch depth in the range of -63..63
         */
        public int getEnvPitchDepth ()
        {
            return this.envPitchDepth;
        }


        /**
         * Get the TVF key follow point.
         *
         * @return The TVF key follow point
         */
        public int getTvfKfPoint ()
        {
            return this.tvfKfPoint;
        }


        /**
         * Get the envelope time key follow.
         *
         * @return The envelope time key follow
         */
        public int getEnvTimeKf ()
        {
            return this.envTimeKf;
        }


        /**
         * Get the envelope depth key follow.
         *
         * @return The envelope depth key follow
         */
        public int getEnvDepthKf ()
        {
            return this.envDepthKf;
        }


        /**
         * Get the cutoff key follow.
         *
         * @return The cutoff key follow in the range of [-63..63]
         */
        public int getCutoffKf ()
        {
            return this.cutoffKf;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "TvfSection [filterMode=" + this.filterMode + ", cutoff=" + this.cutoff + ", resonance=" + this.resonance + ", velocityCurveType=" + this.velocityCurveType + ", velocityCurveRatio=" + this.velocityCurveRatio + ", timeVelocitySens=" + this.timeVelocitySensitivity + ", cutoffVelocitySens=" + this.cutoffVelocitySens + ", levels=" + Arrays.toString (this.levels) + ", times=" + Arrays.toString (this.times) + ", envTvfDepth=" + this.envTvfDepth + ", envPitchDepth=" + this.envPitchDepth + ", tvfKfPoint=" + this.tvfKfPoint + ", envTimeKf=" + this.envTimeKf + ", envDepthKf=" + this.envDepthKf + ", cutoffKf=" + this.cutoffKf + "]";
        }
    }


    /** TVA section (16 bytes). */
    public static class TvaSection
    {
        private final int    velocityCurveType;
        private final int    velocityCurveRatio;
        private final int    timeVelocitySensitivity;
        private final int [] levels;
        private final int [] times;
        private final int    tvaKfPoint;
        private final int    envTimeKf;
        private final int    levelKf;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public TvaSection (final InputStream input) throws IOException
        {
            this.velocityCurveType = StreamUtils.readUnsigned8 (input);
            this.velocityCurveRatio = StreamUtils.readSigned8 (input);
            this.timeVelocitySensitivity = StreamUtils.readUnsigned8 (input);
            this.levels = new int [4];
            for (int i = 0; i < 4; i++)
                this.levels[i] = StreamUtils.readUnsigned8 (input);
            this.times = new int [4];
            for (int i = 0; i < 4; i++)
                this.times[i] = StreamUtils.readUnsigned8 (input);
            input.skipNBytes (1);
            this.tvaKfPoint = StreamUtils.readUnsigned8 (input);
            this.envTimeKf = StreamUtils.readUnsigned8 (input);
            input.skipNBytes (1);
            this.levelKf = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the velocity curve type.
         *
         * @return The velocity curve type in the range of 0..3
         */
        public int getVelocityCurveType ()
        {
            return this.velocityCurveType;
        }


        /**
         * Get the velocity curve ratio.
         *
         * @return The curve ratio in the range of -63..63
         */
        public int getVelocityCurveRatio ()
        {
            return this.velocityCurveRatio;
        }


        /**
         * Get the time velocity sensitivity.
         *
         * @return The velocity sensitivity
         */
        public int getTimeVelocitySensitivity ()
        {
            return this.timeVelocitySensitivity;
        }


        /**
         * Get the TVA envelope levels 1-4.
         *
         * @return The TVA envelope levels in the range of 0..127
         */
        public int [] getLevels ()
        {
            return this.levels;
        }


        /**
         * Get the TVA envelope times 1-4.
         *
         * @return The TVA envelope times in the range of 0..127
         */
        public int [] getTimes ()
        {
            return this.times;
        }


        /**
         * Get the TVA key follow point.
         *
         * @return The TVA key follow point
         */
        public int getTvaKfPoint ()
        {
            return this.tvaKfPoint;
        }


        /**
         * Get the envelope time key follow.
         *
         * @return The envelope time key follow
         */
        public int getEnvTimeKf ()
        {
            return this.envTimeKf;
        }


        /**
         * Get the level key follow.
         *
         * @return The level key follow
         */
        public int getLevelKf ()
        {
            return this.levelKf;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "TvaSection [velocityCurveType=" + this.velocityCurveType + ", velocityCurveRatio=" + this.velocityCurveRatio + ", timeVelocitySensitivity=" + this.timeVelocitySensitivity + ", levels=" + Arrays.toString (this.levels) + ", times=" + Arrays.toString (this.times) + ", tvaKfPoint=" + this.tvaKfPoint + ", envTimeKf=" + this.envTimeKf + ", levelKf=" + this.levelKf + "]";
        }
    }


    /** LFO generator section (9 bytes). */
    public static class LfoSection
    {
        private final int waveForm;
        private final int rate;
        private final int keySync;
        private final int delay;
        private final int delayKf;
        private final int detune;
        private final int pitch;
        private final int tvfModulationDepth;
        private final int tvaModulationDepth;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public LfoSection (final InputStream input) throws IOException
        {
            this.waveForm = StreamUtils.readUnsigned8 (input);
            this.rate = StreamUtils.readUnsigned8 (input);
            this.keySync = StreamUtils.readUnsigned8 (input);
            this.delay = StreamUtils.readUnsigned8 (input);
            this.delayKf = StreamUtils.readUnsigned8 (input);
            this.detune = StreamUtils.readUnsigned8 (input);
            this.pitch = StreamUtils.readUnsigned8 (input);
            this.tvfModulationDepth = StreamUtils.readUnsigned8 (input);
            this.tvaModulationDepth = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the LFO wave form.
         *
         * @return The wave form
         */
        public int getWaveForm ()
        {
            return this.waveForm;
        }


        /**
         * Get the LFO rate.
         *
         * @return The LFO rate
         */
        public int getRate ()
        {
            return this.rate;
        }


        /**
         * Get the key sync.
         *
         * @return The key sync
         */
        public int getKeySync ()
        {
            return this.keySync;
        }


        /**
         * Get the LFO start delay.
         *
         * @return The delay
         */
        public int getDelay ()
        {
            return this.delay;
        }


        /**
         * Get the delay key follow.
         *
         * @return The delay key follow
         */
        public int getDelayKf ()
        {
            return this.delayKf;
        }


        /**
         * Get the tuning modulation.
         *
         * @return The de-tuning
         */
        public int getDetune ()
        {
            return this.detune;
        }


        /**
         * Get the pitch modulation.
         *
         * @return The pitch modulation
         */
        public int getPitch ()
        {
            return this.pitch;
        }


        /**
         * Get the TVF modulation.
         *
         * @return The TVF modulation
         */
        public int getTvfModulationDepth ()
        {
            return this.tvfModulationDepth;
        }


        /**
         * Get the TVA modulation.
         *
         * @return The TVA modulation
         */
        public int getTvaModulationDepth ()
        {
            return this.tvaModulationDepth;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "LfoSection [waveForm=" + this.waveForm + ", rate=" + this.rate + ", keySync=" + this.keySync + ", delay=" + this.delay + ", delayKf=" + this.delayKf + ", detune=" + this.detune + ", pitch=" + this.pitch + ", tvfModulationDepth=" + this.tvfModulationDepth + ", tvaModulationDepth=" + this.tvaModulationDepth + "]";
        }
    }
}