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
    /** The size of an exported PADCONF.BIN (EXPORT/IMPORT PROJECT form). */
    public static final int        PADCONF_SIZE_EXPORT   = 31488;
    /**
     * The size of an internal-storage PADCONF.BIN (has a larger header and a trailing marks
     * section).
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

    // Pad-metadata record fields (relative to the record start)
    /** Sample end as a byte offset into the .SMP file (equals the file size for a full pad). */
    public static final int        PAD_END               = 0x00;
    /** Sample start as a byte offset; {@link #START_SENTINEL} means "from the beginning". */
    public static final int        PAD_START             = 0x04;
    /** Duplicate of the sample end (original / user end pair). */
    public static final int        PAD_END2              = 0x08;
    /** Playback level, 0-127. */
    public static final int        PAD_VOLUME            = 0x0C;
    /** Loop enable, 0 or 1. */
    public static final int        PAD_LOOP              = 0x10;
    /** Constant 1 for every pad record. */
    public static final int        PAD_USED              = 0x18;
    /** Pad tempo, BPM * 100. */
    public static final int        PAD_BPM               = 0x24;
    /** Loop start as a byte offset. */
    public static final int        PAD_LOOP_START        = 0x2C;
    /** Coarse pitch in semitones (signed). */
    public static final int        PAD_PITCH             = 0x30;
    /** Fine pitch in cents (signed). */
    public static final int        PAD_FINE              = 0x34;
    /** Playback speed, 10000 = 100%. */
    public static final int        PAD_SPEED             = 0x40;
    /** Panning, 0x40 = center. */
    public static final int        PAD_PAN               = 0x48;
    /** The byte-offset value used to mean "start at the very beginning". */
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
    /** Size of the RFWV header; PCM data follows. */
    public static final int        RFWV_HEADER_SIZE      = 32;
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
