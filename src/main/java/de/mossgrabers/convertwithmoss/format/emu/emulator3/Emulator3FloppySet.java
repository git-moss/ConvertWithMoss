// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator3;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.INotifier;


/**
 * Assembles the floppy disks of the EIIIX and ESI samplers into a bank. The samplers save a bank
 * onto one or more 1.44 MB floppy disks, each of which starts with a 512 byte disk header that
 * holds the bank identifier and name plus the number of the disk and the size of the set. The
 * payload behind the headers is not a bank file but a dump of the memory of the sampler: the header
 * and the address tables of the bank are stored big-endian, the addresses are memory addresses
 * instead of file positions and the sample headers are kept in a separate table of 92 byte slots
 * behind the sample data instead of in front of each sample. This class concatenates the payloads
 * of the disks of a set and converts them into a little-endian bank so that the result can be
 * parsed like a bank file. The layout was derived from the floppy sets of the E-mu and Sweetwater
 * sound libraries; see documentation/design/EIII_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator3FloppySet
{
    /** The size of a raw image of a 3.5" HD floppy disk. */
    public static final int   FLOPPY_SIZE                   = 1474560;

    /** The offset of the number of the disk in the disk header. */
    private static final int  DISK_NUMBER                   = 0x28;
    /** The offset of the number of disks of the set in the disk header. */
    private static final int  TOTAL_DISKS                   = 0x2C;
    /** The size of the disk header at the start of every disk of a set. */
    private static final int  DISK_HEADER_SIZE              = 0x200;
    /** The largest number of disks a set is accepted to span. */
    private static final int  MAX_DISKS                     = 99;

    /**
     * The offset of a structure in a bank file minus its offset in the floppy payload: the payload
     * starts with the bank name (which a bank file stores at 0x10) followed by 16 bytes of fields
     * and then continues with the content behind the bank file header (which starts at 0x6C).
     */
    private static final int  RECORD_SHIFT                  = 0x4C;

    /**
     * The mask of the sample memory addresses. The samplers set bit 26 on addresses of their second
     * memory bank; the masked address is the position in the sample data region.
     */
    private static final long SAMPLE_ADDRESS_MASK           = 0x3FFFFFF;

    /**
     * The size of the sample header table: one 92 byte slot for each of the 999 samples plus slot
     * 0.
     */
    private static final int  HEADER_TABLE_SIZE             = (Emulator3Constants.SAMPLE_HEADER_SIZE * 1000 + Emulator3Constants.BLOCK_SIZE - 1) / Emulator3Constants.BLOCK_SIZE * Emulator3Constants.BLOCK_SIZE;

    // The option flags of a sample header on a floppy, which differ from the ones of a bank file
    private static final int  FLOPPY_OPTION_CHANNEL_LEFT    = 0x02;
    private static final int  FLOPPY_OPTION_CHANNEL_RIGHT   = 0x04;
    private static final int  FLOPPY_OPTION_LOOP_IN_RELEASE = 0x08;
    private static final int  FLOPPY_OPTION_LOOP            = 0x40;


    /**
     * Private constructor since this is a utility class.
     */
    private Emulator3FloppySet ()
    {
        // Intentionally empty
    }


    /**
     * Check whether the data is a floppy disk of a bank set. A disk has the size of a raw floppy
     * image and carries the disk header fields big-endian, while a bank file stores the same region
     * little-endian, which tells the two apart.
     *
     * @param data The content of the file
     * @return True if it is a floppy disk
     */
    public static boolean isFloppyDisk (final byte [] data)
    {
        if (data.length != FLOPPY_SIZE)
            return false;
        final long diskNumber = getU32BE (data, DISK_NUMBER);
        final long totalDisks = getU32BE (data, TOTAL_DISKS);
        // The field in front of the disk number is always 1
        return getU32BE (data, 0x24) == 1 && diskNumber >= 1 && diskNumber <= totalDisks && totalDisks <= MAX_DISKS;
    }


    /**
     * Get the number of the disk in its set.
     *
     * @param data The content of the disk
     * @return The 1-based number of the disk
     */
    public static int getDiskNumber (final byte [] data)
    {
        return (int) getU32BE (data, DISK_NUMBER);
    }


    /**
     * Get the number of disks of the set.
     *
     * @param data The content of the disk
     * @return The number of disks
     */
    public static int getTotalDisks (final byte [] data)
    {
        return (int) getU32BE (data, TOTAL_DISKS);
    }


    /**
     * Check whether a file is a disk of the same set as the given first disk with the given disk
     * number. The disks of a set share the identifier and the bank name of their disk headers; the
     * file names cannot be used since the sets in the wild number their files in different ways.
     *
     * @param firstDisk The content of the first disk of the set
     * @param candidate The first {@link #DISK_HEADER_SIZE} bytes of the candidate file
     * @param diskNumber The 1-based number of the wanted disk
     * @return True if the candidate is the wanted disk
     */
    public static boolean isContinuationDisk (final byte [] firstDisk, final byte [] candidate, final int diskNumber)
    {
        if (candidate.length < DISK_HEADER_SIZE || !isFloppyDisk0 (candidate) || getDiskNumber (candidate) != diskNumber || getTotalDisks (candidate) != getTotalDisks (firstDisk))
            return false;
        // Identifier and bank name must match
        for (int i = 0; i < 2 * Emulator3Constants.NAME_LENGTH; i++)
            if (firstDisk[i] != candidate[i])
                return false;
        return true;
    }


    /**
     * The header part of {@link #isFloppyDisk(byte[])} for a candidate of which only the header was
     * read.
     *
     * @param header The first bytes of the file, at least {@link #DISK_HEADER_SIZE}
     * @return True if the header is the one of a floppy disk
     */
    private static boolean isFloppyDisk0 (final byte [] header)
    {
        final long diskNumber = getU32BE (header, DISK_NUMBER);
        final long totalDisks = getU32BE (header, TOTAL_DISKS);
        return getU32BE (header, 0x24) == 1 && diskNumber >= 1 && diskNumber <= totalDisks && totalDisks <= MAX_DISKS;
    }


    /**
     * Convert the disks of a set into a little-endian bank which can be parsed like a bank file.
     *
     * @param disks The content of the disks of the set, ordered by their disk number
     * @param bankFormat The format of the bank, must not be the compact Emulator III format
     * @param bankName The name of the bank for error messages
     * @param notifier Where to report malformed samples
     * @return The bank or null if the payload cannot be a bank
     */
    public static Optional<byte []> createBank (final byte [] [] disks, final Emulator3BankFormat bankFormat, final String bankName, final INotifier notifier)
    {
        // Concatenate the payloads of the disks
        final int payloadSize = FLOPPY_SIZE - DISK_HEADER_SIZE;
        final byte [] stream = new byte [disks.length * payloadSize];
        for (int i = 0; i < disks.length; i++)
            System.arraycopy (disks[i], DISK_HEADER_SIZE, stream, i * payloadSize, payloadSize);

        final int presetTable = bankFormat.getPresetTableOffset () - RECORD_SHIFT;
        final int presetArea = bankFormat.getPresetAreaOffset () - RECORD_SHIFT;
        final int maxPresets = bankFormat.getMaxPresets ();
        final int maxSamples = bankFormat.getMaxSamples ();

        // The preset address table holds memory addresses; the first entry is the address of the
        // preset area, which turns the entries into the offsets a bank file stores
        final long [] presetEntries = new long [maxPresets + 1];
        for (int i = 0; i <= maxPresets; i++)
        {
            presetEntries[i] = getU32BE (stream, presetTable + i * 4);
            if (i > 0 && presetEntries[i] < presetEntries[i - 1])
                return Optional.empty ();
        }
        final long presetAreaSize = presetEntries[maxPresets] - presetEntries[0];
        if (presetArea + presetAreaSize + 1 > stream.length)
            return Optional.empty ();

        // The payload is organized in 512 byte blocks: the region up to the end of the presets is
        // followed by the sample data and the table of the 92 byte sample headers. The EIIIX
        // puts the sample data first, the ESI samplers the header table.
        final int recordBlocks = blockAligned (presetArea + presetAreaSize + 1);
        final int sampleTable = bankFormat.getSampleTableOffset () - RECORD_SHIFT;
        final long [] sampleEntries = new long [maxSamples + 1];
        for (int i = 0; i <= maxSamples; i++)
            sampleEntries[i] = getU32BE (stream, sampleTable + i * 4);
        final long pcmSize = sampleEntries[maxSamples] & SAMPLE_ADDRESS_MASK;
        final int headerTableOffset;
        final int pcmOffset;
        if (bankFormat == Emulator3BankFormat.ESI_32_V3)
        {
            headerTableOffset = recordBlocks;
            pcmOffset = recordBlocks + HEADER_TABLE_SIZE;
        }
        else
        {
            pcmOffset = recordBlocks;
            headerTableOffset = recordBlocks + blockAligned (pcmSize);
        }
        if (headerTableOffset < 0 || headerTableOffset + (maxSamples + 1) * Emulator3Constants.SAMPLE_HEADER_SIZE > stream.length)
            return Optional.empty ();

        // Collect the samples; a slot whose header flags neither channel is a deleted leftover
        final FloppySample [] samples = new FloppySample [maxSamples + 1];
        long sampleAreaSize = 0;
        for (int i = 1; i <= maxSamples; i++)
        {
            if (sampleEntries[i - 1] == 0)
                continue;
            final FloppySample sample = readSampleHeader (stream, headerTableOffset, pcmOffset, i, bankName, notifier);
            if (sample != null)
            {
                samples[i] = sample;
                sampleAreaSize += Emulator3Constants.SAMPLE_HEADER_SIZE + sample.getDataSize ();
            }
        }

        // Assemble the bank
        final long bankSize = bankFormat.getPresetAreaOffset () + presetAreaSize + 1 + sampleAreaSize;
        if (bankSize > Integer.MAX_VALUE)
            return Optional.empty ();
        final byte [] bank = new byte [(int) bankSize];
        final byte [] identifier = bankFormat.getIdentifier ().getBytes (StandardCharsets.US_ASCII);
        System.arraycopy (identifier, 0, bank, 0, identifier.length);
        System.arraycopy (stream, 0, bank, Emulator3Constants.BANK_NAME, Emulator3Constants.NAME_LENGTH);
        System.arraycopy (stream, 0, bank, Emulator3Constants.BANK_NAME_COPY, Emulator3Constants.NAME_LENGTH);
        for (int i = 0; i <= maxPresets; i++)
            Emulator3Constants.putU32 (bank, bankFormat.getPresetTableOffset () + i * 4, presetEntries[i] - presetEntries[0]);
        System.arraycopy (stream, presetArea, bank, bankFormat.getPresetAreaOffset (), (int) presetAreaSize);
        bank[bankFormat.getPresetAreaOffset () + (int) presetAreaSize] = (byte) bankFormat.getSampleAreaMarker ();

        // The flags of a zone are stored with their bits mirrored on a floppy: the flag which a
        // bank file keeps in bit 0 sits in bit 7 and so on. Without the mirroring the flag which
        // almost every zone sets would read as 'mute the right channel' and turn the stereo
        // presets mono - the zones of the 'Stereo Grand' library are the proof, they all carry it
        for (int i = 0; i < maxPresets; i++)
        {
            if (presetEntries[i] == presetEntries[i + 1])
                continue;
            mirrorZoneFlags (bank, bankFormat.getPresetAreaOffset () + (int) (presetEntries[i] - presetEntries[0]));
        }

        final int sampleArea = bankFormat.getPresetAreaOffset () + (int) presetAreaSize + 1;
        int writeOffset = sampleArea;
        for (int i = 1; i <= maxSamples; i++)
        {
            final FloppySample sample = samples[i];
            if (sample == null)
                continue;
            Emulator3Constants.putU32 (bank, bankFormat.getSampleTableOffset () + (i - 1) * 4, writeOffset - sampleArea + (long) Emulator3Constants.SAMPLE_ADDRESS_OFFSET);
            writeOffset = sample.write (stream, bank, writeOffset);
        }
        Emulator3Constants.putU32 (bank, bankFormat.getSampleTableOffset () + maxSamples * 4, writeOffset - sampleArea + (long) Emulator3Constants.SAMPLE_ADDRESS_OFFSET);
        return Optional.of (bank);
    }


    /**
     * Mirror the bits of the flag byte of all zones of a preset.
     *
     * @param bank The assembled bank
     * @param presetOffset The offset of the preset
     */
    private static void mirrorZoneFlags (final byte [] bank, final int presetOffset)
    {
        if (presetOffset + Emulator3Constants.PRESET_SIZE > bank.length)
            return;
        final int numNoteZones = bank[presetOffset + Emulator3Constants.PRESET_NUM_NOTE_ZONES] & 0xFF;
        final int noteZoneOffset = presetOffset + Emulator3Constants.PRESET_SIZE;
        final int zoneOffset = noteZoneOffset + numNoteZones * Emulator3Constants.NOTE_ZONE_SIZE;

        // The zone array is only as long as the highest index a note zone references
        int maxZone = -1;
        for (int i = 0; i < numNoteZones && noteZoneOffset + (i + 1) * Emulator3Constants.NOTE_ZONE_SIZE <= bank.length; i++)
        {
            final int primary = bank[noteZoneOffset + i * Emulator3Constants.NOTE_ZONE_SIZE + Emulator3Constants.NOTE_ZONE_PRIMARY] & 0xFF;
            final int secondary = bank[noteZoneOffset + i * Emulator3Constants.NOTE_ZONE_SIZE + Emulator3Constants.NOTE_ZONE_SECONDARY] & 0xFF;
            if (primary != Emulator3Constants.UNUSED)
                maxZone = Math.max (maxZone, primary);
            if (secondary != Emulator3Constants.UNUSED)
                maxZone = Math.max (maxZone, secondary);
        }
        for (int i = 0; i <= maxZone; i++)
        {
            final int flagsOffset = zoneOffset + i * Emulator3Constants.ZONE_SIZE + Emulator3Constants.ZONE_FLAGS;
            if (flagsOffset >= bank.length)
                break;
            bank[flagsOffset] = (byte) (Integer.reverse (bank[flagsOffset] & 0xFF) >>> 24);
        }
    }


    /**
     * Read one slot of the sample header table.
     *
     * @param stream The concatenated payload of the disks
     * @param headerTableOffset The offset of the sample header table
     * @param pcmOffset The offset of the sample data region
     * @param sampleIndex The 1-based index of the sample
     * @param bankName The name of the bank for error messages
     * @param notifier Where to report a malformed sample
     * @return The sample or null if the slot is empty or malformed
     */
    private static FloppySample readSampleHeader (final byte [] stream, final int headerTableOffset, final int pcmOffset, final int sampleIndex, final String bankName, final INotifier notifier)
    {
        final int header = headerTableOffset + sampleIndex * Emulator3Constants.SAMPLE_HEADER_SIZE;
        final int flags = stream[header + Emulator3Constants.SAMPLE_OPTIONS] & 0xFF;
        final boolean hasLeft = (flags & FLOPPY_OPTION_CHANNEL_LEFT) > 0;
        final boolean hasRight = (flags & FLOPPY_OPTION_CHANNEL_RIGHT) > 0;
        if (!hasLeft && !hasRight)
            return null;

        final FloppySample sample = new FloppySample ();
        sample.headerOffset = header;
        sample.isStereo = hasLeft && hasRight;

        // The positions are byte offsets relative to the start of the sample data of the channel;
        // a channel which is not flagged holds stale values and must not be read
        final int fieldOffset = hasLeft ? 0 : 4;
        long dataSize = getU32BE (stream, header + Emulator3Constants.SAMPLE_END_LEFT + fieldOffset);
        if (sample.isStereo)
            dataSize = Math.min (dataSize, getU32BE (stream, header + Emulator3Constants.SAMPLE_END_RIGHT));
        sample.channelSize = (int) (dataSize / 2) * 2;
        sample.sampleRate = getU32BE (stream, header + Emulator3Constants.SAMPLE_RATE);
        sample.playbackRate = getU16BE (stream, header + Emulator3Constants.SAMPLE_PLAYBACK_RATE);
        sample.leftOffset = pcmOffset + (getU32BE (stream, header + Emulator3Constants.SAMPLE_DATA_OFFSET_LEFT) & SAMPLE_ADDRESS_MASK);
        sample.rightOffset = pcmOffset + (getU32BE (stream, header + Emulator3Constants.SAMPLE_DATA_OFFSET_RIGHT) & SAMPLE_ADDRESS_MASK);
        sample.hasLeft = hasLeft;

        final long primaryOffset = hasLeft ? sample.leftOffset : sample.rightOffset;
        if (sample.channelSize <= 0 || sample.sampleRate <= 0 || primaryOffset + sample.channelSize > stream.length || sample.isStereo && sample.rightOffset + sample.channelSize > stream.length)
        {
            notifier.logError ("IDS_EIII_MALFORMED_SAMPLE", Integer.toString (sampleIndex), bankName);
            return null;
        }

        if ((flags & FLOPPY_OPTION_LOOP) > 0)
        {
            final long loopStart = getU32BE (stream, header + Emulator3Constants.SAMPLE_LOOP_START_LEFT + fieldOffset);
            final long loopEnd = getU32BE (stream, header + Emulator3Constants.SAMPLE_LOOP_END_LEFT + fieldOffset);
            if (loopStart >= 0 && loopStart < loopEnd && loopEnd <= sample.channelSize)
            {
                sample.hasLoop = true;
                sample.loopStart = (int) loopStart;
                sample.loopEnd = (int) loopEnd;
                sample.loopInRelease = (flags & FLOPPY_OPTION_LOOP_IN_RELEASE) > 0;
            }
        }
        return sample;
    }


    /** One sample of a floppy set on its way into the assembled bank. */
    private static class FloppySample
    {
        private int     headerOffset;
        private boolean isStereo;
        private boolean hasLeft;
        private int     channelSize;
        private long    sampleRate;
        private int     playbackRate;
        private long    leftOffset;
        private long    rightOffset;
        private boolean hasLoop;
        private int     loopStart;
        private int     loopEnd;
        private boolean loopInRelease;


        /**
         * Get the number of bytes of the sample data of all channels.
         *
         * @return The number of bytes
         */
        private long getDataSize ()
        {
            return (long) this.channelSize * (this.isStereo ? 2 : 1);
        }


        /**
         * Write the sample as a bank file stores it: the header followed by the data of the left
         * and then the right channel with the 16 bit values converted to little-endian.
         *
         * @param stream The concatenated payload of the disks
         * @param bank The bank to write to
         * @param offset The offset to write to
         * @return The offset behind the written sample
         */
        private int write (final byte [] stream, final byte [] bank, final int offset)
        {
            System.arraycopy (stream, this.headerOffset, bank, offset, Emulator3Constants.NAME_LENGTH);

            // The positions of a bank file are relative to the start of the sample header; the
            // end fields hold the position of the last frame of their channel
            final int headerSize = Emulator3Constants.SAMPLE_HEADER_SIZE;
            final boolean writeLeft = this.hasLeft || this.isStereo;
            final boolean writeRight = !this.hasLeft || this.isStereo;
            Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_START_LEFT, writeLeft ? headerSize : 0);
            Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_START_RIGHT, this.isStereo ? (long) headerSize + this.channelSize : writeRight ? headerSize : 0);
            Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_END_LEFT, writeLeft ? (long) headerSize + this.channelSize - 2 : 0);
            Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_END_RIGHT, this.isStereo ? headerSize + 2L * this.channelSize - 2 : writeRight ? (long) headerSize + this.channelSize - 2 : 0);
            if (this.hasLoop)
            {
                // The loop end of a floppy already points behind the last frame of the loop while
                // a bank file stores the frame before the last one: sweeping the offset over the
                // loops of the floppy sets shows the clean seam without the +1 of the file parser
                Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_LOOP_START_LEFT, (long) headerSize + this.loopStart);
                Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_LOOP_START_RIGHT, (long) headerSize + this.loopStart);
                Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_LOOP_END_LEFT, (long) headerSize + this.loopEnd - 2);
                Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_LOOP_END_RIGHT, (long) headerSize + this.loopEnd - 2);
            }
            Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_RATE, this.sampleRate);
            Emulator3Constants.putU16 (bank, offset + Emulator3Constants.SAMPLE_PLAYBACK_RATE, this.playbackRate);
            int options = 0x10;
            if (writeLeft)
                options |= Emulator3Constants.OPTION_CHANNEL_LEFT;
            if (writeRight)
                options |= Emulator3Constants.OPTION_CHANNEL_RIGHT;
            if (this.hasLoop)
                options |= Emulator3Constants.OPTION_LOOP;
            if (this.loopInRelease)
                options |= Emulator3Constants.OPTION_LOOP_IN_RELEASE;
            Emulator3Constants.putU16 (bank, offset + Emulator3Constants.SAMPLE_OPTIONS, options);
            Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_DATA_OFFSET_LEFT, writeLeft ? headerSize : 0);
            Emulator3Constants.putU32 (bank, offset + Emulator3Constants.SAMPLE_DATA_OFFSET_RIGHT, this.isStereo ? (long) headerSize + this.channelSize : writeRight ? headerSize : 0);

            // The 16 bit sample values of a floppy are big-endian
            int writeOffset = offset + headerSize;
            writeOffset = copySwapped (stream, (int) this.leftOffset, bank, writeOffset, this.hasLeft || this.isStereo ? this.channelSize : 0);
            writeOffset = copySwapped (stream, (int) this.rightOffset, bank, writeOffset, this.hasLeft ? this.isStereo ? this.channelSize : 0 : this.channelSize);
            return writeOffset;
        }


        /**
         * Copy sample data and swap the bytes of each 16 bit value.
         *
         * @param source The data to copy from
         * @param sourceOffset The offset to copy from
         * @param destination The data to copy to
         * @param destinationOffset The offset to copy to
         * @param length The number of bytes to copy
         * @return The offset behind the copied data
         */
        private static int copySwapped (final byte [] source, final int sourceOffset, final byte [] destination, final int destinationOffset, final int length)
        {
            for (int i = 0; i < length; i += 2)
            {
                destination[destinationOffset + i] = source[sourceOffset + i + 1];
                destination[destinationOffset + i + 1] = source[sourceOffset + i];
            }
            return destinationOffset + length;
        }
    }


    /**
     * Round a size up to full 512 byte blocks.
     *
     * @param size The size in bytes
     * @return The size rounded up
     */
    private static int blockAligned (final long size)
    {
        return (int) ((size + Emulator3Constants.BLOCK_SIZE - 1) / Emulator3Constants.BLOCK_SIZE * Emulator3Constants.BLOCK_SIZE);
    }


    /**
     * Read an unsigned 32 bit big-endian value.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The value
     */
    private static long getU32BE (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFFL) << 24 | (data[offset + 1] & 0xFFL) << 16 | (data[offset + 2] & 0xFFL) << 8 | data[offset + 3] & 0xFFL;
    }


    /**
     * Read an unsigned 16 bit big-endian value.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The value
     */
    private static int getU16BE (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }
}
