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

    /** The length of the fixed header before the block stream starts. */
    public static final int      PREAMBLE_SIZE          = 0x5A;

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

    /** The tag of the 8 byte parameter block inside of a sample container. */
    public static final int      TAG_BLOCK_01F8         = 0x01F8;
    /** The tag of the 14 byte zeroed block inside of a sample container. */
    public static final int      TAG_BLOCK_01F9         = 0x01F9;
    /** The tag of the 48 byte parameter block inside of a sample container. */
    public static final int      TAG_BLOCK_01FA         = 0x01FA;
    /** The tag of the two 20 byte zeroed blocks inside of a sample container. */
    public static final int      TAG_BLOCK_01FB         = 0x01FB;
    /** The tag of the 2 byte zeroed block inside of a sample container. */
    public static final int      TAG_BLOCK_01FC         = 0x01FC;
    /** The tag of the 16 byte parameter block inside of a sample container. */
    public static final int      TAG_BLOCK_01FD         = 0x01FD;
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

    /** The number of parameter slot blocks. */
    public static final int      NUM_PARAMETER_SLOTS    = 99;

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

    /** The offset of the frame count in the audio format block. */
    public static final int      FORMAT_FRAME_COUNT     = 0;
    /** The offset of the channel count in the audio format block. */
    public static final int      FORMAT_CHANNELS        = 8;
    /** The offset of the bytes-per-frame field in the audio format block. */
    public static final int      FORMAT_BYTES_PER_FRAME = 12;
    /** The offset of the sample rate (stored as a 32-bit float!) in the audio format block. */
    public static final int      FORMAT_SAMPLE_RATE     = 16;

    private static final HexFormat HEX                  = HexFormat.of ();

    /** The fixed 90 byte header. The size field at 0x28 needs to be patched when writing. */
    public static final byte []  TEMPLATE_PREAMBLE      = HEX.parseHex ("447750722600000006000000100000000000000000000000000000000000000000000000010000000000000000000000640000001e000000000000000000000000006666e63e00000000300000003f000000faff000000000000");

    /** The 25 byte zone mapping block. Bytes 0-4 need to be patched when writing. */
    public static final byte []  TEMPLATE_ZONE_MAPPING  = HEX.parseHex ("240024007f000000000000803f0000003f0100000000020000");

    /** The 40 byte audio format block. Frame count, channels, bytes/frame and rate are patched. */
    public static final byte []  TEMPLATE_AUDIO_FORMAT  = HEX.parseHex ("90c4050000000000020000000400000000442c470000000000000000000000000000000020000000");

    /** The 8 byte block with the tag 0x01F8. */
    public static final byte []  TEMPLATE_BLOCK_01F8    = HEX.parseHex ("0000003f00006400");

    /** The 48 byte block with the tag 0x01FA. */
    public static final byte []  TEMPLATE_BLOCK_01FA    = HEX.parseHex ("0000000000000000000000000000803f0000000000e235040000000070f7390400000000e0c520000000000001000000");

    /** The 16 byte block with the tag 0x01FD. */
    public static final byte []  TEMPLATE_BLOCK_01FD    = HEX.parseHex ("000000000000803f0000803fec51383e");

    /** The first of the sixteen 8 byte blocks with the tag 0x0204. The other 15 are zeroed. */
    public static final byte []  TEMPLATE_BLOCK_0204    = HEX.parseHex ("0200020000000000");


    /**
     * Private constructor for utility class.
     */
    private DirectWaveTag ()
    {
        // Intentionally empty
    }
}
