// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sfz;

/**
 * The SFZ format consists of a number of headers. Below a header there are opcodes (key/value
 * pairs). See https://sfzformat.com
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzOpcode
{
    ////////////////////////////////////////////////////////////////
    // Control opcodes

    /** SFZ v2. Default path under which the samples should be found. */
    public static final String DEFAULT_PATH     = "default_path";

    ////////////////////////////////////////////////////////////////
    // Global opcodes

    /** ARIA. Sets what is displayed in the default info tab of Sforzando. */
    public static final String GLOBAL_LABEL     = "global_label";

    ////////////////////////////////////////////////////////////////
    // Group opcodes

    /** ARIA. Sets what is displayed in the default info tab of Sforzando. */
    public static final String GROUP_LABEL      = "group_label";
    /** SFZ v1. Sequence length, used together with seq_position to use samples as round robins. */
    public static final String SEQ_LENGTH       = "seq_length";

    ////////////////////////////////////////////////////////////////
    // Region opcodes

    /** ARIA. Sets what is displayed in the default info tab of Sforzando. */
    public static final String REGION_LABEL     = "region_label";

    /** SFZ v1. Defines which sample file the region will play. */
    public static final String SAMPLE           = "sample";
    /** SFZ v2. The direction in which the sample is to be played. */
    public static final String DIRECTION        = "direction";
    /** SFZ v1. The region will play if the internal sequence counter is equal to seq_position. */
    public static final String SEQ_POSITION     = "seq_position";

    /** SFZ v1. Sets low-key, high-key and pitch-key-center to the same note value. */
    public static final String KEY              = "key";
    /** SFZ v1. Root key for the sample. */
    public static final String PITCH_KEY_CENTER = "pitch_keycenter";
    /** SFZ v1. Determine the low boundary of a certain region. */
    public static final String LO_KEY           = "lokey";
    /** SFZ v1. Determine the high boundary of a certain region. */
    public static final String HI_KEY           = "hikey";
    /** SFZ v1. Fade in control based on MIDI note (keyboard position). Low boundary. */
    public static final String XF_IN_LO_KEY     = "xfin_lokey";
    /** SFZ v1. Fade in control based on MIDI note (keyboard position). High boundary. */
    public static final String XF_IN_HI_KEY     = "xfin_hikey";
    /** SFZ v1. Fade out control based on MIDI note (keyboard position). Low boundary. */
    public static final String XF_OUT_LO_KEY    = "xfout_lokey";
    /** SFZ v1. Fade out control based on MIDI note (keyboard position). High boundary. */
    public static final String XF_OUT_HI_KEY    = "xfout_hikey";

    /** SFZ v1. Determine the low velocity boundary of a certain region. */
    public static final String LO_VEL           = "lovel";
    /** SFZ v1. Determine the high velocity boundary of a certain region. */
    public static final String HI_VEL           = "hivel";
    /** SFZ v1. Fade in velocity control. Low boundary. */
    public static final String XF_IN_LO_VEL     = "xfin_lovel";
    /** SFZ v1. Fade in velocity control. High boundary. */
    public static final String XF_IN_HI_VEL     = "xfin_hivel";
    /** SFZ v1. Fade out velocity control. Low boundary. */
    public static final String XF_OUT_LO_VEL    = "xfout_lovel";
    /** SFZ v1. Fade out velocity control. High boundary. */
    public static final String XF_OUT_HI_VEL    = "xfout_hivel";

    /** SFZ v1. The offset used to play the sample (play start). */
    public static final String OFFSET           = "offset";
    /** SFZ v1. The endpoint of the sample. */
    public static final String END              = "end";

    /** SFZ v1. The fine tuning for the sample, in cents. */
    public static final String TUNE             = "tune";
    /** ARIA. Alias for tune. */
    public static final String PITCH            = "pitch";
    /** SFZ v1. Defines how much the pitch changes with every note. */
    public static final String PITCH_KEYTRACK   = "pitch_keytrack";
    /** SFZ v1. The volume for the region, in decibels. */
    public static final String VOLUME           = "volume";

    /** SFZ v1. Allows playing samples with loops defined in the not looped mode. */
    public static final String LOOP_MODE        = "loop_mode";
    /** SFZ v2. Defines the looping mode. */
    public static final String LOOP_TYPE        = "loop_type";
    /** SFZ v1. The loop start point, in samples. */
    public static final String LOOP_START       = "loop_start";
    /** SFZ v2. Alternative to loop_start. */
    public static final String LOOPSTART        = "loopstart";
    /** SFZ v1. The loop end point, in samples. */
    public static final String LOOP_END         = "loop_end";
    /** SFZ v2. Alternative to loop_end. */
    public static final String LOOPEND          = "loopend";
    /** SFZ v2. Loop cross fade. */
    public static final String LOOP_CROSSFADE   = "loop_crossfade";


    /**
     * Private constructor for utility class.
     */
    private SfzOpcode ()
    {
        // Intentionally empty
    }
}
