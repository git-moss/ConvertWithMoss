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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfoModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.utils.NoteParser;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively DecentSampler preset and library files in folders. Files must end with
 * <i>.dspreset</i>, <i>.dsproduct</i> or <i>.dslibrary</i>.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerDetector extends AbstractDetector<DecentSamplerDetectorUI>
{
    private static final String                  DECENT_SAMPLER        = "DecentSampler";
    private static final String                  ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String                  ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";

    private static final String                  ENDING_DSBUNDLE       = ".dsbundle";
    private static final String                  ENDING_DSLIBRARY      = ".dslibrary";
    private static final String                  ENDING_DSPRESET       = ".dspreset";
    /**
     * A start tag or an empty-element tag with at least one attribute: name, attributes,
     * white-space, slash.
     */
    private static final Pattern                 START_TAG_PATTERN     = Pattern.compile ("<([A-Za-z_][\\w.:-]*)((?:\\s+[A-Za-z_][\\w.:-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))+)(\\s*)(/?)>");
    /**
     * One attribute of a start tag: the white-space in front of it, its name and its quoted value.
     */
    private static final Pattern                 ATTRIBUTE_PATTERN     = Pattern.compile ("(\\s+)([A-Za-z_][\\w.:-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

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

    /** The attributes of the amplitude envelope. */
    private static final String []      ENVELOPE_ATTRIBUTES  =
    {
        DecentSamplerTag.ENV_ATTACK,
        DecentSamplerTag.ENV_DECAY,
        DecentSamplerTag.ENV_SUSTAIN,
        DecentSamplerTag.ENV_RELEASE,
        DecentSamplerTag.ENV_ATTACK_CURVE,
        DecentSamplerTag.ENV_DECAY_CURVE,
        DecentSamplerTag.ENV_RELEASE_CURVE
    };

    private Element                     currentGroupsElement = null;
    private Element                     currentGroupElement  = null;
    private Element                     currentSampleElement = null;
    /** The playback features of the current preset which cannot be converted completely. */
    private final Set<String>           unsupportedPlayback  = new LinkedHashSet<> ();
    /** The gain (in dB) of the tags of the current preset. */
    private final Map<String, Double>   tagGains             = new HashMap<> ();
    /** The disabled tags of the current preset. */
    private final Set<String>           disabledTags         = new HashSet<> ();
    /** The exclusive groups of the sample elements of the current preset. */
    private final Map<Element, Integer> exclusiveGroups      = new HashMap<> ();


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DecentSamplerDetector (final INotifier notifier)
    {
        super (DECENT_SAMPLER, DECENT_SAMPLER, notifier, new DecentSamplerDetectorUI (DECENT_SAMPLER), ENDING_DSPRESET, ENDING_DSPRODUCT, ENDING_DSLIBRARY);
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

        final List<IMultisampleSource> result = isPreset (file.getName ()) ? this.processPresetFile (file) : this.processLibraryFile (file);

        if (this.settingsConfiguration.logUnsupportedAttributes ())
        {
            this.printUnsupportedElements ();
            this.printUnsupportedAttributes ();
        }

        return result;
    }


    /** {@inheritDoc} */
    @Override
    protected boolean isPackageFolder (final String folderName)
    {
        return folderName.toLowerCase (Locale.US).endsWith (ENDING_DSBUNDLE);
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
        if (name == null || entry.isDirectory () || !isPreset (name))
            return Collections.emptyList ();

        final File presetFile = new File (name);
        String parent = presetFile.getParent ();
        if (parent == null)
            parent = "";

        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final String content = this.readPresetContent (in, name);
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (FileUtils.getNameWithoutType (presetFile), file, parent, true, document);
        }
        catch (final IOException | SAXException | IllegalArgumentException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, name + ": " + ex.getMessage ());
            return Collections.emptyList ();
        }
    }


    /**
     * Test if a file is a preset, which is a XML document (dspreset).
     *
     * @param name The name of the file
     * @return True if it is a preset
     */
    private static boolean isPreset (final String name)
    {
        final String lower = name.toLowerCase (Locale.ROOT);
        return lower.endsWith (ENDING_DSPRESET);
    }


    /**
     * Read the XML document of a preset.
     *
     * @param input The stream of the preset or of its entry in a library
     * @param name The name of the preset file
     * @return The XML document
     * @throws IOException Could not read the preset
     */
    private String readPresetContent (final InputStream input, final String name) throws IOException
    {
        return this.fixInvalidXML (StreamUtils.readUtf8 (input));
    }


    /**
     * Workaround for invalid XML files: comments in front of the XML header are removed and an
     * attribute which is repeated on an element is ignored. Presets are often written by hand and
     * such a repetition is easy to produce; DecentSampler still loads the preset.
     *
     * @param content The XML document
     * @return The potentially fixed XML document
     */
    private String fixInvalidXML (final String content)
    {
        final int headerStart = content.indexOf ("<?xml");
        return this.removeDuplicateAttributes (headerStart > 0 ? content.substring (headerStart) : content);
    }


    /**
     * Removes attributes which are repeated on the same element, which the XML parser rejects. The
     * first occurrence is kept, which is the value the XML parser of JUCE returns for a repeated
     * attribute: it appends every attribute it reads and looks the name up from the front. A
     * repetition is logged if its value differs from the first one and the attribute is one which
     * the conversion uses - a repetition in e.g. the user interface makes no difference to the
     * result and is only logged if all unused elements and attributes are logged.
     *
     * @param content The XML document
     * @return The XML document without repeated attributes
     */
    private String removeDuplicateAttributes (final String content)
    {
        final Matcher tagMatcher = START_TAG_PATTERN.matcher (content);
        final StringBuilder result = new StringBuilder (content.length ());
        while (tagMatcher.find ())
        {
            final String elementName = tagMatcher.group (1);
            final Map<String, String> attributeValues = new HashMap<> ();
            final StringBuilder attributes = new StringBuilder ();
            boolean changed = false;
            final Matcher attributeMatcher = ATTRIBUTE_PATTERN.matcher (tagMatcher.group (2));
            while (attributeMatcher.find ())
            {
                final String attributeName = attributeMatcher.group (2);
                final String quotedValue = attributeMatcher.group (3);
                final String value = quotedValue.substring (1, quotedValue.length () - 1);
                if (attributeValues.putIfAbsent (attributeName, value) == null)
                {
                    attributes.append (attributeMatcher.group ());
                    continue;
                }

                if (!value.equals (attributeValues.get (attributeName)) && (isAttributeUsed (elementName, attributeName) || this.settingsConfiguration.logUnsupportedAttributes ()))
                    this.notifier.log ("IDS_DS_DUPLICATE_ATTRIBUTE_IGNORED", attributeName, elementName);
                changed = true;

                // Keep the line breaks so that the line numbers in error messages stay correct
                final String whitespace = attributeMatcher.group (1);
                if (whitespace.indexOf ('\n') >= 0)
                    attributes.append (whitespace);
            }
            if (changed)
                tagMatcher.appendReplacement (result, Matcher.quoteReplacement ("<" + elementName + attributes + tagMatcher.group (3) + tagMatcher.group (4) + ">"));
        }
        tagMatcher.appendTail (result);
        return result.toString ();
    }


    /**
     * Test whether the conversion uses an attribute of an element.
     *
     * @param elementName The name of the element
     * @param attributeName The name of the attribute
     * @return True if the attribute is used
     */
    private static boolean isAttributeUsed (final String elementName, final String attributeName)
    {
        final Set<String> attributes = DecentSamplerTag.getAttributes (elementName);
        return attributes != null && attributes.contains (attributeName);
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
            final String content = this.readPresetContent (in, file.getName ());
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (FileUtils.getNameWithoutType (file), file, file.getParent (), false, document);
        }
        catch (final SAXParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_COULD_NOT_PARSE_XML", Integer.toString (ex.getLineNumber ()), Integer.toString (ex.getColumnNumber ()), ex.getLocalizedMessage ());
        }
        catch (final IOException | SAXException | IllegalArgumentException ex)
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
        // A library contains independent presets, nothing of the previous one may affect the next
        this.currentGroupElement = null;
        this.currentSampleElement = null;
        this.unsupportedPlayback.clear ();
        this.tagGains.clear ();
        this.disabledTags.clear ();
        this.exclusiveGroups.clear ();

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
        this.checkAttributes (DecentSamplerTag.GROUPS, groupsElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.GROUPS));

        // DecentSampler sends the values of the user interface controls to the parameters which
        // they are bound to when a preset is loaded, which is the sound to convert
        DecentSamplerUiState.apply (topElement, this.unsupportedPlayback);
        this.readTags (topElement);
        this.readExclusiveGroups (groupsElement);
        this.checkPlaybackSupport (topElement);

        final double globalTuningOffset = XMLUtils.getDoubleAttribute (groupsElement, DecentSamplerTag.GLOBAL_TUNING, 0);
        final List<IGroup> groups = this.parseGroups (topElement, groupsElement, basePath, isLibrary ? sourceFile : null, globalTuningOffset);
        groups.removeIf (group -> group.getSampleZones ().isEmpty ());
        final int polyphony = parsePolyphony (topElement, groupsElement);

        if (!this.unsupportedPlayback.isEmpty ())
            this.notifier.log ("IDS_DS_UNSUPPORTED_PLAYBACK", presetName, String.join (", ", this.unsupportedPlayback));
        if (groups.isEmpty ())
        {
            this.notifier.log ("IDS_DS_EMPTY_PRESET", presetName);
            return Collections.emptyList ();
        }

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
        referencedTags.addAll (splitTags (element.getAttribute (DecentSamplerTag.TAGS_ATTRIBUTE)));
    }


    /**
     * Split a comma-separated list of tags.
     *
     * @param tags The list, might be null
     * @return The tags without surrounding white-space, empty entries are removed
     */
    private static Set<String> splitTags (final String tags)
    {
        final Set<String> result = new LinkedHashSet<> ();
        if (tags != null)
            for (final String tag: tags.split (","))
            {
                final String trimmed = tag.trim ();
                if (!trimmed.isEmpty ())
                    result.add (trimmed);
            }
        return result;
    }


    /**
     * Get the value of an attribute from the first of the given elements which sets it.
     *
     * @param attribute The name of the attribute
     * @param elements The elements, the most specific first
     * @return The trimmed value or an empty string if none of the elements sets it
     */
    private static String getInheritedAttribute (final String attribute, final Element... elements)
    {
        for (final Element element: elements)
        {
            final String value = element.getAttribute (attribute);
            if (value != null && !value.isBlank ())
                return value.trim ();
        }
        return "";
    }


    /**
     * Test if the value of a boolean attribute is true.
     *
     * @param value The value of the attribute
     * @return True if it is true or 1
     */
    private static boolean isTrue (final String value)
    {
        return "true".equalsIgnoreCase (value) || "1".equals (value);
    }


    /**
     * Read the volumes of the tags and which tags are disabled.
     *
     * @param topElement The top element
     */
    private void readTags (final Element topElement)
    {
        final Element tagsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.TAGS);
        if (tagsElement == null)
            return;

        for (final Element tagElement: XMLUtils.getChildElementsByName (tagsElement, DecentSamplerTag.TAG))
        {
            final String name = tagElement.getAttribute (DecentSamplerTag.TAG_NAME).trim ();
            this.tagGains.put (name, Double.valueOf (parseVolume (tagElement, DecentSamplerTag.VOLUME)));
            if (DecentSamplerUiState.isFalse (tagElement.getAttribute (DecentSamplerTag.ENABLED)))
                this.disabledTags.add (name);
        }
    }


    /**
     * Convert the silencing of samples by tags into exclusive groups. A sample stops playing when a
     * sample which carries one of its silencing tags is triggered. An exclusive group of the model
     * is mutual: each of its zones stops all the others. Therefore, a tag becomes an exclusive
     * group only if it is the one silencing tag of every sample which carries it and if every
     * sample which it silences carries it as well, e.g. several hi-hats which all carry and are
     * silenced by the tag 'hihat'. Other silencing is reported since it cannot be converted.
     *
     * @param groupsElement The groups element
     */
    private void readExclusiveGroups (final Element groupsElement)
    {
        final Map<Element, Set<String>> sampleTags = new LinkedHashMap<> ();
        final Map<Element, Set<String>> silencingTags = new LinkedHashMap<> ();
        final Set<String> mutualTags = new HashSet<> ();
        boolean isSilencingFaded = false;
        for (final Element groupElement: XMLUtils.getChildElementsByName (groupsElement, DecentSamplerTag.GROUP))
        {
            if (this.isGroupSkipped (groupsElement, groupElement))
                continue;
            for (final Element sampleElement: XMLUtils.getChildElementsByName (groupElement, DecentSamplerTag.SAMPLE, false))
            {
                if (this.isSampleSkipped (sampleElement))
                    continue;

                final Set<String> tags = new HashSet<> ();
                addReferencedTags (tags, groupsElement);
                addReferencedTags (tags, groupElement);
                addReferencedTags (tags, sampleElement);
                sampleTags.put (sampleElement, tags);

                final Set<String> silencedBy = splitTags (getInheritedAttribute (DecentSamplerTag.SILENCED_BY_TAGS, sampleElement, groupElement, groupsElement));
                if (silencedBy.isEmpty ())
                    continue;
                silencingTags.put (sampleElement, silencedBy);
                if (silencedBy.size () == 1)
                    mutualTags.addAll (silencedBy);

                // Only stopping a sample immediately is converted
                final String mode = getInheritedAttribute (DecentSamplerTag.SILENCING_MODE, sampleElement, groupElement, groupsElement);
                final String decay = getInheritedAttribute (DecentSamplerTag.SILENCING_DECAY, sampleElement, groupElement, groupsElement);
                if (!mode.isEmpty () && !DecentSamplerTag.SILENCING_MODE_FAST.equalsIgnoreCase (mode) || !decay.isEmpty () && !DecentSamplerUiState.isFalse (decay))
                    isSilencingFaded = true;
            }
        }
        if (isSilencingFaded)
            this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_SILENCING_FADE"));

        // Remove the tags which do not silence mutually
        for (final Map.Entry<Element, Set<String>> entry: sampleTags.entrySet ())
        {
            final Set<String> silencedBy = silencingTags.getOrDefault (entry.getKey (), Collections.emptySet ());
            for (final String tag: entry.getValue ())
                if (!(silencedBy.size () == 1 && silencedBy.contains (tag)))
                    mutualTags.remove (tag);
            if (silencedBy.size () == 1 && !entry.getValue ().containsAll (silencedBy))
                mutualTags.removeAll (silencedBy);
        }

        // Number the exclusive groups in the order of the samples
        final Map<String, Integer> groupNumbers = new HashMap<> ();
        for (final Map.Entry<Element, Set<String>> entry: silencingTags.entrySet ())
        {
            final Set<String> silencedBy = entry.getValue ();
            final String tag = silencedBy.iterator ().next ();
            if (silencedBy.size () == 1 && mutualTags.contains (tag))
                this.exclusiveGroups.put (entry.getKey (), groupNumbers.computeIfAbsent (tag, _ -> Integer.valueOf (groupNumbers.size () + 1)));
            else
                this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_SILENCING"));
        }
    }


    /**
     * Test if a group is not played. DecentSampler does not play a disabled group or a group which
     * carries a disabled tag, therefore it is skipped. Except when each group becomes its own
     * multi-sample: presets which contain several alternative kits as groups enable only one of
     * them, but all kits are wanted then.
     *
     * @param groupsElement The groups element
     * @param groupElement The group element
     * @return True if the group is skipped
     */
    private boolean isGroupSkipped (final Element groupsElement, final Element groupElement)
    {
        if (this.settingsConfiguration.isMultisamplePerGroup ())
            return false;
        return DecentSamplerUiState.isFalse (groupElement.getAttribute (DecentSamplerTag.GROUP_ENABLED)) || this.hasDisabledTag (groupsElement) || this.hasDisabledTag (groupElement);
    }


    /**
     * Test if a sample is not played since it carries a disabled tag. See
     * {@link #isGroupSkipped(Element, Element)}.
     *
     * @param sampleElement The sample element
     * @return True if the sample is skipped
     */
    private boolean isSampleSkipped (final Element sampleElement)
    {
        return !this.settingsConfiguration.isMultisamplePerGroup () && this.hasDisabledTag (sampleElement);
    }


    /**
     * Test if an element carries a disabled tag.
     *
     * @param element The element
     * @return True if one of the tags of the element is disabled
     */
    private boolean hasDisabledTag (final Element element)
    {
        if (this.disabledTags.isEmpty ())
            return false;
        final Set<String> tags = new HashSet<> ();
        addReferencedTags (tags, element);
        return !Collections.disjoint (tags, this.disabledTags);
    }


    /**
     * Get the sum of the gains of all tags of the current sample, its group and the groups.
     *
     * @return The gain in dB
     */
    private double getTagGain ()
    {
        final Set<String> tags = new HashSet<> ();
        addReferencedTags (tags, this.currentGroupsElement);
        addReferencedTags (tags, this.currentGroupElement);
        addReferencedTags (tags, this.currentSampleElement);
        double gain = 0;
        for (final String tag: tags)
            gain += this.tagGains.getOrDefault (tag, Double.valueOf (0)).doubleValue ();
        return gain;
    }


    /**
     * Get the gain of the main output of the current sample. DecentSampler can send a sample to up
     * to 8 outputs, by default only to the first which is the main output. Only the main output is
     * converted, sending to the other outputs is reported. A sample which is not sent to the main
     * output at all stays audible.
     *
     * @return The gain in dB
     */
    private double getOutputGain ()
    {
        boolean hasMainOutput = false;
        double level = 0;
        for (int output = 1; output <= DecentSamplerTag.NUMBER_OF_OUTPUTS; output++)
        {
            final String attribute = DecentSamplerTag.OUTPUT + output;
            final String target = this.getAttribute (attribute + DecentSamplerTag.OUTPUT_TARGET).orElse (output == 1 ? DecentSamplerTag.OUTPUT_MAIN : DecentSamplerTag.OUTPUT_NONE);
            final double outputLevel = Math.max (0, this.getDoubleValue (attribute + DecentSamplerTag.OUTPUT_VOLUME, 1));
            if (DecentSamplerTag.OUTPUT_MAIN.equals (target))
            {
                hasMainOutput = true;
                level += outputLevel;
            }
            else if (!DecentSamplerTag.OUTPUT_NONE.equals (target) && outputLevel > 0)
                this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_OUTPUTS"));
        }
        return hasMainOutput ? MathUtils.valueToDb (level) : 0;
    }


    /**
     * Get the sum of the levels of the enabled gain effects of an element.
     *
     * @param parentElement The element which contains the effects
     * @return The gain in dB
     */
    private static double parseGainEffects (final Element parentElement)
    {
        final Element effectsElement = XMLUtils.getChildElementByName (parentElement, DecentSamplerTag.EFFECTS);
        if (effectsElement == null)
            return 0;

        double gain = 0;
        for (final Element effectElement: XMLUtils.getChildElementsByName (effectsElement, DecentSamplerTag.EFFECTS_EFFECT, false))
        {
            if (!DecentSamplerTag.EFFECT_TYPE_GAIN.equalsIgnoreCase (effectElement.getAttribute (DecentSamplerTag.EFFECT_TYPE)) || DecentSamplerUiState.isFalse (effectElement.getAttribute (DecentSamplerTag.ENABLED)))
                continue;
            // The level is in decibels unless its unit is set to linear
            if (DecentSamplerTag.LEVEL_UNIT_LINEAR.equals (effectElement.getAttribute (DecentSamplerTag.EFFECT_LEVEL_UNIT)))
                gain += parseVolume (effectElement, DecentSamplerTag.EFFECT_LEVEL);
            else
                gain += XMLUtils.getDoubleAttribute (effectElement, DecentSamplerTag.EFFECT_LEVEL, 0);
        }
        return gain;
    }


    /**
     * Report the features of a preset which change how it sounds but cannot be converted: effects
     * besides one filter and the gain effect in an effect chain, MIDI controller bindings, effect
     * buses, the arpeggiator and note sequences.
     *
     * @param topElement The top element
     */
    private void checkPlaybackSupport (final Element topElement)
    {
        final NodeList effectsElements = topElement.getElementsByTagName (DecentSamplerTag.EFFECTS);
        for (int i = 0; i < effectsElements.getLength (); i++)
        {
            int filters = 0;
            for (final Element effectElement: XMLUtils.getChildElementsByName (effectsElements.item (i), DecentSamplerTag.EFFECTS_EFFECT, false))
            {
                if (DecentSamplerUiState.isFalse (effectElement.getAttribute (DecentSamplerTag.ENABLED)))
                    continue;
                final String type = effectElement.getAttribute (DecentSamplerTag.EFFECT_TYPE).toLowerCase (Locale.ROOT);
                if (FILTER_TYPE_MAP.containsKey (type))
                {
                    filters++;
                    if (filters > 1)
                        this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_FILTER_CHAIN"));
                }
                else if (!DecentSamplerTag.EFFECT_TYPE_GAIN.equals (type) && isAudible (effectElement, type))
                    this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_EFFECT", type));
            }
        }

        final Element midiElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.MIDI);
        if (midiElement != null && midiElement.getElementsByTagName (DecentSamplerTag.BINDING).getLength () > 0)
            this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_MIDI"));
        final Element busesElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.BUSES);
        if (busesElement != null && busesElement.getElementsByTagName (DecentSamplerTag.EFFECTS_EFFECT).getLength () > 0)
            this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_BUSES"));
        final Element arpeggiatorElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.ARPEGGIATOR);
        if (arpeggiatorElement != null && !DecentSamplerUiState.isFalse (arpeggiatorElement.getAttribute (DecentSamplerTag.ENABLED)))
            this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_ARPEGGIATOR"));
        if (XMLUtils.getChildElementByName (topElement, DecentSamplerTag.NOTE_SEQUENCES) != null)
            this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_NOTE_SEQUENCES"));
    }


    /**
     * Test if an effect changes the sound. The wet level of a reverb is 0 if it is not set and a
     * pitch shift by 0 semitones keeps the pitch.
     *
     * @param effectElement The effect element
     * @param type The type of the effect
     * @return True if the effect has a mix and wet level above 0
     */
    private static boolean isAudible (final Element effectElement, final String type)
    {
        if (DecentSamplerTag.EFFECT_TYPE_PITCH.equals (type) && XMLUtils.getDoubleAttribute (effectElement, DecentSamplerTag.EFFECT_PITCH_SHIFT, 0) == 0)
            return false;
        final double wetLevel = XMLUtils.getDoubleAttribute (effectElement, DecentSamplerTag.EFFECT_WET_LEVEL, DecentSamplerTag.EFFECT_TYPE_REVERB.equals (type) ? 0 : 1);
        return wetLevel > 0 && XMLUtils.getDoubleAttribute (effectElement, DecentSamplerTag.EFFECT_MIX, 1) > 0;
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
            if (DecentSamplerUiState.isFalse (effectElement.getAttribute (DecentSamplerTag.ENABLED)))
                continue;
            final String effectType = effectElement.getAttribute (DecentSamplerTag.EFFECT_TYPE).toLowerCase (Locale.ROOT);
            final FilterType filterType = FILTER_TYPE_MAP.get (effectType);
            if (filterType != null)
            {
                final int poles = FILTER_POLES_MAP.get (effectType).intValue ();
                final double frequency = XMLUtils.getDoubleAttribute (effectElement, DecentSamplerTag.EFFECT_FREQUENCY, IFilter.MAX_FREQUENCY);
                final double resonance = Math.clamp ((XMLUtils.getDoubleAttribute (effectElement, DecentSamplerTag.EFFECT_RESONANCE, 0.7) - 0.7) / 4.3, 0, 1);
                final IFilter filter = new DefaultFilter (filterType, poles, frequency, resonance);

                // Parse the filter envelope
                final Element modulatorsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.MODULATORS);
                if (modulatorsElement != null)
                    for (final Element envelopeElement: XMLUtils.getChildElementsByName (modulatorsElement, DecentSamplerTag.ENVELOPE))
                    {
                        final Element bindingElement = XMLUtils.getChildElementByName (envelopeElement, DecentSamplerTag.BINDING);
                        if (bindingElement != null && "FX_FILTER_FREQUENCY".equals (bindingElement.getAttribute (DecentSamplerTag.BINDING_PARAMETER)))
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
        // The volumes of the groups element, a group and a sample are separate levels which are
        // multiplied, while the pan of a sample is inherited from its group and the groups element
        // and has separate offsets for the whole instrument and each group
        final double globalGain = parseVolume (groupElements, groupElements.hasAttribute (DecentSamplerTag.GLOBAL_VOLUME) ? DecentSamplerTag.GLOBAL_VOLUME : DecentSamplerTag.VOLUME) + parseGainEffects (topElement);
        final double globalPan = XMLUtils.getDoubleAttribute (groupElements, DecentSamplerTag.GLOBAL_PAN, 0) / 100.0;
        int groupCounter = 1;
        for (final Element groupElement: groupNodes)
        {
            this.currentGroupElement = groupElement;
            this.currentSampleElement = null;

            this.checkAttributes (DecentSamplerTag.GROUP, groupElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.GROUP));

            // Since we cannot support enabling deactivated groups in any way, simply skip them
            if (this.isGroupSkipped (groupElements, groupElement))
                continue;

            final String k = groupElement.getAttribute (DecentSamplerTag.GROUP_NAME);
            final String groupName = k == null || k.isBlank () ? "Group " + groupCounter : k;
            final IGroup group = new DefaultGroup (groupName);

            final double groupVolumeOffset = parseVolume (groupElement, groupElement.hasAttribute (DecentSamplerTag.GROUP_VOLUME) ? DecentSamplerTag.GROUP_VOLUME : DecentSamplerTag.VOLUME) + parseGainEffects (groupElement);
            final double groupPanningOffset = XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.GROUP_PAN, 0) / 100.0;
            final double groupTuningOffset = XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.GROUP_TUNING, 0);

            // Note: all three values are additionally flattened into each zone of the group below,
            // which must not be changed. They are stored here as well since DecentSampler does
            // have a real group layer. The volume is already in dB and the tuning already in
            // semi-tones, only the panning needs to be normalized from [-100..100] to [-1..1]
            if (groupVolumeOffset != 0)
                group.setGain (groupVolumeOffset);
            if (groupPanningOffset != 0)
                group.setPanning (Math.clamp (groupPanningOffset, -1.0, 1.0));
            if (groupTuningOffset != 0)
                group.setTuning (groupTuningOffset);

            final String triggerAttribute = groupElement.getAttribute (DecentSamplerTag.TRIGGER);

            this.parseGroup (topElement, group, groupElement, basePath, libraryFile, globalGain + groupVolumeOffset, globalPan + groupPanningOffset, globalTuningOffset + groupTuningOffset, triggerAttribute);
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
        // IMPROVE: Should be added to group itself but needs to be adapted in all other formats
        final Optional<IFilter> optFilter = parseFilterEffect (topElement, groupElement);
        final Optional<IEnvelopeModulator> pitchModulation = parsePitchModulation (topElement);
        final Optional<ILfoModulator> pitchLfoModulation = parsePitchLfoModulation (topElement);
        final Optional<ILfoModulator> amplitudeLfoModulation = parseAmplitudeLfoModulation (topElement);

        for (final Element sampleElement: XMLUtils.getChildElementsByName (groupElement, DecentSamplerTag.SAMPLE, false))
        {
            this.currentSampleElement = sampleElement;

            this.checkAttributes (DecentSamplerTag.SAMPLE, sampleElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.SAMPLE));
            this.checkChildTags (DecentSamplerTag.SAMPLE, DecentSamplerTag.SAMPLE_TAGS, XMLUtils.getChildElements (sampleElement));

            if (this.isSampleSkipped (sampleElement))
                continue;

            final Optional<DefaultSampleZone> optSampleZone = this.createSampleZone (basePath, libraryFile, sampleElement);
            if (optSampleZone.isEmpty ())
                continue;

            final ISampleZone sampleZone = optSampleZone.get ();
            this.convertSampleZone (sampleElement, sampleZone, groupVolumeOffset, groupPanningOffset, tuningOffset, trigger);

            // Without the amplitude envelope a sample plays to its end, as a one-shot
            final boolean isOneShot = DecentSamplerUiState.isFalse (this.getAttribute (DecentSamplerTag.AMP_ENV_ENABLED).orElse (""));
            sampleZone.setOneShot (isOneShot);
            if (!isOneShot)
                this.convertVolumeEnvelope (sampleZone.getAmplitudeEnvelopeModulator ().getSource ());
            sampleZone.getAmplitudeVelocityModulator ().setDepth (this.getDoubleValue (DecentSamplerTag.AMP_VELOCITY_TRACK, 1));
            final Integer exclusiveGroup = this.exclusiveGroups.get (sampleElement);
            if (exclusiveGroup != null)
                sampleZone.setExclusiveGroup (exclusiveGroup.intValue ());

            // Check for sequence e.g. round robin
            final Optional<String> seqModeAttribute = this.getAttribute (DecentSamplerTag.SEQ_MODE);
            if (seqModeAttribute.isPresent () && !DecentSamplerTag.SEQ_ALWAYS.equalsIgnoreCase (seqModeAttribute.get ()))
            {
                sampleZone.setPlayLogic (parsePlayLogic (seqModeAttribute.get ()));

                final int seqPosition = (int) this.getDoubleValue (DecentSamplerTag.SEQ_POSITION, -1);
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
            if (amplitudeLfoModulation.isPresent ())
            {
                final ILfoModulator lfoModulator = amplitudeLfoModulation.get ();
                final ILfoModulator amplitudeLfoModulator = sampleZone.getAmplitudeLfoModulator ();
                amplitudeLfoModulator.setDepth (lfoModulator.getDepth ());
                amplitudeLfoModulator.setSource (lfoModulator.getSource ());
            }

            group.addSampleZone (sampleZone);
        }
    }


    private void convertSampleZone (final Element sampleElement, final ISampleZone sampleZone, final double groupVolumeOffset, final double groupPanningOffset, final double tuningOffset, final String trigger)
    {
        final String triggerAttribute = this.getAttribute (DecentSamplerTag.TRIGGER).orElse (trigger);
        if (triggerAttribute != null && !triggerAttribute.isBlank ())
            try
            {
                sampleZone.setTrigger (TriggerType.valueOf (triggerAttribute.toUpperCase (Locale.ENGLISH)));
            }
            catch (final IllegalArgumentException _)
            {
                this.notifier.logError ("IDS_DS_UNKNOWN_TRIGGER", triggerAttribute);
            }

        sampleZone.setStart ((int) Math.round (this.getDoubleValue (DecentSamplerTag.START, 0)));
        sampleZone.setStop ((int) Math.round (this.getDoubleValue (DecentSamplerTag.END, -1)));
        sampleZone.setGain (groupVolumeOffset + parseVolume (sampleElement, DecentSamplerTag.VOLUME) + this.getTagGain () + this.getOutputGain ());
        sampleZone.setPanning (Math.clamp (groupPanningOffset + this.getDoubleValue (DecentSamplerTag.PANNING, 0) / 100.0, -1, 1));
        sampleZone.setTuning (tuningOffset + this.getDoubleValue (DecentSamplerTag.TUNING, 0));

        sampleZone.setPlayLogic (parsePlayLogic (this.getAttribute (DecentSamplerTag.SEQ_MODE).orElse (DecentSamplerTag.SEQ_ALWAYS)));

        sampleZone.setKeyTracking (this.getDoubleValue (DecentSamplerTag.PITCH_KEY_TRACK, 1));
        sampleZone.setKeyRoot (this.getNoteValue (DecentSamplerTag.ROOT_NOTE, -1));
        sampleZone.setKeyLow (this.getNoteValue (DecentSamplerTag.LO_NOTE, 0));
        sampleZone.setKeyHigh (this.getNoteValue (DecentSamplerTag.HI_NOTE, 127));

        final int velLow = (int) this.getDoubleValue (DecentSamplerTag.LO_VEL, -1);
        final int velHigh = (int) this.getDoubleValue (DecentSamplerTag.HI_VEL, -1);
        if (velLow > 0)
            sampleZone.setVelocityLow (velLow);
        if (velHigh > 0)
            sampleZone.setVelocityHigh (velHigh);

        // -----------------------------------------------------------
        // Loops

        final int loopStart = (int) Math.round (this.getDoubleValue (DecentSamplerTag.LOOP_START, -1));
        final int loopEnd = (int) Math.round (this.getDoubleValue (DecentSamplerTag.LOOP_END, -1));
        final int loopCrossfade = (int) Math.round (this.getDoubleValue (DecentSamplerTag.LOOP_CROSSFADE, 0));

        // An explicitly disabled loop also suppresses loops from the sample file chunks
        final String loopEnabledAttribute = this.getAttribute (DecentSamplerTag.LOOP_ENABLED).orElse ("");
        final boolean isLoopDisabled = DecentSamplerUiState.isFalse (loopEnabledAttribute);

        try
        {
            final Optional<ISampleData> sampleData = sampleZone.getSampleData ();
            if (sampleData.isPresent ())
            {
                // Start and end of the sample and of the loop are inclusive, a missing end is the
                // last frame
                final ISampleData data = sampleData.get ();
                final int lastFrame = data.getAudioMetadata ().getNumberOfSamples () - 1;
                final int stop = sampleZone.getStop ();
                data.addZoneData (sampleZone, sampleZone.getKeyRoot () < 0, !isLoopDisabled);
                sampleZone.setStop (stop < 0 ? lastFrame : Math.min (stop, lastFrame));

                // Loop points which are not set are taken from the loop in the sample file
                if (!isLoopDisabled && (loopStart >= 0 || loopEnd >= 0 || loopCrossfade > 0 || isTrue (loopEnabledAttribute)))
                {
                    final List<ISampleLoop> embeddedLoops = sampleZone.getLoops ();
                    final ISampleLoop embeddedLoop = embeddedLoops.isEmpty () ? null : embeddedLoops.get (0);
                    final DefaultSampleLoop loop = new DefaultSampleLoop ();
                    if (embeddedLoop != null)
                        loop.setType (embeddedLoop.getType ());
                    loop.setStart (loopStart >= 0 ? loopStart : embeddedLoop == null ? 0 : embeddedLoop.getStart ());
                    loop.setEnd (Math.min (loopEnd >= 0 ? loopEnd : embeddedLoop == null ? lastFrame : embeddedLoop.getEnd (), lastFrame));
                    embeddedLoops.clear ();
                    if (loop.getStart () < loop.getEnd ())
                    {
                        loop.setCrossfadeInSamples (loopCrossfade);
                        sampleZone.addLoop (loop);
                        if (loopCrossfade > 0)
                            this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_LOOP_CROSSFADE"));
                    }
                    else
                        this.unsupportedPlayback.add (Functions.getMessage ("IDS_DS_FEATURE_INVALID_LOOP"));
                }
            }
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
        }
    }


    /**
     * Convert the amplitude envelope of the current sample. If the preset does not set any of its
     * parameters, the envelope stays unset, which applies the default envelope of the category of
     * the preset. Otherwise, a curve which is not set gets the default of DecentSampler, which is
     * logarithmic for the attack and exponential for the decay and the release.
     *
     * @param envelope The envelope to fill
     */
    private void convertVolumeEnvelope (final IEnvelope envelope)
    {
        boolean isSet = false;
        for (final String attribute: ENVELOPE_ATTRIBUTES)
            isSet |= this.getAttribute (attribute).isPresent ();
        if (!isSet)
            return;

        envelope.setAttackTime (this.getDoubleValue (DecentSamplerTag.ENV_ATTACK, -1));
        envelope.setDecayTime (this.getDoubleValue (DecentSamplerTag.ENV_DECAY, -1));
        envelope.setSustainLevel (this.getDoubleValue (DecentSamplerTag.ENV_SUSTAIN, -1));
        envelope.setReleaseTime (this.getDoubleValue (DecentSamplerTag.ENV_RELEASE, -1));

        envelope.setAttackSlope (this.getDoubleValue (DecentSamplerTag.ENV_ATTACK_CURVE, -100) / 100.0);
        envelope.setDecaySlope (this.getDoubleValue (DecentSamplerTag.ENV_DECAY_CURVE, 100) / 100.0);
        envelope.setReleaseSlope (this.getDoubleValue (DecentSamplerTag.ENV_RELEASE_CURVE, 100) / 100.0);
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

        final File sampleFile = new File (basePath, sampleName.replace ('\\', '/'));
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
                if (bindingElement != null && "GROUP_TUNING".equals (bindingElement.getAttribute (DecentSamplerTag.BINDING_PARAMETER)))
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
        // Parse a low frequency oscillator bound to the pitch (vibrato). The oscillator bound to
        // the
        // global tuning in the template is a mod-wheel routing and is intentionally not matched.
        final Element modulatorsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.MODULATORS);
        if (modulatorsElement != null)
            for (final Element lfoElement: XMLUtils.getChildElementsByName (modulatorsElement, DecentSamplerTag.LFO))
            {
                final Element bindingElement = XMLUtils.getChildElementByName (lfoElement, DecentSamplerTag.BINDING);
                if (bindingElement == null || !"GROUP_TUNING".equals (bindingElement.getAttribute (DecentSamplerTag.BINDING_PARAMETER)))
                    continue;

                final double depth = XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.MOD_AMOUNT, 0);
                if (depth == 0)
                    continue;

                final ILfoModulator pitchLfoModulator = new DefaultLfoModulator (depth);
                final ILfo pitchLfo = pitchLfoModulator.getSource ();
                pitchLfo.setWaveform (toLfoWaveform (lfoElement.getAttribute (DecentSamplerTag.LFO_SHAPE)));
                // Only a frequency given in Hertz can be converted; a tempo synchronized rate has
                // no
                // representation without a tempo
                final String frequencyFormat = lfoElement.getAttribute (DecentSamplerTag.LFO_FREQUENCY_FORMAT);
                if (frequencyFormat.isEmpty () || "hz".equals (frequencyFormat))
                    pitchLfo.setRate (XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.LFO_FREQUENCY, -1));
                pitchLfo.setDelay (XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.LFO_DELAY_TIME, -1));
                return Optional.of (pitchLfoModulator);
            }
        return Optional.empty ();
    }


    private static Optional<ILfoModulator> parseAmplitudeLfoModulation (final Element topElement)
    {
        // Parse a low frequency oscillator bound to the group volume (tremolo)
        final Element modulatorsElement = XMLUtils.getChildElementByName (topElement, DecentSamplerTag.MODULATORS);
        if (modulatorsElement != null)
            for (final Element lfoElement: XMLUtils.getChildElementsByName (modulatorsElement, DecentSamplerTag.LFO))
            {
                final Element bindingElement = XMLUtils.getChildElementByName (lfoElement, DecentSamplerTag.BINDING);
                if (bindingElement == null || !"AMP_VOLUME".equals (bindingElement.getAttribute (DecentSamplerTag.BINDING_PARAMETER)))
                    continue;

                final double modAmount = XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.MOD_AMOUNT, 0);
                if (modAmount <= 0)
                    continue;

                // The volume parameter is linear with 1 being full volume. The bipolar oscillator
                // swings by the modulation amount around it, therefore a swing of x dips to 1-x
                // which is -20*log10(1-x) dB below full volume.
                final double depthDecibels = modAmount >= 1 ? ILfoModulator.MAX_VOLUME_DEPTH : Math.min (-20.0 * Math.log10 (1.0 - modAmount), ILfoModulator.MAX_VOLUME_DEPTH);
                final ILfoModulator amplitudeLfoModulator = new DefaultLfoModulator (depthDecibels / ILfoModulator.MAX_VOLUME_DEPTH);
                final ILfo amplitudeLfo = amplitudeLfoModulator.getSource ();
                amplitudeLfo.setWaveform (toLfoWaveform (lfoElement.getAttribute (DecentSamplerTag.LFO_SHAPE)));
                // Only a frequency given in Hertz can be converted; a tempo synchronized rate has
                // no representation without a tempo
                final String frequencyFormat = lfoElement.getAttribute (DecentSamplerTag.LFO_FREQUENCY_FORMAT);
                if (frequencyFormat.isEmpty () || "hz".equals (frequencyFormat))
                    amplitudeLfo.setRate (XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.LFO_FREQUENCY, -1));
                amplitudeLfo.setDelay (XMLUtils.getDoubleAttribute (lfoElement, DecentSamplerTag.LFO_DELAY_TIME, -1));
                return Optional.of (amplitudeLfoModulator);
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
     * @param attributeName The name of the attribute from which to get the note value
     * @param defaultValue The value when the attribute is absent
     * @return The value
     */
    private int getNoteValue (final String attributeName, final int defaultValue)
    {
        final Optional<String> value = this.getAttribute (attributeName);
        return value.isPresent () ? NoteParser.parseNote (value.get ()) : defaultValue;
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

        // A value without a unit is a linear amplitude (1.0 = 0dB)
        return MathUtils.valueToDb (Double.parseDouble (attribute));
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
