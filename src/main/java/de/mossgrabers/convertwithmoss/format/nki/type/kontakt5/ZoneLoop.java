// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A loop for a zone. A zone can have 1-8 loops.
 *
 * @author Jürgen Moßgraber
 */
public class ZoneLoop
{
    /** Until End. */
    public static final int MODE_UNTIL_END     = 1;
    /** Until Release. */
    public static final int MODE_UNTIL_RELEASE = 2;
    /** One shot. */
    public static final int MODE_ONESHOT       = 3;

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
        // Padding, except after last loop
        if (in.available () > 0)
            in.skipNBytes (1);
    }


    /**
     * Get the loop mode. See the defined constants above.
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
    public int isAlternating ()
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
     * Get the length of the cross-fade in samples.
     *
     * @return The length of the cross-fade in samples.
     */
    public int getCrossfadeLength ()
    {
        return this.xFadeLength;
    }
}
