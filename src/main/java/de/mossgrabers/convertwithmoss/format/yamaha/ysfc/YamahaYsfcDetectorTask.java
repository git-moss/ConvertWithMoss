// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


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
        ".x0a",
        ".x0w",
        ".x3a",
        ".x3w",
        ".x6a",
        ".x6w",
        ".x7u",
        ".x7l",
        ".x7a",
        ".x8u",
        ".x8l",
        ".x8a"
    };
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

        try
        {
            final YsfcFile ysfcFile = new YsfcFile (file);
            this.notifier.log ("IDS_YSFC_FOUND_TYPE", getWorkstationName (ysfcFile), ysfcFile.getVersionStr ());
            return this.createMultisample (ysfcFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    private static String getWorkstationName (final YsfcFile ysfcFile)
    {
        final int version = ysfcFile.getVersion ();
        if (version <= 101)
            return "Motif XS";
        if (version == 102)
            return "Motif XF";
        if (version == 103)
            return "MOXF";
        if (version >= 500)
            return "MODX";
        return "Montage";
    }


    /**
     * Create a multi-sample from the chunk data.
     *
     * @param ysfcFile The YSFC source file
     * @return The multi-sample(s)
     * @throws IOException COuld not read the multi-sample
     */
    private List<IMultisampleSource> createMultisample (final YsfcFile ysfcFile) throws IOException
    {
        final Map<String, YamahaYsfcChunk> chunks = ysfcFile.getChunks ();

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
            final List<YamahaYsfcKeybank> keyBanks = readKeyBanks (dwfmChunks.get (i), ysfcFile.getVersion ());
            final List<YamahaYsfcWaveData> waveDataItems = readWaveData (dwimChunks.get (i));

            int categoryValue = -1;
            final YamahaYsfcEntry yamahaYsfcEntry = ewfmListChunks.get (i);
            String name = yamahaYsfcEntry.getItemName ();
            if (name.isBlank ())
                name = FileUtils.getNameWithoutType (ysfcFile.getSourceFile ());
            else
            {
                final String [] split = name.split (":");
                if (split.length == 2)
                {
                    name = split[1];
                    categoryValue = Integer.parseInt (split[0]);
                }
            }
            final IMultisampleSource multisampleSource = this.createMultisampleSource (ysfcFile, name, categoryValue);
            final IGroup group = createSampleZones (keyBanks, waveDataItems, name, ysfcFile.getVersion ());
            multisampleSource.setGroups (Collections.singletonList (group));
            multisampleSources.add (multisampleSource);
        }

        return multisampleSources;
    }


    private static IGroup createSampleZones (final List<YamahaYsfcKeybank> keyBanks, final List<YamahaYsfcWaveData> waveDataItems, final String name, final int version) throws IOException
    {
        final DefaultGroup group = new DefaultGroup ("Layer");

        int keybankIndex = 0;
        int waveDataIndex = 0;
        while (keybankIndex < keyBanks.size ())
        {
            final YamahaYsfcKeybank keyBank = keyBanks.get (keybankIndex);

            final ISampleZone zone = createSampleZone (name, keyBank);
            group.addSampleZone (zone);

            final byte [] data = waveDataItems.get (waveDataIndex).getData ();
            final int sampleLength = data.length / (SAMPLE_RESOLUTION / 8);

            final int channels = keyBank.getChannels ();
            if (channels == 1)
            {
                final IAudioMetadata audioMetadata = new DefaultAudioMetadata (channels, keyBank.getSampleFrequency (), SAMPLE_RESOLUTION, sampleLength);
                zone.setSampleData (new InMemorySampleData (audioMetadata, data));
                keybankIndex++;
                waveDataIndex++;
            }
            else
            {
                // Combine the 2 left/right mono channels into a stereo one?
                final int nextIndex = waveDataIndex + 1;
                if (nextIndex >= waveDataItems.size ())
                    throw new IOException (Functions.getMessage ("IDS_YSFC_MISSING_RIGHT_SAMPLE"));

                int chns = 1;
                final byte [] dataRight = waveDataItems.get (nextIndex).getData ();
                if (data.length == dataRight.length)
                    chns = 2;

                final IAudioMetadata audioMetadata = new DefaultAudioMetadata (chns, keyBank.getSampleFrequency (), SAMPLE_RESOLUTION, sampleLength);
                final InMemorySampleData sampleData = new InMemorySampleData (audioMetadata, data);
                zone.setSampleData (sampleData);

                if (chns == 1)
                {
                    // Left/right sample have different length. Therefore, keep mono samples and pan
                    // them left/right
                    final ISampleZone zoneRight = new DefaultSampleZone (zone);
                    zoneRight.setSampleData (new InMemorySampleData (audioMetadata, dataRight));
                    zone.setPanorama (-1.0);
                    zoneRight.setPanorama (1.0);
                    group.addSampleZone (zoneRight);
                }
                else
                    sampleData.setSampleData (WaveFile.interleaveChannels (data, dataRight, SAMPLE_RESOLUTION));

                keybankIndex += version < 400 ? 1 : 2;
                waveDataIndex += 2;
            }
        }
        return group;
    }


    private IMultisampleSource createMultisampleSource (final YsfcFile ysfcFile, final String name, final int categoryValue)
    {
        final File sourceFile = ysfcFile.getSourceFile ();
        final File folder = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (folder, this.sourceFolder, name);
        final String mappingName = AudioFileUtils.subtractPaths (this.sourceFolder, sourceFile) + " : " + name;
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, name, mappingName);

        final IMetadata metadata = multisampleSource.getMetadata ();
        if (categoryValue > 0)
        {
            final String category = YamahaYsfcCategories.getWaveformCategory (categoryValue);
            if (!YamahaYsfcCategories.TAG_NO_ASSIGN.equals (category))
                metadata.setCategory (category);
        }
        else
            metadata.detectMetadata (this.metadataConfig, parts);

        return multisampleSource;
    }


    private static ISampleZone createSampleZone (final String name, final YamahaYsfcKeybank keybank)
    {
        final int rootNote = keybank.getRootNote ();
        final String sampleName = String.format ("%s_%d_%s", name.replace (':', '_'), Integer.valueOf (rootNote), NoteParser.formatNoteSharps (rootNote));

        final ISampleZone zone = new DefaultSampleZone (sampleName, null);
        zone.setKeyRoot (rootNote);
        zone.setKeyLow (keybank.getKeyRangeLower ());
        zone.setKeyHigh (keybank.getKeyRangeUpper ());
        zone.setVelocityLow (keybank.getVelocityRangeLower ());
        zone.setVelocityHigh (keybank.getVelocityRangeUpper ());
        zone.setTune (keybank.getCoarseTune () + keybank.getFineTune () / 100.0);
        final int level = keybank.getLevel ();
        zone.setGain (level == 0 ? Double.NEGATIVE_INFINITY : -95.25 + (level - 1) * 0.375);
        final int channels = keybank.getChannels ();
        if (channels == 1)
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
        return zone;
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
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @return The parsed wave metadata items
     * @throws IOException Could not read the data
     */
    private static List<YamahaYsfcKeybank> readKeyBanks (final byte [] dwfmDataArray, final int version) throws IOException
    {
        final List<YamahaYsfcKeybank> keyBanks = new ArrayList<> ();
        final ByteArrayInputStream dwfmContentStream = new ByteArrayInputStream (dwfmDataArray);
        final boolean isVersion1 = version < 400;
        // numberOfKeyBank
        StreamUtils.readUnsigned16 (dwfmContentStream, isVersion1);
        dwfmContentStream.skipNBytes (2);
        while (dwfmContentStream.available () > 0)
            keyBanks.add (new YamahaYsfcKeybank (dwfmContentStream, version));
        return keyBanks;
    }


    private static double normalizePanorama (final int panorama)
    {
        final double p = panorama > 0 ? panorama / 63.0 : panorama / 64.0;
        return Math.abs (p * 100) / 100.0;
    }
}
