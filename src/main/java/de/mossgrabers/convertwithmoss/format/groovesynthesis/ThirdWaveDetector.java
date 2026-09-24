// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.groovesynthesis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects the sample slots of the Groove Synthesis 3rd Wave: multi-sample slot files (*.bin) and
 * unified program files (*.pgdata). A slot of a multi-sample file becomes a multi-sample with one
 * group. A program becomes a multi-sample with a group for each oscillator which plays a sample
 * slot, with the level, tuning, panning and keyboard range of its part; the slots of which the
 * program does not play all samples become multi-samples of their own as well. Envelopes, filters,
 * modulation and effects of a program are not converted.
 *
 * @author Jürgen Moßgraber
 */
public class ThirdWaveDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String [] BIT_DEPTHS =
    {
        "8",
        "12",
        "16"
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ThirdWaveDetector (final INotifier notifier)
    {
        super ("Groove Synthesis 3rd Wave", "ThirdWave", notifier, new MetadataSettingsUI ("ThirdWave"), ".bin", ".pgdata");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final ThirdWaveFile.Content content;
        try
        {
            content = ThirdWaveFile.read (file);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
        // Not a file of the 3rd Wave, other devices use the same ending
        if (content == null)
            return Collections.emptyList ();

        final String fileName = FileUtils.getNameWithoutType (file);
        final List<ThirdWaveFile.Slot> slots = new ArrayList<> ();
        for (final ThirdWaveFile.Slot slot: content.slots ())
            if (!slot.samples ().isEmpty ())
                slots.add (slot);
        final Map<ThirdWaveFile.Sample, ISampleData> sampleData = new IdentityHashMap<> ();

        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        if (!content.isProgram ())
        {
            for (final ThirdWaveFile.Slot slot: slots)
            {
                final String slotName = getSlotName (slot, fileName);
                multisampleSources.add (this.createMultisampleSource (file, slotName, this.createSlotGroups (slot, slotName, sampleData)));
            }
            return multisampleSources;
        }

        final String programName = content.name ().isBlank () ? fileName : content.name ();
        final ThirdWaveProgram program = content.program ();
        final Set<Integer> playedSlots = new HashSet<> ();
        if (program == null)
            this.notifier.log ("IDS_THIRD_WAVE_ONLY_SAMPLE_SLOTS", programName);
        else
        {
            final List<IGroup> groups = this.createProgramGroups (content, program, programName, playedSlots, sampleData);
            if (!groups.isEmpty ())
            {
                this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_READ", programName);
                multisampleSources.add (this.createMultisampleSource (file, programName, groups));
            }
        }

        final List<ThirdWaveFile.Slot> unplayedSlots = new ArrayList<> ();
        for (final ThirdWaveFile.Slot slot: slots)
            if (!playedSlots.contains (Integer.valueOf (slot.resource ())))
                unplayedSlots.add (slot);
        if (!multisampleSources.isEmpty () && !unplayedSlots.isEmpty ())
            this.notifier.log ("IDS_THIRD_WAVE_UNPLAYED_SLOTS", programName, Integer.toString (unplayedSlots.size ()));
        for (final ThirdWaveFile.Slot slot: unplayedSlots)
        {
            final String slotName = getSlotName (slot, fileName);
            // A program which plays none of its slots and has only one is named like the program
            final String name = multisampleSources.isEmpty () && slots.size () == 1 ? programName : programName + " - " + slotName;
            multisampleSources.add (this.createMultisampleSource (file, name, this.createSlotGroups (slot, slotName, sampleData)));
        }

        if (slots.isEmpty ())
            this.notifier.log ("IDS_THIRD_WAVE_NO_SAMPLE_SLOTS", programName);
        return multisampleSources;
    }


    /**
     * Create the groups of a program: one for each oscillator of a part which plays a sample slot.
     * The transposition of the keyboard of a part shifts the notes which its keys play; the part
     * plays these notes if they lie in one of the keyboard sections to which it is assigned.
     *
     * @param content The content of the file
     * @param program The program
     * @param programName The name of the program
     * @param playedSlots Where to add the IDs of the slots of which the program plays all samples
     * @param sampleData The sample data of the samples which were already created
     * @return The groups, empty if the program plays no sample slot
     */
    private List<IGroup> createProgramGroups (final ThirdWaveFile.Content content, final ThirdWaveProgram program, final String programName, final Set<Integer> playedSlots, final Map<ThirdWaveFile.Sample, ISampleData> sampleData)
    {
        final int [] [] sections = program.getKeyboardSections ();
        final List<IGroup> groups = new ArrayList<> ();
        final List<String> slotNames = new ArrayList<> ();
        int synthesisOscillators = 0;
        for (int part = 0; part < ThirdWaveProgram.NUM_PARTS; part++)
        {
            final List<int []> noteRanges = new ArrayList<> ();
            for (int section = 0; section < sections.length; section++)
                if ((program.getSectionParts (section) & 1 << part) != 0)
                    noteRanges.add (sections[section]);
            final float partVolume = program.getPartVolume (part);
            if (noteRanges.isEmpty () || partVolume <= 0)
                continue;

            for (int oscillator = 0; oscillator < ThirdWaveProgram.NUM_OSCILLATORS; oscillator++)
            {
                final float level = program.getOscillatorLevel (part, oscillator);
                if (level <= 0)
                    continue;
                final int resource = program.getOscillatorResource (part, oscillator);
                if (!content.isSampleSlot (resource))
                {
                    synthesisOscillators++;
                    continue;
                }
                final ThirdWaveFile.Slot slot = content.getSlot (resource);
                if (slot == null || slot.samples ().isEmpty ())
                    continue;

                final double gain = MathUtils.valueToDb (level * partVolume);
                final double panning = program.getPartPanning (part);
                final double tuning = program.getOscillatorTuning (part, oscillator) + program.getPartFineTuning (part);
                final int transpose = program.getPartTranspose (part);
                final boolean isPitchTracked = program.isOscillatorPitchTracked (part, oscillator);

                final String slotName = getSlotName (slot, programName);
                final DefaultGroup group = new DefaultGroup (String.format (Locale.US, "Part %d Osc %d - %s", Integer.valueOf (part + 1), Integer.valueOf (oscillator + 1), slotName));
                // The group values are only carriers, they are part of the zones already
                group.setGain (gain);
                group.setPanning (panning);
                group.setTuning (tuning);
                final List<ThirdWaveFile.Sample> samples = slot.samples ();
                int playedSamples = 0;
                for (int i = 0; i < samples.size (); i++)
                {
                    final ThirdWaveFile.Sample sample = samples.get (i);
                    boolean isPlayed = false;
                    for (final int [] noteRange: noteRanges)
                    {
                        // The keys play the notes of the transposed keyboard
                        final int low = Math.max (Math.max (sample.low (), noteRange[0]) - transpose, 0);
                        final int high = Math.min (Math.min (sample.high (), noteRange[1]) - transpose, 127);
                        if (low > high)
                            continue;
                        final ISampleZone zone = this.createSampleZone (sample, slotName + " " + (i + 1), sampleData);
                        final int root = sample.root () - transpose;
                        final int clampedRoot = Math.clamp (root, 0, 127);
                        zone.setKeyRoot (clampedRoot);
                        zone.setKeyLow (low);
                        zone.setKeyHigh (high);
                        zone.setTuning (zone.getTuning () + tuning + clampedRoot - root);
                        zone.setGain (zone.getGain () + gain);
                        zone.setPanning (panning);
                        if (!isPitchTracked)
                            zone.setKeyTracking (0);
                        group.addSampleZone (zone);
                        isPlayed = true;
                    }
                    if (isPlayed)
                        playedSamples++;
                }
                // A slot of which the program does not play all samples is read on its own as well
                if (playedSamples == samples.size ())
                    playedSlots.add (Integer.valueOf (resource));
                if (!group.getSampleZones ().isEmpty ())
                {
                    groups.add (group);
                    slotNames.add (slotName);
                }
            }
        }

        if (synthesisOscillators > 0)
            this.notifier.log ("IDS_THIRD_WAVE_SYNTHESIS_OSCILLATORS", programName, Integer.toString (synthesisOscillators));

        // Only name the groups by their part if there are several
        if (groups.size () == 1)
            groups.get (0).setName (slotNames.get (0));
        return groups;
    }


    /**
     * Create the group of a sample slot as it is stored in the slot.
     *
     * @param slot The slot
     * @param slotName The name of the slot
     * @param sampleData The sample data of the samples which were already created
     * @return The group in a list
     */
    private List<IGroup> createSlotGroups (final ThirdWaveFile.Slot slot, final String slotName, final Map<ThirdWaveFile.Sample, ISampleData> sampleData)
    {
        final List<ThirdWaveFile.Sample> samples = slot.samples ();
        final DefaultGroup group = new DefaultGroup (slotName);
        for (int i = 0; i < samples.size (); i++)
            group.addSampleZone (this.createSampleZone (samples.get (i), slotName + " " + (i + 1), sampleData));
        final List<IGroup> groups = new ArrayList<> ();
        groups.add (group);
        return groups;
    }


    /**
     * Get the name of a slot. If it has none it is named like the file or its slot on the device.
     *
     * @param slot The slot
     * @param fallbackName The name to use if the slot has none and its index is unknown
     * @return The name
     */
    private static String getSlotName (final ThirdWaveFile.Slot slot, final String fallbackName)
    {
        if (!slot.name ().isBlank ())
            return slot.name ();
        if (slot.index () < 0)
            return fallbackName;
        return String.format (Locale.US, "S%02d", Integer.valueOf (slot.index ()));
    }


    /**
     * Create a sample zone from a sample of a slot.
     *
     * @param sample The sample
     * @param fallbackName The name to use if the sample has none
     * @param sampleData The sample data of the samples which were already created, zones of the
     *            same sample share it
     * @return The sample zone
     */
    private ISampleZone createSampleZone (final ThirdWaveFile.Sample sample, final String fallbackName, final Map<ThirdWaveFile.Sample, ISampleData> sampleData)
    {
        final String name = sample.name ().isBlank () ? fallbackName : sample.name ();

        final int sampleRate = Math.round (sample.sampleRate ());
        ISampleData data = sampleData.get (sample);
        if (data == null)
        {
            final int secondSampleRate = Math.round (sample.secondSampleRate ());
            if (sampleRate != secondSampleRate)
                this.notifier.log ("IDS_THIRD_WAVE_TWO_SAMPLE_RATES", name, Integer.toString (sampleRate), Integer.toString (secondSampleRate));
            if (sample.bitDepth () != ThirdWaveFile.BIT_DEPTH_16)
                this.notifier.log ("IDS_THIRD_WAVE_BIT_DEPTH", name, BIT_DEPTHS[sample.bitDepth ()]);
            data = new InMemorySampleData (new DefaultAudioMetadata (1, sampleRate, 16, sample.getFrames ()), sample.audio ());
            sampleData.put (sample, data);
        }

        final DefaultSampleZone zone = new DefaultSampleZone (name, data);
        zone.setKeyRoot (sample.root ());
        zone.setKeyLow (sample.low ());
        zone.setKeyHigh (sample.high ());
        zone.setStart (sample.start ());
        zone.setStop (sample.end ());
        zone.setTuning (sample.tune ());
        zone.setGain (MathUtils.valueToDb (sample.volume ()));

        // A sample without a loop still stops at the release of the note, it is not a one-shot
        if (sample.loopMode () != ThirdWaveFile.LOOP_OFF && sample.loopStart () < sample.loopEnd ())
        {
            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (sample.loopStart ());
            loop.setEnd (sample.loopEnd ());
            // 'Looping on when note on' plays the rest of the sample after the release, 'looping
            // always on' continues looping
            loop.setLoopUntilRelease (sample.loopMode () == ThirdWaveFile.LOOP_WHILE_HELD);
            if (sample.crossfadeType () != ThirdWaveFile.CROSSFADE_OFF)
                loop.setCrossfadeInSamples (sample.crossfade ());
            zone.addLoop (loop);
        }
        return zone;
    }
}
