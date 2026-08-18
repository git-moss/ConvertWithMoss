// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.isla.s2400;

/**
 * Constants for the ISLA Instruments S2400 kit format (file ending <i>.kit</i>).
 *
 * A kit file is a flat stream of self-describing records. Each record starts with a one byte type
 * tag followed by a 16-bit little-endian identifier. The remaining payload depends on the type:
 * <ul>
 * <li>{@link #REC_U32} - a 32-bit little-endian unsigned integer</li>
 * <li>{@link #REC_I32} - a 32-bit little-endian signed integer</li>
 * <li>{@link #REC_BLOB} - a 32-bit little-endian length followed by that many bytes (a string
 * including its terminating zero byte, or a 16-bit little-endian value for the modulation
 * amounts)</li>
 * </ul>
 * The stream starts with a version record, a track count and then one block per assigned track.
 * Each track block ends with nine slice slots ({@link #FIELD_SLICE_INDEX} 0 to 8) whereby slot
 * {@link #MAIN_SLICE_INDEX} carries the performance parameters (level, filter, pitch, envelopes)
 * and the slots 0 to 7 are the eight multi-slices.
 *
 * The format was reverse-engineered from the official ISLA "S2400 Kit Builder" web application,
 * which is the only tool that writes kit files - the device firmware itself is encrypted.
 *
 * @author Jürgen Moßgraber
 */
public final class S2400Constants
{
    /** Record type: 32-bit little-endian unsigned integer payload. */
    public static final int    REC_U32                = 1;
    /** Record type: 32-bit little-endian signed integer payload. */
    public static final int    REC_I32                = 2;
    /** Record type: 32-bit little-endian length prefixed byte blob payload. */
    public static final int    REC_BLOB               = 3;

    // Header fields
    /** Format version record, always holds {@link #VERSION_VALUE}. */
    public static final int    FIELD_VERSION          = 0;
    /** The number of assigned tracks in the file. */
    public static final int    FIELD_TRACK_COUNT      = 1;
    /** Second header marker, always holds the value 1. */
    public static final int    FIELD_HEADER_MARKER    = 58;

    // Per-track fields
    /** The zero-based track (pad) index, starts a new track block. */
    public static final int    FIELD_TRACK_INDEX      = 2;
    /** The pad color, encoded as 0x00BBGGRR. */
    public static final int    FIELD_COLOR            = 3;
    /** The (first / left) audio output. */
    public static final int    FIELD_OUTPUT_CHANNEL   = 4;
    /** The second (right) audio output for stereo tracks. */
    public static final int    FIELD_OUTPUT_CHANNEL_2 = 5;
    /** The playback bit-depth reduction. */
    public static final int    FIELD_BIT_REDUCTION    = 6;
    /** The resampler / audio engine setting. */
    public static final int    FIELD_RESAMPLER        = 7;
    /** The stereo mix-down mode, see {@link #MIXDOWN_MONO_LR} etc. */
    public static final int    FIELD_MIXDOWN          = 8;
    /** The track name, which is the WAV file name without its extension (a string blob). */
    public static final int    FIELD_TRACK_NAME       = 10;
    /** The slice slot index, 0 to 8 whereby 8 is the main performance slot. */
    public static final int    FIELD_SLICE_INDEX      = 11;
    /** The slot level, 0 to 255 (255 is unity gain). */
    public static final int    FIELD_LEVEL            = 12;
    /** The classic two-stage envelope decay, 0 to 31 (31 disables the envelope). */
    public static final int    FIELD_CLASSIC_DECAY    = 13;
    /** The filter resonance, 0 to 255. */
    public static final int    FIELD_FILTER_RESONANCE = 15;
    /** The slice start position in sample frames. */
    public static final int    FIELD_SLICE_START      = 17;
    /** The slice end position in sample frames. */
    public static final int    FIELD_SLICE_END        = 18;
    /** The loop start position in sample frames. */
    public static final int    FIELD_LOOP_START       = 19;
    /** The filter type, see {@link #FILTER_LOW_PASS} etc. */
    public static final int    FIELD_FILTER_MODE      = 35;
    /** The volume envelope style, see {@link #ENVELOPE_CLASSIC} / {@link #ENVELOPE_HIFI}. */
    public static final int    FIELD_ENVELOPE_STYLE   = 37;
    /** The envelope to pitch modulation amount (a 16-bit value blob). */
    public static final int    FIELD_ENV_PITCH_AMOUNT = 40;
    /** The envelope to filter modulation amount (a 16-bit value blob). */
    public static final int    FIELD_ENV_FILTER_AMOUNT = 41;
    /** The HiFi envelope index, 0 (volume) or 1. */
    public static final int    FIELD_ENV_INDEX        = 42;
    /** The HiFi envelope attack time, 0 to 1023. */
    public static final int    FIELD_ENV_ATTACK       = 43;
    /** The HiFi envelope attack hold time, 0 to 1023. */
    public static final int    FIELD_ENV_ATTACK_HOLD  = 44;
    /** The HiFi envelope decay time, 0 to 1023. */
    public static final int    FIELD_ENV_DECAY        = 45;
    /** The HiFi envelope sustain level, 0 to 1023. */
    public static final int    FIELD_ENV_SUSTAIN      = 46;
    /** The HiFi envelope sustain hold time, 0 to 1023. */
    public static final int    FIELD_ENV_SUSTAIN_HOLD = 47;
    /** The HiFi envelope release time, 0 to 1023. */
    public static final int    FIELD_ENV_RELEASE      = 48;
    /** The loop end position in sample frames. */
    public static final int    FIELD_LOOP_END         = 51;
    /** The gate mode flag (0 = one-shot, 1 = gated). */
    public static final int    FIELD_GATE_MODE        = 52;
    /** The filter cutoff frequency in Hertz (20 to 20000). */
    public static final int    FIELD_FILTER_CUTOFF    = 54;
    /** The stop-on-mute flag (0 or 1). */
    public static final int    FIELD_STOP_ON_MUTE     = 56;
    /** The choke group, 0 means no choke group. */
    public static final int    FIELD_CHOKE_GROUP      = 59;
    /** The base track gain in dB (a signed value). */
    public static final int    FIELD_TRACK_GAIN_DB    = 60;
    /** The fine pitch offset in cents (a signed value). */
    public static final int    FIELD_PITCH_FINE       = 61;
    /** The trigger group, 0 means no trigger group. */
    public static final int    FIELD_TRIGGER_GROUP    = 64;

    // Fields which the reference application always writes with a fixed value
    /** Reserved per-track field, written as a signed zero. */
    public static final int    FIELD_RESERVED_53      = 53;
    /** Reserved per-track field, always the value 2. */
    public static final int    FIELD_RESERVED_36      = 36;
    /** Reserved per-track field, always zero. */
    public static final int    FIELD_RESERVED_49      = 49;
    /** Reserved per-track field, always zero. */
    public static final int    FIELD_RESERVED_57      = 57;
    /** Reserved per-track field, always zero. */
    public static final int    FIELD_RESERVED_55      = 55;
    /** Reserved per-slot field, always zero. */
    public static final int    FIELD_RESERVED_34      = 34;
    /** Reserved per-slot field, always zero. */
    public static final int    FIELD_RESERVED_50      = 50;

    /** The value of the format version record: 0x00020002. */
    public static final int    VERSION_VALUE          = 0x00020002;
    /** The slice slot index which carries the main performance parameters. */
    public static final int    MAIN_SLICE_INDEX       = 8;
    /** The number of slice slots written per track (0 to 8). */
    public static final int    SLICE_SLOT_COUNT       = 9;
    /** The number of HiFi envelopes per slot. */
    public static final int    HIFI_ENVELOPE_COUNT    = 2;
    /** The maximum number of tracks (4 banks of 8 pads). */
    public static final int    MAX_TRACKS             = 32;

    /** The full range value used for envelope times, levels and normalized cutoff. */
    public static final int    RANGE_10_BIT           = 1023;
    /** The full range value used for levels and resonance. */
    public static final int    RANGE_8_BIT            = 255;
    /** The maximum classic envelope decay value (disables the envelope). */
    public static final int    CLASSIC_DECAY_MAX      = 31;

    /** The lowest filter cutoff frequency in Hertz. */
    public static final int    CUTOFF_MIN_HERTZ       = 20;
    /** The highest filter cutoff frequency in Hertz. */
    public static final int    CUTOFF_MAX_HERTZ       = 20000;

    /** The smallest resonance value the filter accepts. */
    public static final double RESONANCE_MIN          = 0.1;
    /** The largest resonance value the filter accepts. */
    public static final double RESONANCE_MAX          = 20.0;

    /** The MIDI note the first pad (A1) is mapped to, matching the default sample track MIDI map. */
    public static final int    DEFAULT_ROOT_NOTE      = 36;

    // Mix-down modes
    /** Play the left channel only. */
    public static final int    MIXDOWN_MONO_L         = 0;
    /** Play the right channel only. */
    public static final int    MIXDOWN_MONO_R         = 1;
    /** Mix the left and right channels to mono. */
    public static final int    MIXDOWN_MONO_LR        = 2;
    /** Play in stereo. */
    public static final int    MIXDOWN_STEREO         = 3;

    // Filter types
    /** Low-pass filter. */
    public static final int    FILTER_LOW_PASS        = 0;
    /** Band-pass filter. */
    public static final int    FILTER_BAND_PASS       = 1;
    /** High-pass filter. */
    public static final int    FILTER_HIGH_PASS       = 2;

    // Volume envelope styles
    /** Classic two-stage sustain and release envelope. */
    public static final int    ENVELOPE_CLASSIC       = 0;
    /** HiFi six-stage envelope. */
    public static final int    ENVELOPE_HIFI          = 1;

    /** The default resampler / audio engine value written by the reference application. */
    public static final int    DEFAULT_RESAMPLER      = 1;
    /** The default pad color (white), encoded as 0x00BBGGRR. */
    public static final int    DEFAULT_COLOR          = 0x00FFFFFF;

    /** The file ending of a kit file. */
    public static final String ENDING_KIT             = ".kit";


    /**
     * Constructor.
     */
    private S2400Constants ()
    {
        // Intentionally empty
    }
}
