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
    /** The disk has 80 tracks per side. */
    public static final int    CYLINDERS               = 80;
    /** The disk has two sides. */
    public static final int    HEADS                   = 2;
    /** The size of one track. */
    public static final int    TRACK_SIZE              = 3584;
    /** The size of a raw disk image: 2 sides of 80 tracks with one 3584 byte sector each. */
    public static final int    IMAGE_SIZE              = CYLINDERS * HEADS * TRACK_SIZE;
    /** The bank starts behind the operating system, at track 22. */
    public static final int    BANK_OFFSET             = 22 * TRACK_SIZE;
    /** The size of the bank region of a disk. */
    public static final int    BANK_SIZE               = IMAGE_SIZE - BANK_OFFSET;
    /** The memory address at which the bank is loaded, which the pointers in the bank use. */
    public static final int    BANK_ADDRESS            = 0x9600;

    /** The keyboard of the Emulator II has 61 keys, C2 to C7. */
    public static final int    NUM_KEYS                = 61;
    /** The MIDI note of the lowest key. */
    public static final int    LOWEST_KEY              = 36;
    /** The transposition value at which a voice plays at its recorded pitch. */
    public static final int    TRANSPOSE_UNITY         = 0x10;

    /** The name of the selected preset. */
    public static final int    SELECTED_NAME           = 0x000;
    /** The key map of the selected preset: the voice number each key plays. */
    public static final int    KEY_MAP_VOICE_NUMBER    = 0x00C;
    /** The key map of the selected preset: the identifier of the voice each key plays. */
    public static final int    KEY_MAP_VOICE_ID        = 0x049;
    /** The key map of the selected preset: the transposition of each key. */
    public static final int    KEY_MAP_TRANSPOSE       = 0x086;
    /** The record of the selected preset. */
    public static final int    SELECTED_PRESET         = 0x2AB;
    /** The voice list of the bank: the identifier of each of its up to 100 voice numbers. */
    public static final int    VOICE_LIST              = 0x2CF;
    /** The number of entries of the voice list. */
    public static final int    VOICE_LIST_SIZE         = 100;
    /** The identifier of the first voice record; an identifier below it means 'no voice'. */
    public static final int    VOICE_ID_BASE           = 0x9B;

    /** The array of voice records. */
    public static final int    VOICE_TABLE             = 0x500;
    /** The size of one voice record. */
    public static final int    VOICE_SIZE              = 0x100;
    /** The largest number of voices a bank holds. */
    public static final int    MAX_VOICES              = 100;
    /** The two bytes which start every voice record. */
    public static final int    VOICE_TAG_1             = 0x04;
    /** The second tag byte. */
    public static final int    VOICE_TAG_2             = 0x03;
    /** The start of the audio less one, 24 bit little-endian, which mirrors the sample start. */
    public static final int    VOICE_START_MINUS_ONE   = 0x02;
    /** The flags of the voice. */
    public static final int    VOICE_FLAGS             = 0xC6;
    /** The loop of the voice is switched on. */
    public static final int    VOICE_FLAG_LOOP         = 0x02;
    /** The name of the voice, ASCII, space padded. */
    public static final int    VOICE_NAME              = 0xBA;
    /** The length of the name of a voice. */
    public static final int    VOICE_NAME_LENGTH       = 12;
    /** The start of the audio of a voice, relative to the bank, 24 bit little-endian. */
    public static final int    VOICE_SAMPLE_START      = 0xC7;
    /** The size of the memory slot of the voice, which holds the audio, the loop and 4 bytes. */
    public static final int    VOICE_SLOT_SIZE         = 0xCA;
    /** The end of the audio of a voice. */
    public static final int    VOICE_SAMPLE_END        = 0xCD;
    /** The length of the loop of a voice in frames. */
    public static final int    VOICE_LOOP_LENGTH       = 0xD0;
    /** The start of the loop of a voice, relative to the bank. */
    public static final int    VOICE_LOOP_START        = 0xD3;
    /** The bytes of a slot behind the loop. */
    public static final int    VOICE_SLOT_PADDING      = 4;

    /** A preset record starts with a 9 byte header, whose last byte has its top bit set. */
    public static final int    PRESET_HEADER_SIZE      = 9;
    /** The name of a preset follows the header. */
    public static final int    PRESET_NAME_OFFSET      = PRESET_HEADER_SIZE;
    /** The length of the name of a preset. */
    public static final int    PRESET_NAME_LENGTH      = 12;
    /** The parameters of a preset follow its name. */
    public static final int    PRESET_PARAMETERS_SIZE  = 14;
    /** The key range entries of a preset follow its parameters. */
    public static final int    PRESET_ENTRIES_OFFSET   = PRESET_HEADER_SIZE + PRESET_NAME_LENGTH + PRESET_PARAMETERS_SIZE;
    /** The size of a key range entry. */
    public static final int    ENTRY_SIZE              = 5;
    /** The size of the secondary voice which follows a key range entry in the dual mode. */
    public static final int    ENTRY_SECONDARY_SIZE    = 3;
    /** The key range entry: the number of keys in the lower 6 bits, the mode in the upper 2. */
    public static final int    ENTRY_COUNT_MASK        = 0x3F;
    /** The mode of an entry: 0 ends the list, 1 is silent, 2 plays a voice, 3 plays two. */
    public static final int    ENTRY_MODE_SHIFT        = 6;
    /** The mode which ends the list of entries. */
    public static final int    ENTRY_MODE_END          = 0;
    /** The mode of an entry which plays two voices. */
    public static final int    ENTRY_MODE_DUAL         = 3;
    /** The voice number of an entry, 1 based; 0 means silent. */
    public static final int    ENTRY_VOICE             = 2;
    /** The transposition of the first key of an entry. */
    public static final int    ENTRY_TRANSPOSE         = 3;
    /** The level of an entry. */
    public static final int    ENTRY_LEVEL             = 4;
    /** The list of entries ends with a 4 byte end marker followed by the length of the next record. */
    public static final int    ENTRY_END_SIZE          = 4;

    /** The Emulator II plays back at this fixed rate and cannot vary it. */
    public static final int    SAMPLE_RATE             = 27777;


    /**
     * Private constructor since this is a utility class.
     */
    private Emulator2Constants ()
    {
        // Intentionally empty
    }
}
