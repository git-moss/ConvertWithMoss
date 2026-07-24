// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator4;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
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
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Creator for E-mu Emulator IV bank files (*.e4b). Every multi-sample source becomes one preset in
 * the bank; a library collects all sources into a single bank. Every zone is written as one voice
 * with a single zone entry, which keeps the per-zone tuning, volume, filter and envelope settings.
 * Samples are stored as 16-bit mono PCM (stereo sources are mixed down) and are de-duplicated by
 * their content. The format was reverse-engineered by the mpc2emu project, see
 * documentation/design/E4B_FORMAT.md. Written banks have not been verified on hardware yet but
 * validate against the mpc2emu reference parser. Files round-trip through
 * {@link Emulator4Detector}.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator4Creator extends AbstractCreator<EmptySettingsUI>
{
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);


    /** Holds one de-duplicated sample to be written as an E3S1 chunk. */
    private static class Sample
    {
        String  name;
        byte [] pcm;
        int     sampleRate;
        boolean hasLoop;
        int     loopStart;
        int     loopEnd;
        int     rootKey;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator4Creator (final INotifier notifier)
    {
        super ("E-mu Emulator IV", "E4B", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.writeBank (destinationFolder, List.of (multisampleSource), multisampleSource.getName ());
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
            this.writeBank (destinationFolder, multisampleSources, libraryName);
    }


    /**
     * Write one bank file for the given sources.
     *
     * @param destinationFolder Where to create the bank file
     * @param multisampleSources The sources to convert, each becomes one preset
     * @param name The bank name
     * @throws IOException Could not write the bank
     */
    private void writeBank (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String name) throws IOException
    {
        final File outputFile = this.createUniqueFilename (destinationFolder, createSafeFilename (name), "e4b");
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());

        final List<Sample> samples = new ArrayList<> ();
        final Map<Object, Integer> sampleIndicesByContent = new HashMap<> ();
        final Set<String> usedSampleNames = new HashSet<> ();
        final List<byte []> presetBodies = new ArrayList<> ();
        final List<String> presetNames = new ArrayList<> ();

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (presetBodies.size () >= Emulator4Constants.MAX_PRESETS)
            {
                this.notifier.logError ("IDS_E4B_TOO_MANY_PRESETS", multisampleSource.getName ());
                break;
            }

            final List<ISampleZone> zones = new ArrayList<> ();
            for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
                zones.addAll (group.getSampleZones ());

            final List<byte []> voices = new ArrayList<> ();
            int numMixedDown = 0;
            for (final ISampleZone zone: zones)
            {
                final int sampleIndex = this.addSample (zone, samples, sampleIndicesByContent, usedSampleNames);
                if (sampleIndex == 0)
                    continue;
                if (sampleIndex < 0)
                    numMixedDown++;
                voices.add (createVoice (zone, Math.abs (sampleIndex)));
            }
            if (numMixedDown > 0)
                this.notifier.log ("IDS_E4B_MIXED_TO_MONO", Integer.toString (numMixedDown), multisampleSource.getName ());
            if (voices.isEmpty ())
            {
                this.notifier.logError ("IDS_E4B_NO_ZONES", multisampleSource.getName ());
                continue;
            }

            presetBodies.add (createPresetBody (presetBodies.size (), multisampleSource.getName (), voices));
            presetNames.add (multisampleSource.getName ());
        }

        if (presetBodies.isEmpty ())
            return;
        this.writeFile (outputFile, presetBodies, presetNames, samples);
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Convert the sample of a zone to 16-bit mono PCM and add it to the bank samples, re-using an
     * already added sample with identical content.
     *
     * @param zone The zone
     * @param samples The samples collected so far
     * @param sampleIndicesByContent The 1-based indices of the collected samples by their content
     * @param usedSampleNames All sample names used so far
     * @return The 1-based index of the sample, negated if the sample was newly mixed down from
     *         stereo; 0 if the zone must be skipped
     * @throws IOException Could not convert the sample data
     */
    private int addSample (final ISampleZone zone, final List<Sample> samples, final Map<Object, Integer> sampleIndicesByContent, final Set<String> usedSampleNames) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName ());
            return 0;
        }

        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_FORMAT);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (numChannels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (numChannels), zone.getName ());
            return 0;
        }
        final int sampleRate = waveFile.getFormatChunk ().getSampleRate ();

        final byte [] wavData = waveFile.getDataChunk ().getData ();
        final boolean isStereo = numChannels == 2;
        final byte [] pcm = isStereo ? mixToMono (wavData) : wavData;
        final int numFrames = pcm.length / 2;
        if (numFrames <= 0)
            return 0;

        // The loop is a property of the sample in this format. Backwards loops cannot be
        // expressed; an alternating loop is written as a forward loop
        boolean hasLoop = false;
        int loopStart = 0;
        int loopEnd = 0;
        for (final ISampleLoop loop: zone.getLoops ())
            if (loop.getType () == LoopType.FORWARDS || loop.getType () == LoopType.ALTERNATING)
            {
                loopStart = Math.clamp (loop.getStart (), 0, numFrames - 1);
                loopEnd = Math.clamp (loop.getEnd (), loopStart + 1, numFrames);
                hasLoop = true;
                break;
            }

        final int rootKey = Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), 0, 127);

        // Re-use an already written sample with identical content and parameters, e.g. when the
        // same sample is mapped to several key ranges or velocity layers
        final Object contentKey = List.of (ByteBuffer.wrap (pcm), Integer.valueOf (sampleRate), Boolean.valueOf (hasLoop), Integer.valueOf (loopStart), Integer.valueOf (loopEnd), Integer.valueOf (rootKey));
        final Integer existingIndex = sampleIndicesByContent.get (contentKey);
        if (existingIndex != null)
            return existingIndex.intValue ();

        if (samples.size () >= Emulator4Constants.MAX_SAMPLES)
        {
            this.notifier.logError ("IDS_E4B_TOO_MANY_SAMPLES", zone.getName ());
            return 0;
        }

        final Sample sample = new Sample ();
        // The root note suffix (e.g. '_C3') is appended to the name; shorten the base name so the
        // suffix always fits into the 16 characters
        final String suffix = Emulator4Constants.formatNoteSuffix (rootKey);
        sample.name = createUniqueSampleName (zone.getName (), usedSampleNames, Emulator4Constants.NAME_LENGTH - suffix.length ()) + suffix;
        sample.pcm = pcm;
        sample.sampleRate = sampleRate;
        sample.hasLoop = hasLoop;
        sample.loopStart = loopStart;
        sample.loopEnd = loopEnd;
        sample.rootKey = rootKey;
        samples.add (sample);

        final int index = samples.size ();
        sampleIndicesByContent.put (contentKey, Integer.valueOf (index));
        return isStereo ? -index : index;
    }


    /**
     * Mix interleaved 16-bit stereo PCM down to mono.
     *
     * @param stereo The interleaved little-endian stereo data
     * @return The mono data
     */
    private static byte [] mixToMono (final byte [] stereo)
    {
        final int numFrames = stereo.length / 4;
        final byte [] mono = new byte [numFrames * 2];
        for (int i = 0; i < numFrames; i++)
        {
            final int left = stereo[i * 4 + 1] << 8 | stereo[i * 4] & 0xFF;
            final int right = stereo[i * 4 + 3] << 8 | stereo[i * 4 + 2] & 0xFF;
            final int value = (left + right) / 2;
            mono[i * 2] = (byte) (value & 0xFF);
            mono[i * 2 + 1] = (byte) (value >> 8 & 0xFF);
        }
        return mono;
    }


    /**
     * Create one voice block with a single zone entry from a zone.
     *
     * @param zone The zone
     * @param sampleIndex The 1-based index of the sample of the zone
     * @return The voice block
     */
    private static byte [] createVoice (final ISampleZone zone, final int sampleIndex)
    {
        final byte [] voice = new byte [Emulator4Constants.VOICE_SIZE + Emulator4Constants.ZONE_ENTRY_SIZE];

        final int keyLow = Math.clamp (zone.getKeyLow (), 0, 127);
        final int keyHigh = Math.clamp (zone.getKeyHigh (), keyLow, 127);
        final int velocityLow = Math.clamp (zone.getVelocityLow (), 0, 127);
        final int velocityHigh = Math.clamp (zone.getVelocityHigh (), velocityLow, 127);

        // The voice parameters. The offset at [2] is how the hardware locates the next voice
        Emulator4Constants.putU16BE (voice, 2, Emulator4Constants.VOICE_SIZE + Emulator4Constants.ZONE_ENTRY_SIZE);
        voice[4] = 1;
        voice[7] = 0x64;
        // The voice level key window and velocity range must mirror the span of its zones,
        // otherwise velocity layers play stacked instead of switched
        voice[14] = (byte) keyLow;
        voice[17] = (byte) keyHigh;
        voice[18] = (byte) velocityLow;
        voice[21] = (byte) velocityHigh;
        voice[25] = 0x7F;

        // Tuning: the integer part goes to the coarse tune, the rest to the fine tune (in 1/64
        // semitone units); the key transpose at [34] stays 0
        final double tuning = zone.getTuning ();
        final int semitones = Math.clamp ((int) Math.round (tuning), -72, 24);
        voice[35] = (byte) semitones;
        voice[36] = (byte) Math.clamp (Math.round ((tuning - semitones) * 64.0), -64, 63);
        voice[38] = (byte) (zone.getKeyTracking () == 0 ? 1 : 0);
        voice[51] = (byte) 0x80;
        voice[54] = (byte) Math.clamp (Math.round (zone.getGain ()), -96, 24);

        // The filter setting; without a filter write the EOS bypass state (4-pole low-pass,
        // fully open, no resonance)
        final Optional<IFilter> optFilter = zone.getFilter ();
        final IFilter filter = optFilter.isPresent () ? optFilter.get () : null;
        if (filter == null)
        {
            voice[58] = 0x00;
            voice[60] = (byte) 0xFF;
            voice[61] = 0x00;
        }
        else
        {
            voice[58] = (byte) getFilterTypeCode (filter);
            voice[60] = (byte) Emulator4Constants.hertzToCutoff (filter.getCutoff ());
            voice[61] = (byte) Math.clamp (Math.round (filter.getResonance () * 127), 0, 127);
        }

        // The primary zone table with the amplitude envelope, the filter envelope and the
        // default LFO settings
        System.arraycopy (Emulator4Constants.PRIMARY_ZONE_TEMPLATE, 0, voice, Emulator4Constants.VOICE_PZT_OFFSET, Emulator4Constants.PRIMARY_ZONE_TEMPLATE.length);
        writeAmplitudeEnvelope (voice, zone);
        final double filterEnvelopeDepth = filter == null ? 0 : writeFilterEnvelope (voice, filter);

        // The modulation cord table: start from the EOS factory default cord set (required for
        // non-transpose voices to be recognized) and set the depths of the used routings
        System.arraycopy (Emulator4Constants.MOD_CORD_TEMPLATE, 0, voice, Emulator4Constants.VOICE_MOD_OFFSET, Emulator4Constants.MOD_CORD_TEMPLATE.length);
        final double velocityDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
        voice[Emulator4Constants.VOICE_MOD_OFFSET + Emulator4Constants.MOD_VELOCITY_AMOUNT] = (byte) Math.clamp (Math.round (velocityDepth * 127), 0, 127);
        if (filterEnvelopeDepth != 0)
            voice[Emulator4Constants.VOICE_MOD_OFFSET + Emulator4Constants.MOD_FILTER_ENVELOPE_AMOUNT] = (byte) Math.clamp (Math.round (filterEnvelopeDepth * 127), -127, 127);
        if (filter != null && filter.getCutoffKeyTracking () != 0)
            voice[Emulator4Constants.VOICE_MOD_OFFSET + Emulator4Constants.MOD_KEY_TRACKING_AMOUNT] = (byte) Math.clamp (Math.round (filter.getCutoffKeyTracking () / Emulator4Constants.FULL_KEY_TRACKING * 127), -127, 127);

        // The zone entry
        final int entryOffset = Emulator4Constants.VOICE_SIZE;
        voice[entryOffset + 2] = (byte) keyLow;
        voice[entryOffset + 5] = (byte) keyHigh;
        voice[entryOffset + 6] = (byte) velocityLow;
        voice[entryOffset + 9] = (byte) velocityHigh;
        Emulator4Constants.putU16BE (voice, entryOffset + 10, sampleIndex);
        voice[entryOffset + 14] = (byte) Math.clamp (zone.getKeyRoot () < 0 ? keyLow : zone.getKeyRoot (), 0, 127);

        return voice;
    }


    /**
     * Write the amplitude envelope of a zone into the primary zone table of a voice. The 6-stage
     * EOS envelope uses the standard ADSR mapping: attack 1 rises to full level, decay 1 falls to
     * the sustain level which decay 2 holds, release 1 falls to silence.
     *
     * @param voice The voice block
     * @param zone The zone
     */
    private static void writeAmplitudeEnvelope (final byte [] voice, final ISampleZone zone)
    {
        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        final int offset = Emulator4Constants.VOICE_PZT_OFFSET;

        final double sustainLevel = envelope.getSustainLevel ();
        final int sustain = sustainLevel < 0 ? 127 : (int) Math.clamp (Math.round (sustainLevel * 127), 0, 127);
        final double attackTime = envelope.getAttackTime ();
        final double holdTime = envelope.getHoldTime ();
        final double decayTime = envelope.getDecayTime ();
        final double releaseTime = envelope.getReleaseTime ();

        // A hold stage is expressed with the attack 2 stage: it moves to the same (full) level as
        // attack 1 and therefore plateaus for its rate time
        voice[offset] = (byte) (attackTime < 0 ? 0 : Emulator4Constants.envelopeTimeToRate (attackTime));
        voice[offset + 1] = 127;
        voice[offset + 2] = (byte) (holdTime < 0 ? 0 : Emulator4Constants.envelopeTimeToRate (holdTime));
        voice[offset + 3] = 127;
        voice[offset + 4] = (byte) (decayTime < 0 ? 0 : Emulator4Constants.envelopeTimeToRate (decayTime));
        voice[offset + 5] = (byte) sustain;
        voice[offset + 6] = 0;
        voice[offset + 7] = (byte) sustain;
        voice[offset + 8] = (byte) (releaseTime < 0 ? Emulator4Constants.DEFAULT_RELEASE_RATE : Emulator4Constants.envelopeTimeToRate (releaseTime));
        voice[offset + 9] = 0;
        voice[offset + 10] = 0;
        voice[offset + 11] = 0;
    }


    /**
     * Write the filter envelope of a zone into the primary zone table of a voice. The envelope
     * shape is always stored at full scale; its depth and direction is the amount of the filter
     * envelope to cutoff modulation cord, which the caller writes into the cord table.
     *
     * @param voice The voice block
     * @param filter The filter
     * @return The depth of the filter envelope modulation (-1..1), 0 if there is none
     */
    private static double writeFilterEnvelope (final byte [] voice, final IFilter filter)
    {
        final IEnvelopeModulator modulator = filter.getCutoffEnvelopeModulator ();
        final double depth = modulator.getDepth ();
        if (depth == 0)
            return 0;

        final IEnvelope envelope = modulator.getSource ();
        final int offset = Emulator4Constants.VOICE_PZT_OFFSET;
        final double sustainLevel = envelope.getSustainLevel ();
        final int sustain = sustainLevel < 0 ? 127 : (int) Math.clamp (Math.round (sustainLevel * 127), 0, 127);
        final double attackTime = envelope.getAttackTime ();
        final double decayTime = envelope.getDecayTime ();
        final double releaseTime = envelope.getReleaseTime ();

        voice[offset + 14] = (byte) (attackTime < 0 ? 0 : Emulator4Constants.envelopeTimeToRate (attackTime));
        voice[offset + 15] = 127;
        voice[offset + 16] = 0;
        voice[offset + 17] = 127;
        voice[offset + 18] = (byte) (decayTime < 0 ? 0 : Emulator4Constants.envelopeTimeToRate (decayTime));
        voice[offset + 19] = (byte) sustain;
        voice[offset + 20] = 0;
        voice[offset + 21] = (byte) sustain;
        voice[offset + 22] = (byte) (releaseTime < 0 ? Emulator4Constants.DEFAULT_RELEASE_RATE : Emulator4Constants.envelopeTimeToRate (releaseTime));
        voice[offset + 23] = 0;
        voice[offset + 24] = 0;
        voice[offset + 25] = 0;

        return Math.clamp (depth, -1, 1);
    }


    /**
     * Map a model filter to the EOS filter type byte. The byte encodes the filter group in the
     * upper bits and the slope variant in the lower 3 bits.
     *
     * @param filter The filter
     * @return The filter type byte
     */
    private static int getFilterTypeCode (final IFilter filter)
    {
        final int poles = filter.getPoles ();
        return switch (filter.getType ())
        {
            case LOW_PASS -> poles <= 2 ? 0x01 : poles >= 6 ? 0x02 : 0x00;
            case HIGH_PASS -> poles >= 4 ? 0x09 : 0x08;
            case BAND_PASS -> poles >= 4 ? 0x11 : 0x10;
            case BAND_REJECTION -> 0x12;
        };
    }


    /**
     * Create the body of an E4P1 preset chunk from its voices.
     *
     * @param presetIndex The 0-based index of the preset in the bank
     * @param name The preset name
     * @param voices The voice blocks
     * @return The chunk body
     */
    private static byte [] createPresetBody (final int presetIndex, final String name, final List<byte []> voices)
    {
        int voicesSize = 0;
        for (final byte [] voice: voices)
            voicesSize += voice.length;

        // Only the last voice of a preset is followed by 2 trailing zero bytes
        final byte [] body = new byte [Emulator4Constants.PRESET_HEADER_SIZE + voicesSize + 2];
        Emulator4Constants.putU16BE (body, 0, presetIndex);
        Emulator4Constants.encodeName (body, 2, name);
        body[19] = 0x52;
        Emulator4Constants.putU16BE (body, 20, voices.size ());
        body[28] = 0x78;
        if (voices.size () > 1)
        {
            body[41] = 0x04;
            body[43] = 0x01;
        }
        body[52] = 0x52;
        body[53] = 0x23;
        body[54] = 0x00;
        body[55] = 0x7E;
        body[56] = (byte) 0xFF;
        body[57] = (byte) 0xFF;
        body[58] = (byte) 0xFF;
        body[59] = (byte) 0xFF;

        int offset = Emulator4Constants.PRESET_HEADER_SIZE;
        for (final byte [] voice: voices)
        {
            System.arraycopy (voice, 0, body, offset, voice.length);
            offset += voice.length;
        }
        return body;
    }


    /**
     * Create the header of an E3S1 sample chunk. All offsets are byte offsets relative to the
     * start of the 92 byte EOS sample struct, which begins after the 2 byte sample index.
     *
     * @param sample The sample
     * @param sampleIndex The 1-based index of the sample in the bank
     * @return The header
     */
    private static byte [] createSampleHeader (final Sample sample, final int sampleIndex)
    {
        final byte [] header = new byte [Emulator4Constants.SAMPLE_HEADER_SIZE];
        final int structSize = Emulator4Constants.SAMPLE_STRUCT_SIZE;
        final int endOffset = structSize + sample.pcm.length - 2;

        Emulator4Constants.putU16BE (header, 0, sampleIndex);
        Emulator4Constants.encodeName (header, 2, sample.name);
        Emulator4Constants.putU32LE (header, 22, structSize);
        Emulator4Constants.putU32LE (header, 30, endOffset);
        if (sample.hasLoop)
        {
            Emulator4Constants.putU32LE (header, 38, (long) sample.loopStart * 2 + structSize);
            Emulator4Constants.putU32LE (header, 46, Math.min ((long) sample.loopEnd * 2 + structSize, endOffset));
        }
        else
        {
            Emulator4Constants.putU32LE (header, 38, structSize);
            Emulator4Constants.putU32LE (header, 46, endOffset);
        }
        Emulator4Constants.putU32LE (header, 54, sample.sampleRate);
        Emulator4Constants.putU16LE (header, 60, sample.hasLoop ? Emulator4Constants.OPTIONS_MONO_LOOP : Emulator4Constants.OPTIONS_MONO);
        Emulator4Constants.putU32LE (header, 62, structSize);
        return header;
    }


    /**
     * Assemble and write the bank file.
     *
     * @param outputFile The file to write
     * @param presetBodies The bodies of the preset chunks
     * @param presetNames The names of the presets, for the table of contents
     * @param samples The samples
     * @throws IOException Could not write the file
     */
    private void writeFile (final File outputFile, final List<byte []> presetBodies, final List<String> presetNames, final List<Sample> samples) throws IOException
    {
        // Calculate the chunk offsets. Chunks are word aligned; only sample chunks can have an
        // odd size since all other chunk sizes are even
        final int numTocEntries = 1 + presetBodies.size () + samples.size ();
        final int tocChunkSize = 8 + numTocEntries * Emulator4Constants.TOC_ENTRY_SIZE;

        int position = 12 + tocChunkSize;
        final int e4maOffset = position;
        position += 8 + Emulator4Constants.E4MA_SIZE;

        final int [] presetOffsets = new int [presetBodies.size ()];
        for (int i = 0; i < presetBodies.size (); i++)
        {
            presetOffsets[i] = position;
            position += 8 + presetBodies.get (i).length;
        }

        final int [] sampleOffsets = new int [samples.size ()];
        final int [] sampleBodySizes = new int [samples.size ()];
        for (int i = 0; i < samples.size (); i++)
        {
            sampleOffsets[i] = position;
            sampleBodySizes[i] = Emulator4Constants.SAMPLE_HEADER_SIZE + samples.get (i).pcm.length;
            position += 8 + sampleBodySizes[i] + sampleBodySizes[i] % 2;
        }

        final byte [] masterSetup = Emulator4Constants.getDefaultMasterSetup ();
        position += 8 + masterSetup.length;

        // The FORM size uses the EOS convention which excludes the 4 byte form type, so it is 4
        // less than the standard IFF value and ends inside the trailing zeros of the EMSt chunk
        final long formSize = position - 12L;

        // The table of contents lists all chunks except the trailing EMSt
        final byte [] toc = new byte [numTocEntries * Emulator4Constants.TOC_ENTRY_SIZE];
        createTocEntry (toc, 0, Emulator4Constants.E4MA_TAG, Emulator4Constants.E4MA_SIZE, e4maOffset, 0, "Multimap");
        for (int i = 0; i < presetBodies.size (); i++)
            createTocEntry (toc, 1 + i, Emulator4Constants.PRESET_TAG, presetBodies.get (i).length, presetOffsets[i], i, presetNames.get (i));
        for (int i = 0; i < samples.size (); i++)
            createTocEntry (toc, 1 + presetBodies.size () + i, Emulator4Constants.SAMPLE_TAG, sampleBodySizes[i], sampleOffsets[i], i + 1, samples.get (i).name);

        final byte [] multimap = new byte [Emulator4Constants.E4MA_SIZE];
        for (int i = 0; i < Emulator4Constants.E4MA_SIZE; i++)
            multimap[i] = Emulator4Constants.E4MA_ENTRY[i % Emulator4Constants.E4MA_ENTRY.length];

        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (outputFile.toPath ())))
        {
            out.write (Emulator4Constants.FORM_MAGIC);
            writeU32BE (out, formSize);
            out.write (Emulator4Constants.FORM_TYPE);

            writeChunkHeader (out, Emulator4Constants.TOC_TAG, toc.length);
            out.write (toc);
            writeChunkHeader (out, Emulator4Constants.E4MA_TAG, multimap.length);
            out.write (multimap);
            for (final byte [] presetBody: presetBodies)
            {
                writeChunkHeader (out, Emulator4Constants.PRESET_TAG, presetBody.length);
                out.write (presetBody);
            }
            for (int i = 0; i < samples.size (); i++)
            {
                final Sample sample = samples.get (i);
                writeChunkHeader (out, Emulator4Constants.SAMPLE_TAG, sampleBodySizes[i]);
                out.write (createSampleHeader (sample, i + 1));
                out.write (sample.pcm);
                if (sampleBodySizes[i] % 2 == 1)
                    out.write (0);
            }
            writeChunkHeader (out, Emulator4Constants.EMST_TAG, masterSetup.length);
            out.write (masterSetup);
        }
    }


    /**
     * Fill one 32 byte entry of the table of contents.
     *
     * @param toc The table of contents
     * @param entryIndex The index of the entry
     * @param tag The chunk tag
     * @param dataSize The size of the chunk data
     * @param fileOffset The absolute offset of the chunk tag in the file
     * @param index The index of the chunk: 0 for the multimap, the 0-based preset index or the
     *            1-based sample index
     * @param name The name of the chunk
     */
    private static void createTocEntry (final byte [] toc, final int entryIndex, final byte [] tag, final int dataSize, final int fileOffset, final int index, final String name)
    {
        final int offset = entryIndex * Emulator4Constants.TOC_ENTRY_SIZE;
        System.arraycopy (tag, 0, toc, offset, 4);
        Emulator4Constants.putU32BE (toc, offset + 4, dataSize);
        Emulator4Constants.putU32BE (toc, offset + 8, fileOffset);
        Emulator4Constants.putU16BE (toc, offset + 12, index);
        Emulator4Constants.encodeName (toc, offset + 14, name);
        // The bytes at [30] (null) and [31] (MIDI program, 0 = any) stay 0
    }


    /**
     * Write an IFF chunk header (tag and big-endian size).
     *
     * @param out The output stream
     * @param tag The chunk tag
     * @param size The size of the chunk data
     * @throws IOException Could not write
     */
    private static void writeChunkHeader (final OutputStream out, final byte [] tag, final int size) throws IOException
    {
        out.write (tag);
        writeU32BE (out, size);
    }


    /**
     * Write a big-endian 32 bit value.
     *
     * @param out The output stream
     * @param value The value
     * @throws IOException Could not write
     */
    private static void writeU32BE (final OutputStream out, final long value) throws IOException
    {
        out.write ((int) (value >> 24 & 0xFF));
        out.write ((int) (value >> 16 & 0xFF));
        out.write ((int) (value >> 8 & 0xFF));
        out.write ((int) (value & 0xFF));
    }


    private static String createUniqueSampleName (final String zoneName, final Set<String> usedSampleNames, final int maxLength)
    {
        String name = zoneName.trim ();
        if (name.length () > maxLength)
            name = name.substring (0, maxLength);
        int counter = 1;
        while (!usedSampleNames.add (name))
        {
            counter++;
            final String suffix = Integer.toString (counter);
            final String base = name.substring (0, Math.min (name.length (), maxLength - suffix.length ()));
            name = base + suffix;
        }
        return name;
    }
}
