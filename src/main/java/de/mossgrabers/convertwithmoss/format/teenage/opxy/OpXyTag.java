// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.teenage.opxy;

/**
 * Constants of the Teenage Engineering OP-XY preset format. A preset is a folder with the ending
 * <i>.preset</i> which contains the description file <i>patch.json</i> and all samples as WAV
 * files.
 *
 * @author Jürgen Moßgraber
 */
public class OpXyTag
{
    /** The name of the description file of a preset. */
    public static final String PATCH_FILE            = "patch.json";
    /** The ending of a preset folder. */
    public static final String PRESET_ENDING         = ".preset";

    /** The type of a multi-sample preset. */
    public static final String TYPE_MULTISAMPLER     = "multisampler";
    /** The platform of the device. */
    public static final String PLATFORM              = "OP-XY";

    /** The type attribute. */
    public static final String TAG_TYPE              = "type";
    /** The platform attribute. */
    public static final String TAG_PLATFORM          = "platform";
    /** The version attribute. */
    public static final String TAG_VERSION           = "version";
    /** The octave attribute. */
    public static final String TAG_OCTAVE            = "octave";
    /** The regions which map the samples. */
    public static final String TAG_REGIONS           = "regions";

    /** The engine section. */
    public static final String TAG_ENGINE            = "engine";
    /** The pitch bend range of the engine. */
    public static final String TAG_BEND_RANGE        = "bendrange";
    /** The play mode of the engine. */
    public static final String TAG_PLAYMODE          = "playmode";
    /** The transpose of the engine. */
    public static final String TAG_TRANSPOSE         = "transpose";
    /** The volume of the engine. */
    public static final String TAG_VOLUME            = "volume";

    /** The envelope section. */
    public static final String TAG_ENVELOPE          = "envelope";
    /** The amplitude envelope. */
    public static final String TAG_AMP               = "amp";
    /** The filter envelope. */
    public static final String TAG_FILTER            = "filter";
    /** The attack of an envelope. */
    public static final String TAG_ATTACK            = "attack";
    /** The decay of an envelope. */
    public static final String TAG_DECAY             = "decay";
    /** The sustain of an envelope. */
    public static final String TAG_SUSTAIN           = "sustain";
    /** The release of an envelope. */
    public static final String TAG_RELEASE           = "release";

    /** The sample file name of a region. */
    public static final String TAG_SAMPLE            = "sample";
    /** The lowest key of a region. */
    public static final String TAG_LOW_KEY           = "lokey";
    /** The highest key of a region. */
    public static final String TAG_HIGH_KEY          = "hikey";
    /** The root key of a region. */
    public static final String TAG_KEY_CENTER        = "pitch.keycenter";
    /** The number of frames of the sample of a region. */
    public static final String TAG_FRAME_COUNT       = "framecount";
    /** The play start of a region. */
    public static final String TAG_SAMPLE_START      = "sample.start";
    /** The play end of a region. */
    public static final String TAG_SAMPLE_END        = "sample.end";
    /** Is the loop enabled? */
    public static final String TAG_LOOP_ENABLED      = "loop.enabled";
    /** Does the loop run until the key is released? */
    public static final String TAG_LOOP_ON_RELEASE   = "loop.onrelease";
    /** The start of the loop. */
    public static final String TAG_LOOP_START        = "loop.start";
    /** The end of the loop. */
    public static final String TAG_LOOP_END          = "loop.end";
    /** The cross-fade of the loop. */
    public static final String TAG_LOOP_CROSSFADE    = "loop.crossfade";
    /** The gain of a region. */
    public static final String TAG_GAIN              = "gain";
    /** The tuning of a region in semitones. */
    public static final String TAG_TUNE              = "tune";
    /** Is the sample of the region played backwards? */
    public static final String TAG_REVERSE           = "reverse";

    /** The play mode which plays several notes at once. */
    public static final String PLAYMODE_POLY         = "poly";
    /** The play mode which plays one note at a time. */
    public static final String PLAYMODE_MONO         = "mono";
    /** The play mode which plays one note at a time without re-triggering the envelope. */
    public static final String PLAYMODE_LEGATO       = "legato";

    /** The maximum value of the normalized parameters of the device. */
    public static final int    MAX_VALUE             = 32767;
    /** The value of a bipolar parameter at its center. */
    public static final int    CENTER_VALUE          = 16384;

    /**
     * The longest time of an envelope in seconds. The envelope parameters are stored normalized to
     * {@link #MAX_VALUE} and mapped with a square taper, which puts the default decay of a new
     * preset (20295) at about 3.8 seconds.
     */
    public static final double ENVELOPE_MAX_TIME     = 10.0;
    /** The exponent of the envelope taper. */
    public static final double ENVELOPE_TIME_EXPONENT = 2.0;
    /** The maximum pitch bend range of the device in semitones. */
    public static final int    MAX_BEND_RANGE        = 24;


    /**
     * Private constructor for utility class.
     */
    private OpXyTag ()
    {
        // Intentionally empty
    }


    /**
     * Convert a normalized device value into a factor in the range of [0..1].
     *
     * @param value The value to convert
     * @return The factor
     */
    public static double toFactor (final int value)
    {
        return Math.clamp (value / (double) MAX_VALUE, 0, 1);
    }


    /**
     * Convert a factor in the range of [0..1] into a normalized device value.
     *
     * @param factor The factor to convert
     * @return The value
     */
    public static int fromFactor (final double factor)
    {
        return (int) Math.round (Math.clamp (factor, 0, 1) * MAX_VALUE);
    }


    /**
     * Convert a normalized envelope value into a time in seconds.
     *
     * @param value The value to convert
     * @return The time in seconds
     */
    public static double toEnvelopeTime (final int value)
    {
        return ENVELOPE_MAX_TIME * Math.pow (toFactor (value), ENVELOPE_TIME_EXPONENT);
    }


    /**
     * Convert a time in seconds into a normalized envelope value.
     *
     * @param time The time in seconds
     * @return The value
     */
    public static int fromEnvelopeTime (final double time)
    {
        if (time < 0)
            return -1;
        return fromFactor (Math.pow (time / ENVELOPE_MAX_TIME, 1.0 / ENVELOPE_TIME_EXPONENT));
    }
}
