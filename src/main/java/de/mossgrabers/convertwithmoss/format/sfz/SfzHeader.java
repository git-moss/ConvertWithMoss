// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

/**
 * The SFZ format consists of a number of headers. See https://sfzformat.com
 *
 * @author Jürgen Moßgraber
 */
public class SfzHeader
{
    /** SFZ v1. An instrument is defined by one or more regions. */
    public static final String REGION  = "region";
    /** SFZ v1. Multiple regions can be arranged in a group. */
    public static final String GROUP   = "group";
    /** SFZ v2. Global control parameters. */
    public static final String CONTROL = "control";
    /** SFZ v2. Allows entering parameters which are common for all regions. */
    public static final String GLOBAL  = "global";
    /** SFZ v2. A header for defining curves for MIDI CC controls. */
    public static final String CURVE   = "curve";
    /** SFZ v2. Effects controls. */
    public static final String EFFECT  = "effect";
    /** ARIA. An intermediate level in the header hierarchy, between global and group. */
    public static final String MASTER  = "master";
    /** ARIA. MIDI pre-processor effects. */
    public static final String MIDI    = "midi";
    /** Cakewalk. Allows to embed sample data directly in SFZ files (Rapture). */
    public static final String SAMPLE  = "sample";


    /**
     * Private constructor for utility class.
     */
    private SfzHeader ()
    {
        // Intentionally empty
    }
}
