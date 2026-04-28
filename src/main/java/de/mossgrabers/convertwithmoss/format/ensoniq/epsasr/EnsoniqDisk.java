// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.epsasr;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.hfe.DiskImageBuilder;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile.HfeVersion;
import de.mossgrabers.convertwithmoss.file.hfe.Sector;
import de.mossgrabers.tools.ui.Functions;


/**
 * Reads Ensoniq disk images. Supports RAW, HFE, GKH, EDE and EDA encoding formats.
 *
 * @author Jürgen Moßgraber
 */
public class EnsoniqDisk
{
    private static final int REMOVABLE_TYPE_CD_ROM   = 0x00;
    private static final int REMOVABLE_TYPE_DISKETTE = 0x80;

    private static final int FAT_ENTRY_SIZE          = 3;
    private static final int BLOCK_SIZE              = 512;
    private static final int DIRECTORY_ENTRY_SIZE    = 26;


    enum EncodingType
    {
        // IMG
        RAW,
        GKH,
        EFE,
        EDE,
        EDA,
        HFE
    }


    private final File                  sourceFile;
    private byte []                     fileContent;
    // EDE / EDA only
    private byte []                     skipTable;

    private int                         bytesPerBlock = BLOCK_SIZE;
    private String                      diskID;
    private String                      diskLabel     = "";
    private EncodingType                encodingType;
    private String                      description;
    private int                         sizeBlocks;
    private String                      osVersion;
    private String                      minimumRomVersion;

    private EnsoniqFile                 rootDirectory = null;
    private final Map<Integer, Integer> fatCache      = new HashMap<> ();


    /**
     * Constructor.
     *
     * @param sourceFile The source file to parse
     * @throws IOException Could not parse the file
     */
    public EnsoniqDisk (final File sourceFile) throws IOException
    {
        this.sourceFile = sourceFile;
        if (this.getSourceFile ().getName ().toLowerCase ().endsWith (".hfe"))
        {
            this.encodingType = EncodingType.HFE;
            this.parseHfeImage ();
            return;
        }

        this.fileContent = Files.readAllBytes (sourceFile.toPath ());
        this.encodingType = this.detectEncoding ();
        switch (this.encodingType)
        {
            case GKH:
                this.parseGkhImage ();
                break;
            case EFE:
                this.parseEfeImage ();
                break;
            case EDE:
                this.parseEdeImage ();
                break;
            case EDA:
                this.parseEdaImage ();
                break;
            // IMG
            default:
            case RAW:
                this.parseEnsoniqImage ();
                break;
        }
    }


    private EncodingType detectEncoding () throws IOException
    {
        final int chunk = (int) Math.min (5L * BLOCK_SIZE, this.fileContent.length);
        final byte [] d = this.readImageData (0, chunk);

        // GKH: starts with 'TDDF'
        if (d.length >= 4 && d[0] == 'T' && d[1] == 'D' && d[2] == 'D' && d[3] == 'F')
            return EncodingType.GKH;

        if (d[0] == 0x0D && d[1] == 0x0A)
        {
            // EFE: CRLF at [0],[78],[157]; IBM EOF 0x1A at [159]
            if (d.length > 49 && d[47] == 0x0D && d[48] == 0x0A && (d[49] & 0xFF) == 0x1A)
                return EncodingType.EFE;

            // EDE: CRLF at [0],[78],[157]; IBM EOF 0x1A at [159]
            if (d.length > 159 && d[78] == 0x0D && d[79] == 0x0A && d[157] == 0x0D && d[158] == 0x0A && (d[159] & 0xFF) == 0x1A)
                return EncodingType.EDE;

            // EDA: CRLF at [0],[78],[93]; IBM EOF 0x1A at [95]
            if (d.length > 95 && d[78] == 0x0D && d[79] == 0x0A && d[93] == 0x0D && d[94] == 0x0A && (d[95] & 0xFF) == 0x1A)
                return EncodingType.EDA;
        }

        // RAW: 'ID' at byte 550; 'DR' at byte 2558
        if (d.length >= 2560 && d[550] == 'I' && d[551] == 'D' && d[2558] == 'D' && d[2559] == 'R')
            return EncodingType.RAW;

        throw new IOException (Functions.getMessage ("IDS_EPS_UNKNOWN_DISK_FORMAT", this.getSourceFile ().getAbsolutePath ()));
    }


    /**
     * Parses the GKH header to locate the embedded Ensoniq image. GKH uses Intel (little-endian)
     * byte ordering for its tag values.
     *
     * @throws IOException Could not read the image
     */
    private void parseGkhImage () throws IOException
    {
        final byte [] minHdr = this.readImageData (0, 8);
        final ByteBuffer b = ByteBuffer.wrap (minHdr).order (ByteOrder.LITTLE_ENDIAN);

        final byte [] fileInd = new byte [4];
        b.get (fileInd);
        if (!"TDDF".equals (new String (fileInd, "ISO-8859-1")))
            throw new IOException (Functions.getMessage ("IDS_EPS_INVALID_GKH"));
        final int nti = b.get () & 0xFF;
        if (nti != 'I')
            throw new IOException (Functions.getMessage ("IDS_EPS_UNKNOWN_GKH_NUMBER", Character.toString ((char) nti)));
        final int version = b.get () & 0xFF;
        if (version != 1)
            throw new IOException (Functions.getMessage ("IDS_EPS_UNKNOWN_GKH_VERSION", Integer.toString (version)));
        final int numTags = b.getShort () & 0xFFFF;

        final int TAG_SIZE = 10;
        final byte [] hdr = this.readImageData (0, 8 + numTags * TAG_SIZE);

        int rawDataOffset = -1;
        int rawDataLength = -1;
        for (int i = 0; i < numTags; i++)
        {
            final int base = 8 + i * TAG_SIZE;
            final ByteBuffer tb = ByteBuffer.wrap (hdr, base, TAG_SIZE).order (ByteOrder.LITTLE_ENDIAN);
            final int tagType = tb.get () & 0xFF;
            // tagFormat – not needed here
            tb.get ();
            if (tagType == 0x0B)
            {
                rawDataLength = tb.getInt ();
                rawDataOffset = tb.getInt ();
                break;
            }
        }

        if (rawDataOffset < 0)
            throw new IOException (Functions.getMessage ("IDS_EPS_UNKNOWN_GKH_MISSES_IMAGE_LOCATION"));

        this.fileContent = Arrays.copyOfRange (this.fileContent, rawDataOffset, rawDataOffset + rawDataLength);
        this.parseEnsoniqImage ();
    }


    /**
     * Parses the 512-byte EFE header.
     *
     * @throws IOException Could not read the image
     */
    private void parseEfeImage () throws IOException
    {
        final ByteBuffer buffer = this.readHeader ();

        // Read the description text
        final String [] metadata = StreamUtils.readAscii (buffer, (byte) 0x1A).split ("\r\n");

        this.description = metadata.length > 1 ? metadata[1].trim () : "";
        if (this.description.length () < 33)
            throw new IOException (Functions.getMessage ("IDS_EPS_WRONG_STRUCTURE"));

        final String instrumentName = this.description.substring (16, 28).trim ();
        final String type = this.description.substring (28, this.description.length ()).trim ();

        this.description = this.description.substring (0, 16).trim ();
        if (this.description.endsWith (":"))
            this.description = this.description.substring (0, this.description.length () - 1);

        if (!"Instr".equals (type))
        {
            // Unsupported type will be shown in log
            this.description = type;
            return;
        }

        buffer.position (0x34);
        final int numberOfBlocks = buffer.getShort ();
        // This only represents the information from the original disk. The EFE file always contains
        // the whole file. Therefore, the number of contiguous blocks is set to the number of
        // blocks.
        @SuppressWarnings("unused")
        final int numberOfContiguousBlocks = buffer.getShort ();

        // The rest of the header data is not relevant

        // Extract the raw file
        this.fileContent = Arrays.copyOfRange (this.fileContent, BLOCK_SIZE, BLOCK_SIZE + numberOfBlocks * BLOCK_SIZE);
        this.rootDirectory = new EnsoniqFile (this, 0, instrumentName, EnsoniqFile.TYPE_EPS_INST, numberOfBlocks, numberOfBlocks, 0, null);
    }


    /**
     * Parses the 512-byte EDE header (compressed EPS floppy image). Layout after byte 160: 200-byte
     * skip table | 149 unknown | 1 compressionType | 2 diskType.
     *
     * @throws IOException Could not read the image
     */
    private void parseEdeImage () throws IOException
    {
        final ByteBuffer buffer = this.readHeader ();

        // Read the description text
        final String [] metadata = StreamUtils.readAscii (buffer, (byte) 0x1A).split ("\r\n");
        this.description = metadata.length > 1 ? metadata[1].trim () : "";

        // Each BIT represents a block on the disk. If the bit is set then that block on the
        // original disk was empty otherwise that block had real data in it.
        this.skipTable = StreamUtils.readNBytes (buffer, 160, 200);

        // Skip unknown bytes
        buffer.position (buffer.position () + 149);

        final int compressionType = buffer.get () & 0xFF;
        if (compressionType != 0)
            throw new IOException (Functions.getMessage ("IDS_EPS_COMPRESSED_EDE_NOT_SUPPORTED", Integer.toString (compressionType)));
        final int diskType = buffer.getShort () & 0xFFFF;
        if (diskType != 3)
            throw new IOException (Functions.getMessage ("IDS_EPS_UNKNOWN_EDE_TYPE", Integer.toString (diskType)));

        // Extract the raw file
        final int skippedBLocks = countBitsSet (this.skipTable);
        final int rawDataEnd = this.fileContent.length - BLOCK_SIZE + BLOCK_SIZE * skippedBLocks;
        this.fileContent = Arrays.copyOfRange (this.fileContent, BLOCK_SIZE, rawDataEnd);

        this.parseEnsoniqImage ();
    }


    /**
     * Parses the 512-byte EDA header (compressed hard-disk image). Layout after byte 96: 400-byte
     * skip table | 13 unknown | 1 compressionType | 2 diskType.
     *
     * @throws IOException Could not read the image
     */
    private void parseEdaImage () throws IOException
    {
        final ByteBuffer buffer = this.readHeader ();

        // Read the description text
        final String [] metadata = StreamUtils.readAscii (buffer, (byte) 0x1A).split ("\r\n");
        this.description = metadata.length > 1 ? metadata[1].trim () : "";

        // Each BIT represents a block on the disk. If the bit is set then that block on the
        // original disk was empty otherwise that block had real data in it.
        this.skipTable = StreamUtils.readNBytes (buffer, 96, 400);

        // Skip unknown bytes
        buffer.position (buffer.position () + 13);

        final int compressionType = buffer.get () & 0xFF;
        if (compressionType != 0)
            throw new IOException (Functions.getMessage ("IDS_EPS_COMPRESSED_EDA_NOT_SUPPORTED"));
        final int diskType = buffer.getShort () & 0xFFFF;
        if (diskType != 203)
            throw new IOException (Functions.getMessage ("IDS_EPS_UNKNOWN_EDA_TYPE", Integer.toString (diskType)));

        // Extract the raw file
        final int skippedBLocks = countBitsSet (this.skipTable);
        final int rawDataEnd = this.fileContent.length - BLOCK_SIZE + BLOCK_SIZE * skippedBLocks;
        this.fileContent = Arrays.copyOfRange (this.fileContent, 512, rawDataEnd);

        this.parseEnsoniqImage ();
    }


    /**
     * Parses an HFE file.
     *
     * @throws IOException Could not read the image
     */
    private void parseHfeImage () throws IOException
    {
        final HfeFile hfeFile = new HfeFile (this.getSourceFile ());
        final HfeVersion hfeVersion = hfeFile.getHfeVersion ();
        if (hfeVersion != HfeVersion.VERSION_1)
            throw new IOException (Functions.getMessage ("IDS_HFE_VERSION_NOT_SUPPORTED", hfeVersion == HfeVersion.VERSION_2 ? "v2" : "v3"));
        if (hfeFile.getFloppyInterfaceMode () != HfeFile.FLOPPYMODE_GENERIC_SHUGGART_DD)
            throw new IOException (Functions.getMessage ("IDS_HFE_CAN_ONLY_DECODE_FLOPPY_MODE", "Generic Shuggart"));

        final List<Sector> allSectors = hfeFile.decodeMfmSectors ();
        this.fileContent = DiskImageBuilder.buildImage (allSectors, hfeFile.getNumTracks (), hfeFile.getNumSides (), 10, BLOCK_SIZE, true);

        this.parseEnsoniqImage ();
    }


    private void parseEnsoniqImage () throws IOException
    {
        this.parseDeviceIdBlock ();
        this.parseOsBlock ();
        // Root directory spans blocks 3-4 (2 contiguous blocks)
        this.rootDirectory = new EnsoniqFile (this, 0, "** ROOT **", EnsoniqFile.TYPE_SUBDIR, 2, 2, 3, null);
        this.parseDirectory (this.rootDirectory);
        this.cacheFAT ();
        this.calculateDiskId ();
    }


    /**
     * Parses Ensoniq block 1 (device ID block) to obtain disk geometry, bytes-per-block, and the
     * raw disk label.
     *
     * <pre>
     * Layout (40 bytes, big-endian):
     *   4B  – peripheral type, removable type, standards version, SCSI reserved
     *   3H  – sectors/track, heads, cylinders
     *   2L  – bytes/block, blocks on disk
     *   2B  – SCSI medium type, density code
     *   10s – reserved
     *   8s  – disk label (first byte 0xFF is a marker; remainder = label chars)
     *   2s  – device ID signature "ID"
     * </pre>
     *
     * @throws IOException Could not read
     */
    private void parseDeviceIdBlock () throws IOException
    {
        final ByteBuffer b = ByteBuffer.wrap (this.readEnsoniqDataBlocks (1, 1)).order (ByteOrder.BIG_ENDIAN);

        final int peripheralType = b.get () & 0xFF;
        final int removableType = b.get () & 0xFF;
        final int standardsVersion = b.get () & 0xFF;
        // SCSI reserved
        b.get ();
        // Sectors, heads, cylinders
        b.getShort ();
        b.getShort ();
        b.getShort ();
        final long bytesPerBlock = b.getInt () & 0xFFFFFFFFL;
        // Blocks on disk (informational)
        b.getInt ();
        // Medium type, density code
        b.get ();
        b.get ();
        // Reserved (10 bytes)
        b.position (b.position () + 10);
        final byte [] label = new byte [8];
        // Raw label
        b.get (label);
        final byte [] signature = new byte [2];
        // "ID" signature
        b.get (signature);

        if (peripheralType != 0x00)
            throw new IOException (Functions.getMessage ("IDS_EPS_UNEXPECTED_DEVICE_TYPE", Integer.toString (peripheralType)));
        if (removableType != REMOVABLE_TYPE_DISKETTE && removableType != REMOVABLE_TYPE_CD_ROM)
            throw new IOException (Functions.getMessage ("IDS_EPS_UNEXPECTED_MEDIA_TYPE", Integer.toString (removableType)));
        if (standardsVersion != 0x01 && standardsVersion != 0x02)
            throw new IOException (Functions.getMessage ("IDS_EPS_UNEXPECTED_STANDARDS_VERSION", Integer.toString (standardsVersion)));
        if (signature[0] != 'I' || signature[1] != 'D')
            throw new IOException (Functions.getMessage ("IDS_EPS_INVALID_DEVICE_ID_SIG"));

        this.bytesPerBlock = (int) bytesPerBlock;
        this.sizeBlocks = this.calculateBlockCount ();

        // Strip leading 0xFF marker byte then remove non-printable chars
        int start = 0;
        while (start < label.length && (label[start] & 0xFF) == 0xFF)
            start++;
        this.diskLabel = parseRawDiskLabel (new String (label, start, label.length - start, java.nio.charset.StandardCharsets.ISO_8859_1));
    }


    /**
     * Parses Ensoniq block 2 (OS info block) to obtain free-block count and optional OS version
     * strings.
     *
     * <pre>
     * Layout (30 bytes, big-endian):
     *   L  – free blocks on disk
     *   4B – major OS version, minor OS version, major ROM version, minor ROM version
     *   H  – checkEPS (must be 0)
     *   18s – reserved
     *   2s  – check bytes "OS"
     * </pre>
     *
     * @throws IOException Could not read
     */
    private void parseOsBlock () throws IOException
    {
        final ByteBuffer b = ByteBuffer.wrap (this.readEnsoniqDataBlocks (2, 1)).order (ByteOrder.BIG_ENDIAN);

        @SuppressWarnings("unused")
        final int freeBlocks = b.getInt ();
        final int majorOS = b.get () & 0xFF;
        final int minorOS = b.get () & 0xFF;
        final int majorROM = b.get () & 0xFF;
        final int minorROM = b.get () & 0xFF;
        final int checkEPS = b.getShort () & 0xFFFF;
        b.position (b.position () + 18); // reserved
        final byte [] ck = new byte [2];
        b.get (ck);

        if (checkEPS != 0)
            throw new IOException (Functions.getMessage ("IDS_EPS_UNEXPECTED_CHECK_VALUE", Integer.toString (checkEPS)));
        if (ck[0] != 'O' || ck[1] != 'S')
            throw new IOException (Functions.getMessage ("IDS_EPS_UNEXPECTED_CHECK_BYTES"));

        if (majorOS != 0 || minorOS != 0)
        {
            this.osVersion = majorOS + "." + minorOS;
            this.minimumRomVersion = majorROM + "." + minorROM;
        }
    }


    /**
     * Parses an Ensoniq directory block recursively.
     *
     * <pre>
     * Each directory entry is 26 bytes:
     *   1B  – reserved
     *   1B  – file type
     *   12s – file name
     *   2H  – size in blocks, contiguous blocks
     *   L   – first block index
     *   4B  – reserved
     * </pre>
     *
     * @param ensoniqFile The Ensoniq file to read the directory from
     * @throws IOException Could not read
     */
    private void parseDirectory (final EnsoniqFile ensoniqFile) throws IOException
    {
        final byte [] data = this.readEnsoniqFile (ensoniqFile);

        if (data.length >= 2 && (data[data.length - 2] != 'D' || data[data.length - 1] != 'R'))
            throw new IOException (Functions.getMessage ("IDS_EPS_MISSING_DR_MARKER", Integer.toString (ensoniqFile.getFirstBlock ())));

        final Map<Integer, EnsoniqFile> files = new TreeMap<> ();
        for (int i = 0; i < data.length / DIRECTORY_ENTRY_SIZE; i++)
        {
            final ByteBuffer b = ByteBuffer.wrap (data, i * DIRECTORY_ENTRY_SIZE, DIRECTORY_ENTRY_SIZE).order (ByteOrder.BIG_ENDIAN);

            b.get (); // reserved
            final int fileType = b.get () & 0xFF;
            final byte [] nameBytes = new byte [12];
            b.get (nameBytes);
            final int fileSize = b.getShort () & 0xFFFF;
            final int contiguousBlocks = b.getShort () & 0xFFFF;
            final int firstBlock = (int) (b.getInt () & 0xFFFFFFFFL);

            if (fileType == EnsoniqFile.TYPE_UNUSED)
                continue;

            final String name = new String (nameBytes, StandardCharsets.ISO_8859_1).trim ();
            final EnsoniqFile entry = new EnsoniqFile (this, i, name, fileType, fileSize, contiguousBlocks, firstBlock, ensoniqFile);
            files.put (Integer.valueOf (i), entry);

            if (fileType == EnsoniqFile.TYPE_SUBDIR)
                this.parseDirectory (entry);
        }
        ensoniqFile.setChildren (files);
    }


    /**
     * Builds the in-memory File Allocation Table (FAT) cache. FAT starts at block 5; each of the
     * 170 entries is 3 bytes big-endian. Entry value: 0 = free, 1 = end-of-file, 2 = bad block, N =
     * next block. The first 15 entries of an EPS/EPS16+ disk, and the first 23 entries for a VFX-SD
     * disk are set to one. An empty file allocation block contains all zeros except for the last
     * two bytes of the block. Those two bytes contain: 0x46 and 0x42, which are the ASCII
     * characters 'F' and 'B' respectively.
     *
     * @throws IOException Could not read
     */
    private void cacheFAT () throws IOException
    {
        int fatIndex = 0;
        int fatBlock = 5;
        while (true)
        {
            final byte [] data = this.readEnsoniqDataBlocks (fatBlock, 1);
            if (data.length >= 2 && (data[data.length - 2] != 'F' || data[data.length - 1] != 'B'))
            {
                // FAT block misses 'FB' marker on some disks but seems not to be an issue
            }

            for (int offset = 0; offset < data.length - 2; offset += FAT_ENTRY_SIZE)
            {
                final int entry = (data[offset] & 0xFF) << 16 | (data[offset + 1] & 0xFF) << 8 | data[offset + 2] & 0xFF;
                this.fatCache.put (Integer.valueOf (fatIndex), Integer.valueOf (entry));
                fatIndex++;
            }
            // Sanity check if this the last FAT block
            if (this.fatCache.size () > this.sizeBlocks)
                break;
            fatBlock++;
        }

        // Validate that all FAT entries thru the end of the FAT itself are marked as '1'
        for (int i = 0; i < fatBlock; i++)
        {
            final Integer value = this.fatCache.get (Integer.valueOf (i));
            if (value != null && value.intValue () != 1)
            {
                // FAT entry does not comply with standard value of '1'
                // Can be ignored since such entries are not used (happens on ISO files)
            }
        }
    }


    private ByteBuffer readHeader ()
    {
        return ByteBuffer.wrap (this.readImageData (0, 512)).order (ByteOrder.BIG_ENDIAN);
    }


    private void calculateDiskId () throws IOException
    {
        try
        {
            final MessageDigest md = MessageDigest.getInstance ("MD5");
            md.update (this.readEnsoniqDataBlocks (1, 15));
            for (final EnsoniqFile f: this.listFiles ())
                md.update (this.readEnsoniqFile (f));
            final byte [] hash = md.digest ();
            final StringBuilder sb = new StringBuilder (32);
            for (final byte hb: hash)
                sb.append (String.format ("%02x", Byte.valueOf (hb)));
            this.diskID = sb.toString ();
        }
        catch (final NoSuchAlgorithmException e)
        {
            this.diskID = Integer.toHexString (this.getSourceFile ().hashCode ());
        }
    }


    /**
     * Reads {@code length} consecutive 512-byte Ensoniq data blocks starting at logical block
     * {@code index}. For EDE images, skipped (empty) blocks are synthesized as 0x6DB6 fill.
     *
     * @param index The index to start reading
     * @param length The length to read
     * @return The read bytes
     * @throws IOException Could not read
     */
    public byte [] readEnsoniqDataBlocks (final int index, final int length) throws IOException
    {
        if (this.skipTable == null)
        {
            final int start = index * this.bytesPerBlock;
            return this.readImageData (start, length * this.bytesPerBlock);
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream (length * this.bytesPerBlock);
        for (int i = index; i < index + length; i++)
            if (this.isSkippedBlock (i))
            {
                final byte [] empty = new byte [this.bytesPerBlock];
                for (int j = 0; j < this.bytesPerBlock - 1; j += 2)
                {
                    empty[j] = 0x6D;
                    empty[j + 1] = (byte) 0xB6;
                }
                baos.write (empty);
            }
            else
            {
                final int start = (i - this.countSkippedBlocksUntilIndex (i)) * this.bytesPerBlock;
                baos.write (this.readImageData (start, this.bytesPerBlock));
            }
        return baos.toByteArray ();
    }


    /**
     * Reads the complete byte content of an Ensoniq file, following FAT chains for non-contiguous
     * allocations.
     *
     * @param fileRef The Ensoniq file to read
     * @return The content of the file
     * @throws IOException Could not read the file
     */
    public byte [] readEnsoniqFile (final EnsoniqFile fileRef) throws IOException
    {
        final int numberOfBlocks = fileRef.getNumberOfBlocks ();
        final int contiguousBlocks = fileRef.getContiguousBlocks ();
        final int firstBlock = fileRef.getFirstBlock ();

        final ByteArrayOutputStream baos = new ByteArrayOutputStream (numberOfBlocks * this.bytesPerBlock);
        baos.write (this.readEnsoniqDataBlocks (firstBlock, contiguousBlocks));

        if (numberOfBlocks > contiguousBlocks)
        {
            int lastBlock = firstBlock + contiguousBlocks - 1;
            while (true)
            {
                final int next = this.fatCache.getOrDefault (Integer.valueOf (lastBlock), Integer.valueOf (1)).intValue ();
                if (next == 1)
                    break;
                if (next == 2)
                    throw new IOException ("Bad block in FAT reading: " + fileRef.getName ());
                baos.write (this.readEnsoniqDataBlocks (next, 1));
                lastBlock = next;
            }
        }
        return baos.toByteArray ();
    }


    private boolean isSkippedBlock (final int index)
    {
        if (this.skipTable == null)
            return false;
        final int byteIdx = index / 8;
        if (byteIdx >= this.skipTable.length)
            return false;
        return ((this.skipTable[byteIdx] & 0xFF) >> 7 - index % 8 & 1) == 1;
    }


    private int countSkippedBlocksUntilIndex (final int index)
    {
        final int bulk = index / 8;
        int skipped = countBitsSet (Arrays.copyOfRange (this.skipTable, 0, bulk));
        for (int i = bulk * 8; i < index; i++)
            if (this.isSkippedBlock (i))
                skipped++;
        return skipped;
    }


    private static int countBitsSet (final byte [] data)
    {
        int n = 0;
        for (final byte b: data)
            n += Integer.bitCount (b & 0xFF);
        return n;
    }


    private int calculateBlockCount ()
    {
        final int skipped = this.skipTable == null ? 0 : countBitsSet (this.skipTable);
        return skipped + this.fileContent.length / this.bytesPerBlock;
    }


    /**
     * Removes non-printable / non-ASCII characters from a raw disk label string.
     *
     * @param raw The raw string
     * @return The ASCII string
     */
    private static String parseRawDiskLabel (final String raw)
    {
        final StringBuilder sb = new StringBuilder ();
        for (final char c: raw.toCharArray ())
            if (c >= 32 && c <= 127)
                sb.append (c);
        return sb.toString ();
    }


    /**
     * Returns every non-directory file on this disk image.
     *
     * @return The non-directory files
     */
    public List<EnsoniqFile> listFiles ()
    {
        final List<EnsoniqFile> result = new ArrayList<> ();
        this.collectFiles (this.rootDirectory, result);
        return result;
    }


    /**
     * Returns only files with type in {@link EnsoniqFile#INSTRUMENT_TYPES}.
     *
     * @return The instrument files
     */
    public List<EnsoniqFile> listInstruments ()
    {
        final List<EnsoniqFile> result = new ArrayList<> ();
        for (final EnsoniqFile f: this.listFiles ())
            if (EnsoniqFile.INSTRUMENT_TYPES.contains (Integer.valueOf (f.getType ())))
                result.add (f);
        return result;
    }


    private void collectFiles (final EnsoniqFile node, final List<EnsoniqFile> result)
    {
        if (node == null)
            return;
        final int t = node.getType ();
        if (t == EnsoniqFile.TYPE_UNUSED || t == EnsoniqFile.TYPE_PARENT_PTR)
            return;
        if (t == EnsoniqFile.TYPE_SUBDIR)
            node.getChildren ().values ().forEach (c -> this.collectFiles (c, result));
        else
            result.add (node);
    }


    private byte [] readImageData (final int startBytes, final int lengthBytes)
    {
        return Arrays.copyOfRange (this.fileContent, startBytes, startBytes + lengthBytes);
    }


    /**
     * Get the ID of the disk.
     *
     * @return The ID
     */
    public String getDiskID ()
    {
        return this.diskID;
    }


    /**
     * Get the label of the disk.
     *
     * @return The label
     */
    public String getDiskLabel ()
    {
        return this.diskLabel;
    }


    /**
     * Get the description metadata. Only present for EDE/EDA files.
     *
     * @return The description
     */
    public String getDescription ()
    {
        return this.description;
    }


    /**
     * Get the disk encoding type.
     * 
     * @return The encoding type
     */
    public EncodingType getEncodingType ()
    {
        return this.encodingType;
    }


    /**
     * Get the OS version.
     *
     * @return The OS version
     */
    public String getOsVersion ()
    {
        return this.osVersion;
    }


    /**
     * Get the minimum ROM version.
     *
     * @return The minimum ROM version
     */
    public String getMinimumRomVersion ()
    {
        return this.minimumRomVersion;
    }


    /**
     * Get the source file.
     * 
     * @return The source file
     */
    public File getSourceFile ()
    {
        return this.sourceFile;
    }


    /**
     * Decodes an 4-byte EPSlong at the current byte offset.
     *
     * @param buffer Buffer wrapping the raw bytes (any position)
     * @return decoded unsigned 24-bit integer
     */
    public static int parseEpsLong (final ByteBuffer buffer)
    {
        final int lsw = (buffer.getShort () & 0xFFFF) >>> 4;
        final int msw = (buffer.getShort () & 0xFFFF) >>> 4;
        return msw << 12 | lsw;
    }


    /**
     * Decodes an unsigned INTx16 at absolute byte offset {@code offset}.
     *
     * @param buffer The buffer to read from
     * @param offset The offset to start reading
     * @return The read integer
     */
    public static int parseIntx16 (final ByteBuffer buffer, final int offset)
    {
        return (buffer.getShort (offset) & 0xFFFF) >>> 4;
    }
}