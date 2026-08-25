// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emax;

import java.nio.charset.StandardCharsets;


/**
 * Constants of the sound banks of the E-mu Emax and Emax II, which are a dump of the whole memory
 * of the sampler. Both samplers use the same structures and differ only in how they store their
 * audio: the Emax companded bytes, the Emax II 16 bit samples. See
 * documentation/design/EMAX_FORMAT.md for how these were established.
 *
 * @author Jürgen Moßgraber
 */
public class EmaxConstants
{
    /** The fixed signature which EMXP puts in front of the bank of an EM1 file. */
    static final byte []           SIGNATURE                  = "emaxutil v1.1 Fri Mar 19 13:31:05 1993\n".getBytes (StandardCharsets.US_ASCII);

    /** The size of the parameter memory, which holds the presets and the sample directory. */
    public static final int        PARAMETER_SIZE             = 0x7000;
    /** The number of frames the sample memory of the Emax holds; it stores one byte per frame. */
    public static final int        MEMORY_FRAMES_EMAX         = 0x80000;
    /** The number of frames the sample memory of a 2 MB Emax II holds; it stores two per frame. */
    public static final int        MEMORY_FRAMES_EMAX_2       = 0x100000;
    /** The number of frames the sample memory of a fully expanded 8 MB Emax II holds. */
    public static final int        MEMORY_FRAMES_EMAX_2_MAX   = 0x400000;
    /** The size of a whole bank of the Emax, whose sample memory is always fully written. */
    public static final int        BANK_SIZE                  = PARAMETER_SIZE + MEMORY_FRAMES_EMAX;
    /** The parameter memory is mapped at this address of the CPU, all pointers include it. */
    public static final int        CPU_BASE                   = 0x8000;
    /** The CPU address of the end of the parameter memory. */
    public static final int        CPU_END                    = CPU_BASE + PARAMETER_SIZE;

    /** The number of preset slots of a bank. */
    public static final int        NUM_PRESET_SLOTS           = 100;
    /** The first free byte of the parameter memory, as a 16 bit CPU address. */
    public static final int        HEAP_POINTER               = 0x0C8;
    /** Unknown, always one. */
    public static final int        BANK_UNKNOWN               = 0x0CC;
    /**
     * The preset which was selected when the bank was saved, 32 bit. This is not the number of
     * presets - a bank can hold a preset in any of the 100 slots, and the library banks put their
     * sequence demos into the last ones.
     */
    public static final int        SELECTED_PRESET            = 0x0D0;
    /** The pointers to the data of the sequences of the sequencer, 32 bit each. */
    public static final int        SEQUENCE_TABLE             = 0x0D4;
    /** The number of sequence slots. */
    public static final int        NUM_SEQUENCE_SLOTS         = 51;
    /**
     * An unused sequence slot holds the number of frames of the sample memory, which is what tells
     * an Emax bank from an Emax II bank: the Emax always has 512 KB, so 0x80000 frames of one byte,
     * while the Emax II has at least 2 MB, so 0x100000 frames of two bytes.
     */
    public static final int        SEQUENCE_EMPTY             = MEMORY_FRAMES_EMAX;
    /** The CPU address of the lowest entry of the sample directory, 32 bit. */
    public static final int        SAMPLE_DIRECTORY_BOTTOM    = 0x1A0;
    /** The CPU address of the highest entry of the sample directory, 32 bit. */
    public static final int        SAMPLE_DIRECTORY_TOP       = 0x1A4;
    /** The number of used bytes of the sample memory, 32 bit. */
    public static final int        SAMPLE_MEMORY_USED         = 0x1A8;
    /** The offset of the first preset record, which is where the preset heap starts. */
    public static final int        PRESET_HEAP                = 0x1AC;

    /** The length of the name of a preset. */
    public static final int        PRESET_NAME_LENGTH         = 12;
    /** The number of key areas of a preset, which is the length of its voice table. */
    public static final int        PRESET_KEY_AREA_COUNT      = 0x23;
    /** The table which maps each of the 88 keys to an entry of the voice table. */
    public static final int        PRESET_KEY_MAP             = 0x24;
    /** The voice table, which follows the key map. */
    public static final int        PRESET_VOICE_TABLE         = 0x7C;
    /** The number of keys of the key map. */
    public static final int        NUM_KEYS                   = 88;
    /** The MIDI note of the first key of the key map. */
    public static final int        KEY_OFFSET                 = 21;
    /** The key map entry of a key which plays nothing. */
    public static final int        KEY_UNMAPPED               = 0xFF;

    /** The size of one entry of the voice table. */
    public static final int        VOICE_TABLE_ENTRY_SIZE     = 4;
    /** The flags of a voice table entry. */
    public static final int        VOICE_TABLE_MODE           = 0;
    /** The flag which marks that a key area has a secondary voice. */
    public static final int        VOICE_TABLE_MODE_DUAL      = 0x10;
    /** The primary voice of a key area. */
    public static final int        VOICE_TABLE_PRIMARY        = 2;
    /** The secondary voice of a key area. */
    public static final int        VOICE_TABLE_SECONDARY      = 3;
    /** A voice table slot which references no voice. */
    public static final int        VOICE_NONE                 = 0xFF;

    /** The size of one voice record. */
    public static final int        VOICE_SIZE                 = 32;
    /** The key at which the voice plays its sample at the recorded pitch, in key map numbering. */
    public static final int        VOICE_ORIGINAL_KEY         = 12;
    /** The number of the sample which the voice plays. */
    public static final int        VOICE_SAMPLE               = 13;
    /** The initial cutoff frequency of the low pass filter, 0 to 120. */
    public static final int        VOICE_FILTER_CUTOFF        = 16;
    /** The panning in the upper nibble, 0 to 15 with 8 centred. */
    public static final int        VOICE_PANNING              = 24;
    /** The byte which holds the chorus switch. */
    public static final int        VOICE_CHORUS               = 27;
    /** The bit which switches the chorus on. */
    public static final int        VOICE_CHORUS_BIT           = 0x08;

    /** The highest value of the filter cutoff, at which the filter is fully open. */
    public static final int        FILTER_CUTOFF_MAX          = 120;
    /** The value of the panning nibble which is centred. */
    public static final int        PANNING_CENTER             = 8;

    /** The size of one entry of the sample directory. */
    public static final int        SAMPLE_ENTRY_SIZE          = 32;
    /** The start of the audio of a sample in the sample memory, 32 bit. */
    public static final int        SAMPLE_START               = 4;
    /** The end of the audio of a sample, which is the start of the next one, 32 bit. */
    public static final int        SAMPLE_END                 = 8;
    /** The start of the sustain loop, 32 bit. */
    public static final int        SAMPLE_LOOP_START          = 12;
    /** The end of the sustain loop, 32 bit. */
    public static final int        SAMPLE_LOOP_END            = 16;
    /** The start of the release loop, 32 bit. */
    public static final int        SAMPLE_RELEASE_LOOP_START  = 20;
    /** The end of the release loop, 32 bit. */
    public static final int        SAMPLE_RELEASE_LOOP_END    = 24;
    /** The flags of a sample. */
    public static final int        SAMPLE_FLAGS               = 28;
    /** The index into {@link #SAMPLE_RATES} at which the sample was recorded. */
    public static final int        SAMPLE_RATE_INDEX          = 29;
    /** The flag which switches the loop on. */
    public static final int        SAMPLE_FLAG_LOOP           = 0x01;
    /** The flag which keeps the loop running after the key was released. */
    public static final int        SAMPLE_FLAG_LOOP_RELEASE   = 0x02;

    /** The sample rates which the sample rate index of a sample selects. */
    static final int []            SAMPLE_RATES               =
    {
        10000,
        15625,
        20000,
        22050,
        27778,
        31250,
        41667,
        44100
    };

    /** How many semi-tones a sample of each of the sample rates can be transposed upwards. */
    static final int []            MAX_UPWARD_TRANSPOSE       =
    {
        25,
        18,
        13,
        12,
        8,
        6,
        1,
        0
    };

    // The 32 bytes of a voice record are a bit stream in which the parameters are packed
    // little-endian: bit N of the stream is bit N % 8 of byte N / 8. The offsets below are into
    // that stream. They were established by aligning 37,965 voices of the Emax II library CD-ROMs
    // with the SoundFont files which EMXP produced from the same banks - see the design document.

    /** The attack of the amplitude envelope, 5 bits, an index into the time table. */
    public static final int        VOICE_AMP_ATTACK           = 0;
    /** The hold of the amplitude envelope, 5 bits. */
    public static final int        VOICE_AMP_HOLD             = 5;
    /** The decay of the amplitude envelope, 5 bits. */
    public static final int        VOICE_AMP_DECAY            = 10;
    /** The sustain of the amplitude envelope, 5 bits. */
    public static final int        VOICE_AMP_SUSTAIN          = 15;
    /** The release of the amplitude envelope, 5 bits. */
    public static final int        VOICE_AMP_RELEASE          = 20;
    /** The rate of the LFO, 7 bits. */
    public static final int        VOICE_LFO_RATE             = 25;
    /** The delay before the LFO starts, 6 bits. */
    public static final int        VOICE_LFO_DELAY            = 32;
    /** The depth of the LFO on the pitch (vibrato), 4 bits of 13 cents each. */
    public static final int        VOICE_LFO_TO_PITCH         = 43;
    /** The tuning of the voice, a signed 5 bit value of 3 cents each. */
    public static final int        VOICE_TUNE                 = 47;
    /** The amount of velocity on the filter cutoff, 4 bits. */
    public static final int        VOICE_VELOCITY_TO_CUTOFF   = 52;
    /** The amount of velocity on the filter attack, 4 bits. */
    public static final int        VOICE_VELOCITY_TO_F_ATTACK = 56;
    /** The depth of the LFO on the level (tremolo), 4 bits of 1.6 dB each. */
    public static final int        VOICE_LFO_TO_VOLUME        = 64;
    /** The amount of velocity on the level, 4 bits, an index into the dynamic range table. */
    public static final int        VOICE_VELOCITY_TO_LEVEL    = 68;
    /** The amount of velocity on the amplitude attack, 4 bits. */
    public static final int        VOICE_VELOCITY_TO_ATTACK   = 76;
    /** The amount of the chorus, 5 bits. */
    public static final int        VOICE_CHORUS_AMOUNT        = 88;
    /** The initial cutoff frequency of the low pass filter, 7 bits. */
    public static final int        VOICE_FILTER_CUTOFF_BITS   = 128;
    /** The resonance of the low pass filter, 7 bits. */
    public static final int        VOICE_FILTER_RESONANCE     = 136;
    /** The amount of the filter envelope, a signed 7 bit value of 240 cents each. */
    public static final int        VOICE_FILTER_ENV_AMOUNT    = 144;
    /** The attack of the filter envelope, 5 bits. */
    public static final int        VOICE_FILTER_ATTACK        = 160;
    /** The hold of the filter envelope, 5 bits. */
    public static final int        VOICE_FILTER_HOLD          = 165;
    /** The decay of the filter envelope, 5 bits. */
    public static final int        VOICE_FILTER_DECAY         = 170;
    /** The sustain of the filter envelope, 5 bits. */
    public static final int        VOICE_FILTER_SUSTAIN       = 175;
    /** The release of the filter envelope, 5 bits. */
    public static final int        VOICE_FILTER_RELEASE       = 180;
    /** The amount of velocity on the filter resonance, 4 bits. */
    public static final int        VOICE_VELOCITY_TO_Q        = 185;
    /** The flag which stops the voice from being transposed over the keyboard, 1 bit. */
    public static final int        VOICE_NON_TRANSPOSE        = 191;
    /** The keyboard tracking of the filter cutoff, 4 bits. */
    public static final int        VOICE_FILTER_TRACKING      = 192;
    /** The panning, 4 bits: 1 is fully right, 8 is centred and 15 is fully left. */
    public static final int        VOICE_PANNING_BITS         = 196;
    /** The depth of the LFO on the filter cutoff, 4 bits of 340 cents each. */
    public static final int        VOICE_LFO_TO_CUTOFF        = 204;
    /** The delay between the key and the start of the voice, 6 bits. */
    public static final int        VOICE_DELAY                = 208;
    /** The attenuation of the voice, 5 bits of 1.5 dB each. */
    public static final int        VOICE_ATTENUATION          = 214;
    /** The flag which switches the chorus on, 1 bit. */
    public static final int        VOICE_CHORUS_BIT_STREAM    = 219;

    /** The cents which one step of the voice tuning is worth. */
    public static final double     TUNE_CENTS_PER_STEP        = 3.0;
    /** The decibels which one step of the voice attenuation is worth. */
    public static final double     ATTENUATION_DB_PER_STEP    = 1.5;
    /** The cents which one step of the filter envelope amount is worth. */
    public static final double     FILTER_ENV_CENTS_PER_STEP  = 240.0;
    /** The cents which one step of the LFO to pitch depth is worth. */
    public static final double     LFO_PITCH_CENTS_PER_STEP   = 13.0;
    /** The decibels which one step of the LFO to volume depth is worth. */
    public static final double     LFO_VOLUME_DB_PER_STEP     = 1.6;
    /** The cents which one step of the LFO to cutoff depth is worth. */
    public static final double     LFO_CUTOFF_CENTS_PER_STEP  = 340.0;
    /** The cents per key which one step of the filter keyboard tracking is worth. */
    public static final double     FILTER_TRACKING_PER_STEP   = 5.9;

    /** The attack time in seconds of the 32 values of an envelope attack. */
    private static final double [] ENVELOPE_ATTACK_TIME       =
    {
        0.0010,
        0.0100,
        0.0200,
        0.0350,
        0.0450,
        0.0590,
        0.0720,
        0.0850,
        0.0960,
        0.1050,
        0.1350,
        0.1800,
        0.2500,
        0.3301,
        0.4099,
        0.5099,
        0.6499,
        0.8001,
        0.9800,
        1.2002,
        1.5000,
        1.8500,
        2.3001,
        2.7495,
        3.2006,
        3.6490,
        4.1506,
        4.6995,
        5.8159,
        7.2017,
        8.6939,
        10.5013
    };

    /** The hold time in seconds of the 32 values of an envelope hold. */
    private static final double [] ENVELOPE_HOLD_TIME         =
    {
        0.0010,
        0.0800,
        0.1500,
        0.2200,
        0.2800,
        0.3399,
        0.4001,
        0.4700,
        0.5399,
        0.6001,
        0.6601,
        0.7299,
        0.8001,
        0.8700,
        0.9298,
        0.9902,
        1.0497,
        1.1199,
        1.1803,
        1.2498,
        1.3096,
        1.3700,
        1.4398,
        1.5000,
        1.5601,
        1.6301,
        1.6896,
        1.7502,
        1.8088,
        1.8704,
        1.9296,
        2.0000
    };

    /** The time in seconds of the 32 values of an envelope decay and release. */
    private static final double [] ENVELOPE_DECAY_TIME        =
    {
        0.0010,
        0.1500,
        0.1900,
        0.2300,
        0.3399,
        0.4900,
        0.7900,
        1.1701,
        1.5601,
        2.0598,
        2.4396,
        2.8895,
        3.2303,
        3.6196,
        4.0395,
        4.5211,
        5.0513,
        5.6405,
        6.6691,
        7.4902,
        8.3494,
        9.3394,
        10.4529,
        11.6992,
        13.2003,
        15.6979,
        21.0027,
        32.0000,
        55.0117,
        100.0215,
        100.0215,
        100.0215
    };

    /** The level of the 32 values of an envelope sustain, 0 to 1. */
    private static final double [] ENVELOPE_SUSTAIN_LEVEL     =
    {
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00000,
        0.00002,
        0.00003,
        0.00006,
        0.00009,
        0.00028,
        0.00045,
        0.00224,
        0.00631,
        0.01259,
        0.01778,
        0.03981,
        0.06310,
        0.14125,
        0.19953,
        0.28184,
        0.44668,
        0.56234,
        0.73104,
        1.00000
    };

    /** The rate in Hertz of the 128 values of the LFO rate. */
    private static final double [] LFO_RATE                   =
    {
        0.133,
        0.133,
        0.138,
        0.144,
        0.150,
        0.156,
        0.163,
        0.171,
        0.180,
        0.189,
        0.200,
        0.207,
        0.216,
        0.225,
        0.235,
        0.246,
        0.259,
        0.272,
        0.287,
        0.303,
        0.322,
        0.332,
        0.342,
        0.353,
        0.364,
        0.377,
        0.390,
        0.404,
        0.420,
        0.436,
        0.454,
        0.472,
        0.490,
        0.510,
        0.531,
        0.555,
        0.581,
        0.609,
        0.641,
        0.675,
        0.714,
        0.740,
        0.769,
        0.800,
        0.833,
        0.869,
        0.909,
        0.952,
        1.000,
        1.052,
        1.111,
        1.149,
        1.190,
        1.234,
        1.282,
        1.333,
        1.388,
        1.449,
        1.515,
        1.587,
        1.666,
        1.739,
        1.818,
        1.904,
        2.000,
        2.105,
        2.223,
        2.352,
        2.501,
        2.666,
        2.857,
        2.933,
        3.012,
        3.095,
        3.183,
        3.279,
        3.379,
        3.484,
        3.598,
        3.716,
        3.845,
        4.032,
        4.237,
        4.463,
        4.715,
        5.001,
        5.320,
        5.682,
        6.097,
        6.580,
        7.142,
        7.301,
        7.463,
        7.637,
        7.816,
        7.998,
        8.195,
        8.401,
        8.622,
        8.849,
        9.198,
        9.561,
        9.944,
        10.337,
        10.745,
        11.169,
        11.616,
        12.075,
        12.551,
        13.046,
        13.569,
        14.104,
        14.661,
        15.239,
        15.850,
        16.475,
        17.125,
        17.801,
        18.514,
        19.245,
        20.004,
        20.004,
        20.004,
        20.004,
        20.004,
        20.004,
        20.004,
        20.004
    };

    /** The delay in seconds of the 64 values of the LFO delay. */
    private static final double [] LFO_DELAY_TIME             =
    {
        0.0010,
        0.0630,
        0.1270,
        0.1900,
        0.2539,
        0.3181,
        0.3809,
        0.4449,
        0.5090,
        0.5720,
        0.6362,
        0.7002,
        0.7631,
        0.8269,
        0.8899,
        0.9537,
        1.0151,
        1.0811,
        1.1447,
        1.2093,
        1.2716,
        1.3364,
        1.3980,
        1.4632,
        1.5271,
        1.5911,
        1.6539,
        1.7181,
        1.7797,
        1.8446,
        1.9086,
        1.9713,
        2.0361,
        2.0994,
        2.1634,
        2.2268,
        2.2894,
        2.3538,
        2.4172,
        2.4808,
        2.5432,
        2.6087,
        2.6728,
        2.7352,
        2.8008,
        2.8629,
        2.9248,
        2.9880,
        3.0525,
        3.1185,
        3.1785,
        3.2396,
        3.3020,
        3.3655,
        3.4303,
        3.4963,
        3.5636,
        3.6259,
        3.6893,
        3.7538,
        3.7538,
        3.7538,
        3.7538,
        3.7538
    };

    /** The delay in seconds of the 64 values of the voice delay. */
    private static final double [] VOICE_DELAY_TIME           =
    {
        0.0010,
        0.0060,
        0.0130,
        0.0200,
        0.0280,
        0.0360,
        0.0450,
        0.0524,
        0.0610,
        0.0700,
        0.0794,
        0.0900,
        0.0995,
        0.1100,
        0.1196,
        0.1300,
        0.1400,
        0.1507,
        0.1622,
        0.1746,
        0.1880,
        0.2031,
        0.2193,
        0.2367,
        0.2555,
        0.2760,
        0.3018,
        0.3301,
        0.3660,
        0.4059,
        0.4501,
        0.4906,
        0.5346,
        0.5824,
        0.6347,
        0.6918,
        0.7539,
        0.8212,
        0.8950,
        0.9755,
        1.0631,
        1.1580,
        1.2621,
        1.3755,
        1.4992,
        1.6330,
        1.7797,
        1.9397,
        2.1140,
        2.3027,
        2.5097,
        2.7352,
        2.9811,
        3.2471,
        3.5390,
        3.8571,
        4.2037,
        4.5789,
        4.9904,
        5.4390,
        5.9278,
        6.4569,
        7.0372,
        7.6697
    };

    /** The cutoff frequency in Hertz of the 128 values of the filter frequency. */
    private static final double [] CUTOFF_FREQUENCY           =
    {
        20.0,
        20.0,
        20.0,
        20.0,
        21.5,
        23.0,
        25.0,
        27.0,
        28.9,
        31.0,
        33.0,
        36.0,
        39.0,
        37.0,
        40.0,
        43.0,
        46.0,
        48.9,
        52.0,
        55.9,
        60.0,
        64.0,
        68.0,
        71.5,
        75.1,
        79.0,
        83.8,
        89.0,
        95.0,
        101.0,
        108.0,
        115.0,
        121.0,
        127.0,
        133.0,
        139.0,
        145.0,
        151.0,
        156.0,
        161.0,
        167.0,
        173.0,
        179.0,
        185.0,
        191.0,
        200.0,
        209.9,
        220.0,
        230.0,
        240.1,
        250.0,
        260.0,
        270.1,
        280.1,
        290.0,
        300.0,
        310.1,
        320.1,
        330.0,
        340.1,
        350.0,
        380.0,
        410.1,
        440.0,
        470.0,
        499.9,
        530.0,
        560.2,
        590.1,
        640.1,
        700.1,
        759.9,
        820.1,
        880.0,
        939.9,
        999.8,
        1059.9,
        1120.3,
        1180.1,
        1240.2,
        1300.4,
        1499.8,
        1700.1,
        1999.7,
        2500.6,
        3299.6,
        4301.3,
        5001.2,
        6301.1,
        7998.8,
        9501.2,
        10499.7,
        11799.1,
        12896.8,
        13999.2,
        15004.0,
        15997.5,
        16997.8,
        18196.8,
        18697.6,
        19002.4,
        19201.0,
        19301.1,
        19401.7,
        19480.3,
        19559.2,
        19615.8,
        19661.2,
        19695.3,
        19740.8,
        19775.1,
        19797.9,
        19832.3,
        19855.2,
        19866.7,
        19889.6,
        19912.6,
        19935.6,
        19958.7,
        19981.7,
        19981.7,
        19981.7,
        19981.7,
        19981.7,
        19981.7,
        19981.7,
        19981.7,
        19981.7
    };

    /** The resonance in decibels of the 128 values of the filter Q. */
    private static final double [] RESONANCE                  =
    {
        0.0,
        0.0,
        0.0,
        0.0,
        1.0,
        2.0,
        2.0,
        2.0,
        3.0,
        3.0,
        4.0,
        4.0,
        4.0,
        4.0,
        4.5,
        5.0,
        5.0,
        5.5,
        6.0,
        6.0,
        7.0,
        7.0,
        7.0,
        7.0,
        8.0,
        8.0,
        8.0,
        9.0,
        9.0,
        9.0,
        10.0,
        10.0,
        11.0,
        11.0,
        12.0,
        12.0,
        13.0,
        13.0,
        14.0,
        14.0,
        15.0,
        15.0,
        16.0,
        16.0,
        17.0,
        17.0,
        18.0,
        18.0,
        19.0,
        19.0,
        20.0,
        20.0,
        21.0,
        22.0,
        23.0,
        24.0,
        24.0,
        25.0,
        26.0,
        27.0,
        28.0,
        28.0,
        29.0,
        30.0,
        31.0,
        32.0,
        32.0,
        33.0,
        34.0,
        35.0,
        36.0,
        37.0,
        38.5,
        40.0,
        41.5,
        43.0,
        44.0,
        45.0,
        47.0,
        48.0,
        50.3,
        52.6,
        54.9,
        57.2,
        59.5,
        61.8,
        64.1,
        66.4,
        68.7,
        71.0,
        73.3,
        75.6,
        77.9,
        80.2,
        82.5,
        84.8,
        87.1,
        89.4,
        91.7,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0,
        94.0
    };

    /** The dynamic range in decibels of the 16 values of velocity to level. */
    private static final double [] VELOCITY_TO_LEVEL          =
    {
        0.0,
        1.0,
        3.0,
        4.0,
        6.0,
        7.0,
        8.0,
        10.0,
        12.0,
        14.0,
        16.0,
        18.0,
        21.0,
        24.0,
        28.0,
        32.0
    };

    /** Expansion of the 256 possible sample bytes into signed 16 bit audio. */
    private static final short []  EXPANSION                  = createExpansionTable ();


    /**
     * Private constructor since this is a utility class.
     */
    private EmaxConstants ()
    {
        // Intentionally empty
    }


    /**
     * Expand one stored sample byte into a signed 16 bit sample value.
     *
     * @param sampleByte The byte as stored in the bank
     * @return The sample value
     */
    public static short expand (final int sampleByte)
    {
        return EXPANSION[sampleByte & 0xFF];
    }


    /**
     * Compand a signed 16 bit sample value into the byte which comes closest to it.
     *
     * @param value The sample value
     * @return The byte to store in the bank
     */
    public static int compand (final int value)
    {
        final boolean isNegative = value < 0;
        final int magnitude = isNegative ? -value : value;
        // The transfer function rises monotonically, so the closest of the 16 steps of the chord
        // which covers the magnitude is the closest of all 128 steps
        int chord = 0;
        while (chord < 7 && magnitude > EXPANSION[chord << 4 | 0x0F])
            chord++;
        int bestStep = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int step = 0; step < 16; step++)
        {
            final int distance = Math.abs (EXPANSION[chord << 4 | step] - magnitude);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestStep = step;
            }
        }
        return (isNegative ? 0x80 : 0x00) | chord << 4 | bestStep;
    }


    /**
     * Read a parameter of a voice record. The parameters are packed into the 32 bytes as a
     * little-endian bit stream: bit N of the stream is bit N modulo 8 of byte N divided by 8.
     *
     * @param voiceRecord The 32 bytes of the voice record
     * @param offset The offset of the parameter in the bit stream
     * @param width The number of bits of the parameter
     * @return The value
     */
    public static int readVoiceField (final byte [] voiceRecord, final int offset, final int width)
    {
        int value = 0;
        for (int i = 0; i < width; i++)
        {
            final int bit = offset + i;
            if ((voiceRecord[bit / 8] >> bit % 8 & 1) != 0)
                value |= 1 << i;
        }
        return value;
    }


    /**
     * Read a parameter of a voice record which is stored as a signed value.
     *
     * @param voiceRecord The 32 bytes of the voice record
     * @param offset The offset of the parameter in the bit stream
     * @param width The number of bits of the parameter
     * @return The value
     */
    public static int readSignedVoiceField (final byte [] voiceRecord, final int offset, final int width)
    {
        final int value = readVoiceField (voiceRecord, offset, width);
        final int sign = 1 << width - 1;
        return (value & sign) != 0 ? value - (sign << 1) : value;
    }


    /**
     * Write a parameter into a voice record.
     *
     * @param voiceRecord The 32 bytes of the voice record
     * @param offset The offset of the parameter in the bit stream
     * @param width The number of bits of the parameter
     * @param value The value
     */
    public static void writeVoiceField (final byte [] voiceRecord, final int offset, final int width, final int value)
    {
        for (int i = 0; i < width; i++)
        {
            final int bit = offset + i;
            final int mask = 1 << bit % 8;
            if ((value >> i & 1) != 0)
                voiceRecord[bit / 8] |= (byte) mask;
            else
                voiceRecord[bit / 8] &= (byte) ~mask;
        }
    }


    /**
     * Get the attack time of an envelope.
     *
     * @param value The value of the parameter, 0 to 31
     * @return The time in seconds
     */
    public static double getEnvelopeAttackTime (final int value)
    {
        return ENVELOPE_ATTACK_TIME[Math.clamp (value, 0, ENVELOPE_ATTACK_TIME.length - 1)];
    }


    /**
     * Get the hold time of an envelope.
     *
     * @param value The value of the parameter, 0 to 31
     * @return The time in seconds
     */
    public static double getEnvelopeHoldTime (final int value)
    {
        return ENVELOPE_HOLD_TIME[Math.clamp (value, 0, ENVELOPE_HOLD_TIME.length - 1)];
    }


    /**
     * Get the decay or release time of an envelope, which share one table.
     *
     * @param value The value of the parameter, 0 to 31
     * @return The time in seconds
     */
    public static double getEnvelopeDecayTime (final int value)
    {
        return ENVELOPE_DECAY_TIME[Math.clamp (value, 0, ENVELOPE_DECAY_TIME.length - 1)];
    }


    /**
     * Get the sustain level of an envelope.
     *
     * @param value The value of the parameter, 0 to 31
     * @return The level from 0 to 1
     */
    public static double getEnvelopeSustainLevel (final int value)
    {
        return ENVELOPE_SUSTAIN_LEVEL[Math.clamp (value, 0, ENVELOPE_SUSTAIN_LEVEL.length - 1)];
    }


    /**
     * Get the rate of the LFO.
     *
     * @param value The value of the parameter, 0 to 127
     * @return The rate in Hertz
     */
    public static double getLfoRate (final int value)
    {
        return LFO_RATE[Math.clamp (value, 0, LFO_RATE.length - 1)];
    }


    /**
     * Get the delay before the LFO starts.
     *
     * @param value The value of the parameter, 0 to 63
     * @return The delay in seconds
     */
    public static double getLfoDelayTime (final int value)
    {
        return LFO_DELAY_TIME[Math.clamp (value, 0, LFO_DELAY_TIME.length - 1)];
    }


    /**
     * Get the delay between the key and the start of a voice.
     *
     * @param value The value of the parameter, 0 to 63
     * @return The delay in seconds
     */
    public static double getVoiceDelayTime (final int value)
    {
        return VOICE_DELAY_TIME[Math.clamp (value, 0, VOICE_DELAY_TIME.length - 1)];
    }


    /**
     * Get the cutoff frequency of the low pass filter.
     *
     * @param value The value of the cutoff parameter, 0 to 120
     * @return The cutoff frequency in Hertz
     */
    public static double getCutoffFrequency (final int value)
    {
        return CUTOFF_FREQUENCY[Math.clamp (value, 0, CUTOFF_FREQUENCY.length - 1)];
    }


    /**
     * Get the value of the cutoff parameter which comes closest to a cutoff frequency.
     *
     * @param frequency The cutoff frequency in Hertz
     * @return The value of the cutoff parameter, 0 to 120
     */
    public static int getCutoffValue (final double frequency)
    {
        return Math.min (FILTER_CUTOFF_MAX, findClosest (CUTOFF_FREQUENCY, frequency));
    }


    /**
     * Get the resonance of the low pass filter.
     *
     * @param value The value of the Q parameter, 0 to 127
     * @return The resonance in decibels
     */
    public static double getResonance (final int value)
    {
        return RESONANCE[Math.clamp (value, 0, RESONANCE.length - 1)];
    }


    /**
     * Get the value of the Q parameter which comes closest to a resonance.
     *
     * @param resonance The resonance in decibels
     * @return The value of the Q parameter
     */
    public static int getResonanceValue (final double resonance)
    {
        return findClosest (RESONANCE, resonance);
    }


    /**
     * Get the dynamic range which velocity spans at the given amount.
     *
     * @param value The value of the parameter, 0 to 15
     * @return The range in decibels
     */
    public static double getVelocityToLevel (final int value)
    {
        return VELOCITY_TO_LEVEL[Math.clamp (value, 0, VELOCITY_TO_LEVEL.length - 1)];
    }


    /**
     * Get the value of an envelope attack which comes closest to a time.
     *
     * @param seconds The time in seconds
     * @return The value, 0 to 31
     */
    public static int getEnvelopeAttackValue (final double seconds)
    {
        return findClosest (ENVELOPE_ATTACK_TIME, seconds);
    }


    /**
     * Get the value of an envelope hold which comes closest to a time.
     *
     * @param seconds The time in seconds
     * @return The value, 0 to 31
     */
    public static int getEnvelopeHoldValue (final double seconds)
    {
        return findClosest (ENVELOPE_HOLD_TIME, seconds);
    }


    /**
     * Get the value of an envelope decay or release which comes closest to a time.
     *
     * @param seconds The time in seconds
     * @return The value, 0 to 31
     */
    public static int getEnvelopeDecayValue (final double seconds)
    {
        return findClosest (ENVELOPE_DECAY_TIME, seconds);
    }


    /**
     * Get the value of an envelope sustain which comes closest to a level.
     *
     * @param level The level from 0 to 1
     * @return The value, 0 to 31
     */
    public static int getEnvelopeSustainValue (final double level)
    {
        return findClosest (ENVELOPE_SUSTAIN_LEVEL, level);
    }


    /**
     * Get the value of the LFO rate which comes closest to a rate.
     *
     * @param hertz The rate in Hertz
     * @return The value, 0 to 127
     */
    public static int getLfoRateValue (final double hertz)
    {
        return findClosest (LFO_RATE, hertz);
    }


    /**
     * Get the value of the LFO delay which comes closest to a time.
     *
     * @param seconds The time in seconds
     * @return The value, 0 to 63
     */
    public static int getLfoDelayValue (final double seconds)
    {
        return Math.min (LFO_DELAY_TIME.length - 1, findClosest (LFO_DELAY_TIME, seconds));
    }


    /**
     * Get the value of the voice delay which comes closest to a time.
     *
     * @param seconds The time in seconds
     * @return The value, 0 to 63
     */
    public static int getVoiceDelayValue (final double seconds)
    {
        return Math.min (VOICE_DELAY_TIME.length - 1, findClosest (VOICE_DELAY_TIME, seconds));
    }


    /**
     * Get the value of velocity to level which comes closest to a dynamic range.
     *
     * @param decibels The range in decibels
     * @return The value, 0 to 15
     */
    public static int getVelocityToLevelValue (final double decibels)
    {
        return findClosest (VELOCITY_TO_LEVEL, decibels);
    }


    /**
     * Get the index of the entry of a table which comes closest to a value. The tables rise
     * monotonically, so the closest entry is the best representation of the value.
     *
     * @param table The table to search
     * @param value The value to look for
     * @return The index of the closest entry
     */
    private static int findClosest (final double [] table, final double value)
    {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < table.length; i++)
        {
            final double distance = Math.abs (table[i] - value);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }


    /**
     * Get the index of the sample rate which comes closest to the given one but is not higher, so
     * that the audio never needs to be up-sampled.
     *
     * @param sampleRate The sample rate in Hertz
     * @return The index into {@link #SAMPLE_RATES}
     */
    public static int getSampleRateIndex (final int sampleRate)
    {
        for (int i = SAMPLE_RATES.length - 1; i > 0; i--)
            if (SAMPLE_RATES[i] <= sampleRate)
                return i;
        return 0;
    }


    /**
     * Build the transfer function of the AM6072 companding DAC which the Emax uses, the same one
     * the Emulator II has. A stored byte is a sign in bit 7, a chord in bits 6 to 4 and a step in
     * bits 3 to 0; the chord doubles the size of a step, which is what gives the 8 bits of a sample
     * the dynamic range of about 13 linear bits.
     *
     * @return The expansion of all 256 byte values, scaled to the 16 bit range
     */
    private static short [] createExpansionTable ()
    {
        final short [] table = new short [256];
        for (int value = 0; value < 256; value++)
        {
            final int chord = value >> 4 & 0x07;
            final int step = value & 0x0F;
            final int magnitude = (((step << 1) + 33) << chord) - 33;
            // The largest magnitude is 8031, which leaves the scaled value inside the 16 bit range
            final int scaled = magnitude * 4;
            table[value] = (short) ((value & 0x80) != 0 ? -scaled : scaled);
        }
        return table;
    }
}
