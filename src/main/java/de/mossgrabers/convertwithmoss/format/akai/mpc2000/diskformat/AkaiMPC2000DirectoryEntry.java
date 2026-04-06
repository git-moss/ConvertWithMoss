// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000.diskformat;

/**
 * Directory Entry structure.
 * 
 * @author Jürgen Moßgraber
 */
public class AkaiMPC2000DirectoryEntry
{
    String  name;
    String  extension;
    int     attributes;
    int     firstCluster;
    int     fileSize;
    boolean isDeleted;
    boolean isDirectory;
    boolean isVolumeLabel;


    /**
     * Get the name of the entry.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the file name extension.
     * 
     * @return The extension
     */
    public String getExtension ()
    {
        return this.extension;
    }


    /**
     * Get the full filename.
     * 
     * @return The filename
     */
    public String getFullName ()
    {
        if (this.isVolumeLabel || this.extension.isEmpty ())
            return this.name;
        return this.name + "." + this.extension;
    }


    /** Dump the name and type. */
    @Override
    public String toString ()
    {
        final String type = this.isDirectory ? "DIR " : "FILE";
        final String status = this.isDeleted ? "(DEL)" : "     ";
        return String.format ("%-12s %5s %10d bytes  cluster=%4d %s", this.getFullName (), type, Integer.valueOf (this.fileSize), Integer.valueOf (this.firstCluster), status);
    }
}
