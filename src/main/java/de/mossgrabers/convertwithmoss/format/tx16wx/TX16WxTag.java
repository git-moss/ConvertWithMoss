// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tx16wx;

/**
 * The TX16Wx program format consists of several XML tags.
 *
 * @author Jürgen Moßgraber
 */
public class TX16WxTag
{
    /** The root tag. */
    public static final String PROGRAM          = "program";

    /** The group tag. */
    public static final String GROUP            = "group";
    /** The group name attribute. */
    public static final String GROUP_NAME       = "name";
    /** The group play mode attribute. */
    public static final String GROUP_PLAYMODE   = "playmode";
    /** The group sound shape tag and attribute. */
    public static final String SOUND_SHAPE      = "soundshape";
    /** The group sound shape ID attribute. */
    public static final String SOUND_SHAPE_ID   = "id";

    /** The volume tag on different levels. */
    public static final String VOLUME           = "volume";
    /** The panorama attribute on different levels. */
    public static final String PANORAMA         = "pan";
    /** The coarse tuning attribute. */
    public static final String TUNING_COARSE    = "coarse";
    /** The fine tuning attribute. */
    public static final String TUNING_FINE      = "fine";

    /** The region tag. */
    public static final String REGION           = "region";
    /** The sample tag. */
    public static final String SAMPLE           = "wave";
    /** The sample ID attribute. */
    public static final String SAMPLE_ID        = "id";
    /** The sample path attribute. */
    public static final String PATH             = "path";
    /** The start attribute. */
    public static final String START            = "start";
    /** The end attribute. */
    public static final String END              = "end";

    /** The sample loop tag. */
    public static final String SAMPLE_LOOP      = "loop";
    /** The loop start tag sample attribute. */
    public static final String LOOP_START       = "start";
    /** The loop end tag sample attribute. */
    public static final String LOOP_END         = "end";
    /** The mode of the loop. */
    public static final String LOOP_MODE        = "mode";
    /** The loop cross-fade attribute. */
    public static final String LOOP_CROSSFADE   = "xfade";

    /** The root note attribute. */
    public static final String ROOT_NOTE        = "rootkey";

    /** The key/velocity-bounds element. */
    public static final String BOUNDS           = "bounds";
    /** The key/velocity-fade-bounds element. */
    public static final String FADE_BOUNDS      = "fade";
    /** The low note tag sample attribute. */
    public static final String LO_NOTE          = "low-key";
    /** The high note tag sample attribute. */
    public static final String HI_NOTE          = "high-key";
    /** The alternative low note tag sample attribute. */
    public static final String LO_NOTE_ALT      = "lowkey";
    /** The alternative high note tag sample attribute. */
    public static final String HI_NOTE_ALT      = "highkey";
    /** The low velocity tag sample attribute. */
    public static final String LO_VEL           = "low-el";
    /** The high velocity tag sample attribute. */
    public static final String HI_VEL           = "high-vel";

    /** The amplitude envelope tag. */
    public static final String AMP_ENVELOPE     = "aeg";
    /** The amplitude envelope attack attribute. */
    public static final String AMP_ENV_ATTACK   = "attack";
    /** The amplitude envelope decay attribute. */
    public static final String AMP_ENV_DECAY    = "decay1";
    /** The amplitude envelope sustain attribute. */
    public static final String AMP_ENV_SUSTAIN  = "sustain";
    /** The amplitude envelope release attribute. */
    public static final String AMP_ENV_RELEASE  = "release";

    /** The filter tag. */
    public static final String FILTER           = "filter1";
    /** The filter type attribute. */
    public static final String FILTER_TYPE      = "type";
    /** The filter frequency type attribute. */
    public static final String FILTER_FREQUENCY = "freq";
    /** The filter cutoff type attribute. */
    public static final String FILTER_CUTOFF    = "cutoff";


    /**
     * Private constructor for utility class.
     */
    private TX16WxTag ()
    {
        // Intentionally empty
    }
}
