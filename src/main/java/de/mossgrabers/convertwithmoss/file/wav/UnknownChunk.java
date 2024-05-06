// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * Class for an unknown/unsupported chunk.
 *
 * @author Jürgen Moßgraber
 */
public class UnknownChunk extends RIFFChunk
{
    /**
     * Constructor.
     *
     * @param riffId The RIFF id
     * @param chunk The RIFF chunk which contains the data
     */
    public UnknownChunk (final RiffID riffId, final RIFFChunk chunk)
    {
        super (riffId, chunk.getData (), chunk.getData ().length);
    }
}
