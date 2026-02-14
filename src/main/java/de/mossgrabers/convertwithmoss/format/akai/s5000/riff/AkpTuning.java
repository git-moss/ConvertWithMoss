// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000.riff;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * A S5000/S6000 tune chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkpTuning extends AbstractSpecificRIFFChunk
{
    /** The length of the Output structure. */
    private static final int LENGTH_OUT = 22;

    private byte []          data;


    /**
     * Default constructor.
     */
    public AkpTuning ()
    {
        super (AkpRiffChunkId.TUNE_ID, LENGTH_OUT);
    }


    /**
     * Get an unsigned value from the data.
     *
     * @param position First data item is at 0x32
     * @return The data value
     */
    public int getUnsignedValue (final int position)
    {
        return Byte.toUnsignedInt (this.data[position - 0x32]);
    }


    /**
     * Get a signed value from the data.
     *
     * @param position First data item is at 0x32
     * @return The data value
     */
    public int getValue (final int position)
    {
        return this.data[position - 0x32];
    }


    /**
     * Reads the data from a chunk.
     *
     * @param chunk The chunk to read
     * @throws ParseException Could not read the data
     */
    public void read (final RawRIFFChunk chunk) throws ParseException
    {
        this.data = chunk.getData ();
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        // Not used
        return "";
    }
}
