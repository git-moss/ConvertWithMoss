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
    private final String        partialName;
    private final SampleSection sample1;
    private final int           outputAssign8;
    private final int           stereoMixLevel;
    private final int           partialLevel;
    private final int           outputAssign6;
    private final SampleSection sample2;
    private final int           pan;
    private final int           coarseTune;
    private final int           fineTune;
    private final int           breathCtrl;
    private final SampleSection sample3;
    private final SampleSection sample4;
    private final TvfSection    tvf;
    private final TvaSection    tva;
    private final LfoSection    lfoGenerator;


    public S770Partial (final InputStream in) throws IOException
    {
        this.partialName = StreamUtils.readAscii (in, 16);

        this.sample1 = new SampleSection (in);
        in.skipNBytes (1);
        this.outputAssign8 = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.stereoMixLevel = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.partialLevel = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.outputAssign6 = StreamUtils.readUnsigned8 (in) & 0xFF;

        this.sample2 = new SampleSection (in);
        in.skipNBytes (1);
        this.pan = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.coarseTune = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.fineTune = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.breathCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;

        this.sample3 = new SampleSection (in);
        in.skipNBytes (5);

        this.sample4 = new SampleSection (in);
        this.tvf = new TvfSection (in);
        this.tva = new TvaSection (in);
        this.lfoGenerator = new LfoSection (in);
        in.skipNBytes (7);
    }


    public String getPartialName ()
    {
        return this.partialName;
    }


    public SampleSection getSample1 ()
    {
        return this.sample1;
    }


    public int getOutputAssign8 ()
    {
        return this.outputAssign8;
    }


    public int getStereoMixLevel ()
    {
        return this.stereoMixLevel;
    }


    public int getPartialLevel ()
    {
        return this.partialLevel;
    }


    public int getOutputAssign6 ()
    {
        return this.outputAssign6;
    }


    public SampleSection getSample2 ()
    {
        return this.sample2;
    }


    public int getPan ()
    {
        return this.pan;
    }


    public int getCoarseTune ()
    {
        return this.coarseTune;
    }


    public int getFineTune ()
    {
        return this.fineTune;
    }


    public int getBreathCtrl ()
    {
        return this.breathCtrl;
    }


    public SampleSection getSample3 ()
    {
        return this.sample3;
    }


    public SampleSection getSample4 ()
    {
        return this.sample4;
    }


    public TvfSection getTvf ()
    {
        return this.tvf;
    }


    public TvaSection getTva ()
    {
        return this.tva;
    }


    public LfoSection getLfoGenerator ()
    {
        return this.lfoGenerator;
    }


    @Override
    public String toString ()
    {
        return "S770PartialParameter [\n" + "  partialName='" + this.partialName.trim () + "'\n" + "  sample1=" + this.sample1 + "\n" + "  outputAssign8=" + this.outputAssign8 + "\n" + "  stereoMixLevel=" + this.stereoMixLevel + "\n" + "  partialLevel=" + this.partialLevel + "\n" + "  outputAssign6=" + this.outputAssign6 + "\n" + "  sample2=" + this.sample2 + "\n" + "  pan=" + this.pan + "\n" + "  coarseTune=" + this.coarseTune + "\n" + "  fineTune=" + this.fineTune + "\n" + "  breathCtrl=" + this.breathCtrl + "\n" + "  sample3=" + this.sample3 + "\n" + "  sample4=" + this.sample4 + "\n" + "  tvf=" + this.tvf + "\n" + "  tva=" + this.tva + "\n" + "  lfoGenerator=" + this.lfoGenerator + "\n]";
    }

    // -------------------------------------------------------------------------


    /** Sample section (11 bytes): u2 + u1×9. */
    public static class SampleSection
    {
        private final int sampleSelection;
        private final int pitchKf;
        private final int sampleLevel;
        private final int pan;
        private final int coarseTune;
        private final int fineTune;
        private final int smtVelocityLower;
        private final int smtFadeWithLower;
        private final int smtVelocityUpper;
        private final int smtFadeWithUpper;


        public SampleSection (final InputStream in) throws IOException
        {
            this.sampleSelection = StreamUtils.readUnsigned16 (in, false);
            this.pitchKf = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.sampleLevel = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.pan = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.coarseTune = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.fineTune = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.smtVelocityLower = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.smtFadeWithLower = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.smtVelocityUpper = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.smtFadeWithUpper = StreamUtils.readUnsigned8 (in) & 0xFF;
        }


        public int getSampleSelection ()
        {
            return this.sampleSelection;
        }


        public int getPitchKf ()
        {
            return this.pitchKf;
        }


        public int getSampleLevel ()
        {
            return this.sampleLevel;
        }


        public int getPan ()
        {
            return this.pan;
        }


        public int getCoarseTune ()
        {
            return this.coarseTune;
        }


        public int getFineTune ()
        {
            return this.fineTune;
        }


        public int getSmtVelocityLower ()
        {
            return this.smtVelocityLower;
        }


        public int getSmtFadeWithLower ()
        {
            return this.smtFadeWithLower;
        }


        public int getSmtVelocityUpper ()
        {
            return this.smtVelocityUpper;
        }


        public int getSmtFadeWithUpper ()
        {
            return this.smtFadeWithUpper;
        }


        @Override
        public String toString ()
        {
            return "SampleSection [sampleSelection=" + this.sampleSelection + ", pitchKf=" + this.pitchKf + ", sampleLevel=" + this.sampleLevel + ", pan=" + this.pan + ", coarseTune=" + this.coarseTune + ", fineTune=" + this.fineTune + ", smtVelocityLower=" + this.smtVelocityLower + ", smtFadeWithLower=" + this.smtFadeWithLower + ", smtVelocityUpper=" + this.smtVelocityUpper + ", smtFadeWithUpper=" + this.smtFadeWithUpper + "]";
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
        private final int    timeVelocitySens;
        private final int    cutoffVelocitySens;
        private final int [] levels;
        private final int [] times;
        private final int    envTvfDepth;
        private final int    envPitchDepth;
        private final int    tvfKfPoint;
        private final int    envTimeKf;
        private final int    envDepthKf;
        private final int    cutoffKf;


        public TvfSection (final InputStream in) throws IOException
        {
            this.filterMode = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.cutoff = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.resonance = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.velocityCurveType = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.velocityCurveRatio = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.timeVelocitySens = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.cutoffVelocitySens = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.levels = new int [4];
            for (int i = 0; i < 4; i++)
                this.levels[i] = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.times = new int [4];
            for (int i = 0; i < 4; i++)
                this.times[i] = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.envTvfDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.envPitchDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvfKfPoint = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.envTimeKf = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.envDepthKf = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.cutoffKf = StreamUtils.readUnsigned8 (in) & 0xFF;
        }


        public int getFilterMode ()
        {
            return this.filterMode;
        }


        public int getCutoff ()
        {
            return this.cutoff;
        }


        public int getResonance ()
        {
            return this.resonance;
        }


        public int getVelocityCurveType ()
        {
            return this.velocityCurveType;
        }


        public int getVelocityCurveRatio ()
        {
            return this.velocityCurveRatio;
        }


        public int getTimeVelocitySens ()
        {
            return this.timeVelocitySens;
        }


        public int getCutoffVelocitySens ()
        {
            return this.cutoffVelocitySens;
        }


        public int [] getLevels ()
        {
            return this.levels;
        }


        public int [] getTimes ()
        {
            return this.times;
        }


        public int getEnvTvfDepth ()
        {
            return this.envTvfDepth;
        }


        public int getEnvPitchDepth ()
        {
            return this.envPitchDepth;
        }


        public int getTvfKfPoint ()
        {
            return this.tvfKfPoint;
        }


        public int getEnvTimeKf ()
        {
            return this.envTimeKf;
        }


        public int getEnvDepthKf ()
        {
            return this.envDepthKf;
        }


        public int getCutoffKf ()
        {
            return this.cutoffKf;
        }


        @Override
        public String toString ()
        {
            return "TvfSection [filterMode=" + this.filterMode + ", cutoff=" + this.cutoff + ", resonance=" + this.resonance + ", velocityCurveType=" + this.velocityCurveType + ", velocityCurveRatio=" + this.velocityCurveRatio + ", timeVelocitySens=" + this.timeVelocitySens + ", cutoffVelocitySens=" + this.cutoffVelocitySens + ", levels=" + Arrays.toString (this.levels) + ", times=" + Arrays.toString (this.times) + ", envTvfDepth=" + this.envTvfDepth + ", envPitchDepth=" + this.envPitchDepth + ", tvfKfPoint=" + this.tvfKfPoint + ", envTimeKf=" + this.envTimeKf + ", envDepthKf=" + this.envDepthKf + ", cutoffKf=" + this.cutoffKf + "]";
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


        public TvaSection (final InputStream in) throws IOException
        {
            this.velocityCurveType = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.velocityCurveRatio = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.timeVelocitySensitivity = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.levels = new int [4];
            for (int i = 0; i < 4; i++)
                this.levels[i] = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.times = new int [4];
            for (int i = 0; i < 4; i++)
                this.times[i] = StreamUtils.readUnsigned8 (in) & 0xFF;
            in.skipNBytes (1);
            this.tvaKfPoint = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.envTimeKf = StreamUtils.readUnsigned8 (in) & 0xFF;
            in.skipNBytes (1);
            this.levelKf = StreamUtils.readUnsigned8 (in) & 0xFF;
        }


        public int getVelocityCurveType ()
        {
            return this.velocityCurveType;
        }


        public int getVelocityCurveRatio ()
        {
            return this.velocityCurveRatio;
        }


        public int getTimeVelocitySensitivity ()
        {
            return this.timeVelocitySensitivity;
        }


        public int [] getLevels ()
        {
            return this.levels;
        }


        public int [] getTimes ()
        {
            return this.times;
        }


        public int getTvaKfPoint ()
        {
            return this.tvaKfPoint;
        }


        public int getEnvTimeKf ()
        {
            return this.envTimeKf;
        }


        public int getLevelKf ()
        {
            return this.levelKf;
        }


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


        public LfoSection (final InputStream in) throws IOException
        {
            this.waveForm = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.rate = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.keySync = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.delay = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.delayKf = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.detune = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.pitch = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvfModulationDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvaModulationDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
        }


        public int getWaveForm ()
        {
            return this.waveForm;
        }


        public int getRate ()
        {
            return this.rate;
        }


        public int getKeySync ()
        {
            return this.keySync;
        }


        public int getDelay ()
        {
            return this.delay;
        }


        public int getDelayKf ()
        {
            return this.delayKf;
        }


        public int getDetune ()
        {
            return this.detune;
        }


        public int getPitch ()
        {
            return this.pitch;
        }


        public int getTvfModulationDepth ()
        {
            return this.tvfModulationDepth;
        }


        public int getTvaModulationDepth ()
        {
            return this.tvaModulationDepth;
        }


        @Override
        public String toString ()
        {
            return "LfoSection [waveForm=" + this.waveForm + ", rate=" + this.rate + ", keySync=" + this.keySync + ", delay=" + this.delay + ", delayKf=" + this.delayKf + ", detune=" + this.detune + ", pitch=" + this.pitch + ", tvfModulationDepth=" + this.tvfModulationDepth + ", tvaModulationDepth=" + this.tvaModulationDepth + "]";
        }
    }
}