// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;


/**
 * Constants and conversion helpers for the 3 low frequency oscillators of the Ableton Sampler. LFO
 * 1 has fixed destinations (volume, panorama, pitch, filter), LFO 2 and LFO 3 each have 2
 * configurable destinations (A and B) which use the same destination numbers as the modulation
 * matrix of the MIDI tab.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonLfo
{
    /** The modulation destination of the pitch. */
    public static final int             DESTINATION_PITCH            = 6;
    /** The modulation destination of the filter cutoff. */
    public static final int             DESTINATION_FILTER_FREQUENCY = 12;
    /** The modulation destination of the volume. */
    public static final int             DESTINATION_VOLUME           = 18;
    /** The modulation destination of the volume amount of LFO 1. */
    public static final int             DESTINATION_LFO1_VOLUME      = 22;
    /** The modulation destination of the filter amount of LFO 1. */
    public static final int             DESTINATION_LFO1_FILTER      = 24;
    /** The modulation destination of the pitch amount of LFO 1. */
    public static final int             DESTINATION_LFO1_PITCH       = 25;
    /** The modulation destination of the amount A of LFO 2. */
    public static final int             DESTINATION_LFO2_AMOUNT_A    = 27;
    /** The modulation destination of the amount B of LFO 2. */
    public static final int             DESTINATION_LFO2_AMOUNT_B    = 28;
    /** The modulation destination of the amount A of LFO 3. */
    public static final int             DESTINATION_LFO3_AMOUNT_A    = 30;
    /** The modulation destination of the amount B of LFO 3. */
    public static final int             DESTINATION_LFO3_AMOUNT_B    = 31;

    /** An amount of 100% (volume amount of LFO 1 or the volume destination) covers 96dB. */
    public static final double          VOLUME_SCALE                 = 1.0;
    /** An amount of 100% (pitch amount of LFO 1 or the pitch destination) is +/-12 semi-tones. */
    public static final double          PITCH_SCALE                  = 1200.0 / IEnvelope.MAX_ENVELOPE_DEPTH;
    /** The filter amount of LFO 1 is in semi-tones. */
    public static final double          FILTER_SEMITONE_SCALE        = 100.0 / IEnvelope.MAX_ENVELOPE_DEPTH;
    /** An amount of 100% of the cutoff destination covers the full range of 30Hz to 22kHz. */
    public static final double          CUTOFF_SCALE                 = 1200.0 * MathUtils.log2 (22000.0 / 30.0) / IEnvelope.MAX_ENVELOPE_DEPTH;

    private static final double         ASSUMED_TEMPO                = 120.0;

    // Sine, Square, Triangle, Saw Down, Saw Up, Random
    private static final LfoWaveform [] WAVEFORMS                    =
    {
        LfoWaveform.SINE,
        LfoWaveform.SQUARE,
        LfoWaveform.TRIANGLE,
        LfoWaveform.SAWTOOTH_DOWN,
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.RANDOM
    };

    // The length of one cycle of the tempo synchronized rates in quarter notes: 1/64, 1/48, 1/32,
    // 1/24, 1/16, 1/12, 1/8, 1/6, 3/16, 1/4, 5/16, 1/3, 3/8, 1/2, 3/4, 1, 1.5, 2, 3, 4, 6, 8 bars
    private static final double []      BEAT_RATE_QUARTERS           =
    {
        4.0 / 64.0,
        4.0 / 48.0,
        4.0 / 32.0,
        4.0 / 24.0,
        4.0 / 16.0,
        4.0 / 12.0,
        4.0 / 8.0,
        4.0 / 6.0,
        4.0 * 3.0 / 16.0,
        4.0 / 4.0,
        4.0 * 5.0 / 16.0,
        4.0 / 3.0,
        4.0 * 3.0 / 8.0,
        4.0 / 2.0,
        4.0 * 3.0 / 4.0,
        4.0,
        4.0 * 1.5,
        4.0 * 2.0,
        4.0 * 3.0,
        4.0 * 4.0,
        4.0 * 6.0,
        4.0 * 8.0
    };


    /**
     * Private due to utility class.
     */
    private AbletonLfo ()
    {
        // Intentionally empty
    }


    /**
     * Convert the waveform index to a waveform.
     *
     * @param index The index
     * @return The waveform
     */
    public static LfoWaveform toWaveform (final int index)
    {
        return WAVEFORMS[Math.clamp (index, 0, WAVEFORMS.length - 1)];
    }


    /**
     * Convert a waveform to the waveform index.
     *
     * @param waveform The waveform
     * @return The index
     */
    public static int fromWaveform (final LfoWaveform waveform)
    {
        if (waveform == null)
            return 0;
        return switch (waveform)
        {
            case SINE -> 0;
            case SQUARE -> 1;
            case TRIANGLE -> 2;
            case SAWTOOTH_DOWN -> 3;
            case SAWTOOTH_UP -> 4;
            case RANDOM -> 5;
        };
    }


    /**
     * Convert a tempo synchronized rate to Hertz. Since the tempo is not stored in a preset, a tempo
     * of 120 BPM is assumed.
     *
     * @param index The index of the beat rate
     * @return The rate in Hertz
     */
    public static double beatRateToHertz (final int index)
    {
        final double quarters = BEAT_RATE_QUARTERS[Math.clamp (index, 0, BEAT_RATE_QUARTERS.length - 1)];
        return ASSUMED_TEMPO / 60.0 / quarters;
    }
}
