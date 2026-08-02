// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
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
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.exception.MethodNotImplemented;
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
    private static final String               IDS_NOTIFY_ANALYZING                = "IDS_NOTIFY_ANALYZING";
    private static final String               IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED = "IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED";

    private static final AudioFileFormat.Type OGG_TYPE                            = new AudioFileFormat.Type ("OGG", "ogg");
    private static final AudioFileFormat.Type FLAC_TYPE                           = new AudioFileFormat.Type ("FLAC", "flac");
    /** Yield to the rest of the application after this number of delivered sources. */
    private static final int                  DELIVERY_YIELD_INTERVAL             = 64;

    private final ExecutorService             executor                            = Executors.newSingleThreadExecutor ();

    protected Consumer<IMultisampleSource>    multisampleSourceConsumer;
    protected Consumer<IPerformanceSource>    performanceSourceConsumer;
    protected File                            sourceFolder;
    protected boolean                         detectPerformances;

    protected final Map<String, Set<String>>  unsupportedElements                 = new HashMap<> ();
    protected final Map<String, Set<String>>  unsupportedAttributes               = new HashMap<> ();

    private final AtomicBoolean               isCancelled                         = new AtomicBoolean (false);
    /** If not empty, only these files are processed instead of searching the full source folder. */
    private final List<File>                  sourceFiles                         = new ArrayList<> ();

    private int                               deliveryCounter                     = 0;


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
        super (name, prefix, notifier, userInterface, fileEndings);
    }


    /** {@inheritDoc} */
    @Override
    public final void detect (final File sourceFolder, final List<File> sourceFiles, final Consumer<IMultisampleSource> multisampleSourceConsumer, final Consumer<IPerformanceSource> performanceSourceConsumer, final boolean detectPerformances)
    {
        this.configureFileEndings (detectPerformances);

        this.multisampleSourceConsumer = multisampleSourceConsumer;
        this.performanceSourceConsumer = performanceSourceConsumer;
        this.sourceFolder = sourceFolder;
        this.detectPerformances = detectPerformances;

        // If specific files are given, only these are processed instead of searching the source
        // folder. The source folder is still required since it is the reference for all sub-path
        // calculations.
        this.sourceFiles.clear ();
        if (sourceFiles != null)
            this.sourceFiles.addAll (sourceFiles);
        // Process the files in the same stable alphabetical order as the folder search does
        this.sourceFiles.sort (Comparator.comparing (File::getName, String.CASE_INSENSITIVE_ORDER));

        this.unsupportedElements.clear ();
        this.unsupportedAttributes.clear ();
        this.isCancelled.set (false);

        this.startDetection ();
    }


    /** {@inheritDoc} */
    @Override
    public synchronized Optional<IMultisampleSource> readSource (final File sourceFolder, final File sourceFile, final int indexInFile, final boolean detectPerformances)
    {
        this.configureFileEndings (detectPerformances);

        this.sourceFolder = sourceFolder;
        this.detectPerformances = detectPerformances;
        this.sourceFiles.clear ();
        this.unsupportedElements.clear ();
        this.unsupportedAttributes.clear ();
        this.isCancelled.set (false);

        final SourcePicker picker = new SourcePicker (indexInFile);
        // Reading is stopped as soon as the wanted source was delivered
        this.multisampleSourceConsumer = multisampleSource -> {
            if (picker.accept (multisampleSource))
                this.isCancelled.set (true);
        };
        this.performanceSourceConsumer = performanceSource -> {
            final List<IInstrumentSource> instrumentSources = performanceSource.getInstruments ();
            if (!instrumentSources.isEmpty () && picker.accept (instrumentSources.get (0).getMultisampleSource ()))
                this.isCancelled.set (true);
        };

        try
        {
            this.detectSources (List.of (sourceFile));
        }
        catch (final RuntimeException | OutOfMemoryError err)
        {
            this.notifier.logError (err);
        }
        finally
        {
            this.isCancelled.set (false);
        }

        return Optional.ofNullable (picker.getSource ());
    }


    /**
     * Set the source folder.
     *
     * @param sourceFolder The sourceFolder to set
     */
    public void setSourceFolder (final File sourceFolder)
    {
        this.sourceFolder = sourceFolder;
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


    /** {@inheritDoc} */
    @Override
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

        this.notifier.log (IDS_NOTIFY_ANALYZING, folder.getAbsolutePath ());
        if (this.waitForDelivery ())
            return;

        final Optional<List<File>> listFiles = this.listFiles (folder, this.fileEndings);
        if (listFiles.isEmpty ())
        {
            this.notifier.log ("IDS_NOT_A_DIRECTORY", folder.getAbsolutePath ());
            return;
        }
        for (final File file: listFiles.get ())
            // Ignore MacOS crap
            if (!file.getName ().startsWith ("._"))
            {
                this.notifier.log (IDS_NOTIFY_ANALYZING, file.getAbsolutePath ());

                if (this.waitForDelivery ())
                    break;

                if (this.detectPerformances)
                    this.handlePerformanceFile (file);
                else
                    this.handlePresetFile (file);
            }
    }


    /**
     * Detect the multi-sample(s) in the given sources. A source is normally a file, but a format
     * whose presets are not files at all - the sample files of a folder become one multi-sample -
     * reports its folder as the source of a multi-sample, which is then detected as that folder.
     *
     * @param sources The files and folders to process
     */
    private void detectSources (final List<File> sources)
    {
        final List<File> files = new ArrayList<> (sources.size ());
        for (final File source: sources)
            if (source.isDirectory ())
            {
                if (!this.isCancelled ())
                    this.detect (source);
            }
            else
                files.add (source);
        if (!files.isEmpty () && !this.isCancelled ())
            this.detectFiles (files);
    }


    /**
     * Detect the multi-sample(s) in the given source files. This is called instead of
     * {@link #detect(File)} if the user selected specific files instead of a folder as the source.
     * Overwrite, if a detector does not process its source files individually.
     *
     * @param files The files to process
     */
    protected void detectFiles (final List<File> files)
    {
        for (final File file: files)
        {
            if (this.isCancelled ())
                break;
            this.detectSingleFile (file);
        }
    }


    /**
     * Detect the multi-sample(s) in one single source file.
     *
     * @param file The file to process
     */
    protected void detectSingleFile (final File file)
    {
        this.notifier.log (IDS_NOTIFY_ANALYZING, file.getAbsolutePath ());
        if (this.waitForDelivery ())
            return;

        if (!this.matchesFileEnding (file))
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_WRONG_FILE_ENDING", file.getName (), this.getName (), String.join (", ", this.fileEndings));
            return;
        }

        if (this.detectPerformances)
            this.handlePerformanceFile (file);
        else
            this.handlePresetFile (file);
    }


    /**
     * Test if the name of the given file matches one of the file endings which are currently
     * configured for this detector.
     *
     * @param file The file to check
     * @return True if it matches or if the detector has no file endings configured at all
     */
    protected boolean matchesFileEnding (final File file)
    {
        if (this.fileEndings.length == 0)
            return true;

        final String lower = file.getName ().toLowerCase (Locale.US);
        for (final String ending: this.fileEndings)
            if (lower.endsWith (ending))
                return true;
        return false;
    }


    private void handlePresetFile (final File file)
    {
        try
        {
            for (final IMultisampleSource multisample: this.readPresetFile (file))
            {
                if (this.waitForDelivery ())
                    break;

                updateCreationDateTime (multisample.getMetadata (), file);
                this.multisampleSourceConsumer.accept (multisample);
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
            final List<IPerformanceSource> performances = this.readPerformanceFile (file);
            if (performances.isEmpty () || this.waitForDelivery ())
                return;

            for (final IPerformanceSource performance: performances)
            {
                if (this.waitForDelivery ())
                    break;

                updateCreationDateTime (performance.getMetadata (), file);
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
    protected List<IPerformanceSource> readPerformanceFile (final File sourceFile)
    {
        throw new MethodNotImplemented (this.getClass ().getName () + " does not support Performance files.");
    }


    /** {@inheritDoc} */
    @Override
    public void run ()
    {
        try
        {
            if (this.sourceFiles.isEmpty ())
                this.detect (this.sourceFolder);
            else
                this.detectSources (this.sourceFiles);
        }
        catch (final RuntimeException | OutOfMemoryError err)
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
        // Yield now and then, so that a cancellation is noticed and the user interface keeps up,
        // but not once per source: this is called for every detected preset, and a source which
        // holds thousands of them - a CD-ROM image of an E-mu sampler holds a few thousand - spent
        // nearly all of its time sleeping. Reading such an image took 11 seconds, of which 8 were
        // these sleeps; it now takes well under a second.
        this.deliveryCounter++;
        if (this.deliveryCounter % DELIVERY_YIELD_INTERVAL == 0)
            try
            {
                Thread.sleep (1);
            }
            catch (final InterruptedException _)
            {
                if (this.isCancelled ())
                    return true;
                Thread.currentThread ().interrupt ();
            }
        return this.isCancelled ();
    }


    /**
     * Get all files with specific endings. Recursively, calls detect method on sub-folders.
     * Sub-folders and files are processed in a stable alphabetical order independent of the file
     * system enumeration order, so consecutive runs process the presets identically (this also
     * gives e.g. the import file numbering of the Waldorf QPAT creator a predictable order).
     *
     * @param folder The folder to start searching
     * @param endings The file endings to match, including the dot, e.g. '.wav'
     * @return All found files
     */
    protected Optional<List<File>> listFiles (final File folder, final String... endings)
    {
        final File [] children = folder.listFiles ();
        if (children == null)
            return Optional.empty ();
        Arrays.sort (children, Comparator.comparing (File::getName, String.CASE_INSENSITIVE_ORDER));

        for (final File child: children)
        {
            if (this.isCancelled ())
                break;
            if (child.isDirectory ())
                this.detect (child);
        }

        final List<File> matchingFiles = new ArrayList<> ();
        for (final File child: children)
        {
            if (this.isCancelled ())
                break;
            if (child.isDirectory ())
                continue;
            final String lower = child.getName ().toLowerCase (Locale.US);
            for (final String ending: endings)
                if (lower.endsWith (ending))
                {
                    matchingFiles.add (child);
                    break;
                }
        }
        return Optional.of (matchingFiles);
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


    protected IMultisampleSource createMultisampleSource (final File sourceFile, final String multisampleSourceName)
    {
        return this.createMultisampleSource (sourceFile, multisampleSourceName, Collections.emptyList ());
    }


    protected IMultisampleSource createMultisampleSource (final File sourceFile, final String multisampleSourceName, final List<IGroup> groups)
    {
        return createMultisampleSource (sourceFile, multisampleSourceName, groups, "");
    }


    protected IMultisampleSource createMultisampleSource (final File sourceFile, final String multisampleSourceName, final List<IGroup> groups, final String metadataDescription)
    {
        final String n = this.settingsConfiguration instanceof final MetadataSettingsUI metadataSettings && metadataSettings.isPreferFolderName () ? this.sourceFolder.getName () : multisampleSourceName;
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, n);
        return createMultisampleSource (this.settingsConfiguration instanceof final MetadataSettingsUI metadataSettings ? metadataSettings : null, sourceFile, parts, multisampleSourceName, groups, metadataDescription);
    }


    protected IMultisampleSource createMultisampleSource (final File sourceFile, final String [] parts, final String multisampleSourceName, final List<IGroup> groups)
    {
        return createMultisampleSource (this.settingsConfiguration instanceof final MetadataSettingsUI metadataSettings ? metadataSettings : null, sourceFile, parts, multisampleSourceName, groups, "");
    }


    protected IMultisampleSource createMultisampleSource (final File sourceFile, final String [] parts, final String multisampleSourceName, final List<IGroup> groups, final String metadataDescription)
    {
        return createMultisampleSource (this.settingsConfiguration instanceof final MetadataSettingsUI metadataSettings ? metadataSettings : null, sourceFile, parts, multisampleSourceName, groups, metadataDescription);
    }


    protected static IMultisampleSource createMultisampleSource (final IMetadataConfig configuration, final File sourceFile, final String [] parts, final String multisampleSourceName, final List<IGroup> groups)
    {
        return createMultisampleSource (configuration, sourceFile, parts, multisampleSourceName, groups, "");
    }


    protected static IMultisampleSource createMultisampleSource (final IMetadataConfig configuration, final File sourceFile, final String [] parts, final String multisampleSourceName, final List<IGroup> groups, final String metadataDescription)
    {
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, multisampleSourceName);
        multisampleSource.setGroups (groups);

        final IMetadata metadata = multisampleSource.getMetadata ();
        if (metadataDescription != null && !metadataDescription.isBlank ())
            metadata.setDescription (metadataDescription);

        final List<String> tokens = new ArrayList<> ();
        tokens.addAll (Arrays.asList (parts));
        tokens.add (multisampleSourceName);
        final String [] descriptionTokens = metadata.getDescription ().split ("\\W");
        tokens.addAll (Arrays.asList (descriptionTokens));

        createMetadata (configuration, metadata, getFirstSample (groups), tokens.toArray (new String [tokens.size ()]));
        updateCreationDateTime (metadata, sourceFile);

        return multisampleSource;
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
                // Check if it is a compressed (= encrypted) AIFC file and report accordingly.
                // AIFC files with plain PCM sound data (e.g. little-endian 'sowt') are supported.
                final AiffFile aiffFile = new AiffFile (sampleFile);
                final AiffCommonChunk commonChunk = aiffFile.getCommonChunk ();
                if (commonChunk != null && !commonChunk.isPCM ())
                    throw new IOException (Functions.getMessage ("IDS_ERR_COMPRESSED_AIFF_FILE", sampleFile.getName (), commonChunk.getCompressionName (), commonChunk.getCompressionType ()));

                return new AiffFileSampleData (sampleFile);
            }

            if (fileEnding.endsWith (".ncw"))
                return new NcwFileSampleData (sampleFile);

            // Some hosts store a complete Ogg stream inside a WAV file (e.g. the sample files of
            // the legacy DirectWave packs of FL Studio); de-compress it to PCM first
            final Optional<byte []> oggInWav = AudioFileUtils.decompressOggInWav (sampleFile);
            if (oggInWav.isPresent ())
                return new WavFileSampleData (sampleFile, oggInWav.get ());

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
     * chunk in the sample WAV.
     *
     * @param configuration The metadata settings
     * @param metadata The metadata object to fill
     * @param sampleData The sample file data
     * @param parts The already processed parts of the file name
     */
    protected static void createMetadata (final IMetadataConfig configuration, final IMetadata metadata, final Optional<? extends IFileBasedSampleData> sampleData, final String [] parts)
    {
        createMetadata (configuration, metadata, sampleData, parts, null);
    }


    /**
     * Guess metadata from the sample names and folder. Also check for the Broadcast Audio Extension
     * chunk in the sample WAV.
     *
     * @param configuration The metadata settings
     * @param metadata The metadata object to fill
     * @param sampleData The wave file data
     * @param parts The already processed parts of the file name
     * @param category If the category is not null, it is assigned and not detected
     */
    protected static void createMetadata (final IMetadataConfig configuration, final IMetadata metadata, final Optional<? extends IFileBasedSampleData> sampleData, final String [] parts, final String category)
    {
        if (configuration != null)
            metadata.detectMetadata (configuration, parts, category);
        if (sampleData.isPresent ())
            sampleData.get ().updateMetadata (metadata);
    }


    /**
     * Update the metadata creation date/time from the source file.
     *
     * @param metadata The metadata to update
     * @param sourceFile The source file from which to get the creation date/time
     */
    protected static void updateCreationDateTime (final IMetadata metadata, final File sourceFile)
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
        catch (final IOException _)
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
    protected static Optional<WavFileSampleData> getFirstSample (final List<IGroup> groups)
    {
        if (groups.isEmpty ())
            return Optional.empty ();

        final List<ISampleZone> zones = groups.get (0).getSampleZones ();
        if (zones.isEmpty ())
            return Optional.empty ();

        final Optional<ISampleData> sampleData = zones.get (0).getSampleData ();
        if (sampleData.isPresent () && sampleData.get () instanceof final WavFileSampleData sf)
            return Optional.of (sf);

        return Optional.empty ();
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
        final File localFile = findLocalFile (notifier, folder, previousFolder, fileName, levels, fileType);
        if (localFile.exists ())
            return localFile;

        for (final File variantFile: getCaseVariants (localFile))
        {
            final File found = findLocalFile (notifier, folder, previousFolder, variantFile.getAbsolutePath (), levels, fileType);
            if (found.exists ())
                return found;
        }

        // Triggers the missing file error
        return localFile;
    }


    private static List<File> getCaseVariants (final File file)
    {
        final String fileName = file.getName ();
        final int dotIndex = fileName.lastIndexOf ('.');

        final List<File> variants = new ArrayList<> ();
        // No extension?
        if (dotIndex == -1 || dotIndex == fileName.length () - 1)
            return variants;

        final String nameWithoutExt = fileName.substring (0, dotIndex);
        final String ext = fileName.substring (dotIndex + 1);

        final boolean isAllUpper = ext.equals (ext.toUpperCase ());
        final boolean isAllLower = ext.equals (ext.toLowerCase ());

        final File parentDir = file.getParentFile ();

        // Another workaround for a single quote which got replaced by an underscore
        final String nameWithoutExtCleaned = nameWithoutExt.replace ('\'', '_');
        final boolean addCleaned = !nameWithoutExtCleaned.equals (nameWithoutExt);

        if (isAllUpper || !isAllLower)
        {
            variants.add (new File (parentDir, nameWithoutExt + "." + ext.toLowerCase ()));
            if (addCleaned)
                variants.add (new File (parentDir, nameWithoutExtCleaned + "." + ext.toLowerCase ()));
        }
        if (isAllLower || !isAllUpper)
        {
            variants.add (new File (parentDir, nameWithoutExt + "." + ext.toUpperCase ()));
            if (addCleaned)
                variants.add (new File (parentDir, nameWithoutExtCleaned + "." + ext.toUpperCase ()));
        }

        return variants;
    }


    private static File findLocalFile (final INotifier notifier, final File folder, final File previousFolder, final String fileName, final int levels, final String fileType)
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
        final Optional<File> found = findFileRecursively (startDirectory, sampleFile.getName ());
        // Returning the original non-existing file triggers the missing file error...
        if (found.isEmpty ())
        {
            if (notifier != null)
                notifier.logText ("\n");
            return sampleFile;
        }

        if (notifier != null)
            notifier.log ("IDS_NOTIFY_SEARCH_FILE_IN_FOUND");
        return found.get ();
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


    protected static Optional<File> findFileRecursively (final File folder, final String fileName)
    {
        final File sampleFile = new File (folder, fileName);
        if (sampleFile.exists ())
            return Optional.of (sampleFile);

        final File [] children = folder.listFiles (File::isDirectory);
        if (children == null)
            return Optional.empty ();

        for (final File subFolder: children)
        {
            if (subFolder.isHidden () || subFolder.getName ().startsWith ("."))
                continue;

            final Optional<File> sampleFileOpt = findFileRecursively (subFolder, fileName);
            if (sampleFileOpt.isPresent ())
                return sampleFileOpt;
        }

        return Optional.empty ();
    }


    protected void readMissingValues (final ISampleZone zone)
    {
        try
        {
            // If loop was not explicitly deactivated, there is a loop present, which might need to
            // read the parameters from the WAV file
            final List<ISampleLoop> loops = zone.getLoops ();
            final boolean hasLoop = !loops.isEmpty () && loops.get (0).getStart () >= 0 && loops.get (0).getEnd () >= 0;
            final List<ISampleLoop> previousLoops = new ArrayList<> (loops);
            loops.clear ();

            final Optional<ISampleData> sampleDataOpt = zone.getSampleData ();
            if (sampleDataOpt.isEmpty ())
                return;

            sampleDataOpt.get ().addZoneData (zone, true, true);

            // If start or end was already set overwrite it here
            if (loops.isEmpty () && hasLoop)
            {
                final ISampleLoop previousLoop = previousLoops.get (0);
                final int oldStart = previousLoop.getStart ();
                final int oldEnd = previousLoop.getEnd ();
                if (oldStart >= 0 && oldEnd >= 0)
                {
                    previousLoop.setStart (oldStart);
                    previousLoop.setEnd (oldEnd);
                    loops.add (previousLoop);
                }
            }
        }
        catch (final FileNotFoundException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex);
        }
    }


    /**
     * Picks one specific source out of all the sources which a file delivers. The index is counted
     * exactly like a detection run counts it - per file and in the order of delivery - so that both
     * address the same source.
     */
    private static class SourcePicker
    {
        private final int          wantedIndex;

        private File               currentFile = null;
        private int                indexInFile = 0;
        private IMultisampleSource source      = null;


        /**
         * Constructor.
         *
         * @param wantedIndex The index of the source to pick
         */
        SourcePicker (final int wantedIndex)
        {
            this.wantedIndex = wantedIndex;
        }


        /**
         * Check the next delivered source.
         *
         * @param multisampleSource The delivered source
         * @return True if this was the wanted source and nothing else needs to be read
         */
        boolean accept (final IMultisampleSource multisampleSource)
        {
            final File sourceFile = multisampleSource.getSourceFile ();
            if (this.currentFile == null || !this.currentFile.equals (sourceFile))
            {
                this.currentFile = sourceFile;
                this.indexInFile = 0;
            }
            if (this.indexInFile++ != this.wantedIndex)
                return false;
            this.source = multisampleSource;
            return true;
        }


        /**
         * Get the picked source.
         *
         * @return The source, null if the file delivered no source with the wanted index
         */
        IMultisampleSource getSource ()
        {
            return this.source;
        }
    }
}
