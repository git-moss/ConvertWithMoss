// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * An AIFF loop as used in the Instrument chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AiffLoop
{
    /** Loop is off. */
    public static final int NO_LOOPING               = 0;
    /** Loop is forward. */
    public static final int FORWARD_LOOPING          = 1;
    /** Loop is alternating. */
    public static final int FORWARD_BACKWARD_LOOPING = 2;

    int                     playMode                 = NO_LOOPING;
    int                     beginLoopMarkerID;
    int                     endLoopMarkerID;


    /**
     * Read a loop.
     * 
     * @param in The input stream to read from
     * @throws IOException Could not read
     */
    public void read (final InputStream in) throws IOException
    {
        this.playMode = StreamUtils.readUnsigned16 (in, true);
        this.beginLoopMarkerID = StreamUtils.readUnsigned16 (in, true);
        this.endLoopMarkerID = StreamUtils.readUnsigned16 (in, true);
    }
}
