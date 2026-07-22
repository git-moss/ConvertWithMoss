// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.StringUtils;


/**
 * Creator for Logic EXS24 files.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24Creator extends AbstractWavCreator<WavChunkSettingsUI>
{
    /**
     * The value to use for a fully enabled random sample selection. The exact scaling of the
     * parameter is unknown, 1000 is used since all other normalized EXS24 parameters use 1000 for
     * 100%.
     */
    private static final int RANDOM_SAMPLE_SELECT_FULL = 1000;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EXS24Creator (final INotifier notifier)
    {
        super ("Logic EXS24", "EXS24", notifier, new WavChunkSettingsUI ("EXS24"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a sub-folder to have the EXS file together with the samples
        final File subFolder = this.createUniqueFilename (destinationFolder, sampleName, "");
        if (!subFolder.exists () && !subFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
            return;
        }

        final File multiFile = this.createUniqueFilename (subFolder, sampleName, "exs");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final Map<String, File> writtenSamples = new HashMap<> ();
        for (final File sampleFile: this.writeSamples (subFolder, multisampleSource))
            writtenSamples.put (sampleFile.getName (), sampleFile);
        this.storeMultisample (multisampleSource, multiFile, writtenSamples);

        this.progress.notifyDone ();
    }


    private void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final Map<String, File> writtenSamples) throws IOException
    {
        final boolean isBigEndian = true;

        final EXS24File exs24File = new EXS24File (this.notifier);

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        final Map<IGroup, Integer> roundRobinGroups = multisampleSource.getRoundRobinGroups ();
        boolean hasRandomZone = false;
        for (final IGroup group: groups)
        {
            final EXS24Group exsGroup = new EXS24Group ();
            exs24File.addGroup (exsGroup);
            exsGroup.name = group.getName ();
            exsGroup.releaseTrigger = group.getTrigger () == TriggerType.RELEASE;

            // The exclusive group can only be stored on the group level, therefore it can only be
            // applied if all zones of the group use the same one
            exsGroup.exclusive = getUniformExclusiveGroup (group);

            if (roundRobinGroups.containsKey (group))
            {
                final int sequencePosition = roundRobinGroups.get (group).intValue ();
                if (sequencePosition > 0)
                {
                    exsGroup.roundRobinGroupPos = sequencePosition - 2;
                    exsGroup.enableByRoundRobin = true;
                }
            }

            for (final ISampleZone zone: group.getSampleZones ())
            {
                final EXS24Zone exs24Zone = exs24File.createZone ();
                final EXS24Sample exs24Sample = exs24File.createSample (exs24Zone);

                hasRandomZone = hasRandomZone || zone.getPlayLogic () == PlayLogic.RANDOM;

                // Fill zone
                exs24Zone.name = zone.getName ();

                exs24Zone.keyLow = limitToDefault (zone.getKeyLow (), 0);
                exs24Zone.keyHigh = limitToDefault (zone.getKeyHigh (), 127);
                exs24Zone.key = limitToDefault (zone.getKeyRoot (), exs24Zone.keyLow);
                exs24Zone.velocityRangeOn = true;
                exs24Zone.velocityLow = limitToDefault (zone.getVelocityLow (), 1);
                exs24Zone.velocityHigh = limitToDefault (zone.getVelocityHigh (), 127);
                exs24Zone.sampleStart = zone.getStart ();
                exs24Zone.sampleEnd = zone.getStop ();
                exs24Zone.reverse = zone.isReversed ();
                exs24Zone.oneshot = zone.isOneShot ();
                exs24Zone.volumeAdjust = (int) Math.round (zone.getGain ());
                exs24Zone.pitch = true;
                final double tune = zone.getTuning ();
                exs24Zone.coarseTuning = (int) Math.round (tune);
                exs24Zone.fineTuning = (int) Math.round ((tune - exs24Zone.coarseTuning) * 100);
                exs24Zone.pan = (int) Math.round (zone.getPanning () * 50);

                final Optional<ISampleData> sampleData = zone.getSampleData ();
                if (sampleData.isEmpty ())
                    throw new IOException ("Empty sample data in zone: " + zone.getName ());
                final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();

                final List<ISampleLoop> loops = zone.getLoops ();
                exs24Zone.loopOn = !loops.isEmpty ();
                if (exs24Zone.loopOn)
                {
                    final ISampleLoop loop = loops.get (0);
                    switch (loop.getType ())
                    {
                        case LoopType.BACKWARDS -> exs24Zone.loopDirection = 1;
                        case LoopType.ALTERNATING -> exs24Zone.loopDirection = 2;
                        // LoopType.FORWARDS
                        default -> exs24Zone.loopDirection = 0;
                    }
                    exs24Zone.loopStart = loop.getStart ();
                    exs24Zone.loopEnd = loop.getEnd () + 1;
                    final double crossfade = loop.getCrossfade ();
                    final int loopLength = loop.getLength ();
                    if (crossfade > 0 && loopLength > 0)
                    {
                        final double loopLengthInSeconds = loopLength / (double) audioMetadata.getSampleRate ();
                        exs24Zone.loopCrossfade = (int) Math.round (crossfade * loopLengthInSeconds * 1000.0);
                    }
                    exs24Zone.loopTune = (int) Math.round (loop.getTuning () * 100.0);
                }

                // Fill sample
                final String name = zone.getName () + ".wav";
                final File sampleFile = writtenSamples.get (name);
                exs24Sample.name = name;
                exs24Sample.waveDataStart = (int) WaveFile.getPositionOfDataChunkData (sampleFile);
                exs24Sample.length = audioMetadata.getNumberOfSamples ();
                exs24Sample.sampleRate = audioMetadata.getSampleRate ();
                exs24Sample.bitDepth = audioMetadata.getBitResolution ();
                exs24Sample.channels = audioMetadata.getChannels ();
                exs24Sample.channels2 = audioMetadata.getChannels ();
                exs24Sample.type = isBigEndian ? "WAVE" : "EVAW";
                exs24Sample.size = (int) sampleFile.length ();
                exs24Sample.filePath = "";
                exs24Sample.fileName = name;
            }
        }

        exs24File.createInstrument (StringUtils.fixASCII (multisampleSource.getName ()));

        // Random sample selection can only be enabled globally
        if (hasRandomZone)
            exs24File.addParameter (EXS24Parameters.RANDOM_SAMPLE_SEL, RANDOM_SAMPLE_SELECT_FULL);

        // The voice settings can only be stored globally. The polyphony is a plain number of
        // voices and 'Mono Legato' is a simple switch. Note: the portamento time is intentionally
        // not written to the GLIDE parameter, since the mapping of a time in seconds to its value
        // is unknown
        final int polyphony = multisampleSource.getPolyphony ();
        final boolean isMonophonicLegato = multisampleSource.isMonophonicLegato ();
        if (polyphony > 0)
            exs24File.addParameter (EXS24Parameters.POLYPHONY_VOICES, Math.clamp (polyphony, 1, EXS24Parameters.MAX_POLYPHONY_VOICES));
        else if (isMonophonicLegato)
            exs24File.addParameter (EXS24Parameters.POLYPHONY_VOICES, 1);
        if (isMonophonicLegato)
            exs24File.addParameter (EXS24Parameters.MONO_LEGATO, 1);

        // Fill global parameters from zone 1
        if (!groups.isEmpty ())
        {
            final List<ISampleZone> sampleZones = groups.get (0).getSampleZones ();
            if (!sampleZones.isEmpty ())
            {
                final ISampleZone zone = sampleZones.get (0);

                // The volume key-scaling uses the same scaling as the filter key-tracking below,
                // which means 1000 = 100%
                final double amplitudeKeyTracking = zone.getAmplitudeKeyTracking ();
                if (amplitudeKeyTracking != 0)
                    exs24File.addParameter (EXS24Parameters.VOLUME_KEYSCALE, (int) Math.round (Math.clamp (amplitudeKeyTracking, -1, 1) * 1000.0));

                // Pitch bend up/down
                exs24File.addParameter (EXS24Parameters.PITCH_BEND_UP, Math.clamp (Math.round (zone.getBendUp () / 100.0), 0, 24));
                // The down amount is stored as a positive number of semi-tones, -1 is the special
                // value for 'linked to the up amount'
                exs24File.addParameter (EXS24Parameters.PITCH_BEND_DOWN, Math.clamp (Math.round (Math.abs (zone.getBendDown ()) / 100.0), 0, 24));

                final double velocityDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
                final int velocityModulation = (int) Math.round (Math.clamp ((1 - velocityDepth) * -60.0, -60, 0));
                exs24File.addParameter (EXS24Parameters.ENV1_VEL_SENS, velocityModulation);

                final EXS24Parameters parameters = exs24File.getParameters ();
                createEnvelope (parameters, 1, zone.getAmplitudeEnvelopeModulator ());
                applyFilterParameters (exs24File, parameters, multisampleSource.getGlobalFilter ());
            }
        }

        try (final OutputStream out = new FileOutputStream (multiFile))
        {
            exs24File.write (out, isBigEndian);
        }
    }


    /**
     * Get the exclusive group of the zones of the given group. EXS24 can only store this attribute
     * on the group level, therefore it is only applied if all zones of the group use the same one.
     *
     * @param group The group from which to get the exclusive group
     * @return The exclusive group or 0 if there is none or the zones do not share the same one
     */
    private static int getUniformExclusiveGroup (final IGroup group)
    {
        int exclusiveGroup = -1;
        for (final ISampleZone zone: group.getSampleZones ())
        {
            final int zoneExclusiveGroup = zone.getExclusiveGroup ();
            if (exclusiveGroup == -1)
                exclusiveGroup = zoneExclusiveGroup;
            else if (exclusiveGroup != zoneExclusiveGroup)
                return 0;
        }
        // The value is stored in one byte
        return Math.clamp (exclusiveGroup, 0, 255);
    }


    private static void createEnvelope (final EXS24Parameters parameters, final int envelopeIndex, final IEnvelopeModulator modulator)
    {
        final IEnvelope envelope = modulator.getSource ();
        final double depth = modulator.getDepth ();
        if (depth == 0)
            return;

        final int delay = formatEnvTime (envelope.getDelayTime ());
        final int attack = formatEnvTime (envelope.getAttackTime ());
        final int hold = formatEnvTime (envelope.getHoldTime ());
        final int decay = formatEnvTime (envelope.getDecayTime ());
        final int sustain = formatEnvVolume (envelope.getSustainLevel (), depth);
        final int release = formatEnvTime (envelope.getReleaseTime ());
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_DELAY_START : EXS24Parameters.ENV2_DELAY_START, delay);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_ATK_HI_VEL : EXS24Parameters.ENV2_ATK_HI_VEL, attack);
        // The attack time at the lowest velocity must be set to the same value, otherwise it stays
        // at 0 and the attack time gets velocity dependent
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_ATK_LO_VEL : EXS24Parameters.ENV2_ATK_LO_VEL, attack);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_HOLD : EXS24Parameters.ENV2_HOLD, hold);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_DECAY : EXS24Parameters.ENV2_DECAY, decay);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_SUSTAIN : EXS24Parameters.ENV2_SUSTAIN, sustain);
        parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_RELEASE : EXS24Parameters.ENV2_RELEASE, release);

        // EXS24 stores a second attack time which is applied at the lowest velocity, see
        // EXS24Detector.convertVelocityToAttack for the conversion
        final double timeVelocityTracking = envelope.getTimeVelocityTracking ();
        if (timeVelocityTracking != 0)
            parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_ATK_LO_VEL : EXS24Parameters.ENV2_ATK_LO_VEL, Math.clamp (Math.round (attack + timeVelocityTracking * 127.0), 0, 127));

        final double attackSlope = envelope.getAttackSlope ();
        if (attackSlope != 0)
        {
            int v = (int) Math.round (attackSlope * 99);
            if (v < 0)
                v += 0x100 + 0xFF00;
            parameters.put (envelopeIndex == 1 ? EXS24Parameters.ENV1_ATK_CURVE : EXS24Parameters.ENV2_ATK_CURVE, v);
        }
    }


    private static void applyFilterParameters (final EXS24File exs24File, final EXS24Parameters parameters, final Optional<IFilter> filterOpt)
    {
        final boolean isEnabled = filterOpt.isPresent ();
        parameters.put (EXS24Parameters.FILTER1_TOGGLE, isEnabled ? 1 : 0);
        if (!isEnabled)
            return;

        final IFilter filter = filterOpt.get ();
        final int poles = filter.getPoles ();
        final int filterTypeIndex;
        switch (filter.getType ())
        {
            case LOW_PASS:
                switch (poles)
                {
                    default:
                    case 4:
                        filterTypeIndex = 0;
                        break;
                    case 3:
                        filterTypeIndex = 1;
                        break;
                    case 2:
                        filterTypeIndex = 2;
                        break;
                    case 1:
                        filterTypeIndex = 3;
                        break;
                }
                break;

            case HIGH_PASS:
                filterTypeIndex = 4;
                break;

            case BAND_PASS:
                filterTypeIndex = 5;
                break;

            case BAND_REJECTION:
            default:
                parameters.put (EXS24Parameters.FILTER1_TOGGLE, 0);
                return;
        }
        parameters.put (EXS24Parameters.FILTER1_TYPE, filterTypeIndex);

        final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
        if (cutoffEnvelopeModulator.getDepth () != 0)
        {
            createEnvelope (parameters, 2, cutoffEnvelopeModulator);
            final EXSModulator env2Modulator = new EXSModulator (EXSModulator.SOURCE_ENV2, EXSModulator.DESTINATION_FILTER_1_CUTOFF);
            env2Modulator.lowValue = (int) Math.round (cutoffEnvelopeModulator.getDepth () * 1000.0);
            env2Modulator.highValue = env2Modulator.lowValue < 0 ? 0 : 1000;
            exs24File.addModulator (env2Modulator);
        }

        parameters.put (EXS24Parameters.FILTER1_CUTOFF, (int) Math.round (MathUtils.normalize (filter.getCutoff (), 0, IFilter.MAX_FREQUENCY) * 1000.0));
        parameters.put (EXS24Parameters.FILTER1_RESO, (int) Math.round (filter.getResonance () * 1000));
        parameters.put (EXS24Parameters.FILTER1_KEYTRACK, (int) Math.round (filter.getCutoffKeyTracking () * 1000.0));
    }


    private static int formatEnvTime (final double time)
    {
        // The device maps the 0..127 parameter to a maximum of 10 seconds with a fourth-power
        // curve (see EXS24Detector.envelopeTimeToSeconds), so invert it:
        // parameter = 127 * (seconds / 10) ^ (1/4).
        if (time <= 0)
            return 0;
        return (int) Math.round (127.0 * Math.pow (Math.min (time, 10.0) / 10.0, 0.25));
    }


    private static int formatEnvVolume (final double volume, final double depth)
    {
        return (int) Math.round ((volume < 0 ? 127 : volume * 127.0) * depth);
    }
}