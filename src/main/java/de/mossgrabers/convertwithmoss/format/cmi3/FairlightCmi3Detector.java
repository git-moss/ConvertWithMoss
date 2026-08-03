// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.cmi3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detector for Fairlight CMI Voice (VC) files. Two dialects are supported: the Series III voice
 * files with their sub-voices and the fixed-size 8-bit voice files of the CMI I/II/IIx, which are
 * also read and written by the QasarBeach recreation and read by the Arturia CMI V.
 *
 * @author Jürgen Moßgraber
 */
public class FairlightCmi3Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final int VC_VERSION_A          = 768;
    private static final int VC_VERSION_B          = 769;
    private static final int VC_NAME_SIZE          = 16;
    private static final int FUNC_BLOCK_BASE       = 768;
    private static final int SAMPLE_DATA_OFFSET    = 2304;

    private static final int IIX_FILE_SIZE         = 21888;
    private static final int IIX_HEADER_SIZE       = 0x1500;
    private static final int IIX_NUM_SAMPLES       = 16384;
    private static final int IIX_SEGMENT_SIZE      = 128;
    private static final int IIX_LOOP_START_OFFSET = 0x1332;
    private static final int IIX_LOOP_END_OFFSET   = 0x1333;
    private static final int IIX_LOOP_MODE_OFFSET  = 0x133B;
    /** The IIx dialect stores no sample rate - the documented default sampling rate of the IIx. */
    private static final int IIX_SAMPLE_RATE       = 14080;
    /** The native voice format of the QasarBeach recreation: 16-bit audio at offset 0x11. */
    private static final int QBV2_AUDIO_OFFSET     = 0x11;
    /** The control chunk of a QasarBeach voice follows directly after the audio data. */
    private static final int QBV2_CONTROL_OFFSET   = QBV2_AUDIO_OFFSET + IIX_NUM_SAMPLES * 2;
    /** The damping (release) and volume in the parameter block of a QasarBeach voice. */
    private static final int QBV2_DAMPING_OFFSET   = 0xD3DD;
    private static final int QBV2_VOLUME_OFFSET    = 0xD3CB;
    /**
     * The CMI II reads one 128 sample segment per waveform period, therefore a voice plays at its
     * original pitch on the key with the frequency of the sample rate divided by 128. For the
     * 14080 Hz default rate this is the 110 Hz A below the middle C.
     */
    private static final int IIX_ROOT_KEY          = 45;


    /** All parsed properties of a single CMI3 sub-voice. */
    private static class SubVoice
    {
        int                idA             = 0;
        int                idB             = 0;
        int                bitRate         = 16;
        int                sizeA           = 0;
        int                sizeB           = 0;
        int                sampleRate      = 44100;
        String             name            = "";
        int                tune            = 0;
        int                wordA           = 0;
        int                wordB           = 0;
        int                startA          = 0;
        int                startB          = 0;
        int                endA            = 0;
        int                endB            = 0;
        int                loopStartA      = 0;
        int                loopStartB      = 0;
        int                loopEndA        = 0;
        int                loopEndB        = 0;
        boolean            interleaved     = false;
        boolean            loop            = false;
        boolean            releaseLoop     = false;
        double             attackFast      = 0.0;
        double             attackSlow      = 0.0;
        double             hold            = 0.0;
        double             decay           = 0.0;
        double             sustain         = 0.0;
        double             amp             = 0.0;
        double             releaseFast     = 0.0;
        double             releaseSlow     = 0.0;
        boolean            attackExtended  = false;
        boolean            releaseExtended = false;

        IAudioMetadata     audioMetadata;
        InMemorySampleData sampleData;
        IAudioMetadata     audioMetadataR;
        InMemorySampleData sampleDataR;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public FairlightCmi3Detector (final INotifier notifier)
    {
        super ("Fairlight CMI3 Voice", "CMI3", notifier, new MetadataSettingsUI ("CMI3"), ".vc");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        try (final InputStream stream = new FileInputStream (sourceFile))
        {
            return Collections.singletonList (this.read (stream, sourceFile));
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", ex);
            return Collections.emptyList ();
        }
    }


    private IMultisampleSource read (final InputStream inputStream, final File sourceFile) throws IOException, ParseException
    {
        final byte [] inBytes = inputStream.readAllBytes ();

        // Files of the 8-bit CMI I/II/IIx dialect (e.g. from QasarBeach or the original library
        // floppies) have no version word; they are identified by their fixed file sizes. The
        // QasarBeach recreation saves voices in its own 16-bit format with a magic tag
        final int version = readBE16 (inBytes, 0);
        if (version != VC_VERSION_A && version != VC_VERSION_B)
        {
            if (isQasarBeachFile (inBytes))
                return this.readQasarBeach (inBytes, sourceFile);
            if (isIIxFile (inBytes.length))
                return this.readIIx (inBytes, sourceFile);
            throw new ParseException (Functions.getMessage ("IDS_CMI3_UNKNOWN_VERSION", Integer.toString (version)));
        }

        final int channels = Byte.toUnsignedInt (inBytes[16]) >= 127 ? 2 : 1;

        // Build sub-voice ID and zone-offset lookup tables from the file header
        final List<Integer> subvoiceIDs = new ArrayList<> ();
        final List<Integer> zoneOffsets = new ArrayList<> ();
        for (int i = 0; i < 128; i++)
        {
            final int id = inBytes[i * 4 + 259];
            if (id <= 0)
                break;
            subvoiceIDs.add (Integer.valueOf (Byte.toUnsignedInt (inBytes[i * 4 + 259])));
            zoneOffsets.add (Integer.valueOf (readBE24 (inBytes, i * 4 + 256) * 256));
        }
        final int numSubVoices = subvoiceIDs.size ();

        // Extract global voice tune and key-mapping table offset from function blocks
        final int [] voiceParams = parseVoiceFunctions (inBytes);
        final int voiceTune = voiceParams[0];
        final int mappingOffset = voiceParams[1];

        // Parse each sub-voice header, functions, and assemble PCM data
        final SubVoice [] subVoices = new SubVoice [numSubVoices];
        for (int i = 0; i < numSubVoices; i++)
        {
            subVoices[i] = parseSubVoice (inBytes, i, zoneOffsets.get (i).intValue (), channels, FileUtils.getNameWithoutType (sourceFile));
            buildSubVoiceSampleData (inBytes, subVoices, i, channels, zoneOffsets);
        }

        // Create sample zones from the 128-key mapping table
        final IGroup group = new DefaultGroup ("CMI3");
        buildSampleZones (inBytes, mappingOffset, numSubVoices, subvoiceIDs, subVoices, channels, voiceTune, group);

        return this.createMultisampleSource (sourceFile, FileUtils.getNameWithoutType (sourceFile), Collections.singletonList (group));
    }


    /**
     * Read the control (CO) file referenced by a IIx voice file and apply its parameters: the
     * loop switch with its 1-based start segment and length, the attack and the damping (release)
     * times in milliseconds. The parameters are stored in 8 byte entries at fixed addresses with
     * the patch type in the third byte (0xB1 = a value, 0xC0/0xC1 = a boolean which is on/off) and
     * a big-endian value in the fourth and fifth byte.
     *
     * @param vcData The content of the voice file
     * @param sourceFile The voice file, the control file is searched next to it
     * @param zone The zone to apply the parameters to
     * @return True if a control file was found and applied
     */
    private boolean applyIIxControlFile (final byte [] vcData, final File sourceFile, final ISampleZone zone)
    {
        final String name = new String (vcData, 0x00A0, 8, StandardCharsets.US_ASCII).trim ();
        if (name.isEmpty () || name.chars ().anyMatch (c -> c < 32 || c > 126))
            return false;

        final File folder = sourceFile.getParentFile ();
        File controlFile = new File (folder, name + ".CO");
        if (!controlFile.exists ())
            controlFile = new File (folder, name + ".co");
        if (!controlFile.exists ())
            return false;

        try
        {
            final byte [] controlData = Files.readAllBytes (controlFile.toPath ());
            if (controlData.length < 0x150)
                return false;

            // LOOP CNTRL at 0xE8, LOOP START at 0xF0 and LOOP LNGTH at 0xF8
            if ((controlData[0xE8 + 2] & 0xFF) == 0xC0)
            {
                final int startSegment = readBE16 (controlData, 0xF0 + 3);
                final int length = readBE16 (controlData, 0xF8 + 3);
                if (startSegment >= 1 && startSegment <= 128 && length >= 1)
                {
                    final DefaultSampleLoop loop = new DefaultSampleLoop ();
                    loop.setStart ((startSegment - 1) * IIX_SEGMENT_SIZE);
                    loop.setEnd (Math.min ((startSegment - 1 + length) * IIX_SEGMENT_SIZE, IIX_NUM_SAMPLES) - 1);
                    if (loop.getStart () < loop.getEnd ())
                        zone.addLoop (loop);
                }
            }

            // ATTACK at 0xA8 and DAMPING-1 at 0xA0, both in milliseconds
            final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
            if ((controlData[0xA8 + 2] & 0xFF) == 0xB1)
                envelope.setAttackTime (readBE16 (controlData, 0xA8 + 3) / 1000.0);
            if ((controlData[0xA0 + 2] & 0xFF) == 0xB1)
                envelope.setReleaseTime (readBE16 (controlData, 0xA0 + 3) / 1000.0);
            return true;
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex);
            return false;
        }
    }


    /**
     * Check if this is a voice file saved by the QasarBeach recreation of the IIx, marked with the
     * 'QBV2' tag.
     *
     * @param data The content of the file
     * @return True if it is a QasarBeach voice file
     */
    private static boolean isQasarBeachFile (final byte [] data)
    {
        return data.length >= QBV2_CONTROL_OFFSET && data[0] == 'Q' && data[1] == 'B' && data[2] == 'V' && data[3] == '2';
    }


    /**
     * Read a voice file saved by the QasarBeach recreation: 'QBV2', the voice name, 16-bit
     * little-endian audio of 16384 samples and a control ('QBC9') chunk with the mode and the
     * 0-based inclusive loop segments. The same segment playback law as for the 8-bit dialect
     * applies, therefore the voice gets the same default rate and root.
     *
     * @param inBytes The content of the file
     * @param sourceFile The source file
     * @return The multi-sample source
     */
    private IMultisampleSource readQasarBeach (final byte [] inBytes, final File sourceFile)
    {
        String name = new String (inBytes, 4, 8, StandardCharsets.US_ASCII).trim ();
        if (name.isEmpty ())
            name = FileUtils.getNameWithoutType (sourceFile);

        final ISampleZone zone = new DefaultSampleZone (name, 0, 127);
        zone.setKeyRoot (IIX_ROOT_KEY);
        zone.setKeyTracking (1);
        final byte [] audio = Arrays.copyOfRange (inBytes, QBV2_AUDIO_OFFSET, QBV2_CONTROL_OFFSET);
        zone.setSampleData (new InMemorySampleData (new DefaultAudioMetadata (1, IIX_SAMPLE_RATE, 16, IIX_NUM_SAMPLES), audio));

        // The control chunk: [5] = mode, [7] = loop start segment, [8] = loop end segment, [12]
        // is the loop switch
        if (inBytes.length >= QBV2_CONTROL_OFFSET + 16 && inBytes[QBV2_CONTROL_OFFSET] == 'Q' && inBytes[QBV2_CONTROL_OFFSET + 1] == 'B' && inBytes[QBV2_CONTROL_OFFSET + 2] == 'C')
        {
            final int startSegment = Byte.toUnsignedInt (inBytes[QBV2_CONTROL_OFFSET + 7]);
            final int endSegment = Byte.toUnsignedInt (inBytes[QBV2_CONTROL_OFFSET + 8]);
            if (inBytes[QBV2_CONTROL_OFFSET + 12] == 1 && startSegment <= endSegment && endSegment < 128)
            {
                final DefaultSampleLoop loop = new DefaultSampleLoop ();
                loop.setStart (startSegment * IIX_SEGMENT_SIZE);
                loop.setEnd ((endSegment + 1) * IIX_SEGMENT_SIZE - 1);
                zone.addLoop (loop);
            }
        }

        // Damping (release) and volume from the parameter block, stored 0-based. A value with the
        // top bit set is patched to a modulator and is skipped. The time law of the damping was
        // measured from QasarBeach recordings: fade-out time = 0.0255 * dial value^0.741 seconds
        if (inBytes.length > QBV2_DAMPING_OFFSET)
        {
            final int damping = Byte.toUnsignedInt (inBytes[QBV2_DAMPING_OFFSET]);
            if (damping < 0x80)
                zone.getAmplitudeEnvelopeModulator ().getSource ().setReleaseTime (0.0255 * Math.pow (damping + 1.0, 0.741));
            final int volume = Byte.toUnsignedInt (inBytes[QBV2_VOLUME_OFFSET]);
            if (volume < 0x80)
                zone.setGain (20.0 * Math.log10 ((volume + 1) / 128.0));
        }

        final IGroup group = new DefaultGroup ("IIx");
        group.addSampleZone (zone);
        return this.createMultisampleSource (sourceFile, name, Collections.singletonList (group));
    }


    /**
     * Check if the file size matches one of the fixed sizes of the 8-bit voice file dialect: the
     * bare 16 KB audio data, or the audio data with the header and optionally the footer.
     *
     * @param length The length of the file
     * @return True if it is a IIx voice file
     */
    private static boolean isIIxFile (final int length)
    {
        return length == IIX_NUM_SAMPLES || length == IIX_HEADER_SIZE + IIX_NUM_SAMPLES || length == IIX_FILE_SIZE;
    }


    /**
     * Read a voice file of the 8-bit CMI I/II/IIx dialect. The audio data is 16384 bytes of 8-bit
     * unsigned samples. Since the dialect stores no sample rate or root note, the documented
     * default rate of the IIx is used with the root at middle C.
     *
     * @param inBytes The content of the file
     * @param sourceFile The source file
     * @return The multi-sample source
     */
    private IMultisampleSource readIIx (final byte [] inBytes, final File sourceFile)
    {
        final int audioOffset = inBytes.length == IIX_NUM_SAMPLES ? 0 : IIX_HEADER_SIZE;
        final byte [] audio = Arrays.copyOfRange (inBytes, audioOffset, audioOffset + IIX_NUM_SAMPLES);

        final String name = FileUtils.getNameWithoutType (sourceFile);
        final ISampleZone zone = new DefaultSampleZone (name, 0, 127);
        zone.setKeyRoot (IIX_ROOT_KEY);
        zone.setKeyTracking (1);
        zone.setSampleData (new InMemorySampleData (new DefaultAudioMetadata (1, IIX_SAMPLE_RATE, 8, IIX_NUM_SAMPLES), audio));

        // The control parameters live in the referenced control (CO) file, which wins over the
        // loop bytes cached in the voice file when it is present next to it
        if (audioOffset > 0 && !this.applyIIxControlFile (inBytes, sourceFile, zone) && inBytes[IIX_LOOP_MODE_OFFSET] == 1)
        {
            // The loop is stored as 128 sample segments with an inclusive end segment
            final int startSegment = Byte.toUnsignedInt (inBytes[IIX_LOOP_START_OFFSET]);
            final int endSegment = Byte.toUnsignedInt (inBytes[IIX_LOOP_END_OFFSET]);
            if (startSegment < 128 && endSegment < 128 && startSegment <= endSegment)
            {
                final DefaultSampleLoop loop = new DefaultSampleLoop ();
                loop.setStart (startSegment * IIX_SEGMENT_SIZE);
                loop.setEnd ((endSegment + 1) * IIX_SEGMENT_SIZE - 1);
                zone.addLoop (loop);
            }
        }

        final IGroup group = new DefaultGroup ("IIx");
        group.addSampleZone (zone);
        return this.createMultisampleSource (sourceFile, name, Collections.singletonList (group));
    }


    /**
     * Scans the voice-level function block chain starting at offset {@value #FUNC_BLOCK_BASE}.
     *
     * @param data The voice data
     * @return {@code int[2]}: [0] = voiceTune, [1] = key-mapping table offset
     */
    private static int [] parseVoiceFunctions (final byte [] data)
    {
        int voiceTune = 0;
        int mappingOffset = 0;
        int pos = FUNC_BLOCK_BASE;

        while (Byte.toUnsignedInt (data[pos + 1]) > 2 && data[pos + 1] != 11)
        {
            final int entrySize = Byte.toUnsignedInt (data[pos + 1]);
            final int cp = pos + 2; // start of entry content

            switch (data[cp])
            {
                case 6:
                    mappingOffset = cp + 2;
                    break;
                case 9:
                    if (data[cp + 2] == 24)
                        voiceTune = readBE16 (data, cp + 4);
                    break;
                default:
                    break;
            }
            pos = cp + entrySize;
        }
        return new int []
        {
            voiceTune,
            mappingOffset
        };
    }


    /**
     * Parses the fixed-layout header and function blocks of one sub-voice.
     *
     * @param data The voice data
     * @param index The index of the sub-voice
     * @param zoneOffset The zone offset
     * @param channels The number of channels
     * @param baseName The prefix name
     * @return The sub-voice
     */
    private static SubVoice parseSubVoice (final byte [] data, final int index, final int zoneOffset, final int channels, final String baseName)
    {
        final SubVoice sv = new SubVoice ();

        sv.idA = data[zoneOffset + 16];
        sv.bitRate = data[zoneOffset + 17] == 2 ? 16 : 8;
        sv.sizeA = readBE32 (data, zoneOffset + 18);
        final int srTemp = readBE32 (data, zoneOffset + 22);
        sv.sampleRate = srTemp == 0 ? 44100 : srTemp;

        if (channels == 2)
        {
            sv.idB = data[zoneOffset + 33];
            sv.sizeB = readBE32 (data, zoneOffset + 34);
        }

        sv.name = parseName (data, zoneOffset + 42, baseName, index);
        parseSubVoiceFunctions (sv, data, zoneOffset + 256);

        // Interleaved stereo: both channels share the same word, start, end, and loop region
        if (channels == 2 && sv.wordA == sv.wordB && sv.startA == sv.startB && sv.endA == sv.endB && sv.loopStartA == sv.loopStartB && sv.loopEndA == sv.loopEndB)
            sv.interleaved = true;

        return sv;
    }


    /**
     * Decodes the null-terminated 7-bit name field and appends a zero-padded index suffix. Falls
     * back to {@code baseName_N_NNN} when the name field is empty.
     *
     * @param data The voice data
     * @param offset The offset to the name
     * @param baseName The prefix name
     * @param index The index to use for the name
     * @return The full name to use
     */
    private static String parseName (final byte [] data, final int offset, final String baseName, final int index)
    {
        final String suffix = "_" + String.format ("%03d", Integer.valueOf (index));
        if (data[offset] == 0x00)
            return baseName + "_" + (index + 1) + suffix;

        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < VC_NAME_SIZE; i++)
        {
            if (data[offset + i] == 0x00)
                break;
            sb.append ((char) (data[offset + i] & 0x7F));
        }
        return sb + suffix;
    }


    /**
     * Iterates the sub-voice function block chain and populates the SubVoice fields.
     *
     * @param subVoice The sub-voice to populate
     * @param data The data to read from
     * @param startPos The start of the sub-voice data
     */
    private static void parseSubVoiceFunctions (final SubVoice subVoice, final byte [] data, final int startPos)
    {
        int pos = startPos;
        while (Byte.toUnsignedInt (data[pos + 1]) > 2 && data[pos + 1] != 11)
        {
            final int entrySize = Byte.toUnsignedInt (data[pos + 1]);
            final int cp = pos + 2;

            switch (data[cp])
            {
                case 9:
                    parseEnvelopeParam (subVoice, data, cp);
                    break;
                case 13:
                    subVoice.wordA = data[cp + 3];
                    subVoice.startA = readBE32 (data, cp + 4);
                    subVoice.endA = readBE32 (data, cp + 8);
                    subVoice.loopStartA = readBE32 (data, cp + 12);
                    subVoice.loopEndA = readBE32 (data, cp + 16);
                    break;
                case 18:
                    subVoice.wordB = data[cp + 3];
                    subVoice.startB = readBE32 (data, cp + 4);
                    subVoice.endB = readBE32 (data, cp + 8);
                    subVoice.loopStartB = readBE32 (data, cp + 12);
                    subVoice.loopEndB = readBE32 (data, cp + 16);
                    break;
                default:
                    break;
            }
            pos = cp + entrySize;
        }
    }


    /**
     * Decodes a single type-9 parameter entry and writes it into the SubVoice.
     *
     * @param subVoice The sub-voice to populate
     * @param data The data to read from
     * @param offset The offset to the envelope data
     */
    private static void parseEnvelopeParam (final SubVoice subVoice, final byte [] data, final int offset)
    {
        final int rawValue = readBE16 (data, offset + 4);
        switch (data[offset + 2])
        {
            case 5:
                subVoice.attackFast = toSignedNormalized (rawValue, 4096);
                break;
            case 6:
                subVoice.hold = toSignedNormalized (rawValue, 4096);
                break;
            case 7:
                subVoice.decay = toSignedNormalized (rawValue, 2048);
                break;
            case 8:
                subVoice.sustain = levelConvert (rawValue);
                break;
            case 9:
                subVoice.amp = levelConvertDB (rawValue);
                break;
            case 10:
                subVoice.releaseFast = toSignedNormalized (rawValue, 2048);
                break;
            case 16:
                subVoice.attackSlow = toSignedNormalized (rawValue, 4096);
                break;
            case 17:
                subVoice.releaseSlow = toSignedNormalized (rawValue, 2048);
                break;
            case 24:
                subVoice.tune = rawValue;
                break;
            case 27:
                subVoice.attackExtended = Byte.toUnsignedInt (data[offset + 3]) > 127;
                break;
            case 28:
                subVoice.releaseExtended = Byte.toUnsignedInt (data[offset + 3]) > 127;
                break;
            case 29:
                subVoice.loop = Byte.toUnsignedInt (data[offset + 3]) > 127;
                break;
            case 42:
                subVoice.releaseLoop = Byte.toUnsignedInt (data[offset + 3]) > 127;
                break;
            default:
                break;
        }
    }


    /**
     * Assembles left and (where applicable) right PCM data for the given sub-voices.
     *
     * @param data The data to read from
     * @param subVoices The sub-voice for which to read the sample data
     * @param index The index of the sub-voice
     * @param channels The number of channels
     * @param zoneOffsets The offsets
     */
    private static void buildSubVoiceSampleData (final byte [] data, final SubVoice [] subVoices, final int index, final int channels, final List<Integer> zoneOffsets)
    {
        final SubVoice subVoice = subVoices[index];
        final int posL = findSamplePosByIdA (subVoices, subVoice.idA, zoneOffsets);

        final byte [] dataL = Arrays.copyOfRange (data, posL, posL + subVoice.sizeA);
        byte [] pcmData;

        if (channels == 2 && subVoice.interleaved)
        {
            final int posR = findInterleavedRightPos (subVoice, posL, zoneOffsets);
            final byte [] dataR = Arrays.copyOfRange (data, posR, posR + subVoice.sizeA);
            pcmData = interleaveChannels (dataL, dataR, subVoice.sizeA);
        }
        else
            pcmData = dataL;

        applyByteOrder (pcmData, subVoice.bitRate);

        subVoice.audioMetadata = new DefaultAudioMetadata (subVoice.interleaved ? 2 : 1, subVoice.sampleRate, subVoice.bitRate, subVoice.sizeA / 2);
        subVoice.sampleData = new InMemorySampleData (subVoice.audioMetadata, pcmData);

        if (channels == 2 && !subVoice.interleaved)
        {
            final int posR = findSeparateRightPos (subVoices, subVoice, zoneOffsets);
            final byte [] dataR = Arrays.copyOfRange (data, posR, posR + subVoice.sizeA);
            applyByteOrder (dataR, subVoice.bitRate); // fixed: was always flipBytes
            subVoice.audioMetadataR = new DefaultAudioMetadata (1, subVoice.sampleRate, subVoice.bitRate, subVoice.sizeB / 2);
            subVoice.sampleDataR = new InMemorySampleData (subVoice.audioMetadataR, dataR);
        }
    }


    /**
     * Finds the sample-data file position for the first sub-voice whose {@code idA} matches.
     *
     * @param subVoices The sub-voices
     * @param idA The ID to look for
     * @param zoneOffsets The offsets
     * @return The offset to the sample data
     */
    private static int findSamplePosByIdA (final SubVoice [] subVoices, final int idA, final List<Integer> zoneOffsets)
    {
        for (int i = 0; i < subVoices.length; i++)
            if (subVoices[i].idA == idA)
                return zoneOffsets.get (i).intValue () + SAMPLE_DATA_OFFSET;
        return zoneOffsets.get (0).intValue () + SAMPLE_DATA_OFFSET;
    }


    /**
     * Computes the right-channel sample position for an interleaved stereo sub-voice.
     *
     * @param subVoice The sub-voice
     * @param posL The position of the left sample
     * @param zoneOffsets The offsets
     * @return The right offset
     */
    private static int findInterleavedRightPos (final SubVoice subVoice, final int posL, final List<Integer> zoneOffsets)
    {
        if ((subVoice.idB & 127) == subVoice.idA)
            return posL + subVoice.sizeA;

        // idB is used as a direct zone-offset list index in this layout
        int posR = zoneOffsets.get (subVoice.idB).intValue () + SAMPLE_DATA_OFFSET;
        if (subVoice.idB < 0)
            posR += subVoice.sizeA;
        return posR;
    }


    /**
     * Computes the right-channel sample position for a separate (non-interleaved) stereo sub-voice.
     *
     * @param subVoices The sub-voices
     * @param subVoice The sub-voice
     * @param zoneOffsets The offsets
     * @return The right position
     */
    private static int findSeparateRightPos (final SubVoice [] subVoices, final SubVoice subVoice, final List<Integer> zoneOffsets)
    {
        for (int i = 0; i < subVoices.length; i++)
            if (subVoices[i].idB == subVoice.idB)
            {
                int posR = zoneOffsets.get (i).intValue () + SAMPLE_DATA_OFFSET;
                if (subVoice.idB > 127 || subVoice.idB < 0)
                    posR += subVoice.sizeA;
                return posR;
            }
        return zoneOffsets.get (0).intValue () + SAMPLE_DATA_OFFSET;
    }


    /**
     * Interleaves two mono 16-bit PCM buffers into a stereo buffer (L0 R0 L1 R1 …).
     *
     * @param left The data of the left sample
     * @param right The data of the right sample
     * @param sizeA The size of 1 channel
     * @return The interleaved stereo data
     */
    private static byte [] interleaveChannels (final byte [] left, final byte [] right, final int sizeA)
    {
        final byte [] out = new byte [sizeA * 2];
        for (int i = 0; i < sizeA / 2; i++)
        {
            out[i * 4] = left[i * 2];
            out[i * 4 + 1] = left[i * 2 + 1];
            out[i * 4 + 2] = right[i * 2];
            out[i * 4 + 3] = right[i * 2 + 1];
        }
        return out;
    }


    /**
     * Applies the appropriate byte-order correction in-place based on bit depth.
     *
     * @param data The data to flip
     * @param bitRate The bit-rate
     */
    private static void applyByteOrder (final byte [] data, final int bitRate)
    {
        if (bitRate == 16)
            flipBytes (data);
        else
            flipBits (data);
    }


    /**
     * Builds {@link DefaultSampleZone} objects for all 128 keys from the mapping table.
     *
     * @param data The data
     * @param mappingOffset The mapping offset
     * @param numSubVoices The number of sub-voices
     * @param subvoiceIDs The IDs of the sub-voices
     * @param subVoices All sub-voice objects
     * @param channels The number of channels
     * @param voiceTune The voice tuning
     * @param group The group where to add the created sample zones
     */
    private static void buildSampleZones (final byte [] data, final int mappingOffset, final int numSubVoices, final List<Integer> subvoiceIDs, final SubVoice [] subVoices, final int channels, final int voiceTune, final IGroup group)
    {
        final byte [] mapping = Arrays.copyOfRange (data, mappingOffset, mappingOffset + 128);
        int prevMappingID = -1;

        for (int key = 0; key < 128; key++)
        {
            final int mappingID = Byte.toUnsignedInt (mapping[key]);

            // Skip: unmapped keys (0), out-of-range IDs, repeated span, or unregistered IDs
            if (mappingID == 0 || mappingID > numSubVoices || mappingID == prevMappingID || !subvoiceIDs.contains (Integer.valueOf (mappingID)))
                continue;

            final int svIndex = subvoiceIDs.indexOf (Integer.valueOf (mappingID));
            final SubVoice sv = subVoices[svIndex];
            final int keyHigh = findKeyHigh (mapping, key);

            final ISampleZone zone = buildZone (sv, key, keyHigh, voiceTune, false);
            zone.setSampleData (sv.sampleData);

            if (channels == 2 && !sv.interleaved)
            {
                zone.setName (sv.name + "_L");
                zone.setPanning (-1);

                final ISampleZone zoneR = buildZone (sv, key, keyHigh, voiceTune, true);
                zoneR.setName (sv.name + "_R");
                zoneR.setPanning (1);
                zoneR.setSampleData (sv.sampleDataR);
                group.addSampleZone (zoneR);
            }

            group.addSampleZone (zone);
            prevMappingID = mappingID;
        }
    }


    /**
     * Constructs a fully configured {@link ISampleZone} for a sub-voice.
     *
     * @param subVoice Source sub-voice data
     * @param keyLow Lowest MIDI key of the zone
     * @param keyHigh Highest MIDI key of the zone
     * @param voiceTune Global voice tune offset
     * @param useB {@code true} to use channel-B loop points (right-channel zone)
     * @return The created sample zone
     */
    private static ISampleZone buildZone (final SubVoice subVoice, final int keyLow, final int keyHigh, final int voiceTune, final boolean useB)
    {
        final ISampleZone zone = new DefaultSampleZone (subVoice.name, keyLow, keyHigh);
        zone.setGain (subVoice.amp);

        if (subVoice.tune == -1)
        {
            zone.setKeyTracking (0);
            zone.setKeyRoot (useB ? 60 : 65);
        }
        else
        {
            final double pitch = pitchConvert (subVoice.tune, voiceTune, subVoice.sampleRate);
            final int root = (int) Math.round (pitch);
            zone.setKeyTracking (1);
            zone.setKeyRoot (root < 0 ? root + 128 : root);
            zone.setTuning ((pitch - root) / -1.0);
        }

        if (subVoice.loop)
        {
            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (useB ? subVoice.loopStartB : subVoice.loopStartA);
            loop.setEnd (useB ? subVoice.loopEndB : subVoice.loopEndA);
            loop.setLoopUntilRelease (subVoice.releaseLoop);
            zone.addLoop (loop);
        }

        applyEnvelope (zone, subVoice);
        return zone;
    }


    /**
     * Writes amplitude envelope parameters onto a zone, selecting fast or slow segments.
     *
     * @param zone The sample zone
     * @param subVoice The sub-voice
     */
    private static void applyEnvelope (final ISampleZone zone, final SubVoice subVoice)
    {
        final IEnvelope env = zone.getAmplitudeEnvelopeModulator ().getSource ();
        env.setAttackTime (subVoice.attackExtended ? subVoice.attackSlow : subVoice.attackFast);
        env.setHoldTime (subVoice.hold);
        env.setDecayTime (subVoice.decay);
        env.setSustainLevel (subVoice.sustain);
        env.setReleaseTime (subVoice.releaseExtended ? subVoice.releaseSlow : subVoice.releaseFast);
    }


    /**
     * Returns the highest key index that shares the same mapping ID as {@code keyLow}.
     *
     * @param mapping The mapping data
     * @param keyLow The lower key
     * @return The high key
     */
    private static int findKeyHigh (final byte [] mapping, final int keyLow)
    {
        final int id = Byte.toUnsignedInt (mapping[keyLow]);
        for (int k = keyLow + 1; k < 128; k++)
            if (Byte.toUnsignedInt (mapping[k]) != id)
                return k - 1;
        return 127;
    }


    /**
     * Converts a raw unsigned 16-bit value to a signed, normalized double. Values above 32767 are
     * treated as negative (two's-complement wrap).
     *
     * @param rawValue The raw value
     * @param divisor The divisor
     * @return The normalized value
     */
    private static double toSignedNormalized (final int rawValue, final double divisor)
    {
        double v = rawValue;
        if (v > 32767)
            v = 65536 - v;
        return v / divisor;
    }


    private static double pitchConvert (final int inV, final int gV, final int srV)
    {
        int outV = inV;
        if (outV >= 16384)
            outV -= 32768;
        int outGV = gV;
        if (outGV >= 16384)
            outGV -= 32768;
        final double sr0 = Math.log (srV / 44701.0) / Math.log (2);
        return ((-outV - outGV) / 256.0 + sr0 * 12 + 65) % 128;
    }


    private static double levelConvert (final int inV)
    {
        if (inV == 0)
            return 1;
        double outV = inV;
        if (outV >= 32768)
            outV -= 65536;
        return Math.max (0, 1.01 - Math.pow (10, outV / 256) / 100);
    }


    private static double levelConvertDB (final int inV)
    {
        double outV = inV;
        if (outV >= 32768)
            outV -= 65536;
        return outV / 512;
    }


    /**
     * Reads a big-endian unsigned 32-bit integer from {@code data[offset..offset+3]}.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The read value
     */
    private static int readBE32 (final byte [] data, final int offset)
    {
        return Byte.toUnsignedInt (data[offset]) * 16_777_216 + Byte.toUnsignedInt (data[offset + 1]) * 65_536 + Byte.toUnsignedInt (data[offset + 2]) * 256 + Byte.toUnsignedInt (data[offset + 3]);
    }


    /**
     * Reads a big-endian unsigned 16-bit integer from {@code data[offset..offset+1]}.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The read value
     */
    private static int readBE16 (final byte [] data, final int offset)
    {
        return Byte.toUnsignedInt (data[offset]) * 256 + Byte.toUnsignedInt (data[offset + 1]);
    }


    /**
     * Reads a big-endian unsigned 24-bit integer from {@code data[offset..offset+2]}.
     *
     * @param data The data
     * @param offset The offset to read from
     * @return The read value
     */
    private static int readBE24 (final byte [] data, final int offset)
    {
        return Byte.toUnsignedInt (data[offset]) * 65_536 + Byte.toUnsignedInt (data[offset + 1]) * 256 + Byte.toUnsignedInt (data[offset + 2]);
    }


    /**
     * Swaps adjacent byte pairs in-place (big-endian ↔ little-endian for 16-bit samples).
     *
     * @param data The data
     */
    private static void flipBytes (final byte [] data)
    {
        for (int i = 0; i < data.length; i += 2)
        {
            final byte temp = data[i];
            data[i] = data[i + 1];
            data[i + 1] = temp;
        }
    }


    /**
     * Flips the sign bit of every other byte in-place (8-bit unsigned ↔ signed conversion).
     *
     * @param data The data
     */
    private static void flipBits (final byte [] data)
    {
        for (int i = 0; i < data.length; i += 2)
            data[i] = (byte) (data[i] ^ 128);
    }
}