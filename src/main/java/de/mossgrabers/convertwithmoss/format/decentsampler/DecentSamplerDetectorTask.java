// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively DecentSampler preset and library files in folders. Files must end with
 * <i>.dspreset</i> or <i>.dslibrary</i>.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerDetectorTask extends AbstractDetectorTask
{
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";

    private static final String ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";
    private static final String ENDING_DSLIBRARY      = ".dslibrary";
    private static final String ENDING_DSPRESET       = ".dspreset";

    private Element             currentGroupsElement;
    private Element             currentGroupElement;
    private Element             currentSampleElement;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    protected DecentSamplerDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ENDING_DSPRESET, ENDING_DSLIBRARY);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final List<IMultisampleSource> result = file.getName ().endsWith (ENDING_DSPRESET) ? this.processPresetFile (file) : this.processLibraryFile (file);

        this.printUnsupportedElements ();
        this.printUnsupportedAttributes ();

        return result;
    }


    /**
     * Reads a DecentSampler library file and processes all presets it contains.
     *
     * @param file The library file
     * @return The processed multi samples
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
     * @return The parsed multi samples
     * @throws IOException Could not process the file
     */
    private List<IMultisampleSource> processFile (final File file, final ZipFile zipFile, final ZipEntry entry) throws IOException
    {
        final String name = entry.getName ();
        if (name == null || !name.endsWith (ENDING_DSPRESET))
            return Collections.emptyList ();

        String parent = new File (name).getParent ();
        if (parent == null)
            parent = "";

        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final String content = fixInvalidXML (StreamUtils.readUTF8 (in));
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (file, parent, true, document);
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
     * @return The processed multi sample (singleton list)
     */
    private List<IMultisampleSource> processPresetFile (final File file)
    {
        try (final FileInputStream in = new FileInputStream (file))
        {
            final String content = fixInvalidXML (StreamUtils.readUTF8 (in));
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (file, file.getParent (), false, document);
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The preset or library file
     * @param basePath The parent folder, in case of a library the relative folder in the ZIP
     *            directory structure
     * @param isLibrary If it is a library otherwise a preset
     * @param document The XML document to parse
     * @return The parsed multisample source
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String basePath, final boolean isLibrary, final Document document)
    {
        final Element top = document.getDocumentElement ();
        if (!DecentSamplerTag.DECENTSAMPLER.equals (top.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        this.checkAttributes (DecentSamplerTag.DECENTSAMPLER, top.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.DECENTSAMPLER));
        this.checkChildTags (DecentSamplerTag.DECENTSAMPLER, DecentSamplerTag.TOP_LEVEL_TAGS, XMLUtils.getChildElements (top));

        final Element groupsElement = XMLUtils.getChildElementByName (top, DecentSamplerTag.GROUPS);
        if (groupsElement == null)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }
        this.currentGroupsElement = groupsElement;

        final double globalTuningOffset = XMLUtils.getDoubleAttribute (groupsElement, DecentSamplerTag.GLOBAL_TUNING, 0);

        final List<IGroup> groups = this.parseGroups (groupsElement, basePath, isLibrary ? multiSampleFile : null, globalTuningOffset);

        final String name = FileUtils.getNameWithoutType (multiSampleFile);
        final String n = this.metadataConfig.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, n);

        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (groups), parts);

        multisampleSource.setGroups (groups);

        parseEffects (top, multisampleSource);

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parse the effects on the top level.
     *
     * @param top The top element
     * @param multisampleSource The multisample to fill
     */
    private static void parseEffects (final Element top, final DefaultMultisampleSource multisampleSource)
    {
        final Element effectsElement = XMLUtils.getChildElementByName (top, DecentSamplerTag.EFFECTS);
        if (effectsElement == null)
            return;

        for (final Element effectElement: XMLUtils.getChildElementsByName (top, DecentSamplerTag.EFFECTS_EFFECT, false))
        {
            final String effectType = effectElement.getAttribute ("type");
            if ("lowpass_4pl".equals (effectType))
            {
                final double frequency = XMLUtils.getDoubleAttribute (effectElement, "frequency", IFilter.MAX_FREQUENCY);
                final double resonance = XMLUtils.getDoubleAttribute (effectElement, "resonance", 0);
                multisampleSource.setGlobalFilter (new DefaultFilter (FilterType.LOW_PASS, 4, frequency, resonance));
                return;
            }
        }
    }


    /**
     * Parses all groups.
     *
     * @param groupElements The XML element containing all groups
     * @param basePath The base path of the samples
     * @param libraryFile If it is a library otherwise null
     * @param globalTuningOffset The global tuning offset
     * @return All parsed groups
     */
    private List<IGroup> parseGroups (final Element groupElements, final String basePath, final File libraryFile, final double globalTuningOffset)
    {
        final Node [] groupNodes = XMLUtils.getChildrenByName (groupElements, DecentSamplerTag.GROUP, false);
        final List<IGroup> groups = new ArrayList<> (groupNodes.length);
        int groupCounter = 1;
        for (final Node groupNode: groupNodes)
            if (groupNode instanceof final Element groupElement)
            {
                this.currentGroupElement = groupElement;

                this.checkAttributes (DecentSamplerTag.GROUP, groupElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.GROUP));

                final String k = groupElement.getAttribute (DecentSamplerTag.GROUP_NAME);
                final String groupName = k == null || k.isBlank () ? "Velocity Layer " + groupCounter : k;
                final DefaultGroup group = new DefaultGroup (groupName);

                final double groupVolumeOffset = parseVolume (groupElement, DecentSamplerTag.VOLUME);
                double groupTuningOffset = XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.GROUP_TUNING, 0);
                // Actually not in the specification but support it anyway
                if (groupTuningOffset == 0)
                    groupTuningOffset = XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.TUNING, 0);

                final String triggerAttribute = groupElement.getAttribute (DecentSamplerTag.TRIGGER);

                this.parseGroup (group, groupElement, basePath, libraryFile, groupVolumeOffset, globalTuningOffset + groupTuningOffset, triggerAttribute);
                groups.add (group);
                groupCounter++;
            }
            else
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                return Collections.emptyList ();
            }
        return groups;
    }


    /**
     * Parse a group.
     *
     * @param group The object to fill in the data
     * @param groupElement The XML group element
     * @param basePath The base path of the samples
     * @param libraryFile If it is a library otherwise null
     * @param groupVolumeOffset The volume offset
     * @param tuningOffset The tuning offset
     * @param trigger The trigger value
     */
    private void parseGroup (final DefaultGroup group, final Element groupElement, final String basePath, final File libraryFile, final double groupVolumeOffset, final double tuningOffset, final String trigger)
    {
        for (final Element sampleElement: XMLUtils.getChildElementsByName (groupElement, DecentSamplerTag.SAMPLE, false))
        {
            this.currentSampleElement = sampleElement;

            this.checkAttributes (DecentSamplerTag.SAMPLE, sampleElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.SAMPLE));
            this.checkChildTags (DecentSamplerTag.SAMPLE, DecentSamplerTag.SAMPLE_TAGS, XMLUtils.getChildElements (sampleElement));

            final String sampleName = sampleElement.getAttribute (DecentSamplerTag.PATH);
            if (sampleName == null || sampleName.isBlank ())
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                return;
            }

            final File sampleFile = new File (basePath, sampleName);
            final String zoneName = FileUtils.getNameWithoutType (sampleFile);
            final ISampleData sampleData;
            try
            {
                if (libraryFile == null)
                {
                    if (!AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                        return;
                    sampleData = new WavFileSampleData (sampleFile);
                }
                else
                    sampleData = new WavFileSampleData (libraryFile, sampleFile);
            }
            catch (final IOException ex)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
                return;
            }
            final DefaultSampleZone sampleZone = new DefaultSampleZone (zoneName, sampleData);

            String triggerAttribute = sampleElement.getAttribute (DecentSamplerTag.TRIGGER);
            if (triggerAttribute == null || triggerAttribute.isBlank ())
                triggerAttribute = trigger;
            if (triggerAttribute != null && !triggerAttribute.isBlank ())
                try
                {
                    sampleZone.setTrigger (TriggerType.valueOf (triggerAttribute.toUpperCase (Locale.ENGLISH)));
                }
                catch (final IllegalArgumentException ex)
                {
                    this.notifier.logError ("IDS_DS_UNKNOWN_TRIGGER", triggerAttribute);
                }

            sampleZone.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.START, -1)));
            sampleZone.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.END, -1)));
            sampleZone.setGain (groupVolumeOffset + parseVolume (sampleElement, DecentSamplerTag.VOLUME));
            sampleZone.setTune (tuningOffset + XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.TUNING, 0));

            final String zoneLogic = this.currentGroupsElement.getAttribute (DecentSamplerTag.SEQ_MODE);
            sampleZone.setPlayLogic (zoneLogic != null && "round_robin".equals (zoneLogic) ? PlayLogic.ROUND_ROBIN : PlayLogic.ALWAYS);

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

            /////////////////////////////////////////////////////
            // Loops

            final int loopStart = (int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_START, -1));
            final int loopEnd = (int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_END, -1));
            final double loopCrossfade = XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_CROSSFADE, 0);

            if (loopStart >= 0 || loopEnd > 0 || loopCrossfade > 0)
            {
                final DefaultSampleLoop loop = new DefaultSampleLoop ();
                loop.setStart (loopStart);
                loop.setEnd (loopEnd);
                loop.setCrossfadeInSamples (loopCrossfade);
                sampleZone.addLoop (loop);
            }

            try
            {
                sampleZone.getSampleData ().addMetadata (sampleZone, false, false);
            }
            catch (final IOException ex)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            }

            /////////////////////////////////////////////////////
            // Volume envelope

            final IEnvelope amplitudeEnvelope = sampleZone.getAmplitudeModulator ().getSource ();
            amplitudeEnvelope.setAttack (this.getDoubleValue (DecentSamplerTag.AMP_ENV_ATTACK, -1));
            amplitudeEnvelope.setDecay (this.getDoubleValue (DecentSamplerTag.AMP_ENV_DECAY, -1));
            amplitudeEnvelope.setSustain (this.getDoubleValue (DecentSamplerTag.AMP_ENV_SUSTAIN, -1));
            amplitudeEnvelope.setRelease (this.getDoubleValue (DecentSamplerTag.AMP_ENV_RELEASE, -1));

            group.addSampleZone (sampleZone);
        }
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
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
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
        String value = this.currentGroupsElement == null ? null : this.currentGroupsElement.getAttribute (key);
        if (value == null || value.isBlank ())
        {
            value = this.currentGroupElement == null ? null : this.currentGroupElement.getAttribute (key);
            if (value == null || value.isBlank ())
                value = this.currentSampleElement == null ? null : this.currentSampleElement.getAttribute (key);
        }
        return Optional.ofNullable (value);
    }
}
