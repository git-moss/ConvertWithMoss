// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tx16wx;

/**
 * The TX16Wx program format consists of several XML tags.
 *
 * @author Jürgen Moßgraber
 */
public class TX16WxTag
{
    /** The name attribute. */
    public static final String NAME                   = "tx:name";
    /** The MIDI channel attribute. */
    public static final String MIDI_CHANNEL           = "tx:channel";

    /** The performance root tag. */
    public static final String PERFORMANCE            = "tx:performance";
    /** A program slot in a performance. */
    public static final String SLOT                   = "tx:slot";

    /** The program root tag. */
    public static final String PROGRAM                = "tx:program";
    /** The quality attribute. */
    public static final String PROGRAM_QUALITY        = "tx:quality";
    /** The created-by attribute. */
    public static final String PROGRAM_CREATED_BY     = "tx:created-by";

    /** The program icon attribute. */
    public static final String PROGRAM_ICON           = "tx:icon";

    /** The group tag. */
    public static final String GROUP                  = "tx:group";
    /** The group play mode attribute. */
    public static final String GROUP_PLAYMODE         = "tx:playmode";
    /** The group output attribute. */
    public static final String GROUP_OUTPUT           = "tx:output";
    /** The group delay attribute. */
    public static final String GROUP_DELAY            = "tx:delay";

    /** The group sound shape tag and attribute. */
    public static final String SOUND_SHAPE            = "tx:soundshape";
    /** The group sound shape ID attribute. */
    public static final String SOUND_SHAPE_ID         = "tx:id";

    /** The volume tag on different levels. */
    public static final String VOLUME                 = "tx:volume";
    /** The attenuation tag on different levels. */
    public static final String ATTENUATION            = "tx:attenuation";

    /** The panning attribute on different levels. */
    public static final String PANNING                = "tx:pan";
    /** The coarse tuning attribute. */
    public static final String TUNING_COARSE          = "tx:coarse";
    /** The fine tuning attribute. */
    public static final String TUNING_FINE            = "tx:fine";

    /** The region tag. */
    public static final String REGION                 = "tx:region";
    /** The sample tag. */
    public static final String SAMPLE                 = "tx:wave";
    /** The sample ID attribute. */
    public static final String SAMPLE_ID              = "tx:id";
    /** The sample path attribute. */
    public static final String PATH                   = "tx:path";
    /** The start attribute. */
    public static final String START                  = "tx:start";
    /** The end attribute. */
    public static final String END                    = "tx:end";
    /** The reverse attribute. */
    public static final String REVERSE                = "tx:reverse";

    /** The sample loop tag. */
    public static final String SAMPLE_LOOP            = "tx:loop";
    /** The loop start tag sample attribute. */
    public static final String LOOP_START             = "tx:start";
    /** The loop end tag sample attribute. */
    public static final String LOOP_END               = "tx:end";
    /** The mode of the loop. */
    public static final String LOOP_MODE              = "tx:mode";
    /** The loop cross-fade attribute. */
    public static final String LOOP_CROSSFADE         = "tx:xfade";

    /** The root note attribute. */
    public static final String ROOT                   = "tx:root";

    /** The key/velocity-bounds element. */
    public static final String BOUNDS                 = "tx:bounds";
    /** The key/velocity-fade-bounds element. */
    public static final String FADE_BOUNDS            = "tx:fade";
    /** The low note tag sample attribute. */
    public static final String LO_NOTE                = "tx:low-key";
    /** The high note tag sample attribute. */
    public static final String HI_NOTE                = "tx:high-key";
    /** The alternative low note tag sample attribute. */
    public static final String LO_NOTE_ALT            = "tx:lowkey";
    /** The alternative high note tag sample attribute. */
    public static final String HI_NOTE_ALT            = "tx:highkey";
    /** The low velocity tag sample attribute. */
    public static final String LO_VEL                 = "tx:low-vel";
    /** The high velocity tag sample attribute. */
    public static final String HI_VEL                 = "tx:high-vel";

    /** The amplitude velocity modulation tag. */
    public static final String AMP_VELOCITY           = "tx:velocity";
    /** The amplitude envelope tag. */
    public static final String AMP_ENVELOPE           = "tx:aeg";
    /** The amplitude envelope attack attribute. */
    public static final String AMP_ENV_ATTACK         = "tx:attack";
    /** The amplitude envelope decay 1 attribute. */
    public static final String AMP_ENV_DECAY1         = "tx:decay1";
    /** The amplitude envelope level 1 attribute. */
    public static final String AMP_ENV_LEVEL1         = "tx:level1";
    /** The amplitude envelope level 2 attribute. */
    public static final String AMP_ENV_LEVEL2         = "tx:level2";
    /** The amplitude envelope decay 2 attribute. */
    public static final String AMP_ENV_DECAY2         = "tx:decay2";
    /** The amplitude envelope sustain attribute. */
    public static final String AMP_ENV_SUSTAIN        = "tx:sustain";
    /** The amplitude envelope release attribute. */
    public static final String AMP_ENV_RELEASE        = "tx:release";
    /** The amplitude envelope attack-shape attribute. */
    public static final String AMP_ENV_ATTACK_SHAPE   = "tx:attack-shape";
    /** The amplitude envelope decay1-shape attribute. */
    public static final String AMP_ENV_DECAY1_SHAPE   = "tx:decay1-shape";
    /** The amplitude envelope decay2-shape attribute. */
    public static final String AMP_ENV_DECAY2_SHAPE   = "tx:decay2-shape";
    /** The amplitude envelope release-shape attribute. */
    public static final String AMP_ENV_RELEASE_SHAPE  = "tx:release-shape";

    /** The envelope 1 tag. */
    public static final String ENVELOPE_1             = "tx:env1";
    /** The envelope 2 tag. */
    public static final String ENVELOPE_2             = "tx:env2";
    /** The envelope 1/2 level 0 attribute. */
    public static final String ENV_LEVEL0             = "tx:level0";
    /** The envelope 1/2 level 1 attribute. */
    public static final String ENV_LEVEL1             = "tx:level1";
    /** The envelope 1/2 level 2 attribute. */
    public static final String ENV_LEVEL2             = "tx:level2";
    /** The envelope 1/2 level 3 attribute. */
    public static final String ENV_LEVEL3             = "tx:level3";
    /** The envelope 1/2 time 1 attribute. */
    public static final String ENV_TIME1              = "tx:time1";
    /** The envelope 1/2 time 2 attribute. */
    public static final String ENV_TIME2              = "tx:time2";
    /** The envelope 1/2 time 3 attribute. */
    public static final String ENV_TIME3              = "tx:time3";
    /** The envelope 1/2 shape 1 attribute. */
    public static final String ENV_SHAPE1             = "tx:shape1";
    /** The envelope 1/2 shape 2 attribute. */
    public static final String ENV_SHAPE2             = "tx:shape2";
    /** The envelope 1/2 shape 3 attribute. */
    public static final String ENV_SHAPE3             = "tx:shape3";

    /** The filter tag. */
    public static final String FILTER                 = "tx:filter";
    /** The filter 1 tag. */
    public static final String FILTER1                = "tx:filter1";
    /** The filter type attribute. */
    public static final String FILTER_TYPE            = "tx:type";
    /** The filter frequency attribute. */
    public static final String FILTER_FREQUENCY       = "tx:freq";
    /** The filter resonance attribute (resonance in 0-100%). */
    public static final String FILTER_RESONANCE       = "tx:resonance";
    /** The filter slope attribute. */
    public static final String FILTER_SLOPE           = "tx:slope";

    /** The filter res attribute (resonance in volume). */
    public static final String FILTER_RES             = "tx:res";
    /** The filter cutoff type attribute. */
    public static final String FILTER_CUTOFF          = "tx:cutoff";

    /** The modulation tag. */
    public static final String MODULATION             = "tx:modulation";
    /** The modulation entry tag. */
    public static final String MODULATION_ENTRY       = "tx:entry";
    /** The modulation source attribute. */
    public static final String MODULATION_SOURCE      = "tx:source";
    /** The modulation destination attribute. */
    public static final String MODULATION_DESTINATION = "tx:destination";
    /** The modulation amount attribute. */
    public static final String MODULATION_AMOUNT      = "tx:amount";
    /** The modulation source curve tag. */
    public static final String MODULATION_SRC_CURVE   = "tx:src-curve";
    /** The modulation via curve tag. */
    public static final String MODULATION_VIA_CURVE   = "tx:via-curve";
    /** The modulation curve smooth attribute. */
    public static final String MODULATION_SMOOTH      = "tx:smooth";
    /** The modulation curve shape attribute. */
    public static final String MODULATION_SHAPE       = "tx:shape";

    /** The region sound-offsets tag. */
    public static final String SOUND_OFFSETS          = "tx:sound-offsets";


    /**
     * Private constructor for utility class.
     */
    private TX16WxTag ()
    {
        // Intentionally empty
    }
}
