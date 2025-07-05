// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively SFZ multi-sample files in folders. Files must end with <i>.sfz</i>.
 *
 * @author Jürgen Moßgraber
 */
public class SfzDetector extends AbstractDetector<SfzDetectorUI>
{
    private static final Pattern                 HEADER_PATTERN    = Pattern.compile ("<([a-z]+)>([^<]*)", Pattern.DOTALL);
    private static final Pattern                 ATTRIBUTE_PATTERN = Pattern.compile ("(\\b\\w+)=(.*?(?=\\s\\w+=|//|$))", Pattern.DOTALL);
    private static final Map<String, FilterType> FILTER_TYPE_MAP   = new HashMap<> ();
    private static final Map<String, LoopType>   LOOP_TYPE_MAP     = new HashMap<> (3);

    static
    {
        FILTER_TYPE_MAP.put ("lpf", FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put ("hpf", FilterType.HIGH_PASS);
        FILTER_TYPE_MAP.put ("bpf", FilterType.BAND_PASS);
        FILTER_TYPE_MAP.put ("brf", FilterType.BAND_REJECTION);

        LOOP_TYPE_MAP.put ("forward", LoopType.FORWARDS);
        LOOP_TYPE_MAP.put ("backward", LoopType.BACKWARDS);
        LOOP_TYPE_MAP.put ("alternate", LoopType.ALTERNATING);
    }

    private Map<String, String> globalAttributes = Collections.emptyMap ();
    private Map<String, String> masterAttributes = Collections.emptyMap ();
    private Map<String, String> groupAttributes  = Collections.emptyMap ();
    private Map<String, String> regionAttributes = Collections.emptyMap ();
    private final Set<String>   processedOpcodes = new HashSet<> ();
    private final Set<String>   allOpcodes       = new HashSet<> ();


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SfzDetector (final INotifier notifier)
    {
        super ("SFZ", "SFZ", notifier, new SfzDetectorUI ("SFZ"), ".sfz");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final String content = this.loadTextFileWithReferences (file);
            this.clearAttributes ();
            return this.parseMetadataFile (file, content);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Loads a text file in UTF-8 encoding. If UTF-8 fails a string is created anyway but with
     * unspecified behavior. If the file contains #include statements the referenced file is loaded
     * as well and included at the position.
     *
     * @param file The file to load
     * @return The loaded text
     * @throws IOException Could not load the file
     */
    private String loadTextFileWithReferences (final File file) throws IOException
    {
        final Map<String, String> cachedContents = new HashMap<> ();
        final Set<String> processingFiles = new HashSet<> ();
        return this.parseFile (file, cachedContents, processingFiles);
    }


    private String parseFile (final File file, final Map<String, String> cachedContents, final Set<String> processingFiles) throws IOException
    {
        final String absolutePath = file.getAbsolutePath ();

        // Check if the content is already cached
        if (cachedContents.containsKey (absolutePath))
            return cachedContents.get (absolutePath);

        // Check for endless loop
        if (processingFiles.contains (absolutePath))
            throw new IOException (Functions.getMessage ("IDS_SFZ_INCLUDE_LOOP_DETECTED", absolutePath));

        // Mark the file as currently processing
        processingFiles.add (absolutePath);

        final StringBuilder content = new StringBuilder ();
        final Iterator<String> iterator = this.loadTextFile (file).lines ().iterator ();
        while (iterator.hasNext ())
        {
            final String line = iterator.next ();
            if (line.startsWith ("#include"))
            {
                final String includedFilePath = extractIncludedFilePath (line);
                if (includedFilePath == null)
                    throw new IOException (Functions.getMessage ("IDS_SFZ_MALFORMED_INCLUDE", line));

                // Recursively parse the included file
                final File includedFile = new File (file.getParent (), includedFilePath);
                content.append (this.parseFile (includedFile, cachedContents, processingFiles));
            }
            else
                content.append (line).append ('\n');
        }

        // Cache the content of the current file in case it is included multiple times...
        cachedContents.put (absolutePath, content.toString ());

        // Mark the file as processed
        processingFiles.remove (absolutePath);
        return content.toString ();
    }


    private static String extractIncludedFilePath (final String line)
    {
        final int start = line.indexOf ('"');
        final int end = line.lastIndexOf ('"');
        if (start < 0 || end < 0 || start == end)
            return null;
        return line.substring (start + 1, end);
    }


    private void clearAttributes ()
    {
        this.globalAttributes = Collections.emptyMap ();
        this.masterAttributes = Collections.emptyMap ();
        this.groupAttributes = Collections.emptyMap ();
        this.regionAttributes = Collections.emptyMap ();
        this.processedOpcodes.clear ();
        this.allOpcodes.clear ();
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The file
     * @param content The content to parse
     * @return The parsed multi-sample source
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String content)
    {
        final List<Pair<String, Map<String, String>>> result = parseSfz (content);
        if (result.isEmpty ())
        {
            this.notifier.logError ("IDS_ERR_COULD_NOT_DETECT_MULTI_SAMPLE");
            return Collections.emptyList ();
        }

        if (this.settingsConfiguration.logUnsupportedOpcodes ())
            this.printUnsupportedOpcodes (this.diffOpcodes ());

        String name = FileUtils.getNameWithoutType (multiSampleFile);
        final String n = this.settingsConfiguration.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, n);

        final List<IGroup> groups = this.parseGroups (multiSampleFile.getParentFile (), result);
        if (groups.isEmpty ())
        {
            this.notifier.logError ("IDS_ERR_COULD_NOT_DETECT_MULTI_SAMPLE");
            return Collections.emptyList ();
        }

        final Optional<String> globalName = this.getAttribute (SfzOpcode.GLOBAL_LABEL);
        if (globalName.isPresent ())
            name = globalName.get ();

        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));

        final IMetadata metadata = multisampleSource.getMetadata ();
        this.createMetadata (metadata, this.getFirstSample (groups), parts);

        multisampleSource.setGroups (groups);
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parses all groups (= regions in SFZ).
     *
     * @param basePath The path where the SFZ file is located, this is the base path for samples
     * @param headers All parsed headers with their key/value pairs
     * @return The parsed groups
     */
    private List<IGroup> parseGroups (final File basePath, final List<Pair<String, Map<String, String>>> headers)
    {
        File sampleBaseFolder = basePath;

        final List<IGroup> groups = new ArrayList<> ();
        IGroup group = new DefaultGroup ();
        for (final Pair<String, Map<String, String>> pair: headers)
        {
            final Map<String, String> attributes = pair.getValue ();
            this.allOpcodes.addAll (attributes.keySet ());

            switch (pair.getKey ())
            {
                case SfzHeader.CONTROL:
                    this.processedOpcodes.add (SfzOpcode.DEFAULT_PATH);
                    final String defaultPath = attributes.get (SfzOpcode.DEFAULT_PATH);
                    if (defaultPath != null)
                    {
                        // The default path might be relative, so make sure it is fully
                        // canonical otherwise samples will not be found
                        sampleBaseFolder = FileUtils.makeCanonical (new File (basePath, defaultPath.replace ('\\', '/')));
                        if (!sampleBaseFolder.exists ())
                        {
                            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_FOLDER_DOES_NOT_EXIST", sampleBaseFolder.getAbsolutePath ());
                            return groups;
                        }
                    }
                    break;

                case SfzHeader.GLOBAL:
                    this.globalAttributes = attributes;
                    break;

                case SfzHeader.MASTER:
                    this.masterAttributes = attributes;
                    break;

                case SfzHeader.GROUP:
                    if (!group.getSampleZones ().isEmpty ())
                        groups.add (group);
                    group = new DefaultGroup ();

                    this.groupAttributes = attributes;

                    final Optional<String> groupLabel = this.getAttribute (SfzOpcode.GROUP_LABEL);
                    if (groupLabel.isPresent ())
                        group.setName (groupLabel.get ());

                    final TriggerType triggerType = this.getTriggerType (this.getAttribute (SfzOpcode.TRIGGER));
                    if (triggerType != TriggerType.ATTACK)
                        group.setTrigger (triggerType);

                    // We do not need the value but mark it as processed
                    this.getIntegerValue (SfzOpcode.SEQ_LENGTH);

                    break;

                case SfzHeader.REGION:
                    this.regionAttributes = attributes;

                    final Optional<String> sampleName = this.getAttribute (SfzOpcode.SAMPLE);
                    if (sampleName.isEmpty ())
                        continue;

                    final File sampleFile = this.createCanonicalFile (sampleBaseFolder, sampleName.get ());
                    try
                    {
                        final ISampleZone sampleZone = this.createSampleZone (sampleFile);
                        this.parseRegion (sampleZone);
                        this.readMissingValues (sampleZone);
                        group.addSampleZone (sampleZone);
                    }
                    catch (final FileNotFoundException ex)
                    {
                        this.notifier.logError ("IDS_ERR_COULD_NOT_CREATE_ZONE", ex.getMessage ());
                    }
                    catch (final IOException ex)
                    {
                        this.notifier.logError (ex);
                    }
                    break;

                default:
                    // Other headers are not supported
                    break;
            }
        }

        // Don't forget to add the last group
        if (!group.getSampleZones ().isEmpty ())
            groups.add (group);

        // Fix empty names
        for (int i = 0; i < groups.size (); i++)
        {
            group = groups.get (i);
            final String name = group.getName ();
            if (name == null || name.isBlank ())
                group.setName ("Group " + (i + 1));
        }

        return groups;
    }


    /**
     * Parse all SFZ header with their key/value pairs.
     *
     * @param content The text to parse
     * @return The parse headers and key/value pairs
     */
    private static List<Pair<String, Map<String, String>>> parseSfz (final String content)
    {
        final List<Pair<String, Map<String, String>>> headersWithOpCodes = new ArrayList<> ();

        final Matcher blockMatcher = HEADER_PATTERN.matcher (content);
        while (blockMatcher.find ())
        {
            final String headerName = blockMatcher.group (1);
            if (headerName == null || headerName.isBlank ())
                continue;

            final String attributeBlock = blockMatcher.group (2);
            final Map<String, String> attributes = new HashMap<> ();
            if (attributeBlock != null && !attributeBlock.isBlank ())
            {
                final Matcher keyValueMatcher = ATTRIBUTE_PATTERN.matcher (attributeBlock);
                while (keyValueMatcher.find ())
                {
                    final String key = keyValueMatcher.group (1);
                    final String value = keyValueMatcher.group (2);
                    if (key != null && !key.isBlank () && value != null)
                        attributes.put (key.trim (), value.trim ());
                }
            }

            headersWithOpCodes.add (new Pair<> (headerName, attributes));
        }

        return headersWithOpCodes;
    }


    /**
     * Parses the key/values of a region header.
     *
     * @param sampleMetadata Where to store the parsed information
     */
    private void parseRegion (final ISampleZone sampleMetadata)
    {
        final TriggerType triggerType = this.getTriggerType (this.getAttribute (SfzOpcode.TRIGGER));
        if (triggerType != TriggerType.ATTACK)
            sampleMetadata.setTrigger (triggerType);

        final Optional<String> direction = this.getAttribute (SfzOpcode.DIRECTION);
        if (direction.isPresent ())
            sampleMetadata.setReversed ("reverse".equals (direction.get ()));
        final int sequencePosition = this.getIntegerValue (SfzOpcode.SEQ_POSITION);
        if (sequencePosition > 0)
        {
            sampleMetadata.setPlayLogic (PlayLogic.ROUND_ROBIN);
            sampleMetadata.setSequencePosition (sequencePosition);
        }

        final int offset = this.getIntegerValue (SfzOpcode.OFFSET);
        if (offset >= 0)
            sampleMetadata.setStart (offset);
        final int end = this.getIntegerValue (SfzOpcode.END);
        if (end >= 0)
            sampleMetadata.setStop (end);

        ////////////////////////////////////////////////////////////
        // Key range

        final int key = this.getKeyValue (SfzOpcode.KEY);
        if (key >= 0)
        {
            sampleMetadata.setKeyRoot (key);
            sampleMetadata.setKeyLow (key);
            sampleMetadata.setKeyHigh (key);
        }

        // Lower bounds including cross-fade
        int lowKey = this.getKeyValue (SfzOpcode.XF_IN_LO_KEY);
        if (lowKey < 0)
            lowKey = this.getKeyValue (SfzOpcode.LO_KEY);
        else
        {
            final int xfInHighKey = this.getKeyValue (SfzOpcode.XF_IN_HI_KEY);
            if (xfInHighKey >= 0)
                sampleMetadata.setNoteCrossfadeLow (xfInHighKey - lowKey);
        }
        if (lowKey >= 0)
            sampleMetadata.setKeyLow (lowKey);

        // Upper bounds including cross-fade
        int highKey = this.getKeyValue (SfzOpcode.XF_OUT_HI_KEY);
        if (highKey < 0)
            highKey = this.getKeyValue (SfzOpcode.HI_KEY);
        else
        {
            final int xfOutLowKey = this.getKeyValue (SfzOpcode.XF_OUT_LO_KEY);
            if (xfOutLowKey >= 0)
                sampleMetadata.setNoteCrossfadeHigh (highKey - xfOutLowKey);
        }
        if (highKey >= 0)
            sampleMetadata.setKeyHigh (highKey);

        // The center key
        final int pitchKeyCenter = this.getKeyValue (SfzOpcode.PITCH_KEY_CENTER);
        if (pitchKeyCenter >= 0)
            sampleMetadata.setKeyRoot (pitchKeyCenter);

        ////////////////////////////////////////////////////////////
        // Velocity

        // Lower bounds including cross-fade
        int lowVel = this.getIntegerValue (SfzOpcode.XF_IN_LO_VEL);
        if (lowVel < 0)
            lowVel = this.getIntegerValue (SfzOpcode.LO_VEL);
        else
        {
            final int xfInHighVel = this.getIntegerValue (SfzOpcode.XF_IN_HI_VEL);
            if (xfInHighVel >= 0)
                sampleMetadata.setNoteCrossfadeLow (xfInHighVel - lowVel);
        }
        if (lowVel >= 0)
            sampleMetadata.setVelocityLow (lowVel);

        // Upper bounds including cross-fade
        int highVel = this.getIntegerValue (SfzOpcode.XF_OUT_HI_VEL);
        if (highVel < 0)
            highVel = this.getIntegerValue (SfzOpcode.HI_VEL);
        else
        {
            final int xfOutLowVel = this.getIntegerValue (SfzOpcode.XF_OUT_LO_VEL);
            if (xfOutLowVel >= 0)
                sampleMetadata.setNoteCrossfadeHigh (highVel - xfOutLowVel);
        }
        if (highVel >= 0)
            sampleMetadata.setVelocityHigh (highVel);

        ////////////////////////////////////////////////////////////
        // Sample Loop

        this.parseLoop (sampleMetadata);

        ////////////////////////////////////////////////////////////
        // Tune

        double tune = this.getDoubleValue (SfzOpcode.TUNE, 0);
        if (tune == 0)
            tune = this.getDoubleValue (SfzOpcode.PITCH, 0);
        sampleMetadata.setTune (Math.clamp (tune, -3600, 3600) / 100.0);

        final double pitchKeytrack = this.getDoubleValue (SfzOpcode.PITCH_KEYTRACK, 100);
        sampleMetadata.setKeyTracking (Math.clamp (pitchKeytrack, 0, 100) / 100.0);

        sampleMetadata.setBendUp (this.getIntegerValue (SfzOpcode.BEND_UP, 200));
        sampleMetadata.setBendDown (this.getIntegerValue (SfzOpcode.BEND_DOWN, -200));

        int envelopeDepth = this.getIntegerValue (SfzOpcode.PITCHEG_DEPTH, 0);
        if (envelopeDepth == 0)
            envelopeDepth = this.getIntegerValue (SfzOpcode.PITCH_DEPTH, 0);
        final IEnvelopeModulator pitchModulator = sampleMetadata.getPitchModulator ();
        pitchModulator.setDepth (envelopeDepth / (double) IEnvelope.MAX_ENVELOPE_DEPTH);

        final IEnvelope pitchEnvelope = pitchModulator.getSource ();
        pitchEnvelope.setDelayTime (this.getDoubleValue (SfzOpcode.PITCHEG_DELAY, SfzOpcode.PITCH_DELAY));
        pitchEnvelope.setAttackTime (this.getDoubleValue (SfzOpcode.PITCHEG_ATTACK, SfzOpcode.PITCH_ATTACK));
        pitchEnvelope.setHoldTime (this.getDoubleValue (SfzOpcode.PITCHEG_HOLD, SfzOpcode.PITCH_HOLD));
        pitchEnvelope.setDecayTime (this.getDoubleValue (SfzOpcode.PITCHEG_DECAY, SfzOpcode.PITCH_DECAY));
        pitchEnvelope.setReleaseTime (this.getDoubleValue (SfzOpcode.PITCHEG_RELEASE, SfzOpcode.PITCH_RELEASE));

        final double startValue = this.getDoubleValue (SfzOpcode.PITCHEG_START, SfzOpcode.PITCH_START);
        final double sustainValue = this.getDoubleValue (SfzOpcode.PITCHEG_SUSTAIN, SfzOpcode.PITCH_SUSTAIN);
        pitchEnvelope.setStartLevel (startValue < 0 ? -1 : startValue / 100.0);
        pitchEnvelope.setSustainLevel (sustainValue < 0 ? -1 : sustainValue / 100.0);

        pitchEnvelope.setAttackSlope (this.getDoubleValue (SfzOpcode.PITCHEG_ATTACK_SHAPE, 0) / 10.0);
        pitchEnvelope.setDecaySlope (this.getDoubleValue (SfzOpcode.PITCHEG_DECAY_SHAPE, 0) / 10.0);
        pitchEnvelope.setReleaseSlope (this.getDoubleValue (SfzOpcode.PITCHEG_RELEASE_SHAPE, 0) / 10.0);

        ////////////////////////////////////////////////////////////
        // Volume

        this.parseVolume (sampleMetadata);

        ////////////////////////////////////////////////////////////
        // Filter

        this.parseFilter (sampleMetadata);
    }


    private void parseFilter (final ISampleZone sampleZone)
    {
        double cutoff = this.getDoubleValue (SfzOpcode.CUTOFF, -1);
        final Optional<String> filterTypeAttribute = this.getAttribute (SfzOpcode.FILTER_TYPE);

        // Don't create a filter if there is no cutoff and no filter type
        if (cutoff < 0 && filterTypeAttribute.isEmpty ())
            return;

        if (cutoff < 0)
            cutoff = IFilter.MAX_FREQUENCY;

        final String filterTypeStr = filterTypeAttribute.isEmpty () ? "lpf_2p" : filterTypeAttribute.get ();
        if (filterTypeStr.length () < 6)
            return;

        FilterType filterType = FILTER_TYPE_MAP.get (filterTypeStr.substring (0, 3));
        // Unsupported filter type?
        if (filterType == null)
            filterType = FilterType.LOW_PASS;
        int poles;
        try
        {
            poles = Integer.parseInt (filterTypeStr.substring (4, 5));
            if (poles <= 0)
                poles = 2;
        }
        catch (final NumberFormatException ex)
        {
            poles = 2;
        }

        final double resonance = this.getDoubleValue (SfzOpcode.RESONANCE, 0);
        int envelopeDepth = this.getIntegerValue (SfzOpcode.FILEG_DEPTH, 0);
        if (envelopeDepth == 0)
            envelopeDepth = this.getIntegerValue (SfzOpcode.FIL_DEPTH, 0);

        final IFilter filter = new DefaultFilter (filterType, poles, cutoff, resonance / IFilter.MAX_RESONANCE);
        sampleZone.setFilter (filter);

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        cutoffModulator.setDepth (envelopeDepth / (double) IEnvelope.MAX_ENVELOPE_DEPTH);

        // Filter envelope
        final IEnvelope filterEnvelope = cutoffModulator.getSource ();
        filterEnvelope.setDelayTime (this.getDoubleValue (SfzOpcode.FILEG_DELAY, SfzOpcode.FIL_DELAY));
        filterEnvelope.setAttackTime (this.getDoubleValue (SfzOpcode.FILEG_ATTACK, SfzOpcode.FIL_ATTACK));
        filterEnvelope.setHoldTime (this.getDoubleValue (SfzOpcode.FILEG_HOLD, SfzOpcode.FIL_HOLD));
        filterEnvelope.setDecayTime (this.getDoubleValue (SfzOpcode.FILEG_DECAY, SfzOpcode.FIL_DECAY));
        filterEnvelope.setReleaseTime (this.getDoubleValue (SfzOpcode.FILEG_RELEASE, SfzOpcode.FIL_RELEASE));

        final double startValue = this.getDoubleValue (SfzOpcode.FILEG_START, SfzOpcode.FIL_START);
        final double sustainValue = this.getDoubleValue (SfzOpcode.FILEG_SUSTAIN, SfzOpcode.FIL_SUSTAIN);
        filterEnvelope.setStartLevel (startValue < 0 ? -1 : startValue / 100.0);
        filterEnvelope.setSustainLevel (sustainValue < 0 ? -1 : sustainValue / 100.0);

        filterEnvelope.setAttackSlope (this.getDoubleValue (SfzOpcode.FILEG_ATTACK_SHAPE, 0) / 10.0);
        filterEnvelope.setDecaySlope (this.getDoubleValue (SfzOpcode.FILEG_DECAY_SHAPE, 0) / 10.0);
        filterEnvelope.setReleaseSlope (this.getDoubleValue (SfzOpcode.FILEG_RELEASE_SHAPE, 0) / 10.0);

        // Filter velocity modulation
        final int filterVelocity = this.getIntegerValue (SfzOpcode.FIL_VELOCITY_TRACK, 0);
        filter.getCutoffVelocityModulator ().setDepth (filterVelocity / 9600.0);
    }


    /**
     * Parse the settings of the loop.
     *
     * @param sampleMetadata Where to store the data
     */
    private void parseLoop (final ISampleZone sampleMetadata)
    {
        final DefaultSampleLoop loop = new DefaultSampleLoop ();

        final Optional<String> loopMode = this.getAttribute (SfzOpcode.LOOP_MODE);
        if (loopMode.isPresent ())
            switch (loopMode.get ())
            {
                default:
                case "no_loop", "one_shot":
                    // No looping
                    return;

                case "loop_continuous", "loop_sustain":
                    final Optional<String> loopType = this.getAttribute (SfzOpcode.LOOP_TYPE);
                    if (loopType.isPresent ())
                    {
                        final LoopType type = LOOP_TYPE_MAP.get (loopType.get ());
                        if (type != null)
                            loop.setType (type);
                    }
                    break;
            }

        final int loopStart = this.getIntegerValue (SfzOpcode.LOOP_START, SfzOpcode.LOOPSTART);
        if (loopStart >= 0)
            loop.setStart (loopStart);
        final int loopEnd = this.getIntegerValue (SfzOpcode.LOOP_END, SfzOpcode.LOOPEND);
        if (loopEnd >= 0)
            loop.setEnd (loopEnd);

        final double crossfadeInSeconds = this.getDoubleValue (SfzOpcode.LOOP_CROSSFADE, 0);
        if (crossfadeInSeconds > 0)
            try
            {
                loop.setCrossfadeInSeconds (crossfadeInSeconds, sampleMetadata.getSampleData ().getAudioMetadata ().getSampleRate ());
            }
            catch (final IOException ex)
            {
                this.notifier.logError (ex);
            }

        // The loop might not have valid start and end set, in that case they will be read from the
        // WAV file
        sampleMetadata.addLoop (loop);
    }


    /**
     * Parse the parameters of the volume and amplitude envelope.
     *
     * @param sampleZone Where to store the data
     */
    private void parseVolume (final ISampleZone sampleZone)
    {
        sampleZone.setGain (this.getDoubleValue (SfzOpcode.VOLUME, 0));
        final double panning = this.getDoubleValue (SfzOpcode.PANNING, 0);
        sampleZone.setPanning (Math.clamp (panning, -100, 100) / 100.0);

        // Amplitude envelope

        final IEnvelope amplitudeEnvelope = sampleZone.getAmplitudeEnvelopeModulator ().getSource ();
        amplitudeEnvelope.setDelayTime (this.getDoubleValue (SfzOpcode.AMPEG_DELAY, SfzOpcode.AMP_DELAY));
        amplitudeEnvelope.setAttackTime (this.getDoubleValue (SfzOpcode.AMPEG_ATTACK, SfzOpcode.AMP_ATTACK));
        amplitudeEnvelope.setHoldTime (this.getDoubleValue (SfzOpcode.AMPEG_HOLD, SfzOpcode.AMP_HOLD));
        amplitudeEnvelope.setDecayTime (this.getDoubleValue (SfzOpcode.AMPEG_DECAY, SfzOpcode.AMP_DECAY));
        amplitudeEnvelope.setReleaseTime (this.getDoubleValue (SfzOpcode.AMPEG_RELEASE, SfzOpcode.AMP_RELEASE));

        final double startValue = this.getDoubleValue (SfzOpcode.AMPEG_START, SfzOpcode.AMP_START);
        final double sustainValue = this.getDoubleValue (SfzOpcode.AMPEG_SUSTAIN, SfzOpcode.AMP_SUSTAIN);
        amplitudeEnvelope.setStartLevel (startValue < 0 ? -1 : startValue / 100.0);
        amplitudeEnvelope.setSustainLevel (sustainValue < 0 ? -1 : sustainValue / 100.0);

        amplitudeEnvelope.setAttackSlope (this.getDoubleValue (SfzOpcode.AMPEG_ATTACK_SHAPE, 0) / 10.0);
        amplitudeEnvelope.setDecaySlope (this.getDoubleValue (SfzOpcode.AMPEG_DECAY_SHAPE, 0) / 10.0);
        amplitudeEnvelope.setReleaseSlope (this.getDoubleValue (SfzOpcode.AMPEG_RELEASE_SHAPE, 0) / 10.0);

        // Amplitude velocity modulation

        final double ampVelTrack = this.getDoubleValue (SfzOpcode.AMP_VELOCITY_TRACK, 100);
        sampleZone.getAmplitudeVelocityModulator ().setDepth (ampVelTrack / 100.0);
    }


    private TriggerType getTriggerType (final Optional<String> optTrigger)
    {
        if (!optTrigger.isPresent ())
            return TriggerType.ATTACK;

        final String trigger = optTrigger.get ();
        switch (trigger)
        {
            case "release":
                return TriggerType.RELEASE;

            case "release_key":
                this.notifier.logError ("IDS_NOTIFY_SFZ_PARTIALLY_UNSUPPORTED_TRIGGER", trigger);
                return TriggerType.RELEASE;

            case "first":
                return TriggerType.FIRST;

            case "legato":
                return TriggerType.LEGATO;

            default:
            case "attack":
                return TriggerType.ATTACK;
        }
    }


    /**
     * Calculate the difference between the supported and present opcodes.
     *
     * @return The unsupported opcodes which are present in the parsed SFZ file
     */
    private Set<String> diffOpcodes ()
    {
        final Set<String> unsupported = new TreeSet<> ();
        this.allOpcodes.forEach (attribute -> {
            if (!this.processedOpcodes.contains (attribute))
                unsupported.add (attribute);
        });
        this.allOpcodes.clear ();
        return unsupported;
    }


    /**
     * Formats and reports all unsupported opcodes.
     *
     * @param unsupportedOpcodes The unsupported opcodes
     */
    private void printUnsupportedOpcodes (final Set<String> unsupportedOpcodes)
    {
        final StringBuilder sb = new StringBuilder ();

        unsupportedOpcodes.forEach (attribute -> {
            if (!sb.isEmpty ())
                sb.append (", ");
            sb.append (attribute);
        });

        if (!sb.isEmpty ())
            this.notifier.logError ("IDS_NOTIFY_UNSUPPORTED_OPCODES", sb.toString ());
    }


    /**
     * Get the attribute key value for the given key. The value might be a MIDI note or a text (e.g.
     * c#2). The value is searched starting from region upwards to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @return The value or -1 if not found or is not an integer
     */
    private int getKeyValue (final String key)
    {
        final Optional<String> value = this.getAttribute (key);
        return value.isEmpty () ? -1 : NoteParser.parseNote (value.get ());
    }


    /**
     * Get the value of the first or second key, whichever is present. If none is present -1 is
     * returned.
     *
     * @param key1 The first key to check
     * @param key2 The second key to check if the first is not present
     * @return The value
     */
    private int getIntegerValue (final String key1, final String key2)
    {
        final int value = this.getIntegerValue (key1);
        return value < 0 ? this.getIntegerValue (key2) : value;
    }


    /**
     * Get the attribute integer value for the given key. The value is searched starting from region
     * upwards to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @return The value or -1 if not found or is not an integer
     */
    private int getIntegerValue (final String key)
    {
        return this.getIntegerValue (key, -1);
    }


    /**
     * Get the attribute integer value for the given key. The value is searched starting from region
     * upwards to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @param defaultValue The value to return if the key is not present or cannot be read
     * @return The value or -1 if not found or is not an integer
     */
    private int getIntegerValue (final String key, final int defaultValue)
    {
        final Optional<String> value = this.getAttribute (key);
        if (value.isEmpty ())
            return defaultValue;
        try
        {
            return Integer.parseInt (value.get ());
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    /**
     * Get the value of the first or second key, whichever is present. If none is present -1 is
     * returned.
     *
     * @param key1 The first key to check
     * @param key2 The second key to check if the first is not present
     * @return The value
     */
    private double getDoubleValue (final String key1, final String key2)
    {
        final double value = this.getDoubleValue (key1, -1);
        return value < 0 ? this.getDoubleValue (key2, -1) : value;
    }


    /**
     * Get the attribute double value for the given key. The value is searched starting from region
     * upwards to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @param defaultValue The value to return if the key is not present or cannot be read
     * @return The value or 0 if not found or is not a double
     */
    private double getDoubleValue (final String key, final double defaultValue)
    {
        final Optional<String> value = this.getAttribute (key);
        if (value.isEmpty ())
            return defaultValue;
        try
        {
            return Double.parseDouble (value.get ());
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    /**
     * Get the attribute value for the given key. The value is searched starting from the region
     * upwards to group, master and finally global.
     *
     * @param key The key of the value to lookup
     * @return The optional value or empty if not found
     */
    private Optional<String> getAttribute (final String key)
    {
        String value = this.regionAttributes.get (key);
        if (value == null)
        {
            value = this.groupAttributes.get (key);
            if (value == null)
            {
                value = this.masterAttributes.get (key);
                if (value == null)
                    value = this.globalAttributes.get (key);
            }
        }

        if (value != null)
            this.processedOpcodes.add (key);
        return Optional.ofNullable (value);
    }
}
