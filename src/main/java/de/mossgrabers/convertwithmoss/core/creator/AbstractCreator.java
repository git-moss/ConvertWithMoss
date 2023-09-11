// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.creator;

import de.mossgrabers.convertwithmoss.core.AbstractCoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.Utils;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;

import org.w3c.dom.Document;

import javax.xml.parsers.ParserConfigurationException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * Base class for creator classes.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractCreator extends AbstractCoreTask implements ICreator
{
    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param notifier The notifier
     */
    protected AbstractCreator (final String name, final INotifier notifier)
    {
        super (name, notifier);
    }


    /**
     * Create a new XML document.
     *
     * @return The document or not present if there is a configuration problem
     */
    protected Optional<Document> createXMLDocument ()
    {
        try
        {
            return Optional.of (XMLUtils.newDocument ());
        }
        catch (final ParserConfigurationException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_PARSER", ex);
            return Optional.empty ();
        }
    }


    protected static int check (final int value, final int defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }


    // Normalize to [0..1]
    protected static double normalizeValue (final double value, final double minimum, final double maximum)
    {
        return Utils.clamp (value, minimum, maximum) / maximum;
    }


    /**
     * Removes illegal characters from file names.
     *
     * @param filename A potential filename
     * @return The filename with illegal characters replaced by an underscore
     */
    protected static String createSafeFilename (final String filename)
    {
        return filename.replaceAll ("[\\\\/:*?\"<>|&\\.]", "_");
    }


    /**
     * Format the path and filename replacing all slashes with forward slashes.
     *
     * @param path A path
     * @param filename A filename
     * @return The formatted path
     */
    public static String formatFileName (final String path, final String filename)
    {
        return new StringBuilder ().append (path).append ('/').append (filename).toString ().replace ('\\', '/');
    }


    /**
     * Format a double attribute with a dot as the fraction separator.
     *
     * @param value The value to format
     * @param fractions The number of fractions to format
     * @return The formatted value
     */
    public static String formatDouble (final double value, final int fractions)
    {
        final String formatPattern = "%." + fractions + "f";
        return String.format (Locale.US, formatPattern, Double.valueOf (value));
    }


    /**
     * Create the given folder if it does not already exist
     *
     * @param folder The folder to create
     * @throws IOException If the folder could not be created
     */
    protected static void safeCreateDirectory (final File folder) throws IOException
    {
        if (folder.exists () || folder.mkdir ())
            return;

        // A parallel thread might already have created the directory and mkdir did return
        // false. Therefore check again before throwing an exception
        if (!folder.exists ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERROR_SAMPLE_FOLDER", folder.getAbsolutePath ()));
    }


    /**
     * Converts an unsigned integer to a number of bytes with least significant bytes first.
     *
     * @param value The value to convert
     * @param numberOfBytes The number of bytes to write
     * @return The converted integer
     */
    protected static byte [] toBytesLSB (final long value, final int numberOfBytes)
    {
        final byte [] data = new byte [numberOfBytes];

        for (int i = 0; i < numberOfBytes; i++)
            data[i] = (byte) (value >> 8 * i & 0xFF);

        return data;
    }


    /**
     * Adds an UTF-8 text file to the compressed ZIP output stream.
     *
     * @param zos The compressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param content The text content of the file to add
     * @throws IOException Could not add the file
     */
    protected void zipTextFile (final ZipOutputStream zos, final String fileName, final String content) throws IOException
    {
        this.zipDataFile (zos, fileName, content.getBytes (StandardCharsets.UTF_8));
    }


    /**
     * Adds a file (in form of an array of bytes) to a compressed ZIP output stream.
     *
     * @param zos The compressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param data The content of the file to add
     * @throws IOException Could not add the file
     */
    protected void zipDataFile (final ZipOutputStream zos, final String fileName, final byte [] data) throws IOException
    {
        zos.putNextEntry (new ZipEntry (fileName));
        zos.write (data);
        zos.flush ();
        zos.closeEntry ();
    }


    /**
     * Adds an UTF-8 text file to the uncompressed ZIP output stream.
     *
     * @param zos The uncompressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param content The text content of the file to add
     * @throws IOException Could not add the file
     */
    protected void storeTextFile (final ZipOutputStream zos, final String fileName, final String content) throws IOException
    {
        this.storeDataFile (zos, fileName, content.getBytes (StandardCharsets.UTF_8));
    }


    /**
     * Adds a file (in form of an array of bytes) to an uncompressed ZIP output stream.
     *
     * @param zos The uncompressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param data The content of the file to add
     * @throws IOException Could not add the file
     */
    protected void storeDataFile (final ZipOutputStream zos, final String fileName, final byte [] data) throws IOException
    {
        // The checksum needs to be calculated in advance before the data is written to the output
        // stream!
        final CRC32 crc = new CRC32 ();
        crc.update (data);
        putUncompressedEntry (zos, fileName, data, crc);
    }


    /**
     * Add all samples from all groups in the given compressed ZIP output stream.
     *
     * @param zos The ZIP output stream to which to add the samples
     * @param relativeFolderName The relative folder under which to store the file in the ZIP
     * @param multisampleSource The multisample
     * @throws IOException Could not store the samples
     */
    protected void zipSampleFiles (final ZipOutputStream zos, final String relativeFolderName, final IMultisampleSource multisampleSource) throws IOException
    {
        int outputCount = 0;
        final Set<String> alreadyStored = new HashSet<> ();
        for (final IGroup group: multisampleSource.getGroups ())
        {
            for (final ISampleMetadata info: group.getSampleMetadata ())
            {
                this.notifier.log ("IDS_NOTIFY_PROGRESS");
                outputCount++;
                if (outputCount % 80 == 0)
                    this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                zipSamplefile (alreadyStored, zos, info, relativeFolderName);
            }
        }
    }


    /**
     * Adds a sample file to the compressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zos The ZIP output stream
     * @param info The file to add
     * @throws IOException Could not read the file
     */
    protected static void zipSamplefile (final Set<String> alreadyStored, final ZipOutputStream zos, final ISampleMetadata info) throws IOException
    {
        zipSamplefile (alreadyStored, zos, info, null);
    }


    /**
     * Adds a sample file to the compressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zos The ZIP output stream
     * @param info The file to add
     * @param path Optional path (may be null), must not end with a slash
     * @throws IOException Could not read the file
     */
    protected static void zipSamplefile (final Set<String> alreadyStored, final ZipOutputStream zos, final ISampleMetadata info, final String path) throws IOException
    {
        final String name = checkSampleName (alreadyStored, info, path);
        if (name == null)
            return;

        final ZipEntry entry = new ZipEntry (name);
        zos.putNextEntry (entry);
        info.writeSample (zos);
        zos.closeEntry ();
    }


    /**
     * Add all samples from all groups in the given uncompressed ZIP output stream.
     *
     * @param zos The ZIP output stream to which to add the samples
     * @param relativeFolderName The relative folder under which to store the file in the ZIP
     * @param multisampleSource The multisample
     * @throws IOException Could not store the samples
     */
    protected void storeSampleFiles (final ZipOutputStream zos, final String relativeFolderName, final IMultisampleSource multisampleSource) throws IOException
    {
        int outputCount = 0;
        final Set<String> alreadyStored = new HashSet<> ();
        for (final IGroup group: multisampleSource.getGroups ())
        {
            for (final ISampleMetadata info: group.getSampleMetadata ())
            {
                this.notifier.log ("IDS_NOTIFY_PROGRESS");
                outputCount++;
                if (outputCount % 80 == 0)
                    this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                storeSamplefile (alreadyStored, zos, info, relativeFolderName);
            }
        }
    }


    /**
     * Adds a sample file to an uncompressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zos The ZIP output stream
     * @param info The file to add
     * @throws IOException Could not read the file
     */
    protected static void storeSamplefile (final Set<String> alreadyStored, final ZipOutputStream zos, final ISampleMetadata info) throws IOException
    {
        storeSamplefile (alreadyStored, zos, info, null);
    }


    /**
     * Adds a sample file to the uncompressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zos The ZIP output stream
     * @param info The file to add
     * @param path Optional path (may be null), must not end with a slash
     * @throws IOException Could not read the file
     */
    protected static void storeSamplefile (final Set<String> alreadyStored, final ZipOutputStream zos, final ISampleMetadata info, final String path) throws IOException
    {
        final String name = checkSampleName (alreadyStored, info, path);
        if (name == null)
            return;

        final CRC32 crc = new CRC32 ();
        try (final ByteArrayOutputStream bout = new ByteArrayOutputStream (); final OutputStream checkedOut = new CheckedOutputStream (bout, crc))
        {
            info.writeSample (checkedOut);
            putUncompressedEntry (zos, name, bout.toByteArray (), crc);
        }
    }


    /**
     * Writes all samples from all groups in the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multisample
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final List<File> writtenFiles = new ArrayList<> ();

        int outputCount = 0;
        for (final IGroup group: multisampleSource.getGroups ())
        {
            for (final ISampleMetadata info: group.getSampleMetadata ())
            {
                final Optional<String> filename = info.getUpdatedFilename ();
                if (filename.isEmpty ())
                    continue;
                final File file = new File (sampleFolder, filename.get ());
                try (final FileOutputStream fos = new FileOutputStream (file))
                {
                    this.notifier.log ("IDS_NOTIFY_PROGRESS");
                    outputCount++;
                    if (outputCount % 80 == 0)
                        this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                    info.writeSample (fos);
                }
                writtenFiles.add (file);
            }
        }

        return writtenFiles;
    }


    /**
     * Creates full path from the sample name and relative path and adding the prefix path.
     *
     * @param alreadyStored All paths already added to the ZIP file
     * @param info The sample to check
     * @param path The prefix path
     * @return The full path or null if already added to the ZIP
     */
    private static String checkSampleName (final Set<String> alreadyStored, final ISampleMetadata info, final String path)
    {
        final Optional<String> filename = info.getUpdatedFilename ();
        if (filename.isEmpty ())
            return null;
        String name = filename.get ();
        if (path != null)
            name = path + "/" + name;
        if (alreadyStored.contains (name))
            return null;
        alreadyStored.add (name);
        return name;
    }


    /**
     * Adds a new entry to an uncompressed ZIP output stream.
     *
     * @param zos The uncompressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param data The content of the file to add
     * @param crc The checksum
     * @throws IOException Could not add the file
     */
    private static void putUncompressedEntry (final ZipOutputStream zos, final String fileName, final byte [] data, final CRC32 crc) throws IOException
    {
        final ZipEntry entry = new ZipEntry (fileName);
        entry.setSize (data.length);
        entry.setCompressedSize (data.length);
        entry.setCrc (crc.getValue ());
        entry.setMethod (ZipOutputStream.STORED);

        zos.putNextEntry (entry);
        zos.write (data);
        zos.closeEntry ();
    }
}
