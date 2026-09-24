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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Creates files for the Groove Synthesis 3rd Wave: either a unified program file (*.pgdata), a
 * program together with its samples, which is loaded with 'Import unified program data file' from
 * the 'Programs' folder, or multi-sample slot files (*.bin), which are loaded with 'Import
 * multisample' or 'Bulk multisample import' from the 'Audio' folder. A sample slot holds up to 8
 * mono samples which are not layered. The program is the init program of the instrument whose
 * parts play the sample slots; the settings of the source (envelopes, filters, ...) are not
 * converted.
 *
 * @author Jürgen Moßgraber
 */
public class ThirdWaveCreator extends AbstractCreator<ThirdWaveCreatorUI>
{
    private static final int DEFAULT_ROOT_KEY = 60;


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
        final List<ThirdWaveFile.Sample> samples = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (this.isCancelled ())
                    return;
                if (zone.getTrigger () == TriggerType.RELEASE || group.getTrigger () == TriggerType.RELEASE)
                    notes.releaseTriggers++;
                else
                    this.createSample (zone, notes, writeProgram).ifPresent (samples::add);
                this.progress.notifyProgress ();
            }

        this.logNotes (name, notes);
        if (samples.isEmpty ())
        {
            this.notifier.logError ("IDS_THIRD_WAVE_NO_SAMPLES", name);
            return;
        }

        final List<List<ThirdWaveFile.Sample>> layers = createLayers (samples);
        if (writeProgram)
            this.writeProgram (destinationFolder, name, layers);
        else
            this.writeSlots (destinationFolder, name, layers);

        this.progress.notifyDone ();
    }


    /**
     * Write a unified program file. The program plays the first layer of samples, each of its
     * sample slots with one part, which split the keyboard.
     *
     * @param destinationFolder Where to write the file
     * @param name The name of the preset
     * @param layers The layers of samples
     * @throws IOException Could not write the file
     */
    private void writeProgram (final File destinationFolder, final String name, final List<List<ThirdWaveFile.Sample>> layers) throws IOException
    {
        if (layers.size () > 1)
            this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_LAYERS", name, Integer.toString (layers.size ()));

        // All samples of a program need to fit into the memory at the same time, therefore its
        // slots are only split by the number of samples
        List<List<ThirdWaveFile.Sample>> slotSamples = splitLayer (layers.get (0), false);
        if (slotSamples.size () > ThirdWaveProgram.NUM_PARTS)
        {
            this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_PARTS", name, Integer.toString (slotSamples.size ()), Integer.toString (slotSamples.size () - ThirdWaveProgram.NUM_PARTS));
            slotSamples = new ArrayList<> (slotSamples.subList (0, ThirdWaveProgram.NUM_PARTS));
        }
        long frames = 0;
        for (final List<ThirdWaveFile.Sample> samples: slotSamples)
            for (final ThirdWaveFile.Sample sample: samples)
                frames += sample.getFrames ();
        if (frames > ThirdWaveFile.MEMORY_FRAMES)
        {
            this.notifier.logError ("IDS_THIRD_WAVE_PROGRAM_MEMORY", name);
            return;
        }

        final int [] splitNotes = splitKeyboard (slotSamples);
        if (splitNotes.length > 0)
            this.notifier.log ("IDS_THIRD_WAVE_PROGRAM_SPLITS", name, Integer.toString (slotSamples.size ()));

        // Part N plays slot N with its first oscillator; its other oscillators and the parts which
        // are not used play the analog waveform, whose ID follows the ones of the slots
        final List<ThirdWaveFile.Slot> slots = new ArrayList<> ();
        final ThirdWaveProgram program = ThirdWaveProgram.createInitProgram ();
        final int analogWaveform = slotSamples.size ();
        for (int part = 0; part < ThirdWaveProgram.NUM_PARTS; part++)
        {
            if (part < slotSamples.size ())
            {
                final String suffix = slotSamples.size () == 1 ? "" : createSuffix (part + 1, slotSamples.size ());
                slots.add (new ThirdWaveFile.Slot (createSlotName (name, suffix), -1, slotSamples.get (part)));
                program.setOscillator (part, 0, part, 1);
                for (int oscillator = 1; oscillator < ThirdWaveProgram.NUM_OSCILLATORS; oscillator++)
                    program.setOscillator (part, oscillator, analogWaveform, 0);
            }
            else
                for (int oscillator = 0; oscillator < ThirdWaveProgram.NUM_OSCILLATORS; oscillator++)
                    program.setOscillatorResource (part, oscillator, analogWaveform);
        }
        program.setKeyboardSplits (splitNotes);

        final File file = this.createUniqueFilename (destinationFolder, SafeFileNames.create (name), "pgdata");
        this.notifier.log ("IDS_NOTIFY_STORING", file.getAbsolutePath ());
        try (final OutputStream out = new BufferedOutputStream (new FileOutputStream (file)))
        {
            ThirdWaveFile.writeProgram (out, name, slots, program);
        }
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
                ThirdWaveFile.write (out, new ThirdWaveFile.Slot (createSlotName (name, suffix), -1, slots.get (i)));
            }
        }
    }


    /**
     * Convert a sample zone to a sample of a slot.
     *
     * @param zone The zone
     * @param notes Where to count the differences
     * @param hasVolume True if the sample stores a volume (unified program files), otherwise the
     *            gain is applied to the audio
     * @return The sample, empty if the zone cannot be converted
     * @throws IOException Could not convert the audio
     */
    private Optional<ThirdWaveFile.Sample> createSample (final ISampleZone zone, final Notes notes, final boolean hasVolume) throws IOException
    {
        final String zoneName = zone.getName ();
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zoneName, "-");
            return Optional.empty ();
        }

        // The assigned note moves by the coarse tuning, the fine tuning is only +/-1 semi-tone
        final double tuning = zone.getTuning ();
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

        // The samples of a unified program store a volume up to +6dB, the multi-sample slot files
        // none; the rest of the gain is applied to the audio - but not further than to its peak
        // level, since the audio would clip
        double gain = Math.pow (10, zone.getGain () / 20.0);
        float volume = 1;
        if (hasVolume)
        {
            volume = (float) Math.min (gain, ThirdWaveFile.MAX_VOLUME);
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
     * extended to play the note as well.
     *
     * @param slots The samples of the slots, the lowest samples might be replaced
     * @return The notes of the split points, one less than there are slots
     */
    private static int [] splitKeyboard (final List<List<ThirdWaveFile.Sample>> slots)
    {
        final int [] splitNotes = new int [slots.size () - 1];
        for (int i = 1; i < slots.size (); i++)
        {
            final List<ThirdWaveFile.Sample> lower = slots.get (i - 1);
            final List<ThirdWaveFile.Sample> upper = slots.get (i);
            int lowerHigh = 0;
            for (final ThirdWaveFile.Sample sample: lower)
                lowerHigh = Math.max (lowerHigh, sample.high ());
            int lowest = 0;
            for (int s = 1; s < upper.size (); s++)
                if (upper.get (s).low () < upper.get (lowest).low ())
                    lowest = s;
            final ThirdWaveFile.Sample lowestSample = upper.get (lowest);

            int splitNote;
            if (lowestSample.low () > lowerHigh + 1)
                splitNote = lowerHigh + 1;
            else
            {
                splitNote = lowestSample.low () - 1;
                if (lowestSample.root () - splitNote <= ThirdWaveFile.MAX_TRANSPOSITION)
                    upper.set (lowest, lowestSample.withLow (splitNote));
            }
            if (i > 1)
                splitNote = Math.max (splitNote, splitNotes[i - 2] + 1);
            splitNotes[i - 1] = Math.clamp (splitNote, 0, 127);
        }
        return splitNotes;
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
