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

    /** The amplitude envelope attack. */
    public static final String ATTR_AMPEG_ATTACK          = "envattack";
    /** The amplitude envelope decay. */
    public static final String ATTR_AMPEG_DECAY           = "envdecay";
    /** The amplitude envelope sustain. */
    public static final String ATTR_AMPEG_SUSTAIN         = "envsus";
    /** The amplitude envelope release. */
    public static final String ATTR_AMPEG_RELEASE         = "envrel";

    /** The filter cutoff frequency. Also indicates low-pass or high-pass. */
    public static final String ATTR_FILTER_CUTOFF         = "dualfilcutoff";
    /** The filter resonance. */
    public static final String ATTR_FILTER_RESONANCE      = "res";

    /** The modulation source tag. */
    public static final String MOD_SOURCE                 = "modsource";

    /** The modulation destination. */
    public static final String ATTR_MOD_DESTINATION       = "dest";
    /** The modulation source. */
    public static final String ATTR_MOD_SOURCE            = "src";
    /** The modulation slot index. */
    public static final String ATTR_MOD_SLOT              = "slot";
    /** The modulation amount. */
    public static final String ATTR_MOD_AMOUNT            = "amount";

    /** The attribute for multi-sample mode. */
    public static final String ATTR_MULTISAMPLE_MODE      = "multisammode";
    /** The attribute for cell mode. */
    public static final String ATTR_CELL_MODE             = "cellmode";

    /** The attribute for reverse sample playback. */
    public static final String ATTR_REVERSE               = "reverse";
    /** The attribute for the sample start. */
    public static final String ATTR_SAMPLE_START          = "samstart";
    /** The attribute for the sample length. */
    public static final String ATTR_SAMPLE_LENGTH         = "samlen";
    /** The attribute for the sample trigger type: 0 (Trigger), 1 (Normal),2 (Toggle). */
    public static final String ATTR_SAMPLE_TRIGGER_TYPE   = "samtrigtype";

    /** The attribute for the sample pitch. */
    public static final String ATTR_PITCH                 = "pitch";
    /** The attribute for the sample panorama. */
    public static final String ATTR_PANORAMA              = "panpos";

    /** The attribute for the loop mode. */
    public static final String ATTR_LOOP_MODE             = "loopmodes";
    /** The attribute for the loop start. */
    public static final String ATTR_LOOP_START            = "loopstart";
    /** The attribute for the loop end. */
    public static final String ATTR_LOOP_END              = "loopend";
    /** The attribute for the loop fade amount. */
    public static final String ATTR_LOOP_FADE_AMOUNT      = "loopfadeamt";


    /**
     * Private constructor for utility class.
     */
    private Music1010Tag ()
    {
        // Intentionally empty
    }
}
