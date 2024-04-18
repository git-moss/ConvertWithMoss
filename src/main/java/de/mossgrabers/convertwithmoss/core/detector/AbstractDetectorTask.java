// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.io.File;
import java.io.FileNotFoundException;
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

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AiffFileSampleData;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.FlacFileSampleData;
import de.mossgrabers.convertwithmoss.file.OggFileSampleData;
import de.mossgrabers.convertwithmoss.file.wav.BroadcastAudioExtensionChunk;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;
import javafx.concurrent.Task;


/**
 * Base class for detector tasks.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractDetectorTask extends Task<Boolean>
{
    private static final AudioFileFormat.Type    OGG_TYPE              = new AudioFileFormat.Type ("OGG", "ogg");
    private static final AudioFileFormat.Type    FLAC_TYPE             = new AudioFileFormat.Type ("FLAC", "flac");

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
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     * @param fileEndings The file ending(s) identifying the files
     */
    protected AbstractDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata, final String... fileEndings)
    {
        this.notifier = notifier;
        this.consumer = consumer;
        this.sourceFolder = sourceFolder;
        this.metadataConfig = metadata;
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
     * Read and parse the given multi-sample file.
     *
     * @param sourceFile The file to process
     * @return The parsed multi-sample information
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
                if (lower.endsWith (ending))
                    return true;

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
        return minimum + MathUtils.clamp (value, 0, 1) * (maximum - minimum);
    }


    /**
     * Check the type of the source sample for compatibility and handle them accordingly. This
     * method supports WAV, AIF, AIFF, OGG and FLAC files.
     *
     * @param sampleFile The sample file for which to create sample metadata
     * @return The matching sample metadata, support is WAV and AIFF
     * @throws IOException Unsupported sample file type
     */
    protected ISampleZone createSampleZone (final File sampleFile) throws IOException
    {
        return new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), createSampleData (sampleFile));
    }


    /**
     * Check the type of the source sample for compatibility and handle them accordingly. This
     * method supports WAV, AIF, AIFF, OGG and FLAC files.
     *
     * @param sampleFile The sample file for which to create sample metadata
     * @return The matching sample metadata, support is WAV and AIFF
     * @throws IOException Unsupported sample file type
     */
    protected ISampleData createSampleData (final File sampleFile) throws IOException
    {
        if (!sampleFile.exists ())
            throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ()));

        try
        {
            ISampleData sampleData = null;

            if (sampleFile.getName ().toLowerCase ().endsWith (".aiff"))
                // Note: only AIF is picked up as correct ending below
                sampleData = new AiffFileSampleData (sampleFile);
            else
            {
                final AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat (sampleFile);
                final AudioFileFormat.Type type = audioFileFormat.getType ();
                if (AudioFileFormat.Type.WAVE.equals (type))
                {
                    if (AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                        sampleData = new WavFileSampleData (sampleFile);
                }
                else if (AudioFileFormat.Type.AIFF.equals (type))
                    sampleData = new AiffFileSampleData (sampleFile);
                else if (OGG_TYPE.equals (type))
                    sampleData = new OggFileSampleData (sampleFile);
                else if (FLAC_TYPE.equals (type))
                    sampleData = new FlacFileSampleData (sampleFile);
                if (sampleData == null)
                    throw new IOException (Functions.getMessage ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", type.toString ()));
            }

            return sampleData;
        }
        catch (final UnsupportedAudioFileException | IOException ex)
        {
            throw new IOException (Functions.getMessage ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", sampleFile.getName ()));
        }
    }


    /**
     * Guess metadata from the sample names and folder. Also check for the Broadcast Audio Extension
     * chunk in the first sample WAV.
     *
     * @param metadata The metadata object to fill
     * @param sampleFiles The wave file data
     * @param parts The already processed parts of the file name
     */
    protected void createMetadata (final IMetadata metadata, final WavFileSampleData [] sampleFiles, final String [] parts)
    {
        this.createMetadata (metadata, sampleFiles == null || sampleFiles.length == 0 ? null : sampleFiles[0], parts);
    }


    /**
     * Guess metadata from the sample names and folder. Also check for the Broadcast Audio Extension
     * chunk in the sample WAV.
     *
     * @param metadata The metadata object to fill
     * @param sampleFile The wave file data
     * @param parts The already processed parts of the file name
     */
    protected void createMetadata (final IMetadata metadata, final WavFileSampleData sampleFile, final String [] parts)
    {
        this.createMetadata (metadata, sampleFile, parts, null);
    }


    /**
     * Guess metadata from the sample names and folder. Also check for the Broadcast Audio Extension
     * chunk in the sample WAV.
     *
     * @param metadata The metadata object to fill
     * @param sampleFile The wave file data
     * @param parts The already processed parts of the file name
     * @param category If the category is not null, it is assigned and not detected
     */
    protected void createMetadata (final IMetadata metadata, final WavFileSampleData sampleFile, final String [] parts, final String category)
    {
        metadata.detectMetadata (this.metadataConfig, parts, category);

        if (sampleFile == null)
            return;
        final BroadcastAudioExtensionChunk broadcastAudioExtensionChunk = sampleFile.getWaveFile ().getBroadcastAudioExtensionChunk ();
        if (broadcastAudioExtensionChunk == null)
            return;

        final String originator = broadcastAudioExtensionChunk.getOriginator ();
        if (!originator.isBlank ())
            metadata.setCreator (originator);
        final String description = broadcastAudioExtensionChunk.getDescription ();
        if (!description.isBlank ())
            metadata.setDescription (description);
        metadata.setCreationTime (broadcastAudioExtensionChunk.getOriginationDateTime ());
    }


    /**
     * Get the first sample WAV file of the first group.
     *
     * @param groups The groups
     * @return The WAV file or null if it does not exist
     */
    protected WavFileSampleData getFirstSample (final List<IGroup> groups)
    {
        WavFileSampleData sampleFile = null;
        if (!groups.isEmpty ())
        {
            final List<ISampleZone> zones = groups.get (0).getSampleZones ();
            if (!zones.isEmpty () && zones.get (0).getSampleData () instanceof final WavFileSampleData sf)
                sampleFile = sf;
        }
        return sampleFile;
    }


    /**
     * If the sample is not found in the given folder, a search is started from one folder up and
     * search recursively for the wave file.
     *
     * @param folder The folder where the sample is expected
     * @param fileName The name of the sample file
     * @param levels The number of levels to move upwards to start the search
     * @return The sample file
     */
    protected static File findSampleFile (final File folder, final String fileName, final int levels)
    {
        final File file = new File (fileName);
        File sampleFile;

        // First check if the filename is absolute, if the absolute path does not exist, try only
        // the name
        if (fileName.startsWith ("file:"))
            sampleFile = new File (folder, file.getName ());
        else if (file.isAbsolute ())
        {
            if (file.exists ())
                sampleFile = file;
            else
                sampleFile = new File (folder, file.getName ());
        }
        else
            sampleFile = new File (folder, fileName);

        if (sampleFile.exists ())
            return sampleFile;

        // Go n-levels up and start searching for the file
        File startDirectory = null;
        for (int i = 0; i < levels; i++)
        {
            final File dir = startDirectory == null ? folder.getParentFile () : startDirectory.getParentFile ();
            if (dir.exists () && dir.isDirectory ())
                startDirectory = dir;
        }
        if (startDirectory == null)
            return sampleFile;

        // Go one folder up and search recursively...
        final File found = findSampleFileRecursively (startDirectory, sampleFile.getName ());
        // Returning the original file triggers the expected error...
        return found == null ? sampleFile : found;
    }


    private static File findSampleFileRecursively (final File folder, final String fileName)
    {
        File sampleFile = new File (folder, fileName);
        if (sampleFile.exists ())
            return sampleFile;

        final File [] children = folder.listFiles (File::isDirectory);
        if (children != null)
        {
            for (final File subFolder: children)
            {
                sampleFile = findSampleFileRecursively (subFolder, fileName);
                if (sampleFile != null)
                    return sampleFile;
            }
        }

        return null;
    }
}
