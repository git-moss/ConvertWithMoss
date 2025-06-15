// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.MathUtils;
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
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;


/**
 * Creator for Yamaha YSFC files.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcCreator extends AbstractCreator
{
    private static final String                                 YSFC_OUTPUT_FORMAT_LIBRARY = "YsfcOutputFormatPreset";
    private static final String                                 YSFC_CREATE_ONLY_WAVEFORMS = "YsfcCreateOnlyWaveforms";

    private static final DestinationAudioFormat                 DESTINATION_AUDIO_FORMAT   = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);

    private static final Map<YamahaYsfcFileFormat, byte []>     PERFORMANCE_TEMPLATES      = new EnumMap<> (YamahaYsfcFileFormat.class);
    private static final Map<Integer, YamahaYsfcFileFormat>     FILE_FORMAT_MAP            = new HashMap<> ();
    private static final Map<Integer, Boolean>                  LIBRARY_FORMAT_MAP         = new HashMap<> ();
    private static final Map<FilterType, Map<Integer, Integer>> FILTER_TYPE_MAP            = new EnumMap<> (FilterType.class);
    private static final Map<FilterType, Integer>               DEFAULT_FILTERS            = new EnumMap<> (FilterType.class);
    static
    {
        FILE_FORMAT_MAP.put (Integer.valueOf (0), YamahaYsfcFileFormat.MONTAGE);
        FILE_FORMAT_MAP.put (Integer.valueOf (1), YamahaYsfcFileFormat.MONTAGE);
        FILE_FORMAT_MAP.put (Integer.valueOf (2), YamahaYsfcFileFormat.MODX);
        FILE_FORMAT_MAP.put (Integer.valueOf (3), YamahaYsfcFileFormat.MODX);
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (0), Boolean.TRUE);
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (1), Boolean.FALSE);
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (2), Boolean.TRUE);
        LIBRARY_FORMAT_MAP.put (Integer.valueOf (3), Boolean.FALSE);

        try
        {
            final String FOLDER = "de/mossgrabers/convertwithmoss/templates/ysfc/";
            PERFORMANCE_TEMPLATES.put (YamahaYsfcFileFormat.MONTAGE, Functions.rawFileFor (FOLDER + "InitPerf405.bin"));
            PERFORMANCE_TEMPLATES.put (YamahaYsfcFileFormat.MODX, Functions.rawFileFor (FOLDER + "InitPerf501.bin"));
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
    private static final int CONTENT_NUMBER = 0x3F2000;

    private ToggleGroup      outputFormatGroup;
    private CheckBox         createOnlyWaveforms;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public YamahaYsfcCreator (final INotifier notifier)
    {
        super ("Yamaha YSFC", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        // TODO return true; - Ditch this until it is fixed...
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_YSFC_LIBRARY_FORMAT");
        this.outputFormatGroup = new ToggleGroup ();
        for (int i = 0; i < 4; i++)
        {
            final RadioButton order = panel.createRadioButton ("@IDS_YSFC_OUTPUT_FORMAT_OPTION" + i);
            order.setAccessibleHelp (Functions.getMessage ("IDS_YSFC_LIBRARY_FORMAT"));
            order.setToggleGroup (this.outputFormatGroup);
        }

        this.createOnlyWaveforms = panel.createCheckBox ("@IDS_YSFC_DESTINATION_TYPE_WAVEFORMS");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        Functions.setSelectedToggleIndex (this.outputFormatGroup, config.getInteger (YSFC_OUTPUT_FORMAT_LIBRARY, 1));
        this.createOnlyWaveforms.setSelected (config.getBoolean (YSFC_CREATE_ONLY_WAVEFORMS, false));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (YSFC_OUTPUT_FORMAT_LIBRARY, this.getSelectedOutputFormat ());
        config.setBoolean (YSFC_CREATE_ONLY_WAVEFORMS, this.createOnlyWaveforms.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.createLibrary (destinationFolder, Collections.singletonList (multisampleSource), AbstractCreator.createSafeFilename (multisampleSource.getName ()));
    }


    /** {@inheritDoc} */
    @Override
    public void createLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (multisampleSources.isEmpty ())
            return;

        final Integer selectedOutputFormat = Integer.valueOf (this.getSelectedOutputFormat ());
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

        final Integer selectedOutputFormat = Integer.valueOf (this.getSelectedOutputFormat ());
        final YamahaYsfcFileFormat format = FILE_FORMAT_MAP.get (selectedOutputFormat);

        final YsfcFile ysfcFile = new YsfcFile (true);
        ysfcFile.setVersionStr (format.getMaxVersion ());

        final boolean isUser = LIBRARY_FORMAT_MAP.get (selectedOutputFormat).booleanValue ();
        final String libraryName = AbstractCreator.createSafeFilename (performanceSource.getName ());
        final File multiFile = this.createUniqueFilename (destinationFolder, libraryName, format.getEnding (isUser));
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.storePerformance (performanceSource, multiFile, format, ysfcFile);

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
        final boolean addPerformances = !this.createOnlyWaveforms.isSelected ();
        if (!addPerformances)
            this.notifier.log ("IDS_YSFC_CREATES_ONLY_WAVEFORMS");

        final YsfcFile ysfcFile = new YsfcFile (addPerformances);
        ysfcFile.setVersionStr (format.getMaxVersion ());

        if (addPerformances)
            this.createPerformancesForMultiSources (multisampleSources, format, ysfcFile);
        else
            this.createKeyBanksForMultiSources (multisampleSources, format, ysfcFile);

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            ysfcFile.write (out);
        }
    }


    /**
     * Create all YSFC chunks for the given performance and store the file.
     *
     * @param performanceSource The performance sources to store
     * @param multiFile The file in which to store
     * @param format The output format
     * @param ysfcFile The YSFC file to which to add the performance
     * @throws IOException Could not store the file
     */
    private void storePerformance (final IPerformanceSource performanceSource, final File multiFile, final YamahaYsfcFileFormat format, final YsfcFile ysfcFile) throws IOException
    {
        // Numbering is across all(!) samples
        final LibraryCounters counters = new LibraryCounters ();
        final int categoryID = getCategoryIndex (performanceSource.getMetadata ());

        final YamahaYsfcPerformance performance = new YamahaYsfcPerformance (PERFORMANCE_TEMPLATES.get (format), format);
        performance.setName (StringUtils.fixASCII (performanceSource.getName ()));

        // There is exactly 1 part in the template!
        final List<YamahaYsfcPerformancePart> parts = performance.getParts ();
        final YamahaYsfcPerformancePart partTemplate = parts.get (0);
        parts.clear ();

        final List<IInstrumentSource> instrumentSources = limitInstruments (performanceSource.getInstruments ());
        final int numInstruments = instrumentSources.size ();

        // Create one part in the performance for each multi-sample source
        final List<int []> allWaveReferences = new ArrayList<> ();
        for (int i = 0; i < numInstruments; i++)
        {
            final IInstrumentSource instrumentSource = instrumentSources.get (i);
            // TODO Used MIDI channel
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();
            final String multisampleName = StringUtils.fixASCII (multisampleSource.getName ());

            final YamahaYsfcPerformancePart part = partTemplate.deepClone ();
            allWaveReferences.add (this.fillPart (counters, format, ysfcFile, categoryID, multisampleSource, multisampleName, part));
            parts.add (part);
        }

        final int performanceIndex = 0;
        final YamahaYsfcEntry performanceEntry = createPerformanceEntry (categoryID, performance.getName (), CONTENT_NUMBER + performanceIndex, combineArrays (allWaveReferences), parts.size ());
        ysfcFile.fillChunkPair (YamahaYsfcChunk.ENTRY_LIST_PERFORMANCE, YamahaYsfcChunk.DATA_LIST_PERFORMANCE, performanceEntry, performance);
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
            final YamahaYsfcPerformance performance = new YamahaYsfcPerformance (PERFORMANCE_TEMPLATES.get (format), format);

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
            counters.sampleNumber = this.createWaveData (format, counters.sampleNumber, keybankList, waveDataList, zone);

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

        final double tune = zone.getTune ();
        final int semitones = (int) tune;
        element.setCoarseTune (semitones + 64);
        element.setFineTune ((int) ((tune - semitones) * 100) + 64);

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

        final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchModulator ();
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


    private static List<IInstrumentSource> limitInstruments (final List<IInstrumentSource> instruments)
    {
        int maxInstruments = 16;

        final int size = instruments.size ();
        if (size <= maxInstruments)
            return instruments;

        final List<IInstrumentSource> limitedInstruments = new ArrayList<> (maxInstruments);
        for (int i = 0; i < maxInstruments; i++)
            limitedInstruments.add (instruments.get (i));
        return limitedInstruments;
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
        int sampleNumber = 1;

        for (int i = 0; i < multisampleSources.size (); i++)
        {
            final IMultisampleSource multisampleSource = multisampleSources.get (i);
            final List<YamahaYsfcKeybank> keybankList = new ArrayList<> ();
            final List<YamahaYsfcWaveData> waveDataList = new ArrayList<> ();
            for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
                for (final ISampleZone zone: group.getSampleZones ())
                    sampleNumber = this.createWaveData (format, sampleNumber, keybankList, waveDataList, zone);

            final String multisampleName = multisampleSource.getName ();
            final int keyBankIndex = 0x10001 + i;
            final String waveformName = createCategoryNameText (getCategoryIndex (multisampleSource.getMetadata ()), multisampleName);
            final YamahaYsfcEntry keyBankEntry = createKeyBankEntry (waveformName, keyBankIndex);
            final YamahaYsfcEntry waveDataEntry = createWaveDataEntry (waveformName, keyBankIndex);
            ysfcFile.addWaveChunks (keyBankEntry, keybankList, waveDataEntry, waveDataList);
        }
    }


    private int createWaveData (final YamahaYsfcFileFormat version, final int sampleNumber, final List<YamahaYsfcKeybank> keybankList, final List<YamahaYsfcWaveData> waveDataList, final ISampleZone zone) throws IOException
    {
        int newSampleNumber = sampleNumber;

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
            keybankList.add (createKeybank (version, newSampleNumber, zone, formatChunk, numSamples));

            final YamahaYsfcWaveData waveData = new YamahaYsfcWaveData ();
            waveDataList.add (waveData);
            waveData.setData (isStereo ? getChannelData (channel, data) : data);

            this.notifyProgress ();
            this.notifyNewline (newSampleNumber);
            newSampleNumber++;
        }
        return newSampleNumber;
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

        final double tune = zone.getTune ();
        final int semitones = (int) Math.floor (tune);
        keybank.setCoarseTune (semitones);
        keybank.setFineTune ((int) Math.floor ((tune - semitones) * 100.0));

        final double gain = zone.getGain ();
        keybank.setLevel (gain < -95.25 ? 0 : (int) Math.round ((Math.clamp (gain, -95.25, 0) + 95.25) / 0.375) + 1);

        keybank.setPanning (MathUtils.denormalizeIntegerRange (zone.getPanning (), -63, 63, 64));

        keybank.setPlayStart (zone.getStart ());
        keybank.setPlayEnd (zone.getStop ());

        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
            keybank.setLoopMode (1);
        else
        {
            final ISampleLoop loop = loops.get (0);
            keybank.setLoopMode (loop.getType () == LoopType.BACKWARDS ? 2 : 0);
            keybank.setLoopPoint (loop.getStart ());
            if (loop.getEnd () < zone.getStop ())
                keybank.setPlayEnd (loop.getEnd ());
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


    private int getSelectedOutputFormat ()
    {
        final int selected = Functions.getSelectedToggleIndex (this.outputFormatGroup);
        return selected < 0 ? 1 : selected;
    }


    private final class LibraryCounters
    {
        int sampleNumber    = 1;
        int keygroupCounter = 0;
    }
}