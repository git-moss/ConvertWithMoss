// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (true, false, false, false);


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
        this.writeSamples (sampleFolder, multisampleSource, DESTINATION_FORMAT);
    }


    /**
     * Create the text of the description file.
     *
     * @param folderName The name to use for the sample folder
     * @param multisampleSource The multi-sample
     * @return The XML structure
     * @throws IOException Could not create the metadata
     */
    private Optional<String> createMetadata (final String folderName, final IMultisampleSource multisampleSource) throws IOException
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

        final List<IGroup> groups = this.optimizeGroups (multisampleSource.getNonEmptyGroups (true));
        addModulationAttributes (document, groups, programElement, multisampleSource.getGlobalFilter ());

        // Add up to 4 groups
        int groupCounter = 0;
        for (final IGroup group: groups)
        {
            final Element groupElement = XMLUtils.addElement (document, programElement, TALSamplerTag.SAMPLE_LAYER + groupCounter);
            final Element multisamplesElement = XMLUtils.addElement (document, groupElement, TALSamplerTag.MULTISAMPLES);

            programElement.setAttribute (TALSamplerTag.PROGRAM_LAYER_ON + TALSamplerConstants.LAYERS[groupCounter], "1.0");

            // No group name and trigger types

            for (final ISampleZone sample: group.getSampleZones ())
                createSample (document, folderName, programElement, groupCounter, multisamplesElement, sample);

            groupCounter++;
            if (groupCounter == 4)
                break;
        }

        return this.createXMLString (document);
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param folderName The name to use for the sample folder
     * @param programElement The program element
     * @param groupCounter The index of the group
     * @param groupElement The element where to add the sample information
     * @param zone Where to get the sample info from
     */
    private static void createSample (final Document document, final String folderName, final Element programElement, final int groupCounter, final Element groupElement, final ISampleZone zone)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, groupElement, TALSamplerTag.MULTISAMPLE);
        sampleElement.setAttribute (TALSamplerTag.MULTISAMPLE_URL, AbstractCreator.formatFileName (folderName, zone.getName () + ".wav"));

        final double gain = zone.getGain ();
        if (gain != 0)
        {
            // Not sure if this is correct or if proper dB conversion would need to be applied...
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.VOLUME, convertGain (gain), 6);
        }
        XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.PANORAMA, (zone.getPanorama () + 1.0) / 2.0, 2);

        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.START_SAMPLE, Math.max (0, zone.getStart ()));
        final int stop = zone.getStop ();
        if (stop >= 0)
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.END_SAMPLE, stop);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.REVERSE, zone.isReversed () ? 1 : 0);

        // transpose // tune in semitones = floor((48.0f * transpose + 0.5f) - 24.0f)
        final double tune = zone.getTune ();
        if (tune != 0)
        {
            // transpose and de-tune are both +-24 semitones, fine tuning is set on the program with
            // +-100 cent

            final int transpose = (int) tune;
            final double fine = tune - transpose;
            int detune = 0;
            if (transpose > 24 || transpose < -24)
                detune = MathUtils.clamp (transpose > 24 ? transpose - 24 : transpose + 24, -24, 24);
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.TRANSPOSE, (transpose + 24.0) / 48.0, 4);
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.DETUNE, (detune + 24.0) / 48.0, 4);
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.SAMPLE_FINE_TUNE + TALSamplerConstants.LAYERS[groupCounter], (fine + 1.0) / 2.0, 4);
        }

        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.PITCH_KEY_TRACK, zone.getKeyTracking () > 0 ? 1 : 0);

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.ROOT_NOTE, zone.getKeyRoot ());
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LO_NOTE, check (zone.getKeyLow (), 0));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.HI_NOTE, check (zone.getKeyHigh (), 127));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LO_VEL, check (zone.getVelocityLow (), 0));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.HI_VEL, check (zone.getVelocityHigh (), 127));

        // No note and velocity crossfades

        /////////////////////////////////////////////////////
        // Loops

        final List<ISampleLoop> loops = zone.getLoops ();
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


    private static void addModulationAttributes (final Document document, final List<IGroup> groups, final Element programElement, final Optional<IFilter> optFilter) throws IOException
    {
        if (groups.isEmpty ())
            return;

        final ISampleZone sampleMetadata = groups.get (0).getSampleZones ().get (0);

        // Pitchbend 2 semitones up/down
        final int bendUp = Math.abs (sampleMetadata.getBendUp ());
        final double bendUpValue = bendUp == 0 ? 0.16 : MathUtils.clamp (bendUp / 1200.0, 0, 1.0);
        XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.PITCHBEND_RANGE, bendUpValue, 3);

        final double maxEnvelopeTime = TALSamplerConstants.getMediumSampleLength (groups);

        // Add amplitude envelope
        final IEnvelope amplitudeEnvelope = sampleMetadata.getAmplitudeModulator ().getSource ();
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_ATTACK, amplitudeEnvelope.getAttack (), 0, maxEnvelopeTime);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_HOLD, amplitudeEnvelope.getHold (), 0, maxEnvelopeTime);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_DECAY, amplitudeEnvelope.getDecay (), 0, maxEnvelopeTime);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_SUSTAIN, amplitudeEnvelope.getSustain (), 0, 1);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_RELEASE, amplitudeEnvelope.getRelease (), 0, maxEnvelopeTime);

        // Add filter settings
        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();

            // Enable the filter on all 4 velocity layers
            for (int i = 0; i < 4; i++)
                programElement.setAttribute (TALSamplerTag.FILTER_LAYER_ON + TALSamplerConstants.LAYERS[i], "1.0");

            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_MODE, TALSamplerConstants.getFilterValue (filter), 16);

            final double cutoff = MathUtils.normalizeCutoff (filter.getCutoff ());
            final double resonance = MathUtils.normalize (filter.getResonance (), 40.0);
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_CUTOFF, cutoff, 4);
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_RESONANCE, resonance, 4);

            final IModulator cutoffModulator = filter.getCutoffModulator ();
            final double filterModDepth = cutoffModulator.getDepth ();
            if (filterModDepth > 0)
            {
                final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_ATTACK, filterEnvelope.getAttack (), 0, maxEnvelopeTime);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_HOLD, filterEnvelope.getHold (), 0, maxEnvelopeTime);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_DECAY, filterEnvelope.getDecay (), 0, maxEnvelopeTime);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_SUSTAIN, filterEnvelope.getSustain (), 0, 1);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_RELEASE, filterEnvelope.getRelease (), 0, maxEnvelopeTime);

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
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_ATTACK, pitchEnvelope.getAttack (), 0, maxEnvelopeTime);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_HOLD, pitchEnvelope.getHold (), 0, maxEnvelopeTime);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_DECAY, pitchEnvelope.getDecay (), 0, maxEnvelopeTime);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_SUSTAIN, pitchEnvelope.getSustain (), 0, 1);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_RELEASE, pitchEnvelope.getRelease (), 0, maxEnvelopeTime);

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
            XMLUtils.setDoubleAttribute (element, attribute, MathUtils.normalize (value, minimum, maximum), 3);
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


    /**
     * Tries to integrate the given groups with their samples into up to 4 groups and distribute the
     * samples across the groups in such a way that the key and velocity splits do not overlap due
     * to the limitations of TAL Sampler. Groups have only the name and trigger type as attributes
     * which are not supported in TAL Sampler anyway.
     *
     * @param groups The groups to optimize
     * @return The optimized groups
     */
    private List<IGroup> optimizeGroups (final List<IGroup> groups)
    {
        final List<IGroup> optimizedGroups = new ArrayList<> ();
        for (final IGroup group: groups)
        {
            for (final ISampleZone zone: group.getSampleZones ())
                this.findMatchingGroup (optimizedGroups, zone).addSampleMetadata (zone);
        }
        return optimizedGroups;
    }


    /**
     * Check if there is a group among the optimized groups to which the sample can be added. If
     * none can be found and there are less than 4 groups a new group is created and added otherwise
     * the 1st group is returned and a warning is logged.
     *
     * @param optimizedGroups The optimized groups
     * @param zone The sample zone to test for
     * @return The group to which the sample should be added
     */
    private IGroup findMatchingGroup (final List<IGroup> optimizedGroups, final ISampleZone zone)
    {
        for (final IGroup optGroup: optimizedGroups)
        {
            if (!hasOverlappingSample (optGroup, zone))
                return optGroup;
        }
        if (optimizedGroups.size () < 4)
        {
            final IGroup group = new DefaultGroup ();
            optimizedGroups.add (group);
            return group;
        }
        this.notifier.logError ("IDS_TAL_OVERLAPPING_SAMPLES", zone.getName ());
        return optimizedGroups.get (0);
    }


    /**
     * Test if the samples of the given group has an overlapping key/velocity range with the given
     * sample.
     *
     * @param group The group which contains the samples to test
     * @param sampleMetadata The sample to test with
     * @return True if it has an overlapping sample
     */
    private static boolean hasOverlappingSample (final IGroup group, final ISampleZone sampleMetadata)
    {
        final int keyLow = sampleMetadata.getKeyLow ();
        final int keyHigh = sampleMetadata.getKeyHigh ();
        final int velLow = sampleMetadata.getVelocityLow ();
        final int velHigh = sampleMetadata.getVelocityHigh ();

        for (final ISampleZone otherSampleMetadata: group.getSampleZones ())
        {
            final int keyLow2 = otherSampleMetadata.getKeyLow ();
            final int keyHigh2 = otherSampleMetadata.getKeyHigh ();
            if (keyLow2 >= keyLow && keyLow2 <= keyHigh || keyHigh2 >= keyLow && keyHigh2 <= keyHigh)
            {
                final int velLow2 = otherSampleMetadata.getVelocityLow ();
                final int velHigh2 = otherSampleMetadata.getVelocityHigh ();
                if (velLow2 >= velLow && velLow2 <= velHigh || velHigh2 >= velLow && velHigh2 <= velHigh)
                    return true;
            }
        }
        return false;
    }
}