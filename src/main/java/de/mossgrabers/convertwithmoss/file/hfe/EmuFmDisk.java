// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.INotifier;


/**
 * Reads and writes the HxC images of the floppy disks of the E-mu Emulator and Emulator II. Both
 * samplers write the same FM track format with one sector of 3584 bytes per track; the Emulator
 * uses 35 tracks on one side, the Emulator II 80 tracks on two sides. A raw image is the sectors
 * one after another in the order of the tracks, which is the order in which the samplers load
 * them into memory.
 *
 * @author Jürgen Moßgraber
 */
public class EmuFmDisk
{
    /** The length of the interleaved bit-stream of a track in the images of the samplers. */
    public static final int TRACK_LENGTH = 31250;
    /** The bit-rate which the images of the samplers declare in kbit/s. */
    public static final int BITRATE      = 312;
    /** The closing gap of a track continues into the padding of the last block of the track. */
    private static final byte PAD_BYTE   = (byte) 0xAA;


    /**
     * Private constructor since this is a utility class.
     */
    private EmuFmDisk ()
    {
        // Intentionally empty
    }


    /**
     * Check whether a HFE file is a disk of one of the samplers with the given geometry.
     *
     * @param hfeFile The file
     * @param numCylinders The number of tracks per side the sampler uses
     * @param numSides The number of sides the sampler uses
     * @return True if the geometry matches
     */
    public static boolean isEmuDisk (final HfeFile hfeFile, final int numCylinders, final int numSides)
    {
        return hfeFile.getTrackEncoding () == HfeFile.ENCODING_EMU_FM && hfeFile.getNumTracks () == numCylinders && hfeFile.getNumSides () == numSides;
    }


    /**
     * Read the raw image of a disk from its HFE file.
     *
     * @param notifier Where to report damaged tracks
     * @param file The HFE file
     * @param numCylinders The number of tracks per side the sampler uses
     * @param numSides The number of sides the sampler uses
     * @return The raw image or empty if the file is not a disk of a sampler with that geometry
     * @throws IOException Could not read the file
     */
    public static Optional<byte []> readImage (final INotifier notifier, final File file, final int numCylinders, final int numSides) throws IOException
    {
        final HfeFile hfeFile = new HfeFile (file);
        if (!isEmuDisk (hfeFile, numCylinders, numSides))
            return Optional.empty ();

        final List<Sector> sectors = hfeFile.decodeSectors ();
        if (sectors.isEmpty ())
            return Optional.empty ();

        int damaged = numCylinders * numSides - sectors.size ();
        for (final Sector sector: sectors)
            if (!sector.isCrcValid ())
                damaged++;
        if (damaged > 0)
            notifier.logError ("IDS_EMU_DISK_DAMAGED_TRACKS", file.getName (), Integer.toString (damaged));

        return Optional.of (DiskImageBuilder.buildImage (sectors, numCylinders, numSides, 1, EmuFmDecoder.SECTOR_SIZE, true));
    }


    /**
     * Write the raw image of a disk as a HFE file.
     *
     * @param file The HFE file to write
     * @param image The raw image, one sector of 3584 bytes per track
     * @param numCylinders The number of tracks per side the sampler uses
     * @param numSides The number of sides the sampler uses
     * @param layout The spacing of the fields of a track the sampler uses
     * @throws IOException Could not write the file
     */
    public static void writeImage (final File file, final byte [] image, final int numCylinders, final int numSides, final EmuFmEncoder.TrackLayout layout) throws IOException
    {
        if (image.length != numCylinders * numSides * EmuFmDecoder.SECTOR_SIZE)
            throw new IOException ("Unexpected size of image: " + image.length);

        final byte [] [] [] streams = new byte [numCylinders] [numSides] [];
        for (int cylinder = 0; cylinder < numCylinders; cylinder++)
            for (int side = 0; side < numSides; side++)
            {
                // The samplers number the tracks linearly across the sides
                final int trackIndex = cylinder * numSides + side;
                final byte [] data = new byte [EmuFmDecoder.SECTOR_SIZE];
                System.arraycopy (image, trackIndex * EmuFmDecoder.SECTOR_SIZE, data, 0, EmuFmDecoder.SECTOR_SIZE);
                streams[cylinder][side] = EmuFmEncoder.encodeTrack (data, trackIndex, layout, HfeFileWriter.getSideLength (TRACK_LENGTH, side));
            }
        HfeFileWriter.write (file, numSides, HfeFile.ENCODING_EMU_FM, BITRATE, HfeFile.FLOPPYMODE_EMU_SHUGART, TRACK_LENGTH, streams, PAD_BYTE);
    }
}
