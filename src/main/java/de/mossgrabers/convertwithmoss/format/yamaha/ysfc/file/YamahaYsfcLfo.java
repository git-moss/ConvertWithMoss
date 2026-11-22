// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file;

import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;


/**
 * Conversions of the Element and Part LFO parameters of the MONTAGE/MODX. The values are not
 * documented by Yamaha, they were measured on a MODX+ (firmware 3). Element LFO: with a triangle
 * LFO, the speed with tremolo recordings, the amplitude depth from the level dips, the pitch depth
 * with a pitch tracker and the filter depth from the level changes of the harmonics above the
 * cutoff of a LPF12+HPF12 filter (estimated, assuming a slope of 12dB per octave). Part LFO: with a
 * square LFO, the speed and the pitch depth with a pitch tracker, the level dip at full depth.
 *
 * @author Jürgen Moßgraber
 */
public final class YamahaYsfcLfo
{
    /** The wave forms in the order of the LFO Wave parameter: Saw, Triangle, Square. */
    private static final LfoWaveform [] WAVEFORMS          =
    {
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.TRIANGLE,
        LfoWaveform.SQUARE
    };

    /** Measured rates of the normal LFO speed (0-63), interpolated logarithmically. */
    private static final double [] []   NORMAL_SPEEDS      =
    {
        {
            0,
            0.18
        },
        {
            10,
            0.43
        },
        {
            20,
            1.01
        },
        {
            30,
            2.44
        },
        {
            40,
            5.72
        },
        {
            50,
            13.6
        },
        {
            60,
            32.3
        },
        {
            63,
            41.9
        }
    };

    /**
     * The extended LFO speed (0-415) is linear in segments, the slope doubles at 128 and 192 and is
     * about 16 times the slope of the first segment above 256 (measured up to 280, the last slope
     * is used up to 415). Each row contains the start of a segment, its rate in Hertz and its slope
     * in Hertz per step.
     */
    private static final double [] []   EXTENDED_SPEEDS    =
    {
        {
            0,
            0,
            0.0841
        },
        {
            128,
            10.765,
            0.1682
        },
        {
            192,
            21.53,
            0.3364
        },
        {
            256,
            43.06,
            1.34
        }
    };

    /** Measured pitch modulation depths in cents (+/-), interpolated linearly. */
    private static final double [] []   PITCH_DEPTHS       =
    {
        {
            0,
            0
        },
        {
            10,
            2.7
        },
        {
            20,
            4.3
        },
        {
            30,
            8
        },
        {
            40,
            11
        },
        {
            50,
            19
        },
        {
            60,
            30
        },
        {
            70,
            47
        },
        {
            80,
            72
        },
        {
            90,
            109
        },
        {
            100,
            173
        },
        {
            110,
            282
        },
        {
            120,
            397
        },
        {
            127,
            570
        }
    };

    /** Measured level dips of the amplitude modulation in dB, interpolated linearly. */
    private static final double [] []   AMPLITUDE_DEPTHS   =
    {
        {
            0,
            0
        },
        {
            10,
            1.5
        },
        {
            20,
            3.3
        },
        {
            30,
            6.2
        },
        {
            40,
            8.6
        },
        {
            50,
            9.8
        },
        {
            60,
            12.7
        },
        {
            70,
            13.8
        },
        {
            80,
            18.3
        },
        {
            90,
            22.1
        },
        {
            100,
            27.1
        },
        {
            110,
            31.2
        },
        {
            120,
            39.3
        },
        {
            127,
            44.7
        }
    };

    /** Estimated filter cutoff modulation depths in cents (+/-), interpolated linearly. */
    private static final double [] []   FILTER_DEPTHS      =
    {
        {
            0,
            0
        },
        {
            40,
            600
        },
        {
            60,
            885
        },
        {
            80,
            1280
        },
        {
            100,
            1600
        },
        {
            120,
            1850
        },
        {
            127,
            1900
        }
    };

    /**
     * The wave forms in the order of the Part LFO Wave parameter: Triangle, Triangle+, Saw Up, Saw
     * Down, Squ1/4, Squ1/3, Square, Squ2/3, Squ3/4, Trapezoid, S/H1, S/H2, User.
     */
    private static final LfoWaveform [] PART_WAVEFORMS     =
    {
        LfoWaveform.TRIANGLE,
        LfoWaveform.TRIANGLE,
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.SAWTOOTH_DOWN,
        LfoWaveform.SQUARE,
        LfoWaveform.SQUARE,
        LfoWaveform.SQUARE,
        LfoWaveform.SQUARE,
        LfoWaveform.SQUARE,
        LfoWaveform.TRIANGLE,
        LfoWaveform.RANDOM,
        LfoWaveform.RANDOM,
        LfoWaveform.TRIANGLE
    };

    /** The phases of the Part LFO Phase parameter in degrees. */
    private static final int []         PART_PHASES        =
    {
        0,
        90,
        120,
        180,
        240,
        270
    };

    /**
     * The Part LFO runs at the same curve as the normal speed of the Element LFO but is slower by
     * this factor (measured at speed 10, 41 and 63: 0.122Hz, 1.79Hz and 12.0Hz).
     */
    private static final double         PART_SPEED_FACTOR  = 0.286;

    /**
     * Measured depths of the Part LFO relative to the depth at 127, interpolated logarithmically
     * (linearly from 0 to the first measured value). The depth curve is very steep: pitch
     * modulation is +/-10 cents at 32, +/-113 cents at 64 and +/-2400 cents at 127.
     */
    private static final double [] []   PART_DEPTHS        =
    {
        {
            0,
            0
        },
        {
            32,
            10.0 / 2400.0
        },
        {
            64,
            113.0 / 2400.0
        },
        {
            127,
            1
        }
    };

    /** The pitch modulation of the Part LFO at depth 127 in cents (+/-). */
    public static final double          PART_PITCH_RANGE   = 2400;
    /** The level dip of the Part LFO at depth 127 in dB. */
    public static final double          PART_LEVEL_RANGE   = 32;
    /**
     * The cutoff modulation of the Part LFO at depth 127 in cents (+/-). Not measured, assumed to
     * be identical to the pitch modulation.
     */
    public static final double          PART_CUTOFF_RANGE  = 2400;

    /** The highest value of the normal LFO speed. */
    public static final int             MAX_NORMAL_SPEED   = 63;
    /** The highest value of the extended LFO speed. */
    public static final int             MAX_EXTENDED_SPEED = 415;


    /**
     * Constant class.
     */
    private YamahaYsfcLfo ()
    {
        // Intentionally empty
    }


    /**
     * Get the wave form of the LFO Wave parameter.
     *
     * @param value The value: 0 = Saw, 1 = Triangle, 2 = Square
     * @return The wave form
     */
    public static LfoWaveform getWaveform (final int value)
    {
        return value >= 0 && value < WAVEFORMS.length ? WAVEFORMS[value] : LfoWaveform.TRIANGLE;
    }


    /**
     * Get the LFO Wave parameter of a wave form. Sine and random are written as a triangle.
     *
     * @param waveform The wave form
     * @return The value: 0 = Saw, 1 = Triangle, 2 = Square
     */
    public static int getWaveformValue (final LfoWaveform waveform)
    {
        return switch (waveform)
        {
            case SAWTOOTH_UP, SAWTOOTH_DOWN -> 0;
            case SQUARE -> 2;
            default -> 1;
        };
    }


    /**
     * Convert the normal LFO speed to Hertz.
     *
     * @param speed The speed in the range of 0-63
     * @return The rate in Hertz
     */
    public static double normalSpeedToHertz (final int speed)
    {
        final double value = Math.clamp (speed, 0, MAX_NORMAL_SPEED);
        for (int i = 1; i < NORMAL_SPEEDS.length; i++)
            if (value <= NORMAL_SPEEDS[i][0])
            {
                final double [] lower = NORMAL_SPEEDS[i - 1];
                final double [] upper = NORMAL_SPEEDS[i];
                final double ratio = (value - lower[0]) / (upper[0] - lower[0]);
                return lower[1] * Math.pow (upper[1] / lower[1], ratio);
            }
        return NORMAL_SPEEDS[NORMAL_SPEEDS.length - 1][1];
    }


    /**
     * Convert a rate in Hertz to the closest normal LFO speed.
     *
     * @param hertz The rate in Hertz
     * @return The speed in the range of 0-63
     */
    public static int hertzToNormalSpeed (final double hertz)
    {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int speed = 0; speed <= MAX_NORMAL_SPEED; speed++)
        {
            final double distance = Math.abs (Math.log (normalSpeedToHertz (speed) / Math.max (hertz, 0.001)));
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = speed;
            }
        }
        return best;
    }


    /**
     * Convert the Part LFO speed to Hertz.
     *
     * @param speed The speed in the range of 0-63
     * @return The rate in Hertz
     */
    public static double partSpeedToHertz (final int speed)
    {
        return normalSpeedToHertz (speed) * PART_SPEED_FACTOR;
    }


    /**
     * Convert a rate in Hertz to the closest Part LFO speed.
     *
     * @param hertz The rate in Hertz
     * @return The speed in the range of 0-63
     */
    public static int hertzToPartSpeed (final double hertz)
    {
        return hertzToNormalSpeed (hertz / PART_SPEED_FACTOR);
    }


    /**
     * Get the highest rate of the Part LFO.
     *
     * @return The rate in Hertz
     */
    public static double getMaxPartRate ()
    {
        return partSpeedToHertz (MAX_NORMAL_SPEED);
    }


    /**
     * Get the wave form of the Part LFO Wave parameter.
     *
     * @param value The value, see {@link YamahaYsfcPerformancePart#getLfoWave()}
     * @return The wave form
     */
    public static LfoWaveform getPartWaveform (final int value)
    {
        return value >= 0 && value < PART_WAVEFORMS.length ? PART_WAVEFORMS[value] : LfoWaveform.TRIANGLE;
    }


    /**
     * Get the Part LFO Wave parameter of a wave form. Sine is written as a triangle.
     *
     * @param waveform The wave form
     * @return The value, see {@link YamahaYsfcPerformancePart#getLfoWave()}
     */
    public static int getPartWaveformValue (final LfoWaveform waveform)
    {
        return switch (waveform)
        {
            case SAWTOOTH_UP -> 2;
            case SAWTOOTH_DOWN -> 3;
            case SQUARE -> 6;
            case RANDOM -> 10;
            default -> 0;
        };
    }


    /**
     * Get the start phase of the Part LFO Phase parameter.
     *
     * @param value The value: 0 = 0°, 1 = 90°, 2 = 120°, 3 = 180°, 4 = 240°, 5 = 270°
     * @return The start phase in the range of [0..1]
     */
    public static double getPartPhase (final int value)
    {
        return value >= 0 && value < PART_PHASES.length ? PART_PHASES[value] / 360.0 : 0;
    }


    /**
     * Get the Part LFO Phase parameter which is the closest to a start phase.
     *
     * @param phase The start phase in the range of [0..1]
     * @return The value: 0 = 0°, 1 = 90°, 2 = 120°, 3 = 180°, 4 = 240°, 5 = 270°
     */
    public static int getPartPhaseValue (final double phase)
    {
        final double degrees = Math.clamp (phase, 0, 1) * 360.0;
        int best = 0;
        for (int i = 1; i < PART_PHASES.length; i++)
            if (Math.abs (PART_PHASES[i] - degrees) < Math.abs (PART_PHASES[best] - degrees))
                best = i;
        return best;
    }


    /**
     * Convert a Part LFO depth to the modulation relative to the modulation at depth 127.
     *
     * @param depth The depth in the range of 0-127
     * @return The relative modulation in the range of [0..1]
     */
    public static double partDepthToFactor (final int depth)
    {
        final double value = Math.clamp (depth, 0, 127);
        for (int i = 1; i < PART_DEPTHS.length; i++)
            if (value <= PART_DEPTHS[i][0])
            {
                final double [] lower = PART_DEPTHS[i - 1];
                final double [] upper = PART_DEPTHS[i];
                final double ratio = (value - lower[0]) / (upper[0] - lower[0]);
                if (lower[1] <= 0)
                    return upper[1] * ratio;
                return lower[1] * Math.pow (upper[1] / lower[1], ratio);
            }
        return 1;
    }


    /**
     * Convert a modulation relative to the modulation at depth 127 to the closest Part LFO depth.
     *
     * @param factor The relative modulation in the range of [0..1]
     * @return The depth in the range of 0-127
     */
    public static int factorToPartDepth (final double factor)
    {
        final double value = Math.abs (factor);
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int depth = 0; depth <= 127; depth++)
        {
            final double distance = Math.abs (partDepthToFactor (depth) - value);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = depth;
            }
        }
        return best;
    }


    /**
     * Convert the extended LFO speed to Hertz.
     *
     * @param speed The speed in the range of 0-415
     * @return The rate in Hertz
     */
    public static double extendedSpeedToHertz (final int speed)
    {
        final int value = Math.clamp (speed, 0, MAX_EXTENDED_SPEED);
        for (int i = EXTENDED_SPEEDS.length - 1; i >= 0; i--)
        {
            final double [] segment = EXTENDED_SPEEDS[i];
            if (value >= segment[0])
                return segment[1] + (value - segment[0]) * segment[2];
        }
        return 0;
    }


    /**
     * Convert a rate in Hertz to the extended LFO speed.
     *
     * @param hertz The rate in Hertz
     * @return The speed in the range of 0-415
     */
    public static int hertzToExtendedSpeed (final double hertz)
    {
        for (int i = EXTENDED_SPEEDS.length - 1; i >= 0; i--)
        {
            final double [] segment = EXTENDED_SPEEDS[i];
            if (hertz >= segment[1])
                return Math.clamp (Math.round (segment[0] + (hertz - segment[1]) / segment[2]), 0, MAX_EXTENDED_SPEED);
        }
        return 0;
    }


    /**
     * Convert the LFO pitch modulation depth to cents.
     *
     * @param depth The depth in the range of 0-127
     * @return The modulation depth in cents (+/-)
     */
    public static double pitchDepthToCents (final int depth)
    {
        return interpolate (PITCH_DEPTHS, depth);
    }


    /**
     * Convert a modulation depth in cents to the LFO pitch modulation depth.
     *
     * @param cents The modulation depth in cents (+/-)
     * @return The depth in the range of 0-127
     */
    public static int centsToPitchDepth (final double cents)
    {
        return inverseInterpolate (PITCH_DEPTHS, cents);
    }


    /**
     * Convert the LFO amplitude modulation depth to the level dip in dB.
     *
     * @param depth The depth in the range of 0-127
     * @return The level dip in dB
     */
    public static double amplitudeDepthToDb (final int depth)
    {
        return interpolate (AMPLITUDE_DEPTHS, depth);
    }


    /**
     * Convert a level dip in dB to the LFO amplitude modulation depth.
     *
     * @param db The level dip in dB
     * @return The depth in the range of 0-127
     */
    public static int dbToAmplitudeDepth (final double db)
    {
        return inverseInterpolate (AMPLITUDE_DEPTHS, db);
    }


    /**
     * Convert the LFO filter modulation depth to cents.
     *
     * @param depth The depth in the range of 0-127
     * @return The modulation depth of the cutoff in cents (+/-)
     */
    public static double filterDepthToCents (final int depth)
    {
        return interpolate (FILTER_DEPTHS, depth);
    }


    /**
     * Convert a modulation depth of the cutoff in cents to the LFO filter modulation depth.
     *
     * @param cents The modulation depth in cents (+/-)
     * @return The depth in the range of 0-127
     */
    public static int centsToFilterDepth (final double cents)
    {
        return inverseInterpolate (FILTER_DEPTHS, cents);
    }


    /**
     * Interpolate linearly between the rows of a table.
     *
     * @param table The table, each row contains a parameter value and the related result
     * @param value The parameter value
     * @return The interpolated result
     */
    private static double interpolate (final double [] [] table, final int value)
    {
        final double v = Math.clamp (value, table[0][0], table[table.length - 1][0]);
        for (int i = 1; i < table.length; i++)
            if (v <= table[i][0])
            {
                final double [] lower = table[i - 1];
                final double [] upper = table[i];
                return lower[1] + (v - lower[0]) / (upper[0] - lower[0]) * (upper[1] - lower[1]);
            }
        return table[table.length - 1][1];
    }


    /**
     * The inverse of {@link #interpolate(double[][], int)} for a monotonic table.
     *
     * @param table The table, each row contains a parameter value and the related result
     * @param result The result
     * @return The parameter value, rounded and clamped to the range of the table
     */
    private static int inverseInterpolate (final double [] [] table, final double result)
    {
        final double r = Math.abs (result);
        for (int i = 1; i < table.length; i++)
            if (r <= table[i][1])
            {
                final double [] lower = table[i - 1];
                final double [] upper = table[i];
                return (int) Math.round (lower[0] + (r - lower[1]) / (upper[1] - lower[1]) * (upper[0] - lower[0]));
            }
        return (int) table[table.length - 1][0];
    }
}
