// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
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
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcCategories;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcChunk;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcEntry;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcFileFormat;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcKeybank;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcPartElement;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcPerformance;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcPerformancePart;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcWaveData;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for Yamaha YSFC files.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcCreator extends AbstractCreator<YamahaYsfcCreatorUI>
{
    private static final DestinationAudioFormat                 DESTINATION_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);

    private static final Map<YamahaYsfcFileFormat, byte []>     PERFORMANCE_TEMPLATES_1  = new EnumMap<> (YamahaYsfcFileFormat.class);
    private static final Map<YamahaYsfcFileFormat, byte []>     PERFORMANCE_TEMPLATES_8  = new EnumMap<> (YamahaYsfcFileFormat.class);
    private static final Map<Integer, YamahaYsfcFileFormat>     FILE_FORMAT_MAP          = new HashMap<> ();
    private static final Map<Integer, Boolean>                  LIBRARY_FORMAT_MAP       = new HashMap<> ();
    private static final Map<FilterType, Map<Integer, Integer>> FILTER_TYPE_MAP          = new EnumMap<> (FilterType.class);
    private static final Map<FilterType, Integer>               DEFAULT_FILTERS          = new EnumMap<> (FilterType.class);
    static
    {
        FILE_FORMAT_MAP.put (Integer.valueOf (0), YamahaYsfcFileFormat.MONTAGE);
        FILE_FORMAT_MAP.put (Integer.valueOf (1), YamahaYsfcFileFormat.MONTAGE);
        FILE_FORMAT_MAP.put (Integer.valueOf (2), YamahaYsfcFileFormat.MODX);
        FILE_FORMAT_MAP.put (Integer.valueOf (3), YamahaYsfcFileFormat.MODX);
        FILE_FORMAT_MAP.put (Integer.valueOf (4), YamahaYsfcFileFormat.MOXF);
        // Is it a user library?
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (0), Boolean.TRUE);
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (1), Boolean.FALSE);
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (2), Boolean.TRUE);
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (3), Boolean.FALSE);
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (4), Boolean.FALSE);

        try
        {
            final String folder = "de/mossgrabers/convertwithmoss/templates/ysfc/";

            PERFORMANCE_TEMPLATES_1.put (YamahaYsfcFileFormat.MONTAGE, Functions.rawFileFor (folder + "InitPerf405.bin"));
            PERFORMANCE_TEMPLATES_8.put (YamahaYsfcFileFormat.MONTAGE, Functions.rawFileFor (folder + "InitPerf405-8.bin"));
            PERFORMANCE_TEMPLATES_1.put (YamahaYsfcFileFormat.MODX, Functions.rawFileFor (folder + "InitPerf501.bin"));
            PERFORMANCE_TEMPLATES_8.put (YamahaYsfcFileFormat.MODX, Functions.rawFileFor (folder + "InitPerf501-8.bin"));
        }
        catch (final IOException ex)
        {
            throw new RuntimeException (ex);
        }

        // Pole, Index
        final Map<Integer, Integer> lpfPoleMap = new HashMap<> ();
        lpfPoleMap.put (Integer.valueOf (4), Integer.valueOf (0));
        lpfPoleMap.put (Integer.valueOf (3), Integer.valueOf (2));
        lpfPoleMap.put (Integer.valueOf (2), Integer.valueOf (4));
        FILTER_TYPE_MAP.put (FilterType.LOW_PASS, lpfPoleMap);

        final Map<Integer, Integer> hpfPoleMap = new HashMap<> ();
        hpfPoleMap.put (Integer.valueOf (4), Integer.valueOf (6));
        hpfPoleMap.put (Integer.valueOf (2), Integer.valueOf (7));
        FILTER_TYPE_MAP.put (FilterType.HIGH_PASS, hpfPoleMap);

        final Map<Integer, Integer> bpfPoleMap = new HashMap<> ();
        bpfPoleMap.put (Integer.valueOf (2), Integer.valueOf (8));
        bpfPoleMap.put (Integer.valueOf (1), Integer.valueOf (9));
        FILTER_TYPE_MAP.put (FilterType.BAND_PASS, bpfPoleMap);

        final Map<Integer, Integer> brfPoleMap = new HashMap<> ();
        brfPoleMap.put (Integer.valueOf (2), Integer.valueOf (10));
        brfPoleMap.put (Integer.valueOf (1), Integer.valueOf (11));
        FILTER_TYPE_MAP.put (FilterType.BAND_REJECTION, brfPoleMap);

        // Default filter indices to use for unsupported number of poles
        DEFAULT_FILTERS.put (FilterType.LOW_PASS, Integer.valueOf (0));
        DEFAULT_FILTERS.put (FilterType.HIGH_PASS, Integer.valueOf (6));
        DEFAULT_FILTERS.put (FilterType.BAND_PASS, Integer.valueOf (8));
        DEFAULT_FILTERS.put (FilterType.BAND_REJECTION, Integer.valueOf (10));
    }

    /** Bank Select + Program Change. */
    private static final int CONTENT_NUMBER  = 0x3F2000;

    private static final int MAX_INSTRUMENTS = 8;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public YamahaYsfcCreator (final INotifier notifier)
    {
        super ("Yamaha YSFC", "Ysfc", notifier, new YamahaYsfcCreatorUI ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformanceLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.createPresetLibrary (destinationFolder, Collections.singletonList (multisampleSource), AbstractCreator.createSafeFilename (multisampleSource.getName ()));
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (multisampleSources.isEmpty ())
            return;

        final Integer selectedOutputFormat = Integer.valueOf (this.settingsConfiguration.selectedOutputFormat ());
        final YamahaYsfcFileFormat format = FILE_FORMAT_MAP.get (selectedOutputFormat);
        final boolean isUser = LIBRARY_FORMAT_MAP.get (selectedOutputFormat).booleanValue ();
        final File multiFile = this.createUniqueFilename (destinationFolder, libraryName, format.getEnding (isUser));
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.storeMultisamples (multisampleSources, multiFile, format);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public void createPerformance (final File destinationFolder, final IPerformanceSource performanceSource) throws IOException
    {
        final List<IInstrumentSource> instruments = performanceSource.getInstruments ();
        if (instruments.isEmpty ())
            return;

        final Integer selectedOutputFormat = Integer.valueOf (this.settingsConfiguration.selectedOutputFormat ());
        final YamahaYsfcFileFormat format = FILE_FORMAT_MAP.get (selectedOutputFormat);
        final boolean isUser = LIBRARY_FORMAT_MAP.get (selectedOutputFormat).booleanValue ();
        final String libraryName = AbstractCreator.createSafeFilename (performanceSource.getName ());
        final File multiFile = this.createUniqueFilename (destinationFolder, libraryName, format.getEnding (isUser));
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final YsfcFile ysfcFile = new YsfcFile (true);
        ysfcFile.setVersionStr (format.getMaxVersionStr ());

        // Numbering is across all(!) samples
        final LibraryCounters counters = new LibraryCounters ();
        this.addPerformance (performanceSource, format, ysfcFile, 0, counters);

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            ysfcFile.write (out);
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public void createPerformanceLibrary (final File destinationFolder, final List<IPerformanceSource> performanceSources, final String libraryName) throws IOException
    {
        if (performanceSources.isEmpty ())
            return;

        final Integer selectedOutputFormat = Integer.valueOf (this.settingsConfiguration.selectedOutputFormat ());
        final YamahaYsfcFileFormat format = FILE_FORMAT_MAP.get (selectedOutputFormat);
        final boolean isUser = LIBRARY_FORMAT_MAP.get (selectedOutputFormat).booleanValue ();
        final File multiFile = this.createUniqueFilename (destinationFolder, libraryName, format.getEnding (isUser));
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final YsfcFile ysfcFile = new YsfcFile (true);
        ysfcFile.setVersionStr (format.getMaxVersionStr ());

        // Numbering is across all(!) samples
        final LibraryCounters counters = new LibraryCounters ();
        for (int performanceIndex = 0; performanceIndex < performanceSources.size (); performanceIndex++)
        {
            final IPerformanceSource performanceSource = performanceSources.get (performanceIndex);
            this.addPerformance (performanceSource, format, ysfcFile, performanceIndex, counters);
        }

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            ysfcFile.write (out);
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create all YSFC chunks for the given multi-samples and store the file.
     *
     * @param multisampleSources The multi-sample sources to store
     * @param multiFile The file in which to store
     * @param format The output format
     * @throws IOException Could not store the file
     */
    private void storeMultisamples (final List<IMultisampleSource> multisampleSources, final File multiFile, final YamahaYsfcFileFormat format) throws IOException
    {
        final boolean addPerformances = !this.settingsConfiguration.createOnlyWaveforms ();
        if (!addPerformances)
            this.notifier.log ("IDS_YSFC_CREATES_ONLY_WAVEFORMS");

        final YsfcFile ysfcFile = new YsfcFile (addPerformances);
        ysfcFile.setVersionStr (format.getMaxVersionStr ());

        // Version 1 performances not supported!
        if (addPerformances && !format.isVersion1 ())
            this.createPerformancesForMultiSources (multisampleSources, format, ysfcFile);
        else
            this.createKeyBanksForMultiSources (multisampleSources, format, ysfcFile);

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            ysfcFile.write (out);
        }
    }


    private void addPerformance (final IPerformanceSource performanceSource, final YamahaYsfcFileFormat format, final YsfcFile ysfcFile, final int performanceIndex, final LibraryCounters counters) throws IOException
    {
        final List<IInstrumentSource> instrumentSources = this.limitInstruments (performanceSource.getInstruments ());
        final int numInstruments = instrumentSources.size ();
        final byte [] templateData = numInstruments == 1 ? PERFORMANCE_TEMPLATES_1.get (format) : PERFORMANCE_TEMPLATES_8.get (format);
        final YamahaYsfcPerformance performance = new YamahaYsfcPerformance (templateData, format, YsfcFile.parseVersion (format.getMaxVersionStr ()));
        performance.setName (StringUtils.fixASCII (performanceSource.getName ()));
        final int categoryID = getCategoryIndex (performanceSource.getMetadata ());

        final List<YamahaYsfcPerformancePart> parts = performance.getParts ();
        final List<YamahaYsfcPerformancePart> filledParts = new ArrayList<> ();

        // Create one part in the performance for each multi-sample source
        final List<int []> allWaveReferences = new ArrayList<> ();
        final Set<Integer> midiChannelsInUse = new HashSet<> ();
        boolean hasOmni = false;
        for (int i = 0; i < numInstruments; i++)
        {
            final IInstrumentSource instrumentSource = instrumentSources.get (i);
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();
            final String multisampleName = StringUtils.fixASCII (multisampleSource.getName ());

            final YamahaYsfcPerformancePart part = parts.get (i);
            part.setNoteLimitLow (instrumentSource.getClipKeyLow ());
            part.setNoteLimitHigh (instrumentSource.getClipKeyHigh ());

            final int partCategory = getCategoryIndex (multisampleSource.getMetadata ());

            allWaveReferences.add (this.fillPart (counters, format, ysfcFile, partCategory, multisampleSource, multisampleName, part));
            filledParts.add (part);

            final int midiChannel = instrumentSource.getMidiChannel ();
            if (midiChannel == -1)
                hasOmni = true;
            else
                midiChannelsInUse.add (Integer.valueOf (midiChannel));
            for (int scene = 0; scene < 8; scene++)
                part.setSceneKeyboardControl (scene, midiChannel == scene || midiChannel == -1);
        }

        // Must be always 8!
        if (numInstruments > 1)
            for (int i = numInstruments; i < 8; i++)
            {
                final YamahaYsfcPerformancePart part = parts.get (i);
                part.setPartSwitch (false);
                filledParts.add (part);
            }

        parts.clear ();
        parts.addAll (filledParts);

        // If there are only OMNI MIDI channels create at least 1 scene
        if (midiChannelsInUse.isEmpty () && hasOmni)
            midiChannelsInUse.add (Integer.valueOf (0));
        // Only activate those scenes for which there is a MIDI channel!
        for (int i = 0; i < midiChannelsInUse.size (); i++)
            performance.setSceneKeyboardControl (i, true);

        final YamahaYsfcEntry performanceEntry = createPerformanceEntry (categoryID, performance.getName (), CONTENT_NUMBER + performanceIndex, combineArrays (allWaveReferences), parts.size ());
        ysfcFile.fillChunkPair (YamahaYsfcChunk.ENTRY_LIST_PERFORMANCE, YamahaYsfcChunk.DATA_LIST_PERFORMANCE, performanceEntry, performance);
    }


    /**
     * Creates a performance for each multi-sample source and adds them to the given YSFC file.
     *
     * @param multisampleSources The multi-samples sources
     * @param format The output format
     * @param ysfcFile The YSFC file to which to add the performances
     * @throws IOException Could not create and add the chunks
     */
    private void createPerformancesForMultiSources (final List<IMultisampleSource> multisampleSources, final YamahaYsfcFileFormat format, final YsfcFile ysfcFile) throws IOException
    {
        // Numbering is across all(!) samples
        final LibraryCounters counters = new LibraryCounters ();
        final int categoryID = getCategoryIndex (multisampleSources.get (0).getMetadata ());

        // Create one performance for each multi-sample source
        for (int i = 0; i < multisampleSources.size (); i++)
        {
            final YamahaYsfcPerformance performance = new YamahaYsfcPerformance (PERFORMANCE_TEMPLATES_1.get (format), format, YsfcFile.parseVersion (format.getMaxVersionStr ()));

            final IMultisampleSource multisampleSource = multisampleSources.get (i);
            final String multisampleName = StringUtils.fixASCII (multisampleSource.getName ());
            performance.setName (multisampleName);

            // There is exactly 1 part in the template!
            final YamahaYsfcPerformancePart part = performance.getParts ().get (0);
            final int [] waveReferences = this.fillPart (counters, format, ysfcFile, categoryID, multisampleSource, multisampleName, part);
            final YamahaYsfcEntry performanceEntry = createPerformanceEntry (categoryID, multisampleName, CONTENT_NUMBER + i, waveReferences, 1);
            ysfcFile.fillChunkPair (YamahaYsfcChunk.ENTRY_LIST_PERFORMANCE, YamahaYsfcChunk.DATA_LIST_PERFORMANCE, performanceEntry, performance);
        }
    }


    private int [] fillPart (final LibraryCounters counters, final YamahaYsfcFileFormat format, final YsfcFile ysfcFile, final int categoryID, final IMultisampleSource multisampleSource, final String multisampleName, final YamahaYsfcPerformancePart part) throws IOException
    {
        // Set the performance name as well in case one will combine several performances into 1
        part.setName (multisampleName);
        part.setCategory (categoryID);

        final List<IGroup> groups = limitGroups (multisampleSource.getNonEmptyGroups (true));
        final int groupCount = groups.size ();
        final List<YamahaYsfcPartElement> elements = part.getElements ();
        final int [] waveReferences = new int [groupCount];

        // Assign all samples of a group to an element
        final String optimizedMultisampleName = StringUtils.optimizeName (multisampleName, 17);
        for (int g = 0; g < groupCount; g++)
        {
            final String name = groupCount == 1 ? optimizedMultisampleName : optimizedMultisampleName + " E" + (g + 1);
            final String waveformName = createCategoryNameText (getCategoryIndex (multisampleSource.getMetadata ()), name);
            waveReferences[g] = this.createElement (counters, waveformName, groups.get (g), elements.get (g), format, ysfcFile);
        }
        return waveReferences;
    }


    private int createElement (final LibraryCounters counters, final String waveformName, final IGroup group, final YamahaYsfcPartElement element, final YamahaYsfcFileFormat format, final YsfcFile ysfcFile) throws IOException
    {
        // Enable the element - the template part has already 8 fixed elements!
        element.setElementSwitch (1);

        final List<YamahaYsfcKeybank> keybankList = new ArrayList<> ();
        final List<YamahaYsfcWaveData> waveDataList = new ArrayList<> ();

        final List<ISampleZone> sampleZones = group.getSampleZones ();
        for (final ISampleZone zone: sampleZones)
            this.createWaveData (format, counters, keybankList, waveDataList, zone);

        counters.keygroupCounter++;
        final int keyBankIndex = 0x10000 + counters.keygroupCounter;
        final YamahaYsfcEntry keyBankEntry = createKeyBankEntry (waveformName, keyBankIndex);
        final YamahaYsfcEntry waveDataEntry = createWaveDataEntry (waveformName, keyBankIndex);
        ysfcFile.addWaveChunks (keyBankEntry, keybankList, waveDataEntry, waveDataList);

        // 2, 3, ... addresses libraries but these are outside of the bank!
        final int waveBank = 1;
        element.setWaveBank (waveBank);
        element.setWaveformNumber (counters.keygroupCounter);

        setElementParameters (element, sampleZones.get (0));

        // Return the wave reference
        return (waveBank << 16) + counters.keygroupCounter;
    }


    private static void setElementParameters (final YamahaYsfcPartElement element, final ISampleZone zone)
    {
        element.setXaMode (zone.getPlayLogic () == PlayLogic.ROUND_ROBIN ? 3 : 0);

        // Tuning

        final double tune = zone.getTuning ();
        final int semitones = (int) tune;
        element.setCoarseTune (semitones + 64);
        element.setFineTune ((int) ((tune - semitones) * 100) + 64);
        element.setPitchKeyFollowSensitivity ((int) Math.round (zone.getKeyTracking () * 100));

        element.setPan (MathUtils.denormalizeIntegerRange (zone.getPanning (), -63, 63, 64));

        // Gain & Level envelope

        final double gain = zone.getGain ();
        element.setElementLevel (gain == Double.NEGATIVE_INFINITY ? 0 : (int) Math.round ((gain + 95.25) / (2 * 0.375)));

        // Range is actually from -64..63 but but it gets already un-playable from 32 onwards...
        element.setLevelVelocitySensitivity (MathUtils.denormalizeIntegerRange (zone.getAmplitudeVelocityModulator ().getDepth (), -32, 32, 64));

        final IEnvelope ampEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        element.setAegAttackTime (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (ampEnvelope.getAttackTime () * 6.0));
        element.setAegDecay1Time (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (Math.max (0, ampEnvelope.getHoldTime ()) + Math.max (0, ampEnvelope.getDecayTime ())));
        element.setAegDecay2Time (0);
        element.setAegReleaseTime (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (ampEnvelope.getReleaseTime ()));
        final int sustainLevel = (int) Math.round (ampEnvelope.getSustainLevel () * 127.0);
        element.setAegDecay1Level (sustainLevel);
        element.setAegDecay2Level (sustainLevel);

        // Pitch Envelope

        final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchEnvelopeModulator ();
        final double pitchDepth = pitchEnvelopeModulator.getDepth ();
        if (pitchDepth > 0)
        {
            element.setPegDepth (MathUtils.denormalizeIntegerRange (pitchDepth, -64, 63, 64));

            final IEnvelope pitchEnvelope = pitchEnvelopeModulator.getSource ();
            element.setPegHoldLevel (MathUtils.denormalizeIntegerRange (pitchEnvelope.getStartLevel (), -128, 127, 128));
            element.setPegAttackLevel (MathUtils.denormalizeIntegerRange (pitchEnvelope.getHoldLevel (), -128, 127, 128));
            element.setPegDecay1Level (MathUtils.denormalizeIntegerRange (pitchEnvelope.getSustainLevel (), -128, 127, 128));
            element.setPegDecay2Level (MathUtils.denormalizeIntegerRange (pitchEnvelope.getEndLevel (), -128, 127, 128));
            element.setPegHoldTime (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (pitchEnvelope.getDelayTime ()));
            element.setPegAttackTime (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (pitchEnvelope.getAttackTime () * 6.0));
            element.setPegDecay1Time (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (Math.max (0, pitchEnvelope.getHoldTime ()) + Math.max (0, pitchEnvelope.getDecayTime ())));
            element.setPegDecay2Time (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (0));
            element.setPegReleaseTime (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (pitchEnvelope.getReleaseTime ()));
        }

        // Filter

        final Optional<IFilter> filterOpt = zone.getFilter ();
        if (filterOpt.isPresent ())
        {
            final IFilter filter = filterOpt.get ();
            // Cannot be null!
            final FilterType type = filter.getType ();
            final Map<Integer, Integer> polesMap = FILTER_TYPE_MAP.get (type);
            final Integer filterIndex = polesMap.get (Integer.valueOf (filter.getPoles ()));
            element.setFilterType ((filterIndex == null ? DEFAULT_FILTERS.get (type) : filterIndex).intValue ());

            element.setFilterCutoffFrequency ((int) Math.round (MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY) * 255.0));
            element.setFilterResonance ((int) Math.round (filter.getResonance () * 127.0));
            element.setFegLevelVelocitySensitivity (MathUtils.denormalizeIntegerRange (filter.getCutoffVelocityModulator ().getDepth (), -64, 63, 64));

            final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
            final double filterEnvelopeDepth = cutoffEnvelopeModulator.getDepth ();
            if (filterEnvelopeDepth != 0)
            {
                element.setFegDepth (MathUtils.denormalizeIntegerRange (filterEnvelopeDepth, -64, 63, 64));
                final IEnvelope cutoffEnvelope = cutoffEnvelopeModulator.getSource ();
                element.setFegHoldTime (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (cutoffEnvelope.getDelayTime ()));
                element.setFegAttackTime (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (cutoffEnvelope.getAttackTime () * 6.0));
                element.setFegDecay1Time (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (Math.max (0, cutoffEnvelope.getHoldTime ()) + Math.max (0, cutoffEnvelope.getDecayTime ())));
                element.setFegDecay2Time (0);
                element.setFegReleaseTime (YamahaYsfcPartElement.convertSecondsToEnvelopeTime (cutoffEnvelope.getReleaseTime ()));
                element.setFegHoldLevel (MathUtils.denormalizeIntegerRange (cutoffEnvelope.getStartLevel (), -128, 127, 128));
                element.setFegAttackLevel (MathUtils.denormalizeIntegerRange (cutoffEnvelope.getHoldLevel (), -128, 127, 128));
                final int filterSustainLevel = MathUtils.denormalizeIntegerRange (cutoffEnvelope.getSustainLevel (), -128, 127, 128);
                element.setFegDecay1Level (filterSustainLevel);
                element.setFegDecay2Level (filterSustainLevel);
                element.setFegReleaseLevel (MathUtils.denormalizeIntegerRange (cutoffEnvelope.getEndLevel (), -128, 127, 128));
            }
        }
    }


    private List<IInstrumentSource> limitInstruments (final List<IInstrumentSource> instruments)
    {
        if (instruments.size () <= MAX_INSTRUMENTS)
            return instruments;

        final List<IInstrumentSource> limitedInstruments = new ArrayList<> (MAX_INSTRUMENTS);

        // Group all instrument sources by their MIDI channel
        final TreeMap<Integer, List<IInstrumentSource>> sortedByMidiChannel = new TreeMap<> ();
        for (final IInstrumentSource instrumentSource: instruments)
            sortedByMidiChannel.computeIfAbsent (Integer.valueOf (instrumentSource.getMidiChannel ()), _ -> new ArrayList<> ()).add (instrumentSource);

        // First limit the MIDI channels to a maximum 8 different ones
        if (sortedByMidiChannel.size () > MAX_INSTRUMENTS)
            while (sortedByMidiChannel.size () > MAX_INSTRUMENTS)
            {
                final Integer highestKey = sortedByMidiChannel.lastKey ();
                this.notifier.logError ("IDS_YSFC_DROPPED_INSTRUMENTS_BY_MIDI_CHANNEL", highestKey.toString ());
                sortedByMidiChannel.remove (highestKey);
            }

        // Is it already limited to 8?
        for (final Map.Entry<Integer, List<IInstrumentSource>> entry: sortedByMidiChannel.entrySet ())
            limitedInstruments.addAll (entry.getValue ());
        if (instruments.size () <= MAX_INSTRUMENTS)
            return limitedInstruments;

        // How many instruments do we need to aggregate?
        final int reductionTarget = limitedInstruments.size () - MAX_INSTRUMENTS;
        this.notifier.logError ("IDS_YSFC_AGGREGATE_INSTRUMENTS_BY_MIDI_CHANNEL", Integer.toString (reductionTarget));
        this.reduceTreeMap (sortedByMidiChannel, reductionTarget);

        // Collect the rest
        limitedInstruments.clear ();
        for (final Map.Entry<Integer, List<IInstrumentSource>> entry: sortedByMidiChannel.entrySet ())
            limitedInstruments.addAll (entry.getValue ());
        return limitedInstruments;
    }


    private void reduceTreeMap (final TreeMap<Integer, List<IInstrumentSource>> map, final int reductionTarget)
    {
        int leftToReduce = reductionTarget;

        while (leftToReduce > 0)
            for (final Map.Entry<Integer, List<IInstrumentSource>> entry: map.entrySet ())
            {
                final List<IInstrumentSource> list = entry.getValue ();
                if (list.size () >= 2 && leftToReduce > 0)
                {
                    // Combine first two elements
                    final IInstrumentSource instrumentSource1 = list.remove (0);
                    final IInstrumentSource instrumentSource2 = list.remove (this.findInstrumentSourceWithMatchingRange (instrumentSource1, list));
                    instrumentSource1.getMultisampleSource ().getGroups ().addAll (instrumentSource2.getMultisampleSource ().getGroups ());

                    // Add back to the end
                    list.add (instrumentSource1);
                    leftToReduce--;
                }

                // Early exit if we've achieved the reduction
                if (leftToReduce == 0)
                    break;
            }
    }


    private int findInstrumentSourceWithMatchingRange (final IInstrumentSource instrumentSource, final List<IInstrumentSource> instrumentSources)
    {
        final int clipKeyLow = instrumentSource.getClipKeyLow ();
        final int clipKeyHigh = instrumentSource.getClipKeyHigh ();

        // Is there another instrument source with the same key-range?
        for (int i = 0; i < instrumentSources.size (); i++)
        {
            final IInstrumentSource instrumentSource2 = instrumentSources.get (i);
            if (clipKeyLow == instrumentSource2.getClipKeyLow () && clipKeyHigh == instrumentSource2.getClipKeyHigh ())
                return i;
        }

        // If not show a warning and combine the ranges
        instrumentSource.setClipKeyLow (Math.min (instrumentSources.get (0).getClipKeyLow (), clipKeyLow));
        instrumentSource.setClipKeyHigh (Math.max (instrumentSources.get (0).getClipKeyHigh (), clipKeyHigh));
        this.notifier.logError ("IDS_YSFC_AGGREGATE_KEY_RANGE");
        return 0;
    }


    /**
     * If there are more than 8 groups all sample zones of the groups 9 to N are added to group 8.
     *
     * @param groups The groups to limit
     * @return A maximum of 8 groups
     */
    private static List<IGroup> limitGroups (final List<IGroup> groups)
    {
        if (groups.size () <= 8)
            return groups;
        final List<IGroup> limitedGroups = new ArrayList<> (8);
        for (int i = 0; i < 8; i++)
            limitedGroups.add (groups.get (i));
        final IGroup lastGroup = limitedGroups.get (7);
        for (int i = 8; i < groups.size (); i++)
            for (final ISampleZone zone: groups.get (i).getSampleZones ())
                lastGroup.addSampleZone (zone);
        return limitedGroups;
    }


    /**
     * Creates a key-bank and wave-data entry for each multi-sample source and adds them to the
     * given YSFC file.
     *
     * @param multisampleSources The multi-samples sources
     * @param format The output format
     * @param ysfcFile The YSFC file to which to add the
     * @throws IOException Could not create and add the chunks
     */
    private void createKeyBanksForMultiSources (final List<IMultisampleSource> multisampleSources, final YamahaYsfcFileFormat format, final YsfcFile ysfcFile) throws IOException
    {
        // Numbering covers all(!) samples
        final LibraryCounters counters = new LibraryCounters ();

        for (int i = 0; i < multisampleSources.size (); i++)
        {
            final IMultisampleSource multisampleSource = multisampleSources.get (i);
            final List<YamahaYsfcKeybank> keybankList = new ArrayList<> ();
            final List<YamahaYsfcWaveData> waveDataList = new ArrayList<> ();
            for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
                for (final ISampleZone zone: group.getSampleZones ())
                    this.createWaveData (format, counters, keybankList, waveDataList, zone);

            final String multisampleName = multisampleSource.getName ();
            final int keyBankIndex = 0x10001 + i;
            final String waveformName = createCategoryNameText (getCategoryIndex (multisampleSource.getMetadata ()), multisampleName);
            final YamahaYsfcEntry keyBankEntry = createKeyBankEntry (waveformName, keyBankIndex);
            final YamahaYsfcEntry waveDataEntry = createWaveDataEntry (waveformName, keyBankIndex);
            ysfcFile.addWaveChunks (keyBankEntry, keybankList, waveDataEntry, waveDataList);
        }
    }


    private void createWaveData (final YamahaYsfcFileFormat version, final LibraryCounters counters, final List<YamahaYsfcKeybank> keybankList, final List<YamahaYsfcWaveData> waveDataList, final ISampleZone zone) throws IOException
    {
        // Ensure that the WAV is 16 bit
        final WaveFile waveFile = AudioFileUtils.convertToWav (zone.getSampleData (), DESTINATION_AUDIO_FORMAT);
        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        final String sampleName = zone.getName ();
        if (numberOfChannels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), sampleName));

        final DataChunk dataChunk = waveFile.getDataChunk ();
        int numSamples;
        try
        {
            numSamples = dataChunk.calculateLength (formatChunk);
        }
        catch (final CompressionNotSupportedException ex)
        {
            throw new IOException (ex);
        }

        final byte [] data = dataChunk.getData ();
        final boolean isStereo = numberOfChannels == 2;

        for (int channel = 0; channel < numberOfChannels; channel++)
        {
            final YamahaYsfcKeybank keybank = createKeybank (version, counters.sampleNumber, zone, formatChunk, numSamples);
            keybankList.add (keybank);
            keybank.setOffsets (counters.numberOfChannelsWritten, counters.numberOfSamplesWritten);

            final byte [] waveDataContent = isStereo ? getChannelData (channel, data) : data;

            counters.numberOfChannelsWritten++;
            // IMPROVE MOXF - The calculation is not correct
            counters.numberOfSamplesWritten += waveDataContent.length + 8;

            final YamahaYsfcWaveData waveData = new YamahaYsfcWaveData ();
            waveDataList.add (waveData);
            waveData.setData (waveDataContent);
        }

        this.notifyProgress ();
        this.notifyNewline (counters.sampleNumber);
        counters.sampleNumber++;
    }


    private static YamahaYsfcEntry createPerformanceEntry (final int categoryID, final String performanceName, final int programIndex, final int [] waveReferences, final int numParts) throws IOException
    {
        final YamahaYsfcEntry performanceEntry = new YamahaYsfcEntry ();
        performanceEntry.setContentNumber (programIndex);
        performanceEntry.setItemName (createCategoryNameText (categoryID, performanceName));
        performanceEntry.setItemTitle (performanceName);

        final ByteArrayOutputStream flagsOutput = new ByteArrayOutputStream ();

        // Motion Control: 0 = Off
        flagsOutput.write (0);
        // Type Flag: 0 = AWM
        flagsOutput.write (0);
        // Favorite: 0 = off
        flagsOutput.write (0);

        // 0x01 = SSS is unavailable (more than eight parts)
        // 0x02 = single part performance
        // 0x04 = arpeggiator enabled
        // 0x08 = motion sequencer enabled
        // 0x10 = one or more monophonic parts
        if (numParts == 1)
            flagsOutput.write (2);
        else if (numParts > 8)
            flagsOutput.write (1);
        else
            flagsOutput.write (0);

        // Each of the 16 bits represents a category: Bit 0 = Off, Bit 1 = Piano, ...
        final int mainCategory = categoryID / 16;
        final int categoryBit = categoryID == 256 ? 0 : (int) Math.pow (2, mainCategory);
        StreamUtils.writeUnsigned16 (flagsOutput, categoryBit, true);
        performanceEntry.setFlags (flagsOutput.toByteArray ());

        final ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream ();
        for (final int element: waveReferences)
            StreamUtils.writeUnsigned32 (dataOutputStream, element, true);
        performanceEntry.setAdditionalData (dataOutputStream.toByteArray ());
        return performanceEntry;
    }


    private static YamahaYsfcEntry createKeyBankEntry (final String waveformName, final int keyBankIndex)
    {
        final YamahaYsfcEntry keyBankEntry = new YamahaYsfcEntry ();
        keyBankEntry.setContentNumber (keyBankIndex);
        keyBankEntry.setItemName (waveformName);
        return keyBankEntry;
    }


    private static YamahaYsfcEntry createWaveDataEntry (final String waveformName, final int keyBankIndex)
    {
        final YamahaYsfcEntry waveDataEntry = new YamahaYsfcEntry ();
        waveDataEntry.setItemName (waveformName);
        waveDataEntry.setContentNumber (keyBankIndex);
        return waveDataEntry;
    }


    private static YamahaYsfcKeybank createKeybank (final YamahaYsfcFileFormat version, final int sampleNumber, final ISampleZone zone, final FormatChunk formatChunk, final int numSamples)
    {
        final YamahaYsfcKeybank keybank = new YamahaYsfcKeybank (version);
        keybank.setNumber (sampleNumber);

        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        keybank.setChannels (numberOfChannels);
        keybank.setSampleFrequency (formatChunk.getSampleRate ());
        keybank.setSampleLength (numSamples);
        keybank.setRootNote (zone.getKeyRoot ());

        keybank.setKeyRangeLower (zone.getKeyLow ());
        keybank.setKeyRangeUpper (zone.getKeyHigh ());
        keybank.setVelocityRangeLower (zone.getVelocityLow ());
        keybank.setVelocityRangeUpper (zone.getVelocityHigh ());

        final double tune = zone.getTuning ();
        final int semitones = (int) Math.floor (tune);
        keybank.setCoarseTune (semitones);
        keybank.setFineTune ((int) Math.floor ((tune - semitones) * 100.0));

        final double gain = zone.getGain ();
        keybank.setLevel (gain < -95.25 ? 0 : (int) Math.round ((Math.clamp (gain, -95.25, 0) + 95.25) / 0.375) + 1);

        keybank.setPanning (MathUtils.denormalizeIntegerRange (zone.getPanning (), -63, 63, 64));

        keybank.setPlayStart (zone.getStart ());
        keybank.setLoopEnd (zone.getStop ());

        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
            keybank.setLoopMode (1);
        else
        {
            final ISampleLoop loop = loops.get (0);
            keybank.setLoopMode (loop.getType () == LoopType.BACKWARDS ? 2 : 0);
            keybank.setLoopStart (loop.getStart ());
            if (loop.getEnd () < zone.getStop ())
                keybank.setLoopEnd (loop.getEnd ());
        }
        return keybank;
    }


    private static String createCategoryNameText (final int categoryID, final String name)
    {
        return categoryID + ":" + name;
    }


    private static int getCategoryIndex (final IMetadata metadata)
    {
        final String category = metadata.getCategory ();
        if (category != null)
        {
            final Integer categoryID = YamahaYsfcCategories.getWaveformCategoryIndex (TagDetector.normalizeCategory (category));
            if (categoryID != null)
                return categoryID.intValue ();
        }

        return 256;
    }


    private static byte [] getChannelData (final int channel, final byte [] data)
    {
        final byte [] channelData = new byte [data.length / 2];
        int pos = 0;
        final int offset = channel == 0 ? 0 : 2;
        for (int i = 0; i < data.length; i += 4)
        {
            channelData[pos] = data[i + offset];
            channelData[pos + 1] = data[i + 1 + offset];
            pos += 2;
        }
        return channelData;
    }


    private final class LibraryCounters
    {
        int sampleNumber            = 1;
        int keygroupCounter         = 0;
        int numberOfSamplesWritten  = 16;
        int numberOfChannelsWritten = 0x400C;
    }


    private static int [] combineArrays (final List<int []> arrayList)
    {
        int totalLength = 0;
        for (final int [] array: arrayList)
            totalLength += array.length;

        final int [] combined = new int [totalLength];
        int currentIndex = 0;
        for (final int [] array: arrayList)
        {
            System.arraycopy (array, 0, combined, currentIndex, array.length);
            currentIndex += array.length;
        }
        return combined;
    }
}