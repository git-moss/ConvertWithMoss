// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.TransformerException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipOutputStream;


/**
 * Creator for Bitwig multi-sample files. Such a file is a renamed ZIP file with the ending
 * "multisample" and contains all WAV files and a metadata description file (multisample.xml).
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BitwigMultisampleCreator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public BitwigMultisampleCreator (final INotifier notifier)
    {
        super ("Bitwig Multisample", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final File multiFile = new File (destinationFolder, createSafeFilename (multisampleSource.getName ()) + ".multisample");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        final Optional<String> metadata = this.createMetadata (multisampleSource);
        if (metadata.isEmpty ())
            return;

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            zos.setMethod (ZipOutputStream.STORED);
            this.storeTextFile (zos, "multisample.xml", metadata.get ());
            this.storeSampleFiles (zos, null, multisampleSource);
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the text of the description file.
     *
     * @param multisampleSource The multi-sample
     * @return The XML structure
     */
    private Optional<String> createMetadata (final IMultisampleSource multisampleSource)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element multisampleElement = document.createElement ("multisample");
        document.appendChild (multisampleElement);
        multisampleElement.setAttribute ("name", multisampleSource.getName ());

        XMLUtils.addTextElement (document, multisampleElement, "generator", "ConvertWithMoss");
        XMLUtils.addTextElement (document, multisampleElement, "category", multisampleSource.getCategory ());
        XMLUtils.addTextElement (document, multisampleElement, "creator", multisampleSource.getCreator ());
        XMLUtils.addTextElement (document, multisampleElement, "description", multisampleSource.getDescription ());

        final Element keywordsElement = XMLUtils.addElement (document, multisampleElement, "keywords");
        for (final String keyword: multisampleSource.getKeywords ())
            XMLUtils.addTextElement (document, keywordsElement, "keyword", keyword);

        final List<IVelocityLayer> velocityLayers = multisampleSource.getNonEmptyLayers (true);
        for (final IVelocityLayer layer: velocityLayers)
        {
            final String name = layer.getName ();
            if (name != null && !name.isBlank ())
            {
                final Element groupElement = XMLUtils.addElement (document, multisampleElement, "group");
                groupElement.setAttribute ("name", name);
                groupElement.setAttribute ("color", "d92e24");
            }
        }

        int index = 0;
        for (final IVelocityLayer layer: velocityLayers)
        {
            final String name = layer.getName ();
            final int idx = name == null || name.isBlank () ? -1 : index;

            for (final ISampleMetadata sample: layer.getSampleMetadata ())
            {
                if (sample.getTrigger () != TriggerType.RELEASE)
                    createSample (document, multisampleElement, idx, sample);
            }

            index++;
        }

        try
        {
            return Optional.of (XMLUtils.toString (document));
        }
        catch (final TransformerException ex)
        {
            this.notifier.logError (ex);
            return Optional.empty ();
        }
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param multisampleElement The element where to add the sample information
     * @param groupIndex The index of the group to which this sample belongs
     * @param info Where to get the sample info from
     */
    private static void createSample (final Document document, final Element multisampleElement, final int groupIndex, final ISampleMetadata info)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, multisampleElement, "sample");
        final Optional<String> filename = info.getUpdatedFilename ();
        sampleElement.setAttribute ("file", filename.isPresent () ? filename.get () : "");
        if (groupIndex >= 0)
            sampleElement.setAttribute ("group", Integer.toString (groupIndex));
        final double gain = info.getGain ();
        if (gain != 0)
            XMLUtils.setDoubleAttribute (sampleElement, "gain", gain, 2);
        XMLUtils.setDoubleAttribute (sampleElement, "sample-start", Math.max (0, info.getStart ()), 3);
        final int stop = info.getStop ();
        if (stop >= 0)
            XMLUtils.setDoubleAttribute (sampleElement, "sample-stop", stop, 3);
        XMLUtils.setBooleanAttribute (sampleElement, "reverse", info.isReversed ());
        final PlayLogic playLogic = info.getPlayLogic ();
        if (playLogic != PlayLogic.ALWAYS)
            sampleElement.setAttribute ("zone-logic", "round-robin");

        /////////////////////////////////////////////////////
        // Key element and attributes

        final Element keyElement = XMLUtils.addElement (document, sampleElement, "key");
        XMLUtils.setIntegerAttribute (keyElement, "low", check (info.getKeyLow (), 0));
        XMLUtils.setIntegerAttribute (keyElement, "low-fade", check (info.getNoteCrossfadeLow (), 0));
        XMLUtils.setIntegerAttribute (keyElement, "root", info.getKeyRoot ());
        XMLUtils.setIntegerAttribute (keyElement, "high", check (info.getKeyHigh (), 127));
        XMLUtils.setIntegerAttribute (keyElement, "high-fade", check (info.getNoteCrossfadeHigh (), 0));
        XMLUtils.setDoubleAttribute (keyElement, "track", info.getKeyTracking (), 4);
        final double tune = info.getTune ();
        if (tune != 0)
            XMLUtils.setDoubleAttribute (keyElement, "tune", tune, 2);

        /////////////////////////////////////////////////////
        // Key element and attributes

        final Element velocityElement = XMLUtils.addElement (document, sampleElement, "velocity");
        XMLUtils.setIntegerAttribute (velocityElement, "low", check (info.getVelocityLow (), 0));
        XMLUtils.setIntegerAttribute (velocityElement, "low-fade", check (info.getVelocityCrossfadeLow (), 0));
        XMLUtils.setIntegerAttribute (velocityElement, "high", check (info.getVelocityHigh (), 127));
        XMLUtils.setIntegerAttribute (velocityElement, "high-fade", check (info.getVelocityCrossfadeHigh (), 0));

        /////////////////////////////////////////////////////
        // Loops

        final List<ISampleLoop> loops = info.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop sampleLoop = loops.get (0);
            final String type = sampleLoop.getType () == LoopType.ALTERNATING ? "ping-pong" : "loop";

            final Element loopElement = XMLUtils.addElement (document, sampleElement, "loop");
            loopElement.setAttribute ("mode", type);
            XMLUtils.setDoubleAttribute (loopElement, "start", check (sampleLoop.getStart (), 0), 3);
            XMLUtils.setDoubleAttribute (loopElement, "stop", check (sampleLoop.getEnd (), stop), 3);

            final double crossfade = sampleLoop.getCrossfade ();
            if (crossfade > 0)
                XMLUtils.setDoubleAttribute (loopElement, "fade", crossfade, 2);
        }
    }
}
