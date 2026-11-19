// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfo;
import de.mossgrabers.tools.XMLUtils;


/**
 * One of the 4 LFO engines of the modulation tab of a Soundbox preset, stored in an E element of
 * the Engines section. The 'state' attribute holds the oscillator settings, the 'sequence'
 * attribute the 32 steps of the sequencer shape and each P child element assigns the LFO to one
 * parameter of the preset. A modulated parameter does not keep its own value: it moves between
 * its minimum and maximum around a center set by the position of the assignment, therefore the
 * position replaces the static value of the parameter. See SOUNDBOX_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxLfoEngine
{
    /** The tag of the section which contains the LFO engines. */
    public static final String  ENGINES                 = "Engines";
    /** The number of LFO engines of a preset. */
    public static final int     NUMBER_OF_ENGINES       = 4;

    /** The target parameter name of the layer volume (0..1). */
    public static final String  TARGET_VOLUME           = "VOLUME LAYER";
    /** The target parameter name of the layer fine tune (-100..100 cents). */
    public static final String  TARGET_FINE             = "FINE LAYER";
    /** The target parameter name of the layer transpose (-1200..1200 cents). */
    public static final String  TARGET_TRANSPOSE        = "TRANSPOSE LAYER";
    /** The target parameter name of the layer octave (-2400..2400 cents). */
    public static final String  TARGET_OCTAVE           = "OCTAVE LAYER";
    /** The target parameter name of the cutoff of a filter effect (50..15001 Hertz). */
    public static final String  TARGET_FILTER_FREQUENCY = "FILTER FREQUENCY";

    /** The range of the fine tune parameter in cents (in each direction). */
    public static final double  FINE_RANGE_CENTS        = 100;
    /** The range of the transpose parameter in cents (in each direction). */
    public static final double  TRANSPOSE_RANGE_CENTS   = 1200;
    /** The range of the octave parameter in cents (in each direction). */
    public static final double  OCTAVE_RANGE_CENTS      = 2400;
    /** The lowest frequency of the filter frequency parameter. */
    public static final double  MIN_FILTER_FREQUENCY    = 50;
    /** The highest frequency of the filter frequency parameter. */
    public static final double  MAX_FILTER_FREQUENCY    = 15001;

    private static final String TAG_ENGINE              = "E";
    private static final String TAG_ASSIGNMENT          = "P";
    private static final String ATTR_SEQUENCE           = "sequence";

    private static final int    STATE_SIZE              = 13;
    private static final int    ASSIGNMENT_SIZE         = 9;

    private static final int    SHAPE_SINE              = 0;
    private static final int    SHAPE_TRIANGLE          = 1;
    private static final int    SHAPE_SQUARE            = 2;
    private static final int    SHAPE_SAW               = 3;
    private static final int    SHAPE_INVERTED_SAW      = 4;
    private static final int    SHAPE_SEQUENCER         = 5;

    private static final int    MOTION_LOOP             = 0;
    private static final int    MOTION_PING_PONG        = 1;
    private static final int    MOTION_REVERSE          = 2;

    private static final double MIN_RATE                = 0.1;
    private static final double MAX_RATE                = 80;
    private static final double DEFAULT_RATE            = 1;
    /** The tempo is not stored in the preset (the host tempo is used), assume 120 BPM. */
    private static final double DEFAULT_TEMPO           = 120;
    /** The lengths of one LFO cycle in sync mode, in whole notes: 1/16 .. 16. */
    private static final double [] NOTE_LENGTHS         =
    {
        1.0 / 16.0,
        1.0 / 8.0,
        1.0 / 4.0,
        1.0 / 2.0,
        1,
        2,
        4,
        8,
        16
    };

    // The default state and sequence blobs as written by the Soundbox plug-in
    private static final String DEFAULT_STATE           = "13..DPyLybOF....D.HA.";
    private static final String DEFAULT_SEQUENCE        = "128.MyLS9....7iYlY1OMyLy8LyLy7ilYloOMyLy9zLyL8ilYlwO.........7SyLyjOMyLy8nYlY5yLyLyOMyLS+zLyL6iYlY1OZlYF+.....PyLybOMyLS9nYlY5SyLyrO....+nYlY7yLyLyOMyLS+XlYl8C.....MyLy8zLyL4C";

    private int                    shape           = SHAPE_SINE;
    private boolean                retrigger       = true;
    private double                 rate            = MIN_RATE;
    private int                    noteLengthIndex = 6;
    private int                    motion          = MOTION_LOOP;
    private boolean                bypass          = false;
    private boolean                infinityActive  = false;
    private int                    infinityCounter = 1;
    private int                    numberOfSteps   = 32;
    private boolean                sync            = true;
    private String                 sequence        = DEFAULT_SEQUENCE;
    private final List<Assignment> assignments     = new ArrayList<> ();


    /**
     * Creates an LFO engine with the default settings of the plug-in and no assignments.
     *
     * @return The engine
     */
    public static SoundboxLfoEngine createDefault ()
    {
        final SoundboxLfoEngine engine = new SoundboxLfoEngine ();
        try
        {
            engine.parseState (SoundboxJuceBase64.decode (DEFAULT_STATE));
        }
        catch (final IOException ex)
        {
            throw new IllegalStateException (ex);
        }
        return engine;
    }


    /**
     * Creates a free running LFO engine with the settings of an oscillator of the model and no
     * assignments.
     *
     * @param lfo The oscillator
     * @return The engine
     */
    public static SoundboxLfoEngine create (final ILfo lfo)
    {
        final SoundboxLfoEngine engine = createDefault ();
        engine.shape = toShape (lfo.getWaveform ());
        engine.rate = Math.clamp (lfo.getRate () > 0 ? lfo.getRate () : DEFAULT_RATE, MIN_RATE, MAX_RATE);
        engine.retrigger = lfo.isKeySync ();
        engine.sync = false;
        return engine;
    }


    /**
     * Reads the LFO engines of a preset.
     *
     * @param presetElement The root element of the preset
     * @return The engines, empty if the preset has no engines section
     * @throws IOException Could not decode the binary data of an engine
     */
    public static List<SoundboxLfoEngine> readEngines (final Element presetElement) throws IOException
    {
        final List<SoundboxLfoEngine> engines = new ArrayList<> ();
        final Element enginesElement = XMLUtils.getChildElementByName (presetElement, ENGINES);
        if (enginesElement == null)
            return engines;

        for (final Element engineElement: XMLUtils.getChildElementsByName (enginesElement, TAG_ENGINE, false))
        {
            final SoundboxLfoEngine engine = new SoundboxLfoEngine ();
            final String state = engineElement.getAttribute (SoundboxTag.ATTR_STATE);
            engine.parseState (SoundboxJuceBase64.decode (state.isBlank () ? DEFAULT_STATE : state));
            final String sequence = engineElement.getAttribute (ATTR_SEQUENCE);
            if (!sequence.isBlank ())
                engine.sequence = sequence;

            for (final Element assignmentElement: XMLUtils.getChildElementsByName (engineElement, TAG_ASSIGNMENT, false))
            {
                final String name = assignmentElement.getAttribute (SoundboxTag.ATTR_NAME);
                if (!name.isBlank ())
                    engine.assignments.add (Assignment.parse (name, SoundboxJuceBase64.decode (assignmentElement.getAttribute (SoundboxTag.ATTR_STATE))));
            }
            engines.add (engine);
        }
        return engines;
    }


    /**
     * Writes the engines section of a preset. Missing engines are filled up with the default
     * engine.
     *
     * @param document The XML document of the preset
     * @param presetElement The root element of the preset
     * @param engines The engines to write, at most {@link #NUMBER_OF_ENGINES}
     */
    public static void writeEngines (final Document document, final Element presetElement, final List<SoundboxLfoEngine> engines)
    {
        final Element enginesElement = XMLUtils.addElement (document, presetElement, ENGINES);
        for (int engineIndex = 0; engineIndex < NUMBER_OF_ENGINES; engineIndex++)
        {
            final SoundboxLfoEngine engine = engineIndex < engines.size () ? engines.get (engineIndex) : createDefault ();
            final Element engineElement = XMLUtils.addElement (document, enginesElement, TAG_ENGINE);
            engineElement.setAttribute (SoundboxTag.ATTR_STATE, SoundboxJuceBase64.encode (engine.writeState ()));
            engineElement.setAttribute (ATTR_SEQUENCE, engine.sequence);
            for (final Assignment assignment: engine.assignments)
            {
                final Element assignmentElement = XMLUtils.addElement (document, engineElement, TAG_ASSIGNMENT);
                assignmentElement.setAttribute (SoundboxTag.ATTR_NAME, assignment.name);
                assignmentElement.setAttribute (SoundboxTag.ATTR_STATE, SoundboxJuceBase64.encode (assignment.write ()));
            }
        }
    }


    /**
     * Creates the name of a layer parameter as used by the assignments, e.g. 'VOLUME LAYER 1'.
     *
     * @param parameter The parameter, e.g. {@link #TARGET_VOLUME}
     * @param layerIndex The index of the layer (0-3)
     * @return The name
     */
    public static String getLayerTarget (final String parameter, final int layerIndex)
    {
        return parameter + " " + (layerIndex + 1);
    }


    /**
     * Creates the name of an effect parameter as used by the assignments, e.g. 'FILTER FREQUENCY 1
     * (2)' for the second effect slot of the first layer.
     *
     * @param parameter The parameter, e.g. {@link #TARGET_FILTER_FREQUENCY}
     * @param layerIndex The index of the layer (0-3) or -1 for the master effects
     * @param slot The index of the effect slot (0-3)
     * @return The name
     */
    public static String getEffectTarget (final String parameter, final int layerIndex, final int slot)
    {
        return parameter + (layerIndex < 0 ? " Master" : " " + (layerIndex + 1)) + " (" + (slot + 1) + ")";
    }


    /**
     * Get the active assignment to a parameter. Assignments of a bypassed engine and bypassed
     * assignments are ignored.
     *
     * @param target The name of the parameter
     * @return The assignment if present
     */
    public Optional<Assignment> getAssignment (final String target)
    {
        if (this.bypass)
            return Optional.empty ();
        for (final Assignment assignment: this.assignments)
            if (!assignment.bypass && assignment.name.equals (target))
                return Optional.of (assignment);
        return Optional.empty ();
    }


    /**
     * Assign the LFO to a parameter.
     *
     * @param target The name of the parameter
     * @param amount The amount (-1..1)
     * @param position The position (-1..1)
     */
    public void addAssignment (final String target, final double amount, final double position)
    {
        final Assignment assignment = new Assignment ();
        assignment.name = target;
        assignment.amount = Math.clamp (amount, -1, 1);
        assignment.position = Math.clamp (position, -1, 1);
        this.assignments.add (assignment);
    }


    /**
     * Check if the oscillator settings of this engine are identical to the ones of another
     * engine.
     *
     * @param other The other engine
     * @return True if identical
     */
    public boolean hasSameOscillator (final SoundboxLfoEngine other)
    {
        return this.shape == other.shape && this.retrigger == other.retrigger && this.sync == other.sync && Math.abs (this.rate - other.rate) < 0.0001 && this.noteLengthIndex == other.noteLengthIndex && this.motion == other.motion;
    }


    /**
     * Create an oscillator of the model with the settings of this engine. In sync mode the rate is
     * calculated for 120 BPM since the preset does not contain a tempo.
     *
     * @return The oscillator
     */
    public ILfo createLfo ()
    {
        double frequency = this.sync ? DEFAULT_TEMPO / (240.0 * NOTE_LENGTHS[Math.clamp (this.noteLengthIndex, 0, NOTE_LENGTHS.length - 1)]) : this.rate;

        LfoWaveform waveform;
        switch (this.shape)
        {
            case SHAPE_TRIANGLE:
                waveform = LfoWaveform.TRIANGLE;
                break;
            case SHAPE_SQUARE:
                waveform = LfoWaveform.SQUARE;
                break;
            case SHAPE_SAW:
                waveform = LfoWaveform.SAWTOOTH_UP;
                break;
            case SHAPE_INVERTED_SAW:
                waveform = LfoWaveform.SAWTOOTH_DOWN;
                break;
            case SHAPE_SEQUENCER:
                // The steps are user drawn, the closest match is a random wave
                waveform = LfoWaveform.RANDOM;
                break;
            case SHAPE_SINE:
            default:
                waveform = LfoWaveform.SINE;
                break;
        }

        // The motion changes the direction in which the shape is played: reverse turns a rising
        // saw into a falling one, ping-pong plays a saw forwards and backwards, which is a
        // triangle with half the rate
        if (this.motion == MOTION_REVERSE)
        {
            if (waveform == LfoWaveform.SAWTOOTH_UP)
                waveform = LfoWaveform.SAWTOOTH_DOWN;
            else if (waveform == LfoWaveform.SAWTOOTH_DOWN)
                waveform = LfoWaveform.SAWTOOTH_UP;
        }
        else if (this.motion == MOTION_PING_PONG && (waveform == LfoWaveform.SAWTOOTH_UP || waveform == LfoWaveform.SAWTOOTH_DOWN))
        {
            waveform = LfoWaveform.TRIANGLE;
            frequency /= 2.0;
        }

        final ILfo lfo = new DefaultLfo ();
        lfo.setWaveform (waveform);
        lfo.setRate (frequency);
        lfo.setKeySync (this.retrigger);
        return lfo;
    }


    /**
     * Calculates the center of the layer volume (0..1) of a volume assignment.
     *
     * @param assignment The assignment
     * @return The center volume
     */
    public static double getVolumeCenter (final Assignment assignment)
    {
        return Math.clamp ((1.0 + assignment.position) / 2.0, 0, 1);
    }


    /**
     * Calculates the depth of an amplitude LFO of the model from a volume assignment. The depth is
     * half of the swing between the highest and lowest volume in dB.
     *
     * @param assignment The assignment
     * @return The depth (-1..1, 1 = {@link ILfoModulator#MAX_VOLUME_DEPTH})
     */
    public static double getVolumeDepth (final Assignment assignment)
    {
        final double center = getVolumeCenter (assignment);
        final double swing = Math.abs (assignment.amount) / 2.0;
        final double high = Math.min (center + swing, 1);
        final double low = Math.max (center - swing, 0);
        if (high <= 0)
            return 0;
        final double decibels = low <= 0 ? ILfoModulator.MAX_VOLUME_DEPTH : Math.min (10.0 * Math.log10 (high / low), ILfoModulator.MAX_VOLUME_DEPTH);
        return Math.signum (assignment.amount) * decibels / ILfoModulator.MAX_VOLUME_DEPTH;
    }


    /**
     * Calculates the amount of a volume assignment from the depth of an amplitude LFO of the model,
     * see {@link #getVolumeDepth(Assignment)}.
     *
     * @param depth The depth (-1..1)
     * @param center The center volume (0..1)
     * @return The amount
     */
    public static double getVolumeAmount (final double depth, final double center)
    {
        final double ratio = Math.pow (10, Math.abs (depth) * ILfoModulator.MAX_VOLUME_DEPTH / 10.0);
        return Math.signum (depth) * 2.0 * center * (ratio - 1.0) / (ratio + 1.0);
    }


    /**
     * Calculates the center frequency of a filter frequency assignment.
     *
     * @param assignment The assignment
     * @return The center frequency in Hertz
     */
    public static double getFilterCenter (final Assignment assignment)
    {
        final double range = MAX_FILTER_FREQUENCY - MIN_FILTER_FREQUENCY;
        return Math.clamp (MIN_FILTER_FREQUENCY + range * (1.0 + assignment.position) / 2.0, MIN_FILTER_FREQUENCY, MAX_FILTER_FREQUENCY);
    }


    /**
     * Calculates the depth in cents of a cutoff LFO of the model from a filter frequency
     * assignment. The parameter moves linearly in Hertz, the depth is half of the swing between the
     * highest and lowest frequency in cents.
     *
     * @param assignment The assignment
     * @return The depth in cents
     */
    public static double getFilterDepthCents (final Assignment assignment)
    {
        final double center = getFilterCenter (assignment);
        final double swing = Math.abs (assignment.amount) * (MAX_FILTER_FREQUENCY - MIN_FILTER_FREQUENCY) / 2.0;
        final double high = Math.min (center + swing, MAX_FILTER_FREQUENCY);
        final double low = Math.max (center - swing, MIN_FILTER_FREQUENCY);
        return Math.signum (assignment.amount) * 600.0 * Math.log (high / low) / Math.log (2);
    }


    /**
     * Calculates the amount of a filter frequency assignment from the depth of a cutoff LFO of the
     * model, see {@link #getFilterDepthCents(Assignment)}.
     *
     * @param cents The depth in cents
     * @param center The center frequency in Hertz
     * @return The amount
     */
    public static double getFilterAmount (final double cents, final double center)
    {
        final double ratio = Math.pow (2, Math.abs (cents) / 600.0);
        final double swing = center * (ratio - 1.0) / (ratio + 1.0);
        return Math.signum (cents) * 2.0 * swing / (MAX_FILTER_FREQUENCY - MIN_FILTER_FREQUENCY);
    }


    /**
     * Calculates the position of a filter frequency assignment which centers the modulation at
     * the given frequency.
     *
     * @param center The center frequency in Hertz
     * @return The position
     */
    public static double getFilterPosition (final double center)
    {
        return 2.0 * (Math.clamp (center, MIN_FILTER_FREQUENCY, MAX_FILTER_FREQUENCY) - MIN_FILTER_FREQUENCY) / (MAX_FILTER_FREQUENCY - MIN_FILTER_FREQUENCY) - 1.0;
    }


    private void parseState (final byte [] data) throws IOException
    {
        if (data.length < STATE_SIZE)
            throw new IOException ("LFO engine state too short: " + data.length + " bytes.");

        final ByteBuffer buffer = ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN);
        this.shape = buffer.get (0x00);
        this.retrigger = buffer.get (0x01) != 0;
        this.rate = buffer.getFloat (0x02);
        this.noteLengthIndex = buffer.get (0x06);
        this.motion = buffer.get (0x07);
        this.bypass = buffer.get (0x08) != 0;
        this.infinityActive = buffer.get (0x09) != 0;
        this.infinityCounter = buffer.get (0x0A) & 0xFF;
        this.numberOfSteps = buffer.get (0x0B) & 0xFF;
        this.sync = buffer.get (0x0C) != 0;
    }


    private byte [] writeState ()
    {
        final ByteBuffer buffer = ByteBuffer.allocate (STATE_SIZE).order (ByteOrder.LITTLE_ENDIAN);
        buffer.put (0x00, (byte) this.shape);
        buffer.put (0x01, (byte) (this.retrigger ? 1 : 0));
        buffer.putFloat (0x02, (float) this.rate);
        buffer.put (0x06, (byte) this.noteLengthIndex);
        buffer.put (0x07, (byte) this.motion);
        buffer.put (0x08, (byte) (this.bypass ? 1 : 0));
        buffer.put (0x09, (byte) (this.infinityActive ? 1 : 0));
        buffer.put (0x0A, (byte) this.infinityCounter);
        buffer.put (0x0B, (byte) this.numberOfSteps);
        buffer.put (0x0C, (byte) (this.sync ? 1 : 0));
        return buffer.array ();
    }


    private static int toShape (final LfoWaveform waveform)
    {
        if (waveform == null)
            return SHAPE_SINE;
        switch (waveform)
        {
            case TRIANGLE:
                return SHAPE_TRIANGLE;
            case SQUARE:
                return SHAPE_SQUARE;
            case SAWTOOTH_UP:
                return SHAPE_SAW;
            case SAWTOOTH_DOWN:
                return SHAPE_INVERTED_SAW;
            case RANDOM:
                // The default sequence of the plug-in holds random looking steps
                return SHAPE_SEQUENCER;
            case SINE:
            default:
                return SHAPE_SINE;
        }
    }


    /** The assignment of an LFO engine to a parameter, stored in a P element. */
    public static class Assignment
    {
        /** The name of the modulated parameter. */
        public String  name     = "";
        /** The amount (-1..1), negative values invert the LFO. */
        public double  amount   = 0;
        /** The position (-1..1) which sets the center of the modulation in the parameter range. */
        public double  position = 0;
        /** True if the assignment is bypassed. */
        public boolean bypass   = false;


        /**
         * Parses an assignment from its binary form.
         *
         * @param name The name of the modulated parameter
         * @param data The decoded blob
         * @return The parsed assignment
         * @throws IOException If the data is too short
         */
        static Assignment parse (final String name, final byte [] data) throws IOException
        {
            if (data.length < ASSIGNMENT_SIZE)
                throw new IOException ("LFO assignment too short: " + data.length + " bytes.");

            final ByteBuffer buffer = ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN);
            final Assignment assignment = new Assignment ();
            assignment.name = name;
            assignment.amount = buffer.getFloat (0x00);
            assignment.position = buffer.getFloat (0x04);
            assignment.bypass = buffer.get (0x08) != 0;
            return assignment;
        }


        /**
         * Writes the assignment in its binary form.
         *
         * @return The binary form
         */
        byte [] write ()
        {
            final ByteBuffer buffer = ByteBuffer.allocate (ASSIGNMENT_SIZE).order (ByteOrder.LITTLE_ENDIAN);
            buffer.putFloat (0x00, (float) this.amount);
            buffer.putFloat (0x04, (float) this.position);
            buffer.put (0x08, (byte) (this.bypass ? 1 : 0));
            return buffer.array ();
        }
    }
}
