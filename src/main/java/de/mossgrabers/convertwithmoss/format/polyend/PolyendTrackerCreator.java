// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.polyend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Creator for Polyend Tracker instrument files (file ending <i>.pti</i>). A PTI file holds exactly
 * one sample, therefore only one representative sample zone of the multi-sample is stored. The
 * audio is converted to 16-bit / 44.1kHz (mono or stereo).
 *
 * @author Jürgen Moßgraber
 */
public class PolyendTrackerCreator extends AbstractCreator<EmptySettingsUI>
{
    /** A PTI file always stores 16-bit / 44.1kHz audio. */
    private static final DestinationAudioFormat DESTINATION_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        PolyendTrackerConstants.BIT_RESOLUTION
    }, PolyendTrackerConstants.SAMPLE_RATE, true);

    private static final int                    DEFAULT_GRAIN_LENGTH     = 4410;
    private static final int                    GRANULAR_SHAPE_TRIANGLE  = 1;
    private static final int                    LFO_SHAPE_TRIANGLE       = 2;
    private static final int                    LFO_SPEED_S4             = 10;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public PolyendTrackerCreator (final INotifier notifier)
    {
        super ("Polyend Tracker", "PolyendTracker", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final ISampleZone zone = pickRepresentativeZone (multisampleSource);
        if (zone == null)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, multisampleSource.getName (), "-");
            return;
        }

        if (countZones (multisampleSource) > 1)
            this.notifier.log ("IDS_PTI_ONLY_ONE_SAMPLE", zone.getName ());

        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), "-");
            return;
        }

        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_AUDIO_FORMAT);
        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final int channels = formatChunk.getNumberOfChannels ();
        if (channels < 1 || channels > 2)
        {
            this.notifier.logError ("IDS_PTI_UNSUPPORTED_CHANNELS", Integer.toString (channels));
            return;
        }

        final byte [] pcm = waveFile.getDataChunk ().getData ();
        final int frames = pcm.length / (channels * 2);
        if (frames <= 0)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), "-");
            return;
        }

        final File file = this.createUniqueFilename (destinationFolder, createSafeFilename (multisampleSource.getName ()), "pti");
        this.notifier.log ("IDS_NOTIFY_STORING", file.getAbsolutePath ());

        final byte [] output = createInstrument (multisampleSource.getName (), zone, sampleData.get (), pcm, channels, frames);
        try (final FileOutputStream out = new FileOutputStream (file))
        {
            out.write (output);
        }

        this.progress.notifyDone ();
    }


    /**
     * Create the binary content of the PTI file.
     *
     * @param name The name of the instrument
     * @param zone The sample zone to store
     * @param sampleData The original sample data (used to read the original frame count)
     * @param pcm The (16-bit / 44.1kHz) interleaved PCM audio data
     * @param channels The number of channels (1 or 2)
     * @param frames The number of frames in the converted audio
     * @return The binary content
     * @throws IOException Could not read the audio metadata
     */
    private static byte [] createInstrument (final String name, final ISampleZone zone, final ISampleData sampleData, final byte [] pcm, final int channels, final int frames) throws IOException
    {
        final int audioBytes = frames * channels * 2;
        final byte [] output = new byte [PolyendTrackerConstants.AUDIO_START + audioBytes + PolyendTrackerConstants.CRC_SIZE];
        final ByteBuffer buffer = ByteBuffer.wrap (output).order (ByteOrder.LITTLE_ENDIAN);

        writeHeader (buffer);

        buffer.put (PolyendTrackerConstants.OFF_IS_ACTIVE, (byte) 1);
        buffer.put (PolyendTrackerConstants.OFF_SAMPLE_TYPE, (byte) PolyendTrackerConstants.SAMPLE_WAVE);
        writeString (buffer, PolyendTrackerConstants.OFF_SAMPLE_NAME, PolyendTrackerConstants.SAMPLE_NAME_LENGTH, createSafeFilename (name));
        buffer.putInt (PolyendTrackerConstants.OFF_SAMPLE_LENGTH, frames);
        buffer.putShort (PolyendTrackerConstants.OFF_WT_WINDOW_SIZE, (short) 2048);
        buffer.putInt (PolyendTrackerConstants.OFF_WT_WINDOW_COUNT, 0);

        // Playback range and loop. The normalized points are proportional to the sample length and
        // therefore independent of the (possibly resampled) frame count.
        final int totalFrames = sampleData.getAudioMetadata ().getNumberOfSamples () > 0 ? sampleData.getAudioMetadata ().getNumberOfSamples () : frames;
        final int start = Math.max (0, zone.getStart ());
        final int stop = zone.getStop () <= 0 ? totalFrames : zone.getStop ();
        buffer.putShort (PolyendTrackerConstants.OFF_START, (short) PolyendTrackerValueConverter.frameToNormalized (start, totalFrames));
        buffer.putShort (PolyendTrackerConstants.OFF_END, (short) PolyendTrackerValueConverter.frameToNormalized (stop, totalFrames));

        final List<ISampleLoop> loops = zone.getLoops ();
        final ISampleLoop loop = loops.isEmpty () ? null : loops.get (0);
        if (loop != null && loop.getEnd () > loop.getStart ())
        {
            buffer.put (PolyendTrackerConstants.OFF_PLAYMODE, (byte) loopPlaymode (loop));
            buffer.putShort (PolyendTrackerConstants.OFF_LOOP1, (short) PolyendTrackerValueConverter.frameToNormalized (Math.max (0, loop.getStart ()), totalFrames));
            buffer.putShort (PolyendTrackerConstants.OFF_LOOP2, (short) PolyendTrackerValueConverter.frameToNormalized (loop.getEnd (), totalFrames));
        }
        else
        {
            buffer.put (PolyendTrackerConstants.OFF_PLAYMODE, (byte) PolyendTrackerConstants.PLAYMODE_ONESHOT);
            buffer.putShort (PolyendTrackerConstants.OFF_LOOP1, (short) 0);
            buffer.putShort (PolyendTrackerConstants.OFF_LOOP2, (short) PolyendTrackerConstants.NORMALIZED_MAX);
        }

        writeEnvelopesAndLFOs (buffer, zone);
        writeFilter (buffer, zone);

        buffer.put (PolyendTrackerConstants.OFF_TUNE, (byte) PolyendTrackerValueConverter.tuningToTune (zone.getTuning ()));
        buffer.put (PolyendTrackerConstants.OFF_FINETUNE, (byte) PolyendTrackerValueConverter.tuningToFinetune (zone.getTuning ()));
        buffer.put (PolyendTrackerConstants.OFF_VOLUME, (byte) PolyendTrackerValueConverter.gainToRawVolume (zone.getGain ()));
        buffer.putShort (PolyendTrackerConstants.OFF_PANNING, (short) PolyendTrackerValueConverter.modelPanningToRaw (zone.getPanning ()));

        // Granular defaults (only relevant in granular play mode)
        buffer.putShort (PolyendTrackerConstants.OFF_GRANULAR_LENGTH, (short) DEFAULT_GRAIN_LENGTH);
        buffer.put (PolyendTrackerConstants.OFF_GRANULAR_SHAPE, (byte) GRANULAR_SHAPE_TRIANGLE);

        buffer.put (PolyendTrackerConstants.OFF_BITDEPTH, (byte) PolyendTrackerConstants.BIT_RESOLUTION);

        // Audio data. A stereo sample is stored non-interleaved (the complete left channel followed
        // by the complete right channel); a mono sample is copied unchanged. The trailing checksum
        // is left as zero which is accepted by the hardware (factory files use a zero checksum
        // too).
        writeAudio (output, pcm, channels, frames);

        return output;
    }


    /**
     * Write the 16 byte header.
     *
     * @param buffer The output buffer
     */
    private static void writeHeader (final ByteBuffer buffer)
    {
        buffer.put (0, (byte) 'T');
        buffer.put (1, (byte) 'I');
        buffer.putShort (2, (short) PolyendTrackerConstants.TYPE_INSTRUMENT);
        for (int i = 0; i < 4; i++)
        {
            buffer.put (4 + i, (byte) PolyendTrackerConstants.WRITE_FW_VERSION[i]);
            buffer.put (8 + i, (byte) PolyendTrackerConstants.WRITE_STRUCTURE_VERSION[i]);
        }
        buffer.putShort (12, (short) PolyendTrackerConstants.PARAMETER_BLOCK_SIZE);
    }


    /**
     * Write the 6 envelopes and 6 LFOs. The volume envelope is taken from the amplitude envelope of
     * the zone, the cutoff envelope from the filter (if any) and the fine tune envelope from the
     * pitch envelope (if a depth is set). All others are written with sane disabled defaults.
     *
     * @param buffer The output buffer
     * @param zone The zone
     */
    private static void writeEnvelopesAndLFOs (final ByteBuffer buffer, final ISampleZone zone)
    {
        // Disabled defaults for all automation slots
        for (int slot = 0; slot < PolyendTrackerConstants.ENVELOPE_COUNT; slot++)
            writeEnvelope (buffer, slot, null, false, 1.0);

        // The volume envelope is always enabled
        writeEnvelope (buffer, PolyendTrackerConstants.ENV_VOLUME, zone.getAmplitudeEnvelopeModulator ().getSource (), true, 1.0);

        final Optional<IFilter> optionalFilter = zone.getFilter ();
        if (optionalFilter.isPresent ())
        {
            final IFilter filter = optionalFilter.get ();
            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            if (cutoffModulator.getDepth () != 0)
            {
                final double cutoff = PolyendTrackerValueConverter.hertzToNormalizedCutoff (filter.getCutoff ());
                final double amount = Math.clamp (PolyendTrackerValueConverter.hertzToNormalizedCutoff (filter.getCutoff () + cutoffModulator.getDepth ()) - cutoff, 0.0, 1.0);
                writeEnvelope (buffer, PolyendTrackerConstants.ENV_CUTOFF, cutoffModulator.getSource (), true, amount);
            }
        }

        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
        if (pitchModulator.getDepth () != 0)
            writeEnvelope (buffer, PolyendTrackerConstants.ENV_FINETUNE, pitchModulator.getSource (), true, 1.0);

        // LFO defaults
        for (int i = 0; i < PolyendTrackerConstants.LFO_COUNT; i++)
        {
            final int base = PolyendTrackerConstants.OFF_LFOS + i * PolyendTrackerConstants.LFO_SIZE;
            buffer.put (base, (byte) LFO_SHAPE_TRIANGLE);
            buffer.put (base + 1, (byte) LFO_SPEED_S4);
        }
    }


    /**
     * Write one envelope.
     *
     * @param buffer The output buffer
     * @param slot The automation slot index
     * @param envelope The envelope (may be null for a disabled default)
     * @param enabled True to enable the envelope
     * @param amount The envelope amount (0 to 1)
     */
    private static void writeEnvelope (final ByteBuffer buffer, final int slot, final IEnvelope envelope, final boolean enabled, final double amount)
    {
        final int base = PolyendTrackerConstants.OFF_ENVELOPES + slot * PolyendTrackerConstants.ENVELOPE_SIZE;

        final double delay = envelope == null ? 0 : envelope.getDelayTime ();
        final double attack = envelope == null ? 0 : envelope.getAttackTime ();
        final double hold = envelope == null ? 0 : envelope.getHoldTime ();
        final double decay = envelope == null ? 0 : envelope.getDecayTime ();
        final double sustainValue = envelope == null ? 1.0 : envelope.getSustainLevel ();
        final double release = envelope == null ? 1.0 : envelope.getReleaseTime ();

        buffer.putFloat (base + PolyendTrackerConstants.ENV_OFF_AMOUNT, (float) Math.clamp (amount, 0.0, 1.0));
        buffer.putShort (base + PolyendTrackerConstants.ENV_OFF_DELAY, (short) PolyendTrackerValueConverter.secondsToMilliseconds (delay));
        buffer.putShort (base + PolyendTrackerConstants.ENV_OFF_ATTACK, (short) PolyendTrackerValueConverter.secondsToMilliseconds (attack));
        buffer.putShort (base + PolyendTrackerConstants.ENV_OFF_HOLD, (short) PolyendTrackerValueConverter.secondsToMilliseconds (hold));
        buffer.putShort (base + PolyendTrackerConstants.ENV_OFF_DECAY, (short) PolyendTrackerValueConverter.secondsToMilliseconds (decay));
        buffer.putFloat (base + PolyendTrackerConstants.ENV_OFF_SUSTAIN, (float) (sustainValue < 0 ? 1.0 : Math.clamp (sustainValue, 0.0, 1.0)));
        buffer.putShort (base + PolyendTrackerConstants.ENV_OFF_RELEASE, (short) PolyendTrackerValueConverter.secondsToMilliseconds (release));
        buffer.put (base + PolyendTrackerConstants.ENV_OFF_LFO_FLAG, (byte) 0);
        buffer.put (base + PolyendTrackerConstants.ENV_OFF_ENABLED, (byte) (enabled ? 1 : 0));
    }


    /**
     * Write the filter parameters. If the zone has no filter the filter is disabled and the cutoff
     * is parked wide open.
     *
     * @param buffer The output buffer
     * @param zone The zone
     */
    private static void writeFilter (final ByteBuffer buffer, final ISampleZone zone)
    {
        final Optional<IFilter> optionalFilter = zone.getFilter ();
        if (optionalFilter.isEmpty ())
        {
            buffer.putFloat (PolyendTrackerConstants.OFF_CUTOFF, 1.0f);
            buffer.put (PolyendTrackerConstants.OFF_FILTER_ENABLED, (byte) 0);
            return;
        }

        final IFilter filter = optionalFilter.get ();
        final int filterType = switch (filter.getType ())
        {
            case HIGH_PASS -> PolyendTrackerConstants.FILTER_HIGHPASS;
            case BAND_PASS, BAND_REJECTION -> PolyendTrackerConstants.FILTER_BANDPASS;
            default -> PolyendTrackerConstants.FILTER_LOWPASS;
        };
        buffer.putFloat (PolyendTrackerConstants.OFF_CUTOFF, (float) PolyendTrackerValueConverter.hertzToNormalizedCutoff (filter.getCutoff ()));
        buffer.putFloat (PolyendTrackerConstants.OFF_RESONANCE, (float) PolyendTrackerValueConverter.modelResonanceToRaw (filter.getResonance ()));
        buffer.put (PolyendTrackerConstants.OFF_FILTER_TYPE, (byte) filterType);
        buffer.put (PolyendTrackerConstants.OFF_FILTER_ENABLED, (byte) 1);
    }


    /**
     * Write the audio data into the output. A stereo sample is de-interleaved into a left and a
     * right block, a mono sample is copied unchanged.
     *
     * @param output The output array
     * @param pcm The interleaved PCM data
     * @param channels The number of channels
     * @param frames The number of frames
     */
    private static void writeAudio (final byte [] output, final byte [] pcm, final int channels, final int frames)
    {
        final int audioStart = PolyendTrackerConstants.AUDIO_START;
        if (channels == 1)
        {
            System.arraycopy (pcm, 0, output, audioStart, frames * 2);
            return;
        }

        final int rightOffset = audioStart + frames * 2;
        for (int i = 0; i < frames; i++)
        {
            output[audioStart + i * 2] = pcm[i * 4];
            output[audioStart + i * 2 + 1] = pcm[i * 4 + 1];
            output[rightOffset + i * 2] = pcm[i * 4 + 2];
            output[rightOffset + i * 2 + 1] = pcm[i * 4 + 3];
        }
    }


    /**
     * Get the play mode for a loop type.
     *
     * @param loop The loop
     * @return The play mode
     */
    private static int loopPlaymode (final ISampleLoop loop)
    {
        return switch (loop.getType ())
        {
            case BACKWARDS -> PolyendTrackerConstants.PLAYMODE_BACKWARD_LOOP;
            case ALTERNATING -> PolyendTrackerConstants.PLAYMODE_PINGPONG_LOOP;
            default -> PolyendTrackerConstants.PLAYMODE_FORWARD_LOOP;
        };
    }


    /**
     * Write a null-padded ASCII string of the given length.
     *
     * @param buffer The output buffer
     * @param offset The offset
     * @param length The (fixed) number of bytes to occupy
     * @param value The string value
     */
    private static void writeString (final ByteBuffer buffer, final int offset, final int length, final String value)
    {
        final byte [] bytes = value.getBytes (StandardCharsets.US_ASCII);
        final int count = Math.min (bytes.length, length - 1);
        for (int i = 0; i < count; i++)
            buffer.put (offset + i, bytes[i]);
    }


    /**
     * Pick the sample zone to store. As a PTI file holds only one sample, the zone whose key range
     * contains the default root note is preferred, otherwise the first zone is used.
     *
     * @param multisampleSource The multi-sample
     * @return The zone or null if there is none
     */
    private static ISampleZone pickRepresentativeZone (final IMultisampleSource multisampleSource)
    {
        ISampleZone first = null;
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (first == null)
                    first = zone;
                if (zone.getKeyLow () <= PolyendTrackerConstants.DEFAULT_ROOT_NOTE && PolyendTrackerConstants.DEFAULT_ROOT_NOTE <= zone.getKeyHigh ())
                    return zone;
            }
        return first;
    }


    /**
     * Count the total number of sample zones.
     *
     * @param multisampleSource The multi-sample
     * @return The number of zones
     */
    private static int countZones (final IMultisampleSource multisampleSource)
    {
        int count = 0;
        for (final IGroup group: multisampleSource.getGroups ())
            count += group.getSampleZones ().size ();
        return count;
    }
}
