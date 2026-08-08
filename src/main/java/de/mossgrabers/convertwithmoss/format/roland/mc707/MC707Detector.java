// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mc707;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
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
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreUtil;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Roland MC-707 / MC-101 project files (<i>.mpj</i>) and extracts every tone and drum kit
 * that plays the project's own user samples - from the user banks as well as from the per-clip
 * sound banks (the audio of ROM-wave sounds is not present in the file and cannot be converted).
 * Files written by {@link MC707Creator} round-trip through this detector.
 *
 * @author Jürgen Moßgraber
 */
public class MC707Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final int SAMPLE_RATE     = 44100;
    /** The audio of a SMPd chunk starts after a zero pre-pad which follows the chunk header. */
    private static final int PCM_PREPAD      = 64;

    // Partial oscillator block, see MC707Creator.
    private static final int PARTIAL_BLOCK   = 0xC8;
    private static final int PARTIAL_STRIDE  = 0x7C;
    private static final int OSC_LEVEL       = 0x00;
    private static final int OSC_COARSE_TUNE = 0x02;
    private static final int OSC_FINE_TUNE   = 0x03;
    private static final int OSC_PAN         = 0x06;
    private static final int OSC_WAVE_GROUP  = 0x17;
    private static final int OSC_WAVE_NUMBER = 0x1A;
    private static final int OSC_FILTER_TYPE = 0x24;
    private static final int OSC_CUTOFF      = 0x28;
    private static final int OSC_RESONANCE   = 0x2E;

    // Per-partial TVA envelope: 4 u16 times + 4 u16 levels (0-1023), see MC707Creator.
    private static final int TONE_TVA        = 0x37A;
    private static final int TONE_TVA_STRIDE = 0x10;

    // Drum-kit key record fields, see MC707Creator.
    private static final int KEY_LEVEL       = 0x11;
    private static final int KEY_PITCH       = 0x12;
    private static final int KEY_SWITCH      = 0x1C;
    private static final int KEY_WAVE_GROUP  = 0x1D;
    private static final int KEY_WAVE_NUMBER = 0x20;
    /** Per-key TVA envelope: 3 u16 times followed by 3 u16 levels (0-1023). */
    private static final int KEY_ENVELOPE    = 0xCC;

    // Sample-parameter record fields, see MC707Creator. A slot is in use when it has a name:
    // Roland's own projects additionally set a flag at 0x10 but device imports leave it at zero,
    // and 0x40 only tells whether the sample is untrimmed.
    private static final int SP_LEVEL        = 0x41;
    private static final int SP_LOOP_SWITCH  = 0x44;
    private static final int SP_ORIGINAL_KEY = 0x45;
    private static final int SP_START        = 0x48;
    private static final int SP_LOOP_START   = 0x4C;
    private static final int SP_END          = 0x50;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MC707Detector (final INotifier notifier)
    {
        super ("Roland MC-707/MC-101", "MC707", notifier, new MetadataSettingsUI ("MC707"), ".mpj");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            return this.readProject (sourceFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readProject (final File file) throws IOException
    {
        final MC707Project project = new MC707Project (Files.readAllBytes (file.toPath ()));
        final Map<Integer, MC707Sample> samples = readSamplePool (project);
        if (samples.isEmpty ())
        {
            this.notifier.logError ("IDS_MC707_NO_USER_SAMPLES", file.getName ());
            return Collections.emptyList ();
        }

        final List<IMultisampleSource> results = new ArrayList<> ();
        final Set<String> signatures = new HashSet<> ();

        // The user banks plus the per-clip banks; identical sounds stored several times (e.g. as a
        // clip sound and as the track's current sound) collapse via their content signature.
        for (int slot = 0; slot < MC707Project.NUM_USER_TONES; slot++)
            this.readTone (project, project.getUserToneOffset (slot), samples, file, signatures, results);
        for (int slot = 0; slot < MC707Project.NUM_CLIP_TONES; slot++)
            this.readTone (project, project.getClipToneOffset (slot), samples, file, signatures, results);
        for (int slot = 0; slot < MC707Project.NUM_USER_KITS; slot++)
            this.readKit (project, slot, true, samples, file, signatures, results);
        for (int slot = 0; slot < MC707Project.NUM_CLIP_KITS; slot++)
            this.readKit (project, slot, false, samples, file, signatures, results);

        if (results.isEmpty ())
        {
            this.notifier.logError ("IDS_MC707_NO_USER_SAMPLES", file.getName ());
            return Collections.emptyList ();
        }
        this.notifier.log ("IDS_MC707_READING_PROJECT", FileUtils.getNameWithoutType (file), Integer.toString (results.size ()));
        return results;
    }


    /**
     * Read the sample-parameter table and resolve the audio of the used slots from the USDa
     * <i>SMPd</i> chunks (chunks are stored in the order of the used slots).
     *
     * @param project The project
     * @return The samples by their 0-based table slot
     */
    private static Map<Integer, MC707Sample> readSamplePool (final MC707Project project)
    {
        final byte [] data = project.getData ();
        final List<Integer> usedSlots = new ArrayList<> ();
        final Map<Integer, MC707Sample> samples = new HashMap<> ();
        for (int slot = 0; slot < MC707Project.NUM_SAMPLE_SLOTS; slot++)
        {
            final int offset = project.getSampleParamOffset (slot);
            final String name = ZenCoreUtil.readName (data, offset, MC707Project.NAME_LENGTH);
            if (name.isEmpty ())
                continue;
            final MC707Sample sample = new MC707Sample ();
            sample.name = name;
            sample.level = data[offset + SP_LEVEL] & 0x7F;
            sample.hasLoop = data[offset + SP_LOOP_SWITCH] != 0;
            sample.rootKey = data[offset + SP_ORIGINAL_KEY] & 0x7F;
            sample.start = (int) ZenCoreUtil.readUnsigned32 (data, offset + SP_START, false);
            sample.loopStart = (int) ZenCoreUtil.readUnsigned32 (data, offset + SP_LOOP_START, false);
            sample.end = (int) ZenCoreUtil.readUnsigned32 (data, offset + SP_END, false);
            samples.put (Integer.valueOf (slot), sample);
            usedSlots.add (Integer.valueOf (slot));
        }

        // Walk the SMPd chunk chain.
        final int usdaOffset = project.getSectionOffset ("USDa");
        final int usdaEnd = usdaOffset + project.getSectionSize ("USDa");
        int chunk = usdaOffset + 0x10 + 0x20; // skip the block header and the SMPh header
        for (int i = 0; i < usedSlots.size () && chunk + 0x30 <= usdaEnd; i++)
        {
            if (data[chunk] != 'S' || data[chunk + 1] != 'M' || data[chunk + 2] != 'P' || data[chunk + 3] != 'd')
                break;
            final int headerSize = ZenCoreUtil.readUnsigned16 (data, chunk + 4, false);
            final int dataSize = (int) ZenCoreUtil.readUnsigned32 (data, chunk + 8, false);
            // The size field counts the bytes of ONE channel, not 16 bit words, and the audio
            // starts after the chunk header's zero pre-pad. Bit 15 of the sample tag marks a
            // stereo sample, whose two channels are stored interleaved behind each other.
            final int channelBytes = (int) ZenCoreUtil.readUnsigned32 (data, chunk + 0x0C, false);
            final int channels = (ZenCoreUtil.readUnsigned32 (data, chunk + 0x20, false) & 0x8000) == 0 ? 1 : 2;
            final int pcmOffset = chunk + headerSize + PCM_PREPAD;
            final int pcmLength = Math.min (channelBytes * channels, usdaEnd - pcmOffset);
            if (pcmLength > 0)
            {
                final byte [] pcm = new byte [pcmLength];
                System.arraycopy (data, pcmOffset, pcm, 0, pcmLength);
                final MC707Sample sample = samples.get (usedSlots.get (i));
                sample.pcm = pcm;
                sample.channels = channels;
            }
            chunk += headerSize + dataSize;
        }

        // Drop slots without audio, they cannot be converted.
        samples.values ().removeIf (sample -> sample.pcm == null);
        return samples;
    }


    /**
     * Read one tone record and add it as a multi-sample source if a partial plays a user sample.
     *
     * @param project The project
     * @param offset The file offset of the tone record
     * @param samples The sample pool by slot
     * @param file The source file
     * @param signatures The content signatures collected so far (for de-duplication)
     * @param results Where to add the source
     */
    private void readTone (final MC707Project project, final int offset, final Map<Integer, MC707Sample> samples, final File file, final Set<String> signatures, final List<IMultisampleSource> results)
    {
        final byte [] data = project.getData ();
        final String name = ZenCoreUtil.readName (data, offset, MC707Project.NAME_LENGTH);
        final IGroup group = new DefaultGroup ("Samples");
        final StringBuilder signature = new StringBuilder ("Tone:").append (name);
        for (int partial = 0; partial < 4; partial++)
        {
            final int partialOffset = offset + PARTIAL_BLOCK + partial * PARTIAL_STRIDE;
            final int waveGroup = data[partialOffset + OSC_WAVE_GROUP];
            final int waveNumber = ZenCoreUtil.readUnsigned16 (data, partialOffset + OSC_WAVE_NUMBER, false);
            final List<ISampleZone> zones = new ArrayList<> ();
            if (waveGroup == 2)
            {
                // The partial plays a user sample, addressed by its 1-based table slot.
                final MC707Sample sample = samples.get (Integer.valueOf (waveNumber - 1));
                if (sample == null)
                    continue;
                zones.add (createZone (sample, 0, 127, sample.rootKey, sample.level));
                signature.append ('/').append (waveNumber);
            }
            // The partial plays a multi-sample: expand the key-map record into zones.
            else if (waveGroup == 3 && waveNumber >= 1 && waveNumber <= MC707Project.NUM_MULTISAMPLE_MAPS)
                readMultisampleMap (project, waveNumber - 1, samples, zones, signature);

            if (zones.isEmpty ())
                continue;
            applyPartialShaping (data, offset, partial, zones, signature);
            for (final ISampleZone zone: zones)
                group.addSampleZone (zone);
        }
        this.addSource (file, name, group, signature.toString (), signatures, results);
    }


    /**
     * Apply the parameters of one partial to the zones it plays - the inverse of the mapping in
     * {@link MC707Creator}. A tone layers up to four partials which each play their own wave with
     * their own level, tuning, panning, TVF filter and TVA amplitude envelope; several partials
     * playing the same sample transposed and panned apart is a common MC-707 patch.
     *
     * @param data The project data
     * @param offset The file offset of the tone record
     * @param partial The index of the partial (0-3)
     * @param zones The zones which the partial plays
     * @param signature The content signature to extend
     */
    private static void applyPartialShaping (final byte [] data, final int offset, final int partial, final List<ISampleZone> zones, final StringBuilder signature)
    {
        final int partialOffset = offset + PARTIAL_BLOCK + partial * PARTIAL_STRIDE;
        final int level = ZenCoreUtil.readUnsigned16 (data, partialOffset + OSC_LEVEL, false);
        final int coarseTune = data[partialOffset + OSC_COARSE_TUNE];
        final int fineTune = data[partialOffset + OSC_FINE_TUNE];
        final int panning = data[partialOffset + OSC_PAN];
        // The partial level scales the level of the sample slot, both are linear 0-127 volumes.
        final double gain = MathUtils.valueToDb (Math.max (level, 1) / 127.0);
        signature.append ("/P").append (level).append (':').append (coarseTune).append (':').append (fineTune).append (':').append (panning);

        final int tvaOffset = offset + TONE_TVA + partial * TONE_TVA_STRIDE;
        final IEnvelope amplitudeEnvelope = new DefaultEnvelope ();
        amplitudeEnvelope.setAttackTime (ZenCoreUtil.valueToTime (ZenCoreUtil.readUnsigned16 (data, tvaOffset, false)));
        final int hold = ZenCoreUtil.readUnsigned16 (data, tvaOffset + 2, false);
        amplitudeEnvelope.setHoldTime (hold > 0 ? ZenCoreUtil.valueToTime (hold) : 0);
        amplitudeEnvelope.setDecayTime (ZenCoreUtil.valueToTime (ZenCoreUtil.readUnsigned16 (data, tvaOffset + 4, false)));
        amplitudeEnvelope.setReleaseTime (ZenCoreUtil.valueToTime (ZenCoreUtil.readUnsigned16 (data, tvaOffset + 6, false)));
        amplitudeEnvelope.setHoldLevel (ZenCoreUtil.readUnsigned16 (data, tvaOffset + 10, false) / 1023.0);
        amplitudeEnvelope.setSustainLevel (ZenCoreUtil.readUnsigned16 (data, tvaOffset + 12, false) / 1023.0);
        signature.append ("/E").append (ZenCoreUtil.readUnsigned16 (data, tvaOffset, false)).append (':').append (ZenCoreUtil.readUnsigned16 (data, tvaOffset + 12, false));

        final int filterType = ZenCoreUtil.readUnsigned16 (data, partialOffset + OSC_FILTER_TYPE, false) / 0x100;
        final int cutoff = ZenCoreUtil.readUnsigned16 (data, partialOffset + OSC_CUTOFF, false);
        final int resonance = ZenCoreUtil.readUnsigned16 (data, partialOffset + OSC_RESONANCE, false);
        // 0 = off, 1 = LPF, 2 = BPF, 3 = HPF, 4 = PKG (no model equivalent), 5/6 = LPF2/LPF3.
        final FilterType type = switch (filterType)
        {
            case 1, 5, 6 -> FilterType.LOW_PASS;
            case 2 -> FilterType.BAND_PASS;
            case 3 -> FilterType.HIGH_PASS;
            default -> null;
        };
        if (type != null)
            signature.append ("/F").append (filterType).append (':').append (cutoff).append (':').append (resonance);

        for (final ISampleZone zone: zones)
        {
            zone.setGain (zone.getGain () + gain);
            zone.setTuning (coarseTune + fineTune / 100.0);
            zone.setPanning (Math.clamp (panning / 64.0, -1, 1));
            zone.getAmplitudeEnvelopeModulator ().setSource (amplitudeEnvelope);
            if (type != null)
                zone.setFilter (new DefaultFilter (type, 4, MathUtils.denormalizeCutoff (cutoff / 1023.0), resonance / 1023.0));
        }
    }


    /**
     * Turn a multi-sample key-map record into key-ranged zones (a zone per run of one sample).
     *
     * @param project The project
     * @param mapSlot The 0-based map slot
     * @param samples The sample pool by slot
     * @param zones Where to add the zones
     * @param signature The content signature to extend
     */
    private static void readMultisampleMap (final MC707Project project, final int mapSlot, final Map<Integer, MC707Sample> samples, final List<ISampleZone> zones, final StringBuilder signature)
    {
        final byte [] data = project.getData ();
        final int mapOffset = project.getMultisampleMapOffset (mapSlot);
        signature.append ("/M").append (mapSlot);
        int runStart = -1;
        int runSample = -1;
        int runLevel = 0;
        for (int key = 0; key <= 128; key++)
        {
            int sampleSlot = -1;
            int level = 0;
            if (key < 128)
            {
                final int entryOffset = mapOffset + MC707Project.NAME_LENGTH + key * 4;
                final int number = ZenCoreUtil.readUnsigned16 (data, entryOffset, false);
                if (number > 0 && samples.containsKey (Integer.valueOf (number - 1)))
                {
                    sampleSlot = number - 1;
                    level = data[entryOffset + 2] & 0x7F;
                }
            }
            if (sampleSlot != runSample || level != runLevel)
            {
                if (runSample >= 0)
                {
                    final MC707Sample sample = samples.get (Integer.valueOf (runSample));
                    zones.add (createZone (sample, runStart, key - 1, sample.rootKey, runLevel));
                    signature.append ('/').append (runStart).append (':').append (runSample);
                }
                runStart = key;
                runSample = sampleSlot;
                runLevel = level;
            }
        }
    }


    /**
     * Read one drum kit and add it as a multi-sample source if keys play user samples. Runs of
     * neighboring keys playing the same sample with an ascending chromatic pitch merge back into
     * one key-ranged zone.
     *
     * @param project The project
     * @param slot The kit slot
     * @param userBank True for the user bank, false for the clip bank
     * @param samples The sample pool by slot
     * @param file The source file
     * @param signatures The content signatures collected so far (for de-duplication)
     * @param results Where to add the source
     */
    private void readKit (final MC707Project project, final int slot, final boolean userBank, final Map<Integer, MC707Sample> samples, final File file, final Set<String> signatures, final List<IMultisampleSource> results)
    {
        final byte [] data = project.getData ();
        final int kitOffset = userBank ? project.getUserKitOffset (slot) : project.getClipKitOffset (slot);
        String name = ZenCoreUtil.readName (data, kitOffset, MC707Project.NAME_LENGTH);
        if (!userBank)
        {
            // The clip record carries the name the device displays for the clip's sound; the
            // kit-common name is partly stale in Roland's own preset projects.
            final String clipName = ZenCoreUtil.readName (data, project.getClipRecordOffset (slot), MC707Project.NAME_LENGTH);
            if (!clipName.isEmpty ())
                name = clipName;
        }

        final IGroup group = new DefaultGroup ("Keys");
        final StringBuilder signature = new StringBuilder ("Kit:").append (name);
        int runStartKey = -1;
        int runSample = -1;
        int runPitch = 0;
        int runLevel = 0;
        int [] runEnvelope = null;
        for (int keyIndex = 0; keyIndex <= MC707Project.KIT_NUM_KEYS; keyIndex++)
        {
            final int key = MC707Project.KIT_BASE_KEY + keyIndex;
            int sampleSlot = -1;
            int pitch = 0;
            int level = 0;
            int [] envelope = null;
            if (keyIndex < MC707Project.KIT_NUM_KEYS)
            {
                final int keyOffset = userBank ? project.getUserKitKeyOffset (slot, keyIndex) : project.getClipKitKeyOffset (slot, keyIndex);
                if (data[keyOffset + KEY_SWITCH] == 1 && data[keyOffset + KEY_WAVE_GROUP] == 2)
                {
                    final int waveNumber = (int) ZenCoreUtil.readUnsigned32 (data, keyOffset + KEY_WAVE_NUMBER, false);
                    if (samples.containsKey (Integer.valueOf (waveNumber - 1)))
                    {
                        sampleSlot = waveNumber - 1;
                        pitch = data[keyOffset + KEY_PITCH] & 0x7F;
                        level = data[keyOffset + KEY_LEVEL] & 0x7F;
                        envelope = readKeyEnvelope (data, keyOffset);
                    }
                }
            }

            final boolean continuesRun = sampleSlot >= 0 && sampleSlot == runSample && pitch == runPitch + key - runStartKey && level == runLevel && Arrays.equals (envelope, runEnvelope);
            if (!continuesRun)
            {
                if (runSample >= 0)
                {
                    // The written pitch is 0x3C + (key - root), so the root of the merged zone is
                    // the key at which the pitch field crosses its center.
                    final int rootKey = Math.clamp (runStartKey - (runPitch - 0x3CL), 0, 127);
                    final ISampleZone zone = createZone (samples.get (Integer.valueOf (runSample)), runStartKey, key - 1, rootKey, runLevel);
                    applyKeyEnvelope (zone, runEnvelope);
                    group.addSampleZone (zone);
                    signature.append ('/').append (runStartKey).append (':').append (runSample).append (':').append (runPitch).append (':').append (runLevel).append (':').append (Arrays.toString (runEnvelope));
                }
                runStartKey = key;
                runSample = sampleSlot;
                runPitch = pitch;
                runLevel = level;
                runEnvelope = envelope;
            }
        }
        this.addSource (file, name, group, signature.toString (), signatures, results);
    }


    /**
     * Read a drum-kit key's TVA envelope: 3 times followed by 3 levels.
     *
     * @param data The project data
     * @param keyOffset The file offset of the key record
     * @return The 6 raw values
     */
    private static int [] readKeyEnvelope (final byte [] data, final int keyOffset)
    {
        final int [] values = new int [6];
        for (int i = 0; i < values.length; i++)
            values[i] = ZenCoreUtil.readUnsigned16 (data, keyOffset + KEY_ENVELOPE + i * 2, false);
        return values;
    }


    /**
     * Apply a drum-kit key's TVA envelope to a zone. The 3 stages are attack, decay and release;
     * the level reached after the decay stage is the sustain level.
     *
     * @param zone The zone to shape
     * @param values The 6 raw values or null if the key had none
     */
    private static void applyKeyEnvelope (final ISampleZone zone, final int [] values)
    {
        if (values == null)
            return;
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (ZenCoreUtil.valueToTime (values[0]));
        envelope.setDecayTime (ZenCoreUtil.valueToTime (values[1]));
        envelope.setReleaseTime (ZenCoreUtil.valueToTime (values[2]));
        envelope.setSustainLevel (values[4] / 1023.0);
        zone.getAmplitudeEnvelopeModulator ().setSource (envelope);
    }


    private void addSource (final File file, final String name, final IGroup group, final String signature, final Set<String> signatures, final List<IMultisampleSource> results)
    {
        if (group.getSampleZones ().isEmpty () || !signatures.add (signature))
            return;
        results.add (this.createMultisampleSource (file, name.isEmpty () ? FileUtils.getNameWithoutType (file) : name, List.of (group)));
    }


    private static ISampleZone createZone (final MC707Sample sample, final int keyLow, final int keyHigh, final int rootKey, final int level)
    {
        final ISampleZone zone = new DefaultSampleZone (sample.name, keyLow, keyHigh);
        // All positions are frame indices regardless of the channel count.
        final int frames = sample.pcm.length / (2 * sample.channels);
        zone.setSampleData (new InMemorySampleData (new DefaultAudioMetadata (sample.channels, SAMPLE_RATE, 16, frames), sample.pcm));
        zone.setKeyRoot (rootKey);
        zone.setStart (sample.start);
        zone.setStop (Math.min (sample.end + 1, frames));
        zone.setGain (MathUtils.valueToDb (Math.max (level, 1) / 127.0));
        if (sample.hasLoop && sample.end + 1 > sample.loopStart)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (sample.loopStart);
            loop.setEnd (Math.min (sample.end + 1, frames));
            zone.getLoops ().add (loop);
        }
        return zone;
    }
}
