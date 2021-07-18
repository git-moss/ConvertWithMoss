// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.creator;

import de.mossgrabers.sampleconverter.core.AbstractCoreTask;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.util.XMLUtils;

import org.w3c.dom.Document;

import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
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
}
