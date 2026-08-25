// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator1;

import de.mossgrabers.convertwithmoss.file.hfe.EmuFmDecoder;


/**
 * Constants of the disks of the E-mu Emulator, the first Emulator of 1981. See
 * documentation/design/EMULATOR1_FORMAT.md for how these were established.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator1Constants
{
    /** The disk has 35 tracks on one side. */
    public static final int      NUM_TRACKS            = 35;
    /** The size of one track. */
    public static final int      TRACK_SIZE            = EmuFmDecoder.SECTOR_SIZE;
    /** The size of a raw disk image. */
    public static final int      IMAGE_SIZE            = NUM_TRACKS * TRACK_SIZE;
    /** The operating system occupies the first two tracks. */
    public static final int      OS_SIZE               = 2 * TRACK_SIZE;
    /** The lower bank starts at track 2. */
    public static final int      LOWER_BANK_OFFSET     = 2 * TRACK_SIZE;
    /** The upper bank starts at track 18. */
    public static final int      UPPER_BANK_OFFSET     = 18 * TRACK_SIZE;
    /** A bank occupies 16 tracks: the header and the sample memory of one half of the keyboard. */
    public static final int      BANK_SIZE             = 16 * TRACK_SIZE;
    /** The last track holds the memory of the sequencer. */
    public static final int      SEQUENCER_OFFSET      = 34 * TRACK_SIZE;

    /** The memory address at which a bank is loaded, all addresses in a bank are relative to it. */
    public static final int      BANK_ADDRESS          = 0x2000;
    /** The size of the header of a bank. */
    public static final int      HEADER_SIZE           = 0x100;
    /** The memory address of the first sample byte of a bank. */
    public static final int      SAMPLE_MEMORY_START   = BANK_ADDRESS + HEADER_SIZE;
    /** The memory address behind the last sample byte of a bank. */
    public static final int      SAMPLE_MEMORY_END     = 0x10000;
    /** The size of the sample memory of a bank. */
    public static final int      SAMPLE_MEMORY_SIZE    = SAMPLE_MEMORY_END - SAMPLE_MEMORY_START;
    /** Samples are stored at addresses which are multiples of this. */
    public static final int      SAMPLE_ALIGNMENT      = 4;
    /** The bytes behind the end of the audio of a sample up to the end which its record stores. */
    public static final int      SAMPLE_END_GUARD      = 3;

    /** The records of the header are 16 bytes long. */
    public static final int      RECORD_SIZE           = 16;
    /** The record of the sample which is selected, in front of the records of the zones. */
    public static final int      RECORD_SELECTED       = 0;
    /** The first record of a zone. */
    public static final int      RECORD_FIRST_ZONE     = 0x10;
    /** The largest number of zones a bank holds, which fills the records up to the copyright. */
    public static final int      MAX_ZONES             = 8;
    /** The pitch table of the zones, one entry per key. */
    public static final int      TABLE_OFFSET          = 0xC0;
    /** The table holds an entry for each key of a half of the keyboard and one more. */
    public static final int      TABLE_ENTRIES         = 25;

    /** The flags of a record. */
    public static final int      RECORD_FLAGS          = 0;
    /** The bank is a multi-sample bank with zone records; otherwise it holds one sample. */
    public static final int      FLAG_MULTISAMPLE      = 0x10;
    /** The loop of the selected sample is switched off. */
    public static final int      FLAG_LOOP_OFF         = 0x04;
    /** The filter setting of a record in its upper 3 bits, 0 is fully open. */
    public static final int      RECORD_FILTER         = 1;
    /** The address of the pitch table, 16 bit little-endian, in the records of the zones. */
    public static final int      RECORD_TABLE_ADDRESS  = 2;
    /** The address of the first sample byte, 16 bit little-endian. */
    public static final int      RECORD_SAMPLE_START   = 4;
    /** The loop start relative to the sample start, less one. */
    public static final int      RECORD_LOOP_START_REL = 6;
    /** The address of the loop start. */
    public static final int      RECORD_LOOP_START     = 8;
    /** The length of the loop, less one. */
    public static final int      RECORD_LOOP_LENGTH    = 10;
    /** The address of the loop end. */
    public static final int      RECORD_LOOP_END       = 12;
    /** The number of bytes from the loop end to the end of the sample. */
    public static final int      RECORD_RELEASE_LENGTH = 14;
    /** A loop which is this short is the marker of a sample which does not loop. */
    public static final int      NO_LOOP_LENGTH        = 2;

    /** The pitch table entry which plays a sample at the rate it was recorded with. */
    public static final int      PITCH_UNITY           = 2656;
    /** The pitch table value is subtracted from this to get the period of the sample clock. */
    public static final int      PITCH_PERIOD_BASE     = 3072;
    /** The period of the sample clock at unity. */
    public static final int      PITCH_UNITY_PERIOD    = PITCH_PERIOD_BASE - PITCH_UNITY;
    /** The shortest period the sample clock can be set to: one octave above unity. */
    public static final int      PITCH_MIN_PERIOD      = PITCH_UNITY_PERIOD / 2;
    /** The pitch value occupies the lower 13 bits of a table entry. */
    public static final int      PITCH_VALUE_MASK      = 0x1FFF;
    /** The upper 3 bits of a table entry hold a code which depends on the position in the zone. */
    public static final int      PITCH_CODE_SHIFT      = 13;

    /** The keyboard has 49 keys, C2 to C6. */
    public static final int      NUM_KEYS              = 49;
    /** The MIDI note of the lowest key. */
    public static final int      LOWEST_KEY            = 36;
    /** The lower half of the keyboard has 24 keys, the upper half 25. */
    public static final int      KEYS_PER_HALF         = 24;
    /** The first key of the upper half. */
    public static final int      SPLIT_KEY             = LOWEST_KEY + KEYS_PER_HALF;
    /** The root key of a single sample bank which has no pitch table, lower half. */
    public static final int      DEFAULT_ROOT_LOWER    = 48;
    /** The root key of a single sample bank which has no pitch table, upper half. */
    public static final int      DEFAULT_ROOT_UPPER    = 72;
    /** The number of zones a half of the keyboard can be divided into. */
    static final int []          ZONE_COUNTS           =
    {
        1,
        2,
        3,
        4,
        6,
        8
    };

    /** The sampler records and plays at this rate when a sample is not transposed. */
    public static final int      SAMPLE_RATE           = 27778;

    /**
     * The cutoff frequencies of the 8 settings of the filter of a sample; the setting 0 leaves the
     * filter open. The frequencies are the ones EMXP assigns to the settings.
     */
    static final double []       FILTER_CUTOFF         =
    {
        -1,
        19300,
        18200,
        14500,
        9000,
        5000,
        2200,
        800
    };

    /** The content of the sequencer track of a disk without a sequence. */
    private static final byte [] SEQUENCER_HEADER      =
    {
        0x08,
        0x04,
        0x02,
        0x00,
        0x0A,
        0x04,
        0x02,
        0x00,
        0x00,
        (byte) 0xFE,
        0x00,
        (byte) 0xFE
    };


    /**
     * Private constructor since this is a utility class.
     */
    private Emulator1Constants ()
    {
        // Intentionally empty
    }


    /**
     * Get the pitch of a key from the value of its pitch table entry. The value is the complement
     * of the period of the sample clock: the clock runs at 27778 Hz when the period is 416 and
     * proportionally faster when it is shorter.
     *
     * @param value The lower 13 bits of the table entry
     * @return The pitch in cents relative to the rate the sample was recorded with
     */
    public static double getPitchCents (final int value)
    {
        final int period = PITCH_PERIOD_BASE - value;
        if (period <= 0)
            return 0;
        return 1200.0 * Math.log ((double) PITCH_UNITY_PERIOD / period) / Math.log (2);
    }


    /**
     * Get the value of a pitch table entry which plays a sample at a pitch.
     *
     * @param cents The pitch in cents relative to the rate the sample was recorded with
     * @return The period, limited to the range of the sampler
     */
    public static int getPitchValue (final double cents)
    {
        final double period = PITCH_UNITY_PERIOD * Math.pow (2, -cents / 1200.0);
        return PITCH_PERIOD_BASE - Math.clamp ((int) Math.round (period), PITCH_MIN_PERIOD, PITCH_PERIOD_BASE - 1);
    }


    /**
     * Get the code in the upper bits of a pitch table entry. It counts down from the lowest key of
     * a zone in groups of four keys and reaches zero at its top; a zone of less than eight keys
     * uses zero throughout.
     *
     * @param zoneSize The number of keys of the zone
     * @param index The index of the key in the zone
     * @return The code
     */
    public static int getPitchCode (final int zoneSize, final int index)
    {
        final int groups = zoneSize / 4 * 4 - 1 - index;
        return Math.max (0, groups / 4);
    }


    /**
     * Get the number of zones which is the smallest one the sampler offers that holds the given
     * number of samples.
     *
     * @param numSamples The number of samples
     * @return The number of zones, at most {@link #MAX_ZONES}
     */
    public static int getZoneCount (final int numSamples)
    {
        for (final int count: ZONE_COUNTS)
            if (count >= numSamples)
                return count;
        return MAX_ZONES;
    }


    /**
     * Get the index of the filter setting which comes closest to a cutoff frequency.
     *
     * @param frequency The cutoff frequency in Hertz
     * @return The setting, 1 to 7
     */
    public static int getFilterSetting (final double frequency)
    {
        int best = 1;
        for (int i = 1; i < FILTER_CUTOFF.length; i++)
            if (Math.abs (Math.log (FILTER_CUTOFF[i] / frequency)) < Math.abs (Math.log (FILTER_CUTOFF[best] / frequency)))
                best = i;
        return best;
    }


    /**
     * Create the content of the sequencer track of a disk which holds no sequence, which is what
     * the factory disks carry.
     *
     * @return The track
     */
    public static byte [] createEmptySequencerTrack ()
    {
        final byte [] track = new byte [TRACK_SIZE];
        System.arraycopy (SEQUENCER_HEADER, 0, track, 0, SEQUENCER_HEADER.length);
        // Behind the settings the memory alternates between blocks of 64 bytes 0xFF and 0x00
        for (int i = 64; i < TRACK_SIZE; i++)
            track[i] = (byte) ((i - 64) / 64 % 2 == 0 ? 0xFF : 0x00);
        return track;
    }
}
