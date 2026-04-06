// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000.diskformat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.INotifier;


/**
 * Reads Akai MPC2000/MPC60 IMG disks. This is a variant of FAT16. Differences are Cluster
 * calculation uses a lookup table (first 24) + formula, Cluster size is 8 KB fixed, Non-bootable
 * (EB FE loop), Root entries fixed at 512.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC2000DiskImage
{
    private final byte []                         diskImage;
    private AkaiMPC2000BootSector                 bootSector;
    private int []                                fat;
    private final int                             rootDirectoryOffset;
    private final int                             dataAreaOffset;
    private final List<AkaiMPC2000DirectoryEntry> entries;
    private boolean                               isFAT12;


    /**
     * Constructor.
     * 
     * @param file The file to read
     * @throws IOException Could not read the file
     */
    public AkaiMPC2000DiskImage (final File file) throws IOException
    {
        this (Files.readAllBytes (file.toPath ()));
    }


    /**
     * Constructor.
     * 
     * @param diskImage The bytes of the file to read
     * @throws IOException Could not read the file
     */
    public AkaiMPC2000DiskImage (final byte [] diskImage) throws IOException
    {
        this.diskImage = diskImage;
        this.parseDisk ();

        this.rootDirectoryOffset = (this.bootSector.reservedSectors + this.bootSector.numberOfFATs * this.bootSector.sectorsPerFAT) * this.bootSector.bytesPerSector;
        final int rootDirSectors = (this.bootSector.rootEntries * 32 + this.bootSector.bytesPerSector - 1) / this.bootSector.bytesPerSector;
        this.dataAreaOffset = this.rootDirectoryOffset + rootDirSectors * this.bootSector.bytesPerSector;

        this.entries = this.readRootDirectory ();
    }


    /**
     * Read the root directory.
     * 
     * @return The root directory
     */
    private List<AkaiMPC2000DirectoryEntry> readRootDirectory ()
    {
        final List<AkaiMPC2000DirectoryEntry> entries = new ArrayList<> ();

        for (int i = 0; i < this.bootSector.rootEntries; i++)
        {
            final int entryOffset = this.rootDirectoryOffset + i * 32;
            if (entryOffset + 32 > this.diskImage.length)
                break;

            final byte firstByte = this.diskImage[entryOffset];
            // End of directory
            if (firstByte == 0x00)
                break;

            // Skip deleted entries (but we could read them if needed)
            if (firstByte == (byte) 0xE5)
                continue;

            final AkaiMPC2000DirectoryEntry entry = this.parseDirectoryEntry (entryOffset);
            if (entry != null)
                entries.add (entry);
        }

        return entries;
    }


    /**
     * Parse the disk.
     */
    private void parseDisk ()
    {
        final ByteBuffer buffer = ByteBuffer.wrap (this.diskImage);
        this.bootSector = AkaiMPC2000BootSector.parse (buffer);
        this.parseFAT ();
    }


    /**
     * Parse the File Allocation Table (FAT). Supports FAT12 and FAT16.
     * 
     */
    private void parseFAT ()
    {
        // Detect FAT-Type via number of clusters (official Microsoft-Method)
        final int rootDirSectors = (this.bootSector.rootEntries * 32 + this.bootSector.bytesPerSector - 1) / this.bootSector.bytesPerSector;

        final int dataSectors = this.bootSector.totalSectors - this.bootSector.reservedSectors - this.bootSector.numberOfFATs * this.bootSector.sectorsPerFAT - rootDirSectors;

        final int countOfClusters = dataSectors / this.bootSector.sectorsPerCluster;
        this.isFAT12 = countOfClusters < 4085;

        final int fatOffset = this.bootSector.reservedSectors * this.bootSector.bytesPerSector;

        if (this.isFAT12)
        {
            final int totalEntries = countOfClusters + 2;
            this.fat = new int [totalEntries];
            for (int i = 0; i < totalEntries; i++)
            {
                final int byteOffset = fatOffset + i * 3 / 2;
                final int word = (this.diskImage[byteOffset] & 0xFF) | ((this.diskImage[byteOffset + 1] & 0xFF) << 8);
                final int value = (i % 2 == 0) ? (word & 0x0FFF) : ((word >> 4) & 0x0FFF);
                // End-of-Chain (>=0xFF8) normalize to 0xFFFF
                this.fat[i] = value >= 0xFF8 ? 0xFFFF : value;
            }
        }
        else
        {
            // FAT16
            final int fatSizeBytes = this.bootSector.sectorsPerFAT * this.bootSector.bytesPerSector;
            final int totalClusters = fatSizeBytes / 2;
            this.fat = new int [totalClusters];
            final ByteBuffer buffer = ByteBuffer.wrap (this.diskImage, fatOffset, fatSizeBytes);
            buffer.order (ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < totalClusters; i++)
                this.fat[i] = buffer.getShort () & 0xFFFF;
        }
    }


    private AkaiMPC2000DirectoryEntry parseDirectoryEntry (final int offset)
    {
        final ByteBuffer buffer = ByteBuffer.wrap (this.diskImage, offset, 32);
        buffer.order (ByteOrder.LITTLE_ENDIAN);

        final AkaiMPC2000DirectoryEntry entry = new AkaiMPC2000DirectoryEntry ();

        final byte [] nameBytes = new byte [8];
        buffer.get (nameBytes);
        entry.name = new String (nameBytes).trim ();

        final byte [] extBytes = new byte [3];
        buffer.get (extBytes);
        entry.extension = new String (extBytes).trim ();

        entry.attributes = buffer.get () & 0xFF;
        entry.isDirectory = (entry.attributes & 0x10) != 0;
        entry.isVolumeLabel = (entry.attributes & 0x08) != 0;

        // Skip reserved bytes (10 bytes)
        buffer.position (buffer.position () + 10);

        buffer.getShort (); // Time
        buffer.getShort (); // Date
        entry.firstCluster = buffer.getShort () & 0xFFFF;
        entry.fileSize = buffer.getInt ();

        return entry;
    }


    /**
     * Read the content of a file.
     * 
     * @param entry The entry which points to the file
     * @param notifier Where to report errors
     * @return The raw data of the file
     * @throws IOException Could not read the file
     */
    public byte [] readFile (final AkaiMPC2000DirectoryEntry entry, final INotifier notifier) throws IOException
    {
        if (entry.fileSize == 0)
            return new byte [0];

        final ByteArrayOutputStream output = new ByteArrayOutputStream ();
        int cluster = entry.firstCluster;
        int bytesRemaining = entry.fileSize;
        final int maxIterations = 10000; // Safety limit
        int iterations = 0;

        while (cluster >= 2 && cluster < 0xFFF0 && bytesRemaining > 0 && iterations < maxIterations)
        {
            final int clusterOffset = this.dataAreaOffset + (cluster - 2) * this.bootSector.sectorsPerCluster * this.bootSector.bytesPerSector;

            if (clusterOffset < 0 || clusterOffset >= this.diskImage.length)
            {
                notifier.logError ("IDS_MPC2000_INVALID_CLUSTER_OFFSET", Integer.toString (clusterOffset), entry.getFullName ());
                return output.toByteArray ();
            }

            final int bytesToRead = Math.min (bytesRemaining, this.bootSector.sectorsPerCluster * this.bootSector.bytesPerSector);

            output.write (this.diskImage, clusterOffset, bytesToRead);
            bytesRemaining -= bytesToRead;

            // Get next cluster from FAT
            if (cluster >= this.fat.length)
                break;
            cluster = this.fat[cluster];
            iterations++;
        }

        return output.toByteArray ();
    }


    /**
     * Get the directory entries.
     * 
     * @return The entries
     */
    public List<AkaiMPC2000DirectoryEntry> getEntries ()
    {
        return this.entries;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append (this.bootSector.toString ());

        // Debug: show first few FAT entries
        sb.append ("First 10 FAT entries:\n");
        for (int i = 0; i < Math.min (10, this.fat.length); i++)
            sb.append (String.format ("FAT[%d] = 0x%04X\n", Integer.valueOf (i), Integer.valueOf (this.fat[i])));
        sb.append ("\n");

        sb.append ("=== Root Directory ===\n");
        sb.append (String.format ("Root directory at offset: 0x%X (%d)\n", Integer.valueOf (this.rootDirectoryOffset), Integer.valueOf (this.rootDirectoryOffset)));
        sb.append (String.format ("Data area at offset: 0x%X (%d)\n", Integer.valueOf (this.dataAreaOffset), Integer.valueOf (this.dataAreaOffset)));

        return sb.append ("\n").toString ();

    }
}