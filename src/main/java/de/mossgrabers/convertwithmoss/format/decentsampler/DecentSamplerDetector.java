// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfoModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.utils.NoteParser;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively DecentSampler preset and library files in folders. Files must end with
 * <i>.dspreset</i> or <i>.dslibrary</i>.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerDetector extends AbstractDetector<DecentSamplerDetectorUI>
{
    private static final String                  DECENT_SAMPLER        = "DecentSampler";
    private static final String                  ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String                  ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";

    private static final String                  ENDING_DSLIBRARY      = ".dslibrary";
    private static final String                  ENDING_DSPRESET       = ".dspreset";

    private static final Map<String, FilterType> FILTER_TYPE_MAP       = new HashMap<> ();
    private static final Map<String, Integer>    FILTER_POLES_MAP      = new HashMap<> ();
    static
    {
        FILTER_TYPE_MAP.put ("lowpass_4pl", FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put ("lowpass", FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put ("lowpass_1pl", FilterType.LOW_PASS);
        FILTER_TYPE_MAP.put ("highpass", FilterType.HIGH_PASS);
        FILTER_TYPE_MAP.put ("bandpass", FilterType.BAND_PASS);
        FILTER_TYPE_MAP.put ("peak", FilterType.BAND_PASS);
        FILTER_TYPE_MAP.put ("notch", FilterType.BAND_REJECTION);

        FILTER_POLES_MAP.put ("lowpass_4pl", Integer.valueOf (4));
        FILTER_POLES_MAP.put ("lowpass", Integer.valueOf (2));
        FILTER_POLES_MAP.put ("lowpass_1pl", Integer.valueOf (1));
        FILTER_POLES_MAP.put ("highpass", Integer.valueOf (2));
        FILTER_POLES_MAP.put ("bandpass", Integer.valueOf (2));
        FILTER_POLES_MAP.put ("peak", Integer.valueOf (2));
        FILTER_POLES_MAP.put ("notch", Integer.valueOf (2));
    }

    private Element currentGroupsElement = null;
    private Element currentGroupElement  = null;
    private Element currentSampleElement = null;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DecentSamplerDetector (final INotifier notifier)
    {
        super (DECENT_SAMPLER, DECENT_SAMPLER, notifier, new DecentSamplerDetectorUI (DECENT_SAMPLER), ENDING_DSPRESET, ENDING_DSLIBRARY);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        // Clear previous run
        this.currentGroupsElement = null;
        this.currentGroupElement = null;
        this.currentSampleElement = null;

        final List<IMultisampleSource> result = file.getName ().endsWith (ENDING_DSPRESET) ? this.processPresetFile (file) : this.processLibraryFile (file);

        if (this.settingsConfiguration.logUnsupportedAttributes ())
        {
            this.printUnsupportedElements ();
            this.printUnsupportedAttributes ();
        }

        return result;
    }


    /**
     * Reads a DecentSampler library file and processes all presets it contains.
     *
     * @param file The library file
     * @return The processed multi-samples
     */
    private List<IMultisampleSource> processLibraryFile (final File file)
    {
        final List<IMultisampleSource> result = new ArrayList<> ();

        try (final ZipFile zipFile = new ZipFile (file))
        {
            for (final ZipEntry entry: Collections.list (zipFile.entries ()))
                result.addAll (this.processFile (file, zipFile, entry));
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
        }

        return result;
    }


    /**
     * Process one ZIP file entry.
     *
     * @param file The ZIP source file
     * @param zipFile The ZIP file containing the entry
     * @param entry The ZIP entry to process
     * @return The parsed multi-samples
     * @throws IOException Could not process the file
     */
    private List<IMultisampleSource> processFile (final File file, final ZipFile zipFile, final ZipEntry entry) throws IOException
    {
        final String name = entry.getName ();
        if (name == null || !name.endsWith (ENDING_DSPRESET))
            return Collections.emptyList ();

        final File presetFile = new File (name);
        String parent = presetFile.getParent ();
        if (parent == null)
            parent = "";

        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final String content = fixInvalidXML (StreamUtils.readUtf8 (in));
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (FileUtils.getNameWithoutType (presetFile), file, parent, true, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Workaround for invalid XML files which contain comments before the XML header.
     *
     * @param content The XML document
     * @return The potentially fixed XML document
     */
    private static String fixInvalidXML (final String content)
    {
        final int headerStart = content.indexOf ("<?xml");
        return headerStart > 0 ? content.substring (headerStart) : content;
    }


    /**
     * Reads and processes the Decent Sampler preset file.
     *
     * @param file The preset file
     * @return The processed multi-sample (singleton list)
     */
    private List<IMultisampleSource> processPresetFile (final File file)
    {
        try (final FileInputStream in = new FileInputStream (file))
        {
            final String content = fixInvalidXML (StreamUtils.readUtf8 (in));
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (FileUtils.getNameWithoutType (file), file, file.getParent (), false, document);
        }
        catch (final SAXParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_COULD_NOT_PARSE_XML", Integer.toString (ex.getLineNumber ()), Integer.toString (ex.getColumnNumber ()), ex.getLocalizedMessage ());
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param presetName The name to use for the preset
     * @param sourceFile The preset or library file
     * @param basePath The parent folder, in case of a library the relative folder in the ZIP
     *            directory structure
     * @param isLibrary If it is a library otherwise a preset
     * @param document The XML document to parse
     * @return The parsed multi-sample source
     */
    private List<IMultisampleSource> parseMetadataFile (final String presetName, final File sourceFile, final String basePath, final boolean isLibrary, final Document document)
    {
        final Element topElement = document.getDocumentElement ();
        if (!DecentSamplerTag.DECENTSAMPLER.equals (topElement.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Unknown Root");
            return Collections.emptyList ();
        }

        this.checkAttributes (DecentSamplerTag.DECENTSAMPLER, topElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.DECENTSAMPLER));
        this.checkChildTags (DecentSamplerTag.DECENTSAMPLER, DecentSamplerTag.TOP_LEVEL_TAGS, XMLUtils.getChildElements (topElement));

        final Element groupsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.GROUPS);
        if (groupsElement == null)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Missing Groups tag");
            return Collections.emptyList ();
        }
        this.currentGroupsElement = groupsElement;

        final double globalTuningOffset = XMLUtils.getDoubleAttribute (groupsElement, DecentSamplerTag.GLOBAL_TUNING, 0);
        final List<IGroup> groups = this.parseGroups (topElement, groupsElement, basePath, isLibrary ? sourceFile : null, globalTuningOffset);
        final int polyphony = parsePolyphony (topElement, groupsElement);

        // Create one multi-sample per group, e.g. for presets which contain several alternative
        // kits as groups and switch between them via their user interface
        if (this.settingsConfiguration.isMultisamplePerGroup () && groups.size () > 1)
        {
            final List<IMultisampleSource> multisampleSources = new ArrayList<> (groups.size ());
            for (final IGroup group: groups)
            {
                final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, presetName + " - " + group.getName (), Collections.singletonList (group));
                parseEffects (topElement, multisampleSource);
                if (polyphony > 0)
                    multisampleSource.setPolyphony (polyphony);
                multisampleSources.add (multisampleSource);
            }
            return multisampleSources;
        }

        final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, presetName, groups);
        parseEffects (topElement, multisampleSource);
        if (polyphony > 0)
            multisampleSource.setPolyphony (polyphony);
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parse the polyphony from the tags on the top level. DecentSampler can limit the number of
     * voices of a tag, which is applied to all groups which reference that tag by their 'tags'
     * attribute.
     *
     * @param topElement The top element
     * @param groupsElement The groups element
     * @return The polyphony or 0 if it is not limited
     */
    private static int parsePolyphony (final Element topElement, final Element groupsElement)
    {
        final Element tagsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.TAGS);
        if (tagsElement == null)
            return 0;

        // Collect the names of all tags which are referenced by the groups or by a single group
        final Set<String> referencedTags = new HashSet<> ();
        addReferencedTags (referencedTags, groupsElement);
        for (final Element groupElement: XMLUtils.getChildElementsByName (groupsElement, DecentSamplerTag.GROUP))
            addReferencedTags (referencedTags, groupElement);
        if (referencedTags.isEmpty ())
            return 0;

        // The model can only store one polyphony, therefore the most restrictive one is applied
        int polyphony = 0;
        for (final Element tagElement: XMLUtils.getChildElementsByName (tagsElement, DecentSamplerTag.TAG))
        {
            if (!referencedTags.contains (tagElement.getAttribute (DecentSamplerTag.TAG_NAME)))
                continue;
            final int tagPolyphony = XMLUtils.getIntegerAttribute (tagElement, DecentSamplerTag.TAG_POLYPHONY, 0);
            if (tagPolyphony > 0 && (polyphony == 0 || tagPolyphony < polyphony))
                polyphony = tagPolyphony;
        }
        return polyphony;
    }


    /**
     * Add all tag names which are referenced by the 'tags' attribute of the given element.
     *
     * @param referencedTags Where to add the referenced tag names
     * @param element The element from which to read the 'tags' attribute
     */
    private static void addReferencedTags (final Set<String> referencedTags, final Element element)
    {
        final String tags = element.getAttribute (DecentSamplerTag.TAGS_ATTRIBUTE);
        if (tags == null || tags.isBlank ())
            return;
        for (final String tag: tags.split (","))
            referencedTags.add (tag.trim ());
    }


    /**
     * Parse the effects on the top level.
     *
     * @param topElement The top element
     * @param multisampleSource The multi-sample to fill
     */
    private static void parseEffects (final Element topElement, final IMultisampleSource multisampleSource)
    {
        final Optional<IFilter> optFilter = parseFilterEffect (topElement, topElement);
        if (optFilter.isPresent ())
            multisampleSource.setGlobalFilter (optFilter.get ());
    }


    private static Optional<IFilter> parseFilterEffect (final Element topElement, final Element effectParent)
    {
        final Element effectsElement = XMLUtils.getChildElementByName (effectParent, DecentSamplerTag.EFFECTS);
        if (effectsElement == null)
            return Optional.empty ();

        for (final Element effectElement: XMLUtils.getChildElementsByName (effectsElement, DecentSamplerTag.EFFECTS_EFFECT, false))
        {
            final String effectType = effectElement.getAttribute ("type");
            final FilterType filterType = FILTER_TYPE_MAP.get (effectType);
            if (filterType != null)
            {
                final int poles = FILTER_POLES_MAP.get (effectType).intValue ();
                final double frequency = XMLUtils.getDoubleAttribute (effectElement, "frequency", IFilter.MAX_FREQUENCY);
                final double resonance = Math.clamp ((XMLUtils.getDoubleAttribute (effectElement, "resonance", 0.7) - 0.7) / 4.3, 0, 1);
                final IFilter filter = new DefaultFilter (filterType, poles, frequency, resonance);

                // Parse the filter envelope
                final Element modulatorsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.MODULATORS);
                if (modulatorsElement != null)
                    for (final Element envelopeElement: XMLUtils.getChildElementsByName (modulatorsElement, DecentSamplerTag.ENVELOPE))
                    {
                        final Element bindingElement = XMLUtils.getChildElementByName (envelopeElement, DecentSamplerTag.BINDING);
                        if (bindingElement != null && "FX_FILTER_FREQUENCY".equals (bindingElement.getAttribute ("parameter")))
                        {
                            // IMPROVE: All filters are applied to the global filter. If filters on
                            // all levels are supported, this needs to be checked here
                            final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                            convertEnvelope (envelopeElement, cutoffEnvelopeModulator.getSource ());
                            cutoffEnvelopeModulator.setDepth (XMLUtils.getDoubleAttribute (envelopeElement, DecentSamplerTag.MOD_AMOUNT, 1.0));
                            break;
                        }
                    }

                return Optional.of (filter);
            }
        }

        return Optional.empty ();
    }


    /**
     * Parses all groups.
     *
     * @param topElement The top element
     * @param groupElements The XML element containing all groups
     * @param basePath The base path of the samples
     * @param libraryFile If it is a library otherwise null
     * @param globalTuningOffset The global tuning offset
     * @return All parsed groups
     */
    private List<IGroup> parseGroups (final Element topElement, final Element groupElements, final String basePath, final File libraryFile, final double globalTuningOffset)
    {
        final List<Element> groupNodes = XMLUtils.getChildElementsByName (groupElements, DecentSamplerTag.GROUP);
        final List<IGroup> groups = new ArrayList<> (groupNodes.size ());
        int groupCounter = 1;
        for (final Element groupElement: groupNodes)
        {
            this.currentGroupElement = groupElement;

            this.checkAttributes (DecentSamplerTag.GROUP, groupElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.GROUP));

            // Since we cannot support enabling deactivated groups in any way, simply skip them.
            // Except when each group becomes its own multi-sample: presets which contain several
            // alternative kits as groups enable only one of them, but all kits are wanted then
            if (!this.settingsConfiguration.isMultisamplePerGroup ())
            {
                final String groupEnabled = groupElement.getAttribute (DecentSamplerTag.GROUP_ENABLED);
                if (groupEnabled != null && ("0".equals (groupEnabled) || "false".equalsIgnoreCase (groupEnabled)))
                    continue;
            }

            final String k = groupElement.getAttribute (DecentSamplerTag.GROUP_NAME);
            final String groupName = k == null || k.isBlank () ? "Group " + groupCounter : k;
            final IGroup group = new DefaultGroup (groupName);

            final double groupVolumeOffset = parseVolume (groupElement, DecentSamplerTag.VOLUME);
            final double groupPanningOffset = XMLUtils.getIntegerAttribute (groupElement, DecentSamplerTag.PANNING, 0) / 100.0;
            double groupTuningOffset = XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.GROUP_TUNING, 0);
            // Actually not in the specification but support it anyway
            if (groupTuningOffset == 0)
                groupTuningOffset = XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.TUNING, 0);

            // Note: all three values are additionally flattened into each zone of the group below,
            // which must not be changed. They are stored here as well since DecentSampler does
            // have a real group layer. The volume is already in dB and the tuning already in
            // semi-tones, only the panning needs to be normalized from [-100..100] to [-1..1]
            if (groupVolumeOffset != 0)
                group.setGain (groupVolumeOffset);
            if (groupPanningOffset != 0)
                group.setPanning (Math.clamp (groupPanningOffset / 100.0, -1.0, 1.0));
            if (groupTuningOffset != 0)
                group.setTuning (groupTuningOffset);

            final String triggerAttribute = groupElement.getAttribute (DecentSamplerTag.TRIGGER);

            this.parseGroup (topElement, group, groupElement, basePath, libraryFile, groupVolumeOffset, groupPanningOffset, globalTuningOffset + groupTuningOffset, triggerAttribute);
            groups.add (group);
            groupCounter++;
        }
        return groups;
    }


    /**
     * Parse a group.
     *
     * @param topElement The top element
     * @param group The object to fill in the data
     * @param groupElement The XML group element
     * @param basePath The base path of the samples
     * @param libraryFile If it is a library otherwise null
     * @param groupVolumeOffset The volume offset
     * @param groupPanningOffset The panning offset
     * @param tuningOffset The tuning offset
     * @param trigger The trigger value
     */
    private void parseGroup (final Element topElement, final IGroup group, final Element groupElement, final String basePath, final File libraryFile, final double groupVolumeOffset, final double groupPanningOffset, final double tuningOffset, final String trigger)
    {
        final double ampVelocityDepth = XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.AMP_VELOCITY_TRACK, 1);

        // IMPROVE: Should be added to group itself but needs to be adapted in all other formats
        final Optional<IFilter> optFilter = parseFilterEffect (topElement, groupElement);
        final Optional<IEnvelopeModulator> pitchModulation = parsePitchModulation (topElement);
        final Optional<ILfoModulator> pitchLfoModulation = parsePitchLfoModulation (topElement);

        for (final Element sampleElement: XMLUtils.getChildElementsByName (groupElement, DecentSamplerTag.SAMPLE, false))
        {
            this.currentSampleElement = sampleElement;

            this.checkAttributes (DecentSamplerTag.SAMPLE, sampleElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.SAMPLE));
            this.checkChildTags (DecentSamplerTag.SAMPLE, DecentSamplerTag.SAMPLE_TAGS, XMLUtils.getChildElements (sampleElement));

            final Optional<DefaultSampleZone> optSampleZone = this.createSampleZone (basePath, libraryFile, sampleElement);
            if (optSampleZone.isEmpty ())
                continue;

            final ISampleZone sampleZone = optSampleZone.get ();
            this.convertSampleZone (sampleElement, sampleZone, groupVolumeOffset, groupPanningOffset, tuningOffset, trigger);
            this.convertVolumeEnvelope (sampleZone.getAmplitudeEnvelopeModulator ().getSource ());
            sampleZone.getAmplitudeVelocityModulator ().setDepth (ampVelocityDepth);

            // Check for sequence e.g. round robin
            final Optional<String> seqModeAttribute = this.getAttribute (DecentSamplerTag.SEQ_MODE);
            if (seqModeAttribute.isPresent () && !DecentSamplerTag.SEQ_ALWAYS.equalsIgnoreCase (seqModeAttribute.get ()))
            {
                sampleZone.setPlayLogic (parsePlayLogic (seqModeAttribute.get ()));

                final int seqPosition = XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.SEQ_POSITION, -1);
                if (seqPosition >= 1)
                    sampleZone.setSequencePosition (seqPosition);
            }

            if (optFilter.isPresent ())
                sampleZone.setFilter (optFilter.get ());
            if (pitchModulation.isPresent ())
            {
                final IEnvelopeModulator envelopeModulator = pitchModulation.get ();
                final IEnvelopeModulator pitchModulator = sampleZone.getPitchEnvelopeModulator ();
                pitchModulator.setDepth (envelopeModulator.getDepth ());
                pitchModulator.setSource (envelopeModulator.getSource ());
            }
            if (pitchLfoModulation.isPresent ())
            {
                final ILfoModulator lfoModulator = pitchLfoModulation.get ();
                final ILfoModulator pitchLfoModulator = sampleZone.getPitchLfoModulator ();
                pitchLfoModulator.setDepth (lfoModulator.getDepth ());
                pitchLfoModulator.setSource (lfoModulator.getSource ());
            }

            group.addSampleZone (sampleZone);
        }
    }


    private void convertSampleZone (final Element sampleElement, final ISampleZone sampleZone, final double groupVolumeOffset, final double groupPanningOffset, final double tuningOffset, final String trigger)
    {
        String triggerAttribute = sampleElement.getAttribute (DecentSamplerTag.TRIGGER);
        if (triggerAttribute == null || triggerAttribute.isBlank ())
            triggerAttribute = trigger;
        if (triggerAttribute != null && !triggerAttribute.isBlank ())
            try
            {
                sampleZone.setTrigger (TriggerType.valueOf (triggerAttribute.toUpperCase (Locale.ENGLISH)));
            }
            catch (final IllegalArgumentException _)
            {
                this.notifier.logError ("IDS_DS_UNKNOWN_TRIGGER", triggerAttribute);
            }

        sampleZone.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.START, -1)));
        sampleZone.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.END, -1)));
        sampleZone.setGain (groupVolumeOffset + parseVolume (sampleElement, DecentSamplerTag.VOLUME));
        sampleZone.setPanning (Math.clamp (groupPanningOffset + XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.PANNING, 0) / 100.0, -1, 1));
        sampleZone.setTuning (tuningOffset + XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.TUNING, 0));

        sampleZone.setPlayLogic (parsePlayLogic (this.currentGroupsElement.getAttribute (DecentSamplerTag.SEQ_MODE)));

        sampleZone.setKeyTracking (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.PITCH_KEY_TRACK, 1));
        sampleZone.setKeyRoot (getNoteAttribute (sampleElement, DecentSamplerTag.ROOT_NOTE));
        sampleZone.setKeyLow (getNoteAttribute (sampleElement, DecentSamplerTag.LO_NOTE));
        sampleZone.setKeyHigh (getNoteAttribute (sampleElement, DecentSamplerTag.HI_NOTE));

        final int velLow = XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.LO_VEL, -1);
        final int velHigh = XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.HI_VEL, -1);
        if (velLow > 0)
            sampleZone.setVelocityLow (velLow);
        if (velHigh > 0)
            sampleZone.setVelocityHigh (velHigh);

        // -----------------------------------------------------------
        // Loops

        final int loopStart = (int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_START, -1));
        final int loopEnd = (int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_END, -1));
        final int loopCrossfade = XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.LOOP_CROSSFADE, 0);

        // An explicitly disabled loop also suppresses loops from the sample file chunks
        final String loopEnabledAttribute = sampleElement.getAttribute (DecentSamplerTag.LOOP_ENABLED);
        final boolean isLoopDisabled = "0".equals (loopEnabledAttribute) || "false".equalsIgnoreCase (loopEnabledAttribute);

        DefaultSampleLoop loop = null;
        if (!isLoopDisabled && (loopStart >= 0 || loopEnd > 0 || loopCrossfade > 0))
        {
            loop = new DefaultSampleLoop ();
            loop.setStart (loopStart);
            loop.setEnd (loopEnd);
            loop.setCrossfadeInSamples (loopCrossfade);
            sampleZone.addLoop (loop);
        }

        try
        {
            final Optional<ISampleData> sampleData = sampleZone.getSampleData ();
            if (sampleData.isPresent ())
                sampleData.get ().addZoneData (sampleZone, false, !isLoopDisabled && loop == null);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
        }
    }


    private void convertVolumeEnvelope (final IEnvelope envelope)
    {
        envelope.setAttackTime (this.getDoubleValue (DecentSamplerTag.ENV_ATTACK, -1));
        envelope.setDecayTime (this.getDoubleValue (DecentSamplerTag.ENV_DECAY, -1));
        envelope.setSustainLevel (this.getDoubleValue (DecentSamplerTag.ENV_SUSTAIN, -1));
        envelope.setReleaseTime (this.getDoubleValue (DecentSamplerTag.ENV_RELEASE, -1));

        envelope.setAttackSlope (this.getDoubleValue (DecentSamplerTag.ENV_ATTACK_CURVE, 0) / 100.0);
        envelope.setDecaySlope (this.getDoubleValue (DecentSamplerTag.ENV_DECAY_CURVE, 0) / 100.0);
        envelope.setReleaseSlope (this.getDoubleValue (DecentSamplerTag.ENV_RELEASE_CURVE, 0) / 100.0);
    }


    private static void convertEnvelope (final Element element, final IEnvelope envelope)
    {
        envelope.setAttackTime (XMLUtils.getDoubleAttribute (element, DecentSamplerTag.ENV_ATTACK, -1));
        envelope.setDecayTime (XMLUtils.getDoubleAttribute (element, DecentSamplerTag.ENV_DECAY, -1));
        envelope.setSustainLevel (XMLUtils.getDoubleAttribute (element, DecentSamplerTag.ENV_SUSTAIN, -1));
        envelope.setReleaseTime (XMLUtils.getDoubleAttribute (element, DecentSamplerTag.ENV_RELEASE, -1));

        envelope.setAttackSlope (XMLUtils.getDoubleAttribute (element, DecentSamplerTag.ENV_ATTACK_CURVE, 0) / 100.0);
        envelope.setDecaySlope (XMLUtils.getDoubleAttribute (element, DecentSamplerTag.ENV_DECAY_CURVE, 0) / 100.0);
        envelope.setReleaseSlope (XMLUtils.getDoubleAttribute (element, DecentSamplerTag.ENV_RELEASE_CURVE, 0) / 100.0);
    }


    private Optional<DefaultSampleZone> createSampleZone (final String basePath, final File libraryFile, final Element sampleElement)
    {
        final String sampleName = sampleElement.getAttribute (DecentSamplerTag.PATH);
        if (sampleName == null || sampleName.isBlank ())
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Missing Path attribute");
            return Optional.empty ();
        }

        final File sampleFile = new File (basePath, sampleName);
        final String zoneName = FileUtils.getNameWithoutType (sampleFile);
        final ISampleData sampleData;
        try
        {
            if (libraryFile == null)
                sampleData = createSampleData (sampleFile, this.notifier);
            else
                sampleData = this.createSampleData (libraryFile, sampleFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return Optional.empty ();
        }
        return Optional.of (new DefaultSampleZone (zoneName, sampleData));
    }


    private static Optional<IEnvelopeModulator> parsePitchModulation (final Element topElement)
    {
        // Parse the pitch envelope
        final Element modulatorsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.MODULATORS);
        if (modulatorsElement != null)
            for (final Element envelopeElement: XMLUtils.getChildElementsByName (modulatorsElement, DecentSamplerTag.ENVELOPE))
            {
                final Element bindingElement = XMLUtils.getChildElementByName (envelopeElement, DecentSamplerTag.BINDING);
                if (bindingElement != null && "GROUP_TUNING".equals (bindingElement.getAttribute ("parameter")))
                {
                    final double depth = XMLUtils.getDoubleAttribute (envelopeElement, DecentSamplerTag.MOD_AMOUNT, 1.0);
                    final IEnvelopeModulator pitchEnvelopeModulator = new DefaultEnvelopeModulator (depth);
                    convertEnvelope (envelopeElement, pitchEnvelopeModulator.getSource ());
                    return Optional.of (pitchEnvelopeModulator);
                }
            }
        return Optional.empty ();
    }


    private static Optional<ILfoModulator> parsePitchLfoModulation (final Element topElement)
    {
        // Parse a low frequency oscillator bound to the pitch (vibrato). The oscillator bound to the
        // global tuning in the template is a mod-wheel routing and is intentionally not matched.
        final Element modulatorsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.MODULATORS);
        if (modulatorsElement != null)
            for (final Element lfoElement: XMLUtils.getChildElementsByName (modulatorsElement, DecentSamplerTag.LFO))
            {
                final Element bindingElement = XMLUtils.getChildElementByName (lfoElement, DecentSamplerTag.BINDING);
                if (bindingElement == null || !"GROUP_TUNING".equals (bindingElement.getAttribute ("parameter")))
                    continue;

                final double depth = XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.MOD_AMOUNT, 0);
                if (depth == 0)
                    continue;

                final ILfoModulator pitchLfoModulator = new DefaultLfoModulator (depth);
                final ILfo pitchLfo = pitchLfoModulator.getSource ();
                pitchLfo.setWaveform (toLfoWaveform (lfoElement.getAttribute (DecentSamplerTag.LFO_SHAPE)));
                // Only a frequency given in Hertz can be converted; a tempo synchronized rate has no
                // representation without a tempo
                final String frequencyFormat = lfoElement.getAttribute (DecentSamplerTag.LFO_FREQUENCY_FORMAT);
                if (frequencyFormat.isEmpty () || "hz".equals (frequencyFormat))
                    pitchLfo.setRate (XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.LFO_FREQUENCY, -1));
                pitchLfo.setDelay (XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.LFO_DELAY_TIME, -1));
                return Optional.of (pitchLfoModulator);
            }
        return Optional.empty ();
    }


    private static LfoWaveform toLfoWaveform (final String shape)
    {
        return switch (shape)
        {
            case "square" -> LfoWaveform.SQUARE;
            case "saw" -> LfoWaveform.SAWTOOTH_UP;
            default -> LfoWaveform.SINE;
        };
    }


    /**
     * Get the value of a note element. The value can be either an integer MIDI note or a text like
     * C#5.
     *
     * @param element The element
     * @param attributeName The name of the attribute from which to get the note value
     * @return The value
     */
    private static int getNoteAttribute (final Element element, final String attributeName)
    {
        return NoteParser.parseNote (element.getAttribute (attributeName));
    }


    /**
     * Parses a volume value from the given tag.
     *
     * @param element The element which contains the volume attribute
     * @param tag The tag name of the attribute containing the volume
     * @return The volume in dB
     */
    private static double parseVolume (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in dB?
        if (attribute.endsWith ("dB"))
            return Double.parseDouble (attribute.substring (0, attribute.length () - 2));

        // The value is in the range of [0..1] but it is not specified what 0 and 1 means, lets
        // scale it to [0..6] dB.
        return Double.parseDouble (attribute) * 6.0;
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
        catch (final NumberFormatException _)
        {
            return defaultValue;
        }
    }


    /**
     * Parse the value of a sequence mode attribute into the matching play logic.
     *
     * @param seqMode The value of the sequence mode attribute, may be null
     * @return The play logic, {@link PlayLogic#ALWAYS} if the value is null or unknown
     */
    private static PlayLogic parsePlayLogic (final String seqMode)
    {
        if (seqMode == null)
            return PlayLogic.ALWAYS;
        if (DecentSamplerTag.SEQ_ROUND_ROBIN.equalsIgnoreCase (seqMode))
            return PlayLogic.ROUND_ROBIN;
        if (DecentSamplerTag.SEQ_RANDOM.equalsIgnoreCase (seqMode) || DecentSamplerTag.SEQ_TRUE_RANDOM.equalsIgnoreCase (seqMode))
            return PlayLogic.RANDOM;
        return PlayLogic.ALWAYS;
    }


    /**
     * Get the attribute value for the given key. The value is searched starting from sample upwards
     * to group and finally groups.
     *
     * @param key The key of the value to lookup
     * @return The optional value or empty if not found
     */
    private Optional<String> getAttribute (final String key)
    {
        if (this.currentSampleElement != null)
        {
            final String value = this.currentSampleElement.getAttribute (key);
            if (value != null && !value.isBlank ())
                return Optional.of (value);
        }

        if (this.currentGroupElement != null)
        {
            final String value = this.currentGroupElement.getAttribute (key);
            if (value != null && !value.isBlank ())
                return Optional.of (value);
        }

        if (this.currentGroupsElement != null)
        {
            final String value = this.currentGroupsElement.getAttribute (key);
            if (value != null && !value.isBlank ())
                return Optional.of (value);
        }

        return Optional.empty ();
    }
}
