// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Yamaha YSFC files in folders. Files must end with <i>.x7u</i>, <i>.x7l</i>,
 * <i>.x8l</i> or <i>.x8l</i>.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcDetectorTask extends AbstractDetectorTask
{
    // 0: LPF24D, 1: LPF24A, 2: LPF18, 3: LPF18s, 4: LPF12+HPF12, 5: LPF6+HPF12, 6: HPF24D
    // 7: HPF12, 8: BPF12D, 9: BPFw, 10: BEF12, 11: BEF6, 12: DualLPF, 13: DualHPF, 14: DualBPF
    // 15: DualBEF, 16: LPF12+HPF6, 17: Thru
    private static final Map<Integer, FilterType> FILTER_TYPE_MAP = new HashMap<> ();
    private static final Map<Integer, Integer>    FILTER_POLE_MAP = new HashMap<> ();
    static
    {
        FILTER_TYPE_MAP.put (Integer.valueOf (0), FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (1), FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (2), FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (3), FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (4), FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (5), FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (6), FilterType.HIGH_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (7), FilterType.HIGH_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (8), FilterType.BAND_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (9), FilterType.BAND_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (10), FilterType.BAND_REJECTION);
        FILTER_TYPE_MAP.put (Integer.valueOf (11), FilterType.BAND_REJECTION);
        FILTER_TYPE_MAP.put (Integer.valueOf (12), FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (13), FilterType.HIGH_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (14), FilterType.BAND_PASS);
        FILTER_TYPE_MAP.put (Integer.valueOf (15), FilterType.BAND_REJECTION);
        FILTER_TYPE_MAP.put (Integer.valueOf (16), FilterType.LOW_PASS);

        FILTER_POLE_MAP.put (Integer.valueOf (0), Integer.valueOf (4));
        FILTER_POLE_MAP.put (Integer.valueOf (1), Integer.valueOf (4));
        FILTER_POLE_MAP.put (Integer.valueOf (2), Integer.valueOf (3));
        FILTER_POLE_MAP.put (Integer.valueOf (3), Integer.valueOf (3));
        FILTER_POLE_MAP.put (Integer.valueOf (4), Integer.valueOf (2));
        FILTER_POLE_MAP.put (Integer.valueOf (5), Integer.valueOf (2));
        FILTER_POLE_MAP.put (Integer.valueOf (6), Integer.valueOf (4));
        FILTER_POLE_MAP.put (Integer.valueOf (7), Integer.valueOf (2));
        FILTER_POLE_MAP.put (Integer.valueOf (8), Integer.valueOf (2));
        FILTER_POLE_MAP.put (Integer.valueOf (9), Integer.valueOf (1));
        FILTER_POLE_MAP.put (Integer.valueOf (10), Integer.valueOf (2));
        FILTER_POLE_MAP.put (Integer.valueOf (11), Integer.valueOf (1));
        FILTER_POLE_MAP.put (Integer.valueOf (12), Integer.valueOf (4));
        FILTER_POLE_MAP.put (Integer.valueOf (13), Integer.valueOf (4));
        FILTER_POLE_MAP.put (Integer.valueOf (14), Integer.valueOf (4));
        FILTER_POLE_MAP.put (Integer.valueOf (15), Integer.valueOf (4));
        FILTER_POLE_MAP.put (Integer.valueOf (16), Integer.valueOf (2));
    }

    // A = All, U = User, L = Library
    private static final String []                 ENDINGS                        =
    {
        ".x0a",                                                                                                                                                                                                                                                                   // Motif
                                                                                                                                                                                                                                                                                  // XS
        ".x0w",
        ".x3a",                                                                                                                                                                                                                                                                   // Motif
                                                                                                                                                                                                                                                                                  // XF
        ".x3w",
        ".x6a",                                                                                                                                                                                                                                                                   // MOXF
        ".x6w",
        ".x7u",                                                                                                                                                                                                                                                                   // Montage
        ".x7l",
        ".x7a",
        ".x8u",                                                                                                                                                                                                                                                                   // MODX
                                                                                                                                                                                                                                                                                  // /
                                                                                                                                                                                                                                                                                  // MODX+
        ".x8l",
        ".x8a",
        ".y2l",                                                                                                                                                                                                                                                                   // Montage
                                                                                                                                                                                                                                                                                  // M
        ".y2u"
    };
    private static final int                       SAMPLE_RESOLUTION              = 16;

    private static final Set<YamahaYsfcFileFormat> SUPPORTED_PERFORMANCE_VERSIONS = EnumSet.of (YamahaYsfcFileFormat.MONTAGE, YamahaYsfcFileFormat.MODX);

    private final boolean                          isSourceTypePerformance;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     * @param isSourceTypePerformance Create multi-samples from performances if true otherwise only
     *            from waveforms
     */
    protected YamahaYsfcDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata, final boolean isSourceTypePerformance)
    {
        super (notifier, consumer, sourceFolder, metadata, ENDINGS);

        this.isSourceTypePerformance = isSourceTypePerformance;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final YsfcFile ysfcFile = new YsfcFile (file);
            final YamahaYsfcFileFormat version = ysfcFile.getVersion ();
            this.notifier.log ("IDS_YSFC_FOUND_TYPE", version.getTitle (), ysfcFile.getVersionStr ());

            final List<IMultisampleSource> waveforms = this.createMultisamplesFromWaveforms (ysfcFile);
            if (!waveforms.isEmpty ())
            {
                final Map<String, YamahaYsfcChunk> chunks = ysfcFile.getChunks ();
                final YamahaYsfcChunk epfmChunk = chunks.get (YamahaYsfcChunk.ENTRY_LIST_PERFORMANCE);
                final YamahaYsfcChunk dpfmChunk = chunks.get (YamahaYsfcChunk.DATA_LIST_PERFORMANCE);
                final boolean hasNoPerformanceData = epfmChunk == null || dpfmChunk == null || epfmChunk.getEntryListChunks ().isEmpty ();
                // If there are no Performances, create directly from key-groups
                if (hasNoPerformanceData || !SUPPORTED_PERFORMANCE_VERSIONS.contains (version) || !this.isSourceTypePerformance)
                {
                    if (hasNoPerformanceData)
                        this.notifier.log ("IDS_YSFC_NO_PERFORMANCES");
                    if (!SUPPORTED_PERFORMANCE_VERSIONS.contains (version))
                        this.notifier.log ("IDS_YSFC_PERFORMANCES_NOT_SUPPORTED", version.getTitle ());
                    return waveforms;
                }

                return this.createMultisamplesFromPerformances (waveforms, epfmChunk, dpfmChunk, file.getName (), version);
            }
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Create multi-samples from the performance chunk data.
     *
     * @param waveforms The already read waveform data, each waveform is a IMultisampleSource
     * @param epfmChunk The performance entry chunk
     * @param dpfmChunk The performance data chunk
     * @param filename The name of the library file
     * @param version The version of the format
     * @return The multi-sample(s)
     * @throws IOException Could not read the multi-sample
     */
    private List<IMultisampleSource> createMultisamplesFromPerformances (final List<IMultisampleSource> waveforms, final YamahaYsfcChunk epfmChunk, final YamahaYsfcChunk dpfmChunk, final String filename, final YamahaYsfcFileFormat version) throws IOException
    {
        // Waveforms list is not empty and all of them contain metadata which was detected from the
        // library filename
        final IMultisampleSource globalMultisample = waveforms.get (0);
        final IMetadata globalMetadata = globalMultisample.getMetadata ();

        final List<IMultisampleSource> results = new ArrayList<> ();
        final List<byte []> dpfmListChunks = dpfmChunk.getDataArrays ();
        for (int i = 0; i < dpfmListChunks.size (); i++)
        {
            final byte [] performanceData = dpfmListChunks.get (i);

            final YamahaYsfcPerformance performance = new YamahaYsfcPerformance (new ByteArrayInputStream (performanceData), version);
            final String performanceName = performance.getName ();
            this.notifier.log ("IDS_YSFC_ANALYZING_PERFORMANCE", performanceName);
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (globalMultisample.getSourceFile (), globalMultisample.getSubPath (), performanceName, filename + " : " + performanceName);
            fillMetadata (globalMetadata, multisampleSource.getMetadata (), epfmChunk.getEntryListChunks ().get (i));

            final List<IGroup> groups = new ArrayList<> ();
            final List<YamahaYsfcPerformancePart> parts = performance.getParts ();
            for (int p = 0; p < parts.size (); p++)
            {
                final YamahaYsfcPerformancePart part = parts.get (p);
                final IGroup group = new DefaultGroup ();
                group.setName (part.getName ());
                final int commonXaMode = part.getCommonXaMode ();
                if (commonXaMode == 1)
                    group.setTrigger (TriggerType.LEGATO);
                else if (commonXaMode == 2)
                    group.setTrigger (TriggerType.RELEASE);

                // Convert all active elements
                final List<YamahaYsfcPartElement> elements = part.getElements ();
                for (int e = 0; e < elements.size (); e++)
                {
                    final YamahaYsfcPartElement element = elements.get (e);

                    final IMultisampleSource waveform = this.checkElement (element, waveforms, e + 1);
                    if (waveform == null)
                        continue;
                    final List<IGroup> waveGroups = waveform.getGroups ();
                    if (waveGroups.isEmpty ())
                        return null;

                    for (final ISampleZone waveformSampleZone: waveGroups.get (0).getSampleZones ())
                    {
                        // Clone all sample zones since they could be referenced multiple times
                        final ISampleZone sampleZone = new DefaultSampleZone (waveformSampleZone);
                        sampleZone.setSampleData (waveformSampleZone.getSampleData ());

                        // Check if the waveformSampleZone is in the key/velocity range
                        // Ignore if fully outside or clip the ranges it if necessary
                        if (limitKeyrangeAndVelocity (sampleZone, element))
                        {
                            fillParameterValues (sampleZone, element);
                            sampleZone.setBendDown ((part.getPitchBendRangeLower () - 64) * 100);
                            sampleZone.setBendUp ((part.getPitchBendRangeUpper () - 64) * 100);
                            group.addSampleZone (sampleZone);
                        }
                    }
                }

                if (!group.getSampleZones ().isEmpty ())
                    groups.add (group);
            }

            if (!groups.isEmpty ())
            {
                multisampleSource.setGroups (groups);
                results.add (multisampleSource);
            }
        }

        return results;
    }


    /**
     * Create multi-samples from the waveform chunk data.
     *
     * @param ysfcFile The YSFC source file
     * @return The multi-sample(s)
     * @throws IOException Could not read the multi-sample
     */
    private List<IMultisampleSource> createMultisamplesFromWaveforms (final YsfcFile ysfcFile) throws IOException
    {
        final Map<String, YamahaYsfcChunk> chunks = ysfcFile.getChunks ();

        // Waveform Metadata
        final YamahaYsfcChunk ewfmChunk = chunks.get (YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_METADATA);
        final YamahaYsfcChunk dwfmChunk = chunks.get (YamahaYsfcChunk.DATA_LIST_WAVEFORM_METADATA);
        // Waveform Data
        final YamahaYsfcChunk ewimChunk = chunks.get (YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_DATA);
        final YamahaYsfcChunk dwimChunk = chunks.get (YamahaYsfcChunk.DATA_LIST_WAVEFORM_DATA);
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

            final YamahaYsfcEntry yamahaYsfcEntry = ewfmListChunks.get (i);
            final Pair<Integer, String> itemCategoryAndName = yamahaYsfcEntry.getItemCategoryAndName ();
            String name = itemCategoryAndName.getValue ();
            if (name.isBlank ())
                name = FileUtils.getNameWithoutType (ysfcFile.getSourceFile ());
            final int categoryValue = itemCategoryAndName.getKey ().intValue ();
            final IMultisampleSource multisampleSource = this.createMultisampleSource (ysfcFile, name, categoryValue);
            final IGroup group = createSampleZones (keyBanks, waveDataItems, name, ysfcFile.getVersion ());
            multisampleSource.setGroups (Collections.singletonList (group));
            multisampleSources.add (multisampleSource);
        }

        return multisampleSources;
    }


    private static IGroup createSampleZones (final List<YamahaYsfcKeybank> keyBanks, final List<YamahaYsfcWaveData> waveDataItems, final String name, final YamahaYsfcFileFormat version) throws IOException
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
                    zone.setPanning (-1.0);
                    zoneRight.setPanning (1.0);
                    group.addSampleZone (zoneRight);
                }
                else
                    sampleData.setSampleData (WaveFile.interleaveChannels (data, dataRight, SAMPLE_RESOLUTION));

                keybankIndex += version.isVersion1 () ? 1 : 2;
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
        metadata.detectMetadata (this.metadataConfig, parts);
        if (categoryValue >= 0)
        {
            final String category = YamahaYsfcCategories.getMainCategory (categoryValue);
            if (!YamahaYsfcCategories.TAG_NO_ASSIGN.equals (category))
                metadata.setCategory (category);
        }

        return multisampleSource;
    }


    private static ISampleZone createSampleZone (final String name, final YamahaYsfcKeybank keybank)
    {
        final int rootNote = keybank.getRootNote ();
        final String sampleName = String.format ("%s_%d_%s", AbstractCreator.createSafeFilename (name), Integer.valueOf (rootNote), NoteParser.formatNoteSharps (rootNote));

        final ISampleZone zone = new DefaultSampleZone (sampleName, null);
        zone.setKeyRoot (rootNote);
        zone.setKeyLow (keybank.getKeyRangeLower ());
        zone.setKeyHigh (keybank.getKeyRangeUpper ());
        zone.setVelocityLow (keybank.getVelocityRangeLower ());
        zone.setVelocityHigh (keybank.getVelocityRangeUpper ());
        if (keybank.getFixedPitch () == 1)
            zone.setKeyTracking (0);
        zone.setTune (keybank.getCoarseTune () + keybank.getFineTune () / 100.0);
        final int level = keybank.getLevel ();
        zone.setGain (level == 0 ? Double.NEGATIVE_INFINITY : -95.25 + (level - 1) * 0.375);

        zone.setPanning (MathUtils.normalizeIntegerRange (keybank.getPanning (), -63, 63, 64));

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
     * @param version The format version of the YSFC file
     * @return The parsed wave metadata items
     * @throws IOException Could not read the data
     */
    private static List<YamahaYsfcKeybank> readKeyBanks (final byte [] dwfmDataArray, final YamahaYsfcFileFormat version) throws IOException
    {
        final List<YamahaYsfcKeybank> keyBanks = new ArrayList<> ();
        final ByteArrayInputStream dwfmContentStream = new ByteArrayInputStream (dwfmDataArray);
        // Number of key-banks
        StreamUtils.readUnsigned16 (dwfmContentStream, version.isVersion1 ());
        // Pitch offset table number (not used)
        dwfmContentStream.skipNBytes (2);
        while (dwfmContentStream.available () > 0)
            keyBanks.add (new YamahaYsfcKeybank (dwfmContentStream, version));
        return keyBanks;
    }


    private static boolean limitKeyrangeAndVelocity (final ISampleZone sampleZone, final YamahaYsfcPartElement element)
    {
        // Is the key-range is fully outside?
        // Is the velocity-range fully outside?
        if (sampleZone.getKeyHigh () < element.getNoteLimitLow () || sampleZone.getKeyLow () > element.getNoteLimitHigh () || sampleZone.getVelocityHigh () < element.getVelocityLimitLow () || sampleZone.getVelocityLow () > element.getVelocityLimitHigh ())
            return false;

        // Clip key-range if necessary
        if (sampleZone.getKeyLow () < element.getNoteLimitLow ())
            sampleZone.setKeyLow (element.getNoteLimitLow ());
        if (sampleZone.getKeyHigh () > element.getNoteLimitHigh ())
            sampleZone.setKeyHigh (element.getNoteLimitHigh ());

        // Clip velocity-range if necessary
        if (sampleZone.getVelocityLow () < element.getVelocityLimitLow ())
            sampleZone.setVelocityLow (element.getVelocityLimitLow ());
        if (sampleZone.getVelocityHigh () > element.getVelocityLimitHigh ())
            sampleZone.setVelocityHigh (element.getVelocityLimitHigh ());

        return true;
    }


    private static void fillMetadata (final IMetadata globalMetadata, final IMetadata metadata, final YamahaYsfcEntry entry)
    {
        metadata.setCreator (globalMetadata.getCreator ());
        metadata.setCategory (globalMetadata.getCategory ());
        metadata.setKeywords (globalMetadata.getKeywords ());

        final Pair<Integer, String> itemCategoryAndName = entry.getItemCategoryAndName ();
        final int categoryValue = itemCategoryAndName.getKey ().intValue ();
        if (categoryValue < 0)
            return;
        final String category = YamahaYsfcCategories.getMainCategory (categoryValue);
        if (!YamahaYsfcCategories.TAG_NO_ASSIGN.equals (category))
            metadata.setCategory (category);
    }


    private IMultisampleSource checkElement (final YamahaYsfcPartElement element, final List<IMultisampleSource> waveforms, final int elementIndex)
    {
        // Element is switched off
        if (element.getElementSwitch () != 1)
            return null;
        if (element.getWaveBank () == 0)
        {
            this.notifier.logError ("IDS_YSFC_ELEMENT_REFERENCES_PRESET_WAVEFORM", Integer.toString (elementIndex));
            return null;
        }
        final int waveformNumber = element.getWaveformNumber ();
        if (waveformNumber < 1 || waveformNumber > waveforms.size ())
        {
            this.notifier.logError ("IDS_YSFC_WAVEFORM_OUT_OF_RANGE", Integer.toString (waveformNumber));
            return null;
        }

        return waveforms.get (waveformNumber - 1);
    }


    private static void fillParameterValues (final ISampleZone zone, final YamahaYsfcPartElement element)
    {
        // Cycle or Random
        final int xaMode = element.getXaMode ();
        if (xaMode == 3 || xaMode == 4)
            zone.setPlayLogic (PlayLogic.ROUND_ROBIN);

        final double pitchOffset = element.getCoarseTune () - 64 + (element.getFineTune () - 64) / 100.0;
        zone.setTune (zone.getTune () + pitchOffset);

        zone.setPanning ((zone.getPanning () + MathUtils.normalizeIntegerRange (element.getPan (), -63, 63, 64)) / 2.0);

        final int level = element.getElementLevel ();
        zone.setGain (level == 0 ? Double.NEGATIVE_INFINITY : -95.25 + 2 * level * 0.375);

        final double levelDepth = MathUtils.normalizeIntegerRange (element.getLevelVelocitySensitivity (), -64, 63, 64);
        zone.getAmplitudeVelocityModulator ().setDepth (levelDepth);

        final IEnvelope ampEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        ampEnvelope.setAttackTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getAegAttackTime ()) / 6.0);
        ampEnvelope.setDecayTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getAegDecay1Time ()) + YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getAegDecay2Time ()));
        ampEnvelope.setReleaseTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getAegReleaseTime ()));
        ampEnvelope.setSustainLevel (element.getAegDecay2Level () / 127.0);

        // Modulation depth might be non-zero which causes unwanted modulation if not all levels are
        // available on the output format. E.g. SFZ has a fixed value for the attack level.
        // Therefore, check if there really should be a modulation.
        final int pegHoldLevel = element.getPegHoldLevel ();
        final int pegAttackLevel = element.getPegAttackLevel ();
        final int pegDecay1Level = element.getPegDecay1Level ();
        final int pegDecay2Level = element.getPegDecay2Level ();
        final double startLevel = MathUtils.normalizeIntegerRange (pegHoldLevel, -128, 127, 128);
        final double holdLevel = MathUtils.normalizeIntegerRange (pegAttackLevel, -128, 127, 128);
        final double decay1Level = MathUtils.normalizeIntegerRange (pegDecay1Level, -128, 127, 128);
        final double decay2Level = MathUtils.normalizeIntegerRange (pegDecay2Level, -128, 127, 128);
        final int pegHoldTime = element.getPegHoldTime ();
        final int pegAttackTime = element.getPegAttackTime ();
        final int pegDecay1Time = element.getPegDecay1Time ();
        final int pegDecay2Time = element.getPegDecay2Time ();
        final int pegReleaseTime = element.getPegReleaseTime ();
        final boolean hasNoEnvelope = pegHoldLevel == 128 && pegAttackLevel == 128 && pegDecay1Level == 128 && pegDecay2Level == 128 || pegHoldTime == 0 && pegAttackTime == 0 && pegDecay1Time == 0 && pegDecay2Time == 0 && pegReleaseTime == 0;
        if (!hasNoEnvelope)
        {
            final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchModulator ();
            pitchEnvelopeModulator.setDepth (MathUtils.normalizeIntegerRange (element.getPegDepth (), -64, 63, 64));
            final IEnvelope pitchEnvelope = pitchEnvelopeModulator.getSource ();
            pitchEnvelope.setDelayTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (pegHoldTime));
            pitchEnvelope.setAttackTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (pegAttackTime) / 6.0);
            pitchEnvelope.setDecayTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (pegDecay1Time) + YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (pegDecay2Time));
            pitchEnvelope.setReleaseTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (pegReleaseTime));
            pitchEnvelope.setStartLevel (startLevel);
            pitchEnvelope.setHoldLevel (holdLevel);
            pitchEnvelope.setSustainLevel (decay1Level);
            pitchEnvelope.setEndLevel (decay2Level);
        }

        final Integer filterTypeID = Integer.valueOf (element.getFilterType ());
        final FilterType filterType = FILTER_TYPE_MAP.get (filterTypeID);
        if (filterType == null)
            return;
        final double normalizedCutoff = element.getFilterCutoffFrequency () / 255.0;
        final double frequency = MathUtils.denormalizeFrequency (normalizedCutoff, IFilter.MAX_FREQUENCY);
        final double resonance = element.getFilterResonance () / 127.0;
        final IFilter filter = new DefaultFilter (filterType, FILTER_POLE_MAP.get (filterTypeID).intValue (), frequency, resonance);
        zone.setFilter (filter);
        filter.getCutoffVelocityModulator ().setDepth (MathUtils.normalizeIntegerRange (element.getFegLevelVelocitySensitivity (), -64, 63, 64));
        final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
        cutoffEnvelopeModulator.setDepth (MathUtils.normalizeIntegerRange (element.getFegDepth (), -64, 63, 64));
        final IEnvelope cutoffEnvelope = cutoffEnvelopeModulator.getSource ();
        cutoffEnvelope.setDelayTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getFegHoldTime ()));
        cutoffEnvelope.setAttackTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getFegAttackTime ()) / 6.0);
        cutoffEnvelope.setDecayTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getFegDecay1Time ()) + YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getFegDecay2Time ()));
        cutoffEnvelope.setReleaseTime (YamahaYsfcPartElement.convertEnvelopeTimeToSeconds (element.getFegReleaseTime ()));
        cutoffEnvelope.setStartLevel (MathUtils.normalizeIntegerRange (element.getFegHoldLevel (), -128, 127, 128));
        cutoffEnvelope.setHoldLevel (MathUtils.normalizeIntegerRange (element.getFegAttackLevel (), -128, 127, 128));
        cutoffEnvelope.setSustainLevel (MathUtils.normalizeIntegerRange (element.getFegDecay2Level (), -128, 127, 128));
        cutoffEnvelope.setEndLevel (MathUtils.normalizeIntegerRange (element.getFegReleaseLevel (), -128, 127, 128));
    }
}
