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
    public static final String ROOT              = "Ableton";

    /** The Simpler device preset tag. */
    public static final String DEVICE_SIMPLER    = "OriginalSimpler";
    /** The Sampler device preset tag. */
    public static final String DEVICE_SAMPLER    = "MultiSampler";
    /** The Rack device preset tag. */
    public static final String DEVICE_RACK       = "GroupDevicePreset";

    /** The user defined name of the preset. */
    public static final String USER_NAME         = "UserName";
    /** The annotation tag. */
    public static final String ANNOTATION        = "Annotation";

    /** The Player tag. */
    public static final String PLAYER            = "Player";
    /** The Multi-Sample Map tag. */
    public static final String MULTI_SAMPLE_MAP  = "MultiSampleMap";
    /** The Sample Parts tag. */
    public static final String SAMPLE_PARTS      = "SampleParts";
    /** The Multi-Sample Parts tag. */
    public static final String MULTI_SAMPLE_PART = "MultiSamplePart";

    /** The name of the sample zone. */
    public static final String NAME              = "Name";
    /** The name of the sample reference tag. */
    public static final String SAMPLE_REF        = "SampleRef";
    /** The name of the file reference tag. */
    public static final String FILE_REF          = "FileRef";
    /** The name of the relative path tag. */
    public static final String RELATIVE_PATH     = "RelativePath";

    /** The creator attribute. */
    public static final String ATTR_CREATOR      = "Creator";
    /** The value attribute. */
    public static final String ATTR_VALUE        = "Value";


    /**
     * Private constructor for utility class.
     */
    private AbletonTag ()
    {
        // Intentionally empty
    }
}
