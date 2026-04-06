// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

/**
 * Represents a single disk sector with metadata and data.
 *
 * @author Jürgen Moßgraber
 */
public class Sector implements Comparable<Sector>
{
    private final int     cylinder;
    private final int     head;
    private final int     sectorNumber;
    private final int     sizeCode;
    private final byte [] data;
    private boolean       crcValid;


    /**
     * Constructor.
     * 
     * @param cylinder The cylinder/track number
     * @param head The head/side number
     * @param sectorNumber The sector number
     * @param sizeCode The size code (0=128, 1=256, 2=512, 3=1024 bytes)
     * @param data The sector data
     * @param crcValid Whether the CRC check passed
     */
    public Sector (final int cylinder, final int head, final int sectorNumber, final int sizeCode, final byte [] data, final boolean crcValid)
    {
        this.cylinder = cylinder;
        this.head = head;
        this.sectorNumber = sectorNumber;
        this.sizeCode = sizeCode;
        this.data = data;
        this.crcValid = crcValid;
    }


    /**
     * Sets if the CRC is valid.
     * 
     * @param crcValid True to set it to valid
     */
    public void setCrcValid (final boolean crcValid)
    {
        this.crcValid = crcValid;
    }


    /**
     * Get the cylinder of the sector.
     * 
     * @return The cylinder
     */
    public int getCylinder ()
    {
        return this.cylinder;
    }


    /**
     * Get the head of the sector.
     * 
     * @return The head
     */
    public int getHead ()
    {
        return this.head;
    }


    /**
     * Get the number of the sector.
     * 
     * @return The number
     */
    public int getSectorNumber ()
    {
        return this.sectorNumber;
    }


    /**
     * Get the size bytes.
     * 
     * @return 128, 256, 512, 1024 bytes
     */
    public int getSizeBytes ()
    {
        return 128 << this.sizeCode;
    }


    /**
     * Get the data of the sector.
     * 
     * @return The data
     */
    public byte [] getData ()
    {
        return this.data;
    }


    /**
     * Is the CRC valid?
     * 
     * @return True if valid
     */
    public boolean isCrcValid ()
    {
        return this.crcValid;
    }


    /** {@inheritDoc} */
    @Override
    public int compareTo (final Sector other)
    {
        if (this.cylinder != other.cylinder)
            return Integer.compare (this.cylinder, other.cylinder);
        if (this.head != other.head)
            return Integer.compare (this.head, other.head);
        return Integer.compare (this.sectorNumber, other.sectorNumber);
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return String.format ("C:%d H:%d S:%d Size:%d CRC:%s", Integer.valueOf (this.cylinder), Integer.valueOf (this.head), Integer.valueOf (this.sectorNumber), Integer.valueOf (this.getSizeBytes ()), this.crcValid ? "OK" : "ERR");
    }
}