// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.polyend;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
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
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively Polyend Tracker instrument files in folders. Files must end with <i>.pti</i>.
 * A PTI file is a fixed size binary file which contains one 16-bit / 44.1kHz PCM sample (mono or
 * stereo) plus the instrument parameters.
 *
 * @author Jürgen Moßgraber
 */
public class PolyendTrackerDetector extends AbstractDetector<MetadataSettingsUI>
{
    /** A filter parked wide open is sonically transparent and therefore treated as no filter. */
    private static final double TRANSPARENT_HIGH_PASS_MAX_HERTZ = 40.0;
    private static final double TRANSPARENT_LOW_PASS_MIN_HERTZ  = 18000.0;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public PolyendTrackerDetector (final INotifier notifier)
    {
        super ("Polyend Tracker", "PolyendTracker", notifier, new MetadataSettingsUI ("PolyendTracker"), ".pti");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final byte [] data;
        try
        {
            data = Files.readAllBytes (file.toPath ());
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }

        if (data.length < PolyendTrackerConstants.AUDIO_START + PolyendTrackerConstants.CRC_SIZE || data[0] != 'T' || data[1] != 'I')
        {
            this.notifier.logError ("IDS_PTI_NOT_AN_INSTRUMENT", file.getName ());
            return Collections.emptyList ();
        }

        final ByteBuffer buffer = ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN);

        // Determine the number of channels and frames from the actual amount of audio data. The
        // length field of the file is not always accurate, so it is only used to decide between
        // mono and stereo (which differ by a factor of two).
        final int audioBytes = data.length - PolyendTrackerConstants.AUDIO_START - PolyendTrackerConstants.CRC_SIZE;
        final long declaredFrames = buffer.getInt (PolyendTrackerConstants.OFF_SAMPLE_LENGTH) & 0xFFFFFFFFL;
        final int channels = Math.abs (audioBytes - declaredFrames * 4) < Math.abs (audioBytes - declaredFrames * 2) ? 2 : 1;
        final int frames = audioBytes / (channels * 2);
        if (frames <= 0)
        {
            this.notifier.logError ("IDS_PTI_NO_AUDIO_DATA", file.getName ());
            return Collections.emptyList ();
        }

        final byte [] interleaved = deinterleaveToWav (data, PolyendTrackerConstants.AUDIO_START, frames, channels);
        final DefaultAudioMetadata audioMetadata = new DefaultAudioMetadata (channels, PolyendTrackerConstants.SAMPLE_RATE, PolyendTrackerConstants.BIT_RESOLUTION, frames);

        final String name = FileUtils.getNameWithoutType (file);
        final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, name);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name);

        final List<ISampleZone> zones = createZones (buffer, audioMetadata, interleaved, frames, name);
        if (zones.isEmpty ())
        {
            this.notifier.logError ("IDS_PTI_NO_AUDIO_DATA", file.getName ());
            return Collections.emptyList ();
        }

        final IGroup group = new DefaultGroup (zones);
        multisampleSource.setGroups (new ArrayList<> (Collections.singletonList (group)));

        final IMetadata metadata = multisampleSource.getMetadata ();
        this.createMetadata (metadata, (IFileBasedSampleData) null, parts);
        this.updateCreationDateTime (metadata, file);

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Create the sample zones. If the play mode is one of the slice modes and slices are present,
     * one zone is created for each slice (chromatically mapped); otherwise a single zone covering
     * the whole keyboard is created.
     *
     * @param buffer The buffer with the file content
     * @param audioMetadata The audio metadata
     * @param interleaved The interleaved (WAV-ready) audio data
     * @param frames The number of frames of the sample
     * @param name The instrument name
     * @return The zones
     */
    private static List<ISampleZone> createZones (final ByteBuffer buffer, final DefaultAudioMetadata audioMetadata, final byte [] interleaved, final int frames, final String name)
    {
        final int playmode = buffer.get (PolyendTrackerConstants.OFF_PLAYMODE) & 0xFF;
        final int numSlices = buffer.get (PolyendTrackerConstants.OFF_NUM_SLICES) & 0xFF;

        final List<ISampleZone> zones = new ArrayList<> ();
        final boolean sliced = (playmode == PolyendTrackerConstants.PLAYMODE_SLICE || playmode == PolyendTrackerConstants.PLAYMODE_BEAT_SLICE) && numSlices > 0;
        if (sliced)
        {
            // Each slice becomes a self-contained sample mapped chromatically to one key, starting
            // at the default root note. The audio is trimmed to the slice region so that there is
            // no redundant audio data.
            final int channels = audioMetadata.getChannels ();
            final int bytesPerFrame = channels * 2;
            for (int i = 0; i < numSlices; i++)
            {
                final int start = PolyendTrackerValueConverter.normalizedToFrame (buffer.getShort (PolyendTrackerConstants.OFF_SLICES + i * 2) & 0xFFFF, frames);
                final int stop = i + 1 < numSlices ? PolyendTrackerValueConverter.normalizedToFrame (buffer.getShort (PolyendTrackerConstants.OFF_SLICES + (i + 1) * 2) & 0xFFFF, frames) : frames;
                final int sliceFrames = stop - start;
                if (sliceFrames <= 0)
                    continue;

                final byte [] sliceData = new byte [sliceFrames * bytesPerFrame];
                System.arraycopy (interleaved, start * bytesPerFrame, sliceData, 0, sliceData.length);
                final DefaultAudioMetadata sliceMetadata = new DefaultAudioMetadata (channels, PolyendTrackerConstants.SAMPLE_RATE, PolyendTrackerConstants.BIT_RESOLUTION, sliceFrames);

                final ISampleZone zone = new DefaultSampleZone (name + " " + (i + 1), new InMemorySampleData (sliceMetadata, sliceData));
                final int note = Math.clamp (PolyendTrackerConstants.DEFAULT_ROOT_NOTE + i, 0, 127);
                zone.setKeyRoot (note);
                zone.setKeyLow (note);
                zone.setKeyHigh (note);
                zone.setStart (0);
                zone.setStop (sliceFrames);
                applyInstrumentParameters (zone, buffer);
                zones.add (zone);
            }
            return zones;
        }

        final ISampleZone zone = new DefaultSampleZone (name, new InMemorySampleData (audioMetadata, interleaved));
        zone.setKeyRoot (PolyendTrackerConstants.DEFAULT_ROOT_NOTE);
        zone.setKeyLow (0);
        zone.setKeyHigh (127);
        zone.setStart (PolyendTrackerValueConverter.normalizedToFrame (buffer.getShort (PolyendTrackerConstants.OFF_START) & 0xFFFF, frames));
        zone.setStop (PolyendTrackerValueConverter.normalizedToFrame (buffer.getShort (PolyendTrackerConstants.OFF_END) & 0xFFFF, frames));

        final LoopType loopType = switch (playmode)
        {
            case PolyendTrackerConstants.PLAYMODE_FORWARD_LOOP -> LoopType.FORWARDS;
            case PolyendTrackerConstants.PLAYMODE_BACKWARD_LOOP -> LoopType.BACKWARDS;
            case PolyendTrackerConstants.PLAYMODE_PINGPONG_LOOP -> LoopType.ALTERNATING;
            default -> null;
        };
        if (loopType != null)
        {
            final int loopStart = PolyendTrackerValueConverter.normalizedToFrame (buffer.getShort (PolyendTrackerConstants.OFF_LOOP1) & 0xFFFF, frames);
            final int loopEnd = PolyendTrackerValueConverter.normalizedToFrame (buffer.getShort (PolyendTrackerConstants.OFF_LOOP2) & 0xFFFF, frames);
            if (loopEnd > loopStart)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (loopType);
                loop.setStart (loopStart);
                loop.setEnd (loopEnd);
                zone.addLoop (loop);
            }
        }

        applyInstrumentParameters (zone, buffer);
        zones.add (zone);
        return zones;
    }


    /**
     * Apply the global instrument parameters (volume, panning, tuning, amplitude and pitch envelope
     * and the filter) which apply to all zones of the instrument.
     *
     * @param zone The zone to configure
     * @param buffer The buffer with the file content
     */
    private static void applyInstrumentParameters (final ISampleZone zone, final ByteBuffer buffer)
    {
        zone.setGain (PolyendTrackerValueConverter.rawVolumeToGain (buffer.get (PolyendTrackerConstants.OFF_VOLUME) & 0xFF));
        zone.setPanning (PolyendTrackerValueConverter.rawPanningToModel (buffer.getShort (PolyendTrackerConstants.OFF_PANNING)));
        zone.setTuning (PolyendTrackerValueConverter.toTuning (buffer.get (PolyendTrackerConstants.OFF_TUNE), buffer.get (PolyendTrackerConstants.OFF_FINETUNE)));

        final IEnvelope amplitudeEnvelope = readEnvelope (buffer, PolyendTrackerConstants.ENV_VOLUME);
        if (amplitudeEnvelope != null)
            zone.getAmplitudeEnvelopeModulator ().setSource (amplitudeEnvelope);

        final IEnvelope pitchEnvelope = readEnvelope (buffer, PolyendTrackerConstants.ENV_FINETUNE);
        if (pitchEnvelope != null)
        {
            zone.getPitchEnvelopeModulator ().setSource (pitchEnvelope);
            zone.getPitchEnvelopeModulator ().setDepth (1.0);
        }

        final IFilter filter = buildFilter (buffer);
        if (filter != null)
            zone.setFilter (filter);
    }


    /**
     * Build the filter from the file parameters.
     *
     * @param buffer The buffer with the file content
     * @return The filter or null if no (audible) filter is set
     */
    private static IFilter buildFilter (final ByteBuffer buffer)
    {
        if ((buffer.get (PolyendTrackerConstants.OFF_FILTER_ENABLED) & 0xFF) == 0)
            return null;

        final FilterType filterType = switch (buffer.get (PolyendTrackerConstants.OFF_FILTER_TYPE) & 0xFF)
        {
            case PolyendTrackerConstants.FILTER_LOWPASS -> FilterType.LOW_PASS;
            case PolyendTrackerConstants.FILTER_HIGHPASS -> FilterType.HIGH_PASS;
            case PolyendTrackerConstants.FILTER_BANDPASS -> FilterType.BAND_PASS;
            default -> null;
        };
        if (filterType == null)
            return null;

        final double cutoff = Math.clamp (buffer.getFloat (PolyendTrackerConstants.OFF_CUTOFF), 0.0, 1.0);
        final double hertz = PolyendTrackerValueConverter.normalizedCutoffToHertz (cutoff);

        // A filter parked wide open is sonically transparent - treat it as no filter
        if ((filterType == FilterType.HIGH_PASS && hertz <= TRANSPARENT_HIGH_PASS_MAX_HERTZ) || (filterType == FilterType.LOW_PASS && hertz >= TRANSPARENT_LOW_PASS_MIN_HERTZ))
            return null;

        final double resonance = PolyendTrackerValueConverter.rawResonanceToModel (buffer.getFloat (PolyendTrackerConstants.OFF_RESONANCE));
        final IFilter filter = new DefaultFilter (filterType, 2, hertz, resonance);

        final IEnvelope cutoffEnvelope = readEnvelope (buffer, PolyendTrackerConstants.ENV_CUTOFF);
        if (cutoffEnvelope != null)
        {
            final int base = PolyendTrackerConstants.OFF_ENVELOPES + PolyendTrackerConstants.ENV_CUTOFF * PolyendTrackerConstants.ENVELOPE_SIZE;
            final double amount = Math.clamp (buffer.getFloat (base + PolyendTrackerConstants.ENV_OFF_AMOUNT), 0.0, 1.0);
            final double depth = PolyendTrackerValueConverter.normalizedCutoffToHertz (Math.clamp (cutoff + amount, 0.0, 1.0)) - hertz;
            if (depth != 0)
            {
                filter.getCutoffEnvelopeModulator ().setSource (cutoffEnvelope);
                filter.getCutoffEnvelopeModulator ().setDepth (depth);
            }
        }

        return filter;
    }


    /**
     * Read an envelope. Returns null if the envelope is disabled or uses the LFO instead.
     *
     * @param buffer The buffer with the file content
     * @param slot The index of the envelope (0 = volume, 2 = cutoff, 5 = fine tune)
     * @return The envelope or null
     */
    private static IEnvelope readEnvelope (final ByteBuffer buffer, final int slot)
    {
        final int base = PolyendTrackerConstants.OFF_ENVELOPES + slot * PolyendTrackerConstants.ENVELOPE_SIZE;
        final boolean usesLFO = (buffer.get (base + PolyendTrackerConstants.ENV_OFF_LFO_FLAG) & 0xFF) != 0;
        final boolean enabled = (buffer.get (base + PolyendTrackerConstants.ENV_OFF_ENABLED) & 0xFF) != 0;
        if (!enabled || usesLFO)
            return null;

        final int delay = buffer.getShort (base + PolyendTrackerConstants.ENV_OFF_DELAY) & 0xFFFF;
        final int attack = buffer.getShort (base + PolyendTrackerConstants.ENV_OFF_ATTACK) & 0xFFFF;
        final int hold = buffer.getShort (base + PolyendTrackerConstants.ENV_OFF_HOLD) & 0xFFFF;
        final int decay = buffer.getShort (base + PolyendTrackerConstants.ENV_OFF_DECAY) & 0xFFFF;
        final double sustain = buffer.getFloat (base + PolyendTrackerConstants.ENV_OFF_SUSTAIN);
        final int release = buffer.getShort (base + PolyendTrackerConstants.ENV_OFF_RELEASE) & 0xFFFF;

        final IEnvelope envelope = new DefaultEnvelope ();
        if (delay > 0)
            envelope.setDelayTime (PolyendTrackerValueConverter.millisecondsToSeconds (delay));
        envelope.setAttackTime (PolyendTrackerValueConverter.millisecondsToSeconds (attack));
        if (hold > 0)
            envelope.setHoldTime (PolyendTrackerValueConverter.millisecondsToSeconds (hold));
        envelope.setDecayTime (PolyendTrackerValueConverter.millisecondsToSeconds (decay));
        envelope.setSustainLevel (Math.clamp (sustain, 0.0, 1.0));
        envelope.setReleaseTime (PolyendTrackerValueConverter.millisecondsToSeconds (release));
        return envelope;
    }


    /**
     * The audio data of a stereo sample is stored non-interleaved (the complete left channel
     * followed by the complete right channel). Convert it to the interleaved layout expected by the
     * in-memory sample data. A mono sample is returned unchanged.
     *
     * @param data The complete file content
     * @param audioStart The offset of the audio data
     * @param frames The number of frames
     * @param channels The number of channels (1 or 2)
     * @return The interleaved audio data
     */
    private static byte [] deinterleaveToWav (final byte [] data, final int audioStart, final int frames, final int channels)
    {
        if (channels == 1)
        {
            final byte [] mono = new byte [frames * 2];
            System.arraycopy (data, audioStart, mono, 0, mono.length);
            return mono;
        }

        final byte [] interleaved = new byte [frames * 4];
        final int rightOffset = audioStart + frames * 2;
        for (int i = 0; i < frames; i++)
        {
            interleaved[i * 4] = data[audioStart + i * 2];
            interleaved[i * 4 + 1] = data[audioStart + i * 2 + 1];
            interleaved[i * 4 + 2] = data[rightOffset + i * 2];
            interleaved[i * 4 + 3] = data[rightOffset + i * 2 + 1];
        }
        return interleaved;
    }
}
