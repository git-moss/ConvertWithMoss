// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.util.Arrays;


/**
 * Encoder for the FM track format which the E-mu Emulator and the Emulator II write, the
 * counterpart of {@link EmuFmDecoder}. It turns the 3584 data bytes of a track into the raw HFE
 * bit-stream of one side of the track: a gap, a sync, the mark, the track number with its CRC, a
 * sync, a gap, a sync, the mark, the data with its CRC, a sync and a closing gap which fills the
 * rest of the track.
 * <p>
 * The two samplers space the fields of a track slightly differently, which is described by a
 * {@link TrackLayout}. The layout of the Emulator follows the description of ///Esynthesist and is
 * what the machine writes itself; the layout of the Emulator II is the one of the images which the
 * HxC tools generate for it - which put the data mark half a byte earlier than the published
 * description - since those are the images which the Emulator II is known to read.
 *
 * @author Jürgen Moßgraber
 */
public class EmuFmEncoder
{
    /**
     * The spacing of the fields of a track. All counts are bytes except for the gap and the sync in
     * front of the data mark, which are given in bit cells because the Emulator II uses seven and a
     * half bytes of gap there.
     *
     * @param leadGapBytes The gap of 0xFF bytes at the start of the track
     * @param leadSyncBytes The sync of 0x00 bytes in front of the header mark
     * @param headerSyncBytes The sync of 0x00 bytes behind the CRC of the header
     * @param midGapCells The gap of one bits between the header and the data field
     * @param dataSyncCells The sync of zero bits in front of the data mark
     * @param tailSyncBytes The sync of 0x00 bytes behind the CRC of the data
     */
    public record TrackLayout (int leadGapBytes, int leadSyncBytes, int headerSyncBytes, int midGapCells, int dataSyncCells, int tailSyncBytes)
    {
        // Intentionally empty
    }


    /** The layout which the Emulator writes: 2 sync bytes, a gap of 7 and a sync of 4 bytes. */
    public static final TrackLayout LAYOUT_EMULATOR    = new TrackLayout (20, 4, 2, 56, 32, 2);
    /** The layout of the Emulator II images: 1 sync byte, a gap of 7.5 and a sync of 4 bytes. */
    public static final TrackLayout LAYOUT_EMULATOR_II = new TrackLayout (20, 4, 1, 60, 32, 2);

    private static final int        GAP_BYTE           = 0xFF;
    private static final int        SYNC_BYTE          = 0x00;


    /**
     * Private constructor since this is a utility class.
     */
    private EmuFmEncoder ()
    {
        // Intentionally empty
    }


    /**
     * Encode one track.
     *
     * @param data The data of the sector of the track, exactly {@link EmuFmDecoder#SECTOR_SIZE}
     *            bytes
     * @param trackNumber The number of the track as the sampler counts it, which is written into
     *            the header
     * @param layout The spacing of the fields
     * @param streamLength The length of the bit-stream of the side in bytes; the closing gap fills
     *            it up
     * @return The raw HFE bit-stream of the track
     */
    public static byte [] encodeTrack (final byte [] data, final int trackNumber, final TrackLayout layout, final int streamLength)
    {
        if (data.length != EmuFmDecoder.SECTOR_SIZE)
            throw new IllegalArgumentException ("A track holds exactly " + EmuFmDecoder.SECTOR_SIZE + " bytes.");

        final CellWriter writer = new CellWriter (streamLength);
        writer.writeBytes (GAP_BYTE, layout.leadGapBytes ());
        writer.writeBytes (SYNC_BYTE, layout.leadSyncBytes ());
        writer.writeMark ();
        final byte [] header =
        {
            (byte) trackNumber
        };
        writer.writeBytes (header);
        writer.writeCrc (header);
        writer.writeBytes (SYNC_BYTE, layout.headerSyncBytes ());
        writer.writeCells (true, layout.midGapCells ());
        writer.writeCells (false, layout.dataSyncCells ());
        writer.writeMark ();
        writer.writeBytes (data);
        writer.writeCrc (data);
        writer.writeBytes (SYNC_BYTE, layout.tailSyncBytes ());
        writer.fillWithGap ();
        return writer.getStream ();
    }


    /** Writes bit cells into the bit-stream of one side of a track. */
    private static class CellWriter
    {
        private final byte [] stream;
        private int           slot = 0;


        /**
         * Constructor.
         *
         * @param streamLength The length of the bit-stream in bytes
         */
        CellWriter (final int streamLength)
        {
            this.stream = new byte [streamLength];
        }


        /**
         * Write the same byte several times.
         *
         * @param value The byte
         * @param count How often to write it
         */
        void writeBytes (final int value, final int count)
        {
            for (int i = 0; i < count; i++)
                this.writeByte (value);
        }


        /**
         * Write bytes.
         *
         * @param bytes The bytes
         */
        void writeBytes (final byte [] bytes)
        {
            for (final byte b: bytes)
                this.writeByte (b & 0xFF);
        }


        /**
         * Write the mark.
         */
        void writeMark ()
        {
            this.writeByte (EmuFmDecoder.MARK_FIRST);
            this.writeByte (EmuFmDecoder.MARK_SECOND);
        }


        /**
         * Write the CRC of some bytes, least significant byte first.
         *
         * @param bytes The bytes to calculate the CRC of
         */
        void writeCrc (final byte [] bytes)
        {
            final int crc = AbstractDecoder.calculateCrcLsbFirst (bytes);
            this.writeByte (crc & 0xFF);
            this.writeByte (crc >> 8 & 0xFF);
        }


        /**
         * Write one byte, least significant bit first.
         *
         * @param value The byte
         */
        void writeByte (final int value)
        {
            for (int bit = 0; bit < 8; bit++)
                this.writeCell ((value >> bit & 1) != 0);
        }


        /**
         * Write the same bit several times.
         *
         * @param one True to write one bits
         * @param count The number of cells
         */
        void writeCells (final boolean one, final int count)
        {
            for (int i = 0; i < count; i++)
                this.writeCell (one);
        }


        /**
         * Fill the rest of the stream with one bits, which is the gap that runs to the index hole.
         */
        void fillWithGap ()
        {
            while (this.slot + EmuFmDecoder.SLOTS_PER_CELL <= this.stream.length * 8)
                this.writeCell (true);
        }


        /**
         * Write one bit cell: the clock pulse and, for a one bit, the data pulse.
         *
         * @param one The bit
         */
        void writeCell (final boolean one)
        {
            this.setSlot (this.slot + EmuFmDecoder.CLOCK_SLOT);
            if (one)
                this.setSlot (this.slot + EmuFmDecoder.DATA_SLOT);
            this.slot += EmuFmDecoder.SLOTS_PER_CELL;
        }


        /**
         * Set a pulse at a slot. The bits of the stream are ordered least significant bit first.
         *
         * @param position The slot
         */
        private void setSlot (final int position)
        {
            if (position < this.stream.length * 8)
                this.stream[position / 8] |= (byte) (1 << position % 8);
        }


        /**
         * Get the bit-stream.
         *
         * @return The stream
         */
        byte [] getStream ()
        {
            return Arrays.copyOf (this.stream, this.stream.length);
        }
    }
}
