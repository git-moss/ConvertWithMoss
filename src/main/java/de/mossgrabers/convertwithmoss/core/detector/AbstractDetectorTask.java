// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.Utils;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AiffFileSampleData;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import javafx.concurrent.Task;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * @author Jürgen Moßgraber
 */
public abstract class AbstractDetectorTask extends Task<Boolean>
{
    protected final INotifier                    notifier;
    protected final Consumer<IMultisampleSource> consumer;
    protected final File                         sourceFolder;
    protected final String []                    fileEndings;

    private final Map<String, Set<String>>       unsupportedElements   = new HashMap<> ();
    private final Map<String, Set<String>>       unsupportedAttributes = new HashMap<> ();

    protected final IMetadataConfig              metadataConfig;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param fileEndings The file ending(s) identifying the files
     * @param metadata Additional metadata configuration parameters
     */
    protected AbstractDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata, final String... fileEndings)
    {
        this.notifier = notifier;
        this.consumer = consumer;
        this.sourceFolder = sourceFolder;
        this.fileEndings = fileEndings;
        this.metadataConfig = metadata;
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


    /**
     * Loads a text file in UTF-8 encoding. If UTF-8 fails a string is created anyway but with
     * unspecified behavior.
     *
     * @param file The file to load
     * @return The loaded text
     * @throws IOException Could not load the file
     */
    protected String loadTextFile (final File file) throws IOException
    {
        final Path path = file.toPath ();
        try
        {
            return Files.readString (path);
        }
        catch (final IOException ex)
        {
            final String string = new String (Files.readAllBytes (path));
            this.notifier.logError ("IDS_NOTIFY_ERR_ILLEGAL_CHARACTER", ex);
            return string;
        }
    }


    /**
     * Translates a value in the range of [0..1] to the range [minimum..maximum]. Ensures that the
     * given value is in the range [0..1].
     *
     * @param value The value to translate
     * @param minimum The minimum of the range
     * @param maximum The maximum of the range
     * @return The translated value
     */
    protected static double denormalizeValue (final double value, final double minimum, final double maximum)
    {
        return minimum + Utils.clamp (value, 0, 1) * (maximum - minimum);
    }


    /**
     * Check the type of the source sample for compatibility and handle them accordingly.
     *
     * @param sampleFile The sample file for which to create sample metadata
     * @return The matching sample metadata, support is WAV and AIFF
     * @throws IOException
     */
    protected DefaultSampleZone createSampleMetadata (final File sampleFile) throws IOException
    {
        try
        {
            ISampleData sampleData = null;
            final AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat (sampleFile);
            final AudioFileFormat.Type type = audioFileFormat.getType ();
            if (AudioFileFormat.Type.WAVE.equals (type))
            {
                if (AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                    sampleData = new WavFileSampleData (sampleFile);
            }
            else if (AudioFileFormat.Type.AIFF.equals (type))
            {
                this.notifier.log ("IDS_NOTIFY_CONVERT_TO_WAV", sampleFile.getName ());
                sampleData = new AiffFileSampleData (sampleFile);
            }
            if (sampleData == null)
                throw new IOException (Functions.getMessage ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", type.toString ()));
            return new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
        }
        catch (final UnsupportedAudioFileException | IOException ex)
        {
            throw new IOException (Functions.getMessage ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", sampleFile.getName ()));
        }
    }
}
