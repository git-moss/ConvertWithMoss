// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.directwave;

import java.util.HexFormat;


/**
 * Constants of the DirectWave DWP format. See the file DIRECTWAVE_DWP_FORMAT.md in the design
 * documentation folder for the full description. All template byte blocks below are taken verbatim
 * from a real DirectWave export of FL Studio Desktop.
 *
 * @author Jürgen Moßgraber
 */
public class DirectWaveTag
{
    /** The magic bytes at the start of a DWP file. */
    public static final byte []  MAGIC                  = "DwPr".getBytes ();

    /** The length of the fixed header before the block stream starts (versions 0x25 and 0x26). */
    public static final int      PREAMBLE_SIZE          = 0x5A;
    /** The smallest preamble length to look for when reading. */
    public static final int      PREAMBLE_MIN_SIZE      = 0x50;
    /** The largest preamble length to look for when reading (0x5E for the version 0x24). */
    public static final int      PREAMBLE_MAX_SIZE      = 0x70;

    /**
     * The offset of the size field in the preamble. It contains the file size minus 48 (= the size
     * of everything following the first 48 bytes).
     */
    public static final int      PREAMBLE_SIZE_OFFSET   = 0x28;

    /** The number of bytes the value of the preamble size field is smaller than the file size. */
    public static final int      PREAMBLE_SIZE_DELTA    = 48;

    /** The tag of the instrument name block. */
    public static final int      TAG_INSTRUMENT_NAME    = 0x0066;
    /** The tag of the block which contains the path of the DWP file itself. */
    public static final int      TAG_INSTRUMENT_PATH    = 0x0067;
    /** The tags of the zeroed shadow blocks of the name and path (lengths track the originals). */
    public static final int      TAG_SHADOW_NAME        = 0x0068;
    /** The tag of the zeroed shadow block of the path. */
    public static final int      TAG_SHADOW_PATH        = 0x0069;
    /** The first of the two 17 byte metadata blocks. */
    public static final int      TAG_METADATA_1         = 0x006A;
    /** The second of the two 17 byte metadata blocks. */
    public static final int      TAG_METADATA_2         = 0x006B;
    /** The tag of the two 20 byte metadata blocks. */
    public static final int      TAG_METADATA_3         = 0x006C;
    /** The tag of the four 4 byte metadata blocks. */
    public static final int      TAG_METADATA_4         = 0x006D;
    /** The tag of the 99 parameter slot blocks. */
    public static final int      TAG_PARAMETER_SLOT     = 0x006E;
    /** The tag of a sample container block which contains the blocks of one sample zone. */
    public static final int      TAG_SAMPLE_CONTAINER   = 0x0003;
    /** The tag of the top level terminator block. */
    public static final int      TAG_TERMINATOR         = 0x0002;

    /** The tag of the zone mapping block inside of a sample container. */
    public static final int      TAG_ZONE_MAPPING       = 0x01F4;
    /** The tag of the sample name block inside of a sample container. */
    public static final int      TAG_SAMPLE_NAME        = 0x01F5;
    /** The tag of the sample path block inside of a sample container. */
    public static final int      TAG_SAMPLE_PATH        = 0x01F6;
    /** The tag of the audio format block inside of a sample container. */
    public static final int      TAG_AUDIO_FORMAT       = 0x01F7;
    /** The tag of the terminator block of a sample container. */
    public static final int      TAG_SAMPLE_TERMINATOR  = 0x0004;

    /**
     * The tag of the block which contains the embedded audio of a monolithic program. It is the
     * last block of a sample container before its terminator. The payload is the length of the
     * audio data as a 32-bit integer, 4 unused bytes and then the audio as a FLAC stream.
     */
    public static final int      TAG_EMBEDDED_AUDIO     = 0x0206;
    /** The size of the header before the FLAC stream in the embedded audio block. */
    public static final int      EMBEDDED_AUDIO_OFFSET  = 8;
    /**
     * DirectWave stores embedded audio in blocks of this many frames: the frame count of every
     * sample of every factory program is a multiple of it and the encoded audio is one block
     * longer than the frame count. A sample which does not fill its last block crashes the plug-in
     * when the program is loaded, therefore written audio is padded with silence.
     */
    public static final int      EMBEDDED_AUDIO_BLOCK   = 512;
    /** The magic bytes of a FLAC stream. */
    public static final byte []  FLAC_MAGIC             = "fLaC".getBytes ();

    /** The tag of the 8 byte parameter block inside of a sample container. */
    public static final int      TAG_BLOCK_01F8         = 0x01F8;
    /** The tag of the 14 byte zeroed block inside of a sample container. */
    public static final int      TAG_BLOCK_01F9         = 0x01F9;
    /** The tag of the 48 byte parameter block inside of a sample container. */
    public static final int      TAG_BLOCK_01FA         = 0x01FA;
    /** The tag of the two filter blocks (filter 1 and filter 2) inside of a sample container. */
    public static final int      TAG_FILTER             = 0x01FB;

    /** The offset of the filter type (a 32-bit integer!) in a filter block. */
    public static final int      FILTER_TYPE            = 0;
    /** The offset of the filter cutoff (a float knob position) in a filter block. */
    public static final int      FILTER_CUTOFF          = 4;
    /** The offset of the filter resonance (a float knob position) in a filter block. */
    public static final int      FILTER_RESONANCE       = 8;

    /** The filter type value of a disabled filter. */
    public static final int      FILTER_TYPE_OFF        = 0;
    /** The filter type value of a low-pass filter. */
    public static final int      FILTER_TYPE_LOW_PASS   = 1;
    /** The filter type value of a high-pass filter. */
    public static final int      FILTER_TYPE_HIGH_PASS  = 2;
    /** The filter type value of a band-pass filter. */
    public static final int      FILTER_TYPE_BAND_PASS  = 3;
    /** The filter type value of a band-rejection (notch) filter. */
    public static final int      FILTER_TYPE_NOTCH      = 4;

    /**
     * The lowest frequency of the filter cutoff knob. The knob is mapped exponentially from this
     * frequency up to {@link de.mossgrabers.convertwithmoss.core.model.IFilter#MAX_FREQUENCY}. The
     * value was measured by listening: a 220 Hz saw wave through a low-pass filter starts to pass
     * its fundamental at a knob position of 0.26, which puts the cutoff at 220 Hz there, and the
     * wave is fully open at 0.66. Both are reproduced by this range within a few percent.
     */
    public static final double   FILTER_MIN_FREQUENCY   = 45.0;
    /** The tag of the 2 byte zeroed block inside of a sample container. */
    public static final int      TAG_BLOCK_01FC         = 0x01FC;
    /** The tag of the amplitude envelope block (4 floats: attack, decay, sustain, release). */
    public static final int      TAG_AMP_ENVELOPE       = 0x01FD;
    /** The first of the four 9 byte zeroed blocks inside of a sample container. */
    public static final int      TAG_BLOCK_01FE         = 0x01FE;
    /** The second of the four 9 byte zeroed blocks inside of a sample container. */
    public static final int      TAG_BLOCK_01FF         = 0x01FF;
    /** The third of the four 9 byte zeroed blocks inside of a sample container. */
    public static final int      TAG_BLOCK_0200         = 0x0200;
    /** The fourth of the four 9 byte zeroed blocks inside of a sample container. */
    public static final int      TAG_BLOCK_0201         = 0x0201;
    /** The tag of the two 16 byte zeroed blocks inside of a sample container. */
    public static final int      TAG_BLOCK_0202         = 0x0202;
    /** The tag of the two 20 byte zeroed blocks inside of a sample container. */
    public static final int      TAG_BLOCK_0203         = 0x0203;
    /** The tag of the sixteen 8 byte blocks inside of a sample container. */
    public static final int      TAG_BLOCK_0204         = 0x0204;

    /** The number of parameter slot blocks (version 0x25; their ids are 0-based). */
    public static final int      NUM_PARAMETER_SLOTS    = 100;

    /** The offset of the root key in the zone mapping block. */
    public static final int      MAPPING_ROOT_KEY       = 0;
    /** The offset of the low key in the zone mapping block. */
    public static final int      MAPPING_LOW_KEY        = 1;
    /** The offset of the high key in the zone mapping block. */
    public static final int      MAPPING_HIGH_KEY       = 2;
    /** The offset of the low velocity in the zone mapping block. */
    public static final int      MAPPING_LOW_VELOCITY   = 3;
    /** The offset of the high velocity in the zone mapping block. */
    public static final int      MAPPING_HIGH_VELOCITY  = 4;
    /** The offset of the zone gain (a float, linear amplitude, 1.0 = 0 dB) in the mapping block. */
    public static final int      MAPPING_GAIN           = 9;
    /** The offset of the zone panning (a float, 0.5 = center) in the zone mapping block. */
    public static final int      MAPPING_PANNING        = 13;

    /** The offset of the frame count in the audio format block. */
    public static final int      FORMAT_FRAME_COUNT     = 0;
    /** The offset of the channel count in the audio format block. */
    public static final int      FORMAT_CHANNELS        = 8;
    /** The offset of the bytes-per-frame field in the audio format block. */
    public static final int      FORMAT_BYTES_PER_FRAME = 12;
    /** The offset of the sample rate (stored as a 32-bit float!) in the audio format block. */
    public static final int      FORMAT_SAMPLE_RATE     = 16;
    /** The offset of the loop mode (0: off, 2: looped) in the audio format block. */
    public static final int      FORMAT_LOOP_MODE       = 20;
    /** The offset of the loop start (in frames) in the audio format block. */
    public static final int      FORMAT_LOOP_START      = 24;
    /** The offset of the loop end (in frames) in the audio format block. */
    public static final int      FORMAT_LOOP_END        = 28;
    /** The loop mode value of a sample which is not looped. */
    public static final int      LOOP_MODE_DISABLED     = 0;
    /** The loop mode value which plays the sample to its end and ignores a note-off. */
    public static final int      LOOP_MODE_ONE_SHOT     = 1;
    /** The loop mode value of a continuous forward loop. */
    public static final int      LOOP_MODE_FORWARD      = 2;
    /**
     * The loop mode value of a loop which runs while the key is held and then plays the remainder
     * of the sample.
     */
    public static final int      LOOP_MODE_SUSTAINED    = 3;
    /** The loop mode value of an alternating (ping-pong) loop. */
    public static final int      LOOP_MODE_BOUNCE       = 4;

    /** The offset of the sample count in the preamble. */
    public static final int      PREAMBLE_COUNT_OFFSET  = 0x4A;

    /**
     * The envelope time in seconds of an envelope knob at its maximum. The knob positions are
     * mapped with the taper {@link #ENVELOPE_TIME_EXPONENT}. Both values were measured by playing
     * a program whose zones differ only in the decay knob against reference zones whose fade-out
     * is part of their audio; they reproduce the six measured decay times within 3 %.
     */
    public static final double   ENVELOPE_MAX_TIME      = 9.07;
    /** The exponent of the envelope knob taper: time = maximum * position^exponent. */
    public static final double   ENVELOPE_TIME_EXPONENT = 3.96;

    /** The default amplitude envelope knob positions of version 0x25 files. */
    public static final float [] ENVELOPE_DEFAULTS_V25  =
    {
        0f,
        0.5f,
        1f,
        0.25f
    };
    /** The default amplitude envelope knob positions of version 0x26 files. */
    public static final float [] ENVELOPE_DEFAULTS_V26  =
    {
        0f,
        1f,
        1f,
        0.18f
    };

    /** The version byte value of the FL Studio Mobile factory files. */
    public static final int      VERSION_25             = 0x25;
    /** The version byte value of the FL Studio Desktop 20.x specimen. */
    public static final int      VERSION_26             = 0x26;
    /** The offset of the version byte in the preamble. */
    public static final int      PREAMBLE_VERSION_OFFSET = 4;

    /** The fixed length of the zeroed shadow block of the name (tag 0x0068). */
    public static final int      SHADOW_NAME_LENGTH     = 10;
    /** The fixed length of the zeroed shadow block of the path (tag 0x0069). */
    public static final int      SHADOW_PATH_LENGTH     = 18;


    private static final HexFormat HEX                  = HexFormat.of ();

    /**
     * The four program parameter blocks 0x0069, 0x006A, 0x006B and 0x006C (the last one twice).
     * All 117 available programs of all three format versions contain exactly these payloads,
     * therefore they are written as they are instead of being zeroed.
     */
    public static final byte []  TEMPLATE_PROGRAM_1     = HEX.parseHex ("00000000403f0000403f0000003f0000403f");
    /** The program parameter block 0x006A, see TEMPLATE_PROGRAM_1. */
    public static final byte []  TEMPLATE_PROGRAM_2     = HEX.parseHex ("000000803e0000003f0000403f0000803e");
    /** The program parameter block 0x006B, see TEMPLATE_PROGRAM_1. */
    public static final byte []  TEMPLATE_PROGRAM_3     = HEX.parseHex ("000000803e0000003f0000803e00000000");
    /** The program parameter block 0x006C, written twice, see TEMPLATE_PROGRAM_1. */
    public static final byte []  TEMPLATE_PROGRAM_4     = HEX.parseHex ("00000000cdcccc3d0000803f0000000000000000");

    /**
     * The fixed 90 byte header, taken from the first program of a real DirectWave desktop export. The size field at 0x28 and the sample count at 0x4A are zeroed here and need
     * to be patched when writing.
     */
    public static final byte []  TEMPLATE_PREAMBLE      = HEX.parseHex ("447750722600000006000000100000000000000023000000000000000000000000000000010000000000000000000000640000001e000000000000000000000000000000803f0000000000000000000000000000000000000000");

    /**
     * A complete sample container block stream, taken verbatim from the first sample of a
     * real monolithic DirectWave program (the embedded audio block is removed). When writing, the zone mapping
     * is patched, the name and path payloads are replaced and the audio format block gets the
     * frame count, channels, sample rate and loop of the zone; all other blocks are copied
     * unchanged, which keeps every written program inside the byte patterns of the known-good
     * factory files.
     */
    public static final byte []  TEMPLATE_CONTAINER     = HEX.parseHex ("f40100001900000000000000190c1a007f000000000000803f0000003f0000000000020000f50100000e000000000000004c656164202d20416c6f6e652030f60100002400000000000000473a5c355c4c656164202d20416c6f6e655c4c656164202d20416c6f6e6520302e776176f70100002800000000000000006c050000000000020000004000000000442c4702000000a3100400486b05000000000020000000f801000008000000000000000000003f00006400f90100000e000000000000000000000000000000000000000000fa01000030000000000000000000000000000000000000000000803f0000000000000000000000000000000000000000000000000000000000000000fb01000014000000000000000000000050237e3f000000000000000000000000fb01000014000000000000000000000000000000000000000000000000000000fc01000002000000000000000000fd0100001000000000000000c976be3dae47a13ef8fb7d3f295c0f3ffe0100000900000000000000000000000000000000ff01000009000000000000000000000000000000000002000009000000000000000000000000000000000102000009000000000000000000000000000000000202000010000000000000000000000000000000f8fb7d3f6f15d53e0202000010000000000000000000000000000000f8fb7d3f6f15d53e030200001400000000000000020000006e12833a000000000000000000000000030200001400000000000000020000006e12833a000000000000000000000000040200000800000000000000020002000000003f040200000800000000000000040001002549123f04020000080000000000000000000000000000000402000008000000000000000000000000000000040200000800000000000000000000000000000004020000080000000000000000000000000000000402000008000000000000000000000000000000040200000800000000000000000000000000000004020000080000000000000000000000000000000402000008000000000000000000000000000000040200000800000000000000000000000000000004020000080000000000000000000000000000000402000008000000000000000000000000000000040200000800000000000000000000000000000004020000080000000000000000000000000000000402000008000000000000000000000000000000040000000000000000000000");


    /**
     * Private constructor for utility class.
     */
    private DirectWaveTag ()
    {
        // Intentionally empty
    }
}
