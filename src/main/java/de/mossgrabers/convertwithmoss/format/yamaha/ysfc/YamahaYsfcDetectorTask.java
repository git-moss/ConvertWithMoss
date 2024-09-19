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
        ".x6a",
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

        // TODO Remove dumping performance chunk
        final YamahaYsfcChunk dpfmChunk = chunks.get ("DPFM");
        if (dpfmChunk != null)
        {
            // final List<byte []> dataArrays = dpfmChunk.getDataArrays ();
            // for (int i = 0; i < dataArrays.size (); i++)
            // {
            // Files.write (new File ("C:/Users/mos/Desktop/" + ysfcFile.getSourceFile ().getName ()
            // + "DPFM-" + i + ".bin").toPath (), dataArrays.get (i));
            // }
        }

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

            String name = ewfmListChunks.get (i).getItemName ();
            final String [] split = name.split (":");
            int categoryValue = -1;
            if (split.length == 2)
            {
                name = split[1];
                categoryValue = Integer.parseInt (split[0]);
            }
            final IMultisampleSource multisampleSource = this.createMultisampleSource (ysfcFile, name, categoryValue);

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
            if (!YamahaYsfcCategories.NO_ASSIGN.equals (category))
                metadata.setCategory (category);
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
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @return The parsed wave metadata items
     * @throws IOException Could not read the data
     */
    private static List<YamahaYsfcKeybank> readKeyBanks (final byte [] dwfmDataArray, final int version) throws IOException
    {
        final List<YamahaYsfcKeybank> keyBanks = new ArrayList<> ();
        final ByteArrayInputStream dwfmContentStream = new ByteArrayInputStream (dwfmDataArray);
        final int numberOfKeyBanks = (int) StreamUtils.readUnsigned32 (dwfmContentStream, false);
        for (int k = 0; k < numberOfKeyBanks; k++)
            keyBanks.add (new YamahaYsfcKeybank (dwfmContentStream, version));
        return keyBanks;
    }


    private static double normalizePanorama (final int panorama)
    {
        final double p = panorama > 0 ? panorama / 63.0 : panorama / 64;
        return Math.abs (p * 100) / 100.0;
    }
}
