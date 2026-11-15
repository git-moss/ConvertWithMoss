// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;


/**
 * Conversion helpers for the low frequency oscillator of the 1010music multi-sample engine.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010Lfo
{
    /** The modulation source of the 1st low frequency oscillator. */
    public static final String          SOURCE_LFO1        = "lfo1";
    /** The modulation source of the modulation wheel. */
    public static final String          SOURCE_MOD_WHEEL   = "modwheel";
    /** The modulation destination of the volume. */
    public static final String          DESTINATION_GAIN   = "gaindb";
    /** The modulation destination of the pitch. */
    public static final String          DESTINATION_PITCH  = "pitch";
    /** The modulation destination of the filter cutoff. */
    public static final String          DESTINATION_CUTOFF = "dualfilcutoff";
    /** The modulation destination of the depth of the low frequency oscillator. */
    public static final String          DESTINATION_LFO    = "lfoamount";

    /** Modulating the pitch by 100% moves it by +/-12 semi-tones. */
    public static final double          PITCH_SCALE        = 1200.0 / IEnvelope.MAX_ENVELOPE_DEPTH;
    /** Modulating the volume by 100% covers the range of -96dB to 0dB. */
    public static final double          VOLUME_SCALE       = 96.0 / ILfoModulator.MAX_VOLUME_DEPTH;
    /** Modulating the cutoff by 100% opens/closes the filter completely. */
    public static final double          CUTOFF_SCALE       = 1200.0 * MathUtils.log2 (IFilter.MAX_FREQUENCY) / IEnvelope.MAX_ENVELOPE_DEPTH;

    private static final double         MIN_RATE           = 0.1;
    private static final double         OCTAVES            = 7.0;
    private static final double         ASSUMED_TEMPO      = 120.0;

    // Sine, Pos Sine, Triangle, Pos Tri, Square, Pos Square, Saw, Rev Saw, Random; the positive
    // (uni-polar) variants are mapped to their bi-polar counterparts
    private static final LfoWaveform [] WAVEFORMS          =
    {
        LfoWaveform.SINE,
        LfoWaveform.SINE,
        LfoWaveform.TRIANGLE,
        LfoWaveform.TRIANGLE,
        LfoWaveform.SQUARE,
        LfoWaveform.SQUARE,
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.SAWTOOTH_DOWN,
        LfoWaveform.RANDOM
    };

    // The length of one cycle of the tempo synchronized rates in quarter notes: 8 bars, 4 bars, 2
    // bars, 1 bar, 1/2, 1/2T, 1/4, 1/4T, 1/8, 1/8T, 1/16, 1/16T, 1/32, 1/32T, 1/64
    private static final double []      BEAT_SYNC_QUARTERS =
    {
        32,
        16,
        8,
        4,
        2,
        4.0 / 3.0,
        1,
        2.0 / 3.0,
        0.5,
        1.0 / 3.0,
        0.25,
        0.5 / 3.0,
        0.125,
        0.25 / 3.0,
        0.0625
    };


    /**
     * Private due to utility class.
     */
    private Music1010Lfo ()
    {
        // Intentionally empty
    }


    /**
     * Convert the rate parameter value to Hertz. The device maps the range exponentially from 0.1
     * Hertz to 12.8 Hertz.
     *
     * @param value The value in the range of [0..1000]
     * @return The rate in Hertz
     */
    public static double rateToHertz (final int value)
    {
        return MIN_RATE * Math.pow (2, OCTAVES * Math.clamp (value, 0, 1000) / 1000.0);
    }


    /**
     * Convert a rate in Hertz to the rate parameter value.
     *
     * @param rate The rate in Hertz
     * @return The value in the range of [0..1000]
     */
    public static int hertzToRate (final double rate)
    {
        if (rate <= MIN_RATE)
            return 0;
        return Math.clamp (Math.round (MathUtils.log2 (rate / MIN_RATE) / OCTAVES * 1000.0), 0, 1000);
    }


    /**
     * Convert a tempo synchronized rate to Hertz. Since the tempo is not stored in a patch, a tempo
     * of 120 BPM is assumed.
     *
     * @param index The index of the note division [0..14]
     * @return The rate in Hertz
     */
    public static double beatSyncRateToHertz (final int index)
    {
        final double quarters = BEAT_SYNC_QUARTERS[Math.clamp (index, 0, BEAT_SYNC_QUARTERS.length - 1)];
        return ASSUMED_TEMPO / 60.0 / quarters;
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
            case TRIANGLE -> 2;
            case SQUARE -> 4;
            case SAWTOOTH_UP -> 6;
            case SAWTOOTH_DOWN -> 7;
            case RANDOM -> 8;
        };
    }
}
