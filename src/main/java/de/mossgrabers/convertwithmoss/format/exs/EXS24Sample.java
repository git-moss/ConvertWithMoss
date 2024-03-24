// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

/**
 * Structure for a EXS24 sample.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Sample
{
    int     id;
    String  name;
    int     waveDataStart;
    int     length;
    int     sampleRate;
    int     bitDepth;
    int     channels;
    int     channels2;
    String  type;
    int     size;
    boolean isCompressed;
    String  filePath;
    String  fileName;
}
