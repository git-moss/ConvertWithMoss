// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt2;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.Magic;

import java.io.DataInput;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;


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
    private byte []                kontaktVersionBuffer      = new byte [4];
    private Date                   creation;
    private String                 libraryID;
    private int                    zones;
    private int                    groups;
    private int                    instruments;
    private int                    sampleSize;
    private boolean                isMonolith;
    private String                 minSupportedVersion;
    private byte []                minSupportedVersionBuffer = new byte [4];
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
        this.simpleDateFormatter.setTimeZone (TimeZone.getTimeZone ("UTC+1"));
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

        int magic = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        if (magic != Magic.KONTAKT2_INSTRUMENT_HEADER_LE && magic != Magic.KONTAKT2_INSTRUMENT_HEADER_BE && magic != Magic.KONTAKT42_INSTRUMENT_HEADER_LE && magic != Magic.KONTAKT42_INSTRUMENT_HEADER_BE)
            this.notifier.logError ("IDS_NKI_UNKNOWN_HEADER_MAGIC", Integer.toHexString (magic));

        this.patchType = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.kontaktVersion = this.readVersion (fileAccess, this.kontaktVersionBuffer);

        this.libraryID = StreamUtils.readASCII (fileAccess, 4, !this.isBigEndian);
        if (!KNOWN_LIBRARY_IDS.contains (this.libraryID))
            this.notifier.log ("IDS_NKI_UNKNOWN_BLOCK_ID", this.libraryID);

        this.creation = StreamUtils.readTimestamp (fileAccess, this.isBigEndian);

        // No idea yet about these 4 bytes...
        this.unknownA = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        // TODO Remove
        // if (this.unknownA != 0)
        // System.out.println ("u_a: " + this.unknownA);

        this.zones = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.groups = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.instruments = StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);
        this.sampleSize = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        this.isMonolith = StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian) == 1;
        this.minSupportedVersion = this.readVersion (fileAccess, this.minSupportedVersionBuffer);

        // TODO No idea yet about these 4 bytes...
        this.unknownB = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        // TODO Remove
        // if (this.unknownB != 0)
        // System.out.println ("u_c: " + this.unknownB);

        this.readMetadata (fileAccess);

        // TODO seems to be some kind of flags in a bit array
        this.flags = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        // TODO Remove
        // if (this.flags != 0)
        // System.out.println ("flags: " + this.flags);

        if (this.isFourDotTwo)
            this.md5Checksum = StreamUtils.readNBytes (fileAccess, 16);
        else
            this.checksum = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        this.svnRevision = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
    }


    /**
     * Write a Kontakt 2 - 4.2 header.
     *
     * @param out The output stream to write to
     * @throws IOException Error reading the header
     */
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.compressedLength, this.isBigEndian);
        StreamUtils.writeUnsigned16 (out, HEADER_KONTAKT_2, this.isBigEndian);
        StreamUtils.writeUnsigned32 (out, Magic.KONTAKT2_INSTRUMENT_HEADER_BE, this.isBigEndian);
        StreamUtils.writeUnsigned16 (out, this.patchType, this.isBigEndian);

        // TODO
        out.write (this.kontaktVersionBuffer);

        StreamUtils.writeASCII (out, this.libraryID, 4, !this.isBigEndian);
        StreamUtils.writeTimestamp (out, this.creation, this.isBigEndian);

        // No idea yet about these 4 bytes...
        StreamUtils.writeUnsigned32 (out, this.unknownA, this.isBigEndian);

        StreamUtils.writeUnsigned16 (out, this.zones, this.isBigEndian);
        StreamUtils.writeUnsigned16 (out, this.groups, this.isBigEndian);
        StreamUtils.writeUnsigned16 (out, this.instruments, this.isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.sampleSize, this.isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.isMonolith ? 1 : 0, this.isBigEndian);

        // TODO
        out.write (this.minSupportedVersionBuffer);

        // TODO No idea yet about these 4 bytes...
        StreamUtils.writeUnsigned32 (out, this.unknownB, this.isBigEndian);

        this.writeMetadata (out);

        // TODO seems to be some kind of flags in a bit array
        StreamUtils.writeUnsigned32 (out, this.flags, this.isBigEndian);

        if (this.isFourDotTwo)
            out.write (this.md5Checksum);
        else // TODO isn't that a checksum as well?
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
        this.author = StreamUtils.readASCII (fileAccess, 8, StandardCharsets.ISO_8859_1).trim ();

        this.category1 = fileAccess.read ();
        this.category2 = fileAccess.read ();
        this.category3 = fileAccess.read ();

        this.website = StreamUtils.readASCII (fileAccess, 86).trim ();

        // Padding
        StreamUtils.skipNBytes (fileAccess, 3);
    }


    private void writeMetadata (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.iconID, this.isBigEndian);
        StreamUtils.writeASCII (out, this.author, 8);

        out.write (this.category1);
        out.write (this.category2);
        out.write (this.category3);
        StreamUtils.writeASCII (out, this.website, 86);

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


    /**
     * Test to re-write the header - not working due to checksum.
     *
     * @param args Arguments
     */
    public static void main (final String [] args)
    {
        // TODO Remove

        final String filename = "E:\\ConvertWithMoss-Testdateien\\Kontakt\\Kontakt 2-4 Format\\4.0.0.2475\\NKI\\Across The Pacific.nki";
        final File sourceFile = new File (filename);
        try (final RandomAccessFile fileAccess = new RandomAccessFile (sourceFile, "r"))
        {
            final int typeID = fileAccess.readInt ();
            final boolean isBigEndian = typeID == Magic.KONTAKT2_INSTRUMENT_BE;

            final Kontakt2Header header = new Kontakt2Header (null, isBigEndian);
            header.read (fileAccess);

            final int numOfPendingbytes = (int) (sourceFile.length () - fileAccess.getFilePointer ());
            final byte [] rest = StreamUtils.readNBytes (fileAccess, numOfPendingbytes);

            // header.setLibraryID ("Kon2");

            try (final FileOutputStream out = new FileOutputStream (filename + ".x"))
            {
                StreamUtils.writeUnsigned32 (out, Magic.KONTAKT2_INSTRUMENT_BE, isBigEndian);
                header.write (out);
                out.write (rest);
            }
            catch (final IOException ex)
            {
                ex.printStackTrace ();
            }
        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }
}
