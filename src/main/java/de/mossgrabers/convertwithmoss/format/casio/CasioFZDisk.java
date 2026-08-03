// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.casio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.mossgrabers.convertwithmoss.file.hfe.DiskImageBuilder;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile.HfeVersion;
import de.mossgrabers.convertwithmoss.file.hfe.Sector;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Access to the files on a Casio FZ-1/FZ-10M/FZ-20M floppy disk. The disk is double sided with 80
 * tracks, 8 sectors per track and 1,024 bytes per sector. Sector 0 contains the disk name and the
 * cluster allocation table, sector 1 the directory with up to 64 entries. A file consists of a
 * file head sector, which contains up to 64 start/end sector ranges and the object counts, and
 * the content sectors. Additionally, bare dump files (fzf = full dump, fzv = voice, fzb = bank)
 * are supported, which contain the file head followed by the content blocks in sequential order.
 *
 * @author Jürgen Moßgraber
 */
public class CasioFZDisk
{
    /** The size of a sector in bytes. */
    public static final int  SECTOR_SIZE      = 1024;
    /** The number of sectors of a disk. */
    public static final int  NUM_SECTORS      = 1280;
    /** The size of a disk image in bytes. */
    public static final int  DISK_SIZE        = NUM_SECTORS * SECTOR_SIZE;

    /** File type: full dump data. */
    public static final int  TYPE_FULL_DUMP   = 0;
    /** File type: voice data. */
    public static final int  TYPE_VOICE       = 1;
    /** File type: bank data. */
    public static final int  TYPE_BANK        = 2;
    /** File type: effect data. */
    public static final int  TYPE_EFFECT      = 3;
    /** File type: sequence data. */
    public static final int  TYPE_SEQUENCE    = 4;
    /** File type: expanded program data. */
    public static final int  TYPE_PROGRAM     = 5;

    private static final int MAX_DIR_ENTRIES  = 64;
    private static final int MAX_BLOCK_POINTERS = 64;

    private String           diskName         = "";
    private final List<CasioFZFile> files     = new ArrayList<> ();


    /**
     * A file on a FZ disk: the name, the type and its content.
     *
     * @param name The name of the file
     * @param type The type of the file, one of the TYPE_* constants
     * @param head The file head sector (1,024 bytes)
     * @param content The content sectors
     */
    public record CasioFZFile (String name, int type, byte [] head, byte [] content)
    {
        /**
         * Get one of the three object counters at the end of the file head. Depending on the file
         * type these contain the number of voices, banks and wave blocks.
         *
         * @param index The index of the counter (0-2)
         * @return The value of the counter
         */
        public int getCounter (final int index)
        {
            return CasioFZVoice.readUnsigned16 (this.head, 1018 + index * 2);
        }
    }


    /**
     * Constructor. Reads the given disk image (img, hfe) or bare dump file (fzf, fzv, fzb).
     *
     * @param sourceFile The file to read
     * @throws IOException Could not read the file
     */
    public CasioFZDisk (final File sourceFile) throws IOException
    {
        final String fileName = sourceFile.getName ().toLowerCase (Locale.US);

        if (fileName.endsWith (".hfe"))
        {
            this.parseDiskImage (readHfeImage (sourceFile));
            return;
        }

        if (fileName.endsWith (".img") || fileName.endsWith (".ima"))
        {
            final byte [] content = Files.readAllBytes (sourceFile.toPath ());
            if (content.length != DISK_SIZE)
                throw new IOException (Functions.getMessage ("IDS_FZ_UNEXPECTED_IMAGE_SIZE", Integer.toString (content.length), Integer.toString (DISK_SIZE)));
            this.parseDiskImage (content);
        }
        else
        {
            // A bare dump file: the file head followed by the content blocks
            final byte [] dump = Files.readAllBytes (sourceFile.toPath ());
            if (dump.length < 2 * SECTOR_SIZE || dump.length % SECTOR_SIZE != 0)
                throw new IOException (Functions.getMessage ("IDS_FZ_MALFORMED_DUMP_FILE", sourceFile.getName ()));
            final int type;
            if (fileName.endsWith (".fzv"))
                type = TYPE_VOICE;
            else if (fileName.endsWith (".fzb"))
                type = TYPE_BANK;
            else
                type = TYPE_FULL_DUMP;
            final byte [] head = new byte [SECTOR_SIZE];
            System.arraycopy (dump, 0, head, 0, SECTOR_SIZE);
            final byte [] fileContent = new byte [dump.length - SECTOR_SIZE];
            System.arraycopy (dump, SECTOR_SIZE, fileContent, 0, fileContent.length);
            this.diskName = FileUtils.getNameWithoutType (sourceFile);
            this.files.add (new CasioFZFile (this.diskName, type, head, fileContent));
        }
    }


    /**
     * Get the name of the disk.
     *
     * @return The name
     */
    public String getDiskName ()
    {
        return this.diskName;
    }


    /**
     * Get the files of the disk.
     *
     * @return The files
     */
    public List<CasioFZFile> getFiles ()
    {
        return this.files;
    }


    /**
     * Parse the directory and files of a disk image.
     *
     * @param image The disk image
     * @throws IOException The image is malformed
     */
    private void parseDiskImage (final byte [] image) throws IOException
    {
        this.diskName = CasioFZVoice.readName (image, 0);

        for (int entry = 0; entry < MAX_DIR_ENTRIES; entry++)
        {
            final int entryOffset = SECTOR_SIZE + entry * 16;
            if (image[entryOffset] == 0)
                continue;
            final String fileName = CasioFZVoice.readName (image, entryOffset);
            final int ext = CasioFZVoice.readUnsigned16 (image, entryOffset + 12);
            final int startSector = CasioFZVoice.readUnsigned16 (image, entryOffset + 14);

            // Files which are continued from the 1st disk on a 2nd disk cannot be stitched
            // together, the counters and addresses of the head on the 1st disk cover both parts
            final int diskNumber = ext >> 8;
            if (diskNumber != 0)
                continue;

            if (startSector < 2 || startSector >= NUM_SECTORS)
                throw new IOException (Functions.getMessage ("IDS_FZ_MALFORMED_DIRECTORY"));

            // Read the file head and the content sectors which it points to
            final byte [] head = new byte [SECTOR_SIZE];
            System.arraycopy (image, startSector * SECTOR_SIZE, head, 0, SECTOR_SIZE);

            final List<byte []> contentSectors = new ArrayList<> ();
            int contentLength = 0;
            for (int pointer = 0; pointer < MAX_BLOCK_POINTERS; pointer++)
            {
                final int rangeStart = CasioFZVoice.readUnsigned16 (head, pointer * 4);
                final int rangeEnd = CasioFZVoice.readUnsigned16 (head, pointer * 4 + 2);
                if (rangeStart == 0 && rangeEnd == 0)
                    break;
                if (rangeStart < 2 || rangeEnd < rangeStart || rangeEnd >= NUM_SECTORS)
                    throw new IOException (Functions.getMessage ("IDS_FZ_MALFORMED_DIRECTORY"));
                for (int sector = rangeStart; sector <= rangeEnd; sector++)
                {
                    final byte [] sectorData = new byte [SECTOR_SIZE];
                    System.arraycopy (image, sector * SECTOR_SIZE, sectorData, 0, SECTOR_SIZE);
                    contentSectors.add (sectorData);
                    contentLength += SECTOR_SIZE;
                }
            }

            final byte [] content = new byte [contentLength];
            int offset = 0;
            for (final byte [] sectorData: contentSectors)
            {
                System.arraycopy (sectorData, 0, content, offset, SECTOR_SIZE);
                offset += SECTOR_SIZE;
            }

            this.files.add (new CasioFZFile (fileName, ext & 0xFF, head, content));
        }
    }


    /**
     * Read an HFE floppy image file and decode it into a flat disk image.
     *
     * @param sourceFile The HFE file
     * @return The flat disk image
     * @throws IOException Could not read or decode the file
     */
    private static byte [] readHfeImage (final File sourceFile) throws IOException
    {
        final HfeFile hfeFile = new HfeFile (sourceFile);
        final HfeVersion hfeVersion = hfeFile.getHfeVersion ();
        if (hfeVersion != HfeVersion.VERSION_1)
            throw new IOException (Functions.getMessage ("IDS_HFE_VERSION_NOT_SUPPORTED", hfeVersion == HfeVersion.VERSION_2 ? "v2" : "v3"));

        final List<Sector> allSectors = hfeFile.decodeMfmSectors ();
        final byte [] image = DiskImageBuilder.buildImage (allSectors, hfeFile.getNumTracks (), hfeFile.getNumSides (), 8, SECTOR_SIZE, false);
        if (image.length != DISK_SIZE)
            throw new IOException (Functions.getMessage ("IDS_FZ_UNEXPECTED_IMAGE_SIZE", Integer.toString (image.length), Integer.toString (DISK_SIZE)));
        return image;
    }


    /**
     * Create a disk image which contains one full dump file.
     *
     * @param diskName The name of the disk and of the dump file
     * @param head The file head sector (1,024 bytes), the block pointers are filled in
     * @param content The content sectors, must be a multiple of the sector size
     * @return The disk image
     * @throws IOException The content does not fit onto the disk
     */
    public static byte [] createDiskImage (final String diskName, final byte [] head, final byte [] content) throws IOException
    {
        final int contentSectors = content.length / SECTOR_SIZE;
        // Sectors 0 and 1 are the disk header and the directory, sector 2 the file head
        if (3 + contentSectors > NUM_SECTORS)
            throw new IOException (Functions.getMessage ("IDS_FZ_DISK_FULL"));

        final byte [] image = new byte [DISK_SIZE];

        // The disk identification
        CasioFZVoice.writeName (image, 0, diskName);
        image[14] = 2;
        // An empty password
        CasioFZVoice.writeName (image, 16, "");

        // The cluster allocation table: a bit for each used sector
        final int usedSectors = 3 + contentSectors;
        for (int sector = 0; sector < usedSectors; sector++)
            image[128 + sector / 8] |= (byte) (1 << (sector % 8));

        // The directory with one entry for the full dump file
        CasioFZVoice.writeName (image, SECTOR_SIZE, diskName);
        CasioFZVoice.writeUnsigned16 (image, SECTOR_SIZE + 12, TYPE_FULL_DUMP);
        CasioFZVoice.writeUnsigned16 (image, SECTOR_SIZE + 14, 2);

        // The file head with a single continuous sector range
        CasioFZVoice.writeUnsigned16 (head, 0, 3);
        CasioFZVoice.writeUnsigned16 (head, 2, 3 + contentSectors - 1);
        System.arraycopy (head, 0, image, 2 * SECTOR_SIZE, SECTOR_SIZE);

        System.arraycopy (content, 0, image, 3 * SECTOR_SIZE, content.length);

        return image;
    }
}
