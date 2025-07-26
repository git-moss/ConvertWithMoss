// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import de.mossgrabers.convertwithmoss.core.AbstractCoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.FlacFileSampleData;
import de.mossgrabers.convertwithmoss.file.OggFileSampleData;
import de.mossgrabers.convertwithmoss.file.aiff.AiffCommonChunk;
import de.mossgrabers.convertwithmoss.file.aiff.AiffFile;
import de.mossgrabers.convertwithmoss.file.aiff.AiffFileSampleData;
import de.mossgrabers.convertwithmoss.file.ncw.NcwFileSampleData;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Base class for detector descriptors.
 *
 * @param <T> The type of the settings
 * 
 * @author Jürgen Moßgraber
 */
public abstract class AbstractDetector<T extends ICoreTaskSettings> extends AbstractCoreTask<T> implements IDetector<T>
{
    private static final String               IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED = "IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED";
    private static final AudioFileFormat.Type OGG_TYPE                            = new AudioFileFormat.Type ("OGG", "ogg");
    private static final AudioFileFormat.Type FLAC_TYPE                           = new AudioFileFormat.Type ("FLAC", "flac");

    private final ExecutorService             executor                            = Executors.newSingleThreadExecutor ();

    protected Consumer<IMultisampleSource>    multisampleSourceConsumer;
    protected Consumer<IPerformanceSource>    performanceSourceConsumer;
    protected File                            sourceFolder;
    protected String []                       fileEndings;
    protected boolean                         detectPerformances;

    protected final Map<String, Set<String>>  unsupportedElements                 = new HashMap<> ();
    protected final Map<String, Set<String>>  unsupportedAttributes               = new HashMap<> ();

    private final AtomicBoolean               isCancelled                         = new AtomicBoolean (false);


    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     * @param userInterface The user interface
     * @param fileEndings The file endings to search for
     */
    protected AbstractDetector (final String name, final String prefix, final INotifier notifier, final T userInterface, final String... fileEndings)
    {
        super (name, prefix, notifier, userInterface);

        this.fileEndings = fileEndings;
    }


    /** {@inheritDoc} */
    @Override
    public final void detect (final File sourceFolder, final Consumer<IMultisampleSource> multisampleSourceConsumer, final Consumer<IPerformanceSource> performanceSourceConsumer, final boolean detectPerformances)
    {
        this.configureFileEndings (detectPerformances);

        this.multisampleSourceConsumer = multisampleSourceConsumer;
        this.performanceSourceConsumer = performanceSourceConsumer;
        this.sourceFolder = sourceFolder;
        this.detectPerformances = detectPerformances;

        this.unsupportedElements.clear ();
        this.unsupportedAttributes.clear ();

        this.startDetection ();
    }


    /**
     * Overwrite in case that file endings need to be set dynamically.
     * 
     * @param detectPerformances If true, performances are detected otherwise presets
     */
    protected void configureFileEndings (final boolean detectPerformances)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void cancel ()
    {
        this.isCancelled.set (true);
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        this.executor.shutdown ();
    }


    /**
     * Start the detection process.
     */
    protected void startDetection ()
    {
        this.notifier.updateButtonStates (false);
        this.executor.execute (this);
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return false;
    }


    /**
     * Has the task been cancelled?
     * 
     * @return True if cancelled
     */
    public boolean isCancelled ()
    {
        return this.isCancelled.get ();
    }


    /**
     * Detect recursively all potential multi-sample files in the given folder.
     *
     * @param folder The folder to start detection.
     */
    protected void detect (final File folder)
    {
        this.isCancelled.set (false);

        this.notifier.log ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ());
        if (this.waitForDelivery ())
            return;

        final File [] listFiles = this.listFiles (folder, this.fileEndings);
        if (listFiles == null)
        {
            this.notifier.log ("IDS_NOT_A_DIRECTORY", folder.getAbsolutePath ());
            return;
        }
        for (final File file: listFiles)
            // Ignore MacOS crap
            if (!file.getName ().startsWith ("._"))
            {
                this.notifier.log ("IDS_NOTIFY_ANALYZING", file.getAbsolutePath ());

                if (this.waitForDelivery ())
                    break;

                if (this.detectPerformances)
                    this.handlePerformanceFile (file);
                else
                    this.handlePresetFile (file);
            }
    }


    private void handlePresetFile (final File file)
    {
        try
        {
            for (final IMultisampleSource multisample: this.readPresetFile (file))
            {
                if (this.waitForDelivery ())
                    break;

                this.updateCreationDateTime (multisample.getMetadata (), file);
                this.multisampleSourceConsumer.accept (multisample);

                if (this.isCancelled ())
                    return;
            }
        }
        catch (final RuntimeException ex)
        {
            this.notifier.logError (ex);
        }
    }


    private void handlePerformanceFile (final File file)
    {
        try
        {
            final List<IPerformanceSource> performances = this.readPerformanceFiles (file);
            if (performances.isEmpty () || this.waitForDelivery ())
                return;

            for (IPerformanceSource performance: performances)
            {
                this.updateCreationDateTime (performance.getMetadata (), file);
                this.performanceSourceConsumer.accept (performance);
            }
        }
        catch (final RuntimeException ex)
        {
            this.notifier.logError (ex);
        }
    }


    /**
     * Read and parse the given multi-sample file.
     *
     * @param sourceFile The file to process
     * @return The parsed multi-sample information
     */
    protected abstract List<IMultisampleSource> readPresetFile (final File sourceFile);


    /**
     * Read and parse the given performance file. Implement if performance files are supported by
     * this detector.
     *
     * @param sourceFile The file to process
     * @return The parsed performance(s)
     */
    protected List<IPerformanceSource> readPerformanceFiles (final File sourceFile)
    {
        throw new RuntimeException (this.getClass ().getName () + " does not support Performance files.");
    }


    /** {@inheritDoc} */
    @Override
    public void run ()
    {
        try
        {
            this.detect (this.sourceFolder);
        }
        catch (final RuntimeException ex)
        {
            this.notifier.logError (ex);
        }
        catch (final OutOfMemoryError err)
        {
            this.notifier.logError (err);
        }
        this.notifier.finished (this.isCancelled ());
    }


    /**
     * Check for task cancellation.
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
    protected void checkChildTags (final String tagName, final Set<String> supportedElements, final List<Element> childElements)
    {
        for (final Element childElement: childElements)
        {
            final String nodeName = childElement.getNodeName ();
            if (!supportedElements.contains (nodeName))
                this.unsupportedElements.computeIfAbsent (tagName, _ -> new HashSet<> ()).add (nodeName);
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
                this.unsupportedAttributes.computeIfAbsent (tagName, _ -> new HashSet<> ()).add (nodeName);
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
        return minimum + Math.clamp (value, 0, 1) * (maximum - minimum);
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
        return new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), createSampleData (sampleFile, this.notifier));
    }


    /**
     * Check the type of the source sample for compatibility and handle them accordingly. This
     * method supports WAV, AIF, AIFF, OGG and FLAC files. The file is compressed in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the sample file
     * @param sampleFile The sample file for which to create sample metadata
     * @return The matching sample metadata, support is WAV and AIFF
     * @throws IOException Unsupported sample file type
     */
    protected ISampleData createSampleData (final File zipFile, final File sampleFile) throws IOException
    {
        final String fileEnding = sampleFile.getName ().toLowerCase ();

        if (fileEnding.endsWith (".wav"))
            return new WavFileSampleData (zipFile, sampleFile);

        if (fileEnding.endsWith (".aiff") || fileEnding.endsWith (".aif"))
            return new AiffFileSampleData (zipFile, sampleFile);

        throw new IOException (Functions.getMessage (IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED, sampleFile.getName ()));

    }


    /**
     * Check the type of the source sample for compatibility and handle them accordingly. This
     * method supports WAV, AIF, AIFF, OGG and FLAC files.
     *
     * @param sampleFile The sample file for which to create sample metadata
     * @param notifier Where to report errors
     * @return The matching sample metadata, support is WAV and AIFF
     * @throws IOException Unsupported sample file type
     */
    public static IFileBasedSampleData createSampleData (final File sampleFile, final INotifier notifier) throws IOException
    {
        if (!sampleFile.exists ())
            throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ()));

        final String fileEnding = sampleFile.getName ().toLowerCase ();
        try
        {
            // Note: only AIF ending is picked up as correct ending below and it also does not
            // accept all AIFF files
            if (fileEnding.endsWith (".aiff") || fileEnding.endsWith (".aif"))
            {
                // Check if it is a compressed (= encrypted) AIFC file and report accordingly
                final AiffFile aiffFile = new AiffFile (sampleFile);
                final AiffCommonChunk commonChunk = aiffFile.getCommonChunk ();
                if (commonChunk != null)
                {
                    final String compressionType = commonChunk.getCompressionType ();
                    if (compressionType != null)
                        throw new IOException (Functions.getMessage ("IDS_ERR_COMPRESSED_AIFF_FILE", sampleFile.getName (), commonChunk.getCompressionName (), compressionType));
                }

                return new AiffFileSampleData (sampleFile);
            }

            if (fileEnding.endsWith (".ncw"))
                return new NcwFileSampleData (sampleFile);

            IFileBasedSampleData sampleData = null;
            final AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat (sampleFile);
            final AudioFileFormat.Type type = audioFileFormat.getType ();
            if (AudioFileFormat.Type.WAVE.equals (type))
            {
                if (AudioFileUtils.checkSampleFile (sampleFile, notifier))
                    sampleData = new WavFileSampleData (sampleFile);
            }
            else if (AudioFileFormat.Type.AIFF.equals (type))
                sampleData = new AiffFileSampleData (sampleFile);
            else if (OGG_TYPE.equals (type))
                sampleData = new OggFileSampleData (sampleFile);
            else if (FLAC_TYPE.equals (type))
                sampleData = new FlacFileSampleData (sampleFile);

            if (sampleData == null)
                throw new IOException (Functions.getMessage (IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED, type.toString ()));
            return sampleData;
        }
        catch (final UnsupportedAudioFileException | IOException ex)
        {
            throw new IOException (Functions.getMessage (IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED, sampleFile.getName ()), ex);
        }
    }


    /**
     * Guess metadata from the sample names and folder. Also check for the Broadcast Audio Extension
     * chunk in the first sample WAV.
     *
     * @param metadata The metadata object to fill
     * @param sampleData The wave file data
     * @param parts The already processed parts of the file name
     */
    protected void createMetadata (final IMetadata metadata, final List<IFileBasedSampleData> sampleData, final String [] parts)
    {
        this.createMetadata (metadata, sampleData == null || sampleData.isEmpty () ? null : sampleData.get (0), parts);
    }


    /**
     * Guess metadata from the sample names and folder. Also check for the Broadcast Audio Extension
     * chunk in the sample WAV.
     *
     * @param metadata The metadata object to fill
     * @param sampleData The sample file data
     * @param parts The already processed parts of the file name
     */
    protected void createMetadata (final IMetadata metadata, final IFileBasedSampleData sampleData, final String [] parts)
    {
        this.createMetadata (metadata, sampleData, parts, null);
    }


    /**
     * Guess metadata from the sample names and folder. Also check for the Broadcast Audio Extension
     * chunk in the sample WAV.
     *
     * @param metadata The metadata object to fill
     * @param sampleData The wave file data
     * @param parts The already processed parts of the file name
     * @param category If the category is not null, it is assigned and not detected
     */
    protected void createMetadata (final IMetadata metadata, final IFileBasedSampleData sampleData, final String [] parts, final String category)
    {
        if (this.settingsConfiguration instanceof MetadataSettingsUI metadataSettings)
        {
            metadata.detectMetadata (metadataSettings, parts, category);
            if (sampleData != null)
                sampleData.updateMetadata (metadata);
        }
    }


    /**
     * Update the metadata creation date/time from the source file.
     *
     * @param metadata The metadata to update
     * @param sourceFile The source file from which to get the creation date/time
     */
    protected void updateCreationDateTime (final IMetadata metadata, final File sourceFile)
    {
        if (metadata.getCreationDateTime () != null)
            return;

        try
        {
            final BasicFileAttributes attrs = Files.readAttributes (sourceFile.toPath (), BasicFileAttributes.class);
            final FileTime creationTime = attrs.creationTime ();
            final FileTime modifiedTime = attrs.lastModifiedTime ();
            final long creationTimeMillis = creationTime.toMillis ();
            final long modifiedTimeMillis = modifiedTime.toMillis ();
            metadata.setCreationDateTime (new Date (creationTimeMillis < modifiedTimeMillis ? creationTimeMillis : modifiedTimeMillis));
        }
        catch (final IOException ex)
        {
            metadata.setCreationDateTime (new Date ());
        }
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
     * search recursively for the sample file.
     *
     * @param notifier Where to write logging info to
     * @param folder The folder where the sample is expected
     * @param previousFolder The folder in which the previous sample was found, might be null
     * @param fileName The name of the sample file
     * @param levels The number of levels to move upwards to start the search
     * @return The sample file
     */
    public static File findSampleFile (final INotifier notifier, final File folder, final File previousFolder, final String fileName, final int levels)
    {
        return findFile (notifier, folder, previousFolder, fileName, levels, "sample");
    }


    /**
     * If the file is not found in the given folder, a search is started from one folder up and
     * search recursively for the file.
     *
     * @param notifier Where to write logging info to
     * @param folder The folder where the file is expected
     * @param previousFolder The folder in which the previous file was found, might be null
     * @param fileName The name of the file to look for
     * @param levels The number of levels to move upwards to start the search
     * @param fileType The name of the file type, e.g. 'sample'
     * @return The file
     */
    public static File findFile (final INotifier notifier, final File folder, final File previousFolder, final String fileName, final int levels, final String fileType)
    {
        final File file = new File (fileName);

        // First search in the folder where the previous sample file was found
        File sampleFile;
        if (previousFolder != null)
        {
            sampleFile = findInTopFolder (previousFolder, fileName, file);
            if (sampleFile.exists ())
                return sampleFile;
        }

        // Now try the top folder
        sampleFile = findInTopFolder (folder, fileName, file);
        if (sampleFile.exists ())
            return sampleFile;

        // Now brute force: go n-levels up and start searching for the file
        File startDirectory = folder;
        for (int i = 0; i < levels; i++)
        {
            final File dir = startDirectory.getParentFile ();
            if (dir.exists () && dir.isDirectory ())
                startDirectory = dir;
        }

        // ... and search recursively...
        if (notifier != null)
            notifier.log ("IDS_NOTIFY_SEARCH_FILE_IN", fileType, startDirectory.getAbsolutePath ());
        final File found = findFileRecursively (startDirectory, sampleFile.getName ());
        // Returning the original file triggers the expected error...
        if (found == null)
        {
            if (notifier != null)
                notifier.logText ("\n");
            return sampleFile;
        }

        if (notifier != null)
            notifier.log ("IDS_NOTIFY_SEARCH_FILE_IN_FOUND");
        return found;
    }


    private static File findInTopFolder (final File folder, final String fileName, final File file)
    {
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
        return sampleFile;
    }


    private static File findFileRecursively (final File folder, final String fileName)
    {
        File sampleFile = new File (folder, fileName);
        if (sampleFile.exists ())
            return sampleFile;

        final File [] children = folder.listFiles (File::isDirectory);
        if (children != null)
            for (final File subFolder: children)
            {
                if (subFolder.isHidden () || subFolder.getName ().startsWith ("."))
                    continue;

                sampleFile = findFileRecursively (subFolder, fileName);
                if (sampleFile != null)
                    return sampleFile;
            }

        return null;
    }


    protected void readMissingValues (final ISampleZone zone)
    {
        try
        {
            // Read loop and root key if necessary. If loop was not explicitly
            // deactivated, there is a loop present, which might need to read the
            // parameters from the WAV file
            List<ISampleLoop> loops = zone.getLoops ();
            boolean readLoops = false;
            ISampleLoop oldLoop = null;
            if (!loops.isEmpty ())
            {
                oldLoop = loops.get (0);
                readLoops = oldLoop.getStart () < 0 || oldLoop.getEnd () < 0;
                if (readLoops)
                    loops.clear ();
            }

            zone.getSampleData ().addZoneData (zone, true, readLoops);

            // If start or end was already set overwrite it here
            if (readLoops)
            {
                loops = zone.getLoops ();
                // The null check is not necessary but otherwise we get an Eclipse warning
                if (oldLoop != null && !loops.isEmpty ())
                {
                    final ISampleLoop newLoop = loops.get (0);

                    final int oldStart = oldLoop.getStart ();
                    if (oldStart >= 0)
                        newLoop.setStart (oldStart);
                    final int oldEnd = oldLoop.getEnd ();
                    if (oldEnd >= 0)
                        newLoop.setEnd (oldEnd);

                    // If values are still not set remove the loop
                    if (newLoop.getStart () < 0 && newLoop.getEnd () < 0)
                        loops.clear ();
                }
            }
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex);
        }
    }
}
