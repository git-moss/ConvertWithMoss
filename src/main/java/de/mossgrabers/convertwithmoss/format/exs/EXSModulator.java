// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.util.HashMap;
import java.util.Map;


/**
 * A modulator in the modulation matrix.
 *
 * @author Jürgen Moßgraber
 */
public class EXSModulator
{
    /** The modulator is not used and off. */
    public static final int                   SOURCE_OFF                     = -1;
    /** The velocity as the source. */
    public static final int                   SOURCE_VELOCITY                = -3;
    /** The Key as the source. */
    public static final int                   SOURCE_KEY                     = -4;
    /** The Aftertouch as the source. */
    public static final int                   SOURCE_AFTERTOUCH              = -7;
    /** The Release Velocity as the source. */
    public static final int                   SOURCE_RELEASE_VELOCITY        = -8;
    /** The LFO 3 as the source. */
    public static final int                   SOURCE_LFO3                    = -10;
    /** The LFO 2 as the source. */
    public static final int                   SOURCE_LFO2                    = -11;
    /** The LFO 1 as the source. */
    public static final int                   SOURCE_LFO1                    = -12;
    /** The envelope 1 as the source. */
    public static final int                   SOURCE_ENV1                    = -13;
    /** The envelope 2 as the source. */
    public static final int                   SOURCE_ENV2                    = -14;
    /** The LFO 4 as the source. */
    public static final int                   SOURCE_LFO4                    = -20;
    /** The envelope 3 as the source. */
    public static final int                   SOURCE_ENV3                    = -21;
    /** The envelope 4 as the source. */
    public static final int                   SOURCE_ENV4                    = -22;
    /** The envelope 5 as the source. */
    public static final int                   SOURCE_ENV5                    = -23;

    /** The modulator is off. */
    public static final int                   DESTINATION_OFF                = 0;
    /** The sample select as the destination. */
    public static final int                   DESTINATION_SAMPLE_SELECT      = 2;
    /** The Glide Time as the destination. */
    public static final int                   DESTINATION_GLIDE_TIME         = 5;
    /** The pitch as the destination. -1200..1200 Cent */
    public static final int                   DESTINATION_PITCH              = 6;
    /** The Filter 1 Drive as the destination. */
    public static final int                   DESTINATION_FILTER_1_DRIVE     = 7;
    /** The Filter 1 Cutoff as the destination. */
    public static final int                   DESTINATION_FILTER_1_CUTOFF    = 8;
    /** The Filter 1 Resonance as the destination. */
    public static final int                   DESTINATION_FILTER_1_RESONANCE = 9;
    /** The Volume as the destination. */
    public static final int                   DESTINATION_VOLUME             = 10;
    /** The Filter 1 Pan as the destination. */
    public static final int                   DESTINATION_PAN                = 11;
    /** The LFO 3 Rate as the destination. */
    public static final int                   DESTINATION_LFO3_RATE          = 18;
    /** The Loop Position as the destination. */
    public static final int                   DESTINATION_LOOP_POSITION      = 36;
    /** The Loop Start as the destination. */
    public static final int                   DESTINATION_LOOP_START         = 37;
    /** The Loop End as the destination. */
    public static final int                   DESTINATION_LOOP_END           = 38;
    /** The Sample Start as the destination. */
    public static final int                   DESTINATION_SAMPLE_START       = 40;
    /** The Sample End as the destination. */
    public static final int                   DESTINATION_SAMPLE_END         = 41;
    /** The Filter Blend as the destination. */
    public static final int                   DESTINATION_FILTER_BLEND       = 60;
    /** The Flex-Speed as the destination. */
    public static final int                   DESTINATION_FLEX_SPEED         = 90;

    private static final Map<Integer, String> SOURCE_NAMES                   = new HashMap<> ();
    private static final Map<Integer, String> DESTINATION_NAMES              = new HashMap<> ();
    static
    {
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_OFF), "Off");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_VELOCITY), "Velocity");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_KEY), "Key");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_AFTERTOUCH), "Aftertouch");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_RELEASE_VELOCITY), "Release Velocity");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_LFO3), "LFO 3");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_LFO2), "LFO 2");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_LFO1), "LFO 1");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_ENV1), "Envelope 1");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_ENV2), "Envelope 2");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_LFO4), "LFO 4");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_ENV3), "Envelope 3");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_ENV4), "Envelope 4");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_ENV5), "Envelope 5");

        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_OFF), "Off");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_SAMPLE_SELECT), "Sample Select");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_GLIDE_TIME), "Glide Time");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_PITCH), "Pitch");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_FILTER_1_DRIVE), "Filter 1 Drive");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_FILTER_1_CUTOFF), "Filter 1 Cutoff");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_FILTER_1_RESONANCE), "Filter 1 Resonance");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_VOLUME), "Volume");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_PAN), "Pan");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_LOOP_POSITION), "Loop Position");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_LOOP_START), "Loop Start");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_LOOP_END), "Loop End");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_SAMPLE_START), "Sample Start");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_SAMPLE_END), "Sample End");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_FILTER_BLEND), "Filter Blend");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_FLEX_SPEED), "Flex Speed");
    }

    int source;
    int destination;
    int lowValue;
    int highValue;


    /**
     * Default constructor.
     */
    public EXSModulator ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     * 
     * @param source The ID of the modulation source
     * @param destination The ID of the modulation destination
     */
    public EXSModulator (final int source, final int destination)
    {
        this.source = source;
        this.destination = destination;
    }


    /**
     * Get the name of the source.
     *
     * @return The name
     */
    public String getSourceName ()
    {
        return SOURCE_NAMES.getOrDefault (Integer.valueOf (this.source), String.format ("Unknown (%d)", Integer.valueOf (this.source)));
    }


    /**
     * Get the name of the destination.
     *
     * @return The name
     */
    public String getDestinationName ()
    {
        return DESTINATION_NAMES.getOrDefault (Integer.valueOf (this.destination), String.format ("Unknown (%d)", Integer.valueOf (this.destination)));
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return String.format ("%s -> %s %d:%d", getSourceName (), getDestinationName (), Integer.valueOf (this.lowValue), Integer.valueOf (this.highValue));
    }
}
