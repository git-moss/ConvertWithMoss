// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for Omnisphere multi-sample ZMAP/DB files. A ZMAP file is a description file encoded in
 * XML. The related samples are stored in DB files in the same folder.
 *
 * @author Jürgen Moßgraber
 */
public class OmnisphereCreator extends AbstractCreator<EmptySettingsUI>
{
    private static final Map<String, List<String>> ATTRIBUTE_ORDER = new HashMap<> ();
    static
    {
        final List<String> hitVelocityList = new ArrayList<> ();
        Collections.addAll (hitVelocityList, "Level", "Minimum", "Maximum");
        ATTRIBUTE_ORDER.put ("HitVelocity", hitVelocityList);
        final List<String> sampleWaveformList = new ArrayList<> ();
        Collections.addAll (sampleWaveformList, "RoundRobinSequenceNum", "BaseNote", "AudioFilePath", "Level", "A440");
        ATTRIBUTE_ORDER.put ("SampleWaveform", sampleWaveformList);
        final List<String> sampledInstrumentList = new ArrayList<> ();
        Collections.addAll (sampledInstrumentList, "ATTRIB_VALUE_DATA", "PitchedInstr", "Level");
        ATTRIBUTE_ORDER.put ("SampledInstrument", sampledInstrumentList);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public OmnisphereCreator (final INotifier notifier)
    {
        super ("Omnisphere 3", "Omnisphere", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File destFolder = new File (destinationFolder, sampleName).getAbsoluteFile ();
        if (!destFolder.exists () && !destFolder.mkdirs ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", destFolder.getAbsolutePath ()));

        final Optional<String> metadata = this.createMetadata (multisampleSource);
        if (metadata.isEmpty ())
            return;

        final File multiFile = this.createUniqueFilename (destFolder, sampleName, "zmap");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.storePreset (destFolder, multisampleSource, multiFile, metadata.get ());

        this.progress.notifyDone ();
    }


    /**
     * Create a ZMAP and several DB files.
     *
     * @param destinationFolder Where to store the preset file
     * @param multisampleSource The multi-sample to store in the library
     * @param multiFile The output file
     * @param metadata The metadata description file
     * @throws IOException Could not store the file
     */
    private void storePreset (final File destinationFolder, final IMultisampleSource multisampleSource, final File multiFile, final String metadata) throws IOException
    {
        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        safeCreateDirectory (destinationFolder);
        this.writeSamples (destinationFolder, multisampleSource, multisampleSource.getAllSampleZones (false));
    }


    /**
     * Create the text of the description file.
     *
     * @param multisampleSource The multi-sample
     * @return The XML structure
     * @throws IOException Could not create the metadata
     */
    private Optional<String> createMetadata (final IMultisampleSource multisampleSource) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement ("InstrumentMultisample");
        document.appendChild (rootElement);
        // TODO Set the size to the size of the DB file (in bytes) without the prefixed FileSystem
        // -> already calculated in the write method!
        rootElement.setAttribute ("ATTRIB_VALUE_DATA", "Size=495424;");

        // Add all sample zones

        for (final ISampleZone sampleZone: multisampleSource.getAllSampleZones (false))
        {
            final Element zoneElement = XMLUtils.addElement (document, rootElement, "MultisampleZone");
            zoneElement.setAttribute ("MinPitch", Integer.toString (sampleZone.getKeyLow ()));
            zoneElement.setAttribute ("MaxPitch", Integer.toString (sampleZone.getKeyHigh ()));
            zoneElement.setAttribute ("MinVelocity", Integer.toString (sampleZone.getVelocityLow ()));
            zoneElement.setAttribute ("MaxVelocity", Integer.toString (sampleZone.getVelocityHigh ()));
            zoneElement.setAttribute ("HitKind", Integer.toString (sampleZone.getKeyRoot ()));
            zoneElement.setAttribute ("Volume", "3f800000");
            // TODO support pitch adjustment on sample level
            zoneElement.setAttribute ("Pitch", "3f800000");
            // TODO understand settings range
            zoneElement.setAttribute ("fixVel", "-1");

            final Element soundGroupElement = XMLUtils.addElement (document, zoneElement, "SoundGroupWithNames");
            soundGroupElement.setAttribute ("UnderKitSession", "0");
            soundGroupElement.setAttribute ("LibraryName", ".");
            soundGroupElement.setAttribute ("SampledInstrumentName", createSafeFilename (sampleZone.getName ()));
        }

        try
        {
            final String xmlCode = XMLUtils.toString (document, "\n", 0, StandardCharsets.ISO_8859_1.name (), "1.0");
            return Optional.of (xmlCode);
        }
        catch (final TransformerException ex)
        {
            throw new IOException (ex);
        }
    }


    protected long writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final List<ISampleZone> sampleZones) throws IOException
    {
        // TODO for round-robin sample-zones need to be aggregated!

        long overallDbFileSize = 0;
        for (int zoneIndex = 0; zoneIndex < sampleZones.size (); zoneIndex++)
        {
            if (this.isCancelled ())
                return 0;

            final OmnisphereAggregatedFile aggregator = new OmnisphereAggregatedFile ();

            final ISampleZone sampleZone = sampleZones.get (zoneIndex);

            this.progress.notifyProgress ();

            // LinkedHashMap guarantees order
            final byte [] wavFileData = this.serializeWavFile (multisampleSource, sampleZone);
            aggregator.addFile (this.createSampleFilename (sampleZone, -1, ".wav"), wavFileData);

            try
            {
                aggregator.addXmlFile ("HitBundle.xml", this.createHitBundleDocument (sampleZone));
                aggregator.addXmlFile ("Layer.xml", this.createLayerHitStackDocument (sampleZone), ATTRIBUTE_ORDER);
                aggregator.addXmlFile ("SampledInstrument.xml", this.createSampledInstrumentDocument (sampleZone), ATTRIBUTE_ORDER);
                overallDbFileSize += aggregator.write (new File (sampleFolder, this.createSampleFilename (sampleZone, zoneIndex, ".db")));
            }
            catch (final NoSuchFileException | FileNotFoundException | TransformerException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
            }
        }
        return overallDbFileSize;
    }


    private Document createHitBundleDocument (final ISampleZone sampleZone) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            throw new IOException ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement ("HitBundle");
        document.appendChild (rootElement);
        rootElement.setAttribute ("BundleKind", Integer.toString (sampleZone.getKeyRoot ()));
        rootElement.setAttribute ("Level", "3f800000");

        return document;
    }


    // TODO this needs to be created for multiple sample-zones in case of round-robin!
    private Document createLayerHitStackDocument (final ISampleZone sampleZone) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            throw new IOException ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement ("LayerHitStack");
        document.appendChild (rootElement);
        rootElement.setAttribute ("Level", "3f800000");

        final Element hitVelocityElement = XMLUtils.addElement (document, rootElement, "HitVelocity");
        hitVelocityElement.setAttribute ("Level", "3f800000");
        // No idea about this value but the sample is not found if it is set to the minimum velocity
        hitVelocityElement.setAttribute ("Minimum", "0");
        hitVelocityElement.setAttribute ("Maximum", Integer.toString (sampleZone.getVelocityHigh ()));

        // TODO this needs to be created for multiple sample-zones in case of round-robin!

        final Element sampleWaveformElement = XMLUtils.addElement (document, hitVelocityElement, "SampleWaveform");
        final String roundRobinIndex = sampleZone.getPlayLogic () == PlayLogic.ROUND_ROBIN ? Integer.toString (sampleZone.getSequencePosition () - 1) : "0";
        sampleWaveformElement.setAttribute ("RoundRobinSequenceNum", roundRobinIndex);
        sampleWaveformElement.setAttribute ("BaseNote", Integer.toString (sampleZone.getKeyRoot ()));
        sampleWaveformElement.setAttribute ("AudioFilePath", this.createSampleFilename (sampleZone, -1, ".wav"));
        sampleWaveformElement.setAttribute ("Level", "3f800000");
        sampleWaveformElement.setAttribute ("A440", "3f800000");

        return document;
    }


    private Document createSampledInstrumentDocument (final ISampleZone sampleZone) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            throw new IOException ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement ("SampledInstrument");
        document.appendChild (rootElement);
        rootElement.setAttribute ("ATTRIB_VALUE_DATA", "");
        rootElement.setAttribute ("PitchedInstr", sampleZone.getKeyTracking () == 0 ? "0" : "1");
        rootElement.setAttribute ("Level", "3f800000");

        return document;
    }


    private byte [] serializeWavFile (final IMultisampleSource multisampleSource, final ISampleZone zone) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        if (zone.getStart () > 0)
            this.rewriteFile (multisampleSource, zone, out, DESTINATION_FORMAT, true);
        else
        {
            final ISampleData sampleData = zone.getSampleData ();
            if (sampleData == null)
            {
                this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), zone.getName ());
                this.notifier.logText ("\n");
            }
            else
                sampleData.writeSample (out);
        }
        return out.toByteArray ();
    }
}