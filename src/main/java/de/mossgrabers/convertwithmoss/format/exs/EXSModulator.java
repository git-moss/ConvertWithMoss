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
    public static final int                   SOURCE_OFF                = -1;
    /** The envelope 2 as the source. */
    public static final int                   SOURCE_ENV2               = -3;
    /** The LFO 1 as the source. */
    public static final int                   SOURCE_LFO1               = -12;

    /** The modulator is off. */
    public static final int                   DESTINATION_OFF           = 0;
    /** The sample select as the destination. */
    public static final int                   DESTINATION_SAMPLE_SELECT = 2;
    /** The pitch as the destination. -1200..1200 Cent */
    public static final int                   DESTINATION_PITCH         = 6;

    private static final Map<Integer, String> SOURCE_NAMES              = new HashMap<> ();
    private static final Map<Integer, String> DESTINATION_NAMES         = new HashMap<> ();
    static
    {
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_OFF), "Off");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_ENV2), "Envelope 2");
        SOURCE_NAMES.put (Integer.valueOf (SOURCE_LFO1), "LFO 1");

        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_OFF), "Off");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_SAMPLE_SELECT), "Sample Select");
        DESTINATION_NAMES.put (Integer.valueOf (DESTINATION_PITCH), "Pitch");
    }

    int source;
    int destination;
    int lowValue;
    int highValue;


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
}
