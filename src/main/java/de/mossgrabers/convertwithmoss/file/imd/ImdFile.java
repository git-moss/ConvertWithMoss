// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.imd;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;


/**
 * IMD (ImageDisk) is a disk image format for floppy disks, created by Dave Dunfield along with his
 * ImageDisk software/hardware tool. This class reads the metadata from the header and extracts the
 * raw image file.
 *
 * @author Jürgen Moßgraber
 */
public class ImdFile
{
    /** The metadata stored in the header. */
    public static class Metadata
    {
        /** The description of the content. */
        public String description;
        /** The number of cylinders of the disk. */
        public int    cylinders;
        /** The number of heads of the disk. */
        public int    heads;
        /** The number of sectors per track of the disk. */
        public int    sectorsPerTrack;
        /** The size of one sector. */
        public int    sectorSize;
    }


    /** The metadata. */
    public Metadata metadata;
    /** The RAW image data. */
    public byte []  rawImageData;


    /**
     * Constructor.
     *
     * @param file The IMD file to read
     * @throws IOException Could not read the IMD file
     */
    public ImdFile (final File file) throws IOException
    {
        try (final DataInputStream in = new DataInputStream (new BufferedInputStream (new FileInputStream (file))))
        {
            // Parse comment header, terminated by 0x1A
            final ByteArrayOutputStream headerBuf = new ByteArrayOutputStream ();
            int b;
            while ((b = in.read ()) != -1 && b != 0x1A)
                headerBuf.write (b);
            final String header = headerBuf.toString (StandardCharsets.US_ASCII);

            final Map<Long, byte []> tracks = new TreeMap<> ();
            int maxCyl = -1;
            int maxHead = -1;
            int commonSectorsPerTrack = -1;
            int commonSectorSize = -1;

            // Parse track records
            while (true)
            {
                final int mode = in.read ();
                if (mode == -1)
                    break; // EOF

                final int cyl = in.readUnsignedByte ();
                final int headByte = in.readUnsignedByte ();
                final int numSectors = in.readUnsignedByte ();
                final int sizeCode = in.readUnsignedByte ();

                final boolean hasCylMap = (headByte & 0x80) != 0;
                final boolean hasHeadMap = (headByte & 0x40) != 0;
                final int head = headByte & 0x3F;

                if (sizeCode > 6)
                    throw new IOException ("Unsupported sector size code: " + sizeCode);
                final int sectorSize = 128 << sizeCode;

                final int [] sectorNumMap = new int [numSectors];
                for (int i = 0; i < numSectors; i++)
                    sectorNumMap[i] = in.readUnsignedByte ();
                if (hasCylMap)
                    in.skipBytes (numSectors); // optional maps: ignored
                if (hasHeadMap)
                    in.skipBytes (numSectors);

                final byte [] [] sectorData = new byte [numSectors] [];

                for (int i = 0; i < numSectors; i++)
                {
                    final int type = in.readUnsignedByte ();
                    final byte [] data = new byte [sectorSize];
                    switch (type)
                    {
                        case 0: // unavailable -> leave zeroed
                            break;
                        case 1:
                        case 3:
                        case 5:
                        case 7:
                            in.readFully (data);
                            break;
                        case 2:
                        case 4:
                        case 6:
                        case 8:
                            final byte fill = (byte) in.readUnsignedByte ();
                            Arrays.fill (data, fill);
                            break;
                        default:
                            throw new IOException ("Unknown sector data type: " + type);
                    }
                    sectorData[i] = data;
                }

                // Reorder physical -> logical sector order
                final Integer [] order = new Integer [numSectors];
                for (int i = 0; i < numSectors; i++)
                    order[i] = Integer.valueOf (i);
                Arrays.sort (order, (a, c) -> sectorNumMap[a.intValue ()] - sectorNumMap[c.intValue ()]);

                final ByteArrayOutputStream trackBuf = new ByteArrayOutputStream ();
                for (final Integer idx: order)
                    trackBuf.write (sectorData[idx.intValue ()]);
                final byte [] trackBytes = trackBuf.toByteArray ();

                tracks.put (Long.valueOf (((long) cyl << 8) | head), trackBytes);
                maxCyl = Math.max (maxCyl, cyl);
                maxHead = Math.max (maxHead, head);
                if (commonSectorsPerTrack == -1)
                    commonSectorsPerTrack = numSectors;
                if (commonSectorSize == -1)
                    commonSectorSize = sectorSize;
            }

            final int cylinders = maxCyl + 1;
            final int headsCount = maxHead + 1;

            // Assemble flat raw image: cylinder-major, head-minor
            final ByteArrayOutputStream imageBuf = new ByteArrayOutputStream ();
            for (int c = 0; c < cylinders; c++)
                for (int h = 0; h < headsCount; h++)
                {
                    byte [] trackBytes = tracks.get (Long.valueOf (((long) c << 8) | h));
                    if (trackBytes == null)
                        trackBytes = new byte [commonSectorsPerTrack * commonSectorSize];
                    imageBuf.write (trackBytes);
                }

            final Metadata meta = new Metadata ();
            meta.description = header;
            meta.cylinders = cylinders;
            meta.heads = headsCount;
            meta.sectorsPerTrack = commonSectorsPerTrack;
            meta.sectorSize = commonSectorSize;

            this.metadata = meta;
            this.rawImageData = imageBuf.toByteArray ();
        }
    }
}