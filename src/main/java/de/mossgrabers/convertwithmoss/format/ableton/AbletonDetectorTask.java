// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

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
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Ableton Preset/Rack-Preset files in folders. Files must end with <i>.adv</i>
 * or <i>.adg</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonDetectorTask extends AbstractDetectorTask
{
    private static final Map<String, FilterType> FILTER_TYPES = new HashMap<> ();
    static
    {
        FILTER_TYPES.put ("0", FilterType.LOW_PASS);
        FILTER_TYPES.put ("1", FilterType.HIGH_PASS);
        FILTER_TYPES.put ("2", FilterType.BAND_PASS);
        FILTER_TYPES.put ("3", FilterType.BAND_REJECTION);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadataConfig Additional metadata configuration parameters
     */
    protected AbletonDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadataConfig)
    {
        super (notifier, consumer, sourceFolder, metadataConfig, ".adv", ".adg");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        try (final InputStream in = new GZIPInputStream (new FileInputStream (file)))
        {
            final String multiSampleFileContent = StreamUtils.readUTF8 (in);
            return this.readMetadataFile (file, multiSampleFileContent);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Read the metadata description file.
     *
     * @param multiSampleFile The file
     * @param multiSampleFileContent The XML description file content
     * @return The result
     * @throws IOException Error reading the file
     */
    private List<IMultisampleSource> readMetadataFile (final File multiSampleFile, final String multiSampleFileContent) throws IOException
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (multiSampleFileContent)));
            return this.parseDescription (multiSampleFile, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Process the multi-sample metadata file and the related wave files.
     *
     * @param multiSampleFile The multi-sample file
     * @param document The metadata XML document
     * @return The parsed multi-sample source
     * @throws IOException Could not parse the XML document
     */
    private List<IMultisampleSource> parseDescription (final File multiSampleFile, final Document document) throws IOException
    {
        final Element top = document.getDocumentElement ();
        if (!AbletonTag.TAG_ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_TAG", AbletonTag.TAG_ROOT);
            return Collections.emptyList ();
        }

        final String creator = top.getAttribute (AbletonTag.ATTR_CREATOR);

        final Pair<List<Element>, File> samplerElements = getSamplerElements (top, multiSampleFile);
        if (samplerElements == null)
        {
            this.notifier.logError ("IDS_ADV_NOT_A_SAMPLER_PRESET");
            return Collections.emptyList ();
        }

        final File rootPath = samplerElements.getValue ();
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        int counter = 0;
        final List<Element> elementsList = samplerElements.getKey ();
        final boolean multiple = elementsList.size () > 1;
        for (final Element samplerElement: elementsList)
        {
            final IMultisampleSource multiSample = this.parseSampler (multiSampleFile, samplerElement, rootPath, creator);
            multisampleSources.add (multiSample);
            // Create unique names if there are multiple ones
            if (multiple)
                multiSample.setName (FileUtils.getNameWithoutType (multiSample.getName ()) + (counter + 1));
            counter++;
        }
        return multisampleSources;
    }


    /**
     * Parse an Ableton preset XML document with a Simpler or Sampler device.
     *
     * @param multiSampleFile The multi-sample source file
     * @param deviceElement The device element
     * @param rootPath The root path where the samples are located
     * @param creator The creator value
     * @return The parse multi-sample source
     * @throws IOException Could not parse the document
     */
    private IMultisampleSource parseSampler (final File multiSampleFile, final Element deviceElement, final File rootPath, final String creator) throws IOException
    {
        final String name = FileUtils.getNameWithoutType (multiSampleFile);

        final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        final IMetadata metadata = multisampleSource.getMetadata ();
        parseMetadata (deviceElement, metadata, creator);

        this.parseMultiSample (multiSampleFile, rootPath, multisampleSource, deviceElement);
        this.createMetadata (metadata, this.getFirstSample (multisampleSource.getGroups ()), parts);
        return multisampleSource;
    }


    /**
     * Find the root path which contains the preset as well as its samples.
     *
     * @param multiSampleFile The ADV file in case it is needed to search upwards
     * @param deviceElement The device element which contains the sample info to search
     * @return The sample file
     * @throws IOException Could not find the info
     */
    private static File getRootPath (final File multiSampleFile, final Element deviceElement) throws IOException
    {
        Element presetRefElement = XMLUtils.getChildElementByName (deviceElement, AbletonTag.TAG_PRESET_REF);
        final Element valueElement;
        if (presetRefElement == null)
        {
            presetRefElement = getRequiredElement (deviceElement, AbletonTag.TAG_LAST_PRESET_REF);
            valueElement = getRequiredElement (presetRefElement, AbletonTag.TAG_VALUE);
        }
        else
            valueElement = presetRefElement;

        Element filePresetRefElement = XMLUtils.getChildElementByName (valueElement, AbletonTag.TAG_FILE_PRESET_REF);
        if (filePresetRefElement == null)
            filePresetRefElement = XMLUtils.getChildElementByName (valueElement, AbletonTag.TAG_FILE_PRESET_REF2);

        if (filePresetRefElement == null)
            return findSampleFolder (multiSampleFile);

        final Element fileRefElement = getRequiredElement (filePresetRefElement, AbletonTag.TAG_FILE_REF);

        final String filePath;
        final int type;
        try
        {
            final String relativePathType = AbletonDetectorTask.getValueAttribute (fileRefElement, AbletonTag.TAG_RELATIVE_PATH_TYPE);
            type = Integer.parseInt (relativePathType);
        }
        catch (final NumberFormatException ex)
        {
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MISSING_TAG", AbletonTag.TAG_RELATIVE_PATH_TYPE));
        }

        switch (type)
        {
            case 1:
                // no idea what to make out of this relative data. Therefore, search upwards till
                // the Sample folder is found...
                return findSampleFolder (multiSampleFile);

            case 5:
            case 6:
            default:
                final String relativePresetPath = getValueAttribute (fileRefElement, AbletonTag.TAG_RELATIVE_PATH);
                filePath = createUpwardsPath (relativePresetPath);
                return new File (multiSampleFile.getParent (), filePath).getCanonicalFile ();
        }
    }


    /**
     * Parse all zone information from the XML code and store it in the multi-sample.
     *
     * @param multiSampleFile The multi-sample file
     * @param rootPath The root path where the samples are located
     * @param multisampleSource Where to store the data
     * @param deviceElement The device element which contains the zone data
     * @throws IOException Could not access the sample
     */
    private void parseMultiSample (final File multiSampleFile, final File rootPath, final IMultisampleSource multisampleSource, final Element deviceElement) throws IOException
    {
        final Element playerElement = getRequiredElement (deviceElement, AbletonTag.TAG_PLAYER);
        final Element mapElement = getRequiredElement (playerElement, AbletonTag.TAG_MULTI_SAMPLE_MAP);
        final Element samplePartsElement = getRequiredElement (mapElement, AbletonTag.TAG_SAMPLE_PARTS);

        final IGroup group = new DefaultGroup ("Group #1");
        multisampleSource.setGroups (Collections.singletonList (group));

        for (final Element multiSamplePartElement: XMLUtils.getChildElementsByName (samplePartsElement, AbletonTag.TAG_MULTI_SAMPLE_PART, false))
        {
            final String zoneName = AbletonDetectorTask.getValueAttribute (multiSamplePartElement, AbletonTag.TAG_NAME);

            final Element sampleRefElement = getRequiredElement (multiSamplePartElement, AbletonTag.TAG_SAMPLE_REF);
            final Element fileRefElement = getRequiredElement (sampleRefElement, AbletonTag.TAG_FILE_REF);

            final ISampleData sampleData = this.getSampleData (multiSampleFile, fileRefElement, rootPath);
            if (sampleData != null)
            {
                final String name = FileUtils.getNameWithoutType (new File (zoneName));
                final ISampleZone zone = new DefaultSampleZone (name, sampleData);
                readZone (zone, multiSamplePartElement);
                group.addSampleZone (zone);
            }
        }

        final IFilter filter = readFilter (deviceElement);
        if (filter != null)
            multisampleSource.setGlobalFilter (filter);

        applyGlobalEnvelopes (deviceElement, multisampleSource);
    }


    /**
     * Read all zone data.
     *
     * @param zone The zone to fill
     * @param multiSamplePartElement The XML element with the zone info
     * @throws IOException A required tag is missing
     */
    private static void readZone (final ISampleZone zone, final Element multiSamplePartElement) throws IOException
    {
        final Element keyRangeElement = getRequiredElement (multiSamplePartElement, AbletonTag.TAG_KEY_RANGE);
        zone.setKeyLow (AbletonDetectorTask.getIntegerValueAttribute (keyRangeElement, AbletonTag.TAG_MINIMUM, 0));
        zone.setKeyHigh (AbletonDetectorTask.getIntegerValueAttribute (keyRangeElement, AbletonTag.TAG_MAXIMUM, 127));
        zone.setNoteCrossfadeLow (AbletonDetectorTask.getIntegerValueAttribute (keyRangeElement, AbletonTag.TAG_CROSSFADE_MINIMUM, 0));
        zone.setNoteCrossfadeHigh (AbletonDetectorTask.getIntegerValueAttribute (keyRangeElement, AbletonTag.TAG_CROSSFADE_MAXIMUM, 127));

        final Element velocityRangeElement = getRequiredElement (multiSamplePartElement, AbletonTag.TAG_VELOCITY_RANGE);
        zone.setKeyLow (AbletonDetectorTask.getIntegerValueAttribute (velocityRangeElement, AbletonTag.TAG_MINIMUM, 1));
        zone.setKeyHigh (AbletonDetectorTask.getIntegerValueAttribute (velocityRangeElement, AbletonTag.TAG_MAXIMUM, 127));
        zone.setNoteCrossfadeLow (AbletonDetectorTask.getIntegerValueAttribute (velocityRangeElement, AbletonTag.TAG_CROSSFADE_MINIMUM, 1));
        zone.setNoteCrossfadeHigh (AbletonDetectorTask.getIntegerValueAttribute (velocityRangeElement, AbletonTag.TAG_CROSSFADE_MAXIMUM, 127));

        zone.setKeyRoot (AbletonDetectorTask.getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_ROOT_KEY, 60));
        zone.setTune (AbletonDetectorTask.getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_DETUNE, 0) / 100.0);
        zone.setKeyTracking (MathUtils.clamp (AbletonDetectorTask.getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_TUNE_SCALE, 0) / 100.0, 0, 1));
        zone.setPanorama (AbletonDetectorTask.getDoubleValueAttribute (multiSamplePartElement, AbletonTag.TAG_PANORAMA, 0));

        final double volumeVal = AbletonDetectorTask.getDoubleValueAttribute (multiSamplePartElement, AbletonTag.TAG_VOLUME, 1);
        zone.setGain (Math.log (volumeVal) / Math.log (2) * 6.0);

        zone.setStart (AbletonDetectorTask.getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_SAMPLE_START, 0));
        zone.setStop (AbletonDetectorTask.getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_SAMPLE_END, -1));

        final Element reverseElement = XMLUtils.getChildElementByName (multiSamplePartElement, AbletonTag.TAG_REVERSE);
        zone.setReversed (reverseElement != null && "true".equals (AbletonDetectorTask.getValueAttribute (reverseElement, AbletonTag.TAG_MANUAL)));

        final Element sustainLoopElement = XMLUtils.getChildElementByName (multiSamplePartElement, AbletonTag.TAG_SUSTAIN_LOOP);
        if (sustainLoopElement != null)
        {
            final int loopMode = AbletonDetectorTask.getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_LOOP_MODE, 0);
            if (loopMode > 0)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setStart (AbletonDetectorTask.getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_LOOP_START, 0));
                loop.setEnd (AbletonDetectorTask.getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_LOOP_END, zone.getStop ()));
                loop.setCrossfadeInSamples (AbletonDetectorTask.getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_LOOP_CROSSFADE, 0));
                loop.setType (loopMode == 1 ? LoopType.FORWARDS : LoopType.ALTERNATING);
                zone.getLoops ().add (loop);
            }
        }
    }


    /**
     * Try to locate the sample and create a sample data object from it.
     *
     * @param multiSampleFile The multi-sample file
     * @param fileRefElement The file reference element
     * @param rootPath The root path where the samples are located
     * @return The sample data or null is not found
     * @throws IOException Could not access the sample
     */
    private ISampleData getSampleData (final File multiSampleFile, final Element fileRefElement, final File rootPath) throws IOException
    {
        final String relativePath = AbletonDetectorTask.getValueAttribute (fileRefElement, AbletonTag.TAG_RELATIVE_PATH);
        return this.createSampleData (new File (rootPath, relativePath));
    }


    /**
     * Parse the metadata information.
     *
     * @param top The top XML element
     * @param metadata Where to store the parsed information
     * @param creator The creator value
     */
    private static void parseMetadata (final Element top, final IMetadata metadata, final String creator)
    {
        final Element descriptionTag = XMLUtils.getChildElementByName (top, AbletonTag.TAG_ANNOTATION);
        if (descriptionTag != null)
            metadata.setDescription (XMLUtils.readTextContent (descriptionTag));

        if (creator != null && !creator.isBlank ())
            metadata.setCreator (creator);
    }


    private static IFilter readFilter (final Element samplePartsElement)
    {
        try
        {
            final Element filterElement = getRequiredElement (samplePartsElement, AbletonTag.TAG_FILTER);
            final Element filterIsOnElement = getRequiredElement (filterElement, AbletonTag.TAG_IS_ON);
            final boolean isFilterOn = getBooleanValueAttribute (filterIsOnElement, AbletonTag.TAG_MANUAL);
            if (!isFilterOn)
                return null;

            final Element slotElement = getRequiredElement (filterElement, AbletonTag.TAG_SLOT);
            final Element valueElement = getRequiredElement (slotElement, AbletonTag.TAG_VALUE);
            final Element simplerFilterElement = getRequiredElement (valueElement, AbletonTag.TAG_SIMPLER_FILTER);
            final Element typeElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_FILTER_TYPE);
            FilterType type = FILTER_TYPES.get (getValueAttribute (typeElement, AbletonTag.TAG_MANUAL));
            if (type == null)
                type = FilterType.LOW_PASS;

            final Element slopeElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_FILTER_SLOPE);
            int poles = getBooleanValueAttribute (slopeElement, AbletonTag.TAG_MANUAL) ? 4 : 2;

            final Element freqElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_FILTER_FREQUENCY);
            double cutoff = getDoubleValueAttribute (freqElement, AbletonTag.TAG_MANUAL, IFilter.MAX_FREQUENCY);

            final Element resElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_FILTER_RESONANCE);
            double resonance = getDoubleValueAttribute (resElement, AbletonTag.TAG_MANUAL, IFilter.MAX_FREQUENCY) / 1.25;

            final DefaultFilter filter = new DefaultFilter (type, poles, cutoff, resonance);

            // Read the envelope
            final Element envelopeElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_ENVELOPE);
            final Element isOnElement = getRequiredElement (envelopeElement, AbletonTag.TAG_IS_ON);
            if (getBooleanValueAttribute (isOnElement, AbletonTag.TAG_MANUAL))
            {
                final Element amountElement = getRequiredElement (envelopeElement, AbletonTag.TAG_AMOUNT);

                final Element attackTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_TIME);
                final Element decayTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_DECAY_TIME);
                final Element releaseTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_TIME);

                final Element attackLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_LEVEL);
                final Element sustainLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_SUSTAIN_LEVEL);
                final Element releaseLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_LEVEL);

                final Element attackSlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_SLOPE);
                final Element decaySlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_DECAY_SLOPE);
                final Element releaseSlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_SLOPE);

                final IModulator cutoffModulator = filter.getCutoffModulator ();
                cutoffModulator.setDepth (Math.abs (getDoubleValueAttribute (amountElement, AbletonTag.TAG_MANUAL, 0) / 72.0));

                final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                filterEnvelope.setAttackTime (getDoubleValueAttribute (attackTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
                filterEnvelope.setDecayTime (getDoubleValueAttribute (decayTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
                filterEnvelope.setReleaseTime (getDoubleValueAttribute (releaseTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);

                filterEnvelope.setStartLevel (getDoubleValueAttribute (attackLevelElement, AbletonTag.TAG_MANUAL, 0));
                filterEnvelope.setSustainLevel (getDoubleValueAttribute (sustainLevelElement, AbletonTag.TAG_MANUAL, 1));
                filterEnvelope.setEndLevel (getDoubleValueAttribute (releaseLevelElement, AbletonTag.TAG_MANUAL, 0));

                filterEnvelope.setAttackSlope (-getDoubleValueAttribute (attackSlopeElement, AbletonTag.TAG_MANUAL, 0));
                filterEnvelope.setDecaySlope (-getDoubleValueAttribute (decaySlopeElement, AbletonTag.TAG_MANUAL, 0));
                filterEnvelope.setReleaseSlope (-getDoubleValueAttribute (releaseSlopeElement, AbletonTag.TAG_MANUAL, 0));
            }

            return filter;
        }
        catch (final IOException ex)
        {
            // No filter configured
            return null;
        }
    }


    private static void applyGlobalEnvelopes (final Element deviceElement, final IMultisampleSource multisampleSource)
    {
        try
        {
            // Read the amplitude envelope
            final Element volAndPanElement = getRequiredElement (deviceElement, AbletonTag.TAG_VOLUME_AND_PAN);
            final Element envelopeElement = getRequiredElement (volAndPanElement, AbletonTag.TAG_ENVELOPE);

            final Element attackTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_TIME);
            final Element decayTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_DECAY_TIME);
            final Element releaseTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_TIME);

            final Element attackLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_LEVEL);
            final Element sustainLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_SUSTAIN_LEVEL);
            final Element releaseLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_LEVEL);

            final Element attackSlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_SLOPE);
            final Element decaySlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_DECAY_SLOPE);
            final Element releaseSlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_SLOPE);

            final IEnvelope ampEnvelope = new DefaultEnvelope ();
            ampEnvelope.setAttackTime (getDoubleValueAttribute (attackTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
            ampEnvelope.setDecayTime (getDoubleValueAttribute (decayTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
            ampEnvelope.setReleaseTime (getDoubleValueAttribute (releaseTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);

            ampEnvelope.setStartLevel (getDoubleValueAttribute (attackLevelElement, AbletonTag.TAG_MANUAL, 0));
            ampEnvelope.setSustainLevel (getDoubleValueAttribute (sustainLevelElement, AbletonTag.TAG_MANUAL, 1));
            ampEnvelope.setEndLevel (getDoubleValueAttribute (releaseLevelElement, AbletonTag.TAG_MANUAL, 0));

            ampEnvelope.setAttackSlope (-getDoubleValueAttribute (attackSlopeElement, AbletonTag.TAG_MANUAL, 0));
            ampEnvelope.setDecaySlope (-getDoubleValueAttribute (decaySlopeElement, AbletonTag.TAG_MANUAL, 0));
            ampEnvelope.setReleaseSlope (-getDoubleValueAttribute (releaseSlopeElement, AbletonTag.TAG_MANUAL, 0));

            // Read the pitch envelope
            final Element auxEnvelopeElement = getRequiredElement (deviceElement, AbletonTag.TAG_AUX_ENVELOPE);
            final Element isAuxEnvOnElement = getRequiredElement (auxEnvelopeElement, AbletonTag.TAG_IS_ON);
            IEnvelope auxEnvelope = null;
            double auxDepth = 0;
            if (getBooleanValueAttribute (isAuxEnvOnElement, AbletonTag.TAG_MANUAL))
            {
                final Element slotElement = getRequiredElement (auxEnvelopeElement, AbletonTag.TAG_SLOT);
                final Element valueElement = getRequiredElement (slotElement, AbletonTag.TAG_VALUE);
                final Element auxEnvElement = getRequiredElement (valueElement, AbletonTag.TAG_SIMPLER_AUX_ENVELOPE);

                final Element auxAttackTimeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_ATTACK_TIME);
                final Element auxDecayTimeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_DECAY_TIME);
                final Element auxReleaseTimeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_RELEASE_TIME);

                final Element auxAttackLevelElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_ATTACK_LEVEL);
                final Element auxSustainLevelElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_SUSTAIN_LEVEL);
                final Element auxReleaseLevelElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_RELEASE_LEVEL);

                final Element auxAttackSlopeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_ATTACK_SLOPE);
                final Element auxDecaySlopeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_DECAY_SLOPE);
                final Element auxReleaseSlopeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_RELEASE_SLOPE);

                final Element auxModDestElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_MODULATION_DESTINATION);
                Element auxConnectionElement = getRequiredElement (auxModDestElement, AbletonTag.TAG_MODULATION_CONNECTION_0);
                int destination = getIntegerValueAttribute (auxConnectionElement, AbletonTag.TAG_MODULATION_CONNECTION, 0);
                // 6 = Pitch Modulation
                if (destination != 6)
                {
                    auxConnectionElement = getRequiredElement (auxModDestElement, AbletonTag.TAG_MODULATION_CONNECTION_1);
                    destination = getIntegerValueAttribute (auxConnectionElement, AbletonTag.TAG_MODULATION_CONNECTION, 0);
                }
                if (destination == 6)
                {
                    auxEnvelope = new DefaultEnvelope ();
                    auxDepth = Math.abs (getDoubleValueAttribute (auxConnectionElement, AbletonTag.TAG_AMOUNT, 0) / 100.0);

                    auxEnvelope.setAttackTime (getDoubleValueAttribute (auxAttackTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
                    auxEnvelope.setDecayTime (getDoubleValueAttribute (auxDecayTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
                    auxEnvelope.setReleaseTime (getDoubleValueAttribute (auxReleaseTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);

                    auxEnvelope.setStartLevel (getDoubleValueAttribute (auxAttackLevelElement, AbletonTag.TAG_MANUAL, 0));
                    auxEnvelope.setSustainLevel (getDoubleValueAttribute (auxSustainLevelElement, AbletonTag.TAG_MANUAL, 0));
                    auxEnvelope.setEndLevel (getDoubleValueAttribute (auxReleaseLevelElement, AbletonTag.TAG_MANUAL, 0));

                    auxEnvelope.setAttackSlope (-getDoubleValueAttribute (auxAttackSlopeElement, AbletonTag.TAG_MANUAL, 0));
                    auxEnvelope.setDecaySlope (-getDoubleValueAttribute (auxDecaySlopeElement, AbletonTag.TAG_MANUAL, 0));
                    auxEnvelope.setReleaseSlope (-getDoubleValueAttribute (auxReleaseSlopeElement, AbletonTag.TAG_MANUAL, 0));
                }
            }

            for (final IGroup group: multisampleSource.getGroups ())
            {
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    zone.getAmplitudeModulator ().setSource (ampEnvelope);
                    if (auxEnvelope != null)
                    {
                        final IModulator pitchModulator = zone.getPitchModulator ();
                        pitchModulator.setDepth (auxDepth);
                        pitchModulator.setSource (auxEnvelope);
                    }
                }
            }
        }
        catch (final IOException ex)
        {
            // Ignore missing elements
            return;
        }
    }


    private static String createUpwardsPath (final String relativePath)
    {
        int numberOfParentDirectories = Paths.get (relativePath).getNameCount ();
        final String lowerCase = relativePath.toLowerCase ();
        if (lowerCase.endsWith (".adv") || lowerCase.endsWith (".adg"))
            numberOfParentDirectories -= 1;
        return "../".repeat (numberOfParentDirectories);
    }


    private static Pair<List<Element>, File> getSamplerElements (final Element top, final File multiSampleFile) throws IOException
    {
        final List<Element> samplerElements = new ArrayList<> ();

        Element deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.TAG_DEVICE_SIMPLER);
        if (deviceElement == null)
            deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.TAG_DEVICE_SAMPLER);
        if (deviceElement != null)
            samplerElements.add (deviceElement);
        else
        {
            deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.TAG_DEVICE_RACK);
            if (deviceElement == null)
                return null;
            samplerElements.addAll (XMLUtils.getChildElementsByName (deviceElement, AbletonTag.TAG_DEVICE_SAMPLER, true));
            samplerElements.addAll (XMLUtils.getChildElementsByName (deviceElement, AbletonTag.TAG_DEVICE_SIMPLER, true));
        }

        final File rootPath = getRootPath (multiSampleFile, deviceElement);
        return new Pair<> (samplerElements, rootPath);
    }


    private static double getDoubleValueAttribute (final Element parentElement, final String elementTag, final double defaultValue)
    {
        final String value = AbletonDetectorTask.getValueAttribute (parentElement, elementTag);
        try
        {
            return Double.parseDouble (value);
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    private static int getIntegerValueAttribute (final Element parentElement, final String elementTag, final int defaultValue)
    {
        final String value = AbletonDetectorTask.getValueAttribute (parentElement, elementTag);
        try
        {
            return Integer.parseInt (value);
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    private static boolean getBooleanValueAttribute (final Element parentElement, final String elementTag)
    {
        final String value = AbletonDetectorTask.getValueAttribute (parentElement, elementTag);
        return Boolean.parseBoolean (value);
    }


    private static String getValueAttribute (final Element parentElement, final String elementTag)
    {
        final Element element = XMLUtils.getChildElementByName (parentElement, elementTag);
        return element == null ? "" : element.getAttribute (AbletonTag.ATTR_VALUE);
    }


    private static Element getRequiredElement (final Element parentElement, final String tagName) throws IOException
    {
        final Element element = XMLUtils.getChildElementByName (parentElement, tagName);
        if (element == null)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MISSING_TAG", tagName));
        return element;
    }


    private static File findSampleFolder (final File multiSampleFile)
    {
        File folder = multiSampleFile;
        while ((folder = folder.getParentFile ()) != null)
        {
            final Set<String> children = new HashSet<> ();
            Collections.addAll (children, folder.list ());
            if (children.contains ("Samples"))
                return folder;
        }
        return new File ("");
    }
}
