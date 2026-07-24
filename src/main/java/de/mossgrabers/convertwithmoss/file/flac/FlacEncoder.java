// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.flac;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Encoder for the Free Lossless Audio Codec (FLAC) format as specified in RFC 9639. Writes a fixed
 * block size stream using constant, verbatim, fixed prediction and linear prediction (LPC)
 * sub-frames with Rice coded residuals; stereo blocks additionally choose the smallest of the
 * independent and de-correlated (left/side, side/right, mid/side) channel codings. Only features of
 * the original FLAC specification are emitted (4-bit Rice parameters, no escape codes), therefore
 * the output is readable by old decoders as well. All block sizes are handled correctly, including
 * a trailing block which is shorter than the prediction order.
 *
 * @author Jürgen Moßgraber
 */
public class FlacEncoder
{
    private static final int    BLOCK_SIZE          = 4096;
    private static final int    MAX_FIXED_ORDER     = 4;
    private static final int    MAX_LPC_ORDER       = 8;
    private static final int    LPC_PRECISION       = 14;
    private static final int    MAX_RICE_PARAMETER  = 14;
    private static final int    MAX_PARTITION_ORDER = 6;
    private static final int    PADDING_LENGTH      = 40;

    private static final int [] CRC8_TABLE          = new int [256];
    private static final int [] CRC16_TABLE         = new int [256];

    static
    {
        for (int i = 0; i < 256; i++)
        {
            int crc8 = i;
            int crc16 = i << 8;
            for (int bit = 0; bit < 8; bit++)
            {
                crc8 = (crc8 & 0x80) != 0 ? crc8 << 1 ^ 0x07 : crc8 << 1;
                crc16 = (crc16 & 0x8000) != 0 ? crc16 << 1 ^ 0x8005 : crc16 << 1;
            }
            CRC8_TABLE[i] = crc8 & 0xFF;
            CRC16_TABLE[i] = crc16 & 0xFFFF;
        }
    }


    /**
     * Private due to utility class.
     */
    private FlacEncoder ()
    {
        // Intentionally empty
    }


    /**
     * Encodes the given audio data losslessly into a FLAC stream.
     *
     * @param channels The audio data, one array of signed samples per channel, all of the same
     *            length
     * @param sampleRate The sample rate in Hertz
     * @param bitsPerSample The resolution of the samples, may be 8, 16 or 24
     * @return The FLAC stream
     * @throws IOException If the audio attributes cannot be represented in FLAC
     */
    public static byte [] encode (final int [] [] channels, final int sampleRate, final int bitsPerSample) throws IOException
    {
        final int numberOfChannels = channels.length;
        if (numberOfChannels < 1 || numberOfChannels > 8)
            throw new IOException ("FLAC: Unsupported number of channels: " + numberOfChannels);
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24)
            throw new IOException ("FLAC: Unsupported bit resolution: " + bitsPerSample);
        if (sampleRate < 1 || sampleRate > 0xFFFFF)
            throw new IOException ("FLAC: Unsupported sample rate: " + sampleRate);
        final int numberOfSamples = channels[0].length;
        for (final int [] channel: channels)
            if (channel.length != numberOfSamples)
                throw new IOException ("FLAC: All channels must have the same length.");

        final MessageDigest md5 = createMD5Digest ();
        final ByteArrayOutputStream frames = new ByteArrayOutputStream ();
        int minFrameSize = Integer.MAX_VALUE;
        int maxFrameSize = 0;
        int frameIndex = 0;
        final byte [] interleaved = new byte [BLOCK_SIZE * numberOfChannels * (bitsPerSample / 8)];
        for (int offset = 0; offset < numberOfSamples; offset += BLOCK_SIZE)
        {
            final int blockSize = Math.min (BLOCK_SIZE, numberOfSamples - offset);
            md5.update (interleaved, 0, interleaveLittleEndian (channels, offset, blockSize, bitsPerSample, interleaved));
            final byte [] frame = encodeFrame (channels, offset, blockSize, frameIndex, sampleRate, bitsPerSample);
            frames.write (frame, 0, frame.length);
            minFrameSize = Math.min (minFrameSize, frame.length);
            maxFrameSize = Math.max (maxFrameSize, frame.length);
            frameIndex++;
        }
        if (frameIndex == 0)
            minFrameSize = 0;

        final BitWriter header = new BitWriter ();
        header.writeBits (0x664C6143, 32);
        // Meta-data block header: type 0 (STREAMINFO), length 34
        header.writeBits (0x00, 8);
        header.writeBits (34, 24);
        header.writeBits (BLOCK_SIZE, 16);
        header.writeBits (BLOCK_SIZE, 16);
        header.writeBits (minFrameSize, 24);
        header.writeBits (maxFrameSize, 24);
        header.writeBits (sampleRate, 20);
        header.writeBits (numberOfChannels - 1, 3);
        header.writeBits (bitsPerSample - 1, 5);
        header.writeLongBits (numberOfSamples, 36);
        for (final byte b: md5.digest ())
            header.writeBits (b & 0xFF, 8);

        // A PADDING block terminates the meta-data. The jFLAC based reader which is used to read
        // FLAC files cannot handle a stream in which STREAMINFO is the only meta-data block!
        header.writeBits (0x81, 8);
        header.writeBits (PADDING_LENGTH, 24);
        for (int i = 0; i < PADDING_LENGTH; i++)
            header.writeBits (0, 8);

        final ByteArrayOutputStream result = new ByteArrayOutputStream ();
        final byte [] headerBytes = header.toByteArray ();
        result.write (headerBytes, 0, headerBytes.length);
        frames.writeTo (result);
        return result.toByteArray ();
    }


    /**
     * Encodes one FLAC frame.
     *
     * @param channels The audio data of all channels
     * @param offset The index of the first sample of the block
     * @param blockSize The number of samples in the block
     * @param frameIndex The index of the frame in the stream
     * @param sampleRate The sample rate in Hertz
     * @param bitsPerSample The resolution of the samples
     * @return The encoded frame
     */
    private static byte [] encodeFrame (final int [] [] channels, final int offset, final int blockSize, final int frameIndex, final int sampleRate, final int bitsPerSample)
    {
        // Encode the sub-frames of all channels first since the channel assignment (independent
        // or de-correlated stereo) is chosen by the smallest encoded size and needs to be known
        // for the frame header
        int channelAssignment = channels.length - 1;
        BitWriter [] subframes;
        if (channels.length == 2)
        {
            final int [] [] stereo = calcStereoDecorrelation (channels, offset, blockSize);
            final BitWriter left = encodeSubframeToWriter (channels[0], offset, blockSize, bitsPerSample);
            final BitWriter right = encodeSubframeToWriter (channels[1], offset, blockSize, bitsPerSample);
            final BitWriter mid = encodeSubframeToWriter (stereo[0], 0, blockSize, bitsPerSample);
            final BitWriter side = encodeSubframeToWriter (stereo[1], 0, blockSize, bitsPerSample + 1);

            final long leftRight = (long) left.lengthInBits () + right.lengthInBits ();
            final long leftSide = (long) left.lengthInBits () + side.lengthInBits ();
            final long sideRight = (long) side.lengthInBits () + right.lengthInBits ();
            final long midSide = (long) mid.lengthInBits () + side.lengthInBits ();

            final long smallest = Math.min (Math.min (leftRight, midSide), Math.min (leftSide, sideRight));
            if (smallest == leftRight)
                subframes = new BitWriter []
                {
                    left,
                    right
                };
            else if (smallest == leftSide)
            {
                channelAssignment = 0b1000;
                subframes = new BitWriter []
                {
                    left,
                    side
                };
            }
            else if (smallest == sideRight)
            {
                channelAssignment = 0b1001;
                subframes = new BitWriter []
                {
                    side,
                    right
                };
            }
            else
            {
                channelAssignment = 0b1010;
                subframes = new BitWriter []
                {
                    mid,
                    side
                };
            }
        }
        else
        {
            subframes = new BitWriter [channels.length];
            for (int i = 0; i < channels.length; i++)
                subframes[i] = encodeSubframeToWriter (channels[i], offset, blockSize, bitsPerSample);
        }

        final BitWriter writer = new BitWriter ();

        // Sync code, mandatory 0 bit and fixed block size stream marker
        writer.writeBits (0b11111111111110, 14);
        writer.writeBits (0, 2);
        final int blockSizeCode = getBlockSizeCode (blockSize);
        writer.writeBits (blockSizeCode, 4);
        final int sampleRateCode = getSampleRateCode (sampleRate);
        writer.writeBits (sampleRateCode, 4);
        writer.writeBits (channelAssignment, 4);
        writer.writeBits (getSampleSizeCode (bitsPerSample), 3);
        writer.writeBits (0, 1);
        writeUtf8CodedNumber (writer, frameIndex);
        if (blockSizeCode == 0b0111)
            writer.writeBits (blockSize - 1, 16);
        if (sampleRateCode == 0b1101)
            writer.writeBits (sampleRate, 16);
        else if (sampleRateCode == 0b1110)
            writer.writeBits (sampleRate / 10, 16);
        writer.writeBits (calcCRC8 (writer.buffer, writer.size), 8);

        for (final BitWriter subframe: subframes)
            subframe.appendTo (writer);

        writer.align ();
        writer.writeBits (calcCRC16 (writer.buffer, writer.size), 16);
        return writer.toByteArray ();
    }


    /**
     * Calculates the mid and side channels of a stereo block. The side channel needs 1 bit more
     * resolution than the source channels.
     *
     * @param channels The audio data of both channels
     * @param offset The index of the first sample of the block
     * @param blockSize The number of samples in the block
     * @return The mid samples (index 0) and side samples (index 1) of the block
     */
    private static int [] [] calcStereoDecorrelation (final int [] [] channels, final int offset, final int blockSize)
    {
        final int [] mid = new int [blockSize];
        final int [] side = new int [blockSize];
        for (int i = 0; i < blockSize; i++)
        {
            final int left = channels[0][offset + i];
            final int right = channels[1][offset + i];
            mid[i] = left + right >> 1;
            side[i] = left - right;
        }
        return new int [] []
        {
            mid,
            side
        };
    }


    /**
     * Encodes the block of one channel as a sub-frame into a new writer.
     *
     * @param samples The samples of the channel
     * @param offset The index of the first sample of the block
     * @param blockSize The number of samples in the block
     * @param bitsPerSample The resolution of the samples
     * @return The writer with the encoded sub-frame
     */
    private static BitWriter encodeSubframeToWriter (final int [] samples, final int offset, final int blockSize, final int bitsPerSample)
    {
        final BitWriter writer = new BitWriter ();
        encodeSubframe (writer, samples, offset, blockSize, bitsPerSample);
        return writer;
    }


    /**
     * Encodes the block of one channel as a sub-frame. Constant blocks are stored as a single
     * value. Blocks too short for prediction are stored verbatim. All other blocks use the fixed
     * predictor (order 0-4) or linear predictor (LPC) with the smallest encoded size; if the Rice
     * coded residuals would exceed the verbatim size, the block is stored verbatim instead.
     *
     * @param writer The writer to append the sub-frame to
     * @param samples The samples of the channel
     * @param offset The index of the first sample of the block
     * @param blockSize The number of samples in the block
     * @param bitsPerSample The resolution of the samples
     */
    private static void encodeSubframe (final BitWriter writer, final int [] samples, final int offset, final int blockSize, final int bitsPerSample)
    {
        // Mandatory sub-frame header padding bit
        writer.writeBits (0, 1);

        boolean isConstant = true;
        for (int i = 1; i < blockSize; i++)
            if (samples[offset + i] != samples[offset])
            {
                isConstant = false;
                break;
            }
        if (isConstant)
        {
            writer.writeBits (0b000000, 6);
            writer.writeBits (0, 1);
            writer.writeBits (samples[offset], bitsPerSample);
            return;
        }

        // Store very short trailing blocks verbatim: a predictor of order N requires N warm-up
        // samples plus at least 1 residual and the jFLAC based reader which is used to read FLAC
        // files mis-decodes predictions when a block is shorter than 16 samples
        if (blockSize < 16)
        {
            writeVerbatimSubframe (writer, samples, offset, blockSize, bitsPerSample);
            return;
        }

        final Prediction fixed = findBestFixedPrediction (samples, offset, blockSize);
        final Prediction lpc = findBestLpcPrediction (samples, offset, blockSize, bitsPerSample);

        // Compare the encoded payload sizes of all sub-frame alternatives
        final long fixedBits = fixed.order * (long) bitsPerSample + 6 + fixed.riceBits;
        final long lpcBits = lpc == null ? Long.MAX_VALUE : lpc.order * (long) (bitsPerSample + LPC_PRECISION) + 9 + 6 + lpc.riceBits;
        final long verbatimBits = (long) blockSize * bitsPerSample;

        // Null-check is redundant but makes Eclipse happy...
        if (lpc != null && lpcBits < fixedBits && lpcBits < verbatimBits)
        {
            writer.writeBits (0b100000 | lpc.order - 1, 6);
            writer.writeBits (0, 1);
            for (int i = 0; i < lpc.order; i++)
                writer.writeBits (samples[offset + i], bitsPerSample);
            writer.writeBits (LPC_PRECISION - 1, 4);
            writer.writeBits (lpc.shift, 5);
            for (int i = 0; i < lpc.order; i++)
                writer.writeBits (lpc.coefficients[i], LPC_PRECISION);
            writeRiceResidual (writer, lpc);
            return;
        }

        if (fixedBits >= verbatimBits)
        {
            writeVerbatimSubframe (writer, samples, offset, blockSize, bitsPerSample);
            return;
        }

        writer.writeBits (0b001000 | fixed.order, 6);
        writer.writeBits (0, 1);
        for (int i = 0; i < fixed.order; i++)
            writer.writeBits (samples[offset + i], bitsPerSample);
        writeRiceResidual (writer, fixed);
    }


    /**
     * Finds the fixed predictor order (0-4) with the smallest absolute residual sum and calculates
     * its residuals and Rice parameter.
     *
     * @param samples The samples of the channel
     * @param offset The index of the first sample of the block
     * @param blockSize The number of samples in the block
     * @return The prediction
     */
    private static Prediction findBestFixedPrediction (final int [] samples, final int offset, final int blockSize)
    {
        final int [] residuals = new int [blockSize];
        System.arraycopy (samples, offset, residuals, 0, blockSize);
        int bestOrder = 0;
        long bestSum = sumAbsolute (residuals, 0, blockSize);
        for (int order = 1; order <= MAX_FIXED_ORDER; order++)
        {
            for (int i = blockSize - 1; i >= order; i--)
                residuals[i] -= residuals[i - 1];
            final long sum = sumAbsolute (residuals, order, blockSize);
            if (sum < bestSum)
            {
                bestSum = sum;
                bestOrder = order;
            }
        }

        // Re-calculate the residuals of the best order
        System.arraycopy (samples, offset, residuals, 0, blockSize);
        for (int order = 1; order <= bestOrder; order++)
            for (int i = blockSize - 1; i >= order; i--)
                residuals[i] -= residuals[i - 1];

        return new Prediction (bestOrder, residuals, blockSize);
    }


    /**
     * Calculates a linear predictor for the block: a Welch window is applied for the
     * auto-correlation, the Levinson-Durbin recursion provides the predictor coefficients for all
     * orders up to MAX_LPC_ORDER and the order with the smallest estimated size is chosen. The
     * chosen coefficients are quantized to integers; the residuals are calculated with the exact
     * integer arithmetic of the decoder, therefore the compression stays lossless independent of
     * the predictor quality.
     *
     * @param samples The samples of the channel
     * @param offset The index of the first sample of the block
     * @param blockSize The number of samples in the block
     * @param bitsPerSample The resolution of the samples
     * @return The prediction or null if no usable predictor was found
     */
    private static Prediction findBestLpcPrediction (final int [] samples, final int offset, final int blockSize, final int bitsPerSample)
    {
        final int maxOrder = Math.min (MAX_LPC_ORDER, blockSize / 2);

        // Auto-correlation of the windowed block
        final double [] windowed = new double [blockSize];
        final double half = (blockSize - 1) / 2.0;
        for (int i = 0; i < blockSize; i++)
        {
            final double w = (i - half) / half;
            windowed[i] = samples[offset + i] * (1.0 - w * w);
        }
        final double [] autoCorrelation = new double [maxOrder + 1];
        for (int lag = 0; lag <= maxOrder; lag++)
        {
            double sum = 0;
            for (int i = lag; i < blockSize; i++)
                sum += windowed[i] * windowed[i - lag];
            autoCorrelation[lag] = sum;
        }
        if (autoCorrelation[0] <= 0)
            return null;

        // Levinson-Durbin recursion; keep the coefficients of all orders
        final double [] [] coefficients = new double [maxOrder + 1] [];
        final double [] coefficientsWork = new double [maxOrder];
        double error = autoCorrelation[0];
        int bestOrder = 0;
        double bestEstimate = Double.MAX_VALUE;
        for (int order = 1; order <= maxOrder; order++)
        {
            double accumulator = autoCorrelation[order];
            for (int i = 0; i < order - 1; i++)
                accumulator -= coefficientsWork[i] * autoCorrelation[order - 1 - i];
            final double reflection = accumulator / error;
            final double [] previous = coefficientsWork.clone ();
            coefficientsWork[order - 1] = reflection;
            for (int i = 0; i < order - 1; i++)
                coefficientsWork[i] = previous[i] - reflection * previous[order - 2 - i];
            error *= 1 - reflection * reflection;
            if (!Double.isFinite (error) || error < 0)
                break;
            coefficients[order] = coefficientsWork.clone ();

            // Estimated size in bits: Rice coded residuals plus warm-up and coefficients
            final double estimate = 0.5 * blockSize * Math.log (Math.max (error / blockSize, 1e-9)) / Math.log (2) + order * (double) (bitsPerSample + LPC_PRECISION);
            if (estimate < bestEstimate)
            {
                bestEstimate = estimate;
                bestOrder = order;
            }
        }
        if (bestOrder == 0)
            return null;

        // Quantize the coefficients of the best order
        final double [] lpcCoefficients = coefficients[bestOrder];
        double maxCoefficient = 0;
        for (final double coefficient: lpcCoefficients)
            maxCoefficient = Math.max (maxCoefficient, Math.abs (coefficient));
        if (maxCoefficient <= 0 || !Double.isFinite (maxCoefficient))
            return null;
        int shift = LPC_PRECISION - 1 - (Math.getExponent (maxCoefficient) + 1);
        if (shift > 15)
            shift = 15;
        if (shift < 0)
            return null;
        final int [] quantized = new int [bestOrder];
        final int limit = (1 << LPC_PRECISION - 1) - 1;
        double quantizationError = 0;
        for (int i = 0; i < bestOrder; i++)
        {
            final double scaled = lpcCoefficients[i] * (1 << shift) + quantizationError;
            long rounded = Math.round (scaled);
            if (rounded > limit)
                rounded = limit;
            else if (rounded < -limit - 1L)
                rounded = -limit - 1L;
            quantizationError = scaled - rounded;
            quantized[i] = (int) rounded;
        }

        // Calculate the residuals with the integer arithmetic of the decoder
        final int [] residuals = new int [blockSize];
        for (int i = bestOrder; i < blockSize; i++)
        {
            long prediction = 0;
            for (int j = 0; j < bestOrder; j++)
                prediction += (long) quantized[j] * samples[offset + i - 1 - j];
            final long residual = samples[offset + i] - (prediction >> shift);
            // Reject pathological predictors
            if (residual > 1 << 30 || residual < -(1 << 30))
                return null;
            residuals[i] = (int) residual;
        }

        final Prediction prediction = new Prediction (bestOrder, residuals, blockSize);
        prediction.coefficients = quantized;
        prediction.shift = shift;
        return prediction;
    }


    /**
     * Writes the residuals of a prediction: residual coding method 0 (4-bit Rice parameters) with
     * the partition order chosen by the prediction analysis.
     *
     * @param writer The writer to append the residuals to
     * @param prediction The prediction
     */
    private static void writeRiceResidual (final BitWriter writer, final Prediction prediction)
    {
        writer.writeBits (0b00, 2);
        writer.writeBits (prediction.partitionOrder, 4);
        final int partitionSize = prediction.blockSize >> prediction.partitionOrder;
        for (int partition = 0; partition < 1 << prediction.partitionOrder; partition++)
        {
            final int riceParameter = prediction.riceParameters[partition];
            writer.writeBits (riceParameter, 4);
            final int from = partition == 0 ? prediction.order : partition * partitionSize;
            final int to = (partition + 1) * partitionSize;
            for (int i = from; i < to; i++)
            {
                final int zigzag = zigzagEncode (prediction.residuals[i]);
                writer.writeUnary (zigzag >>> riceParameter);
                if (riceParameter > 0)
                    writer.writeBits (zigzag, riceParameter);
            }
        }
    }


    /**
     * Writes a verbatim sub-frame which stores all samples uncompressed.
     *
     * @param writer The writer to append the sub-frame to
     * @param samples The samples of the channel
     * @param offset The index of the first sample of the block
     * @param blockSize The number of samples in the block
     * @param bitsPerSample The resolution of the samples
     */
    private static void writeVerbatimSubframe (final BitWriter writer, final int [] samples, final int offset, final int blockSize, final int bitsPerSample)
    {
        writer.writeBits (0b000001, 6);
        writer.writeBits (0, 1);
        for (int i = 0; i < blockSize; i++)
            writer.writeBits (samples[offset + i], bitsPerSample);
    }


    /**
     * Maps a signed residual to an unsigned value by folding: 0, -1, 1, -2, ... become 0, 1, 2, 3,
     * ...
     *
     * @param value The signed value
     * @return The folded unsigned value
     */
    private static int zigzagEncode (final int value)
    {
        return value >= 0 ? value << 1 : (-value << 1) - 1;
    }


    /**
     * Sums up the absolute values of the given range.
     *
     * @param values The values
     * @param startIndex The index of the first value
     * @param endIndex The index after the last value
     * @return The sum
     */
    private static long sumAbsolute (final int [] values, final int startIndex, final int endIndex)
    {
        long sum = 0;
        for (int i = startIndex; i < endIndex; i++)
            sum += Math.abs ((long) values[i]);
        return sum;
    }


    /**
     * Interleaves a block of all channels into little-endian bytes as needed for the MD5 checksum
     * of the un-encoded audio data.
     *
     * @param channels The audio data of all channels
     * @param offset The index of the first sample of the block
     * @param blockSize The number of samples in the block
     * @param bitsPerSample The resolution of the samples
     * @param output Where to write the bytes to
     * @return The number of bytes written
     */
    private static int interleaveLittleEndian (final int [] [] channels, final int offset, final int blockSize, final int bitsPerSample, final byte [] output)
    {
        final int bytesPerSample = bitsPerSample / 8;
        int position = 0;
        for (int i = 0; i < blockSize; i++)
            for (final int [] channel: channels)
            {
                final int value = channel[offset + i];
                for (int b = 0; b < bytesPerSample; b++)
                    output[position++] = (byte) (value >> 8 * b);
            }
        return position;
    }


    /**
     * Gets the 4-bit code for the block size. Common sizes have a direct code, all others are
     * stored explicitly at the end of the frame header.
     *
     * @param blockSize The number of samples in the block
     * @return The code
     */
    private static int getBlockSizeCode (final int blockSize)
    {
        switch (blockSize)
        {
            case 192:
                return 0b0001;
            case 576:
                return 0b0010;
            case 1152:
                return 0b0011;
            case 2304:
                return 0b0100;
            case 4608:
                return 0b0101;
            case 256:
                return 0b1000;
            case 512:
                return 0b1001;
            case 1024:
                return 0b1010;
            case 2048:
                return 0b1011;
            case 4096:
                return 0b1100;
            case 8192:
                return 0b1101;
            case 16384:
                return 0b1110;
            case 32768:
                return 0b1111;
            default:
                // 16 bit (blockSize - 1) follows at the end of the frame header
                return 0b0111;
        }
    }


    /**
     * Gets the 4-bit code for the sample rate. Common rates have a direct code, other rates are
     * stored explicitly at the end of the frame header or are only present in the STREAMINFO block.
     *
     * @param sampleRate The sample rate in Hertz
     * @return The code
     */
    private static int getSampleRateCode (final int sampleRate)
    {
        switch (sampleRate)
        {
            case 88200:
                return 0b0001;
            case 176400:
                return 0b0010;
            case 192000:
                return 0b0011;
            case 8000:
                return 0b0100;
            case 16000:
                return 0b0101;
            case 22050:
                return 0b0110;
            case 24000:
                return 0b0111;
            case 32000:
                return 0b1000;
            case 44100:
                return 0b1001;
            case 48000:
                return 0b1010;
            case 96000:
                return 0b1011;
            default:
                // 16 bit sample rate in Hz or in tens of Hz follows at the end of the frame
                // header, otherwise the decoder reads it from the STREAMINFO block
                if (sampleRate <= 0xFFFF)
                    return 0b1101;
                if (sampleRate % 10 == 0 && sampleRate / 10 <= 0xFFFF)
                    return 0b1110;
                return 0b0000;
        }
    }


    /**
     * Gets the 3-bit code for the sample resolution.
     *
     * @param bitsPerSample The resolution of the samples
     * @return The code
     */
    private static int getSampleSizeCode (final int bitsPerSample)
    {
        switch (bitsPerSample)
        {
            case 8:
                return 0b001;
            case 16:
                return 0b100;
            case 24:
                return 0b110;
            default:
                throw new IllegalArgumentException ("FLAC: Unsupported bit resolution: " + bitsPerSample);
        }
    }


    /**
     * Writes a number in the variable length UTF-8 style coding used for the frame number.
     *
     * @param writer The writer to append the bytes to
     * @param number The number to write, must be positive
     */
    private static void writeUtf8CodedNumber (final BitWriter writer, final long number)
    {
        if (number < 0x80)
        {
            writer.writeBits ((int) number, 8);
            return;
        }
        int continuationBytes = 1;
        while (number >= 1L << 6 + 5 * continuationBytes)
            continuationBytes++;
        writer.writeBits (0xFF00 >> continuationBytes + 1 & 0xFF | (int) (number >>> 6 * continuationBytes), 8);
        for (int i = continuationBytes - 1; i >= 0; i--)
            writer.writeBits (0x80 | (int) (number >>> 6 * i) & 0x3F, 8);
    }


    /**
     * Calculates the CRC-8 checksum (polynomial 0x07) as used for the frame header.
     *
     * @param data The data
     * @param length The number of bytes to process
     * @return The checksum
     */
    private static int calcCRC8 (final byte [] data, final int length)
    {
        int crc = 0;
        for (int i = 0; i < length; i++)
            crc = CRC8_TABLE[crc ^ data[i] & 0xFF];
        return crc;
    }


    /**
     * Calculates the CRC-16 checksum (polynomial 0x8005) as used for the frame footer.
     *
     * @param data The data
     * @param length The number of bytes to process
     * @return The checksum
     */
    private static int calcCRC16 (final byte [] data, final int length)
    {
        int crc = 0;
        for (int i = 0; i < length; i++)
            crc = crc << 8 & 0xFFFF ^ CRC16_TABLE[crc >> 8 ^ data[i] & 0xFF];
        return crc;
    }


    /**
     * Creates the MD5 digest for the checksum of the un-encoded audio data.
     *
     * @return The digest
     * @throws IOException The MD5 algorithm is not available
     */
    private static MessageDigest createMD5Digest () throws IOException
    {
        try
        {
            return MessageDigest.getInstance ("MD5");
        }
        catch (final NoSuchAlgorithmException ex)
        {
            throw new IOException (ex);
        }
    }


    /**
     * The result of a prediction analysis of one block, with its residuals and the Rice partition
     * order and parameters with the smallest encoded size.
     */
    private static class Prediction
    {
        final int    order;
        final int [] residuals;
        final int    blockSize;
        int          partitionOrder;
        int []       riceParameters;
        long         riceBits;
        int []       coefficients;
        int          shift;


        Prediction (final int order, final int [] residuals, final int blockSize)
        {
            this.order = order;
            this.residuals = residuals;
            this.blockSize = blockSize;

            // Find the Rice partitioning with the smallest total size; a partitioning is only
            // possible if the block size is divisible and the first partition is not empty
            this.riceBits = Long.MAX_VALUE;
            for (int candidate = 0; candidate <= MAX_PARTITION_ORDER; candidate++)
            {
                final int numberOfPartitions = 1 << candidate;
                if ((blockSize & numberOfPartitions - 1) != 0)
                    continue;
                final int partitionSize = blockSize >> candidate;
                if (partitionSize <= order)
                    break;
                final int [] parameters = new int [numberOfPartitions];
                long bits = 0;
                for (int partition = 0; partition < numberOfPartitions; partition++)
                {
                    final int from = partition == 0 ? order : partition * partitionSize;
                    final int to = (partition + 1) * partitionSize;
                    parameters[partition] = findRiceParameter (residuals, from, to);
                    bits += 4 + calcRiceCost (residuals, from, to, parameters[partition]);
                }
                if (bits < this.riceBits)
                {
                    this.riceBits = bits;
                    this.partitionOrder = candidate;
                    this.riceParameters = parameters;
                }
            }
        }


        /**
         * Finds the Rice parameter with the smallest encoded size for the given residual range.
         *
         * @param residuals The residuals of the block
         * @param from The index of the first residual
         * @param to The index after the last residual
         * @return The Rice parameter in the range of [0, MAX_RICE_PARAMETER]
         */
        private static int findRiceParameter (final int [] residuals, final int from, final int to)
        {
            int bestParameter = 0;
            long bestCost = Long.MAX_VALUE;
            for (int parameter = 0; parameter <= MAX_RICE_PARAMETER; parameter++)
            {
                final long cost = calcRiceCost (residuals, from, to, parameter);
                if (cost < bestCost)
                {
                    bestCost = cost;
                    bestParameter = parameter;
                }
            }
            return bestParameter;
        }


        /**
         * Calculates the number of bits needed to Rice code the given residual range.
         *
         * @param residuals The residuals of the block
         * @param from The index of the first residual
         * @param to The index after the last residual
         * @param parameter The Rice parameter
         * @return The number of bits
         */
        private static long calcRiceCost (final int [] residuals, final int from, final int to, final int parameter)
        {
            long cost = 0;
            for (int i = from; i < to; i++)
                cost += (zigzagEncode (residuals[i]) >>> parameter) + 1L + parameter;
            return cost;
        }
    }


    /** Writes single bits into a growing byte buffer, most significant bit first. */
    private static class BitWriter
    {
        private byte [] buffer   = new byte [16384];
        private int     size     = 0;
        private int     bitCache = 0;
        private int     bitCount = 0;


        /**
         * Writes the given number of low bits of the value.
         *
         * @param value The value to write
         * @param count The number of bits, 0 to 32
         */
        final void writeBits (final int value, final int count)
        {
            int remaining = count;
            while (remaining > 0)
            {
                final int take = Math.min (8 - this.bitCount, remaining);
                final int chunk = value >>> remaining - take & (1 << take) - 1;
                this.bitCache = this.bitCache << take | chunk;
                this.bitCount += take;
                remaining -= take;
                if (this.bitCount == 8)
                    this.flushByte ();
            }
        }


        /**
         * Writes the given number of low bits of the long value.
         *
         * @param value The value to write
         * @param count The number of bits, 0 to 64
         */
        final void writeLongBits (final long value, final int count)
        {
            if (count > 32)
            {
                this.writeBits ((int) (value >>> 32), count - 32);
                this.writeBits ((int) value, 32);
            }
            else
                this.writeBits ((int) value, count);
        }


        /**
         * Writes a value in unary coding: the value as a number of 0 bits followed by a 1 bit.
         *
         * @param value The value to write, must be positive
         */
        final void writeUnary (final int value)
        {
            int remaining = value;
            while (remaining >= 32)
            {
                this.writeBits (0, 32);
                remaining -= 32;
            }
            this.writeBits (1, remaining + 1);
        }


        /**
         * Gets the number of written bits.
         *
         * @return The number of bits
         */
        final int lengthInBits ()
        {
            return this.size * 8 + this.bitCount;
        }


        /**
         * Appends all written bits to the given writer.
         *
         * @param other The writer to append to
         */
        final void appendTo (final BitWriter other)
        {
            for (int i = 0; i < this.size; i++)
                other.writeBits (this.buffer[i] & 0xFF, 8);
            if (this.bitCount > 0)
                other.writeBits (this.bitCache & (1 << this.bitCount) - 1, this.bitCount);
        }


        /**
         * Fills up the current byte with 0 bits.
         */
        final void align ()
        {
            if (this.bitCount > 0)
            {
                this.bitCache <<= 8 - this.bitCount;
                this.bitCount = 8;
                this.flushByte ();
            }
        }


        /**
         * Gets all written bytes. The writer must be byte aligned.
         *
         * @return The bytes
         */
        final byte [] toByteArray ()
        {
            final byte [] result = new byte [this.size];
            System.arraycopy (this.buffer, 0, result, 0, this.size);
            return result;
        }


        private void flushByte ()
        {
            if (this.size == this.buffer.length)
            {
                final byte [] grown = new byte [this.buffer.length * 2];
                System.arraycopy (this.buffer, 0, grown, 0, this.size);
                this.buffer = grown;
            }
            this.buffer[this.size] = (byte) this.bitCache;
            this.size++;
            this.bitCache = 0;
            this.bitCount = 0;
        }
    }
}
