// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

/**
 * Relative markers for positioning the read-position.
 *
 * @author Jürgen Moßgraber
 */
public enum AkaiStreamWhence
{
    /** Position the read-position relative (forward) to the start. */
    START,
    /** Position the read-position relative to the current position (backwards/forwards). */
    CURRENT_POSITION,
    /** Position the read-position relative (backwards) to the end. */
    END
}