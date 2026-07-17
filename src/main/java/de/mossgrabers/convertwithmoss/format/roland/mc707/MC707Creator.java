// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mc707;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
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
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreUtil;


/**
 * Creator for Roland MC-707 / MC-101 project files (<i>.mpj</i>). The written project is the
 * device's own init project with the converted sounds placed in the project's user banks: a
 * single-zone source becomes a <b>user tone</b> whose first partial plays the sample chromatically
 * (the pattern of Roland's own user-sample preset tones), a multi-zone source becomes a <b>user
 * drum kit</b> that maps each key of 21-108 to its zone's sample with the key transposition baked
 * into the per-key pitch (the pattern of Roland's own user-sample preset kits). The audio itself is
 * stored in the project like the device's sample import does: interleaved stereo 16-bit at 44.1
 * kHz.
 *
 * <p>
 * A kit key plays a single sample, so overlapping velocity layers cannot be represented - the
 * loudest layer is used. Optionally ({@link MC707CreatorUI}) a multi-zone source is written as a
 * <b>multisample tone</b> instead: a record of the project's multisample key-map table maps each
 * key to its zone's sample and the tone's first partial plays that map (wave group 3, the FANTOM
 * pattern on the shared tone record) - faithful for melodic sources, but no Roland-authored project
 * uses the map table, so this path is not verified on hardware. Copy the file to
 * <code>ROLAND/PROJECT</code> on the device's SD card.
 * </p>
 *
 * @author Jürgen Moßgraber
 */
public class MC707Creator extends AbstractCreator<MC707CreatorUI>
{
    /** The MC-707/MC-101 store user samples at 44.1 kHz / 16-bit (their native rate). */
    private static final DestinationAudioFormat DESTINATION_FORMAT   = new DestinationAudioFormat (new int []
    {
        16
    }, 44100, true);
    private static final int                    SAMPLE_RATE          = 44100;

    // Partial oscillator fields, relative to the partial base 0xDF + partial * 0x7C.
    private static final int                    PARTIAL_OSC          = 0xDF;
    @SuppressWarnings("unused")
    private static final int                    PARTIAL_STRIDE       = 0x7C;
    // 0 = ROM, 2 = user sample
    private static final int                    OSC_WAVE_GROUP       = 0;
    // 0x08 for ROM waves, 0 for user samples
    private static final int                    OSC_WAVE_BANK        = 1;
    private static final int                    OSC_WAVE_L           = 3;
    private static final int                    OSC_WAVE_R           = 5;
    // type * 0x100: 1=LPF, 2=BPF, 3=HPF
    private static final int                    OSC_FILTER_TYPE      = 0x0D;
    private static final int                    OSC_CUTOFF           = 0x11;
    private static final int                    OSC_RESONANCE        = 0x17;
    // Per-partial TVA envelope: 4 u16 times + 4 u16 levels (0-1023).
    private static final int                    TONE_TVA             = 0x37A;
    private static final int                    TONE_TVA_STRIDE      = 0x10;

    // Drum-kit key record fields.
    private static final int                    KEY_LEVEL            = 0x11;
    // 0x3C plays the sample at native pitch
    private static final int                    KEY_PITCH            = 0x12;
    private static final int                    KEY_MODE             = 0x18;
    // u32, 1-based sample slot
    private static final int                    KEY_WAVE_NUMBER      = 0x20;
    /** The wave-reference mode bytes of a kit key playing a user sample (device-verified). */
    private static final byte []                KEY_MODE_USER_SAMPLE =
    {
        0x00,
        0x01,
        0x01,
        0x00,
        0x01,
        0x02,
        0x08,
        0x00
    };

    // Sample-parameter record fields.
    // u32 1 = slot in use
    private static final int                    SP_USED              = 0x10;
    // 1 = untrimmed & unlooped
    private static final int                    SP_WHOLE             = 0x40;
    private static final int                    SP_LEVEL             = 0x41;
    private static final int                    SP_LOOP_SWITCH       = 0x44;
    private static final int                    SP_ORIGINAL_KEY      = 0x45;
    private static final int                    SP_START             = 0x48;
    private static final int                    SP_LOOP_START        = 0x4C;
    private static final int                    SP_END               = 0x50;

    /**
     * A device-authored user-sample tone record (from Roland's own preset projects, sound-design
     * fields reverted to the init tone): partial 1 is set up to play a user sample chromatically,
     * partials 2-4 are off. Name, wave number, filter and envelope are patched per source.
     */
    private static final byte []                TONE_TEMPLATE        = loadResource ("tone_sample.bin");

    private static final int                    MAX_NAME_LENGTH      = MC707Project.NAME_LENGTH - ".wav".length ();


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MC707Creator (final INotifier notifier)
    {
        super ("Roland MC-707/MC-101", "MC707", notifier, new MC707CreatorUI ());
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.writeProject (destinationFolder, List.of (multisampleSource), multisampleSource.getName ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (!multisampleSources.isEmpty ())
            this.writeProject (destinationFolder, multisampleSources, libraryName);
    }


    /**
     * Write one project file containing all sources in its user banks.
     *
     * @param destinationFolder Where to write the project
     * @param multisampleSources The sources to convert
     * @param name The project name
     * @throws IOException Could not write the project
     */
    private void writeProject (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String name) throws IOException
    {
        final File outputFile = this.createUniqueFilename (destinationFolder, createSafeFilename (name), "mpj");
        this.notifier.log ("IDS_MC707_WRITING_PROJECT", outputFile.getAbsolutePath (), Integer.toString (multisampleSources.size ()));

        final MC707Project project = MC707Project.createFromTemplate ();
        project.setProjectName (name);

        final List<MC707Sample> pool = new ArrayList<> ();
        final Map<Object, Integer> byContent = new HashMap<> ();
        final Set<String> usedNames = new HashSet<> ();
        final boolean asMultisamples = this.settingsConfiguration.isMultisampleTones ();
        int toneSlot = 0;
        int kitSlot = 0;

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            // The device's fixed sample rate - scale all loop/start/end positions to it.
            recalculateSamplePositions (multisampleSource, SAMPLE_RATE);

            final List<ISampleZone> zones = new ArrayList<> ();
            for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
                zones.addAll (group.getSampleZones ());
            if (zones.isEmpty ())
                continue;

            if (zones.size () > 1 && !asMultisamples)
            {
                if (kitSlot >= MC707Project.NUM_USER_KITS)
                {
                    this.notifier.logError ("IDS_MC707_TOO_MANY_KITS", multisampleSource.getName ());
                    continue;
                }
                if (this.writeKit (project, kitSlot, multisampleSource, zones, pool, byContent, usedNames))
                    kitSlot++;
            }
            else
            {
                if (toneSlot >= MC707Project.NUM_USER_TONES)
                {
                    this.notifier.logError ("IDS_MC707_TOO_MANY_TONES", multisampleSource.getName ());
                    continue;
                }
                final boolean written = zones.size () == 1 ? this.writeTone (project, toneSlot, multisampleSource, zones.get (0), pool, byContent, usedNames) : this.writeMultisampleTone (project, toneSlot, multisampleSource, zones, pool, byContent, usedNames);
                if (written)
                    toneSlot++;
            }
        }

        writeSampleParameters (project, pool);
        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (outputFile.toPath ())))
        {
            out.write (project.buildWithUserSampleData (buildUserSampleData (pool)));
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Write a single-zone source as a user tone: partial 1 plays the sample chromatically around
     * the sample's original key, with the source's filter and amplitude envelope.
     *
     * @param project The project to patch
     * @param slot The user tone slot
     * @param multisampleSource The source
     * @param zone The single zone
     * @param pool The project's sample pool
     * @param byContent Already added samples for re-use
     * @param usedNames The sample names used so far
     * @return True if the tone was written
     * @throws IOException Could not convert the sample
     */
    private boolean writeTone (final MC707Project project, final int slot, final IMultisampleSource multisampleSource, final ISampleZone zone, final List<MC707Sample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        final int rootKey = Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), 0, 127);
        final int sampleSlot = this.addSample (zone, rootKey, pool, byContent, usedNames);
        if (sampleSlot < 0)
            return false;

        final byte [] toneRecord = TONE_TEMPLATE.clone ();
        System.arraycopy (ZenCoreUtil.padName (multisampleSource.getName (), MC707Project.NAME_LENGTH), 0, toneRecord, 0, MC707Project.NAME_LENGTH);

        // Partial 1 plays the user sample (wave group 2); partials 2-4 stay off as in the template.
        toneRecord[PARTIAL_OSC + OSC_WAVE_GROUP] = 2;
        toneRecord[PARTIAL_OSC + OSC_WAVE_BANK] = 0;
        putU16 (toneRecord, PARTIAL_OSC + OSC_WAVE_L, sampleSlot + 1);
        putU16 (toneRecord, PARTIAL_OSC + OSC_WAVE_R, 0);

        writeToneFilter (toneRecord, zone);
        writeToneEnvelope (toneRecord, zone);

        System.arraycopy (toneRecord, 0, project.getData (), project.getUserToneOffset (slot), MC707Project.TONE_SIZE);
        return true;
    }


    /**
     * Write a multi-zone source as a user tone playing a multi-sample: a record of the project's
     * multi-sample key-map table maps each key to its zone's sample and partial 1 plays that map
     * (wave group 3, the pattern of the FANTOM whose tone record the MC shares). Overlapping
     * velocity layers collapse to the loudest layer (the map holds one sample per key). No
     * Roland-authored project uses the map table, so this path is not verified on hardware.
     *
     * @param project The project to patch
     * @param slot The user tone slot (also used as the map slot)
     * @param multisampleSource The source
     * @param zones All zones of the source
     * @param pool The project's sample pool
     * @param byContent Already added samples for re-use
     * @param usedNames The sample names used so far
     * @return True if the tone was written
     * @throws IOException Could not convert the samples
     */
    private boolean writeMultisampleTone (final MC707Project project, final int slot, final IMultisampleSource multisampleSource, final List<ISampleZone> zones, final List<MC707Sample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        final byte [] data = project.getData ();
        final int mapOffset = project.getMultisampleMapOffset (slot);
        System.arraycopy (ZenCoreUtil.padName (multisampleSource.getName (), MC707Project.NAME_LENGTH), 0, data, mapOffset, MC707Project.NAME_LENGTH);

        boolean hasLayers = false;
        boolean hasKeys = false;
        final Map<ISampleZone, Integer> sampleSlots = new HashMap<> ();
        for (int key = 0; key < 128; key++)
        {
            ISampleZone best = null;
            int covering = 0;
            for (final ISampleZone zone: zones)
                if (zone.getKeyLow () <= key && key <= zone.getKeyHigh ())
                {
                    covering++;
                    if (best == null || zone.getVelocityHigh () > best.getVelocityHigh ())
                        best = zone;
                }
            if (covering > 1)
                hasLayers = true;
            if (best == null)
                continue;

            Integer sampleSlot = sampleSlots.get (best);
            if (sampleSlot == null)
            {
                final int rootKey = Math.clamp (best.getKeyRoot () < 0 ? best.getKeyLow () : best.getKeyRoot (), 0, 127);
                sampleSlot = Integer.valueOf (this.addSample (best, rootKey, pool, byContent, usedNames));
                sampleSlots.put (best, sampleSlot);
            }
            if (sampleSlot.intValue () < 0)
                continue;

            final int entryOffset = mapOffset + MC707Project.NAME_LENGTH + key * 4;
            putU16 (data, entryOffset, sampleSlot.intValue () + 1);
            data[entryOffset + 2] = (byte) levelFromGain (best.getGain ());
            hasKeys = true;
        }
        if (!hasKeys)
            return false;
        if (hasLayers)
            this.notifier.log ("IDS_MC707_LAYERS_REDUCED", multisampleSource.getName ());

        final byte [] toneRecord = TONE_TEMPLATE.clone ();
        System.arraycopy (ZenCoreUtil.padName (multisampleSource.getName (), MC707Project.NAME_LENGTH), 0, toneRecord, 0, MC707Project.NAME_LENGTH);

        // Partial 1 plays the multisample map (wave group 3); the wave-bank byte stays 0x08 and
        // Wave R = Wave L as in the FANTOM's multisample tones (on the FANTOM R = 0 plays mono).
        toneRecord[PARTIAL_OSC + OSC_WAVE_GROUP] = 3;
        toneRecord[PARTIAL_OSC + OSC_WAVE_BANK] = 0x08;
        putU16 (toneRecord, PARTIAL_OSC + OSC_WAVE_L, slot + 1);
        putU16 (toneRecord, PARTIAL_OSC + OSC_WAVE_R, slot + 1);

        final ISampleZone representative = zones.get (0);
        writeToneFilter (toneRecord, representative);
        writeToneEnvelope (toneRecord, representative);

        System.arraycopy (toneRecord, 0, project.getData (), project.getUserToneOffset (slot), MC707Project.TONE_SIZE);
        return true;
    }


    /**
     * Write a multi-zone source as a user drum kit: every key of 21-108 covered by a zone plays
     * that zone's sample, transposed by the key's distance to the zone's root key. Overlapping
     * velocity layers collapse to the loudest layer (a kit key holds one sample).
     *
     * @param project The project to patch
     * @param slot The user kit slot
     * @param multisampleSource The source
     * @param zones All zones of the source
     * @param pool The project's sample pool
     * @param byContent Already added samples for re-use
     * @param usedNames The sample names used so far
     * @return True if the kit was written
     * @throws IOException Could not convert the samples
     */
    private boolean writeKit (final MC707Project project, final int slot, final IMultisampleSource multisampleSource, final List<ISampleZone> zones, final List<MC707Sample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        final byte [] data = project.getData ();
        System.arraycopy (ZenCoreUtil.padName (multisampleSource.getName (), MC707Project.NAME_LENGTH), 0, data, project.getUserKitOffset (slot), MC707Project.NAME_LENGTH);

        boolean hasLayers = false;
        boolean outOfRange = false;
        boolean hasKeys = false;
        final Map<ISampleZone, Integer> sampleSlots = new HashMap<> ();
        for (int keyIndex = 0; keyIndex < MC707Project.KIT_NUM_KEYS; keyIndex++)
        {
            final int key = MC707Project.KIT_BASE_KEY + keyIndex;

            // A kit key plays one sample: of all zones covering the key take the loudest
            // velocity layer (the one reaching up to the highest velocity).
            ISampleZone best = null;
            int covering = 0;
            for (final ISampleZone zone: zones)
                if (zone.getKeyLow () <= key && key <= zone.getKeyHigh ())
                {
                    covering++;
                    if (best == null || zone.getVelocityHigh () > best.getVelocityHigh ())
                        best = zone;
                }
            if (covering > 1)
                hasLayers = true;
            if (best == null)
                continue;

            Integer sampleSlot = sampleSlots.get (best);
            if (sampleSlot == null)
            {
                // Kit samples are stored with the original key at the center 60 and the zone
                // transposition baked into the per-key pitch below, which is correct whether the
                // engine plays the key pitch absolutely or relative to the sample's original key.
                sampleSlot = Integer.valueOf (this.addSample (best, 60, pool, byContent, usedNames));
                sampleSlots.put (best, sampleSlot);
            }
            if (sampleSlot.intValue () < 0)
                continue;

            final int rootKey = Math.clamp (best.getKeyRoot () < 0 ? best.getKeyLow () : best.getKeyRoot (), 0, 127);
            final int keyOffset = project.getUserKitKeyOffset (slot, keyIndex);
            System.arraycopy (ZenCoreUtil.padName (pool.get (sampleSlot.intValue ()).name, MC707Project.NAME_LENGTH), 0, data, keyOffset, MC707Project.NAME_LENGTH);
            data[keyOffset + KEY_LEVEL] = (byte) levelFromGain (best.getGain ());
            data[keyOffset + KEY_PITCH] = (byte) Math.clamp (0x3CL + key - rootKey, 0, 127);
            System.arraycopy (KEY_MODE_USER_SAMPLE, 0, data, keyOffset + KEY_MODE, KEY_MODE_USER_SAMPLE.length);
            ZenCoreUtil.writeUnsigned32 (data, keyOffset + KEY_WAVE_NUMBER, sampleSlot.intValue () + 1L, false);
            hasKeys = true;
        }

        for (final ISampleZone zone: zones)
            if (zone.getKeyHigh () < MC707Project.KIT_BASE_KEY || zone.getKeyLow () >= MC707Project.KIT_BASE_KEY + MC707Project.KIT_NUM_KEYS)
                outOfRange = true;

        if (hasLayers)
            this.notifier.log ("IDS_MC707_LAYERS_REDUCED", multisampleSource.getName ());
        if (outOfRange)
            this.notifier.log ("IDS_MC707_ZONE_OUT_OF_RANGE", multisampleSource.getName ());
        return hasKeys;
    }


    /**
     * Convert a zone's audio to the project's sample pool, re-using an already-added identical
     * sample. The audio is stored the way the device's own import stores it: interleaved stereo
     * 16-bit at 44.1 kHz (mono sources are duplicated to both channels).
     *
     * @param zone The zone to add
     * @param rootKey The original key to store in the sample parameters
     * @param pool The sample pool
     * @param byContent Already added samples for re-use
     * @param usedNames The sample names used so far
     * @return The 0-based sample slot or -1 if the sample could not be added
     * @throws IOException Could not convert the sample
     */
    private int addSample (final ISampleZone zone, final int rootKey, final List<MC707Sample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            return -1;
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_FORMAT);
        final int channels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (channels < 1 || channels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (channels), zone.getName ());
            return -1;
        }
        final byte [] pcm = toInterleavedStereo (waveFile.getDataChunk ().getData (), channels);
        final int frames = pcm.length / 4;
        if (frames <= 0)
            return -1;

        final int start = Math.clamp (zone.getStart (), 0, frames - 1);
        int playEnd = zone.getStop () > 0 ? Math.min (zone.getStop (), frames) : frames;
        final List<ISampleLoop> loops = zone.getLoops ();
        final boolean hasLoop = !loops.isEmpty ();
        int loopStart = 0;
        if (hasLoop)
        {
            final ISampleLoop loop = loops.get (0);
            loopStart = Math.clamp (loop.getStart (), 0, frames - 1);
            if (loop.getEnd () > loopStart)
                playEnd = Math.min (loop.getEnd (), frames);
        }
        final int end = Math.max (start, playEnd - 1); // the end point is the last played frame
        final int level = levelFromGain (zone.getGain ());

        // Re-use an identical sample, e.g. when one sample is mapped to several key ranges.
        final Object contentKey = List.of (ByteBuffer.wrap (pcm), Integer.valueOf (start), Integer.valueOf (loopStart), Integer.valueOf (end), Boolean.valueOf (hasLoop), Integer.valueOf (rootKey), Integer.valueOf (level));
        final Integer existing = byContent.get (contentKey);
        if (existing != null)
            return existing.intValue ();

        if (pool.size () >= MC707Project.NUM_SAMPLE_SLOTS)
        {
            this.notifier.logError ("IDS_MC707_TOO_MANY_SAMPLES", zone.getName ());
            return -1;
        }

        final MC707Sample sample = new MC707Sample ();
        sample.name = createUniqueName (zone.getName (), usedNames);
        sample.rootKey = rootKey;
        sample.hasLoop = hasLoop;
        sample.level = level;
        sample.start = start;
        sample.loopStart = loopStart;
        sample.end = end;
        sample.pcm = pcm;
        pool.add (sample);

        final int slot = pool.size () - 1;
        byContent.put (contentKey, Integer.valueOf (slot));
        return slot;
    }


    /**
     * Fill the sample-parameter table and build the USDa payload (the <i>SMPh</i> header plus one
     * <i>SMPd</i> chunk per sample) for the pool.
     *
     * @param pool The sample pool
     * @return The USDa payload
     */
    private static byte [] buildUserSampleData (final List<MC707Sample> pool)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.writeBytes (new byte []
        {
            'S',
            'M',
            'P',
            'h'
        });
        final byte [] header = new byte [28];
        header[0] = 0x20; // header size
        header[4] = 2; // version
        ZenCoreUtil.writeUnsigned32 (header, 8, pool.size (), false);
        out.writeBytes (header);

        for (int i = 0; i < pool.size (); i++)
        {
            final MC707Sample sample = pool.get (i);
            final int words = sample.pcm.length / 2;
            // The stored size is the PCM rounded up to the device's 1 KB allocation blocks with a
            // 128-frame margin (matches every Roland-authored chunk).
            final int dataSize = (2 * words + 256 + 1023) / 1024 * 1024;

            final byte [] chunk = new byte [0x30 + dataSize];
            chunk[0] = 'S';
            chunk[1] = 'M';
            chunk[2] = 'P';
            chunk[3] = 'd';
            chunk[4] = 0x30; // header size
            putU16 (chunk, 6, (words + 32767) / 32768);
            ZenCoreUtil.writeUnsigned32 (chunk, 8, dataSize, false);
            ZenCoreUtil.writeUnsigned32 (chunk, 0x0C, words, false);
            System.arraycopy (ZenCoreUtil.padNameZero (sample.name + ".wav", MC707Project.NAME_LENGTH), 0, chunk, 0x10, MC707Project.NAME_LENGTH);
            ZenCoreUtil.writeUnsigned32 (chunk, 0x20, 0x8000L + i, false);
            ZenCoreUtil.writeUnsigned32 (chunk, 0x24, SAMPLE_RATE, false);
            // 0x28: uninitialized memory in Roland-written files, left at 0 here
            System.arraycopy (sample.pcm, 0, chunk, 0x30, sample.pcm.length);
            out.writeBytes (chunk);
        }
        return out.toByteArray ();
    }


    /**
     * Fill the pool's records of the sample-parameter table.
     *
     * @param project The project to patch
     * @param pool The sample pool
     */
    private static void writeSampleParameters (final MC707Project project, final List<MC707Sample> pool)
    {
        final byte [] data = project.getData ();
        for (int i = 0; i < pool.size (); i++)
        {
            final MC707Sample sample = pool.get (i);
            final int offset = project.getSampleParamOffset (i);
            System.arraycopy (ZenCoreUtil.padName (sample.name, MC707Project.NAME_LENGTH), 0, data, offset, MC707Project.NAME_LENGTH);
            ZenCoreUtil.writeUnsigned32 (data, offset + SP_USED, 1, false);
            final boolean whole = !sample.hasLoop && sample.start == 0 && sample.end == sample.pcm.length / 4 - 1;
            data[offset + SP_WHOLE] = (byte) (whole ? 1 : 0);
            data[offset + SP_LEVEL] = (byte) sample.level;
            data[offset + SP_LOOP_SWITCH] = (byte) (sample.hasLoop ? 1 : 0);
            data[offset + SP_ORIGINAL_KEY] = (byte) sample.rootKey;
            ZenCoreUtil.writeUnsigned32 (data, offset + SP_START, sample.start, false);
            ZenCoreUtil.writeUnsigned32 (data, offset + SP_LOOP_START, sample.hasLoop ? sample.loopStart : 0, false);
            ZenCoreUtil.writeUnsigned32 (data, offset + SP_END, sample.end, false);
        }
    }


    /**
     * Write the source's filter into partial 1 of the tone record.
     *
     * @param toneRecord The tone record
     * @param zone The zone from which to get the filter
     */
    private static void writeToneFilter (final byte [] toneRecord, final ISampleZone zone)
    {
        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isEmpty ())
            return;
        final IFilter filter = optFilter.get ();
        final int filterType = switch (filter.getType ())
        {
            case LOW_PASS -> 1;
            case BAND_PASS -> 2;
            case HIGH_PASS -> 3;
            default -> 0;
        };
        if (filterType == 0)
            return;
        putU16 (toneRecord, PARTIAL_OSC + OSC_FILTER_TYPE, filterType * 0x100);
        putU16 (toneRecord, PARTIAL_OSC + OSC_CUTOFF, Math.clamp ((int) Math.round (MathUtils.normalizeCutoff (filter.getCutoff ()) * 1023.0), 0, 1023));
        putU16 (toneRecord, PARTIAL_OSC + OSC_RESONANCE, Math.clamp ((int) Math.round (filter.getResonance () * 1023.0), 0, 1023));
    }


    /**
     * Write the source's amplitude envelope into the TVA envelopes of all 4 partials
     * (Roland-authored tones carry identical values in all four blocks).
     *
     * @param toneRecord The tone record
     * @param zone The zone from which to get the envelope
     */
    private static void writeToneEnvelope (final byte [] toneRecord, final ISampleZone zone)
    {
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        if (envelope == null)
            return;

        final int attack = timeToValue (envelope.getAttackTime ());
        final double holdTime = envelope.getHoldTime ();
        final int hold = holdTime > 0 ? timeToValue (holdTime) : 0;
        // Same engine family as the FANTOM: an instant TVA decay stage kills the voice, so keep an
        // inaudibly fast floor (see the ZEN-Core creator).
        final int decay = Math.max (8, timeToValue (envelope.getDecayTime ()));
        final double releaseTime = envelope.getReleaseTime ();
        final int release = releaseTime >= 0 ? timeToValue (releaseTime) : 150;
        final double holdLevelValue = envelope.getHoldLevel ();
        final int holdLevel = holdLevelValue < 0 ? 1023 : Math.clamp ((int) Math.round (holdLevelValue * 1023.0), 0, 1023);
        final double sustainValue = envelope.getSustainLevel ();
        final int sustain = sustainValue < 0 ? 1023 : Math.clamp ((int) Math.round (sustainValue * 1023.0), 0, 1023);

        for (int partial = 0; partial < 4; partial++)
        {
            final int offset = TONE_TVA + partial * TONE_TVA_STRIDE;
            putU16 (toneRecord, offset, attack);
            putU16 (toneRecord, offset + 2, hold);
            putU16 (toneRecord, offset + 4, decay);
            putU16 (toneRecord, offset + 6, release);
            putU16 (toneRecord, offset + 8, 1023); // L1 = peak
            putU16 (toneRecord, offset + 10, holdLevel);
            putU16 (toneRecord, offset + 12, sustain); // L3 = sustain
            putU16 (toneRecord, offset + 14, 0); // L4 = silence
        }
    }


    /**
     * Approximate the ZEN-Core envelope time value (0-1023) from a time in seconds (the same
     * calibrated log2 curve as the ZEN-Core creator).
     *
     * @param seconds The time in seconds
     * @return The 0-1023 time value
     */
    private static int timeToValue (final double seconds)
    {
        if (seconds <= 0)
            return 0;
        return Math.clamp ((int) Math.round (1023 + 168 * Math.log (seconds / 20.0) / Math.log (2)), 0, 1023);
    }


    private static int levelFromGain (final double gainDb)
    {
        return Math.clamp ((int) Math.round (Math.pow (10, gainDb / 20.0) * 127.0), 0, 127);
    }


    /**
     * Interleave mono PCM to stereo; stereo input is returned as-is. The device stores every user
     * sample as interleaved stereo.
     *
     * @param pcm The 16-bit little-endian PCM
     * @param channels The number of channels (1 or 2)
     * @return Interleaved stereo PCM
     */
    private static byte [] toInterleavedStereo (final byte [] pcm, final int channels)
    {
        if (channels == 2)
            return pcm;
        final int frames = pcm.length / 2;
        final byte [] stereo = new byte [frames * 4];
        for (int i = 0; i < frames; i++)
        {
            stereo[i * 4] = pcm[i * 2];
            stereo[i * 4 + 1] = pcm[i * 2 + 1];
            stereo[i * 4 + 2] = pcm[i * 2];
            stereo[i * 4 + 3] = pcm[i * 2 + 1];
        }
        return stereo;
    }


    private static String createUniqueName (final String zoneName, final Set<String> usedNames)
    {
        String name = zoneName == null ? "Sample" : zoneName.trim ();
        if (name.isEmpty ())
            name = "Sample";
        if (name.length () > MAX_NAME_LENGTH)
            name = name.substring (0, MAX_NAME_LENGTH);
        int counter = 1;
        while (!usedNames.add (name))
        {
            counter++;
            final String suffix = Integer.toString (counter);
            name = name.substring (0, Math.min (name.length (), MAX_NAME_LENGTH - suffix.length ())) + suffix;
        }
        return name;
    }


    private static void putU16 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    private static byte [] loadResource (final String resourceName)
    {
        try (final InputStream in = MC707Creator.class.getResourceAsStream (resourceName))
        {
            if (in == null)
                throw new IllegalStateException ("Missing MC-707 template resource: " + resourceName);
            final ByteArrayOutputStream out = new ByteArrayOutputStream ();
            in.transferTo (out);
            return out.toByteArray ();
        }
        catch (final IOException ex)
        {
            throw new IllegalStateException ("Could not read MC-707 template resource: " + resourceName, ex);
        }
    }
}
