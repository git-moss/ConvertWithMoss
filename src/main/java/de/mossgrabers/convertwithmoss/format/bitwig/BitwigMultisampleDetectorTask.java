// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultVelocityLayer;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
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
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     */
    protected BitwigMultisampleDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        super (notifier, consumer, sourceFolder, null, ".multisample");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        try (final ZipFile zipFile = new ZipFile (file))
        {
            final ZipEntry entry = zipFile.getEntry ("multisample.xml");
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
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final ZipFile zipFile, final ZipEntry entry) throws IOException
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final Document document = XMLUtils.parseDocument (new InputSource (in));
            return this.parseDescription (multiSampleFile, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Process the multisample metadata file and the related wave files.
     *
     * @param multiSampleFile The multisample file
     * @param document The metadata XML document
     * @return The parsed multisample source
     */
    private List<IMultisampleSource> parseDescription (final File multiSampleFile, final Document document)
    {
        final Element top = document.getDocumentElement ();

        if (!BitwigMultisampleTag.MULTISAMPLE.equals (top.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        this.checkAttributes (BitwigMultisampleTag.MULTISAMPLE, top.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.MULTISAMPLE));
        this.checkChildTags (BitwigMultisampleTag.MULTISAMPLE, BitwigMultisampleTag.TOP_LEVEL_TAGS, XMLUtils.getChildElements (top));

        final String name = top.getAttribute ("name");
        if (name.isBlank ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_NO_NAME");
            return Collections.emptyList ();
        }

        final String [] parts = createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, name);

        final MultisampleSource multisampleSource = new MultisampleSource (multiSampleFile, parts, name, this.subtractPaths (this.sourceFolder, multiSampleFile));
        this.parseMetadata (top, multisampleSource);

        // Parse all groups
        final Map<Integer, IVelocityLayer> indexedVelocityLayers = new TreeMap<> ();
        final Node [] groupNodes = XMLUtils.getChildrenByName (top, BitwigMultisampleTag.GROUP);
        int groupCounter = 0;
        for (final Node groupNode: groupNodes)
        {
            if (groupNode instanceof final Element groupElement)
            {
                this.checkAttributes (BitwigMultisampleTag.GROUP, groupElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.GROUP));

                final String k = groupElement.getAttribute ("name");
                final String layerName = k.isBlank () ? "Velocity Layer " + (groupCounter + 1) : k;
                indexedVelocityLayers.put (Integer.valueOf (groupCounter), new DefaultVelocityLayer (layerName));
                groupCounter++;
            }
            else
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                return Collections.emptyList ();
            }
        }
        // Additional layer for potentially un-grouped samples
        indexedVelocityLayers.put (Integer.valueOf (-1), new DefaultVelocityLayer ());

        // Parse (deprecated) layer tag
        final Node [] layerNodes = XMLUtils.getChildrenByName (top, BitwigMultisampleTag.LAYER);
        for (final Node layerNode: layerNodes)
        {
            if (layerNode instanceof final Element layerElement)
            {
                this.checkAttributes (BitwigMultisampleTag.LAYER, layerElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.LAYER));

                final String k = layerElement.getAttribute ("name");
                final String layerName = k == null || k.isBlank () ? "Velocity Layer " + (groupCounter + 1) : k;
                indexedVelocityLayers.put (Integer.valueOf (groupCounter), new DefaultVelocityLayer (layerName));
                groupCounter++;

                // Parse all samples of the layer
                for (final Element sampleElement: XMLUtils.getChildElementsByName (layerElement, BitwigMultisampleTag.SAMPLE))
                    this.parseSample (multiSampleFile, indexedVelocityLayers, sampleElement);
            }
            else
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                return Collections.emptyList ();
            }
        }

        // Parse all top level samples
        for (final Element sampleElement: XMLUtils.getChildElementsByName (top, BitwigMultisampleTag.SAMPLE))
            this.parseSample (multiSampleFile, indexedVelocityLayers, sampleElement);

        multisampleSource.setVelocityLayers (new ArrayList<> (indexedVelocityLayers.values ()));

        this.printUnsupportedElements ();
        this.printUnsupportedAttributes ();

        return Collections.singletonList (multisampleSource);
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
            this.checkAttributes (BitwigMultisampleTag.CATEGORY, categoryTag.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.CATEGORY));
        }

        final Element creatorTag = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.CREATOR);
        if (creatorTag != null)
        {
            multisampleSource.setCreator (XMLUtils.readTextContent (creatorTag));
            this.checkAttributes (BitwigMultisampleTag.CREATOR, creatorTag.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.CREATOR));
        }

        final Element descriptionTag = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.DESCRIPTION);
        if (descriptionTag != null)
        {
            multisampleSource.setDescription (XMLUtils.readTextContent (descriptionTag));
            this.checkAttributes (BitwigMultisampleTag.DESCRIPTION, descriptionTag.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.DESCRIPTION));
        }

        final List<String> keywords = new ArrayList<> ();
        final Node keywordsElement = XMLUtils.getChildByName (top, BitwigMultisampleTag.KEYWORDS);
        if (keywordsElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.KEYWORDS, keywordsElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.KEYWORDS));

            for (final Element keywordElement: XMLUtils.getChildElementsByName (keywordsElement, BitwigMultisampleTag.KEYWORD))
            {
                this.checkAttributes (BitwigMultisampleTag.KEYWORD, keywordElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.KEYWORD));

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
        this.checkAttributes (BitwigMultisampleTag.SAMPLE, sampleElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.SAMPLE));
        this.checkChildTags (BitwigMultisampleTag.SAMPLE, BitwigMultisampleTag.SAMPLE_TAGS, XMLUtils.getChildElements (sampleElement));

        final int groupIndex = XMLUtils.getIntegerAttribute (sampleElement, BitwigMultisampleTag.GROUP, -1);
        final IVelocityLayer velocityLayer = indexedVelocityLayers.computeIfAbsent (Integer.valueOf (groupIndex), groupIdx -> new DefaultVelocityLayer ("Velocity layer " + (groupIdx.intValue () + 1)));

        final String filename = sampleElement.getAttribute ("file");
        if (filename == null || filename.isBlank ())
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return;
        }

        final DefaultSampleMetadata sampleMetadata = new DefaultSampleMetadata (zipFile, new File (filename));

        sampleMetadata.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, "sample-start", -1)));
        sampleMetadata.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, "sample-stop", -1)));
        sampleMetadata.setGain (XMLUtils.getDoubleAttribute (sampleElement, "gain", 0));
        sampleMetadata.setReversed (XMLUtils.getBooleanAttribute (sampleElement, "reverse", false));

        final String zoneLogic = sampleElement.getAttribute ("zone-logic");
        sampleMetadata.setPlayLogic (zoneLogic != null && "round-robin".equals (zoneLogic) ? PlayLogic.ROUND_ROBIN : PlayLogic.ALWAYS);

        final Element keyElement = XMLUtils.getChildElementByName (sampleElement, BitwigMultisampleTag.KEY);
        if (keyElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.KEY, keyElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.KEY));

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
            this.checkAttributes (BitwigMultisampleTag.VELOCITY, velocityElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.VELOCITY));

            sampleMetadata.setVelocityLow (XMLUtils.getIntegerAttribute (velocityElement, "low", -1));
            sampleMetadata.setVelocityHigh (XMLUtils.getIntegerAttribute (velocityElement, "high", -1));
            sampleMetadata.setVelocityCrossfadeLow (XMLUtils.getIntegerAttribute (velocityElement, "low-fade", -1));
            sampleMetadata.setVelocityCrossfadeHigh (XMLUtils.getIntegerAttribute (velocityElement, "high-fade", -1));
        }

        final Element loopElement = XMLUtils.getChildElementByName (sampleElement, BitwigMultisampleTag.LOOP);
        if (loopElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.LOOP, loopElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.LOOP));

            final String attribute = loopElement.getAttribute ("mode");
            if (attribute != null)
            {
                final DefaultSampleLoop loop = new DefaultSampleLoop ();
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
                sampleMetadata.addLoop (loop);
            }
        }

        this.loadMissingValues (sampleMetadata);

        velocityLayer.addSampleMetadata (sampleMetadata);
    }
}
