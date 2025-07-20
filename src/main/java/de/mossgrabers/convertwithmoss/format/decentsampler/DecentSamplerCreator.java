// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.ParameterLevel;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for dspreset multi-sample files. A dspreset has a description file encoded in XML. The
 * related samples are in a separate folder. The description file and sample files can optionally be
 * zipped into a dslibrary file.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerCreator extends AbstractWavCreator<DecentSamplerCreatorUI>
{
    private static final List<String> IGNORE_FILES         = Collections.singletonList ("ui.xml");
    private static final String       LIBRARY_INFO_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<DecentSamplerLibraryInfo name=\"%LIBRARY_NAME%\"/>";


    private class PresetResult
    {
        String             dsPreset;
        File               dsPresetFile;
        String             sampleFolder;
        IMultisampleSource sampleSource;
    }


    private static final IFilter DEFAULT_LOW_PASS_FILTER = new DefaultFilter (FilterType.LOW_PASS, 4, 22000.0, 0.0);
    static
    {
        DEFAULT_LOW_PASS_FILTER.getCutoffEnvelopeModulator ().setDepth (1.0);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DecentSamplerCreator (final INotifier notifier)
    {
        super ("DecentSampler", "DecentSampler", notifier, new DecentSamplerCreatorUI ("DecentSampler"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final List<PresetResult> results = this.create (destinationFolder, Collections.singletonList (multisampleSource), false);

        final File resourceDestination;
        if (this.settingsConfiguration.createBundle ())
        {
            // Note: method is called for each multi-source individually!
            final File multiFile = this.createUniqueFilename (destinationFolder, multisampleSource.getName (), "dsbundle");
            this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

            this.storeBundle (multiFile, Collections.singletonList (results.get (0)));

            resourceDestination = multiFile;
        }
        else
        {
            for (final PresetResult presetResult: results)
            {
                this.notifier.log ("IDS_NOTIFY_STORING", presetResult.dsPresetFile.getAbsolutePath ());
                this.storePreset (destinationFolder, presetResult);
            }

            resourceDestination = destinationFolder;
        }

        this.copyResources (resourceDestination);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final List<PresetResult> results = this.create (destinationFolder, multisampleSources, true);

        final boolean isBundle = this.settingsConfiguration.createBundle ();
        final String extension = isBundle ? "dsbundle" : "dslibrary";
        final File multiFile = this.createUniqueFilename (destinationFolder, libraryName, extension);
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
        if (isBundle)
            this.storeBundle (multiFile, results);
        else
            this.storeLibrary (multiFile, results);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private final List<PresetResult> create (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final boolean isLibrary) throws IOException
    {
        if (multisampleSources.isEmpty ())
            return Collections.emptyList ();

        final List<String> otherOutputFiles = new ArrayList<> ();
        final List<PresetResult> results = new ArrayList<> ();
        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            final PresetResult presetResult = new PresetResult ();
            presetResult.sampleSource = multisampleSource;

            // Make sure the file name is unique among either the files in the destination folder or
            // inside of the library
            String sampleName = createSafeFilename (multisampleSource.getName ());
            presetResult.dsPresetFile = isLibrary ? this.createUniqueFilename (destinationFolder, sampleName, ".dspreset", otherOutputFiles) : this.createUniqueFilename (destinationFolder, sampleName, "dspreset");
            sampleName = FileUtils.getNameWithoutType (presetResult.dsPresetFile);
            presetResult.sampleFolder = sampleName + FOLDER_POSTFIX;

            otherOutputFiles.add (presetResult.dsPresetFile.getAbsolutePath ());

            final Optional<String> metadata = this.createPresetDocument (presetResult.sampleFolder, multisampleSource);
            if (metadata.isEmpty ())
                continue;
            presetResult.dsPreset = metadata.get ();
            results.add (presetResult);
        }

        return results;
    }


    /**
     * Create a dspreset file.
     *
     * @param destinationFolder Where to store the preset file
     * @param presetResult The preset to store
     * @throws IOException Could not store the file
     */
    private void storePreset (final File destinationFolder, final PresetResult presetResult) throws IOException
    {
        try (final FileWriter writer = new FileWriter (presetResult.dsPresetFile, StandardCharsets.UTF_8))
        {
            writer.write (presetResult.dsPreset);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, presetResult.sampleFolder);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, presetResult.sampleSource);
    }


    /**
     * Create a dsbundle file.
     *
     * @param multiFile The file of the dsbundle
     * @param presetResults The presets to store in the bundle
     * @throws IOException Could not store the file
     */
    private void storeBundle (final File multiFile, final List<PresetResult> presetResults) throws IOException
    {
        final File bundleFolder = multiFile;

        // The bundle name is a directory!
        if (!bundleFolder.mkdirs ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", multiFile.getAbsolutePath ()));

        for (final PresetResult presetResult: presetResults)
        {
            // Add bundle sub-path
            presetResult.dsPresetFile = new File (bundleFolder, presetResult.dsPresetFile.getName ());
            this.storePreset (bundleFolder, presetResult);
        }
        this.copyResources (bundleFolder);

        final String libraryName = FileUtils.getNameWithoutType (multiFile);
        Files.writeString (new File (bundleFolder, "DSLibraryInfo.xml").toPath (), LIBRARY_INFO_CONTENT.replace ("%LIBRARY_NAME%", libraryName));
    }


    /**
     * Create a dslibrary file.
     *
     * @param multiFile The file of the dslibrary
     * @param presetResults The presets to store in the library
     * @throws IOException Could not store the file
     */
    private void storeLibrary (final File multiFile, final List<PresetResult> presetResults) throws IOException
    {
        final String libraryPath = FileUtils.getNameWithoutType (multiFile);

        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            for (final PresetResult presetResult: presetResults)
            {
                AbstractCreator.zipTextFile (zos, libraryPath + FORWARD_SLASH + presetResult.dsPresetFile.getName (), presetResult.dsPreset);
                this.zipSampleFiles (zos, libraryPath + FORWARD_SLASH + presetResult.sampleFolder, presetResult.sampleSource);
            }

            this.copyResources (zos, libraryPath);
        }
    }


    /**
     * Create the text of the description file.
     *
     * @param folderName The name to use for the sample folder
     * @param multisampleSource The multi-sample
     * @return The XML structure
     * @throws IOException Could not find template
     */
    private Optional<String> createPresetDocument (final String folderName, final IMultisampleSource multisampleSource) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element multisampleElement = document.createElement (DecentSamplerTag.DECENTSAMPLER);
        document.appendChild (multisampleElement);
        multisampleElement.setAttribute (DecentSamplerTag.MIN_VERSION, "1.11");

        final ParameterLevel ampEnvParameterLevel = getAmpEnvelopeParamLevel (multisampleSource);

        // No metadata at all

        final Element groupsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.GROUPS);
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);

        // Add all groups

        final Element modulatorsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.MODULATORS);

        for (int groupIndex = 0; groupIndex < groups.size (); groupIndex++)
        {
            final IGroup group = groups.get (groupIndex);
            final Element groupElement = XMLUtils.addElement (document, groupsElement, DecentSamplerTag.GROUP);

            final String name = group.getName ();
            if (name != null && !name.isBlank ())
                groupElement.setAttribute ("name", name);

            final TriggerType triggerType = group.getTrigger ();
            if (triggerType != TriggerType.ATTACK)
                groupElement.setAttribute (DecentSamplerTag.TRIGGER, triggerType.name ().toLowerCase (Locale.ENGLISH));

            final Set<Double> ampVelDepths = new HashSet<> ();
            final List<ISampleZone> zones = group.getSampleZones ();
            for (int zoneIndex = 0; zoneIndex < zones.size (); zoneIndex++)
            {
                final ISampleZone zone = zones.get (zoneIndex);
                ampVelDepths.add (Double.valueOf (zone.getAmplitudeVelocityModulator ().getDepth ()));
                final Element sampleElement = createSample (document, folderName, groupElement, zone);

                // Note: there is no amplitude envelope modulation depth parameter!
                final IEnvelope modulatorSource = zone.getAmplitudeEnvelopeModulator ().getSource ();
                if (ampEnvParameterLevel == ParameterLevel.ZONE)
                    setEnvelope (sampleElement, modulatorSource);
                else if (ampEnvParameterLevel == ParameterLevel.GROUP && zoneIndex == 0)
                    setEnvelope (groupElement, modulatorSource);
                else if (ampEnvParameterLevel == ParameterLevel.INSTRUMENT && groupIndex == 0 && zoneIndex == 0)
                    setEnvelope (groupsElement, modulatorSource);
            }
            if (ampVelDepths.size () == 1)
                XMLUtils.setDoubleAttribute (groupElement, DecentSamplerTag.AMP_VELOCITY_TRACK, ampVelDepths.iterator ().next ().doubleValue (), 4);

            this.createFilter (document, modulatorsElement, multisampleSource, groupElement, groupIndex);
            if (!zones.isEmpty ())
                createPitchModulator (document, modulatorsElement, zones.get (0).getPitchModulator (), groupIndex);
        }

        this.makeMonophonic (document, multisampleElement, groupsElement);
        this.applyTemplate (document, multisampleElement, groupsElement, modulatorsElement);
        return this.createXMLString (document);
    }


    private void makeMonophonic (final Document document, final Element multisampleElement, final Element groupsElement)
    {
        if (!this.settingsConfiguration.makeMonophonic ())
            return;
        groupsElement.setAttribute ("tags", "monophonic");
        final Element tagsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.TAGS);
        final Element tagElement = XMLUtils.addElement (document, tagsElement, DecentSamplerTag.TAG);
        tagElement.setAttribute ("name", "monophonic");
        tagElement.setAttribute ("polyphony", "1");
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param folderName The name to use for the sample folder
     * @param groupElement The element where to add the sample information
     * @param zone Where to get the sample info from
     * @return The sample element
     */
    private static Element createSample (final Document document, final String folderName, final Element groupElement, final ISampleZone zone)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, groupElement, DecentSamplerTag.SAMPLE);

        final String filename = zone.getName () + ".wav";
        sampleElement.setAttribute (DecentSamplerTag.PATH, AbstractCreator.formatFileName (folderName, filename));

        final double gain = zone.getGain ();
        if (gain != 0)
            sampleElement.setAttribute (DecentSamplerTag.VOLUME, gain + "dB");
        sampleElement.setAttribute (DecentSamplerTag.PANNING, Integer.toString ((int) (zone.getPanning () * 100.0)));
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.START, Math.max (0, zone.getStart ()), 3);
        final int stop = zone.getStop ();
        if (stop >= 0)
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.END, stop, 3);
        final double tune = zone.getTune ();
        if (tune != 0)
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.TUNING, tune, 2);

        final TriggerType triggerType = zone.getTrigger ();
        if (triggerType != TriggerType.ATTACK)
            sampleElement.setAttribute (DecentSamplerTag.TRIGGER, triggerType.name ().toLowerCase (Locale.ENGLISH));

        // No info.isReversed ()

        if (zone.getPlayLogic () == PlayLogic.ROUND_ROBIN)
        {
            final int seqPos = zone.getSequencePosition ();
            if (seqPos >= 1)
                sampleElement.setAttribute (DecentSamplerTag.SEQ_POSITION, Integer.toString (seqPos));
        }

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        final int keyLow = limitToDefault (zone.getKeyLow (), 0);
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LO_NOTE, keyLow);
        // No fades info.getNoteCrossfadeLow ()
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.ROOT_NOTE, limitToDefault (zone.getKeyRoot (), keyLow));
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.HI_NOTE, limitToDefault (zone.getKeyHigh (), 127));
        // No fades info.getNoteCrossfadeHigh ()
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.PITCH_KEY_TRACK, zone.getKeyTracking (), 4);
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LO_VEL, limitToDefault (zone.getVelocityLow (), 1));
        // No fades info.getVelocityCrossfadeLow ()
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.HI_VEL, limitToDefault (zone.getVelocityHigh (), 127));
        // No fades info.getVelocityCrossfadeHigh ()

        /////////////////////////////////////////////////////
        // Loops

        final List<ISampleLoop> loops = zone.getLoops ();
        // Loops are enabled by default!
        if (loops.isEmpty ())
            sampleElement.setAttribute (DecentSamplerTag.LOOP_ENABLED, "false");
        else
        {

            final ISampleLoop sampleLoop = loops.get (0);
            sampleElement.setAttribute (DecentSamplerTag.LOOP_ENABLED, "true");
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_START, limitToDefault (sampleLoop.getStart (), 0), 3);
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_END, limitToDefault (sampleLoop.getEnd (), stop), 3);

            // Calculate the cross-fade in frames/samples from a percentage of the loop length
            final int crossfade = sampleLoop.getCrossfadeInSamples ();
            if (crossfade > 0)
                XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LOOP_CROSSFADE, crossfade);
        }

        return sampleElement;
    }


    private void createFilter (final Document document, final Element modulatorsElement, final IMultisampleSource multisampleSource, final Element groupElement, final int groupIndex)
    {
        final Optional<IFilter> globalFilter = multisampleSource.getGlobalFilter ();
        final IFilter filter;
        if (globalFilter.isEmpty ())
        {
            if (!this.settingsConfiguration.addFilterToGroups ())
                return;
            filter = DEFAULT_LOW_PASS_FILTER;
        }
        else
            filter = globalFilter.get ();

        // Needs to be added to all groups since envelopes seem to only work on a group level (and
        // not instrument level)...

        final Element effectsElement = XMLUtils.addElement (document, groupElement, DecentSamplerTag.EFFECTS);
        final Element filterElement = XMLUtils.addElement (document, effectsElement, DecentSamplerTag.EFFECTS_EFFECT);

        boolean isNotch = false;
        switch (filter.getType ())
        {
            default:
            case LOW_PASS:
                // 'lowpass' is 4 pole filter
                filterElement.setAttribute ("type", filter.getPoles () == 1 ? "lowpass_1pl" : "lowpass");
                break;
            case HIGH_PASS:
                filterElement.setAttribute ("type", "highpass");
                break;
            case BAND_PASS:
                filterElement.setAttribute ("type", "bandpass");
                break;
            case BAND_REJECTION:
                filterElement.setAttribute ("type", "notch");
                isNotch = true;
                break;
        }

        // The correct acceptable range is 0.001 to 5, where 0.7 is the default value. Values
        // lower than 0.7 will start to reduce the frequency at the cutoff point, and a value near 0
        // will actually stop sound altogether. From a practical perspective, it usually makes sense
        // to think of 0.7 as the minimum value, as this produces no "bump" at the cutoff point.
        filterElement.setAttribute ("resonance", formatDouble (filter.getResonance () * 4.3 + 0.7, 3));
        filterElement.setAttribute (isNotch ? "q" : "frequency", formatDouble (filter.getCutoff (), 2));

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final double envelopeDepth = cutoffModulator.getDepth ();
        if (envelopeDepth > 0)
        {
            final Element envelopeElement = XMLUtils.addElement (document, modulatorsElement, DecentSamplerTag.ENVELOPE);
            XMLUtils.setDoubleAttribute (envelopeElement, DecentSamplerTag.MOD_AMOUNT, envelopeDepth, 2);
            envelopeElement.setAttribute ("scope", "voice");

            setEnvelope (envelopeElement, cutoffModulator.getSource ());

            final Element bindingElement = XMLUtils.addElement (document, envelopeElement, DecentSamplerTag.BINDING);
            bindingElement.setAttribute ("type", "effect");
            bindingElement.setAttribute ("level", "group");
            bindingElement.setAttribute ("groupIndex", Integer.toString (groupIndex));
            bindingElement.setAttribute ("effectIndex", "0");
            bindingElement.setAttribute ("parameter", "FX_FILTER_FREQUENCY");
            bindingElement.setAttribute ("modBehavior", "add");
            bindingElement.setAttribute ("translation", "table");
            bindingElement.setAttribute ("translationTable", "0,33;0.3,150;0.4,450;0.5,1100;0.7,4100;0.9,11000;1.0001,22000");
        }
    }


    private static void createPitchModulator (final Document document, final Element modulatorsElement, final IEnvelopeModulator pitchModulator, final int groupIndex)
    {
        final double envelopeDepth = pitchModulator.getDepth ();
        // Only positive values allowed in DecentSampler
        if (envelopeDepth <= 0)
            return;

        final Element envelopeElement = XMLUtils.addElement (document, modulatorsElement, DecentSamplerTag.ENVELOPE);
        XMLUtils.setDoubleAttribute (envelopeElement, DecentSamplerTag.MOD_AMOUNT, envelopeDepth, 2);

        setEnvelope (envelopeElement, pitchModulator.getSource ());
        envelopeElement.setAttribute ("scope", "voice");

        final Element bindingElement = XMLUtils.addElement (document, envelopeElement, DecentSamplerTag.BINDING);
        bindingElement.setAttribute ("type", "amp");
        bindingElement.setAttribute ("level", "group");
        bindingElement.setAttribute ("groupIndex", Integer.toString (groupIndex));
        bindingElement.setAttribute ("parameter", "GROUP_TUNING");
        bindingElement.setAttribute ("translation", "linear");
        bindingElement.setAttribute ("translationOutputMin", "0");
        // Unit are semi-tones; maximum value is 120 semi-tones
        bindingElement.setAttribute ("translationOutputMax", Integer.toString (IEnvelope.MAX_ENVELOPE_DEPTH / 100));
        bindingElement.setAttribute ("modBehavior", "add");
    }


    /**
     * Create the static user interface from a template.
     *
     * @param document The XML document
     * @param rootElement The root XML element
     * @param groupsElement The groups element
     * @param modulatorsElement The modulatorsElement
     * @throws IOException Could not load the template
     */
    private void applyTemplate (final Document document, final Element rootElement, final Element groupsElement, Element modulatorsElement) throws IOException
    {
        // Read again the created amplitude envelope values...
        final double attackAttribute = XMLUtils.getDoubleAttribute (groupsElement, DecentSamplerTag.ENV_ATTACK, 0.0);
        final double decayAttribute = XMLUtils.getDoubleAttribute (groupsElement, DecentSamplerTag.ENV_DECAY, 0.0);
        final double sustainAttribute = XMLUtils.getDoubleAttribute (groupsElement, DecentSamplerTag.ENV_SUSTAIN, 1.0);
        final double releaseAttribute = XMLUtils.getDoubleAttribute (groupsElement, DecentSamplerTag.ENV_RELEASE, 0.1);
        final Element groupElement = XMLUtils.getChildElementByName (groupsElement, DecentSamplerTag.GROUP);
        final double ampVelTrackAttribute = groupElement != null ? XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.AMP_VELOCITY_TRACK, 0.0) : 0.0;

        // ... and apply them to the template variables
        String template = this.settingsConfiguration.getTemplateCode ("ui.xml").trim ();
        template = template.replace ("%ENV_ATTACK_VALUE%", String.format (Locale.US, "%.3f", Double.valueOf (attackAttribute)));
        template = template.replace ("%ENV_DECAY_VALUE%", String.format (Locale.US, "%.3f", Double.valueOf (decayAttribute)));
        template = template.replace ("%ENV_SUSTAIN_VALUE%", String.format (Locale.US, "%.3f", Double.valueOf (sustainAttribute)));
        template = template.replace ("%ENV_RELEASE_VALUE%", String.format (Locale.US, "%.3f", Double.valueOf (releaseAttribute)));
        template = template.replace ("%ENV_VELOCITY_SENSITIVITY%", String.format (Locale.US, "%.4f", Double.valueOf (ampVelTrackAttribute)));

        // Insert the additional code from the (user) template into the created document
        final org.w3c.dom.Node xmlSnippet = readXMLSnippet (document, template);
        addChildByName (rootElement, xmlSnippet, "effects");
        addChildByName (rootElement, xmlSnippet, "midi");
        addChildByName (rootElement, xmlSnippet, "ui");
        final Element childModulatorsElement = XMLUtils.getChildElementByName (xmlSnippet, "modulators");
        if (childModulatorsElement != null)
        {
            final NodeList modulatorsChildrenList = childModulatorsElement.getChildNodes ();
            int length = modulatorsChildrenList.getLength ();
            for (int i = length - 1; i >= 0; i--)
            {
                org.w3c.dom.Node item = modulatorsChildrenList.item (i);
                if (item != null)
                    modulatorsElement.appendChild (item);
            }
        }
    }


    private static void addChildByName (final Element rootElement, final org.w3c.dom.Node xmlSnippet, final String childName)
    {
        final Element childElement = XMLUtils.getChildElementByName (xmlSnippet, childName);
        if (childElement != null)
            rootElement.appendChild (childElement);
    }


    /**
     * Set the amplitude envelope parameters to the given element.
     *
     * @param element Where to add the parameters
     * @param envelope The envelope
     */
    private static void setEnvelope (final Element element, final IEnvelope envelope)
    {
        setEnvelopeTimeAttribute (element, DecentSamplerTag.ENV_ATTACK, envelope.getAttackTime ());
        setEnvelopeTimeAttribute (element, DecentSamplerTag.ENV_DECAY, Math.max (0, envelope.getHoldTime ()) + Math.max (0, envelope.getDecayTime ()));
        setEnvelopeAttribute (element, DecentSamplerTag.ENV_SUSTAIN, envelope.getSustainLevel ());
        setEnvelopeTimeAttribute (element, DecentSamplerTag.ENV_RELEASE, envelope.getReleaseTime ());

        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_ATTACK_CURVE, envelope.getAttackSlope () * 100.0);
        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_DECAY_CURVE, envelope.getDecaySlope () * 100.0);
        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_RELEASE_CURVE, envelope.getReleaseSlope () * 100.0);
    }


    private static void setEnvelopeTimeAttribute (final Element element, final String attribute, final double value)
    {
        if (value >= 0)
            setEnvelopeAttribute (element, attribute, value);
    }


    private static void setEnvelopeAttribute (final Element element, final String attribute, final double value)
    {
        if (value >= 0)
            XMLUtils.setDoubleAttribute (element, attribute, value, 3);
    }


    private static void setEnvelopeSlopeAttribute (final Element element, final String attribute, final double value)
    {
        if (value != 0)
            XMLUtils.setDoubleAttribute (element, attribute, value, 3);
    }


    private static org.w3c.dom.Node readXMLSnippet (final Document document, final String template) throws IOException
    {
        final Document templateDocument;
        try
        {
            // Add a fake root node to make it readable
            final String xmlCode = "<root>" + template + "</root>";
            templateDocument = XMLUtils.parseDocument (new InputSource (new StringReader (xmlCode)));
        }
        catch (final SAXException ex)
        {
            throw new IOException (ex);
        }

        final Element templateDocumentElement = templateDocument.getDocumentElement ();
        templateDocumentElement.normalize ();
        trimWhitespace (templateDocumentElement);
        return document.importNode (templateDocumentElement, true);
    }


    private static void trimWhitespace (final org.w3c.dom.Node node)
    {
        if (node.getNodeType () == org.w3c.dom.Node.TEXT_NODE)
            node.setTextContent (node.getTextContent ().trim ());
        for (org.w3c.dom.Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            trimWhitespace (child);
    }


    private void copyResources (final File resourceDestination) throws IOException
    {
        final File templateFolderPath = this.settingsConfiguration.getTemplateFolderPath ();
        if (templateFolderPath.exists ())
            copyFolderWithIgnoreList (templateFolderPath, resourceDestination, IGNORE_FILES);
    }


    private void copyResources (final ZipOutputStream zos, final String basePath) throws IOException
    {
        final File templateFolderPath = this.settingsConfiguration.getTemplateFolderPath ();
        if (templateFolderPath.exists ())
            zipFolderWithIgnoreList (templateFolderPath, basePath, zos, IGNORE_FILES);
    }


    private static void copyFolderWithIgnoreList (final File sourceFolder, final File destinationFolder, final List<String> ignoreList) throws IOException
    {
        if (!destinationFolder.exists () && !destinationFolder.mkdirs ())
            throw new IOException (Functions.getMessage ("IDS_DS_COULD_NOT_CREATE_TEMPLATE_DIR"));

        final File [] files = sourceFolder.listFiles ();
        if (files == null)
            return;
        for (final File file: files)
        {
            if (ignoreList.contains (file.getName ()))
                continue;
            final File destFile = new File (destinationFolder, file.getName ());
            if (file.isDirectory ())
                copyFolderWithIgnoreList (file, destFile, ignoreList);
            else
                Files.copy (file.toPath (), destFile.toPath (), StandardCopyOption.REPLACE_EXISTING);
        }
    }


    private static void zipFolderWithIgnoreList (final File folder, final String baseName, final ZipOutputStream zos, final List<String> ignoreList) throws IOException
    {
        final File [] files = folder.listFiles ();
        if (files == null)
            return;
        for (final File file: files)
        {
            if (ignoreList.contains (file.getName ()))
                continue;
            final String entryName = baseName + FORWARD_SLASH + file.getName ();
            if (file.isDirectory ())
                zipFolderWithIgnoreList (file, entryName, zos, ignoreList);
            else
            {
                zos.putNextEntry (new ZipEntry (entryName));
                Files.copy (file.toPath (), zos);
                zos.closeEntry ();
            }
        }
    }
}