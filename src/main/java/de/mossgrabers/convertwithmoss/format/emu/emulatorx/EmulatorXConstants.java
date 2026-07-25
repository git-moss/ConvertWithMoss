// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulatorx;

import java.nio.charset.StandardCharsets;


/**
 * Constants, chunk templates and conversion helpers for the E-mu Emulator X bank format. A bank
 * consists of an *.exb file which holds all presets and the list of the samples it uses, and a
 * sibling folder named 'SamplePool' which holds one *.ebl file per sample. Both file types use the
 * same IFF-like 'FORM E5B0TOC2' container with a table of contents of 78 byte entries. All numbers
 * are big-endian except the numeric fields of the sample header. The layout was reverse-engineered
 * from the E-mu factory banks; see documentation/design/EMULATORX_FORMAT.md for the details.
 *
 * @author Jürgen Moßgraber
 */
public class EmulatorXConstants
{
    /** The magic tag of the outer chunk. */
    public static final String  FORM_MAGIC              = "FORM";
    /** The form type of an Emulator X file. */
    public static final String  FORM_TYPE               = "E5B0TOC2";
    /** The tag of a preset chunk. */
    public static final String  PRESET_TAG              = "E5P1";
    /** The tag of a sample link chunk, which references a file of the sample pool. */
    public static final String  SAMPLE_LINK_TAG         = "E5SL";
    /** The tag of a sample chunk, the single chunk of an *.ebl file. */
    public static final String  SAMPLE_TAG              = "E5S1";
    /** The tag of a list chunk, which starts with a 4 character list type. */
    public static final String  LIST_TAG                = "LIST";

    /** The tag of the preset header chunk. */
    public static final String  PRESET_HEADER_TAG       = "Phdr";
    /** The tag of the voice list. */
    public static final String  VOICE_LIST_TYPE         = "E5VL";
    /** The tag of a voice chunk. */
    public static final String  VOICE_TAG               = "E5V1";
    /** The tag of the voice header chunk. */
    public static final String  VOICE_HEADER_TAG        = "Vhdr";
    /** The list type of the key, velocity and real-time windows. */
    public static final String  WINDOW_LIST_TYPE        = "TWL ";
    /** The tag of a window chunk. */
    public static final String  WINDOW_TAG              = "ETW ";
    /** The tag of the oscillator chunk. */
    public static final String  OSCILLATOR_TAG          = "E5Oc";
    /** The tag of the amplifier chunk. */
    public static final String  AMPLIFIER_TAG           = "E5Am";
    /** The tag of the filter chunk. */
    public static final String  FILTER_TAG              = "E5Fl";
    /** The list type of the envelopes. */
    public static final String  ENVELOPE_LIST_TYPE      = "EvL ";
    /** The tag of an envelope chunk. */
    public static final String  ENVELOPE_TAG            = "E5Ev";
    /** The list type of the modulation cords. */
    public static final String  CORD_LIST_TYPE          = "CrdL";
    /** The tag of a modulation cord chunk. */
    public static final String  CORD_TAG                = "E5Cd";
    /** The list type of the zones of a voice. */
    public static final String  ZONE_LIST_TYPE          = "E5ZL";
    /** The tag of a zone header chunk. */
    public static final String  ZONE_HEADER_TAG         = "Zhdr";

    /** The tag of the trailer chunk of a sample file, which uses little-endian sizes. */
    public static final String  SAMPLE_TRAILER_TAG      = "EXLZ";
    /** The tag of the loop marker chunk inside the trailer. */
    public static final String  SAMPLE_MARKER_TAG       = "MARK";
    /** The tag of the info chunk inside the trailer. */
    public static final String  SAMPLE_INFO_TAG         = "INFO";

    /** The name of the folder which holds the sample files of a bank. */
    public static final String  SAMPLE_POOL_FOLDER      = "SamplePool";
    /** The file ending of a bank. */
    public static final String  BANK_ENDING             = ".exb";
    /** The file ending of a sample. */
    public static final String  SAMPLE_ENDING           = ".ebl";
    /** The infix between the bank name and the index in the name of a sample file. */
    public static final String  SAMPLE_FILE_INFIX       = "SL";

    /** The size of a table of contents entry. */
    public static final int     TOC_ENTRY_SIZE          = 78;
    /** The offset of the first table of contents entry. */
    public static final int     TOC_OFFSET              = 20;
    /** The number of bytes a chunk occupies in addition to the size stored in the contents. */
    public static final int     CHUNK_OVERHEAD          = 10;
    /** The length of all name fields in bytes, which is 32 UTF-16 characters. */
    public static final int     NAME_LENGTH             = 64;

    /** The size of the preset header chunk. */
    public static final int     PRESET_HEADER_SIZE      = 154;
    /** The offset of the number of voices inside the preset header. */
    public static final int     PRESET_NUM_VOICES       = 132;
    /** The size of the voice header chunk. */
    public static final int     VOICE_HEADER_SIZE       = 16;
    /** The size of a window chunk. */
    public static final int     WINDOW_SIZE             = 8;
    /** The size of the oscillator chunk. */
    public static final int     OSCILLATOR_SIZE         = 50;
    /** The offset of the transpose in semitones inside the oscillator chunk. */
    public static final int     OSCILLATOR_TRANSPOSE    = 14;
    /** The offset of the coarse tuning in semitones inside the oscillator chunk. */
    public static final int     OSCILLATOR_COARSE_TUNE  = 15;
    /** The offset of the fine tuning in cents inside the oscillator chunk. */
    public static final int     OSCILLATOR_FINE_TUNE    = 16;
    /** The size of the amplifier chunk. */
    public static final int     AMPLIFIER_SIZE          = 12;
    /** The offset of the volume inside the amplifier chunk. */
    public static final int     AMPLIFIER_VOLUME        = 4;
    /** The offset of the panning inside the amplifier chunk. */
    public static final int     AMPLIFIER_PAN           = 8;
    /** The size of the filter chunk. */
    public static final int     FILTER_SIZE             = 62;
    /** The offset of the filter type inside the filter chunk. */
    public static final int     FILTER_TYPE             = 4;
    /** The offset of the normalized cutoff inside the filter chunk. */
    public static final int     FILTER_CUTOFF           = 5;
    /** The size of an envelope chunk. */
    public static final int     ENVELOPE_SIZE           = 64;
    /** The offset of the first envelope stage. */
    public static final int     ENVELOPE_STAGES         = 10;
    /** The size of one envelope stage: time, level and curve. */
    public static final int     ENVELOPE_STAGE_SIZE     = 9;
    /** The number of stages of an envelope. */
    public static final int     ENVELOPE_NUM_STAGES     = 6;
    /** The size of a modulation cord chunk. */
    public static final int     CORD_SIZE               = 10;
    /** The number of modulation cords of a voice. */
    public static final int     VOICE_NUM_CORDS         = 36;
    /** The size of a zone header chunk. */
    public static final int     ZONE_HEADER_SIZE        = 28;
    /** The offset of the sample index inside a zone header. */
    public static final int     ZONE_SAMPLE_INDEX       = 4;
    /** The offset of the original key inside a zone header. */
    public static final int     ZONE_ORIGINAL_KEY       = 10;

    /** The modulation source of the velocity, which adds to the destination. */
    public static final int     CORD_SOURCE_VELOCITY    = 0x0C;
    /** The modulation destination of the amplifier volume. */
    public static final int     CORD_DEST_VOLUME        = 0x40;

    /** The filter type of the 'No Filter' setting, which bypasses the filter section. */
    public static final int     FILTER_TYPE_BYPASS      = 127;
    /** The filter type of the 4-pole low-pass, the standard filter. */
    public static final int     FILTER_TYPE_LOWPASS_4   = 0;
    /** The filter type of the 2-pole low-pass. */
    public static final int     FILTER_TYPE_LOWPASS_2   = 1;
    /** The filter type of the 6-pole low-pass. */
    public static final int     FILTER_TYPE_LOWPASS_6   = 2;
    /** The filter type of the 2-pole high-pass. */
    public static final int     FILTER_TYPE_HIGHPASS_2  = 8;
    /** The filter type of the 4-pole high-pass. */
    public static final int     FILTER_TYPE_HIGHPASS_4  = 9;
    /** The filter type of the 2-pole band-pass. */
    public static final int     FILTER_TYPE_BANDPASS_2  = 16;
    /** The filter type of the 4-pole band-pass. */
    public static final int     FILTER_TYPE_BANDPASS_4  = 17;
    /** The filter type of the contrary band-pass, the closest type to a notch. */
    public static final int     FILTER_TYPE_CONTRARY    = 18;
    /** The lowest filter cutoff frequency (normalized cutoff 0). */
    public static final double  CUTOFF_MIN_HERTZ        = 57.0;
    /** The highest filter cutoff frequency (normalized cutoff 1). */
    public static final double  CUTOFF_MAX_HERTZ        = 20000.0;

    /** The lowest amplifier volume in decibel, which is silence. */
    public static final double  MIN_VOLUME_DB           = -96.0;
    /** The highest amplifier volume in decibel. */
    public static final double  MAX_VOLUME_DB           = 10.0;
    /** The full deflection of the panning of a voice. */
    public static final double  PAN_RANGE               = 64.0;
    /** The level of a fully open envelope stage in percent. */
    public static final float   FULL_LEVEL              = 100.0f;
    /** The full amount of a modulation cord in percent. */
    public static final float   FULL_CORD_AMOUNT        = 100.0f;
    /** The resolution of the fine tuning in cents: E-mu tunes in 1/64 semitones. */
    public static final double  FINE_TUNE_RESOLUTION    = 100.0 / 64.0;

    /** The version of a preset header chunk. */
    public static final int     VERSION_PRESET          = 3;
    /** The version of a zone header chunk. */
    public static final int     VERSION_ZONE            = 2;
    /** The version of an oscillator chunk. */
    public static final int     VERSION_OSCILLATOR      = 3;
    /** The version of a voice settings, envelope and LFO chunk. */
    public static final int     VERSION_2               = 2;
    /** The version of all other chunks. */
    public static final int     VERSION_1               = 1;

    /** The offset of the payload of the sample chunk inside an *.ebl file. */
    public static final int     SAMPLE_PAYLOAD_OFFSET   = 108;
    /** The offset of the header version inside the sample payload. */
    public static final int     SAMPLE_VERSION          = 2;
    /** The offset of the name inside the sample payload. */
    public static final int     SAMPLE_NAME             = 4;
    /** The offset of the format marker inside the sample payload. */
    public static final int     SAMPLE_MARKER           = 68;
    /** The value of the format marker of a sample. */
    public static final int     SAMPLE_MARKER_VALUE     = 301;
    /** The offset of the start of the left channel data inside the sample payload. */
    public static final int     SAMPLE_LEFT_START       = 72;
    /** The offset of the start of the right channel data inside the sample payload. */
    public static final int     SAMPLE_RIGHT_START      = 76;
    /** The offset of the end of the left channel data inside the sample payload. */
    public static final int     SAMPLE_LEFT_END         = 80;
    /** The offset of the end of the right channel data inside the sample payload. */
    public static final int     SAMPLE_RIGHT_END        = 84;
    /** The offset of the loop start of the left channel inside the sample payload. */
    public static final int     SAMPLE_LEFT_LOOP_START  = 88;
    /** The offset of the loop start of the right channel inside the sample payload. */
    public static final int     SAMPLE_RIGHT_LOOP_START = 92;
    /** The offset of the loop end of the left channel inside the sample payload. */
    public static final int     SAMPLE_LEFT_LOOP_END    = 96;
    /** The offset of the loop end of the right channel inside the sample payload. */
    public static final int     SAMPLE_RIGHT_LOOP_END   = 100;
    /** The offset of the sample rate inside the sample payload. */
    public static final int     SAMPLE_RATE             = 104;
    /** The offset of the loop flag inside the sample payload. */
    public static final int     SAMPLE_LOOP_FLAG        = 110;
    /** The offset of the four flag bytes inside the sample payload. */
    public static final int     SAMPLE_FLAGS            = 112;
    /** The offset of the number of channels minus one inside the sample payload. */
    public static final int     SAMPLE_EXTRA_CHANNELS   = 113;
    /** The offset of the mask of the used channels inside the sample payload. */
    public static final int     SAMPLE_CHANNEL_MASK     = 114;
    /** The offset of the comment inside the sample payload. */
    public static final int     SAMPLE_COMMENT          = 116;
    /** The offset of the trailer pointer inside the sample payload of a version 2 header. */
    public static final int     SAMPLE_TRAILER_POINTER  = 181;
    /** The offset of the audio data inside the sample payload of a version 2 header. */
    public static final int     SAMPLE_DATA_OFFSET      = 188;
    /** The number of bytes which follow the audio data before the optional trailer. */
    public static final int     SAMPLE_DATA_POSTFIX     = 2;
    /** The alignment of the start of the right channel of a stereo sample. */
    public static final int     SAMPLE_CHANNEL_GAP      = 4;
    /** The size of the trailer chunk of a sample. */
    public static final int     SAMPLE_TRAILER_SIZE     = 32;
    /** The number of bytes of one 16 bit sample frame of one channel. */
    public static final int     BYTES_PER_FRAME         = 2;

    /**
     * The maximum number of presets of a bank. The real limit of the format is unknown, this is a
     * safety limit which is far above the largest factory bank.
     */
    public static final int     MAX_PRESETS             = 4096;
    /**
     * The maximum number of samples of a bank. The real limit of the format is unknown, this is a
     * safety limit which is far above the largest factory bank.
     */
    public static final int     MAX_SAMPLES             = 4096;


    /**
     * Private constructor because this is a utility class.
     */
    private EmulatorXConstants ()
    {
        // Intentionally empty
    }


    /**
     * Convert a normalized filter cutoff to a frequency in Hertz. The curve is exponential from
     * ~57 Hz at 0 to 20 kHz at 1.
     *
     * @param cutoff The cutoff in the range of 0..1
     * @return The frequency in Hertz
     */
    public static double cutoffToHertz (final double cutoff)
    {
        return CUTOFF_MIN_HERTZ * Math.pow (CUTOFF_MAX_HERTZ / CUTOFF_MIN_HERTZ, Math.clamp (cutoff, 0, 1));
    }


    /**
     * Convert a filter cutoff frequency in Hertz to the normalized cutoff. The inverse of
     * {@link #cutoffToHertz}.
     *
     * @param frequency The frequency in Hertz
     * @return The cutoff in the range of 0..1
     */
    public static double hertzToCutoff (final double frequency)
    {
        final double limited = Math.clamp (frequency, CUTOFF_MIN_HERTZ, CUTOFF_MAX_HERTZ);
        return Math.clamp (Math.log (limited / CUTOFF_MIN_HERTZ) / Math.log (CUTOFF_MAX_HERTZ / CUTOFF_MIN_HERTZ), 0, 1);
    }


    /**
     * Quantize a fine tuning to the 1/64 semitone resolution of the E-mu tuning.
     *
     * @param cents The fine tuning in cents
     * @return The quantized fine tuning in cents
     */
    public static float quantizeFineTune (final double cents)
    {
        return (float) (Math.round (cents / FINE_TUNE_RESOLUTION) * FINE_TUNE_RESOLUTION);
    }


    /**
     * Test whether the given tag is at the given position of the data.
     *
     * @param data The data to check
     * @param offset The offset of the tag
     * @param tag The expected tag
     * @return True if the tag matches
     */
    public static boolean hasTag (final byte [] data, final int offset, final String tag)
    {
        if (offset < 0 || offset + tag.length () > data.length)
            return false;
        for (int i = 0; i < tag.length (); i++)
            if ((char) (data[offset + i] & 0xFF) != tag.charAt (i))
                return false;
        return true;
    }


    /**
     * Read an unsigned 16 bit big-endian value.
     *
     * @param data The data to read from
     * @param offset The offset of the value
     * @return The value
     */
    public static int getU16BE (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }


    /**
     * Write an unsigned 16 bit big-endian value.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value
     */
    public static void putU16BE (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value >> 8 & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }


    /**
     * Read an unsigned 32 bit big-endian value.
     *
     * @param data The data to read from
     * @param offset The offset of the value
     * @return The value
     */
    public static long getU32BE (final byte [] data, final int offset)
    {
        return (long) (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
    }


    /**
     * Write an unsigned 32 bit big-endian value.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value
     */
    public static void putU32BE (final byte [] data, final int offset, final long value)
    {
        data[offset] = (byte) (value >> 24 & 0xFF);
        data[offset + 1] = (byte) (value >> 16 & 0xFF);
        data[offset + 2] = (byte) (value >> 8 & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }


    /**
     * Read an unsigned 32 bit little-endian value.
     *
     * @param data The data to read from
     * @param offset The offset of the value
     * @return The value
     */
    public static long getU32LE (final byte [] data, final int offset)
    {
        return (long) (data[offset + 3] & 0xFF) << 24 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 1] & 0xFF) << 8 | data[offset] & 0xFF;
    }


    /**
     * Write an unsigned 32 bit little-endian value.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value
     */
    public static void putU32LE (final byte [] data, final int offset, final long value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
        data[offset + 2] = (byte) (value >> 16 & 0xFF);
        data[offset + 3] = (byte) (value >> 24 & 0xFF);
    }


    /**
     * Read an unsigned 16 bit little-endian value.
     *
     * @param data The data to read from
     * @param offset The offset of the value
     * @return The value
     */
    public static int getU16LE (final byte [] data, final int offset)
    {
        return (data[offset + 1] & 0xFF) << 8 | data[offset] & 0xFF;
    }


    /**
     * Write an unsigned 16 bit little-endian value.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value
     */
    public static void putU16LE (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    /**
     * Read a 32 bit big-endian float.
     *
     * @param data The data to read from
     * @param offset The offset of the value
     * @return The value
     */
    public static float getFloatBE (final byte [] data, final int offset)
    {
        return Float.intBitsToFloat ((int) getU32BE (data, offset));
    }


    /**
     * Write a 32 bit big-endian float.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value
     */
    public static void putFloatBE (final byte [] data, final int offset, final float value)
    {
        putU32BE (data, offset, Float.floatToIntBits (value) & 0xFFFFFFFFL);
    }


    /**
     * Read a name from a fixed size UTF-16LE field.
     *
     * @param data The data to read from
     * @param offset The offset of the field
     * @return The name without the padding
     */
    public static String decodeName (final byte [] data, final int offset)
    {
        final int available = Math.min (NAME_LENGTH, data.length - offset);
        if (available <= 0)
            return "";
        final String name = new String (data, offset, available / 2 * 2, StandardCharsets.UTF_16LE);
        final int end = name.indexOf (0);
        return (end < 0 ? name : name.substring (0, end)).trim ();
    }


    /**
     * Write a name into a fixed size UTF-16LE field, truncating it if necessary.
     *
     * @param data The data to write to
     * @param offset The offset of the field
     * @param name The name
     */
    public static void encodeName (final byte [] data, final int offset, final String name)
    {
        // The field always keeps room for the terminating zero character
        int length = Math.min (name.length (), NAME_LENGTH / 2 - 1);
        // Truncating in the middle of a surrogate pair would create an invalid character
        if (length > 0 && Character.isHighSurrogate (name.charAt (length - 1)))
            length--;
        final byte [] encoded = name.substring (0, length).getBytes (StandardCharsets.UTF_16LE);
        System.arraycopy (encoded, 0, data, offset, Math.min (encoded.length, NAME_LENGTH - 2));
    }


    /**
     * Create the name of the sample file with the given index of a bank.
     *
     * @param bankName The name of the bank
     * @param sampleIndex The 1-based index of the sample
     * @return The file name including the extension
     */
    public static String createSampleFileName (final String bankName, final int sampleIndex)
    {
        return String.format ("%s%s%03d%s", bankName, SAMPLE_FILE_INFIX, Integer.valueOf (sampleIndex), SAMPLE_ENDING);
    }
}
