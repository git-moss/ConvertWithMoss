// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mv8000;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively Roland MV-8000/MV-8800 patch files in folders. Files must end with
 * <i>.mv0</i>.
 *
 * @author Jürgen Moßgraber
 */
public class MV8000Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final Map<Integer, String> CATEGORY_MAP = new HashMap<> ();
    static
    {
        CATEGORY_MAP.put (Integer.valueOf (1), TagDetector.CATEGORY_PIANO);
        CATEGORY_MAP.put (Integer.valueOf (2), TagDetector.CATEGORY_PIANO);
        CATEGORY_MAP.put (Integer.valueOf (3), TagDetector.CATEGORY_KEYBOARD);
        CATEGORY_MAP.put (Integer.valueOf (4), TagDetector.CATEGORY_BELL);
        CATEGORY_MAP.put (Integer.valueOf (5), TagDetector.CATEGORY_CHROMATIC_PERCUSSION);
        CATEGORY_MAP.put (Integer.valueOf (6), TagDetector.CATEGORY_ORGAN);
        CATEGORY_MAP.put (Integer.valueOf (7), TagDetector.CATEGORY_ORGAN);
        CATEGORY_MAP.put (Integer.valueOf (8), TagDetector.CATEGORY_WINDS);
        CATEGORY_MAP.put (Integer.valueOf (9), TagDetector.CATEGORY_GUITAR);
        CATEGORY_MAP.put (Integer.valueOf (10), TagDetector.CATEGORY_GUITAR);
        CATEGORY_MAP.put (Integer.valueOf (11), TagDetector.CATEGORY_GUITAR);
        CATEGORY_MAP.put (Integer.valueOf (12), TagDetector.CATEGORY_BASS);
        CATEGORY_MAP.put (Integer.valueOf (13), TagDetector.CATEGORY_BASS);
        CATEGORY_MAP.put (Integer.valueOf (14), TagDetector.CATEGORY_STRINGS);
        CATEGORY_MAP.put (Integer.valueOf (15), TagDetector.CATEGORY_ORCHESTRAL);
        CATEGORY_MAP.put (Integer.valueOf (16), TagDetector.CATEGORY_ORCHESTRAL);
        CATEGORY_MAP.put (Integer.valueOf (17), TagDetector.CATEGORY_WINDS);
        CATEGORY_MAP.put (Integer.valueOf (18), TagDetector.CATEGORY_PIPE);
        CATEGORY_MAP.put (Integer.valueOf (19), TagDetector.CATEGORY_BRASS);
        CATEGORY_MAP.put (Integer.valueOf (20), TagDetector.CATEGORY_BRASS);
        CATEGORY_MAP.put (Integer.valueOf (21), TagDetector.CATEGORY_WINDS);
        CATEGORY_MAP.put (Integer.valueOf (22), TagDetector.CATEGORY_LEAD);
        CATEGORY_MAP.put (Integer.valueOf (23), TagDetector.CATEGORY_LEAD);
        CATEGORY_MAP.put (Integer.valueOf (24), TagDetector.CATEGORY_SYNTH);
        CATEGORY_MAP.put (Integer.valueOf (25), TagDetector.CATEGORY_SYNTH);
        CATEGORY_MAP.put (Integer.valueOf (26), TagDetector.CATEGORY_FX);
        CATEGORY_MAP.put (Integer.valueOf (27), TagDetector.CATEGORY_SYNTH);
        CATEGORY_MAP.put (Integer.valueOf (28), TagDetector.CATEGORY_PAD);
        CATEGORY_MAP.put (Integer.valueOf (29), TagDetector.CATEGORY_PAD);
        CATEGORY_MAP.put (Integer.valueOf (30), TagDetector.CATEGORY_VOCAL);
        CATEGORY_MAP.put (Integer.valueOf (31), TagDetector.CATEGORY_PLUCK);
        CATEGORY_MAP.put (Integer.valueOf (32), TagDetector.CATEGORY_WORLD);
        CATEGORY_MAP.put (Integer.valueOf (33), TagDetector.CATEGORY_GUITAR);
        CATEGORY_MAP.put (Integer.valueOf (34), TagDetector.CATEGORY_PERCUSSION);
        CATEGORY_MAP.put (Integer.valueOf (35), TagDetector.CATEGORY_FX);
        CATEGORY_MAP.put (Integer.valueOf (36), TagDetector.CATEGORY_LOOPS);
        CATEGORY_MAP.put (Integer.valueOf (37), TagDetector.CATEGORY_DRUM);
        CATEGORY_MAP.put (Integer.valueOf (38), TagDetector.CATEGORY_SYNTH);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MV8000Detector (final INotifier notifier)
    {
        super ("Roland MV-8000", "MV8000", notifier, new MetadataSettingsUI ("MV8000"), ".mv0");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final MV8000Patch patch;
        try (final InputStream input = new BufferedInputStream (new FileInputStream (sourceFile)))
        {
            patch = new MV8000Patch (input);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }

        String name = patch.getName ();
        if (name.isBlank ())
            name = FileUtils.getNameWithoutType (sourceFile);
        this.notifier.log ("IDS_MV8000_READING_PATCH", name);

        final Map<Integer, MV8000Sample> samplesByID = new HashMap<> ();
        for (final MV8000Sample sample: patch.getSamples ())
            samplesByID.put (Integer.valueOf (sample.getId ()), sample);

        final List<IGroup> groups = new ArrayList<> ();
        for (int i = 0; i < MV8000Partial.NUM_SMT_SLOTS; i++)
            groups.add (new DefaultGroup ("Layer " + (i + 1)));

        // Create the zones from the contiguous runs of the note table
        final int [] noteTable = patch.getNoteTable ();
        int runStart = 0;
        for (int i = 1; i <= noteTable.length; i++)
            if (i == noteTable.length || noteTable[i] != noteTable[runStart])
            {
                if (noteTable[runStart] >= 0)
                    this.createZones (patch, samplesByID, groups, noteTable[runStart], MV8000Patch.NOTE_BASE + runStart, MV8000Patch.NOTE_BASE + i - 1);
                runStart = i;
            }

        final List<IGroup> filledGroups = new ArrayList<> ();
        for (final IGroup group: groups)
            if (!group.getSampleZones ().isEmpty ())
                filledGroups.add (group);

        final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, name, filledGroups);
        final String category = CATEGORY_MAP.get (Integer.valueOf (patch.getCategory ()));
        if (category != null)
            multisampleSource.getMetadata ().setCategory (category);
        return Collections.singletonList (multisampleSource);
    }


    private void createZones (final MV8000Patch patch, final Map<Integer, MV8000Sample> samplesByID, final List<IGroup> groups, final int partialIndex, final int keyLow, final int keyHigh)
    {
        final MV8000Partial partial = patch.getPartial (partialIndex);
        final IEnvelope amplitudeEnvelope = createAmplitudeEnvelope (partial);

        int slotIndex = 0;
        while (slotIndex < MV8000Partial.NUM_SMT_SLOTS)
        {
            final MV8000Smt slot = partial.getSmtSlot (slotIndex);
            final int sampleId = slot.getSampleId ();
            if (sampleId != 0)
            {
                final MV8000Sample sample = samplesByID.get (Integer.valueOf (sampleId));
                if (sample == null)
                    this.notifier.logError ("IDS_MV8000_SAMPLE_MISSING", Integer.toString (sampleId), partial.getName ());
                else
                {
                    // Combine a left/right mono pair in 2 consecutive slots into 1 stereo zone
                    MV8000Sample rightSample = null;
                    if (slotIndex + 1 < MV8000Partial.NUM_SMT_SLOTS && sample.isStereoLeft ())
                    {
                        final MV8000Smt nextSlot = partial.getSmtSlot (slotIndex + 1);
                        final MV8000Sample nextSample = samplesByID.get (Integer.valueOf (nextSlot.getSampleId ()));
                        if (nextSample != null && nextSample.isStereoRightOf (sample) && nextSlot.getVelocityLow () == slot.getVelocityLow () && nextSlot.getVelocityHigh () == slot.getVelocityHigh ())
                            rightSample = nextSample;
                    }

                    final ISampleZone zone = createZone (slot, sample, rightSample, keyLow, keyHigh);
                    zone.getAmplitudeEnvelopeModulator ().setSource (amplitudeEnvelope);
                    zone.getAmplitudeVelocityModulator ().setDepth (partial.getTvaVelocityCurve () == 0 ? 0 : 1);
                    createFilter (zone, partial);
                    groups.get (slotIndex).addSampleZone (zone);

                    slotIndex += rightSample == null ? 1 : 2;
                }
            }
        }
    }


    private static ISampleZone createZone (final MV8000Smt slot, final MV8000Sample sample, final MV8000Sample rightSample, final int keyLow, final int keyHigh)
    {
        final ISampleZone zone = new DefaultSampleZone (sample.getCleanName (), keyLow, keyHigh);
        zone.setSampleData (createSampleData (sample, rightSample));

        zone.setVelocityLow (slot.getVelocityLow ());
        zone.setVelocityCrossfadeLow (slot.getVelocityFadeLow ());
        zone.setVelocityHigh (slot.getVelocityHigh ());
        zone.setVelocityCrossfadeHigh (slot.getVelocityFadeHigh ());

        zone.setStart (sample.getStartPoint ());
        zone.setStop (sample.getEndPoint ());
        zone.setGain (MathUtils.valueToDb (slot.getLevel () / 127.0));
        // The slots of a combined stereo pair are hard-panned (left 32, right 96) to create the
        // stereo image which is already baked into the combined zone
        if (rightSample == null)
            zone.setPanning (Math.clamp ((slot.getPanning () - 64) / 32.0, -1, 1));
        zone.setTuning (slot.getCoarseTune () - 64 + (slot.getFineTune () - 64) / 100.0);

        // 32 = 0% (fixed pitch), 40 = +100% (normal chromatic tracking), 12.5% steps
        final int keyFollow = slot.getKeyFollow ();
        if (keyFollow == MV8000Smt.KEY_FOLLOW_OFF)
        {
            zone.setKeyTracking (0);
            zone.setKeyRoot (keyLow);
        }
        else
        {
            zone.setKeyRoot (sample.getRootKey ());
            if (keyFollow != MV8000Smt.KEY_FOLLOW_NORMAL)
                zone.setKeyTracking (Math.clamp ((keyFollow - MV8000Smt.KEY_FOLLOW_OFF) * 0.125, 0, 1));
        }

        // One-shot modes (uneven) ignore the loop and play the sample until its end
        final int playMode = slot.getPlayMode ();
        if (playMode % 2 == 0)
        {
            final ISampleLoop sampleLoop = new DefaultSampleLoop ();
            final LoopType type = switch (playMode)
            {
                case 2 -> LoopType.ALTERNATING;
                case 4 -> LoopType.BACKWARDS;
                default -> LoopType.FORWARDS;
            };
            sampleLoop.setType (type);
            sampleLoop.setStart (sample.getLoopStart ());
            sampleLoop.setEnd (sample.getEndPoint ());
            if (sampleLoop.getEnd () > sampleLoop.getStart ())
                zone.getLoops ().add (sampleLoop);
        }

        return zone;
    }


    /**
     * Create the amplitude envelope from the TVA envelope of the partial. The TVA envelope raises
     * to level 1 in time 1, continues to level 2 in time 2, to level 3 (= the sustain level) in
     * time 3 and decays in time 4 after note-off.
     *
     * @param partial The partial
     * @return The amplitude envelope
     */
    private static IEnvelope createAmplitudeEnvelope (final MV8000Partial partial)
    {
        final int level1 = partial.getTvaEnvelopeLevel (0);
        final int level2 = partial.getTvaEnvelopeLevel (1);
        final int level3 = partial.getTvaEnvelopeLevel (2);

        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (calculateTime (partial.getTvaEnvelopeTime (0)));
        envelope.setHoldLevel (level1 / 127.0);
        if (level1 == level2)
        {
            envelope.setHoldTime (calculateTime (partial.getTvaEnvelopeTime (1)));
            envelope.setDecayTime (calculateTime (partial.getTvaEnvelopeTime (2)));
        }
        else
            envelope.setDecayTime (calculateTime (partial.getTvaEnvelopeTime (1)) + calculateTime (partial.getTvaEnvelopeTime (2)));
        envelope.setSustainLevel (level3 / 127.0);
        envelope.setReleaseTime (calculateTime (partial.getTvaEnvelopeTime (3)));
        return envelope;
    }


    /**
     * Create the filter from the TVF settings of the partial, if it is enabled.
     *
     * @param zone The zone to which to add the filter
     * @param partial The partial
     */
    private static void createFilter (final ISampleZone zone, final MV8000Partial partial)
    {
        final FilterType filterType = switch (partial.getFilterType ())
        {
            case MV8000Partial.FILTER_LPF -> FilterType.LOW_PASS;
            case MV8000Partial.FILTER_BPF -> FilterType.BAND_PASS;
            case MV8000Partial.FILTER_HPF -> FilterType.HIGH_PASS;
            default -> null;
        };
        if (filterType == null)
            return;

        final double cutoff = MathUtils.denormalizeCutoff (partial.getFilterCutoff () / 127.0);
        final IFilter filter = new DefaultFilter (filterType, 4, cutoff, partial.getFilterResonance () / 127.0);

        final int envelopeDepth = partial.getFilterEnvelopeDepth ();
        if (envelopeDepth != 64)
        {
            final IEnvelope envelope = new DefaultEnvelope ();
            envelope.setAttackTime (calculateTime (partial.getTvfEnvelopeTime (0)));
            envelope.setHoldLevel (partial.getTvfEnvelopeLevel (0) / 127.0);
            envelope.setDecayTime (calculateTime (partial.getTvfEnvelopeTime (1)) + calculateTime (partial.getTvfEnvelopeTime (2)));
            envelope.setSustainLevel (partial.getTvfEnvelopeLevel (2) / 127.0);
            envelope.setReleaseTime (calculateTime (partial.getTvfEnvelopeTime (3)));

            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            cutoffModulator.setSource (envelope);
            cutoffModulator.setDepth ((envelopeDepth - 64) / 63.0);
        }

        zone.setFilter (filter);
    }


    /**
     * Convert an envelope time value to seconds. The exact hardware curve is unknown, the formula
     * of the S-7xx series (same lineage, up to 20 seconds) is used as an approximation.
     *
     * @param value The time value in the range of 0..127
     * @return The time in seconds
     */
    private static double calculateTime (final int value)
    {
        return value == 0 ? 0 : 20.0 * Math.pow (2.0, (value - 127.0) / 21.0);
    }


    private static InMemorySampleData createSampleData (final MV8000Sample leftSample, final MV8000Sample rightSample)
    {
        final byte [] leftData = leftSample.getWaveData ();
        final int numFrames = leftSample.getFrameCount ();

        if (rightSample == null)
        {
            final byte [] pcmData = new byte [numFrames * 2];
            for (int i = 0; i < numFrames; i++)
            {
                pcmData[i * 2] = leftData[i * 2 + 1];
                pcmData[i * 2 + 1] = leftData[i * 2];
            }
            return new InMemorySampleData (new DefaultAudioMetadata (1, MV8000Sample.SAMPLE_RATE, 16, numFrames), pcmData);
        }

        final byte [] rightData = rightSample.getWaveData ();
        final int numStereoFrames = Math.min (numFrames, rightSample.getFrameCount ());
        final byte [] pcmData = new byte [numStereoFrames * 4];
        for (int i = 0; i < numStereoFrames; i++)
        {
            pcmData[i * 4] = leftData[i * 2 + 1];
            pcmData[i * 4 + 1] = leftData[i * 2];
            pcmData[i * 4 + 2] = rightData[i * 2 + 1];
            pcmData[i * 4 + 3] = rightData[i * 2];
        }
        return new InMemorySampleData (new DefaultAudioMetadata (2, MV8000Sample.SAMPLE_RATE, 16, numStereoFrames), pcmData);
    }
}
