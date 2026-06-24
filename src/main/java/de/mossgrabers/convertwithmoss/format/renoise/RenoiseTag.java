// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.renoise;

/**
 * The Renoise instrument (XRNI) format consists of several XML tags. The structure (for document
 * version 24-34, which covers Renoise 3.0 - 3.5) is:
 *
 * <pre>
 * RenoiseInstrument
 *   Name
 *   GlobalProperties (Volume, Transpose, Comments/Comment)
 *   SampleGenerator
 *     Samples (Sample*)
 *     ModulationSets (ModulationSet*)
 *     DeviceChains (DeviceChain*)
 *     KeyzoneOverlappingMode
 * </pre>
 *
 * @author Jürgen Moßgraber
 */
public class RenoiseTag
{
    /** The root tag. */
    public static final String ROOT                     = "RenoiseInstrument";
    /** The document version attribute of the root tag. */
    public static final String ATTR_DOC_VERSION         = "doc_version";
    /** The type attribute (present on several typed elements). */
    public static final String ATTR_TYPE                = "type";

    /** The instrument name tag. */
    public static final String NAME                     = "Name";
    /** The global properties tag. */
    public static final String GLOBAL_PROPERTIES        = "GlobalProperties";
    /** The (linear) volume tag. */
    public static final String VOLUME                   = "Volume";
    /** The transpose (semitones) tag. */
    public static final String TRANSPOSE                = "Transpose";
    /** The comments wrapper tag. */
    public static final String COMMENTS                 = "Comments";
    /** A single comment line tag. */
    public static final String COMMENT                  = "Comment";

    /** The sample generator tag. */
    public static final String SAMPLE_GENERATOR         = "SampleGenerator";
    /** The samples wrapper tag. */
    public static final String SAMPLES                  = "Samples";
    /** A single sample tag. */
    public static final String SAMPLE                   = "Sample";
    /** The keyzone overlapping mode tag (round-robin / layering control). */
    public static final String KEYZONE_OVERLAPPING_MODE = "KeyzoneOverlappingMode";

    /** The sample file-name reference tag. */
    public static final String FILE_NAME                = "FileName";
    /** The sample panning tag (0=left, 0.5=center, 1=right). */
    public static final String PANNING                  = "Panning";
    /** The sample fine-tune tag (-127..127). */
    public static final String FINETUNE                 = "Finetune";
    /** The one-shot trigger tag. */
    public static final String ONE_SHOT                 = "OneShotTrigger";
    /** The new-note-action tag. */
    public static final String NEW_NOTE_ACTION          = "NewNoteAction";
    /** The interpolation mode tag. */
    public static final String INTERPOLATION            = "InterpolationMode";
    /** The auto-seek tag. */
    public static final String AUTO_SEEK                = "AutoSeek";
    /** The auto-fade tag. */
    public static final String AUTO_FADE                = "AutoFade";
    /** The loop mode tag. */
    public static final String LOOP_MODE                = "LoopMode";
    /** The release-loop flag tag. */
    public static final String LOOP_RELEASE             = "LoopRelease";
    /** The loop start (frame index) tag. */
    public static final String LOOP_START               = "LoopStart";
    /** The loop end (frame index) tag. */
    public static final String LOOP_END                 = "LoopEnd";
    /** The mute-group index tag. */
    public static final String MUTE_GROUP_INDEX         = "MuteGroupIndex";
    /** The modulation-set index tag (reference into ModulationSets). */
    public static final String MODULATION_SET_INDEX     = "ModulationSetIndex";
    /** The device-chain index tag (reference into DeviceChains). */
    public static final String DEVICE_CHAIN_INDEX       = "DeviceChainIndex";

    /** The sample key/velocity mapping tag. */
    public static final String MAPPING                  = "Mapping";
    /** The mapping layer tag. */
    public static final String LAYER                    = "Layer";
    /** The mapping root note tag (MIDI note number). */
    public static final String BASE_NOTE                = "BaseNote";
    /** The mapping low key tag. */
    public static final String NOTE_START               = "NoteStart";
    /** The mapping high key tag. */
    public static final String NOTE_END                 = "NoteEnd";
    /** The map-key-to-pitch tag. */
    public static final String MAP_KEY_TO_PITCH         = "MapKeyToPitch";
    /** The mapping low velocity tag. */
    public static final String VELOCITY_START           = "VelocityStart";
    /** The mapping high velocity tag. */
    public static final String VELOCITY_END             = "VelocityEnd";
    /** The map-velocity-to-volume tag. */
    public static final String MAP_VELOCITY_TO_VOLUME   = "MapVelocityToVolume";

    /** The modulation sets wrapper tag. */
    public static final String MODULATION_SETS          = "ModulationSets";
    /** A single modulation set tag. */
    public static final String MODULATION_SET           = "ModulationSet";
    /** The filter type (index) of a modulation set. */
    public static final String FILTER_TYPE              = "FilterType";
    /** The filter bank version of a modulation set. */
    public static final String FILTER_BANK_VERSION      = "FilterBankVersion";
    /** The devices wrapper tag (used by modulation sets and device chains). */
    public static final String DEVICES                  = "Devices";

    /** The AHDSR modulation device tag (and its required type attribute value). */
    public static final String AHDSR_DEVICE             = "SampleAhdsrModulationDevice";
    /** The mixer modulation device tag - holds the base input values (cutoff, resonance, ...). */
    public static final String MIXER_DEVICE             = "SampleMixerModulationDevice";
    /** The selected preset name tag. */
    public static final String SELECTED_PRESET_NAME     = "SelectedPresetName";
    /** The selected preset library tag. */
    public static final String SELECTED_PRESET_LIBRARY  = "SelectedPresetLibrary";
    /** The selected-preset-is-modified flag tag. */
    public static final String SELECTED_PRESET_MODIFIED = "SelectedPresetIsModified";
    /** The is-active sub-device tag. */
    public static final String IS_ACTIVE                = "IsActive";
    /** The generic value tag of a device parameter. */
    public static final String VALUE                    = "Value";
    /** The visualization tag of a device parameter. */
    public static final String VISUALIZATION            = "Visualization";
    /** The modulation target tag. */
    public static final String TARGET                   = "Target";
    /** The modulation operator tag. */
    public static final String OPERATOR                 = "Operator";
    /** The bipolar flag tag. */
    public static final String BIPOLAR                  = "Bipolar";
    /** The tempo-synced flag tag. */
    public static final String TEMPO_SYNCED             = "TempoSynced";
    /** The attack parameter tag. */
    public static final String ATTACK                   = "Attack";
    /** The hold parameter tag. */
    public static final String HOLD                     = "Hold";
    /** The decay parameter tag. */
    public static final String DECAY                    = "Decay";
    /** The sustain parameter (level) tag. */
    public static final String SUSTAIN                  = "Sustain";
    /** The release parameter tag. */
    public static final String RELEASE                  = "Release";

    /** The pitch base value parameter tag (mixer device). */
    public static final String PITCH                    = "Pitch";
    /** The pitch modulation range tag (mixer device). */
    public static final String PITCH_MODULATION_RANGE   = "PitchModulationRange";
    /** The cutoff base value parameter tag (mixer device, 0..127). */
    public static final String CUTOFF                   = "Cutoff";
    /** The resonance base value parameter tag (mixer device, 0..127). */
    public static final String RESONANCE                = "Resonance";
    /** The drive base value parameter tag (mixer device). */
    public static final String DRIVE                    = "Drive";

    /** The device chains wrapper tag. */
    public static final String DEVICE_CHAINS            = "DeviceChains";
    /** A single device chain tag. */
    public static final String DEVICE_CHAIN             = "DeviceChain";

    // Enumeration values

    /** Loop mode: no loop. */
    public static final String LOOP_OFF                 = "Off";
    /** Loop mode: forward. */
    public static final String LOOP_FORWARD             = "Forward";
    /** Loop mode: backward. */
    public static final String LOOP_BACKWARD            = "Backward";
    /** Loop mode: alternating. */
    public static final String LOOP_PING_PONG           = "PingPong";

    /** Mapping layer: triggered on note-on. */
    public static final String LAYER_NOTE_ON            = "Note-On Layer";
    /** Mapping layer: triggered on note-off (release). */
    public static final String LAYER_NOTE_OFF           = "Note-Off Layer";

    /** Keyzone overlapping mode: play all overlapping zones (layering). */
    public static final String OVERLAP_PLAY_ALL         = "Play All";
    /** Keyzone overlapping mode: cycle through overlapping zones (round-robin). */
    public static final String OVERLAP_CYCLE            = "Cycle";
    /** Keyzone overlapping mode: pick a random overlapping zone. */
    public static final String OVERLAP_RANDOM           = "Random";

    /** Modulation target: volume (amplitude envelope). */
    public static final String TARGET_VOLUME            = "Volume";
    /** Modulation target: pitch. */
    public static final String TARGET_PITCH             = "Pitch";
    /** Modulation target: filter cutoff. */
    public static final String TARGET_CUTOFF            = "Cutoff";

    /** Modulation operator: multiply with the target's running value. */
    public static final String OP_MULTIPLY              = "*";
    /** Modulation operator: add to the target's running value. */
    public static final String OP_ADD                   = "+";

    /** New-note-action: cut the previous note. */
    public static final String NNA_CUT                  = "Cut";
    /** New-note-action: send a note-off to the previous note. */
    public static final String NNA_NOTE_OFF             = "NoteOff";


    /**
     * Private constructor for utility class.
     */
    private RenoiseTag ()
    {
        // Intentionally empty
    }
}
