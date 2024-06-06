// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;


/**
 * Creator for dspreset multi-sample files. A dspreset has a description file encoded in XML. The
 * related samples are in a separate folder. The description file and sample files can optionally be
 * zipped into a dslibrary file.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerCreator extends AbstractCreator
{
    private boolean             isOutputFormatLibrary;

    private static final String DS_OUTPUT_FORMAT_LIBRARY  = "DsOutputFormatPreset";
    private static final String DS_OUTPUT_MAKE_MONOPHONIC = "DsOutputMakeMonophonic";
    private static final String DS_OUTPUT_ADD_REVERB      = "DsOutputAddReverb";

    private ToggleGroup         outputFormatGroup;
    private CheckBox            addReverbBox;
    private CheckBox            makeMonophonicBox;

    private int                 seqPosition               = 1;


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

        this.outputFormatGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_DS_PRESET");
        order1.setToggleGroup (this.outputFormatGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_DS_LIBRARY");
        order2.setToggleGroup (this.outputFormatGroup);

        this.makeMonophonicBox = panel.createCheckBox ("@IDS_DS_MAKE_MONOPHONIC");

        final TitledSeparator separator = panel.createSeparator ("@IDS_DS_USER_INTERFACE");
        separator.getStyleClass ().add ("titled-separator-pane");

        this.addReverbBox = panel.createCheckBox ("@IDS_DS_ADD_REVERB");

        this.addWavChunkOptions (panel).getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputFormatGroup.selectToggle (this.outputFormatGroup.getToggles ().get (config.getBoolean (DS_OUTPUT_FORMAT_LIBRARY, true) ? 1 : 0));
        this.makeMonophonicBox.setSelected (config.getBoolean (DS_OUTPUT_MAKE_MONOPHONIC, false));
        this.addReverbBox.setSelected (config.getBoolean (DS_OUTPUT_ADD_REVERB, true));

        this.loadWavChunkSettings (config, "Ds");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (DS_OUTPUT_FORMAT_LIBRARY, this.isOutputFormatLibrary ());
        config.setBoolean (DS_OUTPUT_MAKE_MONOPHONIC, this.makeMonophonicBox.isSelected ());
        config.setBoolean (DS_OUTPUT_ADD_REVERB, this.addReverbBox.isSelected ());

        this.saveWavChunkSettings (config, "Ds");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.seqPosition = 1;

        this.setOutputToLibrary (this.isOutputFormatLibrary ());

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = new File (destinationFolder, sampleName + (this.isOutputFormatLibrary ? ".dslibrary" : ".dspreset"));
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        final String relativeFolderName = this.isOutputFormatLibrary ? FOLDER_POSTFIX.trim () : sampleName + FOLDER_POSTFIX;

        final Optional<String> metadata = this.createMetadata (relativeFolderName, multisampleSource);
        if (metadata.isEmpty ())
            return;

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        if (this.isOutputFormatLibrary)
            this.storeLibrary (relativeFolderName, multisampleSource, sampleName, multiFile, metadata.get ());
        else
            this.storePreset (relativeFolderName, destinationFolder, multisampleSource, multiFile, metadata.get ());

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a dspreset file.
     *
     * @param relativeFolderName A relative path for the samples
     * @param destinationFolder Where to store the preset file
     * @param multisampleSource The multi-sample to store in the library
     * @param multiFile The file of the dslibrary
     * @param metadata The dspreset metadata description file
     * @throws IOException Could not store the file
     */
    private void storePreset (final String relativeFolderName, final File destinationFolder, final IMultisampleSource multisampleSource, final File multiFile, final String metadata) throws IOException
    {
        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, relativeFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource);
    }


    /**
     * Create a dslibrary file.
     *
     * @param relativeFolderName A relative path for the samples
     * @param multisampleSource The multi-sample to store in the library
     * @param sampleName The name of the multi-sample
     * @param multiFile The file of the dslibrary
     * @param metadata The dspreset metadata description file
     * @throws IOException Could not store the file
     */
    private void storeLibrary (final String relativeFolderName, final IMultisampleSource multisampleSource, final String sampleName, final File multiFile, final String metadata) throws IOException
    {
        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            AbstractCreator.zipTextFile (zos, sampleName + ".dspreset", metadata);
            this.zipSampleFiles (zos, relativeFolderName, multisampleSource);
        }
    }


    /**
     * Create the text of the description file.
     *
     * @param folderName The name to use for the sample folder
     * @param multisampleSource The multi-sample
     * @return The XML structure
     */
    private Optional<String> createMetadata (final String folderName, final IMultisampleSource multisampleSource)
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

        boolean hasRoundRobin = false;

        if (!groups.isEmpty ())
        {
            final ISampleZone zone = groups.get (0).getSampleZones ().get (0);

            final PlayLogic playLogic = zone.getPlayLogic ();
            hasRoundRobin = playLogic == PlayLogic.ROUND_ROBIN;
            if (hasRoundRobin)
                groupsElement.setAttribute (DecentSamplerTag.SEQ_MODE, "round_robin");
        }
        this.createUI (document, multisampleElement);

        // Add all groups

        final Element modulatorsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.MODULATORS);

        for (int i = 0; i < groups.size (); i++)
        {
            final IGroup group = groups.get (i);

            final Element groupElement = XMLUtils.addElement (document, groupsElement, DecentSamplerTag.GROUP);

            if (hasRoundRobin)
            {
                groupElement.setAttribute (DecentSamplerTag.SEQ_POSITION, Integer.toString (this.seqPosition));
                this.seqPosition++;
            }

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
        sampleElement.setAttribute (DecentSamplerTag.PANORAMA, Integer.toString ((int) (zone.getPanorama () * 100.0)));
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


    /**
     * Set the output format to otherwise preset only.
     *
     * @param outputFormatLibrary True to output dspreset files otherwise dslibrary
     */
    public void setOutputToLibrary (final boolean outputFormatLibrary)
    {
        this.isOutputFormatLibrary = outputFormatLibrary;
    }


    /**
     * Check if the toggle setting is set to preset or library.
     *
     * @return True if library
     */
    private boolean isOutputFormatLibrary ()
    {
        return this.outputFormatGroup.getToggles ().get (1).isSelected ();
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
            setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_ATTACK, filterEnvelope.getAttackTime ());
            setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_DECAY, filterEnvelope.getDecayTime ());
            setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_SUSTAIN, filterEnvelope.getSustainLevel ());
            setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_RELEASE, filterEnvelope.getReleaseTime ());

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
        // Only positive values allowed in Decent Sampler
        if (envelopeDepth <= 0)
            return;

        final Element envelopeElement = XMLUtils.addElement (document, modulatorsElement, DecentSamplerTag.ENVELOPE);
        XMLUtils.setDoubleAttribute (envelopeElement, DecentSamplerTag.MOD_AMOUNT, envelopeDepth, 2);

        final IEnvelope envelope = pitchModulator.getSource ();
        setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_ATTACK, envelope.getAttackTime ());
        setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_DECAY, envelope.getDecayTime ());
        setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_SUSTAIN, envelope.getSustainLevel ());
        setEnvelopeAttribute (envelopeElement, DecentSamplerTag.ENV_RELEASE, envelope.getReleaseTime ());

        final Element bindingElement = XMLUtils.addElement (document, envelopeElement, DecentSamplerTag.BINDING);
        bindingElement.setAttribute ("type", "amp");
        bindingElement.setAttribute ("level", "group");
        bindingElement.setAttribute ("groupIndex", Integer.toString (groupIndex));
        bindingElement.setAttribute ("parameter", "GROUP_TUNING");
        bindingElement.setAttribute ("translation", "linear");
        bindingElement.setAttribute ("translationOutputMin", "0");
        bindingElement.setAttribute ("translationOutputMax", Integer.toString (IEnvelope.MAX_ENVELOPE_DEPTH));
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
        setEnvelopeAttribute (element, DecentSamplerTag.ENV_ATTACK, amplitudeEnvelope.getAttackTime ());
        setEnvelopeAttribute (element, DecentSamplerTag.ENV_DECAY, amplitudeEnvelope.getDecayTime ());
        setEnvelopeAttribute (element, DecentSamplerTag.ENV_SUSTAIN, amplitudeEnvelope.getSustainLevel ());
        setEnvelopeAttribute (element, DecentSamplerTag.ENV_RELEASE, amplitudeEnvelope.getReleaseTime ());

        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_ATTACK_CURVE, amplitudeEnvelope.getAttackSlope () * 100.0);
        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_DECAY_CURVE, amplitudeEnvelope.getDecaySlope () * 100.0);
        setEnvelopeSlopeAttribute (element, DecentSamplerTag.ENV_RELEASE_CURVE, amplitudeEnvelope.getReleaseSlope () * 100.0);
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