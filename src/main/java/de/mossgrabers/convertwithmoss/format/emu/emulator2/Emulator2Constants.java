// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator2;

/**
 * Constants of the E-mu Emulator II bank, which is stored in the second part of a floppy disk. See
 * documentation/design/EMULATOR2_FORMAT.md for how these were established.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator2Constants
{
    /** The size of a raw disk image: 2 sides of 80 tracks with one 3584 byte sector each. */
    public static final int   IMAGE_SIZE          = 573440;
    /** The bank starts behind the operating system, at track 22. */
    public static final int   BANK_OFFSET         = 0x13400;

    /** The keyboard of the Emulator II has 61 keys. */
    public static final int   NUM_KEYS            = 61;
    /** The MIDI note of the lowest key. */
    public static final int   LOWEST_KEY          = 26;

    /**
     * The identifier of the voice which is assigned to a key; zero means that the key is silent.
     * Identifiers start at {@link #VOICE_ID_BASE} and follow the order of the voice records. There
     * is a second table of voice numbers at 0x00C, but that one is a display numbering whose base
     * differs from bank to bank, so it cannot be used to find the record.
     */
    public static final int   KEY_MAP_VOICE_ID    = 0x049;
    /** The identifier of the first voice record. */
    public static final int   VOICE_ID_BASE       = 0x9B;
    /** The transposition of a key, one step per semitone. */
    public static final int   KEY_MAP_TRANSPOSE   = 0x086;
    /** The transposition value at which a voice plays at its recorded pitch. */
    public static final int   TRANSPOSE_UNITY     = 0x0E;

    /** The array of voice records. */
    public static final int   VOICE_TABLE         = 0x5BA;
    /** The size of one voice record. */
    public static final int   VOICE_SIZE          = 0x100;
    /** The length of the name of a voice. */
    public static final int   VOICE_NAME_LENGTH   = 12;
    /** The start of the audio of a voice, relative to the bank, 24 bit little-endian. */
    public static final int   VOICE_SAMPLE_START  = 0x0D;
    /** The end of the audio of a voice. */
    public static final int   VOICE_SAMPLE_END    = 0x13;
    /** The length of the loop of a voice in frames. */
    public static final int   VOICE_LOOP_LENGTH   = 0x16;
    /** The start of the loop of a voice, relative to the bank. */
    public static final int   VOICE_LOOP_START    = 0x19;

    /** The signature which starts a preset record. */
    public static final int   PRESET_NAME_OFFSET  = 9;
    /** The signature which starts a preset record. */
    public static final byte  PRESET_SIGNATURE [] =
    {
        0x01,
        0x04,
        0x00,
        0x00,
        0x00,
        0x00,
        0x02,
        0x03,
        (byte) 0x89
    };

    /** The Emulator II plays back at this fixed rate and cannot vary it. */
    public static final int   SAMPLE_RATE         = 27777;

    /** Expansion of the 256 possible sample bytes into signed 16 bit audio. */
    private static final short EXPANSION []       = createExpansionTable ();


    /**
     * Private constructor since this is a utility class.
     */
    private Emulator2Constants ()
    {
        // Intentionally empty
    }


    /**
     * Expand one stored sample byte into a signed 16 bit sample value.
     *
     * @param sampleByte The byte as stored on the disk
     * @return The sample value
     */
    public static short expand (final int sampleByte)
    {
        return EXPANSION[sampleByte & 0xFF];
    }


    /**
     * Build the transfer function of the AM6072 companding DAC which the Emulator II uses for each
     * of its output channels. A stored byte is a sign in bit 7, a chord in bits 6 to 4 and a step
     * in bits 3 to 0; the chord doubles the size of a step, which is what gives the 8 bits of a
     * sample the dynamic range of about 13 linear bits.
     *
     * @return The expansion of all 256 byte values, scaled to the 16 bit range
     */
    private static short [] createExpansionTable ()
    {
        final short [] table = new short [256];
        for (int value = 0; value < 256; value++)
        {
            final int chord = value >> 4 & 0x07;
            final int step = value & 0x0F;
            final int magnitude = (((step << 1) + 33) << chord) - 33;
            // The largest magnitude is 8031, which leaves the scaled value inside the 16 bit range
            final int scaled = magnitude * 4;
            table[value] = (short) ((value & 0x80) != 0 ? -scaled : scaled);
        }
        return table;
    }
}
