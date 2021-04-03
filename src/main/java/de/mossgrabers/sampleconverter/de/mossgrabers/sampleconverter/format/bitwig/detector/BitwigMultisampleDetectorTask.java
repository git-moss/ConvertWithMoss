// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.bitwig.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.LoopType;
import de.mossgrabers.sampleconverter.core.PlayLogic;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.VelocityLayer;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.core.detector.MultisampleSource;
import de.mossgrabers.sampleconverter.format.bitwig.BitwigMultisampleTag;
import de.mossgrabers.sampleconverter.format.bitwig.BitwigSampleMetadata;
import de.mossgrabers.sampleconverter.util.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Detects recursively Bitwig multi-sample files in folders. Files must end with
 * <i>.multisample</i>.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BitwigMultisampleDetectorTask extends AbstractDetectorTask
{
    private final Map<String, Set<String>> unsupportedElements   = new HashMap<> ();
    private final Map<String, Set<String>> unsupportedAttributes = new HashMap<> ();


    /** {@inheritDoc} */
    @Override
    protected void detect (final File folder)
    {
        if (this.consumer.isEmpty ())
            return;

        // Detect all WAV files in the folder
        this.log ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ());
        if (this.waitForDelivery ())
            return;

        final File [] multisampleFiles = folder.listFiles ( (parent, name) -> {

            if (this.isCancelled ())
                return false;

            final File f = new File (parent, name);
            if (f.isDirectory ())
            {
                this.detect (f);
                return false;
            }
            return name.toLowerCase (Locale.US).endsWith (".multisample");
        });
        if (multisampleFiles.length == 0)
            return;

        for (final File multiSampleFile: multisampleFiles)
        {
            this.log ("IDS_NOTIFY_ANALYZING", multiSampleFile.getAbsolutePath ());

            if (this.waitForDelivery ())
                break;

            final Optional<IMultisampleSource> multisample = this.readFile (multiSampleFile);
            if (multisample.isPresent () && !this.isCancelled ())
                this.consumer.get ().accept (multisample.get ());
        }
    }


    /**
     * Read and parse the given Bitwig multi-sample file.
     *
     * @param multiSampleFile The file to process
     * @return The parse file information
     */
    private Optional<IMultisampleSource> readFile (final File multiSampleFile)
    {
        try (final ZipFile zipFile = new ZipFile (multiSampleFile))
        {
            final ZipEntry entry = zipFile.getEntry ("multisample.xml");
            if (entry == null)
            {
                this.log ("IDS_NOTIFY_ERR_NO_METADATA_FILE");
                return Optional.empty ();
            }

            return this.parseMetadataFile (multiSampleFile, zipFile, entry);
        }
        catch (final IOException ex)
        {
            this.log ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Optional.empty ();
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
    private Optional<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final ZipFile zipFile, final ZipEntry entry) throws IOException
    {
        if (this.waitForDelivery ())
            return Optional.empty ();

        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final Document document = XMLUtils.parseDocument (new InputSource (in));
            return this.parseDescription (multiSampleFile, document);
        }
        catch (final SAXException ex)
        {
            this.log ("IDS_NOTIFY_ERR_BAD_METADATA_FILE", ex);
            return Optional.empty ();
        }
    }


    /**
     * Process the multisample metadata file and the related wave files.
     *
     * @param multiSampleFile The multisample file
     * @param document The metadata XML document
     * @return The parsed multisample source
     */
    private Optional<IMultisampleSource> parseDescription (final File multiSampleFile, final Document document)
    {
        final Element top = document.getDocumentElement ();

        if (!BitwigMultisampleTag.MULTISAMPLE.equals (top.getNodeName ()))
        {
            this.log ("IDS_NOTIFY_ERR_BAD_METADATA_FILE");
            return Optional.empty ();
        }

        this.checkAttributes (BitwigMultisampleTag.MULTISAMPLE, top.getAttributes ());
        this.checkChildTags (BitwigMultisampleTag.MULTISAMPLE, BitwigMultisampleTag.TOP_LEVEL_TAGS, XMLUtils.getChildElements (top));

        final String name = top.getAttribute ("name");
        if (name.isBlank ())
        {
            this.log ("IDS_NOTIFY_ERR_BAD_METADATA_NO_NAME");
            return Optional.empty ();
        }

        final String [] parts = createPathParts (multiSampleFile.getParentFile (), this.sourceFolder.get (), name);

        final MultisampleSource multisampleSource = new MultisampleSource (multiSampleFile, parts, name);
        this.parseMetadata (top, multisampleSource);

        // Parse all groups
        final Map<Integer, IVelocityLayer> indexedVelocityLayers = new TreeMap<> ();
        final Node [] groupNodes = XMLUtils.getChildrenByName (top, BitwigMultisampleTag.GROUP);
        int groupCounter = 0;
        for (final Node groupNode: groupNodes)
        {
            if (groupNode instanceof final Element groupElement)
            {
                this.checkAttributes (BitwigMultisampleTag.GROUP, groupElement.getAttributes ());

                final String k = groupElement.getAttribute ("name");
                final String layerName = k.isBlank () ? "Velocity Layer " + (groupCounter + 1) : k;
                indexedVelocityLayers.put (Integer.valueOf (groupCounter), new VelocityLayer (layerName));
                groupCounter++;
            }
            else
            {
                this.log ("IDS_NOTIFY_ERR_BAD_METADATA_FILE");
                return Optional.empty ();
            }
        }
        // Additional layer for potentially un-grouped samples
        indexedVelocityLayers.put (Integer.valueOf (-1), new VelocityLayer ());

        // Parse (deprecated) layer tag
        final Node [] layerNodes = XMLUtils.getChildrenByName (top, BitwigMultisampleTag.LAYER);
        for (final Node layerNode: layerNodes)
        {
            if (layerNode instanceof final Element layerElement)
            {
                this.checkAttributes (BitwigMultisampleTag.LAYER, layerElement.getAttributes ());

                final String k = layerElement.getAttribute ("name");
                final String layerName = k.isBlank () ? "Velocity Layer " + (groupCounter + 1) : k;
                indexedVelocityLayers.put (Integer.valueOf (groupCounter), new VelocityLayer (layerName));
                groupCounter++;

                // Parse all samples of the layer
                for (final Element sampleElement: XMLUtils.getChildElementsByName (layerElement, BitwigMultisampleTag.SAMPLE))
                    this.parseSample (multiSampleFile, indexedVelocityLayers, sampleElement);
            }
            else
            {
                this.log ("IDS_NOTIFY_ERR_BAD_METADATA_FILE");
                return Optional.empty ();
            }
        }

        // Parse all top level samples
        for (final Element sampleElement: XMLUtils.getChildElementsByName (top, BitwigMultisampleTag.SAMPLE))
            this.parseSample (multiSampleFile, indexedVelocityLayers, sampleElement);

        multisampleSource.setVelocityLayers (new ArrayList<> (indexedVelocityLayers.values ()));

        this.printUnsupportedElements ();
        this.printUnsupportedAttributes ();

        return Optional.of (multisampleSource);
    }


    /**
     * Parse the Bitwig multisample description file.
     *
     * @param top The top XML element
     * @param multisampleSource Where to store the parsed information
     */
    private void parseMetadata (final Element top, final MultisampleSource multisampleSource)
    {
        final Element categoryTag = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.CATEGORY);
        if (categoryTag != null)
        {
            multisampleSource.setCategory (XMLUtils.readTextContent (categoryTag));
            this.checkAttributes (BitwigMultisampleTag.CATEGORY, categoryTag.getAttributes ());
        }

        final Element creatorTag = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.CREATOR);
        if (creatorTag != null)
        {
            multisampleSource.setCreator (XMLUtils.readTextContent (creatorTag));
            this.checkAttributes (BitwigMultisampleTag.CREATOR, creatorTag.getAttributes ());
        }

        final Element descriptionTag = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.DESCRIPTION);
        if (descriptionTag != null)
        {
            multisampleSource.setDescription (XMLUtils.readTextContent (descriptionTag));
            this.checkAttributes (BitwigMultisampleTag.DESCRIPTION, descriptionTag.getAttributes ());
        }

        final List<String> keywords = new ArrayList<> ();
        final Node keywordsElement = XMLUtils.getChildByName (top, BitwigMultisampleTag.KEYWORDS);
        if (keywordsElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.KEYWORDS, keywordsElement.getAttributes ());

            for (final Element keywordElement: XMLUtils.getChildElementsByName (keywordsElement, BitwigMultisampleTag.KEYWORD))
            {
                this.checkAttributes (BitwigMultisampleTag.KEYWORD, keywordElement.getAttributes ());

                final String k = XMLUtils.readTextContent (keywordElement);
                if (!k.isBlank ())
                    keywords.add (k);
            }
            multisampleSource.setKeywords (keywords.toArray (new String [keywords.size ()]));
        }
    }


    /**
     * Parse the sample information.
     *
     * @param zipFile The multisample ZIP file
     * @param indexedVelocityLayers The indexed velocity layers
     * @param sampleElement The XML sample element
     */
    private void parseSample (final File zipFile, final Map<Integer, IVelocityLayer> indexedVelocityLayers, final Element sampleElement)
    {
        this.checkAttributes (BitwigMultisampleTag.SAMPLE, sampleElement.getAttributes ());
        this.checkChildTags (BitwigMultisampleTag.SAMPLE, BitwigMultisampleTag.SAMPLE_TAGS, XMLUtils.getChildElements (sampleElement));

        final int groupIndex = XMLUtils.getIntegerAttribute (sampleElement, BitwigMultisampleTag.GROUP, -1);
        final IVelocityLayer velocityLayer = indexedVelocityLayers.computeIfAbsent (Integer.valueOf (groupIndex), groupIdx -> new VelocityLayer ("Velocity layer " + (groupIdx.intValue () + 1)));

        final String filename = sampleElement.getAttribute ("file");
        if (filename == null || filename.isBlank ())
        {
            this.log ("IDS_NOTIFY_ERR_BAD_METADATA_FILE");
            return;
        }

        final BitwigSampleMetadata sampleMetadata = new BitwigSampleMetadata (zipFile, filename);

        sampleMetadata.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, "sample-start", -1)));
        sampleMetadata.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, "sample-stop", -1)));
        sampleMetadata.setGain (XMLUtils.getDoubleAttribute (sampleElement, "gain", 0));
        sampleMetadata.setReversed (XMLUtils.getBooleanAttribute (sampleElement, "reverse", false));

        final String zoneLogic = sampleElement.getAttribute ("zone-logic");
        sampleMetadata.setPlayLogic (zoneLogic != null && "round-robin".equals (zoneLogic) ? PlayLogic.ROUND_ROBIN : PlayLogic.ALWAYS);

        final Element keyElement = XMLUtils.getChildElementByName (sampleElement, BitwigMultisampleTag.KEY);
        if (keyElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.KEY, keyElement.getAttributes ());

            sampleMetadata.setKeyRoot (XMLUtils.getIntegerAttribute (keyElement, "root", -1));
            sampleMetadata.setKeyLow (XMLUtils.getIntegerAttribute (keyElement, "low", -1));
            sampleMetadata.setKeyHigh (XMLUtils.getIntegerAttribute (keyElement, "high", -1));
            sampleMetadata.setNoteCrossfadeLow (XMLUtils.getIntegerAttribute (keyElement, "low-fade", -1));
            sampleMetadata.setNoteCrossfadeHigh (XMLUtils.getIntegerAttribute (keyElement, "high-fade", -1));
            sampleMetadata.setTune (XMLUtils.getDoubleAttribute (keyElement, "tune", 0));

            // Older multisample files use true/false
            final String attribute = keyElement.getAttribute ("track");
            if (attribute != null)
            {
                if ("true".equals (attribute))
                    sampleMetadata.setKeyTracking (1.0);
                else if ("false".equals (attribute))
                    sampleMetadata.setKeyTracking (0);
                else
                    sampleMetadata.setKeyTracking (XMLUtils.getDoubleAttribute (keyElement, "track", 0));
            }
        }

        final Element velocityElement = XMLUtils.getChildElementByName (sampleElement, BitwigMultisampleTag.VELOCITY);
        if (velocityElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.VELOCITY, velocityElement.getAttributes ());

            sampleMetadata.setVelocityLow (XMLUtils.getIntegerAttribute (velocityElement, "low", -1));
            sampleMetadata.setVelocityHigh (XMLUtils.getIntegerAttribute (velocityElement, "high", -1));
            sampleMetadata.setVelocityCrossfadeLow (XMLUtils.getIntegerAttribute (velocityElement, "low-fade", -1));
            sampleMetadata.setVelocityCrossfadeHigh (XMLUtils.getIntegerAttribute (velocityElement, "high-fade", -1));
        }

        final Element loopElement = XMLUtils.getChildElementByName (sampleElement, BitwigMultisampleTag.LOOP);
        if (loopElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.LOOP, loopElement.getAttributes ());

            final String attribute = loopElement.getAttribute ("mode");
            if (attribute != null)
            {
                final SampleLoop loop = new SampleLoop ();
                switch (attribute)
                {
                    default:
                    case "loop":
                        loop.setType (LoopType.FORWARD);
                        break;
                    case "ping-pong":
                        loop.setType (LoopType.ALTERNATING);
                        break;
                }
                loop.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (loopElement, "start", -1)));
                loop.setEnd ((int) Math.round (XMLUtils.getDoubleAttribute (loopElement, "stop", -1)));
                loop.setCrossfade (XMLUtils.getDoubleAttribute (loopElement, "fade", 0));
                if (loop.getCrossfade () > 0)
                {
                    try
                    {
                        sampleMetadata.addMissingInfoFromWaveFile ();
                    }
                    catch (final IOException ex)
                    {
                        this.log ("IDS_NOTIFY_ERR_BROKEN_WAV", ex.getMessage ());
                    }
                }
                sampleMetadata.addLoop (loop);
            }
        }

        velocityLayer.addSampleMetadata (sampleMetadata);
    }


    /**
     * Check for unsupported child tags of a tag.
     *
     * @param tagName The name of the tag to check for its' attributes
     * @param supportedElements The supported child elements
     * @param childElements The present child elements
     */
    private void checkChildTags (final String tagName, final Set<String> supportedElements, final Element [] childElements)
    {
        for (final Element childElement: childElements)
        {
            final String nodeName = childElement.getNodeName ();
            if (!supportedElements.contains (nodeName))
                this.unsupportedElements.computeIfAbsent (tagName, tag -> new HashSet<> ()).add (nodeName);
        }
    }


    /**
     * Formats and reports all unsupported elements.
     */
    private void printUnsupportedElements ()
    {
        String tagName;
        for (final Entry<String, Set<String>> entry: this.unsupportedElements.entrySet ())
        {
            tagName = entry.getKey ();

            final StringBuilder sb = new StringBuilder ();
            entry.getValue ().forEach (element -> {
                if (!sb.isEmpty ())
                    sb.append (", ");
                sb.append (element);
            });

            if (!sb.isEmpty ())
                this.log ("IDS_NOTIFY_UNSUPPORTED_ELEMENTS", tagName, sb.toString ());
        }
    }


    /**
     * Check for unsupported attributes of a tag.
     *
     * @param tagName The name of the tag to check for its' attributes
     * @param attributes The present attributes
     */
    private void checkAttributes (final String tagName, final NamedNodeMap attributes)
    {
        final Set<String> supportedAttributes = BitwigMultisampleTag.getAttributes (tagName);

        for (int i = 0; i < attributes.getLength (); i++)
        {
            final String nodeName = attributes.item (i).getNodeName ();
            if (!supportedAttributes.contains (nodeName))
                this.unsupportedAttributes.computeIfAbsent (tagName, tag -> new HashSet<> ()).add (nodeName);
        }
    }


    /**
     * Formats and reports all unsupported attributes.
     */
    private void printUnsupportedAttributes ()
    {
        String tagName;
        for (final Entry<String, Set<String>> entry: this.unsupportedAttributes.entrySet ())
        {
            tagName = entry.getKey ();

            final StringBuilder sb = new StringBuilder ();
            entry.getValue ().forEach (attribute -> {
                if (!sb.isEmpty ())
                    sb.append (", ");
                sb.append (attribute);
            });

            if (!sb.isEmpty ())
                this.log ("IDS_NOTIFY_UNSUPPORTED_ATTRIBUTES", tagName, sb.toString ());
        }
    }
}
