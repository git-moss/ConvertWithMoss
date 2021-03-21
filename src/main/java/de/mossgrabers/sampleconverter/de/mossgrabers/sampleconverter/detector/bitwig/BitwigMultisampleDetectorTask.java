// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector.bitwig;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.VelocityLayer;
import de.mossgrabers.sampleconverter.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.detector.MultisampleSource;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.util.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Detects recursivly Bitwig multisample files in folders. Files must end with <i>.multisample</i>.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BitwigMultisampleDetectorTask extends AbstractDetectorTask
{
    /** {@inheritDoc} */
    @Override
    protected void detect (final File folder)
    {
        if (this.consumer.isEmpty ())
            return;

        // Detect all wav files in the folder
        this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ()));
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
            this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ANALYZING", multiSampleFile.getAbsolutePath ()));

            if (this.waitForDelivery ())
                break;

            final Optional<IMultisampleSource> multisample = this.readFile (multiSampleFile);
            if (multisample.isPresent ())
                this.consumer.get ().accept (multisample.get ());
        }
    }


    /**
     * Read and parse the given Bitwig multisqample file.
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
                this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ERR_NO_METADATA_FILE"));
                return Optional.empty ();
            }

            return this.parseMetadataFile (multiSampleFile, zipFile, entry);
        }
        catch (final IOException ex)
        {
            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_LOAD_FILE"), ex);
            return Optional.empty ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The file
     * @param zipFile The ZIP file which contains the description file
     * @param entry THe zip entry of the file
     * @return The result
     * @throws IOException Error reading the file
     */
    private Optional<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final ZipFile zipFile, final ZipEntry entry) throws IOException
    {
        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final Document document = XMLUtils.parseDocument (new InputSource (in));
            return this.parseDescription (multiSampleFile, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_BAD_METADATA_FILE"), ex);
            return Optional.empty ();
        }
    }


    private Optional<IMultisampleSource> parseDescription (final File multiSampleFile, final Document document)
    {
        final Element top = document.getDocumentElement ();

        if (!"multisample".equals (top.getNodeName ()))
        {
            this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ERR_BAD_METADATA_FILE"));
            return Optional.empty ();
        }

        final String name = top.getAttribute ("name");
        if (name.isBlank ())
        {
            this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ERR_BAD_METADATA_NO_NAME"));
            return Optional.empty ();
        }

        final String [] parts = createPathParts (multiSampleFile.getParentFile (), this.sourceFolder.get (), name);

        final MultisampleSource multisampleSource = new MultisampleSource (multiSampleFile, parts, name);
        parseMetadata (top, multisampleSource);

        // Parse all groups
        final Map<Integer, IVelocityLayer> indexedVelocityLayers = new TreeMap<> ();
        final Node [] groupNodes = XMLUtils.getChildrenByName (top, "group");
        for (int i = 0; i < groupNodes.length; i++)
        {
            if (groupNodes[i]instanceof Element groupElement)
            {
                final String k = groupElement.getAttribute ("name");
                final String layerName = k.isBlank () ? "Velocity Layer " + (i + 1) : k;
                indexedVelocityLayers.put (Integer.valueOf (i), new VelocityLayer (layerName));
            }
            else
            {
                this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ERR_BAD_METADATA_FILE"));
                return Optional.empty ();
            }
        }
        indexedVelocityLayers.put (Integer.valueOf (-1), new VelocityLayer ());

        // Parse all samples
        for (final Element sampleElement: XMLUtils.getChildElementsByName (top, "sample"))
            this.parseSample (multiSampleFile, indexedVelocityLayers, sampleElement);
        multisampleSource.setVelocityLayers (new ArrayList<> (indexedVelocityLayers.values ()));

        return Optional.of (multisampleSource);
    }


    private static void parseMetadata (final Element top, final MultisampleSource multisampleSource)
    {
        final String category = XMLUtils.getChildElementContent (top, "category");
        if (category != null)
            multisampleSource.setCategory (category);

        final String creator = XMLUtils.getChildElementContent (top, "creator");
        if (creator != null)
            multisampleSource.setCreator (creator);

        final String description = XMLUtils.getChildElementContent (top, "description");
        if (description != null)
            multisampleSource.setDescription (description);

        final List<String> keywords = new ArrayList<> ();
        final Node keywordsElement = XMLUtils.getChildByName (top, "keywords");
        if (keywordsElement != null)
        {
            for (final Element keywordElement: XMLUtils.getChildElementsByName (keywordsElement, "keyword"))
            {
                final String k = XMLUtils.readTextContent (keywordElement);
                if (!k.isBlank ())
                    keywords.add (k);
            }
            multisampleSource.setKeywords (keywords.toArray (new String [keywords.size ()]));
        }
    }


    private void parseSample (File zipFile, final Map<Integer, IVelocityLayer> indexedVelocityLayers, final Element sampleElement)
    {
        final int groupIndex = XMLUtils.getIntegerAttribute (sampleElement, "group", -1);
        final IVelocityLayer velocityLayer = indexedVelocityLayers.computeIfAbsent (Integer.valueOf (groupIndex), groupIdx -> new VelocityLayer ("Velocity layer " + (groupIdx.intValue () + 1)));

        final String filename = sampleElement.getAttribute ("file");
        if (filename == null || filename.isBlank ())
        {
            this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ERR_BAD_METADATA_FILE"));
            return;
        }

        final BitwigSampleMetadata sampleMetadata = new BitwigSampleMetadata (zipFile, filename);

        sampleMetadata.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, "sample-start", -1)));
        sampleMetadata.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, "sample-stop", -1)));

        final Element keyElement = XMLUtils.getChildElementByName (sampleElement, "key");
        if (keyElement != null)
        {
            sampleMetadata.setKeyRoot (XMLUtils.getIntegerAttribute (keyElement, "root", -1));
            sampleMetadata.setKeyLow (XMLUtils.getIntegerAttribute (keyElement, "low", -1));
            sampleMetadata.setKeyHigh (XMLUtils.getIntegerAttribute (keyElement, "high", -1));
            sampleMetadata.setNoteCrossfadeLow (XMLUtils.getIntegerAttribute (keyElement, "low-fade", -1));
            sampleMetadata.setNoteCrossfadeHigh (XMLUtils.getIntegerAttribute (keyElement, "high-fade", -1));

        }

        final Element velocityElement = XMLUtils.getChildElementByName (sampleElement, "velocity");
        if (velocityElement != null)
        {
            sampleMetadata.setVelocityLow (XMLUtils.getIntegerAttribute (velocityElement, "low", -1));
            sampleMetadata.setVelocityHigh (XMLUtils.getIntegerAttribute (velocityElement, "high", -1));
            sampleMetadata.setVelocityCrossfadeLow (XMLUtils.getIntegerAttribute (velocityElement, "low-fade", -1));
            sampleMetadata.setVelocityCrossfadeHigh (XMLUtils.getIntegerAttribute (velocityElement, "high-fade", -1));
        }

        final Element loopElement = XMLUtils.getChildElementByName (sampleElement, "loop");
        if (loopElement != null)
        {
            final String attribute = loopElement.getAttribute ("mode");
            // TODO check for other values (ping-pong)
            final boolean hasLoop = attribute != null && "loop".equals (attribute);
            sampleMetadata.setHasLoop (hasLoop);

            if (hasLoop)
            {
                sampleMetadata.setLoopStart ((int) Math.round (XMLUtils.getDoubleAttribute (loopElement, "start", -1)));
                sampleMetadata.setLoopEnd ((int) Math.round (XMLUtils.getDoubleAttribute (loopElement, "stop", -1)));
            }
        }

        velocityLayer.addSampleMetadata (sampleMetadata);
    }
}
