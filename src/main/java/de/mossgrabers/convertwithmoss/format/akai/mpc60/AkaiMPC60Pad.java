// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc60;

import de.mossgrabers.convertwithmoss.core.model.ISampleData;


/**
 * The data of a pad in a Akai MPC60 SET file.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC60Pad
{
    String      name;
    ISampleData sampleData;
    double      tuning = 0;

    /** The offset to the start of the sample in the concatenated-samples. */
    int         startInFrames;
    /** The length of the sample in frames. */
    int         lengthInFrames;
    /** The start of the play-back (absolute as well, not relative to start!). */
    int         playStartInFrames;
    /** The decay in [0..255] */
    int         attack;
    /** The decay in [0..255] */
    int         decay;
    /** The volume in [0..127] */
    int         volume;
    /** The panning in [0..127], 64 is center */
    int         panning;
}
