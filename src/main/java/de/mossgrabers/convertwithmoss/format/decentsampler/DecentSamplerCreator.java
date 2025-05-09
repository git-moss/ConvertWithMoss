// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;


/**
 * Creator for dspreset multi-sample files. A dspreset has a description file encoded in XML. The
 * related samples are in a separate folder. The description file and sample files can optionally be
 * zipped into a dslibrary file.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerCreator extends AbstractCreator
{
    private static final String LIBRARY_INFO_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<DecentSamplerLibraryInfo name=\"%LIBRARY_NAME%\"/>";


    private class PresetResult
    {
        String             dsPreset;
        File               dsPresetFile;
        String             sampleFolder;
        IMultisampleSource sampleSource;
    }


    private static final String DS_OUTPUT_CREATE_BUNDLE   = "DsOutputCreateBundle";
    private static final String DS_OUTPUT_MAKE_MONOPHONIC = "DsOutputMakeMonophonic";
    private static final String DS_OUTPUT_ADD_REVERB      = "DsOutputAddReverb";

    private CheckBox            createBundleBox;
    private CheckBox            addReverbBox;
    private CheckBox            makeMonophonicBox;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DecentSamplerCreator (final INotifier notifier)
    {
        super ("DecentSampler", notifier);

        this.configureWavChunkUpdates (true, false, false, false);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_DS_OUTPUT_FORMAT");
        this.createBundleBox = panel.createCheckBox ("@IDS_DS_CREATE_BUNDLE");

        final TitledSeparator separator = panel.createSeparator ("@IDS_DS_USER_INTERFACE");
        separator.getStyleClass ().add ("titled-separator-pane");

        this.makeMonophonicBox = panel.createCheckBox ("@IDS_DS_MAKE_MONOPHONIC");
        this.addReverbBox = panel.createCheckBox ("@IDS_DS_ADD_REVERB");

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.createBundleBox.setSelected (config.getBoolean (DS_OUTPUT_CREATE_BUNDLE, false));
        this.makeMonophonicBox.setSelected (config.getBoolean (DS_OUTPUT_MAKE_MONOPHONIC, false));
        this.addReverbBox.setSelected (config.getBoolean (DS_OUTPUT_ADD_REVERB, true));

        this.loadWavChunkSettings (config, "Ds");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (DS_OUTPUT_CREATE_BUNDLE, this.createBundleBox.isSelected ());
        config.setBoolean (DS_OUTPUT_MAKE_MONOPHONIC, this.makeMonophonicBox.isSelected ());
        config.setBoolean (DS_OUTPUT_ADD_REVERB, this.addReverbBox.isSelected ());

        this.saveWavChunkSettings (config, "Ds");
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final List<PresetResult> results = this.create (destinationFolder, Collections.singletonList (multisampleSource), false);

        if (this.createBundleBox.isSelected ())
        {
            // Note: method is called for each multi-source individually!
            final File multiFile = this.createUniqueFilename (destinationFolder, multisampleSource.getName (), "dsbundle");
            this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

            this.storeBundle (multiFile, Collections.singletonList (results.get (0)));
        }
        else
        {
            for (final PresetResult presetResult: results)
            {
                this.notifier.log ("IDS_NOTIFY_STORING", presetResult.dsPresetFile.getAbsolutePath ());
                this.storePreset (destinationFolder, presetResult);
            }
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public void createLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final List<PresetResult> results = create (destinationFolder, multisampleSources, true);

        final boolean isBundle = this.createBundleBox.isSelected ();
        final String extension = isBundle ? "dsbundle" : "dslibrary";
        final File multiFile = this.createUniqueFilename (destinationFolder, libraryName, extension);
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
        if (isBundle)
            this.storeBundle (multiFile, results);
        else
            this.storeLibrary (multiFile, results);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private final List<PresetResult> create (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final boolean isLibrary)
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

            final Optional<String> metadata = this.createPresetMetadata (presetResult.sampleFolder, multisampleSource);
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
        final String libraryPath = FileUtils.getNameWithoutType (multiFile) + FORWARD_SLASH;

        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            for (final PresetResult presetResult: presetResults)
            {
                AbstractCreator.zipTextFile (zos, libraryPath + presetResult.dsPresetFile.getName (), presetResult.dsPreset);
                this.zipSampleFiles (zos, libraryPath + presetResult.sampleFolder, presetResult.sampleSource);
            }
        }
    }


    /**
     * Create the text of the description file.
     *
     * @param folderName The name to use for the sample folder
     * @param multisampleSource The multi-sample
     * @return The XML structure
     */
    private Optional<String> createPresetMetadata (final String folderName, final IMultisampleSource multisampleSource)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element multisampleElement = document.createElement (DecentSamplerTag.DECENTSAMPLER);
        document.appendChild (multisampleElement);
        multisampleElement.setAttribute ("minVersion", "1.11");

        // No metadata at all

        final Element groupsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.GROUPS);
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);

        this.createUI (document, multisampleElement);

        // Add all groups

        final Element modulatorsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.MODULATORS);

        for (int i = 0; i < groups.size (); i++)
        {
            final IGroup group = groups.get (i);
            final Element groupElement = XMLUtils.addElement (document, groupsElement, DecentSamplerTag.GROUP);

            final String name = group.getName ();
            if (name != null && !name.isBlank ())
                groupElement.setAttribute ("name", name);

            final TriggerType triggerType = group.getTrigger ();
            if (triggerType != TriggerType.ATTACK)
                groupElement.setAttribute (DecentSamplerTag.TRIGGER, triggerType.name ().toLowerCase (Locale.ENGLISH));

            final Set<Double> ampVelDepths = new HashSet<> ();
            final List<ISampleZone> zones = group.getSampleZones ();
            for (final ISampleZone zone: zones)
            {
                ampVelDepths.add (Double.valueOf (zone.getAmplitudeVelocityModulator ().getDepth ()));
                createSample (document, folderName, groupElement, zone);
            }
            if (ampVelDepths.size () == 1)
                groupElement.setAttribute (DecentSamplerTag.AMP_VELOCITY_TRACK, Double.toString (ampVelDepths.iterator ().next ().doubleValue ()));

            createFilter (document, modulatorsElement, multisampleSource, groupElement, i);
            if (!zones.isEmpty ())
                createPitchModulator (document, modulatorsElement, zones.get (0).getPitchModulator (), i);
        }

        this.makeMonophonic (document, multisampleElement, groupsElement);
        this.createEffects (document, multisampleElement);
        return this.createXMLString (document);
    }


    private void makeMonophonic (final Document document, final Element multisampleElement, final Element groupsElement)
    {
        if (!this.makeMonophonicBox.isSelected ())
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
     */
    private static void createSample (final Document document, final String folderName, final Element groupElement, final ISampleZone zone)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, groupElement, DecentSamplerTag.SAMPLE);
        addVolumeEnvelope (zone.getAmplitudeEnvelopeModulator ().getSource (), sampleElement);

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
        if (!loops.isEmpty ())
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
    }


    private static void createFilter (final Document document, final Element modulatorsElement, final IMultisampleSource multisampleSource, final Element groupElement, final int groupIndex)
    {
        final Optional<IFilter> globalFilter = multisampleSource.getGlobalFilter ();
        if (!globalFilter.isPresent ())
            return;

        // Needs to be added to all groups since there seems to be a bug with envelope
        // modulation on the instrument level...

        final Element effectsElement = XMLUtils.addElement (document, groupElement, DecentSamplerTag.EFFECTS);
        final Element filterElement = XMLUtils.addElement (document, effectsElement, DecentSamplerTag.EFFECTS_EFFECT);

        boolean isNotch = false;
        final IFilter filter = globalFilter.get ();
        switch (filter.getType ())
        {
            default:
            case LOW_PASS:
                switch (filter.getPoles ())
                {
                    case 4:
                        filterElement.setAttribute ("type", "lowpass_4pl");
                        break;
                    case 1:
                        filterElement.setAttribute ("type", "lowpass_1pl");
                        break;
                    default:
                        filterElement.setAttribute ("type", "lowpass");
                        break;
                }
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

        // Note: Resonance is in the range [0..1] but it is not documented what value 1
        // represents. Therefore, we assume 40dB maximum and a linear range (could also
        // be logarithmic).

        // There seems to be an issue with resonance (default = 0.7 seems to act like 0)...
        filterElement.setAttribute ("resonance", formatDouble (filter.getResonance () + 0.7, 3));
        filterElement.setAttribute (isNotch ? "q" : "frequency", formatDouble (filter.getCutoff (), 2));

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final double envelopeDepth = cutoffModulator.getDepth ();
        if (envelopeDepth > 0)
        {
            final Element envelopeElement = XMLUtils.addElement (document, modulatorsElement, DecentSamplerTag.ENVELOPE);
            XMLUtils.setDoubleAttribute (envelopeElement, DecentSamplerTag.MOD_AMOUNT, envelopeDepth, 2);
            envelopeElement.setAttribute ("scope", "voice");

            final IEnvelope filterEnvelope = cutoffModulator.getSource ();
            setEnvelopeTimeAttribute (envelopeElement, DecentSamplerTag.ENV_ATTACK, filterEnvelope.getAttackTime ());
            setEnvelopeTimeAttribute (envelopeElement, DecentSamplerTag.ENV_DECAY, filterEnvelope.getDecayTime ());
            setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_SUSTAIN, filterEnvelope.getSustainLevel ());
            setEnvelopeTimeAttribute (envelopeElement, DecentSamplerTag.ENV_RELEASE, filterEnvelope.getReleaseTime ());

            final Element bindingElement = XMLUtils.addElement (document, envelopeElement, DecentSamplerTag.BINDING);
            bindingElement.setAttribute ("type", "effect");
            bindingElement.setAttribute ("level", "group");
            bindingElement.setAttribute ("groupIndex", Integer.toString (groupIndex));
            bindingElement.setAttribute ("effectIndex", "0");
            bindingElement.setAttribute ("parameter", "FX_FILTER_FREQUENCY");
            bindingElement.setAttribute ("modBehavior", "set");
            bindingElement.setAttribute ("translation", "table");
            bindingElement.setAttribute ("translationTable", "0,33;0.3,150;0.4,450;0.5,1100;0.7,4100;0.9,11000;1.0001,22000");
        }
    }


    /**
     * Creates the reverb effect elements.
     *
     * @param document The XML document
     * @param rootElement Where to add the effect elements
     */
    private void createEffects (final Document document, final Element rootElement)
    {
        if (!this.addReverbBox.isSelected ())
            return;

        final Element effectsElement = XMLUtils.addElement (document, rootElement, DecentSamplerTag.EFFECTS);
        final Element reverbElement = XMLUtils.addElement (document, effectsElement, DecentSamplerTag.EFFECTS_EFFECT);
        reverbElement.setAttribute ("type", "reverb");
    }


    private static void createPitchModulator (final Document document, final Element modulatorsElement, final IEnvelopeModulator pitchModulator, final int groupIndex)
    {
        final double envelopeDepth = pitchModulator.getDepth ();
        // Only positive values allowed in DecentSampler
        if (envelopeDepth <= 0)
            return;

        final Element envelopeElement = XMLUtils.addElement (document, modulatorsElement, DecentSamplerTag.ENVELOPE);
        XMLUtils.setDoubleAttribute (envelopeElement, DecentSamplerTag.MOD_AMOUNT, envelopeDepth, 2);

        final IEnvelope envelope = pitchModulator.getSource ();
        setEnvelopeTimeAttribute (envelopeElement, DecentSamplerTag.ENV_ATTACK, envelope.getAttackTime ());
        setEnvelopeTimeAttribute (envelopeElement, DecentSamplerTag.ENV_DECAY, envelope.getDecayTime ());
        setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_SUSTAIN, envelope.getSustainLevel ());
        setEnvelopeTimeAttribute (envelopeElement, DecentSamplerTag.ENV_RELEASE, envelope.getReleaseTime ());

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
     * Create the static user interface.
     *
     * @param document The XML document
     * @param root The root XML element
     */
    private void createUI (final Document document, final Element root)
    {
        final Element uiElement = XMLUtils.addElement (document, root, DecentSamplerTag.UI);
        final Element tabElement = XMLUtils.addElement (document, uiElement, DecentSamplerTag.TAB);
        tabElement.setAttribute ("name", "main");

        if (this.addReverbBox.isSelected ())
        {
            Element knobElement = createKnob (document, tabElement, 200, 0, "Reverb Wet Level", 1000, 0);
            Element bindingElement = createBinding (document, knobElement, DecentSamplerTag.MOD_EFFECT, "FX_REVERB_WET_LEVEL");
            bindingElement.setAttribute ("position", "1");
            bindingElement.setAttribute ("translation", "linear");
            bindingElement.setAttribute ("translationOutputMax", "1");
            bindingElement.setAttribute ("translationOutputMin", "0.0");

            knobElement = createKnob (document, tabElement, 300, 0, "Reverb Room Size", 1000, 0);
            bindingElement = createBinding (document, knobElement, DecentSamplerTag.MOD_EFFECT, "FX_REVERB_ROOM_SIZE");
            bindingElement.setAttribute ("position", "1");
            bindingElement.setAttribute ("translation", "linear");
            bindingElement.setAttribute ("translationOutputMax", "1");
            bindingElement.setAttribute ("translationOutputMin", "0.0");
        }
    }


    private static Element createKnob (final Document document, final Element tab, final int x, final int y, final String label, final int maxValue, final double value)
    {
        final Element knobElement = XMLUtils.addElement (document, tab, DecentSamplerTag.LABELED_KNOB);
        knobElement.setAttribute ("x", Integer.toString (x));
        knobElement.setAttribute ("y", Integer.toString (y));
        knobElement.setAttribute ("label", label);
        knobElement.setAttribute ("type", "float");
        knobElement.setAttribute ("minValue", "0");
        knobElement.setAttribute ("maxValue", Integer.toString (maxValue));
        knobElement.setAttribute ("textColor", "FF000000");
        knobElement.setAttribute ("value", Double.toString (value));
        return knobElement;
    }


    private static final Element createBinding (final Document document, final Element knobElement, final String type, final String parameter)
    {
        final Element bindingElement = XMLUtils.addElement (document, knobElement, DecentSamplerTag.BINDING);
        bindingElement.setAttribute ("type", type);
        bindingElement.setAttribute ("level", "instrument");
        bindingElement.setAttribute ("parameter", parameter);
        return bindingElement;
    }


    /**
     * Add the amplitude envelope parameters to the given element.
     *
     * @param amplitudeEnvelope The amplitude envelope
     * @param element Where to add the parameters
     */
    private static void addVolumeEnvelope (final IEnvelope amplitudeEnvelope, final Element element)
    {
        setEnvelopeTimeAttribute (element, DecentSamplerTag.ENV_ATTACK, amplitudeEnvelope.getAttackTime ());
        setEnvelopeTimeAttribute (element, DecentSamplerTag.ENV_DECAY, amplitudeEnvelope.getDecayTime ());
        setEnvelopeAttribute (element, DecentSamplerTag.ENV_SUSTAIN, amplitudeEnvelope.getSustainLevel ());
        setEnvelopeTimeAttribute (element, DecentSamplerTag.ENV_RELEASE, amplitudeEnvelope.getReleaseTime ());

        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_ATTACK_CURVE, amplitudeEnvelope.getAttackSlope () * 100.0);
        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_DECAY_CURVE, amplitudeEnvelope.getDecaySlope () * 100.0);
        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_RELEASE_CURVE, amplitudeEnvelope.getReleaseSlope () * 100.0);
    }


    private static void setEnvelopeTimeAttribute (final Element element, final String attribute, final double value)
    {
        // Adjust the seconds by factor 2 which seems more fitting!
        if (value >= 0)
            setEnvelopeAttribute (element, attribute, value / 2.0);
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
}