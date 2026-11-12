// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
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
    private static final String       TAG_TRANSLATION_OUTPUT_MIN = "translationOutputMin";
    private static final String       TAG_TRANSLATION_OUTPUT_MAX = "translationOutputMax";
    private static final String       TAG_LINEAR                 = "linear";
    private static final String       TAG_GROUP                  = "group";
    private static final String       TAG_GROUP_INDEX            = "groupIndex";
    private static final String       TAG_LEVEL                  = "level";
    private static final String       TAG_MOD_BEHAVIOR           = "modBehavior";
    private static final String       TAG_PARAMETER              = "parameter";
    private static final String       TAG_TRANSLATION            = "translation";
    // Maps the range of [0..1] to the cutoff frequency, the same table is used by the template
    private static final String       FILTER_CUTOFF_TABLE        = "0,33;0.3,150;0.4,450;0.5,1100;0.7,4100;0.9,11000;1.0001,22000";

    private static final String       IDS_NOTIFY_STORING         = "IDS_NOTIFY_STORING";
    private static final String       DECENT_SAMPLER             = "DecentSampler";

    private static final List<String> IGNORE_FILES               = Collections.singletonList ("ui.xml");
    private static final String       LIBRARY_INFO_CONTENT       = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<DecentSamplerLibraryInfo name=\"%LIBRARY_NAME%\"/>";


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
        super (DECENT_SAMPLER, DECENT_SAMPLER, notifier, new DecentSamplerCreatorUI (DECENT_SAMPLER));
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
            final File multiFile = this.createUniqueFilename (destinationFolder, SafeFileNames.create (multisampleSource.getName ()), "dsbundle");
            this.notifier.log (IDS_NOTIFY_STORING, multiFile.getAbsolutePath ());

            this.storeBundle (multiFile, Collections.singletonList (results.get (0)));

            resourceDestination = multiFile;
        }
        else
        {
            for (final PresetResult presetResult: results)
            {
                this.notifier.log (IDS_NOTIFY_STORING, presetResult.dsPresetFile.getAbsolutePath ());
                this.storePreset (destinationFolder, presetResult);
            }

            resourceDestination = destinationFolder;
        }

        this.copyResources (resourceDestination);

        this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final List<PresetResult> results = this.create (destinationFolder, multisampleSources, true);

        final boolean isBundle = this.settingsConfiguration.createBundle ();
        final String extension = isBundle ? "dsbundle" : "dslibrary";
        final File multiFile = this.createUniqueFilename (destinationFolder, libraryName, extension);
        this.notifier.log (IDS_NOTIFY_STORING, multiFile.getAbsolutePath ());
        if (isBundle)
            this.storeBundle (multiFile, results);
        else
            this.storeLibrary (multiFile, results);

        this.progress.notifyDone ();
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
            String sampleName = SafeFileNames.create (multisampleSource.getName ());
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
        final int polyphony = this.getPolyphony (multisampleSource);
        final String polyphonyTag = polyphony > 0 ? getPolyphonyTag (polyphony) : null;

        // No metadata at all

        final Element groupsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.GROUPS);
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);

        // Add all groups

        final Element modulatorsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.MODULATORS);
        boolean hasModWheelVibrato = false;

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
            boolean isSequence = false;
            boolean isRandom = false;
            int seqLength = 0;
            for (int zoneIndex = 0; zoneIndex < zones.size (); zoneIndex++)
            {
                final ISampleZone zone = zones.get (zoneIndex);

                final PlayLogic playLogic = zone.getPlayLogic ();
                isSequence = isSequence || playLogic != PlayLogic.ALWAYS;
                isRandom = isRandom || playLogic == PlayLogic.RANDOM;
                // The sequence length is the number of round-robin positions and not the
                // number of zones
                if (isSequence && zone.getSequencePosition () >= 1)
                    seqLength = Math.max (seqLength, zone.getSequencePosition ());

                ampVelDepths.add (Double.valueOf (zone.getAmplitudeVelocityModulator ().getDepth ()));
                final Element sampleElement = createSample (document, folderName, groupElement, zone, polyphonyTag);

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

            if (isSequence)
            {
                groupElement.setAttribute (DecentSamplerTag.SEQ_MODE, isRandom ? DecentSamplerTag.SEQ_RANDOM : DecentSamplerTag.SEQ_ROUND_ROBIN);
                XMLUtils.setIntegerAttribute (groupElement, DecentSamplerTag.SEQ_LENGTH, seqLength);
            }

            this.createFilter (document, modulatorsElement, multisampleSource, groupElement, groupIndex);
            if (!zones.isEmpty ())
            {
                createPitchModulator (document, modulatorsElement, zones.get (0).getPitchEnvelopeModulator (), groupIndex);
                hasModWheelVibrato |= createPitchLfoModulator (document, modulatorsElement, zones.get (0).getPitchLfoModulator (), groupIndex);
                createAmplitudeLfoModulator (document, modulatorsElement, zones.get (0).getAmplitudeLfoModulator (), groupIndex);
            }
        }

        this.applyPolyphony (document, multisampleElement, groupsElement, multisampleSource);
        this.applyTemplate (document, multisampleElement, groupsElement, modulatorsElement, hasModWheelVibrato);
        return this.createXMLString (document);
    }


    /**
     * Limit the number of voices of the instrument. DecentSampler can only limit the polyphony of a
     * tag, therefore a tag is created and assigned to all groups. The option to make the instrument
     * monophonic always enforces one voice, otherwise the polyphony of the instrument is applied,
     * if it is set.
     *
     * @param document The XML document
     * @param multisampleElement The top level element
     * @param groupsElement The groups element
     * @param multisampleSource The multi-sample source
     */
    private void applyPolyphony (final Document document, final Element multisampleElement, final Element groupsElement, final IMultisampleSource multisampleSource)
    {
        final int polyphony = this.getPolyphony (multisampleSource);
        if (polyphony <= 0)
            return;

        final String tagName = getPolyphonyTag (polyphony);
        groupsElement.setAttribute (DecentSamplerTag.TAGS_ATTRIBUTE, tagName);
        final Element tagsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.TAGS);
        final Element tagElement = XMLUtils.addElement (document, tagsElement, DecentSamplerTag.TAG);
        tagElement.setAttribute (DecentSamplerTag.TAG_NAME, tagName);
        tagElement.setAttribute (DecentSamplerTag.TAG_POLYPHONY, Integer.toString (polyphony));
    }


    /**
     * Get the number of voices of the instrument. The option to make the instrument monophonic
     * always enforces one voice, otherwise the polyphony of the instrument is applied, if it is
     * set.
     *
     * @param multisampleSource The multi-sample source
     * @return The number of voices, 0 or less if it is not limited
     */
    private int getPolyphony (final IMultisampleSource multisampleSource)
    {
        if (this.settingsConfiguration.makeMonophonic () || multisampleSource.isMonophonicLegato ())
            return 1;
        return multisampleSource.getPolyphony ();
    }


    /**
     * Get the name of the tag which limits the number of voices.
     *
     * @param polyphony The number of voices
     * @return The name of the tag
     */
    private static String getPolyphonyTag (final int polyphony)
    {
        return polyphony == 1 ? DecentSamplerTag.TAG_MONOPHONIC : DecentSamplerTag.TAG_POLYPHONY;
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param folderName The name to use for the sample folder
     * @param groupElement The element where to add the sample information
     * @param zone Where to get the sample info from
     * @param polyphonyTag The tag which limits the number of voices, null if there is none
     * @return The sample element
     */
    private static Element createSample (final Document document, final String folderName, final Element groupElement, final ISampleZone zone, final String polyphonyTag)
    {
        // -----------------------------------------------------------
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, groupElement, DecentSamplerTag.SAMPLE);

        final String filename = zone.getName () + ".wav";
        sampleElement.setAttribute (DecentSamplerTag.PATH, AbstractCreator.formatFileName (folderName, filename));

        final double gain = zone.getGain ();
        if (gain != 0)
            sampleElement.setAttribute (DecentSamplerTag.VOLUME, gain + "dB");
        sampleElement.setAttribute (DecentSamplerTag.PANNING, Integer.toString ((int) Math.round (zone.getPanning () * 100.0)));
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.START, Math.max (0, zone.getStart ()), 3);
        final int stop = zone.getStop ();
        if (stop >= 0)
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.END, stop, 3);
        final double tune = zone.getTuning ();
        if (tune != 0)
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.TUNING, tune, 2);

        final TriggerType triggerType = zone.getTrigger ();
        if (triggerType != TriggerType.ATTACK)
            sampleElement.setAttribute (DecentSamplerTag.TRIGGER, triggerType.name ().toLowerCase (Locale.ENGLISH));

        // No info.isReversed ()

        // A random selection uses the sequence positions as well, therefore they are written for
        // round-robin and random alike
        if (zone.getPlayLogic () != PlayLogic.ALWAYS)
        {
            final int seqPos = zone.getSequencePosition ();
            if (seqPos >= 1)
                sampleElement.setAttribute (DecentSamplerTag.SEQ_POSITION, Integer.toString (seqPos));
        }

        // -----------------------------------------------------------
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

        // -----------------------------------------------------------
        // Loops

        final List<ISampleLoop> loops = zone.getLoops ();
        // Loops are enabled by default!
        if (loops.isEmpty ())
        {
            sampleElement.setAttribute (DecentSamplerTag.LOOP_ENABLED, "false");

            // Without its amplitude envelope a sample plays to its end like a one-shot. A looped
            // sample would never stop, therefore only a sample without a loop is written as one
            if (zone.isOneShot ())
                sampleElement.setAttribute (DecentSamplerTag.AMP_ENV_ENABLED, "false");
        }
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

        // -----------------------------------------------------------
        // Exclusive group

        // A sample is stopped when a sample with one of its silencing tags is triggered, therefore
        // all samples of an exclusive group carry the tag by which they are silenced. The sample
        // keeps the tag which limits the number of voices
        final int exclusiveGroup = zone.getExclusiveGroup ();
        if (exclusiveGroup > 0)
        {
            final String exclusiveGroupTag = DecentSamplerTag.TAG_EXCLUSIVE_GROUP + exclusiveGroup;
            sampleElement.setAttribute (DecentSamplerTag.TAGS_ATTRIBUTE, polyphonyTag == null ? exclusiveGroupTag : polyphonyTag + "," + exclusiveGroupTag);
            sampleElement.setAttribute (DecentSamplerTag.SILENCED_BY_TAGS, exclusiveGroupTag);
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
            // The controls of the template address the envelope by its tag
            envelopeElement.setAttribute (DecentSamplerTag.TAGS_ATTRIBUTE, DecentSamplerTag.FILTER_ENVELOPE);

            setEnvelope (envelopeElement, cutoffModulator.getSource ());

            final Element bindingElement = XMLUtils.addElement (document, envelopeElement, DecentSamplerTag.BINDING);
            bindingElement.setAttribute ("type", "effect");
            bindingElement.setAttribute (TAG_LEVEL, TAG_GROUP);
            bindingElement.setAttribute (TAG_GROUP_INDEX, Integer.toString (groupIndex));
            bindingElement.setAttribute ("effectIndex", "0");
            bindingElement.setAttribute (TAG_PARAMETER, "FX_FILTER_FREQUENCY");
            bindingElement.setAttribute (TAG_MOD_BEHAVIOR, "add");
            bindingElement.setAttribute (TAG_TRANSLATION, "table");
            bindingElement.setAttribute ("translationTable", FILTER_CUTOFF_TABLE);
        }

        if (!isNotch)
            createCutoffLfoModulator (document, modulatorsElement, filter, groupIndex);
    }


    /**
     * Create an oscillator bound to the cutoff frequency of the filter of a group. If the
     * modulation wheel controls the depth, a MIDI binding is added as well. DecentSampler adds the
     * oscillator linearly in Hertz while the depth is given in cents, therefore the symmetric swing
     * around the cutoff is the average of the swings upwards and downwards.
     *
     * @param document The XML document
     * @param modulatorsElement The modulators element
     * @param filter The filter
     * @param groupIndex The index of the group
     */
    private static void createCutoffLfoModulator (final Document document, final Element modulatorsElement, final IFilter filter, final int groupIndex)
    {
        final ILfoModulator cutoffLfoModulator = filter.getCutoffLfoModulator ();
        if (!cutoffLfoModulator.isActive ())
            return;

        final double cutoff = filter.getCutoff ();
        final double lfoDepth = cutoffLfoModulator.getDepth ();
        final double modWheelDepth = cutoffLfoModulator.getDepthModWheelModulator ().getDepth ();
        final double swing = toCutoffSwing (cutoff, lfoDepth);
        final double modWheelSwing = toCutoffSwing (cutoff, lfoDepth + modWheelDepth);
        // The range of the binding is the largest swing which is required
        final double range = Math.max (swing, modWheelSwing);
        if (range <= 0)
            return;

        final double modAmount = swing / range;
        final Element lfoElement = createLfoElement (document, modulatorsElement, cutoffLfoModulator.getSource (), modAmount);

        final Element bindingElement = XMLUtils.addElement (document, lfoElement, DecentSamplerTag.BINDING);
        bindingElement.setAttribute ("type", "effect");
        bindingElement.setAttribute (TAG_LEVEL, TAG_GROUP);
        bindingElement.setAttribute (TAG_GROUP_INDEX, Integer.toString (groupIndex));
        bindingElement.setAttribute ("effectIndex", "0");
        bindingElement.setAttribute (TAG_PARAMETER, "FX_FILTER_FREQUENCY");
        bindingElement.setAttribute (TAG_TRANSLATION, TAG_LINEAR);
        // The unit is Hertz, the oscillator is bipolar
        XMLUtils.setDoubleAttribute (bindingElement, TAG_TRANSLATION_OUTPUT_MIN, -range, 2);
        XMLUtils.setDoubleAttribute (bindingElement, TAG_TRANSLATION_OUTPUT_MAX, range, 2);
        bindingElement.setAttribute (TAG_MOD_BEHAVIOR, "add");

        if (modWheelDepth != 0)
            addModWheelBinding (document, modulatorsElement, lfoElement, modAmount, modWheelSwing / range);
    }


    /**
     * Convert the depth of a cutoff oscillator to a symmetric swing around the cutoff frequency.
     *
     * @param cutoff The cutoff frequency in Hertz
     * @param depth The depth in the range of [-1..1], relative to MAX_ENVELOPE_DEPTH cents
     * @return The swing in Hertz
     */
    private static double toCutoffSwing (final double cutoff, final double depth)
    {
        final double octaves = Math.min (Math.abs (depth), 1.0) * IEnvelope.MAX_ENVELOPE_DEPTH / 1200.0;
        return cutoff * (Math.pow (2, octaves) - Math.pow (2, -octaves)) / 2.0;
    }


    /**
     * Create an oscillator bound to the tuning of a group (vibrato). If the modulation wheel
     * controls the depth, a MIDI binding is added as well.
     *
     * @param document The XML document
     * @param modulatorsElement The modulators element
     * @param pitchLfoModulator The pitch oscillator modulator
     * @param groupIndex The index of the group
     * @return True if the modulation wheel controls the depth
     */
    private static boolean createPitchLfoModulator (final Document document, final Element modulatorsElement, final ILfoModulator pitchLfoModulator, final int groupIndex)
    {
        if (!pitchLfoModulator.isActive ())
            return false;

        // The modulation depth is applied through the binding range below, like the pitch envelope.
        // A vibrato depth is small in relation to the full range, so more digits are needed here.
        final double lfoDepth = pitchLfoModulator.getDepth ();
        final double modAmount = Math.min (Math.abs (lfoDepth), 1.0);
        final Element lfoElement = createLfoElement (document, modulatorsElement, pitchLfoModulator.getSource (), modAmount);

        final Element bindingElement = XMLUtils.addElement (document, lfoElement, DecentSamplerTag.BINDING);
        bindingElement.setAttribute ("type", "amp");
        bindingElement.setAttribute (TAG_LEVEL, TAG_GROUP);
        bindingElement.setAttribute (TAG_GROUP_INDEX, Integer.toString (groupIndex));
        bindingElement.setAttribute (TAG_PARAMETER, "GROUP_TUNING");
        bindingElement.setAttribute (TAG_TRANSLATION, TAG_LINEAR);
        // Unit are semi-tones; the oscillator is bipolar, so the full range is applied
        // symmetrically
        bindingElement.setAttribute (TAG_TRANSLATION_OUTPUT_MIN, Integer.toString (-IEnvelope.MAX_ENVELOPE_DEPTH / 100));
        bindingElement.setAttribute (TAG_TRANSLATION_OUTPUT_MAX, Integer.toString (IEnvelope.MAX_ENVELOPE_DEPTH / 100));
        bindingElement.setAttribute (TAG_MOD_BEHAVIOR, "add");

        final double modWheelDepth = pitchLfoModulator.getDepthModWheelModulator ().getDepth ();
        if (modWheelDepth == 0)
            return false;
        addModWheelBinding (document, modulatorsElement, lfoElement, modAmount, Math.min (Math.abs (lfoDepth + modWheelDepth), 1.0));
        return true;
    }


    /**
     * Create an oscillator bound to the volume of a group (tremolo). If the modulation wheel
     * controls the depth, a MIDI binding is added as well.
     *
     * @param document The XML document
     * @param modulatorsElement The modulators element
     * @param amplitudeLfoModulator The amplitude oscillator modulator
     * @param groupIndex The index of the group
     */
    private static void createAmplitudeLfoModulator (final Document document, final Element modulatorsElement, final ILfoModulator amplitudeLfoModulator, final int groupIndex)
    {
        if (!amplitudeLfoModulator.isActive ())
            return;

        final double lfoDepth = amplitudeLfoModulator.getDepth ();
        final double modAmount = toVolumeModAmount (lfoDepth);
        final Element lfoElement = createLfoElement (document, modulatorsElement, amplitudeLfoModulator.getSource (), modAmount);

        final Element bindingElement = XMLUtils.addElement (document, lfoElement, DecentSamplerTag.BINDING);
        bindingElement.setAttribute ("type", "amp");
        bindingElement.setAttribute (TAG_LEVEL, TAG_GROUP);
        bindingElement.setAttribute (TAG_GROUP_INDEX, Integer.toString (groupIndex));
        bindingElement.setAttribute (TAG_PARAMETER, "AMP_VOLUME");
        bindingElement.setAttribute (TAG_TRANSLATION, TAG_LINEAR);
        // The oscillator is bipolar, the swing is applied symmetrically around the group volume
        bindingElement.setAttribute (TAG_TRANSLATION_OUTPUT_MIN, "-1");
        bindingElement.setAttribute (TAG_TRANSLATION_OUTPUT_MAX, "1");
        bindingElement.setAttribute (TAG_MOD_BEHAVIOR, "add");

        final double modWheelDepth = amplitudeLfoModulator.getDepthModWheelModulator ().getDepth ();
        if (modWheelDepth != 0)
            addModWheelBinding (document, modulatorsElement, lfoElement, modAmount, toVolumeModAmount (lfoDepth + modWheelDepth));
    }


    /**
     * Convert the depth of an amplitude oscillator to the modulation amount. The volume parameter
     * is linear with 1 being full volume. The bipolar oscillator swings by the modulation amount
     * around it, therefore a depth of d dB requires a swing of 1-10^(-d/20) for its lowest point to
     * be d dB below full volume.
     *
     * @param depth The depth in the range of [-1..1]
     * @return The modulation amount in the range of [0..1]
     */
    private static double toVolumeModAmount (final double depth)
    {
        final double depthDecibels = Math.min (Math.abs (depth), 1.0) * ILfoModulator.MAX_VOLUME_DEPTH;
        return 1.0 - Math.pow (10.0, -depthDecibels / 20.0);
    }


    /**
     * Create an oscillator element with its shape, rate, delay and modulation amount.
     *
     * @param document The XML document
     * @param modulatorsElement The modulators element to which to add the oscillator
     * @param lfo The oscillator
     * @param modAmount The modulation amount in the range of [0..1]
     * @return The created element
     */
    private static Element createLfoElement (final Document document, final Element modulatorsElement, final ILfo lfo, final double modAmount)
    {
        final Element lfoElement = XMLUtils.addElement (document, modulatorsElement, DecentSamplerTag.LFO);
        lfoElement.setAttribute (DecentSamplerTag.LFO_SHAPE, toLfoShape (lfo.getWaveform ()));
        XMLUtils.setDoubleAttribute (lfoElement, DecentSamplerTag.MOD_AMOUNT, modAmount, 6);
        final double rate = lfo.getRate ();
        if (rate > 0)
        {
            lfoElement.setAttribute (DecentSamplerTag.LFO_FREQUENCY_FORMAT, "hz");
            XMLUtils.setDoubleAttribute (lfoElement, DecentSamplerTag.LFO_FREQUENCY, rate, 3);
        }
        final double delay = lfo.getDelay ();
        if (delay > 0)
            XMLUtils.setDoubleAttribute (lfoElement, DecentSamplerTag.LFO_DELAY_TIME, delay, 3);
        return lfoElement;
    }


    /**
     * Add a binding of the modulation wheel (MIDI CC 1) which sets the modulation amount of a
     * modulator. The modulators of the template are added after the created ones, therefore the
     * index of the modulator does not change.
     *
     * @param document The XML document
     * @param modulatorsElement The modulators element which contains the modulator
     * @param modulatorElement The modulator element
     * @param minAmount The modulation amount with the modulation wheel at its minimum
     * @param maxAmount The modulation amount with the modulation wheel at its maximum
     */
    private static void addModWheelBinding (final Document document, final Element modulatorsElement, final Element modulatorElement, final double minAmount, final double maxAmount)
    {
        final int modulatorIndex = XMLUtils.getChildElements (modulatorsElement).indexOf (modulatorElement);
        final Element rootElement = (Element) modulatorsElement.getParentNode ();
        final Element midiElement = getOrAddChild (document, rootElement, DecentSamplerTag.MIDI);
        Element ccElement = null;
        for (final Element element: XMLUtils.getChildElementsByName (midiElement, DecentSamplerTag.MIDI_CC, false))
            if (Integer.toString (DecentSamplerTag.MOD_WHEEL_CC).equals (element.getAttribute (DecentSamplerTag.MIDI_CC_NUMBER)))
                ccElement = element;
        if (ccElement == null)
        {
            ccElement = XMLUtils.addElement (document, midiElement, DecentSamplerTag.MIDI_CC);
            XMLUtils.setIntegerAttribute (ccElement, DecentSamplerTag.MIDI_CC_NUMBER, DecentSamplerTag.MOD_WHEEL_CC);
        }

        final Element bindingElement = XMLUtils.addElement (document, ccElement, DecentSamplerTag.BINDING);
        bindingElement.setAttribute (DecentSamplerTag.BINDING_TYPE, DecentSamplerTag.TYPE_MODULATOR);
        bindingElement.setAttribute (DecentSamplerTag.BINDING_LEVEL, "instrument");
        XMLUtils.setIntegerAttribute (bindingElement, DecentSamplerTag.MODULATOR_INDEX, modulatorIndex);
        bindingElement.setAttribute (DecentSamplerTag.BINDING_PARAMETER, DecentSamplerTag.PARAMETER_MOD_AMOUNT);
        bindingElement.setAttribute (DecentSamplerTag.TRANSLATION, TAG_LINEAR);
        XMLUtils.setDoubleAttribute (bindingElement, DecentSamplerTag.TRANSLATION_OUT_MIN, minAmount, 6);
        XMLUtils.setDoubleAttribute (bindingElement, DecentSamplerTag.TRANSLATION_OUT_MAX, maxAmount, 6);
    }


    private static Element getOrAddChild (final Document document, final Element parentElement, final String name)
    {
        final Element childElement = XMLUtils.getChildElementByName (parentElement, name);
        return childElement == null ? XMLUtils.addElement (document, parentElement, name) : childElement;
    }


    private static String toLfoShape (final LfoWaveform waveform)
    {
        // DecentSampler only knows sine, square and saw. The triangle and random waveforms are
        // mapped to the closest available shape.
        return switch (waveform)
        {
            case SQUARE -> "square";
            case SAWTOOTH_UP, SAWTOOTH_DOWN -> "saw";
            default -> "sine";
        };
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
        bindingElement.setAttribute (TAG_LEVEL, TAG_GROUP);
        bindingElement.setAttribute (TAG_GROUP_INDEX, Integer.toString (groupIndex));
        bindingElement.setAttribute (TAG_PARAMETER, "GROUP_TUNING");
        bindingElement.setAttribute (TAG_TRANSLATION, TAG_LINEAR);
        bindingElement.setAttribute (TAG_TRANSLATION_OUTPUT_MIN, "0");
        // Unit are semi-tones; maximum value is 120 semi-tones
        bindingElement.setAttribute (TAG_TRANSLATION_OUTPUT_MAX, Integer.toString (IEnvelope.MAX_ENVELOPE_DEPTH / 100));
        bindingElement.setAttribute (TAG_MOD_BEHAVIOR, "add");
    }


    /**
     * Create the static user interface from a template.
     *
     * @param document The XML document
     * @param rootElement The root XML element
     * @param groupsElement The groups element
     * @param modulatorsElement The modulatorsElement
     * @param hasModWheelVibrato True if the modulation wheel controls a created vibrato, in this
     *            case the vibrato of the template (tagged with 'modWheelVibrato') is removed
     * @throws IOException Could not load the template
     */
    private void applyTemplate (final Document document, final Element rootElement, final Element groupsElement, final Element modulatorsElement, final boolean hasModWheelVibrato) throws IOException
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

        // Read again the created filter values of the first group and its envelope...
        double cutoff = IFilter.MAX_FREQUENCY;
        double resonance = 0.7;
        final Element firstGroupElement = XMLUtils.getChildElementByName (groupsElement, DecentSamplerTag.GROUP);
        final Element effectsElement = firstGroupElement == null ? null : XMLUtils.getChildElementByName (firstGroupElement, DecentSamplerTag.EFFECTS);
        final Element filterElement = effectsElement == null ? null : XMLUtils.getChildElementByName (effectsElement, DecentSamplerTag.EFFECTS_EFFECT);
        if (filterElement != null && filterElement.hasAttribute (DecentSamplerTag.EFFECT_FREQUENCY))
        {
            cutoff = XMLUtils.getDoubleAttribute (filterElement, DecentSamplerTag.EFFECT_FREQUENCY, cutoff);
            resonance = XMLUtils.getDoubleAttribute (filterElement, DecentSamplerTag.EFFECT_RESONANCE, resonance);
        }
        Element filterEnvelopeElement = null;
        for (final Element envelopeElement: XMLUtils.getChildElementsByName (modulatorsElement, DecentSamplerTag.ENVELOPE, false))
            if (DecentSamplerTag.FILTER_ENVELOPE.equals (envelopeElement.getAttribute (DecentSamplerTag.TAGS_ATTRIBUTE)))
                filterEnvelopeElement = envelopeElement;

        // ... and apply them to the template variables, otherwise the controls would overwrite them
        template = template.replace ("%FILTER_CUTOFF_VALUE%", String.format (Locale.US, "%.6f", Double.valueOf (toFilterCutoffControlValue (cutoff))));
        template = template.replace ("%FILTER_RESONANCE_VALUE%", String.format (Locale.US, "%.3f", Double.valueOf (resonance)));
        template = template.replace ("%FILTER_ENV_ATTACK_VALUE%", getTemplateValue (filterEnvelopeElement, DecentSamplerTag.ENV_ATTACK, 0.0));
        template = template.replace ("%FILTER_ENV_DECAY_VALUE%", getTemplateValue (filterEnvelopeElement, DecentSamplerTag.ENV_DECAY, 0.05));
        template = template.replace ("%FILTER_ENV_SUSTAIN_VALUE%", getTemplateValue (filterEnvelopeElement, DecentSamplerTag.ENV_SUSTAIN, 1.0));
        template = template.replace ("%FILTER_ENV_RELEASE_VALUE%", getTemplateValue (filterEnvelopeElement, DecentSamplerTag.ENV_RELEASE, 0.05));
        template = template.replace ("%FILTER_ENV_AMOUNT_VALUE%", getTemplateValue (filterEnvelopeElement, DecentSamplerTag.MOD_AMOUNT, 0.0));

        // Insert the additional code from the (user) template into the created document
        final org.w3c.dom.Node xmlSnippet = readXMLSnippet (document, template);
        if (hasModWheelVibrato)
            removeModWheelVibrato (xmlSnippet);
        addChildByName (rootElement, xmlSnippet, "effects");
        mergeMidiBindings (rootElement, xmlSnippet);
        addChildByName (rootElement, xmlSnippet, "ui");
        final Element childModulatorsElement = XMLUtils.getChildElementByName (xmlSnippet, "modulators");
        if (childModulatorsElement != null)
        {
            final NodeList modulatorsChildrenList = childModulatorsElement.getChildNodes ();
            final int length = modulatorsChildrenList.getLength ();
            for (int i = length - 1; i >= 0; i--)
            {
                final org.w3c.dom.Node item = modulatorsChildrenList.item (i);
                if (item != null)
                    modulatorsElement.appendChild (item);
            }
        }
    }


    /**
     * Remove the vibrato oscillator of the template which is controlled by the modulation wheel and
     * its MIDI binding. Both are identified by the tag 'modWheelVibrato'.
     *
     * @param xmlSnippet The template
     */
    private static void removeModWheelVibrato (final org.w3c.dom.Node xmlSnippet)
    {
        final Element templateModulatorsElement = XMLUtils.getChildElementByName (xmlSnippet, DecentSamplerTag.MODULATORS);
        if (templateModulatorsElement != null)
            for (final Element modulatorElement: XMLUtils.getChildElements (templateModulatorsElement))
                if (hasModWheelVibratoTag (modulatorElement.getAttribute (DecentSamplerTag.TAGS_ATTRIBUTE)))
                    templateModulatorsElement.removeChild (modulatorElement);

        final Element templateMidiElement = XMLUtils.getChildElementByName (xmlSnippet, DecentSamplerTag.MIDI);
        if (templateMidiElement == null)
            return;
        for (final Element ccElement: XMLUtils.getChildElementsByName (templateMidiElement, DecentSamplerTag.MIDI_CC, false))
        {
            for (final Element bindingElement: XMLUtils.getChildElementsByName (ccElement, DecentSamplerTag.BINDING, false))
                if (hasModWheelVibratoTag (bindingElement.getAttribute (DecentSamplerTag.MODULATOR_TAGS)))
                    ccElement.removeChild (bindingElement);
            if (XMLUtils.getChildElements (ccElement).isEmpty ())
                templateMidiElement.removeChild (ccElement);
        }
    }


    /**
     * Add the MIDI bindings of the template to the created ones (of the modulation wheel). The
     * bindings of a controller which is already present are added to its element.
     *
     * @param rootElement The root XML element
     * @param xmlSnippet The template
     */
    private static void mergeMidiBindings (final Element rootElement, final org.w3c.dom.Node xmlSnippet)
    {
        final Element templateMidiElement = XMLUtils.getChildElementByName (xmlSnippet, DecentSamplerTag.MIDI);
        final Element midiElement = XMLUtils.getChildElementByName (rootElement, DecentSamplerTag.MIDI);
        if (templateMidiElement == null || midiElement == null)
        {
            addChildByName (rootElement, xmlSnippet, DecentSamplerTag.MIDI);
            return;
        }

        for (final Element templateChildElement: XMLUtils.getChildElements (templateMidiElement))
        {
            Element ccElement = null;
            if (DecentSamplerTag.MIDI_CC.equals (templateChildElement.getNodeName ()))
                for (final Element element: XMLUtils.getChildElementsByName (midiElement, DecentSamplerTag.MIDI_CC, false))
                    if (element.getAttribute (DecentSamplerTag.MIDI_CC_NUMBER).equals (templateChildElement.getAttribute (DecentSamplerTag.MIDI_CC_NUMBER)))
                        ccElement = element;
            if (ccElement == null)
                midiElement.appendChild (templateChildElement);
            else
                while (templateChildElement.getFirstChild () != null)
                    ccElement.appendChild (templateChildElement.getFirstChild ());
        }
    }


    private static boolean hasModWheelVibratoTag (final String tags)
    {
        for (final String tag: tags.split (","))
            if (DecentSamplerTag.MOD_WHEEL_VIBRATO.equals (tag.trim ()))
                return true;
        return false;
    }


    private static String getTemplateValue (final Element element, final String attribute, final double defaultValue)
    {
        final double value = element == null ? defaultValue : XMLUtils.getDoubleAttribute (element, attribute, defaultValue);
        return String.format (Locale.US, "%.3f", Double.valueOf (value));
    }


    /**
     * Get the value of a control in the range of [0..1] which results in the given cutoff frequency
     * with the cutoff translation table.
     *
     * @param cutoff The cutoff frequency in Hertz
     * @return The value of the control
     */
    private static double toFilterCutoffControlValue (final double cutoff)
    {
        final String [] points = FILTER_CUTOFF_TABLE.split (";");
        double previousValue = 0;
        double previousCutoff = 0;
        for (int i = 0; i < points.length; i++)
        {
            final String [] point = points[i].split (",");
            final double value = Double.parseDouble (point[0]);
            final double pointCutoff = Double.parseDouble (point[1]);
            if (cutoff <= pointCutoff)
            {
                if (i == 0)
                    return value;
                return previousValue + (value - previousValue) * (cutoff - previousCutoff) / (pointCutoff - previousCutoff);
            }
            previousValue = value;
            previousCutoff = pointCutoff;
        }
        return 1.0;
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

        // A curve which is not set is logarithmic for the attack and exponential for the decay and
        // the release, therefore a linear curve (0) needs to be written as well
        if (envelope.isSet ())
        {
            XMLUtils.setDoubleAttribute (element, DecentSamplerTag.ENV_ATTACK_CURVE, envelope.getAttackSlope () * 100.0, 3);
            XMLUtils.setDoubleAttribute (element, DecentSamplerTag.ENV_DECAY_CURVE, envelope.getDecaySlope () * 100.0, 3);
            XMLUtils.setDoubleAttribute (element, DecentSamplerTag.ENV_RELEASE_CURVE, envelope.getReleaseSlope () * 100.0, 3);
        }
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
        if (!templateFolderPath.getPath ().isEmpty () && templateFolderPath.exists ())
            copyFolderWithIgnoreList (templateFolderPath, resourceDestination, IGNORE_FILES);
    }


    private void copyResources (final ZipOutputStream zos, final String basePath) throws IOException
    {
        final File templateFolderPath = this.settingsConfiguration.getTemplateFolderPath ();
        if (!templateFolderPath.getPath ().isEmpty () && templateFolderPath.exists ())
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