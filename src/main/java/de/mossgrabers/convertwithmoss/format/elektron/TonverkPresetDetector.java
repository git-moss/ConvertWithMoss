// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkKeyZone;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkSampleSlot;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkVelocityLayer;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkPresetFile.Machine;
import de.mossgrabers.tools.FileUtils;


/**
 * Detector for Elektron Tonverk preset files (*.tvpst). Supports the sample based generator
 * machines: Multi (multi-sample), One-Shot (single sample), Drum (a kit of up to several voices)
 * and Grainer (granular playback of a single sample, converted like a One-Shot). The amplitude
 * envelope, the filter and its envelope, sample loops, tuning, gain and panning are read into the
 * model. The remaining, synth-specific parameters (granular engine settings, arpeggiator, FX,
 * global LFOs, modulation matrix) have no representation in the multi-sample model and are
 * therefore not converted.
 *
 * @author Jürgen Moßgraber
 */
public class TonverkPresetDetector extends AbstractDetector<MetadataSettingsUI>
{
    /** The number of poles of the Tonverk multi-mode filter (12 dB/octave). */
    private static final int           FILTER_POLES            = 2;
    /** The MIDI note the One-Shot and Grainer machines are centered on. */
    private static final int           SINGLE_SAMPLE_ROOT_NOTE = 60;
    /** The mount point under which the device stores absolute sample paths. */
    private static final String        DEVICE_MOUNT_PREFIX     = "/mnt/sdcard/";

    /**
     * The filter types of the Grainer machine. In contrast to the continuously morphing multi-mode
     * filter of the other machines, the type is an index into a fixed list: Low-pass 4, Low-pass 2,
     * Band-pass 2, High-pass 2, Low-pass notch, Notch 1, Notch 2. The low-pass notch and the two
     * notch variants are mapped to the closest model type.
     */
    private static final FilterType [] GRAINER_FILTER_TYPES    =
    {
        FilterType.LOW_PASS,
        FilterType.LOW_PASS,
        FilterType.BAND_PASS,
        FilterType.HIGH_PASS,
        FilterType.LOW_PASS,
        FilterType.BAND_REJECTION,
        FilterType.BAND_REJECTION
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TonverkPresetDetector (final INotifier notifier)
    {
        super ("Elektron Tonverk Preset", "Tonverk", notifier, new MetadataSettingsUI ("Tonverk"), ".tvpst");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final TonverkPresetFile preset = new TonverkPresetFile (Files.readAllLines (sourceFile.toPath ()));
            for (final String error: preset.errors)
                this.notifier.logText (error);
            final Optional<IMultisampleSource> source = this.convertPreset (sourceFile, preset);
            return source.isEmpty () ? Collections.emptyList () : Collections.singletonList (source.get ());
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private Optional<IMultisampleSource> convertPreset (final File sourceFile, final TonverkPresetFile preset) throws IOException
    {
        final List<IGroup> groups;
        switch (preset.machine)
        {
            case MULTI -> groups = this.buildMultiGroups (sourceFile, preset);
            case ONESHOT, GRAINER -> groups = this.buildSingleSampleGroups (sourceFile, preset);
            case DRUM -> groups = this.buildDrumGroups (sourceFile, preset);
            default -> {
                // A file that parsed without any parameters is empty or corrupt (e.g. a zero-filled
                // file left by a failed write), not a real preset with an unsupported machine.
                final String genMachine = preset.param ("gen_machine");
                if (preset.parameters.isEmpty ())
                    this.notifier.logError ("IDS_TONVERK_EMPTY_OR_CORRUPT", sourceFile.getName ());
                else
                    this.notifier.logError ("IDS_TONVERK_UNKNOWN_MACHINE", genMachine == null ? "" : genMachine);
                return Optional.empty ();
            }
        }

        if (groups.isEmpty ())
            return Optional.empty ();

        final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, FileUtils.getNameWithoutType (sourceFile), groups);

        final IMetadata metadata = multisampleSource.getMetadata ();
        if (preset.category != null && !preset.category.isBlank ())
            metadata.setCategory (preset.category);
        if (!preset.tags.isEmpty ())
            metadata.setKeywords (preset.tags.toArray (new String [preset.tags.size ()]));

        return Optional.of (multisampleSource);
    }


    /**
     * Build the groups of a Multi machine: the key-zones are spread across key- and velocity-ranges
     * (identical to the elmulti mapping), and the single, global generator envelope/filter is
     * applied to every zone.
     *
     * @param sourceFile The source file
     * @param preset The preset
     * @return The converted groups
     * @throws IOException Could not read
     */
    private List<IGroup> buildMultiGroups (final File sourceFile, final TonverkPresetFile preset) throws IOException
    {
        final String prefix = Machine.MULTI.getParameterPrefix ();
        final TreeMap<Integer, TreeMap<Integer, List<ISampleZone>>> orderedKeyRanges = new TreeMap<> ();

        for (final TonverkKeyZone keyZone: preset.keyZones)
        {
            final TreeMap<Integer, List<ISampleZone>> velocityMap = orderedKeyRanges.computeIfAbsent (Integer.valueOf (keyZone.pitch), _ -> new TreeMap<> ());
            for (final TonverkVelocityLayer velocityLayer: keyZone.velocityLayers)
            {
                final List<ISampleZone> zones = new ArrayList<> ();
                for (final TonverkSampleSlot slot: velocityLayer.sampleSlots)
                {
                    final Optional<ISampleZone> zoneOpt = this.createMappedZone (sourceFile, slot, keyZone.pitch, keyZone.keyCenter);
                    if (zoneOpt.isEmpty ())
                        continue;
                    final ISampleZone zone = zoneOpt.get ();
                    applyAmplitudeEnvelope (zone, preset, prefix);
                    applyFilter (zone, preset, prefix);
                    applyGainAndPanning (zone, preset, prefix);
                    zones.add (zone);
                }
                if (zones.isEmpty ())
                    continue;
                if (zones.size () > 1)
                    for (int i = 0; i < zones.size (); i++)
                        zones.get (i).setSequencePosition (1 + i);
                final int velocity = (int) Math.clamp (velocityLayer.velocity * 127.0, 0, 127.0);
                velocityMap.put (Integer.valueOf (velocity), zones);
            }
        }

        if (orderedKeyRanges.values ().stream ().allMatch (TreeMap::isEmpty))
            return Collections.emptyList ();
        TonverkMultiDetector.calculateRanges (orderedKeyRanges);
        return TonverkMultiDetector.collapseToGroups (orderedKeyRanges);
    }


    /**
     * Build the single group of a One-Shot or Grainer machine: one sample mapped across the whole
     * keyboard. The sample start/end and loop points of the One-Shot machine are stored normalized
     * [0..1] and are scaled by the number of sample frames. The Grainer machine plays its sample as
     * grains; since these cannot be represented in the model, the sample is converted with its
     * tuning, envelope and filter but plain (non-granular) playback.
     *
     * @param sourceFile The source file
     * @param preset The preset
     * @return The converted groups
     * @throws IOException Could not read
     */
    private List<IGroup> buildSingleSampleGroups (final File sourceFile, final TonverkPresetFile preset) throws IOException
    {
        final String prefix = preset.machine.getParameterPrefix ();
        final Optional<File> sampleFile = resolveSample (sourceFile, preset.param (prefix + "_sample_slot"));
        if (sampleFile.isEmpty ())
        {
            this.notifier.logError ("IDS_TONVERK_SAMPLE_NOT_FOUND", preset.param (prefix + "_sample_slot"));
            return Collections.emptyList ();
        }

        final ISampleZone zone = this.createSampleZone (sampleFile.get ());

        // The bipolar tune (0.5 = no pitch change) spans +/- 5 octaves for the One-Shot but only
        // +/- 2 octaves for the Grainer machine. Bake the full semitones into the root note (more
        // portable than a large tuning offset) and keep the sub-semitone remainder as tuning.
        final double tuneRange = preset.machine == Machine.GRAINER ? TonverkValues.GRAINER_TUNE_RANGE : TonverkValues.ONESHOT_TUNE_RANGE;
        final double tune = TonverkValues.normalizedToTune (preset.paramDouble (prefix + "_tune", 0.5), tuneRange);
        final int keyRoot = Math.clamp (SINGLE_SAMPLE_ROOT_NOTE - Math.round (tune), 0, 127);
        zone.setKeyRoot (keyRoot);
        zone.setTuning (tune - (SINGLE_SAMPLE_ROOT_NOTE - keyRoot));
        zone.setKeyLow (0);
        zone.setKeyHigh (127);
        zone.setVelocityLow (0);
        zone.setVelocityHigh (127);

        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException ("Empty sample data in zone: " + zone.getName ());
        final int frames = sampleData.get ().getAudioMetadata ().getNumberOfSamples ();
        if (frames > 0)
        {
            final double startNormalized = preset.paramDouble (prefix + "_sample_start", 0);
            final double endNormalized = preset.paramDouble (prefix + "_sample_end", 1);
            zone.setStart ((int) Math.round (TonverkValues.clampNormalized (startNormalized) * frames));
            zone.setStop ((int) Math.round (TonverkValues.clampNormalized (endNormalized) * frames));

            final double loopStartNormalized = preset.paramDouble (prefix + "_loop_start", 0);
            final double loopEndNormalized = preset.paramDouble (prefix + "_loop_end", 0);
            // Only create a loop if it covers a real sub-region (the device stores loop points even
            // when looping is disabled).
            if (loopEndNormalized > loopStartNormalized && (loopStartNormalized > 0.0001 || loopEndNormalized < 0.9999))
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (LoopType.FORWARDS);
                loop.setStart ((int) Math.round (loopStartNormalized * frames));
                loop.setEnd ((int) Math.round (loopEndNormalized * frames));
                final double crossfade = preset.paramDouble (prefix + "_loop_xfade", 0);
                if (crossfade > 0)
                    loop.setCrossfade (TonverkValues.clampNormalized (crossfade));
                zone.getLoops ().add (loop);
            }
        }

        applyAmplitudeEnvelope (zone, preset, prefix);
        applyFilter (zone, preset, prefix);
        applyGainAndPanning (zone, preset, prefix);

        final IGroup group = new DefaultGroup ();
        group.addSampleZone (zone);
        return List.of (group);
    }


    /**
     * Build the single group of a Drum machine: each key-zone maps one drum sample to a single key.
     * The Nth key-zone is played by the Nth drum voice, so the per-voice envelope, filter, gain and
     * panning are applied accordingly.
     *
     * @param sourceFile The source file
     * @param preset The preset
     * @return The converted groups
     * @throws IOException Could not read
     */
    private List<IGroup> buildDrumGroups (final File sourceFile, final TonverkPresetFile preset) throws IOException
    {
        final IGroup group = new DefaultGroup ();
        int voiceIndex = 0;
        for (final TonverkKeyZone keyZone: preset.keyZones)
        {
            final String voicePrefix = Machine.DRUM.getParameterPrefix () + "_voice" + voiceIndex;
            for (final TonverkVelocityLayer velocityLayer: keyZone.velocityLayers)
                for (final TonverkSampleSlot slot: velocityLayer.sampleSlots)
                {
                    final Optional<ISampleZone> zoneOpt = this.createMappedZone (sourceFile, slot, keyZone.pitch, keyZone.keyCenter);
                    if (zoneOpt.isEmpty ())
                        continue;
                    final ISampleZone zone = zoneOpt.get ();
                    zone.setKeyLow (keyZone.pitch);
                    zone.setKeyHigh (keyZone.pitch);
                    zone.setVelocityLow (0);
                    zone.setVelocityHigh (127);
                    applyAmplitudeEnvelope (zone, preset, voicePrefix);
                    applyFilter (zone, preset, voicePrefix);
                    applyGainAndPanning (zone, preset, voicePrefix);
                    group.addSampleZone (zone);
                }
            voiceIndex++;
        }
        return group.getSampleZones ().isEmpty () ? Collections.emptyList () : List.of (group);
    }


    /**
     * Create a sample zone from a mapping-slot sample: resolves the (absolute) sample path, sets
     * the root note, tuning, trim and an (absolute, in samples) loop.
     *
     * @param sourceFile The source file
     * @param slot The sample slot
     * @param pitch The pitch
     * @param keyCenter The key-center
     * @return The created sample zone
     * @throws IOException Could not read
     */
    private Optional<ISampleZone> createMappedZone (final File sourceFile, final TonverkSampleSlot slot, final int pitch, final double keyCenter) throws IOException
    {
        final Optional<File> sampleFile = resolveSample (sourceFile, slot.sample);
        if (sampleFile.isEmpty ())
        {
            this.notifier.logError ("IDS_TONVERK_SAMPLE_NOT_FOUND", slot.sample);
            return Optional.empty ();
        }

        final ISampleZone zone = this.createSampleZone (sampleFile.get ());
        zone.setKeyRoot (pitch);
        zone.setTuning (pitch - keyCenter);

        // A mapping slot without explicit trim points plays the whole sample. Default to the full
        // range (0 .. number-of-frames) rather than leaving the model default of -1, which other
        // formats would write out verbatim (e.g. the Waldorf QPAT shows a sample start/end of -1).
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException ("Empty sample data in zone: " + zone.getName ());
        final int frames = sampleData.get ().getAudioMetadata ().getNumberOfSamples ();
        zone.setStart (slot.trimStart != null && slot.trimStart.intValue () >= 0 ? slot.trimStart.intValue () : 0);
        zone.setStop (slot.trimEnd != null && slot.trimEnd.intValue () >= 0 ? slot.trimEnd.intValue () : frames);

        if ("Forward".equals (slot.loopMode))
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            if (slot.loopStart != null && slot.loopStart.intValue () >= 0)
                loop.setStart (slot.loopStart.intValue ());
            if (slot.loopEnd != null && slot.loopEnd.intValue () >= 0)
                loop.setEnd (slot.loopEnd.intValue ());
            if (slot.loopCrossfade != null && slot.loopCrossfade.intValue () >= 0)
                loop.setCrossfadeInSamples (slot.loopCrossfade.intValue ());
            // The Tonverk keeps looping during release only when 'keep-looping-on-release' is set;
            // otherwise the loop stops on release and the remainder is played (sustain loop)
            loop.setLoopUntilRelease (slot.keepLoopingOnRelease == null || !slot.keepLoopingOnRelease.booleanValue ());
            zone.getLoops ().add (loop);
        }

        return Optional.of (zone);
    }


    /**
     * Apply a Tonverk AHDSR amplitude envelope (parameters &lt;prefix&gt;_amp_env_*) to a zone.
     *
     * @param zone The zone
     * @param preset The preset
     * @param prefix The parameter prefix ('gen_multi', 'gen_oneshot', 'gen_granular' or
     *            'gen_drum_voiceN')
     */
    private static void applyAmplitudeEnvelope (final ISampleZone zone, final TonverkPresetFile preset, final String prefix)
    {
        // The amplitude envelope is either ADSR (amp_mode == 2) or AHD (otherwise). In AHD mode the
        // hold phase is active and the decay runs all the way to zero, so there is neither a
        // sustain
        // level nor a separate release phase.
        final boolean adsr = preset.paramInt (prefix + "_amp_mode", 2) == 2;
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        envelope.setStartLevel (0);
        envelope.setAttackTime (TonverkValues.normalizedToAttackTime (preset.paramDouble (prefix + "_amp_env_attack", 0)));
        envelope.setHoldLevel (1.0);
        envelope.setHoldTime (adsr ? 0 : TonverkValues.normalizedToHoldTime (preset.paramDouble (prefix + "_amp_env_hold", 0)));
        envelope.setDecayTime (TonverkValues.normalizedToDecayTime (preset.paramDouble (prefix + "_amp_env_decay", 0)));
        envelope.setSustainLevel (adsr ? TonverkValues.clampNormalized (preset.paramDouble (prefix + "_amp_env_sustain", 1)) : 0);
        envelope.setReleaseTime (adsr ? TonverkValues.normalizedToReleaseTime (preset.paramDouble (prefix + "_amp_env_release", 0)) : 0);
        envelope.setEndLevel (0);
    }


    /**
     * Apply the Tonverk filter and its DADSR envelope (parameters &lt;prefix&gt;_filter_*) to a
     * zone. The Grainer machine has no filter envelope; its absent parameters leave the modulation
     * depth at zero.
     *
     * @param zone The zone
     * @param preset The preset
     * @param prefix The parameter prefix ('gen_multi', 'gen_oneshot', 'gen_granular' or
     *            'gen_drum_voiceN')
     */
    private static void applyFilter (final ISampleZone zone, final TonverkPresetFile preset, final String prefix)
    {
        final double cutoff = TonverkValues.normalizedToCutoff (preset.paramDouble (prefix + "_filter_frequency", 1.0));
        final double resonance = TonverkValues.clampNormalized (preset.paramDouble (prefix + "_filter_resonance", 0));

        final FilterType type;
        final int poles;
        if (preset.machine == Machine.GRAINER)
        {
            final int typeIndex = Math.clamp (preset.paramInt (prefix + "_filter_type", 0), 0, GRAINER_FILTER_TYPES.length - 1);
            type = GRAINER_FILTER_TYPES[typeIndex];
            // Only the first Grainer filter type (Low-pass 4) is a 24 dB/octave filter
            poles = typeIndex == 0 ? 4 : FILTER_POLES;
        }
        else
        {
            type = mapFilterType (preset.paramDouble (prefix + "_filter_type", 0));
            poles = FILTER_POLES;
        }

        final IFilter filter = new DefaultFilter (type, poles, cutoff, resonance);

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final IEnvelope filterEnvelope = cutoffModulator.getSource ();
        filterEnvelope.setDelayTime (TonverkValues.normalizedToDelayTime (preset.paramDouble (prefix + "_filter_env_delay", 0)));
        filterEnvelope.setAttackTime (TonverkValues.normalizedToAttackTime (preset.paramDouble (prefix + "_filter_env_attack", 0)));
        filterEnvelope.setDecayTime (TonverkValues.normalizedToDecayTime (preset.paramDouble (prefix + "_filter_env_decay", 0)));
        filterEnvelope.setSustainLevel (TonverkValues.clampNormalized (preset.paramDouble (prefix + "_filter_env_sustain", 0)));
        filterEnvelope.setReleaseTime (TonverkValues.normalizedToReleaseTime (preset.paramDouble (prefix + "_filter_env_release", 0)));
        // The depth is stored bipolar with 0.5 as the center (no modulation); map it to [-1..1].
        cutoffModulator.setDepth (Math.clamp ((preset.paramDouble (prefix + "_filter_env_depth", 0.5) - 0.5) * 2.0, -1.0, 1.0));

        zone.setFilter (filter);
    }


    /**
     * Apply the generator volume (as gain in dB) and panning to a zone.
     *
     * @param zone The zone
     * @param preset The preset
     * @param prefix The parameter prefix ('gen_multi', 'gen_oneshot', 'gen_granular' or
     *            'gen_drum_voiceN')
     */
    private static void applyGainAndPanning (final ISampleZone zone, final TonverkPresetFile preset, final String prefix)
    {
        final double volume = preset.paramDouble (prefix + "_volume", 1.0);
        zone.setGain (volume <= 0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10 (volume));
        final double panning = preset.paramDouble (prefix + "_pan", 0.5);
        zone.setPanning (Math.clamp ((panning - 0.5) * 2.0, -1.0, 1.0));
    }


    /**
     * Resolve a sample referenced by its absolute device path (e.g. '/mnt/sdcard/...') to a file on
     * the local file system. The device mount prefix is stripped and the remaining relative path is
     * resolved against the preset's folder and all of its ancestors; additionally, leading path
     * segments are dropped in case the mount maps deeper into the tree. Each path segment which
     * does not exist locally is matched tolerantly against the folder entries, ignoring
     * leading/trailing whitespace and case: the FAT file system of the device SD-card does not
     * allow e.g. trailing spaces in names, so a local folder 'pad 03 ' is referenced as 'pad 03' by
     * the preset.
     *
     * @param file The preset file
     * @param devicePath The absolute device path of the sample
     * @return The resolved file or empty if it could not be found
     */
    private static Optional<File> resolveSample (final File file, final String devicePath)
    {
        if (devicePath == null || devicePath.isBlank ())
            return Optional.empty ();

        final File asIs = new File (devicePath);
        if (asIs.exists ())
            return Optional.of (asIs);

        String relative = devicePath.replace ('\\', '/');
        final int mountIndex = relative.indexOf (DEVICE_MOUNT_PREFIX);
        if (mountIndex >= 0)
            relative = relative.substring (mountIndex + DEVICE_MOUNT_PREFIX.length ());
        else if (relative.startsWith ("/"))
            relative = relative.substring (1);

        final String [] segments = relative.split ("/");
        for (int start = 0; start < segments.length; start++)
            for (File directory = file.getParentFile (); directory != null; directory = directory.getParentFile ())
            {
                final File candidate = resolveSegments (directory, segments, start);
                if (candidate != null)
                    return Optional.of (candidate);
            }

        return Optional.empty ();
    }


    /**
     * Resolve path segments (beginning at the given start index) step-wise against a base folder.
     * Each segment is first decoded via {@link #decodeSfmCharacters}; a segment which does not
     * exist is matched tolerantly via {@link #findTolerant}.
     *
     * @param baseDirectory The folder to resolve against
     * @param segments The path segments
     * @param startIndex The index of the first segment to resolve
     * @return The resolved file or null if a segment could not be matched
     */
    private static File resolveSegments (final File baseDirectory, final String [] segments, final int startIndex)
    {
        File current = baseDirectory;
        for (int i = startIndex; i < segments.length; i++)
        {
            final String segment = decodeSfmCharacters (segments[i]);
            if (segment.isEmpty ())
                continue;
            final File exact = new File (current, segment);
            if (exact.exists ())
            {
                current = exact;
                continue;
            }
            current = findTolerant (current, segment);
            if (current == null)
                return null;
        }
        return current == baseDirectory ? null : current;
    }


    /**
     * Decode the private-use-area characters with which the device file system stores characters
     * that FAT does not allow (the 'Services for Macintosh' convention): U+F001-U+F01F map to the
     * control characters 0x01-0x1F, U+F020-U+F027 to '"', '*', '/', '&lt;', '&gt;', '?', '\' and
     * '|', a trailing space is stored as U+F028 and a trailing period as U+F029. Copying such a
     * file to the computer restores the original character (e.g. a folder 'pad 03&lt;U+F028&gt;'
     * becomes 'pad 03 ' with a trailing space), so the device path needs to be decoded to match.
     *
     * @param name The name to decode
     * @return The decoded name
     */
    private static String decodeSfmCharacters (final String name)
    {
        final StringBuilder sb = new StringBuilder (name.length ());
        for (int i = 0; i < name.length (); i++)
        {
            final char c = name.charAt (i);
            if (c >= '\uF001' && c <= '\uF01F')
                sb.append ((char) (c - '\uF000'));
            else if (c >= '\uF020' && c <= '\uF029')
                sb.append ("\"*/<>?\\| .".charAt (c - '\uF020'));
            else
                sb.append (c);
        }
        return sb.toString ();
    }


    /**
     * Find an entry of a folder by name, ignoring leading/trailing whitespace and case.
     *
     * @param folder The folder to search in
     * @param name The name to look for
     * @return The found file/folder or null
     */
    private static File findTolerant (final File folder, final String name)
    {
        final File [] entries = folder.listFiles ();
        if (entries == null)
            return null;
        final String trimmedName = name.trim ();
        for (final File entry: entries)
            if (entry.getName ().trim ().equalsIgnoreCase (trimmedName))
                return entry;
        return null;
    }


    private static FilterType mapFilterType (final double morph)
    {
        // The Tonverk Multimode filter morphs continuously from low-pass through band-pass to
        // high-pass; map the morph position to the closest discrete filter type of the model.
        if (morph < 1.0 / 3.0)
            return FilterType.LOW_PASS;
        if (morph < 2.0 / 3.0)
            return FilterType.BAND_PASS;
        return FilterType.HIGH_PASS;
    }
}
