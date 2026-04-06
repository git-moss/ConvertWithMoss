// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.iso;

import java.util.EnumMap;
import java.util.Map;


/**
 * Constants for different CD formats.
 *
 * @author Jürgen Moßgraber
 */
public enum IsoFormat
{
    /** The format used by Akai MPC2000. */
    AKAI_MPC2000,
    /** The format used by Akai MPC2000XL. */
    AKAI_MPC2000XL,
    /** The format used by Akai S1000/S1100. */
    AKAI_S1000_S1100,
    /** The format used by Akai S3000. */
    AKAI_S3000,
    /** The ISO 9660 format. */
    ISO_9660,
    /** The format used by Roland S550 and compatible samplers. */
    ROLAND_S550_W30_DJ70,
    /** The format used by Roland S7xx. */
    ROLAND_S7XX,
    /** Unknown format. */
    UNKNOWN;


    private static final Map<IsoFormat, String> NAMES = new EnumMap<> (IsoFormat.class);
    static
    {
        NAMES.put (AKAI_MPC2000, "Akai MPC2000");
        NAMES.put (AKAI_MPC2000XL, "Akai MPC2000XL");
        NAMES.put (AKAI_S1000_S1100, "Akai S1000/S1100 series");
        NAMES.put (AKAI_S3000, "Akai S3000/MPC2000 series");
        NAMES.put (ISO_9660, "ISO 9660");
        NAMES.put (ROLAND_S550_W30_DJ70, "Roland S550, W30, DJ70");
        NAMES.put (ROLAND_S7XX, "Roland S7xx");
        NAMES.put (UNKNOWN, "Unknown Format");
    }


    /**
     * Get the more readable name for the given format.
     *
     * @param isoFormat The format for which to get the name
     * @return The name
     */
    public static String getName (final IsoFormat isoFormat)
    {
        return NAMES.get (isoFormat);
    }
}
