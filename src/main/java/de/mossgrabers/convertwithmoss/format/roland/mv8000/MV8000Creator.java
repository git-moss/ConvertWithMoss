// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mv8000;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.StringUtils;


/**
 * Creator for Roland MV-8000/MV-8800 patch files (.MV0).
 *
 * @author Jürgen Moßgraber
 */
public class MV8000Creator extends AbstractCreator<EmptySettingsUI>
{
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, MV8000Sample.SAMPLE_RATE, true);

    private static final Map<String, Integer>   CATEGORY_MAP       = new HashMap<> ();
    static
    {
        CATEGORY_MAP.put (TagDetector.CATEGORY_PIANO, Integer.valueOf (1));
        CATEGORY_MAP.put (TagDetector.CATEGORY_KEYBOARD, Integer.valueOf (3));
        CATEGORY_MAP.put (TagDetector.CATEGORY_BELL, Integer.valueOf (4));
        CATEGORY_MAP.put (TagDetector.CATEGORY_CHROMATIC_PERCUSSION, Integer.valueOf (5));
        CATEGORY_MAP.put (TagDetector.CATEGORY_ORGAN, Integer.valueOf (6));
        CATEGORY_MAP.put (TagDetector.CATEGORY_GUITAR, Integer.valueOf (10));
        CATEGORY_MAP.put (TagDetector.CATEGORY_BASS, Integer.valueOf (12));
        CATEGORY_MAP.put (TagDetector.CATEGORY_STRINGS, Integer.valueOf (14));
        CATEGORY_MAP.put (TagDetector.CATEGORY_ORCHESTRAL, Integer.valueOf (15));
        CATEGORY_MAP.put (TagDetector.CATEGORY_ENSEMBLE, Integer.valueOf (15));
        CATEGORY_MAP.put (TagDetector.CATEGORY_WINDS, Integer.valueOf (17));
        CATEGORY_MAP.put (TagDetector.CATEGORY_PIPE, Integer.valueOf (18));
        CATEGORY_MAP.put (TagDetector.CATEGORY_BRASS, Integer.valueOf (19));
        CATEGORY_MAP.put (TagDetector.CATEGORY_LEAD, Integer.valueOf (22));
        CATEGORY_MAP.put (TagDetector.CATEGORY_MONOSYNTH, Integer.valueOf (27));
        CATEGORY_MAP.put (TagDetector.CATEGORY_CHIP, Integer.valueOf (27));
        CATEGORY_MAP.put (TagDetector.CATEGORY_SYNTH, Integer.valueOf (27));
        CATEGORY_MAP.put (TagDetector.CATEGORY_PAD, Integer.valueOf (29));
        CATEGORY_MAP.put (TagDetector.CATEGORY_DRONE, Integer.valueOf (29));
        CATEGORY_MAP.put (TagDetector.CATEGORY_VOCAL, Integer.valueOf (30));
        CATEGORY_MAP.put (TagDetector.CATEGORY_PLUCK, Integer.valueOf (31));
        CATEGORY_MAP.put (TagDetector.CATEGORY_WORLD, Integer.valueOf (32));
        CATEGORY_MAP.put (TagDetector.CATEGORY_PERCUSSION, Integer.valueOf (34));
        CATEGORY_MAP.put (TagDetector.CATEGORY_FX, Integer.valueOf (35));
        CATEGORY_MAP.put (TagDetector.CATEGORY_DESTRUCTION, Integer.valueOf (35));
        CATEGORY_MAP.put (TagDetector.CATEGORY_LOOPS, Integer.valueOf (36));
        CATEGORY_MAP.put (TagDetector.CATEGORY_DRUM, Integer.valueOf (37));
        CATEGORY_MAP.put (TagDetector.CATEGORY_ACOUSTIC_DRUM, Integer.valueOf (37));
        CATEGORY_MAP.put (TagDetector.CATEGORY_KICK, Integer.valueOf (37));
        CATEGORY_MAP.put (TagDetector.CATEGORY_SNARE, Integer.valueOf (37));
        CATEGORY_MAP.put (TagDetector.CATEGORY_HI_HAT, Integer.valueOf (37));
        CATEGORY_MAP.put (TagDetector.CATEGORY_CLAP, Integer.valueOf (37));
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MV8000Creator (final INotifier notifier)
    {
        super ("Roland MV-8000", "MV8000", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String name = createSafeFilename (multisampleSource.getName ());
        final File outputFile = this.createUniqueFilename (destinationFolder, name, "MV0");
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());

        // The MV-8000 has a fixed sample rate
        recalculateSamplePositions (multisampleSource, MV8000Sample.SAMPLE_RATE);

        final MV8000Patch patch = new MV8000Patch ();
        patch.setName (multisampleSource.getName ());
        final Integer category = CATEGORY_MAP.get (multisampleSource.getMetadata ().getCategory ());
        patch.setCategory (category == null ? 0 : category.intValue ());

        final int [] noteTable = patch.getNoteTable ();
        final Map<Long, Integer> partialsByRange = new HashMap<> ();
        final int [] slotCounts = new int [MV8000Patch.NUM_PARTIALS];
        final Set<String> usedSampleNames = new HashSet<> ();
        final Map<Object, Integer> sampleIdsByContent = new HashMap<> ();
        int numPartials = 0;
        int sampleId = 1;

        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int keyLow = Math.max (zone.getKeyLow (), MV8000Patch.NOTE_BASE);
                final int keyHigh = Math.min (zone.getKeyHigh (), MV8000Patch.NOTE_BASE + MV8000Patch.NUM_NOTES - 1);
                if (keyLow > keyHigh)
                {
                    this.notifier.logError ("IDS_MV8000_ZONE_OUT_OF_RANGE", zone.getName ());
                    continue;
                }

                // Find or create the partial for the key range of the zone
                final Long rangeKey = Long.valueOf ((long) keyLow << 8 | keyHigh);
                Integer partialIndex = partialsByRange.get (rangeKey);
                if (partialIndex == null)
                {
                    if (numPartials >= MV8000Patch.NUM_PARTIALS)
                    {
                        this.notifier.logError ("IDS_MV8000_TOO_MANY_PARTIALS", zone.getName ());
                        continue;
                    }
                    partialIndex = Integer.valueOf (numPartials);
                    numPartials++;
                    partialsByRange.put (rangeKey, partialIndex);
                    final MV8000Partial partial = patch.getPartial (partialIndex.intValue ());
                    partial.setName (zone.getName ());
                    writeAmplitudeEnvelope (partial, zone);
                    writeFilter (partial, zone);
                    for (int note = keyLow; note <= keyHigh; note++)
                        if (noteTable[note - MV8000Patch.NOTE_BASE] < 0)
                            noteTable[note - MV8000Patch.NOTE_BASE] = partialIndex.intValue ();
                }

                sampleId = this.fillSlots (patch, partialIndex.intValue (), slotCounts, zone, sampleId, usedSampleNames, sampleIdsByContent);
            }

        try (final OutputStream out = new BufferedOutputStream (new FileOutputStream (outputFile)))
        {
            patch.write (out);
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Add the sample(s) of the zone to the SMT slots of the partial. A stereo sample occupies 2
     * slots.
     *
     * @param patch The patch
     * @param partialIndex The index of the partial
     * @param slotCounts The number of already used slots of all partials
     * @param zone The zone to add
     * @param sampleId The next free sample ID
     * @param usedSampleNames All sample names used so far to create unique ones
     * @param sampleIdsByContent Already written samples to re-use for zones with identical content
     * @return The next free sample ID
     * @throws IOException Could not convert the sample data
     */
    private int fillSlots (final MV8000Patch patch, final int partialIndex, final int [] slotCounts, final ISampleZone zone, final int sampleId, final Set<String> usedSampleNames, final Map<Object, Integer> sampleIdsByContent) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException ("Empty sample data in zone: " + zone.getName ());

        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_FORMAT);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (numChannels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (numChannels), zone.getName ());
            return sampleId;
        }
        final boolean isStereo = numChannels == 2;

        final int numSlots = isStereo ? 2 : 1;
        if (slotCounts[partialIndex] + numSlots > MV8000Partial.NUM_SMT_SLOTS)
        {
            this.notifier.logError ("IDS_MV8000_TOO_MANY_LAYERS", zone.getName ());
            return sampleId;
        }

        final byte [] pcmData = waveFile.getDataChunk ().getData ();
        final int numFrames = pcmData.length / (2 * numChannels);

        // Loop and playback range
        final List<ISampleLoop> loops = zone.getLoops ();
        // A one-shot slot ignores a note-off - and the loop - and plays the sample up to its end
        final boolean isOneShot = zone.isOneShot () || loops.isEmpty ();
        final boolean isKeyTracked = zone.getKeyTracking () != 0;
        int loopStart = 0;
        int endPoint = zone.getStop () > 0 ? Math.min (zone.getStop (), numFrames) : numFrames;
        if (!loops.isEmpty ())
        {
            final ISampleLoop loop = loops.get (0);
            loopStart = Math.clamp (loop.getStart (), 0, numFrames);
            if (loop.getEnd () > loopStart)
                endPoint = Math.min (loop.getEnd (), numFrames);
        }

        // Split the tuning into the coarse and fine tune fields
        final double tuning = zone.getTuning ();
        final int semitones = Math.clamp ((int) Math.round (tuning), -48, 48);
        final int coarseTune = 64 + semitones;
        final int fineTune = Math.clamp (64L + (int) Math.round ((tuning - semitones) * 100), 14, 114);
        final int rootKey = Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), 0, 127);

        final String baseName = createUniqueSampleName (zone.getName (), usedSampleNames, isStereo ? 10 : 12);

        final MV8000Partial partial = patch.getPartial (partialIndex);
        int nextSampleId = sampleId;
        for (int channel = 0; channel < numSlots; channel++)
        {
            final byte [] waveData = extractChannelBigEndian (pcmData, numChannels, channel);
            final int startPoint = Math.clamp (zone.getStart (), 0, numFrames);

            // Re-use an already written sample with identical content and parameters, e.g. when
            // the same sample is mapped to several key ranges. The root key does not matter for
            // slots which do not track the keyboard.
            final Object contentKey = List.of (ByteBuffer.wrap (waveData), Integer.valueOf (startPoint), Integer.valueOf (loopStart), Integer.valueOf (endPoint), Integer.valueOf (isKeyTracked ? rootKey : -1));
            Integer id = sampleIdsByContent.get (contentKey);
            if (id == null)
            {
                id = Integer.valueOf (nextSampleId);
                nextSampleId++;
                sampleIdsByContent.put (contentKey, id);

                // The stereo suffix is at the fixed character positions 10/11
                final String sampleName;
                if (isStereo)
                    sampleName = StringUtils.rightPadSpaces (baseName, 10) + (char) 0x7F + (channel == 0 ? 'L' : 'R');
                else
                    sampleName = baseName;
                final MV8000Sample sample = new MV8000Sample (id.intValue (), sampleName);
                sample.setWaveData (waveData);
                sample.setStartPoint (startPoint);
                sample.setLoopStart (loopStart);
                sample.setEndPoint (endPoint);
                sample.setRootKey (rootKey);
                patch.getSamples ().add (sample);
            }

            final MV8000Smt slot = partial.getSmtSlot (slotCounts[partialIndex]);
            slot.setSampleId (id.intValue ());
            slot.setLevel (Math.clamp ((int) Math.round (Math.pow (10, zone.getGain () / 20.0) * 127.0), 0, 127));
            // The 2 mono halves of a stereo zone are hard-panned to re-create the stereo image
            if (isStereo)
                slot.setPanning (channel == 0 ? 32 : 96);
            else
                slot.setPanning (Math.clamp (64L + (int) Math.round (zone.getPanning () * 32.0), 32, 96));
            slot.setCoarseTune (coarseTune);
            slot.setFineTune (fineTune);
            slot.setVelocityLow (Math.clamp (zone.getVelocityLow (), 1, 127));
            slot.setVelocityFadeLow (Math.clamp (zone.getVelocityCrossfadeLow (), 0, 127));
            slot.setVelocityHigh (Math.clamp (zone.getVelocityHigh (), 1, 127));
            slot.setVelocityFadeHigh (Math.clamp (zone.getVelocityCrossfadeHigh (), 0, 127));
            slot.setPlayMode (isOneShot ? 1 : 0);
            slot.setKeyFollow (isKeyTracked ? Math.clamp (MV8000Smt.KEY_FOLLOW_OFF + Math.round (zone.getKeyTracking () * 8), 16, 48) : MV8000Smt.KEY_FOLLOW_OFF);

            slotCounts[partialIndex]++;
        }
        return nextSampleId;
    }


    /**
     * Write the amplitude envelope of the zone into the TVA envelope of the partial.
     *
     * @param partial The partial to update
     * @param zone The zone from which to get the envelope
     */
    private static void writeAmplitudeEnvelope (final MV8000Partial partial, final ISampleZone zone)
    {
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        if (envelope == null)
            return;

        partial.setTvaEnvelopeTime (0, calculateTimeValue (envelope.getAttackTime ()));
        final double holdTime = envelope.getHoldTime ();
        partial.setTvaEnvelopeTime (1, holdTime > 0 ? calculateTimeValue (holdTime) : 0);
        partial.setTvaEnvelopeTime (2, calculateTimeValue (envelope.getDecayTime ()));
        final double releaseTime = envelope.getReleaseTime ();
        if (releaseTime >= 0)
            partial.setTvaEnvelopeTime (3, calculateTimeValue (releaseTime));

        final double holdLevel = envelope.getHoldLevel ();
        final int level1 = holdLevel < 0 ? 127 : Math.clamp ((int) Math.round (holdLevel * 127), 0, 127);
        partial.setTvaEnvelopeLevel (0, level1);
        partial.setTvaEnvelopeLevel (1, level1);
        final double sustainLevel = envelope.getSustainLevel ();
        if (sustainLevel >= 0)
            partial.setTvaEnvelopeLevel (2, Math.clamp ((int) Math.round (sustainLevel * 127), 0, 127));
    }


    /**
     * Write the filter settings of the zone into the TVF section of the partial.
     *
     * @param partial The partial to update
     * @param zone The zone from which to get the filter
     */
    private static void writeFilter (final MV8000Partial partial, final ISampleZone zone)
    {
        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isEmpty ())
            return;
        final IFilter filter = optFilter.get ();

        final int filterType = switch (filter.getType ())
        {
            case LOW_PASS -> MV8000Partial.FILTER_LPF;
            case BAND_PASS -> MV8000Partial.FILTER_BPF;
            case HIGH_PASS -> MV8000Partial.FILTER_HPF;
            default -> MV8000Partial.FILTER_OFF;
        };
        if (filterType == MV8000Partial.FILTER_OFF)
            return;

        partial.setFilterType (filterType);
        partial.setFilterCutoff (Math.clamp ((int) Math.round (MathUtils.normalizeCutoff (filter.getCutoff ()) * 127), 0, 127));
        partial.setFilterResonance (Math.clamp ((int) Math.round (filter.getResonance () * 127), 0, 127));

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final IEnvelope envelope = cutoffModulator.getSource ();
        final double depth = cutoffModulator.getDepth ();
        if (envelope == null || depth == 0)
            return;

        partial.setFilterEnvelopeDepth (Math.clamp (64 + Math.round (depth * 63), 1, 127));
        partial.setTvfEnvelopeTime (0, calculateTimeValue (envelope.getAttackTime ()));
        partial.setTvfEnvelopeTime (1, 0);
        partial.setTvfEnvelopeTime (2, calculateTimeValue (envelope.getDecayTime ()));
        final double releaseTime = envelope.getReleaseTime ();
        if (releaseTime >= 0)
            partial.setTvfEnvelopeTime (3, calculateTimeValue (releaseTime));
        final double holdLevel = envelope.getHoldLevel ();
        final int level1 = holdLevel < 0 ? 127 : Math.clamp ((int) Math.round (holdLevel * 127), 0, 127);
        partial.setTvfEnvelopeLevel (0, level1);
        partial.setTvfEnvelopeLevel (1, level1);
        final double sustainLevel = envelope.getSustainLevel ();
        if (sustainLevel >= 0)
            partial.setTvfEnvelopeLevel (2, Math.clamp ((int) Math.round (sustainLevel * 127), 0, 127));
    }


    /**
     * Convert a time in seconds to an envelope time value. Inverse of the read formula (S-7xx
     * lineage approximation, up to 20 seconds).
     *
     * @param seconds The time in seconds
     * @return The time value in the range of 0..127
     */
    private static int calculateTimeValue (final double seconds)
    {
        if (seconds <= 0)
            return 0;
        return Math.clamp ((int) Math.round (127 + 21 * Math.log (seconds / 20.0) / Math.log (2)), 0, 127);
    }


    private static String createUniqueSampleName (final String zoneName, final Set<String> usedSampleNames, final int maxLength)
    {
        String name = zoneName.trim ();
        if (name.length () > maxLength)
            name = name.substring (0, maxLength);
        int counter = 1;
        while (!usedSampleNames.add (name))
        {
            counter++;
            final String suffix = Integer.toString (counter);
            final String base = name.substring (0, Math.min (name.length (), maxLength - suffix.length ()));
            name = base + suffix;
        }
        return name;
    }


    private static byte [] extractChannelBigEndian (final byte [] pcmData, final int numChannels, final int channel)
    {
        final int numFrames = pcmData.length / (2 * numChannels);
        final byte [] channelData = new byte [numFrames * 2];
        for (int i = 0; i < numFrames; i++)
        {
            final int src = (i * numChannels + channel) * 2;
            // Convert from little-endian to big-endian
            channelData[i * 2] = pcmData[src + 1];
            channelData[i * 2 + 1] = pcmData[src];
        }
        return channelData;
    }
}
