// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

/**
 * Converts the settings of the envelope stages of the Akai samplers into times. The table is the
 * one of the S1000/S3000. The S5000/S6000 uses the same kind of settings (in the range of [0..100])
 * but its curve is not documented, therefore the table of its predecessor is used for it as well.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiEnvelopeTime
{
    /**
     * The rate at which the sound hardware moves an envelope towards its next level, one entry per
     * setting of an envelope stage. The S1000/S3000 looks the rate up with
     * <code>99 - setting</code>, so a setting of 0 gives the fastest rate and 99 the slowest, and
     * it uses this one table for the attack, the decay and the release of both of its envelopes
     * alike. The rates span a range of 16384 to 1, which is why the times are anything but a
     * straight line over the range of a setting.
     */
    private static final int [] ENVELOPE_RATE       =
    {
        2,
        2,
        2,
        3,
        3,
        3,
        4,
        4,
        4,
        5,
        5,
        6,
        6,
        7,
        8,
        9,
        10,
        11,
        12,
        13,
        14,
        16,
        17,
        19,
        21,
        23,
        26,
        28,
        31,
        34,
        38,
        42,
        46,
        51,
        56,
        62,
        68,
        75,
        83,
        91,
        101,
        111,
        123,
        135,
        149,
        165,
        182,
        200,
        221,
        244,
        269,
        297,
        327,
        361,
        398,
        439,
        484,
        534,
        589,
        650,
        716,
        790,
        872,
        961,
        1060,
        1170,
        1290,
        1423,
        1570,
        1731,
        1909,
        2106,
        2323,
        2562,
        2826,
        3117,
        3438,
        3792,
        4183,
        4614,
        5089,
        5613,
        6191,
        6828,
        7532,
        8307,
        9163,
        10106,
        11147,
        12295,
        13562,
        14958,
        16499,
        18198,
        20072,
        22139,
        24419,
        26934,
        29707,
        32767
    };

    /**
     * The distance which an envelope covers between silence and the full level, in the units in
     * which {@link #ENVELOPE_RATE} advances it. The hardware adds the rate to its envelope
     * accumulator once per sample at 44.1 kHz; the width of that accumulator is the one part of the
     * law which the operating system does not state, it is taken from the emulation of the sound
     * hardware. The ratios between the settings do not depend on it.
     */
    private static final double ENVELOPE_FULL_SWING = 32767.0 * 128.0;

    /** The rate at which the sound hardware advances an envelope. */
    private static final double ENVELOPE_RATE_HZ    = 44100.0;


    /**
     * Private due to helper class.
     */
    private AkaiEnvelopeTime ()
    {
        // Intentionally empty
    }


    /**
     * Convert a setting of an envelope stage of the S1000/S3000 into the time which that stage
     * takes.
     *
     * @param value The setting in the range of [0..99], 0 being the fastest
     * @return The time in seconds
     */
    public static double toSeconds (final int value)
    {
        return ENVELOPE_FULL_SWING / ENVELOPE_RATE[99 - Math.clamp (value, 0, 99)] / ENVELOPE_RATE_HZ;
    }


    /**
     * Convert a setting of an envelope stage of the S5000/S6000 into the time which that stage
     * takes. The range of [0..100] is mapped onto the one of the S1000/S3000.
     *
     * @param value The setting in the range of [0..100], 0 being the fastest
     * @return The time in seconds
     */
    public static double toSecondsS5000 (final int value)
    {
        return toSeconds ((int) Math.round (Math.clamp (value, 0, 100) * 0.99));
    }


    /**
     * Convert a time into the setting of an envelope stage of the S5000/S6000. The inverse of
     * toSecondsS5000.
     *
     * @param seconds The time in seconds
     * @return The setting in the range of [0..100] whose time is the closest to the given one
     */
    public static int fromSecondsS5000 (final double seconds)
    {
        if (seconds <= 0)
            return 0;

        // The times grow exponentially, therefore compare on a logarithmic scale
        final double logSeconds = Math.log (seconds);
        int bestValue = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int value = 0; value <= 100; value++)
        {
            final double distance = Math.abs (Math.log (toSecondsS5000 (value)) - logSeconds);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestValue = value;
            }
        }
        return bestValue;
    }
}
