// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.riff.CommonRiffChunkId;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.file.wav.WaveRiffChunkId;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkKeyZone;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkSampleSlot;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkMultiFile.TonverkVelocityLayer;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkPresetCreatorUI.OutputEngine;
import de.mossgrabers.convertwithmoss.format.elektron.TonverkPresetFile.Machine;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for Elektron Tonverk preset files (*.tvpst). A preset bundles a description file and the
 * related (relatively referenced) samples in one folder. The full <code>[parameters]</code> block is
 * created from a neutral factory template (FX bypassed, LFOs/arpeggiator/modulation neutralized); the
 * amplitude envelope, the filter and its envelope, gain and panning are written from the model. The
 * output engine (Multi or Drum) can be selected; the Drum machine always has eight voices.
 *
 * @author Jürgen Moßgraber
 */
public class TonverkPresetCreator extends AbstractWavCreator<TonverkPresetCreatorUI>
{
    /**
     * The factory default velocity. The Tonverk rejects the whole preset file if a velocity layer
     * has a velocity of exactly 0.0.
     */
    private static final double                 DEFAULT_VELOCITY       = 0.49411765;
    private static final int                    DRUM_VOICE_COUNT       = 8;
    private static final int                    DEFAULT_DRUM_ROOT      = 60;
    private static final String                 MULTI_TEMPLATE         = "/de/mossgrabers/convertwithmoss/templates/tonverk/multi-template.tvpst";
    private static final String                 DRUM_TEMPLATE          = "/de/mossgrabers/convertwithmoss/templates/tonverk/drum-template.tvpst";

    private static final DestinationAudioFormat OPTIMIZED_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        24
    }, 48000, true);
    private static final DestinationAudioFormat DEFAULT_AUDIO_FORMAT   = new DestinationAudioFormat (new int []
    {
        16,
        24
    }, -1, false);
    private static final Set<Integer>           SUPPORTED_BIT_DEPTHS   = Set.of (Integer.valueOf (16), Integer.valueOf (24));


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TonverkPresetCreator (final INotifier notifier)
    {
        super ("Elektron Tonverk Preset", "Tonverk", notifier, new TonverkPresetCreatorUI ("Tonverk"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final boolean resample = this.settingsConfiguration.resampleTo2448 ();
        final boolean drum = this.isDrumOutput (multisampleSource);

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File presetFolder = this.createUniqueFilename (destinationFolder, sampleName, "");
        if (!presetFolder.mkdir ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", presetFolder.getAbsolutePath ());
            return;
        }
        final String presetName = presetFolder.getName ();

        multisampleSource.setGroups (this.combineSplitStereo (multisampleSource));

        // Rename all zones to the Elektron naming convention and ensure each zone has a valid
        // start/stop range (required for trimming)
        TonverkMultiCreator.prepareZones (presetName, multisampleSource);

        // Must be done before the samples are written!
        if (resample)
            TonverkMultiCreator.recalculateForResample (multisampleSource);

        // The samples are physically trimmed to the zone start/stop and referenced relatively
        this.writeSamples (presetFolder, multisampleSource, resample ? OPTIMIZED_AUDIO_FORMAT : DEFAULT_AUDIO_FORMAT, true);

        // The preset must be created after the samples were written since trimming updates the
        // zone/loop positions
        final TonverkPresetFile preset = drum ? this.buildDrumPreset (multisampleSource, presetName) : this.buildMultiPreset (multisampleSource);
        applyMetadata (preset, multisampleSource);

        final String presetFileName = presetName + ".tvpst";
        this.notifier.log ("IDS_NOTIFY_STORING", presetFileName);
        preset.write (new File (presetFolder, presetFileName).toPath ());

        this.progress.notifyDone ();
    }


    private boolean isDrumOutput (final IMultisampleSource multisampleSource)
    {
        return switch (this.settingsConfiguration.getOutputEngine ())
        {
            case DRUM -> true;
            case MULTI -> false;
            case AUTO -> looksLikeDrumKit (multisampleSource);
        };
    }


    private static boolean looksLikeDrumKit (final IMultisampleSource multisampleSource)
    {
        final String category = multisampleSource.getMetadata ().getCategory ();
        if (category != null)
        {
            final String lowerCategory = category.toLowerCase ();
            if (lowerCategory.contains ("drum") || lowerCategory.contains ("perc"))
                return true;
        }

        // Heuristic: a small number of (mostly) single-key zones
        final List<ISampleZone> zones = flattenZones (multisampleSource);
        if (zones.isEmpty () || zones.size () > DRUM_VOICE_COUNT)
            return false;
        for (final ISampleZone zone: zones)
            if (zone.getKeyHigh () - zone.getKeyLow () > 1)
                return false;
        return true;
    }


    private TonverkPresetFile buildMultiPreset (final IMultisampleSource multisampleSource) throws IOException
    {
        final TonverkPresetFile preset = loadTemplate (MULTI_TEMPLATE);
        preset.machine = Machine.MULTI;

        final String prefix = Machine.MULTI.getParameterPrefix ();
        final ISampleZone reference = firstZone (multisampleSource);
        if (reference != null)
        {
            applyAmplitudeEnvelope (preset, reference.getAmplitudeEnvelopeModulator ().getSource (), prefix);
            reference.getFilter ().ifPresent (filter -> applyFilter (preset, filter, prefix));
            applyGainAndPanning (preset, reference.getGain (), reference.getPanning (), prefix);
        }

        // Re-use the elmulti mapping builder: the key-zone structure is identical
        final TonverkMultiFile mapping = TonverkMultiCreator.createPreset (multisampleSource);
        preset.mappingSlotName = mapping.name;
        preset.keyZones.clear ();
        preset.keyZones.addAll (mapping.keyZones);
        return preset;
    }


    private TonverkPresetFile buildDrumPreset (final IMultisampleSource multisampleSource, final String presetName) throws IOException
    {
        final TonverkPresetFile preset = loadTemplate (DRUM_TEMPLATE);
        preset.machine = Machine.DRUM;
        preset.mappingSlotName = presetName;
        preset.keyZones.clear ();

        final List<ISampleZone> drumZones = flattenZones (multisampleSource);
        if (drumZones.isEmpty ())
            return preset;
        if (drumZones.size () > DRUM_VOICE_COUNT)
            this.notifier.log ("IDS_TONVERK_DRUM_LIMIT", Integer.toString (drumZones.size ()), Integer.toString (DRUM_VOICE_COUNT));

        // The Drum machine always has exactly eight voices/zones. Map the available drums to the
        // first voices and, if there are fewer than eight, pad the remaining voices by cycling
        // through the drums placed on free keys above the used range.
        int padKey = DEFAULT_DRUM_ROOT;
        for (final ISampleZone zone: drumZones)
            padKey = Math.max (padKey, zone.getKeyRoot ());
        padKey++;

        for (int voice = 0; voice < DRUM_VOICE_COUNT; voice++)
        {
            final boolean mapped = voice < drumZones.size ();
            final ISampleZone zone = drumZones.get (mapped ? voice : voice % drumZones.size ());
            final String voicePrefix = Machine.DRUM.getParameterPrefix () + "_voice" + voice;
            applyAmplitudeEnvelope (preset, zone.getAmplitudeEnvelopeModulator ().getSource (), voicePrefix);
            zone.getFilter ().ifPresent (filter -> applyFilter (preset, filter, voicePrefix));
            applyGainAndPanning (preset, zone.getGain (), zone.getPanning (), voicePrefix);

            final int key = Math.clamp (mapped ? zone.getKeyRoot () : padKey++, 0, 127);
            preset.keyZones.add (createDrumKeyZone (zone, key));
        }
        return preset;
    }


    private static TonverkKeyZone createDrumKeyZone (final ISampleZone zone, final int key)
    {
        final TonverkKeyZone keyZone = new TonverkKeyZone ();
        keyZone.pitch = key;
        keyZone.keyCenter = key - zone.getTuning ();

        final TonverkVelocityLayer velocityLayer = new TonverkVelocityLayer ();
        velocityLayer.velocity = DEFAULT_VELOCITY;
        keyZone.velocityLayers.add (velocityLayer);

        final TonverkSampleSlot sampleSlot = new TonverkSampleSlot ();
        sampleSlot.sample = createSafeFilename (zone.getName ()) + ".wav";
        if (TonverkMultiCreator.hasLoop (zone))
        {
            final ISampleLoop loop = zone.getLoops ().get (0);
            sampleSlot.loopMode = "Forward";
            sampleSlot.loopStart = Integer.valueOf (Math.max (0, loop.getStart ()));
            sampleSlot.loopEnd = Integer.valueOf (loop.getEnd ());
            final int crossfade = loop.getCrossfadeInSamples ();
            if (crossfade > 0)
                sampleSlot.loopCrossfade = Integer.valueOf (crossfade);
            // Keep looping during release unless this is a sustain loop (loop until release)
            sampleSlot.keepLoopingOnRelease = Boolean.valueOf (!loop.isLoopUntilRelease ());
        }
        else
            sampleSlot.loopMode = "Off";
        velocityLayer.sampleSlots.add (sampleSlot);
        return keyZone;
    }


    private static void applyAmplitudeEnvelope (final TonverkPresetFile preset, final IEnvelope envelope, final String prefix)
    {
        // Always write an ADSR envelope; a percussive sound is represented with a sustain of 0
        put (preset, prefix + "_amp_mode", "2");
        put (preset, prefix + "_amp_env_attack", TonverkValues.attackTimeToNormalized (envelope.getAttackTime ()));
        put (preset, prefix + "_amp_env_hold", 0.0);
        put (preset, prefix + "_amp_env_decay", TonverkValues.decayTimeToNormalized (envelope.getDecayTime ()));
        final double sustain = envelope.getSustainLevel ();
        put (preset, prefix + "_amp_env_sustain", TonverkValues.clampNormalized (sustain < 0 ? 1.0 : sustain));
        put (preset, prefix + "_amp_env_release", TonverkValues.releaseTimeToNormalized (envelope.getReleaseTime ()));
    }


    private static void applyFilter (final TonverkPresetFile preset, final IFilter filter, final String prefix)
    {
        put (preset, prefix + "_filter_frequency", TonverkValues.cutoffToNormalized (filter.getCutoff ()));
        put (preset, prefix + "_filter_resonance", TonverkValues.clampNormalized (filter.getResonance ()));
        put (preset, prefix + "_filter_type", filterTypeToMorph (filter.getType ()));

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final IEnvelope filterEnvelope = cutoffModulator.getSource ();
        put (preset, prefix + "_filter_env_delay", TonverkValues.delayTimeToNormalized (filterEnvelope.getDelayTime ()));
        put (preset, prefix + "_filter_env_attack", TonverkValues.attackTimeToNormalized (filterEnvelope.getAttackTime ()));
        put (preset, prefix + "_filter_env_decay", TonverkValues.decayTimeToNormalized (filterEnvelope.getDecayTime ()));
        final double sustain = filterEnvelope.getSustainLevel ();
        put (preset, prefix + "_filter_env_sustain", TonverkValues.clampNormalized (sustain < 0 ? 0.0 : sustain));
        put (preset, prefix + "_filter_env_release", TonverkValues.releaseTimeToNormalized (filterEnvelope.getReleaseTime ()));
        // The depth is stored bipolar with 0.5 as the center (no modulation)
        put (preset, prefix + "_filter_env_depth", TonverkValues.clampNormalized (cutoffModulator.getDepth () / 2.0 + 0.5));
    }


    private static void applyGainAndPanning (final TonverkPresetFile preset, final double gainDecibel, final double panning, final String prefix)
    {
        final double volume = gainDecibel <= -120.0 ? 0.0 : Math.pow (10.0, gainDecibel / 20.0);
        put (preset, prefix + "_volume", TonverkValues.clampNormalized (volume));
        put (preset, prefix + "_pan", TonverkValues.clampNormalized (panning / 2.0 + 0.5));
    }


    private static double filterTypeToMorph (final FilterType type)
    {
        return switch (type)
        {
            case HIGH_PASS -> 1.0;
            case BAND_PASS -> 0.5;
            // LOW_PASS and BAND_REJECTION (the Tonverk has no band-rejection) map to low-pass
            default -> 0.0;
        };
    }


    private static void applyMetadata (final TonverkPresetFile preset, final IMultisampleSource multisampleSource)
    {
        final IMetadata metadata = multisampleSource.getMetadata ();
        final String category = metadata.getCategory ();
        preset.category = category == null ? "" : category;

        preset.tags.clear ();
        final String [] keywords = metadata.getKeywords ();
        if (keywords != null)
            for (final String keyword: keywords)
                if (keyword != null && !keyword.isBlank ())
                    preset.tags.add (keyword);
    }


    private static TonverkPresetFile loadTemplate (final String resourcePath) throws IOException
    {
        final TonverkPresetFile preset = new TonverkPresetFile ();
        try (final InputStream inputStream = TonverkPresetCreator.class.getResourceAsStream (resourcePath))
        {
            if (inputStream == null)
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_LOAD_FILE", resourcePath));
            final List<String> lines = new ArrayList<> ();
            try (final BufferedReader reader = new BufferedReader (new InputStreamReader (inputStream, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine ()) != null)
                    lines.add (line);
            }
            preset.parse (lines);
        }
        return preset;
    }


    private static void put (final TonverkPresetFile preset, final String key, final double value)
    {
        preset.parameters.put (key, Double.toString (value));
    }


    private static void put (final TonverkPresetFile preset, final String key, final String value)
    {
        preset.parameters.put (key, value);
    }


    private static ISampleZone firstZone (final IMultisampleSource multisampleSource)
    {
        for (final IGroup group: multisampleSource.getGroups ())
            if (!group.getSampleZones ().isEmpty ())
                return group.getSampleZones ().get (0);
        return null;
    }


    private static List<ISampleZone> flattenZones (final IMultisampleSource multisampleSource)
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getGroups ())
            zones.addAll (group.getSampleZones ());
        return zones;
    }


    /** {@inheritDoc} */
    @Override
    protected void rewriteFile (final IMultisampleSource multisampleSource, final ISampleZone zone, final OutputStream outputStream, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        final ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
            return;

        final WaveFile wavFile = AudioFileUtils.convertToWav (sampleData, destinationFormat);
        if (wavFile.getDataChunk () == null)
            throw new IOException (Functions.getMessage ("IDS_WAV_CONVERSION_FAILED", zone.getName ()));

        if (trim)
        {
            trimStartToEnd (wavFile, zone);
            TonverkMultiCreator.clampLoops (zone);
        }

        if (this.settingsConfiguration.isUpdateBroadcastAudioChunk ())
            updateBroadcastAudioChunk (multisampleSource.getMetadata (), wavFile);
        if (this.settingsConfiguration.isUpdateInstrumentChunk ())
            updateInstrumentChunk (zone, wavFile);
        // Only write a sample chunk when there is a loop; the Tonverk WAV parser is strict
        if (this.settingsConfiguration.isUpdateSampleChunk () && TonverkMultiCreator.hasLoop (zone))
            updateSampleChunk (zone, wavFile);
        if (this.settingsConfiguration.isRemoveJunkChunks ())
            wavFile.removeChunks (CommonRiffChunkId.JUNK_ID, CommonRiffChunkId.JUNK2_ID, WaveRiffChunkId.FILLER_ID, WaveRiffChunkId.MD5_ID);

        wavFile.write (outputStream);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        if (detectSettings.reduceBitDepth <= 0 || SUPPORTED_BIT_DEPTHS.contains (Integer.valueOf (detectSettings.reduceBitDepth)))
            return true;
        this.notifier.log ("IDS_PROCESSING_REDUCE_BITE_DEPTH_NOT_SUPPORTED", Integer.toString (detectSettings.reduceBitDepth), "16, 24");
        return false;
    }
}
