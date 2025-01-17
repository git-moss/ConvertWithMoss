// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

/**
 * Possible YSFC version types.
 *
 * @author Jürgen Moßgraber
 */
public enum YamahaYsfcFileFormat
{
    /** 1.0.1 */
    MOTIF_XS("Motif XS", "1.0.1", "X0W", null),
    /** 1.0.2 */
    MOTIF_XF("Motif XF", "1.0.2", "X3W", null),
    /** 1.0.3 */
    MOXF("MOXF", "1.0.3", "X6W", null),
    /** 4.0.5 */
    MONTAGE("Montage", "4.0.5", "X7U", "X7L"),
    /** 4.1.0 */
    MONTAGE_M("Montage M", "4.1.0", "Y2U", "Y2L"),
    /** 5.0.1 */
    MODX("MODX", "5.0.1", "X8U", "X8L"),
    /** Unknown version. */
    UNKNOWN("Unknown", "", null, null);


    private final String title;
    private final String maxVersion;
    private final String endingLibrary;
    private final String endingUser;


    /**
     * Constructor.
     * 
     * @param title The title of the workstation model
     * @param maxVersion The maximum file format version number for this device in the form of x.x.x
     * @param endingUser The file ending for user banks of that type
     * @param endingLibrary The file ending for libraries of that type
     */
    private YamahaYsfcFileFormat (final String title, final String maxVersion, final String endingUser, final String endingLibrary)
    {
        this.title = title;
        this.maxVersion = maxVersion;
        this.endingUser = endingUser;
        this.endingLibrary = endingLibrary;
    }


    /**
     * Get the title.
     * 
     * @return The title
     */
    public String getTitle ()
    {
        return this.title;
    }


    /**
     * Get the maximum file format version number of this workstation model.
     * 
     * @return The maximum version number in the form of x.x.x
     */
    public String getMaxVersion ()
    {
        return this.maxVersion;
    }


    /**
     * Get the file ending for libraries or user banks of that type.
     * 
     * @param isUser If true return the ending for user banks otherwise for libraries
     * @return The file ending
     */
    public String getEnding (final boolean isUser)
    {
        return isUser ? this.endingUser : this.endingLibrary;
    }


    /**
     * Get the file ending for libraries of that type.
     * 
     * @return The file ending for libraries of that type
     */
    public String getEndingLibrary ()
    {
        return this.endingLibrary;
    }


    /**
     * Returns true if this is a version 1.0.x version.
     * 
     * @return True if version 1
     */
    public boolean isVersion1 ()
    {
        return this.isMotif () || isMOXF ();
    }


    /**
     * Returns true if this is a version 1.0.x version.
     * 
     * @return True if version 1
     */
    public boolean isMOXF ()
    {
        return this == MOXF;
    }


    /**
     * Returns true if this is a Motif 1.0.x version.
     * 
     * @return True if Motif version
     */
    public boolean isMotif ()
    {
        return this == MOTIF_XF || this == MOTIF_XS;
    }


    /**
     * Get the constant from the 3 digit version number.
     * 
     * @param version The version, e.g. version 4.0.5 is 405.
     * @return The enumeration constant
     */
    public static YamahaYsfcFileFormat get (final int version)
    {
        if (version <= 101)
            return MOTIF_XS;
        if (version == 102)
            return MOTIF_XF;
        if (version == 103)
            return MOXF;
        if (version >= 400 && version < 410)
            return MONTAGE;
        if (version >= 410 && version < 420)
            return MONTAGE_M;
        if (version >= 500 && version < 510)
            return MODX;
        return UNKNOWN;
    }
}
