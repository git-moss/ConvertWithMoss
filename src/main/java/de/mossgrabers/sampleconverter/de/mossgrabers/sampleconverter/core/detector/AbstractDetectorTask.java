// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.detector;

import de.mossgrabers.sampleconverter.core.DefaultSampleMetadata;
import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.wav.FormatChunk;
import de.mossgrabers.sampleconverter.file.wav.WaveFile;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import javafx.concurrent.Task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;


/**
 * Base class for detector tasks.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractDetectorTask extends Task<Boolean>
{
    protected final INotifier                    notifier;
    protected final Consumer<IMultisampleSource> consumer;
    protected final File                         sourceFolder;
    protected final String []                    fileEndings;

    private final Map<String, Set<String>>       unsupportedElements   = new HashMap<> ();
    private final Map<String, Set<String>>       unsupportedAttributes = new HashMap<> ();

    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param fileEndings The file ending(s) identifying the files
     */
    protected AbstractDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final String... fileEndings)
    {
        this.notifier = notifier;
        this.consumer = consumer;
        this.sourceFolder = sourceFolder;
        this.fileEndings = fileEndings;
    }


    /**
     * Detect recursively all potential multi-sample files in the given folder.
     *
     * @param folder The folder to start detection.
     */
    protected void detect (final File folder)
    {
        this.notifier.log ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ());
        if (this.waitForDelivery ())
            return;

        for (final File file: this.listFiles (folder, this.fileEndings))
        {
            this.notifier.log ("IDS_NOTIFY_ANALYZING", file.getAbsolutePath ());

            if (this.waitForDelivery ())
                break;

            for (final IMultisampleSource multisample: this.readFile (file))
            {
                if (this.waitForDelivery ())
                    break;

                this.consumer.accept (multisample);

                if (this.isCancelled ())
                    return;
            }
        }
    }


    /**
     * Read and parse the given multi sample file.
     *
     * @param sourceFile The file to process
     * @return The parsed multisample information
     */
    protected abstract List<IMultisampleSource> readFile (final File sourceFile);


    /** {@inheritDoc} */
    @Override
    protected Boolean call () throws Exception
    {
        try
        {
            this.detect (this.sourceFolder);
        }
        catch (final RuntimeException ex)
        {
            this.notifier.logError (ex);
        }
        final boolean cancelled = this.isCancelled ();
        this.notifier.log (cancelled ? "IDS_NOTIFY_CANCELLED" : "IDS_NOTIFY_FINISHED");
        return Boolean.valueOf (cancelled);
    }


    /**
     * Wait a bit.
     *
     * @return The thread was cancelled if true
     */
    protected boolean waitForDelivery ()
    {
        try
        {
            Thread.sleep (10);
        }
        catch (final InterruptedException ex)
        {
            if (this.isCancelled ())
                return true;
            Thread.currentThread ().interrupt ();
        }
        return false;
    }


    /**
     * Split the parts of the path offset between the selected source folder and the currently
     * processed sub-folder.
     *
     * @param msSourceFolder The currently processed sub-folder
     * @param sourceFolder The source folder
     * @param name The name of the multisample
     * @return The array with all parts and the name in reverse order
     */
    protected static String [] createPathParts (final File msSourceFolder, final File sourceFolder, final String name)
    {
        File f = msSourceFolder;
        final List<String> pathNames = new ArrayList<> ();
        while (!f.equals (sourceFolder))
        {
            pathNames.add (f.getName ());
            f = f.getParentFile ();
        }
        pathNames.add (sourceFolder.getName ());

        final String [] result = new String [pathNames.size () + 1];
        result[0] = name;
        for (int i = 0; i < pathNames.size (); i++)
            result[i + 1] = pathNames.get (i);
        return result;
    }


    /**
     * Gets the name of the file without the ending. E.g. the filename 'aFile.jpeg' will return
     * 'aFile'.
     *
     * @param file The file from which to get the name
     * @return The name of the file without the ending
     */
    protected static String getNameWithoutType (final File file)
    {
        final String filename = file.getName ();
        final int pos = filename.lastIndexOf ('.');
        return pos == -1 ? filename : filename.substring (0, pos);
    }


    /**
     * Get the relative path of the sub-folder.
     *
     * @param sourceFolder The parent folder
     * @param folder The sub-folder
     * @return The relative path starting from the parent folder
     */
    protected String subtractPaths (final File sourceFolder, final File folder)
    {
        final String analysePath = folder.getAbsolutePath ();
        final String sourcePath = sourceFolder.getAbsolutePath ();
        if (analysePath.startsWith (sourcePath))
        {
            final String n = analysePath.substring (sourcePath.length ());
            return n.isEmpty () ? analysePath : n;
        }

        return analysePath;
    }


    /**
     * Get all files with specific endings. Recursively, calls detect method on sub-folders.
     *
     * @param folder The folder to start searching
     * @param endings The file endings to match, including the dot, e.g. '.wav'
     * @return All found files
     */
    protected File [] listFiles (final File folder, final String... endings)
    {
        return folder.listFiles ( (parent, name) -> {

            if (this.isCancelled ())
                return false;

            final File f = new File (parent, name);
            if (f.isDirectory ())
            {
                this.detect (f);
                return false;
            }

            final String lower = name.toLowerCase (Locale.US);
            for (final String ending: endings)
            {
                if (lower.endsWith (ending))
                    return true;
            }

            return false;
        });
    }


    /**
     * Test the sample file for compatibility.
     *
     * @param wavFile The sample file to check
     * @return True if OK
     */
    protected boolean checkSampleFile (final File wavFile)
    {
        if (!wavFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", wavFile.getAbsolutePath ());
            return false;
        }

        try
        {
            final FormatChunk formatChunk = new WaveFile (wavFile, true).getFormatChunk ();
            if (formatChunk == null)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", wavFile.getAbsolutePath ());
                return false;
            }

            final int numberOfChannels = formatChunk.getNumberOfChannels ();
            if (numberOfChannels > 2)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), wavFile.getAbsolutePath ());
                return false;
            }
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex);
            return false;
        }

        return true;
    }


    /**
     * Check if the sample start / stop and the sample rate is set, if not read them from the sample
     * file.
     *
     * @param sampleMetadata The sample metadata
     */
    protected void loadMissingValues (final DefaultSampleMetadata sampleMetadata)
    {
        try
        {
            sampleMetadata.addMissingInfoFromWaveFile ();
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex);
        }
    }


    /**
     * Check for unsupported child tags of a tag.
     *
     * @param tagName The name of the tag to check for its' attributes
     * @param supportedElements The supported child elements
     * @param childElements The present child elements
     */
    protected void checkChildTags (final String tagName, final Set<String> supportedElements, final Element [] childElements)
    {
        for (final Element childElement: childElements)
        {
            final String nodeName = childElement.getNodeName ();
            if (!supportedElements.contains (nodeName))
                this.unsupportedElements.computeIfAbsent (tagName, tag -> new HashSet<> ()).add (nodeName);
        }
    }


    /**
     * Formats and reports all unsupported elements.
     */
    protected void printUnsupportedElements ()
    {
        String tagName;
        for (final Entry<String, Set<String>> entry: this.unsupportedElements.entrySet ())
        {
            tagName = entry.getKey ();

            final StringBuilder sb = new StringBuilder ();
            entry.getValue ().forEach (element -> {
                if (!sb.isEmpty ())
                    sb.append (", ");
                sb.append (element);
            });

            if (!sb.isEmpty ())
                this.notifier.logError ("IDS_NOTIFY_UNSUPPORTED_ELEMENTS", tagName, sb.toString ());
        }
    }


    /**
     * Check for unsupported attributes of a tag.
     *
     * @param tagName The name of the tag to check for its' attributes
     * @param attributes The present attributes
     * @param supportedAttributes The supported attributes if the tag
     */
    protected void checkAttributes (final String tagName, final NamedNodeMap attributes, final Set<String> supportedAttributes)
    {
        for (int i = 0; i < attributes.getLength (); i++)
        {
            final String nodeName = attributes.item (i).getNodeName ();
            if (!supportedAttributes.contains (nodeName))
                this.unsupportedAttributes.computeIfAbsent (tagName, tag -> new HashSet<> ()).add (nodeName);
        }
    }


    /**
     * Formats and reports all unsupported attributes.
     */
    protected void printUnsupportedAttributes ()
    {
        String tagName;
        for (final Entry<String, Set<String>> entry: this.unsupportedAttributes.entrySet ())
        {
            tagName = entry.getKey ();

            final StringBuilder sb = new StringBuilder ();
            entry.getValue ().forEach (attribute -> {
                if (!sb.isEmpty ())
                    sb.append (", ");
                sb.append (attribute);
            });

            if (!sb.isEmpty ())
                this.notifier.logError ("IDS_NOTIFY_UNSUPPORTED_ATTRIBUTES", tagName, sb.toString ());
        }
    }


    /**
     * Fixes platform dependent slashes and resolves canonical paths.
     *
     * @param sampleBaseFolder The folder
     * @param sampleName The sample name (might include further relative folders)
     * @return The canonical file
     */
    protected File createCanonicalFile (final File sampleBaseFolder, final String sampleName)
    {
        File sampleFile = new File (sampleBaseFolder, sampleName.replace ('/', File.separatorChar).replace ('\\', File.separatorChar));
        try
        {
            sampleFile = sampleFile.getCanonicalFile ();
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_PATH", ex.getMessage ());
        }
        return sampleFile;
    }
}
