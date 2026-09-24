// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.groovesynthesis;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Creates files for the Groove Synthesis 3rd Wave: either a unified program file (*.pgdata), a
 * program together with its samples, which is loaded with 'Import unified program data file' from
 * the 'Programs' folder, or multi-sample slot files (*.bin), which are loaded with 'Import
 * multisample' or 'Bulk multisample import' from the 'Audio' folder. A sample slot holds up to 8
 * mono samples which are not layered. The program is the init program of the instrument whose
 * parts play the sample slots, split across the keyboard and panned like the samples, e.g. the two
 * channels of stereo samples; the other settings of the source (envelopes, filters, ...) are not
 * converted.
 *
 * @author Jürgen Moßgraber
 */
public class ThirdWaveCreator extends AbstractCreator<ThirdWaveCreatorUI>
{
    private static final int DEFAULT_ROOT_KEY = 60;


    /**
     * A sample of a slot together with the properties of its zone which a program needs.
     *
     * @param sample The sample, for a program without the tuning of the zone
     * @param tuning The tuning of the zone in semi-tones
     * @param panning The panning of the zone
     * @param velocityHigh The highest velocity which plays the zone
     * @param isAlternative True if the zone is not the first of a round robin or random selection
     */
    private record ProgramSample (ThirdWaveFile.Sample sample, double tuning, double panning, int velocityHigh, boolean isAlternative)
    {
        // Intentionally empty
    }


    /** Counts the samples of a preset which could not be converted exactly. */
    private static class Notes
    {
        int mixedToMono;
        int limitedGain;
        int changedLoops;
        int droppedLoops;
        int limitedKeyRanges;
        int fixedPitch;
        int releaseTriggers;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ThirdWaveCreator (final INotifier notifier)
    {
        super ("Groove Synthesis 3rd Wave", "ThirdWave", notifier, new ThirdWaveCreatorUI ("ThirdWave"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String name = multisampleSource.getName ();
        final boolean writeProgram = this.settingsConfiguration.isWriteProgram ();

        // The 3rd Wave plays 10 to 48kHz, the audio of a looped sample outside of this range needs
        // to be converted together with its positions
        this.recalculateSamplePositions (multisampleSource, ThirdWaveFile.MIN_SAMPLE_RATE, ThirdWaveFile.MAX_SAMPLE_RATE);

        final Notes notes = new Notes ();
        final List<ProgramSample> samples = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (this.isCancelled ())
                    return;
                if (zone.getTrigger () == TriggerType.RELEASE || group.getTrigger () == TriggerType.RELEASE)
                    notes.releaseTriggers++;
                else
                {
                    final Optional<ThirdWaveFile.Sample> sample = this.createSample (zone, notes, writeProgram);
                    if (sample.isPresent ())
                    {
                        final boolean isAlternative = zone.getPlayLogic () != PlayLogic.ALWAYS && zone.getSequencePosition () > 1;
                        samples.add (new ProgramSample (sample.get (), zone.getTuning (), zone.getPanning (), limitToDefault (zone.getVelocityHigh (), 127), isAlternative));
                    }
                }
                this.progress.notifyProgress ();
            }

        if (samples.isEmpty ())
        {
            this.logNotes (name, notes);
            this.notifier.logError ("IDS_THIRD_WAVE_NO_SAMPLES", name);
            return;
        }

        if (writeProgram)
            this.writeProgram (destinationFolder, name, samples, notes);
        else
        {
            final List<ThirdWaveFile.Sample> slotSamples = new ArrayList<> ();
            for (final ProgramSample sample: samples)
                slotSamples.add (sample.sample ());
            this.writeSlots (destinationFolder, name, createLayers (slotSamples));
        }
        this.logNotes (name, notes);

        this.progress.notifyDone ();
    }


    /**
     * Write a unified program file. A program cannot switch between velocity layers or round
     * robins, it plays only the loudest velocity layer and the first round. Samples which play at
     * the same time, e.g. the two channels of a stereo recording or detuned copies, are played by
     * parts of their own, panned like their samples. The sample slots of such a layer split the
     * keyboard.
     *
     * @param destinationFolder Where to write the file
     * @param name The name of the preset
     * @param samples The samples
     * @param notes Where to count the differences
     * @throws IOException Could not write the file
     */
    private void writeProgram (final File destinationFolder, final String name, final List<ProgramSample> samples, final Notes notes) throws IOException
    {
        int maxVelocity = 0;
        for (final ProgramSample sample: samples)
            if (!sample.isAlternative ())
                maxVelocity = Math.max (maxVelocity, sample.velocityHigh ());
        final List<ProgramSample> loudestSamples = new ArrayList<> ();
        for (final ProgramSample sample: samples)
            if (!sample.isAlternative () && sample.velocityHigh () == maxVelocity)
                loudestSamples.add (sample);
        if (loudestSamples.size () < samples.size ())
            this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_LAYERS", name, Integer.toString (samples.size () - loudestSamples.size ()));
        final List<ProgramSample> playedSamples = combineIdenticalSamples (loudestSamples);

        final Map<ThirdWaveFile.Sample, Double> tunings = new IdentityHashMap<> ();
        for (final ProgramSample sample: playedSamples)
            tunings.put (sample.sample (), Double.valueOf (sample.tuning ()));

        // Samples which are tuned differently than the others of their slot need parts of their
        // own, e.g. a sound which is tuned an octave down, if their tuning cannot be moved to their
        // assigned notes; every part divides the voices of the instrument
        List<Voice> voices = createVoices (playedSamples, true, false);
        if (!isTunable (voices, tunings))
        {
            final List<Voice> tunedVoices = createVoices (playedSamples, true, true);
            if (countParts (tunedVoices) <= ThirdWaveProgram.NUM_PARTS)
                voices = tunedVoices;
        }
        if (countParts (voices) > ThirdWaveProgram.NUM_PARTS && voices.size () > 1)
        {
            final List<Voice> centeredVoices = createVoices (playedSamples, false, false);
            if (centeredVoices.size () < voices.size ())
            {
                this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_PANNING", name);
                voices = centeredVoices;
            }
        }
        final int numParts = countParts (voices);
        if (numParts > ThirdWaveProgram.NUM_PARTS)
        {
            this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_PARTS", name, Integer.toString (numParts), Integer.toString (numParts - ThirdWaveProgram.NUM_PARTS));
            voices = limitParts (voices);
        }
        if (voices.size () > 1)
            this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_VOICES", name, Integer.toString (voices.size ()));

        // The tuning and the loudness which the samples of a slot share are set on the part which
        // plays it, e.g. a part which is tuned an octave down; its samples keep the rest
        final Map<List<ThirdWaveFile.Sample>, double []> partSettings = new IdentityHashMap<> ();
        for (final Voice voice: voices)
            for (final List<ThirdWaveFile.Sample> slot: voice.slots ())
            {
                final double [] settings = moveToPart (slot, tunings);
                partSettings.put (slot, settings);
                notes.limitedKeyRanges += (int) settings[3];
            }

        // The keyboard is split between the slots of the voice with the most slots; the voices whose
        // slots fit into these sections share them, the others play in the sections which their
        // slots reach
        int mainVoice = 0;
        for (int i = 1; i < voices.size (); i++)
            if (voices.get (i).slots ().size () > voices.get (mainVoice).slots ().size ())
                mainVoice = i;
        final int numSections = voices.get (mainVoice).slots ().size ();
        final int [] splitNotes = splitKeyboard (voices, numSections, voices.get (mainVoice).slots ());

        // Parts whose slots have the same samples play the same slot, e.g. a slot which is panned to
        // both sides
        final List<ThirdWaveFile.Slot> slots = new ArrayList<> ();
        final List<int []> parts = new ArrayList<> ();
        for (int voice = 0; voice < voices.size (); voice++)
        {
            final List<List<ThirdWaveFile.Sample>> voiceSlots = voices.get (voice).slots ();
            for (int slot = 0; slot < voiceSlots.size (); slot++)
            {
                final List<ThirdWaveFile.Sample> slotSamples = voiceSlots.get (slot);
                int resource = -1;
                for (int i = 0; i < slots.size (); i++)
                    if (isSameSlot (slots.get (i).samples (), slotSamples))
                        resource = i;
                if (resource < 0)
                {
                    resource = slots.size ();
                    final String suffix = createSuffix (slot, voiceSlots.size (), voice, voices);
                    slots.add (new ThirdWaveFile.Slot (createSlotName (name, suffix), -1, resource, slotSamples));
                }
                parts.add (new int []
                {
                    voice,
                    slot,
                    resource
                });
            }
        }

        // All samples of a program need to fit into the memory at the same time
        long frames = 0;
        for (final ThirdWaveFile.Slot slot: slots)
            frames += slot.getFrames ();
        if (frames > ThirdWaveFile.MEMORY_FRAMES)
        {
            this.notifier.logError ("IDS_THIRD_WAVE_PROGRAM_MEMORY", name);
            return;
        }
        if (splitNotes.length > 0)
            this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_SPLITS", name, Integer.toString (numSections));

        // Part N plays one slot with its first oscillator; its other oscillators and the parts
        // which are not used play the analog waveform, whose ID follows the ones of the slots
        final ThirdWaveProgram program = ThirdWaveProgram.createInitProgram ();
        final int analogWaveform = slots.size ();
        final int [] sectionParts = new int [numSections];
        for (int part = 0; part < ThirdWaveProgram.NUM_PARTS; part++)
        {
            if (part >= parts.size ())
            {
                for (int oscillator = 0; oscillator < ThirdWaveProgram.NUM_OSCILLATORS; oscillator++)
                    program.setOscillatorResource (part, oscillator, analogWaveform);
                continue;
            }

            final int [] partSlot = parts.get (part);
            final Voice voice = voices.get (partSlot[0]);
            final List<ThirdWaveFile.Sample> slotSamples = voice.slots ().get (partSlot[1]);
            if (voice.slots ().size () == numSections && fitsSections (voice.slots (), splitNotes))
                sectionParts[partSlot[1]] |= 1 << part;
            else
                for (int section = 0; section < numSections; section++)
                    if (isInSection (slotSamples, section, splitNotes))
                        sectionParts[section] |= 1 << part;

            final double [] settings = partSettings.get (slotSamples);
            program.setOscillator (part, 0, partSlot[2], (float) settings[1]);
            program.setOscillatorTuning (part, 0, settings[0]);
            for (int oscillator = 1; oscillator < ThirdWaveProgram.NUM_OSCILLATORS; oscillator++)
                program.setOscillator (part, oscillator, analogWaveform, 0);
            program.setPartVolume (part, (float) settings[2]);
            program.setPartPanning (part, voice.panning ());
        }
        program.setKeyboardSplits (splitNotes, sectionParts);

        final File file = this.createUniqueFilename (destinationFolder, SafeFileNames.create (name), "pgdata");
        this.notifier.log ("IDS_NOTIFY_STORING", file.getAbsolutePath ());
        try (final OutputStream out = new BufferedOutputStream (new FileOutputStream (file)))
        {
            ThirdWaveFile.writeProgram (out, name, slots, program);
        }
    }


    /**
     * A voice of a program: samples which are not layered, split into sample slots along the
     * keyboard, and the panning of their parts.
     *
     * @param panning The panning of the parts, -1 (left) to 1 (right)
     * @param slots The samples of the slots
     */
    private record Voice (double panning, List<List<ThirdWaveFile.Sample>> slots)
    {
        // Intentionally empty
    }


    /**
     * Combine samples which play the same audio in the same way on the same keys, e.g. a layer which
     * is doubled: they sound like one sample with the sum of their volumes.
     *
     * @param samples The samples
     * @return The samples, of which each plays in a different way
     */
    private static List<ProgramSample> combineIdenticalSamples (final List<ProgramSample> samples)
    {
        final List<ProgramSample> combined = new ArrayList<> ();
        for (final ProgramSample sample: samples)
        {
            int index = -1;
            for (int i = 0; i < combined.size () && index < 0; i++)
            {
                final ProgramSample other = combined.get (i);
                if (other.tuning () == sample.tuning () && other.panning () == sample.panning () && isSameSample (other.sample (), sample.sample (), false))
                    index = i;
            }
            if (index < 0)
                combined.add (sample);
            else
            {
                final ProgramSample other = combined.get (index);
                final ThirdWaveFile.Sample otherSample = other.sample ();
                combined.set (index, new ProgramSample (otherSample.withVolume (otherSample.volume () + sample.sample ().volume ()), other.tuning (), other.panning (), other.velocityHigh (), other.isAlternative ()));
            }
        }
        return combined;
    }


    /**
     * Select the slots which are written if the voices need more than the parts of a program. The
     * voices get parts layer by layer: the first voice of each panning, then the second one of each
     * and so on. In a layer, voices which are panned to both sides by the same amount, e.g. the two
     * channels of a stereo sample, come first and get their slots together from the lowest one on,
     * so that both sides keep the same keys; then the other voices get theirs.
     *
     * @param voices The voices, ordered by their panning
     * @return The voices with the slots which are written
     */
    private static List<Voice> limitParts (final List<Voice> voices)
    {
        final TreeMap<Double, List<Voice>> panningGroups = new TreeMap<> ();
        for (final Voice voice: voices)
            panningGroups.computeIfAbsent (Double.valueOf (voice.panning ()), _ -> new ArrayList<> ()).add (voice);

        final Map<Voice, Integer> numSlots = new IdentityHashMap<> ();
        int remaining = ThirdWaveProgram.NUM_PARTS;
        for (int layer = 0; remaining > 0; layer++)
        {
            // A layer has one voice of each panning at most
            final TreeMap<Double, Voice> layerVoices = new TreeMap<> ();
            for (final Map.Entry<Double, List<Voice>> panningGroup: panningGroups.entrySet ())
                if (layer < panningGroup.getValue ().size ())
                    layerVoices.put (panningGroup.getKey (), panningGroup.getValue ().get (layer));
            if (layerVoices.isEmpty ())
                break;

            final List<List<Voice>> units = new ArrayList<> ();
            final Set<Double> pairedPannings = new HashSet<> ();
            for (final Map.Entry<Double, Voice> entry: layerVoices.entrySet ())
            {
                final double panning = entry.getKey ().doubleValue ();
                final Voice mirrored = layerVoices.get (Double.valueOf (-panning));
                if (panning < 0 && mirrored != null)
                {
                    units.add (List.of (entry.getValue (), mirrored));
                    pairedPannings.add (entry.getKey ());
                    pairedPannings.add (Double.valueOf (-panning));
                }
            }
            for (final Map.Entry<Double, Voice> entry: layerVoices.entrySet ())
                if (!pairedPannings.contains (entry.getKey ()))
                    units.add (List.of (entry.getValue ()));

            for (final List<Voice> unit: units)
                for (int slot = 0;; slot++)
                {
                    int needed = 0;
                    for (final Voice voice: unit)
                        if (slot < voice.slots ().size ())
                            needed++;
                    if (needed == 0 || needed > remaining)
                        break;
                    for (final Voice voice: unit)
                        if (slot < voice.slots ().size ())
                            numSlots.put (voice, Integer.valueOf (slot + 1));
                    remaining -= needed;
                }
        }

        final List<Voice> limitedVoices = new ArrayList<> ();
        for (final Voice voice: voices)
        {
            final Integer count = numSlots.get (voice);
            if (count != null)
                limitedVoices.add (new Voice (voice.panning (), new ArrayList<> (voice.slots ().subList (0, count.intValue ()))));
        }
        return limitedVoices;
    }


    /**
     * Distribute the samples to voices: first by their panning and by their tuning in semi-tones,
     * e.g. a part which plays an octave lower, then into layers.
     *
     * @param samples The samples
     * @param usePanning If false, all samples are centered
     * @param useTuning If false, the tuning is not considered
     * @return The voices, ordered by their panning
     */
    private static List<Voice> createVoices (final List<ProgramSample> samples, final boolean usePanning, final boolean useTuning)
    {
        final TreeMap<Double, TreeMap<Long, List<ThirdWaveFile.Sample>>> groups = new TreeMap<> ();
        for (final ProgramSample sample: samples)
        {
            final double panning = usePanning ? Math.round (sample.panning () * 100) / 100.0 : 0;
            final long tuning = useTuning ? Math.round (sample.tuning ()) : 0;
            groups.computeIfAbsent (Double.valueOf (panning), _ -> new TreeMap<> ()).computeIfAbsent (Long.valueOf (tuning), _ -> new ArrayList<> ()).add (sample.sample ());
        }
        final List<Voice> voices = new ArrayList<> ();
        for (final Map.Entry<Double, TreeMap<Long, List<ThirdWaveFile.Sample>>> panningGroup: groups.entrySet ())
            for (final List<ThirdWaveFile.Sample> tuningGroup: panningGroup.getValue ().values ())
                for (final List<ThirdWaveFile.Sample> layer: createLayers (tuningGroup))
                    voices.add (new Voice (panningGroup.getKey ().doubleValue (), splitLayer (layer, false)));
        return voices;
    }


    /**
     * Test if the tuning of the samples of all slots can be set on the parts which play them: the
     * part gets the tuning which most samples of its slot share, the others need to move their
     * assigned note by the difference without losing a key of their key range.
     *
     * @param voices The voices
     * @param tunings The tuning of each sample
     * @return True if the tuning of all samples can be kept
     */
    private static boolean isTunable (final List<Voice> voices, final Map<ThirdWaveFile.Sample, Double> tunings)
    {
        for (final Voice voice: voices)
            for (final List<ThirdWaveFile.Sample> slot: voice.slots ())
            {
                final double tuning = getPartTuning (slot, tunings);
                for (final ThirdWaveFile.Sample sample: slot)
                {
                    final long root = sample.root () - Math.round (tunings.get (sample).doubleValue () - tuning);
                    if (root < 0 || root > 127 || sample.low () < root - ThirdWaveFile.MAX_TRANSPOSITION || sample.high () > root + ThirdWaveFile.MAX_TRANSPOSITION)
                        return false;
                }
            }
        return true;
    }


    /**
     * Get the tuning for the part which plays a slot: the one which most of its samples have.
     *
     * @param slot The samples of the slot
     * @param tunings The tuning of each sample
     * @return The tuning in semi-tones
     */
    private static double getPartTuning (final List<ThirdWaveFile.Sample> slot, final Map<ThirdWaveFile.Sample, Double> tunings)
    {
        final Map<Double, Integer> counts = new HashMap<> ();
        double tuning = tunings.get (slot.get (0)).doubleValue ();
        int maxCount = 0;
        for (final ThirdWaveFile.Sample sample: slot)
        {
            final Double sampleTuning = tunings.get (sample);
            final int count = counts.merge (sampleTuning, Integer.valueOf (1), Integer::sum).intValue ();
            if (count > maxCount)
            {
                maxCount = count;
                tuning = sampleTuning.doubleValue ();
            }
        }
        return tuning;
    }


    /**
     * Move the tuning and the loudness which the samples of a slot share to the part which plays
     * the slot: the tuning which most of the samples have and the volume of the loudest one. The
     * samples keep the difference.
     *
     * @param slot The samples of the slot, which are replaced
     * @param tunings The tuning of each sample
     * @return The tuning of the oscillator in semi-tones, its level, the volume of the part and the
     *         number of samples whose key range was limited
     */
    private static double [] moveToPart (final List<ThirdWaveFile.Sample> slot, final Map<ThirdWaveFile.Sample, Double> tunings)
    {
        final double tuning = getPartTuning (slot, tunings);
        int limitedKeyRanges = 0;
        double volume = 0;
        for (final ThirdWaveFile.Sample sample: slot)
            volume = Math.max (volume, sample.volume ());
        final double level = Math.min (volume, 1);
        final double partVolume = Math.clamp (volume, 1, ThirdWaveProgram.MAX_PART_VOLUME);
        final double partGain = level * partVolume;

        for (int i = 0; i < slot.size (); i++)
        {
            final ThirdWaveFile.Sample sample = slot.get (i);
            // The rest of the tuning moves the assigned note, if it can
            final double rest = tunings.get (sample).doubleValue () - tuning;
            long coarse = Math.round (rest);
            final long root = sample.root () - coarse;
            if (root < 0 || root > 127)
                coarse = 0;
            final int newRoot = (int) (sample.root () - coarse);
            final int low = Math.max (sample.low (), newRoot - ThirdWaveFile.MAX_TRANSPOSITION);
            final int high = Math.min (sample.high (), newRoot + ThirdWaveFile.MAX_TRANSPOSITION);
            if (low != sample.low () || high != sample.high ())
                limitedKeyRanges++;
            final float fineTuning = (float) Math.clamp (rest - coarse, -1, 1);
            final float sampleVolume = (float) (partGain > 0 ? Math.min (sample.volume () / partGain, ThirdWaveFile.MAX_VOLUME) : 1);
            slot.set (i, new ThirdWaveFile.Sample (sample.name (), sample.sampleRate (), sample.bitDepth (), sample.start (), sample.end (), sample.loopStart (), sample.loopEnd (), newRoot, Math.min (low, high), high, sample.crossfadeType (), sample.crossfade (), sample.loopMode (), fineTuning, sample.secondSampleRate (), sampleVolume, sample.audio ()));
        }
        return new double []
        {
            tuning,
            level,
            partVolume,
            limitedKeyRanges
        };
    }


    /**
     * Test if two slots have the same samples: the same audio, mapping, loop, tuning and volume.
     *
     * @param samples1 The samples of the first slot
     * @param samples2 The samples of the second slot
     * @return True if they are the same
     */
    private static boolean isSameSlot (final List<ThirdWaveFile.Sample> samples1, final List<ThirdWaveFile.Sample> samples2)
    {
        if (samples1.size () != samples2.size ())
            return false;
        for (int i = 0; i < samples1.size (); i++)
            if (!isSameSample (samples1.get (i), samples2.get (i), true))
                return false;
        return true;
    }


    /**
     * Test if two samples play the same audio in the same way: the same mapping, play range, loop
     * and tuning.
     *
     * @param s1 The first sample
     * @param s2 The second sample
     * @param compareVolume True to compare their volume as well
     * @return True if they are the same
     */
    private static boolean isSameSample (final ThirdWaveFile.Sample s1, final ThirdWaveFile.Sample s2, final boolean compareVolume)
    {
        return s1.sampleRate () == s2.sampleRate () && s1.start () == s2.start () && s1.end () == s2.end () && s1.loopStart () == s2.loopStart () && s1.loopEnd () == s2.loopEnd () && s1.root () == s2.root () && s1.low () == s2.low () && s1.high () == s2.high () && s1.crossfadeType () == s2.crossfadeType () && s1.crossfade () == s2.crossfade () && s1.loopMode () == s2.loopMode () && s1.tune () == s2.tune () && (!compareVolume || s1.volume () == s2.volume ()) && Arrays.equals (s1.audio (), s2.audio ());
    }


    private static int countParts (final List<Voice> voices)
    {
        int count = 0;
        for (final Voice voice: voices)
            count += voice.slots ().size ();
        return count;
    }


    /**
     * Test if the slots of a voice lie in the keyboard sections with the same index. A slot may
     * reach one key into the neighbouring sections, which the lowest sample of the upper slot plays
     * as well, see splitKeyboard.
     *
     * @param slots The samples of the slots of the voice, one for each section
     * @param splitNotes The notes of the split points; a split point belongs to the lower section
     * @return True if all slots fit into their sections
     */
    private static boolean fitsSections (final List<List<ThirdWaveFile.Sample>> slots, final int [] splitNotes)
    {
        for (int section = 0; section < slots.size (); section++)
        {
            final int low = section == 0 ? 0 : splitNotes[section - 1];
            final int high = section == splitNotes.length ? 127 : splitNotes[section] + 1;
            for (final ThirdWaveFile.Sample sample: slots.get (section))
                if (sample.low () < low || sample.high () > high)
                    return false;
        }
        return true;
    }


    /**
     * Test if one of the samples of a slot plays in a keyboard section.
     *
     * @param samples The samples of the slot
     * @param section The index of the section
     * @param splitNotes The notes of the split points; a split point belongs to the lower section
     * @return True if a sample plays in the section
     */
    private static boolean isInSection (final List<ThirdWaveFile.Sample> samples, final int section, final int [] splitNotes)
    {
        final int low = section == 0 ? 0 : splitNotes[section - 1] + 1;
        final int high = section == splitNotes.length ? 127 : splitNotes[section];
        for (final ThirdWaveFile.Sample sample: samples)
            if (sample.low () <= high && sample.high () >= low)
                return true;
        return false;
    }


    /**
     * Write multi-sample slot files, one for each slot of all layers.
     *
     * @param destinationFolder Where to write the files
     * @param name The name of the preset
     * @param layers The layers of samples
     * @throws IOException Could not write a file
     */
    private void writeSlots (final File destinationFolder, final String name, final List<List<ThirdWaveFile.Sample>> layers) throws IOException
    {
        final List<List<ThirdWaveFile.Sample>> slots = new ArrayList<> ();
        long frames = 0;
        for (final List<ThirdWaveFile.Sample> layer: layers)
        {
            slots.addAll (splitLayer (layer, true));
            for (final ThirdWaveFile.Sample sample: layer)
                frames += sample.getFrames ();
        }
        if (slots.size () > 1)
            this.notifier.log ("IDS_THIRD_WAVE_SLOTS", name, Integer.toString (slots.size ()));
        if (frames > ThirdWaveFile.MEMORY_FRAMES)
            this.notifier.log ("IDS_THIRD_WAVE_MEMORY", name);

        for (int i = 0; i < slots.size (); i++)
        {
            final String suffix = slots.size () == 1 ? "" : createSuffix (i + 1, slots.size ());
            final File file = this.createUniqueFilename (destinationFolder, SafeFileNames.create (name + suffix), "bin");
            this.notifier.log ("IDS_NOTIFY_STORING", file.getAbsolutePath ());
            try (final OutputStream out = new BufferedOutputStream (new FileOutputStream (file)))
            {
                ThirdWaveFile.write (out, new ThirdWaveFile.Slot (createSlotName (name, suffix), -1, -1, slots.get (i)));
            }
        }
    }


    /**
     * Convert a sample zone to a sample of a slot.
     *
     * @param zone The zone
     * @param notes Where to count the differences
     * @param forProgram True if the sample is for a unified program file: its gain is stored as its
     *            volume and its tuning is left to the program; otherwise the gain is applied to the
     *            audio and the tuning to the assigned note and the fine tuning
     * @return The sample, empty if the zone cannot be converted
     * @throws IOException Could not convert the audio
     */
    private Optional<ThirdWaveFile.Sample> createSample (final ISampleZone zone, final Notes notes, final boolean forProgram) throws IOException
    {
        final String zoneName = zone.getName ();
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zoneName, "-");
            return Optional.empty ();
        }

        // The assigned note moves by the coarse tuning, the fine tuning is only +/-1 semi-tone
        final double tuning = forProgram ? 0 : zone.getTuning ();
        final long coarseTuning = Math.round (tuning);
        final int keyLow = Math.clamp (limitToDefault (zone.getKeyLow (), 0), 0, 127);
        final int keyHigh = Math.clamp (limitToDefault (zone.getKeyHigh (), 127), keyLow, 127);
        int keyRoot = limitToDefault (zone.getKeyRoot (), DEFAULT_ROOT_KEY);
        if (zone.getKeyTracking () < 0.5)
        {
            // A sample which plays the same pitch on all keys plays it on the assigned note
            keyRoot = keyLow + (keyHigh - keyLow) / 2;
            if (keyHigh > keyLow)
                notes.fixedPitch++;
        }
        final long root = keyRoot - coarseTuning;
        final int low = (int) Math.max (keyLow, root - ThirdWaveFile.MAX_TRANSPOSITION);
        final int high = (int) Math.min (keyHigh, root + ThirdWaveFile.MAX_TRANSPOSITION);
        if (root < 0 || root > 127 || low > high)
        {
            this.notifier.logError ("IDS_THIRD_WAVE_CANNOT_MAP", zoneName);
            return Optional.empty ();
        }
        if (low != keyLow || high != keyHigh)
            notes.limitedKeyRanges++;

        final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
        final int sampleRate = Math.clamp (audioMetadata.getSampleRate (), ThirdWaveFile.MIN_SAMPLE_RATE, ThirdWaveFile.MAX_SAMPLE_RATE);
        if (Math.round (audioMetadata.getNumberOfSamples () * (double) sampleRate / audioMetadata.getSampleRate ()) > ThirdWaveFile.MEMORY_FRAMES)
        {
            this.notifier.logError ("IDS_THIRD_WAVE_SAMPLE_TOO_LONG", zoneName);
            return Optional.empty ();
        }

        final DestinationAudioFormat destinationFormat = new DestinationAudioFormat (new int []
        {
            16
        }, sampleRate, true);
        this.logResampling (zone, destinationFormat);
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), destinationFormat);
        final int channels = waveFile.getFormatChunk ().getNumberOfChannels ();
        final byte [] data = waveFile.getDataChunk ().getData ();
        final int frames = data.length / (2 * channels);
        if (frames == 0)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zoneName, "-");
            return Optional.empty ();
        }
        if (channels > 1)
            notes.mixedToMono++;

        // Mix down to mono and play reversed samples forwards
        final boolean isReversed = zone.isReversed ();
        final double [] mono = new double [frames];
        double peak = 0;
        for (int frame = 0; frame < frames; frame++)
        {
            final int sourceFrame = isReversed ? frames - 1 - frame : frame;
            double value = 0;
            for (int channel = 0; channel < channels; channel++)
            {
                final int offset = 2 * (sourceFrame * channels + channel);
                value += (short) (data[offset] & 0xFF | data[offset + 1] << 8);
            }
            mono[frame] = value / channels;
            peak = Math.max (peak, Math.abs (mono[frame]));
        }

        // The samples of a unified program store a volume up to +6dB, which the volume of the part
        // raises by up to 1.7 (see moveToPart), the multi-sample slot files none; the rest of the
        // gain is applied to the audio - but not further than to its peak level, since the audio
        // would clip
        double gain = Math.pow (10, zone.getGain () / 20.0);
        float volume = 1;
        if (forProgram)
        {
            volume = (float) Math.min (gain, ThirdWaveFile.MAX_VOLUME * ThirdWaveProgram.MAX_PART_VOLUME);
            gain = volume > 0 ? gain / volume : 1;
        }
        if (gain > 1 && peak * gain > Short.MAX_VALUE)
        {
            gain = Math.max (1, Short.MAX_VALUE / peak);
            notes.limitedGain++;
        }
        final byte [] audio = new byte [2 * frames];
        for (int frame = 0; frame < frames; frame++)
        {
            final int value = Math.clamp (Math.round (mono[frame] * gain), Short.MIN_VALUE, Short.MAX_VALUE);
            audio[2 * frame] = (byte) value;
            audio[2 * frame + 1] = (byte) (value >> 8);
        }

        // The positions are already the ones of the converted audio, see
        // recalculateSamplePositions. The stop of a zone is often the length of the sample.
        int start = Math.clamp (zone.getStart (), 0, frames - 1);
        final int stop = zone.getStop ();
        int end = stop <= 0 || stop >= frames ? frames - 1 : stop;
        if (start > end)
        {
            start = 0;
            end = frames - 1;
        }

        int loopMode = ThirdWaveFile.LOOP_OFF;
        int loopStart = start;
        int loopEnd = end;
        int crossfade = 0;
        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            // The instrument plays only one forward loop, which lies within the play range
            final ISampleLoop loop = loops.get (0);
            final int newLoopStart = Math.clamp (loop.getStart (), start, end);
            final int newLoopEnd = Math.clamp (loop.getEnd (), start, end);
            if (newLoopStart < newLoopEnd)
            {
                loopStart = newLoopStart;
                loopEnd = newLoopEnd;
                loopMode = loop.isLoopUntilRelease () ? ThirdWaveFile.LOOP_WHILE_HELD : ThirdWaveFile.LOOP_ALWAYS;
                crossfade = Math.clamp (loop.getCrossfadeInSamples (), 0, loopEnd - loopStart + 1);
                if (loops.size () > 1 || loop.getType () != LoopType.FORWARDS || loopStart != loop.getStart () || loopEnd != loop.getEnd ())
                    notes.changedLoops++;
            }
            else
                notes.droppedLoops++;
        }

        if (isReversed)
        {
            final int reversedStart = frames - 1 - end;
            end = frames - 1 - start;
            start = reversedStart;
            final int reversedLoopStart = frames - 1 - loopEnd;
            loopEnd = frames - 1 - loopStart;
            loopStart = reversedLoopStart;
        }

        final float fineTuning = (float) (tuning - coarseTuning);
        final int crossfadeType = crossfade > 0 ? ThirdWaveFile.CROSSFADE_LINEAR : ThirdWaveFile.CROSSFADE_OFF;
        return Optional.of (new ThirdWaveFile.Sample (zoneName, sampleRate, ThirdWaveFile.BIT_DEPTH_16, start, end, loopStart, loopEnd, (int) root, low, high, crossfadeType, crossfade, loopMode, fineTuning, sampleRate, volume, audio));
    }


    /**
     * Log the differences of the samples of a preset, which could not be converted exactly.
     *
     * @param name The name of the preset
     * @param notes The differences
     */
    private void logNotes (final String name, final Notes notes)
    {
        this.logNote ("IDS_THIRD_WAVE_RELEASE_TRIGGERS", notes.releaseTriggers, name);
        this.logNote ("IDS_THIRD_WAVE_MIXED_TO_MONO", notes.mixedToMono, name);
        this.logNote ("IDS_THIRD_WAVE_GAIN_LIMITED", notes.limitedGain, name);
        this.logNote ("IDS_THIRD_WAVE_LOOPS_CHANGED", notes.changedLoops, name);
        this.logNote ("IDS_THIRD_WAVE_LOOPS_DROPPED", notes.droppedLoops, name);
        this.logNote ("IDS_THIRD_WAVE_KEY_RANGES", notes.limitedKeyRanges, name);
        this.logNote ("IDS_THIRD_WAVE_FIXED_PITCH", notes.fixedPitch, name);
    }


    private void logNote (final String messageID, final int count, final String name)
    {
        if (count > 0)
            this.notifier.log (messageID, Integer.toString (count), name);
    }


    /**
     * Distribute the samples to layers, e.g. one for each velocity layer. The samples of a layer
     * have different assigned notes, which do not lie in the key range of another sample of the
     * layer.
     *
     * @param samples The samples
     * @return The samples of each layer, ordered by their assigned note
     */
    private static List<List<ThirdWaveFile.Sample>> createLayers (final List<ThirdWaveFile.Sample> samples)
    {
        final List<ThirdWaveFile.Sample> sortedSamples = new ArrayList<> (samples);
        sortedSamples.sort (Comparator.comparingInt (ThirdWaveFile.Sample::root));

        final List<List<ThirdWaveFile.Sample>> layers = new ArrayList<> ();
        for (final ThirdWaveFile.Sample sample: sortedSamples)
        {
            List<ThirdWaveFile.Sample> matchingLayer = null;
            for (final List<ThirdWaveFile.Sample> layer: layers)
                if (!isLayered (layer, sample))
                {
                    matchingLayer = layer;
                    break;
                }
            if (matchingLayer == null)
            {
                matchingLayer = new ArrayList<> ();
                layers.add (matchingLayer);
            }
            matchingLayer.add (sample);
        }
        return layers;
    }


    /**
     * Split a layer in the order of the keys into slots, which hold up to 8 samples.
     *
     * @param layer The samples of the layer, ordered by their assigned note
     * @param limitMemory If true, a slot also ends before it would exceed the sample memory
     * @return The samples of each slot
     */
    private static List<List<ThirdWaveFile.Sample>> splitLayer (final List<ThirdWaveFile.Sample> layer, final boolean limitMemory)
    {
        final List<List<ThirdWaveFile.Sample>> slots = new ArrayList<> ();
        List<ThirdWaveFile.Sample> slot = new ArrayList<> ();
        int frames = 0;
        for (final ThirdWaveFile.Sample sample: layer)
        {
            if (slot.size () == ThirdWaveFile.MAX_SAMPLES || limitMemory && frames + sample.getFrames () > ThirdWaveFile.MEMORY_FRAMES)
            {
                slots.add (slot);
                slot = new ArrayList<> ();
                frames = 0;
            }
            slot.add (sample);
            frames += sample.getFrames ();
        }
        slots.add (slot);
        return slots;
    }


    /**
     * Calculate the split points of the keyboard between the slots, which are ordered by their
     * keys. The instrument sets a split point to 'the end of the lower and the beginning of the
     * upper split zone', it is not clear to which one the note belongs. Therefore, the split point
     * lies either on a note which neither slot plays or the lowest sample of the upper slot is
     * extended to play the note as well; this is done for all voices with as many slots.
     *
     * @param voices The voices, the lowest samples of their slots might be replaced
     * @param numSections The number of keyboard sections, the number of slots of the main voice
     * @param slots The slots of the main voice
     * @return The notes of the split points, one less than there are sections
     */
    private static int [] splitKeyboard (final List<Voice> voices, final int numSections, final List<List<ThirdWaveFile.Sample>> slots)
    {
        final int [] splitNotes = new int [numSections - 1];
        for (int i = 1; i < numSections; i++)
        {
            int lowerHigh = 0;
            for (final ThirdWaveFile.Sample sample: slots.get (i - 1))
                lowerHigh = Math.max (lowerHigh, sample.high ());
            final ThirdWaveFile.Sample lowestSample = slots.get (i).get (getLowestSample (slots.get (i)));

            int splitNote = lowestSample.low () > lowerHigh + 1 ? lowerHigh + 1 : lowestSample.low () - 1;
            if (i > 1)
                splitNote = Math.max (splitNote, splitNotes[i - 2] + 1);
            splitNotes[i - 1] = Math.clamp (splitNote, 0, 127);

            for (final Voice voice: voices)
            {
                if (voice.slots ().size () != numSections)
                    continue;
                final List<ThirdWaveFile.Sample> upper = voice.slots ().get (i);
                final int lowest = getLowestSample (upper);
                final ThirdWaveFile.Sample sample = upper.get (lowest);
                if (sample.low () == splitNotes[i - 1] + 1 && sample.root () - splitNotes[i - 1] <= ThirdWaveFile.MAX_TRANSPOSITION)
                    upper.set (lowest, sample.withLow (splitNotes[i - 1]));
            }
        }
        return splitNotes;
    }


    private static int getLowestSample (final List<ThirdWaveFile.Sample> samples)
    {
        int lowest = 0;
        for (int i = 1; i < samples.size (); i++)
            if (samples.get (i).low () < samples.get (lowest).low ())
                lowest = i;
        return lowest;
    }


    /**
     * Tests if a sample is layered with one of the samples of a layer, which is the case if their
     * assigned notes are the same or one of them lies within the key range of the other. Key
     * ranges which only overlap at their edges are allowed, slots of the instrument have them as
     * well.
     *
     * @param layer The samples of the layer
     * @param sample The sample to test
     * @return True if layered
     */
    private static boolean isLayered (final List<ThirdWaveFile.Sample> layer, final ThirdWaveFile.Sample sample)
    {
        for (final ThirdWaveFile.Sample other: layer)
            if (other.root () == sample.root () || isInRange (other.root (), sample) || isInRange (sample.root (), other))
                return true;
        return false;
    }


    private static boolean isInRange (final int note, final ThirdWaveFile.Sample sample)
    {
        return note >= sample.low () && note <= sample.high ();
    }


    private static String createSuffix (final int number, final int count)
    {
        return count < 10 ? " " + number : String.format (Locale.US, " %02d", Integer.valueOf (number));
    }


    /**
     * Create the suffix of the name of a slot of a program, which numbers the slots along the
     * keyboard and names the voice: 'L' and 'R' for two voices panned to both sides, otherwise
     * letters.
     *
     * @param slot The index of the slot along the keyboard
     * @param numSlots The number of slots of the voice
     * @param voice The index of the voice
     * @param voices All voices, ordered from left to right
     * @return The suffix
     */
    private static String createSuffix (final int slot, final int numSlots, final int voice, final List<Voice> voices)
    {
        final String suffix = numSlots == 1 ? "" : createSuffix (slot + 1, numSlots);
        if (voices.size () == 1)
            return suffix;
        if (voices.size () == 2 && voices.get (0).panning () < 0 && voices.get (1).panning () > 0)
            return suffix + (voice == 0 ? " L" : " R");
        return suffix + " " + (char) ('A' + Math.min (voice, 25));
    }


    /**
     * Create the name of a slot, which is shortened so that the suffix is kept.
     *
     * @param name The name of the preset
     * @param suffix The suffix which numbers the slots of a preset
     * @return The name
     */
    private static String createSlotName (final String name, final String suffix)
    {
        final int maxLength = ThirdWaveFile.MAX_NAME_LENGTH - suffix.length ();
        return (name.length () > maxLength ? name.substring (0, maxLength).trim () : name) + suffix;
    }
}
