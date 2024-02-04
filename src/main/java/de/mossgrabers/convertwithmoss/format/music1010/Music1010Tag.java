// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

/**
 * The 1010music preset format consists of several XML tags.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010Tag
{
    /** The root tag. */
    public static final String ROOT                       = "document";
    /** The session tag. */
    public static final String SESSION                    = "session";
    /** The cell tag. */
    public static final String CELL                       = "cell";
    /** The parameters tag. */
    public static final String PARAMS                     = "params";

    /** The version attribute. */
    public static final String ATTR_VERSION               = "version";

    /** The row attribute. */
    public static final String ATTR_ROW                   = "row";
    /** The column attribute. */
    public static final String ATTR_COLUMN                = "column";
    /** The layer attribute. */
    public static final String ATTR_LAYER                 = "layer";
    /** The filename attribute. */
    public static final String ATTR_FILENAME              = "filename";
    /** The type attribute. */
    public static final String ATTR_TYPE                  = "type";

    /** The interpolation quality attribute. */
    public static final String ATTR_INTERPOLATION_QUALITY = "interpqual";

    /** The root note attribute. */
    public static final String ATTR_ROOT_NOTE             = "rootnote";
    /** The low note attribute. */
    public static final String ATTR_LO_NOTE               = "keyrangebottom";
    /** The top note attribute. */
    public static final String ATTR_HI_NOTE               = "keyrangetop";
    /** The velocity range bottom attribute. */
    public static final String ATTR_LO_VEL                = "velrangebottom";
    /** The velocity range top attribute. */
    public static final String ATTR_HI_VEL                = "velrangetop";

    /** The asset source row attribute. */
    public static final String ATTR_ASSET_SOURCE_ROW      = "asssrcrow";
    /** The asset source column attribute. */
    public static final String ATTR_ASSET_SOURCE_COLUMN   = "asssrccol";


    /**
     * Private constructor for utility class.
     */
    private Music1010Tag ()
    {
        // Intentionally empty
    }
}
