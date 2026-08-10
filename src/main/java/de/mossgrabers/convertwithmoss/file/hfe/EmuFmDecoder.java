// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Decoder for the FM track format written by the E-mu Emulator II. This is the encoding which HFE
 * files flag as {@link HfeFile#ENCODING_EMU_FM} in combination with the floppy interface mode
 * {@link HfeFile#FLOPPYMODE_EMU_SHUGART}. It is not the IBM System 34 FM format which
 * {@link FmDecoder} implements - none of its assumptions hold here:
 * <ul>
 * <li>The address mark is the byte pair 0xFA 0x96 instead of an ID address mark with a missing
 * clock pattern.</li>
 * <li>A track header consists of a single byte - the track number - instead of the cylinder, head,
 * sector and size quadruple. There is exactly one sector per track and it is always 3584 bytes
 * long, a size which no IBM size code can express.</li>
 * <li>The CRC uses the polynomial 0x8005 with the initial value 0x0000 instead of the CRC-CCITT,
 * it covers only the payload - the track number respectively the sector data - and it is stored
 * least significant byte first.</li>
 * </ul>
 * A track is built up as: 20 bytes 0xFF gap, 4 bytes 0x00 sync, the mark, the track number, its
 * CRC, 1 byte 0x00 sync, 8 bytes 0xFF gap, 4 bytes 0x00 sync, the mark, 3584 bytes of data, their
 * CRC, 2 bytes 0x00 sync and a closing gap of 20 bytes 0xFF.
 * <p>
 * The disk numbers its tracks linearly across both sides, so the track number stored in the header
 * is the cylinder times two plus the side. The sector is nevertheless reported at the physical
 * position it was read from, so that a damaged header cannot displace it during image
 * reconstruction.
 * <p>
 * The format was documented by ///Esynthesist in "Disk layout of Emulator II floppy disks" and the
 * geometry is confirmed by the debug monitor of the Emulator II service manual. The bit level
 * details below were derived from the OS 3.1 boot disk, which the EMXP project publishes both as a
 * HFE and as a raw sector image of the very same disk; this decoder reproduces all 160 tracks of
 * that raw image byte for byte.
 *
 * @author Jürgen Moßgraber
 */
public class EmuFmDecoder extends AbstractDecoder
{
    /** The fixed size of the single sector which the Emulator II stores in each track. */
    public static final int  SECTOR_SIZE   = 3584;

    /** The two bytes which mark both the track header and the sector data. */
    private static final int MARK_FIRST    = 0xFA;
    private static final int MARK_SECOND   = 0x96;

    /** A FM bit cell occupies this many bits of the raw HFE bit-stream. */
    private static final int BITS_PER_CELL = 4;
    /** The data pulse of a bit cell sits in this bit of the cell. */
    private static final int DATA_PULSE    = 0x08;


    /** {@inheritDoc} */
    @Override
    public List<Sector> decodeSectors (final TrackData trackData, final int cylinder, final int head)
    {
        if (trackData == null)
            return Collections.emptyList ();

        final byte [] cellData = trackData.getData ();
        if (cellData == null || cellData.length == 0)
            return Collections.emptyList ();

        final byte [] decoded = decodeCells (cellData);

        final int headerMark = indexOfMark (decoded, 0);
        // The mark, the track number and its CRC must fit
        if (headerMark < 0 || headerMark + 5 > decoded.length)
            return Collections.emptyList ();

        final int trackNumber = decoded[headerMark + 2] & 0xFF;
        final int headerCrc = readCrc (decoded, headerMark + 3);
        final boolean headerCrcValid = calculateCrcLsbFirst (new byte []
        {
            (byte) trackNumber
        }) == headerCrc;

        final int dataMark = indexOfMark (decoded, headerMark + 5);
        if (dataMark < 0)
            return Collections.emptyList ();

        final int dataStart = dataMark + 2;
        if (dataStart + SECTOR_SIZE > decoded.length)
            return Collections.emptyList ();

        final byte [] data = Arrays.copyOfRange (decoded, dataStart, dataStart + SECTOR_SIZE);

        boolean crcValid = headerCrcValid;
        final int dataCrcPosition = dataStart + SECTOR_SIZE;
        if (dataCrcPosition + 2 <= decoded.length && calculateCrcLsbFirst (data) != readCrc (decoded, dataCrcPosition))
            crcValid = false;

        return List.of (Sector.createWithSize (cylinder, head, 0, SECTOR_SIZE, data, crcValid));
    }


    /**
     * Convert the raw HFE bit-stream of one track into the bytes it encodes. Every FM bit cell
     * occupies four bits of the stream: the clock pulse - which is always present - and the data
     * pulse, which is only set for a one bit. Both the bits of the stream and the resulting bytes
     * are ordered least significant bit first.
     *
     * @param cellData The raw bit-stream of the track
     * @return The decoded bytes
     */
    private static byte [] decodeCells (final byte [] cellData)
    {
        final int numberOfCells = cellData.length * 8 / BITS_PER_CELL;
        final byte [] decoded = new byte [numberOfCells / 8];

        int cellIndex = 0;
        for (final byte cellByte: cellData)
            for (int cell = 0; cell < 8 / BITS_PER_CELL; cell++)
            {
                final int position = cellIndex / 8;
                if (position >= decoded.length)
                    return decoded;

                if ((cellByte >> cell * BITS_PER_CELL & DATA_PULSE) != 0)
                    decoded[position] |= (byte) (1 << cellIndex % 8);
                cellIndex++;
            }

        return decoded;
    }


    /**
     * Find the next occurrence of the two byte address mark.
     *
     * @param decoded The decoded track bytes
     * @param fromIndex The index at which to start the search
     * @return The index of the first byte of the mark or -1 if there is none
     */
    private static int indexOfMark (final byte [] decoded, final int fromIndex)
    {
        for (int i = Math.max (0, fromIndex); i < decoded.length - 1; i++)
            if ((decoded[i] & 0xFF) == MARK_FIRST && (decoded[i + 1] & 0xFF) == MARK_SECOND)
                return i;
        return -1;
    }


    /**
     * Read a CRC which is stored least significant byte first.
     *
     * @param decoded The decoded track bytes
     * @param index The index of the first CRC byte
     * @return The CRC
     */
    private static int readCrc (final byte [] decoded, final int index)
    {
        return (decoded[index + 1] & 0xFF) << 8 | decoded[index] & 0xFF;
    }
}
