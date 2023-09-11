// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;


/**
 * A loop for a zone. A zone can have 1-8 loops.
 *
 * @author Jürgen Moßgraber
 */
public class ZoneLoop
{
    /** Until End. */
    public static final int MODE_UNTIL_END         = 0x1;
    /** Until End <->. */
    public static final int MODE_UNTIL_END_ALT     = 0x1006000;
    /** Until Release. */
    public static final int MODE_UNTIL_RELEASE     = 0x0;
    /** Until Release <->. */
    public static final int MODE_UNTIL_RELEASE_ALT = 0x3F80;
    /** One shot. */
    public static final int MODE_ONESHOT           = 0x80000001;

    private int             mode;
    private int             loopStart;
    private int             loopLength;
    private int             loopCount;
    private int             alternatingLoop;
    private float           loopTuning;
    private int             xFadeLength;


    /**
     * Parse the loop data.
     *
     * @param in Where to read the data from
     * @throws IOException Could not read the loop
     */
    public void parse (final ByteArrayInputStream in) throws IOException
    {
        this.mode = StreamUtils.readSigned32 (in, false);
        this.loopStart = (int) StreamUtils.readUnsigned32 (in, false);
        this.loopLength = (int) StreamUtils.readUnsigned32 (in, false);
        this.loopCount = (int) StreamUtils.readUnsigned32 (in, false);
        this.alternatingLoop = in.read ();
        this.loopTuning = StreamUtils.readFloatLE (in);
        this.xFadeLength = (int) StreamUtils.readUnsigned32 (in, false);
    }


    /**
     * Get the loop mode.
     *
     * @return The mode
     */
    public int getMode ()
    {
        return this.mode;
    }


    /**
     * Get the start of the loop in samples.
     *
     * @return The loop start
     */
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /**
     * Get the length of the loop in samples.
     *
     * @return The loop length
     */
    public int getLoopLength ()
    {
        return this.loopLength;
    }


    /**
     * Get the loop count.
     *
     * @return The loop count
     */
    public int getLoopCount ()
    {
        return this.loopCount;
    }


    /**
     * Is the loop alternating?
     *
     * @return 1 if alternating otherwise 0
     */
    public int getAlternatingLoop ()
    {
        return this.alternatingLoop;
    }


    /**
     * Get the loop tuning.
     *
     * @return The loop tuning
     */
    public float getLoopTuning ()
    {
        return this.loopTuning;
    }


    /**
     * Get the length of the crossfade in samples.
     *
     * @return The length of the crossfade in samples.
     */
    public int getCrossfadeLength ()
    {
        return this.xFadeLength;
    }
}
