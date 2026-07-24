// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.Pair;


/**
 * Reads and writes the blocks of a Roland FANTOM keyboard-instrument <i>.SVZ</i>: a constant
 * <i>DIFa</i>, one <i>PATa</i> tone per instrument (ZEN-Core tone, 1632 bytes; its oscillator
 * points at the instrument's multi-sample via the Wave-Number L/R fields), one <i>MSPa</i> 128-key
 * map per instrument, a shared <i>USPa</i> sample-parameter pool (64 byte records) and a shared
 * <i>USDa</i> pool of <i>SMPd</i> sample chunks (a fixed header - 460 bytes as written by the
 * FANTOM, 158 bytes in an older generation - followed by interleaved 16-bit little-endian PCM). All
 * framing carries the per-record CRC32 tables verified from device exports. Byte templates for the
 * constant/opaque parts live next to this class as resources. See
 * {@code documentation/design/ZENCORE_FORMAT.md}.
 *
 * @author Jürgen Moßgraber
 */
public final class ZenCoreSvz
{
    /** The size of a USPa sample-parameter record. */
    public static final int      USP_RECORD_SIZE       = 64;
    /** The size of a MSPa multis-ample key-map record. */
    public static final int      MSP_RECORD_SIZE       = 1040;
    /** The size of the FANTOM / KY019 SMPd chunk header (PCM follows at this offset). */
    public static final int      SMPD_HEADER_SIZE      = 0x1CC;
    /**
     * The size of the compact SMPd chunk header written by an older sample generation (SVZ file
     * version 02 01, e.g. third-party sample packs). It carries a shorter waveform preview than the
     * FANTOM export and stores the channel count as a u16 at {@link #SMPD_CHANNELS_COMPACT} instead
     * of the byte at {@link #SMPD_CHANNELS_KY019} (whose slot then holds an unrelated value, 0x32).
     */
    private static final int     SMPD_HEADER_COMPACT   = 0x9E;
    /**
     * Channel-count field of a FANTOM / KY019 SMPd header: a byte holding 1 (mono) or 2 (stereo).
     */
    private static final int     SMPD_CHANNELS_KY019   = 0x08;
    /** Channel-count field of a compact SMPd header: a u16 holding 1 (mono) or 2 (stereo). */
    private static final int     SMPD_CHANNELS_COMPACT = 0x0A;
    /**
     * Offset of the embedded RIFF/WAVE file in a SF2-to-SVZ converter SMPd chunk: a 0x20 byte
     * header followed by a complete WAV file rather than raw PCM.
     */
    private static final int     SMPD_EMBEDDED_WAVE    = 0x20;

    private static final int     NAME_LENGTH           = 16;
    private static final int     PREVIEW_OFFSET        = 0x60;
    private static final int     PREVIEW_VALUES        = 182;

    // USPa record field offsets (device-confirmed).
    private static final int     USP_LOOP_MODE         = 0x14;
    private static final int     USP_LEVEL             = 0x15;
    private static final int     USP_ORIG_KEY          = 0x19;
    private static final int     USP_START             = 0x1C;
    private static final int     USP_LOOP_START        = 0x20;
    private static final int     USP_END               = 0x24;
    private static final int     USP_CHANNELS          = 0x2C;

    // PATa oscillator Wave-Number fields (device-confirmed): the 1-based multi-sample the partial's
    // oscillator plays. A mono tone plays one multi-sample on both sides (Wave R = Wave L on
    // Partial 1). A stereo tone uses TWO partials (the factory way, verified in the FANTOM firmware
    // where stereo sounds are separate "... L"/"... R" waves): Partial 1 plays the left
    // multi-sample
    // panned hard left, Partial 2 plays the right multi-sample panned hard right - each a mono
    // loop,
    // so neither has the loop-wrap click that an interleaved-stereo sample suffers.
    private static final int     PAT_WAVE_L            = 0xE2;                         // Partial 1
                                                                                       // wave
                                                                                       // number
                                                                                       // (left)
    private static final int     PAT_WAVE_R            = 0xE4;                         // Partial 1
                                                                                       // right wave
                                                                                       // (mono
                                                                                       // tone)
    private static final int     PAT_PARTIAL_STRIDE    = 0x7C;                         // OSC/filter
                                                                                       // block
                                                                                       // stride
                                                                                       // per
                                                                                       // partial
    private static final int     PAT_P2_WAVE           = 0xE2 + PAT_PARTIAL_STRIDE;    // Partial 2
                                                                                       // wave
                                                                                       // number

    // Partial-1 TVF filter + TVA amplitude-envelope offsets - validated against 2048 factory tones.
    // All values are u16 LE, 0-1023. Filter type is a small index times 0x100. The filter block
    // repeats per partial at PAT_PARTIAL_STRIDE; the TVA envelope block repeats at PAT_ENV_STRIDE.

    /** 1=LPF(0x100), 2=BPF(0x200), =HPF(0x300). */
    private static final int     PAT_FILTER_TYPE       = 0xEC;
    private static final int     PAT_CUTOFF            = 0xF0;
    private static final int     PAT_RESONANCE         = 0xF6;
    /** T1,T2,T3,T4 at +0,+2,+4,+6. */
    private static final int     PAT_TVA_TIME          = 0x37A;
    /** L1,L2,L3,L4 at +0,+2,+4,+6. */
    private static final int     PAT_TVA_LEVEL         = 0x382;
    /**
     * Per-partial stride of the TVA amplitude-envelope block: the four partials' TVA envelopes sit
     * back-to-back (P1 @0x37A, P2 @0x38A, ...). Hardware-verified: writing Partial 2's envelope at
     * the wrong stride left it at the template default (a short release), so the right channel cut
     * off well before the left.
     */
    private static final int     PAT_ENV_STRIDE        = 0x10;

    // Pitch and TVF (filter) modulation envelopes - dedicated per-partial blocks (not the mod
    // matrix), offsets mapped from a ZENOLOGY tone with distinctive per-partial values. Each block
    // is a signed-byte Env Depth, then four u16 times {T1..T4}, then five signed levels {L0..L4};
    // the blocks repeat at PAT_MOD_ENV_STRIDE per partial.
    private static final int     PAT_PITCH_ENV_DEPTH   = 0x2B8;
    private static final int     PAT_PITCH_ENV_TIME    = 0x2BC;
    private static final int     PAT_PITCH_ENV_LEVEL   = 0x2C4;
    private static final int     PAT_FILTER_ENV_DEPTH  = 0x318;
    private static final int     PAT_FILTER_ENV_TIME   = 0x31E;
    private static final int     PAT_FILTER_ENV_LEVEL  = 0x326;
    private static final int     PAT_MOD_ENV_STRIDE    = 0x18;

    // Per-partial keyboard/OSC fields used to build multi-partial (velocity-layered) tones. Mapped
    // by an edit-diff of ZENOLOGY-authored tones with distinctive per-partial velocity ranges and
    // pans (a 4-partial and a 2-partial probe), then cross-checked against the verified templates.
    /** Wave group: 3 = user multi-sample (partial active), 0 = partial off. OSC stride. */
    private static final int     PAT_WAVE_GROUP        = 0xDF;
    /** Partial pan: signed byte, -64 = hard left, 0 = centre, +63 = hard right. OSC stride. */
    private static final int     PAT_PAN               = 0xCE;
    /** Velocity range lower (1-127). Keyboard-table stride. */
    private static final int     PAT_VEL_LOW           = 0xA0;
    /** Velocity range upper (1-127). Keyboard-table stride. */
    private static final int     PAT_VEL_HIGH          = 0xA1;
    /**
     * Partial switch: 1 = the partial sounds, 0 = off. Keyboard-table +0x04. Hardware-proven: a
     * cloned partial with the correct wave group, wave, pan and velocity still stayed silent until
     * this byte was set - it is the per-partial on/off the device's own partial page toggles.
     */
    private static final int     PAT_PARTIAL_SWITCH    = 0xA4;
    /** Per-partial stride of the keyboard/range table (velocity + key range). */
    private static final int     PAT_KBD_STRIDE        = 0x0C;
    /** Wave-group value marking an active user-multi-sample partial. */
    private static final int     WAVE_GROUP_ACTIVE     = 3;

    // -------------------------------------------------------------------------------
    // Loaded byte templates (constant or opaque device data).

    /** 32 byte constant record. */
    private static final byte [] DIFA                  = load ("difa.bin");
    /** 1632 byte device multi-sample tone (mono: one partial). */
    private static final byte [] PATA_TEMPLATE         = load ("pata_multisample.bin");
    /**
     * 1632 byte device two-partial hard-panned stereo tone. Partial 1 is panned hard left, Partial
     * 2 hard right; each plays its own mono multi-sample (see {@link #PAT_WAVE_L} /
     * {@link #PAT_P2_WAVE}).
     */
    private static final byte [] PATA_STEREO           = load ("pata_stereo.bin");
    /** 460 byte SMPd header. */
    private static final byte [] SMPD_HEADER           = load ("smpd_header.bin");
    /** 64 byte device USPa record. */
    private static final byte [] USPA_TEMPLATE         = load ("uspa.bin");
    /**
     * 16 byte <i>.svz</i> file header: magic "SVZa", version 05 04, model tag "KY019", flag 0x24
     * and four reserved bytes. KY019 is the shared ZEN-Core interchange tag of the sample-capable
     * hardware - the FANTOM / FANTOM-0 / FANTOM EX, Juno-X, Jupiter-X/Xm and the MC-707/MC-101 -
     * which all accept the same file. The GAIA-2 (MI085) and the ZENOLOGY plug-in (RC001) are not
     * written as targets: neither loads the user samples a multi-sample needs (the GAIA-2 has no
     * sampler at all - its manual's IMPORT loads only tones - and ZENOLOGY imports only the tone),
     * so a multi-sample would play silent there.
     */
    private static final byte [] SVZ_HEADER            = load ("svz_header.bin");


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
        /** Play start frame - the first frame the engine plays on a note-on. */
        public int     start;
        /** Loop start frame. */
        public int     loopStart;
        /** Loop end / play end frame. */
        public int     end;
    }


    /**
     * One active partial of a velocity-layered tone: its own mono multi-sample (a 128-key map), a
     * pan and a velocity window. A mono velocity layer is one partial (centre pan); a stereo layer
     * is two partials (left/right, hard-panned). Up to four partials fit (the engine's ceiling), so
     * up to four mono or two stereo velocity layers. Only used when {@link SvzInstrument#partials}
     * is set; a single-layer tone uses the mono/stereo template path instead.
     */
    public static final class SvzPartial
    {
        /** For each of the 128 keys the 1-based pool sample index, 0 if unassigned. */
        public final int [] keyToSample = new int [128];
        /** The partial's multi-sample name (tone name plus a content hash). */
        public String       multisampleName;
        /** Pan -64 (hard left) .. 0 (centre) .. +63 (hard right). */
        public int          pan;
        /** Velocity range lower 1-127. */
        public int          velLow      = 1;
        /** Velocity range upper 1-127. */
        public int          velHigh     = 127;
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
        public String           name;
        /**
         * The left (or only) multi-sample name: the tone name plus a content hash, since the device
         * re-uses an already imported multi-sample of the same name (whose key map indexes other
         * sample slots) instead of loading the new one. Equal names then imply equal content.
         */
        public String           multisampleName;
        /** The right-channel multi-sample name, or null for a mono instrument. */
        public String           multisampleNameRight;
        /** Whether the instrument is stereo (two panned partials over two mono multi-samples). */
        public boolean          stereo;
        /** For each of the 128 keys the 1-based left/only pool sample index, 0 if unassigned. */
        public final int []     keyToSample      = new int [128];
        /** For each of the 128 keys the 1-based right-channel pool sample index (stereo only). */
        public final int []     keyToSampleRight = new int [128];
        /**
         * When set (two or more entries), the tone is built as a velocity-layered multi-partial
         * tone from these partials instead of the single mono/stereo multi-sample above; each
         * partial has its own multi-sample, pan and velocity window.
         */
        public List<SvzPartial> partials;

        // ----------------------------------------------------------------------------------------
        // Optional Partial-1 tone parameters taken from the source; -1 keeps the template default

        /** Filter type: 1=LPF, 2=BPF, 3=HPF (-1 = keep template). */
        public int              filterType       = -1;
        /** Filter cutoff 0-1023. */
        public int              cutoff           = -1;
        /** Filter resonance 0-1023. */
        public int              resonance        = -1;
        /** TVA amplitude-envelope time values 0-1023 - attack. */
        public int              envAttack        = -1;
        /** TVA amplitude-envelope time values 0-1023 - hold. */
        public int              envHold          = -1;
        /** TVA amplitude-envelope time values 0-1023 - decay. */
        public int              envDecay         = -1;
        /** TVA amplitude-envelope time values 0-1023 - release. */
        public int              envRelease       = -1;
        /** TVA amplitude-envelope hold + sustain levels 0-1023 - hold. */
        public int              envHoldLevel     = -1;
        /** TVA amplitude-envelope hold + sustain levels 0-1023 - sustain. */
        public int              envSustain       = -1;

        // Optional pitch and TVF (filter) modulation envelopes. depth is a signed amount (pitch
        // -63..+63, filter -100..+100); a null times array keeps the template default. times =
        // {T1..T4} (0-1023), levels = {L0..L4} (signed, the modulation shape scaled by depth).
        /** Pitch-envelope depth (signed), or 0 with null times to keep the default. */
        public int              pitchEnvDepth;
        /** Pitch-envelope times {T1..T4} 0-1023, or null. */
        public int []           pitchEnvTimes;
        /** Pitch-envelope levels {L0..L4} signed. */
        public int []           pitchEnvLevels;
        /** TVF (filter) envelope depth (signed), or 0 with null times to keep the default. */
        public int              filterEnvDepth;
        /** TVF (filter) envelope times {T1..T4} 0-1023, or null. */
        public int []           filterEnvTimes;
        /** TVF (filter) envelope levels {L0..L4} signed. */
        public int []           filterEnvLevels;
    }


    /**
     * Assemble a FANTOM instrument <i>.svz</i> from a shared sample pool and one or more
     * instruments (a single tone for one instrument, a multi-tone bank for several).
     *
     * @param pool The samples shared by all instruments (1-based referenced from the key-maps)
     * @param instruments One or more instruments (tones)
     * @return The <i>.svz</i> file content
     * @throws IOException Could not assemble the file
     */
    public static byte [] buildSvz (final List<SvzSample> pool, final List<SvzInstrument> instruments) throws IOException
    {
        final List<byte []> pataRecords = new ArrayList<> ();
        final List<byte []> mspaRecords = new ArrayList<> ();
        for (final SvzInstrument instrument: instruments)
        {
            // A velocity-layered tone has one multi-sample per active partial; a mono tone has one
            // multi-sample (played on both sides); a stereo tone has a left and a right
            // multi-sample,
            // one per panned partial. Multi-samples are 1-based in file order.
            if (instrument.partials != null && !instrument.partials.isEmpty ())
            {
                final int [] waveNumbers = new int [instrument.partials.size ()];
                for (int i = 0; i < instrument.partials.size (); i++)
                {
                    waveNumbers[i] = mspaRecords.size () + 1;
                    mspaRecords.add (buildKeyMap (instrument.partials.get (i).multisampleName, instrument.partials.get (i).keyToSample));
                }
                pataRecords.add (buildToneMulti (instrument, waveNumbers));
                continue;
            }
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
        return ZenCoreContainer.buildSvz (SVZ_HEADER, tags, bodies);
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
        final int partialCount = instrument.stereo ? 2 : 1;
        for (int p = 0; p < partialCount; p++)
            writePartialShaping (aRecord, p, instrument);
        // Switch off the template's unused partials (wave group 0 + Wave Number 0) so they stay
        // silent: the mono template leaves all four enabled and the stereo template leaves Partial
        // 3
        // switched on, and a group-0 partial that keeps a non-zero wave number sounds a ROM wave
        // (hardware-verified). Wave 0 = silent.
        for (int p = partialCount; p < 4; p++)
        {
            aRecord[PAT_WAVE_GROUP + p * PAT_PARTIAL_STRIDE] = 0;
            putU16LE (aRecord, PAT_WAVE_L + p * PAT_PARTIAL_STRIDE, 0);
            putU16LE (aRecord, PAT_WAVE_R + p * PAT_PARTIAL_STRIDE, 0);
        }
        // Partial-switch display byte: each partial's +0x04 gates the NEXT partial's on/off display
        // (see buildToneMulti), so the disabled partials read OFF on the device's partial page.
        for (int p = 0; p < 4; p++)
        {
            int value;
            if (p == 3)
                value = 127;
            else if (p + 1 < partialCount)
                value = 1;
            else if (p < partialCount)
                value = 0;
            else
                value = 127;
            aRecord[PAT_PARTIAL_SWITCH + p * PAT_KBD_STRIDE] = (byte) value;
        }
        return aRecord;
    }


    /**
     * Build a velocity-layered tone: two to four active partials, each playing its own mono
     * multi-sample within its own velocity window. Composed from the hardware-verified two-partial
     * stereo template (two active partials, two off); Partials 3 and 4 are cloned byte-for-byte
     * from the verified active Partials 1 and 2 before their wave, pan and velocity are set, so
     * every active partial's opaque oscillator configuration is a known-good copy. Unused partials
     * are left off (wave group 0). See {@link SvzPartial} and ZenCoreCreator.
     *
     * @param instrument The instrument whose {@link SvzInstrument#partials} drive the tone
     * @param waveNumbers The 1-based multi-sample number per active partial (file order)
     * @return The 1632 byte PATa tone record
     */
    private static byte [] buildToneMulti (final SvzInstrument instrument, final int [] waveNumbers)
    {
        final int count = waveNumbers.length;
        final byte [] aRecord = PATA_STEREO.clone ();
        System.arraycopy (ZenCoreUtil.padName (instrument.name, NAME_LENGTH), 0, aRecord, 0, NAME_LENGTH);
        // Make Partials 3 and 4 active by cloning the verified active Partials 1 and 2.
        if (count >= 3)
            clonePartial (aRecord, 0, 2);
        if (count >= 4)
            clonePartial (aRecord, 1, 3);

        for (int p = 0; p < 4; p++)
        {
            final int oscBase = p * PAT_PARTIAL_STRIDE;
            final int kbdBase = p * PAT_KBD_STRIDE;
            // Partial-switch byte (keyboard-table +0x04): each partial's byte gates the DISPLAY of
            // the NEXT partial - 1 = the next partial shows ON, 0 = it shows OFF; Partial 4 has no
            // next partial and reads 127. Hardware-verified against device exports at 1, 2 and 4
            // active partials: leaving the last active partial's byte at 1 wrongly displayed the
            // following (silent) partial as ON.
            int value;
            if (p == 3)
                value = 127;
            else if (p + 1 < count)
                value = 1;
            else if (p < count)
                value = 0;
            else
                value = 127;
            aRecord[PAT_PARTIAL_SWITCH + kbdBase] = (byte) value;
            if (p < count)
            {
                final SvzPartial partial = instrument.partials.get (p);
                aRecord[PAT_WAVE_GROUP + oscBase] = (byte) WAVE_GROUP_ACTIVE;
                putU16LE (aRecord, PAT_WAVE_L + oscBase, waveNumbers[p]);
                putU16LE (aRecord, PAT_WAVE_R + oscBase, 0); // each partial is a mono, panned voice
                aRecord[PAT_PAN + oscBase] = (byte) Math.clamp (partial.pan, -64, 63);
                aRecord[PAT_VEL_LOW + kbdBase] = (byte) Math.clamp (partial.velLow, 1, 127);
                aRecord[PAT_VEL_HIGH + kbdBase] = (byte) Math.clamp (partial.velHigh, 1, 127);
                writePartialShaping (aRecord, p, instrument);
            }
            else
            {
                // Switch the partial off: wave group 0 and - critically - Wave Number 0. A disabled
                // partial that keeps a non-zero wave number sounds a ROM wave (hardware-verified:
                // leftover Wave 1 played audibly under the real layers); wave 0 = no wave = silent.
                aRecord[PAT_WAVE_GROUP + oscBase] = 0;
                putU16LE (aRecord, PAT_WAVE_L + oscBase, 0);
                putU16LE (aRecord, PAT_WAVE_R + oscBase, 0);
                aRecord[PAT_VEL_LOW + kbdBase] = 1;
                aRecord[PAT_VEL_HIGH + kbdBase] = (byte) 127;
            }
        }
        return aRecord;
    }


    /**
     * Clone a partial's per-partial parameter blocks (oscillator/filter, pitch and filter
     * modulation envelopes, TVA envelope) from one partial slot to another, so a template's
     * verified active partial can be duplicated into an unused slot. Each block is copied within
     * its own table and never past the following table.
     *
     * @param toneRecord The tone record
     * @param src The source partial index (0-3)
     * @param dst The destination partial index (0-3)
     */
    private static void clonePartial (final byte [] toneRecord, final int src, final int dst)
    {
        // The oscillator/filter block runs up to the pitch-envelope table, so the fourth partial's
        // block is naturally shorter - never copy across that boundary.
        copyBlock (toneRecord, PAT_PAN + src * PAT_PARTIAL_STRIDE, PAT_PAN + dst * PAT_PARTIAL_STRIDE, PAT_PARTIAL_STRIDE, PAT_PITCH_ENV_DEPTH);
        copyBlock (toneRecord, PAT_PITCH_ENV_DEPTH + src * PAT_MOD_ENV_STRIDE, PAT_PITCH_ENV_DEPTH + dst * PAT_MOD_ENV_STRIDE, PAT_MOD_ENV_STRIDE, toneRecord.length);
        copyBlock (toneRecord, PAT_FILTER_ENV_DEPTH + src * PAT_MOD_ENV_STRIDE, PAT_FILTER_ENV_DEPTH + dst * PAT_MOD_ENV_STRIDE, PAT_MOD_ENV_STRIDE, toneRecord.length);
        copyBlock (toneRecord, PAT_TVA_TIME + src * PAT_ENV_STRIDE, PAT_TVA_TIME + dst * PAT_ENV_STRIDE, PAT_ENV_STRIDE, toneRecord.length);
    }


    private static void copyBlock (final byte [] toneRecord, final int from, final int to, final int length, final int limit)
    {
        final int n = Math.min (length, limit - to);
        if (n > 0)
            System.arraycopy (toneRecord, from, toneRecord, to, n);
    }


    /**
     * Write the source's filter and amplitude/pitch/filter modulation envelopes into one partial,
     * over the template defaults. Leaves any parameter the source does not specify at the template
     * default. Shared by the mono/stereo and the velocity-layered tone builders so a partial is
     * shaped identically in both.
     *
     * @param aRecord The tone record
     * @param p The partial index (0-3)
     * @param instrument The instrument carrying the (optional) filter and envelope parameters
     */
    private static void writePartialShaping (final byte [] aRecord, final int p, final SvzInstrument instrument)
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
        final int modBase = p * PAT_MOD_ENV_STRIDE;
        writeModEnv (aRecord, PAT_PITCH_ENV_DEPTH + modBase, PAT_PITCH_ENV_TIME + modBase, PAT_PITCH_ENV_LEVEL + modBase, instrument.pitchEnvDepth, instrument.pitchEnvTimes, instrument.pitchEnvLevels);
        writeModEnv (aRecord, PAT_FILTER_ENV_DEPTH + modBase, PAT_FILTER_ENV_TIME + modBase, PAT_FILTER_ENV_LEVEL + modBase, instrument.filterEnvDepth, instrument.filterEnvTimes, instrument.filterEnvLevels);
    }


    /**
     * Write one pitch/TVF modulation envelope block (signed-byte depth, four u16 times, five signed
     * levels) into a partial, or leave the template default if the envelope is absent.
     *
     * @param toneRecord The tone record
     * @param depthOffset The signed-byte Env Depth offset
     * @param timeOffset The four-time block offset
     * @param levelOffset The five-level block offset
     * @param depth The signed depth amount
     * @param times The four times {T1..T4} 0-1023, or null to keep the template default
     * @param levels The five levels {L0..L4} (signed)
     */
    private static void writeModEnv (final byte [] toneRecord, final int depthOffset, final int timeOffset, final int levelOffset, final int depth, final int [] times, final int [] levels)
    {
        if (times == null)
            return;
        toneRecord[depthOffset] = (byte) Math.clamp (depth, -128, 127);
        for (int i = 0; i < 4; i++)
            putU16LE (toneRecord, timeOffset + i * 2, times[i]);
        for (int i = 0; i < 5; i++)
            putU16LE (toneRecord, levelOffset + i * 2, levels[i] & 0xFFFF);
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
        // The audio is always stored from frame 0, so the zone's play start is an index into the
        // stored sample just like the loop points
        ZenCoreUtil.writeUnsigned32 (aRecord, USP_START, sample.start, false);
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
                if (chunkStart >= 0 && chunkSize > SMPD_HEADER_COMPACT && chunkStart + chunkSize <= file.length)
                {
                    // A third SMPd variant - the output of Roland's SF2-to-SVZ converter, used by
                    // some commercial packs - embeds a complete RIFF/WAVE file after a 0x20 byte
                    // header instead of raw PCM (its byte at 0x08 is an unrelated 0x32, so the
                    // raw-PCM channel detection cannot be used); it is read with the standard WAV
                    // reader. Otherwise the header is one of the two raw-PCM variants (see
                    // resolveSmpdLayout); an unrecognized chunk leaves the sample without audio.
                    if (isEmbeddedWaveChunk (file, chunkStart))
                    {
                        try
                        {
                            sample.setSampleData (readEmbeddedWave (file, chunkStart, chunkSize));
                        }
                        catch (final IOException _)
                        {
                            // A malformed embedded WAV leaves the sample without audio.
                        }
                    }
                    else
                    {
                        final Optional<Pair<Integer, Integer>> layout = resolveSmpdLayout (file, chunkStart, chunkSize);
                        if (layout.isPresent ())
                        {
                            final Pair<Integer, Integer> pair = layout.get ();
                            final int channels = pair.getKey ().intValue ();
                            final int headerSize = pair.getValue ().intValue ();
                            final int rate = (int) ZenCoreUtil.readUnsigned32 (file, chunkStart + 0x0C, false);
                            final int pcmStart = chunkStart + headerSize;
                            final int pcmSize = chunkSize - headerSize;
                            sample.setSampleData (decodePcm (file, pcmStart, pcmSize, channels, rate < 8000 || rate > 192000 ? 48000 : rate));
                        }
                    }
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
    public static Optional<ZenCoreKeyMap> readKeyMap (final ZenCoreContainer container)
    {
        final ZenCoreContainer.Section msp = container.getSection ("MSPa");
        if (msp == null || msp.getCount () == 0)
            return Optional.empty ();
        return Optional.of (new ZenCoreKeyMap (msp.getFile (), msp.getDataStart ()));
    }


    /**
     * Read the tone shaping of the first <i>PATa</i> tone (its Partial 1): the filter, the TVA
     * amplitude envelope and the pitch/TVF modulation envelopes - the read-side counterpart of
     * writePartialShaping. All values are raw device values (times 0-1023, levels 0-1023, signed
     * envelope depths); the detector converts them with the calibrated law.
     *
     * @param container The SVZ container
     * @return The shaping in an {@link SvzInstrument}, or null if the container holds no tone
     */
    public static Optional<SvzInstrument> readTone (final ZenCoreContainer container)
    {
        final ZenCoreContainer.Section pat = container.getSection ("PATa");
        if (pat == null || pat.getCount () == 0)
            return Optional.empty ();
        final byte [] file = pat.getFile ();
        final int r = pat.getDataStart ();
        final SvzInstrument tone = new SvzInstrument ();
        tone.filterType = ZenCoreUtil.readUnsigned16 (file, r + PAT_FILTER_TYPE, false) / 0x100;
        tone.cutoff = ZenCoreUtil.readUnsigned16 (file, r + PAT_CUTOFF, false);
        tone.resonance = ZenCoreUtil.readUnsigned16 (file, r + PAT_RESONANCE, false);
        tone.envAttack = ZenCoreUtil.readUnsigned16 (file, r + PAT_TVA_TIME, false);
        tone.envHold = ZenCoreUtil.readUnsigned16 (file, r + PAT_TVA_TIME + 2, false);
        tone.envDecay = ZenCoreUtil.readUnsigned16 (file, r + PAT_TVA_TIME + 4, false);
        tone.envRelease = ZenCoreUtil.readUnsigned16 (file, r + PAT_TVA_TIME + 6, false);
        tone.envHoldLevel = ZenCoreUtil.readUnsigned16 (file, r + PAT_TVA_LEVEL + 2, false);
        tone.envSustain = ZenCoreUtil.readUnsigned16 (file, r + PAT_TVA_LEVEL + 4, false);
        tone.pitchEnvDepth = file[r + PAT_PITCH_ENV_DEPTH];
        tone.pitchEnvTimes = readEnvBlock (file, r + PAT_PITCH_ENV_TIME, 4);
        tone.pitchEnvLevels = readEnvBlock (file, r + PAT_PITCH_ENV_LEVEL, 5);
        tone.filterEnvDepth = file[r + PAT_FILTER_ENV_DEPTH];
        tone.filterEnvTimes = readEnvBlock (file, r + PAT_FILTER_ENV_TIME, 4);
        tone.filterEnvLevels = readEnvBlock (file, r + PAT_FILTER_ENV_LEVEL, 5);
        return Optional.of (tone);
    }


    private static int [] readEnvBlock (final byte [] file, final int offset, final int count)
    {
        final int [] values = new int [count];
        for (int i = 0; i < count; i++)
            values[i] = ZenCoreUtil.readUnsigned16 (file, offset + i * 2, false);
        return values;
    }


    /**
     * Whether a SMPd chunk is the SF2-to-SVZ converter variant, which embeds a complete RIFF/WAVE
     * file after a {@link #SMPD_EMBEDDED_WAVE} byte header instead of raw PCM. Detected by the
     * RIFF/WAVE signature at that offset.
     *
     * @param file The file content
     * @param chunkStart The absolute offset of the SMPd chunk
     * @return True if the chunk embeds a RIFF/WAVE file
     */
    private static boolean isEmbeddedWaveChunk (final byte [] file, final int chunkStart)
    {
        final int riff = chunkStart + SMPD_EMBEDDED_WAVE;
        return riff + 12 <= file.length && file[riff] == 'R' && file[riff + 1] == 'I' && file[riff + 2] == 'F' && file[riff + 3] == 'F' && file[riff + 8] == 'W' && file[riff + 9] == 'A' && file[riff + 10] == 'V' && file[riff + 11] == 'E';
    }


    /**
     * Read the embedded RIFF/WAVE file of a SF2-to-SVZ converter SMPd chunk (the audio follows the
     * {@link #SMPD_EMBEDDED_WAVE} byte header) with the standard WAV reader.
     *
     * @param file The file content
     * @param chunkStart The absolute offset of the SMPd chunk
     * @param chunkSize The chunk size from the USDa directory
     * @return The sample data
     * @throws IOException The embedded WAV could not be read
     */
    private static ISampleData readEmbeddedWave (final byte [] file, final int chunkStart, final int chunkSize) throws IOException
    {
        final int waveStart = chunkStart + SMPD_EMBEDDED_WAVE;
        final byte [] wave = new byte [chunkStart + chunkSize - waveStart];
        System.arraycopy (file, waveStart, wave, 0, wave.length);
        return new WavFileSampleData (new ByteArrayInputStream (wave));
    }


    /**
     * Resolve a <i>SMPd</i> chunk's PCM layout. Two header variants exist. The FANTOM / KY019
     * export uses a {@link #SMPD_HEADER_SIZE} byte header with the channel count in the byte at
     * {@link #SMPD_CHANNELS_KY019}. An older generation (SVZ file version 02 01, e.g. third-party
     * sample packs) uses a {@link #SMPD_HEADER_COMPACT} byte header with the channel count as a u16
     * at {@link #SMPD_CHANNELS_COMPACT} - there the byte at {@link #SMPD_CHANNELS_KY019} holds an
     * unrelated value (0x32), which is why reading the channel count from it alone fails on those
     * files. The matching variant is the one whose PCM region past the header is a whole number of
     * frames: the two header sizes differ by 302 (2 mod 4), so a stereo chunk's byte count is a
     * multiple of the 4 byte stereo frame for exactly one of them.
     *
     * @param file The file content
     * @param chunkStart The absolute offset of the SMPd chunk
     * @param chunkSize The chunk size from the USDa directory
     * @return {channels, headerSize}, or null if neither variant fits
     */
    private static Optional<Pair<Integer, Integer>> resolveSmpdLayout (final byte [] file, final int chunkStart, final int chunkSize)
    {
        final int ky019Channels = file[chunkStart + SMPD_CHANNELS_KY019] & 0xFF;
        if (smpdFits (chunkSize, SMPD_HEADER_SIZE, ky019Channels))
            return Optional.of (new Pair<> (Integer.valueOf (ky019Channels), Integer.valueOf (SMPD_HEADER_SIZE)));

        final int compactChannels = ZenCoreUtil.readUnsigned16 (file, chunkStart + SMPD_CHANNELS_COMPACT, false);
        if (smpdFits (chunkSize, SMPD_HEADER_COMPACT, compactChannels))
            return Optional.of (new Pair<> (Integer.valueOf (compactChannels), Integer.valueOf (SMPD_HEADER_COMPACT)));

        return Optional.empty ();
    }


    /**
     * Whether a SMPd header size and channel count are consistent with the chunk: a plausible
     * channel count (1 or 2) whose PCM region past the header is a whole number of frames. The
     * declared play length is deliberately not used - device exports may declare a handful of
     * frames more than they store - so only the frame alignment, together with the channel-count
     * field, selects the layout.
     *
     * @param chunkSize The chunk size
     * @param headerSize The candidate header size
     * @param channels The candidate channel count
     * @return True if the combination fits
     */
    private static boolean smpdFits (final int chunkSize, final int headerSize, final int channels)
    {
        if (channels < 1 || channels > 2)
            return false;
        final int pcmSize = chunkSize - headerSize;
        return pcmSize > 0 && pcmSize % (channels * 2) == 0;
    }


    private static InMemorySampleData decodePcm (final byte [] file, final int offset, final int size, final int channels, final int rate)
    {
        // SMPd PCM is already 16-bit little-endian, so it can be used verbatim. Copy only whole
        // frames: a chunk whose stored byte count is not an exact multiple of the frame size would
        // otherwise hand InMemorySampleData a buffer that does not fit a WAV of that frame count.
        final int frameBytes = 2 * channels;
        final int frames = size / frameBytes;
        final byte [] pcm = new byte [frames * frameBytes];
        System.arraycopy (file, offset, pcm, 0, pcm.length);
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
