// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

/**
 * One slot in a LAND-type (hard-drive or CD-ROM) directory. Each slot occupies 64 bytes; up to 50
 * printable ASCII characters are read as the contained disk name.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxDirectoryEntry
{
    private final int    slot; // 1-based
    private final String name; // up to 50 printable ASCII chars


    /**
     * Constructor.
     * 
     * @param slot The 1-based slot number within the directory
     * @param name The name of the directory
     */
    public S5xxDirectoryEntry (final int slot, final String name)
    {
        this.slot = slot;
        this.name = name == null ? "" : name;
    }


    /**
     * Get the slot number.
     * 
     * @return 1-based slot number within the directory
     */
    public int getSlot ()
    {
        return this.slot;
    }


    /**
     * Get the disk name.
     * 
     * @return Up to 50 printable ASCII characters
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Check if the directory entry is empty.
     * 
     * @return {@code true} if the name is blank (empty / whitespace only)
     */
    public boolean isEmpty ()
    {
        return this.name.isBlank ();
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return String.format ("DirectoryEntry{slot=%3d, name=\"%s\"}", Integer.valueOf (this.slot), this.name);
    }
}