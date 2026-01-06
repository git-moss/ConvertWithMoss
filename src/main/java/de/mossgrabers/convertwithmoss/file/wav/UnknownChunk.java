// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * Class for an unknown/unsupported chunk.
 *
 * @author Jürgen Moßgraber
 */
public class UnknownChunk extends AbstractSpecificRIFFChunk
{
    /**
     * Constructor.
     *
     * @param riffId The RIFF id
     * @param chunk The RIFF chunk which contains the data
     * @throws ParseException The raw chunk is not of the specific type or the length of data does
     *             not match the expected chunk size
     */
    public UnknownChunk (final RiffID riffId, final RawRIFFChunk chunk) throws ParseException
    {
        super (riffId, chunk);
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        return "Unknown Data";
    }
}
