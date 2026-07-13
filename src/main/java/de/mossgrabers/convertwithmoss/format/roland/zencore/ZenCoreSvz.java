// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;


/**
 * Reads and writes the blocks of a Roland FANTOM keyboard-instrument <i>.SVZ</i>: a constant
 * <i>DIFa</i>, one <i>PATa</i> tone per instrument (ZEN-Core tone, 1632 bytes; its oscillator
 * points at the instrument's multi-sample via the Wave-Number L/R fields), one <i>MSPa</i> 128-key
 * map per instrument, a shared <i>USPa</i> sample-parameter pool (64 byte records) and a shared
 * <i>USDa</i> pool of <i>SMPd</i> sample chunks (a 460 byte header followed by interleaved 16-bit
 * little-endian PCM). All framing carries the per-record CRC32 tables verified from device exports.
 * Byte templates for the constant/opaque parts live next to this class as resources. See
 * {@code documentation/design/ZENCORE_FORMAT.md}.
 *
 * @author Jürgen Moßgraber
 */
public final class ZenCoreSvz
{
    /** The size of a USPa sample-parameter record. */
    public static final int      USP_RECORD_SIZE    = 64;
    /** The size of a MSPa multis-ample key-map record. */
    public static final int      MSP_RECORD_SIZE    = 1040;
    /** The size of the fixed SMPd chunk header (PCM follows at this offset). */
    public static final int      SMPD_HEADER_SIZE   = 0x1CC;

    private static final int     NAME_LENGTH        = 16;
    private static final int     PREVIEW_OFFSET     = 0x60;
    private static final int     PREVIEW_VALUES     = 182;

    // USPa record field offsets (device-confirmed).
    private static final int     USP_LOOP_MODE      = 0x14;
    private static final int     USP_LEVEL          = 0x15;
    private static final int     USP_ORIG_KEY       = 0x19;
    private static final int     USP_START          = 0x1C;
    private static final int     USP_LOOP_START     = 0x20;
    private static final int     USP_END            = 0x24;
    private static final int     USP_CHANNELS       = 0x2C;

    // PATa oscillator Wave-Number fields (device-confirmed): the 1-based multi-sample the partial's
    // oscillator plays. A mono tone plays one multi-sample on both sides (Wave R = Wave L on
    // Partial 1). A stereo tone uses TWO partials (the factory way, verified in the FANTOM firmware
    // where stereo sounds are separate "... L"/"... R" waves): Partial 1 plays the left
    // multi-sample
    // panned hard left, Partial 2 plays the right multi-sample panned hard right - each a mono
    // loop,
    // so neither has the loop-wrap click that an interleaved-stereo sample suffers.
    private static final int     PAT_WAVE_L         = 0xE2;                         // Partial 1
                                                                                    // wave number
                                                                                    // (left)
    private static final int     PAT_WAVE_R         = 0xE4;                         // Partial 1
                                                                                    // right wave
                                                                                    // (mono tone)
    private static final int     PAT_PARTIAL_STRIDE = 0x7C;                         // OSC/filter
                                                                                    // block stride
                                                                                    // per partial
    private static final int     PAT_P2_WAVE        = 0xE2 + PAT_PARTIAL_STRIDE;    // Partial 2
                                                                                    // wave number

    // Partial-1 TVF filter + TVA amplitude-envelope offsets - validated against 2048 factory tones.
    // All values are u16 LE, 0-1023. Filter type is a small index times 0x100. The filter block
    // repeats per partial at PAT_PARTIAL_STRIDE; the TVA envelope block repeats at PAT_ENV_STRIDE.

    /** 1=LPF(0x100), 2=BPF(0x200), =HPF(0x300). */
    private static final int     PAT_FILTER_TYPE    = 0xEC;
    private static final int     PAT_CUTOFF         = 0xF0;
    private static final int     PAT_RESONANCE      = 0xF6;
    /** T1,T2,T3,T4 at +0,+2,+4,+6. */
    private static final int     PAT_TVA_TIME       = 0x37A;
    /** L1,L2,L3,L4 at +0,+2,+4,+6. */
    private static final int     PAT_TVA_LEVEL      = 0x382;
    /**
     * Per-partial stride of the TVA amplitude-envelope block: the four partials' TVA envelopes sit
     * back-to-back (P1 @0x37A, P2 @0x38A, ...). Hardware-verified: writing Partial 2's envelope at
     * the wrong stride left it at the template default (a short release), so the right channel cut
     * off well before the left.
     */
    private static final int     PAT_ENV_STRIDE     = 0x10;

    // -------------------------------------------------------------------------------
    // Loaded byte templates (constant or opaque device data).

    /** 32 byte constant record. */
    private static final byte [] DIFA               = load ("difa.bin");
    /** 1632 byte device multi-sample tone (mono: one partial). */
    private static final byte [] PATA_TEMPLATE      = load ("pata_multisample.bin");
    /**
     * 1632 byte device two-partial hard-panned stereo tone. Partial 1 is panned hard left, Partial
     * 2 hard right; each plays its own mono multi-sample (see {@link #PAT_WAVE_L} /
     * {@link #PAT_P2_WAVE}).
     */
    private static final byte [] PATA_STEREO        = load ("pata_stereo.bin");
    /** 460 byte SMPd header. */
    private static final byte [] SMPD_HEADER        = load ("smpd_header.bin");
    /** 64 byte device USPa record. */
    private static final byte [] USPA_TEMPLATE      = load ("uspa.bin");


    /**
     * Constructor. Private due to utility class
     */
    private ZenCoreSvz ()
    {
        // Intentionally empty
    }


    /** One sample of the pool that a written instrument draws from. */
    public static final class SvzSample
    {
        /** 16-bit little-endian PCM: mono (channels 1) or interleaved stereo (channels 2). */
        public byte [] pcm;
        /** Sample rate in Hz. */
        public int     rate;
        /** Number of channels of the stored PCM (1 mono, or 2 interleaved stereo). */
        public int     channels;
        /** Sample name. */
        public String  name;
        /** Original key (root note) 0-127. */
        public int     originalKey;
        /** Level 0-127. */
        public int     level = 127;
        /** Whether the sample loops (forward) or is a one-shot. */
        public boolean hasLoop;
        /** Loop start frame. */
        public int     loopStart;
        /** Loop end / play end frame. */
        public int     end;
    }


    /**
     * One instrument (tone) that maps keys onto samples of the shared pool. A mono instrument has
     * one multi-sample played by a one-partial tone. A stereo instrument stores its left and right
     * channels as separate mono samples in two multi-samples, played by a two-partial tone (Partial
     * 1 = left, panned hard left; Partial 2 = right, panned hard right) - each channel is a mono
     * loop, so it does not suffer the loop-wrap click of an interleaved-stereo sample. See
     * ZenCoreCreator.
     */
    public static final class SvzInstrument
    {
        /** The instrument (tone) name. */
        public String       name;
        /**
         * The left (or only) multi-sample name: the tone name plus a content hash, since the device
         * re-uses an already imported multi-sample of the same name (whose key map indexes other
         * sample slots) instead of loading the new one. Equal names then imply equal content.
         */
        public String       multisampleName;
        /** The right-channel multi-sample name, or null for a mono instrument. */
        public String       multisampleNameRight;
        /** Whether the instrument is stereo (two panned partials over two mono multi-samples). */
        public boolean      stereo;
        /** For each of the 128 keys the 1-based left/only pool sample index, 0 if unassigned. */
        public final int [] keyToSample      = new int [128];
        /** For each of the 128 keys the 1-based right-channel pool sample index (stereo only). */
        public final int [] keyToSampleRight = new int [128];

        // ----------------------------------------------------------------------------------------
        // Optional Partial-1 tone parameters taken from the source; -1 keeps the template default

        /** Filter type: 1=LPF, 2=BPF, 3=HPF (-1 = keep template). */
        public int          filterType       = -1;
        /** Filter cutoff 0-1023. */
        public int          cutoff           = -1;
        /** Filter resonance 0-1023. */
        public int          resonance        = -1;
        /** TVA amplitude-envelope time values 0-1023 - attack. */
        public int          envAttack        = -1;
        /** TVA amplitude-envelope time values 0-1023 - hold. */
        public int          envHold          = -1;
        /** TVA amplitude-envelope time values 0-1023 - decay. */
        public int          envDecay         = -1;
        /** TVA amplitude-envelope time values 0-1023 - release. */
        public int          envRelease       = -1;
        /** TVA amplitude-envelope hold + sustain levels 0-1023 - hold. */
        public int          envHoldLevel     = -1;
        /** TVA amplitude-envelope hold + sustain levels 0-1023 - sustain. */
        public int          envSustain       = -1;
    }


    /**
     * Assemble a FANTOM instrument <i>.svz</i> from a shared sample pool and one or more
     * instruments (a single tone for one instrument, a multi-tone bank for several).
     *
     * @param pool The samples shared by all instruments (1-based referenced from the key-maps)
     * @param instruments One or more instruments (tones)
     * @param header16 The 16-byte SVZ file header for the target device (magic + version + model
     *            tag + flag + reserved); the version and flag bytes differ per device
     * @return The <i>.svz</i> file content
     * @throws IOException Could not assemble the file
     */
    public static byte [] buildSvz (final List<SvzSample> pool, final List<SvzInstrument> instruments, final byte [] header16) throws IOException
    {
        final List<byte []> pataRecords = new ArrayList<> ();
        final List<byte []> mspaRecords = new ArrayList<> ();
        for (final SvzInstrument instrument: instruments)
        {
            // A mono tone has one multi-sample (played on both sides); a stereo tone has a left and
            // a
            // right multi-sample, one per panned partial. Multi-samples are 1-based in file order.
            final int waveLeft = mspaRecords.size () + 1;
            mspaRecords.add (buildKeyMap (instrument.multisampleName, instrument.keyToSample));
            int waveRight = waveLeft;
            if (instrument.stereo)
            {
                waveRight = mspaRecords.size () + 1;
                mspaRecords.add (buildKeyMap (instrument.multisampleNameRight, instrument.keyToSampleRight));
            }
            pataRecords.add (buildTone (instrument, waveLeft, waveRight));
        }

        final List<byte []> uspaRecords = new ArrayList<> ();
        final List<byte []> smpdChunks = new ArrayList<> ();
        for (final SvzSample sample: pool)
        {
            uspaRecords.add (buildSampleParameters (sample));
            smpdChunks.add (buildSampleChunk (sample));
        }

        final List<String> tags = List.of ("DIFa", "PATa", "USPa", "MSPa", "USDa");
        final List<byte []> bodies = new ArrayList<> ();
        bodies.add (ZenCoreContainer.buildBlock (List.of (DIFA.clone ())));
        bodies.add (ZenCoreContainer.buildBlock (pataRecords));
        bodies.add (ZenCoreContainer.buildBlock (uspaRecords));
        bodies.add (ZenCoreContainer.buildBlock (mspaRecords));
        bodies.add (ZenCoreContainer.buildUsda (smpdChunks));
        return ZenCoreContainer.buildSvz (header16, tags, bodies);
    }


    private static byte [] buildTone (final SvzInstrument instrument, final int waveLeft, final int waveRight)
    {
        // A stereo tone uses the two-partial hard-panned template (Partial 1 left, Partial 2
        // right),
        // each partial playing its own mono multi-sample; a mono tone uses the one-partial template
        // and plays its multi-sample on both sides (Wave R = Wave L on Partial 1).
        final byte [] aRecord = (instrument.stereo ? PATA_STEREO : PATA_TEMPLATE).clone ();
        System.arraycopy (ZenCoreUtil.padName (instrument.name, NAME_LENGTH), 0, aRecord, 0, NAME_LENGTH);
        putU16LE (aRecord, PAT_WAVE_L, waveLeft);
        if (instrument.stereo)
            putU16LE (aRecord, PAT_P2_WAVE, waveRight);
        else
            putU16LE (aRecord, PAT_WAVE_R, waveLeft);

        // Carry the source's filter and amplitude envelope over the template defaults, on both
        // partials of a stereo tone so the left and right channels share the same shaping.
        final int partials = instrument.stereo ? 2 : 1;
        for (int p = 0; p < partials; p++)
        {
            final int filterBase = p * PAT_PARTIAL_STRIDE;
            if (instrument.filterType >= 1)
            {
                putU16LE (aRecord, PAT_FILTER_TYPE + filterBase, instrument.filterType * 0x100);
                if (instrument.cutoff >= 0)
                    putU16LE (aRecord, PAT_CUTOFF + filterBase, instrument.cutoff);
                if (instrument.resonance >= 0)
                    putU16LE (aRecord, PAT_RESONANCE + filterBase, instrument.resonance);
            }
            if (instrument.envAttack >= 0)
            {
                final int t = PAT_TVA_TIME + p * PAT_ENV_STRIDE;
                final int l = PAT_TVA_LEVEL + p * PAT_ENV_STRIDE;
                putU16LE (aRecord, t, instrument.envAttack);
                putU16LE (aRecord, t + 2, Math.max (0, instrument.envHold));
                putU16LE (aRecord, t + 4, instrument.envDecay);
                putU16LE (aRecord, t + 6, instrument.envRelease);
                putU16LE (aRecord, l, 1023); // L1 = peak
                putU16LE (aRecord, l + 2, instrument.envHoldLevel >= 0 ? instrument.envHoldLevel : 1023);
                putU16LE (aRecord, l + 4, instrument.envSustain); // L3 = sustain
                putU16LE (aRecord, l + 6, 0); // L4 = silence
            }
        }
        return aRecord;
    }


    private static byte [] buildKeyMap (final String name, final int [] keyToSample)
    {
        final byte [] aRecord = new byte [MSP_RECORD_SIZE];
        System.arraycopy (ZenCoreUtil.padNameZero (name, NAME_LENGTH), 0, aRecord, 0, NAME_LENGTH);
        for (int key = 0; key < 128; key++)
        {
            final int p = 16 + key * 8;
            // 1-based sample index, 0 = unassigned
            putU16LE (aRecord, p, keyToSample[key]);
            aRecord[p + 2] = 0x7F; // per-key level
            aRecord[p + 4] = (byte) 0x80; // constant flag
        }
        return aRecord;
    }


    private static byte [] buildSampleParameters (final SvzSample sample)
    {
        // Start from a real device record so the sample-format fields (0x2E-0x3F) which determine
        // stereo play-back are correct; patch only the per-sample values.
        final byte [] aRecord = USPA_TEMPLATE.clone ();
        System.arraycopy (ZenCoreUtil.padNameZero (sample.name, NAME_LENGTH), 0, aRecord, 0, NAME_LENGTH);
        aRecord[USP_LOOP_MODE] = (byte) (sample.hasLoop ? 0 : 1); // 0 = forward loop, 1 = one-shot
        aRecord[USP_LEVEL] = (byte) (sample.level & 0x7F);
        aRecord[USP_ORIG_KEY] = (byte) (sample.originalKey & 0x7F);
        ZenCoreUtil.writeUnsigned32 (aRecord, USP_START, 0, false);
        ZenCoreUtil.writeUnsigned32 (aRecord, USP_LOOP_START, sample.hasLoop ? sample.loopStart : 0, false);
        ZenCoreUtil.writeUnsigned32 (aRecord, USP_END, sample.end, false);
        // Always 2, matching every device-written file - the device's own sampler writes 2 here
        // even for its mono-stored samples (the SMPd channel count carries the storage layout).
        aRecord[USP_CHANNELS] = 2;
        return aRecord;
    }


    private static byte [] buildSampleChunk (final SvzSample sample)
    {
        final byte [] header = SMPD_HEADER.clone ();
        header[0] = 'S';
        header[1] = 'M';
        header[2] = 'P';
        header[3] = 'd';
        // f04 = 2 * end, the declared play extent - NOT the stored sample count and independent of
        // the channel count (the device's own mono export also writes 2 * end; the firmware's
        // sample-RAM allocator computes frames = f04 >> 1 with a hard-coded divisor). Invariant
        // across every device export and every file written by Roland's own SF2->SVZ converter,
        // while the stored frames run freely both past it (the converter ships natural frames past
        // the loop end, like our guard frames) and short of it (device exports declare up to 144
        // frames beyond their stored data).
        ZenCoreUtil.writeUnsigned32 (header, 4, 2L * sample.end, false);
        header[8] = (byte) sample.channels;
        header[9] = 16;
        ZenCoreUtil.writeUnsigned32 (header, 0x0C, sample.rate, false);
        System.arraycopy (ZenCoreUtil.padNameZero (sample.name, NAME_LENGTH), 0, header, 0x10, NAME_LENGTH);
        writePreview (header, sample.pcm, sample.channels);

        final byte [] chunk = new byte [header.length + sample.pcm.length];
        System.arraycopy (header, 0, chunk, 0, header.length);
        System.arraycopy (sample.pcm, 0, chunk, header.length, sample.pcm.length);
        return chunk;
    }


    /**
     * The 0x60-0x1CB region is a per-sample display thumbnail: a decimated envelope of the left
     * channel over 182 windows the device renders as the sample's waveform overview. The device
     * stores a smoothed (low-pass) envelope; taking the signed peak-extreme of each window instead
     * makes bright or aliased content (a square wave, say) swing between +/- full-scale from one
     * window to the next and draw as noise ("bow-ties"). A per-window average of the absolute
     * amplitude is used instead - a smooth, always-positive envelope that tracks the waveform's
     * shape for any content and never thrashes. Regenerating it per sample keeps each block
     * distinct as the device does.
     *
     * @param header The header data
     * @param pcm The PCM data
     * @param channels The number of channels
     */
    private static void writePreview (final byte [] header, final byte [] pcm, final int channels)
    {
        final int frames = pcm.length / (2 * channels);
        if (frames < PREVIEW_VALUES)
            return;
        for (int i = 0; i < PREVIEW_VALUES; i++)
        {
            final int lo = i * frames / PREVIEW_VALUES;
            final int hi = Math.max (lo + 1, (i + 1) * frames / PREVIEW_VALUES);
            long sum = 0;
            for (int f = lo; f < hi; f++)
            {
                final int idx = f * channels * 2; // left channel, 16-bit little-endian
                final int value = (short) (pcm[idx] & 0xFF | pcm[idx + 1] << 8);
                sum += Math.abs (value);
            }
            putU16LE (header, PREVIEW_OFFSET + i * 2, (int) (sum / (hi - lo)) & 0xFFFF);
        }
    }


    /**
     * Read the user samples of a SVZ container (the shared <i>USPa</i>/<i>USDa</i> pool).
     *
     * @param container The parsed container
     * @return The samples with audio resolved from the <i>SMPd</i> chunks, or an empty list
     */
    public static List<ZenCoreSample> readSamples (final ZenCoreContainer container)
    {
        final List<ZenCoreSample> result = new ArrayList<> ();
        final ZenCoreContainer.Section usp = container.getSection ("USPa");
        final ZenCoreContainer.Section usd = container.getSection ("USDa");
        if (usp == null || usd == null)
            return result;

        final byte [] file = usp.getFile ();
        final int count = usp.getCount ();
        // The USDa 16-entry directory follows the 16 byte section header; each entry's offset is
        // relative to the section start (matches device exports: dataOffset = 16 + 16 * count).
        final int usdSectionStart = usd.getFileOffset ();
        final int dirStart = usdSectionStart + 16;

        for (int i = 0; i < count; i++)
        {
            final int recordOffset = usp.getDataStart () + i * usp.getUnitSize ();
            if (recordOffset + usp.getUnitSize () > file.length)
                break;

            final ZenCoreSample sample = new ZenCoreSample ();
            sample.setName (ZenCoreUtil.readName (file, recordOffset, NAME_LENGTH));
            sample.setLoopMode (file[recordOffset + USP_LOOP_MODE] & 0xFF);
            sample.setLevel (file[recordOffset + USP_LEVEL] & 0xFF);
            sample.setOriginalKey (file[recordOffset + USP_ORIG_KEY] & 0xFF);
            sample.setStartPoint ((int) ZenCoreUtil.readUnsigned32 (file, recordOffset + USP_START, false));
            sample.setLoopStart ((int) ZenCoreUtil.readUnsigned32 (file, recordOffset + USP_LOOP_START, false));
            sample.setEndPoint ((int) ZenCoreUtil.readUnsigned32 (file, recordOffset + USP_END, false));

            final int entryOffset = dirStart + i * 16;
            if (entryOffset + 16 <= file.length)
            {
                final int chunkOffset = (int) ZenCoreUtil.readUnsigned32 (file, entryOffset + 4, false);
                final int chunkSize = (int) ZenCoreUtil.readUnsigned32 (file, entryOffset + 8, false);
                final int chunkStart = usdSectionStart + chunkOffset;
                if (chunkSize > SMPD_HEADER_SIZE && chunkStart + chunkSize <= file.length)
                {
                    // The storage layout comes from the SMPd chunk: the USPa channel count is 2
                    // even
                    // for mono-stored samples (the device's own exports store mono SMPd data).
                    int channels = file[chunkStart + 8] & 0xFF;
                    if (channels < 1 || channels > 2)
                        channels = 2;
                    final int rate = (int) ZenCoreUtil.readUnsigned32 (file, chunkStart + 0x0C, false);
                    final int pcmStart = chunkStart + SMPD_HEADER_SIZE;
                    final int pcmSize = chunkSize - SMPD_HEADER_SIZE;
                    sample.setSampleData (decodePcm (file, pcmStart, pcmSize, channels, rate < 8000 || rate > 192000 ? 48000 : rate));
                }
            }
            result.add (sample);
        }
        return result;
    }


    /**
     * Read the multi-sample key-map of a SVZ container, if present.
     *
     * @param container The parsed container
     * @return The key-map or null
     */
    public static ZenCoreKeyMap readKeyMap (final ZenCoreContainer container)
    {
        final ZenCoreContainer.Section msp = container.getSection ("MSPa");
        if (msp == null || msp.getCount () == 0)
            return null;
        return new ZenCoreKeyMap (msp.getFile (), msp.getDataStart ());
    }


    private static InMemorySampleData decodePcm (final byte [] file, final int offset, final int size, final int channels, final int rate)
    {
        // SMPd PCM is already 16-bit little-endian, so it can be used verbatim.
        final byte [] pcm = new byte [size];
        System.arraycopy (file, offset, pcm, 0, size);
        final int frames = size / (2 * channels);
        return new InMemorySampleData (new DefaultAudioMetadata (channels, rate, 16, frames), pcm);
    }


    private static void putU16LE (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    private static byte [] load (final String resourceName)
    {
        try (final InputStream in = ZenCoreSvz.class.getResourceAsStream (resourceName))
        {
            if (in == null)
                throw new IllegalStateException ("Missing FANTOM template resource: " + resourceName);
            final ByteArrayOutputStream out = new ByteArrayOutputStream ();
            in.transferTo (out);
            return out.toByteArray ();
        }
        catch (final IOException ex)
        {
            throw new IllegalStateException ("Could not read FANTOM template resource: " + resourceName, ex);
        }
    }
}
