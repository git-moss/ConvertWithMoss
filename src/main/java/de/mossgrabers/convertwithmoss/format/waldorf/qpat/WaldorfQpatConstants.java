// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

/**
 * Waldorf Quantum/Iridium constants.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatConstants
{
    /** Magic number identifying a quantum patch. */
    public static final long MAGIC             = 3402932;

    /** Maximum length of strings. */
    public static final int  MAX_STRING_LENGTH = 32;

    /** The maximum number of resources integrated in the patch. */
    public static final int  MAX_RESOURCES     = 16;


    /**
     * Private due to constants class.
     */
    private WaldorfQpatConstants ()
    {
        // Intentionally empty
    }
}
