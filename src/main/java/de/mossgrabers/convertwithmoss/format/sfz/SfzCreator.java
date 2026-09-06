// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;


/**
 * Creator for SFZ multi-sample files. SFZ has a description file and all related samples in a
 * separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class SfzCreator extends AbstractWavCreator<SfzCreatorUI>
{
    private static final char                      LINE_FEED             = '\n';
    private static final String                    SFZ_HEADER            = """
            /////////////////////////////////////////////////////////////////////////////
            ////
            """;
    private static final String                    SFZ_FOOTER            = """
            ////
            /////////////////////////////////////////////////////////////////////////////

            """;
    private static final String                    COMMENT_PREFIX        = "//// ";

    private static final Map<FilterType, String>   FILTER_TYPE_MAP       = new EnumMap<> (FilterType.class);
    private static final Map<String, Set<Integer>> FILTER_POLES          = new HashMap<> ();
    private static final Map<LoopType, String>     LOOP_TYPE_MAP         = new EnumMap<> (LoopType.class);

    /** The velocities at which a point of the velocity curve is written. */
    private static final int []                    VELOCITY_CURVE_POINTS =
    {
        1,
        2,
        3,
        4,
        5,
        7,
        9,
        12,
        16,
        21,
        28,
        37,
        49,
        64,
        84,
        110,
        127
    };

    static
    {
        FILTER_TYPE_MAP.put (FilterType.LOW_PASS, "lpf");
        FILTER_TYPE_MAP.put (FilterType.HIGH_PASS, "hpf");
        FILTER_TYPE_MAP.put (FilterType.BAND_PASS, "bpf");
        FILTER_TYPE_MAP.put (FilterType.BAND_REJECTION, "brf");

        final Set<Integer> bpfPoles = new HashSet<> ();
        Collections.addAll (bpfPoles, Integer.valueOf (1), Integer.valueOf (2));
        FILTER_POLES.put ("bpf", bpfPoles);
        final Set<Integer> brfPoles = new HashSet<> ();
        Collections.addAll (brfPoles, Integer.valueOf (1), Integer.valueOf (2));
        FILTER_POLES.put ("brf", brfPoles);
        final Set<Integer> hpfPoles = new HashSet<> ();
        Collections.addAll (hpfPoles, Integer.valueOf (1), Integer.valueOf (2), Integer.valueOf (4), Integer.valueOf (6));
        FILTER_POLES.put ("hpf", hpfPoles);
        final Set<Integer> lpfPoles = new HashSet<> ();
        Collections.addAll (lpfPoles, Integer.valueOf (1), Integer.valueOf (2), Integer.valueOf (4), Integer.valueOf (6));
        FILTER_POLES.put ("lpf", lpfPoles);

        LOOP_TYPE_MAP.put (LoopType.FORWARDS, "forward");
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
        super ("SFZ", "SFZ", notifier, new SfzCreatorUI ("SFZ"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String multiSampleName = FileUtils.createSafeFilename (multisampleSource.getName ());
        final String safeSampleFolderName = multiSampleName + FOLDER_POSTFIX;
        final Optional<String> metadata = this.createPresetDocument (safeSampleFolderName, multisampleSource);
        if (metadata.isEmpty ())
            return;

        final File multiFile = this.createUniqueFilename (destinationFolder, multiSampleName, "sfz");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata.get ());
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);

        if (this.settingsConfiguration.convertToFlac ())
            this.writeFlacSamples (sampleFolder, multisampleSource);
        else
            this.writeSamples (sampleFolder, multisampleSource);

        this.progress.notifyDone ();
    }


    /**
     * Create the text of the description file.
     *
     * @param safeSampleFolderName The safe sample folder name (removed illegal characters)
     * @param multisampleSource The multi-sample
     * @return The SFZ structure
     */
    private Optional<String> createPresetDocument (final String safeSampleFolderName, final IMultisampleSource multisampleSource)
    {
        final StringBuilder documentBuffer = createHeader (multisampleSource);

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        if (groups.isEmpty ())
        {
            this.notifier.logError ("IDS_ERR_NO_GROUPS_IN_SOURCE");
            return Optional.empty ();
        }

        // Add all groups with all sample zones (regions)
        final Map<IGroup, Integer> roundRobinGroups = multisampleSource.getRoundRobinGroups ();
        final List<Pair<String, List<Pair<String, List<String>>>>> groupContent = new ArrayList<> ();
        for (final IGroup group: groups)
        {
            final List<ISampleZone> zones = group.getSampleZones ();
            if (zones.isEmpty ())
                continue;

            int maxSequence = -1;
            final boolean isNotRoundRobinGroup = !roundRobinGroups.containsKey (group);
            if (isNotRoundRobinGroup)
                // Check for any sample which does not always play. SFZ cannot express a random
                // selection, therefore such zones are cycled like round-robin ones
                for (final ISampleZone zone: zones)
                    if (zone.getPlayLogic () != PlayLogic.ALWAYS)
                        maxSequence = Math.max (maxSequence, zone.getSequencePosition ());

            final StringBuilder groupBuffer = new StringBuilder ();
            groupBuffer.append (LINE_FEED).append ('<').append (SfzHeader.GROUP).append (">").append (LINE_FEED);
            final String groupName = group.getName ();
            if (groupName != null && !groupName.isBlank ())
                groupBuffer.append (addAttribute (SfzOpcode.GROUP_LABEL, groupName, true));
            if (isNotRoundRobinGroup)
            {
                if (maxSequence > 0)
                    groupBuffer.append (addIntegerAttribute (SfzOpcode.SEQ_LENGTH, maxSequence, true));
            }
            else
            {
                groupBuffer.append (addIntegerAttribute (SfzOpcode.SEQ_LENGTH, roundRobinGroups.size (), true));
                final Integer sequencePosition = roundRobinGroups.get (group);
                if (sequencePosition != null && sequencePosition.intValue () > 0)
                    groupBuffer.append (addIntegerAttribute (SfzOpcode.SEQ_POSITION, sequencePosition.intValue (), true));
            }

            final TriggerType trigger = group.getTrigger ();
            if (trigger != null && trigger != TriggerType.ATTACK)
                groupBuffer.append (addAttribute (SfzOpcode.TRIGGER, trigger.name ().toLowerCase (Locale.ENGLISH), true));

            final List<Pair<String, List<String>>> zonesContent = new ArrayList<> ();
            for (final ISampleZone zone: zones)
                zonesContent.add (this.createSample (safeSampleFolderName, zone, isNotRoundRobinGroup));

            groupContent.add (new Pair<> (groupBuffer.toString (), zonesContent));
        }

        promoteCommonParameters (documentBuffer, groupContent);
        return Optional.of (documentBuffer.toString ());
    }


    private static StringBuilder createHeader (final IMultisampleSource multisampleSource)
    {
        final StringBuilder sb = new StringBuilder (SFZ_HEADER);

        // Metadata (category, creator, keywords) is currently not available in the
        // specification but has a suggestion: https://github.com/sfz/opcode-suggestions/issues/19
        // until then add it as a comment
        final IMetadata metadata = multisampleSource.getMetadata ();
        final String creator = metadata.getCreator ();
        if (creator != null && !creator.isBlank ())
            sb.append (COMMENT_PREFIX).append ("Creator : ").append (creator).append (LINE_FEED);
        final String category = metadata.getCategory ();
        if (category != null && !category.isBlank ())
            sb.append (COMMENT_PREFIX).append ("Category: ").append (category).append (LINE_FEED);
        final String description = metadata.getDescription ();
        if (description != null && !description.isBlank ())
            sb.append (formatWithCommentPrefix (description));
        sb.append (SFZ_FOOTER);

        // Set the name
        final String name = multisampleSource.getName ();
        sb.append ('<').append (SfzHeader.GLOBAL).append (">").append (LINE_FEED);
        if (name != null && !name.isBlank ())
            sb.append (addAttribute (SfzOpcode.GLOBAL_LABEL, name, true));

        // The polyphony is a plain number of voices. Note: SFZ does not have an opcode for the
        // portamento time or for playing monophonic with legato, therefore a monophonic instrument
        // is expressed by limiting the number of voices to 1
        int polyphony = multisampleSource.getPolyphony ();
        if (multisampleSource.isMonophonicLegato ())
            polyphony = 1;
        if (polyphony > 0)
            sb.append (addIntegerAttribute (SfzOpcode.POLYPHONY, polyphony, true));
        return sb;
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param safeSampleFolderName The safe sample folder name
     * @param zone The sample zone
     * @param isNotRoundRobinGroup If the sample zone does not belong to a round robin group
     * @return The region opcode as the key and all parameter opcodes as the values
     */
    private Pair<String, List<String>> createSample (final String safeSampleFolderName, final ISampleZone zone, final boolean isNotRoundRobinGroup)
    {
        final String ending = this.settingsConfiguration.convertToFlac () ? ".flac" : ".wav";

        final StringBuilder regionBuffer = new StringBuilder ();
        regionBuffer.append ("\n<").append (SfzHeader.REGION).append (">\n");
        regionBuffer.append (addAttribute (SfzOpcode.SAMPLE, AbstractCreator.formatFileName (safeSampleFolderName, zone.getName () + ending), true));

        final List<String> opcodes = new ArrayList<> ();

        // Default is 'attack' and does not need to be added
        final TriggerType trigger = zone.getTrigger ();
        if (trigger != TriggerType.ATTACK)
            regionBuffer.append (addAttribute (SfzOpcode.TRIGGER, trigger.name ().toLowerCase (Locale.ENGLISH), true));

        if (zone.isReversed ())
            regionBuffer.append (addAttribute (SfzOpcode.DIRECTION, "reverse", true));
        // SFZ cannot express a random selection, therefore such zones are cycled like round-robin
        // ones instead of falling back to playing all of them at once
        if (zone.getPlayLogic () != PlayLogic.ALWAYS && isNotRoundRobinGroup)
            regionBuffer.append (addIntegerAttribute (SfzOpcode.SEQ_POSITION, Math.max (1, zone.getSequencePosition ()), true));

        // -----------------------------------------------------------
        // Key range

        final int keyRoot = zone.getKeyRoot ();
        final int keyLow = zone.getKeyLow ();
        final int keyHigh = zone.getKeyHigh ();
        if (keyRoot == keyLow && keyLow == keyHigh)
        {
            // Pitch and range are the same, use single key attribute
            if (keyRoot >= 0)
                regionBuffer.append (addIntegerAttribute (SfzOpcode.KEY, keyRoot, true));
        }
        else
        {
            if (keyRoot >= 0)
                regionBuffer.append (addIntegerAttribute (SfzOpcode.PITCH_KEY_CENTER, keyRoot, true));
            regionBuffer.append (addIntegerAttribute (SfzOpcode.LO_KEY, limitToDefault (keyLow, 0), false));
            regionBuffer.append (addIntegerAttribute (SfzOpcode.HI_KEY, limitToDefault (keyHigh, 127), true));
        }

        // The fade ranges lie inside of the zone's range - anchoring them outside would both
        // fade where the zone does not even play and destroy the fade range by the 0/127 clamps
        // for zones at the edges
        final int crossfadeLow = zone.getNoteCrossfadeLow ();
        if (crossfadeLow > 0)
        {
            regionBuffer.append (addIntegerAttribute (SfzOpcode.XF_IN_LO_KEY, keyLow, false));
            regionBuffer.append (addIntegerAttribute (SfzOpcode.XF_IN_HI_KEY, Math.min (127, keyLow + crossfadeLow), true));
        }
        final int crossfadeHigh = zone.getNoteCrossfadeHigh ();
        if (crossfadeHigh > 0)
        {
            regionBuffer.append (addIntegerAttribute (SfzOpcode.XF_OUT_LO_KEY, Math.max (0, keyHigh - crossfadeHigh), false));
            regionBuffer.append (addIntegerAttribute (SfzOpcode.XF_OUT_HI_KEY, keyHigh, true));
        }

        // -----------------------------------------------------------
        // Velocity

        final int velocityLow = zone.getVelocityLow ();
        final int velocityHigh = zone.getVelocityHigh ();
        if (velocityLow > 1)
            regionBuffer.append (addIntegerAttribute (SfzOpcode.LO_VEL, limitToDefault (velocityLow, 1), velocityHigh == 127));
        if (velocityHigh > 0 && velocityHigh < 127)
            regionBuffer.append (addIntegerAttribute (SfzOpcode.HI_VEL, limitToDefault (velocityHigh, 127), true));

        // See the key crossfade above: the fade ranges lie inside of the zone's range
        final int crossfadeVelocityLow = zone.getVelocityCrossfadeLow ();
        if (crossfadeVelocityLow > 0)
        {
            regionBuffer.append (addIntegerAttribute (SfzOpcode.XF_IN_LO_VEL, Math.max (0, velocityLow), false));
            regionBuffer.append (addIntegerAttribute (SfzOpcode.XF_IN_HI_VEL, Math.min (127, velocityLow + crossfadeVelocityLow), true));
        }

        final int crossfadeVelocityHigh = zone.getVelocityCrossfadeHigh ();
        if (crossfadeVelocityHigh > 0)
        {
            regionBuffer.append (addIntegerAttribute (SfzOpcode.XF_OUT_LO_VEL, Math.max (0, velocityHigh - crossfadeVelocityHigh), false));
            regionBuffer.append (addIntegerAttribute (SfzOpcode.XF_OUT_HI_VEL, velocityHigh, true));
        }

        // -----------------------------------------------------------
        // Start, end, tune, volume

        final int start = zone.getStart ();
        if (start >= 0)
            regionBuffer.append (addIntegerAttribute (SfzOpcode.OFFSET, start, false));
        final int end = zone.getStop ();
        if (end >= 0)
            regionBuffer.append (addIntegerAttribute (SfzOpcode.END, end, true));

        final double tune = zone.getTuning ();
        if (tune != 0)
            regionBuffer.append (addIntegerAttribute (SfzOpcode.TUNE, (int) Math.round (tune * 100), true));

        final int keyTracking = (int) Math.round (zone.getKeyTracking () * 100.0);
        if (keyTracking != 100)
            regionBuffer.append (addIntegerAttribute (SfzOpcode.PITCH_KEYTRACK, keyTracking, true));

        opcodes.add (createVolume (zone));

        // -----------------------------------------------------------
        // Pitch Bend / Envelope

        final StringBuilder bendBuffer = new StringBuilder ();
        final int bendUp = zone.getBendUp ();
        if (bendUp != 0)
            bendBuffer.append (addIntegerAttribute (SfzOpcode.BEND_UP, bendUp, true));
        final int bendDown = zone.getBendDown ();
        if (bendDown != 0)
            bendBuffer.append (addIntegerAttribute (SfzOpcode.BEND_DOWN, bendDown, true));
        if (!bendBuffer.isEmpty ())
            opcodes.add (bendBuffer.toString ());

        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
        final double envelopeDepth = pitchModulator.getDepth ();
        if (envelopeDepth != 0)
        {
            final IEnvelope pitchEnvelope = pitchModulator.getSource ();

            final StringBuilder envelopeStr = new StringBuilder ();

            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.PITCHEG_DELAY, pitchEnvelope.getDelayTime ());
            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.PITCHEG_ATTACK, pitchEnvelope.getAttackTime ());
            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.PITCHEG_HOLD, pitchEnvelope.getHoldTime ());
            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.PITCHEG_DECAY, pitchEnvelope.getDecayTime ());
            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.PITCHEG_RELEASE, pitchEnvelope.getReleaseTime ());

            addEnvelopeLevelAttribute (envelopeStr, SfzOpcode.PITCHEG_START, pitchEnvelope.getStartLevel ());
            addEnvelopeLevelAttribute (envelopeStr, SfzOpcode.PITCHEG_SUSTAIN, pitchEnvelope.getSustainLevel ());

            addSlopeAttribute (envelopeStr, SfzOpcode.PITCHEG_ATTACK_SHAPE, pitchEnvelope.getAttackSlope ());
            addSlopeAttribute (envelopeStr, SfzOpcode.PITCHEG_DECAY_SHAPE, pitchEnvelope.getDecaySlope ());
            addSlopeAttribute (envelopeStr, SfzOpcode.PITCHEG_RELEASE_SHAPE, pitchEnvelope.getReleaseSlope ());

            if (!envelopeStr.isEmpty ())
            {
                envelopeStr.append (LINE_FEED);
                final StringBuilder pitchBuffer = new StringBuilder ();
                pitchBuffer.append (SfzOpcode.PITCHEG_DEPTH).append ('=').append ((int) Math.round (envelopeDepth * IEnvelope.MAX_ENVELOPE_DEPTH)).append (LINE_FEED);
                pitchBuffer.append (envelopeStr.toString ());
                opcodes.add (pitchBuffer.toString ());
            }
        }

        // -----------------------------------------------------------
        // Pitch LFO (vibrato)

        final ILfoModulator pitchLfoModulator = zone.getPitchLfoModulator ();
        final double lfoDepth = pitchLfoModulator.getDepth ();
        if (lfoDepth != 0)
        {
            final StringBuilder lfoStr = new StringBuilder ();
            lfoStr.append (SfzOpcode.PITCHLFO_DEPTH).append ('=').append ((int) Math.round (lfoDepth * IEnvelope.MAX_ENVELOPE_DEPTH));

            final ILfo pitchLfo = pitchLfoModulator.getSource ();
            addLfoTimeAttribute (lfoStr, SfzOpcode.PITCHLFO_FREQ, pitchLfo.getRate ());
            addLfoTimeAttribute (lfoStr, SfzOpcode.PITCHLFO_DELAY, pitchLfo.getDelay ());
            addLfoTimeAttribute (lfoStr, SfzOpcode.PITCHLFO_FADE, pitchLfo.getFadeIn ());
            lfoStr.append (LINE_FEED);

            opcodes.add (lfoStr.toString ());
        }

        // -----------------------------------------------------------
        // Sample Loop

        opcodes.add (this.createLoops (zone));

        // -----------------------------------------------------------
        // Filter

        opcodes.add (createFilter (zone));

        return new Pair<> (regionBuffer.toString (), opcodes);
    }


    /**
     * Create the loop info.
     *
     * @param zone The sample zone
     * @return The loop opcodes
     */
    private String createLoops (final ISampleZone zone)
    {
        final StringBuilder buffer = new StringBuilder ();

        final List<ISampleLoop> loops = zone.getLoops ();
        // A one-shot ignores a note-off and always plays the sample to its end. SFZ supports this
        // only for samples which are not looped
        if (loops.isEmpty ())
            buffer.append (addAttribute (SfzOpcode.LOOP_MODE, zone.isOneShot () ? "one_shot" : "no_loop", false));
        else
        {
            final ISampleLoop sampleLoop = loops.get (0);
            buffer.append (addAttribute (SfzOpcode.LOOP_MODE, sampleLoop.isLoopUntilRelease () ? "loop_sustain" : "loop_continuous", false));
            final String type = LOOP_TYPE_MAP.get (sampleLoop.getType ());
            // No need to write the default value
            if (!"forward".equals (type))
                buffer.append (addAttribute (SfzOpcode.LOOP_TYPE, type, false));
            buffer.append (addIntegerAttribute (SfzOpcode.LOOP_START, sampleLoop.getStart (), false));
            buffer.append (SfzOpcode.LOOP_END).append ('=').append (sampleLoop.getEnd ());

            // Calculate the cross-fade in seconds from a percentage of the loop length
            final double crossfade = sampleLoop.getCrossfade ();
            if (crossfade > 0)
            {
                final int loopLength = sampleLoop.getLength ();
                if (loopLength > 0)
                {
                    double loopLengthInSeconds;
                    try
                    {
                        final Optional<ISampleData> sampleData = zone.getSampleData ();
                        if (sampleData.isEmpty ())
                            throw new IOException ("Empty sample data in zone: " + zone.getName ());
                        loopLengthInSeconds = loopLength / (double) sampleData.get ().getAudioMetadata ().getSampleRate ();
                        final double crossfadeInSeconds = crossfade * loopLengthInSeconds;
                        buffer.append (' ').append (SfzOpcode.LOOP_CROSSFADE).append ('=').append (formatAsFloat (crossfadeInSeconds));
                    }
                    catch (final IOException ex)
                    {
                        this.notifier.logError (ex);
                    }
                }
            }

            final double tuning = sampleLoop.getTuning ();
            if (tuning != 0)
                buffer.append (' ').append (SfzOpcode.LOOP_TUNE).append ('=').append (formatAsFloat (Math.round (tuning * 100.0)));
        }
        buffer.append (LINE_FEED);
        return buffer.toString ();
    }


    /**
     * Create the volume and amplitude envelope parameters.
     *
     * @param zone The sample zone
     * @return The created volume opcodes
     */
    private static String createVolume (final ISampleZone zone)
    {
        final StringBuilder buffer = new StringBuilder ();

        final double volume = zone.getGain ();
        final double velAmpDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
        if (volume != 0)
            buffer.append (addAttribute (SfzOpcode.VOLUME, formatDouble (volume, 2), velAmpDepth == 1));
        if (velAmpDepth < 1)
            buffer.append (addAttribute (SfzOpcode.AMP_VELOCITY_TRACK, formatDouble (velAmpDepth * 100.0, 2), true));

        // The curve of the velocity modulation describes the response of the amplitude to the
        // velocity as the power law x^(3^curve) on the normalized velocity: +1 is x^3 (the law of
        // the velocity to volume modulation of Kontakt), 0 is linear and -1 is x^(1/3). SFZ has
        // no curve parameter but the players interpolate linearly between the given points
        final double velAmpCurve = zone.getAmplitudeVelocityModulator ().getCurve ();
        if (velAmpDepth != 0 && velAmpCurve != 0)
        {
            final double power = Math.pow (3, velAmpCurve);
            for (int i = 0; i < VELOCITY_CURVE_POINTS.length; i++)
            {
                final int velocity = VELOCITY_CURVE_POINTS[i];
                buffer.append (addAttribute (SfzOpcode.AMP_VELOCITY_CURVE + velocity, formatDouble (Math.pow (velocity / 127.0, power), 4), i % 6 == 5 || i == VELOCITY_CURVE_POINTS.length - 1));
            }
        }

        // The opcode is given in decibels per key, 100% key tracking is defined as 1 dB per key.
        // The center key is the root key of the zone and defaults to 60 in SFZ
        final double ampKeyTracking = zone.getAmplitudeKeyTracking ();
        if (ampKeyTracking != 0)
        {
            buffer.append (addAttribute (SfzOpcode.AMP_KEY_TRACK, formatDouble (Math.clamp (ampKeyTracking, -1, 1), 3), false));
            buffer.append (addIntegerAttribute (SfzOpcode.AMP_KEY_CENTER, limitToDefault (zone.getKeyRoot (), 60), true));
        }

        final double pan = zone.getPanning ();
        if (pan != 0)
            buffer.append (addAttribute (SfzOpcode.PANNING, Integer.toString ((int) Math.round (pan * 100)), true));

        createVolumeEnvelope (buffer, zone);

        // Amplitude LFO (tremolo)

        final ILfoModulator amplitudeLfoModulator = zone.getAmplitudeLfoModulator ();
        final double lfoDepth = amplitudeLfoModulator.getDepth ();
        if (lfoDepth != 0)
        {
            final StringBuilder lfoStr = new StringBuilder ();
            lfoStr.append (SfzOpcode.AMPLFO_DEPTH).append ('=').append (formatDouble (lfoDepth * ILfoModulator.MAX_VOLUME_DEPTH, 2));

            final ILfo amplitudeLfo = amplitudeLfoModulator.getSource ();
            addLfoTimeAttribute (lfoStr, SfzOpcode.AMPLFO_FREQ, amplitudeLfo.getRate ());
            addLfoTimeAttribute (lfoStr, SfzOpcode.AMPLFO_DELAY, amplitudeLfo.getDelay ());
            addLfoTimeAttribute (lfoStr, SfzOpcode.AMPLFO_FADE, amplitudeLfo.getFadeIn ());

            buffer.append (lfoStr).append (LINE_FEED);
        }

        return buffer.toString ();
    }


    private static void createVolumeEnvelope (final StringBuilder buffer, final ISampleZone zone)
    {
        final StringBuilder envelopeStr = new StringBuilder ();

        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();

        addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.AMPEG_DELAY, amplitudeEnvelope.getDelayTime ());
        addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.AMPEG_ATTACK, amplitudeEnvelope.getAttackTime ());
        addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.AMPEG_HOLD, amplitudeEnvelope.getHoldTime ());
        addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.AMPEG_DECAY, amplitudeEnvelope.getDecayTime ());
        addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.AMPEG_RELEASE, amplitudeEnvelope.getReleaseTime ());

        addEnvelopeLevelAttribute (envelopeStr, SfzOpcode.AMPEG_START, amplitudeEnvelope.getStartLevel ());
        addEnvelopeLevelAttribute (envelopeStr, SfzOpcode.AMPEG_SUSTAIN, amplitudeEnvelope.getSustainLevel ());

        addSlopeAttribute (envelopeStr, SfzOpcode.AMPEG_ATTACK_SHAPE, amplitudeEnvelope.getAttackSlope ());
        addSlopeAttribute (envelopeStr, SfzOpcode.AMPEG_DECAY_SHAPE, amplitudeEnvelope.getDecaySlope ());
        addSlopeAttribute (envelopeStr, SfzOpcode.AMPEG_RELEASE_SHAPE, amplitudeEnvelope.getReleaseSlope ());

        if (!envelopeStr.isEmpty ())
            buffer.append (envelopeStr).append (LINE_FEED);
    }


    /**
     * Create the filter info.
     *
     * @param zone The sample zone
     * @return The created filter opcodes
     */
    private static String createFilter (final ISampleZone zone)
    {
        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isEmpty ())
            return "";

        final IFilter filter = optFilter.get ();
        final String type = FILTER_TYPE_MAP.get (filter.getType ());
        final Set<Integer> allowedPoles = FILTER_POLES.get (type);
        if (allowedPoles == null)
            return "";

        int numPoles = filter.getPoles ();
        if (!allowedPoles.contains (Integer.valueOf (numPoles)))
            numPoles = 2;

        final StringBuilder buffer = new StringBuilder ();
        buffer.append (addAttribute (SfzOpcode.FILTER_TYPE, type + "_" + numPoles + "p", false));
        buffer.append (addAttribute (SfzOpcode.CUTOFF, formatDouble (filter.getCutoff (), 2), false));

        final double velFilterDepth = filter.getCutoffVelocityModulator ().getDepth ();
        if (velFilterDepth != 0)
            buffer.append (addAttribute (SfzOpcode.FIL_VELOCITY_TRACK, Integer.toString ((int) Math.round (velFilterDepth * 9600.0)), false));

        final double filterKeyTracking = filter.getCutoffKeyTracking ();
        buffer.append (addAttribute (SfzOpcode.RESONANCE, formatDouble (filter.getResonance () * IFilter.MAX_RESONANCE, 2), filterKeyTracking <= 0));
        if (filterKeyTracking > 0)
            buffer.append (addAttribute (SfzOpcode.FIL_KEY_TRACK, Integer.toString ((int) Math.round (filter.getCutoffKeyTracking () * 1200.0)), true));

        // Envelope modulation

        final StringBuilder envelopeStr = new StringBuilder ();

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final double envelopeDepth = cutoffModulator.getDepth ();
        if (envelopeDepth != 0)
        {
            buffer.append (SfzOpcode.FILEG_DEPTH).append ('=').append ((int) Math.round (envelopeDepth * IEnvelope.MAX_ENVELOPE_DEPTH)).append (LINE_FEED);

            final IEnvelope filterEnvelope = cutoffModulator.getSource ();

            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.FILEG_DELAY, filterEnvelope.getDelayTime ());
            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.FILEG_ATTACK, filterEnvelope.getAttackTime ());
            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.FILEG_HOLD, filterEnvelope.getHoldTime ());
            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.FILEG_DECAY, filterEnvelope.getDecayTime ());
            addEnvelopeTimeAttribute (envelopeStr, SfzOpcode.FILEG_RELEASE, filterEnvelope.getReleaseTime ());

            addEnvelopeLevelAttribute (envelopeStr, SfzOpcode.FILEG_START, filterEnvelope.getStartLevel ());
            addEnvelopeLevelAttribute (envelopeStr, SfzOpcode.FILEG_SUSTAIN, filterEnvelope.getSustainLevel ());

            addSlopeAttribute (envelopeStr, SfzOpcode.FILEG_ATTACK_SHAPE, filterEnvelope.getAttackSlope ());
            addSlopeAttribute (envelopeStr, SfzOpcode.FILEG_DECAY_SHAPE, filterEnvelope.getDecaySlope ());
            addSlopeAttribute (envelopeStr, SfzOpcode.FILEG_RELEASE_SHAPE, filterEnvelope.getReleaseSlope ());

            if (!envelopeStr.isEmpty ())
                buffer.append (envelopeStr).append (LINE_FEED);
        }

        // LFO Modulation
        final ILfoModulator cutoffLfoModulator = filter.getCutoffLfoModulator ();
        final double lfoDepth = cutoffLfoModulator.getDepth ();
        if (lfoDepth != 0)
        {
            final StringBuilder lfoStr = new StringBuilder ();
            lfoStr.append (SfzOpcode.FILLFO_DEPTH).append ('=').append ((int) Math.round (lfoDepth * 1200));

            final ILfo pitchLfo = cutoffLfoModulator.getSource ();
            addLfoTimeAttribute (lfoStr, SfzOpcode.FILLFO_FREQ, pitchLfo.getRate ());
            addLfoTimeAttribute (lfoStr, SfzOpcode.FILLFO_DELAY, pitchLfo.getDelay ());
            addLfoTimeAttribute (lfoStr, SfzOpcode.FILLFO_FADE, pitchLfo.getFadeIn ());

            buffer.append (lfoStr).append (LINE_FEED);
        }

        return buffer.toString ();
    }


    private static String addAttribute (final String opcode, final String value, final boolean addLineFeed)
    {
        return new StringBuilder ().append (opcode).append ('=').append (value).append (addLineFeed ? LINE_FEED : ' ').toString ();
    }


    private static String addIntegerAttribute (final String opcode, final int value, final boolean addLineFeed)
    {
        return addAttribute (opcode, Integer.toString (value), addLineFeed);
    }


    private static void addEnvelopeTimeAttribute (final StringBuilder sb, final String opcode, final double value)
    {
        if (value <= 0)
            return;
        if (!sb.isEmpty ())
            sb.append (' ');
        sb.append (opcode).append ('=').append (formatAsFloat (Math.clamp (value, 0.0, 100.0)));
    }


    private static void addEnvelopeLevelAttribute (final StringBuilder sb, final String opcode, final double value)
    {
        if (value < 0)
            return;
        if (!sb.isEmpty ())
            sb.append (' ');
        sb.append (opcode).append ('=').append (formatAsFloat (Math.clamp (value * 100.0, 0.0, 100.0)));
    }


    private static void addLfoTimeAttribute (final StringBuilder sb, final String opcode, final double value)
    {
        if (value < 0)
            return;
        if (!sb.isEmpty ())
            sb.append (' ');
        sb.append (opcode).append ('=').append (formatAsFloat (value));
    }


    private static void addSlopeAttribute (final StringBuilder sb, final String opcode, final double value)
    {
        if (value == 0)
            return;
        if (!sb.isEmpty ())
            sb.append (' ');
        sb.append (opcode).append ('=').append (formatAsFloat (Math.clamp (value * 10.0, -10.0, 10.0)));
    }


    private static String formatWithCommentPrefix (final String input)
    {
        final int LINE_LIMIT = 70;
        final String [] words = input.split ("\\s+");
        final StringBuilder result = new StringBuilder ();
        StringBuilder currentLine = new StringBuilder ();

        for (final String word: words)
            if (currentLine.isEmpty ())
                currentLine.append (word);
            else if (currentLine.length () + 1 + word.length () <= LINE_LIMIT)
                currentLine.append (" ").append (word);
            else
            {
                result.append (COMMENT_PREFIX).append (currentLine).append ("\n");
                currentLine = new StringBuilder (word);
            }

        if (!currentLine.isEmpty ())
            result.append (COMMENT_PREFIX).append (currentLine).append ("\n");

        return result.toString ();
    }


    private static String formatAsFloat (final double value)
    {
        return new BigDecimal (Float.toString ((float) value)).stripTrailingZeros ().toPlainString ();
    }


    /**
     * Compares the parameters of the zones within each group. Parameters that are identical across
     * all zones of a group are removed from the zones and appended to the group key. Parameters
     * that are then identical across all groups are further promoted and appended to
     * documentBuffer. All remaining parameters stay unchanged at zone level.
     *
     * @param documentBuffer The top document buffer
     * @param groupContent The groups with the zones and their parameters
     */
    private static void promoteCommonParameters (final StringBuilder documentBuffer, final List<Pair<String, List<Pair<String, List<String>>>>> groupContent)
    {
        if (groupContent.isEmpty ())
            return;

        final List<Set<String>> groupCommonParams = new ArrayList<> ();

        // Step 1: promote parameters that are identical across all zones of a group
        for (int g = 0; g < groupContent.size (); g++)
        {
            final Pair<String, List<Pair<String, List<String>>>> group = groupContent.get (g);
            final List<Pair<String, List<String>>> zones = group.getValue ();

            final Set<String> common = zones.isEmpty () ? Collections.emptySet () : SfzCreator.intersectAll (zones.stream ().map (Pair::getValue).collect (Collectors.toList ()));

            // Remove from zones (only non-common parameters remain)
            for (final Pair<String, List<String>> zone: zones)
                zone.getValue ().removeAll (common);

            groupCommonParams.add (common);
        }

        // Step 2: find parameters that are common across ALL groups
        final List<List<String>> asLists = groupCommonParams.stream ().map (ArrayList::new).collect (Collectors.toList ());
        final Set<String> documentCommon = SfzCreator.intersectAll (asLists);

        // Step 3: update group keys (excluding document-wide parameters)
        for (int g = 0; g < groupContent.size (); g++)
        {
            final Pair<String, List<Pair<String, List<String>>>> group = groupContent.get (g);
            final Set<String> groupOnly = new LinkedHashSet<> (groupCommonParams.get (g));
            groupOnly.removeAll (documentCommon);

            if (!groupOnly.isEmpty ())
            {
                final String updatedKey = SfzCreator.appendParameters (group.getKey (), groupOnly);
                groupContent.set (g, new Pair<> (updatedKey, group.getValue ()));
            }
        }

        // Step 4: append document-wide parameters to documentBuffer
        if (!documentCommon.isEmpty ())
            documentBuffer.append (String.join ("", documentCommon));

        // Step 5: write group keys and zone keys into documentBuffer
        for (int g = 0; g < groupContent.size (); g++)
        {
            final Pair<String, List<Pair<String, List<String>>>> group = groupContent.get (g);
            documentBuffer.append (group.getKey ());
            for (final Pair<String, List<String>> zone: group.getValue ())
                documentBuffer.append (appendParameters (zone.getKey (), zone.getValue ()));
        }
    }


    private static Set<String> intersectAll (final List<List<String>> lists)
    {
        if (lists.isEmpty ())
            return Collections.emptySet ();
        final Set<String> result = new LinkedHashSet<> (lists.get (0));
        for (final List<String> list: lists)
            result.retainAll (list);
        return result;
    }


    private static String appendParameters (final String key, final Collection<String> params)
    {
        if (params.isEmpty ())
            return key;
        return key + String.join ("", params);
    }
}
