// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfoModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.exception.ValueNotAvailableException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.aiff.AiffFileSampleData;
import de.mossgrabers.convertwithmoss.file.ncw.NcwFileSampleData;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.DecodedPath;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.KontaktIcon;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2.K2Tag;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Base class for parsing the NKI XML structure (Kontakt 1 + 2).
 *
 * @author Jürgen Moßgraber
 * @author Philip Stolz
 */
public abstract class AbstractNKIMetadataFileHandler
{
    private static final String               LFO_SAWTOOTH              = "sawtooth";
    protected static final String             TEMPLATE_FOLDER           = "de/mossgrabers/convertwithmoss/templates/nki/";
    protected static final String             XML_HEADER                = "<?xml version=\"1.0\"?>\n";

    private static final String               NULL_ENTRY                = "(null)";
    private static final String               PRO52_FILTER_TYPE         = "pro52";
    private static final String               LADDER_FILTER_TYPE        = "ladder";
    private static final Pattern              FILTER_TYPE_PATTERN       = Pattern.compile ("^(lp|hp|bp|br)(\\d+)pole$");

    // Effects which can be placed in the filter slot but which cannot be mapped to a filter
    private static final Pattern              EFFECT_TYPE_PATTERN       = Pattern.compile ("^(eq\\d+band|phaser|versatile|vowel\\w|sampleAndHold)$");
    private static final Pattern              UTF_PATTERN               = Pattern.compile ("%([0-9a-fA-F]{4})");

    // The cutoff frequencies displayed by Kontakt for the normalized cutoff values 0, 0.1, ..., 1.0
    // of the low-pass filters as well as of the high-, band-pass and band-reject filters
    // (independent of the number of poles). The curves cannot be expressed by a simple formula,
    // therefore the values are interpolated logarithmically
    private static final double []            LOW_PASS_CUTOFFS          =
    {
        43.6,
        91.2,
        179.3,
        342.2,
        642.9,
        1200,
        2200,
        4000,
        7200,
        12600,
        21800
    };
    private static final double []            HIGH_PASS_CUTOFFS         =
    {
        36.1,
        75.5,
        148.5,
        283.4,
        532.5,
        990.3,
        1800,
        3300,
        5900,
        10500,
        18100
    };

    // The pitch intensity of Kontakt (1.0) covers 1 octave
    private static final double               PITCH_INTENSITY_CENTS     = 1200.0;

    private static final String               CYCLE_ROUND_ROBIN         = "cycle_round_robin";
    private static final String               CYCLE_RANDOM              = "cycle_random";

    protected final AbstractTagsAndAttributes tags;
    protected final INotifier                 notifier;
    private final Map<String, String>         absolutePathLookupSuccess = new HashMap<> ();
    protected boolean                         isMulti                   = false;
    private boolean                           isAdditiveTuning          = false;


    /**
     * Constructor.
     *
     * @param tags the format specific tags
     * @param notifier Where to report errors
     */
    protected AbstractNKIMetadataFileHandler (final AbstractTagsAndAttributes tags, final INotifier notifier)
    {
        this.tags = tags;
        this.notifier = notifier;
    }


    /**
     * Parses the XML description file for a multi and its instruments.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param xmlCode The XML content to parse
     * @param metadataConfig Default metadata
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return The parsed multi-sample source
     * @throws IOException An error occurred parsing the XML document
     */
    public Optional<IPerformanceSource> parseMulti (final File sourceFolder, final File sourceFile, final String xmlCode, final IMetadataConfig metadataConfig, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        final Element top = this.getTopLevelElement (xmlCode);
        final IPerformanceSource performanceSource = new DefaultPerformanceSource ();
        performanceSource.setName (FileUtils.getNameWithoutType (sourceFile));

        // The volume and panning of the programs are applied to the instruments instead of the
        // zones
        this.isMulti = true;
        try
        {
            final Map<String, String> topParameters = this.readParameters (top);
            performanceSource.getInstruments ().addAll (this.parseInstrumentSources (sourceFolder, sourceFile, top, metadataConfig, monolithSamples, Optional.of (topParameters)));
        }
        finally
        {
            this.isMulti = false;
        }
        return Optional.of (performanceSource);
    }


    /**
     * Parses the XML description file for instruments.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param xmlCode The XML content to parse
     * @param metadataConfig Default metadata
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return The parsed multi-sample source
     * @throws IOException An error occurred parsing the XML document
     */
    public List<IMultisampleSource> parseInstruments (final File sourceFolder, final File sourceFile, final String xmlCode, final IMetadataConfig metadataConfig, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        final Element top = this.getTopLevelElement (xmlCode);
        final List<IInstrumentSource> instrumentSources = this.parseInstrumentSources (sourceFolder, sourceFile, top, metadataConfig, monolithSamples, Optional.empty ());
        final List<IMultisampleSource> multisampleSource = new ArrayList<> (instrumentSources.size ());
        for (final IInstrumentSource instrumentSource: instrumentSources)
            multisampleSource.add (instrumentSource.getMultisampleSource ());
        return multisampleSource;
    }


    /**
     * Parses all programs of the XML description file.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param top The top element of the XML document
     * @param metadataConfig Default metadata
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @param topParameters The parameters of the multi, only present when reading a multi
     * @return The parsed instruments
     * @throws IOException An error occurred parsing the XML document
     */
    private List<IInstrumentSource> parseInstrumentSources (final File sourceFolder, final File sourceFile, final Element top, final IMetadataConfig metadataConfig, final Map<String, ISampleData> monolithSamples, final Optional<Map<String, String>> topParameters) throws IOException
    {
        final List<Element> programElements = this.findProgramElements (top);
        if (programElements.isEmpty ())
            return Collections.emptyList ();

        final String n = metadataConfig.isPreferFolderName () ? sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFolder, n);

        final List<IInstrumentSource> instrumentSources = new ArrayList<> ();
        for (int i = 0; i < programElements.size (); i++)
        {
            final Element programElement = programElements.get (i);
            final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, null);
            final IInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSource, 0);
            if (this.parseProgram (programElement, instrumentSource, monolithSamples))
            {
                updateMetadata (metadataConfig, parts, multisampleSource.getMetadata ());

                // Only K2 multis: the MIDI channel of the slot, 0 = Omni
                if (topParameters.isPresent ())
                {
                    final String midiChannelStr = topParameters.get ().get (String.format ("midiChannel_slot0%02d", Integer.valueOf (getSlotIndex (programElement, i))));
                    if (midiChannelStr != null)
                        instrumentSource.setMidiChannel (convertMidiChannel (Integer.parseInt (midiChannelStr)));
                }

                instrumentSources.add (instrumentSource);
            }
        }
        return instrumentSources;
    }


    /**
     * Get the slot index of a program in a multi. In K2 each program is stored in its own container
     * which contains the index of the slot. Otherwise the position of the program is used.
     *
     * @param programElement The program element
     * @param position The position of the program element in the list of all program elements
     * @return The slot index
     */
    private static int getSlotIndex (final Element programElement, final int position)
    {
        final Node programsNode = programElement.getParentNode ();
        final Node containerNode = programsNode == null ? null : programsNode.getParentNode ();
        if (containerNode instanceof final Element containerElement && containerElement.hasAttribute ("index"))
            try
            {
                return Integer.parseInt (containerElement.getAttribute ("index"));
            }
            catch (final NumberFormatException _)
            {
                // Use the position instead
            }
        return position;
    }


    /**
     * Convert a Kontakt MIDI channel to the MIDI channel of an instrument.
     *
     * @param midiChannel The Kontakt MIDI channel: 0 = Omni, 1-16 = channel 1-16 of port A, 17-64 =
     *            ports B-D
     * @return The MIDI channel of the instrument
     */
    protected static int convertMidiChannel (final int midiChannel)
    {
        return midiChannel <= 0 ? IInstrumentSource.MIDI_CHANNEL_OMNI : (midiChannel - 1) % 16;
    }


    private Element getTopLevelElement (final String content) throws IOException
    {
        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            final Element top = document.getDocumentElement ();
            if (!this.isValidTopLevelElement (top))
                throw new IOException (Functions.getMessage ("IDS_NKI_NOT_A_VALID_TOP_LEVEL_ELEMENT", top.getTagName ()));
            return top;
        }
        catch (final SAXException ex)
        {
            throw new IOException (ex);
        }
    }


    /**
     * Tries to fill empty metadata fields by using some guessing on the filename.
     *
     * @param metadataConfig The configuration
     * @param parts The filename and path parts
     * @param metadata Where to set the found metadata
     */
    public static void updateMetadata (final IMetadataConfig metadataConfig, final String [] parts, final IMetadata metadata)
    {
        final String creator = metadata.getCreator ();
        if (creator == null || creator.isBlank ())
            metadata.setCreator (TagDetector.detect (parts, metadataConfig.getCreatorTags (), metadataConfig.getCreatorName ()));

        final List<String> name = Collections.singletonList (parts[0]);
        final List<String> folderNames = Arrays.asList (AudioFileUtils.getFolderNames (parts));

        final String category = metadata.getCategory ();
        if (category == null || category.isBlank () || "New".equals (category))
            metadata.setCategory (TagDetector.detectCategory (name, folderNames, false));

        final String [] keywords = metadata.getKeywords ();
        if (keywords == null || keywords.length == 0)
            metadata.setKeywords (TagDetector.detectKeywords (name, folderNames));
    }


    /**
     * Creates a metadata description file for a bank (multi) of up to 64 programs.
     *
     * @param bankName The name of the bank
     * @param safeSampleFolderNames The folders where the samples are placed. Must be of the same
     *            size as multisampleSources!
     * @param instrumentSources The instrument sources
     * @return The XML document as a text
     * @throws IOException Could not create the text due to missing templates
     */
    public String createBank (final String bankName, final List<String> safeSampleFolderNames, final List<IInstrumentSource> instrumentSources) throws IOException
    {
        int numInstruments = instrumentSources.size ();
        if (numInstruments > 64)
        {
            this.notifier.logError ("IDS_ERR_LIMITED_INSTRUMENTS", Integer.toString (64), Integer.toString (numInstruments));
            numInstruments = 64;
        }

        final String templatePrefix = this.getTemplatePrefix ();
        final StringBuilder sb = new StringBuilder (XML_HEADER);
        final String header = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_01_Bank_Start.xml");
        sb.append (header.replace ("%BANK_NAME%", bankName));
        for (int i = 0; i < numInstruments; i++)
            sb.append (this.createRawProgram (safeSampleFolderNames.get (i), instrumentSources.get (i), i));
        sb.append (Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_06_Bank_End.xml"));
        return sb.toString ();
    }


    /**
     * Creates a metadata description file for a single program.
     *
     * @param safeSampleFolderName The folder where the samples are placed
     * @param multisampleSource The multi-sample source
     * @param programIndex The index of the program to use (starts with 0)
     * @return The XML document as a text
     * @throws IOException Could not create the text due to missing templates
     */
    public String createProgram (final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int programIndex) throws IOException
    {
        return XML_HEADER + this.createRawProgram (safeSampleFolderName, new DefaultInstrumentSource (multisampleSource, -1), programIndex);
    }


    private String createRawProgram (final String safeSampleFolderName, final IInstrumentSource instrumentSource, final int programIndex) throws IOException
    {
        final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();

        final String templatePrefix = this.getTemplatePrefix ();

        String text = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_01_Program_Start.xml");
        text = text.replace ("%PROGRAM_INDEX%", Integer.toString (programIndex));
        text = text.replace ("%PROGRAM_NAME%", multisampleSource.getName ());
        int midiChannel = instrumentSource.getMidiChannel ();
        if (midiChannel == IInstrumentSource.MIDI_CHANNEL_OFF)
            midiChannel = 16; // B1 since there is no off-state
        text = text.replace ("%PROGRAM_MIDI_CHANNEL%", Integer.toString (midiChannel == IInstrumentSource.MIDI_CHANNEL_OMNI ? 0 : midiChannel + 1));

        text = text.replace ("%MASTER_VOLUME%", formatDouble (Math.pow (10, instrumentSource.getGain () / 20.0d)));
        text = text.replace ("%MASTER_PANNING%", String.format (Locale.US, "%.02f", Double.valueOf ((instrumentSource.getPanning () + 1.0) / 2.0)));
        text = text.replace ("%MASTER_TRANSPOSE%", Integer.toString (instrumentSource.getTranspose ()));
        text = text.replace ("%MASTER_TUNE%", formatDouble (Math.pow (2.0, instrumentSource.getTuning () / 12.0)));
        text = text.replace ("%MASTER_LOW_KEY%", Integer.toString (instrumentSource.getClipKeyLow ()));
        text = text.replace ("%MASTER_HIGH_KEY%", Integer.toString (instrumentSource.getClipKeyHigh ()));

        // Add all groups
        final String result = this.addGroups (templatePrefix, safeSampleFolderName, multisampleSource.getNonEmptyGroups (false));
        return text + result + Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_06_Program_End.xml");
    }


    protected abstract String getTemplatePrefix ();


    protected String addGroups (final String templatePrefix, final String safeSampleFolderName, final List<IGroup> groups) throws IOException
    {
        final String groupTemplate = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_02_Group.xml");
        final String zoneTemplate = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_04_Zone.xml");
        final String loopTemplate = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_05_Loop.xml");
        // Optional templates which are only used if the group template contains the placeholder
        final String lfoTemplate = groupTemplate.contains ("%PITCH_LFO%") ? Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_07_Lfo.xml") : "";
        final String startCriteriaTemplate = groupTemplate.contains ("%GROUP_START_CRITERIA%") ? Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_08_Start_Criteria.xml") : "";

        final StringBuilder groupsText = new StringBuilder ();
        final StringBuilder zonesText = new StringBuilder ();

        // Samples are numbered across all groups!
        int sampleCount = 0;

        for (int groupCount = 0; groupCount < groups.size (); groupCount++)
        {
            final IGroup group = groups.get (groupCount);

            // Set the group index and name
            String name = group.getName ();
            if (name == null || name.isBlank ())
                name = "Group " + (groupCount + 1);
            String groupContent = groupTemplate.replace ("%GROUP_INDEX%", Integer.toString (groupCount)).replace ("%GROUP_NAME%", name);

            final List<ISampleZone> zones = group.getSampleZones ();
            for (final ISampleZone zone: zones)
            {
                String zoneContent = this.addZoneData (zone, safeSampleFolderName, zoneTemplate, sampleCount, groupCount);

                final StringBuilder loopsContent = new StringBuilder ();
                final List<ISampleLoop> loops = zone.getLoops ();
                for (int loopIndex = 0; loopIndex < loops.size (); loopIndex++)
                {
                    final ISampleLoop loop = loops.get (loopIndex);
                    final String loopContent = this.addLoop (loop, loopTemplate, loopIndex);
                    if (loopIndex > 0)
                        loopsContent.append ("\r\n");
                    loopsContent.append (loopContent);
                }
                // A one-shot which has no loop is described by a loop entry with the one-shot mode
                if (loops.isEmpty () && zone.isOneShot ())
                    loopsContent.append (this.addOneShotLoop (loopTemplate));
                zoneContent = zoneContent.replace ("%ZONE_LOOPS%", loopsContent.toString ());
                zonesText.append (zoneContent);

                sampleCount++;
            }

            // These parameters can only be set on the group. This implementation uses the first
            // found
            final ISampleZone firstZone = zones.isEmpty () ? null : zones.get (0);
            final double pitchBendUp = firstZone == null ? 0 : firstZone.getBendUp ();
            final double ampVelocityMod = firstZone == null ? 1 : firstZone.getAmplitudeVelocityModulator ().getDepth ();
            final boolean keyTracking = firstZone == null || firstZone.getKeyTracking () > 0;
            final boolean isReleaseTrigger = firstZone != null && firstZone.getTrigger () == TriggerType.RELEASE;
            // If the amplitude envelope ignores note-off events the sample is always played back to
            // its end, which means it is a one-shot. Only supported by the Kontakt 2 template.
            final boolean isOneShot = firstZone != null && firstZone.isOneShot ();
            final boolean isReverse = firstZone != null && firstZone.isReversed ();
            groupContent = addModulators (firstZone, groupContent);
            groupContent = groupContent.replace ("%PITCH_LFO%", createPitchLfo (firstZone, lfoTemplate));
            groupContent = groupContent.replace ("%GROUP_START_CRITERIA%", createStartCriteria (firstZone, startCriteriaTemplate));
            groupContent = groupContent.replace ("%AMP_VELOCITY_MOD%", formatDouble (ampVelocityMod));
            groupContent = groupContent.replace ("%PITCH_BEND%", formatDouble (pitchBendUp / 1200));
            groupContent = groupContent.replace ("%GROUP_ONE_SHOT%", isOneShot ? "yes" : "no");
            groupContent = groupContent.replace ("%GROUP_KEY_TRACKING%", keyTracking ? "yes" : "no");
            groupContent = groupContent.replace ("%GROUP_RELEASE_TRIGGER%", isReleaseTrigger ? "yes" : "no");
            groupContent = groupContent.replace ("%GROUP_REVERSE%", isReverse ? "yes" : "no");
            // The voice group can only be set on the group, therefore all of its zones need to
            // agree on the exclusive group. The model uses 0 for 'no group' but Kontakt uses a zero
            // based index with -1 for 'no group', therefore the index is shifted by one
            final int exclusiveGroup = getUniformExclusiveGroup (zones);
            groupContent = groupContent.replace ("%GROUP_VOICE_GROUP%", Integer.toString (exclusiveGroup == 0 ? -1 : exclusiveGroup - 1));
            groupsText.append (groupContent);
        }

        return groupsText.toString () + Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_03_Groups_Zones.xml") + zonesText.toString ();
    }


    /**
     * Creates the first start criteria of a group which contains the round-robin or random
     * playback.
     *
     * @param zone The zone from which to take the play logic, might be null
     * @param template The template of a start criteria
     * @return The filled template
     */
    private static String createStartCriteria (final ISampleZone zone, final String template)
    {
        int cycleClass = 0;
        String mode = "always";

        if (zone != null)
            switch (zone.getPlayLogic ())
            {
                case PlayLogic.ROUND_ROBIN:
                    // The cycle class is the zero based position in the round-robin cycle, the
                    // model starts with 1
                    cycleClass = Math.max (0, zone.getSequencePosition () - 1);
                    mode = CYCLE_ROUND_ROBIN;
                    break;

                case PlayLogic.RANDOM:
                    mode = CYCLE_RANDOM;
                    break;

                default:
                case PlayLogic.ALWAYS:
                    break;
            }

        return template.replace ("%START_CRITERIA_MODE%", mode).replace ("%START_CRITERIA_CYCLE_CLASS%", Integer.toString (cycleClass));
    }


    /**
     * Creates an internal modulator for the pitch oscillator (vibrato).
     *
     * @param zone The zone from which to take the oscillator, might be null
     * @param template The template of an oscillator modulator
     * @return The filled template or an empty text if there is no vibrato
     */
    private static String createPitchLfo (final ISampleZone zone, final String template)
    {
        if (zone == null || template.isEmpty ())
            return "";
        final ILfoModulator modulator = zone.getPitchLfoModulator ();
        final ILfo lfo = modulator.getSource ();
        double intensity = modulator.getDepth () * IEnvelope.MAX_ENVELOPE_DEPTH / PITCH_INTENSITY_CENTS;
        if (intensity == 0 || lfo.getRate () <= 0)
            return "";

        final String type = switch (lfo.getWaveform ())
        {
            case TRIANGLE -> "triangle";
            case SQUARE -> "rectangle";
            case RANDOM -> "random";
            // The sawtooth of Kontakt starts at the maximum and decreases
            case SAWTOOTH_DOWN -> LFO_SAWTOOTH;
            case SAWTOOTH_UP -> {
                intensity = -intensity;
                yield LFO_SAWTOOTH;
            }
            default -> "sine";
        };

        String content = template.replace ("%LFO_TYPE%", type);
        content = content.replace ("%LFO_INTENSITY%", formatDouble (Math.clamp (intensity, -1.0, 1.0)));
        content = content.replace ("%LFO_FREQUENCY%", formatDouble (lfo.getRate ()));
        content = content.replace ("%LFO_RETRIGGER%", lfo.isKeySync () ? "yes" : "no");
        // The delay is stored in milliseconds
        return content.replace ("%LFO_DELAY%", formatDouble (Math.max (0, lfo.getDelay ()) * 1000.0));
    }


    /**
     * Get the exclusive group which is shared by all given zones.
     *
     * @param zones The zones to check
     * @return The exclusive group or 0 if there are no zones or they do not all use the same one
     */
    private static int getUniformExclusiveGroup (final List<ISampleZone> zones)
    {
        if (zones.isEmpty ())
            return 0;
        final int exclusiveGroup = zones.get (0).getExclusiveGroup ();
        for (final ISampleZone zone: zones)
            if (zone.getExclusiveGroup () != exclusiveGroup)
                return 0;
        return exclusiveGroup;
    }


    private String addZoneData (final ISampleZone zone, final String safeSampleFolderName, final String zoneTemplate, final int sampleCount, final int groupCount)
    {
        String zoneContent = zoneTemplate.replace ("%GROUP_INDEX%", Integer.toString (groupCount)).replace ("%ZONE_INDEX%", Integer.toString (sampleCount));
        final String zoneName = zone.getName ();
        final int keyLow = limitToDefault (zone.getKeyLow (), 0);

        zoneContent = zoneContent.replace ("%ZONE_SAMPLE_START%", Integer.toString (zone.getStart ()));
        zoneContent = zoneContent.replace ("%ZONE_SAMPLE_END%", Integer.toString (zone.getStop ()));
        zoneContent = zoneContent.replace ("%ZONE_VEL_LOW%", Integer.toString (limitToDefault (zone.getVelocityLow (), 1)));
        zoneContent = zoneContent.replace ("%ZONE_VEL_HIGH%", Integer.toString (limitToDefault (zone.getVelocityHigh (), 127)));
        zoneContent = zoneContent.replace ("%ZONE_KEY_LOW%", Integer.toString (keyLow));
        zoneContent = zoneContent.replace ("%ZONE_KEY_HIGH%", Integer.toString (limitToDefault (zone.getKeyHigh (), 127)));
        zoneContent = zoneContent.replace ("%ZONE_VEL_CROSS_LOW%", Integer.toString (limitToDefault (zone.getVelocityCrossfadeLow (), 0)));
        zoneContent = zoneContent.replace ("%ZONE_VEL_CROSS_HIGH%", Integer.toString (limitToDefault (zone.getVelocityCrossfadeHigh (), 0)));
        zoneContent = zoneContent.replace ("%ZONE_KEY_CROSS_LOW%", Integer.toString (limitToDefault (zone.getNoteCrossfadeLow (), 0)));
        zoneContent = zoneContent.replace ("%ZONE_KEY_CROSS_HIGH%", Integer.toString (limitToDefault (zone.getNoteCrossfadeHigh (), 0)));
        zoneContent = zoneContent.replace ("%ZONE_KEY_ROOT%", Integer.toString (limitToDefault (zone.getKeyRoot (), keyLow)));
        zoneContent = zoneContent.replace ("%ZONE_VOLUME%", formatDouble (Math.pow (10, zone.getGain () / 20.0d)));
        zoneContent = zoneContent.replace ("%ZONE_TUNE%", formatDouble (Math.pow (2.0, zone.getTuning () / 12.0)));
        zoneContent = zoneContent.replace ("%ZONE_PAN%", formatDouble (this.denormalizePanning (zone.getPanning ())));

        // Note: we need to use backward slashes otherwise Kontakt can read but not save the file
        // again!
        final String formattedFilename = new StringBuilder (safeSampleFolderName).append ('\\').append (zoneName).append (".wav").toString ().replace ('/', '\\');
        return zoneContent.replace ("%ZONE_SAMPLE_NAME%", formattedFilename);
    }


    private String addLoop (final ISampleLoop loop, final String loopTemplate, final int loopIndex)
    {
        String loopContent = loopTemplate.replace ("%LOOP_INDEX%", Integer.toString (loopIndex));

        loopContent = loopContent.replace ("%LOOP_START%", Integer.toString (loop.getStart ()));
        // Kontakt counts the frames of the loop while the model stores the inclusive end
        loopContent = loopContent.replace ("%LOOP_LENGTH%", Integer.toString (loop.getLength () + 1));
        loopContent = loopContent.replace ("%LOOP_MODE%", loop.isLoopUntilRelease () ? this.tags.untilReleaseValue () : this.tags.untilEndValue ());
        loopContent = loopContent.replace ("%LOOP_ALTERNATING%", loop.getType () == LoopType.ALTERNATING ? "yes" : "no");
        loopContent = loopContent.replace ("%LOOP_TUNING%", Float.toString ((float) Math.pow (2.0, loop.getTuning () / 12.0)));
        // The cross-fade of the format is a length in samples, not the fraction of the loop
        return loopContent.replace ("%LOOP_XFADE%", Integer.toString (loop.getCrossfadeInSamples ()));
    }


    /**
     * Creates a loop entry which marks the zone as a one-shot, which means that note-off events are
     * ignored and the sample is always played back to its end. The loop positions are not used in
     * that mode.
     *
     * @param loopTemplate The template of a loop
     * @return The filled loop template
     */
    private String addOneShotLoop (final String loopTemplate)
    {
        String loopContent = loopTemplate.replace ("%LOOP_INDEX%", "0");

        loopContent = loopContent.replace ("%LOOP_START%", "0");
        loopContent = loopContent.replace ("%LOOP_LENGTH%", "0");
        loopContent = loopContent.replace ("%LOOP_MODE%", this.tags.oneshotValue ());
        loopContent = loopContent.replace ("%LOOP_ALTERNATING%", "no");
        loopContent = loopContent.replace ("%LOOP_TUNING%", Float.toString (1.0f));
        return loopContent.replace ("%LOOP_XFADE%", "0");
    }


    private static String addModulators (final ISampleZone sampleMetadata, final String groupContentTemplate)
    {
        final IEnvelopeModulator amplitudeModulator = sampleMetadata == null ? new DefaultEnvelopeModulator (1) : sampleMetadata.getAmplitudeEnvelopeModulator ();
        final IEnvelope amplitudeEnvelope = amplitudeModulator.getSource ();
        String groupContent = groupContentTemplate.replace ("%ENVELOPE_INTENSITY%", formatDouble (amplitudeModulator.getDepth ()));
        groupContent = groupContent.replace ("%ENVELOPE_ATTACK_CURVE%", formatDouble (-amplitudeEnvelope.getAttackSlope ()));
        groupContent = groupContent.replace ("%ENVELOPE_ATTACK%", formatDouble (limitToDefault (amplitudeEnvelope.getAttackTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%ENVELOPE_DECAY%", formatDouble (limitToDefault (amplitudeEnvelope.getDecayTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%ENVELOPE_HOLD%", formatDouble (limitToDefault (amplitudeEnvelope.getHoldTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%ENVELOPE_RELEASE%", formatDouble (limitToDefault (amplitudeEnvelope.getReleaseTime (), 1) * 1000.0d));
        groupContent = groupContent.replace ("%ENVELOPE_SUSTAIN%", formatDouble (limitToDefault (amplitudeEnvelope.getSustainLevel (), 1)));

        final IEnvelopeModulator pitchModulator = sampleMetadata == null ? new DefaultEnvelopeModulator (0) : sampleMetadata.getPitchEnvelopeModulator ();
        final IEnvelope pitchEnvelope = pitchModulator.getSource ();
        // The depth of the model is relative to MAX_ENVELOPE_DEPTH cents
        final double pitchIntensity = pitchModulator.getDepth () * IEnvelope.MAX_ENVELOPE_DEPTH / PITCH_INTENSITY_CENTS;
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_INTENSITY%", formatDouble (Math.clamp (pitchIntensity, -1.0, 1.0)));
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_ATTACK_CURVE%", formatDouble (-pitchEnvelope.getAttackSlope ()));

        // If the start level is set and not 0 (which is normally the case for a pitch envelope),
        // we assume it to be the attack level and then only use the decay value, since this is the
        // most common application for pitch envelopes. A negative value means 'not set'
        if (pitchEnvelope.getStartLevel () > 0)
        {
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_ATTACK%", formatDouble (0));
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_DECAY%", formatDouble (limitToDefault (pitchEnvelope.getAttackTime (), 0) * 1000.0d));
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_HOLD%", formatDouble (0));
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_RELEASE%", formatDouble (0));
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_SUSTAIN%", formatDouble (0));
        }
        else
        {
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_ATTACK%", formatDouble (limitToDefault (pitchEnvelope.getAttackTime (), 0) * 1000.0d));
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_DECAY%", formatDouble (limitToDefault (pitchEnvelope.getDecayTime (), 0) * 1000.0d));
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_HOLD%", formatDouble (limitToDefault (pitchEnvelope.getHoldTime (), 0) * 1000.0d));
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_RELEASE%", formatDouble (limitToDefault (pitchEnvelope.getReleaseTime (), 0) * 1000.0d));
            groupContent = groupContent.replace ("%PITCH_ENVELOPE_SUSTAIN%", formatDouble (limitToDefault (pitchEnvelope.getSustainLevel (), 0)));
        }

        final Optional<IFilter> filterOpt = sampleMetadata == null ? Optional.empty () : sampleMetadata.getFilter ();
        final IFilter filter = filterOpt.isPresent () ? filterOpt.get () : new DefaultFilter (FilterType.LOW_PASS, 2, IFilter.MAX_FREQUENCY, 0);
        groupContent = groupContent.replace ("%FILTER_TYPE%", createFilterType (filter));
        groupContent = groupContent.replace ("%FILTER_BYPASS%", filterOpt.isPresent () ? "no" : "yes");
        groupContent = groupContent.replace ("%FILTER_CUTOFF%", formatDouble (normalizeCutoff (filter.getType (), filter.getCutoff ())));
        groupContent = groupContent.replace ("%FILTER_RESONANCE%", formatDouble (filter.getResonance ()));

        final IEnvelopeModulator filterCutoffModulator = filter.getCutoffEnvelopeModulator ();
        final IEnvelope filterCutoffEnvelope = filterCutoffModulator.getSource ();
        groupContent = groupContent.replace ("%FILTER_CUTOFF_ENVELOPE_INTENSITY%", formatDouble (filterCutoffModulator.getDepth ()));
        groupContent = groupContent.replace ("%FILTER_CUTOFF_ENVELOPE_ATTACK_CURVE%", formatDouble (-filterCutoffEnvelope.getAttackSlope ()));
        groupContent = groupContent.replace ("%FILTER_CUTOFF_ENVELOPE_ATTACK%", formatDouble (limitToDefault (filterCutoffEnvelope.getAttackTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%FILTER_CUTOFF_ENVELOPE_DECAY%", formatDouble (limitToDefault (filterCutoffEnvelope.getDecayTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%FILTER_CUTOFF_ENVELOPE_HOLD%", formatDouble (limitToDefault (filterCutoffEnvelope.getHoldTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%FILTER_CUTOFF_ENVELOPE_RELEASE%", formatDouble (limitToDefault (filterCutoffEnvelope.getReleaseTime (), 1) * 1000.0d));
        groupContent = groupContent.replace ("%FILTER_CUTOFF_ENVELOPE_SUSTAIN%", formatDouble (limitToDefault (filterCutoffEnvelope.getSustainLevel (), 1)));

        final double cutoffVelocityDepth = filter.getCutoffVelocityModulator ().getDepth ();
        groupContent = groupContent.replace ("%FILTER_CUTOFF_VELOCITY_MOD%", formatDouble (limitToDefault (cutoffVelocityDepth, 0)));

        return groupContent;
    }


    /**
     * Get the Kontakt filter type which is the closest to the given filter. Kontakt offers only
     * specific numbers of poles for each filter type.
     *
     * @param filter The filter
     * @return The Kontakt filter type
     */
    private static String createFilterType (final IFilter filter)
    {
        final int poles = filter.getPoles ();
        return switch (filter.getType ())
        {
            case HIGH_PASS -> "hp" + (poles <= 1 ? 1 : poles <= 3 ? 2 : 4) + "pole";
            case BAND_PASS -> poles <= 3 ? "bp2pole" : "bp4pole";
            case BAND_REJECTION -> "br4pole";
            default -> "lp" + (poles <= 1 ? 1 : poles <= 3 ? 2 : poles <= 5 ? 4 : 6) + "pole";
        };
    }


    /**
     * Convert a cutoff frequency to the normalized cutoff of Kontakt. This is the inverse of
     * denormalizeCutoff.
     *
     * @param filterType The type of the filter
     * @param cutoff The cutoff frequency in Hertz
     * @return The normalized cutoff in the range of [0..1]
     */
    private static double normalizeCutoff (final FilterType filterType, final double cutoff)
    {
        final double [] cutoffs = filterType == FilterType.LOW_PASS ? LOW_PASS_CUTOFFS : HIGH_PASS_CUTOFFS;
        final double frequency = Math.clamp (cutoff, cutoffs[0], cutoffs[cutoffs.length - 1]);
        int index = 0;
        while (index < cutoffs.length - 2 && frequency > cutoffs[index + 1])
            index++;
        final double fraction = Math.log (frequency / cutoffs[index]) / Math.log (cutoffs[index + 1] / cutoffs[index]);
        return (index + fraction) / (cutoffs.length - 1);
    }


    private static double limitToDefault (final double value, final double defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }


    private static int limitToDefault (final int value, final int defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }


    /**
     * Checks whether a given element is a valid top-level element.
     *
     * @param top The top-level element.
     * @return True if top is a valid top-level element, false else
     */
    protected abstract boolean isValidTopLevelElement (final Element top);


    /**
     * Returns the program elements in the metadata file.
     *
     * @param top The top element of the document
     * @return An array of program elements, an empty array if nothing was found
     */
    protected abstract List<Element> findProgramElements (final Element top);


    /**
     * Parses a program element and retrieves a IMultisampleSource object representing the program.
     *
     * @param programElement The program element to be parsed
     * @param instrumentSource Where to store the parsed data
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return True if successful
     * @throws IOException Could not create path for samples
     */
    private boolean parseProgram (final Element programElement, final IInstrumentSource instrumentSource, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();

        final String programName = this.tags.programName ();
        if (!programElement.hasAttribute (programName))
            return false;

        final Map<String, String> programParameters = this.readParameters (programElement);
        readMetadata (programParameters, multisampleSource.getMetadata ());

        // Present only in K1
        this.readInstrumentParameters (instrumentSource, programParameters);
        this.readPolyphony (programElement, multisampleSource);
        this.isAdditiveTuning = this.isAdditiveTuning (programElement);

        final List<Element> groupElements = this.getGroupElements (programElement);
        final List<Element> zoneElements = this.getZoneElements (programElement);

        final String sourcePath = multisampleSource.getSourceFile ().getParentFile ().getCanonicalPath ();
        final List<IGroup> groups = this.getGroups (programParameters, groupElements, zoneElements, sourcePath, monolithSamples);
        if (groups.isEmpty ())
        {
            this.notifier.logError ("IDS_NKI_NO_VEL_GROUP_DETECTED");
            return false;
        }

        multisampleSource.setGroups (groups);

        String name = programElement.getAttribute (programName);
        // Kontakt seems to behave like this...
        if ("Instrument 1".equals (name) && groups.size () == 1)
            name = groups.get (0).getName ();
        multisampleSource.setName (name);

        return true;
    }


    /**
     * Read additional parameters which are specific to the instrument. The volume and panning are
     * only applied to the instrument when reading a multi, otherwise they are applied to the zones.
     * The tuning is always applied to the zones since it is a pitch shift.
     *
     * @param instrumentSource The instrument source
     * @param programParameters The program parameter
     */
    protected void readInstrumentParameters (final IInstrumentSource instrumentSource, final Map<String, String> programParameters)
    {
        final String lowKey = programParameters.get ("lowKey");
        if (lowKey != null)
            instrumentSource.setClipKeyLow (Integer.parseInt (lowKey));
        final String highKey = programParameters.get ("highKey");
        if (highKey != null)
            instrumentSource.setClipKeyHigh (Integer.parseInt (highKey));

        final String transpose = programParameters.get ("transpose");
        if (transpose != null)
            instrumentSource.setTranspose (Integer.parseInt (transpose));

        if (!this.isMulti)
            return;

        final String volume = programParameters.get (this.tags.progVolParam ());
        if (volume != null)
            instrumentSource.setGain (MathUtils.valueToDb (Double.parseDouble (volume)));
        final String pan = programParameters.get (this.tags.progPanParam ());
        if (pan != null)
            instrumentSource.setPanning (Math.clamp (this.normalizePanning (Double.parseDouble (pan)), -1.0, 1.0));
    }


    /**
     * Read the maximum number of voices of the instrument. It is stored in the first voice group of
     * the program, which is the voice group of the whole instrument.
     *
     * @param programElement The program element
     * @param multisampleSource Where to store the polyphony
     */
    private void readPolyphony (final Element programElement, final IMultisampleSource multisampleSource)
    {
        final Element polyphonyElement = XMLUtils.getChildElementByName (programElement, this.tags.polyphonyElement ());
        if (polyphonyElement == null)
            return;

        for (final Element voiceGroupElement: XMLUtils.getChildElementsByName (polyphonyElement, this.tags.voiceGroupElement ()))
            if (XMLUtils.getIntegerAttribute (voiceGroupElement, this.tags.indexAttribute (), -1) == 0)
            {
                final int maxNumVoices = parseIndex (this.readValueMap (voiceGroupElement).get (this.tags.maxNumVoicesParam ()));
                if (maxNumVoices > 0)
                    multisampleSource.setPolyphony (maxNumVoices);
                return;
            }
    }


    /**
     * Check if the program and group tuning values are stored as an offset (0 is neutral) instead
     * of a frequency ratio (1 is neutral).
     *
     * @param programElement The program element
     * @return True if the tuning values are stored as an offset
     */
    protected boolean isAdditiveTuning (final Element programElement)
    {
        return false;
    }


    /**
     * Convert a program or group tuning value to a frequency ratio. If the value is stored as an
     * offset it is in octaves, which matches the frequency ratio 2^octaves of the later formats.
     *
     * @param tune The program or group tuning value
     * @return The tuning as a frequency ratio, 1 is neutral
     */
    private double toTuneRatio (final double tune)
    {
        return this.isAdditiveTuning ? Math.pow (2.0, tune) : tune;
    }


    /**
     * Parse an integer index or count.
     *
     * @param value The text to parse, might be null
     * @return The parsed value or -1 if it is not present or cannot be parsed
     */
    protected static int parseIndex (final String value)
    {
        if (value == null || value.isBlank ())
            return -1;
        try
        {
            return Integer.parseInt (value.trim ());
        }
        catch (final NumberFormatException _)
        {
            return -1;
        }
    }


    private static void readMetadata (final Map<String, String> programParameters, final IMetadata metadata)
    {
        final String iconIdx = programParameters.get ("catIconIdx");
        if (iconIdx != null && !iconIdx.isBlank ())
            try
            {
                final int index = Integer.parseInt (iconIdx);
                // 0 is Organ but might simply not be set there use auto-detection instead, 28 is
                // New where we use auto-detection as well
                if (index > 0 && index < 28)
                {
                    final String iconName = KontaktIcon.getName (index);
                    if (iconName != null)
                        metadata.setCategory (iconName);
                }
            }
            catch (final NumberFormatException _)
            {
                // Ignore
            }
        final String author = programParameters.get ("instrumentAuthor");
        if (author != null)
            metadata.setCreator (author);
        final String description = programParameters.get ("instrumentCredits");
        if (description != null && !NULL_ENTRY.equals (description))
            metadata.setDescription (description.replace (NULL_ENTRY, ""));
    }


    /**
     * Reads the parameters of a program under an XML element.
     *
     * @param element The XML element
     * @return A map with the parameters and their values, an empty map if no parameters are found
     */
    private Map<String, String> readParameters (final Element element)
    {
        final Element parametersElement = XMLUtils.getChildElementByName (element, this.tags.parameters ());
        return this.readValueMap (parametersElement);
    }


    /**
     * Reads a value map from a given XML element.
     *
     * @param element The XML element
     * @return The value map. If nothing can be read, an empty map is returned.
     */
    protected Map<String, String> readValueMap (final Element element)
    {
        if (element == null)
            return Collections.emptyMap ();

        final HashMap<String, String> result = new HashMap<> ();
        for (final Element valueElement: XMLUtils.getChildElementsByName (element, this.tags.value ()))
        {
            final String valueName = valueElement.getAttribute (this.tags.valueNameAttribute ());
            final String valueValue = valueElement.getAttribute (this.tags.valueValueAttribute ());
            if (valueName != null && valueValue != null)
                result.put (valueName, valueValue);
        }
        return result;
    }


    /**
     * Retrieves a program's group elements as an array.
     *
     * @param programElement The XML program element
     * @return The array of group elements
     */
    private List<Element> getGroupElements (final Element programElement)
    {
        final Element groupsElement = XMLUtils.getChildElementByName (programElement, this.tags.groups ());
        return groupsElement != null ? XMLUtils.getChildElementsByName (groupsElement, this.tags.group ()) : null;
    }


    /**
     * Retrieves a program's zone elements as an array.
     *
     * @param programElement The XML program element
     * @return The array of zone elements
     */
    private List<Element> getZoneElements (final Element programElement)
    {
        final Element zoneElement = XMLUtils.getChildElementByName (programElement, this.tags.zones ());
        return zoneElement != null ? XMLUtils.getChildElementsByName (zoneElement, this.tags.zone ()) : null;
    }


    /**
     * Creates groups from a program's parameters and its group and zone elements.
     *
     * @param programParameters The program parameters
     * @param groupElements The group elements
     * @param zoneElements The zone elements
     * @param sourcePath The canonical path which contains the NKI file
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return the groups created (empty list is returned if nothing was created)
     * @throws IOException Could not get sample file(s)
     */
    private List<IGroup> getGroups (final Map<String, String> programParameters, final List<Element> groupElements, final List<Element> zoneElements, final String sourcePath, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        if (groupElements == null || zoneElements == null)
            return Collections.emptyList ();

        final LinkedList<IGroup> groups = new LinkedList<> ();
        for (final Element groupElement: groupElements)
        {
            final Optional<IGroup> group = this.getGroup (programParameters, groupElement, zoneElements, sourcePath, monolithSamples);
            if (group.isPresent ())
                groups.add (group.get ());
        }
        return groups;
    }


    /**
     * Creates one group from a program's parameters, a given group element and the program's zone
     * elements.
     *
     * @param programParameters The program parameters
     * @param groupElement The group element from which the group is created
     * @param zoneElements The program's zone elements
     * @param sourcePath The canonical path which contains the NKI file
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return The group created from the zone element (null, if there is no group element)
     * @throws IOException Could not get sample file(s)
     */
    private Optional<IGroup> getGroup (final Map<String, String> programParameters, final Element groupElement, final List<Element> zoneElements, final String sourcePath, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        // Groups which can only be triggered by the (slice) sequencer cannot be played from the
        // keyboard
        if (groupElement == null || this.isSequencerOnly (groupElement))
            return Optional.empty ();

        final IGroup group = new DefaultGroup ();
        group.setName (this.getGroupName (groupElement));
        final Map<String, String> groupParameters = this.readParameters (groupElement);
        this.readGroupValues (group, groupParameters);
        final Optional<GroupFilter> groupFilter = this.readFilter (groupElement);
        final int filterEffectIndex = groupFilter.isPresent () ? groupFilter.get ().effectIndex () : -1;
        final Optional<IFilter> filter = groupFilter.isPresent () ? Optional.of (groupFilter.get ().filter ()) : Optional.empty ();

        final Map<String, IEnvelopeModulator> groupInternalModulators = this.readGroupInternalModulators (groupElement, filterEffectIndex);
        final List<ExternalModulation> extModulators = this.readExtModulators (groupElement);
        final Map<String, ILfoModulator> groupLfoModulators = this.readGroupLfoModulators (groupElement, filterEffectIndex, extModulators);
        final int pitchBend = this.readGroupPitchBend (extModulators);
        final double ampVelocityMod = this.readGroupAmplitudeVelocityModulator (extModulators);
        if (groupFilter.isPresent ())
            this.applyFilterModulation (groupFilter.get (), extModulators, groupLfoModulators);

        group.setTrigger (this.getTriggerTypeFromGroupElement (groupParameters));
        final Element [] groupZones = this.findGroupZones (groupElement, zoneElements);
        final List<ISampleZone> zones = this.getSampleMetadataFromZones (programParameters, groupParameters, groupInternalModulators, groupElement, groupZones, pitchBend, ampVelocityMod, sourcePath, monolithSamples, filter);
        group.setSampleZones (zones);
        // The one-shot setting is stored on the group's amplitude envelope but the model keeps it
        // on the zone
        final boolean isOneShot = this.readGroupOneShot (groupElement);
        for (final ISampleZone zone: zones)
        {
            if (isOneShot)
                zone.setOneShot (true);
            applyLfoModulator (groupLfoModulators.get (this.tags.targetVolValue ()), zone.getAmplitudeLfoModulator ());
            applyLfoModulator (groupLfoModulators.get (this.tags.targetPitchValue ()), zone.getPitchLfoModulator ());
        }
        this.parseRoundRobin (groupElement, zones);
        return Optional.of (group);
    }


    /**
     * Reads the gain, panning and tuning of a group and stores them on the group. Note that these
     * values are (and must stay) applied to each of the sample zones of the group as well, see the
     * reading of a sample zone. A creator must therefore apply either the group or the zone value
     * but never both.
     *
     * @param group The group on which to store the values
     * @param groupParameters The parameters of the group
     */
    private void readGroupValues (final IGroup group, final Map<String, String> groupParameters)
    {
        try
        {
            // The volume is a linear factor, 1 is the neutral value
            final double groupVolume = AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupVolParam ());
            if (groupVolume != 1)
                group.setGain (20.0d * Math.log10 (groupVolume));
        }
        catch (final ValueNotAvailableException _)
        {
            // The parameter is optional, keep the neutral default
        }

        try
        {
            final double groupPan = this.normalizePanning (AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupPanParam ()));
            if (groupPan != 0)
                group.setPanning (Math.clamp (groupPan, -1.0d, 1.0d));
        }
        catch (final ValueNotAvailableException _)
        {
            // The parameter is optional, keep the neutral default
        }

        try
        {
            final double groupTune = this.tags.calculateGroupTune (this.toTuneRatio (AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupTuneParam ())));
            if (groupTune != 0)
                group.setTuning (groupTune);
        }
        catch (final ValueNotAvailableException _)
        {
            // The parameter is optional, keep the neutral default
        }
    }


    /**
     * Reads the note-off-less mode of the group's amplitude envelope. If it is enabled, note-off
     * events are ignored and the sample is always played back to its end (one-shot). The parameter
     * is only present from Kontakt 2 onwards.
     *
     * @param groupElement The group element
     * @return True if the group is played back as a one-shot
     */
    private boolean readGroupOneShot (final Element groupElement)
    {
        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.intModulatorsElement ());
        if (modulatorsElement == null)
            return false;

        for (final Element modulatorElement: XMLUtils.getChildElementsByName (modulatorsElement, this.tags.intModulatorElement ()))
        {
            final Element envElement = XMLUtils.getChildElementByName (modulatorElement, this.tags.envelopeElement ());
            if (envElement == null || this.hasNameValuePairs (modulatorElement, this.tags.bypassParam (), this.tags.yes ()))
                continue;
            final Optional<String> modulationTarget = this.getModulationTarget (modulatorElement);
            if (modulationTarget.isPresent () && this.tags.targetVolValue ().equals (modulationTarget.get ()) && this.hasNameValuePairs (envElement, this.tags.noteOffLessModeParam (), this.tags.yes ()))
                return true;
        }
        return false;
    }


    /**
     * Get the parameters of all start criteria of a group. Up to 4 criteria are present which are
     * combined with each other.
     *
     * @param groupElement The group element
     * @return The parameters of all start criteria which have parameters
     */
    private List<Map<String, String>> getStartCriteria (final Element groupElement)
    {
        final Element groupStartElement = XMLUtils.getChildElementByName (groupElement, this.tags.groupStart ());
        if (groupStartElement == null)
            return Collections.emptyList ();

        final List<Map<String, String>> startCriteria = new ArrayList<> ();
        for (final Element startCriteriaElement: XMLUtils.getChildElementsByName (groupStartElement, this.tags.startCriteria ()))
        {
            final Map<String, String> values = this.readValueMap (startCriteriaElement);
            if (!values.isEmpty ())
                startCriteria.add (values);
        }
        return startCriteria;
    }


    /**
     * Check if the group is only triggered by the sequencer (e.g. slices of a loop) but not by
     * notes played on the keyboard.
     *
     * @param groupElement The group element
     * @return True if only the sequencer triggers the group
     */
    private boolean isSequencerOnly (final Element groupElement)
    {
        for (final Map<String, String> startCriteria: this.getStartCriteria (groupElement))
            if (this.tags.yes ().equals (startCriteria.get (this.tags.sequencerOnlyParam ())))
                return true;
        return false;
    }


    /**
     * Set the round-robin play logic on the zones if any of the start criteria of the group is a
     * cycle mode (e.g. 'cycle_round_robin').
     *
     * @param groupElement The group element
     * @param zones The zones of the group
     */
    private void parseRoundRobin (final Element groupElement, final List<ISampleZone> zones)
    {
        for (final Map<String, String> startCriteria: this.getStartCriteria (groupElement))
        {
            final String mode = startCriteria.get (this.tags.startModeParam ());
            if (mode == null || !mode.startsWith (this.tags.cycleModePrefix ()))
                continue;

            // The cycle class is the zero based position in the round-robin cycle, the model starts
            // with 1
            final int roundRobinPosition = parseIndex (startCriteria.get (this.tags.cycleClassParam ()));
            final PlayLogic playLogic = CYCLE_RANDOM.equals (mode) ? PlayLogic.RANDOM : PlayLogic.ROUND_ROBIN;
            for (final ISampleZone zone: zones)
            {
                zone.setPlayLogic (playLogic);
                if (roundRobinPosition > -1)
                    zone.setSequencePosition (roundRobinPosition + 1);
            }
            return;
        }
    }


    /**
     * Creates sample metadata from zones.
     *
     * @param programParameters The program's parameters
     * @param groupParameters The group's parameters
     * @param groupModulators The group's modulation envelopes
     * @param groupElement The group element from which the metadata is created
     * @param groupZones The zone elements belonging to the group
     * @param pitchBend The pitchbend range (half tone steps)
     * @param ampVelocityMod The amplitude modulation by velocity
     * @param sourcePath The canonical path which contains the NKI file
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @param filter The filter or null if none is applied
     * @return A list of sample metadata object. If nothing can be created, an empty list is
     *         returned.
     * @throws IOException Could not get sample file
     */
    private List<ISampleZone> getSampleMetadataFromZones (final Map<String, String> programParameters, final Map<String, String> groupParameters, final Map<String, IEnvelopeModulator> groupModulators, final Element groupElement, final Element [] groupZones, final int pitchBend, final double ampVelocityMod, final String sourcePath, final Map<String, ISampleData> monolithSamples, final Optional<IFilter> filter) throws IOException
    {
        if (groupElement == null || groupZones == null)
            return Collections.emptyList ();

        final LinkedList<ISampleZone> sampleMetadataList = new LinkedList<> ();

        for (final Element zoneElement: groupZones)
        {
            final Optional<File> sampleFileOpt = this.getZoneSampleFile (zoneElement, sourcePath, !monolithSamples.isEmpty ());
            if (sampleFileOpt.isEmpty ())
                continue;

            final File sampleFile = sampleFileOpt.get ();
            final Optional<ISampleData> sampleData = this.getSampleData (sampleFile, monolithSamples);
            if (sampleData.isPresent ())
            {
                final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData.get ());
                if (filter.isPresent ())
                    zone.setFilter (filter.get ());
                this.readSampleZone (programParameters, groupParameters, groupModulators, pitchBend, ampVelocityMod, sampleMetadataList, zoneElement, zone);
            }
        }

        return sampleMetadataList;
    }


    private Optional<ISampleData> getSampleData (final File sampleFile, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        if (!monolithSamples.isEmpty ())
            return Optional.of (monolithSamples.get (sampleFile.getName ()));

        if (sampleFile.getName ().toLowerCase ().endsWith (".ncw"))
            return Optional.of (new NcwFileSampleData (sampleFile));

        // Check the type of the source sample for compatibility and handle them
        // accordingly
        try
        {
            final AudioFileFormat.Type type = AudioSystem.getAudioFileFormat (sampleFile).getType ();
            if (AudioFileFormat.Type.WAVE.equals (type))
            {
                if (AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                    return Optional.of (new WavFileSampleData (sampleFile));
            }
            else if (AudioFileFormat.Type.AIFF.equals (type))
                return Optional.of (new AiffFileSampleData (sampleFile));

            this.notifier.logError ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", type.toString ());
        }
        catch (final UnsupportedAudioFileException ex)
        {
            this.notifier.logError ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED_LONG", ex.getMessage (), sampleFile.getName ());
        }
        catch (final NoSuchFileException _)
        {
            this.notifier.logError ("IDS_ERR_SAMPLE_FILE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
        }
        catch (final IOException _)
        {
            this.notifier.logError ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", sampleFile.getName ());
        }
        return Optional.empty ();
    }


    private void readSampleZone (final Map<String, String> programParameters, final Map<String, String> groupParameters, final Map<String, IEnvelopeModulator> groupModulators, final int pitchBend, final double ampVelocityMod, final LinkedList<ISampleZone> sampleMetadataList, final Element zoneElement, final ISampleZone zone)
    {
        try
        {
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (sampleData.isPresent ())
                sampleData.get ().addZoneData (zone, true, true);
        }
        catch (final FileNotFoundException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex);
            return;
        }

        final Map<String, String> zoneParameters = this.readParameters (zoneElement);
        try
        {
            zone.setKeyRoot (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.rootKeyParam ()));
            zone.setKeyLow (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.lowKeyParam ()));
            zone.setKeyHigh (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.highKeyParam ()));
            zone.setVelocityLow (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.lowVelocityParam ()));
            zone.setVelocityHigh (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.highVelocityParam ()));
            zone.setNoteCrossfadeLow (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.fadeLowParam ()));
            zone.setNoteCrossfadeHigh (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.fadeHighParam ()));
            zone.setVelocityCrossfadeLow (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.fadeLowVelParam ()));
            zone.setVelocityCrossfadeHigh (AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.fadeHighVelParam ()));

            final int sampleStart = AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.sampleStartParam ());
            final int sampleEnd = AbstractNKIMetadataFileHandler.getInt (zoneParameters, this.tags.sampleEndParam ());

            // The stop of the zone was already set to the length of the sample (if available). A
            // positive end is an absolute position, 0 is the end of the sample and a negative end
            // is the number of frames counted backwards from the end of the sample
            if (sampleStart > 0)
                zone.setStart (sampleStart);
            final int sampleStop = sampleEnd < 0 && zone.getStop () > 0 ? zone.getStop () + sampleEnd : sampleEnd;
            if (sampleStop > zone.getStart ())
                zone.setStop (sampleStop);

            final String keyTracking = AbstractNKIMetadataFileHandler.getString (groupParameters, this.tags.keyTrackingParam ());
            final double keyTrackingValue = keyTracking.equals (this.tags.yes ()) ? 1.0d : 0.0d;
            zone.setKeyTracking (keyTrackingValue);

            final double zoneVol = AbstractNKIMetadataFileHandler.getDouble (zoneParameters, this.tags.zoneVolParam ());
            final double groupVol = AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupVolParam ());
            // In a multi the program volume is applied to the instrument
            final double progVol = this.isMulti ? 1.0 : AbstractNKIMetadataFileHandler.getDouble (programParameters, this.tags.progVolParam ());
            zone.setGain (20.0d * Math.log10 (zoneVol * groupVol * progVol));

            final double zoneTune = AbstractNKIMetadataFileHandler.getDouble (zoneParameters, this.tags.zoneTuneParam ());
            final double groupTune = this.toTuneRatio (AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupTuneParam ()));
            final double progTune = this.toTuneRatio (AbstractNKIMetadataFileHandler.getDouble (programParameters, this.tags.progTuneParam ()));
            zone.setTuning (this.tags.calculateTune (zoneTune, groupTune, progTune));

            final double zonePan = AbstractNKIMetadataFileHandler.getDouble (zoneParameters, this.tags.zonePanParam ());
            final double groupPan = AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupPanParam ());
            // In a multi the program panning is applied to the instrument
            final double progPan = this.isMulti ? 0 : this.normalizePanning (AbstractNKIMetadataFileHandler.getDouble (programParameters, this.tags.progPanParam ()));
            final double totalPan = this.normalizePanning (zonePan) + this.normalizePanning (groupPan) + progPan;
            zone.setPanning (Math.clamp (totalPan, -1.0d, 1.0d));

            if (ampVelocityMod >= 0)
            {
                zone.getAmplitudeVelocityModulator ().setDepth (ampVelocityMod);
                // The volume of Kontakt maps its normalized value to decibels with 60*log10(x).
                // The velocity modulation therefore scales the amplitude with x^3, which is a
                // convex curve (+1)
                zone.getAmplitudeVelocityModulator ().setCurve (1);
            }
        }
        catch (final ValueNotAvailableException e)
        {
            this.notifier.logError ("IDS_NKI_ERROR_MISSING_VALUE", e);
            return;
        }

        this.setModulators (groupModulators, zone);

        if (groupParameters.containsKey (this.tags.reverseParam ()))
        {
            final String reversed = groupParameters.get (this.tags.reverseParam ());
            zone.setReversed (reversed.equals (this.tags.yes ()));
        }

        // Kontakt stores the voice group as a zero based index with -1 for 'no group' but the model
        // uses 0 for 'no group', therefore the index is shifted by one
        final String voiceGroup = groupParameters.get (this.tags.voiceGroupParam ());
        if (voiceGroup != null && !voiceGroup.isBlank ())
            try
            {
                final int voiceGroupIndex = Integer.parseInt (voiceGroup.trim ());
                zone.setExclusiveGroup (voiceGroupIndex < 0 ? 0 : voiceGroupIndex + 1);
            }
            catch (final NumberFormatException _)
            {
                // Ignore an unparsable voice group
            }

        this.readLoopInformation (zoneElement, zone);

        sampleMetadataList.add (zone);

        if (pitchBend >= 0)
        {
            zone.setBendUp (pitchBend);
            zone.setBendDown (-pitchBend);
        }
    }


    private void setModulators (final Map<String, IEnvelopeModulator> groupEnvelopes, final ISampleZone sampleMetadata)
    {
        final IEnvelopeModulator groupAmpModulator = groupEnvelopes.get (this.tags.targetVolValue ());
        if (groupAmpModulator != null)
        {
            final IEnvelope source = groupAmpModulator.getSource ();
            final IEnvelopeModulator amplitudeModulator = sampleMetadata.getAmplitudeEnvelopeModulator ();
            amplitudeModulator.setDepth (groupAmpModulator.getDepth ());

            final IEnvelope amplitudeEnvelope = amplitudeModulator.getSource ();
            amplitudeEnvelope.setAttackSlope (source.getAttackSlope ());
            amplitudeEnvelope.setAttackTime (source.getAttackTime ());
            amplitudeEnvelope.setHoldTime (source.getHoldTime ());
            amplitudeEnvelope.setDecayTime (source.getDecayTime ());
            amplitudeEnvelope.setSustainLevel (source.getSustainLevel ());
            amplitudeEnvelope.setReleaseTime (source.getReleaseTime ());
        }

        final IEnvelopeModulator groupPitchModulator = groupEnvelopes.get (this.tags.targetPitchValue ());
        if (groupPitchModulator != null)
        {
            final IEnvelope source = groupPitchModulator.getSource ();
            final IEnvelopeModulator pitchModulator = sampleMetadata.getPitchEnvelopeModulator ();
            pitchModulator.setDepth (groupPitchModulator.getDepth ());

            final IEnvelope pitchEnvelope = pitchModulator.getSource ();
            pitchEnvelope.setAttackSlope (source.getAttackSlope ());
            pitchEnvelope.setAttackTime (source.getAttackTime ());
            pitchEnvelope.setHoldTime (source.getHoldTime ());
            pitchEnvelope.setDecayTime (source.getDecayTime ());
            pitchEnvelope.setSustainLevel (source.getSustainLevel ());
            pitchEnvelope.setReleaseTime (source.getReleaseTime ());
        }

        final Optional<IFilter> filterOpt = sampleMetadata.getFilter ();
        if (filterOpt.isPresent ())
        {
            final IEnvelopeModulator groupFilterCutoffModulator = groupEnvelopes.get (this.tags.targetFilterCutoffValue ());
            if (groupFilterCutoffModulator != null)
            {
                final IFilter filter = filterOpt.get ();

                final IEnvelope source = groupFilterCutoffModulator.getSource ();
                final IEnvelopeModulator filterCutoffModulator = filter.getCutoffEnvelopeModulator ();
                filterCutoffModulator.setDepth (groupFilterCutoffModulator.getDepth ());

                final IEnvelope filterCutoffEnvelope = filterCutoffModulator.getSource ();
                filterCutoffEnvelope.setAttackSlope (source.getAttackSlope ());
                filterCutoffEnvelope.setAttackTime (source.getAttackTime ());
                filterCutoffEnvelope.setHoldTime (source.getHoldTime ());
                filterCutoffEnvelope.setDecayTime (source.getDecayTime ());
                filterCutoffEnvelope.setSustainLevel (source.getSustainLevel ());
                filterCutoffEnvelope.setReleaseTime (source.getReleaseTime ());
            }
        }
    }


    /**
     * Normalizes a panning value to a range from -1 to 1 where 0 is center and -1 is left.
     *
     * @param panningValue The panning value to normalize
     * @return The normalized panning value
     */
    protected abstract double normalizePanning (final double panningValue);


    /**
     * De-normalizes a panning value from a range from -1 to 1 where 0 is center and -1 is left.
     *
     * @param panningValue The panning value to normalize
     * @return The normalized panning value
     */
    protected double denormalizePanning (final double panningValue)
    {
        return panningValue;
    }


    /**
     * Reads the loop information from a zone element and writes it to a ISampleMetadata object.
     *
     * @param zoneElement The zone element
     * @param sampleMetadata The ISampleMetadata object
     */
    private void readLoopInformation (final Element zoneElement, final ISampleZone sampleMetadata)
    {
        final Element loopsElement = XMLUtils.getChildElementByName (zoneElement, this.tags.loopsElement ());
        if (loopsElement == null)
            return;

        final List<Element> loopElements = XMLUtils.getChildElementsByName (loopsElement, this.tags.loopElement (), false);
        final List<ISampleLoop> loops = sampleMetadata.getLoops ();
        if (!loopElements.isEmpty ())
            loops.clear ();
        for (final Element loopElement: loopElements)
        {
            final Map<String, String> loopParams = this.readValueMap (loopElement);

            int loopStart;
            int loopLength;
            String loopMode;
            int xFadeLength;
            double loopTuning;
            String alternatingLoop;
            try
            {
                loopStart = AbstractNKIMetadataFileHandler.getInt (loopParams, this.tags.loopStartParam ());
                loopLength = AbstractNKIMetadataFileHandler.getInt (loopParams, this.tags.loopLengthParam ());
                loopMode = AbstractNKIMetadataFileHandler.getString (loopParams, this.tags.loopModeParam ());
                loopTuning = AbstractNKIMetadataFileHandler.getDouble (loopParams, this.tags.loopTuningParam ());
                xFadeLength = AbstractNKIMetadataFileHandler.getInt (loopParams, this.tags.xfadeLengthParam ());
                alternatingLoop = AbstractNKIMetadataFileHandler.getString (loopParams, this.tags.alternatingLoopParam ());
            }
            catch (final ValueNotAvailableException _)
            {
                return;
            }

            // If it is a one shot then there is no loop
            if (loopMode.equals (this.tags.oneshotValue ()))
            {
                sampleMetadata.setOneShot (true);
                continue;
            }

            LoopType loopType = LoopType.FORWARDS;
            if ((loopMode.equals (this.tags.untilEndValue ()) || loopMode.equals (this.tags.untilReleaseValue ())) && alternatingLoop.equals (this.tags.yes ()))
                loopType = LoopType.ALTERNATING;

            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (loopStart);
            // The length is the number of frames of the loop, the end of the model is inclusive
            loop.setEnd (loopStart + loopLength - 1);
            loop.setTuning (12.0 * (Math.log (loopTuning) / Math.log (2.0)));
            loop.setCrossfadeInSamples (xFadeLength);
            loop.setType (loopType);
            // 'until_release' loops while the key is held and then plays the remainder of the
            // sample on release (sustain loop); 'until_end' loops continuously
            loop.setLoopUntilRelease (loopMode.equals (this.tags.untilReleaseValue ()));
            sampleMetadata.addLoop (loop);
        }
    }


    /**
     * Retrieves a sample file from a zone element.
     *
     * @param zoneElement The zone element
     * @param sourcePath The canonical path which contains the NKI file
     * @param isMonolith True if samples are contained in the NKI as well
     * @return The sample file. Null is returned if file cannot be retrieved successfully.
     * @throws IOException Could not get the file
     */
    private Optional<File> getZoneSampleFile (final Element zoneElement, final String sourcePath, final boolean isMonolith) throws IOException
    {
        final Element sampleElement = XMLUtils.getChildElementByName (zoneElement, this.tags.zoneSample ());
        if (sampleElement == null)
            return Optional.empty ();

        final Map<String, String> sampleParameters = this.readValueMap (sampleElement);
        String encodedSampleFileName = sampleParameters.get (this.tags.sampleFileAttribute ());
        if (encodedSampleFileName == null)
            encodedSampleFileName = sampleParameters.get (this.tags.sampleFileExAttribute ());
        if (encodedSampleFileName == null)
        {
            this.notifier.logError ("IDS_NKI_SAMPLE_FILE_ATTRIBUTE_MISSING");
            return Optional.empty ();
        }

        return this.getFileFromEncodedSampleFileName (encodedSampleFileName, sourcePath, isMonolith);
    }


    /**
     * Decodes an encoded sample file name and returns the respective File if it can be found.
     *
     * @param encodedSampleFileName The encoded sample file name
     * @param sourcePath The canonical path which contains the NKI file
     * @param isMonolith True if samples are contained in the NKI as well
     * @return The File if it can be found, null else
     * @throws IOException Could not decode file name
     */
    protected Optional<File> getFileFromEncodedSampleFileName (final String encodedSampleFileName, final String sourcePath, final boolean isMonolith) throws IOException
    {
        final DecodedPath decodedPath = this.decodeEncodedSampleFileName (encodedSampleFileName);
        if (decodedPath.getLibrary () != null)
            throw new IOException (Functions.getMessage ("IDS_NKI_SAMPLES_IN_LIBRARY_NOT_SUPPORTED"));

        final String samplePath = decodedPath.getPath ();
        if (isMonolith)
            return Optional.of (new File (samplePath));

        final File parentFolder = new File (sourcePath);

        File sampleFile = null;
        if (decodedPath.isAbsolute ())
        {
            final Path absoluteParentPath = Paths.get (samplePath);
            final String originalAbsoluteParentPath = absoluteParentPath.getParent ().toString ();

            // First check if this absolute path was searched before
            final String resultPath = this.absolutePathLookupSuccess.get (originalAbsoluteParentPath);
            if (resultPath != null)
            {
                sampleFile = new File (resultPath, absoluteParentPath.getFileName ().toString ());
                if (sampleFile.exists () && sampleFile.canRead ())
                    return Optional.of (sampleFile);
            }

            // If it is an absolute path, try to find the sample file in all possible sub-paths from
            // the current folder
            File subFolder = null;
            for (int i = absoluteParentPath.getNameCount () - 1; i > 0; i--)
            {
                final String folder = absoluteParentPath.getName (i).toString ();
                subFolder = subFolder == null ? new File (folder) : new File (folder, subFolder.getPath ());
                sampleFile = new File (parentFolder, subFolder.getPath ());
                if (sampleFile.exists () && sampleFile.canRead ())
                    return Optional.of (this.logFoundFolder (sampleFile, originalAbsoluteParentPath));
            }

            // Search all sub-folders
            final String fileName = absoluteParentPath.getFileName ().toString ();
            sampleFile = AbstractDetector.findSampleFile (this.notifier, parentFolder, null, fileName, 0);
            if (sampleFile != null)
                return Optional.of (this.logFoundFolder (sampleFile, originalAbsoluteParentPath));

            this.notifier.logError ("IDS_NKI_SAMPLE_FILE_DOES_NOT_EXIST", samplePath);
            return Optional.empty ();
        }

        sampleFile = new File (parentFolder, samplePath);
        if (sampleFile.exists () && sampleFile.canRead ())
            return Optional.of (sampleFile);
        // Search all sub-folders - also triggers searching for different extension cases
        // Also go up 1 folder in case this is a library
        sampleFile = AbstractDetector.findSampleFile (this.notifier, parentFolder, null, sampleFile.getAbsolutePath (), 1);
        if (sampleFile.exists () && sampleFile.canRead ())
            return Optional.of (sampleFile);
        this.notifier.logError ("IDS_NKI_SAMPLE_FILE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
        return Optional.empty ();
    }


    private File logFoundFolder (final File sampleFile, final String originalAbsoluteParentPath)
    {
        this.absolutePathLookupSuccess.put (sampleFile.getParent (), originalAbsoluteParentPath);
        return sampleFile;
    }


    /**
     * Decodes an encoded sample file name as used in the NKI's respective sample file attribute.
     *
     * @param encodedSampleFileName The encoded sample file name
     * @return The decoded path
     * @throws IOException Could not decode the file name/path
     */
    protected DecodedPath decodeEncodedSampleFileName (final String encodedSampleFileName) throws IOException
    {
        final DecodedPath decodedPath = new DecodedPath ();
        final StringReader reader = new StringReader (encodedSampleFileName);

        // Indicator for extended path?
        final int startChar = reader.read ();
        if (startChar < 0)
        {
            decodedPath.setPath ("");
            return decodedPath;
        }
        if ((char) startChar != '@')
        {
            decodedPath.setPath (encodedSampleFileName.replace ('\\', '/'));
            return decodedPath;
        }

        final StringBuilder path = new StringBuilder ();
        int id;
        while ((id = reader.read ()) != -1)
            switch (id)
            {
                // The disc letter or name name
                case 'v':
                    final String disc = readFolder (reader, encodedSampleFileName);
                    if (!disc.contains (":"))
                        path.append ('/');
                    path.append (disc).append ('/');
                    decodedPath.setAbsolute (true);
                    break;

                // Folder upwards
                case 'b':
                    path.append ("../");
                    break;

                // A directory
                case 'd':
                    path.append (readFolder (reader, encodedSampleFileName)).append ('/');
                    break;

                // The sample's file name
                case 'F', 'f':
                    final char [] fractionBuffer = new char [id == 'f' ? 6 : 11];
                    if (reader.read (fractionBuffer) != fractionBuffer.length)
                        throw error (encodedSampleFileName);
                    int c;
                    while ((c = reader.read ()) != -1)
                        path.append ((char) c);
                    final String finalPath = decodeUTF8Characters (path.toString ());
                    decodedPath.setPath (finalPath);
                    if (finalPath.startsWith ("/"))
                        decodedPath.setAbsolute (true);
                    return decodedPath;

                // A NKS library
                case 'm':
                    path.append (readFolder (reader, encodedSampleFileName));
                    decodedPath.setLibrary (path.toString ());
                    path.setLength (0);
                    break;

                default:
                    throw error (encodedSampleFileName);
            }

        throw error (encodedSampleFileName);
    }


    private static String decodeUTF8Characters (final String text)
    {
        if (!text.contains ("%"))
            return text;

        final Matcher m = UTF_PATTERN.matcher (text);
        final StringBuffer sb = new StringBuffer ();
        while (m.find ())
            m.appendReplacement (sb, new String (Character.toChars (Integer.parseInt (m.group (1), 16))));
        m.appendTail (sb);

        return sb.toString ();
    }


    /**
     * Retrieves a TriggerType from a group's parameters.
     *
     * @param groupParameters The group element to retrieve the TriggerType from
     * @return The TriggerType that could be retrieved. In doubt, TriggerType.ATTACK is returned.
     */
    private TriggerType getTriggerTypeFromGroupElement (final Map<String, String> groupParameters)
    {
        return this.tags.yes ().equals (groupParameters.get (this.tags.releaseTriggerParam ())) ? TriggerType.RELEASE : TriggerType.ATTACK;
    }


    /**
     * Retrieves a group element's name.
     *
     * @param groupElement The group element
     * @return The name as a String. If no name was found, "" is returned.
     */
    private String getGroupName (final Element groupElement)
    {
        final String groupName = groupElement.getAttribute (this.tags.groupNameAttribute ());
        return groupName == null ? "" : groupName;
    }


    /**
     * Reads all modulator envelopes from a group element.
     *
     * @param groupElement The group element
     * @param filterEffectIndex The index of the effect slot which contains the filter of the group,
     *            -1 if not known
     * @return The found envelopes
     */
    private Map<String, IEnvelopeModulator> readGroupInternalModulators (final Element groupElement, final int filterEffectIndex)
    {
        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.intModulatorsElement ());
        if (modulatorsElement == null)
            return Collections.emptyMap ();

        final Map<String, IEnvelopeModulator> modulators = new HashMap<> ();
        for (final Element modulatorElement: XMLUtils.getChildElementsByName (modulatorsElement, this.tags.intModulatorElement ()))
        {
            final Element envElement = XMLUtils.getChildElementByName (modulatorElement, this.tags.envelopeElement ());
            if (envElement != null && !this.hasNameValuePairs (modulatorElement, this.tags.bypassParam (), this.tags.yes ()))
            {
                final Optional<String> modulationTarget = this.getModulationTarget (modulatorElement);
                if (modulationTarget.isPresent () && this.isModulatingGroupFilter (modulatorElement, modulationTarget.get (), filterEffectIndex))
                {
                    final double modulationIntensity = this.getModulationIntensity (modulatorElement);
                    if (modulationIntensity != 0)
                    {
                        final Optional<IEnvelopeModulator> modulatorOpt = this.readModulatorFromElement (envElement);
                        if (modulatorOpt.isPresent ())
                        {
                            final IEnvelopeModulator modulator = modulatorOpt.get ();
                            // The pitch intensity of Kontakt covers 1 octave, the depth of the
                            // model is relative to MAX_ENVELOPE_DEPTH cents
                            if (this.tags.targetPitchValue ().equals (modulationTarget.get ()))
                                modulator.setDepth (Math.clamp (modulationIntensity * PITCH_INTENSITY_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH, -1.0, 1.0));
                            else
                                modulator.setDepth (modulationIntensity);
                            modulators.put (modulationTarget.get (), modulator);
                        }
                    }
                }
            }
        }
        return modulators;
    }


    /**
     * Reads all low frequency oscillator modulators from a group element.
     *
     * @param groupElement The group element
     * @param filterEffectIndex The index of the effect slot which contains the filter of the group,
     *            -1 if not known
     * @param extModulators All external modulators of the group
     * @return The found modulators mapped to their modulation target
     */
    private Map<String, ILfoModulator> readGroupLfoModulators (final Element groupElement, final int filterEffectIndex, final List<ExternalModulation> extModulators)
    {
        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.intModulatorsElement ());
        if (modulatorsElement == null)
            return Collections.emptyMap ();

        final Map<String, ILfoModulator> modulators = new HashMap<> ();
        for (final Element modulatorElement: XMLUtils.getChildElementsByName (modulatorsElement, this.tags.intModulatorElement ()))
        {
            final Element lfoElement = XMLUtils.getChildElementByName (modulatorElement, this.tags.lfoElement ());
            // If the intensity is controlled by an external modulator (e.g. a tremolo or vibrato
            // which is controlled by the modulation wheel), the oscillator is not active by default
            if (lfoElement == null || this.hasNameValuePairs (modulatorElement, this.tags.bypassParam (), this.tags.yes ()) || this.isIntensityModulated (XMLUtils.getIntegerAttribute (modulatorElement, this.tags.indexAttribute (), -1), extModulators))
                continue;
            final Optional<String> modulationTarget = this.getModulationTarget (modulatorElement);
            if (modulationTarget.isEmpty () || !this.isModulatingGroupFilter (modulatorElement, modulationTarget.get (), filterEffectIndex))
                continue;
            final double modulationIntensity = this.getModulationIntensity (modulatorElement);
            if (modulationIntensity == 0)
                continue;
            final Optional<ILfoModulator> modulator = this.readLfoFromElement (modulatorElement, lfoElement, modulationTarget.get (), modulationIntensity);
            if (modulator.isPresent ())
                modulators.put (modulationTarget.get (), modulator.get ());
        }
        return modulators;
    }


    /**
     * Reads a low frequency oscillator modulator.
     *
     * @param modulatorElement The internal modulator element
     * @param lfoElement The oscillator element of the modulator
     * @param target The modulation target
     * @param intensity The modulation intensity
     * @return The modulator if it could be read
     */
    private Optional<ILfoModulator> readLfoFromElement (final Element modulatorElement, final Element lfoElement, final String target, final double intensity)
    {
        final Optional<LfoWaveform> waveform = getLfoWaveform (lfoElement.getAttribute (this.tags.envTypeAttribute ()));
        if (waveform.isEmpty ())
            return Optional.empty ();

        // Kontakt 1 stores the frequency in the modulator, Kontakt 2 in the oscillator element
        final Map<String, String> lfoParameters = new HashMap<> (this.readValueMap (modulatorElement));
        lfoParameters.putAll (this.readValueMap (lfoElement));
        final double frequency = parseDouble (lfoParameters.get (this.tags.frequencyParam ()), -1);
        if (frequency <= 0)
            return Optional.empty ();

        // The pitch intensity of Kontakt covers 1 octave (same as the pitch-bend), the model uses
        // the range of the pitch envelope
        final double depth = this.tags.targetPitchValue ().equals (target) ? intensity * PITCH_INTENSITY_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH : intensity;
        final ILfoModulator modulator = new DefaultLfoModulator (Math.clamp (depth, -1.0, 1.0));
        final ILfo lfo = modulator.getSource ();
        lfo.setWaveform (waveform.get ());
        lfo.setRate (frequency);
        // The delay is stored in milliseconds
        lfo.setDelay (Math.max (0, parseDouble (lfoParameters.get (this.tags.delayParam ()), 0)) / 1000.0);
        final double startPhase = parseDouble (lfoParameters.get (this.tags.startPhaseParam ()), -1);
        if (startPhase >= 0)
            lfo.setStartPhase (Math.clamp (startPhase, 0, 1));
        lfo.setKeySync (this.tags.yes ().equals (lfoParameters.get (this.tags.retriggerParam ())));
        return Optional.of (modulator);
    }


    /**
     * Get the waveform for the type of a low frequency oscillator.
     *
     * @param type The type of the oscillator
     * @return The waveform, empty if the type is not supported (e.g. 'multi')
     */
    private static Optional<LfoWaveform> getLfoWaveform (final String type)
    {
        return switch (type)
        {
            case "sine" -> Optional.of (LfoWaveform.SINE);
            case "triangle" -> Optional.of (LfoWaveform.TRIANGLE);
            case "rectangle", "square" -> Optional.of (LfoWaveform.SQUARE);
            // The sawtooth starts at the maximum and decreases
            case LFO_SAWTOOTH -> Optional.of (LfoWaveform.SAWTOOTH_DOWN);
            case "random" -> Optional.of (LfoWaveform.RANDOM);
            default -> Optional.empty ();
        };
    }


    /**
     * Check if the intensity of an internal modulator is controlled by an external modulator.
     *
     * @param modulatorIndex The index of the internal modulator
     * @param extModulators All external modulators of the group
     * @return True if the intensity is controlled
     */
    private boolean isIntensityModulated (final int modulatorIndex, final List<ExternalModulation> extModulators)
    {
        if (modulatorIndex < 0)
            return false;
        for (final ExternalModulation extModulator: extModulators)
            if (this.tags.intensityValue ().equals (extModulator.target) && extModulator.targetIndex == modulatorIndex)
                return true;
        return false;
    }


    /**
     * Copy the settings of a low frequency oscillator modulator.
     *
     * @param source The modulator to copy, might be null
     * @param destination The modulator to set
     */
    private static void applyLfoModulator (final ILfoModulator source, final ILfoModulator destination)
    {
        if (source == null)
            return;
        destination.setDepth (source.getDepth ());
        destination.setSource (source.getSource ());
    }


    /**
     * Applies the modulations of the filter cutoff to the filter of the group.
     *
     * @param groupFilter The filter of the group
     * @param extModulators All external modulators of the group
     * @param lfoModulators The low frequency oscillator modulators of the group
     */
    private void applyFilterModulation (final GroupFilter groupFilter, final List<ExternalModulation> extModulators, final Map<String, ILfoModulator> lfoModulators)
    {
        final IFilter filter = groupFilter.filter ();
        applyLfoModulator (lfoModulators.get (this.tags.targetFilterCutoffValue ()), filter.getCutoffLfoModulator ());

        for (final ExternalModulation extModulator: extModulators)
        {
            // Kontakt 2 can have several filters, check that the modulation targets the used one
            if (!this.tags.targetFilterCutoffValue ().equals (extModulator.target) || (extModulator.targetIndex >= 0 && groupFilter.effectIndex () >= 0 && extModulator.targetIndex != groupFilter.effectIndex ()))
                continue;

            final double depth = Math.clamp (extModulator.intensity, -1.0, 1.0);
            if (this.tags.velocityValue ().equals (extModulator.source))
                filter.getCutoffVelocityModulator ().setDepth (depth);
            else if (this.tags.keyPositionValue ().equals (extModulator.source))
                filter.setCutoffKeyTracking (depth);
            else if (this.tags.midiCCValue ().equals (extModulator.source) && extModulator.ccNumber == 1)
                filter.getCutoffModWheelModulator ().setDepth (depth);
        }
    }


    /**
     * Check if a modulator can be applied. This is always the case except for a modulation of the
     * cutoff of a different filter than the one which is used for the group.
     *
     * @param modulatorElement The modulator element
     * @param target The modulation target
     * @param filterEffectIndex The index of the effect slot which contains the filter of the group,
     *            -1 if not known
     * @return True if the modulator can be applied
     */
    private boolean isModulatingGroupFilter (final Element modulatorElement, final String target, final int filterEffectIndex)
    {
        if (!this.tags.targetFilterCutoffValue ().equals (target) || filterEffectIndex < 0)
            return true;
        final int targetIndex = this.getModulationTargetIndex (modulatorElement);
        return targetIndex < 0 || targetIndex == filterEffectIndex;
    }


    /**
     * Reads the filter settings from a group element. Kontakt 1 stores one filter directly in the
     * group, Kontakt 2 stores filters in the insert effect slots of the group.
     *
     * @param groupElement The group element
     * @return The filter if one is found
     */
    private Optional<GroupFilter> readFilter (final Element groupElement)
    {
        final Element filterElement = XMLUtils.getChildElementByName (groupElement, this.tags.groupFilter ());
        if (filterElement != null)
        {
            final Map<String, String> valueMap = this.readValueMap (filterElement);
            if (this.tags.yes ().equals (valueMap.get (this.tags.bypassParam ())))
                return Optional.empty ();
            final Optional<IFilter> filter = this.createFilter (valueMap.get (this.tags.filterType ()), valueMap);
            return filter.isPresent () ? Optional.of (new GroupFilter (filter.get (), -1)) : Optional.empty ();
        }

        final Element insertEffectsElement = XMLUtils.getChildElementByName (groupElement, this.tags.groupInsertEffectsElement ());
        if (insertEffectsElement == null)
            return Optional.empty ();
        // Several filters can be chained. Since only one filter is supported, a low-pass is
        // preferred since it has the most influence on the sound, otherwise the first one is used
        Optional<GroupFilter> firstFilter = Optional.empty ();
        for (final Element effectElement: XMLUtils.getChildElementsByName (insertEffectsElement, this.tags.effectElement ()))
        {
            final Element effectFilterElement = XMLUtils.getChildElementByName (effectElement, this.tags.groupFilter ());
            if (effectFilterElement == null || this.hasNameValuePairs (effectElement, this.tags.bypassParam (), this.tags.yes ()))
                continue;
            final Optional<IFilter> filter = this.createFilter (effectFilterElement.getAttribute (this.tags.filterType ()), this.readValueMap (effectFilterElement));
            if (filter.isEmpty ())
                continue;
            final GroupFilter groupFilter = new GroupFilter (filter.get (), XMLUtils.getIntegerAttribute (effectElement, this.tags.indexAttribute (), -1));
            if (filter.get ().getType () == FilterType.LOW_PASS)
                return Optional.of (groupFilter);
            if (firstFilter.isEmpty ())
                firstFilter = Optional.of (groupFilter);
        }
        return firstFilter;
    }


    /**
     * Creates a filter.
     *
     * @param type The Kontakt filter type, e.g. 'lp4pole'
     * @param valueMap The parameters of the filter
     * @return The filter or empty if the type is not supported
     */
    private Optional<IFilter> createFilter (final String type, final Map<String, String> valueMap)
    {
        if (type == null || type.isBlank ())
            return Optional.empty ();

        final FilterType filterType;
        final int poles;
        if (PRO52_FILTER_TYPE.equals (type) || LADDER_FILTER_TYPE.equals (type))
        {
            // The filter of the Pro-52 and the 4-stage ladder filter are 4 pole low-passes
            filterType = FilterType.LOW_PASS;
            poles = 4;
        }
        else
        {
            final Matcher matcher = FILTER_TYPE_PATTERN.matcher (type);
            if (!matcher.matches ())
            {
                // Effects like an equalizer can be placed in the filter slot as well
                if (!EFFECT_TYPE_PATTERN.matcher (type).matches ())
                    this.notifier.logError ("IDS_NKI_UNKNOWN_FILTER_TYPE", type);
                return Optional.empty ();
            }
            switch (matcher.group (1))
            {
                case "lp":
                    filterType = FilterType.LOW_PASS;
                    break;
                case "hp":
                    filterType = FilterType.HIGH_PASS;
                    break;
                case "bp":
                    filterType = FilterType.BAND_PASS;
                    break;
                case "br":
                    filterType = FilterType.BAND_REJECTION;
                    break;
                default:
                    return Optional.empty ();
            }
            poles = Integer.parseInt (matcher.group (2));
        }

        final double cutoff = denormalizeCutoff (type, filterType, parseDouble (valueMap.get ("cutoff"), 1.0));
        final double resonance = parseDouble (valueMap.get ("resonance"), 0);
        return Optional.of (new DefaultFilter (filterType, poles, cutoff, resonance));
    }


    /**
     * Converts a normalized cutoff value to Hertz. The curve depends on the filter type.
     *
     * @param type The Kontakt filter type, e.g. 'lp4pole'
     * @param filterType The filter type
     * @param normalizedCutoff The normalized cutoff in the range of [0..1]
     * @return The cutoff in Hertz
     */
    private static double denormalizeCutoff (final String type, final FilterType filterType, final double normalizedCutoff)
    {
        final double value = Math.clamp (normalizedCutoff, 0, 1);

        // The cutoff of the Pro-52 and the ladder filter covers 100 semi-tones starting at MIDI
        // note
        // 20 (26Hz)
        if (PRO52_FILTER_TYPE.equals (type) || LADDER_FILTER_TYPE.equals (type))
            return 440.0 * Math.pow (2, (20.0 + value * 100.0 - 69.0) / 12.0);

        // Only the low-pass filters use a different curve
        final double [] cutoffs = filterType == FilterType.LOW_PASS ? LOW_PASS_CUTOFFS : HIGH_PASS_CUTOFFS;
        final double position = value * (cutoffs.length - 1);
        final int index = Math.min ((int) position, cutoffs.length - 2);
        return cutoffs[index] * Math.pow (cutoffs[index + 1] / cutoffs[index], position - index);
    }


    /**
     * Reads a modulator from a modulator XML element.
     *
     * @param envElement The modulator XML element
     * @return The IModulator, if modulator could be read successfully, otherwise null
     */
    private Optional<IEnvelopeModulator> readModulatorFromElement (final Element envElement)
    {
        final String envType = envElement.getAttribute (this.tags.envTypeAttribute ());
        final Map<String, String> envParams = this.readValueMap (envElement);
        if (this.tags.dbdEnvTypeValue ().equals (envType))
            return this.readDecayBreakDecayEnvelope (envParams);
        final boolean isAHD = this.tags.ahdEnvTypeValue ().equals (envType);
        if (!isAHD && !this.tags.ahdsrEnvTypeValue ().equals (envType))
            return Optional.empty ();

        try
        {
            final IEnvelopeModulator modulator = new DefaultEnvelopeModulator (0);
            final IEnvelope env = modulator.getSource ();

            // The attack curve is not present in the envelopes of Kontakt 1.0 (version 0.50)
            env.setAttackSlope (-parseDouble (envParams.get (this.tags.attackCurveParam ()), 0));
            env.setAttackTime (getDouble (envParams, this.tags.attackParam ()) / 1000.0d);
            env.setHoldTime (AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.holdParam ()) / 1000.0d);
            env.setDecayTime (AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.decayParam ()) / 1000.0d);

            if (!isAHD)
            {
                env.setSustainLevel (AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.sustainParam ()));
                env.setReleaseTime (AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.releaseParam ()) / 1000.0d);
            }
            return Optional.of (modulator);
        }
        catch (final ValueNotAvailableException _)
        {
            return Optional.empty ();
        }
    }


    /**
     * Reads a decay-break-decay envelope. It starts at the maximum level, falls to the break level
     * during the first decay and from there to zero during the second decay, independent of the
     * note being held. Since the model does not support this shape it is approximated by an
     * envelope without attack and sustain which decays during the sum of both decay times. This
     * keeps the end level of zero (e.g. no lasting detune of a pitch envelope).
     *
     * @param envParams The parameters of the envelope
     * @return The envelope if all parameters are present
     */
    private Optional<IEnvelopeModulator> readDecayBreakDecayEnvelope (final Map<String, String> envParams)
    {
        try
        {
            final double decay1 = AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.decay1Param ()) / 1000.0d;
            final double breakLevel = AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.breakParam ());
            final double decay2 = AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.decay2Param ()) / 1000.0d;

            final IEnvelopeModulator modulator = new DefaultEnvelopeModulator (0);
            final IEnvelope env = modulator.getSource ();
            env.setAttackTime (0);
            env.setHoldTime (0);
            env.setDecayTime (breakLevel == 0 ? decay1 : decay1 + decay2);
            env.setSustainLevel (0);
            env.setReleaseTime (0);
            return Optional.of (modulator);
        }
        catch (final ValueNotAvailableException _)
        {
            return Optional.empty ();
        }
    }


    /**
     * Returns a String value from a value map.
     *
     * @param valueMap The value
     * @param valueName The value's name
     * @return The String value
     * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
     */
    private static String getString (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException (valueName);
        return valueStr;
    }


    /**
     * Returns an integer value from a value map.
     *
     * @param valueMap The value
     * @param valueName The value's name
     * @return The integer value
     * @throws ValueNotAvailableException indicates that the valueName is not in the valueMap
     */
    private static int getInt (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException (valueName);
        return Integer.parseInt (valueStr);
    }


    /**
     * Returns a double value from a value map.
     *
     * @param valueMap The value
     * @param valueName The value's name
     * @return The double value
     * @throws ValueNotAvailableException Indicates that the valueName is not in the valueMap
     */
    private static double getDouble (final Map<String, String> valueMap, final String valueName) throws ValueNotAvailableException
    {
        final String valueStr = valueMap.get (valueName);
        if (valueStr == null)
            throw new ValueNotAvailableException (valueName);
        return Double.parseDouble (valueStr);
    }


    /**
     * Parse a double value.
     *
     * @param value The text to parse, might be null
     * @param defaultValue The value to return if the text is not present or cannot be parsed
     * @return The parsed value
     */
    private static double parseDouble (final String value, final double defaultValue)
    {
        if (value == null || value.isBlank ())
            return defaultValue;
        try
        {
            return Double.parseDouble (value.trim ());
        }
        catch (final NumberFormatException _)
        {
            return defaultValue;
        }
    }


    /**
     * Retrieves the target from the given modulator element.
     *
     * @param modulator The modulator element
     * @return The target value, null if not present
     */
    protected abstract Optional<String> getModulationTarget (Element modulator);


    /**
     * Retrieves the intensity from the given modulator element.
     *
     * @param modulator The modulator element
     * @return The intensity value, 0 if not present or could parsed
     */
    protected abstract double getModulationIntensity (Element modulator);


    /**
     * Retrieves the index of the modulated effect (e.g. one of several filters) from the given
     * modulator element.
     *
     * @param modulator The modulator element
     * @return The index or -1 if not present
     */
    protected int getModulationTargetIndex (final Element modulator)
    {
        return -1;
    }


    /**
     * Checks if an element has a value map containing a required set of (name, value) pairs.
     *
     * @param element The element
     * @param nameValuePairs A number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return True if the element has all name value pairs in its value map, false else.
     */
    protected boolean hasNameValuePairs (final Element element, final String... nameValuePairs)
    {
        if (nameValuePairs == null)
            return true;

        final Map<String, String> parameters = this.readValueMap (element);
        for (int idx = 0; idx < nameValuePairs.length; idx += 2)
        {
            final String name = nameValuePairs[idx];
            if (parameters.containsKey (name))
            {
                final int valueIdx = idx + 1;
                if (valueIdx < nameValuePairs.length)
                {
                    final String value = nameValuePairs[valueIdx];
                    if (!parameters.get (name).equals (value))
                        return false;
                }
            }
            else
                return false;
        }
        return true;
    }


    /**
     * Finds a child element of a given parent element that has a value map containing a required
     * set of (name, value) pairs.
     *
     * @param parentElement The parent element
     * @param elementNameToBeFound The name of the element to be found
     * @param nameValuePairs A number of name, value pairs, e.g. "volume", "1", "bypass", "no"
     * @return The element with the value name pairs. If none could be found, null is returned.
     */
    protected Optional<Element> findElementWithParameters (final Element parentElement, final String elementNameToBeFound, final String... nameValuePairs)
    {
        for (final Element elementOfInterest: XMLUtils.getChildElementsByName (parentElement, elementNameToBeFound))
        {
            final boolean doesMatch = this.hasNameValuePairs (elementOfInterest, nameValuePairs);
            if (doesMatch)
                return Optional.of (elementOfInterest);
        }
        return Optional.empty ();
    }


    /**
     * Reads the pitch bend configuration from the external modulators of a group element.
     *
     * @param extModulators All external modulators of the group
     * @return The number of semi-tones (up=down)
     */
    private int readGroupPitchBend (final List<ExternalModulation> extModulators)
    {
        for (final ExternalModulation extModulator: extModulators)
            if (this.tags.pitchBendValue ().equals (extModulator.source) && this.tags.pitchValue ().equals (extModulator.target))
                return Math.clamp ((int) Math.round (extModulator.intensity * 1200), -9600, 9600);
        return -1;
    }


    /**
     * Reads the value by which the amplitude is modulated by the velocity from a group element.
     *
     * @param extModulators All external modulators of the group
     * @return The number of modulation (0..1)
     */
    private double readGroupAmplitudeVelocityModulator (final List<ExternalModulation> extModulators)
    {
        for (final ExternalModulation extModulator: extModulators)
            if (this.tags.velocityValue ().equals (extModulator.source) && this.tags.volumeValue ().equals (extModulator.target))
                return extModulator.intensity;
        return -1;
    }


    /**
     * Read all external not bypassed modulators from a group element.
     *
     * @param groupElement The group element to read from
     * @return The active modulators
     */
    private List<ExternalModulation> readExtModulators (final Element groupElement)
    {
        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.extModulatorsElement ());
        if (modulatorsElement == null)
            return Collections.emptyList ();

        final List<ExternalModulation> extModulators = new ArrayList<> ();
        for (final Element modulator: XMLUtils.getChildElementsByName (modulatorsElement, this.tags.extModulatorElement ()))
        {
            final Map<String, String> modulatorParams = this.readValueMap (modulator);
            if (modulatorParams == null)
                continue;
            final String source = modulatorParams.get (this.tags.sourceParam ());
            if (source == null)
                continue;
            final String target = modulatorParams.get (this.tags.targetParam ());
            if (target == null)
            {
                // V2 has (multiple) targets in sub-elemnts
                final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
                if (targetsElement == null)
                    continue;
                for (final Element targetElement: XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false))
                    this.createExtModulator (extModulators, source, modulatorParams, this.readValueMap (targetElement));
            }
            else
                this.createExtModulator (extModulators, source, modulatorParams, modulatorParams);
        }

        return extModulators;
    }


    private void createExtModulator (final List<ExternalModulation> extModulators, final String source, final Map<String, String> modulatorElementParams, final Map<String, String> targetElementParams)
    {
        // Ignore bypassed modulators
        final String bypass = modulatorElementParams.get (this.tags.bypassParam ());
        if (bypass != null && bypass.equals ("yes"))
            return;

        final ExternalModulation extModulator = new ExternalModulation ();
        extModulator.source = source;
        extModulator.target = targetElementParams.get (this.tags.targetParam ());
        // Kontakt 2 stores the index of the modulated effect or internal modulator in the target,
        // Kontakt 1 only the index of the modulated internal modulator in the modulator
        extModulator.targetIndex = parseIndex (targetElementParams.get (this.tags.targetObjectIndexParam ()));
        if (extModulator.targetIndex < 0)
            extModulator.targetIndex = parseIndex (targetElementParams.get (this.tags.targetModIndexParam ()));
        extModulator.ccNumber = parseIndex (modulatorElementParams.get (this.tags.ccNumberParam ()));
        try
        {
            extModulator.intensity = Double.parseDouble (targetElementParams.get (this.tags.intensityParam ()));
        }
        catch (final NumberFormatException _)
        {
            return;
        }
        extModulators.add (extModulator);
    }


    /**
     * Finds a group's zone elements.
     *
     * @param groupElement The group element
     * @param zoneElements The zone elements from which to find the zones belonging to the group
     * @return An array of zone elements belonging to the group element
     */
    private Element [] findGroupZones (final Element groupElement, final List<Element> zoneElements)
    {
        final int index = XMLUtils.getIntegerAttribute (groupElement, this.tags.indexAttribute (), -1);
        if (index == -1 || zoneElements == null)
            return new Element [0];

        final List<Element> matchingZoneElements = new ArrayList<> ();
        for (final Element zoneElement: zoneElements)
        {
            final int groupIndex = XMLUtils.getIntegerAttribute (zoneElement, this.tags.groupIndexAttribute (), -1);
            if (groupIndex == index)
                matchingZoneElements.add (zoneElement);
        }
        return matchingZoneElements.toArray (new Element [matchingZoneElements.size ()]);
    }


    private static String formatDouble (final double value)
    {
        return String.format (Locale.US, "%.6f", Double.valueOf (value));
    }


    private static IOException error (final String encodedSampleFileName)
    {
        return new IOException (Functions.getMessage ("IDS_NKI_UNEXPECTED_STATE", encodedSampleFileName));
    }


    private static String readFolder (final StringReader reader, final String encodedSampleFileName) throws IOException
    {
        final char [] lengthBuffer = new char [3];
        if (reader.read (lengthBuffer) != lengthBuffer.length)
            throw error (encodedSampleFileName);
        final int length = Integer.parseInt (new String (lengthBuffer));
        final char [] folder = new char [length];
        if (reader.read (folder) != length)
            throw error (encodedSampleFileName);
        return new String (folder);
    }


    private static class ExternalModulation
    {
        String source;
        String target;
        double intensity;
        int    targetIndex = -1;
        int    ccNumber    = -1;
    }


    /**
     * The filter of a group.
     *
     * @param filter The filter
     * @param effectIndex The index of the insert effect slot which contains the filter (only
     *            Kontakt 2), otherwise -1
     */
    private record GroupFilter (IFilter filter, int effectIndex)
    {
        // Intentionally empty
    }
}
