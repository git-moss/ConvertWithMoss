// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator3;

import java.nio.charset.StandardCharsets;

import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;


/**
 * Constants, structure offsets and the parameter conversion tables of the E-mu EIII bank format.
 * All values are stored little-endian. The structures were reverse-engineered by the emu3bm
 * project and verified against the E-mu EIIIX and ESI library CD-ROMs, see
 * documentation/design/EIII_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator3Constants
{
    /** The number of characters of all names in a bank; they are padded with spaces. */
    public static final int    NAME_LENGTH                     = 16;
    /** The size of the bank header. */
    public static final int    BANK_HEADER_SIZE                = 0x6C;
    /** The size of a preset without its note zones and zones. */
    public static final int    PRESET_SIZE                     = 142;
    /** The size of one note zone entry of a preset. */
    public static final int    NOTE_ZONE_SIZE                  = 4;
    /** The size of one zone of a preset. */
    public static final int    ZONE_SIZE                       = 48;
    /** The size of the header of a sample, which is followed by its 16 bit PCM data. */
    public static final int    SAMPLE_HEADER_SIZE              = 92;
    /** The number of keys of the E-mu keyboard. */
    public static final int    NUM_KEYS                        = 88;
    /** The MIDI note of E-mu key 0, which the samplers display as A-1. */
    public static final int    KEY_OFFSET                      = 21;
    /** All entries of the sample address table are stored with this offset added. */
    public static final int    SAMPLE_ADDRESS_OFFSET           = 0x400000;
    /** The size of one block; the bank header counts its size in these. */
    public static final int    BLOCK_SIZE                      = 512;
    /** Marks an unused note zone, layer or key mapping. */
    public static final int    UNUSED                          = 0xFF;
    /** The highest sample rate the samplers play back. */
    public static final int    MAX_SAMPLE_RATE                 = 44100;
    /** The size of an empty bank, which is the size of all headers and address tables. */
    public static final int    EMPTY_BANK_SIZE                 = 0x2B73;

    // The fields of the bank header
    /** The offset of the name of the bank. */
    public static final int    BANK_NAME                       = 0x10;
    /** The offset of the number of objects in the bank. */
    public static final int    BANK_OBJECTS                    = 0x20;
    /** The offset of the position behind the last preset. */
    public static final int    BANK_NEXT_PRESET                = 0x30;
    /** The offset of the position behind the last sample. */
    public static final int    BANK_NEXT_SAMPLE                = 0x34;
    /** The offset of the number of blocks which the presets occupy. */
    public static final int    BANK_PRESET_BLOCKS              = 0x3C;
    /** The offset of the number of blocks which the samples occupy. */
    public static final int    BANK_SAMPLE_BLOCKS              = 0x40;
    /** The offset of the number of blocks of the whole bank. */
    public static final int    BANK_TOTAL_BLOCKS               = 0x48;
    /** The offset of the second copy of the name of the bank. */
    public static final int    BANK_NAME_COPY                  = 0x4C;
    /** The offset of the index of the preset which is selected when the bank is loaded. */
    public static final int    BANK_SELECTED_PRESET            = 0x5C;

    // The fields of a preset
    /** The offset of the pitch bend range in semitones. */
    public static final int    PRESET_PITCH_BEND_RANGE         = 0x2C;
    /** The offset of the lowest velocity which triggers the primary layer. */
    public static final int    PRESET_VELOCITY_PRIMARY_LOW     = 0x2D;
    /** The offset of the highest velocity which triggers the primary layer. */
    public static final int    PRESET_VELOCITY_PRIMARY_HIGH    = 0x2E;
    /** The offset of the lowest velocity which triggers the secondary layer. */
    public static final int    PRESET_VELOCITY_SECONDARY_LOW   = 0x2F;
    /** The offset of the highest velocity which triggers the secondary layer. */
    public static final int    PRESET_VELOCITY_SECONDARY_HIGH  = 0x30;
    /** The offset of the 1-based number of the preset which is layered on top of this one. */
    public static final int    PRESET_LINK                     = 0x31;
    /** The offset of the number of note zones of the preset. */
    public static final int    PRESET_NUM_NOTE_ZONES           = 0x35;
    /** The offset of the table which maps each of the 88 keys to one of the note zones. */
    public static final int    PRESET_KEY_MAPPINGS             = 0x36;

    // The fields of a note zone
    /** The offset of the index of the zone of the primary layer. */
    public static final int    NOTE_ZONE_PRIMARY               = 2;
    /** The offset of the index of the zone of the secondary layer. */
    public static final int    NOTE_ZONE_SECONDARY             = 3;

    // The fields of a zone
    /** The offset of the key at which the sample plays back at its original pitch. */
    public static final int    ZONE_ORIGINAL_KEY               = 0;
    /** The offset of the 1-based index of the sample of the zone, 2 bytes. */
    public static final int    ZONE_SAMPLE_INDEX               = 1;
    /**
     * The bits of the sample index of a zone which hold the index. The ESI samplers use the two
     * upper bits as flags of an unknown meaning: comparing the EIIIX and the ESI-4000 version of
     * the same library bank shows 1279 zones whose index only differs by exactly 0x4000 or 0x8000
     * while all other parameters are identical.
     */
    public static final int    ZONE_SAMPLE_INDEX_MASK          = 0x3FFF;
    /** The offset of a parameter which the ESI samplers keep at 0 and the EIIIX at 0x1F. */
    public static final int    ZONE_PARAMETER_A                = 3;
    /** The offset of the amplifier envelope. */
    public static final int    ZONE_VCA_ENVELOPE               = 4;
    /** The offset of the cutoff frequency of the filter. */
    public static final int    ZONE_VCF_CUTOFF                 = 12;
    /** The offset of the resonance of the filter; bit 7 enables its real-time control. */
    public static final int    ZONE_VCF_Q                      = 13;
    /** The offset of the amount of the filter envelope. */
    public static final int    ZONE_VCF_ENVELOPE_AMOUNT        = 14;
    /** The offset of the filter envelope. */
    public static final int    ZONE_VCF_ENVELOPE               = 15;
    /** The offset of the auxiliary envelope. */
    public static final int    ZONE_AUX_ENVELOPE               = 20;
    /** The offset of the amount of the auxiliary envelope. */
    public static final int    ZONE_AUX_ENVELOPE_AMOUNT        = 25;
    /** The offset of the destination of the auxiliary envelope. */
    public static final int    ZONE_AUX_ENVELOPE_DESTINATION   = 26;
    /** The offset of the amount of velocity which is added to the amplifier level. */
    public static final int    ZONE_VELOCITY_TO_VCA_LEVEL      = 28;
    /** The offset of the amount of velocity which is added to the cutoff frequency. */
    public static final int    ZONE_VELOCITY_TO_VCF_CUTOFF     = 32;
    /** The offset of the level of the amplifier. */
    public static final int    ZONE_VCA_LEVEL                  = 40;
    /** The offset of the fine tuning of the zone in 1/64 semitones. */
    public static final int    ZONE_NOTE_TUNING                = 41;
    /** The offset of the amount the cutoff frequency follows the key. */
    public static final int    ZONE_VCF_TRACKING               = 42;
    /** The offset of the delay between the note on and the start of the zone. */
    public static final int    ZONE_NOTE_ON_DELAY              = 43;
    /** The offset of the panorama of the zone. */
    public static final int    ZONE_VCA_PAN                    = 44;
    /** The offset of the LFO rate. */
    public static final int    ZONE_LFO_RATE                   = 9;

    /** The offset of the LFO delay. */
    public static final int    ZONE_LFO_DELAY                  = 10;

    /** The offset of the LFO to pitch (vibrato) amount. */
    public static final int    ZONE_LFO_TO_PITCH               = 36;

    /** The offset of the filter type and the LFO shape. */
    public static final int    ZONE_VCF_TYPE_LFO_SHAPE         = 45;
    /** The offset of the flags which enable the real-time controls. */
    public static final int    ZONE_REALTIME_ENABLE            = 46;
    /** The offset of the flags of the zone. */
    public static final int    ZONE_FLAGS                      = 47;

    // The stages of an envelope, which is 5 bytes
    /** The offset of the attack time of an envelope. */
    public static final int    ENVELOPE_ATTACK                 = 0;
    /** The offset of the hold time of an envelope. */
    public static final int    ENVELOPE_HOLD                   = 1;
    /** The offset of the decay time of an envelope. */
    public static final int    ENVELOPE_DECAY                  = 2;
    /** The offset of the sustain level of an envelope. */
    public static final int    ENVELOPE_SUSTAIN                = 3;
    /** The offset of the release time of an envelope. */
    public static final int    ENVELOPE_RELEASE                = 4;
    /** The number of bytes of an envelope. */
    public static final int    ENVELOPE_SIZE                   = 5;

    // The fields of a sample
    /** The offset of the position of the first frame of the left channel. */
    public static final int    SAMPLE_START_LEFT               = 0x14;
    /** The offset of the position of the first frame of the right channel. */
    public static final int    SAMPLE_START_RIGHT              = 0x18;
    /** The offset of the position of the last frame of the left channel. */
    public static final int    SAMPLE_END_LEFT                 = 0x1C;
    /** The offset of the position of the last frame of the right channel. */
    public static final int    SAMPLE_END_RIGHT                = 0x20;
    /** The offset of the position of the start of the loop of the left channel. */
    public static final int    SAMPLE_LOOP_START_LEFT          = 0x24;
    /** The offset of the position of the start of the loop of the right channel. */
    public static final int    SAMPLE_LOOP_START_RIGHT         = 0x28;
    /** The offset of the position of the end of the loop of the left channel. */
    public static final int    SAMPLE_LOOP_END_LEFT            = 0x2C;
    /** The offset of the position of the end of the loop of the right channel. */
    public static final int    SAMPLE_LOOP_END_RIGHT           = 0x30;
    /** The offset of the sample rate at which the sample was recorded. */
    public static final int    SAMPLE_RATE                     = 0x34;
    /** The offset of the encoded playback rate of the sample. */
    public static final int    SAMPLE_PLAYBACK_RATE            = 0x38;
    /** The offset of the option flags of the sample. */
    public static final int    SAMPLE_OPTIONS                  = 0x3A;
    /** The offset of the position of the left channel in the sample memory. */
    public static final int    SAMPLE_DATA_OFFSET_LEFT         = 0x3C;
    /** The offset of the position of the right channel in the sample memory. */
    public static final int    SAMPLE_DATA_OFFSET_RIGHT        = 0x40;

    // The option flags of a sample
    /** The sample is looped. */
    public static final int    OPTION_LOOP                     = 0x0001;
    /** The loop continues to play during the release phase. */
    public static final int    OPTION_LOOP_IN_RELEASE          = 0x0008;
    /** The sample has a left channel. */
    public static final int    OPTION_CHANNEL_LEFT             = 0x0020;
    /** The sample has a right channel. */
    public static final int    OPTION_CHANNEL_RIGHT            = 0x0040;
    /** The sample has both channels. */
    public static final int    OPTION_STEREO                   = OPTION_CHANNEL_LEFT | OPTION_CHANNEL_RIGHT;

    // The flags of a zone
    /** The zone does not transpose its sample across the keyboard. */
    public static final int    ZONE_FLAG_NON_TRANSPOSE         = 0x02;

    /** The flag bit which enables the chorus (a second, slightly detuned voice). */
    public static final int    ZONE_FLAG_CHORUS                = 0x08;
    /** The zone ignores the loop of its sample. */
    public static final int    ZONE_FLAG_DISABLE_LOOP          = 0x20;
    /** The zone mutes the left channel of its sample. */
    public static final int    ZONE_FLAG_DISABLE_LEFT          = 0x40;
    /** The zone mutes the right channel of its sample. */
    public static final int    ZONE_FLAG_DISABLE_RIGHT         = 0x80;
    /** The flag of the Q parameter which enables its real-time control. */
    public static final int    Q_REALTIME_ENABLE               = 0x80;
    /** All real-time controls of a zone are enabled. */
    public static final int    REALTIME_ENABLE_ALL             = 0xFF;
    /** The value which the EIIIX writes into the unknown parameter A of a zone. */
    public static final int    PARAMETER_A_EMULATOR_3X         = 0x1F;

    /** The default cutoff value, which is the fully open filter. */
    public static final int    DEFAULT_CUTOFF                  = 0xEF;
    /** A low-pass at or above this frequency removes nothing which can be heard. */
    public static final double INAUDIBLE_CUTOFF_HERTZ          = 20000.0;
    /** The value of the amplifier level and the sustain which means 100%. */
    public static final int    FULL_LEVEL                      = 0x7F;
    /** The value of the panorama which means centered. */
    public static final int    CENTER_PAN                      = 0x40;
    /**
     * The value of the filter tracking which means no tracking. The parameter is a signed byte
     * which the samplers show from -2.00 to +2.00, and the operations manual says of 0.00 that
     * "the filter cutoff will not be affected by the keyboard pitch". 0x40 is +1.00 and not the
     * neutral value it was taken for.
     */
    public static final int    NO_VCF_TRACKING                 = 0;
    /** The filter tracking value which follows the keyboard at 100%. */
    public static final double FULL_VCF_TRACKING               = 0.5;

    /** The real-time controller assignments which the samplers use by default. */
    public static final byte [] DEFAULT_REALTIME_CONTROLS      =
    {
        1, 0, 0, 2, 0, 0, 0, 0, 0, 0, 1, 8
    };

    /** The value which the position behind the last preset has in an empty bank. */
    private static final int   INITIAL_NEXT_PRESET             = 0x2B27;

    /**
     * One record of the master settings of an empty bank. A bank holds two of them; their meaning
     * is unknown but every empty bank of the samplers carries exactly these bytes.
     */
    private static final int [] MASTER_SETTINGS                =
    {
        0xFF, 0xFF, 0x00, 0x00, 0x00, 0xFE, 0xFF, 0xFF, 0x28, 0x00, 0x02, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    /** The tuning table of an empty bank, which follows a 'TM' marker. */
    private static final int [] TUNING_TABLE                   =
    {
        0x17037, 0x17027, 0x17029, 0x1702B, 0x1702C, 0x1702E, 0x17030, 0x17032, 0x17033, 0x17035
    };

    /**
     * The filter types of the ESI samplers, which are indexed by the upper 5 bits of the filter
     * type parameter. The EIII and the EIIIX have a single low-pass filter instead and do not use
     * this encoding.
     */
    private static final FilterType [] ESI_FILTER_TYPES        =
    {
        FilterType.LOW_PASS,
        FilterType.LOW_PASS,
        FilterType.LOW_PASS,
        FilterType.HIGH_PASS,
        FilterType.HIGH_PASS,
        FilterType.BAND_PASS,
        FilterType.BAND_PASS,
        FilterType.BAND_REJECTION
    };

    /** The number of poles of each of the {@link #ESI_FILTER_TYPES}. */
    private static final int [] ESI_FILTER_POLES               =
    {
        2, 4, 6, 2, 4, 2, 4, 2
    };

    /** The index of the low-pass filter of the ESI samplers which the EIIIX filter maps to. */
    private static final int   ESI_FILTER_LOW_PASS_4_POLE      = 1;

    /**
     * The times in seconds of the 128 values of an envelope stage. The samplers use the same
     * table for the attack, the hold, the decay and the release of all three envelopes.
     */
    private static final double [] ENVELOPE_TIME             =
    {
        0.00, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08,
        0.09, 0.10, 0.11, 0.12, 0.13, 0.14, 0.15, 0.16, 0.17,
        0.18, 0.19, 0.20, 0.21, 0.22, 0.23, 0.25, 0.26, 0.28,
        0.29, 0.32, 0.34, 0.36, 0.38, 0.41, 0.43, 0.46, 0.49,
        0.52, 0.55, 0.58, 0.62, 0.65, 0.70, 0.74, 0.79, 0.83,
        0.88, 0.93, 0.98, 1.04, 1.10, 1.17, 1.24, 1.31, 1.39,
        1.47, 1.56, 1.65, 1.74, 1.84, 1.95, 2.06, 2.18, 2.31,
        2.44, 2.59, 2.73, 2.89, 3.06, 3.23, 3.42, 3.62, 3.82,
        4.04, 4.28, 4.52, 4.78, 5.05, 5.34, 5.64, 5.97, 6.32,
        6.67, 7.06, 7.46, 7.90, 8.35, 8.83, 9.34, 9.87, 10.45,
        11.06, 11.70, 12.38, 13.11, 13.88, 14.70, 15.56, 16.49, 17.48,
        18.53, 19.65, 20.85, 22.13, 23.50, 24.97, 26.54, 28.24, 30.06,
        32.02, 34.15, 36.44, 38.93, 41.64, 44.60, 47.84, 51.41, 55.34,
        59.70, 64.56, 70.03, 76.22, 83.28, 91.40, 100.87, 112.09, 125.65,
        142.36, 163.69
    };


    /**
     * The cutoff frequencies in Hertz of the 256 values of the filter cutoff parameter.
     */
    private static final double [] CUTOFF_FREQUENCY          =
    {
        26, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 39, 40, 41, 42, 44, 45, 47, 48, 50,
        51, 53, 55, 56, 58, 60, 62, 64, 66, 68, 70,
        72, 75, 77, 80, 82, 85, 87, 90, 93, 96, 99,
        102, 106, 109, 112, 116, 120, 124, 128, 132, 136, 140,
        145, 149, 154, 159, 164, 169, 175, 180, 186, 192, 198,
        204, 211, 217, 224, 231, 239, 246, 254, 262, 271, 279,
        288, 297, 307, 316, 327, 337, 348, 359, 370, 382, 394,
        407, 419, 433, 447, 461, 475, 491, 506, 522, 539, 556,
        574, 592, 611, 630, 650, 671, 692, 714, 737, 760, 784,
        809, 835, 861, 889, 917, 946, 976, 1007, 1039, 1072, 1106,
        1141, 1178, 1215, 1254, 1294, 1335, 1377, 1421, 1466, 1512, 1560,
        1610, 1661, 1714, 1768, 1825, 1882, 1942, 2004, 2068, 2133, 2201,
        2271, 2343, 2417, 2494, 2573, 2655, 2739, 2826, 2916, 3009, 3104,
        3203, 3304, 3409, 3518, 3629, 3744, 3863, 3986, 4112, 4243, 4378,
        4517, 4660, 4808, 4960, 5118, 5280, 5448, 5621, 5799, 5983, 6173,
        6368, 6570, 6779, 6994, 7216, 7444, 7680, 7924, 8175, 8434, 8702,
        8978, 9262, 9556, 9859, 10171, 10493, 10826, 11169, 11522, 11887, 12264,
        12652, 13053, 13466, 13892, 14332, 14785, 15253, 15736, 16233, 16747, 17276,
        17823, 18386, 18967, 19566, 20185, 20822, 21480, 22158, 22858, 23580, 24324,
        25091, 25883, 26699, 27541, 28409, 29305, 30228, 31181, 32163, 33176, 34220,
        35297, 36407, 37553, 38734, 39951, 41207, 42502, 43836, 45213, 46632, 48095,
        49604, 51160, 52763, 54417, 56121, 57879, 59691, 61559, 63484, 65469, 67515,
        69625, 71799, 74040
    };


    /**
     * The panorama in percent of the 128 values of the panorama parameter, where 0 is fully
     * left, 64 is centered and 127 is fully right.
     */
    private static final int [] PANORAMA                     =
    {
        -100, -99, -97, -96, -94, -93, -91, -90, -88, -86, -85, -83, -82, -80, -79,
        -77, -75, -74, -72, -71, -69, -68, -66, -65, -63, -61, -60, -58, -57, -55,
        -54, -52, -50, -49, -47, -46, -44, -43, -41, -40, -38, -36, -35, -33, -32,
        -30, -29, -27, -25, -24, -22, -21, -19, -18, -16, -15, -13, -11, -10, -8,
        -7, -5, -4, -2, 0, 1, 3, 4, 6, 7, 9, 11, 12, 14, 15,
        17, 19, 20, 22, 23, 25, 26, 28, 30, 31, 33, 34, 36, 38, 39,
        41, 42, 44, 46, 47, 49, 50, 52, 53, 55, 57, 58, 60, 61, 63,
        65, 66, 68, 69, 71, 73, 74, 76, 77, 79, 80, 82, 84, 85, 87,
        88, 90, 92, 93, 95, 96, 98, 100
    };

    /**
     * Private constructor since this is a utility class.
     */
    private Emulator3Constants ()
    {
        // Intentionally empty
    }


    /**
     * Read an unsigned 16 bit little-endian value.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The value
     */
    public static int getU16 (final byte [] data, final int offset)
    {
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
    }


    /**
     * Read an unsigned 32 bit little-endian value.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The value
     */
    public static long getU32 (final byte [] data, final int offset)
    {
        return data[offset] & 0xFFL | (data[offset + 1] & 0xFFL) << 8 | (data[offset + 2] & 0xFFL) << 16 | (data[offset + 3] & 0xFFL) << 24;
    }


    /**
     * Write an unsigned 16 bit little-endian value.
     *
     * @param data The data
     * @param offset The offset to write to
     * @param value The value
     */
    public static void putU16 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    /**
     * Write an unsigned 32 bit little-endian value.
     *
     * @param data The data
     * @param offset The offset to write to
     * @param value The value
     */
    public static void putU32 (final byte [] data, final int offset, final long value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
        data[offset + 2] = (byte) (value >> 16 & 0xFF);
        data[offset + 3] = (byte) (value >> 24 & 0xFF);
    }


    /**
     * Read one of the fixed size names of the format. Trailing spaces are removed.
     *
     * @param data The data
     * @param offset The offset of the name
     * @return The name
     */
    public static String decodeName (final byte [] data, final int offset)
    {
        final int length = Math.min (NAME_LENGTH, data.length - offset);
        if (length <= 0)
            return "";
        final StringBuilder sb = new StringBuilder (length);
        for (int i = 0; i < length; i++)
        {
            final int c = data[offset + i] & 0xFF;
            // The samplers display all characters outside of the printable ASCII range as spaces
            sb.append (c >= 32 && c < 127 ? (char) c : ' ');
        }
        return sb.toString ().stripTrailing ();
    }


    /**
     * Write one of the fixed size names of the format. The name is truncated to
     * {@link #NAME_LENGTH} characters and padded with spaces.
     *
     * @param data The data
     * @param offset The offset of the name
     * @param name The name to write
     */
    public static void encodeName (final byte [] data, final int offset, final String name)
    {
        final byte [] text = (name == null ? "" : name).getBytes (StandardCharsets.US_ASCII);
        for (int i = 0; i < NAME_LENGTH; i++)
        {
            final int c = i < text.length ? text[i] & 0xFF : ' ';
            data[offset + i] = (byte) (c >= 32 && c < 127 ? c : '?');
        }
    }


    /**
     * Convert the value of an envelope stage into seconds.
     *
     * @param value The value to convert
     * @return The time in seconds
     */
    public static double getEnvelopeTime (final int value)
    {
        return ENVELOPE_TIME[Math.clamp (value, 0, ENVELOPE_TIME.length - 1)];
    }


    /**
     * Convert a time in seconds into the value of an envelope stage.
     *
     * @param seconds The time in seconds
     * @return The value
     */
    public static int getEnvelopeTimeValue (final double seconds)
    {
        return findClosest (ENVELOPE_TIME, seconds);
    }


    /**
     * Convert the value of the cutoff parameter into a frequency.
     *
     * @param value The value to convert
     * @return The frequency in Hertz
     */
    public static double getCutoffFrequency (final int value)
    {
        return CUTOFF_FREQUENCY[Math.clamp (value, 0, CUTOFF_FREQUENCY.length - 1)];
    }


    /**
     * Convert a frequency into the value of the cutoff parameter.
     *
     * @param frequency The frequency in Hertz
     * @return The value
     */
    public static int getCutoffValue (final double frequency)
    {
        return findClosest (CUTOFF_FREQUENCY, frequency);
    }


    /**
     * Convert the value of the panorama parameter into the normalized range.
     *
     * @param value The value to convert
     * @return The panorama in the range of [-1..1]
     */
    public static double getPanning (final int value)
    {
        return PANORAMA[Math.clamp (value, 0, PANORAMA.length - 1)] / 100.0;
    }


    /**
     * Convert a normalized panorama into the value of the panorama parameter.
     *
     * @param panning The panorama in the range of [-1..1]
     * @return The value
     */
    public static int getPanningValue (final double panning)
    {
        final int percent = (int) Math.round (Math.clamp (panning, -1, 1) * 100);
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < PANORAMA.length; i++)
        {
            final int distance = Math.abs (PANORAMA[i] - percent);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }


    /**
     * Find the index of the entry of an ascending table which is closest to the given value.
     *
     * @param table The table to search
     * @param value The value to look for
     * @return The index of the closest entry
     */
    private static int findClosest (final double [] table, final double value)
    {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < table.length; i++)
        {
            final double distance = Math.abs (table[i] - value);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }


    /**
     * Get the type of the filter of a zone. Only the ESI samplers store a filter type; the EIII and
     * the EIIIX have a single 4-pole low-pass filter, which was confirmed by scanning the EIIIX
     * library CD-ROMs: none of their zones ever sets the three bits which select a filter type.
     *
     * @param filterTypeAndShape The value of the filter type and LFO shape parameter
     * @param bankFormat The format of the bank
     * @return The type of the filter or null if the sampler uses an effect filter which cannot be
     *         expressed by the model, e.g. a phaser, a flanger or a vocal formant filter
     */
    public static FilterType getFilterType (final int filterTypeAndShape, final Emulator3BankFormat bankFormat)
    {
        final int index = bankFormat == Emulator3BankFormat.ESI_32_V3 ? filterTypeAndShape >> 3 : ESI_FILTER_LOW_PASS_4_POLE;
        return index < ESI_FILTER_TYPES.length ? ESI_FILTER_TYPES[index] : null;
    }


    /**
     * Get the number of poles of the filter of a zone. See
     * {@link #getFilterType(int, Emulator3BankFormat)} for the encoding.
     *
     * @param filterTypeAndShape The value of the filter type and LFO shape parameter
     * @param bankFormat The format of the bank
     * @return The number of poles
     */
    public static int getFilterPoles (final int filterTypeAndShape, final Emulator3BankFormat bankFormat)
    {
        final int index = bankFormat == Emulator3BankFormat.ESI_32_V3 ? filterTypeAndShape >> 3 : ESI_FILTER_LOW_PASS_4_POLE;
        return index < ESI_FILTER_POLES.length ? ESI_FILTER_POLES[index] : 4;
    }


    /**
     * Get the value of the filter type parameter for a filter of the model.
     *
     * @param filterType The type of the filter
     * @param poles The number of poles of the filter
     * @param bankFormat The format of the bank
     * @return The value of the upper 5 bits of the filter type and LFO shape parameter
     */
    public static int getFilterTypeValue (final FilterType filterType, final int poles, final Emulator3BankFormat bankFormat)
    {
        // Only the ESI samplers select a filter, the EIII and the EIIIX always use their low-pass
        if (bankFormat != Emulator3BankFormat.ESI_32_V3)
            return 0;
        return switch (filterType)
        {
            case LOW_PASS -> poles <= 2 ? 0 : poles >= 6 ? 2 : 1;
            case HIGH_PASS -> poles >= 4 ? 4 : 3;
            case BAND_PASS -> poles >= 4 ? 6 : 5;
            case BAND_REJECTION -> 7;
        };
    }


    /**
     * Encode a sample rate into the playback rate of a sample. The samplers play back all samples
     * at 44.1 kHz and compensate a lower sample rate with this logarithmic value.
     *
     * @param sampleRate The sample rate
     * @return The encoded playback rate
     */
    public static int encodePlaybackRate (final int sampleRate)
    {
        if (sampleRate >= MAX_SAMPLE_RATE)
            return 0;
        return 0xF800 | (int) (-9799 + 1108 * Math.log (sampleRate)) & 0x7FF;
    }


    /**
     * Create the header and the address tables of an empty bank. Besides the documented fields a
     * bank carries a block of device state - the master settings of the sampler - which is
     * identical in every empty bank and is reproduced here.
     *
     * @param bankFormat The format of the bank, which must not be the compact Emulator III format
     * @param name The name of the bank
     * @return The {@link #EMPTY_BANK_SIZE} bytes of an empty bank
     */
    public static byte [] createEmptyBank (final Emulator3BankFormat bankFormat, final String name)
    {
        final byte [] data = new byte [EMPTY_BANK_SIZE];

        final byte [] identifier = bankFormat.getIdentifier ().getBytes (StandardCharsets.US_ASCII);
        System.arraycopy (identifier, 0, data, 0, identifier.length);
        encodeName (data, BANK_NAME, name);
        encodeName (data, BANK_NAME_COPY, name);

        putU32 (data, 0x24, 1);
        putU32 (data, 0x28, 1);
        putU32 (data, 0x2C, 1);
        putU32 (data, BANK_NEXT_PRESET, INITIAL_NEXT_PRESET);
        putU32 (data, 0x38, 0x00800000);
        putU32 (data, BANK_SELECTED_PRESET, 0xFFFFFFFFL);
        putU32 (data, 0x60, 1);

        // The address table of the original Emulator III, which the later banks still carry
        final Emulator3BankFormat compact = Emulator3BankFormat.EMULATOR_THREE;
        for (int i = 0; i <= compact.getMaxPresets (); i++)
            putU32 (data, compact.getPresetTableOffset () + i * 4, compact.getPresetAddressBias ());

        // The master settings of the sampler
        fill (data, 0x6E2, MASTER_SETTINGS);
        fill (data, 0x6F6, MASTER_SETTINGS);
        for (int i = 0; i < 17; i++)
            data[0x76C + i] = (byte) FULL_LEVEL;
        for (int i = 0; i < 16; i++)
            data[0x78E + i] = (byte) 0xFF;
        data[0xE96] = 'T';
        data[0xE97] = 'M';
        for (int i = 0; i < TUNING_TABLE.length; i++)
            putU32 (data, 0xE98 + i * 4, TUNING_TABLE[i]);
        // Settings which only the EIIIX writes; the ESI samplers leave them at zero
        if (bankFormat == Emulator3BankFormat.EMULATOR_3X)
        {
            fill (data, 0x394, new int []
            {
                0xFF,
                0xFF,
                0xFF,
                0x01
            });
            data[0x6D1] = 0x01;
            data[0x74C] = (byte) 0xFF;
            data[0x74D] = (byte) 0xFF;
            data[0x79F] = 0x01;
        }

        // The last entry of the sample address table points behind the last sample
        putU32 (data, bankFormat.getSampleTableOffset () + bankFormat.getMaxSamples () * 4, SAMPLE_ADDRESS_OFFSET);
        // The filler byte which separates the presets from the samples
        data[bankFormat.getPresetAreaOffset ()] = (byte) bankFormat.getSampleAreaMarker ();
        return data;
    }


    /**
     * Write a block of bytes.
     *
     * @param data The data
     * @param offset The offset to write to
     * @param values The values to write
     */
    private static void fill (final byte [] data, final int offset, final int [] values)
    {
        for (int i = 0; i < values.length; i++)
            data[offset + i] = (byte) values[i];
    }


    /** The LFO rate in Hertz by parameter value, as the devices display it. */
    private static final double [] LFO_RATE                  =
    {
        0.08, 0.11, 0.15, 0.18, 0.21, 0.25, 0.28, 0.32, 0.35, 0.39, 0.42, 0.46, 0.50, 0.54, 0.58, 0.63,
        0.67, 0.71, 0.76, 0.80, 0.85, 0.90, 0.94, 0.99, 1.04, 1.10, 1.15, 1.20, 1.25, 1.31, 1.37, 1.42,
        1.48, 1.54, 1.60, 1.67, 1.73, 1.79, 1.86, 1.93, 2.00, 2.07, 2.14, 2.21, 2.29, 2.36, 2.44, 2.52,
        2.60, 2.68, 2.77, 2.85, 2.94, 3.03, 3.12, 3.21, 3.31, 3.40, 3.50, 3.60, 3.70, 3.81, 3.91, 4.02,
        4.13, 4.25, 4.36, 4.48, 4.60, 4.72, 4.84, 4.97, 5.10, 5.23, 5.37, 5.51, 5.65, 5.79, 5.94, 6.08,
        6.24, 6.39, 6.55, 6.71, 6.88, 7.04, 7.21, 7.39, 7.57, 7.75, 7.93, 8.12, 8.32, 8.51, 8.71, 8.92,
        9.13, 9.34, 9.56, 9.78, 10.00, 10.23, 10.47, 10.71, 10.95, 11.20, 11.46, 11.71, 11.98, 12.25, 12.52, 12.80,
        13.09, 13.38, 13.68, 13.99, 14.30, 14.61, 14.93, 15.26, 15.60, 15.94, 16.29, 16.65, 17.01, 17.38, 17.76, 18.14
    };

    /** Times in seconds in the range of [0..21.69] by parameter value, used by the LFO delay. */
    private static final double [] TIME_21_69                =
    {
        0.00, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.09, 0.10, 0.11, 0.12, 0.13, 0.14, 0.15,
        0.16, 0.17, 0.18, 0.19, 0.20, 0.21, 0.22, 0.23, 0.24, 0.25, 0.26, 0.28, 0.30, 0.32, 0.34, 0.36,
        0.38, 0.40, 0.42, 0.44, 0.47, 0.49, 0.52, 0.54, 0.57, 0.60, 0.63, 0.66, 0.69, 0.73, 0.76, 0.80,
        0.84, 0.87, 0.92, 0.96, 1.00, 1.05, 1.10, 1.15, 1.20, 1.25, 1.31, 1.37, 1.43, 1.49, 1.56, 1.63,
        1.70, 1.77, 1.84, 1.92, 2.01, 2.09, 2.18, 2.27, 2.37, 2.47, 2.58, 2.69, 2.80, 2.92, 3.04, 3.17,
        3.30, 3.44, 3.59, 3.73, 3.89, 4.05, 4.22, 4.40, 4.58, 4.77, 4.96, 5.17, 5.38, 5.60, 5.83, 6.07,
        6.32, 6.58, 6.85, 7.13, 7.42, 7.72, 8.04, 8.37, 8.71, 9.06, 9.43, 9.81, 10.21, 10.63, 11.06, 11.51,
        11.97, 12.46, 12.96, 13.49, 14.03, 14.60, 15.19, 15.81, 16.44, 17.11, 17.80, 18.52, 19.26, 20.04, 20.85, 21.69
    };


    /**
     * Get the LFO rate in Hertz.
     *
     * @param value The parameter value in the range of [0..127]
     * @return The rate in Hertz
     */
    public static double getLfoRate (final int value)
    {
        return LFO_RATE[Math.clamp (value, 0, LFO_RATE.length - 1)];
    }


    /**
     * Get the LFO delay in seconds.
     *
     * @param value The parameter value in the range of [0..127]
     * @return The delay in seconds
     */
    public static double getLfoDelay (final int value)
    {
        return TIME_21_69[Math.clamp (value, 0, TIME_21_69.length - 1)];
    }


    /**
     * Get the LFO waveform, which is stored in the lower 2 bits of the filter type parameter.
     *
     * @param filterTypeAndShape The value of the filter type and LFO shape parameter
     * @return The waveform
     */
    public static LfoWaveform getLfoWaveform (final int filterTypeAndShape)
    {
        return switch (filterTypeAndShape & 3)
        {
            case 1 -> LfoWaveform.SINE;
            case 2 -> LfoWaveform.SAWTOOTH_UP;
            case 3 -> LfoWaveform.SQUARE;
            default -> LfoWaveform.TRIANGLE;
        };
    }
}
