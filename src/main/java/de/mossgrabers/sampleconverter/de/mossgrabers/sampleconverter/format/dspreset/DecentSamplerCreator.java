// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.dspreset;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.PlayLogic;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.creator.AbstractCreator;
import de.mossgrabers.sampleconverter.ui.tools.BasicConfig;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.ui.tools.panel.BoxPanel;
import de.mossgrabers.sampleconverter.util.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

import javax.xml.transform.TransformerException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * Creator for dspreset multi-sample files. A dspreset has a description file encoded in XML. The
 * related samples are in a separate folder. The description file and sample files can optionally be
 * zipped into a dslibrary file.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DecentSamplerCreator extends AbstractCreator
{
    private static final String FOLDER_POSTFIX           = "Samples";
    private boolean             isOutputFormatLibrary;

    private static final String DS_OUTPUT_FORMAT_LIBRARY = "DsOutputFormatPreset";

    private ToggleGroup         outputFormatGroup;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DecentSamplerCreator (final INotifier notifier)
    {
        super ("DecentSampler", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        this.outputFormatGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_DS_PRESET");
        order1.setToggleGroup (this.outputFormatGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_DS_LIBRARY");
        order2.setToggleGroup (this.outputFormatGroup);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputFormatGroup.selectToggle (this.outputFormatGroup.getToggles ().get (config.getBoolean (DS_OUTPUT_FORMAT_LIBRARY, true) ? 1 : 0));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setProperty (DS_OUTPUT_FORMAT_LIBRARY, Boolean.toString (this.isOutputFormatLibrary ()));
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.setOutputToLibrary (this.isOutputFormatLibrary ());

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = new File (destinationFolder, sampleName + (this.isOutputFormatLibrary ? ".dslibrary" : ".dspreset"));
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        final String relativeFolderName = this.isOutputFormatLibrary ? FOLDER_POSTFIX : sampleName + " " + FOLDER_POSTFIX;

        final Optional<String> metadata = this.createMetadata (relativeFolderName, multisampleSource);
        if (metadata.isEmpty ())
            return;

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        if (this.isOutputFormatLibrary)
            this.storeLibrary (relativeFolderName, multisampleSource, sampleName, multiFile, metadata.get ());
        else
            this.storePreset (relativeFolderName, destinationFolder, multisampleSource, multiFile, metadata.get ());

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private void storePreset (final String relativeFolderName, final File destinationFolder, final IMultisampleSource multisampleSource, final File multiFile, final String metadata) throws IOException
    {
        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, relativeFolderName);
        if (!sampleFolder.exists () && !sampleFolder.mkdir ())
        {
            if (!sampleFolder.exists ())
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERROR_SAMPLE_FOLDER", sampleFolder.getAbsolutePath ()));
        }

        int outputCount = 0;
        for (final IVelocityLayer layer: multisampleSource.getSampleMetadata ())
        {
            for (final ISampleMetadata info: layer.getSampleMetadata ())
            {
                final Optional<String> filename = info.getUpdatedFilename ();
                if (filename.isEmpty ())
                    continue;
                try (final FileOutputStream fos = new FileOutputStream (new File (sampleFolder, filename.get ())))
                {
                    this.notifier.log ("IDS_NOTIFY_PROGRESS");
                    outputCount++;
                    if (outputCount % 80 == 0)
                        this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                    info.writeSample (fos);
                }
            }
        }
    }


    private void storeLibrary (final String relativeFolderName, final IMultisampleSource multisampleSource, final String sampleName, final File multiFile, final String metadata) throws IOException
    {
        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            zos.putNextEntry (new ZipEntry (sampleName + ".dspreset"));
            final Writer writer = new BufferedWriter (new OutputStreamWriter (zos, StandardCharsets.UTF_8));
            writer.write (metadata);
            writer.flush ();
            zos.closeEntry ();

            int outputCount = 0;
            final Set<String> alreadyStored = new HashSet<> ();
            for (final IVelocityLayer layer: multisampleSource.getSampleMetadata ())
            {
                for (final ISampleMetadata info: layer.getSampleMetadata ())
                {
                    this.notifier.log ("IDS_NOTIFY_PROGRESS");
                    outputCount++;
                    if (outputCount % 80 == 0)
                        this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                    addFileToZip (alreadyStored, zos, info, relativeFolderName);
                }
            }
        }
    }


    /**
     * Create the text of the description file.
     *
     * @param folderName The name to use for the sample folder
     * @param multisampleSource The multi-sample
     * @return The XML structure
     */
    private Optional<String> createMetadata (final String folderName, final IMultisampleSource multisampleSource)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element multisampleElement = document.createElement (DecentSamplerTag.DECENTSAMPLER);
        document.appendChild (multisampleElement);

        // No metadata at all

        // Add all groups with samples
        final Element groupsElement = document.createElement (DecentSamplerTag.GROUPS);
        multisampleElement.appendChild (groupsElement);
        final List<IVelocityLayer> velocityLayers = multisampleSource.getSampleMetadata ();
        for (final IVelocityLayer layer: velocityLayers)
        {
            final Element groupElement = XMLUtils.addElement (document, groupsElement, DecentSamplerTag.GROUP);

            final String name = layer.getName ();
            if (name != null && !name.isBlank ())
                groupElement.setAttribute ("name", name);

            for (final ISampleMetadata sample: layer.getSampleMetadata ())
                createSample (document, folderName, groupElement, sample);
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
     * @param folderName The name to use for the sample folder
     * @param groupElement The element where to add the sample information
     * @param info Where to get the sample info from
     */
    private static void createSample (final Document document, final String folderName, final Element groupElement, final ISampleMetadata info)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element sampleElement = XMLUtils.addElement (document, groupElement, DecentSamplerTag.SAMPLE);
        final Optional<String> filename = info.getUpdatedFilename ();
        if (filename.isPresent ())
            sampleElement.setAttribute (DecentSamplerTag.PATH, new StringBuilder ().append (folderName).append ('/').append (filename.get ()).toString ());

        final double gain = info.getGain ();
        if (gain != 0)
            sampleElement.setAttribute (DecentSamplerTag.VOLUME, gain + "dB");
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.START, Math.max (0, info.getStart ()), 3);
        final int stop = info.getStop ();
        if (stop >= 0)
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.END, stop, 3);
        // Convert cent to semitones
        final double tune = info.getTune () / 100;
        if (tune != 0)
            XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.TUNING, tune, 2);

        // No info.isReversed ()

        final PlayLogic playLogic = info.getPlayLogic ();
        if (playLogic != PlayLogic.ALWAYS)
            sampleElement.setAttribute (DecentSamplerTag.SEQ_MODE, "round_robin");

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LO_NOTE, check (info.getKeyLow (), 0));
        // No fades info.getNoteCrossfadeLow ()
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.ROOT_NOTE, info.getKeyRoot ());
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.HI_NOTE, check (info.getKeyHigh (), 127));
        // No fades info.getNoteCrossfadeHigh ()
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.PITCH_KEY_TRACK, info.getKeyTracking (), 4);
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LO_VEL, check (info.getVelocityLow (), 0));
        // No fades info.getVelocityCrossfadeLow ()
        XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.HI_VEL, check (info.getVelocityHigh (), 127));
        // No fades info.getVelocityCrossfadeHigh ()

        /////////////////////////////////////////////////////
        // Loops

        final List<SampleLoop> loops = info.getLoops ();
        if (loops.isEmpty ())
            return;

        final SampleLoop sampleLoop = loops.get (0);
        sampleElement.setAttribute (DecentSamplerTag.LOOP_ENABLED, "true");
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_START, check (sampleLoop.getStart (), 0), 3);
        XMLUtils.setDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_END, check (sampleLoop.getEnd (), stop), 3);

        // Calculate the crossfade in frames/samples from a percentage of the loop length
        final double crossfade = sampleLoop.getCrossfade ();
        if (crossfade <= 0)
            return;
        final int loopLength = sampleLoop.getStart () - sampleLoop.getEnd ();
        if (loopLength > 0)
            XMLUtils.setIntegerAttribute (sampleElement, DecentSamplerTag.LOOP_CROSSFADE, (int) Math.round (loopLength * crossfade));
    }


    /**
     * Set the output format to otherwise preset only.
     *
     * @param outputFormatLibrary True to output dspreset files otherwise dslibrary
     */
    public void setOutputToLibrary (final boolean outputFormatLibrary)
    {
        this.isOutputFormatLibrary = outputFormatLibrary;
    }


    private boolean isOutputFormatLibrary ()
    {
        return this.outputFormatGroup.getToggles ().get (1).isSelected ();
    }
}