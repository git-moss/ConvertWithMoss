// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator4;

import java.util.Base64;


/**
 * Constants and conversion helpers for the E-mu Emulator IV bank format (E4B). An E4B file is an
 * IFF-like 'FORM E4B0' container holding a table of contents (TOC1), a MIDI multimap (E4Ma), one
 * E4P1 chunk per preset, one E3S1 chunk per sample and a trailing EMSt master setup chunk. All
 * chunk sizes and indices are big-endian, the fields inside the E3S1 sample header are
 * little-endian. The layout was reverse-engineered by the mpc2emu project from hardware-saved E4XT
 * banks and commercial EOS CD-ROMs; see documentation/design/E4B_FORMAT.md for the details.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator4Constants
{
    /** The magic tag of the outer chunk. */
    public static final byte []  FORM_MAGIC         = "FORM".getBytes ();
    /** The form type of an E4B bank. */
    public static final byte []  FORM_TYPE          = "E4B0".getBytes ();
    /** The tag of the table of contents chunk. */
    public static final byte []  TOC_TAG            = "TOC1".getBytes ();
    /** The tag of the MIDI multimap chunk. */
    public static final byte []  E4MA_TAG           = "E4Ma".getBytes ();
    /** The tag of a preset chunk. */
    public static final byte []  PRESET_TAG         = "E4P1".getBytes ();
    /** The tag of a sample chunk. */
    public static final byte []  SAMPLE_TAG         = "E3S1".getBytes ();
    /** The tag of the master setup chunk (always the last chunk, not listed in the TOC). */
    public static final byte []  EMST_TAG           = "EMSt".getBytes ();

    /** The length of all name fields (space padded ASCII without a terminator). */
    public static final int      NAME_LENGTH        = 16;
    /** The size of a TOC entry. */
    public static final int      TOC_ENTRY_SIZE     = 32;
    /** The size of the E4Ma multimap chunk. */
    public static final int      E4MA_SIZE          = 256;
    /** The size of an E3S1 sample header: 2 bytes sample index + the 92 byte EOS sample struct. */
    public static final int      SAMPLE_HEADER_SIZE = 94;
    /** The size of the EOS sample struct; all start/end/loop offsets are relative to its start. */
    public static final int      SAMPLE_STRUCT_SIZE = 92;
    /** The size of the fixed E4P1 preset header. */
    public static final int      PRESET_HEADER_SIZE = 82;
    /** The size of the fixed part of a voice block. */
    public static final int      VOICE_SIZE         = 284;
    /** The offset of the primary zone table inside a voice block. */
    public static final int      VOICE_PZT_OFFSET   = 110;
    /** The offset of the modulation cord table inside a voice block. */
    public static final int      VOICE_MOD_OFFSET   = 190;
    /** The size of the modulation cord table (20 cords of 4 bytes). */
    public static final int      VOICE_MOD_SIZE     = 80;
    /** The size of a zone entry in the secondary zone table of a voice. */
    public static final int      ZONE_ENTRY_SIZE    = 22;

    /** The maximum number of samples in a bank (S000-S999). */
    public static final int      MAX_SAMPLES        = 1000;
    /** The maximum number of presets in a bank (P000-P999). */
    public static final int      MAX_PRESETS       = 1000;

    /** The 'forward loop on' bit of the sample options field. */
    public static final int      OPTION_LOOP        = 0x0001;
    /** The sample options of a mono sample without a loop. */
    public static final int      OPTIONS_MONO       = 0x0020;
    /** The sample options of a mono sample with a forward loop. */
    public static final int      OPTIONS_MONO_LOOP  = 0x0031;

    /** The lowest filter cutoff frequency (cutoff byte 0). */
    public static final double   CUTOFF_MIN_HERTZ   = 57.0;
    /** The highest filter cutoff frequency (cutoff byte 255). */
    public static final double   CUTOFF_MAX_HERTZ   = 20000.0;

    /** The default velocity-to-amplitude modulation amount of the EOS factory cord set (~24%). */
    public static final int      DEFAULT_VELOCITY_AMOUNT = 0x1E;
    /** The default release rate of the EOS factory envelope. */
    public static final int      DEFAULT_RELEASE_RATE    = 0x14;
    /** The filter cutoff key tracking of a +100% Key-to-FilterFreq cord in octaves per octave. */
    public static final double   FULL_KEY_TRACKING       = 0.713;

    // The envelope rate to time law was calibrated on E4XT hardware by the mpc2emu project:
    // seconds = 0.0310 * e^(0.0581 * rate) with rate 0 = instant and rate 127 = ~47 seconds
    private static final double  ENV_RATE_FACTOR    = 0.0310;
    private static final double  ENV_RATE_EXPONENT  = 0.0581;

    private static final String [] NOTE_NAMES       =
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "B"
    };

    /**
     * The primary zone table template of a voice (64 bytes). It carries the default amplitude
     * envelope at [0..11], the default filter envelope at [14..25] and the default LFO settings,
     * taken from a hardware saved reference voice.
     */
    public static final byte []  PRIMARY_ZONE_TEMPLATE =
    {
        0x00, 0x00, 0x00, 0x7F, 0x00, 0x7E, 0x00, 0x7F,
        0x14, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00,
        0x00, 0x7F, 0x00, 0x7E, 0x00, 0x7F, 0x14, 0x00,
        0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x7F,
        0x00, 0x7E, 0x00, 0x7F, 0x14, 0x00, 0x00, 0x00,
        0x03, 0x00, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    /**
     * The EOS factory default modulation cord table (20 cords of [source, destination, amount,
     * flag]). Cord 0 routes velocity to the amplitude (amount 0x1E = ~24%), cord 5 routes the
     * filter envelope to the cutoff (amount 0 = inactive) and cord 6 routes the key position to
     * the cutoff (amount 0 = no key tracking). A voice with an all zero table is valid but does
     * not respond to velocity and must not use the non-transpose mode.
     */
    public static final byte []  MOD_CORD_TEMPLATE     =
    {
        0x0C, 0x40, 0x1E, 0x00, 0x10, 0x30, 0x08, 0x00,
        0x60, 0x30, 0x00, 0x00, 0x11, (byte) 0xAA, 0x10, 0x00,
        0x0C, 0x38, 0x00, 0x00, 0x50, 0x38, 0x00, 0x00,
        0x08, 0x38, 0x00, 0x00, 0x16, 0x08, 0x7F, 0x00,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    /** The amount byte offset of the velocity-to-amplitude cord in the cord table. */
    public static final int      MOD_VELOCITY_AMOUNT   = 2;
    /** The amount byte offset of the filter-envelope-to-cutoff cord in the cord table. */
    public static final int      MOD_FILTER_ENVELOPE_AMOUNT = 22;
    /** The amount byte offset of the key-to-cutoff (filter key tracking) cord in the cord table. */
    public static final int      MOD_KEY_TRACKING_AMOUNT    = 26;

    /** The repeating 12 byte entry of the default E4Ma multimap (all presets on all channels). */
    public static final byte []  E4MA_ENTRY            =
    {
        0x00, 0x00, 0x00, 0x01, 0x7F, 0x00, (byte) 0xFF, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF
    };

    // The default 1366 byte 'Untitled MSetup' master setup block, captured from hardware-saved
    // banks (identical across all freshly created banks). The FORM size deliberately stops 4 bytes
    // short of the file end inside the trailing zeros of this chunk, so it must be the last chunk.
    private static final String  EMST_DEFAULT_BASE64   = "AABVbnRpdGxlZCBNU2V0dXAgAAACAAAAfwAAAAD/AAAAAAAAAAAAAAAAAAAAAAAAAAB/AAAA" + "AAB/AAAAAP8AAAAAAAAAAAAAAAAAAAAAAAAAAH8AAAAAAH8AAAAA/wAAAAAAAAAAAAAAAAAA" + "AAAAAAAAfwAAAAAAfwAAAAD/AAAAAAAAAAAAAAAAAAAAAAAAAAB/AAAAAAB/AAAAAP8AAAAA" + "AAAAAAAAAAAAAAAAAAAAAH8AAAAAAH8AAAAA/wAAAAAAAAAAAAAAAAAAAAAAAAAAfwAAAAAA" + "fwAAAAD/AAAAAAAAAAAAAAAAAAAAAAAAAAB/AAAAAAB/AAAAAP8AAAAAAAAAAAAAAAAAAAAA" + "AAAAAH8AAAAAAH8AAAAA/wAAAAAAAAAAAAAAAAAAAAAAAAAAfwAAAAAAfwAAAAD/AAAAAAAA" + "AAAAAAAAAAAAAAAAAAB/AAAAAAB/AAAAAP8AAAAAAAAAAAAAAAAAAAAAAAAAAH8AAAAAAH8A" + "AAAA/wAAAAAAAAAAAAAAAAAAAAAAAAAAfwAAAAAAfwAAAAD/AAAAAAAAAAAAAAAAAAAAAAAA" + "AAB/AAAAAAB/AAAAAP8AAAAAAAAAAAAAAAAAAAAAAAAAAH8AAAAAAH8AAAAA/wAAAAAAAAAA" + "AAAAAAAAAAAAAAAAfwAAAAAAfwAAAAD/AAAAAAAAAAAAAAAAAAAAAAAAAAB/AAAAAAB/AAAA" + "AP8AAAAAAAAAAAAAAAAAAAAAAAAAAH8AAAAAAH8AAAAA/wAAAAAAAAAAAAAAAAAAAAAAAAAA" + "fwAAAAAAfwAAAAD/AAAAAAAAAAAAAAAAAAAAAAAAAAB/AAAAAAB/AAAAAP8AAAAAAAAAAAAA" + "AAAAAAAAAAAAAH8AAAAAAH8AAAAA/wAAAAAAAAAAAAAAAAAAAAAAAAAAfwAAAAAAfwAAAAD/" + "AAAAAAAAAAAAAAAAAAAAAAAAAAB/AAAAAAB/AAAAAP8AAAAAAAAAAAAAAAAAAAAAAAAAAH8A" + "AAAAAH8AAAAA/wAAAAAAAAAAAAAAAAAAAAAAAAAAfwAAAAAAfwAAAAD/AAAAAAAAAAAAAAAA" + "AAAAAAAAAAB/AAAAAAB/AAAAAP8AAAAAAAAAAAAAAAAAAAAAAAAAAH8AAAAAAH8AAAAA/wAA" + "AAAAAAAAAAAAAAAAAAAAAAAAfwAAAAAAfwAAAAD/AAAAAAAAAAAAAAAAAAAAAAAAAAB/AAAA" + "AAB/AAAAAP8AAAAAAAAAAAAAAAAAAAAAAAAAAH8AAAAAAH8AAAAA/wAAAAAAAAAAAAAAAAAA" + "AAAAAAAAfwAAAAAAfwAAAAD/AAAAAAAAAAAAAAAAAAAAAAAAAAB/AAAAAAB/AAAAAP8AAAAA" + "AAAAAAAAAAAAAAAAAAAAAH8AAAD//////////wAAAAD/////AAAAAAAAAAAAAAAAAAAAAAAA" + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + "AAAAAAAAAAAAAAAAAAAAAA==";


    /**
     * Private constructor since this is a utility class.
     */
    private Emulator4Constants ()
    {
        // Intentionally empty
    }


    /**
     * Get the default master setup (EMSt) chunk content.
     *
     * @return The 1366 bytes of the default 'Untitled MSetup' block
     */
    public static byte [] getDefaultMasterSetup ()
    {
        return Base64.getDecoder ().decode (EMST_DEFAULT_BASE64);
    }


    /**
     * Convert an envelope time in seconds to an EOS envelope rate byte. The law was calibrated on
     * E4XT hardware: seconds = 0.0310 * e^(0.0581 * rate), 0 = instant, 127 = ~47 seconds.
     *
     * @param seconds The time in seconds
     * @return The rate in the range of 0..127
     */
    public static int envelopeTimeToRate (final double seconds)
    {
        if (seconds <= 0)
            return 0;
        return Math.clamp ((int) Math.round ((Math.log (seconds) - Math.log (ENV_RATE_FACTOR)) / ENV_RATE_EXPONENT), 0, 127);
    }


    /**
     * Convert an EOS envelope rate byte to a time in seconds. The inverse of
     * {@link #envelopeTimeToRate}, except that rate 0 is treated as instant.
     *
     * @param rate The rate in the range of 0..127
     * @return The time in seconds
     */
    public static double envelopeRateToTime (final int rate)
    {
        if (rate <= 0)
            return 0;
        return ENV_RATE_FACTOR * Math.exp (ENV_RATE_EXPONENT * Math.min (rate, 127));
    }


    /**
     * Convert a filter cutoff byte to a frequency in Hertz. The curve is exponential from ~57 Hz
     * at 0 to 20 kHz at 255.
     *
     * @param cutoff The cutoff in the range of 0..255
     * @return The frequency in Hertz
     */
    public static double cutoffToHertz (final int cutoff)
    {
        return CUTOFF_MIN_HERTZ * Math.pow (CUTOFF_MAX_HERTZ / CUTOFF_MIN_HERTZ, Math.clamp (cutoff, 0, 255) / 255.0);
    }


    /**
     * Convert a filter cutoff frequency in Hertz to the cutoff byte. The inverse of
     * {@link #cutoffToHertz}.
     *
     * @param frequency The frequency in Hertz
     * @return The cutoff in the range of 0..255
     */
    public static int hertzToCutoff (final double frequency)
    {
        final double limited = Math.clamp (frequency, CUTOFF_MIN_HERTZ, CUTOFF_MAX_HERTZ);
        return Math.clamp ((int) Math.round (255.0 * Math.log (limited / CUTOFF_MIN_HERTZ) / Math.log (CUTOFF_MAX_HERTZ / CUTOFF_MIN_HERTZ)), 0, 255);
    }


    /**
     * Format the note name suffix which the format appends to sample names to make the root note
     * visible in hardware browsers, e.g. '_C3' for MIDI note 60.
     *
     * @param midiNote The MIDI note
     * @return The suffix including the leading underscore
     */
    public static String formatNoteSuffix (final int midiNote)
    {
        return "_" + NOTE_NAMES[midiNote % 12] + Integer.toString (midiNote / 12 - 2);
    }


    /**
     * Look up a note name (without the leading underscore) and octave as used in the sample name
     * suffix and calculate the MIDI note.
     *
     * @param noteName The note name, e.g. 'C#'
     * @param octave The octave, e.g. 3 for the middle C
     * @return The MIDI note or -1 if the name is not a valid note
     */
    public static int lookupNote (final String noteName, final int octave)
    {
        for (int i = 0; i < NOTE_NAMES.length; i++)
            if (NOTE_NAMES[i].equals (noteName))
            {
                final int midiNote = (octave + 2) * 12 + i;
                return midiNote >= 0 && midiNote <= 127 ? midiNote : -1;
            }
        return -1;
    }


    /**
     * Read an unsigned big-endian 16 bit value.
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
     * Write an unsigned big-endian 16 bit value.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value to write
     */
    public static void putU16BE (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value >> 8 & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }


    /**
     * Read an unsigned big-endian 32 bit value.
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
     * Write an unsigned big-endian 32 bit value.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value to write
     */
    public static void putU32BE (final byte [] data, final int offset, final long value)
    {
        data[offset] = (byte) (value >> 24 & 0xFF);
        data[offset + 1] = (byte) (value >> 16 & 0xFF);
        data[offset + 2] = (byte) (value >> 8 & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }


    /**
     * Read an unsigned little-endian 32 bit value.
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
     * Write an unsigned little-endian 32 bit value.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value to write
     */
    public static void putU32LE (final byte [] data, final int offset, final long value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
        data[offset + 2] = (byte) (value >> 16 & 0xFF);
        data[offset + 3] = (byte) (value >> 24 & 0xFF);
    }


    /**
     * Read an unsigned little-endian 16 bit value.
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
     * Write an unsigned little-endian 16 bit value.
     *
     * @param data The data to write to
     * @param offset The offset of the value
     * @param value The value to write
     */
    public static void putU16LE (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    /**
     * Interpret a byte as a signed value.
     *
     * @param value The byte
     * @return The value in the range of -128..127
     */
    public static int getSigned (final byte value)
    {
        return value;
    }


    /**
     * Check if the data contains the given magic bytes at the given offset.
     *
     * @param data The data to check
     * @param offset The offset of the magic bytes
     * @param magic The magic bytes
     * @return True if the magic bytes are present
     */
    public static boolean hasMagic (final byte [] data, final int offset, final byte [] magic)
    {
        if (data.length < offset + magic.length)
            return false;
        for (int i = 0; i < magic.length; i++)
            if (data[offset + i] != magic[i])
                return false;
        return true;
    }


    /**
     * Decode a 16 character space padded ASCII name field.
     *
     * @param data The data to read from
     * @param offset The offset of the name field
     * @return The trimmed name
     */
    public static String decodeName (final byte [] data, final int offset)
    {
        final StringBuilder sb = new StringBuilder (NAME_LENGTH);
        for (int i = 0; i < NAME_LENGTH; i++)
        {
            final int c = data[offset + i] & 0xFF;
            sb.append (c >= 0x20 && c < 0x7F ? (char) c : '?');
        }
        return sb.toString ().trim ();
    }


    /**
     * Encode a name as a 16 byte space padded ASCII field. Characters outside of the printable
     * ASCII range are replaced with a question mark.
     *
     * @param data The data to write to
     * @param offset The offset of the name field
     * @param name The name to encode
     */
    public static void encodeName (final byte [] data, final int offset, final String name)
    {
        for (int i = 0; i < NAME_LENGTH; i++)
        {
            char c = i < name.length () ? name.charAt (i) : ' ';
            if (c < 0x20 || c >= 0x7F)
                c = '?';
            data[offset + i] = (byte) c;
        }
    }
}
