// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.cmi3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.FileUtils;


/**
 * Creator for Fairlight CMI Voice (VC) files. Three targets can be written. The default is a Series
 * III voice: each sample zone becomes a sub-voice with its key range taken from the 128-key mapping
 * table, with 16-bit mono or stereo audio, loop points, tuning and the amplitude envelope.
 * Alternatively the 8-bit voice format of the CMI I/II/IIx is written (one fixed-size file per
 * sample zone with the control (CO) file it references), which is the format read by the QasarBeach
 * recreation, or the native 16-bit format of QasarBeach itself, which carries the loop, release and
 * level in the file.
 *
 * @author Jürgen Moßgraber
 */
public class FairlightCmi3Creator extends AbstractCreator<FairlightCmi3CreatorUI>
{
    private static final String                 IDS_NOTIFY_STORING         = "IDS_NOTIFY_STORING";

    private static final int                    VC_VERSION                 = 768;
    private static final int                    VC_NAME_SIZE               = 16;
    private static final int                    ZONE_TABLE_OFFSET          = 256;
    private static final int                    FUNC_BLOCK_BASE            = 768;
    private static final int                    SAMPLE_DATA_OFFSET         = 2304;
    private static final int                    PAGE_SIZE                  = 256;
    private static final int                    FIRST_SUB_VOICE_PAGE       = 1024;
    /** The sub-voice IDs are stored in signed bytes, therefore only 1-127 are available. */
    private static final int                    MAX_SUB_VOICES             = 127;

    /** The reference sample rate of the Series III pitch law. */
    private static final double                 PITCH_REFERENCE_RATE       = 44701.0;

    private static final int                    IIX_FILE_SIZE              = 21888;
    private static final int                    IIX_AUDIO_OFFSET           = 0x1500;
    private static final int                    IIX_NUM_SAMPLES            = 16384;
    private static final int                    IIX_SEGMENT_SIZE           = 128;
    private static final int                    IIX_CO_NAME_OFFSET         = 0x00A0;
    private static final int                    IIX_LOOP_START_OFFSET      = 0x1332;
    private static final int                    IIX_LOOP_END_OFFSET        = 0x1333;
    private static final int                    IIX_LOOP_MODE_OFFSET       = 0x133B;

    /** The control (CO) file: 26 parameters of 8 bytes each starting at 0x80. */
    private static final int                    CO_FILE_SIZE               = 384;
    private static final int                    CO_PARAM_OFFSET            = 0x80;
    /** Patch type: a static value. */
    private static final int                    CO_PATCH_VALUE             = 0xB1;
    /** Patch type: a boolean which is on. */
    private static final int                    CO_PATCH_ON                = 0xC0;
    /** Patch type: a boolean which is off. */
    private static final int                    CO_PATCH_OFF               = 0xC1;

    /** The native format of the QasarBeach recreation: 'QBV2', name, 16-bit audio, 'QBC9' chunk. */
    private static final int                    QBV2_NAME_OFFSET           = 0x04;
    private static final int                    QBV2_AUDIO_OFFSET          = 0x11;
    private static final int                    QBV2_CONTROL_OFFSET        = QBV2_AUDIO_OFFSET + IIX_NUM_SAMPLES * 2;
    /** Page 7 parameter block: the damping (release) and volume, stored 0-based. */
    private static final int                    QBV2_DAMPING_OFFSET        = 0xD3DD;
    private static final int                    QBV2_VOLUME_OFFSET         = 0xD3CB;
    /** The measured fade-out time of the damping: 0.0255 * dial value^0.741 seconds. */
    private static final double                 QBV2_DAMPING_TIME_SCALE    = 0.0255;
    private static final double                 QBV2_DAMPING_TIME_EXPONENT = 0.741;

    private static final DestinationAudioFormat DESTINATION_FORMAT         = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);


    /** The audio data and parameters of one zone prepared for writing as a sub-voice. */
    private static class PreparedZone
    {
        ISampleZone zone;
        String      name;
        byte [] []  channelData;
        int         sampleRate;
        int         numFrames;
        boolean     hasLoop;
        int         loopStart;
        int         loopEnd;
        boolean     loopUntilRelease;
        int         id;
    }


    private byte [] qasarBeachTemplate;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public FairlightCmi3Creator (final INotifier notifier)
    {
        super ("Fairlight CMI Voice", "CMI3", notifier, new FairlightCmi3CreatorUI ());
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        switch (this.settingsConfiguration.getTargetFormat ())
        {
            case SERIES_IIX:
                this.createIIxFiles (destinationFolder, multisampleSource, false);
                break;
            case QASAR_BEACH:
                this.createIIxFiles (destinationFolder, multisampleSource, true);
                break;
            default:
                this.createSeries3File (destinationFolder, multisampleSource);
                break;
        }
    }


    /**
     * Write series 3 format.
     *
     * @param destinationFolder Where to store the files
     * @param multisampleSource The multi-sample source
     * @throws IOException Could not store the files
     */
    private void createSeries3File (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final List<IGroup> groups = this.combineSplitStereo (multisampleSource);

        final List<PreparedZone> preparedZones = new ArrayList<> ();
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final PreparedZone preparedZone = this.prepareZone (zone);
                if (preparedZone != null)
                    preparedZones.add (preparedZone);
            }
        if (preparedZones.isEmpty ())
        {
            this.notifier.logError ("IDS_CMI3_NO_ZONES", multisampleSource.getName ());
            return;
        }

        // The voice has no velocity dimension. Fill the 128-key mapping table with the loudest
        // velocity layer first, so that it wins where zones overlap
        preparedZones.sort ((z1, z2) -> {
            final int velocityHigh1 = limitToDefault (z1.zone.getVelocityHigh (), 127);
            final int velocityHigh2 = limitToDefault (z2.zone.getVelocityHigh (), 127);
            if (velocityHigh1 != velocityHigh2)
                return velocityHigh2 - velocityHigh1;
            return z1.zone.getKeyLow () - z2.zone.getKeyLow ();
        });

        final PreparedZone [] keyOwner = new PreparedZone [128];
        int numConflicts = 0;
        for (final PreparedZone preparedZone: preparedZones)
        {
            final int keyLow = Math.clamp (preparedZone.zone.getKeyLow (), 0, 127);
            final int keyHigh = Math.clamp (preparedZone.zone.getKeyHigh (), keyLow, 127);
            for (int key = keyLow; key <= keyHigh; key++)
                if (keyOwner[key] == null)
                    keyOwner[key] = preparedZone;
                else
                    numConflicts++;
        }
        if (numConflicts > 0)
            this.notifier.logError ("IDS_CMI3_OVERLAPPING_ZONES", Integer.toString (numConflicts), multisampleSource.getName ());

        // Assign the sub-voice IDs in keyboard order to the zones which own at least one key
        final List<PreparedZone> subVoices = new ArrayList<> ();
        int numSkipped = 0;
        final byte [] mapping = new byte [128];
        for (int key = 0; key < 128; key++)
        {
            final PreparedZone preparedZone = keyOwner[key];
            if (preparedZone == null)
                continue;
            if (preparedZone.id == 0)
                if (subVoices.size () >= MAX_SUB_VOICES)
                {
                    preparedZone.id = -1;
                    numSkipped++;
                }
                else
                {
                    preparedZone.id = subVoices.size () + 1;
                    subVoices.add (preparedZone);
                }
            if (preparedZone.id > 0)
                mapping[key] = (byte) preparedZone.id;
        }
        if (numSkipped > 0)
            this.notifier.logError ("IDS_CMI3_TOO_MANY_ZONES", multisampleSource.getName (), Integer.toString (numSkipped));

        boolean isStereo = false;
        for (final PreparedZone preparedZone: subVoices)
            isStereo |= preparedZone.channelData.length == 2;

        final byte [] fileData = assembleSeries3File (subVoices, mapping, isStereo);

        final File outputFile = this.createUniqueFilename (destinationFolder, FileUtils.createSafeFilename (multisampleSource.getName ()), "vc");
        this.notifier.log (IDS_NOTIFY_STORING, outputFile.getAbsolutePath ());
        try (final OutputStream out = new FileOutputStream (outputFile))
        {
            out.write (fileData);
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Convert the audio of a zone to 16-bit PCM, cut it to the zone start and stop and move the
     * loop accordingly.
     *
     * @param zone The zone to prepare
     * @return The prepared zone or null if the audio format is not supported
     * @throws IOException Could not convert the sample data
     */
    private PreparedZone prepareZone (final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), zone.getName ());
            return null;
        }

        this.logResampling (zone, DESTINATION_FORMAT);
        final WaveFile waveFile = convertZoneAudio (sampleData.get (), DESTINATION_FORMAT);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (numChannels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (numChannels), zone.getName ());
            return null;
        }

        final byte [] pcmData = waveFile.getDataChunk ().getData ();
        final int totalFrames = pcmData.length / (2 * numChannels);
        final int start = Math.clamp (limitToDefault (zone.getStart (), 0), 0, totalFrames);
        final int stop = Math.clamp (limitToDefault (zone.getStop (), totalFrames), start, totalFrames);
        final int numFrames = stop - start;
        if (numFrames == 0)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), zone.getName ());
            return null;
        }

        final PreparedZone preparedZone = new PreparedZone ();
        preparedZone.zone = zone;
        preparedZone.name = zone.getName ();
        preparedZone.sampleRate = waveFile.getFormatChunk ().getSampleRate ();
        preparedZone.numFrames = numFrames;
        preparedZone.channelData = new byte [numChannels] [];
        for (int channel = 0; channel < numChannels; channel++)
            preparedZone.channelData[channel] = extractChannelBigEndian (pcmData, numChannels, channel, start, stop - 1);

        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop loop = loops.get (0);
            final int loopStart = (int) Math.clamp (limitToDefault (loop.getStart (), 0) - (long) start, 0, numFrames - 1L);
            final int loopEnd = loop.getEnd () > 0 ? (int) Math.clamp (loop.getEnd () - (long) start, loopStart, numFrames - 1L) : numFrames - 1;
            if (loopEnd > loopStart)
            {
                preparedZone.hasLoop = true;
                preparedZone.loopStart = loopStart;
                preparedZone.loopEnd = loopEnd;
                preparedZone.loopUntilRelease = loop.isLoopUntilRelease ();
            }
        }
        return preparedZone;
    }


    /**
     * Assemble the complete voice file.
     *
     * @param subVoices The prepared sub-voices
     * @param mapping The 128-key mapping table with the sub-voice ID of each key
     * @param isStereo True if the file is written as a stereo voice
     * @return The file data
     */
    private static byte [] assembleSeries3File (final List<PreparedZone> subVoices, final byte [] mapping, final boolean isStereo)
    {
        // Calculate the page aligned offset of each sub-voice
        final int [] subVoiceOffsets = new int [subVoices.size ()];
        int offset = FIRST_SUB_VOICE_PAGE;
        for (int i = 0; i < subVoices.size (); i++)
        {
            subVoiceOffsets[i] = offset;
            final int dataSize = subVoices.get (i).channelData[0].length * (isStereo ? 2 : 1);
            offset += (SAMPLE_DATA_OFFSET + dataSize + PAGE_SIZE - 1) / PAGE_SIZE * PAGE_SIZE;
        }

        final byte [] out = new byte [offset];
        writeBE16 (out, 0, VC_VERSION);
        if (isStereo)
            out[16] = (byte) 0xFF;

        // The sub-voice offset (in pages) and ID table
        for (int i = 0; i < subVoices.size (); i++)
        {
            writeBE24 (out, ZONE_TABLE_OFFSET + i * 4, subVoiceOffsets[i] / PAGE_SIZE);
            out[ZONE_TABLE_OFFSET + i * 4 + 3] = (byte) subVoices.get (i).id;
        }

        // The voice level function chain holds only the key mapping table (function 6). The chain
        // ends at the first entry with a size of less than 3, which the zero filled array provides
        out[FUNC_BLOCK_BASE + 1] = (byte) 130;
        out[FUNC_BLOCK_BASE + 2] = 6;
        System.arraycopy (mapping, 0, out, FUNC_BLOCK_BASE + 4, 128);

        for (int i = 0; i < subVoices.size (); i++)
            writeSubVoice (out, subVoiceOffsets[i], subVoices.get (i), isStereo);

        return out;
    }


    /**
     * Write the header, function chain and audio data of one sub-voice.
     *
     * @param out The file data to write to
     * @param subVoiceOffset The offset of the sub-voice
     * @param preparedZone The prepared zone to write
     * @param isStereo True if the file is written as a stereo voice - the data of mono zones is
     *            then duplicated to both channels
     */
    private static void writeSubVoice (final byte [] out, final int subVoiceOffset, final PreparedZone preparedZone, final boolean isStereo)
    {
        final byte [] dataA = preparedZone.channelData[0];
        final byte [] dataB = preparedZone.channelData.length == 2 ? preparedZone.channelData[1] : dataA;

        out[subVoiceOffset + 16] = (byte) preparedZone.id;
        // 2 marks 16-bit audio data
        out[subVoiceOffset + 17] = 2;
        writeBE32 (out, subVoiceOffset + 18, dataA.length);
        writeBE32 (out, subVoiceOffset + 22, preparedZone.sampleRate);
        if (isStereo)
        {
            // The set top bit marks the right channel data which follows the left channel data
            out[subVoiceOffset + 33] = (byte) (preparedZone.id | 0x80);
            writeBE32 (out, subVoiceOffset + 34, dataB.length);
        }
        writeName (out, subVoiceOffset + 42, preparedZone.name);

        // The sub-voice function chain: the audio addresses of both channels and the parameters
        int pos = subVoiceOffset + 256;
        pos = writeAddressBlock (out, pos, 13, preparedZone);
        if (isStereo)
            pos = writeAddressBlock (out, pos, 18, preparedZone);

        final ISampleZone zone = preparedZone.zone;
        final IEnvelopeModulator modulator = zone.getAmplitudeEnvelopeModulator ();
        final IEnvelope envelope = modulator.getDepth () > 0 ? modulator.getSource () : null;
        final int attack = encodeTime (envelope == null ? 0 : envelope.getAttackTime (), 4096);
        final int release = encodeTime (envelope == null ? 0 : envelope.getReleaseTime (), 2048);
        pos = writeParameter (out, pos, 5, attack);
        pos = writeParameter (out, pos, 6, encodeTime (envelope == null ? 0 : envelope.getHoldTime (), 4096));
        pos = writeParameter (out, pos, 7, encodeTime (envelope == null ? 0 : envelope.getDecayTime (), 2048));
        pos = writeParameter (out, pos, 8, encodeSustainLevel (envelope == null ? 1 : limitToDefault (envelope.getSustainLevel (), 1)));
        pos = writeParameter (out, pos, 9, encodeGain (zone.getGain ()));
        pos = writeParameter (out, pos, 10, release);
        // The slow attack/release values are mirrored from the fast ones, the extended flags (27,
        // 28) select the fast ones
        pos = writeParameter (out, pos, 16, attack);
        pos = writeParameter (out, pos, 17, release);
        pos = writeParameter (out, pos, 24, encodeTune (zone, preparedZone.sampleRate));
        pos = writeFlag (out, pos, 27, false);
        pos = writeFlag (out, pos, 28, false);
        pos = writeFlag (out, pos, 29, preparedZone.hasLoop);
        writeFlag (out, pos, 42, preparedZone.hasLoop && preparedZone.loopUntilRelease);

        System.arraycopy (dataA, 0, out, subVoiceOffset + SAMPLE_DATA_OFFSET, dataA.length);
        if (isStereo)
            System.arraycopy (dataB, 0, out, subVoiceOffset + SAMPLE_DATA_OFFSET + dataA.length, dataB.length);
    }


    /**
     * Write the null terminated name limited to 7-bit ASCII characters.
     *
     * @param out The data to write to
     * @param offset The offset to write to
     * @param name The name to write
     */
    private static void writeName (final byte [] out, final int offset, final String name)
    {
        if (name == null)
            return;
        final int length = Math.min (name.length (), VC_NAME_SIZE);
        for (int i = 0; i < length; i++)
        {
            final char c = name.charAt (i);
            out[offset + i] = (byte) (c >= 32 && c <= 126 ? c : '_');
        }
    }


    /**
     * Write a function chain entry with the audio addresses of one channel.
     *
     * @param out The data to write to
     * @param pos The position of the entry
     * @param function The function ID, 13 for channel A and 18 for channel B
     * @param preparedZone The prepared zone
     * @return The position of the next entry
     */
    private static int writeAddressBlock (final byte [] out, final int pos, final int function, final PreparedZone preparedZone)
    {
        out[pos + 1] = 20;
        out[pos + 2] = (byte) function;
        writeBE32 (out, pos + 6, 0);
        writeBE32 (out, pos + 10, preparedZone.numFrames);
        writeBE32 (out, pos + 14, preparedZone.hasLoop ? preparedZone.loopStart : 0);
        writeBE32 (out, pos + 18, preparedZone.hasLoop ? preparedZone.loopEnd : 0);
        return pos + 22;
    }


    /**
     * Write a function chain entry with one 16-bit parameter value (function 9).
     *
     * @param out The data to write to
     * @param pos The position of the entry
     * @param parameterID The ID of the parameter
     * @param value The unsigned 16-bit value
     * @return The position of the next entry
     */
    private static int writeParameter (final byte [] out, final int pos, final int parameterID, final int value)
    {
        out[pos + 1] = 6;
        out[pos + 2] = 9;
        out[pos + 4] = (byte) parameterID;
        // Negative values are sign extended into the third value byte
        out[pos + 5] = value >= 0x8000 ? (byte) 0xFF : 0;
        writeBE16 (out, pos + 6, value);
        return pos + 8;
    }


    /**
     * Write a function chain entry with one boolean parameter (function 9).
     *
     * @param out The data to write to
     * @param pos The position of the entry
     * @param parameterID The ID of the parameter
     * @param isEnabled The state of the parameter
     * @return The position of the next entry
     */
    private static int writeFlag (final byte [] out, final int pos, final int parameterID, final boolean isEnabled)
    {
        out[pos + 1] = 6;
        out[pos + 2] = 9;
        out[pos + 4] = (byte) parameterID;
        out[pos + 5] = isEnabled ? (byte) 0xFF : 0;
        return pos + 8;
    }


    /**
     * Encode an envelope time in seconds.
     *
     * @param seconds The time in seconds, a negative value is 'not set' and becomes 0
     * @param divisor The number of steps of one second
     * @return The unsigned 16-bit value
     */
    private static int encodeTime (final double seconds, final int divisor)
    {
        return Math.clamp (Math.round (limitToDefault (seconds, 0) * divisor), 0, 32767);
    }


    /**
     * Encode the sustain level. The level is stored as a negated fraction of a power of 10, 0
     * decodes to full level.
     *
     * @param sustainLevel The sustain level [0..1]
     * @return The unsigned 16-bit value
     */
    private static int encodeSustainLevel (final double sustainLevel)
    {
        if (sustainLevel >= 1)
            return 0;
        if (sustainLevel <= 0)
            return 520;
        return Math.clamp (Math.round (256.0 * Math.log10 ((1.01 - sustainLevel) * 100.0)), 0, 32767);
    }


    /**
     * Encode the gain in dB as a signed 16-bit value with 512 steps per dB.
     *
     * @param gainDB The gain in dB
     * @return The unsigned 16-bit value
     */
    private static int encodeGain (final double gainDB)
    {
        final int value = (int) Math.round (Math.clamp (gainDB, -63, 63) * 512);
        return value & 0xFFFF;
    }


    /**
     * Encode the root key and fine tuning of a zone into the tune parameter of the Series III pitch
     * law, which combines the pitch with the deviation of the sample rate from its reference rate.
     *
     * @param zone The zone
     * @param sampleRate The sample rate of the written audio data
     * @return The unsigned 16-bit value
     */
    private static int encodeTune (final ISampleZone zone, final int sampleRate)
    {
        final int root = Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), 0, 127);
        final double pitch = Math.clamp (root - zone.getTuning (), 0, 127.49);
        final double rateOffset = 12.0 * Math.log (sampleRate / PITCH_REFERENCE_RATE) / Math.log (2);
        long value = Math.round (256.0 * (rateOffset + 65.0 - pitch));
        // The pitch is stored modulo 128 semitones, move the value into the signed 14-bit range
        while (value > 16383)
            value -= 32768;
        while (value < -16384)
            value += 32768;
        return (int) value & 0xFFFF;
    }


    /**
     * Convert the audio of a zone to 16-bit PCM. An 8-bit source is unsigned (the WAV convention)
     * and stays unsigned when the audio system widens it to 16-bit - the sign bit is corrected here
     * so that the returned data is always signed PCM.
     *
     * @param sampleData The sample data of the zone
     * @param format The destination format
     * @return The converted WAV file
     * @throws IOException Could not convert the sample data
     */
    private static WaveFile convertZoneAudio (final ISampleData sampleData, final DestinationAudioFormat format) throws IOException
    {
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData, format);
        if (sampleData.getAudioMetadata ().getBitResolution () == 8)
        {
            final byte [] data = waveFile.getDataChunk ().getData ();
            for (int i = 1; i < data.length; i += 2)
                data[i] ^= 0x80;
        }
        return waveFile;
    }


    /**
     * Extract one channel from interleaved little-endian 16-bit PCM data as big-endian data.
     *
     * @param pcmData The interleaved little-endian PCM data
     * @param numChannels The number of channels in the data
     * @param channel The channel to extract
     * @param startFrame The first frame to extract
     * @param endFrameInclusive The last frame to extract
     * @return The big-endian channel data
     */
    private static byte [] extractChannelBigEndian (final byte [] pcmData, final int numChannels, final int channel, final int startFrame, final int endFrameInclusive)
    {
        final int numFrames = endFrameInclusive - startFrame + 1;
        final byte [] channelData = new byte [numFrames * 2];
        for (int i = 0; i < numFrames; i++)
        {
            final int src = ((startFrame + i) * numChannels + channel) * 2;
            channelData[i * 2] = pcmData[src + 1];
            channelData[i * 2 + 1] = pcmData[src];
        }
        return channelData;
    }


    /**
     * Write each sample zone as a voice file of the 8-bit CMI I/II/IIx dialect or of the native
     * QasarBeach format. Since neither stores a sample rate, the audio is re-sampled so that the
     * voice plays at its original pitch on the root key of the zone.
     *
     * @param destinationFolder Where to store the files
     * @param multisampleSource The multi-sample source
     * @param asQasarBeach True to write the native 16-bit QasarBeach format instead of the 8-bit
     *            dialect with its control (CO) file
     * @throws IOException Could not store the files
     */
    private void createIIxFiles (final File destinationFolder, final IMultisampleSource multisampleSource, final boolean asQasarBeach) throws IOException
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: this.combineSplitStereo (multisampleSource))
            zones.addAll (group.getSampleZones ());
        if (zones.isEmpty ())
        {
            this.notifier.logError ("IDS_CMI3_NO_ZONES", multisampleSource.getName ());
            return;
        }
        if (zones.size () > 1)
            this.notifier.log ("IDS_CMI3_IIX_SPLIT", Integer.toString (zones.size ()), multisampleSource.getName ());

        final Set<String> controlFileNames = new HashSet<> ();
        for (int i = 0; i < zones.size (); i++)
        {
            if (this.isCancelled ())
                return;

            final ISampleZone zone = zones.get (i);
            final RenderedVoice renderedVoice = this.renderVoice (zone);
            if (renderedVoice == null)
                continue;

            String name = zones.size () == 1 || zone.getName () == null || zone.getName ().isBlank () ? multisampleSource.getName () : zone.getName ();
            if (name == null || name.isBlank ())
                name = "Unnamed";
            if (zones.size () > 1 && (zone.getName () == null || zone.getName ().isBlank ()))
                name = name + " " + (i + 1);

            final byte [] fileData;
            byte [] controlFileData = null;
            String controlName = null;
            if (asQasarBeach)
                fileData = this.createQasarBeachFileData (zone, renderedVoice, name);
            else
            {
                fileData = createIIxFileData (renderedVoice);

                // The control parameters (loop, envelope, level) are read from a control (CO)
                // file which the voice references by an 8 character name
                controlName = createUniqueDOSFileName (destinationFolder, FileUtils.createSafeFilename (name).replaceAll ("\\W", "_"), ".CO", controlFileNames, false);
                for (int c = 0; c < 8; c++)
                    fileData[IIX_CO_NAME_OFFSET + c] = (byte) (c < controlName.length () ? controlName.charAt (c) : ' ');
                controlFileData = createIIxControlFileData (zone, fileData);
            }

            final File outputFile = this.createUniqueFilename (destinationFolder, FileUtils.createSafeFilename (name), "vc");
            this.notifier.log (IDS_NOTIFY_STORING, outputFile.getAbsolutePath ());
            try (final OutputStream out = new FileOutputStream (outputFile))
            {
                out.write (fileData);
            }

            if (controlFileData != null)
            {
                final File controlFile = new File (destinationFolder, controlName + ".CO");
                this.notifier.log (IDS_NOTIFY_STORING, controlFile.getAbsolutePath ());
                try (final OutputStream out = new FileOutputStream (controlFile))
                {
                    out.write (controlFileData);
                }
            }
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** The mono 16-bit audio and loop segments of a zone rendered for the fixed-size formats. */
    private static class RenderedVoice
    {
        final short [] samples = new short [IIX_NUM_SAMPLES];
        boolean        isLooped;
        int            loopStartSegment;
        int            loopEndSegment;
    }


    /**
     * Render the audio of a zone for the fixed-size voice formats: mixed down to mono, cut to the
     * 16384 samples of a voice, with the loop as 128 sample segments.
     *
     * @param zone The zone
     * @return The rendered voice or null if the audio format is not supported
     * @throws IOException Could not convert the sample data
     */
    private RenderedVoice renderVoice (final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), zone.getName ());
            return null;
        }

        // The CMI II reads one 128 sample segment per waveform period, i.e. the playback rate is
        // the frequency of the played note times 128 (the 14080 Hz default rate of the IIx is
        // exactly 128 times the 110 Hz A). A voice therefore plays at its original pitch on its
        // root key when the audio is re-sampled to the root frequency times 128
        final int sourceRate = sampleData.get ().getAudioMetadata ().getSampleRate ();
        final int root = Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), 0, 127);
        final double rootFrequency = 440.0 * Math.pow (2, (root - zone.getTuning () - 69.0) / 12.0);
        final int targetRate = Math.clamp (Math.round (rootFrequency * 128.0), 2100, 100000);

        final WaveFile waveFile = convertZoneAudio (sampleData.get (), new DestinationAudioFormat (new int []
        {
            16
        }, targetRate, true));
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        final byte [] pcmData = waveFile.getDataChunk ().getData ();
        final int totalFrames = pcmData.length / (2 * numChannels);

        // The zone start/stop and loop are in frames of the source sample rate
        final double frameRatio = waveFile.getFormatChunk ().getSampleRate () / (double) sourceRate;
        final int start = Math.clamp (Math.round (limitToDefault (zone.getStart (), 0) * frameRatio), 0, totalFrames);
        final int stop = Math.clamp (zone.getStop () > 0 ? Math.round (zone.getStop () * frameRatio) : totalFrames, start, totalFrames);
        final int numFrames = stop - start;
        if (numFrames == 0)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), zone.getName ());
            return null;
        }
        if (numFrames > IIX_NUM_SAMPLES)
            this.notifier.log ("IDS_CMI3_IIX_TRUNCATED", zone.getName (), Integer.toString (numFrames));

        final RenderedVoice renderedVoice = new RenderedVoice ();
        final int copyFrames = Math.min (numFrames, IIX_NUM_SAMPLES);
        for (int i = 0; i < copyFrames; i++)
        {
            int sum = 0;
            for (int channel = 0; channel < numChannels; channel++)
            {
                final int src = ((start + i) * numChannels + channel) * 2;
                sum += (short) (pcmData[src] & 0xFF | pcmData[src + 1] << 8);
            }
            renderedVoice.samples[i] = (short) (sum / numChannels);
        }

        // The loop is stored as 128 sample segments, the end segment is inclusive
        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop loop = loops.get (0);
            final long loopStart = Math.round (limitToDefault (loop.getStart (), 0) * frameRatio) - start;
            final long loopEnd = loop.getEnd () > 0 ? Math.round (loop.getEnd () * frameRatio) - start : copyFrames - 1L;
            if (loopStart < IIX_NUM_SAMPLES && loopEnd > loopStart)
            {
                renderedVoice.isLooped = true;
                renderedVoice.loopStartSegment = Math.clamp (Math.round (Math.max (loopStart, 0) / (double) IIX_SEGMENT_SIZE), 0, 127);
                renderedVoice.loopEndSegment = Math.clamp (Math.round ((loopEnd + 1) / (double) IIX_SEGMENT_SIZE) - 1, renderedVoice.loopStartSegment, 127);
            }
        }
        return renderedVoice;
    }


    /**
     * Create the data of one 8-bit IIx voice file from a rendered voice.
     *
     * @param renderedVoice The rendered voice
     * @return The file data
     */
    private static byte [] createIIxFileData (final RenderedVoice renderedVoice)
    {
        final byte [] out = new byte [IIX_FILE_SIZE];

        // 8-bit unsigned audio, silence is 0x80
        for (int i = 0; i < IIX_NUM_SAMPLES; i++)
            out[IIX_AUDIO_OFFSET + i] = (byte) ((renderedVoice.samples[i] >> 8) + 128);

        // No control (CO) file is referenced yet - the field is padded with spaces
        Arrays.fill (out, IIX_CO_NAME_OFFSET, IIX_CO_NAME_OFFSET + 8, (byte) 0x20);

        if (renderedVoice.isLooped)
        {
            out[IIX_LOOP_START_OFFSET] = (byte) renderedVoice.loopStartSegment;
            out[IIX_LOOP_END_OFFSET] = (byte) renderedVoice.loopEndSegment;
            out[IIX_LOOP_MODE_OFFSET] = 1;
        }
        return out;
    }


    /**
     * Create the data of one voice file in the native format of the QasarBeach recreation. The
     * unknown parts are taken from a template captured from a QasarBeach save; the name, the 16-bit
     * audio, the loop, the damping (release) and the volume are patched in.
     *
     * @param zone The zone
     * @param renderedVoice The rendered voice
     * @param name The name of the voice
     * @return The file data
     * @throws IOException Could not load the template
     */
    private byte [] createQasarBeachFileData (final ISampleZone zone, final RenderedVoice renderedVoice, final String name) throws IOException
    {
        final byte [] out = this.getQasarBeachTemplate ().clone ();

        for (int i = 0; i < 8; i++)
        {
            final char c = i < name.length () ? name.charAt (i) : ' ';
            out[QBV2_NAME_OFFSET + i] = (byte) (c >= 32 && c <= 126 ? c : '_');
        }

        for (int i = 0; i < IIX_NUM_SAMPLES; i++)
        {
            out[QBV2_AUDIO_OFFSET + i * 2] = (byte) (renderedVoice.samples[i] & 0xFF);
            out[QBV2_AUDIO_OFFSET + i * 2 + 1] = (byte) (renderedVoice.samples[i] >> 8 & 0xFF);
        }

        // The control chunk: the 0-based inclusive loop segments and the loop switch
        out[QBV2_CONTROL_OFFSET + 7] = (byte) (renderedVoice.isLooped ? renderedVoice.loopStartSegment : 0);
        out[QBV2_CONTROL_OFFSET + 8] = (byte) (renderedVoice.isLooped ? renderedVoice.loopEndSegment : 0);
        out[QBV2_CONTROL_OFFSET + 12] = (byte) (renderedVoice.isLooped ? 1 : 0);

        // Damping (release) and volume are stored 0-based like all Page 7 values. The time law of
        // the damping was measured from two QasarBeach recordings (dial value 23 fades with a time
        // constant of 0.087s, 128 with 0.311s): fade-out time = 0.0255 * value^0.741 seconds. The
        // damping of the format maxes out at roughly 0.9 seconds
        final IEnvelopeModulator modulator = zone.getAmplitudeEnvelopeModulator ();
        final IEnvelope envelope = modulator.getDepth () > 0 ? modulator.getSource () : null;
        final double release = limitToDefault (envelope == null ? 0 : envelope.getReleaseTime (), 0);
        final long dampingValue = Math.round (Math.pow (release / QBV2_DAMPING_TIME_SCALE, 1.0 / QBV2_DAMPING_TIME_EXPONENT));
        out[QBV2_DAMPING_OFFSET] = (byte) (Math.clamp (dampingValue, 5, 128) - 1);
        out[QBV2_VOLUME_OFFSET] = (byte) Math.clamp (Math.round (128.0 * Math.pow (10, zone.getGain () / 20.0)) - 1, 0, 127);
        return out;
    }


    /**
     * Get the template for the native QasarBeach format, which provides all unknown parts of the
     * format with the defaults of a voice saved by QasarBeach itself.
     *
     * @return The template data
     * @throws IOException Could not load the template resource
     */
    private byte [] getQasarBeachTemplate () throws IOException
    {
        if (this.qasarBeachTemplate == null)
            try (final InputStream in = FairlightCmi3Creator.class.getResourceAsStream ("QBV2Template.bin"))
            {
                if (in == null)
                    throw new IOException ("Missing resource QBV2Template.bin");
                this.qasarBeachTemplate = in.readAllBytes ();
            }
        return this.qasarBeachTemplate;
    }


    /**
     * Create the data of the control (CO) file of a voice. The control file carries the parameters
     * which the voice file itself does not: the amplitude envelope (ATTACK and DAMPING in
     * milliseconds), the level and the loop switch with its 1-based start segment and length. The
     * loop values are taken from the already encoded voice file data.
     *
     * @param zone The zone
     * @param voiceFileData The encoded voice file data
     * @return The control file data
     */
    private static byte [] createIIxControlFileData (final ISampleZone zone, final byte [] voiceFileData)
    {
        final boolean isLooped = voiceFileData[IIX_LOOP_MODE_OFFSET] == 1;
        final int loopStartSegment = Byte.toUnsignedInt (voiceFileData[IIX_LOOP_START_OFFSET]);
        final int loopEndSegment = Byte.toUnsignedInt (voiceFileData[IIX_LOOP_END_OFFSET]);

        final IEnvelopeModulator modulator = zone.getAmplitudeEnvelopeModulator ();
        final IEnvelope envelope = modulator.getDepth () > 0 ? modulator.getSource () : null;
        final int attack = Math.clamp (Math.round (limitToDefault (envelope == null ? 0 : envelope.getAttackTime (), 0) * 1000.0), 0, 16383);
        // A minimum release prevents a click when the key is released
        final int damping = Math.clamp (Math.round (limitToDefault (envelope == null ? 0 : envelope.getReleaseTime (), 0) * 1000.0), 15, 16383);
        final int level = Math.clamp (Math.round (128.0 * Math.pow (10, zone.getGain () / 20.0)), 1, 255);

        final byte [] out = new byte [CO_FILE_SIZE];
        int index = 0;
        // MODE, SUSTAIN, MAIN LEVEL, FILTER, DAMPING-1, ATTACK, VIB DEPTH, VIB SPEED
        index = writeControlParameter (out, index, 0x0A, 0x06, CO_PATCH_VALUE, 4);
        index = writeControlParameter (out, index, 0x0B, 0x07, isLooped ? CO_PATCH_ON : CO_PATCH_OFF, 0);
        index = writeControlParameter (out, index, 0x0C, 0x01, CO_PATCH_VALUE, level);
        index = writeControlParameter (out, index, 0x0D, 0x02, CO_PATCH_VALUE, 127);
        index = writeControlParameter (out, index, 0x0E, 0x03, CO_PATCH_VALUE, damping);
        index = writeControlParameter (out, index, 0x0F, 0x08, CO_PATCH_VALUE, attack);
        index = writeControlParameter (out, index, 0x10, 0x04, CO_PATCH_VALUE, 0);
        index = writeControlParameter (out, index, 0x11, 0x05, CO_PATCH_VALUE, 0);
        // GLISSANDO, PORTAMENTO, SPEED, CONST TIME, SLUR
        index = writeControlParameter (out, index, 0xA0, 0x0A, CO_PATCH_OFF, 0);
        index = writeControlParameter (out, index, 0xB0, 0x0C, CO_PATCH_OFF, 0);
        index = writeControlParameter (out, index, 0xC0, 0x0B, CO_PATCH_VALUE, 1);
        index = writeControlParameter (out, index, 0xD0, 0x0D, CO_PATCH_OFF, 0);
        index = writeControlParameter (out, index, 0xE0, 0x0E, CO_PATCH_OFF, 0);
        // LOOP CNTRL, LOOP START, LOOP LNGTH (1-based segments, in contrast to the voice file),
        // START SEG, DEAD-SPOT
        index = writeControlParameter (out, index, 0xF0, 0x0F, isLooped ? CO_PATCH_ON : CO_PATCH_OFF, 0);
        index = writeControlParameter (out, index, 0xF2, 0x10, CO_PATCH_VALUE, isLooped ? loopStartSegment + 1 : 1);
        index = writeControlParameter (out, index, 0xF4, 0x11, CO_PATCH_VALUE, isLooped ? loopEndSegment - loopStartSegment + 1 : 1);
        index = writeControlParameter (out, index, 0xF6, 0x12, CO_PATCH_VALUE, 1);
        index = writeControlParameter (out, index, 0xF7, 0x13, CO_PATCH_OFF, 0);
        // PITCHBEND, BENDWIDTH, B/F LOOP, VIB DELAY, VIB ATTACK, AUX LEVEL, DAMP-MODE, DAMPING-2
        index = writeControlParameter (out, index, 0xF8, 0x14, CO_PATCH_VALUE, 64);
        index = writeControlParameter (out, index, 0xF9, 0x15, CO_PATCH_VALUE, 0);
        index = writeControlParameter (out, index, 0xFA, 0x16, CO_PATCH_OFF, 0);
        index = writeControlParameter (out, index, 0xFB, 0x17, CO_PATCH_VALUE, 0);
        index = writeControlParameter (out, index, 0xFC, 0x18, CO_PATCH_VALUE, 0);
        index = writeControlParameter (out, index, 0xFD, 0x19, CO_PATCH_VALUE, 128);
        index = writeControlParameter (out, index, 0xFE, 0x1A, CO_PATCH_VALUE, 1);
        writeControlParameter (out, index, 0xFF, 0x1B, CO_PATCH_VALUE, damping);
        return out;
    }


    /**
     * Write one 8 byte parameter entry of a control file.
     *
     * @param out The control file data
     * @param index The index of the entry
     * @param parameterID The ID of the parameter
     * @param unknown The fixed value of the unknown second byte of the parameter
     * @param patch What the parameter is patched to, one of the CO_PATCH_* constants
     * @param value The value of the parameter
     * @return The index of the next entry
     */
    private static int writeControlParameter (final byte [] out, final int index, final int parameterID, final int unknown, final int patch, final int value)
    {
        final int offset = CO_PARAM_OFFSET + index * 8;
        out[offset] = (byte) parameterID;
        out[offset + 1] = (byte) unknown;
        out[offset + 2] = (byte) patch;
        out[offset + 3] = (byte) (value >> 8 & 0xFF);
        out[offset + 4] = (byte) (value & 0xFF);
        return index + 1;
    }


    /**
     * Write a big-endian unsigned 16-bit integer.
     *
     * @param out The data to write to
     * @param offset The offset to write to
     * @param value The value to write
     */
    private static void writeBE16 (final byte [] out, final int offset, final int value)
    {
        out[offset] = (byte) (value >> 8 & 0xFF);
        out[offset + 1] = (byte) (value & 0xFF);
    }


    /**
     * Write a big-endian unsigned 24-bit integer.
     *
     * @param out The data to write to
     * @param offset The offset to write to
     * @param value The value to write
     */
    private static void writeBE24 (final byte [] out, final int offset, final int value)
    {
        out[offset] = (byte) (value >> 16 & 0xFF);
        out[offset + 1] = (byte) (value >> 8 & 0xFF);
        out[offset + 2] = (byte) (value & 0xFF);
    }


    /**
     * Write a big-endian unsigned 32-bit integer.
     *
     * @param out The data to write to
     * @param offset The offset to write to
     * @param value The value to write
     */
    private static void writeBE32 (final byte [] out, final int offset, final int value)
    {
        out[offset] = (byte) (value >> 24 & 0xFF);
        out[offset + 1] = (byte) (value >> 16 & 0xFF);
        out[offset + 2] = (byte) (value >> 8 & 0xFF);
        out[offset + 3] = (byte) (value & 0xFF);
    }
}
