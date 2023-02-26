package de.mossgrabers.convertwithmoss.format.nki;

public abstract class AbstractTagsAndAttributes
{

    /** The program element name attribute. */
    private static final String PROGRAM_NAME           = "name";

    /** The parameters element. */
    private static final String PARAMETERS             = "Parameters";

    /** The value element. */
    private static final String VALUE                  = "V";

    /** The value name attribute. */
    private static final String VALUE_NAME_ATTRIBUTE   = "name";

    /** The value name attribute. */
    private static final String VALUE_VAlUE_ATTRIBUTE  = "value";

    /** The groups element of a program. */
    private static final String GROUPS                 = "Groups";

    /** The zones element of a program. */
    private static final String ZONES                  = "Zones";

    /** The group name attribute. */
    private static final String GROUP_NAME_ATTRIBUTE   = "name";

    /** The internal modulators element. */
    private static final String INT_MODULATORS_ELEMENT = "IntModulators";

    /** The envelope element. */
    private static final String ENVELOPE_ELEMENT       = "Envelope";

    /** The bypass parameter. */
    private static final String BYPASS_PARAM           = "bypass";

    /** The yes value. */
    private static final String YES                    = "yes";

    /** The target volume value. */
    private static final String TARGET_VOL_VALUE       = "volume";

    /** The target parameter. */
    private static final String TARGET_PARAM           = "target";

    /** The intensity parameter. */
    private static final String INTENSITY_PARAM        = "intensity";

    /** The envelope type attribute. */
    private static final String ENV_TYPE_ATTRIBUTE     = "type";

    /** The AHD envelope type value. */
    private static final String AHD_ENV_TYPE_VALUE     = "ahd";

    /** The AHDSR envelope type value. */
    private static final String AHDSR_ENV_TYPE_VALUE   = "ahdsr";

    /** The attack parameter. */
    private static final String ATTACK_PARAM           = "attack";

    /** The hold parameter. */
    private static final String HOLD_PARAM             = "hold";

    /** The decay parameter. */
    private static final String DECAY_PARAM            = "decay";

    /** The hold parameter. */
    private static final String SUSTAIN_PARAM          = "sustain";

    /** The release parameter. */
    private static final String RELEASE_PARAM          = "release";

    /** The external modulators element. */
    private static final String EXT_MODULATORS_ELEMENT = "ExtModulators";

    /** The source parameter. */
    private static final String SOURCE_PARAM           = "source";

    /** The pitch bend value. */
    private static final String PITCH_BEND_VALUE       = "pitchBend";

    /** The pitch value. */
    private static final String PITCH_VALUE            = "pitch";

    /** The intensity value. */
    private static final String INTENSITY_VALUE        = "intensity";

    /** The index attribute. */
    private static final String INDEX_ATTRIBUTE        = "index";

    /** The group index attribute. */
    private static final String GROUP_INDEX_ATTRIBUTE  = "groupIdx";

    /** The sample element of a program. */
    private static final String ZONE_SAMPLE            = "Sample";

    /** The key tracking parameter. */
    private static final String KEY_TRACKING_PARAM     = "keyTracking";

    /** The group volume parameter. */
    private static final String GROUP_VOL_PARAM        = "volume";

    /** The zone volume parameter. */
    private static final String ZONE_VOL_PARAM         = "zoneVolume";

    /** The zone tune parameter. */
    private static final String ZONE_TUNE_PARAM        = "zoneTune";

    /** The group tune parameter. */
    private static final String GROUP_TUNE_PARAM       = "tune";

    /** The zone pan parameter. */
    private static final String ZONE_PAN_PARAM         = "zonePan";

    /** The group pan parameter. */
    private static final String GROUP_PAN_PARAM        = "pan";

    /** The reverse parameter. */
    private static final String REVERSE_PARAM          = "reverse";

    /** The loops element. */
    private static final String LOOPS_ELEMENT          = "Loops";

    /** The loop element. */
    private static final String LOOP_ELEMENT           = "Loop";

    /** The loop start parameter. */
    private static final String LOOP_START_PARAM       = "loopStart";

    /** The loop length parameter. */
    private static final String LOOP_LENGTH_PARAM      = "loopLength";

    /** The mode parameter. */
    private static final String LOOP_MODE_PARAM        = "mode";

    /** The crossfade length parameter. */
    private static final String XFADE_LENGTH_PARAM     = "xfadeLength";

    /** The until end value. */
    private static final String UNTIL_END_VALUE        = "until_end";

    /** The until release value. */
    private static final String UNTIL_RELEASE_VALUE    = "until_release";

    /** The oneshot value. */
    private static final String ONESHOT_VALUE          = "oneshot";

    /** The alternating loop parameter. */
    private static final String ALTERNATING_LOOP_PARAM = "alternatingLoop";

    /** The root key parameter. */
    private static final String ROOT_KEY_PARAM         = "rootKey";

    /** The low key parameter. */
    private static final String LOW_KEY_PARAM          = "lowKey";

    /** The high key parameter. */
    private static final String HIGH_KEY_PARAM         = "highKey";

    /** The low key parameter. */
    private static final String LOW_VELOCITY_PARAM     = "lowVelocity";

    /** The high key parameter. */
    private static final String HIGH_VELOCITY_PARAM    = "highVelocity";

    /** The fade low parameter. */
    private static final String FADE_LOW_PARAM         = "fadeLowKey";

    /** The fade high parameter. */
    private static final String FADE_HIGH_PARAM        = "fadeHighKey";

    /** The fade low velocity parameter. */
    private static final String FADE_LOW_VEL_PARAM     = "fadeLowVelo";

    /** The fade high velocity parameter. */
    private static final String FADE_HIGH_VEL_PARAM    = "fadeHighVelo";

    /** The sample start parameter. */
    private static final String SAMPLE_START_PARAM     = "sampleStart";

    /** The sample end parameter. */
    private static final String SAMPLE_END_PARAM       = "sampleEnd";


    abstract public String program ();


    public String programName ()
    {
        return PROGRAM_NAME;
    }


    public String parameters ()
    {
        return PARAMETERS;
    }


    public String value ()
    {
        return VALUE;
    }


    public String valueNameAttribute ()
    {
        return VALUE_NAME_ATTRIBUTE;
    }


    public String valueValueAttribute ()
    {
        return VALUE_VAlUE_ATTRIBUTE;
    }


    public String groups ()
    {
        return GROUPS;
    }


    abstract public String group ();


    public String zones ()
    {
        return ZONES;
    }


    abstract public String zone ();


    public String groupNameAttribute ()
    {
        return GROUP_NAME_ATTRIBUTE;
    }


    public String intModulatorsElement ()
    {
        return INT_MODULATORS_ELEMENT;
    }


    abstract public String intModulatorElement ();


    public String envelopeElement ()
    {
        return ENVELOPE_ELEMENT;
    }


    public String bypassParam ()
    {
        return BYPASS_PARAM;
    }


    public String yes ()
    {
        return YES;
    }


    public String targetVolValue ()
    {
        return TARGET_VOL_VALUE;
    }


    public String targetParam ()
    {
        return TARGET_PARAM;
    }


    public String intensityParam ()
    {
        return INTENSITY_PARAM;
    }


    public String envTypeAttribute ()
    {
        return ENV_TYPE_ATTRIBUTE;
    }


    public String ahdEnvTypeValue ()
    {
        return AHD_ENV_TYPE_VALUE;
    }


    public String ahdsrEnvTypeValue ()
    {
        return AHDSR_ENV_TYPE_VALUE;
    }


    public String attackParam ()
    {
        return ATTACK_PARAM;
    }


    public String holdParam ()
    {
        return HOLD_PARAM;
    }


    public String decayParam ()
    {
        return DECAY_PARAM;
    }


    public String sustainParam ()
    {
        return SUSTAIN_PARAM;
    }


    public String releaseParam ()
    {
        return RELEASE_PARAM;
    }


    public String extModulatorsElement ()
    {
        return EXT_MODULATORS_ELEMENT;
    }


    abstract public String extModulatorElement ();


    public String sourceParam ()
    {
        return SOURCE_PARAM;
    }


    public String pitchBendValue ()
    {
        return PITCH_BEND_VALUE;
    }


    public String pitchValue ()
    {
        return PITCH_VALUE;
    }


    public String intensityValue ()
    {
        return INTENSITY_VALUE;
    }


    public String indexAttribute ()
    {
        return INDEX_ATTRIBUTE;
    }


    public String groupIndexAttribute ()
    {
        return GROUP_INDEX_ATTRIBUTE;
    }


    public String zoneSample ()
    {
        return ZONE_SAMPLE;
    }


    abstract public String sampleFileAttribute ();


    public String keyTrackingParam ()
    {
        return KEY_TRACKING_PARAM;
    }


    public String zoneVolParam ()
    {
        return ZONE_VOL_PARAM;
    }


    public String groupVolParam ()
    {
        return GROUP_VOL_PARAM;
    }


    abstract public String progVolParam ();


    public String zoneTuneParam ()
    {
        return ZONE_TUNE_PARAM;
    }


    public String groupTuneParam ()
    {
        return GROUP_TUNE_PARAM;
    }


    abstract public String progTuneParam ();


    public String zonePanParam ()
    {
        return ZONE_PAN_PARAM;
    }


    public String groupPanParam ()
    {
        return GROUP_PAN_PARAM;
    }


    abstract public String progPanParam ();


    public String reverseParam ()
    {
        return REVERSE_PARAM;
    }


    public String loopsElement ()
    {
        return LOOPS_ELEMENT;
    }


    public String loopElement ()
    {
        return LOOP_ELEMENT;
    }


    public String loopStartParam ()
    {
        return LOOP_START_PARAM;
    }


    public String loopLengthParam ()
    {
        return LOOP_LENGTH_PARAM;
    }


    public String loopModeParam ()
    {
        return LOOP_MODE_PARAM;
    }


    public String xfadeLengthParam ()
    {
        return XFADE_LENGTH_PARAM;
    }


    public String untilEndValue ()
    {
        return UNTIL_END_VALUE;
    }


    public String untilReleaseValue ()
    {
        return UNTIL_RELEASE_VALUE;
    }


    public String oneshotValue ()
    {
        return ONESHOT_VALUE;
    }


    public String alternatingLoopParam ()
    {
        return ALTERNATING_LOOP_PARAM;
    }


    abstract public String rootContainer ();


    public String rootKeyParam ()
    {
        return ROOT_KEY_PARAM;
    }


    public String lowKeyParam ()
    {
        return LOW_KEY_PARAM;
    }


    public String highKeyParam ()
    {
        return HIGH_KEY_PARAM;
    }


    public String lowVelocityParam ()
    {
        return LOW_VELOCITY_PARAM;
    }


    public String highVelocityParam ()
    {
        return HIGH_VELOCITY_PARAM;
    }


    public String fadeLowParam ()
    {
        return FADE_LOW_PARAM;
    }


    public String fadeHighParam ()
    {
        return FADE_HIGH_PARAM;
    }


    public String fadeLowVelParam ()
    {
        return FADE_LOW_VEL_PARAM;
    }


    public String fadeHighVelParam ()
    {
        return FADE_HIGH_VEL_PARAM;
    }


    public String sampleStartParam ()
    {
        return SAMPLE_START_PARAM;
    }


    public String sampleEndParam ()
    {
        return SAMPLE_END_PARAM;
    }
}
