// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu;

import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfo;


/**
 * Converts the two LFOs of a voice of the E-mu EOS samplers (Emulator IV series) and the Emulator X
 * and their modulation cords into the amplitude, pitch and cutoff LFOs of the model and back. Both
 * use the same numbers for the cord sources and destinations and the same LFO wave shapes. An LFO
 * has no fixed destination; it modulates whatever a cord routes it to with the amount of the cord,
 * which in turn can be controlled by the modulation wheel through a cord whose destination is the
 * amount of the LFO cord. See documentation/design/E4B_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class EmuLfoModulation
{
    /** The modulation source of the modulation wheel. */
    public static final int     SOURCE_MOD_WHEEL     = 0x11;
    /** The modulation source of LFO 1, centered (bipolar). */
    public static final int     SOURCE_LFO1_CENTER   = 0x60;
    /** The modulation source of LFO 1, positive only. */
    public static final int     SOURCE_LFO1_POSITIVE = 0x61;
    /** The modulation source of LFO 2, centered (bipolar). */
    public static final int     SOURCE_LFO2_CENTER   = 0x68;
    /** The modulation source of LFO 2, positive only. */
    public static final int     SOURCE_LFO2_POSITIVE = 0x69;
    /** The modulation source 'DC offset', a constant value. */
    public static final int     SOURCE_DC            = 0xA0;

    /** The modulation destination 'fine pitch' (1 semi-tone at full amount). */
    public static final int     DEST_FINE_PITCH      = 0x2F;
    /** The modulation destination 'pitch'. */
    public static final int     DEST_PITCH           = 0x30;
    /** The modulation destination 'filter frequency'. */
    public static final int     DEST_CUTOFF          = 0x38;
    /** The modulation destination 'amplifier volume'. */
    public static final int     DEST_VOLUME          = 0x40;
    /** The modulation destination 'amount of cord 1', the following cords are consecutive. */
    public static final int     DEST_CORD_AMOUNT     = 0xA8;

    /** The LFO shape 'triangle'. */
    public static final int     SHAPE_TRIANGLE       = 0;
    /** The LFO shape 'sine'. */
    public static final int     SHAPE_SINE           = 1;
    /** The LFO shape 'sawtooth', which falls and then jumps back up. */
    public static final int     SHAPE_SAWTOOTH       = 2;
    /** The LFO shape 'square'. */
    public static final int     SHAPE_SQUARE         = 3;
    /** The LFO shape '12% pulse', the last of the 4 pulse shapes (33%, 25%, 16%, 12%). */
    public static final int     SHAPE_PULSE_12       = 7;
    /** The LFO shape 'sine + noise'. */
    public static final int     SHAPE_SINE_NOISE     = 14;
    /** The LFO shape 'random', a sample and hold. */
    public static final int     SHAPE_RANDOM         = 255;

    /** The lowest LFO rate in Hertz. */
    public static final double  MIN_RATE             = 0.08;
    /** The highest LFO rate in Hertz. */
    public static final double  MAX_RATE             = 18.01;
    /** The rate of an LFO of the model which has none set. */
    public static final double  DEFAULT_RATE         = 4.15;

    /**
     * The pitch change in cents of a cord with full amount (+100%) at full output of its source:
     * the Emulator X manual gives 3.125% per semi-tone ('Pitch Amount Chart') and +37.5% moves the
     * 'Pat: Octaves' LFO shape by one octave in each direction.
     */
    private static final double PITCH_RANGE_CENTS    = 3200;
    /** The pitch change in cents of a cord into the fine pitch with full amount. */
    private static final double FINE_PITCH_CENTS     = 100;
    /**
     * The cutoff change in cents of a cord with full amount at full output of its source. A key
     * tracking cord of +100% moves the cutoff by 0.713 octaves per octave over 127 keys.
     */
    private static final double CUTOFF_RANGE_CENTS   = 0.713 * 127 * 100;
    /**
     * The 'positive' LFO sources swing between 0 and the full value, which is half of the swing of
     * the centered sources.
     */
    private static final double POSITIVE_LFO_FACTOR  = 0.5;

    // The EOS LFO rate and delay laws, measured on hardware by the simple_e4b project:
    // rate = 1.64054 * 1.01973^byte - 1.57702 (0.08 .. 18.01 Hz), delay = 0.149998 * 1.04^byte -
    // 0.150012 (0 .. 21.7 seconds)
    private static final double RATE_FACTOR          = 1.64054;
    private static final double RATE_BASE            = 1.01973;
    private static final double RATE_OFFSET          = -1.57702;
    private static final double DELAY_FACTOR         = 0.149998;
    private static final double DELAY_BASE           = 1.04;
    private static final double DELAY_OFFSET         = -0.150012;
    private static final double MAX_DELAY            = 21.694;


    /**
     * One modulation cord.
     *
     * @param source The source of the cord
     * @param destination The destination of the cord
     * @param amount The amount of the cord, -1..1 (= -100%..100%)
     */
    public record Cord (int source, int destination, double amount)
    {
        // Intentionally empty
    }


    /** The routing of one LFO of the model to a destination. */
    private static class Route
    {
        ILfo   lfo;
        double depth;
        double modWheelDepth;
        double strength = -1;
    }


    /**
     * Private constructor since this is a utility class.
     */
    private EmuLfoModulation ()
    {
        // Intentionally empty
    }


    /**
     * Converts an EOS LFO rate value into Hertz.
     *
     * @param value The rate value, 0..127
     * @return The rate in Hertz
     */
    public static double eosValueToRate (final int value)
    {
        return Math.clamp (RATE_FACTOR * Math.pow (RATE_BASE, value) + RATE_OFFSET, MIN_RATE, MAX_RATE);
    }


    /**
     * Converts a rate in Hertz into an EOS LFO rate value.
     *
     * @param rate The rate in Hertz
     * @return The rate value, 0..127
     */
    public static int rateToEosValue (final double rate)
    {
        final double clamped = Math.clamp (rate, MIN_RATE, MAX_RATE);
        return Math.clamp (Math.round (Math.log ((clamped - RATE_OFFSET) / RATE_FACTOR) / Math.log (RATE_BASE)), 0, 127);
    }


    /**
     * Converts an EOS LFO delay value into seconds.
     *
     * @param value The delay value, 0..127
     * @return The delay in seconds
     */
    public static double eosValueToDelay (final int value)
    {
        return Math.max (0, DELAY_FACTOR * Math.pow (DELAY_BASE, value) + DELAY_OFFSET);
    }


    /**
     * Converts a delay in seconds into an EOS LFO delay value.
     *
     * @param delay The delay in seconds
     * @return The delay value, 0..127
     */
    public static int delayToEosValue (final double delay)
    {
        if (delay <= 0)
            return 0;
        final double clamped = Math.min (delay, MAX_DELAY);
        return Math.clamp (Math.round (Math.log ((clamped - DELAY_OFFSET) / DELAY_FACTOR) / Math.log (DELAY_BASE)), 0, 127);
    }


    /**
     * Creates an LFO of the model.
     *
     * @param shape The E-mu wave shape
     * @param rate The rate in Hertz
     * @param delay The delay in seconds
     * @param isKeySync True if the LFO restarts with each note
     * @return The LFO
     */
    public static ILfo createLfo (final int shape, final double rate, final double delay, final boolean isKeySync)
    {
        final ILfo lfo = new DefaultLfo ();
        lfo.setWaveform (toWaveform (shape));
        lfo.setRate (Math.clamp (rate, MIN_RATE, MAX_RATE));
        lfo.setDelay (Math.max (0, delay));
        lfo.setKeySync (isKeySync);
        return lfo;
    }


    /**
     * Converts the LFO cords of a voice into the LFO modulators of a zone. Each destination (the
     * volume, the pitch and fine pitch together and the filter cutoff) gets the LFO of the cord
     * with the strongest effect; the modulation wheel depth comes from cords which route the
     * modulation wheel into the amount of that cord. The cutoff is only set if the zone already has
     * a filter.
     *
     * @param zone The zone
     * @param cords All cords of the voice in their order
     * @param lfos The two LFOs of the voice
     */
    public static void applyLfos (final ISampleZone zone, final List<Cord> cords, final ILfo [] lfos)
    {
        // The amount of a cord can itself be modulated: the modulation wheel adds its value (when
        // fully up) and a DC offset adds a constant
        final int numCords = cords.size ();
        final double [] amounts = new double [numCords];
        final double [] modWheelAmounts = new double [numCords];
        for (int i = 0; i < numCords; i++)
            amounts[i] = cords.get (i).amount ();
        for (final Cord cord: cords)
        {
            final int target = cord.destination () - DEST_CORD_AMOUNT;
            if (target < 0 || target >= numCords)
                continue;
            if (cord.source () == SOURCE_MOD_WHEEL)
                modWheelAmounts[target] += cord.amount ();
            else if (cord.source () == SOURCE_DC)
                amounts[target] += cord.amount ();
        }

        final Route amplitude = new Route ();
        final Route pitch = new Route ();
        final Route cutoff = new Route ();
        final Optional<IFilter> filter = zone.getFilter ();
        for (int i = 0; i < numCords; i++)
        {
            final Cord cord = cords.get (i);
            final int lfoIndex = getLfoIndex (cord.source ());
            if (lfoIndex < 0)
                continue;

            final Route route;
            final double scale;
            switch (cord.destination ())
            {
                case DEST_VOLUME:
                    route = amplitude;
                    scale = 1;
                    break;
                case DEST_PITCH:
                    route = pitch;
                    scale = PITCH_RANGE_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH;
                    break;
                case DEST_FINE_PITCH:
                    route = pitch;
                    scale = FINE_PITCH_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH;
                    break;
                case DEST_CUTOFF:
                    if (filter.isEmpty ())
                        continue;
                    route = cutoff;
                    scale = CUTOFF_RANGE_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH;
                    break;
                default:
                    continue;
            }

            final double polarity = isPositiveLfo (cord.source ()) ? POSITIVE_LFO_FACTOR : 1;
            final double depth = Math.clamp (amounts[i] * scale * polarity, -1, 1);
            final double modWheelDepth = Math.clamp (modWheelAmounts[i] * scale * polarity, -1, 1);
            final double strength = Math.abs (depth) + Math.abs (modWheelDepth);
            if (strength > route.strength && strength > 0)
            {
                route.strength = strength;
                route.depth = depth;
                route.modWheelDepth = modWheelDepth;
                route.lfo = lfos[lfoIndex];
            }
        }

        setModulator (zone.getAmplitudeLfoModulator (), amplitude);
        setModulator (zone.getPitchLfoModulator (), pitch);
        if (filter.isPresent ())
            setModulator (filter.get ().getCutoffLfoModulator (), cutoff);
    }


    /**
     * Creates the LFO routings of a zone: the pitch LFO is routed by the existing cord from LFO 1
     * to the pitch (whose amount the existing modulation wheel cord controls), the amplitude and
     * cutoff LFOs get a new cord each which uses LFO 1 if it has the same settings or LFO 2. An LFO
     * which needs a third oscillator is dropped. A rising sawtooth is written as the falling
     * sawtooth of the E-mu with an inverted amount.
     *
     * @param zone The zone
     * @param cords The default cords of a voice, the routings are added to them; must contain the
     *            cord from LFO 1 to the pitch and the cord from the modulation wheel to its amount
     * @param maxCords The maximum number of cords
     * @return The two LFOs of the voice, null for an LFO which is not used
     */
    public static ILfo [] createLfos (final ISampleZone zone, final List<Cord> cords, final int maxCords)
    {
        final ILfo [] lfos = new ILfo [2];

        final ILfoModulator pitch = zone.getPitchLfoModulator ();
        if (pitch.isActive ())
        {
            final int lfoIndex = assignLfo (lfos, pitch.getSource ());
            final double sign = getSign (pitch.getSource ());
            final double scale = PITCH_RANGE_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH;
            final int cordIndex = findCord (cords, SOURCE_LFO1_CENTER, DEST_PITCH);
            if (lfoIndex == 0 && cordIndex >= 0)
            {
                cords.set (cordIndex, new Cord (SOURCE_LFO1_CENTER, DEST_PITCH, Math.clamp (sign * pitch.getDepth () / scale, -1, 1)));
                final double modWheelAmount = Math.clamp (sign * pitch.getDepthModWheelModulator ().getDepth () / scale, -1, 1);
                final int modWheelIndex = findCord (cords, SOURCE_MOD_WHEEL, DEST_CORD_AMOUNT + cordIndex);
                if (modWheelIndex >= 0)
                    cords.set (modWheelIndex, new Cord (SOURCE_MOD_WHEEL, DEST_CORD_AMOUNT + cordIndex, modWheelAmount));
                else if (modWheelAmount != 0 && cords.size () < maxCords)
                    cords.add (new Cord (SOURCE_MOD_WHEEL, DEST_CORD_AMOUNT + cordIndex, modWheelAmount));
            }
        }

        addRoute (zone.getAmplitudeLfoModulator (), DEST_VOLUME, 1, lfos, cords, maxCords);
        final Optional<IFilter> filter = zone.getFilter ();
        if (filter.isPresent ())
            addRoute (filter.get ().getCutoffLfoModulator (), DEST_CUTOFF, CUTOFF_RANGE_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH, lfos, cords, maxCords);
        return lfos;
    }


    /**
     * Converts an LFO wave shape of the model into the E-mu wave shape.
     *
     * @param waveform The wave shape of the model
     * @return The E-mu wave shape
     */
    public static int fromWaveform (final LfoWaveform waveform)
    {
        if (waveform == null)
            return SHAPE_TRIANGLE;
        return switch (waveform)
        {
            case SINE -> SHAPE_SINE;
            case SQUARE -> SHAPE_SQUARE;
            case SAWTOOTH_UP, SAWTOOTH_DOWN -> SHAPE_SAWTOOTH;
            case RANDOM -> SHAPE_RANDOM;
            default -> SHAPE_TRIANGLE;
        };
    }


    /**
     * Get the rate of an LFO for writing.
     *
     * @param lfo The LFO
     * @return The rate in Hertz
     */
    public static double getRate (final ILfo lfo)
    {
        return lfo.getRate () > 0 ? Math.clamp (lfo.getRate (), MIN_RATE, MAX_RATE) : DEFAULT_RATE;
    }


    /**
     * Converts an E-mu LFO wave shape into the wave shape of the model.
     *
     * @param shape The E-mu wave shape
     * @return The wave shape of the model
     */
    private static LfoWaveform toWaveform (final int shape)
    {
        if (shape == SHAPE_RANDOM)
            return LfoWaveform.RANDOM;
        if (shape == SHAPE_SINE || shape == SHAPE_SINE_NOISE)
            return LfoWaveform.SINE;
        if (shape == SHAPE_SAWTOOTH)
            return LfoWaveform.SAWTOOTH_DOWN;
        // The pulse shapes are the closest to a square
        if (shape >= SHAPE_SQUARE && shape <= SHAPE_PULSE_12)
            return LfoWaveform.SQUARE;
        // Triangle and the pattern shapes, which step through intervals
        return LfoWaveform.TRIANGLE;
    }


    /**
     * Adds a cord which routes an LFO to a destination and, if the modulation wheel controls the
     * depth, a cord from the modulation wheel to the amount of that cord.
     *
     * @param modulator The LFO modulator of the model
     * @param destination The destination of the cord
     * @param scale The depth of the model which a full cord amount creates
     * @param lfos The two LFOs of the voice
     * @param cords The cords to add to
     * @param maxCords The maximum number of cords
     */
    private static void addRoute (final ILfoModulator modulator, final int destination, final double scale, final ILfo [] lfos, final List<Cord> cords, final int maxCords)
    {
        if (!modulator.isActive () || cords.size () >= maxCords)
            return;
        final int lfoIndex = assignLfo (lfos, modulator.getSource ());
        if (lfoIndex < 0)
            return;
        final double sign = getSign (modulator.getSource ());
        final int source = lfoIndex == 0 ? SOURCE_LFO1_CENTER : SOURCE_LFO2_CENTER;
        final int cordIndex = cords.size ();
        cords.add (new Cord (source, destination, Math.clamp (sign * modulator.getDepth () / scale, -1, 1)));
        final double modWheelAmount = Math.clamp (sign * modulator.getDepthModWheelModulator ().getDepth () / scale, -1, 1);
        if (modWheelAmount != 0 && cords.size () < maxCords)
            cords.add (new Cord (SOURCE_MOD_WHEEL, DEST_CORD_AMOUNT + cordIndex, modWheelAmount));
    }


    /**
     * Get the index of the LFO of the voice which plays the given LFO of the model. A free LFO is
     * taken if none has the same settings.
     *
     * @param lfos The two LFOs of the voice
     * @param lfo The LFO of the model
     * @return The index of the LFO, -1 if both are used by other settings
     */
    private static int assignLfo (final ILfo [] lfos, final ILfo lfo)
    {
        for (int i = 0; i < lfos.length; i++)
        {
            if (lfos[i] == null)
            {
                lfos[i] = lfo;
                return i;
            }
            if (isSameOscillator (lfos[i], lfo))
                return i;
        }
        return -1;
    }


    /**
     * Test whether two LFOs of the model can be played by the same E-mu LFO.
     *
     * @param lfo1 The first LFO
     * @param lfo2 The second LFO
     * @return True if they have the same settings
     */
    private static boolean isSameOscillator (final ILfo lfo1, final ILfo lfo2)
    {
        return fromWaveform (lfo1.getWaveform ()) == fromWaveform (lfo2.getWaveform ()) && getSign (lfo1) == getSign (lfo2) && Math.abs (getRate (lfo1) - getRate (lfo2)) < 0.001 && Math.abs (Math.max (0, lfo1.getDelay ()) - Math.max (0, lfo2.getDelay ())) < 0.001 && lfo1.isKeySync () == lfo2.isKeySync ();
    }


    /**
     * The E-mu sawtooth falls, therefore a rising sawtooth needs an inverted amount.
     *
     * @param lfo The LFO
     * @return -1 for a rising sawtooth, otherwise 1
     */
    private static double getSign (final ILfo lfo)
    {
        return lfo.getWaveform () == LfoWaveform.SAWTOOTH_UP ? -1 : 1;
    }


    /**
     * Find a cord.
     *
     * @param cords The cords
     * @param source The source of the cord
     * @param destination The destination of the cord
     * @return The index of the cord or -1 if not found
     */
    private static int findCord (final List<Cord> cords, final int source, final int destination)
    {
        for (int i = 0; i < cords.size (); i++)
        {
            final Cord cord = cords.get (i);
            if (cord.source () == source && cord.destination () == destination)
                return i;
        }
        return -1;
    }


    /**
     * Get the index of the LFO which a cord source represents.
     *
     * @param source The cord source
     * @return 0 for LFO 1, 1 for LFO 2, -1 if the source is not an LFO
     */
    private static int getLfoIndex (final int source)
    {
        if (source == SOURCE_LFO1_CENTER || source == SOURCE_LFO1_POSITIVE)
            return 0;
        if (source == SOURCE_LFO2_CENTER || source == SOURCE_LFO2_POSITIVE)
            return 1;
        return -1;
    }


    /**
     * Test whether a cord source is the positive only variant of an LFO.
     *
     * @param source The cord source
     * @return True if positive only
     */
    private static boolean isPositiveLfo (final int source)
    {
        return source == SOURCE_LFO1_POSITIVE || source == SOURCE_LFO2_POSITIVE;
    }


    /**
     * Set the depths and the LFO of a modulator of the model from a found route.
     *
     * @param target The modulator to set
     * @param route The route, nothing is set if it has no LFO
     */
    private static void setModulator (final ILfoModulator target, final Route route)
    {
        if (route.lfo == null)
            return;
        target.setDepth (route.depth);
        target.getDepthModWheelModulator ().setDepth (route.modWheelDepth);
        // Each modulator gets its own copy since an LFO can be routed to several destinations
        final ILfo copy = new DefaultLfo ();
        copy.setWaveform (route.lfo.getWaveform ());
        copy.setRate (route.lfo.getRate ());
        copy.setDelay (route.lfo.getDelay ());
        copy.setKeySync (route.lfo.isKeySync ());
        target.setSource (copy);
    }
}
