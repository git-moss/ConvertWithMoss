// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.sp404mk2;

/**
 * Constants and low-level byte helpers for the Roland SP-404MK2 project format. The pad
 * configuration (<i>PADCONF.BIN</i>, magic <code>RFPD</code>) and the sample files
 * (<i>SMPL/BANK&lt;bank&gt;-&lt;pad&gt;.SMP</i>, magic <code>RFWV</code>) were reverse-engineered
 * and validated against real exported projects and the SP-404MK2 v5.52 firmware (see
 * <i>documentation/design/SP404MK2_FORMAT.md</i>). All multi-byte integers - and the audio PCM
 * itself - are stored big-endian.
 *
 * @author Jürgen Moßgraber
 */
public final class SP404Mk2Constants
{
    /** The device native sample rate. */
    public static final int        SAMPLE_RATE           = 48000;
    /** Number of banks (A-J) in a project. */
    public static final int        BANK_COUNT            = 10;
    /** Number of pads per bank. */
    public static final int        PADS_PER_BANK         = 16;
    /** Total number of pads in a project. */
    public static final int        PAD_COUNT             = BANK_COUNT * PADS_PER_BANK;

    // PADCONF.BIN layout
    /** The fixed file name of the pad configuration of a project. */
    public static final String     PADCONF_FILE_NAME     = "PADCONF.BIN";
    /** The lower-case file name the detector scans for (see {@link #PADCONF_FILE_NAME}). */
    public static final String     PADCONF_ENDING        = "padconf.bin";
    /** The ending of the sample files, e.g. <i>SMPL/BANK1-01.SMP</i>. */
    public static final String     SAMPLE_ENDING         = ".smp";
    /** The magic bytes of a pad configuration file. */
    protected static final byte [] RFPD_MAGIC            =
    {
        'R',
        'F',
        'P',
        'D'
    };
    /** The size of the short PADCONF.BIN form (no project name, no marks section). */
    public static final int        PADCONF_SIZE_EXPORT   = 31488;
    /**
     * The size of the PADCONF.BIN which the device and the SP-404MKII App store and export (has a
     * larger header with the project name and a trailing marks section). Only this form is
     * imported by the SP-404MKII App.
     */
    public static final int        PADCONF_SIZE_INTERNAL = 52000;
    /** Header size of the export form; the pad-metadata block starts right after the header. */
    public static final int        HEADER_SIZE_EXPORT    = 0x80;
    /** Header size of the internal form. */
    public static final int        HEADER_SIZE_INTERNAL  = 0xA0;
    /** Size of one pad-metadata record. */
    public static final int        PAD_RECORD_SIZE       = 172;
    /** Size of one pad-name record. */
    public static final int        NAME_SIZE             = 24;
    /** Maximum number of characters in a pad name (the 24th byte is the terminator). */
    public static final int        NAME_MAX_CHARS        = 23;

    // PADCONF.BIN header fields
    /** Header: number of pads (always 160). */
    public static final int        HDR_PAD_COUNT         = 0x04;
    /** Header: format version. */
    public static final int        HDR_VERSION           = 0x08;
    /** Header: size of the pad-data region (metadata + names). */
    public static final int        HDR_DATA_SIZE         = 0x0C;
    /** Header: first of the 10 per-bank tempo values (BPM * 200). */
    public static final int        HDR_BANK_BPM          = 0x40;
    /** Header of the internal form: the project name (up to 31 ASCII characters). */
    public static final int        HDR_PROJECT_NAME      = 0x80;
    /** The size of the per-pad record of the marks section of the internal form. */
    public static final int        MARKS_RECORD_SIZE     = 128;

    // Pad-metadata record fields (relative to the record start)
    // All sample positions are byte offsets into the .SMP file: SAMPLE_DATA_OFFSET + frame *
    // bytes per frame
    /** Original sample end as a byte offset into the .SMP file (equals the file size). */
    public static final int        PAD_END               = 0x00;
    /** Sample start as a byte offset; {@link #START_SENTINEL} is the first frame. */
    public static final int        PAD_START             = 0x04;
    /** The (trimmed) sample end as a byte offset. */
    public static final int        PAD_END2              = 0x08;
    /** Playback level, 0-127. */
    public static final int        PAD_VOLUME            = 0x0C;
    /** GATE: 1 = plays only while the pad is held, 0 = plays to the end. */
    public static final int        PAD_GATE              = 0x10;
    /** {@link #LOOP_COUNT_INFINITE} if the loop is on, otherwise 0. */
    public static final int        PAD_LOOP_COUNT        = 0x14;
    /** Constant 1 for every pad record. */
    public static final int        PAD_USED              = 0x18;
    /** Mute group: 0 = Off, 1-10 = A-J. */
    public static final int        PAD_MUTE_GROUP        = 0x1C;
    /** Pad tempo, BPM * 100. */
    public static final int        PAD_BPM               = 0x24;
    /** Bit field of the pad flags, see {@link #FLAG_FIXED_VELOCITY} and {@link #FLAG_ONE_SHOT}. */
    public static final int        PAD_FLAGS             = 0x28;
    /** Loop start as a byte offset. */
    public static final int        PAD_LOOP_START        = 0x2C;
    /** LOOP enable, 0 or 1. */
    public static final int        PAD_LOOP              = 0x30;
    /** Coarse pitch in semitones (signed). */
    public static final int        PAD_PITCH             = 0x34;
    /** Fine pitch in cents (signed). */
    public static final int        PAD_FINE              = 0x38;
    /** Play mode: 0 = Forward, 1 = Reverse, 2 = Forward Ping-Pong, 3 = Reverse Ping-Pong. */
    public static final int        PAD_PLAY_MODE         = 0x3C;
    /** Playback speed, 10000 = 100%. */
    public static final int        PAD_SPEED             = 0x40;
    /** VINYL: 1 = the pitch follows the speed and the pitch fields are ignored. */
    public static final int        PAD_VINYL             = 0x44;
    /** Panning, 0x40 = center. */
    public static final int        PAD_PAN               = 0x48;
    /** Envelope attack, 0-127 (127 = 3 seconds). */
    public static final int        PAD_ATTACK            = 0x58;
    /** Envelope hold, 1-100 percent of the sample which is played back. */
    public static final int        PAD_HOLD              = 0x5C;
    /** Envelope release, 0-127 (127 = 3 seconds). */
    public static final int        PAD_RELEASE           = 0x60;

    /** Flag in {@link #PAD_FLAGS}: FIXED VELOCITY is on. */
    public static final int        FLAG_FIXED_VELOCITY   = 0x01;
    /** Flag in {@link #PAD_FLAGS}: ONE SHOT is on. */
    public static final int        FLAG_ONE_SHOT         = 0x20;
    /** The value of {@link #PAD_LOOP_COUNT} for an active loop. */
    public static final long       LOOP_COUNT_INFINITE   = 0x7FFFFFFFL;
    /** Play mode Reverse. */
    public static final int        PLAY_MODE_REVERSE     = 1;
    /** Play mode Forward Ping-Pong. */
    public static final int        PLAY_MODE_PING_PONG   = 2;
    /** The number of mute groups (A-J). */
    public static final int        MUTE_GROUP_COUNT      = 10;
    /** The maximum value of the envelope attack and release. */
    public static final int        ENVELOPE_MAX          = 127;
    /** The time in seconds of the maximum envelope attack and release. */
    public static final double     ENVELOPE_MAX_TIME     = 3.0;
    /** The byte offset of the first frame, see {@link #SAMPLE_DATA_OFFSET}. */
    public static final int        START_SENTINEL        = 512;
    /** Center value of the panning field. */
    public static final int        PAN_CENTER            = 0x40;

    // RFWV sample file layout
    /** The magic bytes of a sample file. */
    protected static final byte [] RFWV_MAGIC            =
    {
        'R',
        'F',
        'W',
        'V'
    };
    /** Size of the RFWV header fields. */
    public static final int        RFWV_HEADER_SIZE      = 32;
    /**
     * The offset of the PCM data. The 32 header bytes are followed by a 480 byte block which looks
     * encrypted. It depends on the length of the sample but not on its content, and the SP-404MKII
     * App does not import a sample whose block is zeroed.
     */
    public static final int        SAMPLE_DATA_OFFSET    = 512;
    /** RFWV header: sample rate. */
    public static final int        RFWV_RATE             = 0x08;
    /** RFWV header: number of channels. */
    public static final int        RFWV_CHANNELS         = 0x0C;
    /** RFWV header: bit resolution. */
    public static final int        RFWV_BITS             = 0x10;


    /**
     * Constructor. Private due to only static usage.
     */
    private SP404Mk2Constants ()
    {
        // Intentionally empty
    }


    /**
     * The header size for a PADCONF.BIN of the given length, or -1 if the length is not a
     * recognized PADCONF.BIN. The pad-metadata block starts right after the header.
     *
     * @param length The file length
     * @return The header size (which equals the pad-metadata start), or -1
     */
    public static int headerSizeForLength (final int length)
    {
        if (length == PADCONF_SIZE_EXPORT)
            return HEADER_SIZE_EXPORT;
        if (length == PADCONF_SIZE_INTERNAL)
            return HEADER_SIZE_INTERNAL;
        return -1;
    }


    /**
     * The offset of the pad-name block, which follows the header and the pad-metadata block.
     *
     * @param headerSize The header size (see {@link #headerSizeForLength(int)})
     * @return The offset of the first pad name
     */
    public static int nameBlockStart (final int headerSize)
    {
        return headerSize + PAD_COUNT * PAD_RECORD_SIZE;
    }


    /**
     * Test if the given data starts with the given magic bytes.
     *
     * @param data The data
     * @param magic The magic bytes
     * @return True if the data starts with the magic bytes
     */
    public static boolean hasMagic (final byte [] data, final byte [] magic)
    {
        if (data.length < magic.length)
            return false;
        for (int i = 0; i < magic.length; i++)
            if (data[i] != magic[i])
                return false;
        return true;
    }


    /**
     * Read an unsigned 32-bit big-endian integer.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The value
     */
    public static long getU32 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFFL) << 24 | (data[offset + 1] & 0xFFL) << 16 | (data[offset + 2] & 0xFFL) << 8 | data[offset + 3] & 0xFFL;
    }


    /**
     * Read a signed 32-bit big-endian integer.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The value
     */
    public static int getS32 (final byte [] data, final int offset)
    {
        return (int) getU32 (data, offset);
    }


    /**
     * Write a 32-bit big-endian integer.
     *
     * @param data The data
     * @param offset The offset to write to
     * @param value The value
     */
    public static void putU32 (final byte [] data, final int offset, final long value)
    {
        data[offset] = (byte) (value >> 24 & 0xFF);
        data[offset + 1] = (byte) (value >> 16 & 0xFF);
        data[offset + 2] = (byte) (value >> 8 & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }


    /**
     * Swap the byte order of every 16-bit sample in place, converting between the big-endian PCM of
     * the RFWV files and the little-endian PCM used everywhere else. The operation is its own
     * inverse.
     *
     * @param pcm The interleaved 16-bit PCM data (its length must be even)
     */
    public static void swap16 (final byte [] pcm)
    {
        for (int i = 0; i + 1 < pcm.length; i += 2)
        {
            final byte tmp = pcm[i];
            pcm[i] = pcm[i + 1];
            pcm[i + 1] = tmp;
        }
    }
}
