// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
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
    public static final String TAG_ROOT               = "Ableton";

    /** The Simpler device preset tag. */
    public static final String TAG_DEVICE_SIMPLER     = "OriginalSimpler";
    /** The Sampler device preset tag. */
    public static final String TAG_DEVICE_SAMPLER     = "MultiSampler";
    /** The Rack device preset tag. */
    public static final String TAG_DEVICE_RACK        = "GroupDevicePreset";

    /** The user defined name of the preset. */
    public static final String TAG_USER_NAME          = "UserName";
    /** The annotation tag. */
    public static final String TAG_ANNOTATION         = "Annotation";

    /** The preset reference tag. */
    public static final String TAG_PRESET_REF         = "PresetRef";

    /** The last preset reference tag. */
    public static final String TAG_LAST_PRESET_REF    = "LastPresetRef";
    /** The value tag. */
    public static final String TAG_VALUE              = "Value";
    /** The file preset reference tag. */
    public static final String TAG_FILE_PRESET_REF    = "FilePresetRef";
    /** Synonym for the file preset reference tag. */
    public static final String TAG_FILE_PRESET_REF2   = "AbletonDefaultPresetRef";

    /** The Player tag. */
    public static final String TAG_PLAYER             = "Player";
    /** The Multi-Sample Map tag. */
    public static final String TAG_MULTI_SAMPLE_MAP   = "MultiSampleMap";
    /** The Sample Parts tag. */
    public static final String TAG_SAMPLE_PARTS       = "SampleParts";
    /** The Multi-Sample Parts tag. */
    public static final String TAG_MULTI_SAMPLE_PART  = "MultiSamplePart";

    /** The name of the sample zone. */
    public static final String TAG_NAME               = "Name";
    /** The name of the sample reference tag. */
    public static final String TAG_SAMPLE_REF         = "SampleRef";
    /** The name of the file reference tag. */
    public static final String TAG_FILE_REF           = "FileRef";
    /** The name of the relative path tag. */
    public static final String TAG_RELATIVE_PATH      = "RelativePath";
    /** The name of the relative path type tag. */
    public static final String TAG_RELATIVE_PATH_TYPE = "RelativePathType";
    /** The name of the absolute path tag. */
    public static final String TAG_PATH               = "Path";

    /** The key-range tag. */
    public static final String TAG_KEY_RANGE          = "KeyRange";
    /** The velocity-range tag. */
    public static final String TAG_VELOCITY_RANGE     = "VelocityRange";
    /** The minimum tag. */
    public static final String TAG_MINIMUM            = "Min";
    /** The maximum tag. */
    public static final String TAG_MAXIMUM            = "Max";
    /** The cross-fade minimum tag. */
    public static final String TAG_CROSSFADE_MINIMUM  = "CrossfadeMin";
    /** The cross-fade maximum tag. */
    public static final String TAG_CROSSFADE_MAXIMUM  = "CrossfadeMax";
    /** The root key tag. */
    public static final String TAG_ROOT_KEY           = "RootKey";
    /** The de-tune tag. */
    public static final String TAG_DETUNE             = "Detune";
    /** The tune-scale tag. */
    public static final String TAG_TUNE_SCALE         = "TuneScale";
    /** The volume tag. */
    public static final String TAG_VOLUME             = "Volume";
    /** The panorama tag. */
    public static final String TAG_PANORAMA           = "Panorama";
    /** The Sample Start tag. */
    public static final String TAG_SAMPLE_START       = "SampleStart";
    /** The Sample End tag. */
    public static final String TAG_SAMPLE_END         = "SampleEnd";
    /** The Sustain Loop tag. */
    public static final String TAG_SUSTAIN_LOOP       = "SustainLoop";
    /** The Sustain Loop Start tag. */
    public static final String TAG_LOOP_START         = "Start";
    /** The Sustain Loop End tag. */
    public static final String TAG_LOOP_END           = "End";
    /** The Sustain Loop Crossfade tag. */
    public static final String TAG_LOOP_CROSSFADE     = "Crossfade";
    /** The Manual tag. */
    public static final String TAG_MANUAL             = "Manual";

    /** The reverse tag. */
    public static final String TAG_REVERSE            = "Reverse";

    /** The creator attribute. */
    public static final String ATTR_CREATOR           = "Creator";
    /** The value attribute. */
    public static final String ATTR_VALUE             = "Value";


    /**
     * Private constructor for utility class.
     */
    private AbletonTag ()
    {
        // Intentionally empty
    }
}
