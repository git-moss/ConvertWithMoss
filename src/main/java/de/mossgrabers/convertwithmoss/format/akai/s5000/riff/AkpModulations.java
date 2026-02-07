// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000.riff;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * A S5000/S6000 mods chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkpModulations extends AbstractSpecificRIFFChunk
{
    /** The length of the Output structure. */
    private static final int LENGTH_OUT = 38;

    private byte []          data;


    /**
     * Default constructor.
     */
    public AkpModulations ()
    {
        super (AkpRiffChunkId.MODS_ID, LENGTH_OUT);
    }


    /**
     * Get a value from the data.
     * 
     * @param position First data item is at 0x78
     * @return The data value
     */
    public int getUnsignedValue (final int position)
    {
        return Byte.toUnsignedInt (this.data[position - 0x78]);
    }


    /**
     * Reads the data from a chunk.
     *
     * @param chunk The chunk to read
     * @throws ParseException Could not read the data
     */
    public void read (final RawRIFFChunk chunk) throws ParseException
    {
        if (chunk.getSize () != LENGTH_OUT)
            throw new ParseException ("Unexpected size of Mods chunk: " + chunk.getSize ());

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
