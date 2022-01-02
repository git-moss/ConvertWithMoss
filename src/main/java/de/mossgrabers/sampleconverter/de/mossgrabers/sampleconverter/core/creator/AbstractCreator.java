// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.creator;

import de.mossgrabers.sampleconverter.core.AbstractCoreTask;
import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.model.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.model.IVelocityLayer;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.util.XMLUtils;

import org.w3c.dom.Document;

import javax.xml.parsers.ParserConfigurationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * Base class for creator classes.
 *
 * @author J&uuml;rgen Mo&szlig;graber
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
    protected String formatFileName (final String path, final String filename)
    {
        return new StringBuilder ().append (path).append ('/').append (filename).toString ().replace ('\\', '/');
    }


    protected static int check (final int value, final int defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }


    /**
     * Adds a file to the ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zos The ZIP output stream
     * @param info The file to add
     * @throws IOException Could not read the file
     */
    protected static void addFileToZip (final Set<String> alreadyStored, final ZipOutputStream zos, final ISampleMetadata info) throws IOException
    {
        addFileToZip (alreadyStored, zos, info, null);
    }


    /**
     * Adds a file to the ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zos The ZIP output stream
     * @param info The file to add
     * @param path Optional path (may be null), must not end with a slash
     * @throws IOException Could not read the file
     */
    protected static void addFileToZip (final Set<String> alreadyStored, final ZipOutputStream zos, final ISampleMetadata info, final String path) throws IOException
    {
        final Optional<String> filename = info.getUpdatedFilename ();
        if (filename.isEmpty ())
            return;
        String name = filename.get ();
        if (path != null)
            name = path + "/" + name;
        if (alreadyStored.contains (name))
            return;
        alreadyStored.add (name);
        final ZipEntry entry = new ZipEntry (name);
        zos.putNextEntry (entry);
        info.writeSample (zos);
        zos.closeEntry ();
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
     * Adds an UTF-8 text file to the ZIP output stream.
     *
     * @param zos The ZIP output stream
     * @param fileName The name to use for the file when added
     * @param metadata The text content of the file to add
     * @throws IOException Could not add the file
     */
    protected void zipMetadataFile (final ZipOutputStream zos, final String fileName, final String metadata) throws IOException
    {
        zos.putNextEntry (new ZipEntry (fileName));
        final Writer writer = new BufferedWriter (new OutputStreamWriter (zos, StandardCharsets.UTF_8));
        writer.write (metadata);
        writer.flush ();
        zos.closeEntry ();
    }


    /**
     * Add all samples from all layers in the given ZIP output stream.
     *
     * @param zos The ZIP output stream to which to add the samples
     * @param relativeFolderName The relative folder under which to store the file in the ZIP
     * @param multisampleSource The multisample
     * @throws IOException Could not store the samples
     */
    protected void zipSamples (final ZipOutputStream zos, final String relativeFolderName, final IMultisampleSource multisampleSource) throws IOException
    {
        int outputCount = 0;
        final Set<String> alreadyStored = new HashSet<> ();
        for (final IVelocityLayer layer: multisampleSource.getLayers ())
        {
            for (final ISampleMetadata info: layer.getSampleMetadata ())
            {
                this.notifier.log ("IDS_NOTIFY_PROGRESS");
                outputCount++;
                if (outputCount % 80 == 0)
                    this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                addFileToZip (alreadyStored, zos, info, relativeFolderName);
            }
        }
    }


    /**
     * Store all samples from all layers in the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multisample
     * @throws IOException Could not store the samples
     */
    protected void storeSamples (final File sampleFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        int outputCount = 0;
        for (final IVelocityLayer layer: multisampleSource.getLayers ())
        {
            for (final ISampleMetadata info: layer.getSampleMetadata ())
            {
                final Optional<String> filename = info.getUpdatedFilename ();
                if (filename.isEmpty ())
                    continue;
                try (final FileOutputStream fos = new FileOutputStream (new File (sampleFolder, filename.get ())))
                {
                    this.notifier.log ("IDS_NOTIFY_PROGRESS");
                    outputCount++;
                    if (outputCount % 80 == 0)
                        this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                    info.writeSample (fos);
                }
            }
        }
    }


    protected static List<IVelocityLayer> getNonEmptyLayers (final List<IVelocityLayer> layers)
    {
        final List<IVelocityLayer> cleanedLayers = new ArrayList<> ();
        for (final IVelocityLayer layer: layers)
        {
            if (!layer.getSampleMetadata ().isEmpty ())
                cleanedLayers.add (layer);
        }
        return cleanedLayers;
    }


    // Normalize to [0..1]
    protected static double normalizeValue (final double value, final double minimum, final double maximum)
    {
        return clamp (value, minimum, maximum) / maximum;
    }


    protected static double clamp (double value, double minimum, double maximum)
    {
        return Math.max (minimum, Math.min (value, maximum));
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
}
