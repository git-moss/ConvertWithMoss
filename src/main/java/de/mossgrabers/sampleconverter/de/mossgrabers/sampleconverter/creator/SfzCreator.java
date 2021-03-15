// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.creator;

import de.mossgrabers.sampleconverter.core.ICreator;
import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;


/**
 * Creator for SFZ multisample files. SFZ has a description file and all related samples in a
 * separate folder.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzCreator implements ICreator
{
    // @formatter:off
    private static final String SFZ_HEADER  = "/////////////////////////////////////////////////////////////////////////////\n" +
                                              "//sfz created by MultisampleGenerator\n" +
                                              "\n" +
                                              "<group>\n";
    // @formatter:on


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource, final INotifier notifier) throws IOException
    {
        final String metadata = createMetadata (multisampleSource);

        final File multiFile = new File (destinationFolder, multisampleSource.getName () + ".sfz");
        notifier.notify (Functions.getMessage ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ()));

        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, multisampleSource.getName ());
        if (!sampleFolder.mkdir ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERROR_SAMPLE_FOLDER", sampleFolder.getAbsolutePath ()));

        final List<List<ISampleMetadata>> sampleMetadata = multisampleSource.getSampleMetadata ();
        for (final List<ISampleMetadata> layer: sampleMetadata)
        {
            for (final ISampleMetadata info: layer)
            {
                try (final FileOutputStream fos = new FileOutputStream (new File (sampleFolder, info.getUpdatedFilename ())))
                {
                    notifier.notify (Functions.getMessage ("IDS_NOTIFY_PROGRESS"));
                    info.writeSample (fos);
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
        final StringBuilder sb = new StringBuilder (SFZ_HEADER);

        // Metadata (name, category, creator, keywords) is currently not available in the
        // specification but has a suggestion: https://github.com/sfz/opcode-suggestions/issues/19

        final List<List<ISampleMetadata>> sampleMetadata = multisampleSource.getSampleMetadata ();
        final int size = sampleMetadata.size ();
        final int range = 127 / size;
        int low = 0;
        int high = range;
        int count = 1;

        final String name = multisampleSource.getName ();

        for (final List<ISampleMetadata> layer: sampleMetadata)
        {
            for (final ISampleMetadata info: layer)
                createSample (name, sb, info, low, high);

            low = high + 1;
            if (count == size - 1)
                high = 127;
            else
                high += range;
            count++;
        }

        return sb.toString ();
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param name The name of the multi-sample
     * @param sb Where to add the XML code
     * @param info Where to get the sample info from
     * @param velocityLow The lower velocity range
     * @param velocityHigh The upper velocity range
     */
    private static void createSample (final String name, final StringBuilder sb, final ISampleMetadata info, final int velocityLow, final int velocityHigh)
    {
        sb.append ("\n<region>\n");
        sb.append ("sample=").append (name).append ('\\').append (info.getUpdatedFilename ()).append ('\n');
        sb.append ("pitch_keycenter=").append (info.getKeyRoot ()).append ('\n');

        final int keyLow = info.getKeyLow ();
        final int keyHigh = info.getKeyHigh ();
        sb.append ("lokey=").append (keyLow).append (" hikey=").append (keyHigh).append ('\n');

        final int crossfadeLow = info.getCrossfadeLow ();
        if (crossfadeLow > 0)
            sb.append ("xfin_lokey=").append (Math.max (0, keyLow - crossfadeLow)).append (" xfin_hikey=").append (keyLow).append ('\n');
        final int crossfadeHigh = info.getCrossfadeHigh ();
        if (crossfadeHigh > 0)
            sb.append ("xfout_lokey=").append (keyHigh).append (" xfout_hikey=").append (Math.min (127, keyHigh + crossfadeHigh)).append ('\n');

        // TODO: Velocity crossfade could be added as well

        sb.append ("lovel=").append (velocityLow).append (" hivel=").append (velocityHigh).append ('\n');

        sb.append ("offset=").append (info.getStart ()).append (" end=").append (info.getStop ()).append ('\n');
        if (info.hasLoop ())
            sb.append ("loop_mode=loop_continuous loop_start=").append (info.getLoopStart ()).append (" loop_end=").append (info.getLoopEnd ()).append ('\n');
    }
}