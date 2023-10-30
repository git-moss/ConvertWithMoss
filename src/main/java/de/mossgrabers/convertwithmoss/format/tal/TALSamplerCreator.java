// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.Utils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.TransformerException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;


/**
 * Creator for TAL Sampler multi-sample files. A talsmpl file has a description file encoded in XML.
 * The related samples are in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerCreator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TALSamplerCreator (final INotifier notifier)
    {
        super ("TAL Sampler", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = new File (destinationFolder, sampleName + ".talsmpl");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        final String relativeFolderName = sampleName + " " + TALSamplerConstants.FOLDER_POSTFIX;

        final Optional<String> metadata = this.createMetadata (relativeFolderName, multisampleSource);
        if (metadata.isEmpty ())
            return;

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.storePreset (relativeFolderName, destinationFolder, multisampleSource, multiFile, metadata.get ());

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a TAL sampler file.
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

        final Element rootElement = document.createElement (TALSamplerTag.ROOT);
        document.appendChild (rootElement);
        rootElement.setAttribute (TALSamplerTag.ROOT_CUR_PROGRAM, "0");
        rootElement.setAttribute (TALSamplerTag.ROOT_VERSION, TALSamplerConstants.CURRENT_VERSION);

        // No metadata at all, except program name

        final Element programsElement = XMLUtils.addElement (document, rootElement, TALSamplerTag.PROGRAMS);
        final Element programElement = XMLUtils.addElement (document, programsElement, TALSamplerTag.PROGRAM);
        programElement.setAttribute (TALSamplerTag.PROGRAM_NAME, multisampleSource.getName ());
        programElement.setAttribute (TALSamplerTag.PROGRAM_NUM_VOICES, "1.0");

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        addModulationAttributes (document, groups, programElement, multisampleSource.getGlobalFilter ());

        // Add all groups

        // TODO maximum of 4 -> split up in programs ?!
        int groupCounter = 0;

        for (final IGroup group: groups)
        {
            final Element groupElement = XMLUtils.addElement (document, programElement, TALSamplerTag.SAMPLE_LAYER + groupCounter);
            final Element multisamplesElement = XMLUtils.addElement (document, groupElement, TALSamplerTag.MULTISAMPLES);

            programElement.setAttribute (TALSamplerTag.PROGRAM_LAYER_ON + TALSamplerConstants.LAYERS[groupCounter], "1.0");

            // No group name and trigger types

            for (final ISampleMetadata sample: group.getSampleMetadata ())
                createSample (document, folderName, programElement, groupCounter, multisamplesElement, sample);

            groupCounter++;
            if (groupCounter == 4)
                break;
        }

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


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param folderName The name to use for the sample folder
     * @param programElement The program element
     * @param groupCounter The index of the group
     * @param groupElement The element where to add the sample information
     * @param info Where to get the sample info from
     */
    private static void createSample (final Document document, final String folderName, final Element programElement, final int groupCounter, final Element groupElement, final ISampleMetadata info)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, groupElement, TALSamplerTag.MULTISAMPLE);
        final Optional<String> filename = info.getUpdatedFilename ();
        if (filename.isPresent ())
            sampleElement.setAttribute (TALSamplerTag.MULTISAMPLE_URL, AbstractCreator.formatFileName (folderName, filename.get ()));

        final double gain = info.getGain ();
        if (gain != 0)
        {
            // Not sure if this is correct or if proper dB conversion would need to be applied...
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.VOLUME, convertGain (gain), 6);
        }
        XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.PANORAMA, (info.getPanorama () + 1.0) / 2.0, 2);

        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.START_SAMPLE, Math.max (0, info.getStart ()));
        final int stop = info.getStop ();
        if (stop >= 0)
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.END_SAMPLE, stop);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.REVERSE, info.isReversed () ? 1 : 0);

        // transpose // tune in semitones = floor((48.0f * transpose + 0.5f) - 24.0f)
        final double tune = info.getTune ();
        if (tune != 0)
        {
            // transpose and de-tune are both +-24 semitones, fine tuning is set on the program with
            // +-100 cent

            int transpose = (int) tune;
            final double fine = tune - transpose;
            int detune = 0;
            if (transpose > 24 || transpose < -24)
                detune = Utils.clamp (transpose > 24 ? transpose - 24 : transpose + 24, -24, 24);
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.TRANSPOSE, (transpose + 24.0) / 48.0, 4);
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.DETUNE, (detune + 24.0) / 48.0, 4);
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.SAMPLE_FINE_TUNE + TALSamplerConstants.LAYERS[groupCounter], (fine + 1.0) / 2.0, 4);
        }

        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.PITCH_KEY_TRACK, info.getKeyTracking () > 0 ? 1 : 0);

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.ROOT_NOTE, info.getKeyRoot ());
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LO_NOTE, check (info.getKeyLow (), 0));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.HI_NOTE, check (info.getKeyHigh (), 127));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LO_VEL, check (info.getVelocityLow (), 0));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.HI_VEL, check (info.getVelocityHigh (), 127));

        // No note and velocity crossfades

        /////////////////////////////////////////////////////
        // Loops

        final List<ISampleLoop> loops = info.getLoops ();
        if (loops.isEmpty ())
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ENABLED, 0);
        else
        {
            final ISampleLoop sampleLoop = loops.get (0);
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ENABLED, 1);
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_START, check (sampleLoop.getStart (), 0));
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_END, check (sampleLoop.getEnd (), stop));

            // No loop crossfade

            final LoopType type = sampleLoop.getType ();
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ALTERNATE, type == LoopType.ALTERNATING ? 1 : 0);
        }

        ////////////////////////////////////////////////////
        // Static not relevant attributes

        XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.FADE_IN_SAMPLES, 0, 1);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.IS_ROM_SAMPLE, 0);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.SLICE, 0);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.PHASE_INVERSE, 0);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.STEREO_INVERSE, 1);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.MUTE_GROUP, 0);
    }


    private static void addModulationAttributes (final Document document, final List<IGroup> groups, final Element programElement, final Optional<IFilter> optFilter)
    {
        if (groups.isEmpty ())
            return;

        final ISampleMetadata sampleMetadata = groups.get (0).getSampleMetadata ().get (0);

        // Pitchbend 2 semitones up/down
        final int bendUp = Math.abs (sampleMetadata.getBendUp ());
        final double bendUpValue = bendUp == 0 ? 0.16 : Utils.clamp (bendUp / 1200.0, 0, 1.0);
        XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.PITCHBEND_RANGE, bendUpValue, 3);

        // Add amplitude envelope
        // TODO maximum values are likely not correct
        final IEnvelope amplitudeEnvelope = sampleMetadata.getAmplitudeModulator ().getSource ();
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_ATTACK, amplitudeEnvelope.getAttack (), 0, 10);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_HOLD, amplitudeEnvelope.getHold (), 0, 10);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_DECAY, amplitudeEnvelope.getDecay (), 0, 8);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_SUSTAIN, amplitudeEnvelope.getSustain (), 0, 1);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_RELEASE, amplitudeEnvelope.getRelease (), 0, 8);

        // Add filter settings
        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();

            // Enable the filter on all 4 velocity layers
            for (int i = 0; i < 4; i++)
                programElement.setAttribute (TALSamplerTag.FILTER_LAYER_ON + TALSamplerConstants.LAYERS[i], "1.0");

            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_MODE, TALSamplerConstants.getFilterValue (filter), 16);

            // TODO values are likely not correct
            final double cutoff = filter.getCutoff () / IFilter.MAX_FREQUENCY;
            final double resonance = Math.min (40.0, filter.getResonance ()) / 40.0;
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_CUTOFF, cutoff, 4);
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_RESONANCE, resonance, 4);

            final IModulator cutoffModulator = filter.getCutoffModulator ();
            final double filterModDepth = cutoffModulator.getDepth ();
            if (filterModDepth > 0)
            {
                // TODO maximum values are likely not correct
                final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_ATTACK, filterEnvelope.getAttack (), 0, 10);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_HOLD, filterEnvelope.getHold (), 0, 10);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_DECAY, filterEnvelope.getDecay (), 0, 8);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_SUSTAIN, filterEnvelope.getSustain (), 0, 1);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_RELEASE, filterEnvelope.getRelease (), 0, 8);

                XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_ENVELOPE, filterModDepth / IFilter.MAX_ENVELOPE_DEPTH, 4);

                // TALSamplerTag.FILTER_KEYBOARD not supported
            }
        }

        // Add pitch envelope
        final IModulator pitchModulator = sampleMetadata.getPitchModulator ();
        final double pitchModDepth = pitchModulator.getDepth ();
        if (pitchModDepth > 0)
        {
            final IEnvelope pitchEnvelope = pitchModulator.getSource ();
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_ATTACK, pitchEnvelope.getAttack (), 0, 10);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_HOLD, pitchEnvelope.getHold (), 0, 10);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_DECAY, pitchEnvelope.getDecay (), 0, 8);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_SUSTAIN, pitchEnvelope.getSustain (), 0, 1);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_RELEASE, pitchEnvelope.getRelease (), 0, 8);

            // Envelope 3 needs to be set to modulate the global pitch
            final Element modMatrixElement = XMLUtils.addElement (document, programElement, "modmatrix");
            Element entryElement = XMLUtils.addElement (document, modMatrixElement, "entry");
            entryElement.setAttribute ("parameterid", "164");
            entryElement.setAttribute ("modmatrixsourceid", "2");
            entryElement.setAttribute ("modmatrixamount", "1.0");
            for (int i = 0; i < 9; i++)
            {
                entryElement = XMLUtils.addElement (document, modMatrixElement, "entry");
                entryElement.setAttribute ("parameterid", "-1");
                entryElement.setAttribute ("modmatrixsourceid", "0");
                entryElement.setAttribute ("modmatrixamount", "0.5");
            }
        }
    }


    private static void setEnvelopeAttribute (final Element element, final String attribute, final double value, final double minimum, final double maximum)
    {
        if (value >= 0)
            XMLUtils.setDoubleAttribute (element, attribute, normalizeValue (value, minimum, maximum), 3);
    }


    /**
     * Convert a volume in the range of [-12dB..12dB] to a range of [0..1] which represent
     * [-Inf..6dB].
     *
     * @param volumeDB The volume to convert
     * @return The converted volume
     */
    private static double convertGain (final double volumeDB)
    {
        final double v = 12 + (volumeDB > 6 ? 6 : volumeDB);
        final double result = TALSamplerConstants.VALUE_RANGE * v / 18.0;
        return TALSamplerConstants.MINUS_12_DB + result;
    }
}