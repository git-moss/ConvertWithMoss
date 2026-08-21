// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland;

/**
 * The time table of the Roland S-7xx sound engine, which the MV-8000/MV-8800 inherits.
 *
 * The samplers store every time as a 0-127 setting and look up the actual duration in a 128 entry
 * table. The table was read out of two firmwares, which hold the identical value sequence: the
 * S-760 system disk (version 2.24, file offset 0xB63EE, 16 bit little-endian, stored descending)
 * and the decompressed MV-8000 operating system (version 3.54, offset 0x50538C, 16 bit big-endian,
 * ascending). All 127 overlapping entries match.
 *
 * The unit of the entries is 1/3000 of a second. The S-760 owner's manual documents the LFO delay,
 * a 0-127 setting like the envelope times, as covering "0.01 - 22 sec": the table runs from 30 to
 * 65535, so 0.01 s / 30 gives 1/3000 s per entry and the last entry is 65535/3000 = 21.8 s, which
 * matches both ends of the documented range within one percent. The resulting law spans 0.0100 s
 * to 21.845 s in a geometric series with a ratio of 1.0624 per step, or 0.01 * 2^(value/11.45).
 *
 * @author Jürgen Moßgraber
 */
public class RolandTimeTable
{
    /** The number of table entries which pass in one second. */
    private static final double TICKS_PER_SECOND = 3000.0;

    /** The time table of the sound engine, in units of 1/3000 second. */
    private static final int [] TIMES            =
    {
        30,
        32,
        34,
        36,
        38,
        41,
        43,
        46,
        49,
        52,
        55,
        58,
        62,
        66,
        70,
        74,
        79,
        84,
        89,
        95,
        101,
        107,
        114,
        121,
        128,
        136,
        145,
        154,
        163,
        174,
        184,
        196,
        208,
        221,
        235,
        250,
        265,
        282,
        299,
        318,
        338,
        359,
        381,
        405,
        431,
        457,
        486,
        516,
        549,
        583,
        619,
        658,
        699,
        743,
        789,
        838,
        890,
        946,
        1005,
        1068,
        1134,
        1205,
        1280,
        1360,
        1445,
        1535,
        1631,
        1733,
        1841,
        1956,
        2078,
        2208,
        2346,
        2492,
        2648,
        2813,
        2989,
        3175,
        3373,
        3584,
        3808,
        4045,
        4298,
        4566,
        4851,
        5154,
        5475,
        5817,
        6180,
        6566,
        6976,
        7411,
        7874,
        8365,
        8887,
        9442,
        10031,
        10657,
        11323,
        12029,
        12780,
        13578,
        14425,
        15326,
        16282,
        17298,
        18378,
        19525,
        20744,
        22038,
        23414,
        24875,
        26428,
        28077,
        29830,
        31692,
        33670,
        35771,
        38004,
        40376,
        42896,
        45573,
        48418,
        51440,
        54650,
        58061,
        61685,
        65535
    };


    /**
     * Private due to helper class.
     */
    private RolandTimeTable ()
    {
        // Intentionally empty
    }


    /**
     * Get the number of settings of the table.
     *
     * @return The number of settings
     */
    public static int getNumberOfValues ()
    {
        return TIMES.length;
    }


    /**
     * Convert a time setting of the sampler into seconds.
     *
     * @param value The setting in the range of 0-127
     * @return The time in seconds, 0.01 at the lowest setting and 21.845 at the highest
     */
    public static double valueToSeconds (final int value)
    {
        return TIMES[Math.clamp (value, 0, TIMES.length - 1)] / TICKS_PER_SECOND;
    }


    /**
     * Convert a time in seconds into the time setting of the sampler. Since the table is a
     * geometric series the nearest setting is the one with the smallest ratio to the given time,
     * not the one with the smallest difference.
     *
     * @param seconds The time in seconds
     * @return The setting in the range of 0-127
     */
    public static int secondsToValue (final double seconds)
    {
        final double ticks = seconds * TICKS_PER_SECOND;
        if (ticks <= TIMES[0])
            return 0;
        final int last = TIMES.length - 1;
        if (ticks >= TIMES[last])
            return last;
        for (int i = 1; i <= last; i++)
            if (ticks < TIMES[i])
                return ticks * ticks < (double) TIMES[i - 1] * TIMES[i] ? i - 1 : i;
        return last;
    }
}
