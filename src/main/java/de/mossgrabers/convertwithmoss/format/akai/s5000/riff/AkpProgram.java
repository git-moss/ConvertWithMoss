// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * A S5000/S6000 program chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkpProgram extends AbstractSpecificRIFFChunk
{
    /** The length of the Program structure. */
    private static final int LENGTH_PRG        = 6;

    private int              programNumber     = 0;
    private int              numberOfKeygroups = 0;


    /**
     * Default constructor.
     */
    public AkpProgram ()
    {
        super (AkpRiffChunkId.APRG_ID, LENGTH_PRG);
    }


    /**
     * Get the program number of the preset in the bank.
     *
     * @return The program number
     */
    public int getProgramNumber ()
    {
        return this.programNumber;
    }


    /**
     * Set the program number of the preset in the bank.
     *
     * @param programNumber The program number
     */
    public void setProgramNumber (final int programNumber)
    {
        this.programNumber = programNumber;
    }


    /**
     * Get the number of key-groups.
     *
     * @return The bank index
     */
    public int getNumberOfKeygroups ()
    {
        return this.numberOfKeygroups;
    }


    /**
     * Set the number of key-groups.
     *
     * @param numberOfKeygroups The number of key-groups
     */
    public void setNumberOfKeygroups (final int numberOfKeygroups)
    {
        this.numberOfKeygroups = numberOfKeygroups;
    }


    /**
     * Reads the data from a chunk.
     *
     * @param chunk The chunk to read
     * @throws ParseException Could not read the data
     */
    public void read (final RawRIFFChunk chunk) throws ParseException
    {
        this.programNumber = chunk.getByteAsUnsignedInt (1);
        this.numberOfKeygroups = chunk.getByteAsUnsignedInt (2);
    }


    /**
     * Write the data to a stream.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final ByteArrayOutputStream out) throws IOException
    {
        out.write (0);
        out.write (this.programNumber);
        out.write (this.numberOfKeygroups);
        out.write (0);
        out.write (0);
        out.write (0);
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        // Not used
        return "";
    }
}
