// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.caf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A region in a CAF Region chunk. A region marks a range of the audio data, e.g. a loop.
 *
 * @author Jürgen Moßgraber
 */
public class CafRegion
{
    /** The region is looped. */
    public static final long      FLAG_LOOP_ENABLE   = 1;
    /** The region is played forward. */
    public static final long      FLAG_PLAY_FORWARD  = 2;
    /** The region is played backward. */
    public static final long      FLAG_PLAY_BACKWARD = 4;

    long                          regionID;
    long                          flags;
    private final List<CafMarker> markers            = new ArrayList<> ();


    /**
     * Read the region data.
     *
     * @param in The input stream to read from
     * @throws IOException Could not read the data
     */
    public void read (final InputStream in) throws IOException
    {
        this.regionID = StreamUtils.readUnsigned32 (in, true);
        this.flags = StreamUtils.readUnsigned32 (in, true);
        final long numberOfMarkers = StreamUtils.readUnsigned32 (in, true);
        for (long i = 0; i < numberOfMarkers; i++)
        {
            final CafMarker marker = new CafMarker ();
            marker.read (in);
            this.markers.add (marker);
        }
    }


    /**
     * Write the region data.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.regionID, true);
        StreamUtils.writeUnsigned32 (out, this.flags, true);
        StreamUtils.writeUnsigned32 (out, this.markers.size (), true);
        for (final CafMarker marker: this.markers)
            marker.write (out);
    }


    /**
     * Get the unique ID of the region.
     *
     * @return The ID
     */
    public long getRegionID ()
    {
        return this.regionID;
    }


    /**
     * Set the unique ID of the region.
     *
     * @param regionID The ID
     */
    public void setRegionID (final long regionID)
    {
        this.regionID = regionID;
    }


    /**
     * Get the flags of the region.
     *
     * @return A combination of the FLAG_* constants
     */
    public long getFlags ()
    {
        return this.flags;
    }


    /**
     * Set the flags of the region.
     *
     * @param flags A combination of the FLAG_* constants
     */
    public void setFlags (final long flags)
    {
        this.flags = flags;
    }


    /**
     * Get the markers which belong to the region.
     *
     * @return The markers
     */
    public List<CafMarker> getMarkers ()
    {
        return this.markers;
    }


    /**
     * Get the smallest frame position of all markers of the region.
     *
     * @return The frame position, -1 if there are no markers
     */
    public double getStartPosition ()
    {
        double start = -1;
        for (final CafMarker marker: this.markers)
        {
            if (CafMarker.TYPE_REGION_START.equals (marker.getType ()) || CafMarker.TYPE_SUSTAIN_LOOP_START.equals (marker.getType ()))
                return marker.getFramePosition ();
            if (start < 0 || marker.getFramePosition () < start)
                start = marker.getFramePosition ();
        }
        return start;
    }


    /**
     * Get the largest frame position of all markers of the region.
     *
     * @return The frame position, -1 if there are no markers
     */
    public double getEndPosition ()
    {
        double end = -1;
        for (final CafMarker marker: this.markers)
        {
            if (CafMarker.TYPE_REGION_END.equals (marker.getType ()) || CafMarker.TYPE_SUSTAIN_LOOP_END.equals (marker.getType ()))
                return marker.getFramePosition ();
            if (marker.getFramePosition () > end)
                end = marker.getFramePosition ();
        }
        return end;
    }
}
