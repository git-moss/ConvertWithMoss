// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;


/**
 * Writes the HFE container of the HxC floppy emulators, the counterpart of {@link HfeFile}. The
 * container is a 512 byte header, a 512 byte table with the position and the length of each track,
 * and the tracks: the bit-streams of the two sides of a track are interleaved in blocks of 256
 * bytes, so each 512 byte block of a track carries 256 bytes of side 0 followed by 256 bytes of
 * side 1, and each track starts at a 512 byte boundary.
 *
 * @author Jürgen Moßgraber
 */
public class HfeFileWriter
{
    private static final int     BLOCK_SIZE        = 512;
    private static final int     TRACK_BLOCK_SIZE  = 256;
    private static final int     FIRST_TRACK_BLOCK = 2;
    private static final byte [] SIGNATURE         = "HXCPICFE".getBytes (StandardCharsets.US_ASCII);


    /**
     * Private constructor since this is a utility class.
     */
    private HfeFileWriter ()
    {
        // Intentionally empty
    }


    /**
     * Get the length of the bit-stream of one side of a track: the side gets 256 bytes of every
     * full 512 byte block of the track and its share of a partial last block.
     *
     * @param trackLength The length of the interleaved track in bytes
     * @param side The side, 0 or 1
     * @return The length of the bit-stream of the side in bytes
     */
    public static int getSideLength (final int trackLength, final int side)
    {
        final int fullBlocks = trackLength / BLOCK_SIZE;
        final int remainder = trackLength % BLOCK_SIZE;
        final int partial = side == 0 ? Math.min (remainder, TRACK_BLOCK_SIZE) : Math.max (0, remainder - TRACK_BLOCK_SIZE);
        return fullBlocks * TRACK_BLOCK_SIZE + partial;
    }


    /**
     * Write a HFE file (format revision 0).
     *
     * @param file The file to write
     * @param numSides The number of sides, 1 or 2
     * @param trackEncoding The encoding of the tracks, one of the ENCODING_ constants of
     *            {@link HfeFile}
     * @param bitrate The bit-rate in kbit/s
     * @param floppyInterfaceMode The interface mode, one of the FLOPPYMODE_ constants of
     *            {@link HfeFile}
     * @param trackLength The length of each interleaved track in bytes
     * @param streams The bit-streams of the sides of each track, indexed by track and side; a side
     *            which is null - the unused side of a single sided disk - is written as zeroes
     * @param padByte The byte with which the space between the end of a track and the next 512 byte
     *            boundary is filled on a used side
     * @throws IOException Could not write the file
     */
    public static void write (final File file, final int numSides, final int trackEncoding, final int bitrate, final int floppyInterfaceMode, final int trackLength, final byte [] [] [] streams, final byte padByte) throws IOException
    {
        final int numTracks = streams.length;
        final int blocksPerTrack = (trackLength + BLOCK_SIZE - 1) / BLOCK_SIZE;

        final ByteBuffer header = ByteBuffer.allocate (BLOCK_SIZE).order (ByteOrder.LITTLE_ENDIAN);
        Arrays.fill (header.array (), (byte) 0xFF);
        header.put (SIGNATURE);
        // Format revision
        header.put ((byte) 0);
        header.put ((byte) numTracks);
        header.put ((byte) numSides);
        header.put ((byte) trackEncoding);
        header.putShort ((short) bitrate);
        // Rotations per minute, not set
        header.putShort ((short) 0);
        header.put ((byte) floppyInterfaceMode);
        // Reserved
        header.put ((byte) 1);
        // The track list follows the header
        header.putShort ((short) 1);
        // The rest of the header - write allowed, single step, alternate encodings - stays 0xFF

        final ByteBuffer trackList = ByteBuffer.allocate (BLOCK_SIZE).order (ByteOrder.LITTLE_ENDIAN);
        Arrays.fill (trackList.array (), (byte) 0xFF);
        for (int track = 0; track < numTracks; track++)
        {
            trackList.putShort ((short) (FIRST_TRACK_BLOCK + track * blocksPerTrack));
            trackList.putShort ((short) trackLength);
        }

        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (file.toPath ())))
        {
            out.write (header.array ());
            out.write (trackList.array ());
            for (int track = 0; track < numTracks; track++)
            {
                final byte [] trackBlock = new byte [blocksPerTrack * BLOCK_SIZE];
                for (int side = 0; side < 2; side++)
                {
                    final byte [] stream = side < numSides && side < streams[track].length ? streams[track][side] : null;
                    final int sideLength = getSideLength (trackLength, side);
                    for (int block = 0; block < blocksPerTrack; block++)
                    {
                        final int offset = block * BLOCK_SIZE + side * TRACK_BLOCK_SIZE;
                        for (int i = 0; i < TRACK_BLOCK_SIZE; i++)
                        {
                            final int position = block * TRACK_BLOCK_SIZE + i;
                            if (stream == null)
                                trackBlock[offset + i] = 0;
                            else if (position < sideLength && position < stream.length)
                                trackBlock[offset + i] = stream[position];
                            else
                                trackBlock[offset + i] = padByte;
                        }
                    }
                }
                out.write (trackBlock);
            }
        }
    }
}
