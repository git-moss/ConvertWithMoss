// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aac;

import java.io.IOException;
import java.util.Arrays;


/**
 * Decoder for MPEG-4 AAC Low Complexity (AAC-LC) audio data. This is a port of the AAC decoder of
 * the FFmpeg project (libavcodec/aac, LGPL 2.1 or later) reduced to the low complexity profile with
 * 1024 samples per frame and up to two channels. High efficiency extensions (SBR, PS) and the other
 * audio object types are not supported.
 *
 * @author Jürgen Moßgraber
 */
public class AacDecoder
{
    /** The number of samples of a full frame. */
    public static final int       FRAME_LENGTH         = 1024;

    private static final int      ID_SCE               = 0;
    private static final int      ID_CPE               = 1;
    @SuppressWarnings("unused")
    private static final int      ID_CCE               = 2;
    private static final int      ID_LFE               = 3;
    private static final int      ID_DSE               = 4;
    private static final int      ID_PCE               = 5;
    private static final int      ID_FIL               = 6;
    private static final int      ID_END               = 7;

    @SuppressWarnings("unused")
    private static final int      ONLY_LONG_SEQUENCE   = 0;
    private static final int      LONG_START_SEQUENCE  = 1;
    private static final int      EIGHT_SHORT_SEQUENCE = 2;
    private static final int      LONG_STOP_SEQUENCE   = 3;

    private static final int      BAND_TYPE_ZERO       = 0;
    private static final int      BAND_TYPE_FIRST_PAIR = 5;
    private static final int      BAND_TYPE_ESC        = 11;
    private static final int      BAND_TYPE_NOISE      = 13;
    private static final int      BAND_TYPE_INTENSITY2 = 14;
    private static final int      BAND_TYPE_INTENSITY  = 15;

    /** The sample rates of the sampling frequency index. */
    private static final int []   SAMPLE_RATES         =
    {
        96000,
        88200,
        64000,
        48000,
        44100,
        32000,
        24000,
        22050,
        16000,
        12000,
        11025,
        8000,
        7350
    };

    private final int             sampleRate;
    private final int             sampleRateIndex;
    private final int             numChannels;

    private final HuffmanTable    scalefactorTable;
    private final HuffmanTable [] spectralTables;

    private final float []        kbdLong              = new float [1024];
    private final float []        kbdShort             = new float [128];
    private final float []        sineLong             = new float [1024];
    private final float []        sineShort            = new float [128];

    // The decoding state of up to two channels
    private final Channel []      channels;
    private int                   randomState          = 0x1F2E3D4C;

    // The inverse MDCT as precomputed cosine matrices: exact, and fast enough for material of
    // sample length
    private static float [] []    imdctLong            = null;
    private static float [] []    imdctShort           = null;


    /** The per channel decoding state. */
    private static final class Channel
    {
        final float []       coefficients    = new float [FRAME_LENGTH];
        final float []       output          = new float [FRAME_LENGTH];
        final float []       overlap         = new float [FRAME_LENGTH];
        final int []         bandType        = new int [120];
        final int []         scalefactors    = new int [120];
        // The ICS info
        int                  windowSequence;
        int                  windowShape;
        int                  previousWindowShape;
        int                  maxSfb;
        int                  numWindows;
        int                  numWindowGroups;
        final int []         groupLen        = new int [8];
        // TNS
        boolean              tnsPresent;
        final int []         tnsNFilt        = new int [8];
        final int [] []      tnsLength       = new int [8] [4];
        final int [] []      tnsOrder        = new int [8] [4];
        final int [] []      tnsDirection    = new int [8] [4];
        final float [] [] [] tnsCoefficients = new float [8] [4] [20];
    }


    /**
     * Constructor. Parses the AudioSpecificConfig, optionally wrapped in an MPEG-4 esds descriptor
     * (the magic cookie of CAF files).
     *
     * @param magicCookie The AudioSpecificConfig or esds descriptor
     * @throws IOException The configuration is not supported
     */
    public AacDecoder (final byte [] magicCookie) throws IOException
    {
        final byte [] asc = extractAudioSpecificConfig (magicCookie);
        final BitReader bits = new BitReader (asc, asc.length);

        int audioObjectType = bits.read (5);
        if (audioObjectType == 31)
            audioObjectType = 32 + bits.read (6);

        int frequencyIndex = bits.read (4);
        int rate = 0;
        if (frequencyIndex == 15)
            rate = bits.read (24);
        else if (frequencyIndex < SAMPLE_RATES.length)
            rate = SAMPLE_RATES[frequencyIndex];

        final int channelConfiguration = bits.read (4);

        // Only the low complexity profile is supported (no SBR/PS which are the object types 5
        // and 29)
        if (audioObjectType != 2)
            throw new IOException ("Unsupported AAC audio object type: " + audioObjectType);
        if (channelConfiguration < 1 || channelConfiguration > 2)
            throw new IOException ("Unsupported number of AAC channels: " + channelConfiguration);

        // GASpecificConfig
        final int frameLengthFlag = bits.read (1);
        if (frameLengthFlag != 0)
            throw new IOException ("Unsupported AAC frame length (960).");
        if (bits.read (1) != 0)
            bits.read (14);
        bits.read (1);

        if (frequencyIndex == 15)
        {
            // Find the closest table index for an escape coded sample rate
            frequencyIndex = 4;
            for (int i = 0; i < SAMPLE_RATES.length; i++)
                if (Math.abs (SAMPLE_RATES[i] - rate) < Math.abs (SAMPLE_RATES[frequencyIndex] - rate))
                    frequencyIndex = i;
        }
        else
            rate = SAMPLE_RATES[frequencyIndex];

        this.sampleRate = rate;
        this.sampleRateIndex = frequencyIndex;
        this.numChannels = channelConfiguration;

        this.scalefactorTable = new HuffmanTable (AacTables.SCALEFACTOR_CODES, AacTables.SCALEFACTOR_BITS);
        this.spectralTables = new HuffmanTable [11];
        for (int i = 0; i < 11; i++)
            this.spectralTables[i] = new HuffmanTable (AacTables.SPECTRAL_CODES[i], AacTables.SPECTRAL_BITS[i]);

        kbdWindow (this.kbdLong, 4.0);
        kbdWindow (this.kbdShort, 6.0);
        for (int i = 0; i < 1024; i++)
            this.sineLong[i] = (float) Math.sin ((i + 0.5) * Math.PI / 2048.0);
        for (int i = 0; i < 128; i++)
            this.sineShort[i] = (float) Math.sin ((i + 0.5) * Math.PI / 256.0);

        this.channels = new Channel []
        {
            new Channel (),
            new Channel ()
        };
    }


    /**
     * Get the sample rate.
     *
     * @return The sample rate in Hz
     */
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * Get the number of audio channels.
     *
     * @return The number of channels
     */
    public int getNumberOfChannels ()
    {
        return this.numChannels;
    }


    /**
     * Decode one packet (a raw data block with 1024 sample frames).
     *
     * @param packetData The data which contains the packet
     * @param packetOffset The offset of the first byte of the packet
     * @param packetLength The number of bytes of the packet
     * @param output Where to write the decoded interleaved 16-bit little-endian samples
     * @param outputOffset The offset in bytes at which to write into the output buffer
     * @return The number of decoded sample frames
     * @throws IOException The packet is malformed
     */
    public int decodePacket (final byte [] packetData, final int packetOffset, final int packetLength, final byte [] output, final int outputOffset) throws IOException
    {
        final byte [] padded = new byte [packetLength + 8];
        System.arraycopy (packetData, packetOffset, padded, 0, packetLength);
        final BitReader bits = new BitReader (padded, packetLength);

        int channelIndex = 0;
        boolean done = false;
        while (!done)
        {
            final int elementType = bits.read (3);
            switch (elementType)
            {
                case ID_SCE, ID_LFE:
                    // The element instance tag
                    bits.read (4);
                    if (channelIndex >= this.numChannels)
                        throw new IOException ("Too many channel elements in AAC packet.");
                    this.decodeIcs (bits, this.channels[channelIndex], false, false);
                    this.applyTnsAndTransform (this.channels[channelIndex]);
                    channelIndex++;
                    break;

                case ID_CPE:
                    bits.read (4);
                    if (channelIndex + 2 > this.numChannels)
                        throw new IOException ("Too many channel elements in AAC packet.");
                    this.decodeChannelPair (bits, this.channels[channelIndex], this.channels[channelIndex + 1]);
                    this.applyTnsAndTransform (this.channels[channelIndex]);
                    this.applyTnsAndTransform (this.channels[channelIndex + 1]);
                    channelIndex += 2;
                    break;

                case ID_DSE:
                    bits.read (4);
                    final int alignFlag = bits.read (1);
                    int count = bits.read (8);
                    if (count == 255)
                        count += bits.read (8);
                    if (alignFlag != 0)
                        bits.byteAlign ();
                    bits.skip (count * 8);
                    break;

                case ID_PCE:
                    skipProgramConfigElement (bits);
                    break;

                case ID_FIL:
                    int fillCount = bits.read (4);
                    if (fillCount == 15)
                        fillCount += bits.read (8) - 1;
                    // The payload may contain SBR data which is not decoded
                    bits.skip (fillCount * 8);
                    break;

                case ID_END:
                    done = true;
                    break;

                default:
                    throw new IOException ("Unsupported element in AAC packet: " + elementType);
            }

            if (channelIndex >= this.numChannels && !done)
                // All channels are decoded, the frame should end now
                done = bits.remaining () < 3 || bits.peek (3) == ID_END;
        }

        // Interleave the output samples as 16-bit
        for (int channel = 0; channel < this.numChannels; channel++)
        {
            final float [] samples = this.channels[channel].output;
            for (int i = 0; i < FRAME_LENGTH; i++)
            {
                final int value = Math.clamp (Math.round (samples[i] * 32768f), Short.MIN_VALUE, Short.MAX_VALUE);
                final int offset = outputOffset + (i * this.numChannels + channel) * 2;
                output[offset] = (byte) value;
                output[offset + 1] = (byte) (value >> 8);
            }
        }

        return FRAME_LENGTH;
    }


    /**
     * Decode a channel pair element with its optional common window and mid/side coding.
     *
     * @param bits The bit reader
     * @param left The left channel
     * @param right The right channel
     * @throws IOException The data is malformed
     */
    private void decodeChannelPair (final BitReader bits, final Channel left, final Channel right) throws IOException
    {
        final boolean commonWindow = bits.read (1) != 0;
        final boolean [] msUsed = new boolean [120];
        if (commonWindow)
        {
            this.decodeIcsInfo (bits, left);
            copyIcsInfo (left, right);

            final int msPresent = bits.read (2);
            switch (msPresent)
            {
                case 3:
                    throw new IOException ("Malformed AAC channel pair.");
                case 2:
                    Arrays.fill (msUsed, 0, left.numWindowGroups * left.maxSfb, true);
                    break;
                case 1:
                    for (int i = 0; i < left.numWindowGroups * left.maxSfb; i++)
                        msUsed[i] = bits.read (1) != 0;
                    break;
                default:
                    break;
            }
        }

        this.decodeIcs (bits, left, commonWindow, false);
        this.decodeIcs (bits, right, commonWindow, true);

        if (commonWindow)
            this.applyMidSideAndIntensity (left, right, msUsed);
    }


    /**
     * Decode an individual channel stream.
     *
     * @param bits The bit reader
     * @param channel The channel state to fill
     * @param commonWindow True if the ICS info was already read from the channel pair
     * @param isSecondChannel True if this is the second channel of a pair
     * @throws IOException The data is malformed
     */
    private void decodeIcs (final BitReader bits, final Channel channel, final boolean commonWindow, final boolean isSecondChannel) throws IOException
    {
        final int globalGain = bits.read (8);

        if (!commonWindow)
            this.decodeIcsInfo (bits, channel);

        final int [] swbOffset = this.getSwbOffsets (channel);

        decodeSectionData (bits, channel);
        this.decodeScalefactors (bits, channel, globalGain);

        // Pulse data is only allowed with long windows
        final int [] pulseAmp = new int [4];
        final int [] pulsePosition = new int [4];
        int numPulse = 0;
        if (bits.read (1) != 0)
        {
            if (channel.windowSequence == EIGHT_SHORT_SEQUENCE)
                throw new IOException ("Malformed AAC frame: pulse data with short windows.");
            numPulse = bits.read (2) + 1;
            final int startSfb = bits.read (6);
            int position = swbOffset[Math.min (startSfb, channel.maxSfb)];
            for (int i = 0; i < numPulse; i++)
            {
                position += bits.read (5);
                pulsePosition[i] = position;
                pulseAmp[i] = bits.read (4);
            }
        }

        channel.tnsPresent = bits.read (1) != 0;
        if (channel.tnsPresent)
            decodeTns (bits, channel);

        if (bits.read (1) != 0)
            throw new IOException ("Unsupported AAC gain control data (SSR).");

        this.decodeSpectrum (bits, channel, swbOffset);

        // Apply the pulses on the quantized values, then dequantize
        for (int i = 0; i < numPulse; i++)
        {
            final int position = pulsePosition[i];
            if (position < FRAME_LENGTH)
            {
                final float coefficient = channel.coefficients[position];
                channel.coefficients[position] = coefficient >= 0 ? coefficient + pulseAmp[i] : coefficient - pulseAmp[i];
            }
        }

        this.dequantize (channel, swbOffset, isSecondChannel);
    }


    private void decodeIcsInfo (final BitReader bits, final Channel channel) throws IOException
    {
        // The reserved bit
        bits.read (1);
        channel.windowSequence = bits.read (2);
        channel.previousWindowShape = channel.windowShape;
        channel.windowShape = bits.read (1);

        if (channel.windowSequence == EIGHT_SHORT_SEQUENCE)
        {
            channel.maxSfb = bits.read (4);
            channel.numWindows = 8;
            final int grouping = bits.read (7);
            channel.numWindowGroups = 1;
            channel.groupLen[0] = 1;
            for (int i = 0; i < 7; i++)
                if ((grouping & 1 << 6 - i) != 0)
                    channel.groupLen[channel.numWindowGroups - 1]++;
                else
                {
                    channel.numWindowGroups++;
                    channel.groupLen[channel.numWindowGroups - 1] = 1;
                }
            if (channel.maxSfb > AacTables.NUM_SWB_128[this.sampleRateIndex])
                throw new IOException ("Malformed AAC frame: too many scalefactor bands.");
        }
        else
        {
            channel.maxSfb = bits.read (6);
            channel.numWindows = 1;
            channel.numWindowGroups = 1;
            channel.groupLen[0] = 1;
            if (bits.read (1) != 0)
                throw new IOException ("Unsupported AAC predictor data (main profile).");
            if (channel.maxSfb > AacTables.NUM_SWB_1024[this.sampleRateIndex])
                throw new IOException ("Malformed AAC frame: too many scalefactor bands.");
        }
    }


    private static void copyIcsInfo (final Channel from, final Channel to)
    {
        to.windowSequence = from.windowSequence;
        to.previousWindowShape = to.windowShape;
        to.windowShape = from.windowShape;
        to.maxSfb = from.maxSfb;
        to.numWindows = from.numWindows;
        to.numWindowGroups = from.numWindowGroups;
        System.arraycopy (from.groupLen, 0, to.groupLen, 0, from.groupLen.length);
    }


    private static void decodeSectionData (final BitReader bits, final Channel channel) throws IOException
    {
        final int bitsPerLength = channel.windowSequence == EIGHT_SHORT_SEQUENCE ? 3 : 5;
        final int escape = (1 << bitsPerLength) - 1;

        for (int group = 0; group < channel.numWindowGroups; group++)
        {
            int band = 0;
            while (band < channel.maxSfb)
            {
                final int bandType = bits.read (4);
                if (bandType == 12)
                    throw new IOException ("Malformed AAC frame: invalid band type.");
                int runLength = 0;
                int increment;
                do
                {
                    increment = bits.read (bitsPerLength);
                    runLength += increment;
                } while (increment == escape);
                if (band + runLength > channel.maxSfb)
                    throw new IOException ("Malformed AAC frame: section data out of range.");
                for (int i = 0; i < runLength; i++)
                    channel.bandType[group * channel.maxSfb + band + i] = bandType;
                band += runLength;
            }
        }
    }


    private void decodeScalefactors (final BitReader bits, final Channel channel, final int globalGain) throws IOException
    {
        int scalefactor = globalGain;
        int intensityPosition = 0;
        int noiseEnergy = globalGain - 90;
        boolean noisePresent = false;

        for (int group = 0; group < channel.numWindowGroups; group++)
            for (int band = 0; band < channel.maxSfb; band++)
            {
                final int index = group * channel.maxSfb + band;
                switch (channel.bandType[index])
                {
                    case BAND_TYPE_ZERO:
                        channel.scalefactors[index] = 0;
                        break;

                    case BAND_TYPE_INTENSITY, BAND_TYPE_INTENSITY2:
                        intensityPosition += this.scalefactorTable.decode (bits) - 60;
                        channel.scalefactors[index] = intensityPosition;
                        break;

                    case BAND_TYPE_NOISE:
                        if (noisePresent)
                            noiseEnergy += this.scalefactorTable.decode (bits) - 60;
                        else
                        {
                            noiseEnergy += bits.read (9) - 256;
                            noisePresent = true;
                        }
                        channel.scalefactors[index] = noiseEnergy;
                        break;

                    default:
                        scalefactor += this.scalefactorTable.decode (bits) - 60;
                        if (scalefactor < 0 || scalefactor > 255)
                            throw new IOException ("Malformed AAC frame: scalefactor out of range.");
                        channel.scalefactors[index] = scalefactor;
                        break;
                }
            }
    }


    private static void decodeTns (final BitReader bits, final Channel channel) throws IOException
    {
        final boolean isShort = channel.windowSequence == EIGHT_SHORT_SEQUENCE;
        for (int window = 0; window < channel.numWindows; window++)
        {
            channel.tnsNFilt[window] = bits.read (isShort ? 1 : 2);
            if (channel.tnsNFilt[window] == 0)
                continue;
            final int coefficientResolution = bits.read (1);
            for (int filter = 0; filter < channel.tnsNFilt[window]; filter++)
            {
                channel.tnsLength[window][filter] = bits.read (isShort ? 4 : 6);
                channel.tnsOrder[window][filter] = bits.read (isShort ? 3 : 5);
                if (channel.tnsOrder[window][filter] > (isShort ? 7 : 12))
                    throw new IOException ("Malformed AAC frame: TNS order too high.");
                if (channel.tnsOrder[window][filter] > 0)
                {
                    channel.tnsDirection[window][filter] = bits.read (1);
                    final int coefficientCompress = bits.read (1);
                    final int coefficientLength = coefficientResolution + 3 - coefficientCompress;
                    final float [] tmp2Map = AacTables.TNS_TMP2_MAP[2 * coefficientCompress + coefficientResolution];

                    // Decode the coefficients and convert them to the LPC filter; the reflection
                    // coefficients are negated like in compute_lpc_coefs of FFmpeg
                    final float [] coefficients = new float [20];
                    for (int i = 0; i < channel.tnsOrder[window][filter]; i++)
                        coefficients[i] = tmp2Map[bits.read (coefficientLength)];

                    final float [] lpc = channel.tnsCoefficients[window][filter];
                    for (int m = 0; m < channel.tnsOrder[window][filter]; m++)
                    {
                        final float reflection = -coefficients[m];
                        lpc[m] = reflection;
                        for (int j = 0; j < m + 1 >> 1; j++)
                        {
                            final float forward = lpc[j];
                            final float backward = lpc[m - 1 - j];
                            lpc[j] = forward + reflection * backward;
                            lpc[m - 1 - j] = backward + reflection * forward;
                        }
                    }
                }
            }
        }
    }


    private void decodeSpectrum (final BitReader bits, final Channel channel, final int [] swbOffset) throws IOException
    {
        Arrays.fill (channel.coefficients, 0);

        final boolean isShort = channel.windowSequence == EIGHT_SHORT_SEQUENCE;
        int windowStart = 0;
        for (int group = 0; group < channel.numWindowGroups; group++)
        {
            final int groupLength = channel.groupLen[group];
            for (int band = 0; band < channel.maxSfb; band++)
            {
                final int bandType = channel.bandType[group * channel.maxSfb + band];
                if (bandType == BAND_TYPE_ZERO || bandType >= BAND_TYPE_NOISE)
                    continue;

                final int start = swbOffset[band];
                final int end = swbOffset[band + 1];
                final HuffmanTable table = this.spectralTables[bandType - 1];
                final boolean signed = bandType == 1 || bandType == 2 || bandType == 5 || bandType == 6;

                for (int windowInGroup = 0; windowInGroup < groupLength; windowInGroup++)
                {
                    final int coefficientBase = (windowStart + windowInGroup) * (isShort ? 128 : 0);
                    if (bandType < BAND_TYPE_FIRST_PAIR)
                        // Quads
                        for (int k = start; k < end; k += 4)
                        {
                            final int index = table.decode (bits);
                            final int [] quad = unpackQuad (bandType, index);
                            for (int j = 0; j < 4; j++)
                            {
                                int value = quad[j];
                                if (!signed && value != 0 && bits.read (1) != 0)
                                    value = -value;
                                channel.coefficients[coefficientBase + k + j] = value;
                            }
                        }
                    else
                        // Pairs; for the escape codebook all sign bits are read first, then the
                        // escape values
                        for (int k = start; k < end; k += 2)
                        {
                            final int index = table.decode (bits);
                            final int [] pair = unpackPair (bandType, index);
                            for (int j = 0; j < 2; j++)
                                if (!signed && pair[j] != 0 && bits.read (1) != 0)
                                    pair[j] = -pair[j];
                            for (int j = 0; j < 2; j++)
                            {
                                int value = pair[j];
                                if (bandType == BAND_TYPE_ESC && Math.abs (value) == 16)
                                {
                                    // The escape value: N one bits, a zero and then N+4 bits
                                    int prefix = 4;
                                    while (bits.read (1) != 0)
                                        prefix++;
                                    if (prefix > 21)
                                        throw new IOException ("Malformed AAC frame: invalid escape value.");
                                    final int escapeValue = (1 << prefix) + bits.read (prefix);
                                    value = value < 0 ? -escapeValue : escapeValue;
                                }
                                channel.coefficients[coefficientBase + k + j] = value;
                            }
                        }
                }
            }
            windowStart += groupLength;
        }
    }


    /**
     * Dequantize the decoded values and fill the noise bands. The spectrum is scaled so that the
     * final output samples are in the range of -1 to 1.
     *
     * @param channel The channel
     * @param swbOffset The scalefactor band offsets
     * @param isSecondChannel True if this is the second channel of a pair whose intensity bands are
     *            filled later
     */
    private void dequantize (final Channel channel, final int [] swbOffset, final boolean isSecondChannel)
    {
        final boolean isShort = channel.windowSequence == EIGHT_SHORT_SEQUENCE;
        int windowStart = 0;
        for (int group = 0; group < channel.numWindowGroups; group++)
        {
            final int groupLength = channel.groupLen[group];
            for (int band = 0; band < channel.maxSfb; band++)
            {
                final int index = group * channel.maxSfb + band;
                final int bandType = channel.bandType[index];
                final int start = swbOffset[band];
                final int end = swbOffset[band + 1];

                for (int windowInGroup = 0; windowInGroup < groupLength; windowInGroup++)
                {
                    final int coefficientBase = (windowStart + windowInGroup) * (isShort ? 128 : 0);

                    if (bandType == BAND_TYPE_NOISE)
                    {
                        // Perceptual noise substitution: fill the band with scaled random noise
                        float energy = 0;
                        final float [] noise = new float [end - start];
                        for (int k = 0; k < noise.length; k++)
                        {
                            this.randomState = this.randomState * 1664525 + 1013904223;
                            noise[k] = this.randomState;
                            energy += noise[k] * noise[k];
                        }
                        final float scale = (float) (Math.pow (2, 0.25 * (channel.scalefactors[index] - 100)) / Math.sqrt (energy / noise.length) / 32768.0);
                        for (int k = 0; k < noise.length; k++)
                            channel.coefficients[coefficientBase + start + k] = noise[k] * scale;
                        continue;
                    }

                    if (bandType >= BAND_TYPE_INTENSITY2 || bandType == BAND_TYPE_ZERO)
                    {
                        if (!isSecondChannel || bandType == BAND_TYPE_ZERO)
                            for (int k = start; k < end; k++)
                                channel.coefficients[coefficientBase + k] = 0;
                        continue;
                    }

                    final float gain = (float) (Math.pow (2, 0.25 * (channel.scalefactors[index] - 100)) / 32768.0);
                    for (int k = start; k < end; k++)
                    {
                        final float value = channel.coefficients[coefficientBase + k];
                        channel.coefficients[coefficientBase + k] = Math.signum (value) * (float) Math.pow (Math.abs (value), 4.0 / 3.0) * gain;
                    }
                }
            }
            windowStart += groupLength;
        }
    }


    /**
     * Apply mid/side decoding and intensity stereo to a channel pair with a common window.
     *
     * @param left The left channel
     * @param right The right channel
     * @param msUsed The mid/side flags per group and band
     */
    private void applyMidSideAndIntensity (final Channel left, final Channel right, final boolean [] msUsed)
    {
        final int [] swbOffset = this.getSwbOffsets (left);
        final boolean isShort = left.windowSequence == EIGHT_SHORT_SEQUENCE;

        int windowStart = 0;
        for (int group = 0; group < left.numWindowGroups; group++)
        {
            final int groupLength = left.groupLen[group];
            for (int band = 0; band < left.maxSfb; band++)
            {
                final int index = group * left.maxSfb + band;
                final int rightType = right.bandType[index];
                final int start = swbOffset[band];
                final int end = swbOffset[band + 1];

                for (int windowInGroup = 0; windowInGroup < groupLength; windowInGroup++)
                {
                    final int coefficientBase = (windowStart + windowInGroup) * (isShort ? 128 : 0);

                    if (rightType == BAND_TYPE_INTENSITY || rightType == BAND_TYPE_INTENSITY2)
                    {
                        // Intensity stereo: derive the right channel from the left one
                        float scale = (float) Math.pow (0.5, 0.25 * right.scalefactors[index]);
                        final boolean invert = rightType == BAND_TYPE_INTENSITY2 ^ msUsed[index];
                        if (invert)
                            scale = -scale;
                        for (int k = start; k < end; k++)
                            right.coefficients[coefficientBase + k] = left.coefficients[coefficientBase + k] * scale;
                        continue;
                    }

                    if (msUsed[index] && left.bandType[index] < BAND_TYPE_NOISE && rightType < BAND_TYPE_NOISE)
                        for (int k = start; k < end; k++)
                        {
                            final float mid = left.coefficients[coefficientBase + k];
                            final float side = right.coefficients[coefficientBase + k];
                            left.coefficients[coefficientBase + k] = mid + side;
                            right.coefficients[coefficientBase + k] = mid - side;
                        }
                }
            }
            windowStart += groupLength;
        }
    }


    /**
     * Apply the temporal noise shaping filters and run the inverse transform with windowing and
     * overlap-add.
     *
     * @param channel The channel
     */
    private void applyTnsAndTransform (final Channel channel)
    {
        if (channel.tnsPresent)
            this.applyTns (channel);
        this.imdctAndWindow (channel);
    }


    /**
     * Apply the temporal noise shaping filters on the spectrum. This is a port of the TNS filter of
     * the FFmpeg AAC decoder.
     *
     * @param channel The channel
     */
    private void applyTns (final Channel channel)
    {
        final boolean isShort = channel.windowSequence == EIGHT_SHORT_SEQUENCE;
        final int [] swbOffset = this.getSwbOffsets (channel);
        final int numSwb = isShort ? AacTables.NUM_SWB_128[this.sampleRateIndex] : AacTables.NUM_SWB_1024[this.sampleRateIndex];
        final int maxTnsSfb = Math.min (isShort ? AacTables.TNS_MAX_BANDS_128[this.sampleRateIndex] : AacTables.TNS_MAX_BANDS_1024[this.sampleRateIndex], channel.maxSfb);

        for (int window = 0; window < channel.numWindows; window++)
        {
            int bottom = numSwb;
            for (int filter = 0; filter < channel.tnsNFilt[window]; filter++)
            {
                final int top = bottom;
                bottom = Math.max (0, top - channel.tnsLength[window][filter]);
                final int order = channel.tnsOrder[window][filter];
                if (order == 0)
                    continue;

                final int start = swbOffset[Math.min (bottom, maxTnsSfb)] + window * (isShort ? 128 : 0);
                final int end = swbOffset[Math.min (top, maxTnsSfb)] + window * (isShort ? 128 : 0);
                if (end <= start)
                    continue;

                final float [] lpc = channel.tnsCoefficients[window][filter];
                if (channel.tnsDirection[window][filter] != 0)
                    // Backwards
                    for (int m = end - 1; m >= start; m--)
                        for (int i = 1; i <= Math.min (order, end - 1 - m); i++)
                            channel.coefficients[m] -= channel.coefficients[m + i] * lpc[i - 1];
                else
                    for (int m = start; m < end; m++)
                        for (int i = 1; i <= Math.min (order, m - start); i++)
                            channel.coefficients[m] -= channel.coefficients[m - i] * lpc[i - 1];
            }
        }
    }


    /**
     * Run the inverse MDCT on the spectrum, apply the windows and overlap-add into the output
     * buffer. This is a port of the imdct_and_windowing function of the FFmpeg AAC decoder.
     *
     * @param channel The channel
     */
    private void imdctAndWindow (final Channel channel)
    {
        final float [] buffer = new float [2048];

        if (channel.windowSequence == EIGHT_SHORT_SEQUENCE)
            for (int window = 0; window < 8; window++)
            {
                final float [] temp = imdct (channel.coefficients, window * 128, 128);
                System.arraycopy (temp, 0, buffer, window * 256, 256);
                // Each short transform is windowed on both sides with the short window; the
                // shape of the previous frame only matters for the very first one
                final float [] windowLeft = window == 0 ? this.getWindow (channel.previousWindowShape, 128) : this.getWindow (channel.windowShape, 128);
                final float [] windowRight = this.getWindow (channel.windowShape, 128);
                for (int i = 0; i < 128; i++)
                {
                    buffer[window * 256 + i] *= windowLeft[i];
                    buffer[window * 256 + 128 + i] *= windowRight[127 - i];
                }
            }
        else
        {
            final float [] temp = imdct (channel.coefficients, 0, 1024);
            System.arraycopy (temp, 0, buffer, 0, 2048);

            // Window the first half
            final float [] windowLeft = this.getWindow (channel.previousWindowShape, channel.windowSequence == LONG_STOP_SEQUENCE ? 128 : 1024);
            if (channel.windowSequence == LONG_STOP_SEQUENCE)
            {
                for (int i = 0; i < 448; i++)
                    buffer[i] = 0;
                for (int i = 0; i < 128; i++)
                    buffer[448 + i] *= windowLeft[i];
            }
            else
                for (int i = 0; i < 1024; i++)
                    buffer[i] *= windowLeft[i];

            // Window the second half
            final float [] windowRight = this.getWindow (channel.windowShape, channel.windowSequence == LONG_START_SEQUENCE ? 128 : 1024);
            if (channel.windowSequence == LONG_START_SEQUENCE)
            {
                for (int i = 0; i < 128; i++)
                    buffer[1024 + 448 + i] *= windowRight[127 - i];
                for (int i = 1024 + 448 + 128; i < 2048; i++)
                    buffer[i] = 0;
            }
            else
                for (int i = 0; i < 1024; i++)
                    buffer[1024 + i] *= windowRight[1023 - i];
        }

        // For the eight short windows the transforms are overlap-added among themselves; the
        // combined result behaves like one long transform aligned at an offset of 448
        final float [] combined = new float [2048];
        if (channel.windowSequence == EIGHT_SHORT_SEQUENCE)
            for (int window = 0; window < 8; window++)
                for (int i = 0; i < 256; i++)
                    combined[448 + window * 128 + i] += buffer[window * 256 + i];
        else
            System.arraycopy (buffer, 0, combined, 0, 2048);

        // Overlap-add with the second half of the previous frame
        for (int i = 0; i < FRAME_LENGTH; i++)
            channel.output[i] = channel.overlap[i] + combined[i];
        System.arraycopy (combined, FRAME_LENGTH, channel.overlap, 0, FRAME_LENGTH);
    }


    private float [] getWindow (final int shape, final int length)
    {
        if (length == 128)
            return shape == 1 ? this.kbdShort : this.sineShort;
        return shape == 1 ? this.kbdLong : this.sineLong;
    }


    /**
     * The inverse MDCT: transforms N/2 spectral coefficients into N time samples with the direct
     * formula of the MPEG-4 standard, using a precomputed cosine matrix. This is exact and fast
     * enough for audio material of sample length.
     *
     * @param input The spectral coefficients
     * @param inputOffset The offset of the first coefficient
     * @param n2 The number of coefficients (N/2)
     * @return The N time samples
     */
    private static float [] imdct (final float [] input, final int inputOffset, final int n2)
    {
        final float [] [] matrix = getImdctMatrix (n2);
        final int n = n2 * 2;
        final float [] output = new float [n];
        for (int sample = 0; sample < n; sample++)
        {
            final float [] row = matrix[sample];
            float sum = 0;
            for (int k = 0; k < n2; k++)
                sum += input[inputOffset + k] * row[k];
            output[sample] = sum;
        }
        return output;
    }


    private static synchronized float [] [] getImdctMatrix (final int n2)
    {
        if (n2 == 1024)
        {
            if (imdctLong == null)
                imdctLong = createImdctMatrix (1024);
            return imdctLong;
        }
        if (imdctShort == null)
            imdctShort = createImdctMatrix (128);
        return imdctShort;
    }


    private static float [] [] createImdctMatrix (final int n2)
    {
        final int n = n2 * 2;
        final float [] [] matrix = new float [n] [n2];
        final double n0 = (n2 + 1.0) / 2.0;
        for (int sample = 0; sample < n; sample++)
            for (int k = 0; k < n2; k++)
                matrix[sample][k] = (float) (2.0 / n * Math.cos (2.0 * Math.PI / n * (sample + n0) * (k + 0.5)));
        return matrix;
    }


    private int [] getSwbOffsets (final Channel channel)
    {
        return channel.windowSequence == EIGHT_SHORT_SEQUENCE ? AacTables.SWB_OFFSET_128[this.sampleRateIndex] : AacTables.SWB_OFFSET_1024[this.sampleRateIndex];
    }


    private static int [] unpackQuad (final int bandType, final int index)
    {
        final int [] result = new int [4];
        if (bandType == 1 || bandType == 2)
        {
            // Signed with values -1..1
            result[0] = index / 27 % 3 - 1;
            result[1] = index / 9 % 3 - 1;
            result[2] = index / 3 % 3 - 1;
            result[3] = index % 3 - 1;
        }
        else
        {
            // Unsigned with values 0..2
            result[0] = index / 27 % 3;
            result[1] = index / 9 % 3;
            result[2] = index / 3 % 3;
            result[3] = index % 3;
        }
        return result;
    }


    private static int [] unpackPair (final int bandType, final int index)
    {
        final int [] result = new int [2];
        switch (bandType)
        {
            case 5, 6:
                // Signed with values -4..4
                result[0] = index / 9 - 4;
                result[1] = index % 9 - 4;
                break;
            case 7, 8:
                // Unsigned with values 0..7
                result[0] = index / 8;
                result[1] = index % 8;
                break;
            case 9, 10:
                // Unsigned with values 0..12
                result[0] = index / 13;
                result[1] = index % 13;
                break;
            default:
                // The escape codebook, unsigned with values 0..16
                result[0] = index / 17;
                result[1] = index % 17;
                break;
        }
        return result;
    }


    private static void skipProgramConfigElement (final BitReader bits)
    {
        // element_instance_tag, object_type, sampling_frequency_index
        bits.read (4);
        bits.read (2);
        bits.read (4);
        final int numFront = bits.read (4);
        final int numSide = bits.read (4);
        final int numBack = bits.read (4);
        final int numLfe = bits.read (2);
        final int numData = bits.read (3);
        final int numCoupling = bits.read (4);
        if (bits.read (1) != 0)
            bits.read (4);
        if (bits.read (1) != 0)
            bits.read (4);
        if (bits.read (1) != 0)
            bits.read (3);
        for (int i = 0; i < numFront + numSide + numBack; i++)
            bits.read (5);
        for (int i = 0; i < numLfe + numData; i++)
            bits.read (4);
        for (int i = 0; i < numCoupling; i++)
            bits.read (5);
        bits.byteAlign ();
        final int commentSize = bits.read (8);
        bits.skip (commentSize * 8);
    }


    /**
     * Extract the AudioSpecificConfig from a magic cookie. CAF files store the full MPEG-4 esds
     * descriptor chain, the config is the payload of the DecoderSpecificInfo descriptor (tag 5).
     *
     * @param magicCookie The cookie data
     * @return The AudioSpecificConfig
     * @throws IOException No configuration found
     */
    private static byte [] extractAudioSpecificConfig (final byte [] magicCookie) throws IOException
    {
        // Walk the descriptor chain and dig into the ES (3) and DecoderConfig (4) descriptors
        int position = 0;
        while (position + 2 <= magicCookie.length)
        {
            final int tag = magicCookie[position] & 0xFF;
            position++;
            int length = 0;
            int lengthBytes = 0;
            while (position < magicCookie.length && lengthBytes < 4)
            {
                final int value = magicCookie[position] & 0xFF;
                position++;
                lengthBytes++;
                length = length << 7 | value & 0x7F;
                if ((value & 0x80) == 0)
                    break;
            }

            switch (tag)
            {
                case 0x03:
                    // The ES descriptor: skip the ES ID and the flags, then continue with the
                    // contained descriptors
                    if (position + 3 > magicCookie.length)
                        throw new IOException ("Malformed AAC magic cookie.");
                    final int flags = magicCookie[position + 2] & 0xFF;
                    position += 3;
                    if ((flags & 0x80) != 0)
                        position += 2;
                    if ((flags & 0x40) != 0)
                        position += 1 + (magicCookie[position] & 0xFF);
                    if ((flags & 0x20) != 0)
                        position += 2;
                    break;

                case 0x04:
                    // The DecoderConfig descriptor: skip the object type, stream type, buffer
                    // size and bit rates, then continue with the contained descriptors
                    position += 13;
                    break;

                case 0x05:
                    // The DecoderSpecificInfo descriptor contains the AudioSpecificConfig
                    if (position + length > magicCookie.length)
                        throw new IOException ("Malformed AAC magic cookie.");
                    return Arrays.copyOfRange (magicCookie, position, position + length);

                default:
                    position += length;
                    break;
            }
        }

        // Not an esds structure, assume it is the plain AudioSpecificConfig
        if (magicCookie.length >= 2)
            return magicCookie;
        throw new IOException ("Malformed AAC magic cookie.");
    }


    /**
     * Fill the rising half of a Kaiser-Bessel derived window as defined in the MPEG-4 standard.
     * This is a port of kbd_window_init of the FFmpeg project.
     *
     * @param window The window to fill with its first half
     * @param alpha The alpha parameter of the window
     */
    private static void kbdWindow (final float [] window, final double alpha)
    {
        final int n = window.length;
        final double [] temp = new double [n / 2 + 1];
        final double alpha2 = 4 * (alpha * Math.PI / n) * (alpha * Math.PI / n);

        double scale = 0;
        for (int i = 0; i <= n / 2; i++)
        {
            temp[i] = bessel (Math.sqrt (i * (double) (n - i) * alpha2));
            scale += temp[i] * (i > 0 && i < n / 2.0 ? 2 : 1);
        }
        scale = 1.0 / (scale + 1);

        double sum = 0;
        int i = 0;
        for (; i <= n / 2; i++)
        {
            sum += temp[i];
            window[i] = (float) Math.sqrt (sum * scale);
        }
        for (; i < n; i++)
        {
            sum += temp[n - i];
            window[i] = (float) Math.sqrt (sum * scale);
        }
    }


    private static double bessel (final double x)
    {
        // The zeroth order modified Bessel function of the first kind
        double sum = 1;
        double term = 1;
        for (int i = 1; i < 50; i++)
        {
            term *= x * x / (4.0 * i * i);
            sum += term;
            if (term < 1e-21 * sum)
                break;
        }
        return sum;
    }


    /** A canonical Huffman decoding table. */
    static final class HuffmanTable
    {
        // Symbol lookup by (length, code)
        private final int [] [] symbols;
        private final int       maxLength;


        HuffmanTable (final int [] codes, final int [] bits)
        {
            int max = 0;
            for (final int bit: bits)
                max = Math.max (max, bit);
            this.maxLength = max;
            this.symbols = new int [max + 1] [];
            for (int length = 1; length <= max; length++)
            {
                final int [] table = new int [1 << length];
                Arrays.fill (table, -1);
                this.symbols[length] = table;
            }
            for (int symbol = 0; symbol < codes.length; symbol++)
                this.symbols[bits[symbol]][codes[symbol]] = symbol;
        }


        int decode (final BitReader bits) throws IOException
        {
            int code = 0;
            for (int length = 1; length <= this.maxLength; length++)
            {
                code = code << 1 | bits.read (1);
                final int symbol = this.symbols[length][code];
                if (symbol >= 0)
                    return symbol;
            }
            throw new IOException ("Malformed AAC frame: invalid Huffman code.");
        }
    }


    /** A big-endian (most significant bit first) bit stream reader. */
    static final class BitReader
    {
        private final byte [] data;
        private final int     endPosition;
        private int           position;


        BitReader (final byte [] data, final int validLength)
        {
            this.data = data;
            this.endPosition = validLength * 8;
        }


        int read (final int numBits)
        {
            int result = 0;
            int remaining = numBits;
            while (remaining > 0)
            {
                final int byteIndex = this.position >>> 3;
                final int bitIndex = this.position & 7;
                final int available = 8 - bitIndex;
                final int take = Math.min (available, remaining);
                final int chunk = (this.data[byteIndex] & 0xFF) >> available - take & (1 << take) - 1;
                result = result << take | chunk;
                this.position += take;
                remaining -= take;
            }
            return result;
        }


        int peek (final int numBits)
        {
            final int savedPosition = this.position;
            final int result = this.read (numBits);
            this.position = savedPosition;
            return result;
        }


        void skip (final int numBits)
        {
            this.position += numBits;
        }


        void byteAlign ()
        {
            final int bitIndex = this.position & 7;
            if (bitIndex != 0)
                this.position += 8 - bitIndex;
        }


        int remaining ()
        {
            return this.endPosition - this.position;
        }
    }
}
