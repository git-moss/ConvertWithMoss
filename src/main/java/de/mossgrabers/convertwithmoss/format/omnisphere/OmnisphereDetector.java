// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Omnisphere ZMAP files in folders. Files must end with <i>.zmap</i>.
 *
 * @author Jürgen Moßgraber
 */
public class OmnisphereDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String IDS_NOTIFY_ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public OmnisphereDetector (final INotifier notifier)
    {
        super ("Omnisphere 3", "Omnisphere", notifier, new MetadataSettingsUI ("Omnisphere"), ".zmap");
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
            final Document document = OmnisphereAggregatedFile.parseXml (content);
            return this.parseDescription (multiSampleFile, document);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex, false);
        }
        return Collections.emptyList ();
    }


    /**
     * Process the ZMAP file and the related wave (*.db) files.
     *
     * @param sourceFile The multi-sample file
     * @param document The metadata XML document
     * @return The parsed multi-sample source
     * @throws IOException Could not parse the description
     */
    private List<IMultisampleSource> parseDescription (final File sourceFile, final Document document) throws IOException
    {
        final Element instrumentElement = document.getDocumentElement ();
        if (!"InstrumentMultisample".equals (instrumentElement.getNodeName ()))
        {
            this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Unknown Root");
            return Collections.emptyList ();
        }

        final String multiSampleName = FileUtils.getNameWithoutType (sourceFile.getName ());
        final File parentFolder = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFolder, this.sourceFolder, multiSampleName);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, multiSampleName, AudioFileUtils.subtractPaths (this.sourceFolder, sourceFile));

        final List<ISampleZone> sampleZones = new ArrayList<> ();
        for (final Element zoneElement: XMLUtils.getChildElementsByName (instrumentElement, "MultisampleZone", false))
        {
            final int minPitch = XMLUtils.getIntegerAttribute (zoneElement, "MinPitch", 0);
            final int maxPitch = XMLUtils.getIntegerAttribute (zoneElement, "MaxPitch", 127);
            final int minVelocity = XMLUtils.getIntegerAttribute (zoneElement, "MinVelocity", 0);
            final int maxVelocity = XMLUtils.getIntegerAttribute (zoneElement, "MaxVelocity", 127);
            final int hitKind = XMLUtils.getIntegerAttribute (zoneElement, "HitKind", 60);
            final float volume = parseHexFloat (zoneElement.getAttribute ("Volume"), 1f);
            final float pitch = parseHexFloat (zoneElement.getAttribute ("Pitch"), 0f);
            final int fixVel = XMLUtils.getIntegerAttribute (zoneElement, "fixVel", -1);

            final ISampleZone sampleZone = new DefaultSampleZone (multiSampleName, minPitch, maxPitch);
            sampleZone.setVelocityLow (minVelocity);
            sampleZone.setVelocityHigh (maxVelocity);
            sampleZone.setKeyRoot (hitKind);
            // TODO set volume, pitch and fixVel

            final Element soundGroupElement = XMLUtils.getChildElementByName (zoneElement, "SoundGroupWithNames");
            if (soundGroupElement == null)
            {
                this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Missing SoundGroupWithNames tag");
                return Collections.emptyList ();
            }

            @SuppressWarnings("unused")
            final int underKitSession = XMLUtils.getIntegerAttribute (soundGroupElement, "UnderKitSession", 0);
            @SuppressWarnings("unused")
            final String libraryName = soundGroupElement.getAttribute ("LibraryName");
            final String sampledInstrumentName = soundGroupElement.getAttribute ("SampledInstrumentName");
            sampleZone.setName (sampledInstrumentName);

            sampleZones.add (sampleZone);
        }
        if (sampleZones.isEmpty ())
            return Collections.emptyList ();

        final List<IGroup> groups = detectAndLoadSamples (parentFolder, sampleZones);
        if (groups.isEmpty ())
            return Collections.emptyList ();
        multisampleSource.setGroups (groups);

        final IMetadata metadata = multisampleSource.getMetadata ();
        this.createMetadata (metadata, this.getFirstSample (groups), parts);
        this.updateCreationDateTime (metadata, sourceFile);

        return Collections.singletonList (multisampleSource);
    }


    private static List<IGroup> detectAndLoadSamples (final File parentFolder, final List<ISampleZone> sampleZones) throws IOException
    {
        final List<IGroup> groups = new ArrayList<> ();
        final IGroup defaultGroup = new DefaultGroup ();

        for (final ISampleZone sampleZone: sampleZones)
        {
            final String sampleName = sampleZone.getName ();
            final File sampleFile = new File (parentFolder, sampleName + ".db");
            if (!sampleFile.exists ())
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", sampleFile.getName ()));

            final OmnisphereAggregatedFile aggregatedFile = new OmnisphereAggregatedFile ();
            aggregatedFile.read (sampleFile);

            // There can be multiple WAV files for round-robin! Just read all WAVs -> the current
            // SampleZone needs to be duplicated and all of them need to be added to a group
            final Map<String, byte []> wavFiles = aggregatedFile.getWavFiles ();
            if (wavFiles.isEmpty ())
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", sampleName + ".wav"));

            // First assign pitch on/off, otherwise we would need to do it for all of the copied
            // sample-zones
            final Collection<Document> sampledInstrumentXmlFiles = aggregatedFile.getXmlFiles ("SampledInstrument").values ();
            if (sampledInstrumentXmlFiles.isEmpty ())
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", sampleFile.getName ()));
            final Document sampledInstrumentXmlDocument = sampledInstrumentXmlFiles.iterator ().next ();
            final Element sampledInstrumentXmlRootElement = sampledInstrumentXmlDocument.getDocumentElement ();
            sampleZone.setKeyTracking (XMLUtils.getIntegerAttribute (sampledInstrumentXmlRootElement, "PitchedInstr", 1) > 0 ? 1.0 : 0);

            // Process round-robin (= stack) XML documents
            for (final Entry<String, Document> xmlFiles: aggregatedFile.getXmlFiles ("LayerHitStack").entrySet ())
            {
                final String xmlFilename = xmlFiles.getKey ();
                final Document xmlDocument = xmlFiles.getValue ();
                final Element layerHitStackElement = xmlDocument.getDocumentElement ();
                if (layerHitStackElement == null)
                    continue;
                final Element hitVelocityElement = XMLUtils.getChildElementByName (layerHitStackElement, "HitVelocity");
                if (hitVelocityElement == null)
                    continue;

                final List<ISampleZone> roundRobinZones = new ArrayList<> ();
                final List<Element> waveformTags = XMLUtils.getChildElementsByName (hitVelocityElement, "SampleWaveform");
                for (final Element sampleWaveformElement: waveformTags)
                {
                    final String wavFileName = new File (sampleWaveformElement.getAttribute ("AudioFilePath")).getName ();
                    final byte [] wavFileData = wavFiles.get (wavFileName);
                    if (wavFileData == null)
                        throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", wavFileName));

                    final ISampleZone rrSampleZone = new DefaultSampleZone (sampleZone);
                    rrSampleZone.setName (FileUtils.getNameWithoutType (wavFileName));
                    final ISampleData sampleData = new WavFileSampleData (new ByteArrayInputStream (wavFileData));
                    sampleData.addZoneData (rrSampleZone, false, true);
                    rrSampleZone.setSampleData (sampleData);
                    roundRobinZones.add (rrSampleZone);

                    rrSampleZone.setKeyRoot (XMLUtils.getIntegerAttribute (sampleWaveformElement, "BaseNote", 60));

                    // Level & A440 attributes not used

                    if (waveformTags.size () > 1)
                        rrSampleZone.setSequencePosition (XMLUtils.getIntegerAttribute (sampleWaveformElement, "RoundRobinSequenceNum", 0) + 1);
                }

                if (roundRobinZones.isEmpty ())
                    continue;
                if (roundRobinZones.size () == 1)
                    defaultGroup.addSampleZone (roundRobinZones.get (0));
                else
                {
                    final IGroup rrGroup = new DefaultGroup (roundRobinZones);
                    groups.add (rrGroup);
                    rrGroup.setName (FileUtils.getNameWithoutType (xmlFilename));
                }
            }
        }

        if (!defaultGroup.getSampleZones ().isEmpty ())
            groups.add (defaultGroup);

        return groups;
    }


    private static float parseHexFloat (final String hex, final float defaultValue)
    {
        if (hex == null)
            return defaultValue;
        // Parse hex string as unsigned 32-bit integer and reinterpret bits as float
        return Float.intBitsToFloat ((int) Long.parseLong (hex, 16));
    }
}
