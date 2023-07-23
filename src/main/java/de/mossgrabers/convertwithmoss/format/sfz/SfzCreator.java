// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.Utils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


/**
 * Creator for SFZ multi-sample files. SFZ has a description file and all related samples in a
 * separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class SfzCreator extends AbstractCreator
{
    private static final char                    LINE_FEED       = '\n';
    private static final String                  FOLDER_POSTFIX  = " Samples";
    private static final String                  SFZ_HEADER      = """
            /////////////////////////////////////////////////////////////////////////////
            ////
            """;
    private static final String                  COMMENT_PREFIX  = "//// ";

    private static final Map<FilterType, String> FILTER_TYPE_MAP = new EnumMap<> (FilterType.class);
    private static final Map<LoopType, String>   LOOP_TYPE_MAP   = new EnumMap<> (LoopType.class);

    static
    {
        FILTER_TYPE_MAP.put (FilterType.LOW_PASS, "lpf");
        FILTER_TYPE_MAP.put (FilterType.HIGH_PASS, "hpf");
        FILTER_TYPE_MAP.put (FilterType.BAND_PASS, "bpf");
        FILTER_TYPE_MAP.put (FilterType.BAND_REJECTION, "brf");

        LOOP_TYPE_MAP.put (LoopType.FORWARD, "forward");
        LOOP_TYPE_MAP.put (LoopType.BACKWARDS, "backward");
        LOOP_TYPE_MAP.put (LoopType.ALTERNATING, "alternate");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SfzCreator (final INotifier notifier)
    {
        super ("SFZ", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = new File (destinationFolder, sampleName + ".sfz");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        final String safeSampleFolderName = sampleName + FOLDER_POSTFIX;
        final String metadata = createMetadata (safeSampleFolderName, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the text of the description file.
     *
     * @param safeSampleFolderName The safe sample folder name (removed illegal characters)
     * @param multisampleSource The multi-sample
     * @return The XML structure
     */
    private static String createMetadata (final String safeSampleFolderName, final IMultisampleSource multisampleSource)
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
            addAttribute (sb, SfzOpcode.GLOBAL_LABEL, name, true);

        for (final IVelocityLayer layer: multisampleSource.getLayers ())
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
                addAttribute (sb, SfzOpcode.GROUP_LABEL, layerName, true);
            if (sequence > 0)
                addIntegerAttribute (sb, SfzOpcode.SEQ_LENGTH, sequence, true);

            final TriggerType trigger = layer.getTrigger ();
            if (trigger != null && trigger != TriggerType.ATTACK)
                addAttribute (sb, SfzOpcode.TRIGGER, trigger.name ().toLowerCase (Locale.ENGLISH), true);

            sequence = 1;
            for (final ISampleMetadata info: sampleMetadata)
            {
                createSample (safeSampleFolderName, sb, info, sequence);
                if (info.getPlayLogic () == PlayLogic.ROUND_ROBIN)
                    sequence++;
            }
        }

        return sb.toString ();
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param safeSampleFolderName The safe sample folder name
     * @param sb Where to add the XML code
     * @param info Where to get the sample info from
     * @param sequenceNumber The number in the sequence for round-robin playback
     */
    private static void createSample (final String safeSampleFolderName, final StringBuilder sb, final ISampleMetadata info, final int sequenceNumber)
    {
        sb.append ("\n<").append (SfzHeader.REGION).append (">\n");
        final Optional<String> filename = info.getUpdatedFilename ();
        if (filename.isPresent ())
            addAttribute (sb, SfzOpcode.SAMPLE, AbstractCreator.formatFileName (safeSampleFolderName, filename.get ()), true);

        // Default is 'attack' and does not need to be added
        final TriggerType trigger = info.getTrigger ();
        if (trigger != TriggerType.ATTACK)
            addAttribute (sb, SfzOpcode.TRIGGER, trigger.name ().toLowerCase (Locale.ENGLISH), true);

        if (info.isReversed ())
            addAttribute (sb, SfzOpcode.DIRECTION, "reverse", true);
        if (info.getPlayLogic () == PlayLogic.ROUND_ROBIN)
            addIntegerAttribute (sb, SfzOpcode.SEQ_POSITION, sequenceNumber, true);

        ////////////////////////////////////////////////////////////
        // Key range

        final int keyRoot = info.getKeyRoot ();
        final int keyLow = info.getKeyLow ();
        final int keyHigh = info.getKeyHigh ();
        if (keyRoot == keyLow && keyLow == keyHigh)
        {
            // Pitch and range are the same, use single key attribute
            addIntegerAttribute (sb, SfzOpcode.KEY, keyRoot, true);
        }
        else
        {
            addIntegerAttribute (sb, SfzOpcode.PITCH_KEY_CENTER, keyRoot, true);
            addIntegerAttribute (sb, SfzOpcode.LO_KEY, check (keyLow, 0), false);
            addIntegerAttribute (sb, SfzOpcode.HI_KEY, check (keyHigh, 127), true);
        }

        final int crossfadeLow = info.getNoteCrossfadeLow ();
        if (crossfadeLow > 0)
        {
            addIntegerAttribute (sb, SfzOpcode.XF_IN_LO_KEY, Math.max (0, keyLow - crossfadeLow), false);
            addIntegerAttribute (sb, SfzOpcode.XF_IN_HI_KEY, keyLow, true);
        }
        final int crossfadeHigh = info.getNoteCrossfadeHigh ();
        if (crossfadeHigh > 0)
        {
            addIntegerAttribute (sb, SfzOpcode.XF_OUT_LO_KEY, keyHigh, false);
            addIntegerAttribute (sb, SfzOpcode.XF_OUT_HI_KEY, Math.min (127, keyHigh + crossfadeHigh), true);
        }

        ////////////////////////////////////////////////////////////
        // Velocity

        final int velocityLow = info.getVelocityLow ();
        final int velocityHigh = info.getVelocityHigh ();
        if (velocityLow > 1)
            addIntegerAttribute (sb, SfzOpcode.LO_VEL, velocityLow, velocityHigh == 127);
        if (velocityHigh > 0 && velocityHigh < 127)
            addIntegerAttribute (sb, SfzOpcode.HI_VEL, velocityHigh, true);

        final int crossfadeVelocityLow = info.getVelocityCrossfadeLow ();
        if (crossfadeVelocityLow > 0)
        {
            addIntegerAttribute (sb, SfzOpcode.XF_IN_LO_VEL, Math.max (0, velocityLow - crossfadeVelocityLow), false);
            addIntegerAttribute (sb, SfzOpcode.XF_IN_HI_VEL, velocityLow, true);
        }

        final int crossfadeVelocityHigh = info.getVelocityCrossfadeHigh ();
        if (crossfadeVelocityHigh > 0)
        {
            addIntegerAttribute (sb, SfzOpcode.XF_OUT_LO_VEL, velocityHigh, false);
            addIntegerAttribute (sb, SfzOpcode.XF_OUT_HI_VEL, Math.min (127, velocityHigh + crossfadeVelocityHigh), true);
        }

        ////////////////////////////////////////////////////////////
        // Start, end, tune, volume

        final int start = info.getStart ();
        if (start >= 0)

            addIntegerAttribute (sb, SfzOpcode.OFFSET, start, false);
        final int end = info.getStop ();
        if (end >= 0)
            addIntegerAttribute (sb, SfzOpcode.END, end, true);

        final double tune = info.getTune ();
        if (tune != 0)
            addIntegerAttribute (sb, SfzOpcode.TUNE, (int) Math.round (tune * 100), true);

        final int keyTracking = (int) Math.round (info.getKeyTracking () * 100.0);
        if (keyTracking != 100)
            addIntegerAttribute (sb, SfzOpcode.PITCH_KEYTRACK, keyTracking, true);

        createVolume (sb, info);

        ////////////////////////////////////////////////////////////
        // Pitch Bend / Envelope

        final int bendUp = info.getBendUp ();
        if (bendUp != 0)
            addIntegerAttribute (sb, SfzOpcode.BEND_UP, bendUp, true);
        final int bendDown = info.getBendDown ();
        if (bendDown != 0)
            addIntegerAttribute (sb, SfzOpcode.BEND_DOWN, bendDown, true);

        final StringBuilder envelopeStr = new StringBuilder ();

        final IModulator pitchModulator = info.getPitchModulator ();
        final double envelopeDepth = pitchModulator.getDepth ();
        if (envelopeDepth > 0)
        {
            sb.append (SfzOpcode.PITCHEG_DEPTH).append ('=').append ((int) envelopeDepth).append (LINE_FEED);

            final IEnvelope pitchEnvelope = pitchModulator.getSource ();

            addEnvelopeAttribute (envelopeStr, SfzOpcode.PITCHEG_DELAY, pitchEnvelope.getDelay ());
            addEnvelopeAttribute (envelopeStr, SfzOpcode.PITCHEG_ATTACK, pitchEnvelope.getAttack ());
            addEnvelopeAttribute (envelopeStr, SfzOpcode.PITCHEG_HOLD, pitchEnvelope.getHold ());
            addEnvelopeAttribute (envelopeStr, SfzOpcode.PITCHEG_DECAY, pitchEnvelope.getDecay ());
            addEnvelopeAttribute (envelopeStr, SfzOpcode.PITCHEG_RELEASE, pitchEnvelope.getRelease ());

            addEnvelopeAttribute (envelopeStr, SfzOpcode.PITCHEG_START, pitchEnvelope.getStart () * 100.0);
            addEnvelopeAttribute (envelopeStr, SfzOpcode.PITCHEG_SUSTAIN, pitchEnvelope.getSustain () * 100.0);

            if (envelopeStr.length () > 0)
                sb.append (envelopeStr).append (LINE_FEED);
        }

        ////////////////////////////////////////////////////////////
        // Sample Loop

        createLoops (sb, info);

        ////////////////////////////////////////////////////////////
        // Filter

        createFilter (sb, info);
    }


    /**
     * Create the loop info.
     *
     * @param sb Where to add the XML code
     * @param info Where to get the sample info from
     */
    private static void createLoops (final StringBuilder sb, final ISampleMetadata info)
    {
        final List<ISampleLoop> loops = info.getLoops ();
        if (loops.isEmpty ())
        {
            addAttribute (sb, SfzOpcode.LOOP_MODE, "no_loop", false);
            return;
        }

        final ISampleLoop sampleLoop = loops.get (0);
        // SFZ currently only supports forward looping
        addAttribute (sb, SfzOpcode.LOOP_MODE, "loop_continuous", false);
        final String type = LOOP_TYPE_MAP.get (sampleLoop.getType ());
        // No need to write the default value
        if (!"forward".equals (type))
            addAttribute (sb, SfzOpcode.LOOP_TYPE, type, false);
        addIntegerAttribute (sb, SfzOpcode.LOOP_START, sampleLoop.getStart (), false);
        sb.append (SfzOpcode.LOOP_END).append ('=').append (sampleLoop.getEnd ());

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


    /**
     * Create the volume and amplitude envelope parameters.
     *
     * @param sb Where to add the created text
     * @param sampleMetadata The data source
     */
    private static void createVolume (final StringBuilder sb, final ISampleMetadata sampleMetadata)
    {
        final double volume = sampleMetadata.getGain ();
        if (volume != 0)
            addAttribute (sb, SfzOpcode.VOLUME, formatDouble (volume, 2), true);
        final double pan = sampleMetadata.getPanorama ();
        if (pan != 0)
            addAttribute (sb, SfzOpcode.PANORAMA, Integer.toString ((int) Math.round (pan * 100)), true);

        final StringBuilder envelopeStr = new StringBuilder ();

        final IEnvelope amplitudeEnvelope = sampleMetadata.getAmplitudeModulator ().getSource ();

        addEnvelopeAttribute (envelopeStr, SfzOpcode.AMPEG_DELAY, amplitudeEnvelope.getDelay ());
        addEnvelopeAttribute (envelopeStr, SfzOpcode.AMPEG_ATTACK, amplitudeEnvelope.getAttack ());
        addEnvelopeAttribute (envelopeStr, SfzOpcode.AMPEG_HOLD, amplitudeEnvelope.getHold ());
        addEnvelopeAttribute (envelopeStr, SfzOpcode.AMPEG_DECAY, amplitudeEnvelope.getDecay ());
        addEnvelopeAttribute (envelopeStr, SfzOpcode.AMPEG_RELEASE, amplitudeEnvelope.getRelease ());

        addEnvelopeAttribute (envelopeStr, SfzOpcode.AMPEG_START, amplitudeEnvelope.getStart () * 100.0);
        addEnvelopeAttribute (envelopeStr, SfzOpcode.AMPEG_SUSTAIN, amplitudeEnvelope.getSustain () * 100.0);

        if (envelopeStr.length () > 0)
            sb.append (envelopeStr).append (LINE_FEED);
    }


    /**
     * Create the filter info.
     *
     * @param sb Where to add the XML code
     * @param info Where to get the sample info from
     */
    private static void createFilter (final StringBuilder sb, final ISampleMetadata info)
    {
        final Optional<IFilter> optFilter = info.getFilter ();
        if (optFilter.isEmpty ())
            return;

        final IFilter filter = optFilter.get ();
        final String type = FILTER_TYPE_MAP.get (filter.getType ());
        addAttribute (sb, SfzOpcode.FILTER_TYPE, type + "_" + Utils.clamp (filter.getPoles (), 1, 4) + "p", false);
        addAttribute (sb, SfzOpcode.CUTOFF, formatDouble (filter.getCutoff (), 2), false);
        addAttribute (sb, SfzOpcode.RESONANCE, formatDouble (Math.min (40, filter.getResonance ()), 2), true);

        final StringBuilder envelopeStr = new StringBuilder ();

        final IModulator cutoffModulator = filter.getCutoffModulator ();
        final double envelopeDepth = cutoffModulator.getDepth ();
        if (envelopeDepth > 0)
        {
            sb.append (SfzOpcode.FILEG_DEPTH).append ('=').append ((int) envelopeDepth).append (LINE_FEED);

            final IEnvelope filterEnvelope = cutoffModulator.getSource ();

            addEnvelopeAttribute (envelopeStr, SfzOpcode.FILEG_DELAY, filterEnvelope.getDelay ());
            addEnvelopeAttribute (envelopeStr, SfzOpcode.FILEG_ATTACK, filterEnvelope.getAttack ());
            addEnvelopeAttribute (envelopeStr, SfzOpcode.FILEG_HOLD, filterEnvelope.getHold ());
            addEnvelopeAttribute (envelopeStr, SfzOpcode.FILEG_DECAY, filterEnvelope.getDecay ());
            addEnvelopeAttribute (envelopeStr, SfzOpcode.FILEG_RELEASE, filterEnvelope.getRelease ());

            addEnvelopeAttribute (envelopeStr, SfzOpcode.FILEG_START, filterEnvelope.getStart () * 100.0);
            addEnvelopeAttribute (envelopeStr, SfzOpcode.FILEG_SUSTAIN, filterEnvelope.getSustain () * 100.0);

            if (envelopeStr.length () > 0)
                sb.append (envelopeStr).append (LINE_FEED);
        }
    }


    private static void addAttribute (final StringBuilder sb, final String opcode, final String value, final boolean addLineFeed)
    {
        sb.append (opcode).append ('=').append (value).append (addLineFeed ? LINE_FEED : ' ');
    }


    private static void addIntegerAttribute (final StringBuilder sb, final String opcode, final int value, final boolean addLineFeed)
    {
        addAttribute (sb, opcode, Integer.toString (value), addLineFeed);
    }


    private static void addEnvelopeAttribute (final StringBuilder sb, final String opcode, final double value)
    {
        if (value < 0)
            return;
        if (sb.length () > 0)
            sb.append (' ');
        sb.append (opcode).append ('=').append (Utils.clamp (value, 0.0, 100.0));
    }
}