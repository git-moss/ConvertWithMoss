// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulatorx;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Creator for E-mu Emulator X banks. Every multi-sample source becomes one preset of the bank and
 * every sample zone becomes one voice with a single zone, which keeps the per-zone key and velocity
 * range, tuning, panning, filter and amplitude envelope. The samples are not stored in the bank
 * itself but as one *.ebl file per sample in a folder named 'SamplePool' next to it, which is how
 * the Emulator X finds them; identical samples are written only once. All other parameters are
 * written with the defaults of the E-mu factory banks. The format was reverse-engineered from those
 * banks, see documentation/design/EMULATORX_FORMAT.md; written banks have not been verified with
 * the Emulator X itself yet but round-trip through {@link EmulatorXDetector}.
 *
 * @author Jürgen Moßgraber
 */
public class EmulatorXCreator extends AbstractCreator<EmptySettingsUI>
{
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);

    /** One modulation cord of a voice. */
    private record Cord (int source, int destination, float amount)
    {
        // Intentionally empty
    }


    /** The modulation cords which every voice of the E-mu factory banks starts with. */
    private static final List<Cord>             DEFAULT_CORDS      = List.of (
            // Velocity to volume, the amount of which is taken from the zone
            new Cord (EmulatorXConstants.CORD_SOURCE_VELOCITY, EmulatorXConstants.CORD_DEST_VOLUME, EmulatorXConstants.FULL_CORD_AMOUNT),
            new Cord (0x11, 0xAA, 6.0f),
            new Cord (0x60, 0x30, 0),
            new Cord (0x68, 0x30, 0),
            new Cord (0x16, 0x08, EmulatorXConstants.FULL_CORD_AMOUNT),
            new Cord (EmulatorXConstants.CORD_SOURCE_FILTER_ENV2, EmulatorXConstants.CORD_DEST_CUTOFF, 0),
            new Cord (EmulatorXConstants.CORD_SOURCE_VELOCITY, EmulatorXConstants.CORD_DEST_CUTOFF, 0));


    /** Holds one de-duplicated sample of the sample pool. */
    private static class Sample
    {
        EmulatorXSampleFile file;
        int                 index;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EmulatorXCreator (final INotifier notifier)
    {
        super ("E-mu Emulator X", "EXB", notifier, EmptySettingsUI.INSTANCE);
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
     * Write one bank and the sample files it references.
     *
     * @param destinationFolder Where to create the bank
     * @param multisampleSources The sources to convert, each becomes one preset
     * @param name The name of the bank
     * @throws IOException Could not write the bank
     */
    private void writeBank (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String name) throws IOException
    {
        // The name of the bank is part of the name of every one of its sample files, therefore the
        // final name must be known before the samples are written
        final File bankFile = this.createUniqueFilename (destinationFolder, createSafeFilename (name), "exb");
        final String bankName = bankFile.getName ().substring (0, bankFile.getName ().length () - EmulatorXConstants.BANK_ENDING.length ());
        this.notifier.log ("IDS_NOTIFY_STORING", bankFile.getAbsolutePath ());

        final List<Sample> samples = new ArrayList<> ();
        final Map<Object, Sample> samplesByContent = new HashMap<> ();
        final List<byte []> presets = new ArrayList<> ();
        final List<String> presetNames = new ArrayList<> ();

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (presets.size () >= EmulatorXConstants.MAX_PRESETS)
            {
                this.notifier.logError ("IDS_EXB_TOO_MANY_PRESETS", multisampleSource.getName ());
                break;
            }

            final List<byte []> voices = new ArrayList<> ();
            for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    final Sample sample = this.addSample (zone, samples, samplesByContent);
                    if (sample != null)
                        voices.add (createVoice (zone, sample.index));
                }

            if (voices.isEmpty ())
            {
                this.notifier.logError ("IDS_EXB_NO_ZONES", multisampleSource.getName ());
                continue;
            }
            presets.add (createPreset (multisampleSource.getName (), voices));
            presetNames.add (multisampleSource.getName ());
        }

        if (presets.isEmpty ())
            return;

        final File samplePool = new File (destinationFolder, EmulatorXConstants.SAMPLE_POOL_FOLDER);
        safeCreateDirectory (samplePool);
        for (final Sample sample: samples)
            Files.write (new File (samplePool, EmulatorXConstants.createSampleFileName (bankName, sample.index)).toPath (), sample.file.write ());

        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (bankFile.toPath ())))
        {
            writeBankFile (out, presets, presetNames, samples);
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Convert the sample of a zone to 16 bit PCM and add it to the sample pool, re-using an already
     * added sample with identical content and parameters.
     *
     * @param zone The zone
     * @param samples The samples collected so far
     * @param samplesByContent The already collected samples by their content
     * @return The sample or null if the zone must be skipped
     * @throws IOException Could not convert the sample data
     */
    private Sample addSample (final ISampleZone zone, final List<Sample> samples, final Map<Object, Sample> samplesByContent) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName ());
            return null;
        }

        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_FORMAT);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (numChannels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (numChannels), zone.getName ());
            return null;
        }
        final byte [] pcm = waveFile.getDataChunk ().getData ();
        final int numFrames = pcm.length / (EmulatorXConstants.BYTES_PER_FRAME * numChannels);
        if (numFrames <= 0)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName ());
            return null;
        }

        // The loop is a property of the sample in this format. A backwards loop cannot be
        // expressed, an alternating loop is written as a forward loop
        int loopStart = 0;
        int loopEnd = 0;
        for (final ISampleLoop loop: zone.getLoops ())
            if (loop.getType () == LoopType.FORWARDS || loop.getType () == LoopType.ALTERNATING)
            {
                loopStart = Math.clamp (loop.getStart (), 0, numFrames - 1);
                loopEnd = Math.clamp (loop.getEnd (), loopStart + 1, numFrames);
                break;
            }

        final int sampleRate = waveFile.getFormatChunk ().getSampleRate ();
        final Object contentKey = List.of (ByteBuffer.wrap (pcm), Integer.valueOf (sampleRate), Integer.valueOf (loopStart), Integer.valueOf (loopEnd));
        final Sample existing = samplesByContent.get (contentKey);
        if (existing != null)
            return existing;

        if (samples.size () >= EmulatorXConstants.MAX_SAMPLES)
        {
            this.notifier.logError ("IDS_EXB_TOO_MANY_SAMPLES", zone.getName ());
            return null;
        }

        final EmulatorXSampleFile sampleFile = new EmulatorXSampleFile ();
        sampleFile.setName (zone.getName ());
        sampleFile.setSampleRate (sampleRate);
        sampleFile.setPcm (pcm, numChannels);
        if (loopEnd > loopStart)
            sampleFile.setLoop (loopStart, loopEnd);

        final Sample sample = new Sample ();
        sample.file = sampleFile;
        sample.index = samples.size () + 1;
        samples.add (sample);
        samplesByContent.put (contentKey, sample);
        return sample;
    }


    /**
     * Write the bank file. The table of contents lists all presets and then all samples; its
     * entries point at the chunks which follow it without any padding.
     *
     * @param out Where to write to
     * @param presets The assembled preset payloads
     * @param presetNames The names of the presets
     * @param samples The samples of the sample pool
     * @throws IOException Could not write the bank
     */
    private static void writeBankFile (final OutputStream out, final List<byte []> presets, final List<String> presetNames, final List<Sample> samples) throws IOException
    {
        final int tocSize = (presets.size () + samples.size ()) * EmulatorXConstants.TOC_ENTRY_SIZE;
        final byte [] toc = new byte [tocSize];

        int entry = 0;
        int offset = EmulatorXConstants.TOC_OFFSET + tocSize;
        for (int i = 0; i < presets.size (); i++)
        {
            createTocEntry (toc, entry++, EmulatorXConstants.PRESET_TAG, presets.get (i).length, offset, i, presetNames.get (i));
            offset += presets.get (i).length + EmulatorXConstants.CHUNK_OVERHEAD;
        }
        for (final Sample sample: samples)
        {
            createTocEntry (toc, entry++, EmulatorXConstants.SAMPLE_LINK_TAG, 4, offset, sample.index, sample.file.getName ());
            offset += 4 + EmulatorXConstants.CHUNK_OVERHEAD;
        }

        out.write (EmulatorXConstants.FORM_MAGIC.getBytes (StandardCharsets.US_ASCII));
        writeU32BE (out, offset - 8L);
        out.write (EmulatorXConstants.FORM_TYPE.getBytes (StandardCharsets.US_ASCII));
        writeU32BE (out, tocSize);
        out.write (toc);

        for (int i = 0; i < presets.size (); i++)
            writeIndexedChunk (out, EmulatorXConstants.PRESET_TAG, i, presets.get (i));
        for (final Sample sample: samples)
        {
            final byte [] link = new byte [4];
            EmulatorXConstants.putU32BE (link, 0, sample.index);
            writeIndexedChunk (out, EmulatorXConstants.SAMPLE_LINK_TAG, sample.index, link);
        }
    }


    /**
     * Write a chunk which the table of contents points at. Such a chunk carries a 16 bit index
     * between its size and its payload, and its size covers that index.
     *
     * @param out Where to write to
     * @param tag The tag of the chunk
     * @param index The index of the chunk
     * @param payload The payload of the chunk
     * @throws IOException Could not write the chunk
     */
    private static void writeIndexedChunk (final OutputStream out, final String tag, final int index, final byte [] payload) throws IOException
    {
        out.write (tag.getBytes (StandardCharsets.US_ASCII));
        writeU32BE (out, payload.length + 2L);
        final byte [] indexData = new byte [2];
        EmulatorXConstants.putU16BE (indexData, 0, index);
        out.write (indexData);
        out.write (payload);
    }


    /**
     * Fill one entry of the table of contents.
     *
     * @param toc The table of contents
     * @param entryIndex The index of the entry
     * @param tag The tag of the chunk the entry points at
     * @param size The size of the payload of the chunk
     * @param offset The offset of the chunk from the start of the file
     * @param index The index of the chunk
     * @param name The name of the chunk
     */
    private static void createTocEntry (final byte [] toc, final int entryIndex, final String tag, final int size, final int offset, final int index, final String name)
    {
        final int position = entryIndex * EmulatorXConstants.TOC_ENTRY_SIZE;
        System.arraycopy (tag.getBytes (StandardCharsets.US_ASCII), 0, toc, position, 4);
        EmulatorXConstants.putU32BE (toc, position + 4, size);
        EmulatorXConstants.putU32BE (toc, position + 8, offset);
        EmulatorXConstants.putU16BE (toc, position + 12, index);
        EmulatorXConstants.encodeName (toc, position + 14, name);
    }


    /**
     * Create the payload of a preset chunk.
     *
     * @param name The name of the preset
     * @param voices The assembled voice chunks
     * @return The payload
     * @throws IOException Could not assemble the preset
     */
    private static byte [] createPreset (final String name, final List<byte []> voices) throws IOException
    {
        final byte [] header = new byte [EmulatorXConstants.PRESET_HEADER_SIZE];
        EmulatorXConstants.putU32BE (header, 0, EmulatorXConstants.VERSION_PRESET);
        EmulatorXConstants.encodeName (header, 4, name);
        EmulatorXConstants.putU32BE (header, EmulatorXConstants.PRESET_NUM_VOICES, voices.size ());
        EmulatorXConstants.putU32BE (header, 144, 0xFFFFFFFFL);

        // The initial values of the 16 MIDI controllers, -1 for 'not set'
        final byte [] controllers = new byte [20];
        EmulatorXConstants.putU32BE (controllers, 0, EmulatorXConstants.VERSION_1);
        for (int i = 4; i < controllers.length; i++)
            controllers[i] = (byte) 0xFF;

        final byte [] presetSettings = new byte [12];
        EmulatorXConstants.putU32BE (presetSettings, 0, EmulatorXConstants.VERSION_1);
        presetSettings[5] = 100;

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.write (EmulatorXChunk.create (EmulatorXConstants.PRESET_HEADER_TAG, header));
        out.write (EmulatorXChunk.create ("E5IC", controllers));
        out.write (EmulatorXChunk.create ("E5CL", createVersionedChunk (516, EmulatorXConstants.VERSION_1)));
        out.write (EmulatorXChunk.create ("E5MP", createVersionedChunk (12, EmulatorXConstants.VERSION_1)));
        out.write (EmulatorXChunk.create ("EXPs", presetSettings));
        out.write (EmulatorXChunk.createList ("AEL ", createRepeated ("E5E1", 2, 12, EmulatorXConstants.VERSION_1)));
        out.write (EmulatorXChunk.createList ("RmpL", createRamps ()));
        out.write (EmulatorXChunk.createList (EmulatorXConstants.CORD_LIST_TYPE, createRepeated (EmulatorXConstants.CORD_TAG, 16, EmulatorXConstants.CORD_SIZE, EmulatorXConstants.VERSION_1)));
        out.write (EmulatorXChunk.createList (EmulatorXConstants.VOICE_LIST_TYPE, voices));
        return out.toByteArray ();
    }


    /**
     * Create one voice with a single zone from a sample zone.
     *
     * @param zone The zone
     * @param sampleIndex The 1-based index of the sample of the zone
     * @return The complete voice chunk
     * @throws IOException Could not assemble the voice
     */
    private static byte [] createVoice (final ISampleZone zone, final int sampleIndex) throws IOException
    {
        final byte [] voiceHeader = new byte [EmulatorXConstants.VOICE_HEADER_SIZE];
        EmulatorXConstants.putU32BE (voiceHeader, 0, EmulatorXConstants.VERSION_1);
        voiceHeader[4] = 1;
        voiceHeader[6] = 1;
        voiceHeader[7] = 0x40;
        voiceHeader[11] = 0x40;

        final byte [] voiceSettings = new byte [14];
        EmulatorXConstants.putU32BE (voiceSettings, 0, EmulatorXConstants.VERSION_2);
        voiceSettings[5] = 100;
        voiceSettings[13] = 100;

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.write (EmulatorXChunk.create (EmulatorXConstants.VOICE_HEADER_TAG, voiceHeader));
        out.write (EmulatorXChunk.create ("E5Vs", voiceSettings));
        out.write (EmulatorXChunk.create ("E5MP", createVersionedChunk (12, EmulatorXConstants.VERSION_1)));
        // The key and velocity ranges are put into the voice, the zone below leaves them open
        out.write (EmulatorXChunk.createList (EmulatorXConstants.WINDOW_LIST_TYPE, List.of (createWindow (zone.getKeyLow (), zone.getNoteCrossfadeLow (), zone.getNoteCrossfadeHigh (), zone.getKeyHigh ()), createWindow (zone.getVelocityLow (), zone.getVelocityCrossfadeLow (), zone.getVelocityCrossfadeHigh (), zone.getVelocityHigh ()), createWindow (0, 0, 0, 127))));
        out.write (EmulatorXChunk.createList ("CCWL", createControllerWindows ()));
        out.write (EmulatorXChunk.create (EmulatorXConstants.OSCILLATOR_TAG, createOscillator (zone)));
        out.write (EmulatorXChunk.create (EmulatorXConstants.AMPLIFIER_TAG, createAmplifier (zone)));
        out.write (EmulatorXChunk.create (EmulatorXConstants.FILTER_TAG, createFilter (zone)));
        out.write (EmulatorXChunk.createList (EmulatorXConstants.ENVELOPE_LIST_TYPE, List.of (createEnvelope (zone.getAmplitudeEnvelopeModulator ().getSource ()), createEnvelope (getFilterEnvelope (zone)), createEnvelope (null))));
        out.write (EmulatorXChunk.createList ("LFOL", createLFOs ()));
        out.write (EmulatorXChunk.createList ("FuGL", createFunctionGenerators ()));
        out.write (EmulatorXChunk.createList (EmulatorXConstants.CORD_LIST_TYPE, createCords (zone)));
        out.write (EmulatorXChunk.createList (EmulatorXConstants.ZONE_LIST_TYPE, createZone (sampleIndex, zone)));
        return EmulatorXChunk.create (EmulatorXConstants.VOICE_TAG, out.toByteArray ());
    }


    /**
     * Create the oscillator chunk which carries the tuning of a zone.
     *
     * @param zone The zone
     * @return The payload of the chunk
     */
    private static byte [] createOscillator (final ISampleZone zone)
    {
        final byte [] data = createVersionedChunk (EmulatorXConstants.OSCILLATOR_SIZE, EmulatorXConstants.VERSION_OSCILLATOR);
        // The coarse tuning is limited to the range of a signed byte, the rest goes into the fine
        // tuning which the E-mu resolves in 1/64 semitones
        final double tuning = zone.getTuning ();
        final int semitones = Math.clamp ((int) tuning, -128, 127);
        data[EmulatorXConstants.OSCILLATOR_COARSE_TUNE] = (byte) semitones;
        EmulatorXConstants.putFloatBE (data, EmulatorXConstants.OSCILLATOR_FINE_TUNE, EmulatorXConstants.quantizeFineTune ((tuning - semitones) * 100.0));
        return data;
    }


    /**
     * Create the amplifier chunk which carries the volume and the panning of a zone.
     *
     * @param zone The zone
     * @return The payload of the chunk
     */
    private static byte [] createAmplifier (final ISampleZone zone)
    {
        final byte [] data = createVersionedChunk (EmulatorXConstants.AMPLIFIER_SIZE, EmulatorXConstants.VERSION_1);
        final double volume = Math.clamp (zone.getGain (), EmulatorXConstants.MIN_VOLUME_DB, EmulatorXConstants.MAX_VOLUME_DB);
        EmulatorXConstants.putFloatBE (data, EmulatorXConstants.AMPLIFIER_VOLUME, (float) volume);
        data[EmulatorXConstants.AMPLIFIER_PAN] = (byte) Math.clamp (Math.round (zone.getPanning () * EmulatorXConstants.PAN_RANGE), -64, 63);
        return data;
    }


    /**
     * Create the filter chunk of a zone. A filter type without an E-mu equivalent is written as the
     * bypassed 'No Filter' default.
     *
     * @param zone The zone
     * @return The payload of the chunk
     */
    private static byte [] createFilter (final ISampleZone zone)
    {
        final byte [] data = createVersionedChunk (EmulatorXConstants.FILTER_SIZE, EmulatorXConstants.VERSION_1);
        final IFilter filter = zone.getFilter ().orElse (null);
        final int filterType = filter == null ? EmulatorXConstants.FILTER_TYPE_BYPASS : getFilterTypeCode (filter);
        data[EmulatorXConstants.FILTER_TYPE] = (byte) filterType;
        if (filterType == EmulatorXConstants.FILTER_TYPE_BYPASS)
        {
            EmulatorXConstants.putFloatBE (data, EmulatorXConstants.FILTER_CUTOFF, 1.0f);
            return data;
        }
        EmulatorXConstants.putFloatBE (data, EmulatorXConstants.FILTER_CUTOFF, (float) EmulatorXConstants.hertzToCutoff (filter.getCutoff ()));
        return data;
    }


    /**
     * Get the E-mu filter type which is closest to the given filter.
     *
     * @param filter The filter
     * @return The filter type, the bypass type if the filter has no E-mu equivalent
     */
    private static int getFilterTypeCode (final IFilter filter)
    {
        final int poles = filter.getPoles ();
        switch (filter.getType ())
        {
            case LOW_PASS:
                if (poles <= 2)
                    return EmulatorXConstants.FILTER_TYPE_LOWPASS_2;
                return poles >= 6 ? EmulatorXConstants.FILTER_TYPE_LOWPASS_6 : EmulatorXConstants.FILTER_TYPE_LOWPASS_4;
            case HIGH_PASS:
                return poles <= 2 ? EmulatorXConstants.FILTER_TYPE_HIGHPASS_2 : EmulatorXConstants.FILTER_TYPE_HIGHPASS_4;
            case BAND_PASS:
                return poles <= 2 ? EmulatorXConstants.FILTER_TYPE_BANDPASS_2 : EmulatorXConstants.FILTER_TYPE_BANDPASS_4;
            case BAND_REJECTION:
                return EmulatorXConstants.FILTER_TYPE_CONTRARY;
            default:
                return EmulatorXConstants.FILTER_TYPE_BYPASS;
        }
    }


    /**
     * Create one envelope chunk. The six stages of the E-mu envelope are attack 1, attack 2,
     * decay 1, decay 2, release 1 and release 2; the attack of the model becomes attack 2, its hold
     * decay 1, its decay and sustain decay 2 and its release becomes release 1.
     *
     * @param envelope The envelope or null to write an empty one, which is what the factory banks
     *            use for the filter and the auxiliary envelope; those two have no effect because
     *            the modulation cords give them no depth
     * @return The complete chunk
     * @throws IOException Could not assemble the chunk
     */
    private static byte [] createEnvelope (final IEnvelope envelope) throws IOException
    {
        final byte [] data = createVersionedChunk (EmulatorXConstants.ENVELOPE_SIZE, EmulatorXConstants.VERSION_2);
        data[8] = 1;
        if (envelope == null)
            return EmulatorXChunk.create (EmulatorXConstants.ENVELOPE_TAG, data);

        final double attack = Math.max (0, envelope.getAttackTime ());
        final double hold = Math.max (0, envelope.getHoldTime ());
        final double decay = Math.max (0, envelope.getDecayTime ());
        final double release = Math.max (0, envelope.getReleaseTime ());
        final double sustain = envelope.getSustainLevel () < 0 ? 1 : Math.clamp (envelope.getSustainLevel (), 0, 1);

        final double [] times =
        {
            0,
            attack,
            hold,
            decay,
            release,
            0
        };
        final double [] levels =
        {
            0,
            1,
            1,
            sustain,
            0,
            0
        };
        for (int stage = 0; stage < EmulatorXConstants.ENVELOPE_NUM_STAGES; stage++)
        {
            final int offset = EmulatorXConstants.ENVELOPE_STAGES + stage * EmulatorXConstants.ENVELOPE_STAGE_SIZE;
            EmulatorXConstants.putFloatBE (data, offset, (float) times[stage]);
            EmulatorXConstants.putFloatBE (data, offset + 4, (float) (levels[stage] * EmulatorXConstants.FULL_LEVEL));
        }
        return EmulatorXChunk.create (EmulatorXConstants.ENVELOPE_TAG, data);
    }


    /**
     * Create the modulation cords of a voice, which are the defaults of the factory banks with the
     * velocity to volume amount taken from the zone.
     *
     * @param zone The zone
     * @return The complete chunks
     * @throws IOException Could not assemble the chunks
     */
    private static List<byte []> createCords (final ISampleZone zone) throws IOException
    {
        final float velocityAmount = (float) (Math.clamp (zone.getAmplitudeVelocityModulator ().getDepth (), 0, 1) * EmulatorXConstants.FULL_CORD_AMOUNT);
        final float cutoffAmount = (float) (getFilterEnvelopeDepth (zone) * EmulatorXConstants.FULL_CORD_AMOUNT);
        final List<byte []> cords = new ArrayList<> ();
        for (int i = 0; i < EmulatorXConstants.VOICE_NUM_CORDS; i++)
        {
            final byte [] data = createVersionedChunk (EmulatorXConstants.CORD_SIZE, EmulatorXConstants.VERSION_1);
            if (i < DEFAULT_CORDS.size ())
            {
                final Cord cord = DEFAULT_CORDS.get (i);
                data[4] = (byte) cord.source ();
                data[5] = (byte) cord.destination ();
                float amount = cord.amount ();
                if (cord.source () == EmulatorXConstants.CORD_SOURCE_VELOCITY && cord.destination () == EmulatorXConstants.CORD_DEST_VOLUME)
                    amount = velocityAmount;
                else if (cord.source () == EmulatorXConstants.CORD_SOURCE_FILTER_ENV2 && cord.destination () == EmulatorXConstants.CORD_DEST_CUTOFF)
                    amount = cutoffAmount;
                EmulatorXConstants.putFloatBE (data, 6, amount);
            }
            cords.add (EmulatorXChunk.create (EmulatorXConstants.CORD_TAG, data));
        }
        return cords;
    }


    /**
     * Get the filter cutoff envelope of a zone.
     *
     * @param zone The zone
     * @return The envelope or null if the zone has no filter or no cutoff envelope
     */
    private static IEnvelope getFilterEnvelope (final ISampleZone zone)
    {
        final IFilter filter = zone.getFilter ().orElse (null);
        return filter == null ? null : filter.getCutoffEnvelopeModulator ().getSource ();
    }


    /**
     * Get the depth of the filter cutoff envelope of a zone.
     *
     * @param zone The zone
     * @return The depth in the range of -1..1, 0 if the zone has no filter or no cutoff envelope
     */
    private static double getFilterEnvelopeDepth (final ISampleZone zone)
    {
        final IFilter filter = zone.getFilter ().orElse (null);
        if (filter == null)
            return 0;
        final IEnvelopeModulator modulator = filter.getCutoffEnvelopeModulator ();
        return modulator.getSource () == null ? 0 : Math.clamp (modulator.getDepth (), -1, 1);
    }


    /**
     * Create the zone list of a voice with a single zone.
     *
     * @param sampleIndex The 1-based index of the sample
     * @param zone The zone
     * @return The complete chunks of the list
     * @throws IOException Could not assemble the chunks
     */
    private static List<byte []> createZone (final int sampleIndex, final ISampleZone zone) throws IOException
    {
        final byte [] header = new byte [EmulatorXConstants.ZONE_HEADER_SIZE];
        EmulatorXConstants.putU32BE (header, 0, EmulatorXConstants.VERSION_ZONE);
        EmulatorXConstants.putU16BE (header, EmulatorXConstants.ZONE_SAMPLE_INDEX, sampleIndex);
        final int rootKey = zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot ();
        header[EmulatorXConstants.ZONE_ORIGINAL_KEY] = (byte) Math.clamp (rootKey, 0, 127);
        // The loop override is unused, the loop of the sample itself is played
        EmulatorXConstants.putU32BE (header, 16, 0xFFFFFFFFL);
        EmulatorXConstants.putU32BE (header, 20, 0xFFFFFFFFL);

        return List.of (EmulatorXChunk.create (EmulatorXConstants.ZONE_HEADER_TAG, header), EmulatorXChunk.createList (EmulatorXConstants.WINDOW_LIST_TYPE, List.of (createWindow (0, 0, 0, 127), createWindow (0, 0, 0, 127))));
    }


    /**
     * Create a window chunk.
     *
     * @param low The lower bound
     * @param lowFade The crossfade at the lower bound
     * @param highFade The crossfade at the upper bound
     * @param high The upper bound
     * @return The complete chunk
     * @throws IOException Could not assemble the chunk
     */
    private static byte [] createWindow (final int low, final int lowFade, final int highFade, final int high) throws IOException
    {
        final byte [] data = createVersionedChunk (EmulatorXConstants.WINDOW_SIZE, EmulatorXConstants.VERSION_1);
        data[4] = (byte) Math.clamp (low, 0, 127);
        data[5] = (byte) Math.clamp (lowFade, 0, 127);
        data[6] = (byte) Math.clamp (highFade, 0, 127);
        data[7] = (byte) Math.clamp (high, 0, 127);
        return EmulatorXChunk.create (EmulatorXConstants.WINDOW_TAG, data);
    }


    /**
     * Create the five continuous controller windows of a voice, which are all fully open.
     *
     * @return The complete chunks
     * @throws IOException Could not assemble the chunks
     */
    private static List<byte []> createControllerWindows () throws IOException
    {
        final List<byte []> windows = new ArrayList<> ();
        for (int i = 0; i < 5; i++)
        {
            final byte [] data = createVersionedChunk (10, EmulatorXConstants.VERSION_1);
            data[7] = 127;
            windows.add (EmulatorXChunk.create ("ECCw", data));
        }
        return windows;
    }


    /**
     * Create the two default LFOs of a voice.
     *
     * @return The complete chunks
     * @throws IOException Could not assemble the chunks
     */
    private static List<byte []> createLFOs () throws IOException
    {
        final List<byte []> lfos = new ArrayList<> ();
        for (int i = 0; i < 2; i++)
        {
            final byte [] data = createVersionedChunk (22, EmulatorXConstants.VERSION_2);
            EmulatorXConstants.putFloatBE (data, 8, 8.1759f);
            data[12] = 1;
            data[13] = 1;
            lfos.add (EmulatorXChunk.create ("E5LF", data));
        }
        return lfos;
    }


    /**
     * Create the three default function generators of a voice.
     *
     * @return The complete chunks
     * @throws IOException Could not assemble the chunks
     */
    private static List<byte []> createFunctionGenerators () throws IOException
    {
        final List<byte []> generators = new ArrayList<> ();
        for (int i = 0; i < 3; i++)
        {
            final byte [] data = createVersionedChunk (417, EmulatorXConstants.VERSION_1);
            EmulatorXConstants.putFloatBE (data, 8, 4.0814f);
            data[19] = 0x10;
            generators.add (EmulatorXChunk.create ("EFGn", data));
        }
        return generators;
    }


    /**
     * Create the two default ramp generators of a preset.
     *
     * @return The complete chunks
     * @throws IOException Could not assemble the chunks
     */
    private static List<byte []> createRamps () throws IOException
    {
        final List<byte []> ramps = new ArrayList<> ();
        for (int i = 0; i < 2; i++)
        {
            final byte [] data = createVersionedChunk (14, EmulatorXConstants.VERSION_1);
            EmulatorXConstants.putFloatBE (data, 8, 0.25f);
            ramps.add (EmulatorXChunk.create ("ERmp", data));
        }
        return ramps;
    }


    /**
     * Create several identical chunks which only carry their version.
     *
     * @param tag The tag of the chunks
     * @param count The number of chunks
     * @param size The size of the payload of one chunk
     * @param version The version to write
     * @return The complete chunks
     * @throws IOException Could not assemble the chunks
     */
    private static List<byte []> createRepeated (final String tag, final int count, final int size, final int version) throws IOException
    {
        final List<byte []> chunks = new ArrayList<> ();
        for (int i = 0; i < count; i++)
            chunks.add (EmulatorXChunk.create (tag, createVersionedChunk (size, version)));
        return chunks;
    }


    /**
     * Create the payload of a chunk which is empty except for its version.
     *
     * @param size The size of the payload
     * @param version The version to write
     * @return The payload
     */
    private static byte [] createVersionedChunk (final int size, final int version)
    {
        final byte [] data = new byte [size];
        EmulatorXConstants.putU32BE (data, 0, version);
        return data;
    }


    /**
     * Write an unsigned 32 bit big-endian value.
     *
     * @param out Where to write to
     * @param value The value
     * @throws IOException Could not write the value
     */
    private static void writeU32BE (final OutputStream out, final long value) throws IOException
    {
        final byte [] data = new byte [4];
        EmulatorXConstants.putU32BE (data, 0, value);
        out.write (data);
    }
}
