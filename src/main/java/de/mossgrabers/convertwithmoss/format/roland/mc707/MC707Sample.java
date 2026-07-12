// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mc707;

/**
 * One slot of a MC-707/MC-101 project's user-sample pool: the sample-parameter record fields plus
 * the audio of the matching <i>SMPd</i> chunk. The device stores every sample as interleaved
 * stereo 16-bit at 44.1 kHz; all positions are in stereo frames.
 *
 * @author Jürgen Moßgraber
 */
public class MC707Sample
{
    /** The sample name (the imported file name without its extension). */
    public String  name      = "";
    /** The original (root) key. */
    public int     rootKey   = 60;
    /** True to loop [loopStart, end], false to play the sample once. */
    public boolean hasLoop   = false;
    /** The level in the range of 0..127. */
    public int     level     = 127;
    /** The start point in frames. */
    public int     start     = 0;
    /** The loop start point in frames. */
    public int     loopStart = 0;
    /** The last played frame (inclusive; an untrimmed sample stores frame count - 1). */
    public int     end       = 0;
    /** The interleaved stereo 16-bit little-endian PCM at 44.1 kHz. */
    public byte [] pcm;
}
