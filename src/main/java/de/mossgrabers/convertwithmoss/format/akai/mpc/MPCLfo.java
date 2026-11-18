// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc;

import java.util.Locale;
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
 * Helper functions to convert the LFO of a MPC key-group. Each key-group has one LFO which
 * modulates the pitch, the amplitude and the filter cutoff with amounts in the range of [0..1]
 * (0..127 on the device). The 'Wheel > LFO' setting of the program sets the share of these amounts
 * which is controlled by the modulation wheel; the rest is always active.
 *
 * @author Jürgen Moßgraber
 */
public class MPCLfo
{
    /** The depth of a pitch amount of 1: 127 steps of which 6 steps are 1 semi-tone. */
    public static final double          PITCH_SCALE    = 127.0 / 6.0 * 100.0 / IEnvelope.MAX_ENVELOPE_DEPTH;
    /** The depth of a volume amount of 1, which goes from silent to full volume. */
    public static final double          VOLUME_SCALE   = 1.0;
    /** The depth of a cutoff amount of 1, which covers the full cutoff range of 140 semi-tones. */
    public static final double          CUTOFF_SCALE   = 140.0 * 100.0 / IEnvelope.MAX_ENVELOPE_DEPTH;

    private static final LfoWaveform [] WAVEFORMS      =
    {
        LfoWaveform.SINE,
        LfoWaveform.TRIANGLE,
        LfoWaveform.RANDOM,
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.SAWTOOTH_DOWN,
        LfoWaveform.SQUARE,
        LfoWaveform.RANDOM
    };

    private static final String []      WAVEFORM_NAMES =
    {
        "Sine",
        "Triangle",
        "S&H",
        "Saw",
        "Saw Down",
        "Square",
        "Noise"
    };

    // The rate curve is not documented, these are values read from the display of the device: the
    // rate parameter in the range of [0..1] and the displayed frequency in Hertz. Values in between
    // are interpolated logarithmically.
    private static final double []      RATE_VALUES    =
    {
        0,
        0.28,
        0.5,
        0.75,
        1.0
    };

    private static final double []      RATE_HERTZ     =
    {
        0.02,
        0.26,
        1.82,
        26.37,
        200.0
    };


    /**
     * Private constructor for utility class.
     */
    private MPCLfo ()
    {
        // Intentionally empty
    }


    /**
     * Convert the rate parameter to a frequency.
     *
     * @param value The rate parameter in the range of [0..1]
     * @return The frequency in Hertz
     */
    public static double rateToHertz (final double value)
    {
        final double v = Math.clamp (value, 0, 1.0);
        int segment = 0;
        while (segment < RATE_VALUES.length - 2 && v > RATE_VALUES[segment + 1])
            segment++;
        final double position = (v - RATE_VALUES[segment]) / (RATE_VALUES[segment + 1] - RATE_VALUES[segment]);
        final double lowLog = Math.log (RATE_HERTZ[segment]);
        return Math.exp (lowLog + position * (Math.log (RATE_HERTZ[segment + 1]) - lowLog));
    }


    /**
     * Convert a frequency to the rate parameter.
     *
     * @param hertz The frequency in Hertz
     * @return The rate parameter in the range of [0..1]
     */
    public static double hertzToRate (final double hertz)
    {
        final double h = Math.clamp (hertz, RATE_HERTZ[0], RATE_HERTZ[RATE_HERTZ.length - 1]);
        int segment = 0;
        while (segment < RATE_HERTZ.length - 2 && h > RATE_HERTZ[segment + 1])
            segment++;
        final double lowLog = Math.log (RATE_HERTZ[segment]);
        final double position = (Math.log (h) - lowLog) / (Math.log (RATE_HERTZ[segment + 1]) - lowLog);
        return RATE_VALUES[segment] + position * (RATE_VALUES[segment + 1] - RATE_VALUES[segment]);
    }


    /**
     * Get the waveform of a waveform index (JSON format).
     *
     * @param index The index
     * @return The waveform, sine if the index is unknown
     */
    public static LfoWaveform toWaveform (final int index)
    {
        return index >= 0 && index < WAVEFORMS.length ? WAVEFORMS[index] : LfoWaveform.SINE;
    }


    /**
     * Get the waveform index (JSON format) of a waveform.
     *
     * @param waveform The waveform
     * @return The index
     */
    public static int fromWaveform (final LfoWaveform waveform)
    {
        return switch (waveform)
        {
            case TRIANGLE -> 1;
            case RANDOM -> 2;
            case SAWTOOTH_UP -> 3;
            case SAWTOOTH_DOWN -> 4;
            case SQUARE -> 5;
            default -> 0;
        };
    }


    /**
     * Get the waveform of a waveform name (XML format). Since only 'Sine' was found in real files,
     * the name is matched tolerantly.
     *
     * @param name The name, might also be an index
     * @return The waveform, sine if the name is unknown
     */
    public static LfoWaveform parseWaveformName (final String name)
    {
        if (name == null || name.isBlank ())
            return LfoWaveform.SINE;
        final String n = name.trim ().toLowerCase (Locale.US);
        try
        {
            return toWaveform (Integer.parseInt (n));
        }
        catch (final NumberFormatException _)
        {
            // Not an index
        }
        if (n.contains ("tri"))
            return LfoWaveform.TRIANGLE;
        if (n.contains ("sq") || n.contains ("pulse"))
            return LfoWaveform.SQUARE;
        if (n.contains ("saw") || n.contains ("ramp"))
            return n.contains ("down") || n.contains ("rev") ? LfoWaveform.SAWTOOTH_DOWN : LfoWaveform.SAWTOOTH_UP;
        if (n.contains ("s&h") || n.contains ("sample") || n.contains ("random") || n.contains ("noise"))
            return LfoWaveform.RANDOM;
        return LfoWaveform.SINE;
    }


    /**
     * Get the name of a waveform (XML format).
     *
     * @param waveform The waveform
     * @return The name
     */
    public static String getWaveformName (final LfoWaveform waveform)
    {
        return WAVEFORM_NAMES[fromWaveform (waveform)];
    }


    /**
     * Create an oscillator.
     *
     * @param waveform The waveform
     * @param rate The rate parameter in the range of [0..1]
     * @param isKeySync True if the oscillator is reset on each note
     * @return The oscillator
     */
    public static ILfo createLfo (final LfoWaveform waveform, final double rate, final boolean isKeySync)
    {
        final ILfo lfo = new DefaultLfo ();
        lfo.setWaveform (waveform);
        lfo.setRate (rateToHertz (rate));
        lfo.setKeySync (isKeySync);
        return lfo;
    }


    /**
     * Set the oscillator and the depths of a modulator. The amount is split into the part which is
     * always active and the part which is controlled by the modulation wheel.
     *
     * @param modulator The modulator to fill
     * @param lfo The oscillator
     * @param amount The amount in the range of [0..1]
     * @param scale The depth of an amount of 1
     * @param wheelShare The share of the amount which is controlled by the modulation wheel in the
     *            range of [0..1]
     */
    public static void setLfoModulator (final ILfoModulator modulator, final ILfo lfo, final double amount, final double scale, final double wheelShare)
    {
        if (amount <= 0)
            return;
        final double share = Math.clamp (wheelShare, 0, 1.0);
        final double depth = Math.min (amount * scale, 1.0);
        modulator.setSource (lfo);
        modulator.setDepth (depth * (1.0 - share));
        modulator.getDepthModWheelModulator ().setDepth (depth * share);
    }


    /**
     * Get the amount of a modulator. The MPC supports only positive amounts, therefore negative
     * depths are inverted.
     *
     * @param modulator The modulator
     * @param scale The depth of an amount of 1
     * @return The amount in the range of [0..1]
     */
    public static double getAmount (final ILfoModulator modulator, final double scale)
    {
        if (!modulator.isActive ())
            return 0;
        return Math.clamp (Math.abs (modulator.getDepth () + modulator.getDepthModWheelModulator ().getDepth ()) / scale, 0, 1.0);
    }


    /**
     * Get the amount of the filter cutoff modulator of a sample zone.
     *
     * @param zone The sample zone
     * @return The amount in the range of [0..1], 0 if there is no filter
     */
    public static double getCutoffAmount (final ISampleZone zone)
    {
        final Optional<IFilter> filter = zone.getFilter ();
        return filter.isPresent () ? getAmount (filter.get ().getCutoffLfoModulator (), CUTOFF_SCALE) : 0;
    }


    /**
     * Get the oscillator of the strongest modulator of a sample zone. The MPC has only one LFO for
     * all destinations.
     *
     * @param zone The sample zone
     * @return The oscillator if any of the modulators is active
     */
    public static Optional<ILfo> getLfo (final ISampleZone zone)
    {
        final Optional<ILfoModulator> modulator = getStrongestModulator (zone);
        return modulator.isPresent () ? Optional.ofNullable (modulator.get ().getSource ()) : Optional.empty ();
    }


    /**
     * Check if any of the sample zones has an active oscillator modulation.
     *
     * @param multisampleSource The multi-sample source
     * @return True if found
     */
    public static boolean hasLfo (final IMultisampleSource multisampleSource)
    {
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
                if (getStrongestModulator (zone).isPresent ())
                    return true;
        return false;
    }


    /**
     * Get the share of the amount which is controlled by the modulation wheel. Since the MPC has
     * only one setting for the whole program, the share is taken from the strongest modulator of
     * the first sample zone which has one.
     *
     * @param multisampleSource The multi-sample source
     * @return The share in the range of [0..1]
     */
    public static double getWheelShare (final IMultisampleSource multisampleSource)
    {
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final Optional<ILfoModulator> modulatorOpt = getStrongestModulator (zone);
                if (modulatorOpt.isEmpty ())
                    continue;
                final ILfoModulator modulator = modulatorOpt.get ();
                final double wheelDepth = modulator.getDepthModWheelModulator ().getDepth ();
                final double maximumDepth = modulator.getDepth () + wheelDepth;
                return maximumDepth == 0 ? 0 : Math.clamp (wheelDepth / maximumDepth, 0, 1.0);
            }
        return 0;
    }


    /**
     * Get the strongest active modulator of a sample zone, relative to the range of its
     * destination.
     *
     * @param zone The sample zone
     * @return The modulator if any is active
     */
    private static Optional<ILfoModulator> getStrongestModulator (final ISampleZone zone)
    {
        ILfoModulator strongest = null;
        double strongestAmount = 0;

        final ILfoModulator amplitudeModulator = zone.getAmplitudeLfoModulator ();
        final double amplitudeAmount = getAmount (amplitudeModulator, VOLUME_SCALE);
        if (amplitudeAmount > strongestAmount)
        {
            strongest = amplitudeModulator;
            strongestAmount = amplitudeAmount;
        }

        final ILfoModulator pitchModulator = zone.getPitchLfoModulator ();
        final double pitchAmount = getAmount (pitchModulator, PITCH_SCALE);
        if (pitchAmount > strongestAmount)
        {
            strongest = pitchModulator;
            strongestAmount = pitchAmount;
        }

        final Optional<IFilter> filter = zone.getFilter ();
        if (filter.isPresent ())
        {
            final ILfoModulator cutoffModulator = filter.get ().getCutoffLfoModulator ();
            if (getAmount (cutoffModulator, CUTOFF_SCALE) > strongestAmount)
                strongest = cutoffModulator;
        }

        return Optional.ofNullable (strongest);
    }
}
