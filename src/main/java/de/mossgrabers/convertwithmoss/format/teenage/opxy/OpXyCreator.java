// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.teenage.opxy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for Teenage Engineering OP-XY presets. A preset is a folder with the ending
 * <i>.preset</i> which contains the description file <i>patch.json</i> and all samples as WAV
 * files.
 *
 * @author Jürgen Moßgraber
 */
public class OpXyCreator extends AbstractWavCreator<WavChunkSettingsUI>
{
    /** The device plays 16 bit / 44.1 kHz samples. */
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, 44100, false);

    private static final int                    MAX_REGIONS        = 24;

    private final ObjectMapper                  mapper             = new ObjectMapper ();


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public OpXyCreator (final INotifier notifier)
    {
        super ("Teenage Engineering OP-XY", "OPXY", notifier, new WavChunkSettingsUI ("OPXY"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String safeName = SafeFileNames.create (multisampleSource.getName ());

        final File presetFolder = this.createUniqueFilename (destinationFolder, safeName, "preset");
        if (!presetFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", presetFolder.getAbsolutePath ());
            return;
        }

        final File patchFile = new File (presetFolder, OpXyTag.PATCH_FILE);
        this.notifier.log ("IDS_NOTIFY_STORING", patchFile.getAbsolutePath ());

        final List<ISampleZone> zones = this.collectZones (multisampleSource);
        if (zones.isEmpty ())
        {
            this.notifier.logError ("IDS_OPXY_NO_REGIONS", presetFolder.getAbsolutePath ());
            return;
        }

        this.checkSampleLengths (zones);
        // Write only the samples of the collected zones: the dropped velocity layers and the
        // regions above the maximum are not referenced by the description file and their samples
        // would only occupy space on the device
        this.writeSamples (presetFolder, multisampleSource, zones, DESTINATION_FORMAT);
        Files.write (patchFile.toPath (), this.createPatch (zones).getBytes (StandardCharsets.UTF_8));

        this.progress.notifyDone ();
    }


    /**
     * Collect the zones to write. The device maps a preset only across the keyboard, therefore only
     * the zones of the first velocity layer are written; it also plays at most {@link #MAX_REGIONS}
     * regions.
     *
     * @param multisampleSource The multi-sample source
     * @return The zones sorted by their key range
     */
    private List<ISampleZone> collectZones (final IMultisampleSource multisampleSource)
    {
        // The format maps only across the keyboard: of all zones which cover the same key range
        // the one which plays at the highest velocity is kept
        final Map<Integer, ISampleZone> zonesByKey = new TreeMap<> ();
        int dropped = 0;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final Integer highKey = Integer.valueOf (Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127));
                final ISampleZone present = zonesByKey.get (highKey);
                if (present == null)
                {
                    zonesByKey.put (highKey, zone);
                    continue;
                }
                dropped++;
                if (limitToDefault (zone.getVelocityHigh (), 127) > limitToDefault (present.getVelocityHigh (), 127))
                    zonesByKey.put (highKey, zone);
            }

        if (dropped > 0)
            this.notifier.log ("IDS_OPXY_DROPPED_VELOCITY_LAYERS", Integer.toString (dropped));

        final List<ISampleZone> zones = new ArrayList<> (zonesByKey.values ());

        if (zones.size () > MAX_REGIONS)
        {
            this.notifier.log ("IDS_OPXY_TOO_MANY_REGIONS", Integer.toString (zones.size ()), Integer.toString (MAX_REGIONS));
            return zones.subList (0, MAX_REGIONS);
        }
        return zones;
    }


    /**
     * Warn about samples which are longer than the device plays.
     *
     * @param zones The zones to check
     * @throws IOException Could not read the audio metadata
     */
    private void checkSampleLengths (final List<ISampleZone> zones) throws IOException
    {
        int tooLong = 0;
        for (final ISampleZone zone: zones)
        {
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (sampleData.isEmpty ())
                continue;
            final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
            final int sampleRate = audioMetadata.getSampleRate ();
            if (sampleRate > 0 && audioMetadata.getNumberOfSamples () / (double) sampleRate > OpXyTag.MAX_SAMPLE_SECONDS)
                tooLong++;
        }
        if (tooLong > 0)
            this.notifier.log ("IDS_OPXY_SAMPLE_TOO_LONG", Integer.toString (tooLong), Integer.toString ((int) OpXyTag.MAX_SAMPLE_SECONDS));
    }


    /**
     * Create the content of the description file.
     *
     * @param zones The zones to write
     * @return The JSON document
     * @throws IOException Could not create the document
     */
    private String createPatch (final List<ISampleZone> zones) throws IOException
    {
        final ObjectNode root = this.mapper.createObjectNode ();
        root.put (OpXyTag.TAG_TYPE, OpXyTag.TYPE_MULTISAMPLER);
        root.put (OpXyTag.TAG_PLATFORM, OpXyTag.PLATFORM);
        root.put (OpXyTag.TAG_VERSION, 4);
        root.put (OpXyTag.TAG_OCTAVE, 0);

        final ISampleZone firstZone = zones.get (0);

        final ObjectNode engine = root.putObject (OpXyTag.TAG_ENGINE);
        engine.put (OpXyTag.TAG_PLAYMODE, OpXyTag.PLAYMODE_POLY);
        engine.put (OpXyTag.TAG_TRANSPOSE, 0);
        engine.put (OpXyTag.TAG_VOLUME, OpXyTag.CENTER_VALUE);
        // Full depth is the neutral state of the model; the velocity response of the device is
        // much steeper than e.g. amp_veltrack=100% of SFZ, therefore that case gets the default
        // of the device instead of the maximum
        final double velocityDepth = firstZone.getAmplitudeVelocityModulator ().getDepth ();
        engine.put (OpXyTag.TAG_VELOCITY_SENSITIVITY, velocityDepth >= 1 ? OpXyTag.DEFAULT_VELOCITY_SENSITIVITY : OpXyTag.fromFactor (velocityDepth));
        final int bendUp = firstZone.getBendUp ();
        if (bendUp > 0)
            engine.put (OpXyTag.TAG_BEND_RANGE, OpXyTag.fromFactor (bendUp / 100.0 / OpXyTag.MAX_BEND_RANGE));

        final ObjectNode envelopes = root.putObject (OpXyTag.TAG_ENVELOPE);
        putEnvelope (envelopes.putObject (OpXyTag.TAG_AMP), firstZone.getAmplitudeEnvelopeModulator ().getSource ());

        // Only a low-pass filter is written, the state variable filter of the device is assumed
        // to be one
        final Optional<IFilter> optFilter = firstZone.getFilter ();
        if (optFilter.isPresent () && optFilter.get ().getType () == FilterType.LOW_PASS)
        {
            final IFilter filter = optFilter.get ();
            putEnvelope (envelopes.putObject (OpXyTag.TAG_FILTER), filter.getCutoffEnvelopeModulator ().getSource ());

            final ObjectNode fx = root.putObject (OpXyTag.TAG_FX);
            fx.put (OpXyTag.TAG_ACTIVE, true);
            fx.put (OpXyTag.TAG_TYPE, OpXyTag.FX_TYPE_SVF);
            final int [] params = new int [OpXyTag.FX_PARAM_COUNT];
            params[OpXyTag.FX_PARAM_CUTOFF] = OpXyTag.fromFactor (MathUtils.normalizeCutoff (filter.getCutoff ()));
            params[OpXyTag.FX_PARAM_RESONANCE] = OpXyTag.fromFactor (filter.getResonance ());
            params[OpXyTag.FX_PARAM_ENVELOPE_AMOUNT] = OpXyTag.fromFactor (filter.getCutoffEnvelopeModulator ().getDepth ());
            params[OpXyTag.FX_PARAM_KEY_TRACKING] = OpXyTag.fromFactor (filter.getCutoffKeyTracking ());
            params[OpXyTag.FX_PARAM_DEFAULT_MAX] = OpXyTag.MAX_VALUE;
            final ArrayNode paramsNode = fx.putArray (OpXyTag.TAG_PARAMS);
            for (final int param: params)
                paramsNode.add (param);
        }

        final ArrayNode regions = root.putArray (OpXyTag.TAG_REGIONS);
        // The device expects the regions to cover the keyboard without gaps: each region ends at
        // its own upper key and starts one key above the previous one
        int lowKey = 0;
        for (final ISampleZone zone: zones)
        {
            final int highKey = Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127);
            regions.add (this.createRegion (zone, lowKey, highKey));
            lowKey = highKey + 1;
        }

        return this.mapper.writerWithDefaultPrettyPrinter ().writeValueAsString (root);
    }


    /**
     * Create one region from a zone.
     *
     * @param zone The zone
     * @param lowKey The lowest key of the region
     * @param highKey The highest key of the region
     * @return The region
     * @throws IOException Could not read the audio metadata
     */
    private ObjectNode createRegion (final ISampleZone zone, final int lowKey, final int highKey) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA", zone.getName (), ""));
        final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
        final int frames = audioMetadata.getNumberOfSamples ();

        final ObjectNode region = this.mapper.createObjectNode ();
        region.put (OpXyTag.TAG_SAMPLE, SafeFileNames.create (zone.getName ()) + ".wav");
        region.put (OpXyTag.TAG_FRAME_COUNT, frames);
        region.put (OpXyTag.TAG_LOW_KEY, lowKey);
        region.put (OpXyTag.TAG_HIGH_KEY, highKey);
        region.put (OpXyTag.TAG_KEY_CENTER, Math.clamp (limitToDefault (zone.getKeyRoot (), highKey), 0, 127));
        region.put (OpXyTag.TAG_GAIN, (int) Math.round (zone.getGain ()));
        region.put (OpXyTag.TAG_TUNE, (int) Math.round (zone.getTuning ()));
        region.put (OpXyTag.TAG_REVERSE, zone.isReversed ());

        final int start = Math.clamp (zone.getStart (), 0, frames);
        final int stop = zone.getStop () <= 0 ? frames : Math.clamp (zone.getStop (), start, frames);
        region.put (OpXyTag.TAG_SAMPLE_START, start);
        region.put (OpXyTag.TAG_SAMPLE_END, stop);

        final List<ISampleLoop> loops = zone.getLoops ();
        final boolean hasLoop = !loops.isEmpty ();
        region.put (OpXyTag.TAG_LOOP_ENABLED, hasLoop);
        if (hasLoop)
        {
            final ISampleLoop loop = loops.get (0);
            region.put (OpXyTag.TAG_LOOP_START, Math.clamp (loop.getStart (), 0, frames));
            region.put (OpXyTag.TAG_LOOP_END, Math.clamp (loop.getEnd (), 0, frames));
            // The device relates its cross-fade percentage to the whole sample, not to the loop
            region.put (OpXyTag.TAG_LOOP_CROSSFADE, (int) Math.round (loop.getCrossfade () * frames));
            // True keeps the loop running after note-off, false plays the part behind the loop
            region.put (OpXyTag.TAG_LOOP_ON_RELEASE, !loop.isLoopUntilRelease ());
        }
        else
        {
            region.put (OpXyTag.TAG_LOOP_START, 0);
            region.put (OpXyTag.TAG_LOOP_END, stop);
            region.put (OpXyTag.TAG_LOOP_CROSSFADE, 0);
            region.put (OpXyTag.TAG_LOOP_ON_RELEASE, false);
        }

        return region;
    }


    /**
     * Store the 4 values of an envelope. A hold phase is added to the decay.
     *
     * @param node The node to fill
     * @param envelope The envelope
     */
    private static void putEnvelope (final ObjectNode node, final IEnvelope envelope)
    {
        putEnvelopeTime (node, OpXyTag.TAG_ATTACK, envelope.getAttackTime (), 0);
        putEnvelopeTime (node, OpXyTag.TAG_DECAY, Math.max (0, envelope.getHoldTime ()) + Math.max (0, envelope.getDecayTime ()), OpXyTag.MAX_VALUE);
        node.put (OpXyTag.TAG_SUSTAIN, envelope.getSustainLevel () < 0 ? OpXyTag.MAX_VALUE : OpXyTag.fromFactor (envelope.getSustainLevel ()));
        putEnvelopeTime (node, OpXyTag.TAG_RELEASE, envelope.getReleaseTime (), 0);
    }


    private static void putEnvelopeTime (final ObjectNode node, final String name, final double time, final int defaultValue)
    {
        final int value = OpXyTag.fromEnvelopeTime (time);
        node.put (name, value < 0 ? defaultValue : value);
    }
}
