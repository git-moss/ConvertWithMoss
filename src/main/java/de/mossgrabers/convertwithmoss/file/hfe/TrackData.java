// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

/**
 * Stores the data of a track.
 *
 * @author Jürgen Moßgraber
 */
public class TrackData
{
    private final byte [] data;


    /**
     * Constructor.
     *
     * @param data The data of the track
     */
    public TrackData (final byte [] data)
    {
        this.data = data;
    }


    /**
     * Get the data of the track.
     *
     * @return The data
     */
    public byte [] getData ()
    {
        return this.data;
    }
}