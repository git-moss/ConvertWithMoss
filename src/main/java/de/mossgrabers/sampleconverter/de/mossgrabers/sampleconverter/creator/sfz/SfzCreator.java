// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.creator.sfz;

import de.mossgrabers.sampleconverter.core.ICreator;
import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;


/**
 * Creator for SFZ multisample files. SFZ has a description file and all related samples in a
 * separate folder.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzCreator implements ICreator
{
    private static final String SFZ_HEADER = """
            /////////////////////////////////////////////////////////////////////////////
            // sfz created by SampleConverter

            """;


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource, final INotifier notifier) throws IOException
    {
        final File multiFile = new File (destinationFolder, multisampleSource.getName () + ".sfz");
        if (multiFile.exists ())
        {
            notifier.notify (Functions.getMessage ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ()));
            return;
        }

        final String metadata = createMetadata (multisampleSource);

        notifier.notify (Functions.getMessage ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ()));

        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, multisampleSource.getName ());
        if (!sampleFolder.mkdir ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERROR_SAMPLE_FOLDER", sampleFolder.getAbsolutePath ()));

        final List<IVelocityLayer> sampleMetadata = multisampleSource.getSampleMetadata ();
        for (final IVelocityLayer layer: sampleMetadata)
        {
            for (final ISampleMetadata info: layer.getSampleMetadata ())
            {
                final Optional<String> filename = info.getUpdatedFilename ();
                if (filename.isEmpty ())
                    continue;
                try (final FileOutputStream fos = new FileOutputStream (new File (sampleFolder, filename.get ())))
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

        final String name = multisampleSource.getName ();
        for (final IVelocityLayer layer: multisampleSource.getSampleMetadata ())
        {
            sb.append ("<group>\n");
            final String layerName = layer.getName ();
            if (layerName != null && layerName.isBlank ())
                sb.append ("group_label=\n").append (layerName);

            for (final ISampleMetadata info: layer.getSampleMetadata ())
                createSample (name, sb, info);
        }

        return sb.toString ();
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param name The name of the multi-sample
     * @param sb Where to add the XML code
     * @param info Where to get the sample info from
     */
    private static void createSample (final String name, final StringBuilder sb, final ISampleMetadata info)
    {
        sb.append ("\n<region>\n");
        final Optional<String> filename = info.getUpdatedFilename ();
        sb.append ("sample=").append (name).append ('\\').append (filename.isPresent () ? filename.get () : "").append ('\n');
        sb.append ("pitch_keycenter=").append (info.getKeyRoot ()).append ('\n');

        final int keyLow = info.getKeyLow ();
        final int keyHigh = info.getKeyHigh ();
        sb.append ("lokey=").append (check (keyLow, 0)).append (" hikey=").append (check (keyHigh, 127)).append ('\n');

        final int crossfadeLow = info.getNoteCrossfadeLow ();
        if (crossfadeLow > 0)
            sb.append ("xfin_lokey=").append (Math.max (0, keyLow - crossfadeLow)).append (" xfin_hikey=").append (keyLow).append ('\n');
        final int crossfadeHigh = info.getNoteCrossfadeHigh ();
        if (crossfadeHigh > 0)
            sb.append ("xfout_lokey=").append (keyHigh).append (" xfout_hikey=").append (Math.min (127, keyHigh + crossfadeHigh)).append ('\n');

        final int velocityLow = info.getVelocityLow ();
        final int velocityHigh = info.getVelocityHigh ();
        sb.append ("lovel=").append (check (velocityLow, 0)).append (" hivel=").append (check (velocityHigh, 127)).append ('\n');

        final int crossfadeVelocityLow = info.getVelocityCrossfadeLow ();
        if (crossfadeVelocityLow > 0)
            sb.append ("xfin_lovel=").append (Math.max (0, velocityLow - crossfadeVelocityLow)).append (" xfin_hivel=").append (velocityLow).append ('\n');
        final int crossfadeVelocityHigh = info.getVelocityCrossfadeHigh ();
        if (crossfadeVelocityHigh > 0)
            sb.append ("xfout_lovel=").append (velocityHigh).append (" xfout_hivel=").append (Math.min (127, velocityHigh + crossfadeVelocityHigh)).append ('\n');

        sb.append ("offset=").append (info.getStart ()).append (" end=").append (info.getStop ()).append ('\n');
        if (info.hasLoop ())
            sb.append ("loop_mode=loop_continuous loop_start=").append (info.getLoopStart ()).append (" loop_end=").append (info.getLoopEnd ()).append ('\n');
    }


    private static int check (final int value, final int defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }
}