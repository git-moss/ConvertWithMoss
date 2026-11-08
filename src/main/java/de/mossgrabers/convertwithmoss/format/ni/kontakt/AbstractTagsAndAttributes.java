// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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

    /** The voice group parameter, which is the Kontakt name for an exclusive group. */
    private static final String VOICE_GROUP_PARAM          = "voiceGroup";

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

    /** The note-off-less mode parameter of an envelope. Only present from Kontakt 2 onwards. */
    private static final String NOTE_OFF_LESS_MODE_PARAM   = "noteOffLessMode";

    /** The alternating loop parameter. */
    private static final String ALTERNATING_LOOP_PARAM     = "alternatingLoop";

    /** The loop tuning parameter. */
    private static final String LOOP_TUNING                = "loopTuning";

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

    /** The filter type parameter (Kontakt 1) or attribute (Kontakt 2). */
    private static final String FILTER_TYPE                = "type";

    /** The element which contains the insert effects of a group (only Kontakt 2). */
    private static final String GROUP_INSERT_FX_ELEMENT    = "GroupInsertFX";

    /** The element which contains the start criteria of a group. */
    private static final String GROUP_START                = "GroupStart";

    /** One start criteria of a group. */
    private static final String START_CRITERIA             = "StartCriteria";

    /** The mode parameter of a start criteria. */
    private static final String START_MODE_PARAM           = "mode";

    /** The prefix of all cycle (round-robin) modes of a start criteria. */
    private static final String CYCLE_MODE_PREFIX          = "cycle";

    /** The position of the group in a round-robin cycle. */
    private static final String CYCLE_CLASS_PARAM          = "cycleClass";

    /** The parameter which indicates that a group is only triggered by the sequencer. */
    private static final String SEQUENCER_ONLY_PARAM       = "sequencerOnly";

    /** The release trigger parameter of a group. */
    private static final String RELEASE_TRIGGER_PARAM      = "releaseTrigger";

    /** The polyphony element of a program. */
    private static final String POLYPHONY_ELEMENT          = "Polyphony";

    /** A voice group element of the polyphony element. */
    private static final String VOICE_GROUP_ELEMENT        = "VoiceGroup";

    /** The maximum number of voices of a voice group. */
    private static final String MAX_NUM_VOICES_PARAM       = "maxNumVoices";

    /** The low frequency oscillator element of an internal modulator. */
    private static final String LFO_ELEMENT                = "LFO";

    /** The frequency parameter of a low frequency oscillator. */
    private static final String FREQUENCY_PARAM            = "frequency";

    /** The delay parameter of a low frequency oscillator. */
    private static final String DELAY_PARAM                = "delay";

    /** The start phase parameter of a low frequency oscillator (only Kontakt 2). */
    private static final String START_PHASE_PARAM          = "startPhase";

    /** The retrigger parameter of an internal modulator. */
    private static final String RETRIGGER_PARAM            = "retrigger";

    /** The decay-break-decay envelope type value. */
    private static final String DBD_ENV_TYPE_VALUE         = "dbd";

    /** The first decay parameter of a decay-break-decay envelope. */
    private static final String DECAY1_PARAM               = "decay1";

    /** The break level parameter of a decay-break-decay envelope. */
    private static final String BREAK_PARAM                = "break";

    /** The second decay parameter of a decay-break-decay envelope. */
    private static final String DECAY2_PARAM               = "decay2";

    /** The key position source value of an external modulator. */
    private static final String KEY_POSITION_VALUE         = "keyPos";

    /** The MIDI CC source value of an external modulator. */
    private static final String MIDI_CC_VALUE              = "midiCC";

    /** The MIDI CC number parameter of an external modulator. */
    private static final String CC_NUMBER_PARAM            = "ccNumber";

    /** The index of the modulated effect (e.g. a filter) of a modulation target (Kontakt 2). */
    private static final String TARGET_OBJECT_INDEX_PARAM  = "targetObjIdx";

    /** The index of the modulated internal modulator of an external modulator (Kontakt 1). */
    private static final String TARGET_MOD_INDEX_PARAM     = "targetModIdx";

    /** The version attribute of an element. */
    private static final String VERSION_ATTRIBUTE          = "version";


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
     * Get the voice group parameter tag, which is the Kontakt name for an exclusive group.
     *
     * @return The tag
     */
    public String voiceGroupParam ()
    {
        return VOICE_GROUP_PARAM;
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
     * Get the loop tuning tag.
     *
     * @return The tag
     */
    public String loopTuningParam ()
    {
        return LOOP_TUNING;
    }


    /**
     * Get the cross-fade length tag.
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
     * Get the note-off-less mode tag. If the amplitude envelope is set to that mode, note-off
     * events are ignored and the sample is always played back to its end (one-shot).
     *
     * @return The tag
     */
    public String noteOffLessModeParam ()
    {
        return NOTE_OFF_LESS_MODE_PARAM;
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
     * Get the filter tag. Kontakt 1 stores it in the group, Kontakt 2 in a group insert effect.
     *
     * @return The tag
     */
    public String groupFilter ()
    {
        return GROUP_FILTER;
    }


    /**
     * Get the filter type parameter (Kontakt 1) or attribute (Kontakt 2) tag.
     *
     * @return The tag
     */
    public String filterType ()
    {
        return FILTER_TYPE;
    }


    /**
     * Get the tag of the element which contains the insert effects of a group (only Kontakt 2).
     *
     * @return The tag
     */
    public String groupInsertEffectsElement ()
    {
        return GROUP_INSERT_FX_ELEMENT;
    }


    /**
     * Get the tag of the element which contains the start criteria of a group.
     *
     * @return The tag
     */
    public String groupStart ()
    {
        return GROUP_START;
    }


    /**
     * Get the start criteria tag.
     *
     * @return The tag
     */
    public String startCriteria ()
    {
        return START_CRITERIA;
    }


    /**
     * Get the mode parameter tag of a start criteria.
     *
     * @return The tag
     */
    public String startModeParam ()
    {
        return START_MODE_PARAM;
    }


    /**
     * Get the prefix of all cycle (round-robin) modes of a start criteria.
     *
     * @return The value
     */
    public String cycleModePrefix ()
    {
        return CYCLE_MODE_PREFIX;
    }


    /**
     * Get the cycle class (round-robin position) parameter tag of a start criteria.
     *
     * @return The tag
     */
    public String cycleClassParam ()
    {
        return CYCLE_CLASS_PARAM;
    }


    /**
     * Get the sequencer only parameter tag of a start criteria.
     *
     * @return The tag
     */
    public String sequencerOnlyParam ()
    {
        return SEQUENCER_ONLY_PARAM;
    }


    /**
     * Get the release trigger parameter tag of a group.
     *
     * @return The tag
     */
    public String releaseTriggerParam ()
    {
        return RELEASE_TRIGGER_PARAM;
    }


    /**
     * Get the polyphony element tag.
     *
     * @return The tag
     */
    public String polyphonyElement ()
    {
        return POLYPHONY_ELEMENT;
    }


    /**
     * Get the voice group element tag.
     *
     * @return The tag
     */
    public String voiceGroupElement ()
    {
        return VOICE_GROUP_ELEMENT;
    }


    /**
     * Get the maximum number of voices parameter tag.
     *
     * @return The tag
     */
    public String maxNumVoicesParam ()
    {
        return MAX_NUM_VOICES_PARAM;
    }


    /**
     * Get the low frequency oscillator element tag.
     *
     * @return The tag
     */
    public String lfoElement ()
    {
        return LFO_ELEMENT;
    }


    /**
     * Get the frequency parameter tag of a low frequency oscillator.
     *
     * @return The tag
     */
    public String frequencyParam ()
    {
        return FREQUENCY_PARAM;
    }


    /**
     * Get the delay parameter tag of a low frequency oscillator.
     *
     * @return The tag
     */
    public String delayParam ()
    {
        return DELAY_PARAM;
    }


    /**
     * Get the start phase parameter tag of a low frequency oscillator.
     *
     * @return The tag
     */
    public String startPhaseParam ()
    {
        return START_PHASE_PARAM;
    }


    /**
     * Get the retrigger parameter tag of an internal modulator.
     *
     * @return The tag
     */
    public String retriggerParam ()
    {
        return RETRIGGER_PARAM;
    }


    /**
     * Get the decay-break-decay envelope type value.
     *
     * @return The value
     */
    public String dbdEnvTypeValue ()
    {
        return DBD_ENV_TYPE_VALUE;
    }


    /**
     * Get the first decay parameter tag of a decay-break-decay envelope.
     *
     * @return The tag
     */
    public String decay1Param ()
    {
        return DECAY1_PARAM;
    }


    /**
     * Get the break level parameter tag of a decay-break-decay envelope.
     *
     * @return The tag
     */
    public String breakParam ()
    {
        return BREAK_PARAM;
    }


    /**
     * Get the second decay parameter tag of a decay-break-decay envelope.
     *
     * @return The tag
     */
    public String decay2Param ()
    {
        return DECAY2_PARAM;
    }


    /**
     * Get the key position source value of an external modulator.
     *
     * @return The value
     */
    public String keyPositionValue ()
    {
        return KEY_POSITION_VALUE;
    }


    /**
     * Get the MIDI CC source value of an external modulator.
     *
     * @return The value
     */
    public String midiCCValue ()
    {
        return MIDI_CC_VALUE;
    }


    /**
     * Get the MIDI CC number parameter tag of an external modulator.
     *
     * @return The tag
     */
    public String ccNumberParam ()
    {
        return CC_NUMBER_PARAM;
    }


    /**
     * Get the tag of the parameter which contains the index of the modulated effect (Kontakt 2).
     *
     * @return The tag
     */
    public String targetObjectIndexParam ()
    {
        return TARGET_OBJECT_INDEX_PARAM;
    }


    /**
     * Get the tag of the parameter which contains the index of the modulated internal modulator
     * (Kontakt 1).
     *
     * @return The tag
     */
    public String targetModIndexParam ()
    {
        return TARGET_MOD_INDEX_PARAM;
    }


    /**
     * Get the version attribute tag.
     *
     * @return The tag
     */
    public String versionAttribute ()
    {
        return VERSION_ATTRIBUTE;
    }


    /**
     * Get the tag of an effect element (only Kontakt 2).
     *
     * @return The tag
     */
    public abstract String effectElement ();


    /**
     * Calculate the tuning value from the different tuning values.
     *
     * @param zoneTune The zone tuning
     * @param groupTune The group tuning
     * @param progTune The program tuning
     * @return The tuning [-1..1] which stands for [-100,100] semitones
     */
    public abstract double calculateTune (double zoneTune, double groupTune, double progTune);


    /**
     * Calculate the tuning offset which is contributed by the group alone. This is the value which
     * needs to be added on top of the tuning of a sample zone.
     *
     * @param groupTune The group tuning
     * @return The tuning offset in semi-tones, 0 if the group does not add any tuning
     */
    public abstract double calculateGroupTune (double groupTune);
}
