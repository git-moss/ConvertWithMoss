// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.enumeration;

/**
 * The playback type of a loop in a sample.
 *
 * @author Jürgen Moßgraber
 */
public enum LoopType
{
    /** Normal forward loop. */
    FORWARD,
    /** Loop backward (reverse). */
    BACKWARDS,
    /** Loop forward/backward, also known as Ping-Pong. */
    ALTERNATING
}
