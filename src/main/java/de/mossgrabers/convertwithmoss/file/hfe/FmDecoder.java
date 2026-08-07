// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * FM (Frequency Modulation) is the original single-density floppy encoding used before MFM. Each
 * data bit is preceded by a clock bit, which is always 1 for normal data. Address marks violate
 * this rule with a specific missing-clock pattern to create a unique, unambiguous sync sequence.
 * <p>
 * IMPORTANT: In HFE files, FM tracks are stored with each bit duplicated (written twice in a row)
 * in the raw bitstream, and the header's bitrate field is doubled accordingly. This is a quirk of
 * the HFE format (its bitstream timing base was designed around MFM). Therefore, before decoding,
 * the raw bitstream must be decimated by 2 to recover the logical FM bit sequence. Since the exact
 * bit alignment (phase) of the duplicated pairs is not known in advance, both phases are tried.
 * <p>
 * NOTE: Many legacy FM controllers/formatters always wrote 0x00 into the head/side field of the ID
 * Address Mark, regardless of the actual physical side being written. Because of this, the physical
 * side (passed in via {@code decodeSectors}) is used for the resulting {@link Sector}'s head value
 * instead of trusting the on-disk encoded byte, which is only used for CRC checking.
 *
 * @author Jürgen Moßgraber
 */
public class FmDecoder extends AbstractDecoder
{
    private static final int [] PHASES    = new int []
    {
        0,
        1
    };

    // -----------------------------------------------------------
    // Encoded 16-bit words (clock+data interleaved) for the FM address marks

    /** Index Address Mark (data 0xFC, clock 0xD7). */
    @SuppressWarnings("unused")
    private static final int    IAM_WORD  = 0xF77A;
    /** ID Address Mark (data 0xFE, clock 0xC7). */
    private static final int    IDAM_WORD = 0xF57E;
    /** Data Address Mark (data 0xFB, clock 0xC7). */
    private static final int    DAM_WORD  = 0xF56F;
    /** Deleted Data Address Mark (data 0xF8, clock 0xC7). */
    private static final int    DDAM_WORD = 0xF56A;


    /** {@inheritDoc} */
    @Override
    public List<Sector> decodeSectors (final TrackData trackData, final int cylinder, final int head)
    {
        final byte [] fmData = trackData.getData ();
        if (fmData == null || fmData.length == 0)
            return Collections.emptyList ();

        // Try double-bit-rate decoding first (the standard HFE FM convention), both phases,
        // then fall back to plain 1-bit-per-cell in case some writer didn't double the bits.
        for (final BitReadMode mode: BIT_READ_MODES)
            for (final int phase: PHASES)
            {
                final List<Sector> sectors = tryDecode (fmData, mode, true, phase, head);
                if (!sectors.isEmpty ())
                    return sectors;
            }

        for (final BitReadMode mode: BIT_READ_MODES)
        {
            final List<Sector> sectors = tryDecode (fmData, mode, false, 0, head);
            if (!sectors.isEmpty ())
                return sectors;
        }

        return Collections.emptyList ();
    }


    private static List<Sector> tryDecode (final byte [] fmData, final BitReadMode mode, final boolean doubled, final int phase, final int physicalHead)
    {
        final List<Sector> sectors = new ArrayList<> ();
        final BitStream bitStream = new BitStream (fmData, mode);
        final int cellWidth = doubled ? 2 : 1;

        final int maxSearchBits = fmData.length * 8;

        while (bitStream.getBitPosition () < maxSearchBits - 200)
        {
            if (!findIdamSync (bitStream, cellWidth, phase))
                break;

            final Optional<Sector> sectorOpt = readSectorHeader (bitStream, cellWidth, phase, physicalHead);
            if (sectorOpt.isEmpty ())
                continue;

            final Sector sector = sectorOpt.get ();
            if (!sector.isCrcValid ())
                continue;

            final int dataMark = findNextDataMark (bitStream, cellWidth, phase);
            if (dataMark < 0)
                continue;

            readSectorData (bitStream, sector, dataMark, cellWidth, phase);
            sectors.add (sector);
        }

        return sectors;
    }


    /**
     * Search for the FM ID Address Mark sync word. Unlike MFM, FM address marks do not need a
     * repeated pre-sync sequence - the missing-clock pattern itself is already unambiguous.
     *
     * @param bitStream The stream to search
     * @param cellWidth The number of raw bits per logical bit (2 if bits are doubled, else 1)
     * @param phase Which raw bit of a duplicated pair to sample (0 or 1); ignored if cellWidth==1
     * @return True if found (stream is positioned right after the mark)
     */
    private static boolean findIdamSync (final BitStream bitStream, final int cellWidth, final int phase)
    {
        final int maxLogicalBits = 400000;
        int searched = 0;

        while (bitStream.hasRemaining () && searched < maxLogicalBits)
        {
            final int word = peekLogicalWord (bitStream, cellWidth, phase);

            if (word == IDAM_WORD)
            {
                skipLogicalBits (bitStream, 16, cellWidth);
                return true;
            }

            bitStream.skipBits (1);
            searched++;
        }

        return false;
    }


    private static Optional<Sector> readSectorHeader (final BitStream bitStream, final int cellWidth, final int phase, final int physicalHead)
    {
        if (!bitStream.hasRemaining ())
            return Optional.empty ();

        final int cyl = readLogicalByte (bitStream, cellWidth, phase);
        // As physically encoded on disk
        final int headerHead = readLogicalByte (bitStream, cellWidth, phase);
        final int sectorNum = readLogicalByte (bitStream, cellWidth, phase);
        final int sizeCode = readLogicalByte (bitStream, cellWidth, phase);
        final int crc1 = readLogicalByte (bitStream, cellWidth, phase);
        final int crc2 = readLogicalByte (bitStream, cellWidth, phase);

        // CRC calculation includes: IDAM + header fields exactly as physically encoded (no sync
        // byte prefix in FM) - must use headerHead here, not physicalHead, since the CRC covers
        // the actual on-disk bytes.
        final byte [] headerData = new byte []
        {
            (byte) IDAM,
            (byte) cyl,
            (byte) headerHead,
            (byte) sectorNum,
            (byte) sizeCode
        };

        final int calculatedCrc = calculateCrc (headerData);
        final int readCrc = crc1 << 8 | crc2;
        final boolean crcValid = calculatedCrc == readCrc;

        // Use the physical side the track was read from, not the (often unreliable/always-0)
        // on-disk head byte, so sectors from side 1 don't collide with side 0 during image
        // reconstruction.
        final int sectorSize = 128 << sizeCode;
        return Optional.of (new Sector (cyl, physicalHead, sectorNum, sizeCode, new byte [sectorSize], crcValid));
    }


    /**
     * Search for the next Data or Deleted-Data Address Mark following an ID Address Mark.
     *
     * @param bitStream The stream to search
     * @param cellWidth The number of raw bits per logical bit
     * @param phase Which raw bit of a duplicated pair to sample
     * @return The mark byte found (DAM or DDAM), or -1 if none was found within the gap limit
     */
    private static int findNextDataMark (final BitStream bitStream, final int cellWidth, final int phase)
    {
        final int maxGap = 20000 * cellWidth;
        final int startPos = bitStream.getBitPosition ();

        while (bitStream.hasRemaining () && bitStream.getBitPosition () - startPos < maxGap)
        {
            final int word = peekLogicalWord (bitStream, cellWidth, phase);

            if (word == DAM_WORD)
            {
                skipLogicalBits (bitStream, 16, cellWidth);
                return DAM;
            }

            if (word == DDAM_WORD)
            {
                skipLogicalBits (bitStream, 16, cellWidth);
                return DDAM;
            }

            bitStream.skipBits (1);
        }

        return -1;
    }


    private static void readSectorData (final BitStream bitStream, final Sector sector, final int dataMark, final int cellWidth, final int phase)
    {
        final int dataSize = sector.getSizeBytes ();
        final byte [] data = sector.getData ();

        for (int i = 0; i < dataSize && bitStream.hasRemaining (); i++)
            data[i] = (byte) readLogicalByte (bitStream, cellWidth, phase);

        if (bitStream.hasRemaining ())
        {
            final int crc1 = readLogicalByte (bitStream, cellWidth, phase);
            final int crc2 = readLogicalByte (bitStream, cellWidth, phase);

            // CRC includes: DAM/DDAM + data (no sync byte prefix in FM)
            final byte [] fullData = new byte [dataSize + 1];
            fullData[0] = (byte) dataMark;
            System.arraycopy (data, 0, fullData, 1, dataSize);

            final int calculatedCrc = calculateCrc (fullData);
            final int readCrc = crc1 << 8 | crc2;
            final boolean dataCrcValid = calculatedCrc == readCrc;

            if (!dataCrcValid)
                sector.setCrcValid (false);
        }
    }

    // -----------------------------------------------------------------------
    // Decimation helpers - reduce a possibly bit-doubled raw stream down to
    // the logical FM bit sequence before applying the normal clock/data
    // interleave decoding (same mechanics as MfmDecoder's readMfmByte).


    private static int readLogicalBit (final BitStream bitStream, final int cellWidth, final int phase)
    {
        if (cellWidth == 1)
            return bitStream.readSingleBit ();

        final int b0 = bitStream.readSingleBit ();
        final int b1 = bitStream.readSingleBit ();
        return phase == 0 ? b0 : b1;
    }


    private static int peekLogicalWord (final BitStream bitStream, final int cellWidth, final int phase)
    {
        final int saved = bitStream.getBitPosition ();
        int word = 0;
        for (int i = 0; i < 16; i++)
            word = word << 1 | readLogicalBit (bitStream, cellWidth, phase);
        bitStream.setBitPosition (saved);
        return word;
    }


    private static void skipLogicalBits (final BitStream bitStream, final int count, final int cellWidth)
    {
        bitStream.skipBits (count * cellWidth);
    }


    private static int readLogicalByte (final BitStream bitStream, final int cellWidth, final int phase)
    {
        int result = 0;
        for (int i = 0; i < 8; i++)
        {
            readLogicalBit (bitStream, cellWidth, phase); // skip clock bit
            final int dataBit = readLogicalBit (bitStream, cellWidth, phase); // data bit
            result = result << 1 | dataBit;
        }
        return result & 0xFF;
    }
}