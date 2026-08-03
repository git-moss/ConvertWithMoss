// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.caf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A marker in a CAF Marker or Region chunk.
 *
 * @author Jürgen Moßgraber
 */
public class CafMarker
{
    /** A generic marker. */
    public static final String TYPE_GENERIC            = "\0\0\0\0";
    /** The start of a region. */
    public static final String TYPE_REGION_START       = "rbeg";
    /** The end of a region. */
    public static final String TYPE_REGION_END         = "rend";
    /** The start of a sustain loop. */
    public static final String TYPE_SUSTAIN_LOOP_START = "slbg";
    /** The end of a sustain loop. */
    public static final String TYPE_SUSTAIN_LOOP_END   = "slen";
    /** The start of a release loop. */
    public static final String TYPE_RELEASE_LOOP_START = "rlbg";
    /** The end of a release loop. */
    public static final String TYPE_RELEASE_LOOP_END   = "rlen";

    String                     type                    = TYPE_GENERIC;
    double                     framePosition;
    long                       markerID;
    long                       channel;


    /**
     * Default constructor.
     */
    public CafMarker ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     *
     * @param type The type of the marker, one of the TYPE_* constants
     * @param framePosition The frame position of the marker
     * @param markerID The unique ID of the marker
     */
    public CafMarker (final String type, final double framePosition, final long markerID)
    {
        this.type = type;
        this.framePosition = framePosition;
        this.markerID = markerID;
    }


    /**
     * Read the marker data.
     *
     * @param in The input stream to read from
     * @throws IOException Could not read the data
     */
    public void read (final InputStream in) throws IOException
    {
        this.type = StreamUtils.readAscii (in, 4);
        this.framePosition = StreamUtils.readDouble (in, true);
        this.markerID = StreamUtils.readUnsigned32 (in, true);
        // The SMPTE time is not used
        in.skipNBytes (8);
        this.channel = StreamUtils.readUnsigned32 (in, true);
    }


    /**
     * Write the marker data.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final OutputStream out) throws IOException
    {
        out.write (this.type.getBytes ());
        StreamUtils.writeDouble (out, this.framePosition, true);
        StreamUtils.writeUnsigned32 (out, this.markerID, true);
        // An unused SMPTE time must have all bytes set to 0xFF
        final byte [] smpteTime = new byte [8];
        Arrays.fill (smpteTime, (byte) 0xFF);
        out.write (smpteTime);
        StreamUtils.writeUnsigned32 (out, this.channel, true);
    }


    /**
     * Get the type of the marker.
     *
     * @return One of the TYPE_* constants or an application defined four character code
     */
    public String getType ()
    {
        return this.type;
    }


    /**
     * Get the frame position of the marker.
     *
     * @return The frame position
     */
    public double getFramePosition ()
    {
        return this.framePosition;
    }


    /**
     * Get the unique ID of the marker.
     *
     * @return The ID, zero if the marker has no ID
     */
    public long getMarkerID ()
    {
        return this.markerID;
    }


    /**
     * Get the channel to which the marker refers.
     *
     * @return The channel number, zero means all channels
     */
    public long getChannel ()
    {
        return this.channel;
    }
}
