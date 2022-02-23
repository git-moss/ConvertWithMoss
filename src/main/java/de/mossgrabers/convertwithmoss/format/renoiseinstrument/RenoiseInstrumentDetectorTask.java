package de.mossgrabers.convertwithmoss.format.renoiseinstrument;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.mossgrabers.convertwithmoss.file.flac.Decoder;

public class RenoiseInstrumentDetectorTask extends AbstractDetectorTask {
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";

    /**
     * Constructor.
     *
     * @param notifier     The notifier
     * @param consumer     The consumer that handles the detected multisample
     *                     sources
     * @param sourceFolder The top source folder for the detection
     */
    public RenoiseInstrumentDetectorTask(final INotifier notifier, final Consumer<IMultisampleSource> consumer,
            final File sourceFolder) {
        super(notifier, consumer, sourceFolder, null, ".xrni");
    }

    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile(final File file) {
        try (final ZipFile zipFile = new ZipFile (file))
        {
            final ZipEntry entry = zipFile.getEntry ("Instrument.xml");
            if (entry == null)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_NO_METADATA_FILE");
                return Collections.emptyList ();
            }

            return this.parseMetadataFile (file, zipFile, entry);
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
     * @param zipFile The ZIP file which contains the description file
     * @param entry The ZIP entry of the file
     * @return The result
     * @throws IOException Error reading the file
     */
    private List<IMultisampleSource> parseMetadataFile (final File instrumentFile, final ZipFile zipFile, final ZipEntry entry) throws IOException
    {
        if (this.waitForDelivery ())
        return Collections.emptyList ();

        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final Document document = XMLUtils.parseDocument (new InputSource (in));
            return this.parseDescription (instrumentFile, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return Collections.emptyList ();
        }
    }

        /**
     * Process the instrumentFile metadata file and the related wave files.
     *
     * @param instrumentFile The instrumentFile file
     * @param document The metadata XML document
     * @return The parsed multisample source
     */
    private List<IMultisampleSource> parseDescription (final File instrumentFile, final Document xmldocument)
    {
        final Element top = xmldocument.getDocumentElement ();
        if (!RenoiseInstrumentTag.RENOISEINSTRUMENT.equals (top.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final Element nm =  XMLUtils.getChildElementByName(top, RenoiseInstrumentTag.NAME);
        final String name = XMLUtils.readTextContent (nm);
        final String [] parts = createPathParts (instrumentFile.getParentFile (), this.sourceFolder, name);

        final MultisampleSource multisampleSource = new MultisampleSource (instrumentFile, parts, name, this.subtractPaths (this.sourceFolder, instrumentFile));

        final Map<Integer, IVelocityLayer> indexedVelocityLayers = new TreeMap<> ();
        indexedVelocityLayers.put (Integer.valueOf (-1), new DefaultVelocityLayer ());

        // Parse all samples
        final Element samples = XMLUtils.getChildElementByName(XMLUtils.getChildElementByName (top, RenoiseInstrumentTag.SAMPLEGENERATOR), RenoiseInstrumentTag.SAMPLES);

        int index = 0;
        for (final org.w3c.dom.Node sampleNode: XMLUtils.getChildrenByName(samples, RenoiseInstrumentTag.SAMPLE)) {
            this.parseSample (instrumentFile, indexedVelocityLayers, sampleNode, index);
            ++index;
        }
        multisampleSource.setVelocityLayers (new ArrayList<> (indexedVelocityLayers.values ()));


        return Collections.singletonList (multisampleSource);
    }

        /**
     * Parse the sample information.
     *
     * @param zipFile The multisample ZIP file
     * @param indexedVelocityLayers The indexed velocity layers
     * @param sampleNode The XML sample node
     */
    private void parseSample (final File instrumentFile, final Map<Integer, IVelocityLayer> indexedVelocityLayers, final org.w3c.dom.Node sampleNode, int index)
    {
        String samplePath = null;

        try (final ZipFile zipFile = new ZipFile (instrumentFile))
        {
            /*
             * Search corresponding sample in the archive
             * The correct sample is the one named  "SampleX ($somename).$extension" where X is the index of the Sample-Tag in the XML.
             */
            final String regex = "SampleData/Sample" + String.format("%02d (.+).[a-zA-Z]+", index);
            final var zipentries = zipFile.entries();
            while (zipentries.hasMoreElements())
            {
                var entry = zipentries.nextElement();
                if (entry.getName().matches(regex)) {
                    samplePath = entry.getName();
                    break;
                }
            }
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return;
        }

        final int groupIndex = 1;
        final IVelocityLayer velocityLayer = indexedVelocityLayers.computeIfAbsent (Integer.valueOf (groupIndex), groupIdx -> new DefaultVelocityLayer ("Velocity layer " + (groupIdx.intValue () + 1)));

        final int loopStart = safelyReadIntFromTag (sampleNode, RenoiseInstrumentTag.LOOPSTART, 0);
        final int loopEnd = safelyReadIntFromTag (sampleNode, RenoiseInstrumentTag.LOOPSTART, 0);
        final String loopMode = XMLUtils.readTextContent (XMLUtils.getChildElementByName(sampleNode, RenoiseInstrumentTag.LOOPSTART));
        final DefaultSampleLoop loop = new DefaultSampleLoop ();
        switch (loopMode)
        {
            default:
            case "Forward":
                loop.setType (LoopType.FORWARD);
                break;
            case "Backward":
                loop.setType (LoopType.BACKWARDS);
                break;
            case "PingPong":
                loop.setType (LoopType.ALTERNATING);
                break;
        }
        loop.setStart (loopStart);
        loop.setEnd (loopEnd);
        loop.setCrossfade (0);

        for (final org.w3c.dom.Node mappingNode: XMLUtils.getChildrenByName(sampleNode, RenoiseInstrumentTag.MAPPING)) {
            final DefaultSampleMetadata sampleMetadata = new DefaultSampleMetadata (instrumentFile, new File (samplePath));
            sampleMetadata.setKeyRoot (safelyReadIntFromTag (mappingNode, RenoiseInstrumentTag.BASENOTE, 0));
            sampleMetadata.setKeyLow (safelyReadIntFromTag (mappingNode, RenoiseInstrumentTag.NOTESTART, 0));
            sampleMetadata.setKeyHigh (safelyReadIntFromTag (mappingNode, RenoiseInstrumentTag.NOTEEND, 0));
            sampleMetadata.setKeyTracking (safelyReadBoolFromTag (mappingNode, RenoiseInstrumentTag.MAPKEYTOPITCH, true) ? 1.0 : 0.0);
            sampleMetadata.setVelocityLow(safelyReadIntFromTag (mappingNode, RenoiseInstrumentTag.VELOCITYSTART, 0));
            sampleMetadata.setVelocityHigh(safelyReadIntFromTag (mappingNode, RenoiseInstrumentTag.VELOCITYEND, 127));

            sampleMetadata.addLoop (loop);
            velocityLayer.addSampleMetadata(sampleMetadata);
        }
    }

    private int safelyReadIntFromTag(final org.w3c.dom.Node node, final String tagname, int error_value) {
        int result = error_value;
        try{
            final Element tag = XMLUtils.getChildElementByName(node, tagname);
            final String value = XMLUtils.readTextContent (tag);
            result = Integer.parseInt(value);
        }
        catch (NumberFormatException ex){
            ex.printStackTrace();
        }
        return result;
    }

    private Boolean safelyReadBoolFromTag(final org.w3c.dom.Node node, final String tagname, Boolean error_value) {
        Boolean result = error_value;
        try{
            final Element tag = XMLUtils.getChildElementByName(node, tagname);
            final String value = XMLUtils.readTextContent (tag);
            result = Boolean.parseBoolean(value);
        }
        catch (NumberFormatException ex){
            ex.printStackTrace();
        }
        return result;
    }
}
