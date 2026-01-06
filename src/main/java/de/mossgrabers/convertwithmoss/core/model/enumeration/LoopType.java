// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.enumeration;

/**
 * The play-back type of a loop in a sample.
 *
 * @author Jürgen Moßgraber
 */
public enum LoopType
{
    /** Normal forward loop. */
    FORWARDS,
    /** Loop backward (reverse). */
    BACKWARDS,
    /** Loop forward/backward, also known as Ping-Pong. */
    ALTERNATING
}
