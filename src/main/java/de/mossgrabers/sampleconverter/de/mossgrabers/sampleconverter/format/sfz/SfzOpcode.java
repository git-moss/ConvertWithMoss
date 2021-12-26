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

    /** SFZ v1. Sets the trigger which will be used for the sample to play. */
    public static final String TRIGGER          = "trigger";

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

    /** SFZ v1. The pitch bend up in cents (-9600 to 9600). */
    public static final String BEND_UP          = "bend_up";
    /** SFZ v1. The pitch bend down in cents (-9600 to 9600). */
    public static final String BEND_DOWN        = "bend_down";

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

    /** SFZ v1. The EG delay time. */
    public static final String AMPEG_DELAY      = "ampeg_delay";
    /** SFZ v1. The EG envelope start level. */
    public static final String AMPEG_START      = "ampeg_start";
    /** SFZ v1. The EG attack time. */
    public static final String AMPEG_ATTACK     = "ampeg_attack";
    /** SFZ v1. The EG hold time. */
    public static final String AMPEG_HOLD       = "ampeg_hold";
    /** SFZ v1. The EG decay time. */
    public static final String AMPEG_DECAY      = "ampeg_decay";
    /** SFZ v1. The EG envelope sustain level. */
    public static final String AMPEG_SUSTAIN    = "ampeg_sustain";
    /** SFZ v1. The EG release time. */
    public static final String AMPEG_RELEASE    = "ampeg_release";
    /** Cakewalk alias. The EG delay time. */
    public static final String AMP_DELAY        = "amp_delay";
    /** Cakewalk alias. The EG envelope start level. */
    public static final String AMP_START        = "amp_start";
    /** Cakewalk alias. The EG attack time. */
    public static final String AMP_ATTACK       = "amp_attack";
    /** Cakewalk alias. The EG hold time. */
    public static final String AMP_HOLD         = "amp_hold";
    /** Cakewalk alias. The EG decay time. */
    public static final String AMP_DECAY        = "amp_decay";
    /** Cakewalk alias. The EG envelope sustain level. */
    public static final String AMP_SUSTAIN      = "amp_sustain";
    /** Cakewalk alias. The EG release time. */
    public static final String AMP_RELEASE      = "amp_release";

    ////////////////////////////////////////////////////////////////
    // Filter opcodes

    /** SFZ v1. The cutoff frequency (Hz) of the 1st filter specified in Hertz. */
    public static final String CUTOFF           = "cutoff";
    /** SFZ v1. The filter cutoff resonance value, in decibels. */
    public static final String RESONANCE        = "resonance";
    /** SFZ v1. The type of filter. */
    public static final String FILTER_TYPE      = "fil_type";

    /** SFZ v1. The filter EG depth. */
    public static final String FILEG_DEPTH      = "fileg_depth";
    /** Cakewalk alias. The filter EG depth. */
    public static final String FIL_DEPTH        = "fil_depth";

    /** SFZ v1. The EG delay time. */
    public static final String FILEG_DELAY      = "fileg_delay";
    /** SFZ v1. The EG envelope start level. */
    public static final String FILEG_START      = "fileg_start";
    /** SFZ v1. The EG attack time. */
    public static final String FILEG_ATTACK     = "fileg_attack";
    /** SFZ v1. The EG hold time. */
    public static final String FILEG_HOLD       = "fileg_hold";
    /** SFZ v1. The EG decay time. */
    public static final String FILEG_DECAY      = "fileg_decay";
    /** SFZ v1. The EG envelope sustain level. */
    public static final String FILEG_SUSTAIN    = "fileg_sustain";
    /** SFZ v1. The EG release time. */
    public static final String FILEG_RELEASE    = "fileg_release";
    /** Cakewalk alias. The EG delay time. */
    public static final String FIL_DELAY        = "fil_delay";
    /** Cakewalk alias. The EG envelope start level. */
    public static final String FIL_START        = "fil_start";
    /** Cakewalk alias. The EG attack time. */
    public static final String FIL_ATTACK       = "fil_attack";
    /** Cakewalk alias. The EG hold time. */
    public static final String FIL_HOLD         = "fil_hold";
    /** Cakewalk alias. The EG decay time. */
    public static final String FIL_DECAY        = "fil_decay";
    /** Cakewalk alias. The EG envelope sustain level. */
    public static final String FIL_SUSTAIN      = "fil_sustain";
    /** Cakewalk alias. The EG release time. */
    public static final String FIL_RELEASE      = "fil_release";

    ////////////////////////////////////////////////////////////////
    // Pitch opcodes

    /** SFZ v1. The filter pitch EG depth. */
    public static final String PITCHEG_DEPTH    = "pitcheg_depth";
    /** Cakewalk alias. The filter pitch EG depth. */
    public static final String PITCH_DEPTH      = "pitch_depth";

    /** SFZ v1. The pitch EG delay time. */
    public static final String PITCHEG_DELAY    = "pitcheg_delay";
    /** SFZ v1. The pitch EG envelope start level. */
    public static final String PITCHEG_START    = "pitcheg_start";
    /** SFZ v1. The pitch EG attack time. */
    public static final String PITCHEG_ATTACK   = "pitcheg_attack";
    /** SFZ v1. The pitch EG hold time. */
    public static final String PITCHEG_HOLD     = "pitcheg_hold";
    /** SFZ v1. The pitch EG decay time. */
    public static final String PITCHEG_DECAY    = "pitcheg_decay";
    /** SFZ v1. The pitch EG envelope sustain level. */
    public static final String PITCHEG_SUSTAIN  = "pitcheg_sustain";
    /** SFZ v1. The pitch EG release time. */
    public static final String PITCHEG_RELEASE  = "pitcheg_release";
    /** Cakewalk alias. The pitch EG delay time. */
    public static final String PITCH_DELAY      = "pitch_delay";
    /** Cakewalk alias. The pitch EG envelope start level. */
    public static final String PITCH_START      = "pitch_start";
    /** Cakewalk alias. The pitch EG attack time. */
    public static final String PITCH_ATTACK     = "pitch_attack";
    /** Cakewalk alias. The pitch EG hold time. */
    public static final String PITCH_HOLD       = "pitch_hold";
    /** Cakewalk alias. The pitch EG decay time. */
    public static final String PITCH_DECAY      = "pitch_decay";
    /** Cakewalk alias. The pitch EG envelope sustain level. */
    public static final String PITCH_SUSTAIN    = "pitch_sustain";
    /** Cakewalk alias. The pitch EG release time. */
    public static final String PITCH_RELEASE    = "pitch_release";


    /**
     * Private constructor for utility class.
     */
    private SfzOpcode ()
    {
        // Intentionally empty
    }
}
