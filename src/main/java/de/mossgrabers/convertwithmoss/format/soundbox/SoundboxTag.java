// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

/**
 * The tags and attributes used in the XML files of an Audiomodern Soundbox pack.
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxTag
{
    /** The name of the pack description file in the ZIP. */
    public static final String PACK_FILE        = "pack.amx";
    /** The folder in the ZIP which contains the presets. */
    public static final String PRESETS_FOLDER   = "_presets/";
    /** The folder in the ZIP which contains the sample files. */
    public static final String SAMPLES_FOLDER   = "_samples/";

    /** The root tag of the pack description. */
    public static final String PACK             = "Audiomodern.Soundbox_Pack";
    /** The tag which contains the preset name elements. */
    public static final String PRESETS          = "Presets";
    /** The tag of one preset name. */
    public static final String PRESET_NAME      = "P";
    /** The tag which contains the groups. */
    public static final String GROUPS           = "Groups";
    /** The tag of one group. */
    public static final String GROUP            = "Audiomodern.Soundbox_Group";
    /** The tag which contains the original sample file names. */
    public static final String SOUNDS           = "Sounds";
    /** The tag of one sound in a group as well as one sample file name in the sounds list. */
    public static final String SOUND            = "S";

    /** The root tag of a preset. */
    public static final String PRESET           = "Audiomodern.Soundbox_Preset";
    /** The tag which contains the 4 layers of a preset. */
    public static final String LAYERS           = "Layers";
    /** The tag of one layer. */
    public static final String LAYER            = "L";
    /** The tag of the effects section of a layer or the master. */
    public static final String EFFECTS          = "Effects";
    /** The tag of the master section of a preset. */
    public static final String MASTER           = "Master";

    /** The name attribute. */
    public static final String ATTR_NAME        = "name";
    /** The author attribute of the pack. */
    public static final String ATTR_AUTHOR      = "author";
    /** The description attribute of the pack. */
    public static final String ATTR_DESCRIPTION = "desc";
    /** The state attribute of a layer. */
    public static final String ATTR_STATE       = "state";
    /** The settings attribute of a layer. */
    public static final String ATTR_SETTINGS    = "settings";


    /**
     * Private constructor since this is a utility class.
     */
    private SoundboxTag ()
    {
        // Intentionally empty
    }
}
