// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sxt;

/**
 * IFF IDs used in the SXT format.
 *
 * @author Jürgen Moßgraber
 */
public class SxtChunkConstants
{
    /** ID for a patch chunk. */
    public static final String PATCH         = "PTCH";

    /** ID for a a CAT type containing zero or more 'REFE' chunks. */
    public static final String REFERENCES    = "REFS";
    /** ID for a chunk containing a reference to a sample. */
    public static final String REFERENCE     = "REFE";
    /** ID for a used to identify the device for this patch (NNXT Digital Sampler). */
    public static final String DESC          = "DESC";
    /** ID for an optional chunk used to identify the author of the patch. */
    public static final String AUTHOR        = "AUTH";
    /** ID for global parameters (those on the NN-XTs main panel). */
    public static final String PARAMETERS    = "PARM";
    /** ID for a chunk which contains the zone and group parameters. */
    public static final String BODY          = "BODY";

    /** Version 1.0.0 as integer. */
    public static final int    VERSION_1_0_0 = 1000000;
    /** Version 1.1.0 as integer. */
    public static final int    VERSION_1_1_0 = 1001000;
    /** Version 1.2.0 as integer. */
    public static final int    VERSION_1_2_0 = 1002000;
    /** Version 1.3.0 as integer. */
    public static final int    VERSION_1_3_0 = 1003000;
    /** Version 1.4.0 as integer. */
    public static final int    VERSION_1_4_0 = 1004000;
    /** Version 1.5.0 as integer. */
    public static final int    VERSION_1_5_0 = 1005000;
    /** Version 1.8.0 as integer. */
    public static final int    VERSION_1_8_0 = 1008000;
    /** Version 1.9.0 as integer. */
    public static final int    VERSION_1_9_0 = 1009000;
    /** Version 2.0.0 as integer. */
    public static final int    VERSION_2_0_0 = 2000000;
    /** Version 2.2.0 as integer. */
    public static final int    VERSION_2_2_0 = 2002000;
    /** Version 3.0.0 as integer. */
    public static final int    VERSION_3_0_0 = 3000000;
    /** Version 4.1.0 as integer. */
    public static final int    VERSION_4_1_0 = 4001000;


    /**
     * Private due to utility class.
     */
    private SxtChunkConstants ()
    {
        // Intentionally empty
    }
}
