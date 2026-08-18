// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.isla.s2400;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively ISLA Instruments S2400 kit files in folders. Files must end with
 * <i>.kit</i>. A kit file describes a set of pads (tracks), each referencing a WAV file located in
 * the same folder plus its performance parameters (level, filter, pitch, envelopes, choke group).
 *
 * @author Jürgen Moßgraber
 */
public class S2400Detector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public S2400Detector (final INotifier notifier)
    {
        super ("ISLA S2400", "S2400", notifier, new MetadataSettingsUI ("S2400"), S2400Constants.ENDING_KIT);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final byte [] data;
        try
        {
            data = Files.readAllBytes (sourceFile.toPath ());
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }

        final List<S2400Record> records = parseRecords (data);
        if (records.isEmpty () || records.get (0).id () != S2400Constants.FIELD_VERSION || records.get (0).value () != S2400Constants.VERSION_VALUE)
        {
            this.notifier.logError ("IDS_S2400_NOT_A_KIT", sourceFile.getName ());
            return Collections.emptyList ();
        }

        final List<S2400Track> tracks = groupTracks (records);
        if (tracks.isEmpty ())
        {
            this.notifier.logError ("IDS_S2400_NO_TRACKS", sourceFile.getName ());
            return Collections.emptyList ();
        }

        final File folder = sourceFile.getParentFile ();
        final IGroup group = new DefaultGroup ("Kit");
        for (final S2400Track track: tracks)
        {
            final ISampleZone zone = this.createZone (folder, track);
            if (zone != null)
                group.addSampleZone (zone);
        }

        if (group.getSampleZones ().isEmpty ())
        {
            this.notifier.logError ("IDS_S2400_NO_TRACKS", sourceFile.getName ());
            return Collections.emptyList ();
        }

        final String name = FileUtils.getNameWithoutType (sourceFile);
        return Collections.singletonList (this.createMultisampleSource (sourceFile, name, Collections.singletonList (group)));
    }


    /**
     * Create a sample zone for one track by loading its WAV file and applying the parameters.
     *
     * @param folder The folder which contains the WAV files
     * @param track The parsed track parameters
     * @return The zone or null if the WAV file could not be loaded
     */
    private ISampleZone createZone (final File folder, final S2400Track track)
    {
        if (track.name == null || track.name.isBlank ())
            return null;

        final File sampleFile = new File (folder, track.name + ".wav");
        if (!AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
            return null;

        final ISampleData sampleData;
        try
        {
            sampleData = new WavFileSampleData (sampleFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return null;
        }

        final ISampleZone zone = new DefaultSampleZone (track.name, sampleData);

        // Map the pad to a single MIDI key, following the default sample track MIDI map (A1 = 36)
        final int note = Math.clamp (S2400Constants.DEFAULT_ROOT_NOTE + (long) track.padIndex, 0, 127);
        zone.setKeyRoot (note);
        zone.setKeyLow (note);
        zone.setKeyHigh (note);
        zone.setVelocityLow (1);
        zone.setVelocityHigh (127);

        zone.setGain (track.gainDb);
        zone.setTuning (track.pitchFineCents / 100.0);
        zone.setOneShot (!track.gated);
        if (track.chokeGroup > 0)
            zone.setExclusiveGroup (track.chokeGroup);

        // The loop points are read from the WAV 'smpl' chunk, which is where the S2400 stores them
        try
        {
            sampleData.addZoneData (zone, false, true);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }

        double durationSeconds = 0;
        try
        {
            final int frames = sampleData.getAudioMetadata ().getNumberOfSamples ();
            final int sampleRate = sampleData.getAudioMetadata ().getSampleRate ();
            if (sampleRate > 0)
                durationSeconds = (double) frames / sampleRate;
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }

        applyFilter (zone, track);
        applyEnvelope (zone, track, durationSeconds);

        return zone;
    }


    /**
     * Apply the filter parameters to the zone. A low-pass filter parked fully open is treated as no
     * filter.
     *
     * @param zone The zone
     * @param track The parsed track parameters
     */
    private static void applyFilter (final ISampleZone zone, final S2400Track track)
    {
        final int cutoffHertz = track.filterCutoffHertz;
        final FilterType filterType = switch (track.filterMode)
        {
            case S2400Constants.FILTER_HIGH_PASS -> FilterType.HIGH_PASS;
            case S2400Constants.FILTER_BAND_PASS -> FilterType.BAND_PASS;
            default -> FilterType.LOW_PASS;
        };

        // A low-pass parked at the top or a high-pass parked at the bottom is sonically transparent
        if (filterType == FilterType.LOW_PASS && cutoffHertz >= S2400Constants.CUTOFF_MAX_HERTZ)
            return;
        if (filterType == FilterType.HIGH_PASS && cutoffHertz <= S2400Constants.CUTOFF_MIN_HERTZ)
            return;

        final double resonance = Math.clamp (track.filterResonance / (double) S2400Constants.RANGE_8_BIT, 0.0, 1.0);
        zone.setFilter (new DefaultFilter (filterType, 2, cutoffHertz, resonance));
    }


    /**
     * Apply the volume envelope to the zone. For a HiFi envelope the stage times are stored as a
     * fraction of the total sample length and are converted back to seconds. A classic envelope is
     * approximated with its decay stage.
     *
     * @param zone The zone
     * @param track The parsed track parameters
     * @param durationSeconds The length of the sample in seconds
     */
    private static void applyEnvelope (final ISampleZone zone, final S2400Track track, final double durationSeconds)
    {
        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();

        if (track.envelopeStyle == S2400Constants.ENVELOPE_CLASSIC)
        {
            // The classic envelope holds at full volume then releases. A maximum decay disables the
            // envelope, everything below shortens the release proportionally. The absolute times are
            // defined by an (encrypted) firmware table, therefore this is an approximation.
            if (track.classicDecay >= S2400Constants.CLASSIC_DECAY_MAX)
                return;
            final double release = (1.0 - track.classicDecay / (double) S2400Constants.CLASSIC_DECAY_MAX) * durationSeconds;
            amplitudeEnvelope.setReleaseTime (release);
            return;
        }

        amplitudeEnvelope.setAttackTime (fractionToSeconds (track.envAttack, durationSeconds));
        amplitudeEnvelope.setHoldTime (fractionToSeconds (track.envAttackHold, durationSeconds));
        amplitudeEnvelope.setDecayTime (fractionToSeconds (track.envDecay, durationSeconds));
        amplitudeEnvelope.setSustainLevel (Math.clamp (track.envSustain / (double) S2400Constants.RANGE_10_BIT, 0.0, 1.0));
        amplitudeEnvelope.setReleaseTime (fractionToSeconds (track.envRelease, durationSeconds));
    }


    /**
     * Convert a 10-bit envelope stage value (a fraction of the total sample length) to seconds.
     *
     * @param value The stage value, 0 to 1023
     * @param durationSeconds The length of the sample in seconds
     * @return The time in seconds
     */
    private static double fractionToSeconds (final int value, final double durationSeconds)
    {
        return Math.clamp (value / (double) S2400Constants.RANGE_10_BIT, 0.0, 1.0) * durationSeconds;
    }


    /**
     * Parse the flat record stream. Parsing stops at the end of the data or as soon as an
     * unknown record type is encountered, which tolerates the trailing padding that the reference
     * application appends.
     *
     * @param data The file content
     * @return The parsed records
     */
    private static List<S2400Record> parseRecords (final byte [] data)
    {
        final List<S2400Record> records = new ArrayList<> ();
        int offset = 0;
        while (offset + 3 <= data.length)
        {
            final int type = data[offset] & 0xFF;
            if (type < S2400Constants.REC_U32 || type > S2400Constants.REC_BLOB)
                break;
            final int id = readU16 (data, offset + 1);
            offset += 3;

            switch (type)
            {
                case S2400Constants.REC_U32:
                case S2400Constants.REC_I32:
                    if (offset + 4 > data.length)
                        return records;
                    final int rawInt = readI32 (data, offset);
                    offset += 4;
                    records.add (new S2400Record (type, id, type == S2400Constants.REC_U32 ? rawInt & 0xFFFFFFFFL : rawInt, null));
                    break;

                case S2400Constants.REC_BLOB:
                default:
                    if (offset + 4 > data.length)
                        return records;
                    final int length = readI32 (data, offset);
                    offset += 4;
                    if (length < 0 || offset + length > data.length)
                        return records;
                    final byte [] blob = new byte [length];
                    System.arraycopy (data, offset, blob, 0, length);
                    offset += length;
                    records.add (new S2400Record (type, id, blobToShort (blob), blob));
                    break;
            }
        }
        return records;
    }


    /**
     * Walk the records and group them into tracks. Only the main slice slot (which carries the
     * performance parameters) and the volume envelope (HiFi envelope index 0) are collected.
     *
     * @param records The parsed records
     * @return The tracks
     */
    private static List<S2400Track> groupTracks (final List<S2400Record> records)
    {
        final List<S2400Track> tracks = new ArrayList<> ();
        S2400Track current = null;
        int currentSlot = -1;
        int currentEnvelope = -1;

        for (final S2400Record record: records)
        {
            final int id = record.id ();
            final int value = (int) record.value ();

            if (id == S2400Constants.FIELD_TRACK_INDEX)
            {
                if (current != null)
                    tracks.add (current);
                current = new S2400Track ();
                current.padIndex = value;
                currentSlot = -1;
                currentEnvelope = -1;
                continue;
            }
            if (current == null)
                continue;

            switch (id)
            {
                case S2400Constants.FIELD_TRACK_NAME:
                    current.name = record.asString ();
                    break;
                case S2400Constants.FIELD_TRACK_GAIN_DB:
                    current.gainDb = value;
                    break;
                case S2400Constants.FIELD_CHOKE_GROUP:
                    current.chokeGroup = value;
                    break;
                case S2400Constants.FIELD_GATE_MODE:
                    current.gated = value != 0;
                    break;
                case S2400Constants.FIELD_ENVELOPE_STYLE:
                    current.envelopeStyle = value;
                    break;

                case S2400Constants.FIELD_SLICE_INDEX:
                    currentSlot = value;
                    currentEnvelope = -1;
                    break;

                case S2400Constants.FIELD_ENV_INDEX:
                    currentEnvelope = value;
                    break;

                default:
                    if (currentSlot == S2400Constants.MAIN_SLICE_INDEX)
                        applyMainSlotField (current, id, value, currentEnvelope);
                    break;
            }
        }

        if (current != null)
            tracks.add (current);
        return tracks;
    }


    /**
     * Apply a field which belongs to the main slice slot to the track.
     *
     * @param track The track to update
     * @param id The field identifier
     * @param value The field value
     * @param envelopeIndex The index of the currently parsed HiFi envelope (-1 if none)
     */
    private static void applyMainSlotField (final S2400Track track, final int id, final int value, final int envelopeIndex)
    {
        switch (id)
        {
            case S2400Constants.FIELD_FILTER_MODE:
                track.filterMode = value;
                break;
            case S2400Constants.FIELD_FILTER_RESONANCE:
                track.filterResonance = value;
                break;
            case S2400Constants.FIELD_FILTER_CUTOFF:
                track.filterCutoffHertz = value;
                break;
            case S2400Constants.FIELD_PITCH_FINE:
                track.pitchFineCents = value;
                break;
            case S2400Constants.FIELD_CLASSIC_DECAY:
                track.classicDecay = value;
                break;
            default:
                // Only the volume envelope (index 0) is mapped to the amplitude envelope
                if (envelopeIndex == 0)
                    applyEnvelopeField (track, id, value);
                break;
        }
    }


    /**
     * Apply a field of the volume envelope to the track.
     *
     * @param track The track to update
     * @param id The field identifier
     * @param value The field value
     */
    private static void applyEnvelopeField (final S2400Track track, final int id, final int value)
    {
        switch (id)
        {
            case S2400Constants.FIELD_ENV_ATTACK:
                track.envAttack = value;
                break;
            case S2400Constants.FIELD_ENV_ATTACK_HOLD:
                track.envAttackHold = value;
                break;
            case S2400Constants.FIELD_ENV_DECAY:
                track.envDecay = value;
                break;
            case S2400Constants.FIELD_ENV_SUSTAIN:
                track.envSustain = value;
                break;
            case S2400Constants.FIELD_ENV_RELEASE:
                track.envRelease = value;
                break;
            default:
                break;
        }
    }


    private static int readU16 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8;
    }


    private static int readI32 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24;
    }


    private static long blobToShort (final byte [] blob)
    {
        if (blob.length < 2)
            return 0;
        return (short) ((blob[0] & 0xFF) | (blob[1] & 0xFF) << 8);
    }


    /**
     * A single parsed record.
     *
     * @param type The record type
     * @param id The field identifier
     * @param value The integer value (a signed 16-bit value for a blob)
     * @param blob The raw blob bytes or null
     */
    private record S2400Record (int type, int id, long value, byte [] blob)
    {
        /**
         * Decode the blob as a null-terminated ASCII string.
         *
         * @return The string
         */
        String asString ()
        {
            if (this.blob == null)
                return "";
            int length = this.blob.length;
            while (length > 0 && this.blob[length - 1] == 0)
                length--;
            return new String (this.blob, 0, length, StandardCharsets.US_ASCII);
        }
    }


    /**
     * Mutable holder for the parameters of one track.
     */
    private static final class S2400Track
    {
        private int    padIndex;
        private String name;
        private int    gainDb;
        private int    pitchFineCents;
        private int    chokeGroup;
        private boolean gated;
        private int    envelopeStyle    = S2400Constants.ENVELOPE_HIFI;
        private int    classicDecay     = S2400Constants.CLASSIC_DECAY_MAX;

        private int    filterMode       = S2400Constants.FILTER_LOW_PASS;
        private int    filterResonance;
        private int    filterCutoffHertz = S2400Constants.CUTOFF_MAX_HERTZ;

        private int    envAttack;
        private int    envAttackHold;
        private int    envDecay;
        private int    envSustain       = S2400Constants.RANGE_10_BIT;
        private int    envRelease;
    }
}
