// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

/**
 * Waldorf Quantum/Iridium resource types.
 *
 * @author Jürgen Moßgraber
 */
public enum WaldorfQpatResourceType
{
    /** An empty unused resource. */
    UNUSED,
    /** The 1st wave-table resource. */
    USER_WAVE_TABLE1,
    /** The 2nd wave-table resource. */
    USER_WAVE_TABLE2,
    /** The 3rd wave-table resource. */
    USER_WAVE_TABLE3,
    /** The 1st sample-map resource. */
    USER_SAMPLE_MAP1,
    /** The 2nd sample-map resource. */
    USER_SAMPLE_MAP2,
    /** The 3rd sample-map resource. */
    USER_SAMPLE_MAP3
}
