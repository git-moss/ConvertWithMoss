// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010.bento;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.format.music1010.Music1010CreatorUI;
import de.mossgrabers.convertwithmoss.format.music1010.Music1010Tag;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for patch files of the 1010music Bento. A patch has a description file encoded in XML
 * located in the Presets folder. The related samples are in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class BentoCreator extends AbstractWavCreator<Music1010CreatorUI>
{
    private static final String                 PATCHES_FOLDER               = "UserPatches\\SampInst\\";
    private static final int                    MAX_INSTRUMENTS              = 8;
    private static final DestinationAudioFormat OPTIMIZED_AUDIO_FORMAT       = new DestinationAudioFormat (new int []
    {
        24
    }, 48000, true);
    private static final DestinationAudioFormat DEFEAULT_AUDIO_FORMAT        = new DestinationAudioFormat ();

    private static final Map<String, String>    TRACK_PARAM_ATTRIBUTES       = new HashMap<> ();
    private static final Map<String, String>    MULTISAMPLE_PARAM_ATTRIBUTES = new HashMap<> ();
    static
    {
        TRACK_PARAM_ATTRIBUTES.put ("selcellpos", "0");
        TRACK_PARAM_ATTRIBUTES.put ("celldisppos", "0");
        TRACK_PARAM_ATTRIBUTES.put ("cellname", "Track 1");
        TRACK_PARAM_ATTRIBUTES.put ("selseqpos", "0");
        TRACK_PARAM_ATTRIBUTES.put ("out3gain", "6000");
        TRACK_PARAM_ATTRIBUTES.put ("fx1send", "0");
        TRACK_PARAM_ATTRIBUTES.put ("fx2send", "0");
        TRACK_PARAM_ATTRIBUTES.put ("outputbus", "0");
        TRACK_PARAM_ATTRIBUTES.put ("midiinport", "0");
        TRACK_PARAM_ATTRIBUTES.put ("midiinchan", "0");
        TRACK_PARAM_ATTRIBUTES.put ("cc1inport", "0");
        TRACK_PARAM_ATTRIBUTES.put ("cc1inchan", "0");
        TRACK_PARAM_ATTRIBUTES.put ("cc2inport", "0");
        TRACK_PARAM_ATTRIBUTES.put ("midioutport", "0");
        TRACK_PARAM_ATTRIBUTES.put ("midioutchan", "0");
        TRACK_PARAM_ATTRIBUTES.put ("padrowoffset", "1");

        MULTISAMPLE_PARAM_ATTRIBUTES.put ("gaindb", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("pitch", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("panpos", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("cellmode", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("samtrigtype", "1");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("loopmodes", "1");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("loopfadeamt", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("reverse", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("velamount", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("outputbus", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("polymode", "5");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("chokegrp", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("dualfilcutoff", "-226");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("res", "302");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("fx1send", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("fx2send", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("overdrive", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("multisammode", "1");

        // Setup for modulation wheel!
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lfowave", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lforate", "845");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lfoamount", "0");

        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lfokeytrig", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lfobeatsync", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("lforatebeatsync", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("legatomode", "0");
        MULTISAMPLE_PARAM_ATTRIBUTES.put ("celldisppos", "0");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public BentoCreator (final INotifier notifier)
    {
        super ("1010music bento", "Bento", notifier, new Music1010CreatorUI ("Bento"));
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
        final String performanceFolderName = createSafeFilename (performanceSource.getName ());
        final File patchesFolder = new File (destinationFolder, PATCHES_FOLDER);
        final File presetFolder = new File (patchesFolder, performanceFolderName);
        final File performanceFolder = this.createUniqueFilename (new File (destinationFolder, "Projects"), performanceFolderName, "");
        if (!performanceFolder.exists () && !performanceFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", performanceFolder.getAbsolutePath ());
            return;
        }

        final boolean resample = this.settingsConfiguration.resampleTo2448 ();
        final boolean trim = this.settingsConfiguration.trimStartToEnd ();

        // Create 1 preset which contains up to 8 multi-samples (a project has a maximum of 8
        // tracks) as well as individual presets for each multi-sample in sub-folders
        List<IInstrumentSource> instrumentSources = performanceSource.getInstruments ();
        final int numInstruments = instrumentSources.size ();
        if (numInstruments > MAX_INSTRUMENTS)
        {
            instrumentSources = instrumentSources.subList (0, MAX_INSTRUMENTS);
            this.notifier.logError ("IDS_ERR_LIMITED_INSTRUMENTS", Integer.toString (MAX_INSTRUMENTS), Integer.toString (numInstruments));
        }

        for (final IInstrumentSource instrumentSource: instrumentSources)
            instrumentSource.clipKeyRange ();

        // Create the overall performance preset
        final Optional<String> xmlCode = this.createPreset (instrumentSources, trim, PATCHES_FOLDER + performanceFolderName + "\\", true);
        if (xmlCode.isPresent ())
        {
            final File performanceFile = new File (performanceFolder, "project.xml");
            this.notifier.log ("IDS_NOTIFY_STORING", performanceFile.getAbsolutePath ());
            storePreset (performanceFile, xmlCode.get ());

            // Add the workaround silent sample...
            final byte [] silentSample = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/Silence24bit48kHz.wav");
            final File silentSampleFile = new File (patchesFolder, "Silence24bit48kHz.wav");
            if (!patchesFolder.exists () && !patchesFolder.mkdirs ())
            {
                this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", patchesFolder.getAbsolutePath ());
                return;
            }
            Files.write (silentSampleFile.toPath (), silentSample);
        }

        // Create all samples in their sub-folders
        for (final IInstrumentSource instrumentSource: instrumentSources)
        {
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();

            final String multisampleName = createSafeFilename (multisampleSource.getName ());
            final File fullPresetFolder = this.createUniqueFilename (presetFolder, multisampleName, "");
            if (!fullPresetFolder.mkdirs ())
            {
                this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", fullPresetFolder.getAbsolutePath ());
                continue;
            }

            // Store all samples
            if (resample)
                recalculateSamplePositions (multisampleSource, 48000);
            this.writeSamples (fullPresetFolder, multisampleSource, resample ? OPTIMIZED_AUDIO_FORMAT : DEFEAULT_AUDIO_FORMAT, trim);
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final boolean resample = this.settingsConfiguration.resampleTo2448 ();
        final boolean trim = this.settingsConfiguration.trimStartToEnd ();

        final String multisampleName = createSafeFilename (multisampleSource.getName ());
        final File presetFolder = this.createUniqueFilename (destinationFolder, multisampleName, "");
        if (!presetFolder.mkdir ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", presetFolder.getAbsolutePath ());
            return;
        }

        final IInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSource, 0);
        final Optional<String> metadata = this.createPreset (Collections.singletonList (instrumentSource), trim, "", false);
        if (metadata.isEmpty ())
            return;

        final File presetFile = new File (presetFolder, "patch.xml");
        this.notifier.log ("IDS_NOTIFY_STORING", presetFile.getAbsolutePath ());
        storePreset (presetFile, metadata.get ());

        // Store all samples
        if (resample)
            recalculateSamplePositions (multisampleSource, 48000);
        final List<File> samplesFiles = this.writeSamples (presetFolder, multisampleSource, resample ? OPTIMIZED_AUDIO_FORMAT : DEFEAULT_AUDIO_FORMAT, trim);
        // Store one of the samples as the preview sample
        if (!samplesFiles.isEmpty ())
        {
            final File source = samplesFiles.get (samplesFiles.size () / 2);
            Files.copy (source.toPath (), new File (source.getParentFile (), "preview.wav").toPath ());
        }

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
     * @param isPerformance True if the preset is part of a performance
     * @return The XML structure
     * @throws IOException Could not combine split-stereo files
     */
    private Optional<String> createPreset (final List<IInstrumentSource> instrumentSources, final boolean trim, final String subFolder, final boolean isPerformance) throws IOException
    {
        final Pair<Document, Element> sessionDocument = this.createSessionDocument ();
        if (sessionDocument == null)
            return Optional.empty ();
        final Document document = sessionDocument.getKey ();
        final Element sessionElement = sessionDocument.getValue ();

        // No metadata at all -> can optionally be written to BEXT chunk

        final int numInstruments = instrumentSources.size ();
        for (int i = 0; i < numInstruments; i++)
        {
            final IInstrumentSource instrumentSource = instrumentSources.get (i);
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();
            this.notifier.log ("IDS_1010_MUSIC_ADDING_INSTRUMENT", multisampleSource.getName ());
            final List<IGroup> groups = this.combineSplitStereo (multisampleSource);
            multisampleSource.setGroups (groups);

            final Element trackElement = XMLUtils.addElement (document, sessionElement, Music1010Tag.TRACK);
            trackElement.setAttribute (Music1010Tag.ATTR_TYPE, "multisamtrack");
            final Element trackParamsElement = XMLUtils.addElement (document, trackElement, Music1010Tag.PARAMS);
            for (final Map.Entry<String, String> entry: TRACK_PARAM_ATTRIBUTES.entrySet ())
                trackParamsElement.setAttribute (entry.getKey (), entry.getValue ());
            // MIDI input channel: 0 = Off, 1..16
            final int midiChannel = instrumentSource.getMidiChannel () % 16 + 1;
            trackParamsElement.setAttribute (Music1010Tag.ATTR_MIDI_INPUT_CHANNEL, Integer.toString (midiChannel));

            trackParamsElement.setAttribute (Music1010Tag.ATTR_CELLNAME, multisampleSource.getName ());

            final Element instElement = XMLUtils.addElement (document, trackElement, Music1010Tag.CELL);
            instElement.setAttribute (Music1010Tag.ATTR_TYPE, "saminst");

            createModulators (document, instElement, multisampleSource);

            // Create sample entries for all sample zones of all groups, adds workaround for silence
            // outside of range
            if (isPerformance)
            {
                final int lowestKey = multisampleSource.getLowestKey ();
                if (lowestKey > 0)
                {
                    final ISampleZone silentZone = new DefaultSampleZone ("Silence24bit48kHz", 0, lowestKey - 1);
                    this.createSample (document, PATCHES_FOLDER, trackElement, silentZone, false);
                }
            }
            for (final IGroup group: groups)
                for (final ISampleZone zone: group.getSampleZones ())
                    this.createSample (document, isPerformance ? subFolder + createSafeFilename (multisampleSource.getName ()) + "\\" : "", trackElement, zone, trim);
            if (isPerformance)
            {
                final int highestKey = multisampleSource.getHighestKey ();
                if (highestKey < 127)
                {
                    final ISampleZone silentZone = new DefaultSampleZone ("Silence24bit48kHz", highestKey + 1, 127);
                    this.createSample (document, PATCHES_FOLDER, trackElement, silentZone, false);
                }
            }

            final Element paramsElement = XMLUtils.addElement (document, instElement, Music1010Tag.PARAMS);
            for (final Map.Entry<String, String> entry: MULTISAMPLE_PARAM_ATTRIBUTES.entrySet ())
                paramsElement.setAttribute (entry.getKey (), entry.getValue ());
            paramsElement.setAttribute (Music1010Tag.ATTR_INTERPOLATION_QUALITY, this.settingsConfiguration.isInterpolationQualityHigh () ? "1" : "0");

            // Add amplitude envelope
            if (!groups.isEmpty ())
            {
                final ISampleZone zone = groups.get (0).getSampleZones ().get (0);
                final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();

                final double sustainVal = amplitudeEnvelope.getSustainLevel ();
                final int sustain = sustainVal < 0 ? 1000 : (int) Math.round (sustainVal * 1000.0);

                paramsElement.setAttribute (Music1010Tag.ATTR_AMPEG_ATTACK, MathUtils.normalizeTimeFormattedAsInt (Math.max (0, amplitudeEnvelope.getAttackTime ()), 9.0));
                paramsElement.setAttribute (Music1010Tag.ATTR_AMPEG_DECAY, MathUtils.normalizeTimeFormattedAsInt (Math.max (0, amplitudeEnvelope.getHoldTime ()) + Math.max (0, amplitudeEnvelope.getDecayTime ()), 25.0));
                paramsElement.setAttribute (Music1010Tag.ATTR_AMPEG_RELEASE, MathUtils.normalizeTimeFormattedAsInt (Math.max (0, amplitudeEnvelope.getReleaseTime ()), 25.0));
                paramsElement.setAttribute (Music1010Tag.ATTR_AMPEG_SUSTAIN, Integer.toString (sustain));
            }

            // Loop settings seem to be only available globally...
            final Set<String> loopModes = new HashSet<> ();
            final Set<String> loopFadeAmounts = new HashSet<> ();
            final Set<String> loopReverses = new HashSet<> ();
            for (final IGroup group: groups)
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    loopReverses.add (zone.isReversed () ? "1" : "0");
                    final List<ISampleLoop> loops = zone.getLoops ();
                    if (loops.isEmpty ())
                    {
                        loopModes.add ("0");
                        loopFadeAmounts.add ("0");
                    }
                    else
                    {
                        final ISampleLoop loop = loops.get (0);
                        loopModes.add (loop.getType () == LoopType.ALTERNATING ? "2" : "1");
                        loopFadeAmounts.add (Integer.toString ((int) Math.round (loop.getCrossfade () * 1000.0)));
                    }
                }
            if (loopModes.size () == 1)
                paramsElement.setAttribute (Music1010Tag.ATTR_LOOP_MODE, loopModes.iterator ().next ());
            if (loopFadeAmounts.size () == 1)
                paramsElement.setAttribute (Music1010Tag.ATTR_LOOP_FADE_AMOUNT, loopFadeAmounts.iterator ().next ());
            if (loopReverses.size () == 1)
                paramsElement.setAttribute (Music1010Tag.ATTR_REVERSE, loopReverses.iterator ().next ());

            createEffects (document, paramsElement, multisampleSource);
        }

        return this.createXMLString (document);
    }


    private static void createModulators (final Document document, final Element instElement, final IMultisampleSource multisampleSource)
    {
        createModulator (document, instElement, "lfo1", "pitch", 128);
        createModulator (document, instElement, "modwheel", "lfoamount", 328);

        final Optional<Double> globalAmplitudeVelocity = multisampleSource.getGlobalAmplitudeVelocity ();
        if (globalAmplitudeVelocity.isPresent ())
        {
            final double depth = globalAmplitudeVelocity.get ().doubleValue ();
            createModulator (document, instElement, "velocity", "gaindb", (int) Math.round (MathUtils.denormalize (depth, -1000, 1000)));
        }

        final Optional<IFilter> globalFilter = multisampleSource.getGlobalFilter ();
        if (globalFilter.isPresent ())
        {
            final double depth = globalFilter.get ().getCutoffVelocityModulator ().getDepth ();
            createModulator (document, instElement, "velocity", "dualfilcutoff", (int) Math.round (MathUtils.denormalize (depth, -1000, 1000)));
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
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param presetPath The offset path to use
     * @param groupElement The element where to add the sample information
     * @param zone Where to get the sample info from
     * @param trim Trim to start/end if true
     */
    private void createSample (final Document document, final String presetPath, final Element groupElement, final ISampleZone zone, final boolean trim)
    {
        // Stored in WAV file: zone.getGain (), zone.getTune ()

        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element cellElement = XMLUtils.addElement (document, groupElement, Music1010Tag.CELL);
        cellElement.setAttribute (Music1010Tag.ATTR_TYPE, "samasst");

        final Element paramsElement = XMLUtils.addElement (document, cellElement, Music1010Tag.PARAMS);

        final String filename = this.createSampleFilename (zone, 0, ".wav");
        paramsElement.setAttribute (Music1010Tag.ATTR_FILENAME, presetPath + filename);

        // IMPROVE The following parameters (panning, gain and pitch) are only available on
        // instrument level (1010music needs to implement it on sample level first)
        // Panning: -100..100% -> -1000..1000
        // paramsElement.setAttribute (Music1010Tag.ATTR_PANNING, Integer.toString ((int) Math.clamp
        // (zone.getPanning () * 1000.0, -1000.0, 1000.0)));
        // -12..12dB -> -12000..12000
        // gaindb="4300"
        // -24..24 -> -24000..24000
        // pitch="-19350"
        // panpos="-352"

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        final int keyLow = limitToDefault (zone.getKeyLow (), 0);
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_ROOT_NOTE, limitToDefault (zone.getKeyRoot (), keyLow));
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LO_NOTE, keyLow);
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_HI_NOTE, limitToDefault (zone.getKeyHigh (), 127));
        // No fades info.getNoteCrossfadeLow ()
        // No fades info.getNoteCrossfadeHigh ()
        // No zone.getKeyTracking ()
        final int loVel = limitToDefault (zone.getVelocityLow (), 1);
        final int hiVel = limitToDefault (zone.getVelocityHigh (), 127);
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_ROOT_VEL, (hiVel - loVel) / 2);
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LO_VEL, loVel);
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_HI_VEL, hiVel);
        // No fades info.getVelocityCrossfadeLow ()
        // No fades info.getVelocityCrossfadeHigh ()

        // Music1010Tag.ATTR_SAMPLE_START was not supported for previous format but might work now
        // (did not test). However it is more efficient to truncate it anyway
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_START, 0);
        int stop = zone.getStop ();
        if (stop > 0)
        {
            if (trim)
                stop -= zone.getStart ();
            XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_LENGTH, stop);
        }

        /////////////////////////////////////////////////////
        // Loops

        // Set to one-shot if there are no loops
        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
        {
            XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_START, 0);
            if (stop > 0)
                XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_END, stop);
        }
        else
        {
            final ISampleLoop loop = loops.get (0);
            XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_START, loop.getStart ());
            XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_END, loop.getEnd ());
        }
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
        sessionElement.setAttribute (Music1010Tag.ATTR_VERSION, "1");

        return new Pair<> (document, sessionElement);
    }
}