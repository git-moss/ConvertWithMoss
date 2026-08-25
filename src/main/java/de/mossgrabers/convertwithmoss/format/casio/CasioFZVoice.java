// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.casio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


/**
 * A voice of a Casio FZ-1/FZ-10M/FZ-20M: 192 bytes of parameters which describe one sample with its
 * addresses in the wave memory, loops, envelopes, filter and key/velocity ranges. All multi-byte
 * values are stored little-endian, addresses are 16-bit word addresses into the wave memory.
 *
 * @author Jürgen Moßgraber
 */
public class CasioFZVoice
{
    /** The size of the voice parameters in bytes. */
    public static final int       SIZE                  = 192;

    /** Sounding style: the waveform is not yet defined. */
    public static final int       MODE_NO_SOUND         = 0x0000;
    /** Sounding style: normal sound. */
    public static final int       MODE_NORMAL           = 0x01D7;
    /** Sounding style: reversed sound. */
    public static final int       MODE_REVERSED         = 0x101D;
    /** Sounding style: cuing sound. */
    public static final int       MODE_CUE              = 0x2014;
    /** Sounding style: synthesized waveform. */
    public static final int       MODE_SYNTHESIZED      = 0x0013;

    /** The number of multi-loops. */
    public static final int       NUM_LOOPS             = 8;
    /**
     * The amount by which the sampler moves an envelope towards the level of its stage on every one
     * of its envelope interrupts, one entry per rate. The sampler adds this to a 16 bit level whose
     * upper byte is the 0-255 level which its amplifier and its filter receive. Its timer interrupt
     * runs 2000 times a second - it is reloaded with 1000 on a 2 MHz clock - and serves every voice
     * with one of 8 tasks in turn, two of which advance the amplitude envelope, so an envelope is
     * advanced 500 times a second. The increments span 3 to 32767, which is why the times are
     * anything but a straight line over the rates.
     */
    private static final int []   ENVELOPE_INCREMENT    =
    {
        0,
        3,
        6,
        9,
        13,
        16,
        20,
        24,
        28,
        33,
        37,
        42,
        47,
        52,
        58,
        64,
        70,
        77,
        84,
        91,
        99,
        107,
        115,
        124,
        133,
        143,
        153,
        164,
        175,
        187,
        200,
        213,
        227,
        241,
        257,
        273,
        290,
        307,
        326,
        346,
        366,
        388,
        411,
        435,
        460,
        487,
        515,
        544,
        575,
        607,
        641,
        677,
        715,
        754,
        796,
        840,
        886,
        934,
        985,
        1038,
        1094,
        1153,
        1215,
        1281,
        1349,
        1421,
        1497,
        1577,
        1661,
        1749,
        1841,
        1939,
        2041,
        2149,
        2262,
        2381,
        2506,
        2637,
        2775,
        2920,
        3073,
        3234,
        3402,
        3580,
        3766,
        3962,
        4168,
        4385,
        4613,
        4852,
        5104,
        5368,
        5647,
        5939,
        6247,
        6570,
        6910,
        7267,
        7642,
        8037,
        8452,
        8888,
        9347,
        9829,
        10336,
        10869,
        11429,
        12018,
        12637,
        13288,
        13972,
        14692,
        15448,
        16243,
        17078,
        17957,
        18881,
        19852,
        20872,
        21945,
        23074,
        24260,
        25506,
        26817,
        28195,
        29643,
        31166,
        32767
    };

    /** How often per second the sampler advances an envelope. */
    private static final double   ENVELOPE_SERVICE_RATE = 500.0;
    /** One step of the 0-255 envelope level in the units in which the increments are counted. */
    private static final double   ENVELOPE_LEVEL_STEP   = 256.0;
    /** The duration reported for a stage which never reaches its level. */
    private static final double   LONGEST_STAGE_SECONDS = 60.0;

    /** The number of envelope stages. */
    public static final int       NUM_ENVELOPE_STAGES   = 8;

    /** The sample rates addressed by the samp field. */
    protected static final int [] SAMPLE_RATES          =
    {
        36000,
        18000,
        9000
    };

    long                          waveStart;
    long                          waveEnd;
    long                          generatorStart;
    long                          generatorEnd;
    int                           mode                  = MODE_NORMAL;
    int                           loopSustain           = 8;
    int                           loopEnd               = 0;
    final long []                 loopStart             = new long [NUM_LOOPS];
    final int []                  loopFine              = new int [NUM_LOOPS];
    final long []                 loopEndAddress        = new long [NUM_LOOPS];
    final boolean []              loopSkip              = new boolean [NUM_LOOPS];
    final int []                  loopCrossfade         = new int [NUM_LOOPS];
    final int []                  loopTime              = new int [NUM_LOOPS];
    int                           pitch;
    int                           cutoff                = 127;
    int                           resonance;
    int                           ampSustainPoint;
    int                           ampEndPoint;
    final int []                  ampRate               = new int [NUM_ENVELOPE_STAGES];
    final int []                  ampStop               = new int [NUM_ENVELOPE_STAGES];
    int                           filterSustainPoint;
    int                           filterEndPoint;
    final int []                  filterRate            = new int [NUM_ENVELOPE_STAGES];
    final int []                  filterStop            = new int [NUM_ENVELOPE_STAGES];
    int                           lfoDelay;
    int                           lfoWaveform;
    int                           lfoAttack             = 1;
    int                           lfoRate;
    int                           lfoPitchDepth;
    int                           lfoAmpDepth;
    int                           lfoFilterDepth;
    int                           lfoResonanceDepth;
    int                           velocityResonance;
    int                           ampKeyFollow;
    int                           ampRateScaling;
    int                           filterKeyFollow;
    int                           filterRateScaling;
    int                           velocityAmpDepth;
    int                           velocityAmpRate;
    int                           velocityFilterDepth;
    int                           velocityFilterRate;
    int                           highKey               = 127;
    int                           lowKey                = 0;
    int                           centerKey             = 60;
    int                           sampleRateIndex       = 0;
    String                        name                  = "";


    /**
     * Read the voice parameters.
     *
     * @param data The data to read from
     * @param offset The offset of the first byte of the voice
     * @throws IOException The data is malformed
     */
    public void read (final byte [] data, final int offset) throws IOException
    {
        if (offset + SIZE > data.length)
            throw new IOException ("Voice data is too short.");

        this.waveStart = readUnsigned32 (data, offset + 0x00);
        this.waveEnd = readUnsigned32 (data, offset + 0x04);
        this.generatorStart = readUnsigned32 (data, offset + 0x08);
        this.generatorEnd = readUnsigned32 (data, offset + 0x0C);
        this.mode = readUnsigned16 (data, offset + 0x10);
        this.loopSustain = data[offset + 0x12] & 0xFF;
        this.loopEnd = data[offset + 0x13] & 0xFF;
        for (int i = 0; i < NUM_LOOPS; i++)
        {
            // The top 8 bits contain the fractional loop start
            final long value = readUnsigned32 (data, offset + 0x14 + i * 4);
            this.loopStart[i] = value & 0xFFFFFF;
            this.loopFine[i] = (int) (value >> 24 & 0xFF);
            // The top bit contains the loop pattern: 1 for Skip, 0 for Trace
            final long endValue = readUnsigned32 (data, offset + 0x34 + i * 4);
            this.loopEndAddress[i] = endValue & 0x7FFFFFFFL;
            this.loopSkip[i] = (endValue & 0x80000000L) > 0;
            this.loopCrossfade[i] = readUnsigned16 (data, offset + 0x54 + i * 2);
            this.loopTime[i] = readUnsigned16 (data, offset + 0x64 + i * 2);
        }
        this.pitch = (short) readUnsigned16 (data, offset + 0x74);
        this.cutoff = data[offset + 0x76] & 0xFF;
        this.resonance = data[offset + 0x77] & 0xFF;
        this.ampSustainPoint = data[offset + 0x78] & 0xFF;
        this.ampEndPoint = data[offset + 0x79] & 0xFF;
        for (int i = 0; i < NUM_ENVELOPE_STAGES; i++)
        {
            this.ampRate[i] = data[offset + 0x7A + i] & 0xFF;
            this.ampStop[i] = data[offset + 0x82 + i] & 0xFF;
            this.filterRate[i] = data[offset + 0x8C + i] & 0xFF;
            this.filterStop[i] = data[offset + 0x94 + i] & 0xFF;
        }
        this.filterSustainPoint = data[offset + 0x8A] & 0xFF;
        this.filterEndPoint = data[offset + 0x8B] & 0xFF;
        this.lfoDelay = readUnsigned16 (data, offset + 0x9C);
        this.lfoWaveform = data[offset + 0x9E] & 0xFF;
        this.lfoAttack = data[offset + 0x9F] & 0xFF;
        this.lfoRate = data[offset + 0xA0] & 0xFF;
        this.lfoPitchDepth = data[offset + 0xA1] & 0xFF;
        this.lfoAmpDepth = data[offset + 0xA2] & 0xFF;
        this.lfoFilterDepth = data[offset + 0xA3] & 0xFF;
        this.lfoResonanceDepth = data[offset + 0xA4] & 0xFF;
        this.velocityResonance = data[offset + 0xA5];
        this.ampKeyFollow = data[offset + 0xA6];
        this.ampRateScaling = data[offset + 0xA7];
        this.filterKeyFollow = data[offset + 0xA8];
        this.filterRateScaling = data[offset + 0xA9];
        this.velocityAmpDepth = data[offset + 0xAA];
        this.velocityAmpRate = data[offset + 0xAB];
        this.velocityFilterDepth = data[offset + 0xAC];
        this.velocityFilterRate = data[offset + 0xAD];
        this.highKey = data[offset + 0xAE] & 0xFF;
        this.lowKey = data[offset + 0xAF] & 0xFF;
        this.centerKey = data[offset + 0xB0] & 0xFF;
        this.sampleRateIndex = data[offset + 0xB1] & 0xFF;
        this.name = readName (data, offset + 0xB2);
    }


    /**
     * Write the voice parameters.
     *
     * @param data The data to write to
     * @param offset The offset of the first byte of the voice
     */
    public void write (final byte [] data, final int offset)
    {
        writeUnsigned32 (data, offset + 0x00, this.waveStart);
        writeUnsigned32 (data, offset + 0x04, this.waveEnd);
        writeUnsigned32 (data, offset + 0x08, this.generatorStart);
        writeUnsigned32 (data, offset + 0x0C, this.generatorEnd);
        writeUnsigned16 (data, offset + 0x10, this.mode);
        data[offset + 0x12] = (byte) this.loopSustain;
        data[offset + 0x13] = (byte) this.loopEnd;
        for (int i = 0; i < NUM_LOOPS; i++)
        {
            writeUnsigned32 (data, offset + 0x14 + i * 4, this.loopStart[i] & 0xFFFFFF | (long) (this.loopFine[i] & 0xFF) << 24);
            writeUnsigned32 (data, offset + 0x34 + i * 4, this.loopEndAddress[i] & 0x7FFFFFFFL | (this.loopSkip[i] ? 0x80000000L : 0));
            writeUnsigned16 (data, offset + 0x54 + i * 2, this.loopCrossfade[i]);
            writeUnsigned16 (data, offset + 0x64 + i * 2, this.loopTime[i]);
        }
        writeUnsigned16 (data, offset + 0x74, this.pitch & 0xFFFF);
        data[offset + 0x76] = (byte) this.cutoff;
        data[offset + 0x77] = (byte) this.resonance;
        data[offset + 0x78] = (byte) this.ampSustainPoint;
        data[offset + 0x79] = (byte) this.ampEndPoint;
        for (int i = 0; i < NUM_ENVELOPE_STAGES; i++)
        {
            data[offset + 0x7A + i] = (byte) this.ampRate[i];
            data[offset + 0x82 + i] = (byte) this.ampStop[i];
            data[offset + 0x8C + i] = (byte) this.filterRate[i];
            data[offset + 0x94 + i] = (byte) this.filterStop[i];
        }
        data[offset + 0x8A] = (byte) this.filterSustainPoint;
        data[offset + 0x8B] = (byte) this.filterEndPoint;
        writeUnsigned16 (data, offset + 0x9C, this.lfoDelay);
        data[offset + 0x9E] = (byte) this.lfoWaveform;
        data[offset + 0x9F] = (byte) this.lfoAttack;
        data[offset + 0xA0] = (byte) this.lfoRate;
        data[offset + 0xA1] = (byte) this.lfoPitchDepth;
        data[offset + 0xA2] = (byte) this.lfoAmpDepth;
        data[offset + 0xA3] = (byte) this.lfoFilterDepth;
        data[offset + 0xA4] = (byte) this.lfoResonanceDepth;
        data[offset + 0xA5] = (byte) this.velocityResonance;
        data[offset + 0xA6] = (byte) this.ampKeyFollow;
        data[offset + 0xA7] = (byte) this.ampRateScaling;
        data[offset + 0xA8] = (byte) this.filterKeyFollow;
        data[offset + 0xA9] = (byte) this.filterRateScaling;
        data[offset + 0xAA] = (byte) this.velocityAmpDepth;
        data[offset + 0xAB] = (byte) this.velocityAmpRate;
        data[offset + 0xAC] = (byte) this.velocityFilterDepth;
        data[offset + 0xAD] = (byte) this.velocityFilterRate;
        data[offset + 0xAE] = (byte) this.highKey;
        data[offset + 0xAF] = (byte) this.lowKey;
        data[offset + 0xB0] = (byte) this.centerKey;
        data[offset + 0xB1] = (byte) this.sampleRateIndex;
        writeName (data, offset + 0xB2, this.name);
    }


    /**
     * Get the sample rate of the voice.
     *
     * @return The sample rate in Hz
     */
    public int getSampleRate ()
    {
        return SAMPLE_RATES[Math.clamp (this.sampleRateIndex, 0, SAMPLE_RATES.length - 1)];
    }


    /**
     * Calculate the duration of one envelope stage. The rate value describes a slope. The
     * documentation does not specify its law, therefore it is approximated: a full swing across the
     * whole level range takes 60 seconds at rate 0 and the time halves with every 8 rate steps (~1
     * millisecond at rate 127). The stage duration scales with the level distance to travel.
     *
     * @param rate The rate of the stage, only the lower 7 bits are used
     * @param levelDelta The level distance of the stage (0-255)
     * @return The approximated duration in seconds
     */
    public static double stageSeconds (final int rate, final int levelDelta)
    {
        final int increment = ENVELOPE_INCREMENT[rate & 0x7F];
        // An increment of zero never reaches the level of the stage, the envelope stops there
        if (increment == 0)
            return LONGEST_STAGE_SECONDS;
        return levelDelta * ENVELOPE_LEVEL_STEP / (increment * ENVELOPE_SERVICE_RATE);
    }


    /**
     * Calculate the rate of an envelope stage from its duration. This is the inverse of
     * {@link #stageSeconds(int, int)}.
     *
     * @param seconds The duration of the stage in seconds
     * @param levelDelta The level distance of the stage (0-255)
     * @return The rate (1-127)
     */
    public static int secondsToRate (final double seconds, final int levelDelta)
    {
        if (seconds <= 0 || levelDelta <= 0)
            return 127;
        // The increments do not follow a formula, therefore the rate whose duration comes closest
        // is searched in the table
        int bestRate = 1;
        double bestDistance = Double.MAX_VALUE;
        for (int rate = 1; rate < ENVELOPE_INCREMENT.length; rate++)
        {
            final double distance = Math.abs (stageSeconds (rate, levelDelta) - seconds);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestRate = rate;
            }
        }
        return bestRate;
    }


    /**
     * Read a name with 12 ASCII characters followed by 2 zero bytes.
     *
     * @param data The data to read from
     * @param offset The offset of the first character
     * @return The trimmed name
     */
    static String readName (final byte [] data, final int offset)
    {
        return new String (data, offset, 12, StandardCharsets.US_ASCII).trim ();
    }


    /**
     * Write a name with 12 ASCII characters followed by 2 zero bytes.
     *
     * @param data The data to write to
     * @param offset The offset of the first character
     * @param name The name to write, blanks are filled with spaces
     */
    static void writeName (final byte [] data, final int offset, final String name)
    {
        final byte [] chars = name.getBytes (StandardCharsets.US_ASCII);
        for (int i = 0; i < 12; i++)
            data[offset + i] = i < chars.length ? chars[i] : 0x20;
        data[offset + 12] = 0;
        data[offset + 13] = 0;
    }


    static int readUnsigned16 (final byte [] data, final int offset)
    {
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
    }


    static void writeUnsigned16 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
    }


    static long readUnsigned32 (final byte [] data, final int offset)
    {
        return data[offset] & 0xFFL | (data[offset + 1] & 0xFFL) << 8 | (data[offset + 2] & 0xFFL) << 16 | (data[offset + 3] & 0xFFL) << 24;
    }


    static void writeUnsigned32 (final byte [] data, final int offset, final long value)
    {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) (value >> 16);
        data[offset + 3] = (byte) (value >> 24);
    }
}
