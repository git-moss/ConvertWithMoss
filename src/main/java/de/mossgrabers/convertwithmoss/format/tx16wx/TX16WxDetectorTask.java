// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tx16wx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import javafx.scene.control.ComboBox;


/**
 * Detects recursively TX16Wx txprog files in folders. Files must end with <i>.txprog</i>.
 *
 * @author Jürgen Moßgraber
 */
public class TX16WxDetectorTask extends AbstractDetectorTask
{
    private static final String                  ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String                  ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";
    private static final String                  ENDING_TXPERFORMANCE  = ".txperf";
    private static final String                  ENDING_TXPROGRAM      = ".txprog";

    private static final Map<String, LoopType>   LOOP_MODES            = new HashMap<> ();
    private static final Map<String, FilterType> FILTER_TYPES          = new HashMap<> ();
    private static final Map<String, Integer>    FILTER_SLOPES         = new HashMap<> ();
    static
    {
        LOOP_MODES.put ("Forward", LoopType.FORWARDS);
        LOOP_MODES.put ("Backward", LoopType.BACKWARDS);
        LOOP_MODES.put ("Bidirectional", LoopType.ALTERNATING);

        FILTER_TYPES.put ("LowPass", FilterType.LOW_PASS);
        FILTER_TYPES.put ("HighPass", FilterType.HIGH_PASS);
        FILTER_TYPES.put ("BandPass", FilterType.BAND_PASS);
        FILTER_TYPES.put ("Notch", FilterType.BAND_REJECTION);

        FILTER_SLOPES.put ("24dB", Integer.valueOf (4));
        FILTER_SLOPES.put ("12dB", Integer.valueOf (2));
        FILTER_SLOPES.put ("6dB", Integer.valueOf (1));
    }

    protected final ComboBox<Integer> levelsOfDirectorySearch;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param multisampleSourceConsumer The consumer that handles the detected multi-sample sources
     * @param performanceSourceConsumer The consumer that handles the detected performance sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     * @param detectPerformances If true, performances are detected otherwise presets
     * @param levelsOfDirectorySearch Combo box to read the directory search level
     */
    protected TX16WxDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> multisampleSourceConsumer, final Consumer<IPerformanceSource> performanceSourceConsumer, final File sourceFolder, final IMetadataConfig metadata, final boolean detectPerformances, final ComboBox<Integer> levelsOfDirectorySearch)
    {
        super (notifier, multisampleSourceConsumer, performanceSourceConsumer, sourceFolder, metadata, detectPerformances, detectPerformances ? ENDING_TXPERFORMANCE : ENDING_TXPROGRAM);

        this.levelsOfDirectorySearch = levelsOfDirectorySearch;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        final IInstrumentSource instrumentSource = readPresetFileAsInstrument (sourceFile);
        return instrumentSource == null ? Collections.emptyList () : Collections.singletonList (instrumentSource.getMultisampleSource ());
    }


    private IInstrumentSource readPresetFileAsInstrument (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return null;

        try (final FileInputStream in = new FileInputStream (sourceFile))
        {
            final String content = StreamUtils.readUTF8 (in);
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parsePresetFile (sourceFile, sourceFile.getParent (), document);
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
            return null;
        }
    }


    /** {@inheritDoc} */
    @Override
    protected List<IPerformanceSource> readPerformanceFiles (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final FileInputStream in = new FileInputStream (sourceFile))
        {
            final String content = StreamUtils.readUTF8 (in);
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parsePerformanceFile (sourceFile, sourceFile.getParent (), document);
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the performance metadata description file.
     *
     * @param sourceFile The performance file
     * @param basePath The parent folder, in case of a library the relative folder in the ZIP
     *            directory structure
     * @param document The XML document to parse
     * @return The parsed multi-sample source
     */
    private List<IPerformanceSource> parsePerformanceFile (final File sourceFile, final String basePath, final Document document)
    {
        final Element topElement = document.getDocumentElement ();
        if (!TX16WxTag.PERFORMANCE.equals (topElement.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final DefaultPerformanceSource performanceSource = new DefaultPerformanceSource ();
        performanceSource.setName (FileUtils.getNameWithoutType (sourceFile));

        File previousFolder = null;
        final File parentFile = new File (basePath);
        for (final Element slotElement: XMLUtils.getChildElementsByName (topElement, TX16WxTag.SLOT))
        {
            final String programPath = slotElement.getAttribute (TX16WxTag.PROGRAM);
            if (programPath == null || programPath.isBlank ())
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                continue;
            }

            final int height = this.levelsOfDirectorySearch.getSelectionModel ().getSelectedItem ().intValue ();
            final File programFile = findFile (this.notifier, parentFile, previousFolder, programPath, height, "program");
            if (!programFile.exists ())
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_PROGRAM_DOES_NOT_EXIST", programFile.getAbsolutePath ());
                continue;
            }
            previousFolder = programFile.getParentFile ();

            final IInstrumentSource instrumentSource = this.readPresetFileAsInstrument (programFile);
            if (instrumentSource == null)
                continue;

            final int midiChannel = XMLUtils.getIntegerAttribute (slotElement, TX16WxTag.MIDI_CHANNEL, 0) - 1;
            instrumentSource.setMidiChannel (midiChannel);
            // Don't use the name from the XML since it might only be something like "CH01"
            instrumentSource.setName (FileUtils.getNameWithoutType (programFile));
            performanceSource.addInstrument (instrumentSource);
        }

        return performanceSource.getInstruments ().isEmpty () ? Collections.emptyList () : Collections.singletonList (performanceSource);
    }


    /**
     * Load and parse the preset metadata description file.
     *
     * @param sourceFile The preset file
     * @param basePath The parent folder, in case of a library the relative folder in the ZIP
     *            directory structure
     * @param document The XML document to parse
     * @return The parsed multi-sample source
     */
    private IInstrumentSource parsePresetFile (final File sourceFile, final String basePath, final Document document)
    {
        final Element topElement = document.getDocumentElement ();
        if (!TX16WxTag.PROGRAM.equals (topElement.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return null;
        }

        final Map<String, ISampleZone> sampleMap = this.parseSamples (topElement, basePath);
        final List<IGroup> groups = new ArrayList<> ();
        final Optional<IFilter> filter = this.parseGroups (topElement, sampleMap, groups);

        String name = topElement.getAttribute (TX16WxTag.NAME);
        if (name == null || name.isBlank ())
            name = FileUtils.getNameWithoutType (sourceFile);
        final String n = this.metadataConfig.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, n);

        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, sourceFile));

        final String category = topElement.getAttribute (TX16WxTag.PROGRAM_ICON);
        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (groups), parts, category);

        multisampleSource.setGroups (groups);

        // Note: groups must be already set!
        if (filter.isPresent ())
            multisampleSource.setGlobalFilter (filter.get ());

        final IInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSource, -1);

        final Element boundsElement = XMLUtils.getChildElementByName (topElement, TX16WxTag.BOUNDS);
        if (boundsElement != null)
        {
            instrumentSource.setClipKeyLow (getNoteAttribute (boundsElement, TX16WxTag.LO_NOTE, TX16WxTag.LO_NOTE_ALT));
            instrumentSource.setClipKeyHigh (getNoteAttribute (boundsElement, TX16WxTag.HI_NOTE, TX16WxTag.HI_NOTE_ALT));
        }

        return instrumentSource;
    }


    /**
     * Parse all sample (wave) tags.
     *
     * @param topElement The top element
     * @param basePath The base path of the samples
     * @return All parsed samples
     */
    private Map<String, ISampleZone> parseSamples (final Element topElement, final String basePath)
    {
        final File parentFile = new File (basePath);
        File previousFolder = null;
        final Map<String, ISampleZone> sampleZoneMap = new HashMap<> ();
        for (final Element waveElement: XMLUtils.getChildElementsByName (topElement, TX16WxTag.SAMPLE, false))
        {
            String sampleName = waveElement.getAttribute (TX16WxTag.PATH);
            if (sampleName == null || sampleName.isBlank ())
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                continue;
            }

            final ISampleZone sampleZone;
            try
            {
                sampleName = URLDecoder.decode (sampleName, StandardCharsets.UTF_8).replace ("\\", File.separator);

                final int height = this.levelsOfDirectorySearch.getSelectionModel ().getSelectedItem ().intValue ();
                final File sampleFile = findSampleFile (this.notifier, parentFile, previousFolder, sampleName, height);
                if (!sampleFile.exists ())
                {
                    this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
                    continue;
                }
                previousFolder = sampleFile.getParentFile ();
                sampleZone = this.createSampleZone (sampleFile);
            }
            catch (final FileNotFoundException ex)
            {
                this.notifier.logError (ex);
                continue;
            }
            catch (final IOException ex)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
                continue;
            }

            final String sampleID = waveElement.getAttribute (TX16WxTag.SAMPLE_ID);
            sampleZoneMap.put (sampleID, sampleZone);

            sampleZone.setStart (XMLUtils.getIntegerAttribute (waveElement, TX16WxTag.START, -1));
            sampleZone.setStop (XMLUtils.getIntegerAttribute (waveElement, TX16WxTag.END, -1));
            sampleZone.setKeyRoot (getNoteAttribute (waveElement, TX16WxTag.ROOT));

            // Parse all loops
            readLoops (waveElement, sampleZone.getLoops ());
        }

        return sampleZoneMap;
    }


    /**
     * Parses all groups.
     *
     * @param programElement The XML element containing all groups
     * @param sampleMap The mapping of all sample element to their ID
     * @param groups Where to add the parsed groups
     * @return All parsed groups
     */
    private Optional<IFilter> parseGroups (final Element programElement, final Map<String, ISampleZone> sampleMap, final List<IGroup> groups)
    {
        final Map<String, Element> soundShapeElementMap = new HashMap<> ();
        for (final Element soundShapeElement: XMLUtils.getChildElementsByName (programElement, TX16WxTag.SOUND_SHAPE, false))
        {
            final String soundShapeID = soundShapeElement.getAttribute (TX16WxTag.SOUND_SHAPE_ID);
            if (soundShapeID != null && !soundShapeID.isBlank ())
                soundShapeElementMap.put (soundShapeID, soundShapeElement);
        }

        Optional<IFilter> filter = Optional.empty ();

        int groupCounter = 1;
        for (final Element groupElement: XMLUtils.getChildElementsByName (programElement, TX16WxTag.GROUP, false))
        {
            final String k = groupElement.getAttribute (TX16WxTag.NAME);
            final String groupName = k == null || k.isBlank () ? "Group " + groupCounter : k;
            final DefaultGroup group = new DefaultGroup (groupName);

            final Optional<IFilter> optFilter = this.parseGroup (group, groupElement, sampleMap, soundShapeElementMap);
            if (optFilter.isPresent () && filter.isEmpty ())
                filter = optFilter;
            groups.add (group);
            groupCounter++;
        }
        return filter;
    }


    /**
     * Parse a group.
     *
     * @param group The object to fill in the data
     * @param groupElement The XML group element
     * @param sampleMap The mapping of all sample element to their ID
     * @param soundShapeElementMap The sound shape elements mapped to their ID
     * @return The filter, if any
     */
    private Optional<IFilter> parseGroup (final DefaultGroup group, final Element groupElement, final Map<String, ISampleZone> sampleMap, final Map<String, Element> soundShapeElementMap)
    {
        final double groupVolumeOffset = this.parseVolume (groupElement, TX16WxTag.VOLUME);
        final double groupPanningOffset = parsePercentage (groupElement, TX16WxTag.PANNING);
        double groupTuningOffset = XMLUtils.getIntegerAttribute (groupElement, TX16WxTag.TUNING_COARSE, 0);
        groupTuningOffset += XMLUtils.getIntegerAttribute (groupElement, TX16WxTag.TUNING_FINE, 0) / 100.0;

        final String playMode = groupElement.getAttribute (TX16WxTag.GROUP_PLAYMODE);
        if ("Release".equals (playMode))
            group.setTrigger (TriggerType.RELEASE);

        Optional<IFilter> filter = Optional.empty ();
        Optional<IEnvelopeModulator> pitchModulator = Optional.empty ();
        int pitchbend = -1;

        IEnvelope ampEnvelope = null;
        double ampVelocity = 0;
        final String soundShape = groupElement.getAttribute (TX16WxTag.SOUND_SHAPE);
        if (soundShape != null && !soundShape.isBlank ())
        {
            final Element soundShapeElement = soundShapeElementMap.get (soundShape);
            if (soundShapeElement != null)
            {
                // Velocity modulation for amplitude
                ampVelocity = parsePercentage (soundShapeElement, TX16WxTag.AMP_VELOCITY);

                // Amplitude envelope
                final Element aegElement = XMLUtils.getChildElementByName (soundShapeElement, TX16WxTag.AMP_ENVELOPE);
                if (aegElement != null)
                {
                    ampEnvelope = new DefaultEnvelope ();
                    // The whole group can be delayed which is basically the same as the amplitude
                    // delay
                    ampEnvelope.setDelayTime (parseTime (soundShapeElement, TX16WxTag.GROUP_DELAY));
                    ampEnvelope.setAttackTime (parseTime (aegElement, TX16WxTag.AMP_ENV_ATTACK));
                    ampEnvelope.setHoldTime (parseTime (aegElement, TX16WxTag.AMP_ENV_DECAY1));
                    ampEnvelope.setDecayTime (parseTime (aegElement, TX16WxTag.AMP_ENV_DECAY2));
                    ampEnvelope.setReleaseTime (parseTime (aegElement, TX16WxTag.AMP_ENV_RELEASE));

                    ampEnvelope.setSustainLevel (parseNormalizedVolume (aegElement, TX16WxTag.AMP_ENV_SUSTAIN));

                    ampEnvelope.setAttackSlope (parsePercentage (aegElement, TX16WxTag.AMP_ENV_ATTACK_SHAPE));
                    ampEnvelope.setDecaySlope (parsePercentage (aegElement, TX16WxTag.AMP_ENV_DECAY2_SHAPE));
                    ampEnvelope.setReleaseSlope (parsePercentage (aegElement, TX16WxTag.AMP_ENV_RELEASE_SHAPE));
                }

                final List<TX16WxModulator> modulators = parseModulators (soundShapeElement);

                filter = this.parseFilter (soundShapeElement, modulators);
                pitchModulator = parsePitchModulator (soundShapeElement, modulators);
                final int bend = parsePitchbend (modulators);
                if (bend > 0)
                    pitchbend = bend;
            }
        }

        for (final Element regionElement: XMLUtils.getChildElementsByName (groupElement, TX16WxTag.REGION, false))
        {
            final ISampleZone zone = sampleMap.get (regionElement.getAttribute (TX16WxTag.SAMPLE));
            if (zone != null)
            {
                this.parseZone (zone, regionElement, groupVolumeOffset, groupPanningOffset, groupTuningOffset);
                group.addSampleZone (zone);

                if (pitchModulator.isPresent ())
                {
                    final IEnvelopeModulator modulator = pitchModulator.get ();
                    final IEnvelopeModulator zonePitchModulator = zone.getPitchModulator ();
                    zonePitchModulator.setDepth (modulator.getDepth ());
                    zonePitchModulator.setSource (modulator.getSource ());
                }

                if (pitchbend >= 0)
                {
                    zone.setBendUp (pitchbend);
                    zone.setBendDown (pitchbend);
                }

                zone.getAmplitudeVelocityModulator ().setDepth (ampVelocity);
                zone.getAmplitudeEnvelopeModulator ().setSource (ampEnvelope);
            }
        }

        return filter;
    }


    /**
     * Parse the zone attributes.
     *
     * @param zone The zone for which to parse the attributes
     * @param regionElement The region element which contains the attributes
     * @param groupVolumeOffset The group offset for volume
     * @param groupPanningOffset The group offset for panning
     * @param groupTuningOffset The group offset for tuning
     */
    private void parseZone (final ISampleZone zone, final Element regionElement, final double groupVolumeOffset, final double groupPanningOffset, final double groupTuningOffset)
    {
        zone.setGain (groupVolumeOffset + this.parseVolume (regionElement, TX16WxTag.ATTENUATION));
        zone.setPanning (groupPanningOffset + parsePercentage (regionElement, TX16WxTag.PANNING));

        double tuning = 0;
        final Element soundOffsetsElement = XMLUtils.getChildElementByName (regionElement, TX16WxTag.SOUND_OFFSETS);
        if (soundOffsetsElement != null)
        {
            tuning = XMLUtils.getIntegerAttribute (soundOffsetsElement, TX16WxTag.TUNING_COARSE, 0);
            tuning += XMLUtils.getIntegerAttribute (soundOffsetsElement, TX16WxTag.TUNING_FINE, 0) / 100.0;
        }
        zone.setTune (groupTuningOffset + tuning);

        // There is group switching with sequences (round-robin) but it seems no zone switching
        // Key tracking not available

        if (zone.getKeyRoot () == -1)
            zone.setKeyRoot (getNoteAttribute (regionElement, TX16WxTag.ROOT));

        final Element boundsElement = XMLUtils.getChildElementByName (regionElement, TX16WxTag.BOUNDS);
        if (boundsElement != null)
        {
            zone.setKeyLow (getNoteAttribute (boundsElement, TX16WxTag.LO_NOTE, TX16WxTag.LO_NOTE_ALT));
            zone.setKeyHigh (getNoteAttribute (boundsElement, TX16WxTag.HI_NOTE, TX16WxTag.HI_NOTE_ALT));
            final int velLow = XMLUtils.getIntegerAttribute (boundsElement, TX16WxTag.LO_VEL, -1);
            final int velHigh = XMLUtils.getIntegerAttribute (boundsElement, TX16WxTag.HI_VEL, -1);
            if (velLow > 0)
                zone.setVelocityLow (velLow);
            if (velHigh > 0)
                zone.setVelocityHigh (velHigh);

            final Element fadeBoundsElement = XMLUtils.getChildElementByName (regionElement, TX16WxTag.FADE_BOUNDS);
            if (fadeBoundsElement != null)
            {
                zone.setNoteCrossfadeLow (getNoteAttribute (fadeBoundsElement, TX16WxTag.LO_NOTE, TX16WxTag.LO_NOTE_ALT));
                zone.setNoteCrossfadeHigh (getNoteAttribute (fadeBoundsElement, TX16WxTag.HI_NOTE, TX16WxTag.HI_NOTE_ALT));
                final int fadeVelLow = XMLUtils.getIntegerAttribute (fadeBoundsElement, TX16WxTag.LO_VEL, -1);
                final int fadeVelHigh = XMLUtils.getIntegerAttribute (fadeBoundsElement, TX16WxTag.HI_VEL, -1);
                if (fadeVelLow > 0)
                    zone.setVelocityCrossfadeLow (fadeVelLow);
                if (fadeVelHigh > 0)
                    zone.setVelocityCrossfadeHigh (fadeVelHigh);
            }
        }

        try
        {
            zone.getSampleData ().addZoneData (zone, false, false);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
        }
    }


    /**
     * Read all loops from the sample element.
     *
     * @param sampleElement The sample element
     * @param loops The loops list
     */
    private static void readLoops (final Element sampleElement, final List<ISampleLoop> loops)
    {
        for (final Element loopElement: XMLUtils.getChildElementsByName (sampleElement, TX16WxTag.SAMPLE_LOOP, false))
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            final String loopMode = loopElement.getAttribute (TX16WxTag.LOOP_MODE);
            if (loopMode != null && !loopMode.isBlank ())
            {
                final LoopType loopType = LOOP_MODES.get (loopMode);
                if (loopType != null)
                {
                    loop.setType (loopType);
                    loop.setStart (XMLUtils.getIntegerAttribute (loopElement, TX16WxTag.LOOP_START, 0));
                    loop.setEnd (XMLUtils.getIntegerAttribute (loopElement, TX16WxTag.LOOP_END, 0));
                    loop.setCrossfadeInSamples (XMLUtils.getIntegerAttribute (loopElement, TX16WxTag.LOOP_CROSSFADE, 0));
                    loops.add (loop);
                }
            }
        }
    }


    /**
     * Parse the filter in the sound shape element.
     *
     * @param soundShapeElement The sound shape element
     * @param modulators The already parsed modulators
     * @return The read filter
     */
    private Optional<IFilter> parseFilter (final Element soundShapeElement, final List<TX16WxModulator> modulators)
    {
        Element filterElement = XMLUtils.getChildElementByName (soundShapeElement, TX16WxTag.FILTER);
        if (filterElement == null)
            filterElement = XMLUtils.getChildElementByName (soundShapeElement, TX16WxTag.FILTER1);
        if (filterElement == null)
            return Optional.empty ();

        final String filterTypeValue = filterElement.getAttribute (TX16WxTag.FILTER_TYPE);
        if (filterTypeValue == null || filterTypeValue.isBlank ())
            return Optional.empty ();
        final FilterType filterType = FILTER_TYPES.get (filterTypeValue);
        if (filterType == null)
            return Optional.empty ();

        String frequencyValue = filterElement.getAttribute (TX16WxTag.FILTER_FREQUENCY);
        if (frequencyValue == null || frequencyValue.isBlank ())
            frequencyValue = filterElement.getAttribute (TX16WxTag.FILTER_CUTOFF);
        if (frequencyValue == null || frequencyValue.isBlank ())
            return Optional.empty ();
        double frequency;
        if (frequencyValue.endsWith ("khz") || frequencyValue.endsWith ("kHz"))
            frequency = Double.parseDouble (frequencyValue.substring (0, frequencyValue.length () - 3).trim ()) * 1000;
        else if (frequencyValue.endsWith ("hz") || frequencyValue.endsWith ("Hz"))
            frequency = Double.parseDouble (frequencyValue.substring (0, frequencyValue.length () - 2).trim ());
        else
            return Optional.empty ();

        final String resonanceValue = filterElement.getAttribute (TX16WxTag.FILTER_RESONANCE);
        double resonance = 0;
        if (resonanceValue != null && resonanceValue.endsWith ("%"))
            resonance = Double.parseDouble (resonanceValue.substring (0, resonanceValue.length () - 1).trim ()) / 100.0;
        else
            resonance = MathUtils.normalize (this.parseVolume (filterElement, TX16WxTag.FILTER_RES), IFilter.MAX_RESONANCE);

        int poles = 4;
        final String slopeValue = filterElement.getAttribute (TX16WxTag.FILTER_SLOPE);
        if (slopeValue != null && !slopeValue.isBlank ())
        {
            final Integer poleValue = FILTER_SLOPES.get (slopeValue);
            if (poleValue != null)
                poles = poleValue.intValue ();
        }

        final DefaultFilter filter = new DefaultFilter (filterType, poles, frequency, resonance);
        parseFilterModulation (filter, soundShapeElement, modulators);
        return Optional.of (filter);
    }


    /**
     * Parse the filter envelope and velocity modulation from the modulation section in the sound
     * shape element.
     *
     * @param filter The filter for which to parse the envelope
     * @param soundShapeElement The sound shape element
     * @param modulators The already parsed modulators
     */
    private static void parseFilterModulation (final IFilter filter, final Element soundShapeElement, final List<TX16WxModulator> modulators)
    {
        for (final TX16WxModulator modulator: modulators)
        {
            if (!modulator.isDestination ("Filter 1 Freq"))
                continue;

            final Optional<Integer> modAmountAsCent = modulator.getModAmountAsCent ();
            if (modAmountAsCent.isEmpty ())
                continue;
            final double amount = Math.clamp (modAmountAsCent.get ().intValue () / (double) IEnvelope.MAX_ENVELOPE_DEPTH, -1, 1);

            if (modulator.isSource ("Vel"))
                filter.getCutoffVelocityModulator ().setDepth (amount);
            else if (modulator.isSource ("ENV1", "ENV2"))
            {
                final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                cutoffModulator.setDepth (amount);
                final Optional<IEnvelope> envelope = parseEnvelope (soundShapeElement, modulator.isSource ("ENV1") ? TX16WxTag.ENVELOPE_1 : TX16WxTag.ENVELOPE_2);
                if (envelope.isPresent ())
                    cutoffModulator.setSource (envelope.get ());
            }
        }
    }


    /**
     * Parse the pitch envelope and modulation depth from the modulation section in the sound shape
     * element.
     *
     * @param soundShapeElement The sound shape element
     * @param modulators The already parsed modulators
     * @return The optional pitch modulator
     */
    private static Optional<IEnvelopeModulator> parsePitchModulator (final Element soundShapeElement, final List<TX16WxModulator> modulators)
    {
        for (final TX16WxModulator modulator: modulators)
            if (modulator.isSource ("ENV1", "ENV2") && modulator.isDestination ("Pitch", "Pitch (Raw)"))
            {
                final Optional<Integer> modAmoundAsCent = modulator.getModAmountAsCent ();
                if (modAmoundAsCent.isPresent ())
                {
                    final IEnvelopeModulator pitchModulator = new DefaultEnvelopeModulator (0);
                    final double amount = modAmoundAsCent.get ().intValue () / 4800.0;
                    pitchModulator.setDepth (Math.clamp (amount, -1, 1));

                    final Optional<IEnvelope> pitchEnvelope = parsePitchEnvelope (soundShapeElement, modulator.isSource ("ENV1") ? TX16WxTag.ENVELOPE_1 : TX16WxTag.ENVELOPE_2);
                    if (pitchEnvelope.isPresent ())
                    {
                        pitchModulator.setSource (pitchEnvelope.get ());
                        return Optional.of (pitchModulator);
                    }
                }
            }

        return Optional.empty ();
    }


    private static Optional<IEnvelope> parseEnvelope (final Element parentElement, final String envelopeTag)
    {
        final Element envElement = XMLUtils.getChildElementByName (parentElement, envelopeTag);
        if (envElement == null)
            return Optional.empty ();

        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (parseTime (envElement, TX16WxTag.ENV_TIME1));
        envelope.setDecayTime (parseTime (envElement, TX16WxTag.ENV_TIME2));
        envelope.setReleaseTime (parseTime (envElement, TX16WxTag.ENV_TIME3));

        envelope.setStartLevel (parseNormalizedVolume (envElement, TX16WxTag.ENV_LEVEL0));
        envelope.setHoldLevel (parseNormalizedVolume (envElement, TX16WxTag.ENV_LEVEL1));
        envelope.setSustainLevel (parseNormalizedVolume (envElement, TX16WxTag.ENV_LEVEL2));
        envelope.setEndLevel (parseNormalizedVolume (envElement, TX16WxTag.ENV_LEVEL3));

        envelope.setAttackSlope (parsePercentage (envElement, TX16WxTag.ENV_SHAPE1));
        envelope.setDecaySlope (parsePercentage (envElement, TX16WxTag.ENV_SHAPE2));
        envelope.setReleaseSlope (parsePercentage (envElement, TX16WxTag.ENV_SHAPE3));

        return Optional.of (envelope);
    }


    private static Optional<IEnvelope> parsePitchEnvelope (final Element parentElement, final String envelopeTag)
    {
        final Element envElement = XMLUtils.getChildElementByName (parentElement, envelopeTag);
        if (envElement == null)
            return Optional.empty ();

        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (0);
        envelope.setDecayTime (parseTime (envElement, TX16WxTag.ENV_TIME1));
        envelope.setReleaseTime (parseTime (envElement, TX16WxTag.ENV_TIME3));

        envelope.setStartLevel (parseNormalizedVolume (envElement, TX16WxTag.ENV_LEVEL0));
        envelope.setHoldLevel (parseNormalizedVolume (envElement, TX16WxTag.ENV_LEVEL0));
        envelope.setSustainLevel (parseNormalizedVolume (envElement, TX16WxTag.ENV_LEVEL1));
        envelope.setEndLevel (parseNormalizedVolume (envElement, TX16WxTag.ENV_LEVEL3));

        envelope.setAttackSlope (parsePercentage (envElement, TX16WxTag.ENV_SHAPE1));
        envelope.setDecaySlope (parsePercentage (envElement, TX16WxTag.ENV_SHAPE2));
        envelope.setReleaseSlope (parsePercentage (envElement, TX16WxTag.ENV_SHAPE3));

        return Optional.of (envelope);
    }


    /**
     * Parse the pitch-bend from the modulation section in the sound shape element.
     *
     * @param modulators The already parsed modulators
     * @return The pitch-bend, negative if not found
     */
    private static int parsePitchbend (final List<TX16WxModulator> modulators)
    {
        for (final TX16WxModulator modulator: modulators)
            if (modulator.isSource ("Pitchbend") && modulator.isDestination ("Pitch", "Pitch (raw)"))
            {
                final Optional<Integer> modAmoundAsCent = modulator.getModAmountAsCent ();
                if (modAmoundAsCent.isPresent ())
                    return Math.abs (modAmoundAsCent.get ().intValue ());
            }
        return -1;
    }


    private static List<TX16WxModulator> parseModulators (final Element soundShapeElement)
    {
        final List<TX16WxModulator> modulators = new ArrayList<> ();
        final Element modulationElement = XMLUtils.getChildElementByName (soundShapeElement, TX16WxTag.MODULATION);
        if (modulationElement != null)
            for (final Element modulationEntryElement: XMLUtils.getChildElementsByName (modulationElement, TX16WxTag.MODULATION_ENTRY, false))
                modulators.add (new TX16WxModulator (modulationEntryElement));
        return modulators;
    }


    /**
     * Get the value of a note element. The value can be either an integer MIDI note or a text like
     * C#5.
     *
     * @param element The element
     * @param attributeNames The name(s) of the attribute(s) from which to get the note value
     * @return The value
     */
    private static int getNoteAttribute (final Element element, final String... attributeNames)
    {
        for (final String attributeName: attributeNames)
        {
            final String attribute = element.getAttribute (attributeName);
            if (attribute != null)
                return NoteParser.parseNote (attribute);
        }
        return -1;
    }


    /**
     * Parses a volume value in dB from the given tag.
     *
     * @param element The element which contains the volume attribute
     * @param tag The tag name of the attribute containing the volume
     * @return The volume in dB
     */
    private double parseVolume (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in dB?
        if (attribute.endsWith ("dB"))
        {
            final String value = attribute.substring (0, attribute.length () - 2).trim ();
            return "-inf".equals (value) ? Double.NEGATIVE_INFINITY : Double.parseDouble (value);
        }

        this.notifier.logError ("IDS_TX16WX_PERCENT_NOT_SUPPORTED", attribute);
        return 0;
    }


    /**
     * Parses a volume value in percent or already normalized to [0..1] from the given tag. If the
     * value is in dB it is assumed to be in the range of [-150..0].
     *
     * @param element The element which contains the volume attribute
     * @param tag The tag name of the attribute containing the volume
     * @return The volume in the range of [0..1]
     */
    private static double parseNormalizedVolume (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in dB?
        if (attribute.endsWith ("dB"))
        {
            final String value = attribute.substring (0, attribute.length () - 2).trim ();
            final double dBValue = "-inf".equals (value) ? Double.NEGATIVE_INFINITY : Double.parseDouble (value);
            return MathUtils.dBToDouble (dBValue);
        }

        final double value = attribute.endsWith ("%") ? Double.parseDouble (attribute.substring (0, attribute.length () - 1).trim ()) / 100.0 : Double.parseDouble (attribute);
        return Math.clamp (value, 0, 1);
    }


    /**
     * Parses a percentage value from the given tag.
     *
     * @param element The element which contains the percentage attribute
     * @param tag The tag name of the attribute containing the percentage
     * @return The percentage in the range of [-1..1]
     */
    private static double parsePercentage (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in percent (-100..100)?
        final double value;
        if (attribute.endsWith ("%"))
            value = Double.parseDouble (attribute.substring (0, attribute.length () - 1).trim ()) / 100.0;
        else
            // The value is in the range of [-1..1]
            value = Double.parseDouble (attribute);
        return Math.clamp (value, -1.0, 1.0);
    }


    /**
     * Parses a time value from the given tag in milli-seconds.
     *
     * @param element The element which contains the attribute
     * @param tag The tag name of the attribute
     * @return The time in seconds in the range of 0.1ms to 38000ms
     */
    private static double parseTime (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in milli-seconds?
        if (attribute.endsWith ("ms"))
            return Double.parseDouble (attribute.substring (0, attribute.length () - 2).trim ()) / 1000.0;
        // ... otherwise in seconds
        return Double.parseDouble (attribute.substring (0, attribute.length () - 1).trim ());
    }
}
