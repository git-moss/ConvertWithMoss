// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.korgmultisample;

/**
 * Tags and IDs used in the Korgmultisample format.
 *
 * @author Jürgen Moßgraber
 */
public class KorgmultisampleTag
{
    /** Tag for Korg. */
    public static final String TAG_KORG           = "Korg";
    /** Tag for File Info. */
    public static final String TAG_FILE_INFO      = "ExtendedFileInfo";
    /** Tag for Multisample. */
    public static final String TAG_MULTISAMPLE    = "MultiSample";
    /** Tag for Single Item. */
    public static final String TAG_SINGLE_ITEM    = "SingleItem";
    /** Tag for Sample Builder. */
    public static final String TAG_SAMPLE_BUILDER = "Sample Builder";

    /** ID for editor version. */
    public static final int    ID_VERSION         = 0x1A;
    /** ID for timestamp. */
    public static final int    ID_TIME            = 0x21;

    ////////////////////////////////////////////////////////////
    // Metadata
    ////////////////////////////////////////////////////////////

    /** ID for Author. */
    public static final int    ID_AUTHOR          = 0x12;
    /** ID for Category. */
    public static final int    ID_CATEGORY        = 0x1A;
    /** ID for Comment. */
    public static final int    ID_COMMENT         = 0x22;
    /** ID for Sample. */
    public static final int    ID_SAMPLE          = 0x2A;
    /** ID for UUID. */
    public static final int    ID_UUID            = 0x3A;

    ////////////////////////////////////////////////////////////
    // Sample
    ////////////////////////////////////////////////////////////

    /** ID for Sample start. */
    public static final int    ID_START           = 0x10;
    /** ID for Sample Loop start. */
    public static final int    ID_LOOP_START      = 0x18;
    /** ID for Sample end. */
    public static final int    ID_END             = 0x20;
    /** ID for Sample loop tuning start. */
    public static final int    ID_LOOP_TUNE       = 0x45;
    /** ID for Sample one-shot. */
    public static final int    ID_ONE_SHOT        = 0x48;
    /** ID for Sample volume boost. */
    public static final int    ID_BOOST_12DB      = 0x50;

    ////////////////////////////////////////////////////////////
    // Key Zone
    ////////////////////////////////////////////////////////////

    /** ID for Key Zone bottom key. */
    public static final int    ID_KEY_BOTTOM      = 0x10;
    /** ID for Key Zone top key. */
    public static final int    ID_KEY_TOP         = 0x18;
    /** ID for Key Zone root key. */
    public static final int    ID_KEY_ORIGINAL    = 0x20;
    /** ID for Key Zone fixed pitch. */
    public static final int    ID_FIXED_PITCH     = 0x28;
    /** ID for Key Zone sample tuning. */
    public static final int    ID_TUNE            = 0x35;
    /** ID for Key Zone level adjustment left channel. */
    public static final int    ID_LEVEL_LEFT      = 0x3D;
    /** ID for Key Zone level adjustment right channel . */
    public static final int    ID_LEVEL_RIGHT     = 0x45;
    /** ID for Key Zone color. */
    public static final int    ID_COLOR           = 0x50;


    /**
     * Private due to utility class.
     */
    private KorgmultisampleTag ()
    {
        // Intentionally empty
    }
}
