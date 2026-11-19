// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

import java.util.Optional;

import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfo;
import de.mossgrabers.tools.XMLUtils;


/**
 * Conversion helpers for the low frequency oscillator of the 1010music multi-sample engine (Bento
 * and Blackbox) and reading of its modulation matrix routings.
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
    /**
     * Modulating the cutoff by 70% matches a modulation of 1 octave (compared by ear with sfizz).
     */
    public static final double          CUTOFF_SCALE       = 1200.0 / 0.7 / IEnvelope.MAX_ENVELOPE_DEPTH;

    private static final double         MIN_RATE           = 0.1;
    private static final double         OCTAVES            = 7.0;
    private static final double         ASSUMED_TEMPO      = 120.0;

    // Rev Saw, Saw, Triangle, Pos Tri, Sine, Pos Sine, Square, Pos Square, Random; the positive
    // (uni-polar) variants are mapped to their bi-polar counterparts
    private static final LfoWaveform [] WAVEFORMS          =
    {
        LfoWaveform.SAWTOOTH_DOWN,
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.TRIANGLE,
        LfoWaveform.TRIANGLE,
        LfoWaveform.SINE,
        LfoWaveform.SINE,
        LfoWaveform.SQUARE,
        LfoWaveform.SQUARE,
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
     * @param isSawFlipped True if the indices of Saw and Rev Saw are swapped (Blackbox)
     * @return The waveform
     */
    public static LfoWaveform toWaveform (final int index, final boolean isSawFlipped)
    {
        return flipSaw (WAVEFORMS[Math.clamp (index, 0, WAVEFORMS.length - 1)], isSawFlipped);
    }


    /**
     * Convert a waveform to the waveform index.
     *
     * @param waveform The waveform
     * @param isSawFlipped True if the indices of Saw and Rev Saw are swapped (Blackbox)
     * @return The index
     */
    public static int fromWaveform (final LfoWaveform waveform, final boolean isSawFlipped)
    {
        if (waveform == null)
            return 0;
        return switch (flipSaw (waveform, isSawFlipped))
        {
            case SAWTOOTH_DOWN -> 0;
            case SAWTOOTH_UP -> 1;
            case TRIANGLE -> 2;
            case SINE -> 4;
            case SQUARE -> 6;
            case RANDOM -> 8;
        };
    }


    /**
     * The Blackbox uses the same waveforms as the Bento but the indices of Saw and Rev Saw are
     * swapped.
     *
     * @param waveform The waveform
     * @param isSawFlipped True to swap the 2 saw waveforms
     * @return The waveform
     */
    private static LfoWaveform flipSaw (final LfoWaveform waveform, final boolean isSawFlipped)
    {
        if (!isSawFlipped)
            return waveform;
        if (waveform == LfoWaveform.SAWTOOTH_UP)
            return LfoWaveform.SAWTOOTH_DOWN;
        if (waveform == LfoWaveform.SAWTOOTH_DOWN)
            return LfoWaveform.SAWTOOTH_UP;
        return waveform;
    }


    /**
     * Read the low frequency oscillator and its routings to the volume, the pitch and the filter
     * cutoff from the modulation matrix. The multi-sample engine has only 1 oscillator. The
     * effective depth of a routing is the depth of the oscillator multiplied by the amount of the
     * routing. The modulation wheel can increase the depth of the oscillator.
     *
     * @param instParamsElement The parameters element of the instrument
     * @param instElement The instrument element which contains the modulation matrix
     * @param group The group which contains all sample zones
     * @param isSawFlipped True if the indices of Saw and Rev Saw are swapped (Blackbox)
     */
    public static void readLfoModulators (final Element instParamsElement, final Element instElement, final IGroup group, final boolean isSawFlipped)
    {
        double gainAmount = 0;
        double pitchAmount = 0;
        double cutoffAmount = 0;
        double modWheelAmount = 0;
        for (final Element modElement: XMLUtils.getChildElementsByName (instElement, Music1010Tag.MOD_SOURCE))
        {
            final String source = modElement.getAttribute (Music1010Tag.ATTR_MOD_SOURCE);
            final String destination = modElement.getAttribute (Music1010Tag.ATTR_MOD_DESTINATION);
            final double amount = Math.clamp (XMLUtils.getIntegerAttribute (modElement, Music1010Tag.ATTR_MOD_AMOUNT, 0) / 1000.0, -1.0, 1.0);
            if (SOURCE_LFO1.equals (source))
                switch (destination)
                {
                    case DESTINATION_GAIN -> gainAmount = amount;
                    case DESTINATION_PITCH -> pitchAmount = amount;
                    case DESTINATION_CUTOFF -> cutoffAmount = amount;
                    case null, default -> {
                        // Everything stays on the defaults
                    }
                }
            else if (SOURCE_MOD_WHEEL.equals (source) && DESTINATION_LFO.equals (destination))
                modWheelAmount = amount;
        }
        if (gainAmount == 0 && pitchAmount == 0 && cutoffAmount == 0)
            return;

        final double lfoAmount = Math.clamp (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_LFO_AMOUNT, 0) / 1000.0, 0, 1.0);
        if (lfoAmount == 0 && modWheelAmount == 0)
            return;

        final ILfo lfo = new DefaultLfo ();
        lfo.setWaveform (toWaveform (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_LFO_WAVE, 0), isSawFlipped));
        // A tempo synchronized rate is converted with an assumed tempo of 120 BPM since the tempo
        // is not part of a patch
        if (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_LFO_BEAT_SYNC, 0) > 0)
            lfo.setRate (beatSyncRateToHertz (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_LFO_RATE_BEAT_SYNC, 0)));
        else
            lfo.setRate (rateToHertz (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_LFO_RATE, 0)));
        lfo.setKeySync (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_LFO_KEY_TRIGGER, 0) > 0);

        for (final ISampleZone zone: group.getSampleZones ())
        {
            if (gainAmount != 0)
                setLfoModulator (zone.getAmplitudeLfoModulator (), lfo, lfoAmount, modWheelAmount, gainAmount * VOLUME_SCALE);
            if (pitchAmount != 0)
                setLfoModulator (zone.getPitchLfoModulator (), lfo, lfoAmount, modWheelAmount, pitchAmount * PITCH_SCALE);
            if (cutoffAmount != 0)
            {
                final Optional<IFilter> optFilter = zone.getFilter ();
                if (optFilter.isPresent ())
                    setLfoModulator (optFilter.get ().getCutoffLfoModulator (), lfo, lfoAmount, modWheelAmount, cutoffAmount * CUTOFF_SCALE);
            }
        }
    }


    /**
     * Set the oscillator and the depths of a modulator.
     *
     * @param lfoModulator The modulator to fill
     * @param lfo The oscillator
     * @param lfoAmount The depth of the oscillator in the range of [0..1]
     * @param modWheelAmount The amount by which the modulation wheel increases the depth of the
     *            oscillator in the range of [-1..1]
     * @param depth The depth of the routing, already scaled to the range of the modulator
     */
    private static void setLfoModulator (final ILfoModulator lfoModulator, final ILfo lfo, final double lfoAmount, final double modWheelAmount, final double depth)
    {
        lfoModulator.setSource (lfo);
        lfoModulator.setDepth (Math.clamp (lfoAmount * depth, -1.0, 1.0));
        lfoModulator.getDepthModWheelModulator ().setDepth (Math.clamp (modWheelAmount * depth, -1.0, 1.0));
    }
}
