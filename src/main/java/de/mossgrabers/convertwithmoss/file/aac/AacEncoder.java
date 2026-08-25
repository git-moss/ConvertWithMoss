// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aac;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Encoder for MPEG-4 AAC Low Complexity (AAC-LC) audio data. The structure follows the AAC encoder
 * of the FFmpeg project (libavcodec/aacenc, LGPL 2.1 or later): window switching with one frame of
 * look-ahead, forward MDCT, per band scalefactor selection, codebook selection and bitstream
 * assembly. Instead of the 3GPP psychoacoustic model, the scalefactors are chosen for a fixed
 * quantization precision per band, which trades bit rate for simplicity. Mono and stereo with
 * 16-bit input are supported; stereo is coded as a channel pair without mid/side coding.
 *
 * @author Jürgen Moßgraber
 */
public class AacEncoder
{
    /** The number of samples of a full frame. */
    public static final int     FRAME_LENGTH         = 1024;

    /** The number of priming samples (the delay of the MDCT framing). */
    public static final int     PRIMING_FRAMES       = FRAME_LENGTH;

    private static final int    ONLY_LONG_SEQUENCE   = 0;
    private static final int    LONG_START_SEQUENCE  = 1;
    private static final int    EIGHT_SHORT_SEQUENCE = 2;
    private static final int    LONG_STOP_SEQUENCE   = 3;

    /** The upper bound for the quantized value of the loudest coefficient of a band. */
    private static final double QUANT_TARGET         = 40;

    /** The maximum number of bits of one frame per channel (the AAC buffer limit). */
    private static final int    MAX_FRAME_BITS       = 6144;

    private static final int [] SAMPLE_RATES         =
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

    private final int           sampleRate;
    private final int           sampleRateIndex;
    private final int           numChannels;

    private static float [] []  mdctLong             = null;
    private static float [] []  mdctShort            = null;

    private final float []      sineLong             = new float [1024];
    private final float []      sineShort            = new float [128];


    /**
     * Constructor.
     *
     * @param sampleRate The sample rate in Hz
     * @param numChannels The number of channels (1 or 2)
     * @throws IOException The configuration is not supported
     */
    public AacEncoder (final int sampleRate, final int numChannels) throws IOException
    {
        if (numChannels < 1 || numChannels > 2)
            throw new IOException ("Unsupported number of AAC channels: " + numChannels);

        this.sampleRate = sampleRate;
        this.numChannels = numChannels;

        int index = -1;
        for (int i = 0; i < SAMPLE_RATES.length; i++)
            if (SAMPLE_RATES[i] == sampleRate)
                index = i;
        if (index < 0)
        {
            // Escape coded rate: use the closest index for the tables
            index = 4;
            for (int i = 0; i < SAMPLE_RATES.length; i++)
                if (Math.abs (SAMPLE_RATES[i] - sampleRate) < Math.abs (SAMPLE_RATES[index] - sampleRate))
                    index = i;
        }
        this.sampleRateIndex = index;

        for (int i = 0; i < 1024; i++)
            this.sineLong[i] = (float) Math.sin ((i + 0.5) * Math.PI / 2048.0);
        for (int i = 0; i < 128; i++)
            this.sineShort[i] = (float) Math.sin ((i + 0.5) * Math.PI / 256.0);
    }


    /**
     * Get the magic cookie (an MPEG-4 esds descriptor with the AudioSpecificConfig) which the
     * decoder needs, e.g. for the 'kuki' chunk of a CAF file.
     *
     * @return The cookie
     */
    public byte [] getMagicCookie ()
    {
        // The AudioSpecificConfig: object type 2 (LC), the frequency (by index or escape coded)
        // and the channel configuration
        final ByteArrayOutputStream ascOut = new ByteArrayOutputStream ();
        final BitWriter asc = new BitWriter (new byte [8]);
        asc.write (2, 5);
        final boolean escapeRate = SAMPLE_RATES[this.sampleRateIndex] != this.sampleRate;
        if (escapeRate)
        {
            asc.write (15, 4);
            asc.write (this.sampleRate, 24);
        }
        else
            asc.write (this.sampleRateIndex, 4);
        asc.write (this.numChannels, 4);
        // GASpecificConfig: 1024 frame length, no core coder, no extension
        asc.write (0, 3);
        asc.byteAlign ();
        ascOut.write (asc.getBuffer (), 0, asc.getPosition () / 8);
        final byte [] ascBytes = ascOut.toByteArray ();

        // The esds descriptor chain: ES -> DecoderConfig -> DecoderSpecificInfo, plus the
        // SLConfig descriptor. The lengths are written in the 4-byte long form like Apple does,
        // which some parsers rely on.
        final int decoderSpecificLength = 5 + ascBytes.length;
        final int decoderConfigLength = 5 + 13 + decoderSpecificLength;
        final int esLength = 3 + decoderConfigLength + 5 + 1;

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.write (0x03);
        writeDescriptorLength (out, esLength);
        // ES ID and flags
        out.write (0);
        out.write (0);
        out.write (0);
        out.write (0x04);
        writeDescriptorLength (out, 13 + decoderSpecificLength);
        // Object type indication: MPEG-4 audio
        out.write (0x40);
        // Stream type: audio
        out.write (0x15);
        // Buffer size and bit rates (unknown)
        for (int i = 0; i < 11; i++)
            out.write (0);
        out.write (0x05);
        writeDescriptorLength (out, ascBytes.length);
        out.write (ascBytes, 0, ascBytes.length);
        // The SLConfig descriptor
        out.write (0x06);
        writeDescriptorLength (out, 1);
        out.write (0x02);
        return out.toByteArray ();
    }


    private static void writeDescriptorLength (final ByteArrayOutputStream out, final int length)
    {
        out.write (0x80);
        out.write (0x80);
        out.write (0x80);
        out.write (length & 0x7F);
    }


    /**
     * Encode interleaved 16-bit little-endian samples into AAC packets. The first packet contains
     * {@link #PRIMING_FRAMES} priming samples.
     *
     * @param input The samples
     * @param inputOffset The offset in bytes of the first sample frame
     * @param totalFrames The number of sample frames
     * @return The encoded packets
     * @throws IOException Could not encode the data
     */
    public List<byte []> encode (final byte [] input, final int inputOffset, final int totalFrames) throws IOException
    {
        final int numPackets = (PRIMING_FRAMES + totalFrames + FRAME_LENGTH - 1) / FRAME_LENGTH;

        // Extract the channels with the priming padding in front
        final float [] [] samples = new float [this.numChannels] [numPackets * FRAME_LENGTH + FRAME_LENGTH];
        for (int frame = 0; frame < totalFrames; frame++)
            for (int channel = 0; channel < this.numChannels; channel++)
            {
                final int offset = inputOffset + (frame * this.numChannels + channel) * 2;
                samples[channel][PRIMING_FRAMES + frame] = (short) (input[offset] & 0xFF | input[offset + 1] << 8);
            }

        // Decide the window sequence of every frame with one frame of look-ahead: a frame with
        // an energy attack gets eight short windows, its neighbours become the start and stop
        // transitions
        final boolean [] isShort = new boolean [numPackets];
        for (int packet = 0; packet < numPackets; packet++)
            isShort[packet] = this.detectAttack (samples, packet);
        final int [] sequence = new int [numPackets];
        for (int packet = 0; packet < numPackets; packet++)
            if (isShort[packet])
                sequence[packet] = EIGHT_SHORT_SEQUENCE;
            else
            {
                final boolean nextShort = packet + 1 < numPackets && isShort[packet + 1];
                final boolean previousShort = packet > 0 && isShort[packet - 1];
                if (nextShort)
                    sequence[packet] = LONG_START_SEQUENCE;
                else if (previousShort)
                    sequence[packet] = LONG_STOP_SEQUENCE;
                else
                    sequence[packet] = ONLY_LONG_SEQUENCE;
            }

        final List<byte []> packets = new ArrayList<> (numPackets);
        final int maxBytes = MAX_FRAME_BITS / 8 * this.numChannels;
        for (int packet = 0; packet < numPackets; packet++)
        {
            // Re-encode with coarser quantization until the frame fits the AAC buffer limit
            byte [] encoded = this.encodeFrame (samples, packet, sequence[packet], 0);
            for (int boost = 3; encoded.length > maxBytes && boost <= 120; boost += 3)
                encoded = this.encodeFrame (samples, packet, sequence[packet], boost);
            packets.add (encoded);
        }
        return packets;
    }


    /**
     * Detect an energy attack inside the second half of the frame window, which requires short
     * transforms to avoid pre-echo.
     *
     * @param samples The channel samples
     * @param packet The packet index
     * @return True if the frame should use eight short windows
     */
    private boolean detectAttack (final float [] [] samples, final int packet)
    {
        final int start = packet * FRAME_LENGTH;
        double previousEnergy = 0;
        boolean attack = false;
        for (int block = 0; block < 16; block++)
        {
            double energy = 0;
            for (int channel = 0; channel < this.numChannels; channel++)
            {
                final float [] data = samples[channel];
                for (int i = 0; i < 128; i++)
                {
                    final float value = data[start + block * 128 + i];
                    energy += value * value;
                }
            }
            if (block > 0 && energy > 1000 && energy > previousEnergy * 16)
                attack = true;
            previousEnergy = Math.max (previousEnergy, energy);
        }
        return attack;
    }


    /**
     * Encode one frame with all its channel elements.
     *
     * @param samples The channel samples
     * @param packet The packet index
     * @param windowSequence The window sequence of the frame
     * @param scalefactorBoost The boost factor
     * @return The encoded packet
     */
    private byte [] encodeFrame (final float [] [] samples, final int packet, final int windowSequence, final int scalefactorBoost)
    {
        final BitWriter bits = new BitWriter (new byte [16384]);

        if (this.numChannels == 2)
        {
            // A channel pair element with a common window and without mid/side coding
            bits.write (1, 3);
            bits.write (0, 4);
            bits.write (1, 1);
            this.writeIcsInfo (bits, windowSequence);
            // No mid/side coding
            bits.write (0, 2);
            this.encodeChannel (bits, samples[0], packet, windowSequence, false, scalefactorBoost);
            this.encodeChannel (bits, samples[1], packet, windowSequence, false, scalefactorBoost);
        }
        else
        {
            bits.write (0, 3);
            bits.write (0, 4);
            this.encodeChannel (bits, samples[0], packet, windowSequence, true, scalefactorBoost);
        }

        // The end element, then byte-align
        bits.write (7, 3);
        bits.byteAlign ();
        return Arrays.copyOf (bits.getBuffer (), bits.getPosition () / 8);
    }


    private void writeIcsInfo (final BitWriter bits, final int windowSequence)
    {
        bits.write (0, 1);
        bits.write (windowSequence, 2);
        // Sine window shape
        bits.write (0, 1);
        if (windowSequence == EIGHT_SHORT_SEQUENCE)
        {
            bits.write (this.getMaxSfb (windowSequence), 4);
            // Every window is its own group
            bits.write (0, 7);
        }
        else
        {
            bits.write (this.getMaxSfb (windowSequence), 6);
            // No predictor data
            bits.write (0, 1);
        }
    }


    private int getMaxSfb (final int windowSequence)
    {
        return windowSequence == EIGHT_SHORT_SEQUENCE ? AacTables.NUM_SWB_128[this.sampleRateIndex] : AacTables.NUM_SWB_1024[this.sampleRateIndex];
    }


    /**
     * Encode one individual channel stream.
     *
     * @param bits The bit writer
     * @param samples The samples of the channel
     * @param packet The packet index
     * @param windowSequence The window sequence
     * @param writeIcsInfo True to write the ICS info (mono element)
     * @param scalefactorBoost Added to all scale factors to reduce the frame size
     */
    private void encodeChannel (final BitWriter bits, final float [] samples, final int packet, final int windowSequence, final boolean writeIcsInfo, final int scalefactorBoost)
    {
        final boolean isShort = windowSequence == EIGHT_SHORT_SEQUENCE;
        final int numWindows = isShort ? 8 : 1;
        final int maxSfb = this.getMaxSfb (windowSequence);
        final int [] swbOffset = isShort ? AacTables.SWB_OFFSET_128[this.sampleRateIndex] : AacTables.SWB_OFFSET_1024[this.sampleRateIndex];

        // The forward MDCT of the frame
        final float [] coefficients = this.transform (samples, packet, windowSequence);

        // Select a scalefactor per band which bounds the quantized value of its loudest
        // coefficient, then enforce the maximum scalefactor distance of 60 between coded bands
        final int numBands = numWindows * maxSfb;
        final int [] scalefactors = new int [numBands];
        final int [] [] quantized = new int [numBands] [];
        final int [] bandType = new int [numBands];
        final boolean [] active = new boolean [numBands];
        for (int window = 0; window < numWindows; window++)
            for (int band = 0; band < maxSfb; band++)
            {
                final int index = window * maxSfb + band;
                final int start = window * 128 + swbOffset[band];
                final int end = window * 128 + swbOffset[band + 1];
                float maxValue = 0;
                for (int k = start; k < end; k++)
                    maxValue = Math.max (maxValue, Math.abs (coefficients[k]));
                if (maxValue < 0.01f)
                    continue;
                active[index] = true;
                scalefactors[index] = Math.clamp ((int) Math.ceil (100 + 4 * Math.log (maxValue / Math.pow (QUANT_TARGET, 4.0 / 3.0)) / Math.log (2)) + (long) scalefactorBoost, 0, 255);
            }

        int previous = -1;
        for (int i = 0; i < numBands; i++)
            if (active[i])
            {
                if (previous >= 0)
                    scalefactors[i] = Math.clamp (scalefactors[i], previous - 60, previous + 60);
                previous = scalefactors[i];
            }

        // Quantize with the final scalefactors
        for (int window = 0; window < numWindows; window++)
            for (int band = 0; band < maxSfb; band++)
            {
                final int index = window * maxSfb + band;
                if (!active[index])
                    continue;
                final int start = window * 128 + swbOffset[band];
                final int end = window * 128 + swbOffset[band + 1];

                final double gain = Math.pow (2, -0.25 * (scalefactors[index] - 100));
                final int [] values = new int [end - start];
                int maxQuant = 0;
                for (int k = start; k < end; k++)
                {
                    final int quant = (int) Math.min (Math.floor (Math.pow (Math.abs (coefficients[k]) * gain, 0.75) + 0.4054), 8191);
                    values[k - start] = coefficients[k] < 0 ? -quant : quant;
                    maxQuant = Math.max (maxQuant, quant);
                }

                if (maxQuant == 0)
                    continue;

                quantized[index] = values;
                bandType[index] = selectCodebook (maxQuant);
            }

        final int globalGain = firstScalefactor (scalefactors, bandType);
        bits.write (globalGain, 8);

        if (writeIcsInfo)
            this.writeIcsInfo (bits, windowSequence);

        // The section data: run-length coded band types
        final int lengthBits = isShort ? 3 : 5;
        final int escape = (1 << lengthBits) - 1;
        for (int window = 0; window < numWindows; window++)
        {
            int band = 0;
            while (band < maxSfb)
            {
                final int type = bandType[window * maxSfb + band];
                int run = 1;
                while (band + run < maxSfb && bandType[window * maxSfb + band + run] == type)
                    run++;
                bits.write (type, 4);
                int remaining = run;
                while (remaining >= escape)
                {
                    bits.write (escape, lengthBits);
                    remaining -= escape;
                }
                bits.write (remaining, lengthBits);
                band += run;
            }
        }

        // The scalefactor data as DPCM against the previous coded band
        int scalefactor = globalGain;
        for (int i = 0; i < numBands; i++)
            if (bandType[i] != 0)
            {
                final int delta = scalefactors[i] - scalefactor + 60;
                bits.write (AacTables.SCALEFACTOR_CODES[delta], AacTables.SCALEFACTOR_BITS[delta]);
                scalefactor = scalefactors[i];
            }

        // No pulse data, no TNS, no gain control
        bits.write (0, 1);
        bits.write (0, 1);
        bits.write (0, 1);

        // The spectral data
        for (int i = 0; i < numBands; i++)
        {
            if (bandType[i] == 0)
                continue;
            encodeSpectralBand (bits, bandType[i], quantized[i]);
        }
    }


    private static int firstScalefactor (final int [] scalefactors, final int [] bandType)
    {
        for (int i = 0; i < bandType.length; i++)
            if (bandType[i] != 0)
                return scalefactors[i];
        return 100;
    }


    private static int selectCodebook (final int maxQuant)
    {
        if (maxQuant <= 1)
            return 1;
        if (maxQuant <= 2)
            return 3;
        if (maxQuant <= 4)
            return 5;
        if (maxQuant <= 7)
            return 7;
        if (maxQuant <= 12)
            return 9;
        return 11;
    }


    /**
     * Encode the quantized values of one band with its codebook.
     *
     * @param bits The bit writer
     * @param codebook The codebook (1-11)
     * @param values The quantized values
     */
    private static void encodeSpectralBand (final BitWriter bits, final int codebook, final int [] values)
    {
        final int [] codes = AacTables.SPECTRAL_CODES[codebook - 1];
        final int [] lengths = AacTables.SPECTRAL_BITS[codebook - 1];

        if (codebook < 5)
        {
            // Quads
            for (int k = 0; k < values.length; k += 4)
            {
                final int index;
                if (codebook < 3)
                    index = (values[k] + 1) * 27 + (values[k + 1] + 1) * 9 + (values[k + 2] + 1) * 3 + values[k + 3] + 1;
                else
                    index = Math.abs (values[k]) * 27 + Math.abs (values[k + 1]) * 9 + Math.abs (values[k + 2]) * 3 + Math.abs (values[k + 3]);
                bits.write (codes[index], lengths[index]);
                if (codebook >= 3)
                    for (int j = 0; j < 4; j++)
                        if (values[k + j] != 0)
                            bits.write (values[k + j] < 0 ? 1 : 0, 1);
            }
            return;
        }

        // Pairs
        for (int k = 0; k < values.length; k += 2)
        {
            final int value0 = values[k];
            final int value1 = values[k + 1];
            final int index;
            switch (codebook)
            {
                case 5, 6:
                    index = (value0 + 4) * 9 + value1 + 4;
                    break;
                case 7, 8:
                    index = Math.abs (value0) * 8 + Math.abs (value1);
                    break;
                case 9, 10:
                    index = Math.abs (value0) * 13 + Math.abs (value1);
                    break;
                default:
                    index = Math.min (Math.abs (value0), 16) * 17 + Math.min (Math.abs (value1), 16);
                    break;
            }
            bits.write (codes[index], lengths[index]);
            if (codebook >= 7)
            {
                if (value0 != 0)
                    bits.write (value0 < 0 ? 1 : 0, 1);
                if (value1 != 0)
                    bits.write (value1 < 0 ? 1 : 0, 1);
            }
            if (codebook == 11)
            {
                if (Math.abs (value0) >= 16)
                    writeEscape (bits, Math.abs (value0));
                if (Math.abs (value1) >= 16)
                    writeEscape (bits, Math.abs (value1));
            }
        }
    }


    private static void writeEscape (final BitWriter bits, final int value)
    {
        final int numBits = 31 - Integer.numberOfLeadingZeros (value);
        for (int i = 0; i < numBits - 4; i++)
            bits.write (1, 1);
        bits.write (0, 1);
        bits.write (value - (1 << numBits), numBits);
    }


    /**
     * Apply the windows and run the forward MDCT of one frame.
     *
     * @param samples The samples of the channel (with the priming padding in front)
     * @param packet The packet index
     * @param windowSequence The window sequence
     * @return The 1024 spectral coefficients (eight blocks of 128 for short windows)
     */
    private float [] transform (final float [] samples, final int packet, final int windowSequence)
    {
        // The frame transforms the window around the packet: the second half of the previous
        // frame period and the packet itself
        final int windowStart = packet * FRAME_LENGTH;
        final float [] window = new float [2048];
        for (int i = 0; i < 2048 && windowStart + i < samples.length; i++)
            window[i] = samples[windowStart + i];

        final float [] coefficients = new float [1024];

        if (windowSequence == EIGHT_SHORT_SEQUENCE)
        {
            final float [] block = new float [256];
            for (int subWindow = 0; subWindow < 8; subWindow++)
            {
                for (int i = 0; i < 256; i++)
                    block[i] = window[448 + subWindow * 128 + i] * (i < 128 ? this.sineShort[i] : this.sineShort[255 - i]);
                mdctForward (block, coefficients, subWindow * 128, 128);
            }
            return coefficients;
        }

        // Window the first half
        if (windowSequence == LONG_STOP_SEQUENCE)
        {
            for (int i = 0; i < 448; i++)
                window[i] = 0;
            for (int i = 0; i < 128; i++)
                window[448 + i] *= this.sineShort[i];
        }
        else
            for (int i = 0; i < 1024; i++)
                window[i] *= this.sineLong[i];

        // Window the second half
        if (windowSequence == LONG_START_SEQUENCE)
        {
            for (int i = 0; i < 128; i++)
                window[1024 + 448 + i] *= this.sineShort[127 - i];
            for (int i = 1024 + 448 + 128; i < 2048; i++)
                window[i] = 0;
        }
        else
            for (int i = 0; i < 1024; i++)
                window[1024 + i] *= this.sineLong[1023 - i];

        mdctForward (window, coefficients, 0, 1024);
        return coefficients;
    }


    private static void mdctForward (final float [] input, final float [] output, final int outputOffset, final int n2)
    {
        final float [] [] matrix = getMdctMatrix (n2);
        final int n = n2 * 2;
        for (int k = 0; k < n2; k++)
        {
            final float [] row = matrix[k];
            float sum = 0;
            for (int sample = 0; sample < n; sample++)
                sum += input[sample] * row[sample];
            output[outputOffset + k] = sum;
        }
    }


    private static synchronized float [] [] getMdctMatrix (final int n2)
    {
        if (n2 == 1024)
        {
            if (mdctLong == null)
                mdctLong = createMdctMatrix (1024);
            return mdctLong;
        }
        if (mdctShort == null)
            mdctShort = createMdctMatrix (128);
        return mdctShort;
    }


    private static float [] [] createMdctMatrix (final int n2)
    {
        final int n = n2 * 2;
        final float [] [] matrix = new float [n2] [n];
        final double n0 = (n2 + 1.0) / 2.0;
        for (int k = 0; k < n2; k++)
            for (int sample = 0; sample < n; sample++)
                matrix[k][sample] = (float) (2.0 * Math.cos (2.0 * Math.PI / n * (sample + n0) * (k + 0.5)));
        return matrix;
    }


    /** A big-endian (most significant bit first) bit stream writer. */
    private static final class BitWriter
    {
        private final byte [] buffer;
        private int           position = 0;


        BitWriter (final byte [] buffer)
        {
            this.buffer = buffer;
        }


        void write (final int value, final int numBits)
        {
            final long bits = numBits == 32 ? value & 0xFFFFFFFFL : value & (1L << numBits) - 1;
            int remaining = numBits;
            while (remaining > 0)
            {
                final int byteIndex = this.position >>> 3;
                final int space = 8 - (this.position & 7);
                final int take = Math.min (space, remaining);
                final int chunk = (int) (bits >>> remaining - take) & (1 << take) - 1;
                this.buffer[byteIndex] |= (byte) (chunk << space - take);
                this.position += take;
                remaining -= take;
            }
        }


        void byteAlign ()
        {
            final int bitIndex = this.position & 7;
            if (bitIndex != 0)
                this.position += 8 - bitIndex;
        }


        int getPosition ()
        {
            return this.position;
        }


        byte [] getBuffer ()
        {
            return this.buffer;
        }
    }
}
