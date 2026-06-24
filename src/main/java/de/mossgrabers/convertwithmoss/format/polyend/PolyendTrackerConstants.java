// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.polyend;

/**
 * Binary layout constants of the Polyend Tracker instrument format (file ending <i>.pti</i>). The
 * file is a fixed size header (16 bytes) followed by 372 bytes of instrument parameters, the raw
 * 16-bit / 44.1kHz PCM audio data (mono or stereo, stored non-interleaved) and a trailing 4 byte
 * checksum. All multi-byte values are little-endian.
 *
 * @author Jürgen Moßgraber
 */
public final class PolyendTrackerConstants
{
    /** The file identifier of an instrument file. */
    public static final String FILE_ID                = "TI";
    /** The file type of an instrument. */
    public static final int    TYPE_INSTRUMENT        = 1;

    /** The size of the header. */
    public static final int    HEADER_SIZE            = 16;
    /** The value of the size field (the number of bytes of the parameter block). */
    public static final int    PARAMETER_BLOCK_SIZE   = 372;
    /** The offset at which the raw audio data starts (header + parameter block). */
    public static final int    AUDIO_START            = HEADER_SIZE + PARAMETER_BLOCK_SIZE;
    /** The number of bytes of the trailing checksum. */
    public static final int    CRC_SIZE               = 4;

    /** Sample start/end and loop points as well as slices are stored as a value in the range of 0 to
     * this value, proportional to the length of the sample. */
    public static final int    NORMALIZED_MAX         = 65535;
    /** The sample rate of all instrument samples is fixed to 44100 Hz. */
    public static final int    SAMPLE_RATE            = 44100;
    /** The bit resolution of all instrument samples is fixed to 16 bit. */
    public static final int    BIT_RESOLUTION         = 16;

    /** The number of slice positions stored in a file (only the first {@code numSlices} are used). */
    public static final int    SLICE_COUNT            = 48;
    /** The number of envelopes (one for each automation target). */
    public static final int    ENVELOPE_COUNT         = 6;
    /** The number of LFOs (one for each automation target). */
    public static final int    LFO_COUNT              = 6;

    // Offsets into the parameter block (absolute file offsets)
    /** Offset of the 'is active' flag. */
    public static final int    OFF_IS_ACTIVE          = 16;
    /** Offset of the sample type (0 = wave file, 1 = wavetable). */
    public static final int    OFF_SAMPLE_TYPE        = 20;
    /** Offset of the sample name (32 bytes, null terminated ASCII). */
    public static final int    OFF_SAMPLE_NAME        = 21;
    /** The maximum number of bytes of the sample name. */
    public static final int    SAMPLE_NAME_LENGTH     = 32;
    /** Offset of the sample length (number of frames, unsigned 32-bit). */
    public static final int    OFF_SAMPLE_LENGTH      = 60;
    /** Offset of the wavetable window size (unsigned 16-bit). */
    public static final int    OFF_WT_WINDOW_SIZE     = 64;
    /** Offset of the wavetable window count (unsigned 32-bit). */
    public static final int    OFF_WT_WINDOW_COUNT    = 68;
    /** Offset of the play mode. */
    public static final int    OFF_PLAYMODE           = 76;
    /** Offset of the playback start point (normalized). */
    public static final int    OFF_START              = 78;
    /** Offset of loop point 1 - the loop start (normalized). */
    public static final int    OFF_LOOP1              = 80;
    /** Offset of loop point 2 - the loop end (normalized). */
    public static final int    OFF_LOOP2              = 82;
    /** Offset of the playback end point (normalized). */
    public static final int    OFF_END                = 84;
    /** Offset of the first envelope. */
    public static final int    OFF_ENVELOPES          = 92;
    /** The number of bytes of one envelope. */
    public static final int    ENVELOPE_SIZE          = 20;
    /** Offset of the first LFO. */
    public static final int    OFF_LFOS               = 212;
    /** The number of bytes of one LFO. */
    public static final int    LFO_SIZE               = 8;
    /** Offset of the filter cutoff (32-bit float in the range of 0 to 1). */
    public static final int    OFF_CUTOFF             = 260;
    /** Offset of the filter resonance (32-bit float in the range of 0 to 4.3). */
    public static final int    OFF_RESONANCE          = 264;
    /** Offset of the filter type. */
    public static final int    OFF_FILTER_TYPE        = 268;
    /** Offset of the filter enabled flag. */
    public static final int    OFF_FILTER_ENABLED     = 269;
    /** Offset of the coarse tune (signed 8-bit, semitones). */
    public static final int    OFF_TUNE               = 270;
    /** Offset of the fine tune (signed 8-bit, cents). */
    public static final int    OFF_FINETUNE           = 271;
    /** Offset of the volume (unsigned 8-bit, 0 to 100, 50 = unity gain). */
    public static final int    OFF_VOLUME             = 272;
    /** Offset of the panning (signed 16-bit, 0 to 100, 50 = center). */
    public static final int    OFF_PANNING            = 276;
    /** Offset of the delay send (unsigned 8-bit, 0 to 100). */
    public static final int    OFF_DELAY_SEND         = 278;
    /** Offset of the first slice position (normalized, unsigned 16-bit). */
    public static final int    OFF_SLICES             = 280;
    /** Offset of the number of used slices (unsigned 8-bit). */
    public static final int    OFF_NUM_SLICES         = 376;
    /** Offset of the selected slice (unsigned 8-bit). */
    public static final int    OFF_SELECTED_SLICE     = 377;
    /** Offset of the granular grain length (unsigned 16-bit). */
    public static final int    OFF_GRANULAR_LENGTH    = 378;
    /** Offset of the granular position (unsigned 16-bit). */
    public static final int    OFF_GRANULAR_POSITION  = 380;
    /** Offset of the granular shape. */
    public static final int    OFF_GRANULAR_SHAPE     = 382;
    /** Offset of the granular loop type. */
    public static final int    OFF_GRANULAR_TYPE      = 383;
    /** Offset of the reverb send (unsigned 8-bit, 0 to 100). */
    public static final int    OFF_REVERB_SEND        = 384;
    /** Offset of the overdrive (unsigned 8-bit, 0 to 100). */
    public static final int    OFF_OVERDRIVE          = 385;
    /** Offset of the bit depth (unsigned 8-bit, 4 to 16). */
    public static final int    OFF_BITDEPTH           = 386;

    // Offsets of the fields inside one envelope (relative to the start of the envelope)
    /** Relative offset of the envelope amount (32-bit float). */
    public static final int    ENV_OFF_AMOUNT         = 0;
    /** Relative offset of the envelope delay (unsigned 16-bit, milliseconds). */
    public static final int    ENV_OFF_DELAY          = 4;
    /** Relative offset of the envelope attack (unsigned 16-bit, milliseconds). */
    public static final int    ENV_OFF_ATTACK         = 6;
    /** Relative offset of the envelope hold (unsigned 16-bit, milliseconds). */
    public static final int    ENV_OFF_HOLD           = 8;
    /** Relative offset of the envelope decay (unsigned 16-bit, milliseconds). */
    public static final int    ENV_OFF_DECAY          = 10;
    /** Relative offset of the envelope sustain (32-bit float, 0 to 1). */
    public static final int    ENV_OFF_SUSTAIN        = 12;
    /** Relative offset of the envelope release (unsigned 16-bit, milliseconds). */
    public static final int    ENV_OFF_RELEASE        = 16;
    /** Relative offset of the LFO flag (1 = the automation uses the LFO instead of the envelope). */
    public static final int    ENV_OFF_LFO_FLAG       = 18;
    /** Relative offset of the envelope enabled flag. */
    public static final int    ENV_OFF_ENABLED        = 19;

    // Automation / envelope indices
    /** The volume (amplitude) automation. */
    public static final int    ENV_VOLUME             = 0;
    /** The panning automation. */
    public static final int    ENV_PANNING            = 1;
    /** The filter cutoff automation. */
    public static final int    ENV_CUTOFF             = 2;
    /** The wavetable position automation. */
    public static final int    ENV_WAVETABLE          = 3;
    /** The granular position automation. */
    public static final int    ENV_GRANULAR           = 4;
    /** The fine tune (pitch) automation. */
    public static final int    ENV_FINETUNE           = 5;

    // Play modes
    /** Play the sample once. */
    public static final int    PLAYMODE_ONESHOT       = 0;
    /** Loop the sample forward. */
    public static final int    PLAYMODE_FORWARD_LOOP  = 1;
    /** Loop the sample backward. */
    public static final int    PLAYMODE_BACKWARD_LOOP = 2;
    /** Loop the sample alternating forward and backward. */
    public static final int    PLAYMODE_PINGPONG_LOOP = 3;
    /** Play a slice of the sample. */
    public static final int    PLAYMODE_SLICE         = 4;
    /** Play a slice of the sample, chromatically mapped. */
    public static final int    PLAYMODE_BEAT_SLICE    = 5;
    /** Play the sample as a wavetable. */
    public static final int    PLAYMODE_WAVETABLE     = 6;
    /** Play the sample granular. */
    public static final int    PLAYMODE_GRANULAR      = 7;

    // Filter types
    /** A low-pass filter. */
    public static final int    FILTER_LOWPASS         = 0;
    /** A high-pass filter. */
    public static final int    FILTER_HIGHPASS        = 1;
    /** A band-pass filter. */
    public static final int    FILTER_BANDPASS        = 2;

    // Sample types
    /** A normal wave file. */
    public static final int    SAMPLE_WAVE            = 0;
    /** A wavetable. */
    public static final int    SAMPLE_WAVETABLE       = 1;

    /** The firmware version bytes written into created files. */
    public static final int [] WRITE_FW_VERSION       =
    {
        1,
        9,
        1,
        1
    };
    /** The file structure version bytes written into created files (matches the value found in all
     * factory files). */
    public static final int [] WRITE_STRUCTURE_VERSION =
    {
        9,
        9,
        9,
        9
    };

    /** The default root note used for an imported sample (middle C). */
    public static final int    DEFAULT_ROOT_NOTE      = 60;


    /**
     * Constructor.
     */
    private PolyendTrackerConstants ()
    {
        // Helper class
    }
}
