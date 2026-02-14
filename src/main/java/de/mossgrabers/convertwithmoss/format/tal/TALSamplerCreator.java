// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.XMLUtils;


/**
 * Creator for TAL Sampler multi-sample files. A talsmpl file has a description file encoded in XML.
 * The related samples are in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerCreator extends AbstractWavCreator<WavChunkSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TALSamplerCreator (final INotifier notifier)
    {
        super ("TAL Sampler", "TALSampler", notifier, new WavChunkSettingsUI ("TALSampler", true, false, false, false));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final String relativeFolderName = sampleName + FOLDER_POSTFIX;

        final Optional<String> metadata = this.createMetadata (relativeFolderName, multisampleSource);
        if (metadata.isEmpty ())
            return;

        final File multiFile = this.createUniqueFilename (destinationFolder, sampleName, "talsmpl");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.storePreset (relativeFolderName, destinationFolder, multisampleSource, multiFile, metadata.get ());

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a TAL sampler file.
     *
     * @param relativeFolderName A relative path for the samples
     * @param destinationFolder Where to store the preset file
     * @param multisampleSource The multi-sample to store in the library
     * @param multiFile The output file
     * @param metadata The metadata description file
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

        // Add up to 4 groups
        int groupCounter = 0;
        final List<IGroup> groups = this.optimizeGroups (multisampleSource.getNonEmptyGroups (true));
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

        addModulationAttributes (document, groups, programElement, multisampleSource.getGlobalFilter ());

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
            // Not sure if this is correct or if proper dB conversion would need to be applied...
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.VOLUME, convertGain (gain), 6);
        XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.PANNING, (zone.getPanning () + 1.0) / 2.0, 2);

        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.START_SAMPLE, Math.max (0, zone.getStart ()));
        final int stop = zone.getStop ();
        if (stop >= 0)
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.END_SAMPLE, stop);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.REVERSE, zone.isReversed () ? 1 : 0);

        // transpose // tune in semi-tones = floor((48.0f * transpose + 0.5f) - 24.0f)
        final double tune = zone.getTuning ();
        if (tune != 0)
        {
            // transpose and de-tune are both +-24 semi-tones, fine tuning is set on the program
            // with +-100 cent

            final int transpose = (int) tune;
            final double fine = tune - transpose;
            int detune = 0;
            if (transpose > 24 || transpose < -24)
                detune = Math.clamp (transpose > 24 ? transpose - 24 : transpose + 24, -24, 24);
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.TRANSPOSE, (transpose + 24.0) / 48.0, 4);
            XMLUtils.setDoubleAttribute (sampleElement, TALSamplerTag.DETUNE, (detune + 24.0) / 48.0, 4);
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.SAMPLE_FINE_TUNE + TALSamplerConstants.LAYERS[groupCounter], (fine + 1.0) / 2.0, 4);
        }

        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.PITCH_KEY_TRACK, zone.getKeyTracking () > 0 ? 1 : 0);

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        final int keyLow = limitToDefault (zone.getKeyLow (), 0);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.ROOT_NOTE, limitToDefault (zone.getKeyRoot (), keyLow));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LO_NOTE, keyLow);
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.HI_NOTE, limitToDefault (zone.getKeyHigh (), 127));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LO_VEL, limitToDefault (zone.getVelocityLow (), 1));
        XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.HI_VEL, limitToDefault (zone.getVelocityHigh (), 127));

        // No note and velocity cross-fades

        /////////////////////////////////////////////////////
        // Loops

        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ENABLED, 0);
        else
        {
            final ISampleLoop sampleLoop = loops.get (0);
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ENABLED, 1);
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_START, limitToDefault (sampleLoop.getStart (), 0));
            XMLUtils.setIntegerAttribute (sampleElement, TALSamplerTag.LOOP_END, limitToDefault (sampleLoop.getEnd (), stop));

            // No loop cross-fade

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

        final ISampleZone zone = groups.get (0).getSampleZones ().get (0);
        final List<TALSamplerModulator> modulators = new ArrayList<> (10);

        // Pitch-bend
        final int bendUp = Math.abs (zone.getBendUp ());
        final double bendUpValue = bendUp == 0 ? 0.16 : Math.clamp (bendUp / 1200.0, 0, 1.0);
        XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.PITCHBEND_RANGE, bendUpValue, 3);

        final double maxEnvelopeTime = TALSamplerConstants.getMediumSampleLength (groups);

        //////////////////////////////////////////////////
        // Amplitude

        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_ATTACK, amplitudeEnvelope.getAttackTime (), 0, maxEnvelopeTime);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_HOLD, amplitudeEnvelope.getHoldTime (), 0, maxEnvelopeTime);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_DECAY, amplitudeEnvelope.getDecayTime (), 0, maxEnvelopeTime);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_SUSTAIN, amplitudeEnvelope.getSustainLevel (), 0, 1);
        setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_RELEASE, amplitudeEnvelope.getReleaseTime (), 0, maxEnvelopeTime);

        final double ampModDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
        if (ampModDepth != 0)
            modulators.add (new TALSamplerModulator (TALSamplerModulator.SOURCE_ID_VELOCITY, TALSamplerModulator.DEST_ID_VOLUME_A, ampModDepth));

        //////////////////////////////////////////////////
        // Filter

        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();

            // Enable the filter on all 4 velocity layers
            for (int i = 0; i < 4; i++)
                programElement.setAttribute (TALSamplerTag.FILTER_LAYER_ON + TALSamplerConstants.LAYERS[i], "1.0");

            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_MODE, TALSamplerConstants.getFilterValue (filter), 16);

            final double cutoff = MathUtils.normalizeCutoff (filter.getCutoff ());
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_CUTOFF, cutoff, 4);
            XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_RESONANCE, filter.getResonance (), 4);

            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            final double filterModDepth = cutoffModulator.getDepth ();
            if (filterModDepth > 0)
            {
                final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_ATTACK, filterEnvelope.getAttackTime (), 0, maxEnvelopeTime);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_HOLD, filterEnvelope.getHoldTime (), 0, maxEnvelopeTime);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_DECAY, filterEnvelope.getDecayTime (), 0, maxEnvelopeTime);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_SUSTAIN, filterEnvelope.getSustainLevel (), 0, 1);
                setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_RELEASE, filterEnvelope.getReleaseTime (), 0, maxEnvelopeTime);

                XMLUtils.setDoubleAttribute (programElement, TALSamplerTag.FILTER_ENVELOPE, filterModDepth, 4);

                // TALSamplerTag.FILTER_KEYBOARD not supported
            }

            final double cutoffModDepth = filter.getCutoffVelocityModulator ().getDepth ();
            if (cutoffModDepth != 0)
                modulators.add (new TALSamplerModulator (TALSamplerModulator.SOURCE_ID_VELOCITY, TALSamplerModulator.DEST_ID_CUTOFF, cutoffModDepth));
        }

        //////////////////////////////////////////////////
        // Pitch

        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
        final double pitchModDepth = pitchModulator.getDepth ();
        if (pitchModDepth > 0)
        {
            final IEnvelope pitchEnvelope = pitchModulator.getSource ();
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_ATTACK, pitchEnvelope.getAttackTime (), 0, maxEnvelopeTime);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_HOLD, pitchEnvelope.getHoldTime (), 0, maxEnvelopeTime);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_DECAY, pitchEnvelope.getDecayTime (), 0, maxEnvelopeTime);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_SUSTAIN, pitchEnvelope.getSustainLevel (), 0, 1);
            setEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_RELEASE, pitchEnvelope.getReleaseTime (), 0, maxEnvelopeTime);

            // Envelope 3 needs to be set to modulate the global pitch
            modulators.add (new TALSamplerModulator (TALSamplerModulator.SOURCE_ID_ENV3, TALSamplerModulator.DEST_ID_MASTER_TUNE, pitchModDepth));
        }

        // Create modulator matrix
        while (modulators.size () != 10)
            modulators.add (new TALSamplerModulator ());
        final Element modMatrixElement = XMLUtils.addElement (document, programElement, TALSamplerTag.MOD_MATRIX);
        for (final TALSamplerModulator modulator: modulators)
            modulator.createModElements (document, modMatrixElement);
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
            for (final ISampleZone zone: group.getSampleZones ())
                this.findMatchingGroup (optimizedGroups, zone).addSampleZone (zone);
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
            if (!hasOverlappingSample (optGroup, zone))
                return optGroup;
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
        final int keyLow = limitToDefault (sampleMetadata.getKeyLow (), 0);
        final int keyHigh = limitToDefault (sampleMetadata.getKeyHigh (), 127);
        final int velLow = limitToDefault (sampleMetadata.getVelocityLow (), 1);
        final int velHigh = limitToDefault (sampleMetadata.getVelocityHigh (), 127);

        for (final ISampleZone otherSampleMetadata: group.getSampleZones ())
        {
            final int keyLow2 = limitToDefault (otherSampleMetadata.getKeyLow (), 0);
            final int keyHigh2 = limitToDefault (otherSampleMetadata.getKeyHigh (), 127);
            if (keyLow2 >= keyLow && keyLow2 <= keyHigh || keyHigh2 >= keyLow && keyHigh2 <= keyHigh)
            {
                final int velLow2 = limitToDefault (otherSampleMetadata.getVelocityLow (), 1);
                final int velHigh2 = limitToDefault (otherSampleMetadata.getVelocityHigh (), 127);
                if (velLow2 >= velLow && velLow2 <= velHigh || velHigh2 >= velLow && velHigh2 <= velHigh)
                    return true;
            }
        }
        return false;
    }
}