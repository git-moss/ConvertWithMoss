// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sfz.creator;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.LoopType;
import de.mossgrabers.sampleconverter.core.PlayLogic;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.creator.AbstractCreator;
import de.mossgrabers.sampleconverter.format.sfz.SfzHeader;
import de.mossgrabers.sampleconverter.format.sfz.SfzOpcode;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Creator for SFZ multi-sample files. SFZ has a description file and all related samples in a
 * separate folder.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzCreator extends AbstractCreator
{
    private static final char                  LINE_FEED        = '\n';
    private static final String                FOLDER_POSTFIX   = " Samples";
    private static final String                SFZ_HEADER       = """
            /////////////////////////////////////////////////////////////////////////////
            ////
            """;
    private static final String                COMMENT_PREFIX   = "//// ";

    private static final Map<LoopType, String> LOOP_TYPE_MAPPER = new EnumMap<> (LoopType.class);
    static
    {
        LOOP_TYPE_MAPPER.put (LoopType.FORWARD, "forward");
        LOOP_TYPE_MAPPER.put (LoopType.BACKWARDS, "backward");
        LOOP_TYPE_MAPPER.put (LoopType.ALTERNATING, "alternate");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = multisampleSource.getName ();
        final File multiFile = new File (destinationFolder, sampleName + ".sfz");
        if (multiFile.exists ())
        {
            this.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        final String metadata = createMetadata (multisampleSource);

        this.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, sampleName + FOLDER_POSTFIX);
        if (!sampleFolder.mkdir ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERROR_SAMPLE_FOLDER", sampleFolder.getAbsolutePath ()));

        int outputCount = 0;
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
                    this.log ("IDS_NOTIFY_PROGRESS");
                    outputCount++;
                    if (outputCount % 80 == 0)
                        this.log ("IDS_NOTIFY_LINE_FEED");

                    info.writeSample (fos);
                }
            }
        }

        this.log ("IDS_NOTIFY_PROGRESS_DONE");
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

        // Metadata (category, creator, keywords) is currently not available in the
        // specification but has a suggestion: https://github.com/sfz/opcode-suggestions/issues/19
        // until then add it as a comment
        final String creator = multisampleSource.getCreator ();
        if (creator != null && !creator.isBlank ())
            sb.append (COMMENT_PREFIX).append ("Creator : ").append (creator).append (LINE_FEED);
        final String category = multisampleSource.getCategory ();
        if (category != null && !category.isBlank ())
            sb.append (COMMENT_PREFIX).append ("Category: ").append (category).append (LINE_FEED);
        final String description = multisampleSource.getDescription ();
        if (description != null && !description.isBlank ())
            sb.append (COMMENT_PREFIX).append (description.replace ("\n", "\n" + COMMENT_PREFIX)).append (LINE_FEED);
        sb.append (LINE_FEED);

        final String name = multisampleSource.getName ();

        sb.append ('<').append (SfzHeader.GLOBAL).append (">").append (LINE_FEED);
        if (name != null && !name.isBlank ())
            sb.append (SfzOpcode.GLOBAL_LABEL).append ('=').append (name).append (LINE_FEED);

        for (final IVelocityLayer layer: multisampleSource.getSampleMetadata ())
        {
            final List<ISampleMetadata> sampleMetadata = layer.getSampleMetadata ();
            if (sampleMetadata.isEmpty ())
                continue;

            // Check for any sample which play round-robin
            int sequence = 0;
            for (final ISampleMetadata info: sampleMetadata)
            {
                if (info.getPlayLogic () == PlayLogic.ROUND_ROBIN)
                    sequence++;
            }

            sb.append (LINE_FEED).append ('<').append (SfzHeader.GROUP).append (">").append (LINE_FEED);
            final String layerName = layer.getName ();
            if (layerName != null && !layerName.isBlank ())
                sb.append (SfzOpcode.GROUP_LABEL).append ('=').append (layerName).append (LINE_FEED);
            if (sequence > 0)
                sb.append (SfzOpcode.SEQ_LENGTH).append ('=').append (sequence).append (LINE_FEED);

            sequence = 1;
            for (final ISampleMetadata info: sampleMetadata)
            {
                createSample (name, sb, info, sequence);
                if (info.getPlayLogic () == PlayLogic.ROUND_ROBIN)
                    sequence++;
            }
        }

        return sb.toString ();
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param name The name of the multi-sample
     * @param sb Where to add the XML code
     * @param info Where to get the sample info from
     * @param sequenceNumber The number in the sequence for round-robin playback
     */
    private static void createSample (final String name, final StringBuilder sb, final ISampleMetadata info, final int sequenceNumber)
    {
        sb.append ("\n<").append (SfzHeader.REGION).append (">\n");
        final Optional<String> filename = info.getUpdatedFilename ();
        if (filename.isPresent ())
            sb.append (SfzOpcode.SAMPLE).append ('=').append (name).append (FOLDER_POSTFIX).append ('\\').append (filename.get ()).append (LINE_FEED);

        if (info.isReversed ())
            sb.append (SfzOpcode.DIRECTION).append ("=reverse").append (LINE_FEED);
        if (info.getPlayLogic () == PlayLogic.ROUND_ROBIN)
            sb.append (SfzOpcode.SEQ_POSITION).append ('=').append (sequenceNumber).append (LINE_FEED);

        ////////////////////////////////////////////////////////////
        // Key range

        final int keyRoot = info.getKeyRoot ();
        final int keyLow = info.getKeyLow ();
        final int keyHigh = info.getKeyHigh ();
        if (keyRoot == keyLow && keyLow == keyHigh)
        {
            // Pitch and range are the same, use single key attribute
            sb.append (SfzOpcode.KEY).append ('=').append (keyRoot).append (LINE_FEED);
        }
        else
        {
            sb.append (SfzOpcode.PITCH_KEY_CENTER).append ('=').append (keyRoot).append (LINE_FEED);
            sb.append (SfzOpcode.LO_KEY).append ('=').append (check (keyLow, 0)).append (' ').append (SfzOpcode.HI_KEY).append ('=').append (check (keyHigh, 127)).append (LINE_FEED);
        }

        final int crossfadeLow = info.getNoteCrossfadeLow ();
        if (crossfadeLow > 0)
            sb.append (SfzOpcode.XF_IN_LO_KEY).append ('=').append (Math.max (0, keyLow - crossfadeLow)).append (' ').append (SfzOpcode.XF_IN_HI_KEY).append ('=').append (keyLow).append (LINE_FEED);
        final int crossfadeHigh = info.getNoteCrossfadeHigh ();
        if (crossfadeHigh > 0)
            sb.append (SfzOpcode.XF_OUT_LO_KEY).append ('=').append (keyHigh).append (' ').append (SfzOpcode.XF_OUT_HI_KEY).append ('=').append (Math.min (127, keyHigh + crossfadeHigh)).append (LINE_FEED);

        ////////////////////////////////////////////////////////////
        // Velocity

        final int velocityLow = info.getVelocityLow ();
        final int velocityHigh = info.getVelocityHigh ();
        if (velocityLow > 1)
            sb.append (SfzOpcode.LO_VEL).append ('=').append (velocityLow).append (velocityHigh == 127 ? LINE_FEED : ' ');
        if (velocityHigh > 0 && velocityHigh < 127)
            sb.append (SfzOpcode.HI_VEL).append ('=').append (velocityHigh).append (LINE_FEED);

        final int crossfadeVelocityLow = info.getVelocityCrossfadeLow ();
        if (crossfadeVelocityLow > 0)
            sb.append (SfzOpcode.XF_IN_LO_VEL).append ('=').append (Math.max (0, velocityLow - crossfadeVelocityLow)).append (" ").append (SfzOpcode.XF_IN_HI_VEL).append ('=').append (velocityLow).append (LINE_FEED);
        final int crossfadeVelocityHigh = info.getVelocityCrossfadeHigh ();
        if (crossfadeVelocityHigh > 0)
            sb.append (SfzOpcode.XF_OUT_LO_VEL).append ('=').append (velocityHigh).append (" ").append (SfzOpcode.XF_OUT_HI_VEL).append ('=').append (Math.min (127, velocityHigh + crossfadeVelocityHigh)).append (LINE_FEED);

        ////////////////////////////////////////////////////////////
        // Start, end, tune, volume

        final int start = info.getStart ();
        if (start >= 0)
            sb.append (SfzOpcode.OFFSET).append ('=').append (start).append (' ');
        final int end = info.getStop ();
        if (end >= 0)
            sb.append (SfzOpcode.END).append ('=').append (end).append (LINE_FEED);

        final double tune = info.getTune ();
        if (tune != 0)
            sb.append (SfzOpcode.TUNE).append ('=').append (Math.round (tune * 100)).append (LINE_FEED);

        final int keyTracking = (int) Math.round (info.getKeyTracking () * 100.0);
        if (keyTracking != 100)
            sb.append (SfzOpcode.PITCH_KEYTRACK).append ('=').append (keyTracking).append (LINE_FEED);

        final int volume = (int) Math.round (info.getGain ());
        if (volume != 0)
            sb.append (SfzOpcode.VOLUME).append ('=').append (volume).append (LINE_FEED);

        ////////////////////////////////////////////////////////////
        // Sample Loop

        final List<SampleLoop> loops = info.getLoops ();
        if (!loops.isEmpty ())
        {
            final SampleLoop sampleLoop = loops.get (0);
            // SFZ currently only supports forward looping
            sb.append (SfzOpcode.LOOP_MODE).append ("=loop_continuous ");
            final String type = LOOP_TYPE_MAPPER.get (sampleLoop.getType ());
            // No need to write the default value
            if (!"forward".equals (type))
                sb.append (SfzOpcode.LOOP_TYPE).append ('=').append (type).append (' ');
            sb.append (SfzOpcode.LOOP_START).append ('=').append (sampleLoop.getStart ()).append (' ').append (SfzOpcode.LOOP_END).append ('=').append (sampleLoop.getEnd ());

            // Calculate the crossfade in seconds from a percentage of the loop length
            final double crossfade = sampleLoop.getCrossfade ();
            if (crossfade > 0)
            {
                final int loopLength = sampleLoop.getStart () - sampleLoop.getEnd ();
                if (loopLength > 0)
                {
                    final double loopLengthInSeconds = loopLength / (double) info.getSampleRate ();

                    final double crossfadeInSeconds = crossfade * loopLengthInSeconds;
                    sb.append (' ').append (SfzOpcode.LOOP_CROSSFADE).append ('=').append (Math.round (crossfadeInSeconds));
                }
            }

            sb.append (LINE_FEED);
        }
    }


    private static int check (final int value, final int defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }
}