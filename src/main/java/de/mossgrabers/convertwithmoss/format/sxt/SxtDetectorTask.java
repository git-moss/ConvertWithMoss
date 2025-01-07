// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sxt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.iff.IffChunk;
import de.mossgrabers.convertwithmoss.file.iff.IffFile;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;
import javafx.scene.control.ComboBox;


/**
 * Detects recursively NN-XT files in folders. Files must end with <i>.sxt</i>. SCT files are based
 * on the 'EA IFF 85' Standard for Interchange Format.
 *
 * @author Jürgen Moßgraber
 */
public class SxtDetectorTask extends AbstractDetectorTask
{
    private static final String       ENDING_NNXT = ".sxt";

    protected final ComboBox<Integer> levelsOfDirectorySearch;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadataConfig Additional metadata configuration parameters
     * @param levelsOfDirectorySearch Combo box to read the directory search level
     */
    protected SxtDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadataConfig, final ComboBox<Integer> levelsOfDirectorySearch)
    {
        super (notifier, consumer, sourceFolder, metadataConfig, ENDING_NNXT);

        this.levelsOfDirectorySearch = levelsOfDirectorySearch;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = new BufferedInputStream (new FileInputStream (file)))
        {
            return this.parseFile (in, file);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        catch (final FormatException ex)
        {
            this.notifier.logError ("IDS_WS_EXPECTED_TAG", ex.getMessage ());
        }
        return Collections.emptyList ();
    }


    /**
     * Load and parse the SXT file.
     *
     * @param in The input stream to read from
     * @param file The source file
     * @return The parsed multi-sample source
     * @throws FormatException Error in the format of the file
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> parseFile (final InputStream in, final File file) throws FormatException, IOException
    {
        final IffChunk iffChunk = IffFile.readChunk (in);
        StreamUtils.checkTag (SxtChunkConstants.PATCH, iffChunk.getId ());

        final File parentFile = file.getParentFile ();
        final String name = FileUtils.getNameWithoutType (file);
        final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

        try (final InputStream chunkStream = iffChunk.streamData ())
        {
            File previousFolder = null;
            final Map<Integer, File> samples = new HashMap<> ();
            while (chunkStream.available () > 0)
            {
                final IffChunk childChunk = IffFile.readChunk (chunkStream);
                try (final InputStream childChunkStream = childChunk.streamData ())
                {
                    switch (childChunk.getId ())
                    {
                        case SxtChunkConstants.REFERENCES:
                            int sampleIndex = 0;
                            while (childChunkStream.available () > 0)
                            {
                                final IffChunk referenceChunk = IffFile.readChunk (childChunkStream);
                                StreamUtils.checkTag (SxtChunkConstants.REFERENCE, referenceChunk.getId ());
                                try (final InputStream referenceChunkStream = referenceChunk.streamData ())
                                {
                                    final File sampleFile = this.parseReference (referenceChunkStream, parentFile, previousFolder);
                                    samples.put (Integer.valueOf (sampleIndex), sampleFile);
                                    previousFolder = sampleFile.getParentFile ();
                                    sampleIndex++;
                                }
                            }
                            break;

                        case SxtChunkConstants.DESC:
                            this.parseDescription (multisampleSource, childChunkStream);
                            break;

                        case SxtChunkConstants.AUTHOR:
                            parseAuthor (multisampleSource.getMetadata (), childChunkStream);
                            break;

                        case SxtChunkConstants.PARAMETERS:
                            // Contains only global offsets which can be ignored
                            break;

                        case SxtChunkConstants.BODY:
                            this.parseGroups (multisampleSource, childChunkStream, samples);
                            break;

                        default:
                            this.notifier.logError ("IDS_SXT_UNKNOWN_CHUNK_TYPE", childChunk.getId ());
                            break;
                    }
                }
            }
        }

        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (multisampleSource.getGroups ()), parts);
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parse a description chunk.
     *
     * @param in The input stream
     * @param multisampleSource Where to store the patch name
     * @throws IOException Could not read the data or data is invalid
     */
    private void parseDescription (final DefaultMultisampleSource multisampleSource, final InputStream in) throws IOException
    {
        final int version = readVersion (in);
        final boolean isReason3 = version == SxtChunkConstants.VERSION_1_3_0;
        // Reserved
        in.read ();
        final String patchName = readString (in, isReason3);
        final String deviceName = readString (in, isReason3);
        if (!patchName.isBlank ())
            multisampleSource.setName (patchName);
        if (!"NNXT Digital Sampler".equals (deviceName))
            this.notifier.logError ("IDS_SXT_DEVICE_NAME_NOT_NNXT", deviceName);
    }


    /**
     * Parse an author chunk.
     *
     * @param in The input stream
     * @param metadata Where to store the author information
     * @throws IOException Could not read the data or data is invalid
     */
    private static void parseAuthor (final IMetadata metadata, final InputStream in) throws IOException
    {
        final int version = readVersion (in);
        final boolean isReason3 = version == SxtChunkConstants.VERSION_1_3_0;
        final String author = readString (in, isReason3);
        final String authorURL = readString (in, isReason3);
        if (!author.isBlank () && !author.contains ("\n") && !author.contains ("{"))
            metadata.setCreator (author);
        if (!authorURL.isBlank () && !authorURL.contains ("Keymap"))
            metadata.setDescription ("Website: " + authorURL);
    }


    /**
     * Parse a body chunk.
     *
     * @param in The input stream
     * @param multisampleSource The multi-sample source
     * @param samples The samples with their indices
     * @throws IOException Could not read the data or data is invalid
     */
    private void parseGroups (final DefaultMultisampleSource multisampleSource, final InputStream in, final Map<Integer, File> samples) throws IOException
    {
        final int bodyVersion = readVersion (in);
        if (bodyVersion != SxtChunkConstants.VERSION_1_0_0)
            throw new IOException (Functions.getMessage ("IDS_SXT_UNKNOWN_BODY_VERSION", Integer.toString (bodyVersion)));

        // Groups
        final int groupsVersion = readVersion (in);
        final List<IGroup> groups = new ArrayList<> ();
        final long groupCount = StreamUtils.readUnsigned32 (in, true);
        for (int i = 0; i < groupCount; i++)
        {
            final SxtGroup sxtGroup = new SxtGroup ();
            sxtGroup.read (in, groupsVersion);
            final IGroup group = new DefaultGroup ("Group #" + (i + 1));
            groups.add (group);
        }

        // Zones
        final int zoneVersion = readVersion (in);
        if (zoneVersion != SxtChunkConstants.VERSION_2_2_0)
            throw new IOException (Functions.getMessage ("IDS_SXT_UNKNOWN_ZONE_VERSION", Integer.toString (bodyVersion)));
        final long zoneCount = StreamUtils.readUnsigned32 (in, true);
        final List<SxtZone> sxtZones = new ArrayList<> ((int) zoneCount);
        for (int i = 0; i < zoneCount; i++)
        {
            final SxtZone sxtZone = new SxtZone ();
            sxtZone.read (in);
            sxtZones.add (sxtZone);
        }

        // Sample references for Zones
        for (int i = 0; i < zoneCount; i++)
        {
            final SxtZone sxtZone = sxtZones.get (i);
            if (in.read () > 0)
            {
                final int version = readVersion (in);
                if (version != SxtChunkConstants.VERSION_4_1_0)
                    throw new IOException (Functions.getMessage ("IDS_SXT_UNKNOWN_SAMPLE_REF_VERSION", Integer.toString (version)));
                // Reserved
                in.skipNBytes (1);
                final long sampleIndex = StreamUtils.readUnsigned32 (in, true);
                final File sampleFile = samples.get (Integer.valueOf ((int) sampleIndex));
                if (sampleFile != null)
                {
                    final IGroup group = groups.get ((int) sxtZone.groupIndex);
                    if (group != null)
                    {
                        final ISampleData sampleData = this.createSampleData (sampleFile);
                        final String zoneName = FileUtils.getNameWithoutType (sampleFile);
                        final ISampleZone zone = new DefaultSampleZone (zoneName, sampleData);
                        sxtZone.fillInto (zone);
                        group.addSampleZone (zone);
                    }
                }
            }
        }

        multisampleSource.setGroups (groups);
    }


    /**
     * Parse a reference chunk.
     *
     * @param in The input stream
     * @param parentFile The parent path of the SXT file
     * @param previousFolder The folder in which the previous sample was found, might be null
     * @return The path to the sample file
     * @throws IOException Could not read the data or data is invalid
     */
    private File parseReference (final InputStream in, final File parentFile, final File previousFolder) throws IOException
    {
        final int version = readVersion (in);
        final boolean isReason3 = version == SxtChunkConstants.VERSION_1_3_0;

        SxtPathInfo paths = readPaths (in, isReason3);

        // userInterfaceSampleName not used
        readString (in, isReason3);
        final String refillName = readString (in, isReason3);
        final String sampleURL = readString (in, isReason3);
        if (!refillName.isBlank () || !sampleURL.isBlank ())
            throw new IOException (Functions.getMessage ("IDS_SXT_REFILLS_NOT_SUPPORTED", refillName));

        // Reserved
        if (in.read () != 13)
            throw new IOException (Functions.getMessage ("IDS_SXT_UNSOUND_REFERENCE_CHUNK"));

        // 'Package Name' not used
        readString (in, isReason3);

        // Are there UTF-8 paths?
        String physicalSampleName = null;
        if (version >= SxtChunkConstants.VERSION_1_3_0)
        {
            physicalSampleName = readString (in, isReason3);
            paths = readPaths (in, isReason3);
        }

        // Choose an existing path
        String sampleFileName = paths.relativePath;
        if (sampleFileName == null || sampleFileName.isBlank ())
        {
            sampleFileName = physicalSampleName;
            if (sampleFileName == null || sampleFileName.isBlank ())
            {
                sampleFileName = paths.absolutePath;
                if (sampleFileName == null || sampleFileName.isBlank ())
                    sampleFileName = paths.databasePath;
            }
        }

        // Find the sample file
        final int height = this.levelsOfDirectorySearch.getSelectionModel ().getSelectedItem ().intValue ();
        return this.findSampleFile (parentFile, previousFolder, sampleFileName, height);
    }


    /**
     * Handles reading the relative, absolute and database paths.
     *
     * @param in The input stream to read from
     * @param isReason3 True if the format is Reason 3
     * @return The parsed path info
     * @throws IOException Could not read the paths
     */
    private static SxtPathInfo readPaths (final InputStream in, final boolean isReason3) throws IOException
    {
        final SxtPathInfo pathInfo = new SxtPathInfo ();

        // Read the relative path
        final int relativePathVersion = readVersion (in);
        if (in.read () > 0)
        {
            // The path is valid
            final int stepUpCount = (int) StreamUtils.readUnsigned32 (in, true);
            final StringBuilder sb = new StringBuilder ();
            if (stepUpCount == 0)
                sb.append (".");
            for (int i = 0; i < stepUpCount; i++)
            {
                if (i > 0)
                    sb.append ("/");
                sb.append ("..");
            }
            pathInfo.relativePath = readSubPaths (sb, in, isReason3);

            if (relativePathVersion >= SxtChunkConstants.VERSION_1_5_0)
                in.skipNBytes (1);
        }

        // Read the database path
        final int databasePathVersion = readVersion (in);
        if (in.read () > 0)
        {
            final String refillName = readString (in, isReason3);
            if (!refillName.isBlank ())
                throw new IOException (Functions.getMessage ("IDS_SXT_REFILLS_NOT_SUPPORTED", refillName));
            pathInfo.databasePath = readSubPaths (new StringBuilder (), in, isReason3);

            if (databasePathVersion >= SxtChunkConstants.VERSION_1_5_0)
                in.skipNBytes (1);
        }

        // Read the absolute path
        final int absolutePathVersion = readVersion (in);
        if (in.read () > 0)
        {
            final String volume = readString (in, isReason3);
            // The volume type
            in.skipNBytes (1);
            final StringBuilder sb = new StringBuilder ();
            if (!volume.isBlank ())
                sb.append (volume).append (":/");
            pathInfo.absolutePath = readSubPaths (sb, in, isReason3);

            // Part of refill?
            if (in.read () > 0)
                throw new IOException (Functions.getMessage ("IDS_SXT_REFILLS_NOT_SUPPORTED", ""));

            if (absolutePathVersion >= SxtChunkConstants.VERSION_1_8_0)
            {
                in.read ();
                final int reservedBytesSize = (int) StreamUtils.readUnsigned32 (in, true);
                in.skipNBytes (reservedBytesSize);
            }

            if (absolutePathVersion >= SxtChunkConstants.VERSION_1_9_0)
                in.skipNBytes (1);
        }

        return pathInfo;
    }


    private static String readSubPaths (final StringBuilder sb, final InputStream in, final boolean isReason3) throws IOException
    {
        final int subPathCount = (int) StreamUtils.readUnsigned32 (in, true);
        for (int i = 0; i < subPathCount; i++)
            sb.append ("/").append (readString (in, isReason3));
        return sb.toString ();
    }


    /**
     * Reads the version information from the given input stream.
     *
     * @param in The input stream to read from
     * @return Aggregated major, minor and revision byte into an integer, e.g. version 1.3.0 is
     *         '1003000'
     * @throws IOException Could not read the data
     */
    private static int readVersion (final InputStream in) throws IOException
    {
        if (in.read () != 0xBC)
            throw new IOException (Functions.getMessage ("IDS_SXT_NOT_A_PROPER_VERSION"));
        final int major = in.read ();
        final int minor = in.read ();
        final int revision = in.read ();
        in.skipNBytes (1);
        return major * 1000000 + minor * 1000 + revision;
    }


    /**
     * Reads a string.
     *
     * @param in The input stream
     * @param isReason3 True if Reason 3, this is the case when the version is set 1.3.0
     * @return The read text
     * @throws IOException Could not read the text
     */
    private static String readString (final InputStream in, final boolean isReason3) throws IOException
    {
        final long stringLength = StreamUtils.readUnsigned32 (in, true);

        // This indicates proper UTF-8
        if (stringLength == 0xFFFFFFFFL)
        {
            final int version = readVersion (in);
            if (version != SxtChunkConstants.VERSION_1_0_0)
                throw new IOException (Functions.getMessage ("IDS_SXT_UNKNOWN_UTF_8_VERSION", Integer.toString (version)));
            final long utf8Bytes = StreamUtils.readUnsigned32 (in, true);
            return new String (in.readNBytes ((int) utf8Bytes), StandardCharsets.UTF_8);
        }

        return new String (in.readNBytes ((int) stringLength), isReason3 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
    }
}
