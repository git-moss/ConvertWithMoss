// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;


/**
 * Creator for preset files of the 1010music blackbox and nanobox tangerine. A preset has a
 * description file encoded in XML located in the Presets folder. The related samples are in a
 * separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010Creator extends AbstractCreator
{
    private static final String                 MUSIC_1010_INTERPOLATION_QUALITY = "Music1010InterpolationQuality";
    private static final String                 MUSIC_1010_RESAMPLE_TO_24_48     = "Music1010ResampleTo2448";
    private static final String                 MUSIC_1010_TRIM_START_TO_END     = "Music1010TrimStartToEnd";
    private static final DestinationAudioFormat OPTIMIZED_AUDIO_FORMAT           = new DestinationAudioFormat (new int []
    {
        24
    }, 48000, true);
    private static final DestinationAudioFormat DEFEAULT_AUDIO_FORMAT            = new DestinationAudioFormat ();

    private static final Map<String, String>    EMPTY_PARAM_ATTRIBUTES           = new HashMap<> ();
    private static final Map<String, String>    MULTISAMPLE_PARAM_ATTRIBUTES     = new HashMap<> ();
    static
    {
        EMPTY_PARAM_ATTRIBUTES.put ("gaindb", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("reverse", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("cellmode", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("envattack", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("envdecay", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("envsus", "1000");
        EMPTY_PARAM_ATTRIBUTES.put ("envrel", "200");
        EMPTY_PARAM_ATTRIBUTES.put ("velamount", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("samstart", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("samlen", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("quantsize", "3");
        EMPTY_PARAM_ATTRIBUTES.put ("synctype", "5");
        EMPTY_PARAM_ATTRIBUTES.put ("slicersync", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("slicestepmode", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("beatcount", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("fx1send", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("fx2send", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("playthru", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("slicerquantsize", "13");
        EMPTY_PARAM_ATTRIBUTES.put ("deftemplate", "1");
        EMPTY_PARAM_ATTRIBUTES.put ("recpresetlen", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("recquant", "3");
        EMPTY_PARAM_ATTRIBUTES.put ("recinput", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("recusethres", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("recthresh", "-20000");
        EMPTY_PARAM_ATTRIBUTES.put ("recmonoutbus", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("mute", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("pitch", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("dualfilcutoff", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("res", "500");
        EMPTY_PARAM_ATTRIBUTES.put ("panpos", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("samtrigtype", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("polymode", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("polymodeslice", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("okegrp", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("midimode", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("midioutchan", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("padnote", "0");
        EMPTY_PARAM_ATTRIBUTES.put ("rootnote", "0");

        MULTISAMPLE_PARAM_ATTRIBUTES.put ("gaindb", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("pitch", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("panpos", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("samtrigtype", "1");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("loopmode", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("loopmodes", "0");
        // Setup for modulation wheel!
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lfowave", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lforate", "845");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lfoamount", "0");

        MULTISAMPLE_PARAM_ATTRIBUTES.put ("reverse", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("cellmode", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("envattack", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("envdecay", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("envsus", "1000");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("envrel", "103");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("samstart", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("samlen", "1425505");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("loopstart", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("loopend", "1425505");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("quantsize", "3");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("synctype", "5");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("actslice", "1");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("outputbus", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("polymode", "5");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("slicestepmode", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("chokegrp", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("dualfilcutoff", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("rootnote", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("beatcount", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("fx1send", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("fx2send", "0");
        // Set this to 0 if one-shots should be supported in the future
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("multisammode", "1");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("playthru", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("slicerquantsize", "13");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("slicersync", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("padnote", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("loopfadeamt", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("grainsize", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("graincount", "3");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("gainspreadten", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("grainreadspeed", "1000");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("recpresetlen", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("recquant", "3");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("recinput", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("recusethres", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("recthresh", "-20000");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("recmonoutbus", "0");
    }

    private ToggleGroup interpolationQualityGroup;
    private boolean     isInterpolationQualityHigh;
    private CheckBox    resampleTo2448;
    private CheckBox    trimStartToEnd;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Music1010Creator (final INotifier notifier)
    {
        super ("1010music", notifier);

        this.configureWavChunkUpdates (true, true, true, true);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_1010_MUSIC_INTER_QUALITY");

        this.interpolationQualityGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_1010_MUSIC_INTERPOLATION_QUALITY_NORMAL");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_1010_MUSIC_INTER_QUALITY"));
        order1.setToggleGroup (this.interpolationQualityGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_1010_MUSIC_INTERPOLATION_QUALITY_HIGH");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_1010_MUSIC_INTER_QUALITY"));
        order2.setToggleGroup (this.interpolationQualityGroup);

        this.resampleTo2448 = panel.createCheckBox ("@IDS_1010_MUSIC_CONVERT_TO_24_48");
        this.trimStartToEnd = panel.createCheckBox ("@IDS_1010_MUSIC_TRIM_START_TO_END");

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.interpolationQualityGroup.selectToggle (this.interpolationQualityGroup.getToggles ().get (config.getBoolean (MUSIC_1010_INTERPOLATION_QUALITY, false) ? 1 : 0));
        this.resampleTo2448.setSelected (config.getBoolean (MUSIC_1010_RESAMPLE_TO_24_48, true));
        this.trimStartToEnd.setSelected (config.getBoolean (MUSIC_1010_TRIM_START_TO_END, true));

        this.loadWavChunkSettings (config, "Music1010");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (MUSIC_1010_INTERPOLATION_QUALITY, this.isHighInterpolationQuality ());
        config.setBoolean (MUSIC_1010_RESAMPLE_TO_24_48, this.resampleTo2448.isSelected ());
        config.setBoolean (MUSIC_1010_TRIM_START_TO_END, this.trimStartToEnd.isSelected ());

        this.saveWavChunkSettings (config, "Music1010");
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPerformance (final File destinationFolder, final IPerformanceSource performanceSource) throws IOException
    {
        final String performanceFolder = createSafeFilename (performanceSource.getName ());
        final File folder = this.createUniqueFilename (destinationFolder, performanceFolder, "");
        if (!folder.exists () && !folder.mkdir ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", folder.getAbsolutePath ());
            return;
        }

        final boolean resample = this.resampleTo2448.isSelected ();
        final boolean trim = this.trimStartToEnd.isSelected ();
        this.setInterpolationQuality (this.isHighInterpolationQuality ());

        // Create 1 preset which contains up to 16 multi-samples as well as individual presets for
        // each multi-sample in sub-folders

        // A preset has a maximum of 16 slots!
        List<IInstrumentSource> instrumentSources = performanceSource.getInstruments ();
        if (instrumentSources.size () > 16)
            instrumentSources = performanceSource.getInstruments ().subList (0, 16);

        for (final IInstrumentSource instrumentSource: instrumentSources)
            instrumentSource.clipKeyRange ();

        // Create the overall performance preset
        final Optional<String> xmlCode = this.createPreset (instrumentSources, trim, performanceFolder + "\\");
        if (xmlCode.isPresent ())
        {
            final File performanceFile = new File (folder, "preset.xml");
            this.notifier.log ("IDS_NOTIFY_STORING", performanceFile.getAbsolutePath ());
            storePreset (performanceFile, xmlCode.get ());

            // Add the workaround silent sample...
            final byte [] silentSample = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/Silence24bit48kHz.wav");
            final File silentSampleFile = new File (folder, "Silence24bit48kHz.wav");
            Files.write (silentSampleFile.toPath (), silentSample);
        }

        // Create all samples in their sub-folders
        for (final IInstrumentSource instrumentSource: instrumentSources)
        {
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();

            final String multisampleName = createSafeFilename (multisampleSource.getName ());
            final File presetFolder = this.createUniqueFilename (folder, multisampleName, "");
            if (!presetFolder.mkdir ())
            {
                this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", presetFolder.getAbsolutePath ());
                continue;
            }

            // Store all samples
            if (resample)
                recalculateSamplePositions (multisampleSource, 48000);
            this.writeSamples (presetFolder, multisampleSource, resample ? OPTIMIZED_AUDIO_FORMAT : DEFEAULT_AUDIO_FORMAT, trim);
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final boolean resample = this.resampleTo2448.isSelected ();
        final boolean trim = this.trimStartToEnd.isSelected ();

        this.setInterpolationQuality (this.isHighInterpolationQuality ());

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File presetFolder = this.createUniqueFilename (destinationFolder, sampleName, "");
        if (!presetFolder.mkdir ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", presetFolder.getAbsolutePath ());
            return;
        }

        final IInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSource, 0);
        final Optional<String> metadata = this.createPreset (Collections.singletonList (instrumentSource), trim, "");
        if (metadata.isEmpty ())
            return;

        final File presetFile = new File (presetFolder, "preset.xml");
        this.notifier.log ("IDS_NOTIFY_STORING", presetFile.getAbsolutePath ());
        storePreset (presetFile, metadata.get ());

        // Store all samples
        if (resample)
            recalculateSamplePositions (multisampleSource, 48000);
        this.writeSamples (presetFolder, multisampleSource, resample ? OPTIMIZED_AUDIO_FORMAT : DEFEAULT_AUDIO_FORMAT, trim);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a preset file.
     *
     * @param multiFile The file to store the preset
     * @param metadata The preset metadata description file
     * @throws IOException Could not store the file
     */
    private static void storePreset (final File multiFile, final String metadata) throws IOException
    {
        Files.writeString (multiFile.toPath (), metadata);
    }


    /**
     * Create the text of the description file with 1 preset.
     *
     * @param instrumentSources The up to 16 instrument sources to add to the preset
     * @param trim Trim to start/end if true
     * @param subFolder The sub-folder inside of the Presets folder to write to
     * @return The XML structure
     * @throws IOException Could not combine split-stereo files
     */
    private Optional<String> createPreset (final List<IInstrumentSource> instrumentSources, final boolean trim, final String subFolder) throws IOException
    {
        final Pair<Document, Element> sessionDocument = createSessionDocument ();
        if (sessionDocument == null)
            return Optional.empty ();
        final Document document = sessionDocument.getKey ();
        final Element sessionElement = sessionDocument.getValue ();

        // No metadata at all -> can optionally be written to BEXT chunk

        final int numInstruments = instrumentSources.size ();
        final List<Element> activeSlots = this.createSlots (document, sessionElement, numInstruments);
        int sampleIndex = 0;
        for (int i = 0; i < numInstruments; i++)
        {
            final IInstrumentSource instrumentSource = instrumentSources.get (i);
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();
            this.notifier.log ("IDS_1010_MUSIC_ADDING_INSTRUMENT", multisampleSource.getName ());
            final List<IGroup> groups = this.combineSplitStereo (multisampleSource);
            multisampleSource.setGroups (groups);

            final Element slotElement = activeSlots.get (i);
            final String presetPath = "\\Presets\\" + subFolder + multisampleSource.getName ();
            slotElement.setAttribute (Music1010Tag.ATTR_FILENAME, presetPath);
            // MIDI-channel 1 acts as the OMNI mode (0 = Off) -> offset all other channels by 1,
            // channel 16 will be turned off
            final int midiChannel = (instrumentSource.getMidiChannel () + 2) % 17;
            XMLUtils.getChildElementByName (slotElement, Music1010Tag.PARAMS).setAttribute (Music1010Tag.ATTR_MIDI_MODE, Integer.toString (midiChannel));

            createModulators (document, slotElement, multisampleSource);

            // Add all groups
            int lowestKey = 127;
            int highestKey = 0;
            for (final IGroup group: groups)
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    createSample (document, presetPath, sessionElement, zone, sampleIndex, i, trim);
                    if (zone.getKeyLow () < lowestKey)
                        lowestKey = zone.getKeyLow ();
                    if (zone.getKeyHigh () > highestKey)
                        highestKey = zone.getKeyHigh ();
                    sampleIndex++;
                }

            // Add workaround to have silence outside of the range...
            final ISampleZone silentZone = new DefaultSampleZone ();
            silentZone.setName ("Silence24bit48kHz");
            if (lowestKey > 0)
            {
                silentZone.setKeyLow (0);
                silentZone.setKeyHigh (lowestKey - 1);
                createSample (document, presetPath, sessionElement, silentZone, sampleIndex, i, false);
                sampleIndex++;
            }
            if (highestKey < 127)
            {
                silentZone.setKeyLow (highestKey + 1);
                silentZone.setKeyHigh (127);
                createSample (document, presetPath, sessionElement, silentZone, sampleIndex, i, false);
                sampleIndex++;
            }

            final Element paramsElement = XMLUtils.getChildElementByName (slotElement, Music1010Tag.PARAMS);

            // Add amplitude envelope
            if (!groups.isEmpty ())
            {
                final ISampleZone zone = groups.get (0).getSampleZones ().get (0);
                final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();

                final double sustainVal = amplitudeEnvelope.getSustainLevel ();
                final int sustain = sustainVal < 0 ? 1000 : (int) Math.round (sustainVal * 1000.0);

                paramsElement.setAttribute (Music1010Tag.ATTR_AMPEG_ATTACK, MathUtils.normalizeTimeFormattedAsInt (amplitudeEnvelope.getAttackTime (), 9.0));
                paramsElement.setAttribute (Music1010Tag.ATTR_AMPEG_DECAY, MathUtils.normalizeTimeFormattedAsInt (Math.max (0, amplitudeEnvelope.getHoldTime ()) + Math.max (0, amplitudeEnvelope.getDecayTime ()), 25.0));
                paramsElement.setAttribute (Music1010Tag.ATTR_AMPEG_RELEASE, MathUtils.normalizeTimeFormattedAsInt (amplitudeEnvelope.getReleaseTime (), 25.0));
                paramsElement.setAttribute (Music1010Tag.ATTR_AMPEG_SUSTAIN, Integer.toString (sustain));
            }

            createEffects (document, paramsElement, multisampleSource);
        }

        return this.createXMLString (document);
    }


    private static void createModulators (final Document document, final Element firstSlot, final IMultisampleSource multisampleSource)
    {
        createModulator (document, firstSlot, "lfo1", "pitch", 128);
        createModulator (document, firstSlot, "modwheel", "lfoamount", 328);

        final Optional<Double> globalAmplitudeVelocity = multisampleSource.getGlobalAmplitudeVelocity ();
        if (globalAmplitudeVelocity.isPresent ())
        {
            final double depth = globalAmplitudeVelocity.get ().doubleValue ();
            createModulator (document, firstSlot, "velocity", "gaindb", (int) Math.round (MathUtils.denormalize (depth, -1000, 1000)));
        }

        final Optional<IFilter> globalFilter = multisampleSource.getGlobalFilter ();
        if (globalFilter.isPresent ())
        {
            final double depth = globalFilter.get ().getCutoffVelocityModulator ().getDepth ();
            createModulator (document, firstSlot, "velocity", "dualfilcutoff", (int) Math.round (MathUtils.denormalize (depth, -1000, 1000)));
        }
    }


    private static void createModulator (final Document document, final Element firstSlot, final String source, final String destination, final int modAmount)
    {
        final Element modSourceElement = XMLUtils.addElement (document, firstSlot, Music1010Tag.MOD_SOURCE);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_DESTINATION, destination);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_SOURCE, source);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_SLOT, "0");
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_AMOUNT, Integer.toString (modAmount));
    }


    /**
     * Create 16 slot cells, multi-sample goes into slot 1.
     *
     * @param document The document
     * @param sessionElement The session element to which to add the slots
     * @param numActiveSlots The number of active slots, maximum 16
     * @return The active slot elements
     */
    private List<Element> createSlots (final Document document, final Element sessionElement, final int numActiveSlots)
    {
        List<Element> activeSlotElements = new ArrayList<> ();
        for (int slot = 0; slot < 16; slot++)
        {
            final boolean isActive = slot < numActiveSlots;
            final Element slotElement = createSlot (document, slot, isActive);
            sessionElement.appendChild (slotElement);
            if (isActive)
                activeSlotElements.add (slotElement);
        }
        return activeSlotElements;
    }


    private Element createSlot (final Document document, final int slot, final boolean isActive)
    {
        final Element cellElement = document.createElement (Music1010Tag.CELL);
        cellElement.setAttribute (Music1010Tag.ATTR_ROW, Integer.toString (slot / 4));
        cellElement.setAttribute (Music1010Tag.ATTR_COLUMN, Integer.toString (slot % 4));
        cellElement.setAttribute (Music1010Tag.ATTR_LAYER, Integer.toString (0));
        cellElement.setAttribute (Music1010Tag.ATTR_FILENAME, "");

        final Element paramsElement = XMLUtils.addElement (document, cellElement, Music1010Tag.PARAMS);
        cellElement.setAttribute (Music1010Tag.ATTR_TYPE, isActive ? "sample" : "samtempl");
        for (final Map.Entry<String, String> entry: isActive ? MULTISAMPLE_PARAM_ATTRIBUTES.entrySet () : EMPTY_PARAM_ATTRIBUTES.entrySet ())
            paramsElement.setAttribute (entry.getKey (), entry.getValue ());

        paramsElement.setAttribute (Music1010Tag.ATTR_INTERPOLATION_QUALITY, this.isInterpolationQualityHigh ? "1" : "0");

        return cellElement;
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param presetPath The offset path to use
     * @param groupElement The element where to add the sample information
     * @param zone Where to get the sample info from
     * @param sampleIndex The index of the sample
     * @param slot The slot to which the sample belongs
     * @param trim Trim to start/end if true
     */
    private static void createSample (final Document document, final String presetPath, final Element groupElement, final ISampleZone zone, final int sampleIndex, final int slot, final boolean trim)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element cellElement = XMLUtils.addElement (document, groupElement, Music1010Tag.CELL);
        final String filename = zone.getName () + ".wav";
        cellElement.setAttribute (Music1010Tag.ATTR_FILENAME, presetPath + "\\" + filename);
        cellElement.setAttribute (Music1010Tag.ATTR_ROW, Integer.toString (sampleIndex));
        cellElement.setAttribute (Music1010Tag.ATTR_TYPE, "asset");

        final Element paramsElement = XMLUtils.addElement (document, cellElement, Music1010Tag.PARAMS);
        paramsElement.setAttribute (Music1010Tag.ATTR_ASSET_SOURCE_ROW, Integer.toString (slot / 4));
        paramsElement.setAttribute (Music1010Tag.ATTR_ASSET_SOURCE_COLUMN, Integer.toString (slot % 4));

        // Stored in WAV file: zone.getGain (), zone.getTune ()

        // Music1010Tag.ATTR_SAMPLE_START is not supported for multi-samples! Therefore, the sample
        // needs to be truncated instead!
        int stop = zone.getStop ();
        if (stop > 0)
        {
            if (trim)
                stop -= zone.getStart ();
            XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_LENGTH, stop);
        }

        // No zone.getTrigger ();

        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_REVERSE, zone.isReversed () ? 1 : 0);

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        final int keyLow = limitToDefault (zone.getKeyLow (), 0);
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_ROOT_NOTE, limitToDefault (zone.getKeyRoot (), keyLow));
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LO_NOTE, keyLow);
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_HI_NOTE, limitToDefault (zone.getKeyHigh (), 127));
        // No fades info.getNoteCrossfadeLow ()
        // No fades info.getNoteCrossfadeHigh ()
        // No zone.getKeyTracking ()
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LO_VEL, limitToDefault (zone.getVelocityLow (), 1));
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_HI_VEL, limitToDefault (zone.getVelocityHigh (), 127));
        // No fades info.getVelocityCrossfadeLow ()
        // No fades info.getVelocityCrossfadeHigh ()

        /////////////////////////////////////////////////////
        // Loops

        // ... are stored in the WAV files

        // Set to one-shot if there are no loops
        if (zone.getLoops ().isEmpty ())
            XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_TRIGGER_TYPE, 0);
    }


    /**
     * Set the interpolation quality to high or normal.
     *
     * @param isInterpolationQualityHigh True to set the interpolation quality to high
     */
    public void setInterpolationQuality (final boolean isInterpolationQualityHigh)
    {
        this.isInterpolationQualityHigh = isInterpolationQualityHigh;
    }


    /**
     * Check if the toggle setting is set to high interpolation quality.
     *
     * @return True if high quality
     */
    private boolean isHighInterpolationQuality ()
    {
        return this.interpolationQualityGroup.getToggles ().get (1).isSelected ();
    }


    /**
     * Creates the filter effect elements.
     *
     * @param document The XML document
     * @param paramsElement Where to add the effect elements
     * @param multisampleSource The multi-sample
     */
    private static void createEffects (final Document document, final Element paramsElement, final IMultisampleSource multisampleSource)
    {
        final Optional<IFilter> optFilter = multisampleSource.getGlobalFilter ();
        if (optFilter.isEmpty ())
            return;

        final IFilter filter = optFilter.get ();
        final FilterType type = filter.getType ();
        if (type != FilterType.LOW_PASS && type != FilterType.HIGH_PASS)
            return;

        // Negative values for frequency represent a low-pass filter, positive values a high-pass.
        // Note: no poles supported
        final double normalizedFrequency = MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY);
        int frequency = (int) (normalizedFrequency * 1000.0);
        if (type == FilterType.LOW_PASS)
            frequency -= 1000;
        paramsElement.setAttribute (Music1010Tag.ATTR_FILTER_CUTOFF, Integer.toString (frequency));

        // Note: Resonance is in the range [0..1] but it is not documented what value 1
        // represents. Therefore, we assume 40dB maximum and a linear range (could also
        // be logarithmic).
        final int resonance = (int) (filter.getResonance () * 1000.0);
        paramsElement.setAttribute (Music1010Tag.ATTR_FILTER_RESONANCE, Integer.toString (resonance));
    }


    /**
     * Create the basic structure for a 1010music session document.
     * 
     * @return The document and the session element
     */
    private Pair<Document, Element> createSessionDocument ()
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return null;
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement (Music1010Tag.ROOT);
        document.appendChild (rootElement);
        final Element sessionElement = XMLUtils.addElement (document, rootElement, Music1010Tag.SESSION);
        sessionElement.setAttribute (Music1010Tag.ATTR_VERSION, "2");

        return new Pair<> (document, sessionElement);
    }
}