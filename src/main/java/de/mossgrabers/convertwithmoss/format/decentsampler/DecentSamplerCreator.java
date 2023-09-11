// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

import javax.xml.transform.TransformerException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipOutputStream;


/**
 * Creator for dspreset multi-sample files. A dspreset has a description file encoded in XML. The
 * related samples are in a separate folder. The description file and sample files can optionally be
 * zipped into a dslibrary file.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerCreator extends AbstractCreator
{
    private static final String MOD_AMP                   = "amp";
    private static final String FOLDER_POSTFIX            = "Samples";
    private boolean             isOutputFormatLibrary;

    private static final String DS_OUTPUT_FORMAT_LIBRARY  = "DsOutputFormatPreset";
    private static final String DS_OUTPUT_MAKE_MONOPHONIC = "DsOutputMakeMonophonic";
    private static final String DS_OUTPUT_ADD_ENVELOPE    = "DsOutputAddEnvelope";
    private static final String DS_OUTPUT_ADD_FILTER      = "DsOutputAddFilter";
    private static final String DS_OUTPUT_ADD_REVERB      = "DsOutputAddReverb";

    private ToggleGroup         outputFormatGroup;
    private CheckBox            addEnvelopeBox;
    private CheckBox            addFilterBox;
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
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        this.outputFormatGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_DS_PRESET");
        order1.setToggleGroup (this.outputFormatGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_DS_LIBRARY");
        order2.setToggleGroup (this.outputFormatGroup);

        this.makeMonophonicBox = panel.createCheckBox ("@IDS_DS_MAKE_MONOPHONIC");

        panel.createSeparator ("@IDS_DS_USER_INTERFACE");

        this.addEnvelopeBox = panel.createCheckBox ("@IDS_DS_ADD_ENVELOPE");
        this.addFilterBox = panel.createCheckBox ("@IDS_DS_ADD_FILTER");
        this.addReverbBox = panel.createCheckBox ("@IDS_DS_ADD_REVERB");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputFormatGroup.selectToggle (this.outputFormatGroup.getToggles ().get (config.getBoolean (DS_OUTPUT_FORMAT_LIBRARY, true) ? 1 : 0));
        this.makeMonophonicBox.setSelected (config.getBoolean (DS_OUTPUT_MAKE_MONOPHONIC, false));
        this.addEnvelopeBox.setSelected (config.getBoolean (DS_OUTPUT_ADD_ENVELOPE, true));
        this.addFilterBox.setSelected (config.getBoolean (DS_OUTPUT_ADD_FILTER, true));
        this.addReverbBox.setSelected (config.getBoolean (DS_OUTPUT_ADD_REVERB, true));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (DS_OUTPUT_FORMAT_LIBRARY, this.isOutputFormatLibrary ());
        config.setBoolean (DS_OUTPUT_MAKE_MONOPHONIC, this.makeMonophonicBox.isSelected ());
        config.setBoolean (DS_OUTPUT_ADD_ENVELOPE, this.addEnvelopeBox.isSelected ());
        config.setBoolean (DS_OUTPUT_ADD_FILTER, this.addFilterBox.isSelected ());
        config.setBoolean (DS_OUTPUT_ADD_REVERB, this.addReverbBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.setOutputToLibrary (this.isOutputFormatLibrary ());

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = new File (destinationFolder, sampleName + (this.isOutputFormatLibrary ? ".dslibrary" : ".dspreset"));
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        final String relativeFolderName = this.isOutputFormatLibrary ? FOLDER_POSTFIX : sampleName + " " + FOLDER_POSTFIX;

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
     * @param multisampleSource The multi sample to store in the library
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
     * @param multisampleSource The multi sample to store in the library
     * @param sampleName The name of the multi sample
     * @param multiFile The file of the dslibrary
     * @param metadata The dspreset metadata description file
     * @throws IOException Could not store the file
     */
    private void storeLibrary (final String relativeFolderName, final IMultisampleSource multisampleSource, final String sampleName, final File multiFile, final String metadata) throws IOException
    {
        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            this.zipTextFile (zos, sampleName + ".dspreset", metadata);
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

        // No metadata at all

        final Element groupsElement = XMLUtils.addElement (document, multisampleElement, DecentSamplerTag.GROUPS);
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);

        boolean hasRoundRobin = false;

        IEnvelope amplitudeEnvelope = null;
        if (!groups.isEmpty ())
        {
            final ISampleMetadata sampleMetadata = groups.get (0).getSampleMetadata ().get (0);
            amplitudeEnvelope = sampleMetadata.getAmplitudeModulator ().getSource ();
            addVolumeEnvelope (amplitudeEnvelope, groupsElement);

            final PlayLogic playLogic = sampleMetadata.getPlayLogic ();
            hasRoundRobin = playLogic != PlayLogic.ALWAYS;
            if (hasRoundRobin)
                groupsElement.setAttribute (DecentSamplerTag.SEQ_MODE, "round_robin");
        }
        this.createUI (document, multisampleElement, amplitudeEnvelope);

        // Add all groups

        for (final IGroup group: groups)
        {
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

            for (final ISampleMetadata sample: group.getSampleMetadata ())
                createSample (document, folderName, groupElement, sample);
        }

        this.makeMonophonic (document, multisampleElement, groupsElement);
        this.createEffects (document, multisampleElement, multisampleSource);

        try
        {
            return Optional.of (XMLUtils.toString (document));
        }
        catch (final TransformerException ex)
        {
            this.notifier.logError (ex);
            return Optional.empty ();
        }
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
     * @param info Where to get the sample info from
     */
    private static void createSample (final Document document, final String folderName, final Element groupElement, final ISampleMetadata info)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, groupElement, DecentSamplerTag.SAMPLE);
        final Optional<String> filename = info.getUpdatedFilename ();
        if (filename.isPresent ())
            sampleElement.setAttribute (DecentSamplerTag.PATH, AbstractCreator.formatFileName (folderName, filename.get ()));

        final double gain = info.getGain ();
        if (gain != 0)
            sampleElement.setAttribute (DecentSamplerTag.VOLUME, gain + "dB");
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.START, Math.max (0, info.getStart ()), 3);
        final int stop = info.getStop ();
        if (stop >= 0)
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.END, stop, 3);
        // Convert cent to semitones
        final double tune = info.getTune () / 100;
        if (tune != 0)
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.TUNING, tune, 2);

        final TriggerType triggerType = info.getTrigger ();
        if (triggerType != TriggerType.ATTACK)
            sampleElement.setAttribute (DecentSamplerTag.TRIGGER, triggerType.name ().toLowerCase (Locale.ENGLISH));

        // No info.isReversed ()

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LO_NOTE, check (info.getKeyLow (), 0));
        // No fades info.getNoteCrossfadeLow ()
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.ROOT_NOTE, info.getKeyRoot ());
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.HI_NOTE, check (info.getKeyHigh (), 127));
        // No fades info.getNoteCrossfadeHigh ()
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.PITCH_KEY_TRACK, info.getKeyTracking (), 4);
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LO_VEL, check (info.getVelocityLow (), 0));
        // No fades info.getVelocityCrossfadeLow ()
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.HI_VEL, check (info.getVelocityHigh (), 127));
        // No fades info.getVelocityCrossfadeHigh ()

        /////////////////////////////////////////////////////
        // Loops

        final List<ISampleLoop> loops = info.getLoops ();
        if (!loops.isEmpty ())
        {

            final ISampleLoop sampleLoop = loops.get (0);
            sampleElement.setAttribute (DecentSamplerTag.LOOP_ENABLED, "true");
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_START, check (sampleLoop.getStart (), 0), 3);
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_END, check (sampleLoop.getEnd (), stop), 3);

            // Calculate the crossfade in frames/samples from a percentage of the loop length
            final double crossfade = sampleLoop.getCrossfade ();
            if (crossfade > 0)
            {
                final int loopLength = sampleLoop.getStart () - sampleLoop.getEnd ();
                if (loopLength > 0)
                    XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LOOP_CROSSFADE, (int) Math.round (loopLength * crossfade));
            }
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


    /**
     * Creates the filter and reverb effect elements.
     *
     * @param document The XML document
     * @param rootElement Where to add the effect elements
     * @param multisampleSource The multi-sample
     */
    private void createEffects (final Document document, final Element rootElement, final IMultisampleSource multisampleSource)
    {
        final Optional<IFilter> optFilter = multisampleSource.getGlobalFilter ();

        final boolean lowPassFilterIsPresent = optFilter.isPresent () && optFilter.get ().getType () == FilterType.LOW_PASS;
        final boolean hasFilter = this.addFilterBox.isSelected () || lowPassFilterIsPresent;
        final boolean hasReverb = this.addReverbBox.isSelected ();

        if (hasFilter || hasReverb)
        {
            final Element effectsElement = XMLUtils.addElement (document, rootElement, DecentSamplerTag.EFFECTS);

            if (hasFilter)
            {
                final Element filterElement = XMLUtils.addElement (document, effectsElement, DecentSamplerTag.EFFECTS_EFFECT);
                filterElement.setAttribute ("type", "lowpass_4pl");
                if (lowPassFilterIsPresent)
                {
                    // Note: this might not be a 4 pole low-pass but better than no filter...
                    final IFilter filter = optFilter.get ();
                    // Note: Resonance is in the range [0..1] but it is not documented what value 1
                    // represents. Therefore, we assume 40dB maximum and a linear range (could also
                    // be logarithmic).
                    final double resonance = Math.min (40, filter.getResonance ());
                    filterElement.setAttribute ("resonance", formatDouble (resonance / 40.0, 3));
                    filterElement.setAttribute ("frequency", formatDouble (filter.getCutoff (), 2));
                }
                else
                {
                    filterElement.setAttribute ("resonance", "0.5");
                    filterElement.setAttribute ("frequency", "22000");
                }
            }

            if (hasReverb)
            {
                final Element reverbElement = XMLUtils.addElement (document, effectsElement, DecentSamplerTag.EFFECTS_EFFECT);
                reverbElement.setAttribute ("type", "reverb");
            }
        }
    }


    /**
     * Create the static user interface.
     *
     * @param document The XML document
     * @param root The root XML element
     * @param amplitudeEnvelope The amplitude envelope
     */
    private void createUI (final Document document, final Element root, final IEnvelope amplitudeEnvelope)
    {
        final Element uiElement = XMLUtils.addElement (document, root, DecentSamplerTag.UI);
        final Element tabElement = XMLUtils.addElement (document, uiElement, DecentSamplerTag.TAB);
        tabElement.setAttribute ("name", "main");

        Element knobElement;

        if (this.addFilterBox.isSelected ())
        {
            knobElement = createKnob (document, tabElement, 0, 0, "Filter Cutoff", 22000, 22000);
            createBinding (document, knobElement, DecentSamplerTag.MOD_EFFECT, "FX_FILTER_FREQUENCY");
            knobElement = createKnob (document, tabElement, 100, 0, "Filter Resonance", 2, 0.01);
            createBinding (document, knobElement, DecentSamplerTag.MOD_EFFECT, "FX_FILTER_RESONANCE");
        }

        if (this.addReverbBox.isSelected ())
        {
            knobElement = createKnob (document, tabElement, 200, 0, "Reverb Wet Level", 1000, 0);
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

        if (this.addEnvelopeBox.isSelected ())
        {
            final double attackValue = getEnvelopeAttribute (amplitudeEnvelope.getAttack (), 0, 10, 0);
            final double decayValue = getEnvelopeAttribute (amplitudeEnvelope.getDecay (), 0, 25, 0);
            final double sustainValue = getEnvelopeAttribute (amplitudeEnvelope.getSustain (), 0, 1, 1);
            final double releaseValue = getEnvelopeAttribute (amplitudeEnvelope.getRelease (), 0, 25, 0.01);

            final Element attackKnobElement = createKnob (document, tabElement, 0, 100, "Attack", 1, attackValue);
            final Element decayKnobElement = createKnob (document, tabElement, 100, 100, "Decay", 1, decayValue);
            final Element sustainKnobElement = createKnob (document, tabElement, 200, 100, "Sustain", 1, sustainValue);
            final Element releaseKnobElement = createKnob (document, tabElement, 300, 100, "Release", 1, releaseValue);

            createBinding (document, attackKnobElement, MOD_AMP, "ENV_ATTACK");
            createBinding (document, decayKnobElement, MOD_AMP, "ENV_DECAY");
            createBinding (document, sustainKnobElement, MOD_AMP, "ENV_SUSTAIN");
            createBinding (document, releaseKnobElement, MOD_AMP, "ENV_RELEASE");
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
        setEnvelopeAttribute (element, DecentSamplerTag.AMP_ENV_ATTACK, amplitudeEnvelope.getAttack (), 0, 10);
        setEnvelopeAttribute (element, DecentSamplerTag.AMP_ENV_DECAY, amplitudeEnvelope.getDecay (), 0, 25);
        setEnvelopeAttribute (element, DecentSamplerTag.AMP_ENV_SUSTAIN, amplitudeEnvelope.getSustain (), 0, 1);
        setEnvelopeAttribute (element, DecentSamplerTag.AMP_ENV_RELEASE, amplitudeEnvelope.getRelease (), 0, 25);
    }


    private static double getEnvelopeAttribute (final double value, final double minimum, final double maximum, final double defaultValue)
    {
        return value < 0 ? defaultValue : normalizeValue (value, minimum, maximum);
    }


    private static void setEnvelopeAttribute (final Element element, final String attribute, final double value, final double minimum, final double maximum)
    {
        if (value >= 0)
            XMLUtils.setDoubleAttribute (element, attribute, normalizeValue (value, minimum, maximum), 3);
    }
}