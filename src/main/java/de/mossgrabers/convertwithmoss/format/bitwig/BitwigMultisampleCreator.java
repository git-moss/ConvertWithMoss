// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.XMLUtils;


/**
 * Creator for Bitwig multi-sample files. Such a file is a renamed ZIP file with the ending
 * "multisample" and contains all WAV files and a metadata description file (multisample.xml).
 *
 * @author Jürgen Moßgraber
 */
public class BitwigMultisampleCreator extends AbstractWavCreator<WavChunkSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public BitwigMultisampleCreator (final INotifier notifier)
    {
        super ("Bitwig Multisample", "Bitwig", notifier, new WavChunkSettingsUI ("Bitwig"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final Optional<String> metadata = this.createMetadata (multisampleSource);
        if (metadata.isEmpty ())
            return;

        final File multiFile = this.createUniqueFilename (destinationFolder, createSafeFilename (multisampleSource.getName ()), "multisample");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            zos.setMethod (ZipOutputStream.STORED);
            AbstractCreator.storeTextFile (zos, "multisample.xml", metadata.get (), multisampleSource.getMetadata ().getCreationDateTime ());
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

        final IMetadata metadata = multisampleSource.getMetadata ();
        XMLUtils.addTextElement (document, multisampleElement, "generator", "ConvertWithMoss");
        XMLUtils.addTextElement (document, multisampleElement, "category", metadata.getCategory ());
        XMLUtils.addTextElement (document, multisampleElement, "creator", metadata.getCreator ());
        XMLUtils.addTextElement (document, multisampleElement, "description", metadata.getDescription ());

        final Element keywordsElement = XMLUtils.addElement (document, multisampleElement, "keywords");
        for (final String keyword: metadata.getKeywords ())
            XMLUtils.addTextElement (document, keywordsElement, "keyword", keyword);

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
        for (final IGroup group: groups)
        {
            final String name = group.getName ();
            if (name != null && !name.isBlank ())
            {
                final Element groupElement = XMLUtils.addElement (document, multisampleElement, "group");
                groupElement.setAttribute ("name", name);
                groupElement.setAttribute ("color", "d92e24");
            }
        }

        int index = 0;
        for (final IGroup group: groups)
        {
            final String name = group.getName ();
            final int idx = name == null || name.isBlank () ? -1 : index;

            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getTrigger () != TriggerType.RELEASE)
                    createSample (document, multisampleElement, idx, zone);

            index++;
        }

        return this.createXMLString (document);
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param multisampleElement The element where to add the sample information
     * @param groupIndex The index of the group to which this sample belongs
     * @param zone Where to get the sample zone info from
     */
    private static void createSample (final Document document, final Element multisampleElement, final int groupIndex, final ISampleZone zone)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, multisampleElement, "sample");
        sampleElement.setAttribute ("file", zone.getName () + ".wav");
        if (groupIndex >= 0)
            sampleElement.setAttribute ("group", Integer.toString (groupIndex));
        final double gain = zone.getGain ();
        if (gain != 0)
            XMLUtils.setDoubleAttribute (sampleElement, "gain", gain, 2);
        XMLUtils.setDoubleAttribute (sampleElement, "sample-start", Math.max (0, zone.getStart ()), 3);
        final int stop = zone.getStop ();
        if (stop >= 0)
            XMLUtils.setDoubleAttribute (sampleElement, "sample-stop", stop, 3);
        XMLUtils.setBooleanAttribute (sampleElement, "reverse", zone.isReversed ());
        final PlayLogic playLogic = zone.getPlayLogic ();
        if (playLogic != PlayLogic.ALWAYS)
            sampleElement.setAttribute ("zone-logic", "round-robin");

        /////////////////////////////////////////////////////
        // Key element and attributes

        final Element keyElement = XMLUtils.addElement (document, sampleElement, "key");
        final int keyLow = limitToDefault (zone.getKeyLow (), 0);
        XMLUtils.setIntegerAttribute (keyElement, "low", keyLow);
        XMLUtils.setIntegerAttribute (keyElement, "low-fade", limitToDefault (zone.getNoteCrossfadeLow (), 0));
        XMLUtils.setIntegerAttribute (keyElement, "root", limitToDefault (zone.getKeyRoot (), keyLow));
        XMLUtils.setIntegerAttribute (keyElement, "high", limitToDefault (zone.getKeyHigh (), 127));
        XMLUtils.setIntegerAttribute (keyElement, "high-fade", limitToDefault (zone.getNoteCrossfadeHigh (), 0));
        XMLUtils.setDoubleAttribute (keyElement, "track", zone.getKeyTracking (), 4);
        final double tune = zone.getTuning ();
        if (tune != 0)
            XMLUtils.setDoubleAttribute (keyElement, "tune", tune, 2);

        /////////////////////////////////////////////////////
        // Key element and attributes

        final Element velocityElement = XMLUtils.addElement (document, sampleElement, "velocity");
        XMLUtils.setIntegerAttribute (velocityElement, "low", limitToDefault (zone.getVelocityLow (), 1));
        XMLUtils.setIntegerAttribute (velocityElement, "low-fade", limitToDefault (zone.getVelocityCrossfadeLow (), 0));
        XMLUtils.setIntegerAttribute (velocityElement, "high", limitToDefault (zone.getVelocityHigh (), 127));
        XMLUtils.setIntegerAttribute (velocityElement, "high-fade", limitToDefault (zone.getVelocityCrossfadeHigh (), 0));

        /////////////////////////////////////////////////////
        // Loops

        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop sampleLoop = loops.get (0);
            final String type = sampleLoop.getType () == LoopType.ALTERNATING ? "ping-pong" : "loop";

            final Element loopElement = XMLUtils.addElement (document, sampleElement, "loop");
            loopElement.setAttribute ("mode", type);
            XMLUtils.setDoubleAttribute (loopElement, "start", limitToDefault (sampleLoop.getStart (), 0), 3);
            XMLUtils.setDoubleAttribute (loopElement, "stop", limitToDefault (sampleLoop.getEnd (), stop), 3);

            final double crossfade = sampleLoop.getCrossfade ();
            if (crossfade > 0)
                XMLUtils.setDoubleAttribute (loopElement, "fade", crossfade, 4);
        }
    }
}
