// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.util.Objects;


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
    private final int     sizeBytes;
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
        this (data, cylinder, head, sectorNumber, 128 << sizeCode, crcValid);
    }


    /**
     * Create a sector for a format which does not use the IBM size code, e.g. the E-mu Emulator II
     * which stores a single sector of 3584 bytes per track - a size which cannot be expressed as
     * 128 shifted left by a size code.
     *
     * @param cylinder The cylinder/track number
     * @param head The head/side number
     * @param sectorNumber The sector number
     * @param sizeBytes The size of the sector in bytes
     * @param data The sector data
     * @param crcValid Whether the CRC check passed
     * @return The created sector
     */
    public static Sector createWithSize (final int cylinder, final int head, final int sectorNumber, final int sizeBytes, final byte [] data, final boolean crcValid)
    {
        return new Sector (data, cylinder, head, sectorNumber, sizeBytes, crcValid);
    }


    private Sector (final byte [] data, final int cylinder, final int head, final int sectorNumber, final int sizeBytes, final boolean crcValid)
    {
        this.cylinder = cylinder;
        this.head = head;
        this.sectorNumber = sectorNumber;
        this.sizeBytes = sizeBytes;
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
     * @return 128, 256, 512, 1024 bytes for IBM formatted sectors, otherwise the explicitly given
     *         sector size
     */
    public int getSizeBytes ()
    {
        return this.sizeBytes;
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
    public int hashCode ()
    {
        return Objects.hash (Integer.valueOf (this.cylinder), Integer.valueOf (this.head), Integer.valueOf (this.sectorNumber));
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if ((obj == null) || (this.getClass () != obj.getClass ()))
            return false;
        final Sector other = (Sector) obj;
        return this.cylinder == other.cylinder && this.head == other.head && this.sectorNumber == other.sectorNumber;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return String.format ("C:%d H:%d S:%d Size:%d CRC:%s", Integer.valueOf (this.cylinder), Integer.valueOf (this.head), Integer.valueOf (this.sectorNumber), Integer.valueOf (this.getSizeBytes ()), this.crcValid ? "OK" : "ERR");
    }
}