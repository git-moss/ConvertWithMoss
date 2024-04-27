// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.iff.IffChunk;


/**
 * An AIFF Marker Chunk as defined in the AIFF specification.
 *
 * @author Jürgen Moßgraber
 */
public class AiffMarkerChunk extends AiffChunk
{
    final Map<Integer, AiffMarker> markers = new TreeMap<> ();


    /**
     * Constructor.
     * 
     * @param chunk The IFF chunk
     */
    protected AiffMarkerChunk (final IffChunk chunk)
    {
        super (chunk);
    }


    /**
     * Get the markers.
     * 
     * @return The markers
     */
    public Map<Integer, AiffMarker> getMarkers ()
    {
        return this.markers;
    }


    /**
     * Read the AIFF Common chunk data.
     *
     * @param chunk The chunk to read from
     * @throws IOException Could not read the data
     */
    public void read (final IffChunk chunk) throws IOException
    {
        try (final InputStream in = chunk.streamData ())
        {
            final int numMarkers = StreamUtils.readUnsigned16 (in, true);
            for (int i = 0; i < numMarkers; i++)
            {
                final AiffMarker marker = new AiffMarker ();
                final int markerID = StreamUtils.readUnsigned16 (in, true);
                marker.position = StreamUtils.readUnsigned32 (in, true);
                marker.name = StreamUtils.readWithLengthAscii (in);
                if ((marker.name.length () + 1) % 2 == 1)
                    in.skipNBytes (1);
                this.markers.put (Integer.valueOf (markerID), marker);
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        for (final Map.Entry<Integer, AiffMarker> entry: this.markers.entrySet ())
        {
            if (sb.length () > 0)
                sb.append ('\n');
            final AiffMarker marker = entry.getValue ();
            sb.append ("Marker ").append (entry.getKey ()).append (": ").append (marker.position).append (" - ").append (marker.name);
        }
        return sb.toString ();
    }
}
