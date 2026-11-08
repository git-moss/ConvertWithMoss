// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.Magic;


/**
 * A header of a Kontakt 2 NKI. Additionally, supports the in-between version 4.2.
 *
 * @author Jürgen Moßgraber
 */
public class Kontakt2Header
{
    /** The 'Other' category. */
    public static final String       CATEGORY_OTHER           = "Other";

    private static final int         HEADER_KONTAKT_2         = 0x100;
    /** The size of a Kontakt 2 - 4.1 header including the file ID. */
    private static final int         HEADER_SIZE              = 0xAA;
    private static final int         CHECKSUM_OFFSET          = 0xA2;
    private static final int         PATCH_TYPE_NKM           = 0;
    private static final int         PATCH_TYPE_NKI           = 1;

    /** The offsets and sizes of all 2 and 4 byte values of the header (for the byte order). */
    private static final int [] []   HEADER_VALUES            =
    {
        { 0x00, 4 },
        { 0x04, 4 },
        { 0x08, 2 },
        { 0x0A, 4 },
        { 0x0E, 2 },
        { 0x10, 4 },
        { 0x14, 4 },
        { 0x18, 4 },
        { 0x1C, 2 },
        { 0x1E, 2 },
        { 0x20, 2 },
        { 0x22, 2 },
        { 0x24, 2 },
        { 0x26, 4 },
        { 0x2A, 4 },
        { 0x2E, 4 },
        { 0x32, 4 },
        { 0x36, 4 },
        { 0x9A, 2 },
        { 0x9C, 2 },
        { 0x9E, 4 },
        { 0xA2, 4 },
        { 0xA6, 4 }
    };

    // Kontakt 2.2.3.006 (the bytes in little-endian order)
    private static final byte []     KONTAKT_2_VERSION        =
    {
        6,
        3,
        2,
        2
    };
    // Kontakt 2.2.0.008 (the bytes in little-endian order)
    private static final byte []     KONTAKT_2_MIN_VERSION    =
    {
        8,
        0,
        2,
        2
    };
    private static final int         HEADER_KONTAKT_42        = 0x110;
    private static final String      NULL_ENTRY               = "(null)";

    private static final String []   K2_INSTRUMENT_CATEGORY_1 = new String []
    {
        CATEGORY_OTHER,
        "Piano",
        "Guitar",
        "Bass",
        "Drums",
        "Keyboard",
        "Synthesizer",
        "Strings",
        "Brass",
        "Organ",
        "Vocal",
        "Mallet",
        "Athmosphere",
        "Loop/Beat",
        "Pad",
        "Lead",
        "Soundeffect",
        "Woodwinds",
        "Percussion"
    };

    private static final String []   K2_INSTRUMENT_CATEGORY_2 = new String []
    {
        CATEGORY_OTHER,
        "Acoustic",
        "Electric",
        "Solo",
        "Ensemble",
        "Analog",
        "Digital",
        "Synthetic",
        "Mixed",
        "Ethnic",
        "Steel",
        "Surround",
        "Synced",
        "KSP",
        "Convoluted",
        "Sequenced",
        "Spacious"
    };

    private static final String []   K2_INSTRUMENT_CATEGORY_3 = new String []
    {
        CATEGORY_OTHER,
        "Noisy",
        "Metallic",
        "Clean ",
        "Distorted",
        "Dark",
        "Light",
        "Groovy",
        "Harmonic",
        "Melodic",
        "Full",
        "Hard",
        "Dissonant",
        "Intense",
        "Relaxed",
        "Big ",
        "Small",
        "Soft"
    };

    /** Maps the categories of the TagDetector to the index of the 1st instrument category. */
    private static final Map<String, Integer> TAG_CATEGORY_TO_CATEGORY_1 = new HashMap<> ();
    static
    {
        final String [] [] mapping =
        {
            { "Piano", "Piano" },
            { "Guitar", "Guitar" },
            { "Bass", "Bass" },
            { "Drum", "Drums" },
            { "Acoustic Drum", "Drums" },
            { "Kick", "Drums" },
            { "Snare", "Drums" },
            { "Hi-Hat", "Drums" },
            { "Clap", "Drums" },
            { "Keyboard", "Keyboard" },
            { "Synth", "Synthesizer" },
            { "Monosynth", "Synthesizer" },
            { "Chip", "Synthesizer" },
            { "Strings", "Strings" },
            { "Orchestral", "Strings" },
            { "Brass", "Brass" },
            { "Organ", "Organ" },
            { "Pipe", "Organ" },
            { "Vocal", "Vocal" },
            { "Chromatic Percussion", "Mallet" },
            { "Bell", "Mallet" },
            { "Drone", "Athmosphere" },
            { "Loops", "Loop/Beat" },
            { "Pad", "Pad" },
            { "Lead", "Lead" },
            { "FX", "Soundeffect" },
            { "Destruction", "Soundeffect" },
            { "Hits", "Soundeffect" },
            { "Winds", "Woodwinds" },
            { "Percussion", "Percussion" }
        };
        for (final String [] entry: mapping)
            TAG_CATEGORY_TO_CATEGORY_1.put (entry[0].toLowerCase (Locale.US), Integer.valueOf (findCategoryIndex (K2_INSTRUMENT_CATEGORY_1, entry[1])));
    }

    private static final Set<String> KNOWN_LIBRARY_IDS        = new HashSet<> ();
    static
    {
        KNOWN_LIBRARY_IDS.add ("Kon2"); // Kontakt 2
        KNOWN_LIBRARY_IDS.add ("Kon3"); // Kontakt 3
        KNOWN_LIBRARY_IDS.add ("Kon4"); // Kontakt 4
        KNOWN_LIBRARY_IDS.add ("AkPi"); // Akustik Piano from Kontakt 3 Library
        KNOWN_LIBRARY_IDS.add ("ElPi"); // Elektrik Piano from Kontakt 3 Library
    }

    private final INotifier        notifier;
    private final boolean          isBigEndian;
    private final SimpleDateFormat simpleDateFormatter       = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss", Locale.GERMAN);

    private int                    compressedLength;
    private int                    headerVersion;
    private boolean                isFourDotTwo;
    private int                    patchType;
    private String                 kontaktVersion;
    private final byte []          kontaktVersionBuffer      = new byte [4];
    private Date                   creation;
    private String                 libraryID;
    private int                    zones;
    private int                    groups;
    private int                    instruments;
    private int                    sampleSize;
    private boolean                isMonolith;
    private String                 minSupportedVersion;
    private final byte []          minSupportedVersionBuffer = new byte [4];
    private int                    iconID;
    private String                 author                    = "";
    private int                    category1;
    private int                    category2;
    private int                    category3;
    private String                 website                   = "";
    private int                    svnRevision;
    private int                    checksum;
    private byte []                md5Checksum;
    private int                    flags;
    private int                    unknownA;
    private int                    unknownB;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     * @param isBigEndian Larger bytes are first, other wise smaller bytes are first (little-endian)
     */
    public Kontakt2Header (final INotifier notifier, final boolean isBigEndian)
    {
        this.notifier = notifier;
        this.isBigEndian = isBigEndian;
        this.simpleDateFormatter.setTimeZone (TimeZone.getTimeZone ("GMT+01:00"));
    }


    /**
     * Read a Kontakt 2 - 4.2 header.
     *
     * @param fileAccess The random access file to read from
     * @throws IOException Error reading the header
     */
    public void read (final RandomAccessFile fileAccess) throws IOException
    {
        this.compressedLength = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        this.headerVersion = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.isFourDotTwo = this.headerVersion == HEADER_KONTAKT_42;

        final int magic = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        if (magic != Magic.KONTAKT2_INSTRUMENT_HEADER_LE && magic != Magic.KONTAKT2_INSTRUMENT_HEADER_BE && magic != Magic.KONTAKT42_INSTRUMENT_HEADER_LE && magic != Magic.KONTAKT42_INSTRUMENT_HEADER_BE)
            this.notifier.logError ("IDS_NKI_UNKNOWN_HEADER_MAGIC", Integer.toHexString (magic));

        this.patchType = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.kontaktVersion = this.readVersion (fileAccess, this.kontaktVersionBuffer);

        this.libraryID = StreamUtils.readAscii (fileAccess, 4, !this.isBigEndian);
        if (!KNOWN_LIBRARY_IDS.contains (this.libraryID))
            this.notifier.log ("IDS_NKI_UNKNOWN_BLOCK_ID", this.libraryID);

        this.creation = StreamUtils.readTimestamp (fileAccess, this.isBigEndian);

        // No idea yet about these 4 bytes...
        this.unknownA = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        this.zones = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.groups = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.instruments = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.sampleSize = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        this.isMonolith = StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian) == 1;
        this.minSupportedVersion = this.readVersion (fileAccess, this.minSupportedVersionBuffer);

        // No idea yet about these 4 bytes...
        this.unknownB = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        this.readMetadata (fileAccess);

        // Seems to be some kind of flags in a bit array
        this.flags = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        if (this.isFourDotTwo)
            this.md5Checksum = StreamUtils.readNBytes (fileAccess, 16);
        else
            this.checksum = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        this.svnRevision = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
    }


    /**
     * Set all values to write a Kontakt 2 multi (NKM) header.
     *
     * @param compressedLength The length of the compressed XML document
     * @param numberOfLines The number of lines of the XML document
     * @param zones The number of zones of all instruments
     * @param groups The number of groups of all instruments
     * @param instruments The number of instruments
     * @param sampleSize The size of all samples in bytes
     * @param creation The creation date
     */
    public void setMultiValues (final int compressedLength, final int numberOfLines, final int zones, final int groups, final int instruments, final long sampleSize, final Date creation)
    {
        this.setInstrumentValues (compressedLength, numberOfLines, zones, groups, sampleSize, creation, 0, "");
        this.patchType = PATCH_TYPE_NKM;
        this.instruments = instruments;
    }


    /**
     * Set all values to write a Kontakt 2 instrument (NKI) header.
     *
     * @param compressedLength The length of the compressed XML document
     * @param numberOfLines The number of lines of the XML document
     * @param zones The number of zones
     * @param groups The number of groups
     * @param sampleSize The size of all samples in bytes
     * @param creation The creation date
     * @param iconID The ID of the instrument icon
     * @param author The author of the instrument (max. 8 characters are stored)
     */
    public void setInstrumentValues (final int compressedLength, final int numberOfLines, final int zones, final int groups, final long sampleSize, final Date creation, final int iconID, final String author)
    {
        this.compressedLength = compressedLength;
        this.headerVersion = HEADER_KONTAKT_2;
        this.isFourDotTwo = false;
        this.patchType = PATCH_TYPE_NKI;
        System.arraycopy (KONTAKT_2_VERSION, 0, this.kontaktVersionBuffer, 0, 4);
        if (this.isBigEndian)
            StreamUtils.reverseArray (this.kontaktVersionBuffer);
        this.libraryID = "Kon2";
        this.creation = creation;
        // The lower 16 bits of the number of lines of the XML document
        this.unknownA = numberOfLines & 0xFFFF;
        this.zones = zones;
        this.groups = groups;
        this.instruments = 1;
        this.sampleSize = (int) (sampleSize & 0xFFFFFFFFL);
        this.isMonolith = false;
        System.arraycopy (KONTAKT_2_MIN_VERSION, 0, this.minSupportedVersionBuffer, 0, 4);
        if (this.isBigEndian)
            StreamUtils.reverseArray (this.minSupportedVersionBuffer);
        // The upper 32 bits of the sample size
        this.unknownB = (int) (sampleSize >>> 32);
        this.iconID = iconID;
        this.author = author == null ? "" : author;
        this.category1 = 0;
        this.category2 = 0;
        this.category3 = 0;
        this.website = "";
        this.flags = 0;
        this.checksum = 0;
        this.svnRevision = 0;
    }


    /**
     * Set the 3 instrument categories.
     *
     * @param category1 The index of the 1st category, see getCategory1Index
     * @param category2 The index of the 2nd category, see getCategory2Index
     * @param category3 The index of the 3rd category, see getCategory3Index
     */
    public void setCategories (final int category1, final int category2, final int category3)
    {
        this.category1 = category1;
        this.category2 = category2;
        this.category3 = category3;
    }


    /**
     * Get the index of the 1st instrument category (the instrument type) which matches the given
     * category.
     *
     * @param category A category of the TagDetector or the name of a category
     * @return The index, 0 (Other) if there is no matching category
     */
    public static int getCategory1Index (final String category)
    {
        if (category == null || category.isBlank ())
            return 0;
        final Integer index = TAG_CATEGORY_TO_CATEGORY_1.get (category.trim ().toLowerCase (Locale.US));
        return index == null ? findCategoryIndex (K2_INSTRUMENT_CATEGORY_1, category) : index.intValue ();
    }


    /**
     * Get the index of the 2nd instrument category (e.g. Acoustic, Electric) which matches one of
     * the given keywords.
     *
     * @param keywords The keywords
     * @return The index of the first matching keyword, 0 (Other) if no keyword matches
     */
    public static int getCategory2Index (final String [] keywords)
    {
        return findCategoryIndex (K2_INSTRUMENT_CATEGORY_2, keywords);
    }


    /**
     * Get the index of the 3rd instrument category (e.g. Dark, Soft) which matches one of the given
     * keywords.
     *
     * @param keywords The keywords
     * @return The index of the first matching keyword, 0 (Other) if no keyword matches
     */
    public static int getCategory3Index (final String [] keywords)
    {
        return findCategoryIndex (K2_INSTRUMENT_CATEGORY_3, keywords);
    }


    private static int findCategoryIndex (final String [] categories, final String [] keywords)
    {
        if (keywords != null)
            for (final String keyword: keywords)
            {
                final int index = findCategoryIndex (categories, keyword);
                if (index > 0)
                    return index;
            }
        return 0;
    }


    private static int findCategoryIndex (final String [] categories, final String name)
    {
        if (name != null)
            // Index 0 is 'Other'
            for (int i = 1; i < categories.length; i++)
                if (categories[i].trim ().equalsIgnoreCase (name.trim ()))
                    return i;
        return 0;
    }


    /**
     * Write a Kontakt 2 - 4.1 header including the file ID and the checksum.
     *
     * @param out The output stream to write to
     * @param fileID The file ID (the first 4 bytes of the file)
     * @throws IOException Error writing the header
     */
    public void write (final OutputStream out, final int fileID) throws IOException
    {
        final ByteArrayOutputStream headerOut = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned32 (headerOut, fileID, this.isBigEndian);
        this.checksum = 0;
        this.write (headerOut);
        final byte [] header = headerOut.toByteArray ();

        this.checksum = calculateChecksum (header, this.isBigEndian);
        final ByteArrayOutputStream checksumOut = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned32 (checksumOut, this.checksum, this.isBigEndian);
        System.arraycopy (checksumOut.toByteArray (), 0, header, CHECKSUM_OFFSET, 4);
        out.write (header);
    }


    /**
     * Calculates the checksum of a Kontakt 2 - 4.1 header. It is calculated over the header in
     * big-endian byte order with the checksum set to 0.
     *
     * @param header The 170 bytes of the header including the file ID
     * @param isBigEndian True if the header is stored in big-endian byte order
     * @return The checksum
     */
    public static int calculateChecksum (final byte [] header, final boolean isBigEndian)
    {
        final byte [] data = Arrays.copyOf (header, HEADER_SIZE);
        if (!isBigEndian)
            for (final int [] value: HEADER_VALUES)
                for (int i = 0; i < value[1] / 2; i++)
                {
                    final int pos1 = value[0] + i;
                    final int pos2 = value[0] + value[1] - 1 - i;
                    final byte b = data[pos1];
                    data[pos1] = data[pos2];
                    data[pos2] = b;
                }
        Arrays.fill (data, CHECKSUM_OFFSET, CHECKSUM_OFFSET + 4, (byte) 0);

        int sum = 0;
        for (int j = 0; j < HEADER_SIZE; j++)
        {
            final int a = (j + 9) % 7;
            final int b = (j + 1) % 18;
            final int c = (a + j) % 17;
            // At the end of the header the last read byte is used again
            int v = data[j] & 0xFF;
            sum += v << j % 13;
            if (j + 1 < HEADER_SIZE)
                v = data[j + 1] & 0xFF;
            sum += v << a;
            if (j + 2 < HEADER_SIZE)
                v = data[j + 2] & 0xFF;
            sum += v << b;
            if (j + 3 < HEADER_SIZE)
                v = data[j + 3] & 0xFF;
            sum += v << c;
        }
        return sum;
    }


    /**
     * Write a Kontakt 2 - 4.2 header (without the file ID).
     *
     * @param out The output stream to write to
     * @throws IOException Error writing the header
     */
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.compressedLength, this.isBigEndian);
        StreamUtils.writeUnsigned16 (out, HEADER_KONTAKT_2, this.isBigEndian);
        StreamUtils.writeUnsigned32 (out, Magic.KONTAKT2_INSTRUMENT_HEADER_BE, this.isBigEndian);
        StreamUtils.writeUnsigned16 (out, this.patchType, this.isBigEndian);

        out.write (this.kontaktVersionBuffer);

        StreamUtils.writeAscii (out, this.libraryID, 4, !this.isBigEndian);
        StreamUtils.writeTimestamp (out, this.creation, this.isBigEndian);

        // No idea yet about these 4 bytes...
        StreamUtils.writeUnsigned32 (out, this.unknownA, this.isBigEndian);

        StreamUtils.writeUnsigned16 (out, this.zones, this.isBigEndian);
        StreamUtils.writeUnsigned16 (out, this.groups, this.isBigEndian);
        StreamUtils.writeUnsigned16 (out, this.instruments, this.isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.sampleSize, this.isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.isMonolith ? 1 : 0, this.isBigEndian);

        out.write (this.minSupportedVersionBuffer);

        // No idea yet about these 4 bytes...
        StreamUtils.writeUnsigned32 (out, this.unknownB, this.isBigEndian);

        this.writeMetadata (out);

        // Seems to be some kind of flags in a bit array
        StreamUtils.writeUnsigned32 (out, this.flags, this.isBigEndian);

        if (this.isFourDotTwo)
            out.write (this.md5Checksum);
        else
            StreamUtils.writeUnsigned32 (out, this.checksum, this.isBigEndian);

        StreamUtils.writeUnsigned32 (out, this.svnRevision, this.isBigEndian);
    }


    /**
     * Reads and formats the Kontakt version number with which the file was created.
     *
     * @param in The input stream
     * @param outBuffer The buffer to read into
     * @return The formatted version number, ends with a '?' if the patch level needs to be read
     *         separately.
     * @throws IOException Could not read the version
     */
    private String readVersion (final DataInput in, final byte [] outBuffer) throws IOException
    {
        final byte [] buffer = new byte [4];
        in.readFully (buffer);
        System.arraycopy (buffer, 0, outBuffer, 0, 4);
        if (!this.isBigEndian)
            StreamUtils.reverseArray (buffer);

        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < 3; i++)
            sb.append (Integer.toString (buffer[i])).append ('.');
        if (buffer[3] == -1)
            sb.append ('?');
        else
            sb.append (String.format ("%03d", Integer.valueOf (buffer[3])));
        return sb.toString ();
    }


    private void readMetadata (final RandomAccessFile fileAccess) throws IOException
    {
        this.iconID = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        this.author = fixBrokenCharacters (StreamUtils.readAscii (fileAccess, 8, StandardCharsets.ISO_8859_1).trim ());

        this.category1 = fileAccess.read ();
        this.category2 = fileAccess.read ();
        this.category3 = fileAccess.read ();

        this.website = StreamUtils.readAscii (fileAccess, 86).trim ();

        // Padding
        StreamUtils.skipNBytes (fileAccess, 3);
    }


    private void writeMetadata (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.iconID, this.isBigEndian);
        StreamUtils.writeAscii (out, this.author, 8);

        out.write (this.category1);
        out.write (this.category2);
        out.write (this.category3);
        StreamUtils.writeAscii (out, this.website, 86);

        // Padding?
        out.write (0);
        out.write (0);
        out.write (0);
    }


    /**
     * Get the length of the compressed data block (ZLIB or FastLZ for Kontakt 4).
     *
     * @return The compressed length
     */
    public int getCompressedLength ()
    {
        return this.compressedLength;
    }


    /**
     * Get the version of the header.
     *
     * @return Kontakt 2 returns 0x100, Kontakt 4.2 returns 0x101
     */
    public int getHeaderVersion ()
    {
        return this.headerVersion;
    }


    /**
     * Is this a Kontakt 4.2 file?
     *
     * @return True if 4.2
     */
    public boolean isFourDotTwo ()
    {
        return this.isFourDotTwo;
    }


    /**
     * Get the type of the patch.
     *
     * @return Returns 0=NKM, 1=NKI, 2=NKB, 3=NKP, 4=NKG, 5=NKZ
     */
    public int getPatchType ()
    {
        return this.patchType;
    }


    /**
     * This combines the Kontakt version with the patch level.
     *
     * @return E.g. 2.0.1.002 or 4.0.0.2475
     */
    public String getKontaktVersion ()
    {
        if (this.kontaktVersion.endsWith ("?"))
            return this.kontaktVersion.substring (0, this.kontaktVersion.length () - 1) + Integer.toString (this.svnRevision);
        return this.kontaktVersion;
    }


    /**
     * Get the date and time when the instrument was created.
     *
     * @return The creation date/time
     */
    public Date getCreation ()
    {
        return this.creation;
    }


    /**
     * Get the date/time formatted as 'dd.MM.yyyy HH:mm:ss'.
     *
     * @return The formatted date/time
     */
    public String getFormattedCreation ()
    {
        return this.simpleDateFormatter.format (this.creation);
    }


    /**
     * Set the date and time when the instrument was created.
     *
     * @param creation The creation date/time
     */
    public void setCreation (final Date creation)
    {
        this.creation = creation;
    }


    /**
     * Get the library ID, e.g. 'Kon4'.
     *
     * @return The library ID
     */
    public String getLibraryID ()
    {
        return this.libraryID;
    }


    /**
     * Set the library ID, e.g. 'Kon4'.
     *
     * @param libraryID The library ID
     */
    public void setLibraryID (final String libraryID)
    {
        this.libraryID = libraryID;
    }


    /**
     * Get the number of zones in the instrument.
     *
     * @return The number of zones
     */
    public int getZones ()
    {
        return this.zones;
    }


    /**
     * Get the number of groups in the instrument.
     *
     * @return The number of the groups
     */
    public int getGroups ()
    {
        return this.groups;
    }


    /**
     * Get the number of instruments in the file (in a NKM Multi).
     *
     * @return The number of instruments
     */
    public int getInstruments ()
    {
        return this.instruments;
    }


    /**
     * The sum of the size of all used samples (only the content data block of a WAV without any
     * headers).
     *
     * @return The PCM size
     */
    public long getSampleSize ()
    {
        return this.sampleSize;
    }


    /**
     * True if it is a monolith, which is a fully self contained file with all samples and
     * resources.
     *
     * @return True if it is a monolith
     */
    public boolean isMonolith ()
    {
        return this.isMonolith;
    }


    /**
     * Get the minimum Kontakt version required to open this file.
     *
     * @return The minimum Kontakt version
     */
    public String getMinSupportedVersion ()
    {
        return this.minSupportedVersion;
    }


    /**
     * Get the ID of the icon.
     *
     * @return The ID of the icon
     */
    public int getIconID ()
    {
        return this.iconID;
    }


    /**
     * Get the author of the file.
     *
     * @return The author
     */
    public String getAuthor ()
    {
        return this.author;
    }


    /**
     * Set the author of the file.
     *
     * @param author The author
     */
    public void setAuthor (final String author)
    {
        this.author = author;
    }


    /**
     * Get the index of category 1.
     *
     * @return The index
     */
    public int getCategory1 ()
    {
        return this.category1;
    }


    /**
     * Get the index of category 2.
     *
     * @return The index
     */
    public int getCategory2 ()
    {
        return this.category2;
    }


    /**
     * Get the index of category 3.
     *
     * @return The index
     */
    public int getCategory3 ()
    {
        return this.category3;
    }


    /**
     * Get the name of category 1.
     *
     * @return The name
     */
    public String getCategory1Name ()
    {
        return K2_INSTRUMENT_CATEGORY_1[this.category1];
    }


    /**
     * Get the name of category 2.
     *
     * @return The name
     */
    public String getCategory2Name ()
    {
        return K2_INSTRUMENT_CATEGORY_2[this.category2];
    }


    /**
     * Get the name of category 3.
     *
     * @return The name
     */
    public String getCategory3Name ()
    {
        return K2_INSTRUMENT_CATEGORY_3[this.category3];
    }


    /**
     * A URL to the web site of the creator.
     *
     * @return The URL
     */
    public String getWebsite ()
    {
        return NULL_ENTRY.equals (this.website) ? "" : this.website;
    }


    /**
     * The patch level of the Kontakt version.
     *
     * @return The patch level
     */
    public int getPatchLevel ()
    {
        return this.svnRevision;
    }


    /**
     * Get the checksum of the header.
     *
     * @return The checksum, 4 byte for Kontakt 2-4.1, 16 bytes for 4.2
     */
    public byte [] getChecksum ()
    {
        return this.md5Checksum;
    }


    private static String fixBrokenCharacters (final String text)
    {
        final byte [] bytes = text.getBytes (StandardCharsets.ISO_8859_1);
        final StringBuilder sb = new StringBuilder ();
        int pos = 0;
        while (pos < text.length ())
            if (bytes[pos] == (byte) 0xEF && pos + 2 < bytes.length)
            {
                if (bytes[pos + 1] == (byte) 0xBF && bytes[pos + 2] == (byte) 0xBC)
                {
                    sb.append ('ü');
                    pos += 2;
                }
            }
            else if (bytes[pos] == (byte) 0xC3 && pos + 1 < bytes.length)
            {
                if (bytes[pos + 1] == (byte) 0xBC)
                {
                    sb.append ('ü');
                    pos++;
                }
            }
            else
            {
                sb.append ((char) bytes[pos]);
                pos++;
            }
        return sb.toString ();
    }
}
