// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

/**
 * The Ableton format consists of several XML tags.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonTag
{
    /** The root tag. */
    public static final String TAG_ROOT                    = "Ableton";

    /** The Simpler device preset tag. */
    public static final String TAG_DEVICE_SIMPLER          = "OriginalSimpler";
    /** The Sampler device preset tag. */
    public static final String TAG_DEVICE_SAMPLER          = "MultiSampler";
    /** The Rack device preset tag. */
    public static final String TAG_DEVICE_RACK             = "GroupDevicePreset";

    /** The user defined name of the preset. */
    public static final String TAG_USER_NAME               = "UserName";
    /** The annotation tag. */
    public static final String TAG_ANNOTATION              = "Annotation";

    /** The preset reference tag. */
    public static final String TAG_PRESET_REF              = "PresetRef";

    /** The last preset reference tag. */
    public static final String TAG_LAST_PRESET_REF         = "LastPresetRef";
    /** The value tag. */
    public static final String TAG_VALUE                   = "Value";
    /** The file preset reference tag. */
    public static final String TAG_FILE_PRESET_REF         = "FilePresetRef";
    /** Synonym for the file preset reference tag. */
    public static final String TAG_FILE_PRESET_REF2        = "AbletonDefaultPresetRef";

    /** The Player tag. */
    public static final String TAG_PLAYER                  = "Player";
    /** The Multi-Sample Map tag. */
    public static final String TAG_MULTI_SAMPLE_MAP        = "MultiSampleMap";
    /** The Sample Parts tag. */
    public static final String TAG_SAMPLE_PARTS            = "SampleParts";
    /** The Multi-Sample Parts tag. */
    public static final String TAG_MULTI_SAMPLE_PART       = "MultiSamplePart";

    /** The name of the sample zone. */
    public static final String TAG_NAME                    = "Name";
    /** The name of the sample reference tag. */
    public static final String TAG_SAMPLE_REF              = "SampleRef";
    /** The name of the file reference tag. */
    public static final String TAG_FILE_REF                = "FileRef";
    /** The name of the relative path tag. */
    public static final String TAG_RELATIVE_PATH           = "RelativePath";
    /** The name of the relative path type tag. */
    public static final String TAG_RELATIVE_PATH_TYPE      = "RelativePathType";
    /** The name of the absolute path tag. */
    public static final String TAG_PATH                    = "Path";

    /** The key-range tag. */
    public static final String TAG_KEY_RANGE               = "KeyRange";
    /** The velocity-range tag. */
    public static final String TAG_VELOCITY_RANGE          = "VelocityRange";
    /** The minimum tag. */
    public static final String TAG_MINIMUM                 = "Min";
    /** The maximum tag. */
    public static final String TAG_MAXIMUM                 = "Max";
    /** The cross-fade minimum tag. */
    public static final String TAG_CROSSFADE_MINIMUM       = "CrossfadeMin";
    /** The cross-fade maximum tag. */
    public static final String TAG_CROSSFADE_MAXIMUM       = "CrossfadeMax";
    /** The root key tag. */
    public static final String TAG_ROOT_KEY                = "RootKey";
    /** The de-tune tag. */
    public static final String TAG_DETUNE                  = "Detune";
    /** The tune-scale tag. */
    public static final String TAG_TUNE_SCALE              = "TuneScale";
    /** The volume tag. */
    public static final String TAG_VOLUME                  = "Volume";
    /** The panning tag. */
    public static final String TAG_PANORAMA                = "Panorama";
    /** The Sample Start tag. */
    public static final String TAG_SAMPLE_START            = "SampleStart";
    /** The Sample End tag. */
    public static final String TAG_SAMPLE_END              = "SampleEnd";
    /** The Sustain Loop tag. */
    public static final String TAG_SUSTAIN_LOOP            = "SustainLoop";
    /** The Loop Mode tag. */
    public static final String TAG_LOOP_MODE               = "Mode";
    /** The Loop Start tag. */
    public static final String TAG_LOOP_START              = "Start";
    /** The Loop End tag. */
    public static final String TAG_LOOP_END                = "End";
    /** The Loop Cross-fade tag. */
    public static final String TAG_LOOP_CROSSFADE          = "Crossfade";
    /** The Manual tag. */
    public static final String TAG_MANUAL                  = "Manual";
    /** The reverse tag. */
    public static final String TAG_REVERSE                 = "Reverse";

    /** The Filter tag. */
    public static final String TAG_FILTER                  = "Filter";
    /** The Filter Is-On tag. */
    public static final String TAG_IS_ON                   = "IsOn";
    /** A modulation slot tag. */
    public static final String TAG_SLOT                    = "Slot";
    /** The Simpler Filter tag. */
    public static final String TAG_SIMPLER_FILTER          = "SimplerFilter";
    /** The Filter type tag. */
    public static final String TAG_FILTER_TYPE             = "Type";
    /** The Filter frequency tag. */
    public static final String TAG_FILTER_FREQUENCY        = "Freq";
    /** The Filter slope tag. */
    public static final String TAG_FILTER_SLOPE            = "Slope";
    /** The Filter resonance tag. */
    public static final String TAG_FILTER_RESONANCE        = "Res";
    /** The Attack Time tag. */
    public static final String TAG_ATTACK_TIME             = "AttackTime";
    /** The Decay Time tag. */
    public static final String TAG_DECAY_TIME              = "DecayTime";
    /** The Release Time tag. */
    public static final String TAG_RELEASE_TIME            = "ReleaseTime";
    /** The Start Level tag. */
    public static final String TAG_ATTACK_LEVEL            = "AttackLevel";
    /** The Sustain Level tag. */
    public static final String TAG_SUSTAIN_LEVEL           = "SustainLevel";
    /** The End Level tag. */
    public static final String TAG_RELEASE_LEVEL           = "ReleaseLevel";
    /** The Attack Slope tag. */
    public static final String TAG_ATTACK_SLOPE            = "AttackSlope";
    /** The Decay Slope tag. */
    public static final String TAG_DECAY_SLOPE             = "DecaySlope";
    /** The Release Slope tag. */
    public static final String TAG_RELEASE_SLOPE           = "ReleaseSlope";
    /** The Amount tag. */
    public static final String TAG_AMOUNT                  = "Amount";
    /** The modulate by velocity tag. */
    public static final String TAG_MOD_BY_VELOCITY         = "ModByVelocity";

    /** The Auxiliary Envelope tag. */
    public static final String TAG_SIMPLER_AUX_ENVELOPE    = "SimplerAuxEnvelope";
    /** The Auxiliary Envelope destination tag. */
    public static final String TAG_MODULATION_DESTINATION  = "ModDst";
    /** The 1st Auxiliary Envelope connection tag. */
    public static final String TAG_MODULATION_CONNECTION_0 = "ModConnections.0";
    /** The 2nd Auxiliary Envelope connection tag. */
    public static final String TAG_MODULATION_CONNECTION_1 = "ModConnections.1";
    /** The Auxiliary Envelope connection tag. */
    public static final String TAG_MODULATION_CONNECTION   = "Connection";

    /** The Volume and Panning tag. */
    public static final String TAG_VOLUME_AND_PAN          = "VolumeAndPan";
    /** The Volume Velocity scale tag. */
    public static final String TAG_VOLUME_VEL_SCALE        = "VolumeVelScale";

    /** The Auxiliary Envelope tag. */
    public static final String TAG_AUX_ENVELOPE            = "AuxEnv";

    /** The envelope tag. */
    public static final String TAG_ENVELOPE                = "Envelope";

    /** The creator attribute. */
    public static final String ATTR_CREATOR                = "Creator";
    /** The value attribute. */
    public static final String ATTR_VALUE                  = "Value";


    /**
     * Private constructor for utility class.
     */
    private AbletonTag ()
    {
        // Intentionally empty
    }
}
