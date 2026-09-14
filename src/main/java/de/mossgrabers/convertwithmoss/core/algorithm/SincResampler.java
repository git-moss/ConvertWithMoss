// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

/**
 * Converts the sample rate of one channel of audio with a band-limited interpolation: the samples
 * are convolved with a sinc kernel which is limited to a number of zero crossings by a Kaiser
 * window.
 * <p>
 * The kernel is stretched when down-sampling, which puts its cut-off frequency below the new
 * Nyquist frequency and is what keeps the frequencies above it from folding back into the audible
 * range. When up-sampling the kernel keeps the cut-off at the Nyquist frequency of the source,
 * which is what keeps the images of the original spectrum out of the result.
 *
 * @author Jürgen Moßgraber
 */
public final class SincResampler
{
    /** The number of zero crossings of the sinc kernel on each side of its center. */
    private static final int       ZERO_CROSSINGS     = 24;
    /** The number of kernel values which are pre-calculated per zero crossing. */
    private static final int       STEPS_PER_CROSSING = 128;
    /** The beta parameter of the Kaiser window, which gives about -100 dB of stop band. */
    private static final double    KAISER_BETA        = 9.0;
    /**
     * The cut-off is placed at this fraction of the Nyquist frequency instead of exactly at it. A
     * filter of a finite length has a transition band, and centering that band on the Nyquist
     * frequency lets the content just below it leak just above it. Measured on a 32 kHz sample
     * converted to 48 kHz: the energy above the source Nyquist frequency is -56 dB at a factor of
     * 1.0 and -79 dB at this one, at the cost of 1.8 dB of the content above 15.2 kHz.
     */
    private static final double    PASS_BAND          = 0.95;
    /**
     * The number of fractional positions for which the kernel weights of a loop are calculated
     * once, the weights in between are interpolated linearly. The error of the interpolation is
     * below -120 dB.
     */
    private static final int       LOOP_PHASES        = 2048;

    private static final double [] KERNEL             = createKernel ();


    /**
     * Private due to helper class.
     */
    private SincResampler ()
    {
        // Intentionally empty
    }


    /**
     * Convert the sample rate of one channel.
     *
     * @param input The input samples
     * @param sourceRate The sample rate of the input
     * @param targetRate The sample rate of the result
     * @return The converted samples
     */
    public static double [] resample (final double [] input, final int sourceRate, final int targetRate)
    {
        final double ratio = targetRate / (double) sourceRate;
        final int outputLength = (int) Math.round (input.length * ratio);
        final double [] output = new double [outputLength];
        if (outputLength == 0 || input.length == 0)
            return output;

        final Lattice lattice = new Lattice (sourceRate, targetRate);
        for (int index = 0; index < outputLength; index++)
            output[index] = lattice.interpolate (input, (long) index * lattice.step);
        return output;
    }


    /**
     * Convert the sample rate of one channel which plays a loop. Converting it like any other audio
     * breaks the loop in two ways: the frames behind the loop end are not the frames which are
     * played after it, but the kernel mixes them into the end of the loop, and a loop of L frames
     * becomes L x ratio frames, which is rarely a whole number - the positions are rounded and the
     * waveform no longer meets itself at the wrap. Both give a click at every repeat which the
     * source does not have.
     * <p>
     * Therefore the loop is converted as the periodic signal it is played as: its frames are
     * interpolated from the loop repeated in both directions, and it is stretched to the nearest
     * whole number of frames, which changes its pitch by less than half a frame per loop length (a
     * loop of 4096 frames by less than 0.25 cents). The audio in front of the loop is converted on
     * a grid which ends exactly at the loop start and the audio behind it on a grid which starts
     * exactly behind the loop end, so both join the loop without an offset.
     *
     * @param input The input samples
     * @param sourceRate The sample rate of the input
     * @param targetRate The sample rate of the result
     * @param loopStart The first frame of the loop
     * @param loopEnd The last frame of the loop (inclusive)
     * @return The converted samples, the loop is located at the positions given by
     *         {@link #mapLoop(int, int, int, int)}
     */
    public static double [] resampleLoop (final double [] input, final int sourceRate, final int targetRate, final int loopStart, final int loopEnd)
    {
        final int length = input.length;
        if (loopStart < 0 || loopEnd <= loopStart || loopEnd >= length)
            return resample (input, sourceRate, targetRate);

        final int [] targetLoop = mapLoop (loopStart, loopEnd, sourceRate, targetRate);
        final int targetStart = targetLoop[0];
        final int targetLoopLength = targetLoop[1] - targetStart + 1;
        final double [] output = new double [getLength (length, loopStart, loopEnd, sourceRate, targetRate)];
        final int targetTailLength = output.length - targetStart - targetLoopLength;

        final Lattice lattice = new Lattice (sourceRate, targetRate);

        // In front of the loop: the output frame at the loop start is exactly the input frame
        final long startOffset = (long) loopStart * lattice.period;
        for (int index = 0; index < targetStart; index++)
            output[index] = lattice.interpolate (input, (index - targetStart) * (long) lattice.step + startOffset);

        // The loop: the input positions advance by loopLength / targetLoopLength per frame
        final int loopLength = loopEnd - loopStart + 1;
        for (int index = 0; index < targetLoopLength; index++)
        {
            final long numerator = (long) index * loopLength;
            final int base = loopStart + (int) (numerator / targetLoopLength);
            final double fraction = numerator % targetLoopLength / (double) targetLoopLength;
            output[targetStart + index] = lattice.interpolateLoop (input, base, fraction, loopStart, loopLength);
        }

        // Behind the loop: the first output frame is exactly the input frame behind the loop end
        final long endOffset = (loopEnd + 1L) * lattice.period;
        final int tailStart = targetStart + targetLoopLength;
        for (int index = 0; index < targetTailLength; index++)
            output[tailStart + index] = lattice.interpolate (input, index * (long) lattice.step + endOffset);

        return output;
    }


    /**
     * Calculate where a loop is located after {@link #resampleLoop(double[], int, int, int, int)}.
     * The start is scaled and the length is the scaled loop length rounded to whole frames.
     *
     * @param loopStart The first frame of the loop in the source
     * @param loopEnd The last frame of the loop in the source (inclusive)
     * @param sourceRate The sample rate of the source
     * @param targetRate The sample rate of the result
     * @return The first and the last (inclusive) frame of the loop in the result
     */
    public static int [] mapLoop (final int loopStart, final int loopEnd, final int sourceRate, final int targetRate)
    {
        final double ratio = targetRate / (double) sourceRate;
        final int start = (int) Math.round (loopStart * ratio);
        final int loopLength = Math.max (1, (int) Math.round ((loopEnd - loopStart + 1) * ratio));
        return new int []
        {
            start,
            start + loopLength - 1
        };
    }


    /**
     * Calculate the number of frames which {@link #resampleLoop(double[], int, int, int, int)}
     * creates.
     *
     * @param length The number of frames of the source
     * @param loopStart The first frame of the loop in the source
     * @param loopEnd The last frame of the loop in the source (inclusive)
     * @param sourceRate The sample rate of the source
     * @param targetRate The sample rate of the result
     * @return The number of frames of the result
     */
    public static int getLength (final int length, final int loopStart, final int loopEnd, final int sourceRate, final int targetRate)
    {
        final double ratio = targetRate / (double) sourceRate;
        if (loopStart < 0 || loopEnd <= loopStart || loopEnd >= length)
            return (int) Math.round (length * ratio);
        final int [] targetLoop = mapLoop (loopStart, loopEnd, sourceRate, targetRate);
        return targetLoop[1] + 1 + (int) Math.round ((length - loopEnd - 1) * ratio);
    }


    /**
     * The kernel weights for the fractional positions which occur when converting between two
     * sample rates. The position of an output frame in the input is index * step / period,
     * therefore only 'period' different fractional positions occur and the weights of each of them
     * are calculated once instead of once per output frame.
     */
    private static final class Lattice
    {
        private final int         step;
        private final int         period;
        private final double      cutoff;
        private final int         first;
        private final int         taps;
        private final double [] [] weights;
        private final double []   weightSums;
        private double [] []       loopWeights;
        private double []         loopWeightSums;


        /**
         * Constructor.
         *
         * @param sourceRate The sample rate of the input
         * @param targetRate The sample rate of the result
         */
        Lattice (final int sourceRate, final int targetRate)
        {
            final double ratio = targetRate / (double) sourceRate;
            this.cutoff = Math.min (1.0, ratio) * PASS_BAND;
            final double halfWidth = ZERO_CROSSINGS / this.cutoff;

            final int divisor = gcd (sourceRate, targetRate);
            this.step = sourceRate / divisor;
            this.period = targetRate / divisor;

            this.first = (int) Math.ceil (-halfWidth);
            final int last = (int) Math.floor (1.0 + halfWidth);
            this.taps = last - this.first + 1;
            this.weights = new double [this.period] [this.taps];
            this.weightSums = new double [this.period];
            for (int phase = 0; phase < this.period; phase++)
                this.weightSums[phase] = this.calculateWeights (phase / (double) this.period, this.weights[phase]);
        }


        /**
         * Interpolate the input at a position on the lattice.
         *
         * @param input The input samples
         * @param position The position in the input multiplied by the period, may be negative
         * @return The interpolated value
         */
        double interpolate (final double [] input, final long position)
        {
            final int base = (int) Math.floorDiv (position, this.period);
            final int phase = (int) Math.floorMod (position, this.period);
            final double [] phaseWeights = this.weights[phase];

            final int length = input.length;
            double sum = 0;
            for (int tap = 0; tap < this.taps; tap++)
            {
                final int index = base + this.first + tap;
                // Taps outside of the sample contribute nothing but still count towards the
                // weight, which fades the very start and end instead of stepping at it
                if (index >= 0 && index < length)
                    sum += input[index] * phaseWeights[tap];
            }
            return this.weightSums[phase] == 0 ? 0 : sum / this.weightSums[phase];
        }


        /**
         * Interpolate the loop, which is repeated in both directions, at any position.
         *
         * @param input The input samples
         * @param base The integer part of the position
         * @param fraction The fractional part of the position
         * @param loopStart The first frame of the loop
         * @param loopLength The number of frames of the loop
         * @return The interpolated value
         */
        double interpolateLoop (final double [] input, final int base, final double fraction, final int loopStart, final int loopLength)
        {
            if (this.loopWeights == null)
            {
                this.loopWeights = new double [LOOP_PHASES + 1] [this.taps];
                this.loopWeightSums = new double [LOOP_PHASES + 1];
                for (int phase = 0; phase <= LOOP_PHASES; phase++)
                    this.loopWeightSums[phase] = this.calculateWeights (phase / (double) LOOP_PHASES, this.loopWeights[phase]);
            }

            final double scaled = fraction * LOOP_PHASES;
            final int phase = Math.min ((int) scaled, LOOP_PHASES - 1);
            final double mix = scaled - phase;
            final double [] lowerWeights = this.loopWeights[phase];
            final double [] upperWeights = this.loopWeights[phase + 1];

            final int loopEnd = loopStart + loopLength;
            int index = loopStart + Math.floorMod (base + this.first - loopStart, loopLength);
            double sum = 0;
            for (int tap = 0; tap < this.taps; tap++)
            {
                sum += input[index] * (lowerWeights[tap] + (upperWeights[tap] - lowerWeights[tap]) * mix);
                index++;
                if (index == loopEnd)
                    index = loopStart;
            }
            final double weightSum = this.loopWeightSums[phase] + (this.loopWeightSums[phase + 1] - this.loopWeightSums[phase]) * mix;
            return weightSum == 0 ? 0 : sum / weightSum;
        }


        private double calculateWeights (final double fraction, final double [] result)
        {
            double sum = 0;
            for (int tap = 0; tap < this.taps; tap++)
            {
                final double weight = kernelValue (this.cutoff * (fraction - (this.first + tap)));
                result[tap] = weight;
                sum += weight;
            }
            return sum;
        }
    }


    /**
     * Calculate the greatest common divisor.
     *
     * @param a The first value
     * @param b The second value
     * @return The greatest common divisor
     */
    private static int gcd (final int a, final int b)
    {
        return b == 0 ? a : gcd (b, a % b);
    }


    /**
     * Look the kernel up at the given distance from its center.
     *
     * @param distance The distance in zero crossings
     * @return The interpolated kernel value
     */
    private static double kernelValue (final double distance)
    {
        final double position = Math.abs (distance) * STEPS_PER_CROSSING;
        final int index = (int) position;
        if (index >= KERNEL.length - 1)
            return 0;
        return KERNEL[index] + (KERNEL[index + 1] - KERNEL[index]) * (position - index);
    }


    /**
     * Pre-calculate one half of the windowed sinc kernel.
     *
     * @return The kernel values from its center outwards
     */
    private static double [] createKernel ()
    {
        final int length = ZERO_CROSSINGS * STEPS_PER_CROSSING + 1;
        final double [] kernel = new double [length];
        final double normalization = besselI0 (KAISER_BETA);
        for (int i = 0; i < length; i++)
        {
            final double x = i / (double) STEPS_PER_CROSSING;
            final double sinc = i == 0 ? 1.0 : Math.sin (Math.PI * x) / (Math.PI * x);
            final double relative = x / ZERO_CROSSINGS;
            final double window = besselI0 (KAISER_BETA * Math.sqrt (Math.max (0, 1.0 - relative * relative))) / normalization;
            kernel[i] = sinc * window;
        }
        return kernel;
    }


    /**
     * The modified Bessel function of the first kind and order zero, calculated from its series.
     *
     * @param x The parameter
     * @return The value
     */
    private static double besselI0 (final double x)
    {
        double sum = 1.0;
        double term = 1.0;
        final double half = x / 2.0;
        for (int i = 1; i < 64; i++)
        {
            term *= half / i;
            final double square = term * term;
            sum += square;
            if (square < sum * 1e-17)
                break;
        }
        return sum;
    }
}
