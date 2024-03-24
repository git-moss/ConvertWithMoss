// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

/**
 * Structure for a EXS24 zone.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Zone
{
    int     id;
    String  name;
    boolean pitch;
    boolean oneshot;
    boolean reverse;
    int     key;
    int     fineTuning;
    int     pan;
    int     volumeAdjust;
    int     volumeScale;
    int     coarseTuning;
    int     keyLow;
    int     keyHigh;
    boolean velocityRangeOn;
    int     velocityLow;
    int     velocityHigh;
    int     sampleStart;
    int     sampleEnd;
    int     loopStart;
    int     loopEnd;
    int     loopCrossfade;
    int     loopTune;
    boolean loopOn;
    boolean loopEqualPower;
    boolean loopPlayToEndOnRelease;
    int     loopDirection;
    int     flexOptions;
    int     flexSpeed;
    int     tailTune;
    int     output;
    int     groupIndex;
    int     sampleIndex;
    int     sampleFadeOut = 0;
    int     offset        = 0;
}
