// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.creator;

import de.mossgrabers.sampleconverter.core.ICreator;
import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * Creator for Bitwig multisample files. Such a file is a renamed ZIP file with the ending
 * "multisample" and contains all WAV files and a metadata description file (multisample.xml).
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BitwigMultisampleCreator implements ICreator
{
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource, final INotifier notifier) throws IOException
    {
        final String metadata = createMetadata (multisampleSource);

        final File multiFile = new File (destinationFolder, multisampleSource.getName () + ".multisample");
        notifier.notify (Functions.getMessage ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ()));

        try (final ZipOutputStream zos = new ZipOutputStream (new FileOutputStream (multiFile)))
        {
            zos.putNextEntry (new ZipEntry ("multisample.xml"));
            final Writer writer = new BufferedWriter (new OutputStreamWriter (zos, StandardCharsets.UTF_8));
            writer.write (metadata);
            writer.flush ();
            zos.closeEntry ();

            // Add all samples
            for (final List<ISampleMetadata> layer: multisampleSource.getSampleMetadata ())
            {
                for (final ISampleMetadata info: layer)
                {
                    notifier.notify (Functions.getMessage ("IDS_NOTIFY_PROGRESS"));
                    addFileToZip (zos, info);
                }
            }
        }

        notifier.notify (Functions.getMessage ("IDS_NOTIFY_PROGRESS_DONE"));
    }


    /**
     * Create the text of the description file.
     *
     * @param multisampleSource The multi-sample
     * @return The XML structure
     */
    private static String createMetadata (final IMultisampleSource multisampleSource)
    {
        final StringBuilder sb = new StringBuilder (XML_HEADER);
        sb.append ("\n<multisample name=\"").append (multisampleSource.getName ()).append ("\">\n");

        sb.append ("   <generator>MultisampleGenerator</generator>\n");
        sb.append ("   <category>").append (multisampleSource.getCategory ()).append ("</category>\n");
        sb.append ("   <creator>").append (multisampleSource.getCreator ()).append ("</creator>\n");
        sb.append ("   <description/>\n");

        sb.append ("   <keywords>\n");
        for (final String keyword: multisampleSource.getKeywords ())
            sb.append ("      <keyword>").append (keyword).append ("</keyword>\n");
        sb.append ("   </keywords>\n");

        final List<List<ISampleMetadata>> sampleMetadata = multisampleSource.getSampleMetadata ();

        final int size = sampleMetadata.size ();
        final int range = 127 / size;
        int low = 0;
        int high = range;
        int count = 1;

        for (final List<ISampleMetadata> layer: sampleMetadata)
        {
            for (final ISampleMetadata info: layer)
                createSample (sb, info, low, high);

            low = high + 1;
            if (count == size - 1)
                high = 127;
            else
                high += range;
            count++;
        }

        sb.append ("</multisample>\n");
        return sb.toString ();
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param sb Where to add the XML code
     * @param info Where to get the sample info from
     * @param velocityLow The lower velocity range
     * @param velocityHigh The upper velocity range
     */
    private static void createSample (final StringBuilder sb, final ISampleMetadata info, final int velocityLow, final int velocityHigh)
    {
        sb.append ("   <sample file=\"").append (info.getUpdatedFilename ()).append ("\" gain=\"0.000\" sample-start=\"").append (info.getStart ()).append (".000\" sample-stop=\"").append (info.getStop ()).append (".000\" tune=\"0.0\">\n");
        sb.append ("      <key high=\"").append (info.getKeyHigh ()).append ("\" high-fade=\"").append (info.getCrossfadeHigh ()).append ("\" low=\"").append (info.getKeyLow ()).append ("\" low-fade=\"").append (info.getCrossfadeLow ()).append ("\" root=\"").append (info.getKeyRoot ()).append ("\" track=\"true\"/>\n");
        sb.append ("      <velocity low=\"").append (velocityLow).append ("\" high=\"").append (velocityHigh).append ("\"/>\n");
        sb.append ("      <loop mode=\"").append (info.hasLoop () ? "loop" : "off").append ("\" start=\"").append (info.getLoopStart ()).append (".000\" stop=\"").append (info.getLoopEnd ()).append (".000\"/>\n");
        sb.append ("   </sample>\n");
    }


    /**
     * Adds a file to the ZIP output stream.
     *
     * @param zos The ZIP output stream
     * @param info The file to add
     * @throws IOException Could not read the file
     */
    private static void addFileToZip (final ZipOutputStream zos, final ISampleMetadata info) throws IOException
    {
        zos.putNextEntry (new ZipEntry (info.getUpdatedFilename ()));
        info.writeSample (zos);
        zos.closeEntry ();
    }
}
