// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emax;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.hfe.DiskImageBuilder;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile;
import de.mossgrabers.convertwithmoss.file.hfe.Sector;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects and reads the sound banks of the E-mu Emax and Emax II. A bank is a dump of the whole
 * memory of the sampler - up to 100 presets, their voices and the audio of all samples - and it is
 * what an EM1, EB1, EM2 or EB2 file, a floppy disk image and each slot of a hard disk image holds.
 * Which of the two samplers a bank belongs to is read from the bank itself, so both are detected
 * from the same file endings.
 * <p>
 * Every preset of a bank becomes one multi-sample source. A preset maps each of its 88 keys to a
 * key area, which plays a primary voice and optionally a secondary one - the Dual Voice of the
 * sampler - so the primary voices become the first group and the secondary voices the second. A
 * voice names the sample it plays, the key at which that sample is at its recorded pitch, its
 * panning and the cutoff of its filter. The audio is expanded from the companded bytes which the
 * sampler feeds to its AM6072 DAC. See documentation/design/EMAX1_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class EmaxDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Hard disk and CD-ROM images of the Emax II hold up to 35 banks of up to 8 MB, but anything
     * beyond this is an image of one of the larger E-mu samplers which must not be pulled into
     * memory here.
     */
    private static final int    MAX_FILE_SIZE     = 320 * 1024 * 1024;
    /** The CPU address of the highest entry of the sample directory, which never changes. */
    private static final int    DIRECTORY_TOP     = EmaxConstants.CPU_END - EmaxConstants.SAMPLE_ENTRY_SIZE;
    /** The Emax formats its disks with 80 cylinders of 10 sectors on both sides, so 800 KB. */
    private static final int    CYLINDERS         = 80;
    private static final int    HEADS             = 2;
    private static final int    SECTORS_PER_TRACK = 10;
    private static final int    SECTOR_SIZE       = 512;
    private static final String ENDING_HFE        = ".hfe";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EmaxDetector (final INotifier notifier)
    {
        super ("E-mu Emax", "Emax", notifier, new MetadataSettingsUI ("Emax"), ".em1", ".eb1", ".em2", ".eb2", ".emx", ".em1fd", ".em2fd", ".ez1", ".ez2", ".img", ENDING_HFE);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery () || (sourceFile.length () > MAX_FILE_SIZE))
            return Collections.emptyList ();

        try
        {
            final byte [] data = readImage (sourceFile);
            if (data.length == 0)
                return Collections.emptyList ();
            final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
            // A file may hold more than one bank - a hard disk image keeps up to 35 of them - and a
            // bank does not have to start at the beginning of the file: an EM1 file puts a
            // signature in front of it and a floppy image the operating system, so it is searched
            int offset = 0;
            while (offset + EmaxConstants.PARAMETER_SIZE <= data.length)
                if (isBank (data, offset))
                {
                    multisampleSources.addAll (this.parseBank (sourceFile, data, offset));
                    offset += EmaxConstants.PARAMETER_SIZE + readInt32 (data, offset + EmaxConstants.SAMPLE_MEMORY_USED) * getModel (data, offset).getBytesPerFrame ();
                }
                else
                    offset++;
            return multisampleSources;
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Get the content of a file, decoding a HxC floppy emulator container if necessary. The Emax
     * writes a standard MFM track format, so unlike the disks of the Emulator II these images can
     * also be made with a PC floppy controller.
     *
     * @param sourceFile The file to read
     * @return The content or an empty array if the file is not an Emax disk
     * @throws IOException Could not read the file
     */
    private static byte [] readImage (final File sourceFile) throws IOException
    {
        if (!sourceFile.getName ().toLowerCase (Locale.US).endsWith (ENDING_HFE))
            return Files.readAllBytes (sourceFile.toPath ());

        final HfeFile hfeFile = new HfeFile (sourceFile);
        if (hfeFile.getTrackEncoding () != HfeFile.ENCODING_ISOIBM_MFM)
            return new byte [0];
        final List<Sector> sectors = hfeFile.decodeSectors ();
        if (sectors.isEmpty ())
            return new byte [0];
        return DiskImageBuilder.buildImage (sectors, CYLINDERS, HEADS, SECTORS_PER_TRACK, SECTOR_SIZE, false);
    }


    /**
     * Check whether a bank starts at the given position. The header of a bank holds a constant, two
     * pointers which have to be aligned to the sample directory and the number of audio bytes which
     * follow the parameter memory, which together do not match anything else.
     *
     * @param data The data to check
     * @param offset The position at which the bank would start
     * @return True if this is a bank
     */
    private static boolean isBank (final byte [] data, final int offset)
    {
        if (readInt32 (data, offset + EmaxConstants.SAMPLE_DIRECTORY_TOP) != DIRECTORY_TOP)
            return false;

        final int directoryBottom = readInt32 (data, offset + EmaxConstants.SAMPLE_DIRECTORY_BOTTOM);
        if (directoryBottom > EmaxConstants.CPU_END || directoryBottom < EmaxConstants.CPU_BASE + EmaxConstants.PRESET_HEAP || (EmaxConstants.CPU_END - directoryBottom) % EmaxConstants.SAMPLE_ENTRY_SIZE != 0)
            return false;

        final int selectedPreset = readInt32 (data, offset + EmaxConstants.SELECTED_PRESET);
        if (selectedPreset < 0 || selectedPreset >= EmaxConstants.NUM_PRESET_SLOTS)
            return false;

        final int firstPreset = readInt16 (data, offset);
        if (firstPreset < EmaxConstants.CPU_BASE + EmaxConstants.PRESET_HEAP || firstPreset >= EmaxConstants.CPU_END)
            return false;

        // The audio has to be there as well, which is what tells a bank apart from a stray copy of
        // a header and what makes an EB1 or EB2 file - a bank with the unused sample memory cut off
        // - work
        final EmaxModel model = getModel (data, offset);
        final int usedSampleMemory = readInt32 (data, offset + EmaxConstants.SAMPLE_MEMORY_USED);
        if (usedSampleMemory < 0 || usedSampleMemory > EmaxConstants.MEMORY_FRAMES_EMAX_2_MAX)
            return false;
        return offset + EmaxConstants.PARAMETER_SIZE + (long) usedSampleMemory * model.getBytesPerFrame () <= data.length;
    }


    /**
     * Get the sampler which a bank belongs to. The unused slots of its sequence table hold the
     * number of frames of the sample memory, which the two samplers do not share.
     *
     * @param data The data which holds the bank
     * @param bank The position of the bank
     * @return The sampler
     */
    private static EmaxModel getModel (final byte [] data, final int bank)
    {
        int memoryFrames = 0;
        for (int index = 0; index < EmaxConstants.NUM_SEQUENCE_SLOTS; index++)
        {
            final int value = readInt32 (data, bank + EmaxConstants.SEQUENCE_TABLE + index * 4);
            if (value > memoryFrames && value <= EmaxConstants.MEMORY_FRAMES_EMAX_2_MAX)
                memoryFrames = value;
        }
        return EmaxModel.fromMemoryFrames (memoryFrames);
    }


    /**
     * Parse all presets of one bank.
     *
     * @param sourceFile The file the bank came from
     * @param data The data which holds the bank
     * @param bank The position of the bank
     * @return One multi-sample source per preset
     */
    private List<IMultisampleSource> parseBank (final File sourceFile, final byte [] data, final int bank)
    {
        final EmaxModel model = getModel (data, bank);
        final List<Sample> samples = readSampleDirectory (data, bank, model);
        if (samples.isEmpty ())
            return Collections.emptyList ();

        final String bankName = FileUtils.getNameWithoutType (sourceFile);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        int damaged = 0;
        // Every slot has to be checked: a bank can hold a preset in any of the 100 of them - the
        // library banks put their sequence demos into the last ones - and the bank does not say
        // which are in use
        for (int presetIndex = 0; presetIndex < EmaxConstants.NUM_PRESET_SLOTS; presetIndex++)
        {
            final int preset = readInt16 (data, bank + presetIndex * 2) - EmaxConstants.CPU_BASE;
            if (!isPreset (data, bank, preset, samples.size ()))
            {
                // An empty slot points into the free memory; only a slot which looks like it holds
                // a record is worth reporting
                if (preset >= EmaxConstants.PRESET_HEAP && preset < EmaxConstants.PARAMETER_SIZE && isPrintable (data[bank + preset]))
                    damaged++;
                continue;
            }
            final IMultisampleSource multisampleSource = this.parsePreset (sourceFile, data, bank, preset, samples, bankName);
            if (multisampleSource != null)
                multisampleSources.add (multisampleSource);
        }

        if (damaged > 0)
            this.notifier.log ("IDS_EMAX_DAMAGED_PRESETS", sourceFile.getName (), Integer.toString (damaged));
        return multisampleSources;
    }


    /**
     * Read the directory of the samples of a bank. It grows downwards from the end of the parameter
     * memory, so the sample which a voice references as number N is the Nth entry from the top.
     * Erasing a sample leaves a gap in the sample memory behind, so the entries do not have to
     * follow each other without one - but they may never overlap.
     *
     * @param data The data which holds the bank
     * @param bank The position of the bank
     * @param model The sampler which the bank belongs to
     * @return The samples; an entry is null where it does not describe usable audio
     */
    private static List<Sample> readSampleDirectory (final byte [] data, final int bank, final EmaxModel model)
    {
        final int numSamples = (EmaxConstants.CPU_END - readInt32 (data, bank + EmaxConstants.SAMPLE_DIRECTORY_BOTTOM)) / EmaxConstants.SAMPLE_ENTRY_SIZE;
        final int usedSampleMemory = readInt32 (data, bank + EmaxConstants.SAMPLE_MEMORY_USED);
        final List<Sample> samples = new ArrayList<> ();
        int previousEnd = 0;
        for (int index = 0; index < numSamples; index++)
        {
            final int entry = bank + EmaxConstants.PARAMETER_SIZE - EmaxConstants.SAMPLE_ENTRY_SIZE * (index + 1);
            final int start = readInt32 (data, entry + EmaxConstants.SAMPLE_START);
            final int end = readInt32 (data, entry + EmaxConstants.SAMPLE_END);
            if (start < previousEnd || end <= start || end > usedSampleMemory)
            {
                // The slot has to be kept so that the samples behind it keep their number
                samples.add (null);
                continue;
            }
            previousEnd = end;

            final Sample sample = new Sample ();
            sample.model = model;
            sample.data = data;
            sample.audioOffset = bank + EmaxConstants.PARAMETER_SIZE;
            sample.start = start;
            sample.end = end;
            sample.loopStart = readInt32 (data, entry + EmaxConstants.SAMPLE_LOOP_START);
            sample.loopEnd = readInt32 (data, entry + EmaxConstants.SAMPLE_LOOP_END);
            sample.flags = data[entry + EmaxConstants.SAMPLE_FLAGS] & 0xFF;
            final int rateIndex = data[entry + EmaxConstants.SAMPLE_RATE_INDEX] & 0xFF;
            sample.sampleRate = EmaxConstants.SAMPLE_RATES[rateIndex < EmaxConstants.SAMPLE_RATES.length ? rateIndex : 0];
            samples.add (sample);
        }
        return samples;
    }


    /**
     * Check whether a byte is a printable ASCII character, which is what a name starts with.
     *
     * @param value The byte
     * @return True if it is printable
     */
    private static boolean isPrintable (final byte value)
    {
        final int character = value & 0xFF;
        return character >= 0x20 && character <= 0x7E;
    }


    /**
     * Check whether a preset slot points at a record which can be read. Library banks which were
     * edited on the sampler contain slots whose pointer survived but whose record was overwritten.
     *
     * @param data The data which holds the bank
     * @param bank The position of the bank
     * @param preset The position of the preset record relative to the bank
     * @param numSamples The number of samples of the bank
     * @return True if the record is intact
     */
    private static boolean isPreset (final byte [] data, final int bank, final int preset, final int numSamples)
    {
        if (preset < EmaxConstants.PRESET_HEAP || preset + EmaxConstants.PRESET_VOICE_TABLE > EmaxConstants.PARAMETER_SIZE)
            return false;
        final int numKeyAreas = data[bank + preset + EmaxConstants.PRESET_KEY_AREA_COUNT] & 0xFF;
        if ((numKeyAreas == 0) || !isPrintable (data[bank + preset]))
            return false;

        for (int key = 0; key < EmaxConstants.NUM_KEYS; key++)
        {
            final int keyArea = data[bank + preset + EmaxConstants.PRESET_KEY_MAP + key] & 0xFF;
            if (keyArea != EmaxConstants.KEY_UNMAPPED && keyArea >= numKeyAreas)
                return false;
        }

        final int numVoices = getNumVoices (data, bank + preset, numKeyAreas);
        if (numVoices == 0 || preset + getPresetSize (numKeyAreas, numVoices) > EmaxConstants.PARAMETER_SIZE)
            return false;

        for (int voiceIndex = 0; voiceIndex < numVoices; voiceIndex++)
        {
            final int voice = bank + preset + getVoiceOffset (numKeyAreas, voiceIndex);
            if ((data[voice + EmaxConstants.VOICE_SAMPLE] & 0xFF) >= numSamples || (data[voice + EmaxConstants.VOICE_ORIGINAL_KEY] & 0xFF) >= EmaxConstants.NUM_KEYS)
                return false;
        }
        return true;
    }


    /**
     * Parse one preset into a multi-sample source.
     *
     * @param sourceFile The file the bank came from
     * @param data The data which holds the bank
     * @param bank The position of the bank
     * @param preset The position of the preset record relative to the bank
     * @param samples The samples of the bank
     * @param bankName The name of the bank
     * @return The multi-sample source or null if the preset maps no key at all
     */
    private IMultisampleSource parsePreset (final File sourceFile, final byte [] data, final int bank, final int preset, final List<Sample> samples, final String bankName)
    {
        final int numKeyAreas = data[bank + preset + EmaxConstants.PRESET_KEY_AREA_COUNT] & 0xFF;
        final IGroup [] groups = new IGroup []
        {
            new DefaultGroup ("Layer 1"),
            new DefaultGroup ("Layer 2")
        };

        int key = 0;
        while (key < EmaxConstants.NUM_KEYS)
        {
            final int keyArea = data[bank + preset + EmaxConstants.PRESET_KEY_MAP + key] & 0xFF;
            if (keyArea == EmaxConstants.KEY_UNMAPPED)
            {
                key++;
                continue;
            }

            int lastKey = key;
            while (lastKey + 1 < EmaxConstants.NUM_KEYS && (data[bank + preset + EmaxConstants.PRESET_KEY_MAP + lastKey + 1] & 0xFF) == keyArea)
                lastKey++;

            final int entry = bank + preset + EmaxConstants.PRESET_VOICE_TABLE + keyArea * EmaxConstants.VOICE_TABLE_ENTRY_SIZE;
            for (int layer = 0; layer < groups.length; layer++)
            {
                final int voice = data[entry + (layer == 0 ? EmaxConstants.VOICE_TABLE_PRIMARY : EmaxConstants.VOICE_TABLE_SECONDARY)] & 0xFF;
                if (voice == EmaxConstants.VOICE_NONE)
                    continue;
                final ISampleZone zone = createZone (data, bank + preset + getVoiceOffset (numKeyAreas, voice), samples, key, lastKey);
                if (zone != null)
                    groups[layer].addSampleZone (zone);
            }
            key = lastKey + 1;
        }

        final List<IGroup> usedGroups = new ArrayList<> ();
        for (final IGroup group: groups)
            if (!group.getSampleZones ().isEmpty ())
                usedGroups.add (group);
        if (usedGroups.isEmpty ())
            return null;

        final String name = new String (data, bank + preset, EmaxConstants.PRESET_NAME_LENGTH, StandardCharsets.US_ASCII).trim ();
        return this.createMultisampleSource (sourceFile, name.isEmpty () ? bankName : name, usedGroups, bankName);
    }


    /**
     * Create one zone from a voice and the run of keys which plays it.
     *
     * @param data The data which holds the bank
     * @param voice The absolute position of the voice record
     * @param samples The samples of the bank
     * @param lowKey The first key of the run
     * @param highKey The last key of the run
     * @return The zone or null if the sample of the voice holds no audio
     */
    private static ISampleZone createZone (final byte [] data, final int voice, final List<Sample> samples, final int lowKey, final int highKey)
    {
        final int sampleIndex = data[voice + EmaxConstants.VOICE_SAMPLE] & 0xFF;
        final Sample sample = samples.get (sampleIndex);
        if (sample == null)
            return null;

        final ISampleZone zone = new DefaultSampleZone ("Sample " + (sampleIndex + 1), lowKey + EmaxConstants.KEY_OFFSET, highKey + EmaxConstants.KEY_OFFSET);
        zone.setKeyRoot ((data[voice + EmaxConstants.VOICE_ORIGINAL_KEY] & 0xFF) + EmaxConstants.KEY_OFFSET);
        zone.setSampleData (sample.getSampleData ());

        final byte [] voiceRecord = new byte [EmaxConstants.VOICE_SIZE];
        System.arraycopy (data, voice, voiceRecord, 0, EmaxConstants.VOICE_SIZE);
        applyVoiceParameters (zone, voiceRecord);

        if ((sample.flags & EmaxConstants.SAMPLE_FLAG_LOOP) > 0 && sample.loopEnd > sample.loopStart && sample.loopStart >= sample.start && sample.loopEnd <= sample.end)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (sample.loopStart - sample.start);
            loop.setEnd (sample.loopEnd - sample.start);
            zone.addLoop (loop);
        }
        return zone;
    }


    /**
     * Apply the parameters which the 32 bytes of a voice record carry to a zone.
     *
     * @param zone The zone to fill
     * @param voiceRecord The voice record
     */
    private static void applyVoiceParameters (final ISampleZone zone, final byte [] voiceRecord)
    {
        // The panning nibble runs from 1 to 15 with 8 in the middle; 1 is fully right and 15 fully
        // left, which the sampler shows as +07 to -07
        final int panning = EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_PANNING_BITS, 4);
        if (panning != 0 && panning != EmaxConstants.PANNING_CENTER)
            zone.setPanning (Math.clamp ((EmaxConstants.PANNING_CENTER - panning) / (double) (EmaxConstants.PANNING_CENTER - 1), -1, 1));

        zone.setGain (-EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_ATTENUATION, 5) * EmaxConstants.ATTENUATION_DB_PER_STEP);
        zone.setTuning (EmaxConstants.readSignedVoiceField (voiceRecord, EmaxConstants.VOICE_TUNE, 5) * EmaxConstants.TUNE_CENTS_PER_STEP / 100.0);
        // A voice which does not transpose plays its sample at the recorded pitch on every key
        if (EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_NON_TRANSPOSE, 1) != 0)
            zone.setKeyTracking (0);

        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        fillEnvelope (amplitudeEnvelope, voiceRecord, EmaxConstants.VOICE_AMP_ATTACK);
        amplitudeEnvelope.setDelayTime (EmaxConstants.getVoiceDelayTime (EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_DELAY, 6)));
        // The sampler makes a note quieter the softer it is played, over the range of this amount
        final double velocityRange = EmaxConstants.getVelocityToLevel (EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_VELOCITY_TO_LEVEL, 4));
        if (velocityRange > 0)
            zone.getAmplitudeVelocityModulator ().setDepth (Math.clamp (velocityRange / ILfoModulator.MAX_VOLUME_DEPTH, 0, 1));

        applyLfo (zone, voiceRecord);
        applyFilter (zone, voiceRecord);
    }


    /**
     * Fill an envelope from the five stages which follow each other in the bit stream of a voice.
     *
     * @param envelope The envelope to fill
     * @param voiceRecord The voice record
     * @param offset The offset of the attack stage in the bit stream
     */
    private static void fillEnvelope (final IEnvelope envelope, final byte [] voiceRecord, final int offset)
    {
        envelope.setAttackTime (EmaxConstants.getEnvelopeAttackTime (EmaxConstants.readVoiceField (voiceRecord, offset, 5)));
        envelope.setHoldTime (EmaxConstants.getEnvelopeHoldTime (EmaxConstants.readVoiceField (voiceRecord, offset + 5, 5)));
        envelope.setDecayTime (EmaxConstants.getEnvelopeDecayTime (EmaxConstants.readVoiceField (voiceRecord, offset + 10, 5)));
        envelope.setSustainLevel (EmaxConstants.getEnvelopeSustainLevel (EmaxConstants.readVoiceField (voiceRecord, offset + 15, 5)));
        envelope.setReleaseTime (EmaxConstants.getEnvelopeDecayTime (EmaxConstants.readVoiceField (voiceRecord, offset + 20, 5)));
    }


    /**
     * Apply the LFO of a voice, which the sampler routes to the pitch, the level and the filter at
     * the same time; the model carries a vibrato and a tremolo, so only those two are converted.
     *
     * @param zone The zone to fill
     * @param voiceRecord The voice record
     */
    private static void applyLfo (final ISampleZone zone, final byte [] voiceRecord)
    {
        final double rate = EmaxConstants.getLfoRate (EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_LFO_RATE, 7));
        final double delay = EmaxConstants.getLfoDelayTime (EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_LFO_DELAY, 6));

        final int toPitch = EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_LFO_TO_PITCH, 4);
        if (toPitch > 0)
        {
            final ILfoModulator modulator = zone.getPitchLfoModulator ();
            modulator.setDepth (Math.clamp (toPitch * EmaxConstants.LFO_PITCH_CENTS_PER_STEP / IEnvelope.MAX_ENVELOPE_DEPTH, 0, 1));
            setLfo (modulator.getSource (), rate, delay);
        }

        final int toVolume = EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_LFO_TO_VOLUME, 4);
        if (toVolume > 0)
        {
            final ILfoModulator modulator = zone.getAmplitudeLfoModulator ();
            modulator.setDepth (Math.clamp (toVolume * EmaxConstants.LFO_VOLUME_DB_PER_STEP / ILfoModulator.MAX_VOLUME_DEPTH, 0, 1));
            setLfo (modulator.getSource (), rate, delay);
        }
    }


    /**
     * Set the rate and the delay of an LFO.
     *
     * @param lfo The LFO
     * @param rate The rate in Hertz
     * @param delay The delay in seconds
     */
    private static void setLfo (final ILfo lfo, final double rate, final double delay)
    {
        lfo.setRate (rate);
        lfo.setDelay (delay);
    }


    /**
     * Apply the low pass filter of a voice with its envelope, its keyboard tracking and its
     * velocity modulation.
     *
     * @param zone The zone to fill
     * @param voiceRecord The voice record
     */
    private static void applyFilter (final ISampleZone zone, final byte [] voiceRecord)
    {
        final int cutoff = EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_CUTOFF_BITS, 7);
        final int envelopeAmount = EmaxConstants.readSignedVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_ENV_AMOUNT, 7);
        final int tracking = EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_TRACKING, 4);
        final int resonance = EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_FILTER_RESONANCE, 7);
        // A filter which is fully open, does not resonate and is not modulated does nothing
        if (cutoff >= EmaxConstants.FILTER_CUTOFF_MAX && envelopeAmount == 0 && tracking == 0 && resonance == 0)
            return;

        // The model carries the resonance as 0 to 1 where 1 is 40 dB
        final IFilter filter = new DefaultFilter (FilterType.LOW_PASS, 4, EmaxConstants.getCutoffFrequency (cutoff), Math.clamp (EmaxConstants.getResonance (resonance) / IFilter.MAX_RESONANCE, 0, 1));
        filter.setCutoffKeyTracking (Math.clamp (tracking * EmaxConstants.FILTER_TRACKING_PER_STEP / 100.0, 0, 1));

        if (envelopeAmount != 0)
        {
            final IEnvelopeModulator modulator = filter.getCutoffEnvelopeModulator ();
            modulator.setDepth (Math.clamp (envelopeAmount * EmaxConstants.FILTER_ENV_CENTS_PER_STEP / IEnvelope.MAX_ENVELOPE_DEPTH, -1, 1));
            fillEnvelope (modulator.getSource (), voiceRecord, EmaxConstants.VOICE_FILTER_ATTACK);
        }

        final int velocityToCutoff = EmaxConstants.readVoiceField (voiceRecord, EmaxConstants.VOICE_VELOCITY_TO_CUTOFF, 4);
        if (velocityToCutoff > 0)
            filter.getCutoffVelocityModulator ().setDepth (Math.clamp (velocityToCutoff / 15.0, 0, 1));

        zone.setFilter (filter);
    }


    /**
     * Get the number of voice records of a preset. It is not stored: the voice table names the
     * voices which the key areas play, and the records of all of them follow the table.
     *
     * @param data The data which holds the bank
     * @param preset The absolute position of the preset record
     * @param numKeyAreas The number of key areas of the preset
     * @return The number of voices
     */
    private static int getNumVoices (final byte [] data, final int preset, final int numKeyAreas)
    {
        int numVoices = 0;
        for (int keyArea = 0; keyArea < numKeyAreas; keyArea++)
        {
            final int entry = preset + EmaxConstants.PRESET_VOICE_TABLE + keyArea * EmaxConstants.VOICE_TABLE_ENTRY_SIZE;
            for (final int field: new int []
            {
                EmaxConstants.VOICE_TABLE_PRIMARY,
                EmaxConstants.VOICE_TABLE_SECONDARY
            })
            {
                final int voice = data[entry + field] & 0xFF;
                if (voice != EmaxConstants.VOICE_NONE && voice + 1 > numVoices)
                    numVoices = voice + 1;
            }
        }
        return numVoices;
    }


    /**
     * Get the size of a preset record.
     *
     * @param numKeyAreas The number of key areas of the preset
     * @param numVoices The number of voices of the preset
     * @return The size in bytes
     */
    private static int getPresetSize (final int numKeyAreas, final int numVoices)
    {
        return EmaxConstants.PRESET_VOICE_TABLE + numKeyAreas * EmaxConstants.VOICE_TABLE_ENTRY_SIZE + numVoices * EmaxConstants.VOICE_SIZE;
    }


    /**
     * Get the position of a voice record inside a preset record.
     *
     * @param numKeyAreas The number of key areas of the preset
     * @param voiceIndex The index of the voice
     * @return The offset relative to the start of the preset record
     */
    private static int getVoiceOffset (final int numKeyAreas, final int voiceIndex)
    {
        return EmaxConstants.PRESET_VOICE_TABLE + numKeyAreas * EmaxConstants.VOICE_TABLE_ENTRY_SIZE + voiceIndex * EmaxConstants.VOICE_SIZE;
    }


    /**
     * Read a 16 bit little-endian value.
     *
     * @param data The data to read from
     * @param offset The position of the value
     * @return The value or -1 if it does not fit into the data
     */
    private static int readInt16 (final byte [] data, final int offset)
    {
        if (offset < 0 || offset + 2 > data.length)
            return -1;
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
    }


    /**
     * Read a 32 bit little-endian value.
     *
     * @param data The data to read from
     * @param offset The position of the value
     * @return The value or -1 if it does not fit into the data
     */
    private static int readInt32 (final byte [] data, final int offset)
    {
        if (offset < 0 || offset + 4 > data.length)
            return -1;
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24;
    }


    /** One sample of the directory of a bank. */
    private static class Sample
    {
        EmaxModel          model;
        byte []            data;
        int                audioOffset;
        int                start;
        int                end;
        int                loopStart;
        int                loopEnd;
        int                flags;
        int                sampleRate;
        InMemorySampleData sampleData;


        /**
         * Get the audio of the sample, expanded from the companded bytes of the bank. The presets
         * of a bank reference the same samples over and over, so the audio is expanded once and
         * shared by all zones which play it instead of being copied for each of them.
         *
         * @return The sample data
         */
        InMemorySampleData getSampleData ()
        {
            if (this.sampleData == null)
            {
                final int numFrames = this.end - this.start;
                final byte [] pcm = new byte [numFrames * 2];
                if (this.model == EmaxModel.EMAX_2)
                    // The Emax II stores its audio as 16 bit little-endian, which is what the model
                    // uses as well
                    System.arraycopy (this.data, this.audioOffset + this.start * 2, pcm, 0, numFrames * 2);
                else
                    for (int i = 0; i < numFrames; i++)
                    {
                        final short value = EmaxConstants.expand (this.data[this.audioOffset + this.start + i]);
                        pcm[i * 2] = (byte) (value & 0xFF);
                        pcm[i * 2 + 1] = (byte) (value >> 8 & 0xFF);
                    }
                this.sampleData = new InMemorySampleData (new DefaultAudioMetadata (1, this.sampleRate, 16, numFrames), pcm);
            }
            return this.sampleData;
        }
    }
}
