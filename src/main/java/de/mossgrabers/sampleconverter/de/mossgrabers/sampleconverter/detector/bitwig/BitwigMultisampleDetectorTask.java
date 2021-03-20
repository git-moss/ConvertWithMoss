// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector.bitwig;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
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
import java.util.Optional;
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
        catch (final IOException ex)
        {
            this.notifier.get ().notifyError (Functions.getMessage ("IDS_NOTIFY_ERR_LOAD_FILE"), ex);
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

        /////////////////////////////////
        // Parse metadata

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

        /////////////////////////////////
        // Parse all samples

        final List<ISampleMetadata> sampleMetadata = new ArrayList<> ();
        for (final Element sampleElement: XMLUtils.getChildElementsByName (top, "sample"))
        {
            this.parseSample (sampleElement);
        }

        final List<List<ISampleMetadata>> layers = new ArrayList<> ();

        // TODO sort by layer
        layers.add (sampleMetadata);

        multisampleSource.setSampleMetadata (layers);

        return Optional.of (multisampleSource);
    }


    private void parseSample (final Element sampleElement)
    {
        // TODO Auto-generated method stub

    }
}
