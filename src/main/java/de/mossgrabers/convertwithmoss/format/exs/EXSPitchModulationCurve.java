// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

/**
 * Detects recursively Logic EXS24 files in folders. Files must end with <i>.exs</i>.
 *
 * @author Jürgen Moßgraber
 */
public class EXSPitchModulationCurve
{
    /** Input knots (strictly increasing). */
    private static final double [] XS =
    {
        -1000,
        -921,
        -896,
        -750,
        -572,
        -520,
        -500,
        -343,
        -250,
        -100,
        -50,
        0,
        50,
        100,
        250,
        343,
        500,
        520,
        572,
        750,
        896,
        921,
        1000
    };

    /** Output knots corresponding to XS. */
    private static final double [] YS =
    {
        -1200,
        -600,
        -500,
        -240,
        -120,
        -100,
        -92,
        -50,
        -32,
        -11,
        -5,
        0,
        5,
        11,
        32,
        50,
        92,
        100,
        120,
        240,
        500,
        600,
        1200
    };

    /**
     * Pre-computed PCHIP (Fritsch–Carlson) slopes at each knot. These guarantee that the resulting
     * piecewise cubic Hermite interpolant passes through all knots and is monotonically increasing
     * on the whole interval.
     */
    private static final double [] MS =
    {
        7.594937,                      // slope at x = -1000
        4.973300,                      // slope at x = -921
        2.709819,                      // slope at x = -896
        0.992790,                      // slope at x = -750
        0.466500,                      // slope at x = -572
        0.393299,                      // slope at x = -520
        0.337914,                      // slope at x = -500
        0.221566,                      // slope at x = -343
        0.164541,                      // slope at x = -250
        0.127595,                      // slope at x = -100
        0.109091,                      // slope at x = -50
        0.100000,                      // slope at x = 0
        0.109091,                      // slope at x = 50
        0.127595,                      // slope at x = 100
        0.164541,                      // slope at x = 250
        0.221566,                      // slope at x = 343
        0.337914,                      // slope at x = 500
        0.393299,                      // slope at x = 520
        0.466500,                      // slope at x = 572
        0.992790,                      // slope at x = 750
        2.709819,                      // slope at x = 896
        4.973300,                      // slope at x = 921
        7.594937                       // slope at x = 1000
    };


    /**
     * Constructor. Private due to helper class.
     */
    private EXSPitchModulationCurve ()
    {
        // Intentionally empty
    }


    /**
     * Maps an input value from the domain [-1000, 1000] to the range [-1200, 1200] using a
     * monotonic cubic Hermite (PCHIP) interpolation that passes exactly through the following
     * calibration points:
     *
     * <pre>
     *   -1000 -> -1200
     *    -921 ->  -600
     *    -896 ->  -500
     *    -750 ->  -240
     *    -572 ->  -120
     *    -520 ->  -100
     *    -500 ->   -92
     *    -343 ->   -50
     *    -250 ->   -32
     *    -100 ->   -11
     *     -50 ->    -5
     *       0 ->     0
     *      50 ->     5
     *     100 ->    11
     *     250 ->    32
     *     343 ->    50
     *     500 ->    92
     *     520 ->   100
     *     572 ->   120
     *     750 ->   240
     *     896 ->   500
     *     921 ->   600
     *    1000 ->  1200
     * </pre>
     *
     * The function is continuous, C¹-smooth, and strictly monotonically increasing. Inputs outside
     * the domain are clamped to the corresponding boundary output.
     *
     * @param x the input value; typically in [-1000, 1000]. Values below -1000 are clamped to
     *            -1200, values above 1000 are clamped to 1200.
     * @return the mapped output value in [-1200, 1200].
     */
    public static double map (final double x)
    {
        if (x <= XS[0])
            return YS[0];
        if (x >= XS[XS.length - 1])
            return YS[YS.length - 1];

        final int i = segmentIndex (x);
        return hermite (x, i);
    }


    /**
     * Inverse of {@link #map(double)}. Maps an output value from [-1200, 1200] back to the
     * corresponding input in [-1000, 1000].
     *
     * Because {@link #map(double)} is strictly monotonic, the inverse is uniquely defined. The
     * inversion is performed by locating the containing segment and refining the result with
     * bisection until a precision of ~1e-10 is reached (at most 60 iterations).
     *
     * @param y the output value to invert; typically in [-1200, 1200]. Values below -1200 are
     *            clamped to -1000, values above 1200 are clamped to 1000.
     * @return the input value {@code x} in [-1000, 1000] such that {@code map(x) ≈ y}.
     */
    public static double unmap (final double y)
    {
        if (y <= YS[0])
            return XS[0];
        if (y >= YS[YS.length - 1])
            return XS[XS.length - 1];

        // Locate segment (YS is strictly increasing).
        int i = 0;
        for (int k = 0; k < YS.length - 1; k++)
            if (y >= YS[k] && y <= YS[k + 1])
            {
                i = k;
                break;
            }

        // Bisection inside the segment.
        double lo = XS[i];
        double hi = XS[i + 1];
        for (int iter = 0; iter < 60; iter++)
        {
            final double mid = 0.5 * (lo + hi);
            final double ym = hermite (mid, i);
            if (ym < y)
                lo = mid;
            else
                hi = mid;
            if (hi - lo < 1e-10)
                break;
        }
        return 0.5 * (lo + hi);
    }


    private static int segmentIndex (final double x)
    {
        for (int k = 0; k < XS.length - 1; k++)
            if (x >= XS[k] && x <= XS[k + 1])
                return k;
        return XS.length - 2;
    }


    private static double hermite (final double x, final int i)
    {
        final double h = XS[i + 1] - XS[i];
        final double t = (x - XS[i]) / h;
        final double t2 = t * t;
        final double t3 = t2 * t;

        final double h00 = 2 * t3 - 3 * t2 + 1;
        final double h10 = t3 - 2 * t2 + t;
        final double h01 = -2 * t3 + 3 * t2;
        final double h11 = t3 - t2;

        return h00 * YS[i] + h10 * h * MS[i] + h01 * YS[i + 1] + h11 * h * MS[i + 1];
    }
}
