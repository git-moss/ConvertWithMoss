// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

/**
 * Possible YSFC versions.
 *
 * @author Jürgen Moßgraber
 */
public enum YamahaYsfcVersion
{
    /** 1.0.1 */
    MOTIF_XS("Motif XS"),
    /** 1.0.2 */
    MOTIF_XF("Motif XF"),
    /** 1.0.3 */
    MOXF("MOXF"),
    /** 4.0.4 */
    MONTAGE("Montage"),
    /** 4.1.0 */
    MONTAGE_M("Montage M"),
    /** 5.0.1 */
    MODX("MODX"),
    /** Unknown version. */
    UNKNOWN("Unknown");


    private final String title;


    private YamahaYsfcVersion (final String title)
    {
        this.title = title;
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
     * Returns true if this is a version 1.0.x version.
     * 
     * @return True if version 1
     */
    public boolean isVersion1 ()
    {
        return this.isMotif () || this == MOXF;
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
    public static YamahaYsfcVersion get (final int version)
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
