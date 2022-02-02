// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.akai;

/**
 * The MPC keygroup format consists of several XML tags.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class MPCKeygroupTag
{
    ///////////////////////////////////////////////////////
    // Elements

    /** The root element. */
    public static final String ROOT                         = "MPCVObject";

    /** The program element. */
    public static final String ROOT_PROGRAM                 = "Program";
    /** The version element. */
    public static final String ROOT_VERSION                 = "Version";

    /** The file version element. */
    public static final String VERSION_FILE_VERSION         = "File_Version";

    /** The program name element. */
    public static final String PROGRAM_NAME                 = "ProgramName";
    /** The program instrument element. */
    public static final String PROGRAM_INSTRUMENTS          = "Instruments";
    /** The program pad note map element. */
    public static final String PROGRAM_PAD_NOTE_MAP         = "PadNoteMap";
    /** The program number of keygroups element. */
    public static final String PROGRAM_NUM_KEYGROUPS        = "KeygroupNumKeygroups";
    /** The program pads setup element. */
    public static final String PROGRAM_PADS                 = "ProgramPads-";
    /** The program keygroup pitch bend range element. */
    public static final String PROGRAM_PITCHBEND_RANGE      = "KeygroupPitchBendRange";
    /** The program keygroup wheel to LFO element. */
    public static final String PROGRAM_WHEEL_TO_LFO         = "KeygroupWheelToLfo";

    /** The instruments instrument element. */
    public static final String INSTRUMENTS_INSTRUMENT       = "Instrument";

    /** The low note element of the instrument element. */
    public static final String INSTRUMENT_LOW_NOTE          = "LowNote";
    /** The high note element of the instrument element. */
    public static final String INSTRUMENT_HIGH_NOTE         = "HighNote";
    /** The ignore base note element of the instrument element. */
    public static final String INSTRUMENT_IGNORE_BASE_NOTE  = "IgnoreBaseNote";
    /** The zone play element of the instrument element. */
    public static final String INSTRUMENT_ZONE_PLAY         = "ZonePlay";
    /** The one-shot element of the instrument element. */
    public static final String INSTRUMENT_ONE_SHOT          = "OneShot";
    /** The layers element of the instrument element. */
    public static final String INSTRUMENT_LAYERS            = "Layers";

    /** The filter type element of the instrument element. */
    public static final String INSTRUMENT_FILTER_TYPE       = "FilterType";
    /** The filter cutoff element of the instrument element. */
    public static final String INSTRUMENT_FILTER_CUTOFF     = "Cutoff";
    /** The filter resonance element of the instrument element. */
    public static final String INSTRUMENT_FILTER_RESONANCE  = "Resonance";

    /** The filter envelope amount element of the instrument element. */
    public static final String INSTRUMENT_FILTER_ENV_AMOUNT = "FilterEnvAmt";

    /** The filter attack element of the instrument element. */
    public static final String INSTRUMENT_FILTER_ATTACK     = "FilterAttack";
    /** The filter hold element of the instrument element. */
    public static final String INSTRUMENT_FILTER_HOLD       = "FilterHold";
    /** The filter decay element of the instrument element. */
    public static final String INSTRUMENT_FILTER_DECAY      = "FilterDecay";
    /** The filter sustain element of the instrument element. */
    public static final String INSTRUMENT_FILTER_SUSTAIN    = "FilterSustain";
    /** The filter release element of the instrument element. */
    public static final String INSTRUMENT_FILTER_RELEASE    = "FilterRelease";

    /** The volume attack element of the instrument element. */
    public static final String INSTRUMENT_VOLUME_ATTACK     = "VolumeAttack";
    /** The volume hold element of the instrument element. */
    public static final String INSTRUMENT_VOLUME_HOLD       = "VolumeHold";
    /** The volume decay element of the instrument element. */
    public static final String INSTRUMENT_VOLUME_DECAY      = "VolumeDecay";
    /** The volume sustain element of the instrument element. */
    public static final String INSTRUMENT_VOLUME_SUSTAIN    = "VolumeSustain";
    /** The volume release element of the instrument element. */
    public static final String INSTRUMENT_VOLUME_RELEASE    = "VolumeRelease";

    /** The pitch attack element of the instrument element. */
    public static final String INSTRUMENT_PITCH_ATTACK      = "PitchAttack";
    /** The pitch hold element of the instrument element. */
    public static final String INSTRUMENT_PITCH_HOLD        = "PitchHold";
    /** The pitch decay element of the instrument element. */
    public static final String INSTRUMENT_PITCH_DECAY       = "PitchDecay";
    /** The pitch sustain element of the instrument element. */
    public static final String INSTRUMENT_PITCH_SUSTAIN     = "PitchSustain";
    /** The pitch release element of the instrument element. */
    public static final String INSTRUMENT_PITCH_RELEASE     = "PitchRelease";

    /** The pitch envelope amount element of the instrument element. */
    public static final String INSTRUMENT_PITCH_ENV_AMOUNT  = "PitchEnvAmount";

    /** The layer element of the layers element. */
    public static final String LAYERS_LAYER                 = "Layer";

    /** The sample name element of the layer element. */
    public static final String LAYER_SAMPLE_NAME            = "SampleName";
    /** The active element of the layer element. */
    public static final String LAYER_ACTIVE                 = "Active";
    /** The volume element of the layer element. */
    public static final String LAYER_VOLUME                 = "Volume";
    /** The panorama element of the layer element. */
    public static final String LAYER_PAN                    = "Pan";
    /** The pitch element of the layer element. */
    public static final String LAYER_PITCH                  = "Pitch";
    /** The coarse tune element of the layer element. */
    public static final String LAYER_COARSE_TUNE            = "TuneCoarse";
    /** The fine tune element of the layer element. */
    public static final String LAYER_FINE_TUNE              = "TuneFine";
    /** The root note element of the layer element. */
    public static final String LAYER_ROOT_NOTE              = "RootNote";
    /** The key track element of the layer element. */
    public static final String LAYER_KEY_TRACK              = "KeyTrack";
    /** The velocity start element of the instrument element. */
    public static final String LAYER_VEL_START              = "VelStart";
    /** The velocity end element of the instrument element. */
    public static final String LAYER_VEL_END                = "VelEnd";
    /** The sample start element of the layer element. */
    public static final String LAYER_SAMPLE_START           = "SampleStart";
    /** The sample end element of the layer element. */
    public static final String LAYER_SAMPLE_END             = "SampleEnd";
    /** The loop start element of the layer element. */
    public static final String LAYER_LOOP_START             = "LoopStart";
    /** The loop end element of the layer element. */
    public static final String LAYER_LOOP_END               = "LoopEnd";
    /** The loop crossfade element of the layer element. */
    public static final String LAYER_LOOP_CROSSFADE         = "LoopCrossfadeLength";
    /** The loop tune element of the layer element. */
    public static final String LAYER_LOOP_TUNE              = "LoopTune";
    /** The pitch randomization element of the layer element. */
    public static final String LAYER_PITCH_RANDOM           = "PitchRandom";
    /** The volume randomization element of the layer element. */
    public static final String LAYER_VOLUME_RANDOM          = "VolumeRandom";
    /** The panorama randomization element of the layer element. */
    public static final String LAYER_PAN_RANDOM             = "PanRandom";
    /** The offset randomization element of the layer element. */
    public static final String LAYER_OFFSET_RANDOM          = "OffsetRandom";
    /** The sample file element of the layer element. */
    public static final String LAYER_SAMPLE_FILE            = "SampleFile";
    /** The slice index element of the layer element. */
    public static final String LAYER_SLICE_INDEX            = "SliceIndex";
    /** The direction element of the layer element. */
    public static final String LAYER_DIRECTION              = "Direction";
    /** The offset element of the layer element. */
    public static final String LAYER_OFFSET                 = "Offset";
    /** The slice start element of the layer element. */
    public static final String LAYER_SLICE_START            = "SliceStart";
    /** The slice end element of the layer element. */
    public static final String LAYER_SLICE_END              = "SliceEnd";
    /** The slice loop element of the layer element. */
    public static final String LAYER_SLICE_LOOP             = "SliceLoop";
    /** The slice loop start element of the layer element. */
    public static final String LAYER_SLICE_LOOP_START       = "SliceLoopStart";
    /** The slice loop crossfade element of the layer element. */
    public static final String LAYER_SLICE_LOOP_CROSSFADE   = "SliceLoopCrossFadeLength";
    /** The slice tail position element of the layer element. */
    public static final String LAYER_SLICE_TAIL_POSITION    = "SliceTailPosition";
    /** The slice tail length element of the layer element. */
    public static final String LAYER_SLICE_TAIL_LENGTH      = "SliceTailLength";

    /** The pad note element of the pad note map element. */
    public static final String PAD_NOTE_MAP_PAD_NOTE        = "PadNote";

    /** The pad note element of the pad note map element. */
    public static final String PAD_NOTE_NOTE                = "Note";

    ///////////////////////////////////////////////////////
    // Attributes

    /** The type attribute of the program element. */
    public static final String PROGRAM_TYPE                 = "type";

    /** The number attribute of the instrument element. */
    public static final String INSTRUMENT_NUMBER            = "number";

    /** The number attribute of the pad note element. */
    public static final String PAD_NOTE_NUMBER              = "number";

    /** The program type keygroup. */
    public static final String TYPE_KEYGROUP                = "Keygroup";
    /** The program type drum. */
    public static final String TYPE_DRUM                    = "Drum";

    /** The true value. */
    public static final String TRUE                         = "True";


    /**
     * Private constructor for utility class.
     */
    private MPCKeygroupTag ()
    {
        // Intentionally empty
    }
}
