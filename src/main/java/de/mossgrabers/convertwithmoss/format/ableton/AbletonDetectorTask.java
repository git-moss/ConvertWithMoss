// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
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
            return this.parseMetadataFile (file, multiSampleFileContent);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> parseMetadataFile ()
    {
        // TODO Auto-generated method stub
        return null;
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The file
     * @param multiSampleFileContent The XML description file content
     * @return The result
     * @throws IOException Error reading the file
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String multiSampleFileContent) throws IOException
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

        if (!AbletonTag.ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_TAG", AbletonTag.ROOT);
            return Collections.emptyList ();
        }

        final String creator = top.getAttribute (AbletonTag.ATTR_CREATOR);

        Element deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.DEVICE_SIMPLER);
        if (deviceElement == null)
            deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.DEVICE_SAMPLER);
        if (deviceElement != null)
            return Collections.singletonList (this.parseSampler (multiSampleFile, deviceElement, creator));

        deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.DEVICE_SAMPLER);
        if (deviceElement != null)
            return Collections.singletonList (this.parseRack (multiSampleFile, deviceElement, creator));

        this.notifier.logError ("IDS_ADV_NOT_A_SAMPLER_PRESET");
        return Collections.emptyList ();
    }


    /**
     * Parse an Ableton preset XML document with a Simpler or Sampler device.
     *
     * @param multiSampleFile The multi-sample source file
     * @param deviceElement The device element
     * @param creator The creator value
     * @return The parse multi-sample source
     * @throws IOException Could not parse the document
     */
    private IMultisampleSource parseSampler (final File multiSampleFile, final Element deviceElement, final String creator) throws IOException
    {
        final String name = this.getValueAttribute (deviceElement, AbletonTag.USER_NAME);

        if (name.isBlank ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_BAD_METADATA_NO_NAME"));
        final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        final IMetadata metadata = multisampleSource.getMetadata ();
        parseMetadata (deviceElement, metadata, creator);

        final Element playerElement = XMLUtils.getChildElementByName (deviceElement, AbletonTag.PLAYER);
        if (playerElement == null)
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_TAG", AbletonTag.PLAYER);

        this.parseMultiSample (multiSampleFile, multisampleSource, playerElement);

        this.createMetadata (metadata, this.getFirstSample (multisampleSource.getGroups ()), parts);

        return multisampleSource;
    }


    private void parseMultiSample (final File multiSampleFile, final DefaultMultisampleSource multisampleSource, final Element playerElement) throws IOException
    {
        final Element mapElement = XMLUtils.getChildElementByName (playerElement, AbletonTag.MULTI_SAMPLE_MAP);
        if (mapElement == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_TAG", AbletonTag.MULTI_SAMPLE_MAP);
            return;
        }
        final Element samplePartsElement = XMLUtils.getChildElementByName (playerElement, AbletonTag.SAMPLE_PARTS);
        if (samplePartsElement == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_TAG", AbletonTag.SAMPLE_PARTS);
            return;
        }

        final IGroup group = new DefaultGroup ("Group #1");
        multisampleSource.setGroups (Collections.singletonList (group));

        final Element [] multiSamplePartElements = XMLUtils.getChildElementsByName (samplePartsElement, AbletonTag.MULTI_SAMPLE_PART, false);
        for (final Element multiSamplePartElement: multiSamplePartElements)
        {
            final String zoneName = this.getValueAttribute (multiSamplePartElement, AbletonTag.NAME);

            final Element sampleRefElement = XMLUtils.getChildElementByName (multiSamplePartElement, AbletonTag.SAMPLE_REF);
            if (sampleRefElement == null)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_TAG", AbletonTag.SAMPLE_REF);
                return;
            }
            final Element fileRefElement = XMLUtils.getChildElementByName (sampleRefElement, AbletonTag.FILE_REF);
            if (fileRefElement == null)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_TAG", AbletonTag.FILE_REF);
                return;
            }

            final String relativePath = this.getValueAttribute (fileRefElement, AbletonTag.RELATIVE_PATH);

            final ISampleData sampleData = createSampleData (new File (multiSampleFile.getParentFile (), relativePath));

            final ISampleZone zone = new DefaultSampleZone (zoneName, sampleData);

            // TODO read zone parameters

            group.addSampleZone (zone);
        }
    }


    /**
     * Parse an Ableton preset XML document with a Rack device.
     *
     * @param multiSampleFile The multi-sample source file
     * @param deviceElement The device element
     * @param creator The creator value
     * @return The parse multi-sample source
     */
    private IMultisampleSource parseRack (final File multiSampleFile, final Element deviceElement, final String creator)
    {
        // TODO -> Device -> InstrumentGroupDevice (1..N) -> parseSampler
        return null;
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
        final Element descriptionTag = XMLUtils.getChildElementByName (top, AbletonTag.ANNOTATION);
        if (descriptionTag != null)
            metadata.setDescription (XMLUtils.readTextContent (descriptionTag));

        if (creator != null && !creator.isBlank ())
            metadata.setCreator (creator);
    }


    private String getValueAttribute (final Element deviceElement, final String elementTag)
    {
        final Element element = XMLUtils.getChildElementByName (deviceElement, elementTag);
        if (element == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_TAG", elementTag);
            return "";
        }
        return element.getAttribute (AbletonTag.ATTR_VALUE);
    }
}
