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

        final double cutoff = Math.min (1.0, ratio) * PASS_BAND;
        final double halfWidth = ZERO_CROSSINGS / cutoff;

        // The position of an output sample in the input is index * step / period, therefore only
        // 'period' different fractional positions occur and the weights of each of them are
        // calculated once instead of once per output sample
        final int divisor = gcd (sourceRate, targetRate);
        final int step = sourceRate / divisor;
        final int period = targetRate / divisor;

        final int first = (int) Math.ceil (-halfWidth);
        final int last = (int) Math.floor (1.0 + halfWidth);
        final int taps = last - first + 1;
        final double [] [] weights = new double [period] [taps];
        final double [] weightSums = new double [period];
        for (int phase = 0; phase < period; phase++)
        {
            final double fraction = phase / (double) period;
            double sum = 0;
            for (int tap = 0; tap < taps; tap++)
            {
                final double weight = kernelValue (cutoff * (fraction - (first + tap)));
                weights[phase][tap] = weight;
                sum += weight;
            }
            weightSums[phase] = sum;
        }

        final int length = input.length;
        for (int index = 0; index < outputLength; index++)
        {
            final long scaled = (long) index * step;
            final int base = (int) (scaled / period);
            final int phase = (int) (scaled % period);
            final double [] phaseWeights = weights[phase];

            double sum = 0;
            for (int tap = 0; tap < taps; tap++)
            {
                final int position = base + first + tap;
                // Taps outside of the sample contribute nothing but still count towards the
                // weight, which fades the very start and end instead of stepping at it
                if (position >= 0 && position < length)
                    sum += input[position] * phaseWeights[tap];
            }
            output[index] = weightSums[phase] == 0 ? 0 : sum / weightSums[phase];
        }
        return output;
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
