// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.util.Collections;
import java.util.List;


/**
 * Decoder for the FM track format which the E-mu Emulator and the Emulator II write. This is the
 * encoding which HFE files flag as {@link HfeFile#ENCODING_EMU_FM} in combination with the floppy
 * interface mode {@link HfeFile#FLOPPYMODE_EMU_SHUGART}. It is not the IBM System 34 FM format
 * which {@link FmDecoder} implements - none of its assumptions hold here:
 * <ul>
 * <li>The address mark is the byte pair 0xFA 0x96 instead of an ID address mark with a missing
 * clock pattern.</li>
 * <li>A track header consists of a single byte - the track number - instead of the cylinder, head,
 * sector and size quadruple. There is exactly one sector per track and it is always 3584 bytes
 * long, a size which no IBM size code can express.</li>
 * <li>The CRC uses the polynomial 0x8005 with the initial value 0x0000 instead of the CRC-CCITT, it
 * covers only the payload - the track number respectively the sector data - and it is stored least
 * significant byte first.</li>
 * </ul>
 * A track is built up as: a gap of 0xFF bytes, a sync of 0x00 bytes, the mark, the track number,
 * its CRC, a sync, a gap, a sync, the mark, the 3584 bytes of data, their CRC, a sync and a closing
 * gap which runs until the index hole. The Emulator uses a single side with 35 tracks, the Emulator
 * II two sides with 80 tracks each and numbers them linearly across both sides, so its track number
 * is the cylinder times two plus the side. The sector is nevertheless reported at the physical
 * position it was read from, so that a damaged header cannot displace it during image
 * reconstruction.
 * <p>
 * The samplers write the header and the data field of a track as two separate operations, which is
 * why the data field starts at an arbitrary phase relative to the header: on disks written by the
 * machines themselves it is found up to half a bit cell early or a whole cell late, and the
 * Emulator II - like the images which the HxC tools generate for it - puts it half a byte earlier
 * than the published track description says. The decoder therefore works on the level of the single
 * flux pulses and synchronizes on each of the two marks separately instead of assuming a fixed byte
 * alignment for the whole track.
 * <p>
 * The format was documented by ///Esynthesist in "Disk layout of Emulator I floppy disks" and "Disk
 * layout of Emulator II floppy disks" and the geometry is confirmed by the debug monitor of the
 * Emulator II service manual. The bit level details below were derived from the OS 3.1 disk of the
 * Emulator II, which the EMXP project publishes both as a HFE and as a raw sector image of the very
 * same disk, and verified on 79 factory disks of the Emulator and 90 of the Emulator II.
 *
 * @author Jürgen Moßgraber
 */
public class EmuFmDecoder extends AbstractDecoder
{
    /** The fixed size of the single sector which the samplers store in each track. */
    public static final int   SECTOR_SIZE    = 3584;

    /** The two bytes which mark both the track header and the sector data. */
    public static final int   MARK_FIRST     = 0xFA;
    /** The second byte of the mark. */
    public static final int   MARK_SECOND    = 0x96;

    /** A FM bit cell occupies this many bits (slots) of the raw HFE bit-stream. */
    public static final int   SLOTS_PER_CELL = 4;
    /** The slot of a cell which carries the clock pulse, which is always present. */
    public static final int   CLOCK_SLOT     = 1;
    /** The slot of a cell which carries the data pulse, which is present for a one bit. */
    public static final int   DATA_SLOT      = 3;
    /** The number of slots of one byte. */
    public static final int   SLOTS_PER_BYTE = 8 * SLOTS_PER_CELL;

    /** The number of slots the two mark bytes occupy. */
    private static final int  MARK_SLOTS     = 2 * SLOTS_PER_BYTE;
    /** The pulses of the mark as a bit pattern, the first slot in the most significant bit. */
    private static final long MARK_PATTERN   = createMarkPattern ();
    /** The header behind the mark: the track number and its CRC. */
    private static final int  HEADER_BYTES   = 3;


    /** {@inheritDoc} */
    @Override
    public List<Sector> decodeSectors (final TrackData trackData, final int cylinder, final int head)
    {
        if (trackData == null)
            return Collections.emptyList ();

        final byte [] cellData = trackData.getData ();
        if (cellData == null || cellData.length == 0)
            return Collections.emptyList ();

        final byte [] pulses = toPulses (cellData);

        final int headerMark = findMark (pulses, 0);
        if (headerMark < 0)
            return Collections.emptyList ();
        final int headerStart = headerMark + MARK_SLOTS;
        if (headerStart + HEADER_BYTES * SLOTS_PER_BYTE > pulses.length)
            return Collections.emptyList ();

        final byte [] header = readBytes (pulses, headerStart, HEADER_BYTES);
        final int trackNumber = header[0] & 0xFF;
        final boolean headerCrcValid = calculateCrcLsbFirst (new byte []
        {
            (byte) trackNumber
        }) == readCrc (header, 1);

        // The data field was written separately from the header and sits at its own phase
        final int dataMark = findMark (pulses, headerStart + HEADER_BYTES * SLOTS_PER_BYTE);
        if (dataMark < 0)
            return Collections.emptyList ();
        final int dataStart = dataMark + MARK_SLOTS;
        if (dataStart + SECTOR_SIZE * SLOTS_PER_BYTE > pulses.length)
            return Collections.emptyList ();

        final byte [] data = readBytes (pulses, dataStart, SECTOR_SIZE);

        boolean crcValid = headerCrcValid;
        final int dataCrcStart = dataStart + SECTOR_SIZE * SLOTS_PER_BYTE;
        if (dataCrcStart + 2 * SLOTS_PER_BYTE <= pulses.length)
        {
            if (calculateCrcLsbFirst (data) != readCrc (readBytes (pulses, dataCrcStart, 2), 0))
                crcValid = false;
        }
        else
            crcValid = false;

        return List.of (Sector.createWithSize (cylinder, head, 0, SECTOR_SIZE, data, crcValid));
    }


    /**
     * Unpack the raw HFE bit-stream of one track into its slots. The bits of the stream are ordered
     * least significant bit first.
     *
     * @param cellData The raw bit-stream of the track
     * @return One entry per slot, 1 where a flux pulse is present
     */
    private static byte [] toPulses (final byte [] cellData)
    {
        final byte [] pulses = new byte [cellData.length * 8];
        int slot = 0;
        for (final byte cellByte: cellData)
            for (int bit = 0; bit < 8; bit++)
                pulses[slot++] = (byte) (cellByte >> bit & 1);
        return pulses;
    }


    /**
     * Find the next occurrence of the two byte address mark at any phase of the bit-stream. The
     * mark is searched as the exact pulse pattern of its 16 bit cells, so the search
     * re-synchronizes to the phase at which the field behind it was written.
     *
     * @param pulses The slots of the track
     * @param fromSlot The slot at which to start the search
     * @return The first slot of the mark or -1 if there is none
     */
    private static int findMark (final byte [] pulses, final int fromSlot)
    {
        long window = 0;
        for (int slot = Math.max (0, fromSlot); slot < pulses.length; slot++)
        {
            window = window << 1 | (pulses[slot] & 0xFF);
            if (window == MARK_PATTERN && slot + 1 >= fromSlot + MARK_SLOTS)
                return slot + 1 - MARK_SLOTS;
        }
        return -1;
    }


    /**
     * Read bytes from the slots, starting at the first slot of the first bit cell. Both the bits of
     * the stream and the resulting bytes are ordered least significant bit first.
     *
     * @param pulses The slots of the track
     * @param fromSlot The first slot of the first cell to read
     * @param count The number of bytes to read
     * @return The bytes
     */
    private static byte [] readBytes (final byte [] pulses, final int fromSlot, final int count)
    {
        final byte [] bytes = new byte [count];
        int slot = fromSlot + DATA_SLOT;
        for (int i = 0; i < count; i++)
        {
            int value = 0;
            for (int bit = 0; bit < 8; bit++)
            {
                if (slot < pulses.length && pulses[slot] != 0)
                    value |= 1 << bit;
                slot += SLOTS_PER_CELL;
            }
            bytes[i] = (byte) value;
        }
        return bytes;
    }


    /**
     * Build the pulse pattern of the mark: each cell of a bit has its clock pulse and, for a one
     * bit, its data pulse.
     *
     * @return The pattern, the first slot in the most significant bit
     */
    private static long createMarkPattern ()
    {
        long pattern = 0;
        for (final int markByte: new int []
        {
            MARK_FIRST,
            MARK_SECOND
        })
            for (int bit = 0; bit < 8; bit++)
                for (int slot = 0; slot < SLOTS_PER_CELL; slot++)
                {
                    final boolean pulse = slot == CLOCK_SLOT || slot == DATA_SLOT && (markByte >> bit & 1) != 0;
                    pattern = pattern << 1 | (pulse ? 1 : 0);
                }
        return pattern;
    }


    /**
     * Read a CRC which is stored least significant byte first.
     *
     * @param bytes The bytes
     * @param index The index of the first CRC byte
     * @return The CRC
     */
    private static int readCrc (final byte [] bytes, final int index)
    {
        return (bytes[index + 1] & 0xFF) << 8 | bytes[index] & 0xFF;
    }
}
