// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively TAL Sampler files in folders. Files must end with <i>.talsmpl</i>.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String IDS_NOTIFY_ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TALSamplerDetector (final INotifier notifier)
    {
        super ("TAL Sampler", "TALSampler", notifier, new MetadataSettingsUI ("TALSampler"), ".talsmpl");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
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
     * @param sourceFile The multi-sample file
     * @param document The metadata XML document
     * @return The parsed multi-sample source
     * @throws IOException Could not parse the description
     */
    private List<IMultisampleSource> parseDescription (final File sourceFile, final Document document) throws IOException
    {
        final Element top = document.getDocumentElement ();

        if (!TALSamplerTag.ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Unknown Root");
            return Collections.emptyList ();
        }

        final Element programsElement = XMLUtils.getChildElementByName (top, TALSamplerTag.PROGRAMS);
        if (programsElement == null)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Missing Programs tag");
            return Collections.emptyList ();
        }

        // TAL Sampler lists its presets by file name, so the file name is the name of the preset.
        // The 'programname' attribute is the name the program was saved under, which the producer
        // of a pack often changes by renaming the file afterwards - e.g. to prefix the synthesizer
        // the samples come from. It is only needed to tell the programs of a file apart.
        final String fileName = FileUtils.getNameWithoutType (sourceFile);
        final List<Element> programElements = XMLUtils.getChildElementsByName (programsElement, TALSamplerTag.PROGRAM, false);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (final Element programElement: programElements)
        {
            final String programName = programElement.getAttribute (TALSamplerTag.PROGRAM_NAME);
            final String name = programElements.size () > 1 && !programName.isBlank () ? fileName + " - " + programName : fileName;

            // Parse all groups
            final List<IGroup> groups = new ArrayList<> (4);
            final File parentFolder = sourceFile.getParentFile ();
            for (int groupCounter = 0; groupCounter < 4; groupCounter++)
                // Group is disabled?
                if (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.PROGRAM_LAYER_ON + TALSamplerConstants.LAYERS[groupCounter], 0) > 0)
                {
                    final Element groupElement = XMLUtils.getChildElementByName (programElement, TALSamplerTag.SAMPLE_LAYER + groupCounter);
                    if (groupElement != null)
                    {
                        final IGroup group = new DefaultGroup ();
                        group.setName ("Group " + (groupCounter + 1));
                        for (final Element layerElement: XMLUtils.getChildElementsByName (groupElement, TALSamplerTag.MULTISAMPLES, false))
                            for (final Element sampleElement: XMLUtils.getChildElementsByName (layerElement, TALSamplerTag.MULTISAMPLE, false))
                            {
                                final Optional<ISampleZone> sampleZone = this.parseSample (parentFolder, programElement, groupCounter, sampleElement);
                                if (sampleZone.isPresent ())
                                    group.addSampleZone (sampleZone.get ());
                            }
                        groups.add (group);
                    }
                }

            final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, name, groups);
            multisampleSource.setPolyphony (TALSamplerConstants.denormalizeVoices (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.PROGRAM_NUM_VOICES, 1.0)));
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
    private Optional<ISampleZone> parseSample (final File parentFolder, final Element programElement, final int groupCounter, final Element sampleElement) throws IOException
    {
        final String filename = sampleElement.getAttribute (TALSamplerTag.MULTISAMPLE_URL);
        if (filename == null || filename.isBlank ())
        {
            // Notify but do not crash
            this.notifier.logError ("IDS_NOTIFY_ERR_NO_SAMPLE_FILE");
            return Optional.empty ();
        }

        if (filename.endsWith (".talwav"))
            throw new IOException (Functions.getMessage ("IDS_TAL_ENCRYPTED_SAMPLES_NOT_SUPPORTED", filename));

        if (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.IS_ROM_SAMPLE, 0) == 1)
            throw new IOException (Functions.getMessage ("IDS_TAL_ROM_SAMPLES_NOT_SUPPORTED", filename));

        final ISampleZone zone;
        try
        {
            zone = this.createSampleZone (lookupSampleFile (parentFolder, filename));
        }
        catch (final FileNotFoundException ex)
        {
            this.notifier.logError (ex, false);
            return Optional.empty ();
        }

        // The default is the raw value which represents 0dB
        zone.setGain (convertGain (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.VOLUME, TALSamplerConstants.MINUS_12_DB + TALSamplerConstants.VALUE_RANGE * 12.0 / 18.0)));
        zone.setPanning (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.PANNING, 0.5) * 2.0 - 1.0);

        zone.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.START_SAMPLE, -1)));
        zone.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.END_SAMPLE, -1)));
        // The flag is stored numerically (0/1) like all other TAL flags
        zone.setReversed (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.REVERSE, 0) > 0);

        final double layerTranspose = Math.round (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.LAYER_TRANSPOSE + TALSamplerConstants.LAYERS[groupCounter], 0.5) * 48.0 - 24.0);
        final double sampleTune = Math.round (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.SAMPLE_TUNE + TALSamplerConstants.LAYERS[groupCounter], 0.5) * 48.0 - 24.0);
        final double sampleFine = XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.SAMPLE_FINE_TUNE + TALSamplerConstants.LAYERS[groupCounter], 0.5) * 2.0 - 1.0;
        final double transpose = Math.round (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.TRANSPOSE, 0.5) * 48.0 - 24.0);
        final double detune = Math.round (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.DETUNE, 0.5) * 48.0 - 24.0);
        zone.setTuning (layerTranspose + sampleTune + transpose + detune + sampleFine);
        zone.setKeyTracking (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.PITCH_KEY_TRACK, 1));

        zone.setKeyRoot (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.ROOT_NOTE, -1));
        // A preset does not necessarily contain the ranges (often there is no velocity range at
        // all), a missing range keeps the full range of the zone
        zone.setKeyLow (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LO_NOTE, zone.getKeyLow ()));
        zone.setKeyHigh (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.HI_NOTE, zone.getKeyHigh ()));
        zone.setVelocityLow (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LO_VEL, zone.getVelocityLow ()));
        zone.setVelocityHigh (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.HI_VEL, zone.getVelocityHigh ()));

        // The mute group is the exclusive group, 0 means that the sample is not assigned to one
        zone.setExclusiveGroup (Math.max (0, XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.MUTE_GROUP, 0)));

        // No note and velocity cross-fades

        if (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ENABLED, 0) == 1)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            if (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LOOP_ALTERNATE, 0) == 1)
                loop.setType (LoopType.ALTERNATING);
            loop.setStart (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LOOP_START, -1));
            loop.setEnd (XMLUtils.getIntegerAttribute (sampleElement, TALSamplerTag.LOOP_END, -1));
            // TAL-Sampler plays the loop with this cross-fade, the loops are authored for it
            loop.setCrossfade (XMLUtils.getDoubleAttribute (sampleElement, TALSamplerTag.FADE_IN_SAMPLES, 0));
            zone.addLoop (loop);
        }

        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isPresent ())
            sampleData.get ().addZoneData (zone, false, false);
        return Optional.of (zone);
    }


    /**
     * Look up the file of a sample. The preset stores the path which the sample had when the
     * preset was saved, relative to the preset or absolute. An absolute path mostly belongs to the
     * computer on which a pack was produced, e.g. 'F:\Nuendo\000007 Prophet VS\Samples\x.wav', and
     * a path of Windows is not even one on macOS or Linux, which do not separate folders with
     * back-slashes. A pack ships its samples with its presets, therefore a sample which is not
     * found at its path is looked up by its file name in the folder of the preset and below it.
     *
     * @param presetFolder The folder which contains the preset
     * @param samplePath The path of the sample as stored in the preset
     * @return The sample file; if it cannot be found, the file of the stored path, which lets the
     *         caller report it as missing
     */
    private static File lookupSampleFile (final File presetFolder, final String samplePath)
    {
        final String path = samplePath.replace ('\\', '/');
        final File file = new File (path);
        final File sampleFile = file.isAbsolute () ? file : new File (presetFolder, path);
        if (sampleFile.isFile ())
            return sampleFile;
        final Optional<File> foundFile = findFileRecursively (presetFolder, file.getName ());
        return foundFile.isPresent () ? foundFile.get () : sampleFile;
    }


    private static Optional<IFilter> parseModulationAttributes (final Element programElement, final IMultisampleSource multisampleSource)
    {
        final List<TALSamplerModulator> modulators = parseModulators (programElement);

        // -----------------------------------------------------------
        // Amplitude

        final double ampAttack = getEnvelopeTime (programElement, TALSamplerTag.ADSR_AMP_ATTACK);
        final double ampHold = getEnvelopeTime (programElement, TALSamplerTag.ADSR_AMP_HOLD);
        final double ampDecay = getEnvelopeTime (programElement, TALSamplerTag.ADSR_AMP_DECAY);
        final double ampSustain = XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.ADSR_AMP_SUSTAIN, 1);
        final double ampRelease = getEnvelopeTime (programElement, TALSamplerTag.ADSR_AMP_RELEASE);

        double ampVelocityModAmount = 0.5;
        for (final TALSamplerModulator modulator: modulators)
            if (modulator.isDestination (TALSamplerModulator.DEST_ID_VOLUME_A) && modulator.isSource (TALSamplerModulator.SOURCE_ID_VELOCITY))
            {
                ampVelocityModAmount = modulator.getModAmount ();
                break;
            }

        // -----------------------------------------------------------
        // Filter

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

                filter.setCutoffKeyTracking (Math.clamp (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.FILTER_KEYBOARD, 0), -1, 1));

                // The amount of the filter envelope is bipolar, 0.5 is 0 %
                final double filterModDepth = XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.FILTER_ENVELOPE, 0.5) * 2.0 - 1.0;
                if (filterModDepth != 0)
                {
                    final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                    cutoffModulator.setDepth (filterModDepth);

                    final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                    filterEnvelope.setAttackTime (getEnvelopeTime (programElement, TALSamplerTag.ADSR_VCF_ATTACK));
                    filterEnvelope.setHoldTime (getEnvelopeTime (programElement, TALSamplerTag.ADSR_VCF_HOLD));
                    filterEnvelope.setDecayTime (getEnvelopeTime (programElement, TALSamplerTag.ADSR_VCF_DECAY));
                    filterEnvelope.setSustainLevel (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.ADSR_VCF_SUSTAIN, 1));
                    filterEnvelope.setReleaseTime (getEnvelopeTime (programElement, TALSamplerTag.ADSR_VCF_RELEASE));
                }

                for (final TALSamplerModulator modulator: modulators)
                    if (modulator.isDestination (TALSamplerModulator.DEST_ID_CUTOFF) && modulator.isSource (TALSamplerModulator.SOURCE_ID_VELOCITY))
                    {
                        filter.getCutoffVelocityModulator ().setDepth (modulator.getModAmount ());
                        break;
                    }

                // The plug-in adds wheel x amount to the normalized cutoff, whose range of 0 to 1
                // is
                // the whole range of the filter - the unit of the depth of the model as well
                for (final TALSamplerModulator modulator: modulators)
                    if (modulator.isDestination (TALSamplerModulator.DEST_ID_CUTOFF) && modulator.isSource (TALSamplerModulator.SOURCE_ID_MOD_WHEEL))
                    {
                        filter.getCutoffModWheelModulator ().setDepth (modulator.getModAmount ());
                        break;
                    }
            }
        }

        // -----------------------------------------------------------
        // Pitch

        // Pitch-bend
        final int bend = (int) Math.clamp (XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.PITCHBEND_RANGE, 1.0) * 1200.0, 0.0, 1200.0);

        // Envelope
        final double pitchAttack = getEnvelopeTime (programElement, TALSamplerTag.ADSR_MOD_ATTACK);
        final double pitchHold = getEnvelopeTime (programElement, TALSamplerTag.ADSR_MOD_HOLD);
        final double pitchDecay = getEnvelopeTime (programElement, TALSamplerTag.ADSR_MOD_DECAY);
        final double pitchSustain = XMLUtils.getDoubleAttribute (programElement, TALSamplerTag.ADSR_MOD_SUSTAIN, 1);
        final double pitchRelease = getEnvelopeTime (programElement, TALSamplerTag.ADSR_MOD_RELEASE);

        // Envelope 3 needs to be set to modulate the global pitch
        double globalPitchEnvelopeDepth = -1;
        for (final TALSamplerModulator modulator: modulators)
            if (modulator.isSource (TALSamplerModulator.SOURCE_ID_ENV3) && modulator.isDestination (TALSamplerModulator.DEST_ID_TUNE_A, TALSamplerModulator.DEST_ID_MASTER_TUNE))
            {
                globalPitchEnvelopeDepth = modulator.getModAmount ();
                break;
            }

        // Set all zones of all groups to the same amplitude and pitch envelope
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setBendUp (bend);
                zone.setBendDown (-bend);

                final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
                amplitudeEnvelope.setAttackTime (ampAttack);
                amplitudeEnvelope.setHoldTime (ampHold);
                amplitudeEnvelope.setDecayTime (ampDecay);
                amplitudeEnvelope.setSustainLevel (ampSustain);
                amplitudeEnvelope.setReleaseTime (ampRelease);

                zone.getAmplitudeVelocityModulator ().setDepth (ampVelocityModAmount);

                if (globalPitchEnvelopeDepth > 0)
                {
                    final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
                    pitchModulator.setDepth (globalPitchEnvelopeDepth);

                    final IEnvelope pitchEnvelope = pitchModulator.getSource ();
                    pitchEnvelope.setAttackTime (pitchAttack);
                    pitchEnvelope.setHoldTime (pitchHold);
                    pitchEnvelope.setDecayTime (pitchDecay);
                    pitchEnvelope.setSustainLevel (pitchSustain);
                    pitchEnvelope.setReleaseTime (pitchRelease);
                }
            }

        return optFilter;
    }


    private static List<TALSamplerModulator> parseModulators (final Element soundShapeElement)
    {
        final List<TALSamplerModulator> modulators = new ArrayList<> ();
        final Element modulationElement = XMLUtils.getChildElementByName (soundShapeElement, TALSamplerTag.MOD_MATRIX);
        if (modulationElement != null)
            for (final Element modulationEntryElement: XMLUtils.getChildElementsByName (modulationElement, TALSamplerTag.MOD_MATRIX_ENTRY, false))
                modulators.add (new TALSamplerModulator (modulationEntryElement));
        return modulators;
    }


    /**
     * Read the time of an envelope stage, see
     * {@link TALSamplerConstants#denormalizeEnvelopeTime(double)}.
     *
     * @param element The program element
     * @param attribute The attribute of the stage
     * @return The time in seconds, 0 if the attribute is missing
     */
    private static double getEnvelopeTime (final Element element, final String attribute)
    {
        return TALSamplerConstants.denormalizeEnvelopeTime (XMLUtils.getDoubleAttribute (element, attribute, 0));
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
