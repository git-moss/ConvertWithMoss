// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator4;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Reads and writes the proprietary E-mu disk filesystem which the EIII and the EOS samplers use on
 * their CD-ROMs and hard disks (and which their CD-ROM/SCSI emulators like the ZuluSCSI serve from
 * raw image files). It is a simple FAT-like filesystem of 512 byte blocks: a superblock, a cluster
 * chain list, a root directory of folders and dir-content blocks with the file entries. The layout
 * was reverse-engineered by the mpc2emu project from commercial E-mu CD-ROMs and EOS formatted
 * disks, cross-checked against the emu3fs Linux kernel module; see
 * documentation/design/E4B_FORMAT.md. All geometry is read from the superblock, so both the CD-ROM
 * and the hard disk variant of the filesystem are supported. Both sampler generations use the same
 * filesystem for their banks but fill a few of its fields differently, see {@link ImageLayout}.
 *
 * @author Jürgen Moßgraber
 */
public class Emu3DiskImage
{
    /** The magic bytes of the superblock. */
    private static final byte [] MAGIC               = "EMU3".getBytes ();

    private static final int     BLOCK_SIZE          = 512;
    private static final int     ENTRY_SIZE          = 32;
    /** The number of dir-content block references of a folder entry. */
    private static final int     FOLDER_BLOCK_LIST   = 7;
    /** The end-of-chain marker in the cluster list. */
    private static final int     LAST_CLUSTER        = 0x7FFF;
    /** The folder markers: 0x40 = user folder (CD), 0x80 = 'Default Folder' (hard disk). */
    private static final int     FOLDER_TYPE_USER    = 0x40;
    private static final int     FOLDER_TYPE_DEFAULT = 0x80;
    /** The file type of the operating system of the sampler. */
    private static final int     FILE_TYPE_SYS       = 0x80;
    /** The file type of a bank, which is what all volumes of both sampler generations use. */
    private static final int     FILE_TYPE_BANK      = 0x81;


    /** A file read from the image. */
    public static class ImageFile
    {
        private final String  name;
        private final byte [] content;


        /**
         * Constructor.
         *
         * @param name The name of the file
         * @param content The content of the file
         */
        public ImageFile (final String name, final byte [] content)
        {
            this.name = name;
            this.content = content;
        }


        /**
         * Get the name of the file.
         *
         * @return The name
         */
        public String getName ()
        {
            return this.name;
        }


        /**
         * Get the content of the file.
         *
         * @return The content
         */
        public byte [] getContent ()
        {
            return this.content;
        }
    }


    /**
     * Private constructor since this is a utility class.
     */
    private Emu3DiskImage ()
    {
        // Intentionally empty
    }


    /**
     * Check if the data starts with the magic bytes of the filesystem superblock.
     *
     * @param data The first bytes of a file, at least 4
     * @return True if it is an E-mu disk image
     */
    public static boolean isEmu3Image (final byte [] data)
    {
        return Emulator4Constants.hasMagic (data, 0, MAGIC);
    }


    /**
     * Read all files from the image. The files are not interpreted; the caller decides by their
     * content what they are (EOS images contain E4B banks, images of the older EIII samplers
     * contain EIII banks).
     *
     * @param imageFile The image file
     * @return The files of the image
     * @throws IOException The image could not be read or is malformed
     */
    public static List<ImageFile> readFiles (final File imageFile) throws IOException
    {
        try (final RandomAccessFile file = new RandomAccessFile (imageFile, "r"))
        {
            final byte [] superblock = readBlocks (file, 0, 1);
            if (!isEmu3Image (superblock))
                throw new IOException ("Not an E-mu disk image.");

            final int rootStart = (int) Emulator4Constants.getU32LE (superblock, 0x08);
            final int rootBlocks = (int) Emulator4Constants.getU32LE (superblock, 0x0C);
            final int fatStart = (int) Emulator4Constants.getU32LE (superblock, 0x18);
            final int fatBlocks = (int) Emulator4Constants.getU32LE (superblock, 0x1C);
            final int dataStart = (int) Emulator4Constants.getU32LE (superblock, 0x20);
            final int clusterSizeExtra = superblock[0x28] & 0xFF;
            final long totalBlocks = file.length () / BLOCK_SIZE;
            if (rootStart <= 0 || rootBlocks <= 0 || rootBlocks > 64 || fatStart <= 0 || fatBlocks <= 0 || fatBlocks > 64 || dataStart <= 0 || clusterSizeExtra < 1 || clusterSizeExtra > 12 || rootStart + rootBlocks > totalBlocks || fatStart + fatBlocks > totalBlocks)
                throw new IOException ("Malformed E-mu disk image superblock.");

            final long clusterBytes = 1L << 15 + clusterSizeExtra;
            final int blocksPerCluster = (int) (clusterBytes / BLOCK_SIZE);

            // The cluster chain list ('FAT')
            final byte [] fatData = readBlocks (file, fatStart, fatBlocks);
            final int [] fat = new int [fatData.length / 2];
            for (int i = 0; i < fat.length; i++)
                fat[i] = Emulator4Constants.getU16LE (fatData, i * 2);

            // Collect the dir-content blocks of all folders in the root directory
            final byte [] rootData = readBlocks (file, rootStart, rootBlocks);
            final List<Integer> dirContentBlocks = new ArrayList<> ();
            for (int offset = 0; offset + ENTRY_SIZE <= rootData.length; offset += ENTRY_SIZE)
            {
                final int folderType = rootData[offset + 17] & 0xFF;
                if (folderType != FOLDER_TYPE_USER && folderType != FOLDER_TYPE_DEFAULT)
                    continue;
                for (int i = 0; i < FOLDER_BLOCK_LIST; i++)
                {
                    final int block = Emulator4Constants.getU16LE (rootData, offset + 18 + i * 2);
                    if (block > 0 && block != 0xFFFF && block < totalBlocks)
                        dirContentBlocks.add (Integer.valueOf (block));
                }
            }

            // Read the file entries of all dir-content blocks
            final List<ImageFile> files = new ArrayList<> ();
            for (final Integer dirContentBlock: dirContentBlocks)
            {
                final byte [] entries = readBlocks (file, dirContentBlock.intValue (), 1);
                for (int offset = 0; offset + ENTRY_SIZE <= entries.length; offset += ENTRY_SIZE)
                {
                    final ImageFile imageFile2 = readFileEntry (file, entries, offset, fat, dataStart, blocksPerCluster, clusterBytes);
                    if (imageFile2 != null)
                        files.add (imageFile2);
                }
            }
            return files;
        }
    }


    /**
     * Read the file described by one 32 byte dir-content entry.
     *
     * @param file The image file
     * @param entries The content of the dir-content block
     * @param offset The offset of the entry in the block
     * @param fat The cluster chain list
     * @param dataStart The first block of the data area
     * @param blocksPerCluster The number of blocks of a cluster
     * @param clusterBytes The size of a cluster in bytes
     * @return The file or null if the entry is empty or malformed
     * @throws IOException Could not read the image
     */
    private static ImageFile readFileEntry (final RandomAccessFile file, final byte [] entries, final int offset, final int [] fat, final int dataStart, final int blocksPerCluster, final long clusterBytes) throws IOException
    {
        final int startCluster = Emulator4Constants.getU16LE (entries, offset + 18);
        final int numClusters = Emulator4Constants.getU16LE (entries, offset + 20);
        final int lastClusterBlocks = Emulator4Constants.getU16LE (entries, offset + 22);
        final int lastBlockBytes = Emulator4Constants.getU16LE (entries, offset + 24);
        final int fileType = entries[offset + 26] & 0xFF;
        // A system file holds the operating system of the sampler - a memory dump whose content
        // can look like a sound bank; the samplers do not offer it as one either
        if (fileType == 0 || fileType == FILE_TYPE_SYS || startCluster < 1 || numClusters < 1 || lastClusterBlocks < 1 && lastBlockBytes == 0)
            return null;

        // The size of the data in the last cluster: a partially filled last block still counts
        // as a whole block, the used bytes of it are stored separately
        final long lastClusterBytes = Math.min ((lastClusterBlocks - 1L) * BLOCK_SIZE + (lastBlockBytes > 0 ? lastBlockBytes : BLOCK_SIZE), clusterBytes);
        final long size = (numClusters - 1L) * clusterBytes + lastClusterBytes;
        if (size <= 0 || size > Integer.MAX_VALUE)
            return null;

        final byte [] content = new byte [(int) size];
        final Set<Integer> visited = new HashSet<> ();
        int cluster = startCluster;
        long position = 0;
        for (int i = 0; i < numClusters; i++)
        {
            if (cluster < 1 || cluster >= fat.length || !visited.add (Integer.valueOf (cluster)))
                return null;
            final long imageOffset = (dataStart + (cluster - 1L) * blocksPerCluster) * BLOCK_SIZE;
            final int length = (int) Math.min (clusterBytes, size - position);
            if (imageOffset + length > file.length ())
                return null;
            file.seek (imageOffset);
            file.readFully (content, (int) position, length);
            position += length;
            if (i < numClusters - 1)
            {
                final int next = fat[cluster];
                cluster = next == LAST_CLUSTER ? -1 : next;
            }
        }

        return new ImageFile (Emulator4Constants.decodeName (entries, offset), content);
    }


    /**
     * Read a number of 512 byte blocks from the image.
     *
     * @param file The image file
     * @param startBlock The first block to read
     * @param numBlocks The number of blocks to read
     * @return The data
     * @throws IOException Could not read the blocks
     */
    private static byte [] readBlocks (final RandomAccessFile file, final int startBlock, final int numBlocks) throws IOException
    {
        final byte [] data = new byte [numBlocks * BLOCK_SIZE];
        file.seek ((long) startBlock * BLOCK_SIZE);
        file.readFully (data);
        return data;
    }

    // -----------------------------------------------------------
    // Writing - the CD-ROM variant of the filesystem with its fixed geometry, which is
    // what the firmware expects of a CD-ROM volume (a computed 'tighter' layout is not
    // mountable).


    /** The number of file entries which one dir-content block holds. */
    private static final int    ENTRIES_PER_BLOCK   = BLOCK_SIZE / ENTRY_SIZE;
    /** The first block of the cluster chain list, which follows the superblock and its padding. */
    private static final int    FAT_START           = 2;
    /** The name of the folder which holds the written files. */
    public static final String  DEFAULT_FOLDER_NAME = "Default Folder";


    /**
     * The geometry which is written. All of it is read back from the superblock - the volumes in
     * the wild differ in it - but the firmware checks several of the other fields of a volume, so
     * each layout copies the CD-ROMs of the sampler generation it is meant for.
     */
    public enum ImageLayout
    {
        /**
         * The layout of the EOS CD-ROMs, which matches the hardware-verified reference builder of
         * the mpc2emu project.
         */
        EOS(5, 4, 125, Emulator4Constants.FORM_TYPE, (byte) 0x00, true),
        /**
         * The layout of the Emulator IIIX library CD-ROMs, which the EIII, EIIIX and ESI samplers
         * read. Their file entries carry no form type and their superblock holds the size of the
         * medium where the EOS volumes have a flag byte.
         */
        EMULATOR_3(7, 6, 192, new byte [4], (byte) 0x80, false);


        private final int     fatBlocks;
        private final int     rootBlocks;
        private final int     dirBlocks;
        private final byte [] fileType;
        private final byte    paddingFill;
        private final boolean hasEosFlag;


        /**
         * Constructor.
         *
         * @param fatBlocks The number of blocks of the cluster chain list
         * @param rootBlocks The number of blocks of the root directory
         * @param dirBlocks The number of dir-content blocks
         * @param fileType The 4 bytes which every file entry carries behind its block counts
         * @param paddingFill The byte with which the padding block behind the superblock is filled
         * @param hasEosFlag Whether the superblock carries the flag byte of the EOS volumes
         */
        private ImageLayout (final int fatBlocks, final int rootBlocks, final int dirBlocks, final byte [] fileType, final byte paddingFill, final boolean hasEosFlag)
        {
            this.fatBlocks = fatBlocks;
            this.rootBlocks = rootBlocks;
            this.dirBlocks = dirBlocks;
            this.fileType = fileType;
            this.paddingFill = paddingFill;
            this.hasEosFlag = hasEosFlag;
        }


        /**
         * Get the number of files which fit into one image. All of them go into the single folder
         * of the image, which references at most 7 dir-content blocks of 16 entries each.
         *
         * @return The number of files
         */
        public int getMaximumFiles ()
        {
            return ENTRIES_PER_BLOCK * Math.min (FOLDER_BLOCK_LIST, this.dirBlocks);
        }


        /**
         * Get the number of clusters which the cluster chain list can address; its entry 0 is the
         * reserved media descriptor.
         *
         * @return The number of clusters
         */
        private int getMaximumClusters ()
        {
            return this.fatBlocks * (BLOCK_SIZE / 2) - 1;
        }


        private int getRootStart ()
        {
            return FAT_START + this.fatBlocks;
        }


        private int getDirStart ()
        {
            return this.getRootStart () + this.rootBlocks;
        }


        private int getDataStart ()
        {
            return this.getDirStart () + this.dirBlocks;
        }
    }


    /**
     * Write a CD-ROM image containing the given files. The image can be renamed to e.g. CD1.iso on
     * the SD card of a SCSI emulator like the ZuluSCSI to be served as a CD-ROM.
     *
     * @param outputFile The image file to write
     * @param files The files to store, at most {@link ImageLayout#getMaximumFiles ()}
     * @param layout The geometry to write
     * @param folderName The name of the folder which holds the files
     * @throws IOException Could not write the image or the files are too large for one image
     */
    public static void writeImage (final File outputFile, final List<ImageFile> files, final ImageLayout layout, final String folderName) throws IOException
    {
        if (files.size () > layout.getMaximumFiles ())
            throw new IOException ("Too many files for one image: " + files.size ());

        // The smallest cluster size (512 KB, 1 MB or 2 MB) which keeps the clusters of all files
        // in the FAT. 512 KB is preferred, larger clusters caused read errors on real hardware
        int clusterSizeExtra = -1;
        long clusterBytes = 0;
        long numClusters = 0;
        for (int extra = 4; extra <= 6; extra++)
        {
            clusterBytes = 1L << 15 + extra;
            numClusters = 0;
            for (final ImageFile file: files)
                numClusters += (file.getContent ().length + clusterBytes - 1) / clusterBytes;
            if (numClusters <= layout.getMaximumClusters ())
            {
                clusterSizeExtra = extra;
                break;
            }
        }
        // Can never happen but makes SonarQube happy
        if (clusterBytes == 0)
            throw new IOException ("Cluster bytes are 0?!");
        if (clusterSizeExtra < 0)
            throw new IOException ("The files are too large for one image.");
        final int blocksPerCluster = (int) (clusterBytes / BLOCK_SIZE);
        final long totalBlocks = layout.getDataStart () + numClusters * blocksPerCluster;
        // The files are listed in as many dir-content blocks as they need
        final int numDirBlocks = Math.max (1, (files.size () + ENTRIES_PER_BLOCK - 1) / ENTRIES_PER_BLOCK);

        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (outputFile.toPath ())))
        {
            out.write (createSuperblock (layout, totalBlocks, numClusters, clusterSizeExtra));
            out.write (createPaddingBlock (layout, numDirBlocks));
            out.write (createFat (layout, files, clusterBytes));
            out.write (createRootDirectory (layout, folderName, numDirBlocks));
            out.write (createDirContent (layout, files, clusterBytes, numDirBlocks));
            out.write (new byte [(layout.dirBlocks - numDirBlocks) * BLOCK_SIZE]);

            // The file data, each file padded to a full cluster
            for (final ImageFile file: files)
            {
                final byte [] content = file.getContent ();
                out.write (content);
                final int pad = (int) ((clusterBytes - content.length % clusterBytes) % clusterBytes);
                if (pad > 0)
                    out.write (new byte [pad]);
            }
        }
    }


    /**
     * Create the superblock.
     *
     * @param layout The geometry to write
     * @param totalBlocks The total number of blocks of the image
     * @param numClusters The total number of data clusters
     * @param clusterSizeExtra The cluster size (bytes = 1 &lt;&lt; (15 + value))
     * @return The 512 byte superblock
     */
    private static byte [] createSuperblock (final ImageLayout layout, final long totalBlocks, final long numClusters, final int clusterSizeExtra)
    {
        final byte [] superblock = new byte [BLOCK_SIZE];
        System.arraycopy (MAGIC, 0, superblock, 0, 4);
        Emulator4Constants.putU32LE (superblock, 0x04, totalBlocks - 1);
        Emulator4Constants.putU32LE (superblock, 0x08, layout.getRootStart ());
        Emulator4Constants.putU32LE (superblock, 0x0C, layout.rootBlocks);
        Emulator4Constants.putU32LE (superblock, 0x10, layout.getDirStart ());
        Emulator4Constants.putU32LE (superblock, 0x14, layout.dirBlocks);
        Emulator4Constants.putU32LE (superblock, 0x18, FAT_START);
        Emulator4Constants.putU32LE (superblock, 0x1C, layout.fatBlocks);
        Emulator4Constants.putU32LE (superblock, 0x20, layout.getDataStart ());
        Emulator4Constants.putU32LE (superblock, 0x24, numClusters);
        // Flag bytes present in every working reference image; the firmware checks for them
        superblock[0x28] = (byte) clusterSizeExtra;
        superblock[0x29] = 0x01;
        if (layout.hasEosFlag)
            superblock[0x2D] = 0x08;
        else
            // The volumes of the EIII samplers store the size of the medium here instead, which on
            // a pressed CD-ROM is a bit larger than the volume itself
            Emulator4Constants.putU32LE (superblock, 0x2A, totalBlocks);
        superblock[0x32] = 0x01;
        superblock[0x33] = 0x0D;
        // The checksum is verified at mount time; without it the volume does not mount
        int checksum = 0;
        for (int i = 0; i < 0x1FE; i += 2)
            checksum = checksum + Emulator4Constants.getU16LE (superblock, i) & 0xFFFF;
        Emulator4Constants.putU16LE (superblock, 0x1FE, checksum);
        return superblock;
    }


    /**
     * Create the padding block which follows the superblock. It holds the number of the first
     * dir-content block which is still free; the volumes of the EIII samplers fill its remainder
     * with a constant marker byte.
     *
     * @param layout The geometry to write
     * @param numDirBlocks The number of dir-content blocks which are in use
     * @return The 512 byte padding block
     */
    private static byte [] createPaddingBlock (final ImageLayout layout, final int numDirBlocks)
    {
        final byte [] padding = new byte [BLOCK_SIZE];
        Arrays.fill (padding, 4, BLOCK_SIZE, layout.paddingFill);
        Emulator4Constants.putU32LE (padding, 0, layout.getDirStart () + (long) numDirBlocks);
        return padding;
    }


    /**
     * Create the cluster chain list. Every file occupies a sequential run of clusters, starting at
     * cluster 1.
     *
     * @param layout The geometry to write
     * @param files The files
     * @param clusterBytes The size of a cluster in bytes
     * @return The FAT blocks
     */
    private static byte [] createFat (final ImageLayout layout, final List<ImageFile> files, final long clusterBytes)
    {
        final byte [] fat = new byte [layout.fatBlocks * BLOCK_SIZE];
        // Entry 0 is the reserved media descriptor
        Emulator4Constants.putU16LE (fat, 0, 0x8000);
        int cluster = 1;
        for (final ImageFile file: files)
        {
            final int numClusters = (int) ((file.getContent ().length + clusterBytes - 1) / clusterBytes);
            for (int i = 0; i < numClusters; i++)
            {
                Emulator4Constants.putU16LE (fat, cluster * 2, i < numClusters - 1 ? cluster + 1 : LAST_CLUSTER);
                cluster++;
            }
        }
        return fat;
    }


    /**
     * Create the root directory with the single folder which references the dir-content blocks.
     *
     * @param layout The geometry to write
     * @param folderName The name of the folder
     * @param numDirBlocks The number of dir-content blocks which the folder holds
     * @return The root directory blocks
     */
    private static byte [] createRootDirectory (final ImageLayout layout, final String folderName, final int numDirBlocks)
    {
        final byte [] root = new byte [layout.rootBlocks * BLOCK_SIZE];
        Emulator4Constants.encodeName (root, 0, folderName);
        root[17] = FOLDER_TYPE_USER;
        for (int i = 0; i < FOLDER_BLOCK_LIST; i++)
            Emulator4Constants.putU16LE (root, 18 + i * 2, i < numDirBlocks ? layout.getDirStart () + i : 0xFFFF);
        return root;
    }


    /**
     * Create the dir-content blocks with one entry per file.
     *
     * @param layout The geometry to write
     * @param files The files
     * @param clusterBytes The size of a cluster in bytes
     * @param numDirBlocks The number of dir-content blocks to fill
     * @return The dir-content blocks
     */
    private static byte [] createDirContent (final ImageLayout layout, final List<ImageFile> files, final long clusterBytes, final int numDirBlocks)
    {
        final byte [] block = new byte [numDirBlocks * BLOCK_SIZE];
        int cluster = 1;
        for (int i = 0; i < files.size (); i++)
        {
            final ImageFile file = files.get (i);
            // The entries fill one block after the other but their numbers run through all of them
            final int offset = i / ENTRIES_PER_BLOCK * BLOCK_SIZE + i % ENTRIES_PER_BLOCK * ENTRY_SIZE;
            final long size = file.getContent ().length;
            final int numClusters = (int) ((size + clusterBytes - 1) / clusterBytes);
            final long lastClusterBytes = size - (numClusters - 1L) * clusterBytes;
            // A partially filled last block counts as a whole block, otherwise the firmware
            // does not read the tail of the file
            final int lastClusterBlocks = (int) ((lastClusterBytes + BLOCK_SIZE - 1) / BLOCK_SIZE);
            // The bytes used in the last block are derived from that block count and are therefore
            // always 1..BLOCK_SIZE. A plain remainder would be 0 for a file whose last cluster is
            // an exact multiple of the block size, which makes the firmware read one block too
            // few and abort the load of that file with an end of file error
            final int lastBlockBytes = (int) (lastClusterBytes - (lastClusterBlocks - 1L) * BLOCK_SIZE);

            Emulator4Constants.encodeName (block, offset, file.getName ());
            block[offset + 17] = (byte) i;
            Emulator4Constants.putU16LE (block, offset + 18, cluster);
            Emulator4Constants.putU16LE (block, offset + 20, numClusters);
            Emulator4Constants.putU16LE (block, offset + 22, lastClusterBlocks);
            Emulator4Constants.putU16LE (block, offset + 24, lastBlockBytes);
            block[offset + 26] = (byte) FILE_TYPE_BANK;
            System.arraycopy (layout.fileType, 0, block, offset + 28, 4);
            cluster += numClusters;
        }
        return block;
    }
}
