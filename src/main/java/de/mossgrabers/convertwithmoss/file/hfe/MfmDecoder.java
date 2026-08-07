// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * MFM is a modification to the original frequency modulation encoding (FM) code specifically for
 * use with magnetic storage. MFM allowed devices to double the speed data was written to the media
 * as the code guaranteed only one polarity change per encoded data bit. For this reason, MFM disks
 * are typically known as "double density", while the earlier FM became known as "single density".
 * MFM is used with a data rate of 250–500 kbit/s (500–1000 kbit/s encoded) on industry-standard
 * 5+1⁄4-inch and 3+1⁄2-inch ordinary and high-density floppy diskettes. MFM was also used in early
 * hard disk designs, before the advent of more efficient types of RLL codes. Outside of niche
 * applications, MFM encoding is obsolete in magnetic recording.
 *
 * @author Jürgen Moßgraber
 */
public class MfmDecoder extends AbstractDecoder
{
    private static final int SYNC_WORD = 0x4489; // A1 with missing clock bit
    private static final int SYNC_BYTE = 0xA1;   // The actual sync byte value


    /** {@inheritDoc} */
    @Override
    public List<Sector> decodeSectors (final TrackData trackData, final int cylinder, final int head)
    {
        final byte [] mfmData = trackData.getData ();
        if (mfmData == null || mfmData.length == 0)
            return Collections.emptyList ();

        for (final BitReadMode mode: BIT_READ_MODES)
        {
            final List<Sector> sectors = tryDecode (mfmData, mode);
            if (!sectors.isEmpty ())
                return sectors;
        }

        return Collections.emptyList ();
    }


    private static List<Sector> tryDecode (final byte [] mfmData, final BitReadMode mode)
    {
        final List<Sector> sectors = new ArrayList<> ();
        final BitStream bitStream = new BitStream (mfmData, mode);

        final int maxSearchBits = mfmData.length * 8;

        while (bitStream.getBitPosition () < maxSearchBits - 100)
            if (findSync (bitStream))
            {
                final int markByte = bitStream.readMfmByte ();
                if (markByte != IDAM)
                    continue;

                final Optional<Sector> sectorOpt = readSectorHeader (bitStream);
                if (sectorOpt.isEmpty ())
                    continue;

                final Sector sector = sectorOpt.get ();
                if (sector.isCrcValid () && findNextDataMark (bitStream))
                {
                    readSectorData (bitStream, sector);
                    sectors.add (sector);
                }
            }

        return sectors;
    }


    private static boolean findSync (final BitStream bitStream)
    {
        int syncCount = 0;
        final int maxBits = 100000;
        int searched = 0;

        while (bitStream.hasRemaining () && searched < maxBits)
        {
            final int word = bitStream.peekWord ();

            if (word == SYNC_WORD)
            {
                bitStream.skipBits (16);
                syncCount++;

                if (syncCount >= 3)
                    return true;
            }
            else
            {
                syncCount = 0;
                bitStream.skipBits (1);
                searched++;
            }
        }

        return false;
    }


    private static Optional<Sector> readSectorHeader (final BitStream bitStream)
    {
        if (!bitStream.hasRemaining ())
            return Optional.empty ();

        final int cyl = bitStream.readMfmByte ();
        final int head = bitStream.readMfmByte ();
        final int sectorNum = bitStream.readMfmByte ();
        final int sizeCode = bitStream.readMfmByte ();
        final int crc1 = bitStream.readMfmByte ();
        final int crc2 = bitStream.readMfmByte ();

        // CRC calculation includes: 0xA1 0xA1 0xA1 + IDAM + header fields
        final byte [] headerData = new byte []
        {
            (byte) SYNC_BYTE,
            (byte) SYNC_BYTE,
            (byte) SYNC_BYTE, // 3x sync
            (byte) IDAM,
            (byte) cyl,
            (byte) head,
            (byte) sectorNum,
            (byte) sizeCode
        };

        final int calculatedCrc = calculateCrc (headerData);
        final int readCrc = crc1 << 8 | crc2;
        final boolean crcValid = calculatedCrc == readCrc;

        final int sectorSize = 128 << sizeCode;
        return Optional.of (new Sector (cyl, head, sectorNum, sizeCode, new byte [sectorSize], crcValid));
    }


    private static boolean findNextDataMark (final BitStream bitStream)
    {
        final int maxGap = 50000;
        final int startPos = bitStream.getBitPosition ();

        while (bitStream.hasRemaining () && bitStream.getBitPosition () - startPos < maxGap)
            if (findSync (bitStream))
            {
                final int markByte = bitStream.peekMfmByte ();
                if (markByte == DAM || markByte == DDAM)
                {
                    bitStream.readMfmByte ();
                    return true;
                }
            }
        return false;
    }


    private static void readSectorData (final BitStream bitStream, final Sector sector)
    {
        final int dataSize = sector.getSizeBytes ();
        final byte [] data = sector.getData ();

        for (int i = 0; i < dataSize && bitStream.hasRemaining (); i++)
            data[i] = (byte) bitStream.readMfmByte ();

        if (bitStream.hasRemaining ())
        {
            final int crc1 = bitStream.readMfmByte ();
            final int crc2 = bitStream.readMfmByte ();

            // CRC includes: 0xA1 0xA1 0xA1 + DAM + data
            final byte [] fullData = new byte [dataSize + 4];
            fullData[0] = (byte) SYNC_BYTE;
            fullData[1] = (byte) SYNC_BYTE;
            fullData[2] = (byte) SYNC_BYTE;
            fullData[3] = (byte) DAM;
            System.arraycopy (data, 0, fullData, 4, dataSize);

            final int calculatedCrc = calculateCrc (fullData);
            final int readCrc = crc1 << 8 | crc2;
            final boolean dataCrcValid = calculatedCrc == readCrc;

            // Update sector CRC status (both header and data must be valid)
            if (!dataCrcValid)
                sector.setCrcValid (false);
        }
    }
}