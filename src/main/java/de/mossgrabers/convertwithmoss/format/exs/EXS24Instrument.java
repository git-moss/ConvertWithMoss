// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

/**
 * Structure for a EXS24 instrument.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Instrument
{
    String name;

    int    numZoneBlocks;
    int    numGroupBlocks;
    int    numSampleBlocks;
    int    numParameterBlocks;
}
