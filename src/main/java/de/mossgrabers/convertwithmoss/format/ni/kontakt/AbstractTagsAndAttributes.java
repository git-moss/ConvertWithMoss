// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt;

/**
 * Tags used in the NKI XML structure.
 *
 * @author Jürgen Moßgraber
 * @author Philip Stolz
 */
public abstract class AbstractTagsAndAttributes
{
    /** The program element name attribute. */
    private static final String PROGRAM_NAME               = "name";

    /** The parameters element. */
    private static final String PARAMETERS                 = "Parameters";

    /** The value element. */
    private static final String VALUE                      = "V";

    /** The value name attribute. */
    private static final String VALUE_NAME_ATTRIBUTE       = "name";

    /** The value name attribute. */
    private static final String VALUE_VALUE_ATTRIBUTE      = "value";

    /** The groups element of a program. */
    private static final String GROUPS                     = "Groups";

    /** The zones element of a program. */
    private static final String ZONES                      = "Zones";

    /** The group name attribute. */
    private static final String GROUP_NAME_ATTRIBUTE       = "name";

    /** The internal modulators element. */
    private static final String INT_MODULATORS_ELEMENT     = "IntModulators";

    /** The envelope element. */
    private static final String ENVELOPE_ELEMENT           = "Envelope";

    /** The bypass parameter. */
    private static final String BYPASS_PARAM               = "bypass";

    /** The yes value. */
    private static final String YES                        = "yes";

    /** The target volume value. */
    private static final String TARGET_VOL_VALUE           = "volume";

    /** The target pitch value. */
    private static final String TARGET_PITCH_VALUE         = "pitch";

    /** The target filter cutoff value. */
    private static final String TARGET_FILTER_CUTOFF_VALUE = "filterCutoff";

    /** The target parameter. */
    private static final String TARGET_PARAM               = "target";

    /** The intensity parameter. */
    private static final String INTENSITY_PARAM            = "intensity";

    /** The envelope type attribute. */
    private static final String ENV_TYPE_ATTRIBUTE         = "type";

    /** The AHD envelope type value. */
    private static final String AHD_ENV_TYPE_VALUE         = "ahd";

    /** The AHDSR envelope type value. */
    private static final String AHDSR_ENV_TYPE_VALUE       = "ahdsr";

    /** The attack curve parameter. */
    private static final String ATTACK_PARAM_CURVE         = "atkCurving";

    /** The attack parameter. */
    private static final String ATTACK_PARAM               = "attack";

    /** The hold parameter. */
    private static final String HOLD_PARAM                 = "hold";

    /** The decay parameter. */
    private static final String DECAY_PARAM                = "decay";

    /** The hold parameter. */
    private static final String SUSTAIN_PARAM              = "sustain";

    /** The release parameter. */
    private static final String RELEASE_PARAM              = "release";

    /** The external modulators element. */
    private static final String EXT_MODULATORS_ELEMENT     = "ExtModulators";

    /** The source parameter. */
    private static final String SOURCE_PARAM               = "source";

    /** The pitch bend value. */
    private static final String PITCH_BEND_VALUE           = "pitchBend";

    /** The velocity value. */
    private static final String VELOCITY_VALUE             = "velocity";

    /** The pitch value. */
    private static final String PITCH_VALUE                = "pitch";

    /** The volume value. */
    private static final String VOLUME_VALUE               = "volume";

    /** The intensity value. */
    private static final String INTENSITY_VALUE            = "intensity";

    /** The index attribute. */
    private static final String INDEX_ATTRIBUTE            = "index";

    /** The group index attribute. */
    private static final String GROUP_INDEX_ATTRIBUTE      = "groupIdx";

    /** The sample element of a program. */
    private static final String ZONE_SAMPLE                = "Sample";

    /** The key tracking parameter. */
    private static final String KEY_TRACKING_PARAM         = "keyTracking";

    /** The group volume parameter. */
    private static final String GROUP_VOL_PARAM            = "volume";

    /** The zone volume parameter. */
    private static final String ZONE_VOL_PARAM             = "zoneVolume";

    /** The zone tune parameter. */
    private static final String ZONE_TUNE_PARAM            = "zoneTune";

    /** The group tune parameter. */
    private static final String GROUP_TUNE_PARAM           = "tune";

    /** The zone pan parameter. */
    private static final String ZONE_PAN_PARAM             = "zonePan";

    /** The group pan parameter. */
    private static final String GROUP_PAN_PARAM            = "pan";

    /** The reverse parameter. */
    private static final String REVERSE_PARAM              = "reverse";

    /** The loops element. */
    private static final String LOOPS_ELEMENT              = "Loops";

    /** The loop element. */
    private static final String LOOP_ELEMENT               = "Loop";

    /** The loop start parameter. */
    private static final String LOOP_START_PARAM           = "loopStart";

    /** The loop length parameter. */
    private static final String LOOP_LENGTH_PARAM          = "loopLength";

    /** The mode parameter. */
    private static final String LOOP_MODE_PARAM            = "mode";

    /** The crossfade length parameter. */
    private static final String XFADE_LENGTH_PARAM         = "xfadeLength";

    /** The until end value. */
    private static final String UNTIL_END_VALUE            = "until_end";

    /** The until release value. */
    private static final String UNTIL_RELEASE_VALUE        = "until_release";

    /** The one-shot value. */
    private static final String ONESHOT_VALUE              = "oneshot";

    /** The alternating loop parameter. */
    private static final String ALTERNATING_LOOP_PARAM     = "alternatingLoop";

    /** The root key parameter. */
    private static final String ROOT_KEY_PARAM             = "rootKey";

    /** The low key parameter. */
    private static final String LOW_KEY_PARAM              = "lowKey";

    /** The high key parameter. */
    private static final String HIGH_KEY_PARAM             = "highKey";

    /** The low key parameter. */
    private static final String LOW_VELOCITY_PARAM         = "lowVelocity";

    /** The high key parameter. */
    private static final String HIGH_VELOCITY_PARAM        = "highVelocity";

    /** The fade low parameter. */
    private static final String FADE_LOW_PARAM             = "fadeLowKey";

    /** The fade high parameter. */
    private static final String FADE_HIGH_PARAM            = "fadeHighKey";

    /** The fade low velocity parameter. */
    private static final String FADE_LOW_VEL_PARAM         = "fadeLowVelo";

    /** The fade high velocity parameter. */
    private static final String FADE_HIGH_VEL_PARAM        = "fadeHighVelo";

    /** The sample start parameter. */
    private static final String SAMPLE_START_PARAM         = "sampleStart";

    /** The sample end parameter. */
    private static final String SAMPLE_END_PARAM           = "sampleEnd";

    /** The group filter element. */
    private static final String GROUP_FILTER               = "Filter";


    /**
     * Get the program tag.
     *
     * @return The tag
     */
    public abstract String program ();


    /**
     * Get the program name tag.
     *
     * @return The tag
     */
    public String programName ()
    {
        return PROGRAM_NAME;
    }


    /**
     * Get the parameters tag.
     *
     * @return The tag
     */
    public String parameters ()
    {
        return PARAMETERS;
    }


    /**
     * Get the value tag.
     *
     * @return The tag
     */
    public String value ()
    {
        return VALUE;
    }


    /**
     * Get the value name tag.
     *
     * @return The tag
     */
    public String valueNameAttribute ()
    {
        return VALUE_NAME_ATTRIBUTE;
    }


    /**
     * Get the value tag of the value tag.
     *
     * @return The tag
     */
    public String valueValueAttribute ()
    {
        return VALUE_VALUE_ATTRIBUTE;
    }


    /**
     * Get the groups tag.
     *
     * @return The tag
     */
    public String groups ()
    {
        return GROUPS;
    }


    /**
     * Get the group tag.
     *
     * @return The tag
     */
    public abstract String group ();


    /**
     * Get the zones tag.
     *
     * @return The tag
     */
    public String zones ()
    {
        return ZONES;
    }


    /**
     * Get the zone tag.
     *
     * @return The tag
     */
    public abstract String zone ();


    /**
     * Get the group name tag.
     *
     * @return The tag
     */
    public String groupNameAttribute ()
    {
        return GROUP_NAME_ATTRIBUTE;
    }


    /**
     * Get the modulators tag.
     *
     * @return The tag
     */
    public String intModulatorsElement ()
    {
        return INT_MODULATORS_ELEMENT;
    }


    /**
     * Get the modulator tag.
     *
     * @return The tag
     */
    public abstract String intModulatorElement ();


    /**
     * Get the envelope element tag.
     *
     * @return The tag
     */
    public String envelopeElement ()
    {
        return ENVELOPE_ELEMENT;
    }


    /**
     * Get the bypass tag.
     *
     * @return The tag
     */
    public String bypassParam ()
    {
        return BYPASS_PARAM;
    }


    /**
     * Get the yes tag.
     *
     * @return The tag
     */
    public String yes ()
    {
        return YES;
    }


    /**
     * Get the target volume value tag.
     *
     * @return The tag
     */
    public String targetVolValue ()
    {
        return TARGET_VOL_VALUE;
    }


    /**
     * Get the target pitch value tag.
     *
     * @return The tag
     */
    public String targetPitchValue ()
    {
        return TARGET_PITCH_VALUE;
    }


    /**
     * Get the target filter cutoff value tag.
     *
     * @return The tag
     */
    public String targetFilterCutoffValue ()
    {
        return TARGET_FILTER_CUTOFF_VALUE;
    }


    /**
     * Get the target parameter tag.
     *
     * @return The tag
     */
    public String targetParam ()
    {
        return TARGET_PARAM;
    }


    /**
     * Get the intensity tag.
     *
     * @return The tag
     */
    public String intensityParam ()
    {
        return INTENSITY_PARAM;
    }


    /**
     * Get the envelope type tag.
     *
     * @return The tag
     */
    public String envTypeAttribute ()
    {
        return ENV_TYPE_ATTRIBUTE;
    }


    /**
     * Get the AHD envelope value tag.
     *
     * @return The tag
     */
    public String ahdEnvTypeValue ()
    {
        return AHD_ENV_TYPE_VALUE;
    }


    /**
     * Get the AHDSR envelope value tag.
     *
     * @return The tag
     */
    public String ahdsrEnvTypeValue ()
    {
        return AHDSR_ENV_TYPE_VALUE;
    }


    /**
     * Get the envelope attack tag.
     *
     * @return The tag
     */
    public String attackParam ()
    {
        return ATTACK_PARAM;
    }


    /**
     * Get the envelope attack curve tag.
     *
     * @return The tag
     */
    public String attackCurveParam ()
    {
        return ATTACK_PARAM_CURVE;
    }


    /**
     * Get the envelope hold tag.
     *
     * @return The tag
     */
    public String holdParam ()
    {
        return HOLD_PARAM;
    }


    /**
     * Get the envelope decay tag.
     *
     * @return The tag
     */
    public String decayParam ()
    {
        return DECAY_PARAM;
    }


    /**
     * Get the envelope sustain tag.
     *
     * @return The tag
     */
    public String sustainParam ()
    {
        return SUSTAIN_PARAM;
    }


    /**
     * Get the envelope release tag.
     *
     * @return The tag
     */
    public String releaseParam ()
    {
        return RELEASE_PARAM;
    }


    /**
     * Get the extended modulators tag.
     *
     * @return The tag
     */
    public String extModulatorsElement ()
    {
        return EXT_MODULATORS_ELEMENT;
    }


    /**
     * Get the extended modulator tag.
     *
     * @return The tag
     */
    public abstract String extModulatorElement ();


    /**
     * Get the source tag.
     *
     * @return The tag
     */
    public String sourceParam ()
    {
        return SOURCE_PARAM;
    }


    /**
     * Get the pitch bend value tag.
     *
     * @return The tag
     */
    public String pitchBendValue ()
    {
        return PITCH_BEND_VALUE;
    }


    /**
     * Get the velocity value tag.
     *
     * @return The tag
     */
    public String velocityValue ()
    {
        return VELOCITY_VALUE;
    }


    /**
     * Get the pitch value tag.
     *
     * @return The tag
     */
    public String pitchValue ()
    {
        return PITCH_VALUE;
    }


    /**
     * Get the volume value tag.
     *
     * @return The tag
     */
    public String volumeValue ()
    {
        return VOLUME_VALUE;
    }


    /**
     * Get the intensity value tag.
     *
     * @return The tag
     */
    public String intensityValue ()
    {
        return INTENSITY_VALUE;
    }


    /**
     * Get the index attribute tag.
     *
     * @return The tag
     */
    public String indexAttribute ()
    {
        return INDEX_ATTRIBUTE;
    }


    /**
     * Get the group index tag.
     *
     * @return The tag
     */
    public String groupIndexAttribute ()
    {
        return GROUP_INDEX_ATTRIBUTE;
    }


    /**
     * Get the zone sample tag.
     *
     * @return The tag
     */
    public String zoneSample ()
    {
        return ZONE_SAMPLE;
    }


    /**
     * Get the sample file tag.
     *
     * @return The tag
     */
    public abstract String sampleFileAttribute ();


    /**
     * Get the extended sample file tag.
     *
     * @return The tag
     */
    public abstract String sampleFileExAttribute ();


    /**
     * Get the key tracking tag.
     *
     * @return The tag
     */
    public String keyTrackingParam ()
    {
        return KEY_TRACKING_PARAM;
    }


    /**
     * Get the zone volume tag.
     *
     * @return The tag
     */
    public String zoneVolParam ()
    {
        return ZONE_VOL_PARAM;
    }


    /**
     * Get the group volume tag.
     *
     * @return The tag
     */
    public String groupVolParam ()
    {
        return GROUP_VOL_PARAM;
    }


    /**
     * Get the program volume tag.
     *
     * @return The tag
     */
    public abstract String progVolParam ();


    /**
     * Get the zone tune parameter tag.
     *
     * @return The tag
     */
    public String zoneTuneParam ()
    {
        return ZONE_TUNE_PARAM;
    }


    /**
     * Get the group tune tag.
     *
     * @return The tag
     */
    public String groupTuneParam ()
    {
        return GROUP_TUNE_PARAM;
    }


    /**
     * Get the program tune parameter tag.
     *
     * @return The tag
     */
    public abstract String progTuneParam ();


    /**
     * Get the zone panning parameter tag.
     *
     * @return The tag
     */
    public String zonePanParam ()
    {
        return ZONE_PAN_PARAM;
    }


    /**
     * Get the group panning parameter tag.
     *
     * @return The tag
     */
    public String groupPanParam ()
    {
        return GROUP_PAN_PARAM;
    }


    /**
     * Get the program panning parameter tag.
     *
     * @return The tag
     */
    public abstract String progPanParam ();


    /**
     * Get the reverse parameter tag.
     *
     * @return The tag
     */
    public String reverseParam ()
    {
        return REVERSE_PARAM;
    }


    /**
     * Get the loops element tag.
     *
     * @return The tag
     */
    public String loopsElement ()
    {
        return LOOPS_ELEMENT;
    }


    /**
     * Get the loop element tag.
     *
     * @return The tag
     */
    public String loopElement ()
    {
        return LOOP_ELEMENT;
    }


    /**
     * Get the loop start tag.
     *
     * @return The tag
     */
    public String loopStartParam ()
    {
        return LOOP_START_PARAM;
    }


    /**
     * Get the loop length tag.
     *
     * @return The tag
     */
    public String loopLengthParam ()
    {
        return LOOP_LENGTH_PARAM;
    }


    /**
     * Get the loop mode tag.
     *
     * @return The tag
     */
    public String loopModeParam ()
    {
        return LOOP_MODE_PARAM;
    }


    /**
     * Get the crossfade length tag.
     *
     * @return The tag
     */
    public String xfadeLengthParam ()
    {
        return XFADE_LENGTH_PARAM;
    }


    /**
     * Get the loop until end tag.
     *
     * @return The tag
     */
    public String untilEndValue ()
    {
        return UNTIL_END_VALUE;
    }


    /**
     * Get the loop until release tag.
     *
     * @return The tag
     */
    public String untilReleaseValue ()
    {
        return UNTIL_RELEASE_VALUE;
    }


    /**
     * Get the one shot value tag.
     *
     * @return The tag
     */
    public String oneshotValue ()
    {
        return ONESHOT_VALUE;
    }


    /**
     * Get the alternating loop tag.
     *
     * @return The tag
     */
    public String alternatingLoopParam ()
    {
        return ALTERNATING_LOOP_PARAM;
    }


    /**
     * Get the root container tag.
     *
     * @return The tag
     */
    public abstract String rootContainer ();


    /**
     * Get the root key parameter tag.
     *
     * @return The tag
     */
    public String rootKeyParam ()
    {
        return ROOT_KEY_PARAM;
    }


    /**
     * Get the low key parameter tag.
     *
     * @return The tag
     */
    public String lowKeyParam ()
    {
        return LOW_KEY_PARAM;
    }


    /**
     * Get the high key parameter tag.
     *
     * @return The tag
     */
    public String highKeyParam ()
    {
        return HIGH_KEY_PARAM;
    }


    /**
     * Get the low velocity parameter tag.
     *
     * @return The tag
     */
    public String lowVelocityParam ()
    {
        return LOW_VELOCITY_PARAM;
    }


    /**
     * Get the high velocity parameter tag.
     *
     * @return The tag
     */
    public String highVelocityParam ()
    {
        return HIGH_VELOCITY_PARAM;
    }


    /**
     * Get the fade low tag.
     *
     * @return The tag
     */
    public String fadeLowParam ()
    {
        return FADE_LOW_PARAM;
    }


    /**
     * Get the fade high parameter tag.
     *
     * @return The tag
     */
    public String fadeHighParam ()
    {
        return FADE_HIGH_PARAM;
    }


    /**
     * Get the fade low velocity parameter tag.
     *
     * @return The tag
     */
    public String fadeLowVelParam ()
    {
        return FADE_LOW_VEL_PARAM;
    }


    /**
     * Get the fade high velocity parameter tag.
     *
     * @return The tag
     */
    public String fadeHighVelParam ()
    {
        return FADE_HIGH_VEL_PARAM;
    }


    /**
     * Get the sample start parameter tag.
     *
     * @return The tag
     */
    public String sampleStartParam ()
    {
        return SAMPLE_START_PARAM;
    }


    /**
     * Get the sample end parameter tag.
     *
     * @return The tag
     */
    public String sampleEndParam ()
    {
        return SAMPLE_END_PARAM;
    }


    /**
     * Get the group filter tag (only Kontakt 1).
     *
     * @return The tag
     */
    public String groupFilter ()
    {
        return GROUP_FILTER;
    }


    /**
     * Calculate the tuning value from the different tuning values.
     *
     * @param zoneTune The zone tuning
     * @param groupTune The group tuning
     * @param progTune The program tuning
     * @return The tuning [-1..1] which stands for [-100,100] semitones
     */
    public abstract double calculateTune (double zoneTune, double groupTune, double progTune);
}
