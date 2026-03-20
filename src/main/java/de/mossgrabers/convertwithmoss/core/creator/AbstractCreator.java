// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.creator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;

import de.mossgrabers.convertwithmoss.core.AbstractCoreTask;
import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.ParameterLevel;
import de.mossgrabers.convertwithmoss.core.ZoneChannels;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.ui.ProgressLogger;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Base class for creator classes.
 *
 * @param <T> The type of the settings
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractCreator<T extends ICoreTaskSettings> extends AbstractCoreTask<T> implements ICreator<T>
{
    protected static final AudioFileFormat.Type   FLAC_TARGET_FORMAT                 = new AudioFileFormat.Type ("FLAC", "flac");
    /** The post-fix to use for the samples folder. */
    protected static final String                 FOLDER_POSTFIX                     = " Samples";

    protected static final DestinationAudioFormat DESTINATION_FORMAT                 = new DestinationAudioFormat ();
    protected static final String                 IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA = "IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA";
    protected static final String                 FORWARD_SLASH                      = "/";

    protected final ProgressLogger                progress;
    private final AtomicBoolean                   isCancelled                        = new AtomicBoolean (false);
    private boolean [] []                         layerCheckMatrix                   = new boolean [128] [128];


    /**
     * Constructor.
     *
     * @param name The name of the creator.
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     * @param settingsConfiguration The configuration of the settings
     */
    protected AbstractCreator (final String name, final String prefix, final INotifier notifier, final T settingsConfiguration)
    {
        super (name, prefix, notifier, settingsConfiguration);

        this.progress = new ProgressLogger (this.notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        // Overwrite as well as supportsPresetLibraries() to support
    }


    /** {@inheritDoc} */
    @Override
    public void createPerformance (final File destinationFolder, final IPerformanceSource performanceSource) throws IOException
    {
        // Overwrite as well as supportsPerformances() to support
    }


    /** {@inheritDoc} */
    @Override
    public void createPerformanceLibrary (final File destinationFolder, final List<IPerformanceSource> performanceSources, final String libraryName) throws IOException
    {
        // Overwrite as well as supportsPerformanceLibraries() to support
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformanceLibraries ()
    {
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public void cancel ()
    {
        this.isCancelled.set (true);
    }


    /** {@inheritDoc} */
    @Override
    public boolean isCancelled ()
    {
        return this.isCancelled.get ();
    }


    /** {@inheritDoc} */
    @Override
    public void clearCancelled ()
    {
        this.isCancelled.set (false);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        return true;
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
    public static String createSafeFilename (final String filename)
    {
        return filename.replaceAll ("[\\\\/:*?\"<>|&\\.']", "_").trim ();
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
        if (folder.exists () || folder.mkdirs ())
            return;

        // A parallel thread might already have created the directory and mkdirs did return
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
     * @param zipOutputStream The compressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param content The text content of the file to add
     * @throws IOException Could not add the file
     */
    protected static void zipTextFile (final ZipOutputStream zipOutputStream, final String fileName, final String content) throws IOException
    {
        zipDataFile (zipOutputStream, fileName, content.getBytes (StandardCharsets.UTF_8));
    }


    /**
     * Adds a file (in form of an array of bytes) to a compressed ZIP output stream.
     *
     * @param zipOutputStream The compressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param data The content of the file to add
     * @throws IOException Could not add the file
     */
    protected static void zipDataFile (final ZipOutputStream zipOutputStream, final String fileName, final byte [] data) throws IOException
    {
        zipOutputStream.putNextEntry (new ZipEntry (fileName));
        zipOutputStream.write (data);
        zipOutputStream.flush ();
        zipOutputStream.closeEntry ();
    }


    /**
     * Adds an UTF-8 text file to the uncompressed ZIP output stream.
     *
     * @param zipOutputStream The uncompressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param content The text content of the file to add
     * @throws IOException Could not add the file
     */
    protected static void storeTextFile (final ZipOutputStream zipOutputStream, final String fileName, final String content) throws IOException
    {
        storeTextFile (zipOutputStream, fileName, content, null);
    }


    /**
     * Adds an UTF-8 text file to the uncompressed ZIP output stream.
     *
     * @param zipOutputStream The uncompressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param content The text content of the file to add
     * @param dateTime The date and time to set as the creation date of the file entry
     * @throws IOException Could not add the file
     */
    protected static void storeTextFile (final ZipOutputStream zipOutputStream, final String fileName, final String content, final Date dateTime) throws IOException
    {
        storeDataFile (zipOutputStream, fileName, content.getBytes (StandardCharsets.UTF_8), dateTime);
    }


    /**
     * Adds a file (in form of an array of bytes) to an uncompressed ZIP output stream.
     *
     * @param zipOutputStream The uncompressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param data The content of the file to add
     * @throws IOException Could not add the file
     */
    protected static void storeDataFile (final ZipOutputStream zipOutputStream, final String fileName, final byte [] data) throws IOException
    {
        storeDataFile (zipOutputStream, fileName, data, null);
    }


    /**
     * Adds a file (in form of an array of bytes) to an uncompressed ZIP output stream.
     *
     * @param zipOutputStream The uncompressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param data The content of the file to add
     * @param dateTime The date and time to set as the creation date of the file entry
     * @throws IOException Could not add the file
     */
    protected static void storeDataFile (final ZipOutputStream zipOutputStream, final String fileName, final byte [] data, final Date dateTime) throws IOException
    {
        // The checksum needs to be calculated in advance before the data is written to the output
        // stream!
        final CRC32 crc = new CRC32 ();
        crc.update (data);
        putUncompressedEntry (zipOutputStream, fileName, data, crc, dateTime);
    }


    /**
     * Since panning is not working on the sample level, combine split stereo to stereo files If the
     * combination fails, the file is created anyway but might contain wrong panning.
     *
     * @param multisampleSource The multi-sample source
     * @return The combined stereo group or the original groups if they could not be combined
     * @throws IOException Could not combine the groups
     */
    protected List<IGroup> combineSplitStereo (final IMultisampleSource multisampleSource) throws IOException
    {
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);

        if (ZoneChannels.detectChannelConfiguration (groups) != ZoneChannels.SPLIT_STEREO)
            return groups;

        final Optional<IGroup> stereoGroup = ZoneChannels.combineSplitStereo (groups);
        if (stereoGroup.isPresent ())
        {
            this.notifier.log ("IDS_NOTIFY_COMBINED_TO_STEREO");
            return Collections.singletonList (stereoGroup.get ());
        }

        this.notifier.logError ("IDS_NOTIFY_NOT_COMBINED_TO_STEREO");
        return groups;
    }


    /**
     * Create the name of the sample file.
     *
     * @param zone The sample zone
     * @param zoneIndex The index of the zone in the group
     * @param fileEnding The file ending to use for the file
     * @return The sample file name
     */
    protected String createSampleFilename (final ISampleZone zone, final int zoneIndex, final String fileEnding)
    {
        return createSafeFilename (zone.getName ()) + fileEnding;
    }


    /**
     * Writes all samples in the given audio file format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @return The written files
     * @throws IOException Could not store the samples
     * @throws UnsupportedAudioFileException The audio format is not supported
     */
    protected List<File> writeFlacSamples (final File sampleFolder, final IMultisampleSource multisampleSource) throws IOException, UnsupportedAudioFileException
    {
        final List<File> writtenFiles = new ArrayList<> ();
        final String extension = "." + FLAC_TARGET_FORMAT.getExtension ();

        for (final IGroup group: multisampleSource.getGroups ())
        {
            final List<ISampleZone> sampleZones = group.getSampleZones ();
            for (int zoneIndex = 0; zoneIndex < sampleZones.size (); zoneIndex++)
            {
                if (this.isCancelled ())
                    return writtenFiles;

                final ISampleZone zone = sampleZones.get (zoneIndex);

                final File file = new File (sampleFolder, this.createSampleFilename (zone, zoneIndex, extension));
                this.progress.notifyProgress ();
                final ISampleData sampleData = zone.getSampleData ();
                if (sampleData == null)
                    this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), file.getName ());
                else
                {
                    AudioFileUtils.compressToFLAC (sampleData, FLAC_TARGET_FORMAT, file);
                    writtenFiles.add (file);
                }
            }
        }

        return writtenFiles;
    }


    /**
     * Re-calculates the sample start, stop and loop start, stop positions for the given new sample
     * rate of all samples/zones in the given multi-sample.
     *
     * @param multisampleSource The multi-sample source
     * @param newSampleRate The new sample rate
     * @throws IOException Could not retrieve the current sample rate
     */
    protected static void recalculateSamplePositions (final IMultisampleSource multisampleSource, final int newSampleRate) throws IOException
    {
        recalculateSamplePositions (multisampleSource, newSampleRate, false);
    }


    /**
     * Re-calculates the sample start, stop and loop start, stop positions for the given new sample
     * rate of all samples/zones in the given multi-sample.
     *
     * @param multisampleSource The multi-sample source
     * @param newSampleRate The new sample rate
     * @param onlyIfLarger If true, the values are only re-calculated if the sample frequency is
     *            larger than the new sample rate
     * @throws IOException Could not retrieve the current sample rate
     */
    protected static void recalculateSamplePositions (final IMultisampleSource multisampleSource, final int newSampleRate, final boolean onlyIfLarger) throws IOException
    {
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final ISampleData sampleData = zone.getSampleData ();
                if (sampleData == null)
                    continue;
                final int sampleRate = sampleData.getAudioMetadata ().getSampleRate ();
                if (onlyIfLarger && sampleRate <= newSampleRate)
                    continue;
                final double sampleRateRatio = newSampleRate / (double) sampleRate;
                zone.setStart ((int) Math.round (zone.getStart () * sampleRateRatio));
                zone.setStop ((int) Math.round (zone.getStop () * sampleRateRatio));

                for (final ISampleLoop loop: zone.getLoops ())
                {
                    loop.setStart ((int) Math.round (loop.getStart () * sampleRateRatio));
                    loop.setEnd ((int) Math.round (loop.getEnd () * sampleRateRatio));
                }
            }
    }


    protected static double limitToDefault (final double value, final double defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }


    /**
     * If the value is negative the given default value is returned otherwise the unchanged value.
     *
     * @param value The value to check
     * @param defaultValue The default value if negative
     * @return The value or default value
     */
    public static int limitToDefault (final int value, final int defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }


    protected static String formatDouble (final double value)
    {
        if (value == 0)
            return "0";
        return String.format (Locale.US, "%.8f", Double.valueOf (value));
    }


    /**
     * Creates a unique file name in the given folder. If the file does already exists a unique
     * prefix is appended.
     *
     * @param destinationFolder The folder in which to create the file
     * @param sampleName The name for the file
     * @param extension The extension for the file
     * @return A unique name
     */
    protected File createUniqueFilename (final File destinationFolder, final String sampleName, final String extension)
    {
        final String ext = extension.isBlank () ? "" : "." + extension;
        File multiFile = new File (destinationFolder, sampleName + ext);
        int counter = 1;
        while (multiFile.exists ())
        {
            counter++;
            multiFile = new File (destinationFolder, sampleName + " (" + counter + ")" + ext);
        }
        return multiFile;
    }


    /**
     * Creates a unique file name which does not already exists in the list of the given ones.
     *
     * @param destinationFolder The folder in which to create the file
     * @param sampleName The name for the file
     * @param extension The extension for the file
     * @param existingAbsoluteFilenames The list with existing absolute file names
     * @return A unique name
     */
    protected File createUniqueFilename (final File destinationFolder, final String sampleName, final String extension, final List<String> existingAbsoluteFilenames)
    {
        File multiFile = new File (destinationFolder, sampleName + extension);
        int counter = 1;
        while (existingAbsoluteFilenames.contains (multiFile.getAbsolutePath ()))
        {
            counter++;
            multiFile = new File (destinationFolder, sampleName + " (" + counter + ")" + extension);
        }
        return multiFile;
    }


    /**
     * Creates a DOS file name with a maximum number of 8 characters. Adds numbers to make it unique
     * among the given other file names.
     *
     * @param destinationFolder The target folder
     * @param filename The filename to shorten
     * @param extension The file extension
     * @param createdNames Prevent conflicts with these file names
     * @param useForFolder If true only folders will be checked for uniqueness, if false only
     *            non-folder files
     * @return The unique DOS file name without the extension
     */
    public static String createUniqueDOSFileName (final File destinationFolder, final String filename, final String extension, final Collection<String> createdNames, final boolean useForFolder)
    {
        String dosFilename = filename.toUpperCase ().replace (' ', '_');
        if (dosFilename.length () > 8)
            dosFilename = dosFilename.substring (0, 8);

        int counter = 1;
        while (true)
        {
            final File file = new File (destinationFolder, dosFilename + extension);
            final boolean exists = file.exists ();
            if (!createdNames.contains (dosFilename) && !exists || useForFolder && file.isFile () || !useForFolder && file.isDirectory ())
                break;

            counter++;
            final String counterStr = Integer.toString (counter);
            dosFilename = dosFilename.substring (0, 8 - counterStr.length ()) + counterStr;
        }

        createdNames.add (dosFilename);

        return dosFilename;
    }


    /**
     * Get the level to which the amplitude envelope should be applied.
     *
     * @param multisampleSource The multi-sample instrument to check
     * @return The level to which the amplitude envelope should be applied
     */
    protected static ParameterLevel getAmpEnvelopeParamLevel (final IMultisampleSource multisampleSource)
    {
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        if (groups.isEmpty ())
            return ParameterLevel.INSTRUMENT;

        // First check if all modulators in a group are identical
        final List<IEnvelopeModulator> groupModulators = new ArrayList<> ();
        for (final IGroup group: groups)
        {
            final List<ISampleZone> sampleZones = group.getSampleZones ();
            final IEnvelopeModulator amplitudeEnvelopeModulator = sampleZones.get (0).getAmplitudeEnvelopeModulator ();
            for (int i = 1; i < sampleZones.size (); i++)
            {
                final ISampleZone zone = sampleZones.get (i);
                final IEnvelopeModulator amplitudeEnvelopeModulator2 = zone.getAmplitudeEnvelopeModulator ();
                if (!amplitudeEnvelopeModulator.equals (amplitudeEnvelopeModulator2))
                    return ParameterLevel.ZONE;
            }
            groupModulators.add (amplitudeEnvelopeModulator);
        }

        // Then check if the modulators are identical for all groups
        if (groupModulators.size () == 1)
            return ParameterLevel.INSTRUMENT;
        final IEnvelopeModulator groupModulator = groupModulators.get (0);
        for (int i = 1; i < groupModulators.size (); i++)
        {
            final IEnvelopeModulator groupModulator2 = groupModulators.get (i);
            if (!groupModulator.equals (groupModulator2))
                return ParameterLevel.GROUP;
        }

        return ParameterLevel.ZONE;
    }


    /**
     * Adds a new entry to an uncompressed ZIP output stream.
     *
     * @param zipOutputStream The uncompressed ZIP output stream
     * @param fileName The name to use for the file when added
     * @param data The content of the file to add
     * @param checksum The checksum
     * @param dateTime The date and time to set as the creation date of the file entry
     * @throws IOException Could not add the file
     */
    protected static void putUncompressedEntry (final ZipOutputStream zipOutputStream, final String fileName, final byte [] data, final CRC32 checksum, final Date dateTime) throws IOException
    {
        final ZipEntry entry = new ZipEntry (fileName);
        entry.setSize (data.length);
        entry.setCompressedSize (data.length);
        entry.setCrc (checksum.getValue ());
        entry.setMethod (ZipOutputStream.STORED);
        if (dateTime != null)
            entry.setLastModifiedTime (FileTime.fromMillis (dateTime.getTime ()));
        zipOutputStream.putNextEntry (entry);
        zipOutputStream.write (data);
        zipOutputStream.closeEntry ();
    }


    /**
     * Adds a sample file to the uncompressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zipOutputStream The ZIP output stream
     * @param multiSampleSource The multi-sample source
     * @param zoneIndex The index of the zone
     * @param zone The zone to add
     * @param path Optional path (may be null), must not end with a slash
     * @throws IOException Could not read the file
     */
    protected void storeSamplefile (final Set<String> alreadyStored, final ZipOutputStream zipOutputStream, final IMultisampleSource multiSampleSource, final int zoneIndex, final ISampleZone zone, final String path) throws IOException
    {
        final String name = checkSampleName (alreadyStored, zoneIndex, zone, path);
        if (name == null)
            return;

        final ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), name);
            this.notifier.logText ("\n");
            return;
        }

        final CRC32 crc = new CRC32 ();
        try (final ByteArrayOutputStream bout = new ByteArrayOutputStream (); final OutputStream checkedOut = new CheckedOutputStream (bout, crc))
        {
            if (this.requiresRewrite (DESTINATION_FORMAT))
                this.rewriteFile (multiSampleSource, zone, checkedOut, DESTINATION_FORMAT, false);
            else
                sampleData.writeSample (checkedOut);
            putUncompressedEntry (zipOutputStream, name, bout.toByteArray (), crc, multiSampleSource.getMetadata ().getCreationDateTime ());
        }
    }


    /**
     * Add all samples from all groups in the given uncompressed ZIP output stream.
     *
     * @param zipOutputStream The ZIP output stream to which to add the samples
     * @param relativeFolderName The relative folder under which to store the file in the ZIP
     * @param multisampleSource The multi-sample
     * @throws IOException Could not store the samples
     */
    protected void storeSampleFiles (final ZipOutputStream zipOutputStream, final String relativeFolderName, final IMultisampleSource multisampleSource) throws IOException
    {
        final Set<String> alreadyStored = new HashSet<> ();
        int zoneIndex = 0;
        for (final IGroup group: multisampleSource.getGroups ())
        {
            for (final ISampleZone zone: group.getSampleZones ())
            {
                this.progress.notifyProgress ();
                this.storeSamplefile (alreadyStored, zipOutputStream, multisampleSource, zoneIndex, zone, relativeFolderName);
                zoneIndex++;
            }
        }
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        return this.writeSamples (sampleFolder, multisampleSource, DESTINATION_FORMAT);
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @param destinationFormat The destination audio format
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final DestinationAudioFormat destinationFormat) throws IOException
    {
        return this.writeSamples (sampleFolder, multisampleSource, ".wav", destinationFormat, false);
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @param destinationFormat The destination audio format
     * @param trim Trim the sample from zone start to end if enabled
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        return this.writeSamples (sampleFolder, multisampleSource, ".wav", destinationFormat, trim);
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @param fileEnding The suffix to use for the file
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final String fileEnding) throws IOException
    {
        return this.writeSamples (sampleFolder, multisampleSource, fileEnding, DESTINATION_FORMAT, false);
    }


    /**
     * Writes all samples in WAV format from all groups into the given folder.
     *
     * @param sampleFolder The destination folder
     * @param multisampleSource The multi-sample
     * @param fileEnding The suffix to use for the file
     * @param destinationFormat The destination audio format
     * @param trim Trim the sample from zone start to end if enabled
     * @return The written files
     * @throws IOException Could not store the samples
     */
    protected List<File> writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final String fileEnding, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        final List<File> writtenFiles = new ArrayList<> ();

        for (final IGroup group: multisampleSource.getGroups ())
        {
            final List<ISampleZone> sampleZones = group.getSampleZones ();
            for (int zoneIndex = 0; zoneIndex < sampleZones.size (); zoneIndex++)
            {
                if (this.isCancelled ())
                    return writtenFiles;

                final ISampleZone zone = sampleZones.get (zoneIndex);

                final File file = new File (sampleFolder, this.createSampleFilename (zone, zoneIndex, fileEnding));
                try (final FileOutputStream fos = new FileOutputStream (file))
                {
                    this.progress.notifyProgress ();

                    if (this.requiresRewrite (destinationFormat) || trim)
                        this.rewriteFile (multisampleSource, zone, fos, destinationFormat, trim);
                    else
                    {
                        final ISampleData sampleData = zone.getSampleData ();
                        if (sampleData == null)
                        {
                            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), file.getName ());
                            this.notifier.logText ("\n");
                        }
                        else
                            sampleData.writeSample (fos);
                    }

                    writtenFiles.add (file);
                }
                catch (final NoSuchFileException | FileNotFoundException ex)
                {
                    this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
                }
            }
        }

        return writtenFiles;
    }


    /**
     * Check if the sample file needs to be rewritten.
     * 
     * @param destinationFormat The expected destination format for the sample
     * @return True if the file needs to be written again
     */
    protected boolean requiresRewrite (final DestinationAudioFormat destinationFormat)
    {
        // Overwrite to support
        return false;
    }


    /**
     * Writes the sample of the given zone and updates/adds their instrument and sample chunks.
     *
     * @param multisampleSource The multi-sample source
     * @param zone The zone from which to take the data to store into the chunks
     * @param outputStream Where to write the result
     * @param destinationFormat The destination audio format
     * @param trim Trim the sample from zone start to end if enabled
     * @throws IOException Could not store the samples
     */
    protected void rewriteFile (final IMultisampleSource multisampleSource, final ISampleZone zone, final OutputStream outputStream, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        // Overwrite to implement rewriting
    }


    /**
     * Add all samples from all groups in the given compressed ZIP output stream.
     *
     * @param zipOutputStream The ZIP output stream to which to add the samples
     * @param relativeFolderName The relative folder under which to store the file in the ZIP
     * @param multisampleSource The multi-sample
     * @throws IOException Could not store the samples
     */
    protected void zipSampleFiles (final ZipOutputStream zipOutputStream, final String relativeFolderName, final IMultisampleSource multisampleSource) throws IOException
    {
        final Set<String> alreadyStored = new HashSet<> ();
        int zoneIndex = 0;
        for (final IGroup group: multisampleSource.getGroups ())
        {
            for (final ISampleZone zone: group.getSampleZones ())
            {
                this.progress.notifyProgress ();
                this.zipSamplefile (alreadyStored, zipOutputStream, zoneIndex, zone, multisampleSource.getMetadata ().getCreationDateTime (), relativeFolderName);
                zoneIndex++;
            }
        }
    }


    /**
     * Adds a sample file to the compressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zipOutputStream The ZIP output stream
     * @param zoneIndex The index of the zone
     * @param zone The zone to add
     * @param dateTime The date and time to set as the creation date of the file entry
     * @throws IOException Could not read the file
     */
    protected void zipSamplefile (final Set<String> alreadyStored, final ZipOutputStream zipOutputStream, final int zoneIndex, final ISampleZone zone, final Date dateTime) throws IOException
    {
        this.zipSamplefile (alreadyStored, zipOutputStream, zoneIndex, zone, dateTime, null);
    }


    /**
     * Adds a sample file to the compressed ZIP output stream.
     *
     * @param alreadyStored Set with the already files to prevent trying to add duplicated files
     * @param zipOutputStream The ZIP output stream
     * @param zoneIndex The index of the zone
     * @param zone The zone to add
     * @param dateTime The date and time to set as the creation date of the file entry
     * @param path Optional path (may be null), must not end with a slash
     * @throws IOException Could not read the file
     */
    protected void zipSamplefile (final Set<String> alreadyStored, final ZipOutputStream zipOutputStream, final int zoneIndex, final ISampleZone zone, final Date dateTime, final String path) throws IOException
    {
        final String name = checkSampleName (alreadyStored, zoneIndex, zone, path);
        if (name == null)
            return;

        final ZipEntry entry = new ZipEntry (name);
        if (dateTime != null)
        {
            final long millis = dateTime.getTime ();
            entry.setCreationTime (FileTime.fromMillis (millis));
            entry.setTime (millis);
        }

        zipOutputStream.putNextEntry (entry);
        final ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), name);
            this.notifier.logText ("\n");
        }
        else
            sampleData.writeSample (zipOutputStream);
        zipOutputStream.closeEntry ();
    }


    /**
     * Trims the data of the wave file to the part from the zone start and zone end. The zone is
     * updated accordingly.
     *
     * @param wavFile The WAV file to trim
     * @param zone The zone
     */
    protected static void trimStartToEnd (final WaveFile wavFile, final ISampleZone zone)
    {
        final FormatChunk formatChunk = wavFile.getFormatChunk ();

        // Create the truncated data array
        final DataChunk dataChunk = wavFile.getDataChunk ();
        final byte [] data = dataChunk.getData ();
        final int start = zone.getStart ();
        final int stop = zone.getStop ();
        final int lengthInSamples = stop - start;
        final int numBytesPerSample = formatChunk.calculateBytesPerSample ();
        final int startByte = start * numBytesPerSample;
        final int newLength = lengthInSamples * numBytesPerSample;
        final byte [] truncatedData = new byte [newLength];
        System.arraycopy (data, startByte, truncatedData, 0, Math.min (newLength, data.length - startByte));

        // Replace the previous data chunk
        final DataChunk truncatedDataChunk = new DataChunk (formatChunk, lengthInSamples);
        truncatedDataChunk.setData (truncatedData);
        wavFile.setDataChunk (truncatedDataChunk);

        // Update the zone values - necessary for follow-up instrument/sample chunks!
        zone.setStart (0);
        zone.setStop (lengthInSamples);
        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop loop = loops.get (0);
            loop.setStart (Math.max (loop.getStart () - start, 0));
            loop.setEnd (Math.min (loop.getEnd () - start, lengthInSamples));
        }
    }


    /**
     * Creates an optional string from the XML document.
     *
     * @param document The XML document
     * @return The optional string; empty if an error occurs
     */
    protected Optional<String> createXMLString (final Document document)
    {
        try
        {
            return Optional.of (XMLUtils.toString (document));
        }
        catch (final TransformerException ex)
        {
            this.notifier.logError (ex);
            return Optional.empty ();
        }
    }


    /**
     * Creates full path from the sample name and relative path and adding the prefix path.
     *
     * @param alreadyStored All paths already added to the ZIP file
     * @param zoneIndex The index of the zone
     * @param zone The sample zone to check
     * @param path The prefix path
     * @return The full path or null if already added to the ZIP
     */
    protected String checkSampleName (final Set<String> alreadyStored, final int zoneIndex, final ISampleZone zone, final String path)
    {
        String filename = createFileName (zoneIndex, zone);
        if (path != null)
            filename = path + FORWARD_SLASH + filename;
        if (alreadyStored.contains (filename))
            return null;
        alreadyStored.add (filename);
        return filename;
    }


    /**
     * Create the name of the sample (with the ending).
     * 
     * @param zoneIndex The index of the zone
     * @param zone The zone for which to create the full name
     * @return The full sample name
     */
    protected String createFileName (final int zoneIndex, final ISampleZone zone)
    {
        return zone.getName ();
    }


    /**
     * Check if there are layered samples and display a warning.
     * 
     * @param groups The groups of the multi-sample
     */
    protected void checkDuplicateRanges (final List<IGroup> groups)
    {
        // Clear the matrix
        for (int i = 0; i < 128; i++)
            Arrays.fill (this.layerCheckMatrix[i], false);
        // Mark the covered ranges
        for (final IGroup group: groups)
        {
            for (final ISampleZone zone: group.getSampleZones ())
            {
                for (int k = zone.getKeyLow (); k <= zone.getKeyHigh (); k++)
                    for (int v = zone.getVelocityLow (); v <= zone.getVelocityHigh (); v++)
                    {
                        if (this.layerCheckMatrix[k][v])
                        {
                            this.notifier.logError ("IDS_ERR_SOURCE_CONTAINS_LAYERS");
                            return;
                        }
                        this.layerCheckMatrix[k][v] = true;
                    }
            }
        }
    }
}
