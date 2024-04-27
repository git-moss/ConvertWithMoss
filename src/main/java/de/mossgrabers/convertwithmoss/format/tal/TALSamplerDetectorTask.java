// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively TAL Sampler files in folders. Files must end with <i>.talsmpl</i>.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerDetectorTask extends AbstractDetectorTask
{
    private static final String IDS_NOTIFY_ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadataConfig Additional metadata configuration parameters
     */
    protected TALSamplerDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadataConfig)
    {
        super (notifier, consumer, sourceFolder, metadataConfig, ".talsmpl");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final String content = this.loadTextFile (file).trim ();
            return this.parseMetadataFile (file, content);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The file
     * @param content The content of the file
     * @return The result
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String content)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseDescription (multiSampleFile, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE, ex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex, false);
        }
        return Collections.emptyList ();
    }


    /**
     * Process the TAL Sampler metadata file and the related wave files.
     *
     * @param multiSampleFile The multi-sample file
     * @param document The metadata XML document
     * @return The parsed multi-sample source
     * @throws IOException Could not parse the description
     */
    private List<IMultisampleSource> parseDescription (final File multiSampleFile, final Document document) throws IOException
    {
        final Element top = document.getDocumentElement ();

        if (!TALSamplerTag.ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final Element programsElement = XMLUtils.getChildElementByName (top, TALSamplerTag.PROGRAMS);
        if (programsElement == null)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (final Element programElement: XMLUtils.getChildElementsByName (programsElement, TALSamplerTag.PROGRAM, false))
        {
            final String name = programElement.getAttribute (TALSamplerTag.PROGRAM_NAME);
            if (name.isBlank ())
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_NO_NAME");
                continue;
            }
            final File parentFolder = multiSampleFile.getParentFile ();
            final String [] parts = AudioFileUtils.createPathParts (parentFolder, this.sourceFolder, name);

            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));

            // Parse all groups
            final List<IGroup> groups = new ArrayList<> (4);
            for (int groupCounter = 0; groupCounter < 4; groupCounter++)
                // Group is disabled?
                if (XMLUtils.getIntegerAttribute (programElement, TALSamplerTag.PROGRAM_LAYER_ON + TALSamplerConstants.LAYERS[groupCounter], 0) == 1)
                {
                    final Element groupElement = XMLUtils.getChildElementByName (programElement, TALSamplerTag.SAMPLE_LAYER + groupCounter);
                    if (groupElement != null)
                    {
                        final IGroup group = new DefaultGroup ();
                        group.setName ("Group " + (groupCounter + 1));
                        for (final Element layerElement: XMLUtils.getChildElementsByName (groupElement, TALSamplerTag.MULTISAMPLES, false))
                            for (final Element sampleElement: XMLUtils.getChildElementsByName (layerElement, TALSamplerTag.MULTISAMPLE, false))
                            {
                                final ISampleZone sampleZone = this.parseSample (parentFolder, programElement, groupCounter, sampleElement);
                                if (sampleZone != null)
                                    group.addSampleZone (sampleZone);
                            }
                        groups.add (group);
                    }
                }

            this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (groups), parts);
            multisampleSource.setGroups (groups);

            final Optional<IFilter> optFilter = parseModulationAttributes (programElement, multisampleSource);
            if (optFilter.isPresent ())
                multisampleSource.setGlobalFilter (optFilter.get ());

            multisampleSources.add (multisampleSource);
        }

        return multisampleSources;
    }


    /**
     * Parse the sample information.
     *
     * @param parentFolder The parent folder which contains the sample
     * @param programElement The program element
     * @param groupCounter The index of the group
     * @param sampleElement The XML sample element
     * @return The created sample metadata
     * @throws IOException Could not create the sample metadata
     */
    private ISampleZone parseSample (final File parentFolder, final Element programElement, final int groupCounter, final Element sampleElement) throws IOException
    {
        final String filename = sampleElement.getAttribute (TALSamplerTag.MULTISAMPLE_URL);
        if (filename == null || filename.isBlank ())
        {
            // Notify but do not crash
            this.notifier.logError ("IDS_NOTIFY_ERR_NO_SAMPLE_FILE");
            return null;
        }

        if (filename.endsWith (".talwav"))
            throw new IOException (Functions.getMessage ("IDS_TAL_ENCRYPTED_SAMPLES_NOT_SUPPORTED", filename));

        if (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.IS_ROM_SAMPLE, 0) == 1)
            throw new IOException (Functions.getMessage ("IDS_TAL_ROM_SAMPLES_NOT_SUPPORTED", filename));

        final ISampleZone zone = this.createSampleZone (new File (parentFolder, filename));

        zone.setGain (convertGain (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.VOLUME, 0)));
        zone.setPanorama (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.PANORAMA, 0.5) * 2.0 - 1.0);

        zone.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.START_SAMPLE, -1)));
        zone.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.END_SAMPLE, -1)));
        zone.setReversed (XMLUtils.getBooleanAttribute (sampleElement, TALSamplerTag.REVERSE, false));

        final double layerTranspose = Math.round (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.LAYER_TRANSPOSE + TALSamplerConstants.LAYERS[groupCounter], 0.5) * 48.0 - 24.0);
        final double sampleTune = Math.round (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.SAMPLE_TUNE + TALSamplerConstants.LAYERS[groupCounter], 0.5) * 48.0 - 24.0);
        final double sampleFine = XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.SAMPLE_FINE_TUNE + TALSamplerConstants.LAYERS[groupCounter], 0.5) * 2.0 - 1.0;
        final double transpose = Math.round (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.TRANSPOSE, 0.5) * 48.0 - 24.0);
        final double detune = Math.round (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.DETUNE, 0.5) * 48.0 - 24.0);
        zone.setTune (layerTranspose + sampleTune + transpose + detune + sampleFine);
        zone.setKeyTracking (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.PITCH_KEY_TRACK, 1));

        zone.setKeyRoot (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.ROOT_NOTE, -1));
        zone.setKeyLow (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LO_NOTE, -1));
        zone.setKeyHigh (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.HI_NOTE, -1));
        zone.setVelocityLow (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LO_VEL, -1));
        zone.setVelocityHigh (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.HI_VEL, -1));

        // No note and velocity crossfades

        if (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ENABLED, 0) == 1)
        {
            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            if (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ALTERNATE, 0) == 1)
                loop.setType (LoopType.ALTERNATING);
            loop.setStart (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LOOP_START, -1));
            loop.setEnd (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LOOP_END, -1));
            zone.addLoop (loop);
        }

        zone.getSampleData ().addZoneData (zone, false, false);
        return zone;
    }


    private static Optional<IFilter> parseModulationAttributes (final Element programElement, final DefaultMultisampleSource multisampleSource) throws IOException
    {
        // Pitch-bend
        final int bend = (int) MathUtils.clamp (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.PITCHBEND_RANGE, 1.0) * 1200.0, 0.0, 1200.0);

        final double maxEnvelopeTime = TALSamplerConstants.getMediumSampleLength (multisampleSource.getGroups ());

        // Add amplitude envelope
        final double ampAttach = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_ATTACK, 0, maxEnvelopeTime, 0);
        final double ampHold = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_HOLD, 0, maxEnvelopeTime, 0);
        final double ampDecay = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_DECAY, 0, maxEnvelopeTime, 0);
        final double ampSustain = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_SUSTAIN, 0, 1, 1);
        final double ampRelease = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_AMP_RELEASE, 0, maxEnvelopeTime, 0);

        // Get filter settings
        // We only have a global filter, therefore take only values from the 1st layer
        Optional<IFilter> optFilter = Optional.empty ();
        if (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.FILTER_LAYER_ON + TALSamplerConstants.LAYERS[0], 0) > 0)
        {
            final Optional<IFilter> filterType = TALSamplerConstants.getFilterType (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.FILTER_MODE, 0));
            if (filterType.isPresent ())
            {
                final IFilter baseFilter = filterType.get ();

                final double cutoff = MathUtils.denormalizeCutoff (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.FILTER_CUTOFF, 1.0));
                final double resonance = XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.FILTER_RESONANCE, 0);
                final IFilter filter = new DefaultFilter (baseFilter.getType (), baseFilter.getPoles (), cutoff, resonance);
                optFilter = Optional.of (filter);

                final double filterModDepth = XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.FILTER_ENVELOPE, 0);
                if (filterModDepth > 0)
                {
                    final IModulator cutoffModulator = filter.getCutoffModulator ();
                    cutoffModulator.setDepth (filterModDepth);

                    final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                    filterEnvelope.setAttack (getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_ATTACK, 0, maxEnvelopeTime, 0));
                    filterEnvelope.setHold (getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_HOLD, 0, maxEnvelopeTime, 0));
                    filterEnvelope.setDecay (getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_DECAY, 0, maxEnvelopeTime, 0));
                    filterEnvelope.setSustain (getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_SUSTAIN, 0, 1, 1));
                    filterEnvelope.setRelease (getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_VCF_RELEASE, 0, maxEnvelopeTime, 0));
                }
            }
        }

        // Get pitch (modulation) envelope
        final double pitchAttack = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_ATTACK, 0, maxEnvelopeTime, 0);
        final double pitchHold = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_HOLD, 0, maxEnvelopeTime, 0);
        final double pitchDecay = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_DECAY, 0, maxEnvelopeTime, 0);
        final double pitchSustain = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_SUSTAIN, 0, 1, 1);
        final double pitchRelease = getEnvelopeAttribute (programElement, TALSamplerTag.ADSR_MOD_RELEASE, 0, maxEnvelopeTime, 0);

        // Envelope 3 needs to be set to modulate the global pitch
        final Element modMatrixElement = XMLUtils.getChildElementByName (programElement, "modmatrix");
        double globalPitchEnvelopeDepth = -1;
        if (modMatrixElement != null)
            for (final Element entryElement: XMLUtils.getChildElementsByName (modMatrixElement, "entry", false))
                if (XMLUtils.getIntegerAttribute (entryElement, "parameterid", -1) == 164 && XMLUtils.getIntegerAttribute (entryElement, "modmatrixsourceid", -1) == 2)
                {
                    globalPitchEnvelopeDepth = XMLUtils.getDoubleAttribute (entryElement, "modmatrixamount", 1.0);
                    break;
                }

        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setBendUp (bend);
                zone.setBendDown (bend);

                final IEnvelope amplitudeEnvelope = zone.getAmplitudeModulator ().getSource ();
                amplitudeEnvelope.setAttack (ampAttach);
                amplitudeEnvelope.setHold (ampHold);
                amplitudeEnvelope.setDecay (ampDecay);
                amplitudeEnvelope.setSustain (ampSustain);
                amplitudeEnvelope.setRelease (ampRelease);

                if (globalPitchEnvelopeDepth > 0)
                {
                    final IModulator pitchModulator = zone.getPitchModulator ();
                    pitchModulator.setDepth (globalPitchEnvelopeDepth);

                    final IEnvelope pitchEnvelope = pitchModulator.getSource ();
                    pitchEnvelope.setAttack (pitchAttack);
                    pitchEnvelope.setHold (pitchHold);
                    pitchEnvelope.setDecay (pitchDecay);
                    pitchEnvelope.setSustain (pitchSustain);
                    pitchEnvelope.setRelease (pitchRelease);
                }
            }

        return optFilter;
    }


    private static double getEnvelopeAttribute (final Element element, final String attribute, final double minimum, final double maximum, final double defaultValue)
    {
        final double value = XMLUtils.getDoubleAttribute (element, attribute, defaultValue);
        return denormalizeValue (value, minimum, maximum);
    }


    /**
     * Convert a volume in the range of [0..1] which represent [-Inf..6dB] to a range of
     * [-12dB..12dB].
     *
     * @param volume The volume to convert
     * @return The converted volume DB
     */
    private static double convertGain (final double volume)
    {
        final double result = volume - TALSamplerConstants.MINUS_12_DB;
        return result * 18.0 / TALSamplerConstants.VALUE_RANGE - 12;
    }
}
