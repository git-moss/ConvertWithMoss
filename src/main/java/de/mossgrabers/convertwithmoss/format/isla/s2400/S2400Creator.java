// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.isla.s2400;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.FileUtils;


/**
 * Creator for ISLA Instruments S2400 kit files (file ending <i>.kit</i>). A kit is written into its
 * own folder which contains the kit file (named after the folder) plus one WAV file per pad. The
 * audio is converted to the device native 48kHz / 16-bit format and loop points are stored in the
 * WAV 'smpl' chunk, which is where the S2400 reads them from.
 *
 * @author Jürgen Moßgraber
 */
public class S2400Creator extends AbstractWavCreator<WavChunkSettingsUI>
{
    /** The S2400 audio engine is 48kHz / 16-bit, which is the optimal format for playback. */
    private static final int                    NATIVE_SAMPLE_RATE       = 48000;
    private static final DestinationAudioFormat DESTINATION_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, NATIVE_SAMPLE_RATE, false);


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public S2400Creator (final INotifier notifier)
    {
        super ("ISLA S2400", "S2400", notifier, new WavChunkSettingsUI ("S2400", false, true, true, false));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final List<ISampleZone> zones = flattenZones (multisampleSource);
        if (zones.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, multisampleSource.getName (), "-");
            return;
        }

        List<ISampleZone> kitZones = zones;
        if (zones.size () > S2400Constants.MAX_TRACKS)
        {
            this.notifier.logError ("IDS_S2400_TOO_MANY_PADS", Integer.toString (S2400Constants.MAX_TRACKS), Integer.toString (zones.size ()));
            kitZones = zones.subList (0, S2400Constants.MAX_TRACKS);
        }

        final String kitName = FileUtils.createSafeFilename (multisampleSource.getName ());
        final File kitFolder = this.createUniqueFilename (destinationFolder, kitName, "");
        if (!kitFolder.exists () && !kitFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", kitFolder.getAbsolutePath ());
            return;
        }

        // Down-sample only the (rare) samples above the native rate, so the loop points in the WAV
        // 'smpl' chunk match the written audio. Samples at 44.1kHz or 48kHz are kept unchanged.
        recalculateAllSamplePositions (multisampleSource, NATIVE_SAMPLE_RATE, true);

        // Write only the WAV files of the kit zones - the zones beyond the last pad are not
        // referenced by the kit file. The track names in the kit file are derived the same way, so
        // they match the file names on disk.
        this.writeSamples (kitFolder, multisampleSource, kitZones, DESTINATION_AUDIO_FORMAT);

        final byte [] kit = this.createKit (kitZones);
        final File kitFile = new File (kitFolder, kitFolder.getName () + S2400Constants.ENDING_KIT);
        this.notifier.log ("IDS_NOTIFY_STORING", kitFile.getAbsolutePath ());
        Files.write (kitFile.toPath (), kit);

        this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    protected void additionalProcessing (final IMultisampleSource multisampleSource, final ISampleZone zone, final WaveFile wavFile)
    {
        super.additionalProcessing (multisampleSource, zone, wavFile);

        // The S2400 reads loop points from the WAV 'smpl' chunk. Make sure it is always written when
        // the zone is looped, even if the CLI sample chunk option is off (its default resets it).
        if (!this.settingsConfiguration.isUpdateSampleChunk () && !zone.getLoops ().isEmpty ())
            updateSampleChunk (zone, wavFile);
    }


    /**
     * Collect all sample zones of all groups into a single list.
     *
     * @param multisampleSource The multi-sample
     * @return The zones
     */
    private static List<ISampleZone> flattenZones (final IMultisampleSource multisampleSource)
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getSampleData () != null && zone.getSampleData ().isPresent ())
                    zones.add (zone);
        return zones;
    }


    /**
     * Create the binary content of the kit file.
     *
     * @param zones The sample zones, one per pad
     * @return The kit file content
     * @throws IOException Could not read the sample metadata
     */
    private byte [] createKit (final List<ISampleZone> zones) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        writeU32Record (out, S2400Constants.FIELD_VERSION, S2400Constants.VERSION_VALUE);
        writeU32Record (out, S2400Constants.FIELD_TRACK_COUNT, zones.size ());
        writeU32Record (out, S2400Constants.FIELD_HEADER_MARKER, 1);

        for (int i = 0; i < zones.size (); i++)
            this.writeTrack (out, i, zones.get (i));

        return out.toByteArray ();
    }


    /**
     * Write one track (pad) block.
     *
     * @param out The output stream
     * @param padIndex The zero-based pad index
     * @param zone The sample zone
     * @throws IOException Could not read the sample metadata
     */
    private void writeTrack (final ByteArrayOutputStream out, final int padIndex, final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
        final int sampleRate = audioMetadata.getSampleRate ();
        final int originalFrames = audioMetadata.getNumberOfSamples ();
        final int frames = sampleRate > NATIVE_SAMPLE_RATE ? (int) Math.round (originalFrames * (double) NATIVE_SAMPLE_RATE / sampleRate) : originalFrames;
        final double durationSeconds = sampleRate > 0 ? (double) originalFrames / sampleRate : 0;
        final boolean stereo = audioMetadata.getChannels () > 1;

        final int gainDb = (int) Math.round (zone.getGain ());
        final int pitchFineCents = (int) Math.round (zone.getTuning () * 100.0);
        final int chokeGroup = Math.max (0, zone.getExclusiveGroup ());
        final int gate = zone.isOneShot () ? 0 : 1;
        final int mixdown = stereo ? S2400Constants.MIXDOWN_STEREO : S2400Constants.MIXDOWN_MONO_LR;
        final int secondChannel = stereo ? 1 : 0;

        writeU32Record (out, S2400Constants.FIELD_TRACK_INDEX, padIndex);
        writeStringRecord (out, S2400Constants.FIELD_TRACK_NAME, FileUtils.createSafeFilename (zone.getName ()));
        writeU32Record (out, S2400Constants.FIELD_COLOR, S2400Constants.DEFAULT_COLOR);
        writeI32Record (out, S2400Constants.FIELD_RESERVED_53, 0);
        writeU32Record (out, S2400Constants.FIELD_RESERVED_36, 2);
        writeI32Record (out, S2400Constants.FIELD_TRACK_GAIN_DB, gainDb);
        writeU32Record (out, S2400Constants.FIELD_ENVELOPE_STYLE, S2400Constants.ENVELOPE_HIFI);
        writeU32Record (out, S2400Constants.FIELD_OUTPUT_CHANNEL, 0);
        writeU32Record (out, S2400Constants.FIELD_OUTPUT_CHANNEL_2, secondChannel);
        writeU32Record (out, S2400Constants.FIELD_CHOKE_GROUP, chokeGroup);
        writeU32Record (out, S2400Constants.FIELD_TRIGGER_GROUP, 0);
        writeU32Record (out, S2400Constants.FIELD_BIT_REDUCTION, 0);
        writeU32Record (out, S2400Constants.FIELD_RESAMPLER, S2400Constants.DEFAULT_RESAMPLER);
        writeU32Record (out, S2400Constants.FIELD_MIXDOWN, mixdown);
        writeU32Record (out, S2400Constants.FIELD_RESERVED_49, 0);
        writeU32Record (out, S2400Constants.FIELD_RESERVED_57, 0);
        writeU32Record (out, S2400Constants.FIELD_RESERVED_55, 0);
        writeU32Record (out, S2400Constants.FIELD_GATE_MODE, gate);
        writeU32Record (out, S2400Constants.FIELD_STOP_ON_MUTE, 0);

        for (int slot = 0; slot < S2400Constants.SLICE_SLOT_COUNT; slot++)
        {
            writeU32Record (out, S2400Constants.FIELD_SLICE_INDEX, slot);
            if (slot == S2400Constants.MAIN_SLICE_INDEX)
                writeMainSlot (out, zone, frames, durationSeconds);
            else
                writeDefaultSlice (out, frames);
        }
    }


    /**
     * Write the main slice slot, which carries the performance parameters.
     *
     * @param out The output stream
     * @param zone The sample zone
     * @param frames The number of frames in the written sample
     * @param durationSeconds The length of the sample in seconds
     */
    private static void writeMainSlot (final ByteArrayOutputStream out, final ISampleZone zone, final int frames, final double durationSeconds)
    {
        final int end = Math.max (0, frames - 1);

        int filterMode = S2400Constants.FILTER_LOW_PASS;
        int resonance = 0;
        int cutoffHertz = S2400Constants.CUTOFF_MAX_HERTZ;
        final Optional<IFilter> optionalFilter = zone.getFilter ();
        if (optionalFilter.isPresent ())
        {
            final IFilter filter = optionalFilter.get ();
            filterMode = switch (filter.getType ())
            {
                case HIGH_PASS -> S2400Constants.FILTER_HIGH_PASS;
                case BAND_PASS, BAND_REJECTION -> S2400Constants.FILTER_BAND_PASS;
                default -> S2400Constants.FILTER_LOW_PASS;
            };
            cutoffHertz = (int) Math.clamp (Math.round (filter.getCutoff ()), S2400Constants.CUTOFF_MIN_HERTZ, S2400Constants.CUTOFF_MAX_HERTZ);
            resonance = (int) Math.round (Math.clamp (filter.getResonance (), 0.0, 1.0) * S2400Constants.RANGE_8_BIT);
        }

        writeU32Record (out, S2400Constants.FIELD_LEVEL, S2400Constants.RANGE_8_BIT);
        writeU32Record (out, S2400Constants.FIELD_FILTER_MODE, filterMode);
        writeU32Record (out, S2400Constants.FIELD_FILTER_RESONANCE, resonance);
        writeU32Record (out, S2400Constants.FIELD_FILTER_CUTOFF, cutoffHertz);
        writeI32Record (out, S2400Constants.FIELD_PITCH_FINE, (int) Math.round (zone.getTuning () * 100.0));
        writeU32Record (out, S2400Constants.FIELD_SLICE_START, 0);
        writeU32Record (out, S2400Constants.FIELD_SLICE_END, end);
        writeU32Record (out, S2400Constants.FIELD_RESERVED_34, 0);
        writeU32Record (out, S2400Constants.FIELD_LOOP_START, 0);
        writeU32Record (out, S2400Constants.FIELD_LOOP_END, end);
        writeU32Record (out, S2400Constants.FIELD_RESERVED_50, 0);

        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        writeVolumeEnvelope (out, amplitudeEnvelope, durationSeconds);
        writeDefaultEnvelope (out, 1);
    }


    /**
     * Write a default (whole-sample) multi-slice slot.
     *
     * @param out The output stream
     * @param frames The number of frames in the written sample
     */
    private static void writeDefaultSlice (final ByteArrayOutputStream out, final int frames)
    {
        final int end = Math.max (0, frames - 1);

        writeU32Record (out, S2400Constants.FIELD_LEVEL, S2400Constants.RANGE_8_BIT);
        writeU32Record (out, S2400Constants.FIELD_FILTER_MODE, S2400Constants.FILTER_LOW_PASS);
        writeU32Record (out, S2400Constants.FIELD_FILTER_RESONANCE, 0);
        writeU32Record (out, S2400Constants.FIELD_FILTER_CUTOFF, S2400Constants.RANGE_10_BIT);
        writeI32Record (out, S2400Constants.FIELD_PITCH_FINE, 0);
        writeU32Record (out, S2400Constants.FIELD_SLICE_START, 0);
        writeU32Record (out, S2400Constants.FIELD_SLICE_END, end);
        writeU32Record (out, S2400Constants.FIELD_RESERVED_34, 0);
        writeU32Record (out, S2400Constants.FIELD_LOOP_START, 0);
        writeU32Record (out, S2400Constants.FIELD_LOOP_END, end);
        writeU32Record (out, S2400Constants.FIELD_RESERVED_50, 0);

        for (int envelope = 0; envelope < S2400Constants.HIFI_ENVELOPE_COUNT; envelope++)
        {
            writeU32Record (out, S2400Constants.FIELD_ENV_INDEX, envelope);
            writeU32Record (out, S2400Constants.FIELD_ENV_ATTACK, 0);
            writeU32Record (out, S2400Constants.FIELD_ENV_ATTACK_HOLD, 0);
            writeU32Record (out, S2400Constants.FIELD_ENV_DECAY, 0);
            writeU32Record (out, S2400Constants.FIELD_ENV_SUSTAIN, S2400Constants.RANGE_10_BIT);
            writeU32Record (out, S2400Constants.FIELD_ENV_SUSTAIN_HOLD, S2400Constants.RANGE_10_BIT);
            writeU32Record (out, S2400Constants.FIELD_ENV_RELEASE, 0);
            writeShortBlobRecord (out, S2400Constants.FIELD_ENV_PITCH_AMOUNT, 0);
            writeShortBlobRecord (out, S2400Constants.FIELD_ENV_FILTER_AMOUNT, 0);
        }
    }


    /**
     * Write the volume envelope (HiFi envelope index 0) from the amplitude envelope of the zone.
     *
     * @param out The output stream
     * @param envelope The amplitude envelope
     * @param durationSeconds The length of the sample in seconds
     */
    private static void writeVolumeEnvelope (final ByteArrayOutputStream out, final IEnvelope envelope, final double durationSeconds)
    {
        final double sustainLevel = envelope.getSustainLevel ();
        final int sustain = sustainLevel < 0 ? S2400Constants.RANGE_10_BIT : (int) Math.round (Math.clamp (sustainLevel, 0.0, 1.0) * S2400Constants.RANGE_10_BIT);

        writeU32Record (out, S2400Constants.FIELD_ENV_INDEX, 0);
        writeU32Record (out, S2400Constants.FIELD_ENV_ATTACK, secondsToFraction (envelope.getAttackTime (), durationSeconds));
        writeU32Record (out, S2400Constants.FIELD_ENV_ATTACK_HOLD, secondsToFraction (envelope.getHoldTime (), durationSeconds));
        writeU32Record (out, S2400Constants.FIELD_ENV_DECAY, secondsToFraction (envelope.getDecayTime (), durationSeconds));
        writeU32Record (out, S2400Constants.FIELD_ENV_SUSTAIN, sustain);
        writeU32Record (out, S2400Constants.FIELD_ENV_SUSTAIN_HOLD, 0);
        writeU32Record (out, S2400Constants.FIELD_ENV_RELEASE, secondsToFraction (envelope.getReleaseTime (), durationSeconds));
        writeShortBlobRecord (out, S2400Constants.FIELD_ENV_PITCH_AMOUNT, 0);
        writeShortBlobRecord (out, S2400Constants.FIELD_ENV_FILTER_AMOUNT, 0);
    }


    /**
     * Write a default (neutral) HiFi envelope.
     *
     * @param out The output stream
     * @param index The envelope index
     */
    private static void writeDefaultEnvelope (final ByteArrayOutputStream out, final int index)
    {
        writeU32Record (out, S2400Constants.FIELD_ENV_INDEX, index);
        writeU32Record (out, S2400Constants.FIELD_ENV_ATTACK, 0);
        writeU32Record (out, S2400Constants.FIELD_ENV_ATTACK_HOLD, 0);
        writeU32Record (out, S2400Constants.FIELD_ENV_DECAY, 0);
        writeU32Record (out, S2400Constants.FIELD_ENV_SUSTAIN, S2400Constants.RANGE_10_BIT);
        writeU32Record (out, S2400Constants.FIELD_ENV_SUSTAIN_HOLD, 0);
        writeU32Record (out, S2400Constants.FIELD_ENV_RELEASE, 0);
        writeShortBlobRecord (out, S2400Constants.FIELD_ENV_PITCH_AMOUNT, 0);
        writeShortBlobRecord (out, S2400Constants.FIELD_ENV_FILTER_AMOUNT, 0);
    }


    /**
     * Convert a time in seconds to a 10-bit envelope stage value (a fraction of the sample length).
     *
     * @param seconds The time in seconds
     * @param durationSeconds The length of the sample in seconds
     * @return The stage value, 0 to 1023
     */
    private static int secondsToFraction (final double seconds, final double durationSeconds)
    {
        if (durationSeconds <= 0 || seconds <= 0)
            return 0;
        return (int) Math.round (Math.clamp (seconds / durationSeconds, 0.0, 1.0) * S2400Constants.RANGE_10_BIT);
    }


    private static void writeU32Record (final ByteArrayOutputStream out, final int id, final long value)
    {
        out.write (S2400Constants.REC_U32);
        writeU16 (out, id);
        writeU32 (out, value);
    }


    private static void writeI32Record (final ByteArrayOutputStream out, final int id, final int value)
    {
        out.write (S2400Constants.REC_I32);
        writeU16 (out, id);
        writeU32 (out, value & 0xFFFFFFFFL);
    }


    private static void writeStringRecord (final ByteArrayOutputStream out, final int id, final String value)
    {
        final byte [] bytes = value.getBytes (StandardCharsets.US_ASCII);
        out.write (S2400Constants.REC_BLOB);
        writeU16 (out, id);
        writeU32 (out, bytes.length + 1L);
        out.write (bytes, 0, bytes.length);
        out.write (0);
    }


    private static void writeShortBlobRecord (final ByteArrayOutputStream out, final int id, final int value)
    {
        out.write (S2400Constants.REC_BLOB);
        writeU16 (out, id);
        writeU32 (out, 2L);
        out.write (value & 0xFF);
        out.write (value >> 8 & 0xFF);
    }


    private static void writeU16 (final ByteArrayOutputStream out, final int value)
    {
        out.write (value & 0xFF);
        out.write (value >> 8 & 0xFF);
    }


    private static void writeU32 (final ByteArrayOutputStream out, final long value)
    {
        out.write ((int) (value & 0xFF));
        out.write ((int) (value >> 8 & 0xFF));
        out.write ((int) (value >> 16 & 0xFF));
        out.write ((int) (value >> 24 & 0xFF));
    }
}
