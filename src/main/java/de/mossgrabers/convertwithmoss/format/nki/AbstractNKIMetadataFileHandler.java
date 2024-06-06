// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.ValueNotAvailableException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.aiff.AiffFileSampleData;
import de.mossgrabers.convertwithmoss.file.ncw.NcwFileSampleData;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.nki.type.DecodedPath;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Base class for parsing the NKI XML structure.
 *
 * @author Jürgen Moßgraber
 * @author Philip Stolz
 */
public abstract class AbstractNKIMetadataFileHandler
{
    protected static final String                TEMPLATE_FOLDER     = "de/mossgrabers/convertwithmoss/templates/nki/";

    private static final String                  NULL_ENTRY          = "(null)";

    private static final Pattern                 FILTER_TYPE_PATTERN = Pattern.compile ("^(lp|hp|bp)(\\d+)pole$");

    private static final Map<FilterType, String> FILTER_PREFIXES     = new EnumMap<> (FilterType.class);
    static
    {
        FILTER_PREFIXES.put (FilterType.LOW_PASS, "lp");
        FILTER_PREFIXES.put (FilterType.HIGH_PASS, "hp");
        FILTER_PREFIXES.put (FilterType.BAND_PASS, "bp");
    }

    protected final AbstractTagsAndAttributes tags;
    protected final INotifier                 notifier;


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
     * Parses the metadata description file.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param content The XML content to parse
     * @param metadataConfig Default metadata
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return The parsed multi-sample source
     * @throws IOException An error occurred parsing the XML document
     */
    public List<IMultisampleSource> parse (final File sourceFolder, final File sourceFile, final String content, final IMetadataConfig metadataConfig, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            final Element top = document.getDocumentElement ();

            if (!this.isValidTopLevelElement (top))
                return Collections.emptyList ();

            final List<Element> programElements = this.findProgramElements (top);
            if (programElements.isEmpty ())
                return Collections.emptyList ();

            final String n = metadataConfig.isPreferFolderName () ? sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFolder, n);

            final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
            for (final Element programElement: programElements)
            {
                final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, null, AudioFileUtils.subtractPaths (sourceFolder, sourceFile));
                if (this.parseProgram (programElement, multisampleSource, monolithSamples))
                {
                    updateMetadata (metadataConfig, parts, multisampleSource.getMetadata ());
                    multisampleSources.add (multisampleSource);
                }
            }
            return multisampleSources;
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

        final String category = metadata.getCategory ();
        if (category == null || category.isBlank () || "New".equals (category))
            metadata.setCategory (TagDetector.detectCategory (parts));

        final String [] keywords = metadata.getKeywords ();
        if (keywords == null || keywords.length == 0)
            metadata.setKeywords (TagDetector.detectKeywords (parts));
    }


    /**
     * Creates a metadata description file.
     *
     * @param safeSampleFolderName The folder where the samples are placed
     * @param multisampleSource The multi-sample source
     * @return The XML document as a text
     */
    public Optional<String> create (final String safeSampleFolderName, final IMultisampleSource multisampleSource)
    {
        final String templatePrefix = this.getTemplatePrefix ();

        try
        {
            String text = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_01_Header.xml").replace ("%PROGRAM_NAME%", multisampleSource.getName ());

            // Add all groups
            final String result = this.addGroups (templatePrefix, safeSampleFolderName, multisampleSource.getNonEmptyGroups (false));
            text += result + Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_06_Footer.xml");
            return Optional.of (text);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex);
            return Optional.empty ();
        }
    }


    protected abstract String getTemplatePrefix ();


    protected String addGroups (final String templatePrefix, final String safeSampleFolderName, final List<IGroup> groups) throws IOException
    {
        final String groupTemplate = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_02_Group.xml");
        final String zoneTemplate = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_04_Zone.xml");
        final String loopTemplate = Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_05_Loop.xml");

        final StringBuilder groupsText = new StringBuilder ();
        final StringBuilder zonesText = new StringBuilder ();

        // Samples are numbered across all groups!
        int sampleCount = 0;
        boolean isReverse = false;

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
                    final String loopContent = addLoop (loop, loopTemplate, loopIndex);
                    if (loopIndex > 0)
                        loopsContent.append ("\r\n");
                    loopsContent.append (loopContent);

                    isReverse = loop.getType () == LoopType.BACKWARDS;
                }
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
            groupContent = addModulators (firstZone, groupContent);
            groupContent = groupContent.replace ("%AMP_VELOCITY_MOD%", formatDouble (ampVelocityMod));
            groupContent = groupContent.replace ("%PITCH_BEND%", formatDouble (pitchBendUp / 1200));
            groupContent = groupContent.replace ("%GROUP_KEY_TRACKING%", keyTracking ? "yes" : "no");
            groupContent = groupContent.replace ("%GROUP_RELEASE_TRIGGER%", isReleaseTrigger ? "yes" : "no");
            groupContent = groupContent.replace ("%GROUP_REVERSE%", isReverse ? "yes" : "no");
            groupsText.append (groupContent);
        }

        return groupsText.toString () + Functions.textFileFor (TEMPLATE_FOLDER + templatePrefix + "_03_Groups_Zones.xml") + zonesText.toString ();
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
        zoneContent = zoneContent.replace ("%ZONE_VEL_CROSS_HIGH%", Integer.toString (limitToDefault (zone.getVelocityCrossfadeLow (), 0)));
        zoneContent = zoneContent.replace ("%ZONE_KEY_CROSS_LOW%", Integer.toString (limitToDefault (zone.getNoteCrossfadeLow (), 0)));
        zoneContent = zoneContent.replace ("%ZONE_KEY_CROSS_HIGH%", Integer.toString (limitToDefault (zone.getNoteCrossfadeHigh (), 0)));
        zoneContent = zoneContent.replace ("%ZONE_KEY_ROOT%", Integer.toString (limitToDefault (zone.getKeyRoot (), keyLow)));
        zoneContent = zoneContent.replace ("%ZONE_VOLUME%", formatDouble (Math.pow (10, zone.getGain () / 20.0d)));
        zoneContent = zoneContent.replace ("%ZONE_TUNE%", formatDouble (Math.exp (zone.getTune () / 0.12d * Math.log (2))));
        zoneContent = zoneContent.replace ("%ZONE_PAN%", formatDouble (this.denormalizePanning (zone.getPanorama ())));

        // Note: we need to use backward slashes otherwise Kontakt can read but not save the file
        // again!
        final String formattedFilename = new StringBuilder (safeSampleFolderName).append ('\\').append (zoneName).append (".wav").toString ().replace ('/', '\\');
        return zoneContent.replace ("%ZONE_SAMPLE_NAME%", formattedFilename);
    }


    private static String addLoop (final ISampleLoop loop, final String loopTemplate, final int loopIndex)
    {
        String loopContent = loopTemplate.replace ("%LOOP_INDEX%", Integer.toString (loopIndex));

        loopContent = loopContent.replace ("%LOOP_START%", Integer.toString (loop.getStart ()));
        loopContent = loopContent.replace ("%LOOP_LENGTH%", Integer.toString (loop.getLength ()));

        loopContent = loopContent.replace ("%LOOP_ALTERNATING%", loop.getType () == LoopType.ALTERNATING ? "yes" : "no");
        return loopContent.replace ("%LOOP_XFADE%", Integer.toString ((int) loop.getCrossfade ()));
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

        final IEnvelopeModulator pitchModulator = sampleMetadata == null ? new DefaultEnvelopeModulator (0) : sampleMetadata.getPitchModulator ();
        final IEnvelope pitchEnvelope = pitchModulator.getSource ();
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_INTENSITY%", formatDouble (pitchModulator.getDepth ()));
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_ATTACK_CURVE%", formatDouble (-pitchEnvelope.getAttackSlope ()));
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_ATTACK%", formatDouble (limitToDefault (pitchEnvelope.getAttackTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_DECAY%", formatDouble (limitToDefault (pitchEnvelope.getDecayTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_HOLD%", formatDouble (limitToDefault (pitchEnvelope.getHoldTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_RELEASE%", formatDouble (limitToDefault (pitchEnvelope.getReleaseTime (), 0) * 1000.0d));
        groupContent = groupContent.replace ("%PITCH_ENVELOPE_SUSTAIN%", formatDouble (limitToDefault (pitchEnvelope.getSustainLevel (), 0)));

        final Optional<IFilter> filterOpt = sampleMetadata == null ? Optional.empty () : sampleMetadata.getFilter ();
        final IFilter filter = filterOpt.isPresent () ? filterOpt.get () : new DefaultFilter (FilterType.LOW_PASS, 2, IFilter.MAX_FREQUENCY, 0);
        groupContent = groupContent.replace ("%FILTER_TYPE%", createFilterType (filter));
        groupContent = groupContent.replace ("%FILTER_CUTOFF%", formatDouble (filter.getCutoff () / IFilter.MAX_FREQUENCY));
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
        return groupContent;
    }


    private static String createFilterType (final IFilter filter)
    {
        return FILTER_PREFIXES.getOrDefault (filter.getType (), "lp") + filter.getPoles () + "pole";
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
     * @param multisampleSource Where to store the parsed data
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @return True if successful
     * @throws IOException Could not create path for samples
     */
    private boolean parseProgram (final Element programElement, final DefaultMultisampleSource multisampleSource, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        final String programName = this.tags.programName ();
        if (!programElement.hasAttribute (programName))
            return false;

        final Map<String, String> programParameters = this.readParameters (programElement);

        final String name = programElement.getAttribute (programName);
        multisampleSource.setName (name);
        final IMetadata metadata = multisampleSource.getMetadata ();
        final String author = programParameters.get ("instrumentAuthor");
        if (author != null)
            metadata.setCreator (author);
        final String description = programParameters.get ("instrumentCredits");
        if (description != null && !NULL_ENTRY.equals (description))
            metadata.setDescription (description);

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
        return true;
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
            final IGroup group = this.getGroup (programParameters, groupElement, zoneElements, sourcePath, monolithSamples);
            if (group != null)
                groups.add (group);
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
    private IGroup getGroup (final Map<String, String> programParameters, final Element groupElement, final List<Element> zoneElements, final String sourcePath, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        if (groupElement == null)
            return null;

        final DefaultGroup group = new DefaultGroup ();
        group.setName (this.getGroupName (groupElement));
        final Map<String, String> groupParameters = this.readParameters (groupElement);
        final IFilter filter = this.readFilter (groupElement);
        final Map<String, IEnvelopeModulator> groupModulators = this.readGroupModulators (groupElement);
        final int pitchBend = this.readGroupPitchBend (groupElement);
        final double ampVelocityMod = this.readGroupAmplitudeVelocityModulator (groupElement);
        group.setTrigger (this.getTriggerTypeFromGroupElement (groupParameters));
        final Element [] groupZones = this.findGroupZones (groupElement, zoneElements);
        group.setSampleZones (this.getSampleMetadataFromZones (programParameters, groupParameters, groupModulators, groupElement, groupZones, pitchBend, ampVelocityMod, sourcePath, monolithSamples, filter));
        return group;
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
    private List<ISampleZone> getSampleMetadataFromZones (final Map<String, String> programParameters, final Map<String, String> groupParameters, final Map<String, IEnvelopeModulator> groupModulators, final Element groupElement, final Element [] groupZones, final int pitchBend, final double ampVelocityMod, final String sourcePath, final Map<String, ISampleData> monolithSamples, final IFilter filter) throws IOException
    {
        if (groupElement == null || groupZones == null)
            return Collections.emptyList ();

        final LinkedList<ISampleZone> sampleMetadataList = new LinkedList<> ();

        for (final Element zoneElement: groupZones)
        {
            final File sampleFile = this.getZoneSampleFile (zoneElement, sourcePath, !monolithSamples.isEmpty ());
            if (sampleFile == null)
                continue;

            final ISampleData sampleData = this.getSampleMetadata (sampleFile, monolithSamples);
            if (sampleData != null)
            {
                final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
                if (filter != null)
                    zone.setFilter (filter);
                this.readMetadata (programParameters, groupParameters, groupModulators, pitchBend, ampVelocityMod, sampleMetadataList, zoneElement, zone);
            }
        }

        return sampleMetadataList;
    }


    private ISampleData getSampleMetadata (final File sampleFile, final Map<String, ISampleData> monolithSamples) throws IOException
    {
        if (!monolithSamples.isEmpty ())
            return monolithSamples.get (sampleFile.getName ());

        if (sampleFile.getName ().toLowerCase ().endsWith (".ncw"))
            return new NcwFileSampleData (sampleFile);

        // Check the type of the source sample for compatibility and handle them
        // accordingly
        try
        {
            final AudioFileFormat.Type type = AudioSystem.getAudioFileFormat (sampleFile).getType ();
            if (AudioFileFormat.Type.WAVE.equals (type))
            {
                if (AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                    return new WavFileSampleData (sampleFile);
            }
            else if (AudioFileFormat.Type.AIFF.equals (type))
                return new AiffFileSampleData (sampleFile);

            this.notifier.logError ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", type.toString ());
        }
        catch (final UnsupportedAudioFileException | IOException ex)
        {
            this.notifier.logError ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", sampleFile.getName ());
        }
        return null;
    }


    private void readMetadata (final Map<String, String> programParameters, final Map<String, String> groupParameters, final Map<String, IEnvelopeModulator> groupModulators, final int pitchBend, final double ampVelocityMod, final LinkedList<ISampleZone> sampleMetadataList, final Element zoneElement, final ISampleZone zone)
    {
        try
        {
            zone.getSampleData ().addZoneData (zone, true, true);
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

            if (sampleEnd > sampleStart)
            {
                zone.setStart (sampleStart);
                zone.setStop (sampleEnd);
            }

            final String keyTracking = AbstractNKIMetadataFileHandler.getString (groupParameters, this.tags.keyTrackingParam ());
            final double keyTrackingValue = keyTracking.equals (this.tags.yes ()) ? 1.0d : 0.0d;
            zone.setKeyTracking (keyTrackingValue);

            final double zoneVol = AbstractNKIMetadataFileHandler.getDouble (zoneParameters, this.tags.zoneVolParam ());
            final double groupVol = AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupVolParam ());
            final double progVol = AbstractNKIMetadataFileHandler.getDouble (programParameters, this.tags.progVolParam ());
            zone.setGain (20.0d * Math.log10 (zoneVol * groupVol * progVol));

            final double zoneTune = AbstractNKIMetadataFileHandler.getDouble (zoneParameters, this.tags.zoneTuneParam ());
            final double groupTune = AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupTuneParam ());
            final double progTune = AbstractNKIMetadataFileHandler.getDouble (programParameters, this.tags.progTuneParam ());
            zone.setTune (this.tags.calculateTune (zoneTune, groupTune, progTune));

            final double zonePan = AbstractNKIMetadataFileHandler.getDouble (zoneParameters, this.tags.zonePanParam ());
            final double groupPan = AbstractNKIMetadataFileHandler.getDouble (groupParameters, this.tags.groupPanParam ());
            final double progPan = AbstractNKIMetadataFileHandler.getDouble (programParameters, this.tags.progPanParam ());
            final double totalPan = this.normalizePanning (zonePan) + this.normalizePanning (groupPan) + this.normalizePanning (progPan);
            zone.setPanorama (MathUtils.clamp (totalPan, -1.0d, 1.0d));

            if (ampVelocityMod >= 0)
                zone.getAmplitudeVelocityModulator ().setDepth (ampVelocityMod);
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

        this.readLoopInformation (zoneElement, zone);

        sampleMetadataList.add (zone);

        if (pitchBend >= 0)
        {
            zone.setBendUp (pitchBend);
            zone.setBendDown (pitchBend);
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
            final IEnvelopeModulator pitchModulator = sampleMetadata.getPitchModulator ();
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
            String alternatingLoop;
            try
            {
                loopStart = AbstractNKIMetadataFileHandler.getInt (loopParams, this.tags.loopStartParam ());
                loopLength = AbstractNKIMetadataFileHandler.getInt (loopParams, this.tags.loopLengthParam ());
                loopMode = AbstractNKIMetadataFileHandler.getString (loopParams, this.tags.loopModeParam ());
                xFadeLength = AbstractNKIMetadataFileHandler.getInt (loopParams, this.tags.xfadeLengthParam ());
                alternatingLoop = AbstractNKIMetadataFileHandler.getString (loopParams, this.tags.alternatingLoopParam ());
            }
            catch (final ValueNotAvailableException e)
            {
                return;
            }

            // If it is a one shot then there is no loop
            if (loopMode.equals (this.tags.oneshotValue ()))
                continue;

            LoopType loopType = LoopType.FORWARDS;
            if ((loopMode.equals (this.tags.untilEndValue ()) || loopMode.equals (this.tags.untilReleaseValue ())) && alternatingLoop.equals (this.tags.yes ()))
                loopType = LoopType.ALTERNATING;

            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (loopStart);
            loop.setEnd (loopLength + loopStart);
            loop.setCrossfadeInSamples (xFadeLength);
            loop.setType (loopType);
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
    private File getZoneSampleFile (final Element zoneElement, final String sourcePath, final boolean isMonolith) throws IOException
    {
        final Element sampleElement = XMLUtils.getChildElementByName (zoneElement, this.tags.zoneSample ());
        if (sampleElement == null)
            return null;

        final Map<String, String> sampleParameters = this.readValueMap (sampleElement);
        String encodedSampleFileName = sampleParameters.get (this.tags.sampleFileAttribute ());
        if (encodedSampleFileName == null)
            encodedSampleFileName = sampleParameters.get (this.tags.sampleFileExAttribute ());
        if (encodedSampleFileName == null)
        {
            this.notifier.logError ("IDS_NKI_SAMPLE_FILE_ATTRIBUTE_MISSING");
            return null;
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
    protected File getFileFromEncodedSampleFileName (final String encodedSampleFileName, final String sourcePath, final boolean isMonolith) throws IOException
    {
        final DecodedPath decodedPath = this.decodeEncodedSampleFileName (encodedSampleFileName);
        if (decodedPath.getLibrary () != null)
            throw new IOException (Functions.getMessage ("IDS_NKI_SAMPLES_IN_LIBRARY_NOT_SUPPORTED"));

        final String samplePath = decodedPath.getPath ();
        if (isMonolith)
            return new File (samplePath);

        final File parentFolder = new File (sourcePath);

        if (decodedPath.isAbsolute ())
        {
            // If it is an absolute path, try to find the sample file in all possible sub-paths from
            // the current folder
            final Path path = Paths.get (samplePath);

            File subFolder = null;
            for (int i = path.getNameCount () - 1; i > 0; i--)
            {
                final String folder = path.getName (i).toString ();
                subFolder = subFolder == null ? new File (folder) : new File (folder, subFolder.getPath ());
                final File sampleFile = new File (parentFolder, subFolder.getPath ());
                if (sampleFile.exists () && sampleFile.canRead ())
                    return sampleFile;
            }

            this.notifier.logError ("IDS_NKI_SAMPLE_FILE_DOES_NOT_EXIST", samplePath);
            return null;
        }

        final File sampleFile = new File (parentFolder, samplePath);
        if (sampleFile.exists () && sampleFile.canRead ())
            return sampleFile;
        this.notifier.logError ("IDS_NKI_SAMPLE_FILE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
        return null;
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
        if ((char) reader.read () != '@')
        {
            decodedPath.setPath (encodedSampleFileName.replace ('\\', '/'));
            return decodedPath;
        }

        final StringBuilder path = new StringBuilder ();
        int id;
        while ((id = reader.read ()) != -1)
            switch (id)
            {
                case 'v':
                    final String disc = readFolder (reader, encodedSampleFileName);
                    if (!disc.contains (":"))
                        path.append ('/');
                    path.append (disc).append ('/');
                    decodedPath.setAbsolute (true);
                    break;

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
                        error (encodedSampleFileName);
                    int c;
                    while ((c = reader.read ()) != -1)
                        path.append ((char) c);
                    decodedPath.setPath (path.toString ());
                    return decodedPath;

                // A NKS library
                case 'm':
                    path.append (readFolder (reader, encodedSampleFileName));
                    decodedPath.setLibrary (path.toString ());
                    path.setLength (0);
                    break;

                default:
                    error (encodedSampleFileName);
            }

        error (encodedSampleFileName);
        // Never reached
        return null;
    }


    /**
     * Retrieves a TriggerType from a group's parameters.
     *
     * @param groupParameters The group element to retrieve the TriggerType from
     * @return The TriggerType that could be retrieved. In doubt, TriggerType.ATTACK is returned.
     */
    protected abstract TriggerType getTriggerTypeFromGroupElement (final Map<String, String> groupParameters);


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
     * @return The found envelopes
     */
    private Map<String, IEnvelopeModulator> readGroupModulators (final Element groupElement)
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
                final String modulationTarget = this.getModulationTarget (modulatorElement);
                if (modulationTarget != null)
                {
                    final double modulationIntensity = this.getModulationIntensity (modulatorElement);
                    if (modulationIntensity != 0)
                    {
                        final IEnvelopeModulator modulator = this.readModulatorFromElement (envElement);
                        if (modulator != null)
                        {
                            modulator.setDepth (modulationIntensity);
                            modulators.put (modulationTarget, modulator);
                        }
                    }
                }
            }
        }
        return modulators;
    }


    /**
     * Reads the filter settings from a group element. Only present for Kontakt 1.
     *
     * @param groupElement The group element
     * @return The filter or null if none is found
     */
    private IFilter readFilter (final Element groupElement)
    {
        final Element filterElement = XMLUtils.getChildElementByName (groupElement, this.tags.groupFilter ());
        if (filterElement == null)
            return null;
        final Map<String, String> valueMap = this.readValueMap (filterElement);
        if (valueMap.isEmpty ())
            return null;
        final String type = valueMap.get ("type");
        if (type == null)
            return null;
        final Matcher matcher = FILTER_TYPE_PATTERN.matcher (type);
        if (!matcher.matches ())
        {
            this.notifier.logError ("IDS_NKI_UNKNOWN_FILTER_TYPE", type);
            return null;
        }
        FilterType filterType;
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
            default:
                return null;
        }

        final String cutoffText = valueMap.get ("cutoff");
        final double cutoff = cutoffText == null ? 1.0 : Double.parseDouble (cutoffText);
        final String resonanceText = valueMap.get ("resonance");
        final double resonance = resonanceText == null ? 0 : Double.parseDouble (resonanceText);
        return new DefaultFilter (filterType, Integer.parseInt (matcher.group (2)), cutoff, resonance);
    }


    /**
     * Reads a modulator from a modulator XML element.
     *
     * @param envElement The modulator XML element
     * @return The IModulator, if modulator could be read successfully, otherwise null
     */
    private IEnvelopeModulator readModulatorFromElement (final Element envElement)
    {
        final String envType = envElement.getAttribute (this.tags.envTypeAttribute ());
        if (envType == null)
            return null;

        try
        {
            final DefaultEnvelopeModulator modulator = new DefaultEnvelopeModulator (0);
            final IEnvelope env = modulator.getSource ();

            final Map<String, String> envParams = this.readValueMap (envElement);
            env.setAttackSlope (-getDouble (envParams, this.tags.attackCurveParam ()));
            env.setAttackTime (getDouble (envParams, this.tags.attackParam ()) / 1000.0d);
            env.setHoldTime (AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.holdParam ()) / 1000.0d);
            env.setDecayTime (AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.decayParam ()) / 1000.0d);

            if (!envType.equals (this.tags.ahdEnvTypeValue ()))
            {
                env.setSustainLevel (AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.sustainParam ()));
                env.setReleaseTime (AbstractNKIMetadataFileHandler.getDouble (envParams, this.tags.releaseParam ()) / 1000.0d);
            }
            return modulator;
        }
        catch (final ValueNotAvailableException e)
        {
            return null;
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
     * Retrieves the target from the given modulator element.
     *
     * @param modulator The modulator element
     * @return The target value, null if not present
     */
    protected abstract String getModulationTarget (Element modulator);


    /**
     * Retrieves the intensity from the given modulator element.
     *
     * @param modulator The modulator element
     * @return The intensity value, 0 if not present or could parsed
     */
    protected abstract double getModulationIntensity (Element modulator);


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
    protected Element findElementWithParameters (final Element parentElement, final String elementNameToBeFound, final String... nameValuePairs)
    {
        for (final Element elementOfInterest: XMLUtils.getChildElementsByName (parentElement, elementNameToBeFound))
        {
            final boolean doesMatch = this.hasNameValuePairs (elementOfInterest, nameValuePairs);
            if (doesMatch)
                return elementOfInterest;
        }
        return null;
    }


    /**
     * Reads the pitch bend configuration from a group element.
     *
     * @param groupElement The group element
     * @return The number of semi-tones (up=down)
     */
    private int readGroupPitchBend (final Element groupElement)
    {
        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.extModulatorsElement ());
        if (modulatorsElement != null)
            for (final Element modulator: XMLUtils.getChildElementsByName (modulatorsElement, this.tags.extModulatorElement ()))
            {
                final int pitchBend = this.getPitchBendFromModulator (modulator);
                if (pitchBend >= 0)
                    return pitchBend;
            }
        return -1;
    }


    /**
     * Reads a pitch bend value from a modulator Element.
     *
     * @param modulator The modulator Element
     * @return The pitch bend value. If none could be found, a -1 is returned.
     */
    private int getPitchBendFromModulator (final Element modulator)
    {
        int pitchBend = -1;

        final Map<String, String> modulatorParams = this.readValueMap (modulator);
        if (modulatorParams == null || !modulatorParams.containsKey (this.tags.sourceParam ()))
            return pitchBend;

        final String source = modulatorParams.get (this.tags.sourceParam ());

        if (!source.equals (this.tags.pitchBendValue ()))
            return pitchBend;

        final String intensity = this.readPitchBendIntensity (modulator);

        if (intensity == null)
            return pitchBend;

        final double pitchOctaves = Double.parseDouble (intensity);
        final double pitchCents = pitchOctaves * 1200;
        pitchBend = (int) Math.round (pitchCents);
        if (pitchBend < 0)
            pitchBend = -pitchBend;

        if (pitchBend > 9600)
            pitchBend = 9600;

        return pitchBend;
    }


    /**
     * Reads the value by which the amplitude is modulated by the velocity from a group element.
     *
     * @param groupElement The group element
     * @return The number of modulation (0..1)
     */
    private double readGroupAmplitudeVelocityModulator (final Element groupElement)
    {
        final Element modulatorsElement = XMLUtils.getChildElementByName (groupElement, this.tags.extModulatorsElement ());
        if (modulatorsElement != null)
            for (final Element modulator: XMLUtils.getChildElementsByName (modulatorsElement, this.tags.extModulatorElement ()))
            {
                final double ampVelocityMod = this.getAmplitudeVelocityModulator (modulator);
                if (ampVelocityMod >= 0)
                    return ampVelocityMod;
            }
        return -1;
    }


    /**
     * Reads the value by which the amplitude is modulated by the velocity from a modulator Element.
     *
     * @param modulator The modulator Element
     * @return The pitch bend value. If none could be found, a -1 is returned.
     */
    private double getAmplitudeVelocityModulator (final Element modulator)
    {
        double ampVelocityMod = -1;

        final Map<String, String> modulatorParams = this.readValueMap (modulator);
        if (modulatorParams == null || !modulatorParams.containsKey (this.tags.sourceParam ()))
            return ampVelocityMod;

        final String source = modulatorParams.get (this.tags.sourceParam ());

        if (!source.equals (this.tags.velocityValue ()))
            return ampVelocityMod;

        final String intensity = this.readAmplitudeVelocityIntensity (modulator);

        if (intensity == null)
            return ampVelocityMod;

        return Double.parseDouble (intensity);
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


    /**
     * Reads the pitch bend intensity value from a modulator element.
     *
     * @param modulator The modulator element
     * @return The pitch bend intensity value or null, if intensity couldn't be read.
     */
    protected abstract String readPitchBendIntensity (final Element modulator);


    /**
     * Reads the volume intensity value from a modulator element.
     *
     * @param modulator The modulator element
     * @return The volume intensity value or null, if intensity couldn't be read.
     */
    protected abstract String readAmplitudeVelocityIntensity (final Element modulator);


    private static String formatDouble (final double value)
    {
        return String.format (Locale.US, "%.6f", Double.valueOf (value));
    }


    private static void error (final String encodedSampleFileName) throws IOException
    {
        throw new IOException (Functions.getMessage ("IDS_NKI_UNEXPECTED_STATE", encodedSampleFileName));
    }


    private static String readFolder (final StringReader reader, final String encodedSampleFileName) throws IOException
    {
        final char [] lengthBuffer = new char [3];
        if (reader.read (lengthBuffer) != lengthBuffer.length)
            error (encodedSampleFileName);
        final int length = Integer.parseInt (new String (lengthBuffer));
        final char [] folder = new char [length];
        if (reader.read (folder) != length)
            error (encodedSampleFileName);
        return new String (folder);
    }
}
