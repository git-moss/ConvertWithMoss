// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulatorx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
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
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects E-mu Emulator X bank files (*.exb) and the sample files (*.ebl) of their sample pool.
 * Every preset of a bank becomes one multi-sample source. A preset is a list of voices; each voice
 * has a key and a velocity range, references its samples through one or more zones and carries the
 * tuning, volume, panning, filter and envelope settings for them. A voice usually holds a single
 * zone, so the keyboard map is built from many voices while velocity layers are built from the
 * zones; all zones which cover the same velocity range are collected into one group. The samples
 * are not stored in the bank but in a sibling folder named 'SamplePool' which holds one *.ebl file
 * per sample. A single *.ebl file is read as a multi-sample source with one zone, which makes the
 * sample pool of a bank usable on its own. The chunks of the format are versioned and several
 * generations exist, so their size is always taken from the chunk header. The layout was
 * reverse-engineered from 14 banks and the parameter ranges come from the Emulator X manuals, see
 * documentation/design/EMULATORX_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class EmulatorXDetector extends AbstractDetector<MetadataSettingsUI>
{
    /** Below this normalized cutoff a filter is considered to be closed. */
    private static final double MINIMUM_STATIC_CUTOFF = 0.01;


    /** The velocity range of a group of voices. */
    private record VelocityRange (int low, int high)
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EmulatorXDetector (final INotifier notifier)
    {
        super ("E-mu Emulator X", "EXB", notifier, new MetadataSettingsUI ("EXB"), EmulatorXConstants.BANK_ENDING, EmulatorXConstants.SAMPLE_ENDING);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final boolean isSampleFile = sourceFile.getName ().toLowerCase (Locale.US).endsWith (EmulatorXConstants.SAMPLE_ENDING);
        // The samples of a bank are read through the bank, not one by one
        if (isSampleFile && belongsToBank (sourceFile))
            return Collections.emptyList ();

        try
        {
            final byte [] data = Files.readAllBytes (sourceFile.toPath ());
            if (!EmulatorXConstants.hasTag (data, 0, EmulatorXConstants.FORM_MAGIC) || !EmulatorXConstants.hasTag (data, 8, EmulatorXConstants.FORM_TYPE))
            {
                this.notifier.logError (isSampleFile ? "IDS_EXB_NOT_A_SAMPLE" : "IDS_EXB_NOT_A_BANK", sourceFile.getName ());
                return Collections.emptyList ();
            }

            return isSampleFile ? this.parseSampleFile (sourceFile, data) : this.parseBank (sourceFile, data);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Test whether a sample file is part of the sample pool of a bank, which is the case if it sits
     * in a folder named 'SamplePool' which has a bank next to it.
     *
     * @param sampleFile The sample file
     * @return True if a bank references the file
     */
    private static boolean belongsToBank (final File sampleFile)
    {
        final File folder = sampleFile.getParentFile ();
        if (folder == null || !EmulatorXConstants.SAMPLE_POOL_FOLDER.equalsIgnoreCase (folder.getName ()))
            return false;
        final File parent = folder.getParentFile ();
        if (parent == null)
            return false;
        final File [] banks = parent.listFiles ( (dir, name) -> name.toLowerCase (Locale.US).endsWith (EmulatorXConstants.BANK_ENDING));
        return banks != null && banks.length > 0;
    }


    /**
     * Read a single sample file and wrap it into a multi-sample source with one zone.
     *
     * @param sourceFile The sample file
     * @param data The content of the file
     * @return The multi-sample source or an empty list if the file could not be read
     */
    private List<IMultisampleSource> parseSampleFile (final File sourceFile, final byte [] data)
    {
        final EmulatorXSampleFile sampleFile;
        try
        {
            sampleFile = EmulatorXSampleFile.read (data);
        }
        catch (final ParseException ex)
        {
            this.notifier.logError (ex.getMessage (), sourceFile.getName ());
            return Collections.emptyList ();
        }

        final String name = sampleFile.getName ().isBlank () ? FileUtils.getNameWithoutType (sourceFile) : sampleFile.getName ();
        final ISampleZone zone = new DefaultSampleZone (name, 0, 127);
        zone.setSampleData (createSampleData (sampleFile));
        zone.setStart (0);
        zone.setStop (sampleFile.getNumFrames ());
        zone.setKeyRoot (60);
        addLoop (zone, sampleFile);

        final IGroup group = new DefaultGroup ("Group");
        group.addSampleZone (zone);
        return List.of (this.createMultisampleSource (sourceFile, name, List.of (group)));
    }


    /**
     * Parse a bank and create one multi-sample source per preset.
     *
     * @param sourceFile The bank file
     * @param data The content of the bank
     * @return The multi-sample sources
     */
    private List<IMultisampleSource> parseBank (final File sourceFile, final byte [] data)
    {
        final int tocSize = (int) EmulatorXConstants.getU32BE (data, 16);
        final int tocEnd = EmulatorXConstants.TOC_OFFSET + tocSize;
        if (tocSize <= 0 || tocEnd > data.length)
        {
            this.notifier.logError ("IDS_EXB_NOT_A_BANK", sourceFile.getName ());
            return Collections.emptyList ();
        }

        final File samplePool = new File (sourceFile.getParentFile (), EmulatorXConstants.SAMPLE_POOL_FOLDER);
        final String bankName = FileUtils.getNameWithoutType (sourceFile);
        final Map<Integer, EmulatorXSampleFile> samples = new HashMap<> ();
        final List<EmulatorXChunk> presets = new ArrayList<> ();

        int missingSamples = 0;
        for (int position = EmulatorXConstants.TOC_OFFSET; position + EmulatorXConstants.TOC_ENTRY_SIZE <= tocEnd; position += EmulatorXConstants.TOC_ENTRY_SIZE)
        {
            final int size = (int) EmulatorXConstants.getU32BE (data, position + 4);
            final int offset = (int) EmulatorXConstants.getU32BE (data, position + 8);
            final int index = EmulatorXConstants.getU16BE (data, position + 12);
            if (size < 0 || offset < 0 || offset + EmulatorXConstants.CHUNK_OVERHEAD + size > data.length)
                continue;

            // The payload of a chunk which the contents points at starts behind its 16 bit index
            if (EmulatorXConstants.hasTag (data, position, EmulatorXConstants.PRESET_TAG))
                presets.add (EmulatorXChunk.wrap (data, EmulatorXConstants.PRESET_TAG, offset + EmulatorXConstants.CHUNK_OVERHEAD, size));
            else if (EmulatorXConstants.hasTag (data, position, EmulatorXConstants.SAMPLE_LINK_TAG) && !this.loadSample (samplePool, bankName, index, samples))
                missingSamples++;
        }

        if (missingSamples > 0)
            this.notifier.logError ("IDS_EXB_SAMPLES_MISSING", Integer.toString (missingSamples), samplePool.getAbsolutePath ());

        final List<IMultisampleSource> results = new ArrayList<> ();
        for (final EmulatorXChunk preset: presets)
        {
            final IMultisampleSource multisampleSource = this.parsePreset (sourceFile, preset, samples);
            if (multisampleSource != null)
                results.add (multisampleSource);
        }
        if (results.isEmpty ())
            this.notifier.logError ("IDS_EXB_NO_PRESETS", sourceFile.getName ());
        else
            this.notifier.log ("IDS_EXB_READING_BANK", sourceFile.getName (), Integer.toString (results.size ()), Long.toString (samples.values ().stream ().filter (Objects::nonNull).count ()));
        return results;
    }


    /**
     * Load one sample file of the sample pool of a bank.
     *
     * @param samplePool The sample pool folder
     * @param bankName The name of the bank, which is the prefix of all of its sample files
     * @param index The 1-based index of the sample
     * @param samples Where to add the loaded sample
     * @return True if the sample could be loaded
     */
    private boolean loadSample (final File samplePool, final String bankName, final int index, final Map<Integer, EmulatorXSampleFile> samples)
    {
        // A sample which is listed by the bank but cannot be loaded is remembered with a null entry
        // so that its zones do not report it a second time
        final File file = new File (samplePool, EmulatorXConstants.createSampleFileName (bankName, index));
        if (!file.exists ())
        {
            samples.put (Integer.valueOf (index), null);
            return false;
        }
        try
        {
            samples.put (Integer.valueOf (index), EmulatorXSampleFile.read (Files.readAllBytes (file.toPath ())));
            return true;
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_EXB_MALFORMED_SAMPLE", file.getName ());
            samples.put (Integer.valueOf (index), null);
            return false;
        }
    }


    /**
     * Parse a preset chunk into a multi-sample source.
     *
     * @param sourceFile The bank file
     * @param preset The preset chunk
     * @param samples The samples of the bank by their 1-based index
     * @return The multi-sample source or null if the preset contains no usable zones
     */
    private IMultisampleSource parsePreset (final File sourceFile, final EmulatorXChunk preset, final Map<Integer, EmulatorXSampleFile> samples)
    {
        final EmulatorXChunk header = preset.getChild (EmulatorXConstants.PRESET_HEADER_TAG);
        if (header == null)
            return null;
        final String presetName = EmulatorXConstants.decodeName (header.getData (), header.getOffset () + 4);

        final EmulatorXChunk voiceList = preset.getList (EmulatorXConstants.VOICE_LIST_TYPE);
        if (voiceList == null)
            return null;
        final int [] initialControllers = readInitialControllers (preset);

        // A group of the model is a velocity layer, therefore all voices which cover the same
        // velocity range go into one group. Voices which additionally overlap on the keyboard are
        // layers on top of each other and get one group each
        final Map<VelocityRange, List<IGroup>> groupsByVelocity = new HashMap<> ();
        final List<IGroup> groups = new ArrayList<> ();
        for (final EmulatorXChunk voice: voiceList.getChildren ())
        {
            if (!voice.is (EmulatorXConstants.VOICE_TAG))
                continue;
            for (final ISampleZone zone: this.parseVoice (voice, presetName, samples, initialControllers))
            {
                final VelocityRange velocityRange = new VelocityRange (zone.getVelocityLow (), zone.getVelocityHigh ());
                final List<IGroup> candidates = groupsByVelocity.computeIfAbsent (velocityRange, key -> new ArrayList<> ());
                IGroup group = null;
                for (final IGroup candidate: candidates)
                    if (!overlaps (candidate, zone))
                    {
                        group = candidate;
                        break;
                    }
                if (group == null)
                {
                    group = new DefaultGroup ("Group " + (groups.size () + 1));
                    candidates.add (group);
                    groups.add (group);
                }
                group.addSampleZone (zone);
            }
        }

        if (groups.isEmpty ())
            return null;
        return this.createMultisampleSource (sourceFile, presetName.isBlank () ? FileUtils.getNameWithoutType (sourceFile) : presetName, groups);
    }


    /**
     * Test whether the key range of a zone overlaps the key range of any zone of a group.
     *
     * @param group The group to test
     * @param zone The zone to test
     * @return True if there is an overlap
     */
    private static boolean overlaps (final IGroup group, final ISampleZone zone)
    {
        for (final ISampleZone other: group.getSampleZones ())
            if (zone.getKeyLow () <= other.getKeyHigh () && other.getKeyLow () <= zone.getKeyHigh ())
                return true;
        return false;
    }


    /**
     * Parse one voice into its sample zones. The key and velocity ranges of the voice are combined
     * with the ranges of its zones, which are relative to them.
     *
     * @param voice The voice chunk
     * @param presetName The name of the preset, for error messages
     * @param samples The samples of the bank by their 1-based index
     * @param initialControllers The initial values of the assignable MIDI controllers of the preset
     * @return The zones of the voice
     */
    private List<ISampleZone> parseVoice (final EmulatorXChunk voice, final String presetName, final Map<Integer, EmulatorXSampleFile> samples, final int [] initialControllers)
    {
        final EmulatorXChunk windowList = voice.getList (EmulatorXConstants.WINDOW_LIST_TYPE);
        final List<EmulatorXChunk> windows = windowList == null ? Collections.emptyList () : windowList.getChildren ();
        final int [] keyWindow = readWindow (windows, 0);
        final int [] velocityWindow = readWindow (windows, 1);

        final EmulatorXChunk oscillator = voice.getChild (EmulatorXConstants.OSCILLATOR_TAG);
        final EmulatorXChunk amplifier = voice.getChild (EmulatorXConstants.AMPLIFIER_TAG);
        final double tuning = oscillator == null ? 0 : oscillator.getSignedByte (EmulatorXConstants.OSCILLATOR_TRANSPOSE) + oscillator.getSignedByte (EmulatorXConstants.OSCILLATOR_COARSE_TUNE) + oscillator.getFloat (EmulatorXConstants.OSCILLATOR_FINE_TUNE) / 100.0;
        // Banks written by third party converters exceed the documented panning range, clamp them
        final double panning = amplifier == null ? 0 : Math.clamp (amplifier.getSignedByte (EmulatorXConstants.AMPLIFIER_PAN) / EmulatorXConstants.PAN_RANGE, -1, 1);
        final double gain = amplifier == null ? 0 : Math.clamp (amplifier.getFloat (EmulatorXConstants.AMPLIFIER_VOLUME), EmulatorXConstants.MIN_VOLUME_DB, EmulatorXConstants.MAX_VOLUME_DB);

        final IEnvelope amplitudeEnvelope = readEnvelope (voice, EmulatorXConstants.ENVELOPE_AMPLITUDE);
        final double velocityDepth = readVelocityToVolume (voice);
        final IFilter filter = createFilter (voice, initialControllers);

        final List<ISampleZone> zones = new ArrayList<> ();
        final EmulatorXChunk zoneList = voice.getList (EmulatorXConstants.ZONE_LIST_TYPE);
        if (zoneList == null)
            return zones;

        EmulatorXChunk zoneHeader = null;
        for (final EmulatorXChunk child: zoneList.getChildren ())
        {
            if (child.is (EmulatorXConstants.ZONE_HEADER_TAG))
            {
                zoneHeader = child;
                continue;
            }
            if (zoneHeader == null || !child.isList (EmulatorXConstants.WINDOW_LIST_TYPE))
                continue;

            final Integer sampleIndex = Integer.valueOf (zoneHeader.getU16 (EmulatorXConstants.ZONE_SAMPLE_INDEX));
            final EmulatorXSampleFile sampleFile = samples.get (sampleIndex);
            if (sampleFile == null)
            {
                // Samples which the bank lists but which could not be loaded are already reported
                if (!samples.containsKey (sampleIndex))
                    this.notifier.logError ("IDS_EXB_SAMPLE_MISSING", sampleIndex.toString (), presetName);
                zoneHeader = null;
                continue;
            }

            final List<EmulatorXChunk> zoneWindows = child.getChildren ();
            final int [] zoneKeys = intersect (keyWindow, readWindow (zoneWindows, 0));
            final int [] zoneVelocities = intersect (velocityWindow, readWindow (zoneWindows, 1));

            final ISampleZone zone = new DefaultSampleZone (sampleFile.getName (), zoneKeys[0], zoneKeys[3]);
            zone.setNoteCrossfadeLow (zoneKeys[1]);
            zone.setNoteCrossfadeHigh (zoneKeys[2]);
            zone.setVelocityLow (Math.max (1, zoneVelocities[0]));
            zone.setVelocityHigh (Math.max (1, zoneVelocities[3]));
            zone.setVelocityCrossfadeLow (zoneVelocities[1]);
            zone.setVelocityCrossfadeHigh (zoneVelocities[2]);
            zone.setKeyRoot (zoneHeader.getByte (EmulatorXConstants.ZONE_ORIGINAL_KEY));
            zone.setSampleData (createSampleData (sampleFile));
            zone.setStart (0);
            zone.setStop (sampleFile.getNumFrames ());
            zone.setTuning (tuning);
            zone.setPanning (panning);
            zone.setGain (gain);
            addLoop (zone, sampleFile);

            zone.getAmplitudeEnvelopeModulator ().setSource (amplitudeEnvelope);
            zone.getAmplitudeVelocityModulator ().setDepth (velocityDepth);
            if (filter != null)
                zone.setFilter (filter);

            zones.add (zone);
            zoneHeader = null;
        }
        return zones;
    }


    /**
     * Create the sample data of a sample file.
     *
     * @param sampleFile The sample file
     * @return The sample data
     */
    private static InMemorySampleData createSampleData (final EmulatorXSampleFile sampleFile)
    {
        return new InMemorySampleData (new DefaultAudioMetadata (sampleFile.getNumChannels (), sampleFile.getSampleRate (), 16, sampleFile.getNumFrames ()), sampleFile.getPcm ());
    }


    /**
     * Add the loop of a sample file to a zone.
     *
     * @param zone The zone
     * @param sampleFile The sample file
     */
    private static void addLoop (final ISampleZone zone, final EmulatorXSampleFile sampleFile)
    {
        if (!sampleFile.hasLoop ())
            return;
        final ISampleLoop loop = new DefaultSampleLoop ();
        loop.setType (LoopType.FORWARDS);
        loop.setStart (sampleFile.getLoopStart ());
        loop.setEnd (sampleFile.getLoopEnd ());
        zone.getLoops ().add (loop);
    }


    /**
     * Read one window chunk of a window list.
     *
     * @param windows The chunks of the window list
     * @param index The index of the window
     * @return The low value, the low fade, the high fade and the high value; the full range if the
     *         window is missing
     */
    private static int [] readWindow (final List<EmulatorXChunk> windows, final int index)
    {
        int position = 0;
        for (final EmulatorXChunk window: windows)
            if (window.is (EmulatorXConstants.WINDOW_TAG) && position++ == index)
                return new int []
                {
                    window.getByte (4),
                    window.getByte (5),
                    window.getByte (6),
                    window.getByte (7)
                };
        return new int []
        {
            0,
            0,
            0,
            127
        };
    }


    /**
     * Combine the window of a voice with the window of one of its zones, which is relative to it.
     *
     * @param voiceWindow The window of the voice
     * @param zoneWindow The window of the zone
     * @return The resulting window
     */
    private static int [] intersect (final int [] voiceWindow, final int [] zoneWindow)
    {
        final int low = Math.max (voiceWindow[0], zoneWindow[0]);
        final int high = Math.min (voiceWindow[3], zoneWindow[3]);
        return new int []
        {
            Math.clamp (low, 0, 127),
            Math.max (voiceWindow[1], zoneWindow[1]),
            Math.max (voiceWindow[2], zoneWindow[2]),
            Math.clamp (Math.max (low, high), 0, 127)
        };
    }


    /**
     * Read one of the three envelopes of a voice: the amplitude, the filter and the auxiliary
     * envelope.
     *
     * @param voice The voice chunk
     * @param index The index of the envelope
     * @return The envelope or null if the voice does not have it
     */
    private static IEnvelope readEnvelope (final EmulatorXChunk voice, final int index)
    {
        final EmulatorXChunk envelopeList = voice.getList (EmulatorXConstants.ENVELOPE_LIST_TYPE);
        if (envelopeList == null)
            return null;
        EmulatorXChunk chunk = null;
        int position = 0;
        for (final EmulatorXChunk child: envelopeList.getChildren ())
            if (child.is (EmulatorXConstants.ENVELOPE_TAG) && position++ == index)
            {
                chunk = child;
                break;
            }
        if (chunk == null)
            return null;

        // The six stages are attack 1, attack 2, decay 1, decay 2, release 1 and release 2. The
        // model has one attack, hold, decay and release stage, so the pairs are added up; a decay 1
        // stage which stays at the level of attack 2 is a plateau and therefore the hold stage
        final double [] times = new double [EmulatorXConstants.ENVELOPE_NUM_STAGES];
        final double [] levels = new double [EmulatorXConstants.ENVELOPE_NUM_STAGES];
        for (int stage = 0; stage < EmulatorXConstants.ENVELOPE_NUM_STAGES; stage++)
        {
            final int stageOffset = EmulatorXConstants.ENVELOPE_STAGES + stage * EmulatorXConstants.ENVELOPE_STAGE_SIZE;
            times[stage] = Math.max (0, chunk.getFloat (stageOffset));
            levels[stage] = Math.clamp (chunk.getFloat (stageOffset + 4) / EmulatorXConstants.FULL_LEVEL, 0, 1);
        }

        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (times[0] + times[1]);
        final boolean isPlateau = Math.abs (levels[2] - levels[1]) < 0.01;
        envelope.setHoldTime (isPlateau ? times[2] : 0);
        envelope.setDecayTime (isPlateau ? times[3] : times[2] + times[3]);
        envelope.setSustainLevel (levels[3]);
        envelope.setReleaseTime (times[4] + times[5]);
        return envelope;
    }


    /**
     * Read the amount of the modulation cord which routes the velocity to the volume.
     *
     * @param voice The voice chunk
     * @return The depth in the range of 0..1
     */
    private static double readVelocityToVolume (final EmulatorXChunk voice)
    {
        final EmulatorXChunk cordList = voice.getList (EmulatorXConstants.CORD_LIST_TYPE);
        if (cordList == null)
            return 0;
        for (final EmulatorXChunk cord: cordList.getChildren ())
            if (cord.is (EmulatorXConstants.CORD_TAG) && cord.getByte (4) == EmulatorXConstants.CORD_SOURCE_VELOCITY && cord.getByte (5) == EmulatorXConstants.CORD_DEST_VOLUME)
                return Math.clamp (Math.abs (cord.getFloat (6)) / EmulatorXConstants.FULL_CORD_AMOUNT, 0, 1);
        return 0;
    }


    /**
     * Read the depth of the modulation cord which routes the filter envelope to the filter cutoff.
     *
     * @param voice The voice chunk
     * @return The depth in the range of -1..1
     */
    private static double readFilterEnvelopeDepth (final EmulatorXChunk voice)
    {
        final EmulatorXChunk cordList = voice.getList (EmulatorXConstants.CORD_LIST_TYPE);
        if (cordList == null)
            return 0;
        for (final EmulatorXChunk cord: cordList.getChildren ())
        {
            if (!cord.is (EmulatorXConstants.CORD_TAG) || cord.getByte (5) != EmulatorXConstants.CORD_DEST_CUTOFF)
                continue;
            final int source = cord.getByte (4);
            if (source == EmulatorXConstants.CORD_SOURCE_FILTER_ENV || source == EmulatorXConstants.CORD_SOURCE_FILTER_ENV2)
                return Math.clamp (cord.getFloat (6) / EmulatorXConstants.FULL_CORD_AMOUNT, -1, 1);
        }
        return 0;
    }


    /**
     * Read the initial values of the 16 assignable MIDI controllers A to P of a preset. The
     * modulation cords which use one of them start at that value, so it decides where a cutoff
     * which is modulated by a controller actually sits when a note starts.
     *
     * @param preset The preset chunk
     * @return The values in the range of 0..127, -1 for a controller which is not set
     */
    private static int [] readInitialControllers (final EmulatorXChunk preset)
    {
        final int [] controllers = new int [EmulatorXConstants.NUM_MIDI_CONTROLLERS];
        Arrays.fill (controllers, -1);
        final EmulatorXChunk chunk = preset.getChild (EmulatorXConstants.INITIAL_CONTROLLER_TAG);
        if (chunk == null)
            return controllers;
        for (int i = 0; i < controllers.length; i++)
        {
            final int value = chunk.getByte (EmulatorXConstants.INITIAL_CONTROLLERS + i);
            if (value != EmulatorXConstants.CONTROLLER_UNSET)
                controllers[i] = value;
        }
        return controllers;
    }


    /**
     * Calculate how far the assignable MIDI controllers of the preset open the filter cutoff when a
     * note starts. A controller which is not set contributes nothing.
     *
     * @param voice The voice chunk
     * @param initialControllers The initial values of the controllers of the preset
     * @return The offset to add to the normalized cutoff
     */
    private static double controllerCutoffOffset (final EmulatorXChunk voice, final int [] initialControllers)
    {
        final EmulatorXChunk cordList = voice.getList (EmulatorXConstants.CORD_LIST_TYPE);
        if (cordList == null)
            return 0;
        double offset = 0;
        for (final EmulatorXChunk cord: cordList.getChildren ())
        {
            if (!cord.is (EmulatorXConstants.CORD_TAG) || cord.getByte (5) != EmulatorXConstants.CORD_DEST_CUTOFF)
                continue;
            final int controller = cord.getByte (4) - EmulatorXConstants.CORD_SOURCE_MIDI_A;
            if (controller < 0 || controller >= initialControllers.length || initialControllers[controller] < 0)
                continue;
            offset += initialControllers[controller] / EmulatorXConstants.CONTROLLER_RANGE * (cord.getFloat (6) / EmulatorXConstants.FULL_CORD_AMOUNT);
        }
        return offset;
    }


    /**
     * Test whether any modulation cord routes something into the filter cutoff.
     *
     * @param voice The voice chunk
     * @return True if the cutoff is modulated
     */
    private static boolean isCutoffModulated (final EmulatorXChunk voice)
    {
        final EmulatorXChunk cordList = voice.getList (EmulatorXConstants.CORD_LIST_TYPE);
        if (cordList == null)
            return false;
        for (final EmulatorXChunk cord: cordList.getChildren ())
            if (cord.is (EmulatorXConstants.CORD_TAG) && cord.getByte (5) == EmulatorXConstants.CORD_DEST_CUTOFF && cord.getFloat (6) != 0)
                return true;
        return false;
    }


    /**
     * Create the filter of a voice. The 'No Filter' setting and a fully open low-pass, which is
     * sonically the same, create no filter. The effect and morph filter types of the E-mu (phasers,
     * flangers, vowel formants, EQ morphs, distortions) have no model equivalent and create no
     * filter either.
     *
     * @param voice The voice chunk
     * @param initialControllers The initial values of the assignable MIDI controllers of the preset
     * @return The filter or null if the voice does not use one
     */
    private static IFilter createFilter (final EmulatorXChunk voice, final int [] initialControllers)
    {
        final EmulatorXChunk chunk = voice.getChild (EmulatorXConstants.FILTER_TAG);
        if (chunk == null)
            return null;

        final FilterType type;
        final int poles;
        switch (chunk.getByte (EmulatorXConstants.FILTER_TYPE))
        {
            case EmulatorXConstants.FILTER_TYPE_LOWPASS_4:
                type = FilterType.LOW_PASS;
                poles = 4;
                break;
            case EmulatorXConstants.FILTER_TYPE_LOWPASS_2:
                type = FilterType.LOW_PASS;
                poles = 2;
                break;
            case EmulatorXConstants.FILTER_TYPE_LOWPASS_6:
                type = FilterType.LOW_PASS;
                poles = 6;
                break;
            case EmulatorXConstants.FILTER_TYPE_HIGHPASS_2:
                type = FilterType.HIGH_PASS;
                poles = 2;
                break;
            case EmulatorXConstants.FILTER_TYPE_HIGHPASS_4:
                type = FilterType.HIGH_PASS;
                poles = 4;
                break;
            case EmulatorXConstants.FILTER_TYPE_BANDPASS_2:
                type = FilterType.BAND_PASS;
                poles = 2;
                break;
            case EmulatorXConstants.FILTER_TYPE_BANDPASS_4:
                type = FilterType.BAND_PASS;
                poles = 4;
                break;
            case EmulatorXConstants.FILTER_TYPE_CONTRARY:
                type = FilterType.BAND_REJECTION;
                poles = 6;
                break;
            default:
                return null;
        }

        final double cutoff = chunk.getFloat (EmulatorXConstants.FILTER_CUTOFF);

        // Many presets park the cutoff at the bottom and open it again with a modulation cord. The
        // most common one routes an assignable MIDI controller to the cutoff and gives that
        // controller an initial value in the preset, so the position it starts at can be
        // calculated. What is left over is modulation which cannot be converted; the static cutoff
        // alone would then turn the voice into silence, which is much further from the original
        // than leaving the filter out
        final double envelopeDepth = readFilterEnvelopeDepth (voice);
        final double startCutoff = Math.clamp (cutoff + controllerCutoffOffset (voice, initialControllers), 0, 1);
        if (startCutoff < MINIMUM_STATIC_CUTOFF && envelopeDepth == 0 && isCutoffModulated (voice))
            return null;
        // A wide open low-pass is what the banks use instead of switching the filter off
        if (type == FilterType.LOW_PASS && startCutoff >= 1)
            return null;

        final IFilter filter = new DefaultFilter (type, poles, EmulatorXConstants.cutoffToHertz (startCutoff), 0);
        if (envelopeDepth != 0)
        {
            final IEnvelope envelope = readEnvelope (voice, EmulatorXConstants.ENVELOPE_FILTER);
            if (envelope != null)
            {
                final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                cutoffModulator.setSource (envelope);
                cutoffModulator.setDepth (envelopeDepth);
            }
        }
        return filter;
    }
}
