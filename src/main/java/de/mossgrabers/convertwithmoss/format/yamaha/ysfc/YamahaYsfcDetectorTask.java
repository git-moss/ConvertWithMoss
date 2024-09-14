// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
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
import java.util.TreeMap;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;


/**
 * Detects recursively Yamaha YSFC files in folders. Files must end with <i>.x7u</i>, <i>.x7l</i>,
 * <i>.x8l</i> or <i>.x8l</i>.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcDetectorTask extends AbstractDetectorTask
{
    private static final String [] ENDINGS           =
    {
        ".x7u",
        ".x7l",
        ".x7a",
        ".x8u",
        ".x8l",
        ".x8a"
    };
    private static final String    YAMAHA_YSFC       = "YAMAHA-YSFC";
    private static final int       HEADER_SIZE       = 64;
    private static final int       SAMPLE_RESOLUTION = 16;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    protected YamahaYsfcDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ENDINGS);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = new BufferedInputStream (new FileInputStream (file)))
        {
            return this.processFile (in, file);
        }
        catch (final IOException | FormatException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Process one YSFC file.
     *
     * @param in The input stream to read from
     * @param file The YSFC source file
     * @return The parsed multi-samples
     * @throws IOException Could not process the file
     * @throws FormatException Found unexpected format of the file
     */
    private List<IMultisampleSource> processFile (final InputStream in, final File file) throws IOException, FormatException
    {
        final String headerTag = StreamUtils.readASCII (in, 16);
        StreamUtils.checkTag (YAMAHA_YSFC, headerTag.trim ());

        // The version in the form of 'A.B.C', e.g. '1.0.2'. Older versions may have appended 0xFF
        // instead of 0x00
        final String versionStr = createAsciiString (in.readNBytes (16));
        final int version = parseVersion (versionStr.trim ());
        this.notifier.log ("IDS_YSFC_FOUND_TYPE", version < 400 ? "Motif" : "Montage/MODX", versionStr);

        // The size of the chunk catalog block
        final int catalogueSize = (int) StreamUtils.readUnsigned32 (in, true);

        // Padding
        in.skipNBytes (12);

        // The size of the library block
        long librarySize = StreamUtils.readUnsigned32 (in, true);
        // Library data present?
        if (librarySize >= 0xFFFFFFFFL)
            librarySize = 0;

        // Padding
        in.skipNBytes (12);

        readCatalog (in, HEADER_SIZE + catalogueSize + librarySize, catalogueSize);

        // Library data currently not used
        in.skipNBytes (librarySize);

        return this.createMultisample (file, readChunks (in));
    }


    /**
     * Create a multi-sample from the chunk data.
     *
     * @param file The YSFC source file
     * @param chunks The YSFC chunks
     * @return The multi-sample(s)
     * @throws IOException COuld not read the multi-sample
     */
    private List<IMultisampleSource> createMultisample (final File file, final Map<String, YamahaYsfcChunk> chunks) throws IOException
    {
        // Waveform Metadata
        final YamahaYsfcChunk ewfmChunk = chunks.get ("EWFM");
        final YamahaYsfcChunk dwfmChunk = chunks.get ("DWFM");
        // Wave Data
        final YamahaYsfcChunk ewimChunk = chunks.get ("EWIM");
        final YamahaYsfcChunk dwimChunk = chunks.get ("DWIM");
        if (ewfmChunk == null || ewimChunk == null || dwfmChunk == null || dwimChunk == null)
        {
            this.notifier.logError ("IDS_YSFC_NO_MULTISAMPLE_DATA");
            return Collections.emptyList ();
        }

        final List<YamahaYsfcEntry> ewfmListChunks = ewfmChunk.getEntryListChunks ();
        final List<YamahaYsfcEntry> ewimListChunks = ewimChunk.getEntryListChunks ();
        final List<byte []> dwfmChunks = dwfmChunk.getDataArrays ();
        final List<byte []> dwimChunks = dwimChunk.getDataArrays ();
        if (ewfmListChunks.size () != ewimListChunks.size () || dwfmChunks.size () != dwimChunks.size () || ewfmListChunks.size () != dwfmChunks.size ())
        {
            this.notifier.logError ("IDS_YSFC_DIFFERENT_NUMBER_OF_WAVEFORM_CHUNKS");
            return Collections.emptyList ();
        }

        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (int i = 0; i < ewfmListChunks.size (); i++)
        {
            final List<YamahaYsfcKeybank> keyBanks = readKeyBanks (dwfmChunks.get (i));
            final List<YamahaYsfcWaveData> waveDataItems = readWaveData (dwimChunks.get (i));

            String name = ewfmListChunks.get (i).getItemName ();
            final String [] split = name.split (":");
            int categoryValue = -1;
            if (split.length == 2)
            {
                name = split[1];
                categoryValue = Integer.parseInt (split[0]);
            }
            final IMultisampleSource multisampleSource = this.createMultisampleSource (file, name, categoryValue);

            // There are no groups
            final DefaultGroup group = new DefaultGroup ("Layer");
            final int size = keyBanks.size ();
            for (int k = 0; k < size; k++)
                k = createSampleZone (waveDataItems, keyBanks, keyBanks.get (k), group, name, k, size);

            multisampleSource.setGroups (Collections.singletonList (group));
            multisampleSources.add (multisampleSource);
        }

        return multisampleSources;
    }


    private static int createSampleZone (final List<YamahaYsfcWaveData> waveDataItems, final List<YamahaYsfcKeybank> keyBanks, final YamahaYsfcKeybank keybank, final DefaultGroup group, final String name, final int index, final int size)
    {
        final int channels = keybank.getChannels ();
        final byte [] data = waveDataItems.get (index).getData ();
        final IAudioMetadata audioMetadata = new DefaultAudioMetadata (channels, keybank.getSampleFrequency (), SAMPLE_RESOLUTION, keybank.getSampleLength ());
        final InMemorySampleData sampleData = new InMemorySampleData (audioMetadata, data);

        final int rootNote = keybank.getRootNote ();
        final String sampleName = String.format ("%s_%d_%s", name.replace (':', '_'), Integer.valueOf (rootNote), NoteParser.formatNoteSharps (rootNote));

        final ISampleZone zone = new DefaultSampleZone (sampleName, sampleData);
        zone.setKeyRoot (rootNote);
        zone.setKeyLow (keybank.getKeyRangeLower ());
        zone.setKeyHigh (keybank.getKeyRangeUpper ());
        zone.setVelocityLow (keybank.getVelocityRangeLower ());
        zone.setVelocityHigh (keybank.getVelocityRangeUpper ());
        zone.setTune (keybank.getCoarseTune () + keybank.getFineTune () / 100.0);
        zone.setGain (20.0 * Math.log10 (keybank.getLevel () / 255.0));
        zone.setPanorama (normalizePanorama (keybank.getPanorama ()));

        final int loopMode = keybank.getLoopMode ();
        if (loopMode != 1)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            zone.getLoops ().add (loop);
            loop.setStart (keybank.getLoopPoint ());
            loop.setEnd (keybank.getPlayEnd ());
            if (loopMode == 2)
                loop.setType (LoopType.BACKWARDS);
        }

        zone.setStart (keybank.getPlayStart ());
        zone.setStop (keybank.getPlayEnd ());

        group.addSampleZone (zone);

        // Combine the 2 left/right mono channels into a stereo one
        final int nextIndex = index + 1;
        if (channels != 2 || nextIndex >= size)
            return index;

        final int panoramaRight = keyBanks.get (nextIndex).getPanorama ();
        zone.setPanorama (normalizePanorama (Math.clamp (Math.round ((keybank.getPanorama () + panoramaRight) / 2.0), -64, 63)));
        sampleData.setSampleData (WaveFile.interleaveChannels (data, waveDataItems.get (nextIndex).getData (), SAMPLE_RESOLUTION));
        return nextIndex;
    }


    private IMultisampleSource createMultisampleSource (final File file, final String name, final int categoryValue)
    {
        final File folder = file.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (folder, this.sourceFolder, name);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, folder));

        final IMetadata metadata = multisampleSource.getMetadata ();
        if (categoryValue > 0)
        {
            final String category = YamahaYsfcCategories.getCategory (categoryValue);
            if (!YamahaYsfcCategories.NO_ASSIGN.equals (category))
                metadata.setCategory (category);
            final String subCategory = YamahaYsfcCategories.getSubCategory (categoryValue);
            if (!YamahaYsfcCategories.NO_ASSIGN.equals (subCategory))
                metadata.setKeywords (subCategory);
        }
        else
            metadata.detectMetadata (this.metadataConfig, parts);

        return multisampleSource;
    }


    /**
     * Reads all the wave data items from the given data array.
     *
     * @param dwimDataArray The array to read from
     * @return The parsed wave data items
     * @throws IOException Could not read the data
     */
    private static List<YamahaYsfcWaveData> readWaveData (final byte [] dwimDataArray) throws IOException
    {
        final List<YamahaYsfcWaveData> waveDataItems = new ArrayList<> ();
        final ByteArrayInputStream dwimContentStream = new ByteArrayInputStream (dwimDataArray);
        final int numberOfDataItems = (int) StreamUtils.readUnsigned32 (dwimContentStream, true);
        for (int k = 0; k < numberOfDataItems; k++)
            waveDataItems.add (new YamahaYsfcWaveData (dwimContentStream));
        return waveDataItems;
    }


    /**
     * Reads all the key-bank data items from the given data array.
     *
     * @param dwfmDataArray The array to read from
     * @return The parsed wave metadata items
     * @throws IOException Could not read the data
     */
    private static List<YamahaYsfcKeybank> readKeyBanks (final byte [] dwfmDataArray) throws IOException
    {
        final List<YamahaYsfcKeybank> keyBanks = new ArrayList<> ();
        final ByteArrayInputStream dwfmContentStream = new ByteArrayInputStream (dwfmDataArray);
        final int numberOfKeyBanks = (int) StreamUtils.readUnsigned32 (dwfmContentStream, false);
        for (int k = 0; k < numberOfKeyBanks; k++)
            keyBanks.add (new YamahaYsfcKeybank (dwfmContentStream));
        return keyBanks;
    }


    /**
     * Read all chunks.
     *
     * @param in The input stream to read from
     * @return The chunks
     * @throws IOException Could not read the chunks
     */
    private static Map<String, YamahaYsfcChunk> readChunks (final InputStream in) throws IOException
    {
        final Map<String, YamahaYsfcChunk> chunks = new HashMap<> ();
        while (in.available () > 0)
        {
            final YamahaYsfcChunk chunk = new YamahaYsfcChunk ();
            chunk.read (in);
            chunks.put (chunk.getChunkID (), chunk);
        }
        return chunks;
    }


    /**
     * Read the catalog block which consists of tuples of chunk IDs and their offset into the file.
     *
     * @param in The input stream to read from
     * @param startOfChunks The start of the chunks in the file counted from the start of the file
     * @param catalogueSize The size of the chunk catalog
     * @return A map where the key is the offset and the value if the chunk ordered by the offset
     * @throws IOException Could not process the block
     */
    private static Map<Long, String> readCatalog (final InputStream in, final long startOfChunks, final int catalogueSize) throws IOException
    {
        final int count = catalogueSize / 8;
        final Map<Long, String> chunkOffsets = new TreeMap<> ();
        for (int i = 0; i < count; i++)
        {
            final String chunkID = StreamUtils.readASCII (in, 4);
            final long offset = StreamUtils.readUnsigned32 (in, true);
            chunkOffsets.put (Long.valueOf (offset - startOfChunks), chunkID);
        }
        return chunkOffsets;
    }


    /**
     * Parse the version in the form of 'A.B.C' (e.g. '1.0.2') into an integer ABC (e.g. 102).
     *
     * @param versionStr The version to parse
     * @return The version as an integer
     */
    private static int parseVersion (final String versionStr)
    {
        try
        {
            return Integer.parseInt ("" + versionStr.charAt (0) + versionStr.charAt (2) + versionStr.charAt (4));
        }
        catch (final NumberFormatException | IndexOutOfBoundsException ex)
        {
            return 100;
        }
    }


    private static String createAsciiString (final byte [] byteArray)
    {
        int lastAsciiIndex = byteArray.length - 1;
        while (lastAsciiIndex >= 0 && (byteArray[lastAsciiIndex] < 0 || byteArray[lastAsciiIndex] > 127))
            lastAsciiIndex--;
        return new String (byteArray, 0, lastAsciiIndex + 1, StandardCharsets.US_ASCII);
    }


    private static double normalizePanorama (final int panorama)
    {
        final double p = panorama > 0 ? panorama / 63.0 : panorama / 64;
        return Math.abs (p * 100) / 100.0;
    }
}
