// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator4;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Reads the proprietary E-mu disk filesystem which the EOS samplers use on their CD-ROMs and hard
 * disks (and which their CD-ROM/SCSI emulators like the ZuluSCSI serve from raw image files). It
 * is a simple FAT-like filesystem of 512 byte blocks: a superblock, a cluster chain list, a root
 * directory of folders and dir-content blocks with the file entries. The layout was
 * reverse-engineered by the mpc2emu project from commercial E-mu CD-ROMs and EOS formatted disks,
 * cross-checked against the emu3fs Linux kernel module; see documentation/design/E4B_FORMAT.md.
 * All geometry is read from the superblock, so both the CD-ROM and the hard disk variant of the
 * filesystem are supported.
 *
 * @author Jürgen Moßgraber
 */
public class Emu3DiskImage
{
    /** The magic bytes of the superblock. */
    public static final byte []  MAGIC             = "EMU3".getBytes ();

    private static final int     BLOCK_SIZE        = 512;
    private static final int     ENTRY_SIZE        = 32;
    /** The number of dir-content block references of a folder entry. */
    private static final int     FOLDER_BLOCK_LIST = 7;
    /** The end-of-chain marker in the cluster list. */
    private static final int     LAST_CLUSTER      = 0x7FFF;
    /** The folder markers: 0x40 = user folder (CD), 0x80 = 'Default Folder' (hard disk). */
    private static final int     FOLDER_TYPE_USER  = 0x40;
    private static final int     FOLDER_TYPE_DEFAULT = 0x80;


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
        if (fileType == 0 || startCluster < 1 || numClusters < 1 || lastClusterBlocks < 1 && lastBlockBytes == 0)
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
}
