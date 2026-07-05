// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.cmi3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
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
 * Detector for Fairlight CMI3 Voice (VC) files.
 *
 * @author Jürgen Moßgraber
 */
public class FairlightCmi3Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final int VC_VERSION_A       = 768;
    private static final int VC_VERSION_B       = 769;
    private static final int VC_NAME_SIZE       = 16;
    private static final int FUNC_BLOCK_BASE    = 768;
    private static final int SAMPLE_DATA_OFFSET = 2304;


    /** All parsed properties of a single CMI3 sub-voice. */
    private static class SubVoice
    {
        int                  idA             = 0;
        int                  idB             = 0;
        int                  bitRate         = 16;
        int                  sizeA           = 0;
        int                  sizeB           = 0;
        int                  sampleRate      = 44100;
        String               name            = "";
        int                  tune            = 0;
        int                  wordA           = 0;
        int                  wordB           = 0;
        int                  startA          = 0;
        int                  startB          = 0;
        int                  endA            = 0;
        int                  endB            = 0;
        int                  loopStartA      = 0;
        int                  loopStartB      = 0;
        int                  loopEndA        = 0;
        int                  loopEndB        = 0;
        boolean              interleaved     = false;
        boolean              loop            = false;
        boolean              releaseLoop     = false;
        double               attackFast      = 0.0;
        double               attackSlow      = 0.0;
        double               hold            = 0.0;
        double               decay           = 0.0;
        double               sustain         = 0.0;
        double               amp             = 0.0;
        double               releaseFast     = 0.0;
        double               releaseSlow     = 0.0;
        boolean              attackExtended  = false;
        boolean              releaseExtended = false;

        DefaultAudioMetadata audioMetadata;
        InMemorySampleData   sampleData;
        DefaultAudioMetadata audioMetadataR;
        InMemorySampleData   sampleDataR;
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

        validateHeader (inBytes);

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
            subVoices[i] = this.parseSubVoice (inBytes, i, zoneOffsets.get (i).intValue (), channels, FileUtils.getNameWithoutType (sourceFile));
            buildSubVoiceSampleData (inBytes, subVoices, i, channels, zoneOffsets);
        }

        // Create sample zones from the 128-key mapping table
        final IGroup group = new DefaultGroup ("CMI3");
        this.buildSampleZones (inBytes, mappingOffset, numSubVoices, subvoiceIDs, subVoices, channels, voiceTune, group);

        return this.createMultisampleSource (sourceFile, FileUtils.getNameWithoutType (sourceFile), Collections.singletonList (group));
    }


    /**
     * Verifies the two-byte version word at the start of the file.
     *
     * @param data The data of the header
     * @throws ParseException Found unsupported version
     */
    private static void validateHeader (final byte [] data) throws ParseException
    {
        final int version = readBE16 (data, 0);
        if (version != VC_VERSION_A && version != VC_VERSION_B)
            throw new ParseException (Functions.getMessage ("IDS_CMI3_UNKNOWN_VERSION", Integer.toString (version)));
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
    private SubVoice parseSubVoice (final byte [] data, final int index, final int zoneOffset, final int channels, final String baseName)
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
        this.parseSubVoiceFunctions (sv, data, zoneOffset + 256);

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
    private void parseSubVoiceFunctions (final SubVoice subVoice, final byte [] data, final int startPos)
    {
        int pos = startPos;
        while (Byte.toUnsignedInt (data[pos + 1]) > 2 && data[pos + 1] != 11)
        {
            final int entrySize = Byte.toUnsignedInt (data[pos + 1]);
            final int cp = pos + 2;

            switch (data[cp])
            {
                case 9:
                    this.parseEnvelopeParam (subVoice, data, cp);
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
    private void parseEnvelopeParam (final SubVoice subVoice, final byte [] data, final int offset)
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
                subVoice.sustain = this.levelConvert (rawValue);
                break;
            case 9:
                subVoice.amp = this.levelConvertDB (rawValue);
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


    /** Interleaves two mono 16-bit PCM buffers into a stereo buffer (L0 R0 L1 R1 …). */
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


    /** Applies the appropriate byte-order correction in-place based on bit depth. */
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
     * <p>
     * Fix: the unsigned comparison {@code mappingID > numSubVoices} replaces the original signed
     * {@code mappingInfo[key] > numSubVoices}, which mis-classified high-byte IDs.
     */
    private void buildSampleZones (final byte [] inBytes, final int mappingOffset, final int numSubVoices, final List<Integer> subvoiceIDs, final SubVoice [] subVoices, final int channels, final int voiceTune, final IGroup group)
    {
        final byte [] mapping = Arrays.copyOfRange (inBytes, mappingOffset, mappingOffset + 128);
        int prevMappingID = -1;

        for (int key = 0; key < 128; key++)
        {
            final int mappingID = Byte.toUnsignedInt (mapping[key]);

            // Skip: unmapped keys (0), out-of-range IDs, repeated span, or unregistered IDs
            if (mappingID == 0 || mappingID > numSubVoices || mappingID == prevMappingID || !subvoiceIDs.contains (mappingID))
                continue;

            final int svIndex = subvoiceIDs.indexOf (mappingID);
            final SubVoice sv = subVoices[svIndex];
            final int keyHigh = findKeyHigh (mapping, key);

            final DefaultSampleZone zone = this.buildZone (sv, key, keyHigh, voiceTune, false);
            zone.setSampleData (sv.sampleData);

            if (channels == 2 && !sv.interleaved)
            {
                zone.setName (sv.name + "_L");
                zone.setPanning (-1);

                final DefaultSampleZone zoneR = this.buildZone (sv, key, keyHigh, voiceTune, true);
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
     * Constructs a fully configured {@link DefaultSampleZone} for a subvoice.
     *
     * @param sv source subvoice data
     * @param keyLow lowest MIDI key of the zone
     * @param keyHigh highest MIDI key of the zone
     * @param voiceTune global voice tune offset
     * @param useB {@code true} to use channel-B loop points (right-channel zone)
     */
    private DefaultSampleZone buildZone (final SubVoice sv, final int keyLow, final int keyHigh, final int voiceTune, final boolean useB)
    {
        final DefaultSampleZone zone = new DefaultSampleZone ();
        zone.setKeyLow (keyLow);
        zone.setKeyHigh (keyHigh);
        zone.setName (sv.name);
        zone.setGain (sv.amp);

        if (sv.tune == -1)
        {
            zone.setKeyTracking (0);
            zone.setKeyRoot (useB ? 60 : 65);
        }
        else
        {
            final double pitch = this.pitchConvert (sv.tune, voiceTune, sv.sampleRate);
            final int root = (int) Math.round (pitch);
            zone.setKeyTracking (1);
            zone.setKeyRoot (root < 0 ? root + 128 : root);
            zone.setTuning ((pitch - root) / -1.0);
        }

        if (sv.loop)
        {
            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            loop.setStart (useB ? sv.loopStartB : sv.loopStartA);
            loop.setEnd (useB ? sv.loopEndB : sv.loopEndA);
            zone.addLoop (loop);
        }

        applyEnvelope (zone, sv);
        return zone;
    }


    /** Writes amplitude envelope parameters onto a zone, selecting fast or slow segments. */
    private static void applyEnvelope (final DefaultSampleZone zone, final SubVoice sv)
    {
        final IEnvelope env = zone.getAmplitudeEnvelopeModulator ().getSource ();
        env.setAttackTime (sv.attackExtended ? sv.attackSlow : sv.attackFast);
        env.setHoldTime (sv.hold);
        env.setDecayTime (sv.decay);
        env.setSustainLevel (sv.sustain);
        env.setReleaseTime (sv.releaseExtended ? sv.releaseSlow : sv.releaseFast);
    }


    /**
     * Returns the highest key index that shares the same mapping ID as {@code keyLow}.
     *
     * <p>
     * Fix: now correctly returns 127 when the same ID covers all keys to the end of the range.
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
     * Converts a raw unsigned 16-bit value to a signed, normalised double. Values above 32767 are
     * treated as negative (two's-complement wrap).
     */
    private static double toSignedNormalized (final int rawValue, final double divisor)
    {
        double v = rawValue;
        if (v > 32767)
            v = 65536 - v;
        return v / divisor;
    }


    private double pitchConvert (final int inV, final int gV, final int srV)
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


    private double levelConvert (final int inV)
    {
        if (inV == 0)
            return 1;
        double outV = inV;
        if (outV >= 32768)
            outV -= 65536;
        return Math.max (0, 1.01 - Math.pow (10, outV / 256) / 100);
    }


    private double levelConvertDB (final int inV)
    {
        double outV = inV;
        if (outV >= 32768)
            outV -= 65536;
        return outV / 512;
    }


    /** Reads a big-endian unsigned 32-bit integer from {@code data[offset..offset+3]}. */
    private static int readBE32 (final byte [] data, final int offset)
    {
        return Byte.toUnsignedInt (data[offset]) * 16_777_216 + Byte.toUnsignedInt (data[offset + 1]) * 65_536 + Byte.toUnsignedInt (data[offset + 2]) * 256 + Byte.toUnsignedInt (data[offset + 3]);
    }


    /** Reads a big-endian unsigned 16-bit integer from {@code data[offset..offset+1]}. */
    private static int readBE16 (final byte [] data, final int offset)
    {
        return Byte.toUnsignedInt (data[offset]) * 256 + Byte.toUnsignedInt (data[offset + 1]);
    }


    /** Reads a big-endian unsigned 24-bit integer from {@code data[offset..offset+2]}. */
    private static int readBE24 (final byte [] data, final int offset)
    {
        return Byte.toUnsignedInt (data[offset]) * 65_536 + Byte.toUnsignedInt (data[offset + 1]) * 256 + Byte.toUnsignedInt (data[offset + 2]);
    }


    /** Swaps adjacent byte pairs in-place (big-endian ↔ little-endian for 16-bit samples). */
    private static void flipBytes (final byte [] data)
    {
        for (int i = 0; i < data.length; i += 2)
        {
            final byte temp = data[i];
            data[i] = data[i + 1];
            data[i + 1] = temp;
        }
    }


    /** Flips the sign bit of every other byte in-place (8-bit unsigned ↔ signed conversion). */
    private static void flipBits (final byte [] data)
    {
        for (int i = 0; i < data.length; i += 2)
            data[i] = (byte) (data[i] ^ 128);
    }
}