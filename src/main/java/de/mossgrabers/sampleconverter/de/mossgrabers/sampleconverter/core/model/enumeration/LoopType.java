// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model.enumeration;

/**
 * The playback type of a loop in a sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
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
