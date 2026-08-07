// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.util.List;


/**
 * Interface for abstracting from different (M)FM decoders.
 *
 * @author Jürgen Moßgraber
 */
public interface IDecoder
{
    /**
     * Decodes (M)FM encoded data.
     *
     * @param trackData The tracks to decode
     * @param cylinder The number of cylinders of the disk
     * @param head The side
     * @return The decoded sectors
     */
    List<Sector> decodeSectors (TrackData trackData, int cylinder, int head);
}
