// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.hfe;

import java.util.Collections;
import java.util.List;


/**
 * Builds a raw IMG disk image from sectors.
 * 
 * @author Jürgen Moßgraber
 */
public class DiskImageBuilder
{
    /**
     * Build IMG file data from sectors (1-based) with specified geometry.
     *
     * @param sectors List of sectors (will be sorted)
     * @param numCylinders Number of cylinders
     * @param numHeads Number of heads
     * @param sectorsPerTrack Sectors per track
     * @param bytesPerSector Bytes per sector
     * @return The IMG file data
     */
    public static byte [] buildImage (final List<Sector> sectors, final int numCylinders, final int numHeads, final int sectorsPerTrack, final int bytesPerSector)
    {
        return buildImage (sectors, numCylinders, numHeads, sectorsPerTrack, bytesPerSector, false);
    }


    /**
     * Build IMG file data from sectors with specified geometry.
     *
     * @param sectors List of sectors (will be sorted)
     * @param numCylinders Number of cylinders
     * @param numHeads Number of heads
     * @param sectorsPerTrack Sectors per track
     * @param bytesPerSector Bytes per sector
     * @param sectorIsZeroBased True if the first sector starts at 0 otherwise at 1
     * @return The IMG file data
     */
    public static byte [] buildImage (final List<Sector> sectors, final int numCylinders, final int numHeads, final int sectorsPerTrack, final int bytesPerSector, final boolean sectorIsZeroBased)
    {
        // Sort sectors by cylinder, head, sector number
        Collections.sort (sectors);

        final byte [] image = createEmptyImage (numCylinders * numHeads * sectorsPerTrack * bytesPerSector);

        // Place sectors in image
        for (final Sector sector: sectors)
        {
            final int lba = calculateLBA (sector.getCylinder (), sector.getHead (), sector.getSectorNumber (), numHeads, sectorsPerTrack, sectorIsZeroBased);
            final int offset = lba * bytesPerSector;
            if (offset + bytesPerSector <= image.length && sector.getData ().length == bytesPerSector)
            {
                try
                {
                    System.arraycopy (sector.getData (), 0, image, offset, bytesPerSector);
                }
                catch (ArrayIndexOutOfBoundsException ex)
                {
                    ex.printStackTrace ();
                }
            }
        }

        return image;
    }


    /**
     * Creates and empty image of the given byte size and fills it with standard empty sector
     * pattern.
     * 
     * @param imageSize The size of the image in bytes
     * @return The created image
     */
    public static byte [] createEmptyImage (final int imageSize)
    {
        final byte [] image = new byte [imageSize];
        for (int i = 0; i < image.length; i++)
            image[i] = (byte) 0xF6;
        return image;
    }


    /**
     * Calculate Logical Block Address from CHS (Cylinder/Head/Sector). Note: Sector numbers
     * typically start at 1, not 0.
     * 
     * @param cylinder The cylinder
     * @param head The head
     * @param sector The sector
     * @param numHeads The number of heads
     * @param sectorsPerTrack The sectors per track
     * @param sectorIsZeroBased True if the first sector starts at 0 otherwise at 1
     * @return The calculated LBA
     */
    private static int calculateLBA (final int cylinder, final int head, final int sector, final int numHeads, final int sectorsPerTrack, final boolean sectorIsZeroBased)
    {
        final int lba = (cylinder * numHeads + head) * sectorsPerTrack + sector;
        return sectorIsZeroBased ? lba : lba - 1;
    }
}