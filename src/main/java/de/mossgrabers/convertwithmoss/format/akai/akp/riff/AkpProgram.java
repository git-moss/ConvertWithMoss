// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;


/**
 * A S5000/S6000 program chunk.
 *
 * @author Jürgen Moßgraber
 */
public class AkpProgram extends AbstractAkpChunk
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
        super (AkpRiffChunkId.PRG_ID, LENGTH_PRG, 1);
    }


    /**
     * Get the MIDI program number of the preset.
     *
     * @return The program number, 0 = OFF
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
     * @return The number of key-groups in the range of [1..99]
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


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        this.programNumber = getUnsigned (data, 1);
        this.numberOfKeygroups = getUnsigned (data, 2);
    }


    /** {@inheritDoc} */
    @Override
    protected void writeDefaults (final byte [] data, final int targetVersion)
    {
        // Undocumented but always 2
        data[0x04] = 2;
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        setByte (data, 1, this.programNumber);
        setByte (data, 2, this.numberOfKeygroups);
    }
}
