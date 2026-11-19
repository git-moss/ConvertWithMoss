// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfo;


/**
 * Reads and writes the 3 LFOs of EXS24 and their routings in the modulation matrix to the pitch
 * (vibrato), the volume (tremolo) and the cutoff of filter 1.
 *
 * @author Jürgen Moßgraber
 */
public class EXSLfo
{
    private static final int []         SOURCES          =
    {
        EXSModulator.SOURCE_LFO1,
        EXSModulator.SOURCE_LFO2,
        EXSModulator.SOURCE_LFO3
    };

    private static final int []         RATE_IDS         =
    {
        EXS24Parameters.LFO_1_RATE,
        EXS24Parameters.LFO_2_RATE,
        EXS24Parameters.LFO_3_RATE
    };

    private static final int []         WAVE_IDS         =
    {
        EXS24Parameters.LFO_1_WAVE_SHAPE,
        EXS24Parameters.LFO_2_WAVE_SHAPE,
        EXS24Parameters.LFO_3_WAVE_SHAPE
    };

    private static final int []         FADE_IDS         =
    {
        EXS24Parameters.LFO_1_FADE,
        EXS24Parameters.LFO_2_FADE,
        EXS24Parameters.LFO_3_FADE
    };

    /** The rate values which are used if the parameter is not present. */
    private static final int []         DEFAULT_RATES    =
    {
        98,
        34,
        98
    };

    /**
     * The 8 waveforms: triangle, sawtooth, inverted sawtooth, square (switches between a positive
     * value and zero), square (switches between equal positive and negative values), random,
     * smoothed random and sine. Larger values repeat the list.
     */
    private static final LfoWaveform [] WAVEFORMS        =
    {
        LfoWaveform.TRIANGLE,
        LfoWaveform.SAWTOOTH_DOWN,
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.SQUARE,
        LfoWaveform.SQUARE,
        LfoWaveform.RANDOM,
        LfoWaveform.RANDOM,
        LfoWaveform.SINE
    };

    /** The value of the waveform which only uses the positive half of the range. */
    private static final int            WAVE_POS_SQUARE  = 3;

    /**
     * The rate values 0-33 are synchronized to the tempo. These are the lengths of the note values
     * in whole notes: 1/128t, 1/64, 1/64t, 1/32, 1/32t, 3/64, 1/16, 1/12, 3/32, 1/8, 1/6, 3/16, 1/4,
     * 1/3, 3/8, 1/2, 2/3, 3/4, 1, 4/3, 3/2, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 28, 32.
     */
    private static final double []      SYNC_LENGTHS     =
    {
        1.0 / 192.0,
        1.0 / 64.0,
        1.0 / 96.0,
        1.0 / 32.0,
        1.0 / 48.0,
        3.0 / 64.0,
        1.0 / 16.0,
        1.0 / 12.0,
        3.0 / 32.0,
        1.0 / 8.0,
        1.0 / 6.0,
        3.0 / 16.0,
        1.0 / 4.0,
        1.0 / 3.0,
        3.0 / 8.0,
        1.0 / 2.0,
        2.0 / 3.0,
        3.0 / 4.0,
        1.0,
        4.0 / 3.0,
        3.0 / 2.0,
        2.0,
        3.0,
        4.0,
        5.0,
        6.0,
        8.0,
        10.0,
        12.0,
        16.0,
        20.0,
        24.0,
        28.0,
        32.0
    };

    /** The rate value which outputs a constant (DC) value instead of an oscillation. */
    private static final int            RATE_DC          = 34;

    /** Rate values (35-127) of the free running range and the related frequencies in Hertz. */
    private static final double []      RATE_VALUES      =
    {
        35,
        48,
        64,
        80,
        98,
        110,
        118,
        127
    };

    private static final double []      RATE_FREQUENCIES =
    {
        0.12,
        0.27,
        0.68,
        1.72,
        4.8,
        9.7,
        15.5,
        35.0
    };

    /** Negative rate values are invalid and result in the default rate of 4.8Hz. */
    private static final double         INVALID_RATE     = 4.8;

    /** The tempo which is used to convert the tempo synchronized rates to Hertz. */
    private static final double         TEMPO            = 120.0;

    /** The maximum fade-in time in milliseconds. */
    private static final int            MAX_FADE         = 10000;

    /** The destinations to read and write: pitch, volume and filter 1 cutoff. */
    private static final int []         DESTINATIONS     =
    {
        EXSModulator.DESTINATION_PITCH,
        EXSModulator.DESTINATION_VOLUME,
        EXSModulator.DESTINATION_FILTER_1_CUTOFF
    };


    /**
     * Constructor. Private due to helper class.
     */
    private EXSLfo ()
    {
        // Intentionally empty
    }


    /**
     * Read the LFO routings to the pitch, the volume and the cutoff of filter 1 from the modulation
     * matrix and apply them to all zones of the multi-sample. The filter must already be set.
     *
     * @param multisampleSource The multi-sample to which to apply the LFOs
     * @param exs24File The EXS24 file which contains the parameters and the modulation matrix
     */
    public static void applyLfos (final IMultisampleSource multisampleSource, final EXS24File exs24File)
    {
        final EXS24Parameters parameters = exs24File.getParameters ();
        for (final int destination: DESTINATIONS)
        {
            final Optional<EXSModulator> modulatorOpt = findLfoModulator (exs24File.getModulators (), destination);
            if (modulatorOpt.isEmpty ())
                continue;
            final EXSModulator modulator = modulatorOpt.get ();
            final int lfoIndex = getLfoIndex (modulator.source);
            final Optional<ILfo> lfoOpt = readLfo (parameters, lfoIndex);
            if (lfoOpt.isEmpty ())
                continue;

            // The low value is the amount if the via source is at its minimum, the high value if
            // it is at its maximum
            final Integer wave = parameters.get (WAVE_IDS[lfoIndex]);
            final double polarity = wave != null && wave.intValue () % WAVEFORMS.length == WAVE_POS_SQUARE ? 0.5 : 1.0;
            final double depth = polarity * amountToDepth (destination, modulator.lowValue);
            final double modWheelDepth = modulator.via == EXSModulator.VIA_MOD_WHEEL ? polarity * amountToDepth (destination, modulator.highValue) - depth : 0;
            if (depth == 0 && modWheelDepth == 0)
                continue;

            for (final IGroup group: multisampleSource.getGroups ())
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    final ILfoModulator lfoModulator = getLfoModulator (zone, destination);
                    if (lfoModulator == null)
                        continue;
                    lfoModulator.setDepth (Math.clamp (depth, -1, 1));
                    lfoModulator.getDepthModWheelModulator ().setDepth (Math.clamp (modWheelDepth, -1, 1));
                    lfoModulator.setSource (copyLfo (lfoOpt.get ()));
                }
        }
    }


    /**
     * Write the LFO modulations of the given zone to the LFOs and the modulation matrix. Up to 3
     * different LFOs are supported, the LFO routings which require more are dropped.
     *
     * @param exs24File The EXS24 file to which to add the parameters and modulators
     * @param zone The zone from which to take the LFO modulations
     */
    public static void writeLfos (final EXS24File exs24File, final ISampleZone zone)
    {
        final List<ILfo> lfos = new ArrayList<> ();
        for (final int destination: DESTINATIONS)
        {
            final ILfoModulator lfoModulator = getLfoModulator (zone, destination);
            if (lfoModulator == null || !lfoModulator.isActive ())
                continue;
            final ILfo lfo = lfoModulator.getSource ();
            if (lfo == null || !lfo.isSet ())
                continue;

            int lfoIndex = indexOfEqualLfo (lfos, lfo);
            if (lfoIndex < 0)
            {
                if (lfos.size () >= SOURCES.length)
                    continue;
                lfoIndex = lfos.size ();
                lfos.add (lfo);
                writeLfo (exs24File, lfoIndex, lfo);
            }

            final double depth = lfoModulator.getDepth ();
            final double modWheelDepth = lfoModulator.getDepthModWheelModulator ().getDepth ();
            final EXSModulator modulator = new EXSModulator (SOURCES[lfoIndex], destination);
            modulator.lowValue = depthToAmount (destination, depth);
            if (modWheelDepth == 0)
                modulator.highValue = modulator.lowValue;
            else
            {
                modulator.highValue = depthToAmount (destination, depth + modWheelDepth);
                modulator.via = EXSModulator.VIA_MOD_WHEEL;
            }
            exs24File.addModulator (modulator);
        }
    }


    /**
     * Convert a rate value to a frequency.
     *
     * @param rate The rate value, 0-33 are synchronized to the tempo, 34 is DC, 35-127 are free
     *            running
     * @return The frequency in Hertz, 0 for DC
     */
    static double rateToFrequency (final int rate)
    {
        if (rate < 0)
            return INVALID_RATE;
        if (rate < RATE_DC)
        {
            // Length of the whole note values in seconds at the default tempo
            final double seconds = SYNC_LENGTHS[rate] * 4.0 * 60.0 / TEMPO;
            return 1.0 / seconds;
        }
        if (rate == RATE_DC)
            return 0;

        final double value = Math.min (rate, RATE_VALUES[RATE_VALUES.length - 1]);
        for (int i = 0; i < RATE_VALUES.length - 1; i++)
            if (value <= RATE_VALUES[i + 1])
            {
                final double t = (value - RATE_VALUES[i]) / (RATE_VALUES[i + 1] - RATE_VALUES[i]);
                return Math.exp (Math.log (RATE_FREQUENCIES[i]) + t * (Math.log (RATE_FREQUENCIES[i + 1]) - Math.log (RATE_FREQUENCIES[i])));
            }
        return RATE_FREQUENCIES[RATE_FREQUENCIES.length - 1];
    }


    /**
     * Convert a frequency to a rate value of the free running range.
     *
     * @param frequency The frequency in Hertz
     * @return The rate value in the range of 35-127
     */
    static int frequencyToRate (final double frequency)
    {
        final double logFrequency = Math.log (Math.clamp (frequency, RATE_FREQUENCIES[0], RATE_FREQUENCIES[RATE_FREQUENCIES.length - 1]));
        for (int i = 0; i < RATE_FREQUENCIES.length - 1; i++)
        {
            final double logLow = Math.log (RATE_FREQUENCIES[i]);
            final double logHigh = Math.log (RATE_FREQUENCIES[i + 1]);
            if (logFrequency <= logHigh)
            {
                final double t = (logFrequency - logLow) / (logHigh - logLow);
                return (int) Math.round (RATE_VALUES[i] + t * (RATE_VALUES[i + 1] - RATE_VALUES[i]));
            }
        }
        return (int) RATE_VALUES[RATE_VALUES.length - 1];
    }


    /**
     * Read the settings of one LFO.
     *
     * @param parameters The parameters
     * @param lfoIndex The index of the LFO (0-2)
     * @return The LFO or empty if it does not oscillate (DC)
     */
    private static Optional<ILfo> readLfo (final EXS24Parameters parameters, final int lfoIndex)
    {
        final Integer rate = parameters.get (RATE_IDS[lfoIndex]);
        final double frequency = rateToFrequency (rate == null ? DEFAULT_RATES[lfoIndex] : rate.intValue ());
        if (frequency <= 0)
            return Optional.empty ();

        final ILfo lfo = new DefaultLfo ();
        lfo.setRate (frequency);
        final Integer wave = parameters.get (WAVE_IDS[lfoIndex]);
        lfo.setWaveform (WAVEFORMS[Math.floorMod (wave == null ? 0 : wave.intValue (), WAVEFORMS.length)]);

        // Positive values are the fade-in time in milliseconds, negative values fade the
        // modulation out (10000 + value in milliseconds) which is not supported
        final Integer fade = parameters.get (FADE_IDS[lfoIndex]);
        if (fade != null && fade.intValue () > 0)
            lfo.setFadeIn (Math.min (fade.intValue (), MAX_FADE) / 1000.0);

        // LFO 1 is polyphonic and restarts with each note, LFO 2 and 3 are running freely
        lfo.setKeySync (lfoIndex == 0);
        return Optional.of (lfo);
    }


    /**
     * Write the settings of one LFO.
     *
     * @param exs24File The EXS24 file to which to add the parameters
     * @param lfoIndex The index of the LFO (0-2)
     * @param lfo The LFO to write
     */
    private static void writeLfo (final EXS24File exs24File, final int lfoIndex, final ILfo lfo)
    {
        exs24File.addParameter (RATE_IDS[lfoIndex], frequencyToRate (lfo.getRate ()));
        final int wave = switch (lfo.getWaveform ())
        {
            case SAWTOOTH_DOWN -> 1;
            case SAWTOOTH_UP -> 2;
            case SQUARE -> 4;
            case RANDOM -> 5;
            case SINE -> 7;
            default -> 0;
        };
        exs24File.addParameter (WAVE_IDS[lfoIndex], wave);

        // There is only a fade-in, therefore the delay is used if there is no fade-in time
        final double fadeIn = lfo.getFadeIn () > 0 ? lfo.getFadeIn () : lfo.getDelay ();
        exs24File.addParameter (FADE_IDS[lfoIndex], (int) Math.round (Math.clamp (fadeIn * 1000.0, 0, MAX_FADE)));
    }


    /**
     * Convert the amount of a modulator to the depth of the model.
     *
     * @param destination The destination of the modulator
     * @param amount The amount, -1000..1000
     * @return The depth, -1..1
     */
    private static double amountToDepth (final int destination, final int amount)
    {
        // The pitch amount is in cents (+/-1200 at the maximum), see EXSPitchModulationCurve
        if (destination == EXSModulator.DESTINATION_PITCH)
            return EXSPitchModulationCurve.map (amount) / IEnvelope.MAX_ENVELOPE_DEPTH;
        // Volume and cutoff are in percent of the full range: 1000 = 100%
        return amount / 1000.0;
    }


    /**
     * Convert the depth of the model to the amount of a modulator.
     *
     * @param destination The destination of the modulator
     * @param depth The depth, -1..1
     * @return The amount, -1000..1000
     */
    private static int depthToAmount (final int destination, final double depth)
    {
        if (destination == EXSModulator.DESTINATION_PITCH)
            return (int) Math.round (EXSPitchModulationCurve.unmap (depth * IEnvelope.MAX_ENVELOPE_DEPTH));
        return (int) Math.round (Math.clamp (depth, -1, 1) * 1000.0);
    }


    /**
     * Find the first modulator which routes one of the LFOs to the given destination.
     *
     * @param modulators The modulators of the modulation matrix
     * @param destination The destination
     * @return The modulator if found
     */
    private static Optional<EXSModulator> findLfoModulator (final List<EXSModulator> modulators, final int destination)
    {
        for (final EXSModulator modulator: modulators)
            if (modulator.destination == destination && getLfoIndex (modulator.source) >= 0)
                return Optional.of (modulator);
        return Optional.empty ();
    }


    /**
     * Get the index of the LFO of a modulation source.
     *
     * @param source The modulation source
     * @return The index of the LFO (0-2) or -1 if the source is not an LFO
     */
    private static int getLfoIndex (final int source)
    {
        for (int i = 0; i < SOURCES.length; i++)
            if (SOURCES[i] == source)
                return i;
        return -1;
    }


    /**
     * Get the LFO modulator of a zone for the given destination.
     *
     * @param zone The zone
     * @param destination The destination
     * @return The modulator or null if the zone has no filter for the cutoff destination
     */
    private static ILfoModulator getLfoModulator (final ISampleZone zone, final int destination)
    {
        if (destination == EXSModulator.DESTINATION_PITCH)
            return zone.getPitchLfoModulator ();
        if (destination == EXSModulator.DESTINATION_VOLUME)
            return zone.getAmplitudeLfoModulator ();
        final Optional<IFilter> filter = zone.getFilter ();
        return filter.isPresent () ? filter.get ().getCutoffLfoModulator () : null;
    }


    /**
     * Find an LFO with the same settings in the list.
     *
     * @param lfos The LFOs
     * @param lfo The LFO to look up
     * @return The index of the LFO with the same settings or -1 if not found
     */
    private static int indexOfEqualLfo (final List<ILfo> lfos, final ILfo lfo)
    {
        for (int i = 0; i < lfos.size (); i++)
        {
            final ILfo other = lfos.get (i);
            if (other.getWaveform () == lfo.getWaveform () && frequencyToRate (other.getRate ()) == frequencyToRate (lfo.getRate ()) && other.getFadeIn () == lfo.getFadeIn () && other.getDelay () == lfo.getDelay ())
                return i;
        }
        return -1;
    }


    /**
     * Create a copy of an LFO, each modulator gets its own instance.
     *
     * @param lfo The LFO to copy
     * @return The copy
     */
    private static ILfo copyLfo (final ILfo lfo)
    {
        final ILfo copy = new DefaultLfo ();
        copy.setWaveform (lfo.getWaveform ());
        copy.setRate (lfo.getRate ());
        copy.setFadeIn (lfo.getFadeIn ());
        copy.setKeySync (lfo.isKeySync ());
        return copy;
    }
}
