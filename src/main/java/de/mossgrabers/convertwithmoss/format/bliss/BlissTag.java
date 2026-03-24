// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bliss;

/**
 * The Bliss format consists of several XML tags.
 *
 * @author Jürgen Moßgraber
 */
public class BlissTag
{
    /** The bank root tag. */
    public static final String BANK             = "bank";
    /** The programs tag. */
    public static final String PROGRAMS         = "programs";
    /** The root tag. */
    public static final String PROGRAM          = "program";
    /** The zones tag. */
    public static final String ZONES            = "zones";
    /** The zone tag. */
    public static final String ZONE             = "zone";
    /** The lower input range tag. */
    public static final String LOW_INPUT_RANGE  = "lo_input_range";
    /** The high input range tag. */
    public static final String HIGH_INPUT_RANGE = "hi_input_range";


    /**
     * Private constructor for utility class.
     */
    private BlissTag ()
    {
        // Intentionally empty
    }
}
