// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively Bitwig multi-sample files in folders. Files must end with
 * <i>.multisample</i>.
 *
 * @author Jürgen Moßgraber
 */
public class BitwigMultisampleDetectorTask extends AbstractDetectorTask
{
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
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
     * Process the multi-sample metadata file and the related wave files.
     *
     * @param multiSampleFile The multi-sample file
     * @param document The metadata XML document
     * @return The parsed multi-sample source
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

        final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, name);

        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        final IMetadata metadata = multisampleSource.getMetadata ();
        this.parseMetadata (top, metadata);

        try
        {
            final BasicFileAttributes attrs = Files.readAttributes (multiSampleFile.toPath (), BasicFileAttributes.class);
            final FileTime creationTime = attrs.creationTime ();
            final FileTime modifiedTime = attrs.lastModifiedTime ();
            final long creationTimeMillis = creationTime.toMillis ();
            final long modifiedTimeMillis = modifiedTime.toMillis ();
            metadata.setCreationDateTime (new Date (creationTimeMillis < modifiedTimeMillis ? creationTimeMillis : modifiedTimeMillis));
        }
        catch (final IOException ex)
        {
            metadata.setCreationDateTime (new Date ());
        }

        // Parse all groups
        final Map<Integer, IGroup> indexedGroups = new TreeMap<> ();
        int groupCounter = 0;
        for (final Node groupNode: XMLUtils.getChildElementsByName (top, BitwigMultisampleTag.GROUP))
            if (groupNode instanceof final Element groupElement)
            {
                this.checkAttributes (BitwigMultisampleTag.GROUP, groupElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.GROUP));

                final String k = groupElement.getAttribute ("name");
                final String groupName = k.isBlank () ? "Group " + (groupCounter + 1) : k;
                indexedGroups.put (Integer.valueOf (groupCounter), new DefaultGroup (groupName));
                groupCounter++;
            }
            else
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                return Collections.emptyList ();
            }
        // Additional group for potentially un-grouped samples
        indexedGroups.put (Integer.valueOf (-1), new DefaultGroup ());

        // Parse (deprecated) layer tag
        for (final Node layerNode: XMLUtils.getChildElementsByName (top, BitwigMultisampleTag.LAYER))
            if (layerNode instanceof final Element layerElement)
            {
                this.checkAttributes (BitwigMultisampleTag.LAYER, layerElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.LAYER));

                final String k = layerElement.getAttribute ("name");
                final String groupName = k == null || k.isBlank () ? "Group " + (groupCounter + 1) : k;
                indexedGroups.put (Integer.valueOf (groupCounter), new DefaultGroup (groupName));
                groupCounter++;

                // Parse all samples of the layer
                for (final Element sampleElement: XMLUtils.getChildElementsByName (layerElement, BitwigMultisampleTag.SAMPLE, false))
                    this.parseSample (multiSampleFile, indexedGroups, sampleElement);
            }
            else
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                return Collections.emptyList ();
            }

        // Parse all top level samples
        for (final Element sampleElement: XMLUtils.getChildElementsByName (top, BitwigMultisampleTag.SAMPLE, false))
            this.parseSample (multiSampleFile, indexedGroups, sampleElement);

        multisampleSource.setGroups (new ArrayList<> (indexedGroups.values ()));

        this.printUnsupportedElements ();
        this.printUnsupportedAttributes ();

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parse the metadata description file.
     *
     * @param top The top XML element
     * @param metadata Where to store the parsed information
     */
    private void parseMetadata (final Element top, final IMetadata metadata)
    {
        final Element categoryTag = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.CATEGORY);
        if (categoryTag != null)
        {
            metadata.setCategory (XMLUtils.readTextContent (categoryTag));
            this.checkAttributes (BitwigMultisampleTag.CATEGORY, categoryTag.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.CATEGORY));
        }

        final Element creatorTag = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.CREATOR);
        if (creatorTag != null)
        {
            metadata.setCreator (XMLUtils.readTextContent (creatorTag));
            this.checkAttributes (BitwigMultisampleTag.CREATOR, creatorTag.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.CREATOR));
        }

        final Element descriptionTag = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.DESCRIPTION);
        if (descriptionTag != null)
        {
            metadata.setDescription (XMLUtils.readTextContent (descriptionTag));
            this.checkAttributes (BitwigMultisampleTag.DESCRIPTION, descriptionTag.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.DESCRIPTION));
        }

        final List<String> keywords = new ArrayList<> ();
        final Element keywordsElement = XMLUtils.getChildElementByName (top, BitwigMultisampleTag.KEYWORDS);
        if (keywordsElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.KEYWORDS, keywordsElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.KEYWORDS));

            for (final Element keywordElement: XMLUtils.getChildElementsByName (keywordsElement, BitwigMultisampleTag.KEYWORD, false))
            {
                this.checkAttributes (BitwigMultisampleTag.KEYWORD, keywordElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.KEYWORD));

                final String k = XMLUtils.readTextContent (keywordElement);
                if (!k.isBlank ())
                    keywords.add (k);
            }
            metadata.setKeywords (keywords.toArray (new String [keywords.size ()]));
        }
    }


    /**
     * Parse the sample information.
     *
     * @param zipFile The multisample ZIP file
     * @param indexedGroups The indexed groups
     * @param sampleElement The XML sample element
     */
    private void parseSample (final File zipFile, final Map<Integer, IGroup> indexedGroups, final Element sampleElement)
    {
        this.checkAttributes (BitwigMultisampleTag.SAMPLE, sampleElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.SAMPLE));
        this.checkChildTags (BitwigMultisampleTag.SAMPLE, BitwigMultisampleTag.SAMPLE_TAGS, XMLUtils.getChildElements (sampleElement));

        final int groupIndex = XMLUtils.getIntegerAttribute (sampleElement, BitwigMultisampleTag.GROUP, -1);
        final IGroup group = indexedGroups.computeIfAbsent (Integer.valueOf (groupIndex), groupIdx -> new DefaultGroup ("Group " + (groupIdx.intValue () + 1)));

        final String filename = sampleElement.getAttribute ("file");
        if (filename == null || filename.isBlank ())
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return;
        }

        final File file = new File (filename);
        final WavFileSampleData sampleData;
        try
        {
            sampleData = new WavFileSampleData (zipFile, file);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return;
        }
        final DefaultSampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (file), sampleData);

        zone.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, "sample-start", -1)));
        zone.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, "sample-stop", -1)));
        zone.setGain (XMLUtils.getDoubleAttribute (sampleElement, "gain", 0));
        zone.setReversed (XMLUtils.getBooleanAttribute (sampleElement, "reverse", false));

        final String zoneLogic = sampleElement.getAttribute ("zone-logic");
        zone.setPlayLogic (zoneLogic != null && "round-robin".equals (zoneLogic) ? PlayLogic.ROUND_ROBIN : PlayLogic.ALWAYS);

        final Element keyElement = XMLUtils.getChildElementByName (sampleElement, BitwigMultisampleTag.KEY);
        if (keyElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.KEY, keyElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.KEY));

            zone.setKeyRoot (XMLUtils.getIntegerAttribute (keyElement, "root", -1));
            zone.setKeyLow (XMLUtils.getIntegerAttribute (keyElement, "low", -1));
            zone.setKeyHigh (XMLUtils.getIntegerAttribute (keyElement, "high", -1));
            zone.setNoteCrossfadeLow (XMLUtils.getIntegerAttribute (keyElement, "low-fade", -1));
            zone.setNoteCrossfadeHigh (XMLUtils.getIntegerAttribute (keyElement, "high-fade", -1));
            zone.setTune (XMLUtils.getDoubleAttribute (keyElement, "tune", 0));

            // Older multisample files use true/false
            final String attribute = keyElement.getAttribute ("track");
            if (attribute != null)
                if ("true".equals (attribute))
                    zone.setKeyTracking (1.0);
                else if ("false".equals (attribute))
                    zone.setKeyTracking (0);
                else
                    zone.setKeyTracking (XMLUtils.getDoubleAttribute (keyElement, "track", 0));
        }

        final Element velocityElement = XMLUtils.getChildElementByName (sampleElement, BitwigMultisampleTag.VELOCITY);
        if (velocityElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.VELOCITY, velocityElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.VELOCITY));

            zone.setVelocityLow (XMLUtils.getIntegerAttribute (velocityElement, "low", -1));
            zone.setVelocityHigh (XMLUtils.getIntegerAttribute (velocityElement, "high", -1));
            zone.setVelocityCrossfadeLow (XMLUtils.getIntegerAttribute (velocityElement, "low-fade", -1));
            zone.setVelocityCrossfadeHigh (XMLUtils.getIntegerAttribute (velocityElement, "high-fade", -1));
        }

        final Element loopElement = XMLUtils.getChildElementByName (sampleElement, BitwigMultisampleTag.LOOP);
        if (loopElement != null)
        {
            this.checkAttributes (BitwigMultisampleTag.LOOP, loopElement.getAttributes (), BitwigMultisampleTag.getAttributes (BitwigMultisampleTag.LOOP));

            final String attribute = loopElement.getAttribute ("mode");
            if (attribute != null && !"off".equalsIgnoreCase (attribute))
            {
                final DefaultSampleLoop loop = new DefaultSampleLoop ();
                switch (attribute)
                {
                    default:
                    case "loop":
                        loop.setType (LoopType.FORWARDS);
                        break;
                    case "ping-pong":
                        loop.setType (LoopType.ALTERNATING);
                        break;
                }
                loop.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (loopElement, "start", -1)));
                loop.setEnd ((int) Math.round (XMLUtils.getDoubleAttribute (loopElement, "stop", -1)));
                loop.setCrossfade (XMLUtils.getDoubleAttribute (loopElement, "fade", 0));
                zone.addLoop (loop);
            }
        }

        try
        {
            zone.getSampleData ().addZoneData (zone, false, false);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex);
        }

        group.addSampleZone (zone);
    }
}
