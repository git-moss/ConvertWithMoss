// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator4;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import de.mossgrabers.tools.FileUtils;


/**
 * Detects E-mu Emulator IV bank files (*.e4b). A bank contains up to 1000 presets and 1000
 * samples; every preset becomes one multi-sample source. A preset is a list of voices, each of
 * which maps a set of zones (key/velocity ranges referencing a sample) and carries the tuning,
 * volume, filter and envelope settings for them; every voice becomes one group. The format was
 * reverse-engineered by the mpc2emu project, see documentation/design/E4B_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator4Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final Pattern NOTE_SUFFIX_PATTERN = Pattern.compile ("_([A-G]#?)(-?\\d+)$");


    /** Holds the parsed information of one E3S1 sample chunk. */
    private static class Sample
    {
        String              name;
        InMemorySampleData  sampleData;
        int                 numFrames;
        int                 rootKey;
        boolean             hasLoop;
        int                 loopStart;
        int                 loopEnd;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator4Detector (final INotifier notifier)
    {
        super ("E-mu Emulator IV", "E4B", notifier, new MetadataSettingsUI ("E4B"), ".e4b");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final byte [] data = Files.readAllBytes (sourceFile.toPath ());
            return this.parseBank (sourceFile, data);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Parse a bank file and create one multi-sample source per preset.
     *
     * @param sourceFile The bank file
     * @param data The content of the file
     * @return The multi-sample sources
     */
    private List<IMultisampleSource> parseBank (final File sourceFile, final byte [] data)
    {
        if (data.length < 12 || !Emulator4Constants.hasMagic (data, 0, Emulator4Constants.FORM_MAGIC) || !Emulator4Constants.hasMagic (data, 8, Emulator4Constants.FORM_TYPE))
        {
            this.notifier.logError ("IDS_E4B_NOT_A_BANK", sourceFile.getName ());
            return Collections.emptyList ();
        }

        // Walk the chunks sequentially instead of trusting the TOC offsets, which is more robust
        // against third-party files. Note that the FORM size uses the EOS convention (4 less than
        // standard IFF) and therefore ends inside the trailing EMSt chunk, which is not needed
        final List<byte []> presetChunks = new ArrayList<> ();
        final Map<Integer, Sample> samplesByIndex = new HashMap<> ();
        final Set<String> usedNames = new HashSet<> ();
        int position = 12;
        while (position + 8 <= data.length)
        {
            final long size = Emulator4Constants.getU32BE (data, position + 4);
            final long end = position + 8 + size;
            if (size < 0 || end > data.length)
                break;
            if (Emulator4Constants.hasMagic (data, position, Emulator4Constants.PRESET_TAG))
            {
                final byte [] body = new byte [(int) size];
                System.arraycopy (data, position + 8, body, 0, (int) size);
                presetChunks.add (body);
            }
            else if (Emulator4Constants.hasMagic (data, position, Emulator4Constants.SAMPLE_TAG))
                this.parseSample (data, position + 8, (int) size, samplesByIndex, usedNames);
            // TOC1, E4Ma and EMSt chunks are ignored
            position = (int) (end + (size % 2 == 0 ? 0 : 1));
        }

        final List<IMultisampleSource> results = new ArrayList<> ();
        for (final byte [] presetChunk: presetChunks)
        {
            final IMultisampleSource multisampleSource = this.parsePreset (sourceFile, presetChunk, samplesByIndex);
            if (multisampleSource != null)
                results.add (multisampleSource);
        }
        if (results.isEmpty ())
            this.notifier.logError ("IDS_E4B_NO_PRESETS", sourceFile.getName ());
        else
            this.notifier.log ("IDS_E4B_READING_BANK", sourceFile.getName (), Integer.toString (results.size ()), Integer.toString (samplesByIndex.size ()));
        return results;
    }


    /**
     * Parse an E3S1 sample chunk. The chunk holds a 94 byte header (all fields little-endian
     * except the sample index) followed by 16-bit little-endian mono PCM data. Loop positions are
     * stored as byte offsets relative to the 92 byte EOS sample struct.
     *
     * @param data The bank content
     * @param offset The offset of the chunk body
     * @param size The size of the chunk body
     * @param samplesByIndex Where to add the parsed sample by its 1-based index
     * @param usedNames All sample names used so far, to make the zone names unique
     */
    private void parseSample (final byte [] data, final int offset, final int size, final Map<Integer, Sample> samplesByIndex, final Set<String> usedNames)
    {
        if (size < Emulator4Constants.SAMPLE_HEADER_SIZE)
        {
            this.notifier.logError ("IDS_E4B_MALFORMED_SAMPLE", Integer.toString (size));
            return;
        }

        final int sampleIndex = Emulator4Constants.getU16BE (data, offset);
        final String displayName = Emulator4Constants.decodeName (data, offset + 2);
        final long loopStartOffset = Emulator4Constants.getU32LE (data, offset + 38);
        final long loopEndOffset = Emulator4Constants.getU32LE (data, offset + 46);
        final int sampleRate = (int) Emulator4Constants.getU32LE (data, offset + 54);
        final int options = Emulator4Constants.getU16LE (data, offset + 60);

        final int pcmLength = (size - Emulator4Constants.SAMPLE_HEADER_SIZE) / 2 * 2;
        final int numFrames = pcmLength / 2;
        if (numFrames <= 0 || sampleRate <= 0)
        {
            this.notifier.logError ("IDS_E4B_MALFORMED_SAMPLE", displayName);
            return;
        }
        final byte [] pcm = new byte [pcmLength];
        System.arraycopy (data, offset + Emulator4Constants.SAMPLE_HEADER_SIZE, pcm, 0, pcmLength);

        final Sample sample = new Sample ();
        sample.sampleData = new InMemorySampleData (new DefaultAudioMetadata (1, sampleRate, 16, numFrames), pcm);
        sample.numFrames = numFrames;

        // The root note is conventionally appended to the name, e.g. 'Piano_C3' for MIDI note 60.
        // The zone entries carry the authoritative root key, this one is only the fallback
        String baseName = displayName;
        sample.rootKey = 60;
        final Matcher matcher = NOTE_SUFFIX_PATTERN.matcher (displayName);
        if (matcher.find ())
        {
            final int midiNote = Emulator4Constants.lookupNote (matcher.group (1), Integer.parseInt (matcher.group (2)));
            if (midiNote >= 0)
            {
                baseName = displayName.substring (0, matcher.start ());
                sample.rootKey = midiNote;
            }
        }
        // Prefer the suffix-stripped base name; on a collision keep the full display name and as
        // the last resort append the unique sample index
        String name = baseName.isBlank () ? displayName : baseName;
        if (!usedNames.add (name))
        {
            name = displayName;
            if (!usedNames.add (name))
            {
                name = displayName + " " + sampleIndex;
                usedNames.add (name);
            }
        }
        sample.name = name;

        if ((options & Emulator4Constants.OPTION_LOOP) > 0)
        {
            sample.loopStart = (int) (loopStartOffset - Emulator4Constants.SAMPLE_STRUCT_SIZE) / 2;
            sample.loopEnd = Math.min ((int) (loopEndOffset - Emulator4Constants.SAMPLE_STRUCT_SIZE) / 2, numFrames);
            sample.hasLoop = sample.loopStart >= 0 && sample.loopStart < numFrames && sample.loopEnd > sample.loopStart;
        }

        samplesByIndex.put (Integer.valueOf (sampleIndex), sample);
    }


    /**
     * Parse an E4P1 preset chunk into a multi-sample source. Every voice of the preset becomes one
     * group; the voice parameters (tuning, volume, filter, envelopes, modulation cords) are
     * applied to all zones of the voice.
     *
     * @param sourceFile The bank file
     * @param body The content of the preset chunk
     * @param samplesByIndex The samples of the bank by their 1-based index
     * @return The multi-sample source or null if the preset contains no usable zones
     */
    private IMultisampleSource parsePreset (final File sourceFile, final byte [] body, final Map<Integer, Sample> samplesByIndex)
    {
        if (body.length < Emulator4Constants.PRESET_HEADER_SIZE)
            return null;

        final String presetName = Emulator4Constants.decodeName (body, 2);
        final int numVoices = Emulator4Constants.getU16BE (body, 20);

        final List<IGroup> groups = new ArrayList<> ();
        int offset = Emulator4Constants.PRESET_HEADER_SIZE;
        for (int voiceIndex = 0; voiceIndex < numVoices; voiceIndex++)
        {
            if (offset + Emulator4Constants.VOICE_SIZE > body.length)
            {
                this.notifier.logError ("IDS_E4B_MALFORMED_PRESET", presetName);
                break;
            }

            // The offset of the end of the zone table relative to the voice start is how the
            // hardware locates the next voice; it also implies the number of zones
            final int zoneTableEnd = Emulator4Constants.getU16BE (body, offset + 2);
            final int numZones = (zoneTableEnd - Emulator4Constants.VOICE_SIZE) / Emulator4Constants.ZONE_ENTRY_SIZE;
            if (zoneTableEnd < Emulator4Constants.VOICE_SIZE || offset + Emulator4Constants.VOICE_SIZE + (long) numZones * Emulator4Constants.ZONE_ENTRY_SIZE > body.length)
            {
                this.notifier.logError ("IDS_E4B_MALFORMED_PRESET", presetName);
                break;
            }

            final IGroup group = new DefaultGroup ("Voice " + (voiceIndex + 1));
            this.parseVoice (body, offset, numZones, presetName, samplesByIndex, group);
            if (!group.getSampleZones ().isEmpty ())
                groups.add (group);

            offset += Emulator4Constants.VOICE_SIZE + numZones * Emulator4Constants.ZONE_ENTRY_SIZE;
            // Only the last voice is followed by 2 trailing zero bytes but they do not matter here
        }

        if (groups.isEmpty ())
            return null;
        return this.createMultisampleSource (sourceFile, presetName.isBlank () ? FileUtils.getNameWithoutType (sourceFile) : presetName, groups);
    }


    /**
     * Parse one voice block and add its zones to the given group.
     *
     * @param body The content of the preset chunk
     * @param offset The offset of the voice block
     * @param numZones The number of zone entries of the voice
     * @param presetName The name of the preset, for error messages
     * @param samplesByIndex The samples of the bank by their 1-based index
     * @param group Where to add the created zones
     */
    private void parseVoice (final byte [] body, final int offset, final int numZones, final String presetName, final Map<Integer, Sample> samplesByIndex, final IGroup group)
    {
        // Per-voice tuning: key transpose and coarse tune in semitones, fine tune in 1/64
        // semitone units. All three simply offset the playback pitch of the zones
        final double tuning = body[offset + 34] + body[offset + 35] + body[offset + 36] / 64.0;
        final boolean isFixedPitch = body[offset + 38] == 1;
        final int volume = body[offset + 54];

        // The modulation cord table provides the depths of the fixed routings
        final int modOffset = offset + Emulator4Constants.VOICE_MOD_OFFSET;
        double velocityToAmplitude = 0;
        double filterEnvelopeDepth = 0;
        double filterKeyTracking = 0;
        for (int slot = 0; slot < 20; slot++)
        {
            final int source = body[modOffset + slot * 4] & 0xFF;
            final int destination = body[modOffset + slot * 4 + 1] & 0xFF;
            final int amount = body[modOffset + slot * 4 + 2];
            if (amount == 0)
                continue;
            // Velocity sources: 0x0A add, 0x0B centered, 0x0C subtract
            if (destination == 0x40 && source >= 0x0A && source <= 0x0C)
                velocityToAmplitude = Math.clamp (Math.abs (amount) / 127.0, 0, 1);
            else if (destination == 0x38 && source == 0x50)
                filterEnvelopeDepth = Math.clamp (amount / 127.0, -1, 1);
            else if (destination == 0x38 && source == 0x08)
                filterKeyTracking = Math.clamp (amount / 127.0 * Emulator4Constants.FULL_KEY_TRACKING, 0, 1);
        }

        // The amplitude envelope: 6 rate/level stages in the primary zone table of which the
        // standard ADSR mapping uses attack 1, decay 1 (its level is the sustain) and release 1
        final int pztOffset = offset + Emulator4Constants.VOICE_PZT_OFFSET;
        final IEnvelope amplitudeEnvelope = new DefaultEnvelope ();
        amplitudeEnvelope.setAttackTime (Emulator4Constants.envelopeRateToTime (body[pztOffset] & 0xFF));
        // An attack 2 stage with the same level as attack 1 is a plateau, which is a hold stage
        final double holdTime = Emulator4Constants.envelopeRateToTime (body[pztOffset + 2] & 0xFF);
        if (holdTime > 0 && body[pztOffset + 1] == body[pztOffset + 3])
            amplitudeEnvelope.setHoldTime (holdTime);
        amplitudeEnvelope.setDecayTime (Emulator4Constants.envelopeRateToTime (body[pztOffset + 4] & 0xFF));
        amplitudeEnvelope.setSustainLevel (Math.clamp (body[pztOffset + 5] / 127.0, 0, 1));
        amplitudeEnvelope.setReleaseTime (Emulator4Constants.envelopeRateToTime (body[pztOffset + 8] & 0xFF));

        final IFilter filter = createFilter (body, offset, pztOffset, filterEnvelopeDepth, filterKeyTracking);

        for (int zoneIndex = 0; zoneIndex < numZones; zoneIndex++)
        {
            final int entryOffset = offset + Emulator4Constants.VOICE_SIZE + zoneIndex * Emulator4Constants.ZONE_ENTRY_SIZE;
            final int sampleIndex = Emulator4Constants.getU16BE (body, entryOffset + 10);
            final Sample sample = samplesByIndex.get (Integer.valueOf (sampleIndex));
            if (sample == null)
            {
                this.notifier.logError ("IDS_E4B_SAMPLE_MISSING", Integer.toString (sampleIndex), presetName);
                continue;
            }

            final int keyLow = body[entryOffset + 2] & 0xFF;
            final int keyHigh = body[entryOffset + 5] & 0xFF;
            final ISampleZone zone = new DefaultSampleZone (sample.name, Math.min (keyLow, 127), Math.min (keyHigh, 127));
            zone.setSampleData (sample.sampleData);

            final int velocityLow = body[entryOffset + 6] & 0xFF;
            final int velocityHigh = body[entryOffset + 9] & 0xFF;
            if (velocityLow <= velocityHigh && velocityHigh > 0)
            {
                zone.setVelocityLow (Math.max (1, velocityLow));
                zone.setVelocityHigh (Math.min (127, velocityHigh));
            }

            final int rootKey = body[entryOffset + 14] & 0xFF;
            zone.setKeyRoot (rootKey > 0 && rootKey < 128 ? rootKey : sample.rootKey);
            zone.setStart (0);
            zone.setStop (sample.numFrames);
            zone.setTuning (tuning);
            zone.setGain (volume);
            if (isFixedPitch)
                zone.setKeyTracking (0);

            if (sample.hasLoop)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (LoopType.FORWARDS);
                loop.setStart (sample.loopStart);
                loop.setEnd (sample.loopEnd);
                zone.getLoops ().add (loop);
            }

            zone.getAmplitudeEnvelopeModulator ().setSource (amplitudeEnvelope);
            zone.getAmplitudeVelocityModulator ().setDepth (velocityToAmplitude);
            if (filter != null)
                zone.setFilter (filter);

            group.addSampleZone (zone);
        }
    }


    /**
     * Create the filter of a voice. The 'wide open' default (4-pole low-pass at full frequency
     * without resonance, envelope or key tracking) is the EOS bypass state and creates no filter.
     * The effect and morph filter types of the EOS (phasers, flangers, vocal formants, EQ morphs)
     * have no model equivalent and create no filter either.
     *
     * @param body The content of the preset chunk
     * @param offset The offset of the voice block
     * @param pztOffset The offset of the primary zone table of the voice
     * @param filterEnvelopeDepth The depth of the filter envelope to cutoff modulation (-1..1)
     * @param filterKeyTracking The key tracking of the filter cutoff (0..1)
     * @return The filter or null if the voice does not use one
     */
    private static IFilter createFilter (final byte [] body, final int offset, final int pztOffset, final double filterEnvelopeDepth, final double filterKeyTracking)
    {
        final int filterType = body[offset + 58] & 0xFF;
        final int cutoff = body[offset + 60] & 0xFF;
        final int resonance = body[offset + 61] & 0xFF;

        final FilterType type;
        final int poles;
        switch (filterType)
        {
            case 0x00:
                type = FilterType.LOW_PASS;
                poles = 4;
                break;
            case 0x01:
                type = FilterType.LOW_PASS;
                poles = 2;
                break;
            case 0x02:
                type = FilterType.LOW_PASS;
                poles = 6;
                break;
            case 0x08:
                type = FilterType.HIGH_PASS;
                poles = 2;
                break;
            case 0x09:
                type = FilterType.HIGH_PASS;
                poles = 4;
                break;
            case 0x10:
                type = FilterType.BAND_PASS;
                poles = 2;
                break;
            case 0x11:
                type = FilterType.BAND_PASS;
                poles = 4;
                break;
            case 0x12:
                // 'Contrary band-pass' is the closest EOS type to a notch
                type = FilterType.BAND_REJECTION;
                poles = 2;
                break;
            default:
                return null;
        }

        // A fully open low-pass without any modulation is the bypass state
        if (filterType == 0x00 && cutoff == 255 && resonance == 0 && filterEnvelopeDepth == 0 && filterKeyTracking == 0)
            return null;

        final IFilter filter = new DefaultFilter (type, poles, Emulator4Constants.cutoffToHertz (cutoff), Math.clamp (resonance / 127.0, 0, 1));
        filter.setCutoffKeyTracking (filterKeyTracking);

        if (filterEnvelopeDepth != 0)
        {
            final IEnvelope envelope = new DefaultEnvelope ();
            envelope.setAttackTime (Emulator4Constants.envelopeRateToTime (body[pztOffset + 14] & 0xFF));
            envelope.setDecayTime (Emulator4Constants.envelopeRateToTime (body[pztOffset + 18] & 0xFF));
            envelope.setSustainLevel (Math.clamp (body[pztOffset + 19] / 127.0, 0, 1));
            envelope.setReleaseTime (Emulator4Constants.envelopeRateToTime (body[pztOffset + 22] & 0xFF));

            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            cutoffModulator.setSource (envelope);
            cutoffModulator.setDepth (filterEnvelopeDepth);
        }

        return filter;
    }
}
