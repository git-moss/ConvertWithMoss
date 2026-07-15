// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mc707;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
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
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
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

    // Partial oscillator fields, see MC707Creator.
    private static final int PARTIAL_OSC     = 0xDF;
    private static final int PARTIAL_STRIDE  = 0x7C;

    // Drum-kit key record fields, see MC707Creator.
    private static final int KEY_LEVEL       = 0x11;
    private static final int KEY_PITCH       = 0x12;
    private static final int KEY_SWITCH      = 0x1C;
    private static final int KEY_WAVE_GROUP  = 0x1D;
    private static final int KEY_WAVE_NUMBER = 0x20;

    // Sample-parameter record fields, see MC707Creator.
    private static final int SP_USED         = 0x10;
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
            if (ZenCoreUtil.readUnsigned32 (data, offset + SP_USED, false) != 1)
                continue;
            final MC707Sample sample = new MC707Sample ();
            sample.name = ZenCoreUtil.readName (data, offset, MC707Project.NAME_LENGTH);
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
            final int words = (int) ZenCoreUtil.readUnsigned32 (data, chunk + 0x0C, false);
            final int pcmLength = Math.min (words * 2, usdaEnd - chunk - headerSize);
            if (pcmLength > 0)
            {
                final byte [] pcm = new byte [pcmLength];
                System.arraycopy (data, chunk + headerSize, pcm, 0, pcmLength);
                samples.get (usedSlots.get (i)).pcm = pcm;
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
            final int oscOffset = offset + PARTIAL_OSC + partial * PARTIAL_STRIDE;
            final int waveGroup = data[oscOffset];
            final int waveNumber = ZenCoreUtil.readUnsigned16 (data, oscOffset + 3, false);
            if (waveGroup == 2)
            {
                // The partial plays a user sample, addressed by its 1-based table slot.
                final MC707Sample sample = samples.get (Integer.valueOf (waveNumber - 1));
                if (sample == null)
                    continue;
                group.addSampleZone (createZone (sample, 0, 127, sample.rootKey, sample.level));
                signature.append ('/').append (waveNumber);
                continue;
            }

            // The partial plays a multi-sample: expand the key-map record into zones.
            if (waveGroup == 3 && waveNumber >= 1 && waveNumber <= MC707Project.NUM_MULTISAMPLE_MAPS)
                readMultisampleMap (project, waveNumber - 1, samples, group, signature);
        }
        this.addSource (file, name, group, signature.toString (), signatures, results);
    }


    /**
     * Turn a multi-sample key-map record into key-ranged zones (a zone per run of one sample).
     *
     * @param project The project
     * @param mapSlot The 0-based map slot
     * @param samples The sample pool by slot
     * @param group Where to add the zones
     * @param signature The content signature to extend
     */
    private static void readMultisampleMap (final MC707Project project, final int mapSlot, final Map<Integer, MC707Sample> samples, final IGroup group, final StringBuilder signature)
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
                    group.addSampleZone (createZone (sample, runStart, key - 1, sample.rootKey, runLevel));
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
        for (int keyIndex = 0; keyIndex <= MC707Project.KIT_NUM_KEYS; keyIndex++)
        {
            final int key = MC707Project.KIT_BASE_KEY + keyIndex;
            int sampleSlot = -1;
            int pitch = 0;
            int level = 0;
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
                    }
                }
            }

            final boolean continuesRun = sampleSlot >= 0 && sampleSlot == runSample && pitch == runPitch + key - runStartKey && level == runLevel;
            if (!continuesRun)
            {
                if (runSample >= 0)
                {
                    // The written pitch is 0x3C + (key - root), so the root of the merged zone is
                    // the key at which the pitch field crosses its center.
                    final int rootKey = Math.clamp (runStartKey - (runPitch - 0x3CL), 0, 127);
                    group.addSampleZone (createZone (samples.get (Integer.valueOf (runSample)), runStartKey, key - 1, rootKey, runLevel));
                    signature.append ('/').append (runStartKey).append (':').append (runSample).append (':').append (runPitch).append (':').append (runLevel);
                }
                runStartKey = key;
                runSample = sampleSlot;
                runPitch = pitch;
                runLevel = level;
            }
        }
        this.addSource (file, name, group, signature.toString (), signatures, results);
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
        final int frames = sample.pcm.length / 4;
        zone.setSampleData (new InMemorySampleData (new DefaultAudioMetadata (2, SAMPLE_RATE, 16, frames), sample.pcm));
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
