// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
    private boolean         alternatingLoop;
    private float           loopTuning;
    private int             xFadeLength;


    /**
     * Parse the loop data.
     *
     * @param in Where to read the data from
     * @throws IOException Could not read the loop
     */
    public void read (final InputStream in) throws IOException
    {
        this.mode = StreamUtils.readSigned32 (in, false);
        this.loopStart = (int) StreamUtils.readUnsigned32 (in, false);
        this.loopLength = (int) StreamUtils.readUnsigned32 (in, false);
        this.loopCount = (int) StreamUtils.readUnsigned32 (in, false);
        this.alternatingLoop = in.read () > 0;
        this.loopTuning = StreamUtils.readFloatLE (in);
        this.xFadeLength = (int) StreamUtils.readUnsigned32 (in, false);
        // Padding, except after last loop
        if (in.available () > 0)
            in.skipNBytes (1);
    }


    /**
     * Parse the loop data.
     *
     * @param out Where to write the data to
     * @param isLast True if this is the last loop
     * @throws IOException Could not read the loop
     */
    public void write (final OutputStream out, final boolean isLast) throws IOException
    {
        StreamUtils.writeSigned32 (out, this.mode, false);
        StreamUtils.writeUnsigned32 (out, this.loopStart, false);
        StreamUtils.writeUnsigned32 (out, this.loopLength, false);
        StreamUtils.writeUnsigned32 (out, this.loopCount, false);
        out.write (this.alternatingLoop ? 1 : 0);
        StreamUtils.writeFloatLE (out, this.loopTuning);
        StreamUtils.writeUnsigned32 (out, this.xFadeLength, false);
        // Padding, except after last loop
        if (!isLast)
            out.write (0);
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
     * @return True if alternating
     */
    public boolean isAlternating ()
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
