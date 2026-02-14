// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.ParameterLevel;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;


/**
 * Creator for SFZ multi-sample files. SFZ has a description file and all related samples in a
 * separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class SfzCreator extends AbstractWavCreator<SfzCreatorUI>
{
    private static final AudioFileFormat.Type      TARGET_FORMAT   = new AudioFileFormat.Type ("FLAC", "flac");
    private static final char                      LINE_FEED       = '\n';
    private static final String                    SFZ_HEADER      = """
            /////////////////////////////////////////////////////////////////////////////
            ////
            """;
    private static final String                    COMMENT_PREFIX  = "//// ";

    private static final Map<FilterType, String>   FILTER_TYPE_MAP = new EnumMap<> (FilterType.class);
    private static final Map<String, Set<Integer>> FILTER_POLES    = new HashMap<> ();
    private static final Map<LoopType, String>     LOOP_TYPE_MAP   = new EnumMap<> (LoopType.class);

    static
    {
        FILTER_TYPE_MAP.put (FilterType.LOW_PASS, "lpf");
        FILTER_TYPE_MAP.put (FilterType.HIGH_PASS, "hpf");
        FILTER_TYPE_MAP.put (FilterType.BAND_PASS, "bpf");
        FILTER_TYPE_MAP.put (FilterType.BAND_REJECTION, "brf");

        final Set<Integer> BPF_POLES = new HashSet<> ();
        Collections.addAll (BPF_POLES, Integer.valueOf (1), Integer.valueOf (2));
        FILTER_POLES.put ("bpf", BPF_POLES);
        final Set<Integer> BRF_POLES = new HashSet<> ();
        Collections.addAll (BRF_POLES, Integer.valueOf (1), Integer.valueOf (2));
        FILTER_POLES.put ("brf", BRF_POLES);
        final Set<Integer> HPF_POLES = new HashSet<> ();
        Collections.addAll (HPF_POLES, Integer.valueOf (1), Integer.valueOf (2), Integer.valueOf (4), Integer.valueOf (6));
        FILTER_POLES.put ("hpf", HPF_POLES);
        final Set<Integer> LPF_POLES = new HashSet<> ();
        Collections.addAll (LPF_POLES, Integer.valueOf (1), Integer.valueOf (2), Integer.valueOf (4), Integer.valueOf (6));
        FILTER_POLES.put ("lpf", LPF_POLES);

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
        final String multiSampleName = createSafeFilename (multisampleSource.getName ());
        final String safeSampleFolderName = multiSampleName + FOLDER_POSTFIX;
        final String metadata = this.createPresetDocument (safeSampleFolderName, multisampleSource);
        if (metadata == null)
            return;

        final File multiFile = this.createUniqueFilename (destinationFolder, multiSampleName, "sfz");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);

        if (this.settingsConfiguration.convertToFlac ())
            try
            {
                this.writeSamples (sampleFolder, multisampleSource, TARGET_FORMAT);
            }
            catch (final UnsupportedAudioFileException ex)
            {
                throw new IOException (ex);
            }
        else
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
    private String createPresetDocument (final String safeSampleFolderName, final IMultisampleSource multisampleSource)
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
            sb.append (COMMENT_PREFIX).append (description.replace ("\n", "\n" + COMMENT_PREFIX)).append (LINE_FEED);
        sb.append (LINE_FEED);

        // Set the name
        final String name = multisampleSource.getName ();
        sb.append ('<').append (SfzHeader.GLOBAL).append (">").append (LINE_FEED);
        if (name != null && !name.isBlank ())
            addAttribute (sb, SfzOpcode.GLOBAL_LABEL, name, true);

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        if (groups.isEmpty ())
        {
            this.notifier.logError ("IDS_ERR_NO_GROUPS_IN_SOURCE");
            return null;
        }

        final ParameterLevel ampEnvParamLevel = getAmpEnvelopeParamLevel (multisampleSource);
        if (ampEnvParamLevel == ParameterLevel.INSTRUMENT)
            createVolumeEnvelope (sb, groups.get (0).getSampleZones ().get (0));

        // Add all groups with all sample zones (regions)
        final Map<IGroup, Integer> roundRobinGroups = multisampleSource.getRoundRobinGroups ();
        for (final IGroup group: groups)
        {
            final List<ISampleZone> zones = group.getSampleZones ();
            if (zones.isEmpty ())
                continue;

            int maxSequence = -1;
            final boolean isNotRoundRobinGroup = !roundRobinGroups.containsKey (group);
            if (isNotRoundRobinGroup)
                // Check for any sample which play round-robin
                for (final ISampleZone zone: zones)
                    if (zone.getPlayLogic () == PlayLogic.ROUND_ROBIN)
                        maxSequence = Math.max (maxSequence, zone.getSequencePosition ());

            sb.append (LINE_FEED).append ('<').append (SfzHeader.GROUP).append (">").append (LINE_FEED);
            final String groupName = group.getName ();
            if (groupName != null && !groupName.isBlank ())
                addAttribute (sb, SfzOpcode.GROUP_LABEL, groupName, true);
            if (isNotRoundRobinGroup)
            {
                if (maxSequence > 0)
                    addIntegerAttribute (sb, SfzOpcode.SEQ_LENGTH, maxSequence, true);
            }
            else
            {
                addIntegerAttribute (sb, SfzOpcode.SEQ_LENGTH, roundRobinGroups.size (), true);
                final Integer sequencePosition = roundRobinGroups.get (group);
                if (sequencePosition != null && sequencePosition.intValue () > 0)
                    addIntegerAttribute (sb, SfzOpcode.SEQ_POSITION, sequencePosition.intValue (), true);
            }

            final TriggerType trigger = group.getTrigger ();
            if (trigger != null && trigger != TriggerType.ATTACK)
                addAttribute (sb, SfzOpcode.TRIGGER, trigger.name ().toLowerCase (Locale.ENGLISH), true);

            if (ampEnvParamLevel == ParameterLevel.GROUP)
                createVolumeEnvelope (sb, zones.get (0));

            for (final ISampleZone zone: zones)
                this.createSample (safeSampleFolderName, sb, zone, isNotRoundRobinGroup, ampEnvParamLevel);
        }

        return sb.toString ();
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param safeSampleFolderName The safe sample folder name
     * @param buffer Where to add the XML code
     * @param zone The sample zone
     * @param isNotRoundRobinGroup If the sample zone does not belong to a round robin group
     * @param ampEnvParameterLevel The level to which to apply the amplitude envelope parameters
     */
    private void createSample (final String safeSampleFolderName, final StringBuilder buffer, final ISampleZone zone, final boolean isNotRoundRobinGroup, final ParameterLevel ampEnvParameterLevel)
    {
        final String ending = this.settingsConfiguration.convertToFlac () ? ".flac" : ".wav";

        buffer.append ("\n<").append (SfzHeader.REGION).append (">\n");
        addAttribute (buffer, SfzOpcode.SAMPLE, AbstractCreator.formatFileName (safeSampleFolderName, zone.getName () + ending), true);

        // Default is 'attack' and does not need to be added
        final TriggerType trigger = zone.getTrigger ();
        if (trigger != TriggerType.ATTACK)
            addAttribute (buffer, SfzOpcode.TRIGGER, trigger.name ().toLowerCase (Locale.ENGLISH), true);

        if (zone.isReversed ())
            addAttribute (buffer, SfzOpcode.DIRECTION, "reverse", true);
        if (zone.getPlayLogic () == PlayLogic.ROUND_ROBIN && isNotRoundRobinGroup)
            addIntegerAttribute (buffer, SfzOpcode.SEQ_POSITION, Math.max (1, zone.getSequencePosition ()), true);

        ////////////////////////////////////////////////////////////
        // Key range

        final int keyRoot = zone.getKeyRoot ();
        final int keyLow = zone.getKeyLow ();
        final int keyHigh = zone.getKeyHigh ();
        if (keyRoot == keyLow && keyLow == keyHigh)
        {
            // Pitch and range are the same, use single key attribute
            if (keyRoot >= 0)
                addIntegerAttribute (buffer, SfzOpcode.KEY, keyRoot, true);
        }
        else
        {
            if (keyRoot >= 0)
                addIntegerAttribute (buffer, SfzOpcode.PITCH_KEY_CENTER, keyRoot, true);
            addIntegerAttribute (buffer, SfzOpcode.LO_KEY, limitToDefault (keyLow, 0), false);
            addIntegerAttribute (buffer, SfzOpcode.HI_KEY, limitToDefault (keyHigh, 127), true);
        }

        final int crossfadeLow = zone.getNoteCrossfadeLow ();
        if (crossfadeLow > 0)
        {
            addIntegerAttribute (buffer, SfzOpcode.XF_IN_LO_KEY, Math.max (0, keyLow - crossfadeLow), false);
            addIntegerAttribute (buffer, SfzOpcode.XF_IN_HI_KEY, keyLow, true);
        }
        final int crossfadeHigh = zone.getNoteCrossfadeHigh ();
        if (crossfadeHigh > 0)
        {
            addIntegerAttribute (buffer, SfzOpcode.XF_OUT_LO_KEY, keyHigh, false);
            addIntegerAttribute (buffer, SfzOpcode.XF_OUT_HI_KEY, Math.min (127, keyHigh + crossfadeHigh), true);
        }

        ////////////////////////////////////////////////////////////
        // Velocity

        final int velocityLow = zone.getVelocityLow ();
        final int velocityHigh = zone.getVelocityHigh ();
        if (velocityLow > 1)
            addIntegerAttribute (buffer, SfzOpcode.LO_VEL, limitToDefault (velocityLow, 1), velocityHigh == 127);
        if (velocityHigh > 0 && velocityHigh < 127)
            addIntegerAttribute (buffer, SfzOpcode.HI_VEL, limitToDefault (velocityHigh, 127), true);

        final int crossfadeVelocityLow = zone.getVelocityCrossfadeLow ();
        if (crossfadeVelocityLow > 0)
        {
            addIntegerAttribute (buffer, SfzOpcode.XF_IN_LO_VEL, Math.max (0, velocityLow - crossfadeVelocityLow), false);
            addIntegerAttribute (buffer, SfzOpcode.XF_IN_HI_VEL, velocityLow, true);
        }

        final int crossfadeVelocityHigh = zone.getVelocityCrossfadeHigh ();
        if (crossfadeVelocityHigh > 0)
        {
            addIntegerAttribute (buffer, SfzOpcode.XF_OUT_LO_VEL, velocityHigh, false);
            addIntegerAttribute (buffer, SfzOpcode.XF_OUT_HI_VEL, Math.min (127, velocityHigh + crossfadeVelocityHigh), true);
        }

        ////////////////////////////////////////////////////////////
        // Start, end, tune, volume

        final int start = zone.getStart ();
        if (start >= 0)
            addIntegerAttribute (buffer, SfzOpcode.OFFSET, start, false);
        final int end = zone.getStop ();
        if (end >= 0)
            addIntegerAttribute (buffer, SfzOpcode.END, end, true);

        final double tune = zone.getTuning ();
        if (tune != 0)
            addIntegerAttribute (buffer, SfzOpcode.TUNE, (int) Math.round (tune * 100), true);

        final int keyTracking = (int) Math.round (zone.getKeyTracking () * 100.0);
        if (keyTracking != 100)
            addIntegerAttribute (buffer, SfzOpcode.PITCH_KEYTRACK, keyTracking, true);

        createVolume (buffer, zone, ampEnvParameterLevel);

        ////////////////////////////////////////////////////////////
        // Pitch Bend / Envelope

        final int bendUp = zone.getBendUp ();
        if (bendUp != 0)
            addIntegerAttribute (buffer, SfzOpcode.BEND_UP, bendUp, true);
        final int bendDown = zone.getBendDown ();
        if (bendDown != 0)
            addIntegerAttribute (buffer, SfzOpcode.BEND_DOWN, bendDown, true);

        final StringBuilder envelopeStr = new StringBuilder ();

        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
        final double envelopeDepth = pitchModulator.getDepth ();
        if (envelopeDepth != 0)
        {
            buffer.append (SfzOpcode.PITCHEG_DEPTH).append ('=').append ((int) (envelopeDepth * IEnvelope.MAX_ENVELOPE_DEPTH)).append (LINE_FEED);

            final IEnvelope pitchEnvelope = pitchModulator.getSource ();

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
                buffer.append (envelopeStr).append (LINE_FEED);
        }

        // //////////////////////////////////////////////////////////
        // Sample Loop

        this.createLoops (buffer, zone);

        // //////////////////////////////////////////////////////////
        // Filter

        createFilter (buffer, zone);
    }


    /**
     * Create the loop info.
     *
     * @param buffer Where to add the XML code
     * @param zone The sample zone
     */
    private void createLoops (final StringBuilder buffer, final ISampleZone zone)
    {
        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
            addAttribute (buffer, SfzOpcode.LOOP_MODE, "no_loop", false);
        else
        {
            final ISampleLoop sampleLoop = loops.get (0);
            // SFZ currently only supports forward looping
            addAttribute (buffer, SfzOpcode.LOOP_MODE, "loop_continuous", false);
            final String type = LOOP_TYPE_MAP.get (sampleLoop.getType ());
            // No need to write the default value
            if (!"forward".equals (type))
                addAttribute (buffer, SfzOpcode.LOOP_TYPE, type, false);
            addIntegerAttribute (buffer, SfzOpcode.LOOP_START, sampleLoop.getStart (), false);
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
                        loopLengthInSeconds = loopLength / (double) zone.getSampleData ().getAudioMetadata ().getSampleRate ();
                        final double crossfadeInSeconds = crossfade * loopLengthInSeconds;
                        buffer.append (' ').append (SfzOpcode.LOOP_CROSSFADE).append ('=').append (crossfadeInSeconds);
                    }
                    catch (final IOException ex)
                    {
                        this.notifier.logError (ex);
                    }
                }
            }
        }
        buffer.append (LINE_FEED);
    }


    /**
     * Create the volume and amplitude envelope parameters.
     *
     * @param buffer Where to add the created text
     * @param zone The sample zone
     * @param ampEnvParameterLevel The level to which to apply the amplitude envelope parameters
     */
    private static void createVolume (final StringBuilder buffer, final ISampleZone zone, final ParameterLevel ampEnvParameterLevel)
    {
        final double volume = zone.getGain ();
        final double velAmpDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
        if (volume != 0)
            addAttribute (buffer, SfzOpcode.VOLUME, formatDouble (volume, 2), velAmpDepth == 1);
        if (velAmpDepth < 1)
            addAttribute (buffer, SfzOpcode.AMP_VELOCITY_TRACK, formatDouble (velAmpDepth * 100.0, 2), true);

        final double pan = zone.getPanning ();
        if (pan != 0)
            addAttribute (buffer, SfzOpcode.PANNING, Integer.toString ((int) Math.round (pan * 100)), true);

        if (ampEnvParameterLevel == ParameterLevel.ZONE)
            createVolumeEnvelope (buffer, zone);
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
     * @param buffer Where to add the XML code
     * @param zone The sample zone
     */
    private static void createFilter (final StringBuilder buffer, final ISampleZone zone)
    {
        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isEmpty ())
            return;

        final IFilter filter = optFilter.get ();
        final String type = FILTER_TYPE_MAP.get (filter.getType ());
        final Set<Integer> allowedPoles = FILTER_POLES.get (type);
        if (allowedPoles == null)
            return;

        int numPoles = filter.getPoles ();
        if (!allowedPoles.contains (Integer.valueOf (numPoles)))
            numPoles = 2;
        addAttribute (buffer, SfzOpcode.FILTER_TYPE, type + "_" + numPoles + "p", false);
        addAttribute (buffer, SfzOpcode.CUTOFF, formatDouble (filter.getCutoff (), 2), false);

        final double velFilterDepth = filter.getCutoffVelocityModulator ().getDepth ();
        if (velFilterDepth != 0)
            addAttribute (buffer, SfzOpcode.FIL_VELOCITY_TRACK, Integer.toString ((int) Math.round (velFilterDepth * 9600.0)), false);

        addAttribute (buffer, SfzOpcode.RESONANCE, formatDouble (filter.getResonance () * IFilter.MAX_RESONANCE, 2), true);

        // Envelope modulation

        final StringBuilder envelopeStr = new StringBuilder ();

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final double envelopeDepth = cutoffModulator.getDepth ();
        if (envelopeDepth > 0)
        {
            buffer.append (SfzOpcode.FILEG_DEPTH).append ('=').append ((int) (envelopeDepth * IEnvelope.MAX_ENVELOPE_DEPTH)).append (LINE_FEED);

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
    }


    private static void addAttribute (final StringBuilder sb, final String opcode, final String value, final boolean addLineFeed)
    {
        sb.append (opcode).append ('=').append (value).append (addLineFeed ? LINE_FEED : ' ');
    }


    private static void addIntegerAttribute (final StringBuilder sb, final String opcode, final int value, final boolean addLineFeed)
    {
        addAttribute (sb, opcode, Integer.toString (value), addLineFeed);
    }


    private static void addEnvelopeTimeAttribute (final StringBuilder sb, final String opcode, final double value)
    {
        if (value <= 0)
            return;
        if (!sb.isEmpty ())
            sb.append (' ');
        sb.append (opcode).append ('=').append (Math.clamp (value, 0.0, 100.0));
    }


    private static void addEnvelopeLevelAttribute (final StringBuilder sb, final String opcode, final double value)
    {
        if (value < 0)
            return;
        if (!sb.isEmpty ())
            sb.append (' ');
        sb.append (opcode).append ('=').append (Math.clamp (value * 100.0, 0.0, 100.0));
    }


    private static void addSlopeAttribute (final StringBuilder sb, final String opcode, final double value)
    {
        if (value == 0)
            return;
        if (!sb.isEmpty ())
            sb.append (' ');
        sb.append (opcode).append ('=').append (Math.clamp (value * 10.0, -10.0, 10.0));
    }
}
