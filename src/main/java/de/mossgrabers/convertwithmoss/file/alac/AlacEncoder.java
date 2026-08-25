// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.alac;

import java.io.IOException;
import java.util.Arrays;


/**
 * Encoder for the Apple Lossless Audio Codec (ALAC). This is a port of the encoder part of the
 * reference implementation published by Apple at https://github.com/macosforge/alac (Apache License
 * 2.0, (c) 2011 Apple Inc.). Only mono and stereo data with 16, 24 or 32 bits is supported.
 *
 * @author Jürgen Moßgraber
 */
public class AlacEncoder
{
    /** Single Channel Element. */
    private static final int  ID_SCE               = 0;
    /** Channel Pair Element. */
    private static final int  ID_CPE               = 1;
    /** Frame End Element. */
    private static final int  ID_END               = 7;

    // Constants of the adaptive Golomb coder
    private static final int  QBSHIFT              = 9;
    private static final int  QB                   = 1 << QBSHIFT;
    private static final int  MMULSHIFT            = 2;
    private static final int  MDENSHIFT            = QBSHIFT - MMULSHIFT - 1;
    private static final int  MOFF                 = 1 << MDENSHIFT - 2;
    private static final int  BITOFF               = 24;
    private static final int  MAX_PREFIX_16        = 9;
    private static final int  MAX_PREFIX_32        = 9;
    private static final int  MAX_DATATYPE_BITS_16 = 16;
    private static final int  N_MAX_MEAN_CLAMP     = 0xFFFF;
    private static final int  N_MEAN_CLAMP_VAL     = 0xFFFF;
    private static final int  PB0                  = 40;
    private static final int  MB0                  = 10;
    private static final int  KB0                  = 14;

    // Constants of the dynamic predictor
    private static final int  DENSHIFT_DEFAULT     = 9;
    private static final int  AINIT                = 38;
    private static final int  BINIT                = -29;
    private static final int  CINIT                = -2;

    // Constants of the parameter search
    private static final int  DEFAULT_MIX_BITS     = 2;
    private static final int  MAX_RES              = 4;
    private static final int  DEFAULT_NUM_UV       = 8;
    private static final int  MIN_UV               = 4;
    private static final int  MAX_UV               = 8;

    private final int         bitDepth;
    private final int         numChannels;
    private final int         sampleRate;
    private final int         frameLength;

    private final int []      mixBufferU;
    private final int []      mixBufferV;
    private final int []      predictorU;
    private final int []      predictorV;
    private final int []      shiftBufferUV;
    private final short [] [] coefsU;
    private final short [] [] coefsV;
    private final byte []     workBuffer;
    private int               lastMixRes           = 0;


    /**
     * Constructor.
     *
     * @param bitDepth The number of bits of one sample (16, 24 or 32)
     * @param numChannels The number of audio channels (1 or 2)
     * @param sampleRate The sample rate in Hz
     * @param frameLength The number of sample frames of a full packet, e.g. 4096
     * @throws IOException The configuration is not supported
     */
    public AlacEncoder (final int bitDepth, final int numChannels, final int sampleRate, final int frameLength) throws IOException
    {
        if (bitDepth != 16 && bitDepth != 24 && bitDepth != 32)
            throw new IOException ("Unsupported ALAC bit depth: " + bitDepth);
        if (numChannels < 1 || numChannels > 2)
            throw new IOException ("Unsupported number of ALAC channels: " + numChannels);

        this.bitDepth = bitDepth;
        this.numChannels = numChannels;
        this.sampleRate = sampleRate;
        this.frameLength = frameLength;

        this.mixBufferU = new int [frameLength];
        this.mixBufferV = new int [frameLength];
        this.predictorU = new int [frameLength];
        this.predictorV = new int [frameLength];
        this.shiftBufferUV = new int [frameLength * 2];
        this.workBuffer = new byte [this.getMaxPacketSize ()];

        // One persistent set of prediction coefficients per number of active coefficients.
        // Retaining their state across packets results in better overall compression.
        this.coefsU = new short [MAX_UV] [32];
        this.coefsV = new short [MAX_UV] [32];
        for (int i = 0; i < MAX_UV; i++)
        {
            initCoefs (this.coefsU[i]);
            initCoefs (this.coefsV[i]);
        }
    }


    /**
     * Get the maximum size of one encoded packet.
     *
     * @return The maximum number of bytes
     */
    public int getMaxPacketSize ()
    {
        return this.frameLength * this.numChannels * ((10 + this.bitDepth) / 8) + 64;
    }


    /**
     * Get the magic cookie (ALACSpecificConfig) which the decoder needs, e.g. for the 'kuki' chunk
     * of a CAF file.
     *
     * @return The 24 bytes of the configuration
     */
    public byte [] getMagicCookie ()
    {
        final byte [] cookie = new byte [24];
        writeInt32 (cookie, 0, this.frameLength);
        // The compatible version is 0
        cookie[4] = 0;
        cookie[5] = (byte) this.bitDepth;
        cookie[6] = PB0;
        cookie[7] = MB0;
        cookie[8] = KB0;
        cookie[9] = (byte) this.numChannels;
        // The maximum run length
        cookie[10] = 0;
        cookie[11] = (byte) 255;
        // The maximum frame bytes and average bit rate are unknown
        writeInt32 (cookie, 12, 0);
        writeInt32 (cookie, 16, 0);
        writeInt32 (cookie, 20, this.sampleRate);
        return cookie;
    }


    /**
     * Encode one packet.
     *
     * @param input The interleaved audio data in little-endian byte order (2 bytes per sample for
     *            16-bit, 3 for 24-bit, 4 for 32-bit)
     * @param inputOffset The offset in bytes of the first sample frame to encode
     * @param numFrames The number of sample frames to encode, at maximum the frame length
     * @return The encoded packet
     * @throws IOException Could not encode the data
     */
    public byte [] encodePacket (final byte [] input, final int inputOffset, final int numFrames) throws IOException
    {
        final BitWriter bitstream = new BitWriter (new byte [this.getMaxPacketSize ()]);

        // The 3-bit element tag and the 4-bit element instance tag
        if (this.numChannels == 2)
        {
            bitstream.write (ID_CPE, 3);
            bitstream.write (0, 4);
            this.encodeStereo (bitstream, input, inputOffset, numFrames);
        }
        else
        {
            bitstream.write (ID_SCE, 3);
            bitstream.write (0, 4);
            this.encodeMono (bitstream, input, inputOffset, numFrames);
        }

        // The 3-bit frame end tag, then byte-align the output data
        bitstream.write (ID_END, 3);
        bitstream.byteAlign ();

        return Arrays.copyOf (bitstream.getBuffer (), bitstream.getPosition () / 8);
    }


    /**
     * Encode a channel pair element. This is a port of EncodeStereo() of ALACEncoder.cpp.
     *
     * @param bitstream The bit stream to write to
     * @param input The interleaved input data
     * @param inputOffset The offset in bytes of the first sample frame
     * @param numSamples The number of sample frames
     * @throws IOException Could not encode the data
     */
    private void encodeStereo (final BitWriter bitstream, final byte [] input, final int inputOffset, final int numSamples) throws IOException
    {
        final int startPosition = bitstream.getPosition ();

        // Matrix encoding adds an extra bit but 32-bit inputs cannot be matrixed because 33 is
        // too many, so shift off 16 bits and encode in 17-bit mode. In addition, the 24-bit mode
        // really improves with one byte shifted off.
        final int bytesShifted;
        if (this.bitDepth == 32)
            bytesShifted = 2;
        else if (this.bitDepth == 24)
            bytesShifted = 1;
        else
            bytesShifted = 0;

        final int chanBits = this.bitDepth - bytesShifted * 8 + 1;
        final int partialFrame = numSamples == this.frameLength ? 0 : 1;

        final int mixBits = DEFAULT_MIX_BITS;
        final int pbFactor = 4;
        final int mode = 0;

        // Brute-force optimization loop over the mixing parameter
        int dilate = 8;
        long minBits1 = 1L << 31;
        int bestRes = this.lastMixRes;
        for (int mixRes = 0; mixRes <= MAX_RES; mixRes++)
        {
            this.mixStereo (input, inputOffset, numSamples / dilate, mixBits, mixRes, bytesShifted);

            pcBlock (this.mixBufferU, this.predictorU, numSamples / dilate, this.coefsU[DEFAULT_NUM_UV - 1], DEFAULT_NUM_UV, chanBits, DENSHIFT_DEFAULT);
            pcBlock (this.mixBufferV, this.predictorV, numSamples / dilate, this.coefsV[DEFAULT_NUM_UV - 1], DEFAULT_NUM_UV, chanBits, DENSHIFT_DEFAULT);

            final BitWriter workBits = new BitWriter (this.workBuffer);
            final long bits1 = dynComp (pbFactor * PB0 / 4, this.predictorU, workBits, numSamples / dilate, chanBits);
            final long bits2 = dynComp (pbFactor * PB0 / 4, this.predictorV, workBits, numSamples / dilate, chanBits);
            if (bits1 + bits2 < minBits1)
            {
                minBits1 = bits1 + bits2;
                bestRes = mixRes;
            }
        }
        this.lastMixRes = bestRes;

        // Mix the stereo inputs with the best mixing parameter
        final int mixRes = bestRes;
        this.mixStereo (input, inputOffset, numSamples, mixBits, mixRes, bytesShifted);

        // The predictor coefficient search loop
        int numU = MIN_UV;
        int numV = MIN_UV;
        minBits1 = 1L << 31;
        long minBits2 = 1L << 31;
        for (int numUV = MIN_UV; numUV <= MAX_UV; numUV += 4)
        {
            // Run the predictor over the same data multiple times to help it converge
            dilate = 32;
            for (int converge = 0; converge < 8; converge++)
            {
                pcBlock (this.mixBufferU, this.predictorU, numSamples / dilate, this.coefsU[numUV - 1], numUV, chanBits, DENSHIFT_DEFAULT);
                pcBlock (this.mixBufferV, this.predictorV, numSamples / dilate, this.coefsV[numUV - 1], numUV, chanBits, DENSHIFT_DEFAULT);
            }

            dilate = 8;
            final BitWriter workBits = new BitWriter (this.workBuffer);
            final long bits1 = dynComp (pbFactor * PB0 / 4, this.predictorU, workBits, numSamples / dilate, chanBits);
            if (bits1 * dilate + 16 * numUV < minBits1)
            {
                minBits1 = bits1 * dilate + 16 * numUV;
                numU = numUV;
            }
            final long bits2 = dynComp (pbFactor * PB0 / 4, this.predictorV, workBits, numSamples / dilate, chanBits);
            if (bits2 * dilate + 16 * numUV < minBits2)
            {
                minBits2 = bits2 * dilate + 16 * numUV;
                numV = numUV;
            }
        }

        // Check whether an escape (uncompressed) packet would be smaller than the estimated
        // compressed size
        long minBits = minBits1 + minBits2 + 8 * 8 + (partialFrame != 0 ? 32 : 0);
        if (bytesShifted != 0)
            minBits += (long) numSamples * (bytesShifted * 8) * 2;
        final long escapeBits = (long) numSamples * this.bitDepth * 2 + (partialFrame != 0 ? 32 : 0) + 2 * 8;

        boolean doEscape = minBits >= escapeBits;

        if (!doEscape)
        {
            // Write the bitstream header and the coefficients
            bitstream.write (0, 12);
            bitstream.write (partialFrame << 3 | bytesShifted << 1, 4);
            if (partialFrame != 0)
                bitstream.write (numSamples, 32);
            bitstream.write (mixBits, 8);
            bitstream.write (mixRes, 8);

            bitstream.write (mode << 4 | DENSHIFT_DEFAULT, 8);
            bitstream.write (pbFactor << 5 | numU, 8);
            for (int index = 0; index < numU; index++)
                bitstream.write (this.coefsU[numU - 1][index], 16);

            bitstream.write (mode << 4 | DENSHIFT_DEFAULT, 8);
            bitstream.write (pbFactor << 5 | numV, 8);
            for (int index = 0; index < numV; index++)
                bitstream.write (this.coefsV[numV - 1][index], 16);

            // If the shift is active, write the interleaved shift buffers
            if (bytesShifted != 0)
            {
                final int bitShift = bytesShifted * 8;
                for (int index = 0; index < numSamples * 2; index += 2)
                    bitstream.write (this.shiftBufferUV[index] << bitShift | this.shiftBufferUV[index + 1], bitShift * 2);
            }

            // Run the dynamic predictor and the lossless compression for both channels
            pcBlock (this.mixBufferU, this.predictorU, numSamples, this.coefsU[numU - 1], numU, chanBits, DENSHIFT_DEFAULT);
            dynComp (pbFactor * PB0 / 4, this.predictorU, bitstream, numSamples, chanBits);

            pcBlock (this.mixBufferV, this.predictorV, numSamples, this.coefsV[numV - 1], numV, chanBits, DENSHIFT_DEFAULT);
            dynComp (pbFactor * PB0 / 4, this.predictorV, bitstream, numSamples, chanBits);

            // If the compressed packet turned out to be bigger than an escape packet, chuck it
            // and do an escape packet
            if (bitstream.getPosition () - startPosition >= escapeBits)
            {
                bitstream.setPosition (startPosition);
                doEscape = true;
            }
        }

        if (doEscape)
        {
            // Write the header of the uncompressed frame; the lowest bit means "not compressed"
            bitstream.write (0, 12);
            bitstream.write (partialFrame << 3 | 1, 4);
            if (partialFrame != 0)
                bitstream.write (numSamples, 32);

            // Just copy the input data to the output buffer
            for (int index = 0; index < numSamples; index++)
            {
                bitstream.write (this.readSample (input, inputOffset, index * 2), this.bitDepth);
                bitstream.write (this.readSample (input, inputOffset, index * 2 + 1), this.bitDepth);
            }
        }
    }


    /**
     * Encode a single channel element. This is a port of EncodeMono() of ALACEncoder.cpp.
     *
     * @param bitstream The bit stream to write to
     * @param input The input data
     * @param inputOffset The offset in bytes of the first sample frame
     * @param numSamples The number of sample frames
     * @throws IOException Could not encode the data
     */
    private void encodeMono (final BitWriter bitstream, final byte [] input, final int inputOffset, final int numSamples) throws IOException
    {
        final int startPosition = bitstream.getPosition ();

        // Lop off the lower byte(s) for 24-/32-bit encodings
        final int bytesShifted;
        if (this.bitDepth == 32)
            bytesShifted = 2;
        else if (this.bitDepth == 24)
            bytesShifted = 1;
        else
            bytesShifted = 0;

        final int shift = bytesShifted * 8;
        final int mask = (1 << shift) - 1;
        final int chanBits = this.bitDepth - bytesShifted * 8;
        final int partialFrame = numSamples == this.frameLength ? 0 : 1;
        final int pbFactor = 4;

        // Convert the input data to 32-bit for the predictor and extract the shifted off bytes
        for (int index = 0; index < numSamples; index++)
        {
            final int value = this.readSample (input, inputOffset, index);
            if (bytesShifted != 0)
            {
                this.shiftBufferUV[index] = value & mask;
                this.mixBufferU[index] = value >> shift;
            }
            else
                this.mixBufferU[index] = value;
        }

        // Brute-force optimization loop over the number of predictor coefficients
        int bestU = MIN_UV;
        long minBits = 1L << 31;
        for (int numU = MIN_UV; numU <= MAX_UV; numU += 4)
        {
            int dilate = 32;
            for (int converge = 0; converge < 7; converge++)
                pcBlock (this.mixBufferU, this.predictorU, numSamples / dilate, this.coefsU[numU - 1], numU, chanBits, DENSHIFT_DEFAULT);

            dilate = 8;
            pcBlock (this.mixBufferU, this.predictorU, numSamples / dilate, this.coefsU[numU - 1], numU, chanBits, DENSHIFT_DEFAULT);

            final BitWriter workBits = new BitWriter (this.workBuffer);
            final long bits1 = dynComp (pbFactor * PB0 / 4, this.predictorU, workBits, numSamples / dilate, chanBits);
            final long numBits = dilate * bits1 + 16 * numU;
            if (numBits < minBits)
            {
                bestU = numU;
                minBits = numBits;
            }
        }

        // Check whether an escape (uncompressed) packet would be smaller than the estimated
        // compressed size
        minBits += 4 * 8 + (partialFrame != 0 ? 32 : 0);
        if (bytesShifted != 0)
            minBits += (long) numSamples * (bytesShifted * 8);
        final long escapeBits = (long) numSamples * this.bitDepth + (partialFrame != 0 ? 32 : 0) + 2 * 8;

        boolean doEscape = minBits >= escapeBits;

        if (!doEscape)
        {
            // Write the bitstream header
            bitstream.write (0, 12);
            bitstream.write (partialFrame << 3 | bytesShifted << 1, 4);
            if (partialFrame != 0)
                bitstream.write (numSamples, 32);
            // No mixing for mono
            bitstream.write (0, 16);

            // Write the parameters and the predictor coefficients; the mode is always zero
            final int numU = bestU;
            bitstream.write (DENSHIFT_DEFAULT, 8);
            bitstream.write (pbFactor << 5 | numU, 8);
            for (int index = 0; index < numU; index++)
                bitstream.write (this.coefsU[numU - 1][index], 16);

            // If the shift is active, write the shift buffer
            if (bytesShifted != 0)
                for (int index = 0; index < numSamples; index++)
                    bitstream.write (this.shiftBufferUV[index], shift);

            // Run the dynamic predictor with the best result
            pcBlock (this.mixBufferU, this.predictorU, numSamples, this.coefsU[numU - 1], numU, chanBits, DENSHIFT_DEFAULT);
            dynComp (PB0, this.predictorU, bitstream, numSamples, chanBits);

            // If the compressed packet turned out to be bigger than an escape packet, chuck it
            // and do an escape packet
            if (bitstream.getPosition () - startPosition >= escapeBits)
            {
                bitstream.setPosition (startPosition);
                doEscape = true;
            }
        }

        if (doEscape)
        {
            // Write the header of the uncompressed frame; the lowest bit means "not compressed"
            bitstream.write (0, 12);
            bitstream.write (partialFrame << 3 | 1, 4);
            if (partialFrame != 0)
                bitstream.write (numSamples, 32);

            // Just copy the input data to the output buffer
            for (int index = 0; index < numSamples; index++)
                bitstream.write (this.readSample (input, inputOffset, index), this.bitDepth);
        }
    }


    /**
     * Mix the interleaved stereo input into the U/V buffers and extract the shifted off bytes. This
     * is a port of mix16(), mix24() and mix32() of matrix_enc.c.
     *
     * @param input The interleaved input data
     * @param inputOffset The offset in bytes of the first sample frame
     * @param numSamples The number of sample frames to mix
     * @param mixBits The mixing shift
     * @param mixRes The mixing resolution, 0 keeps the channels separated
     * @param bytesShifted The number of bytes to shift off (24-/32-bit only)
     */
    private void mixStereo (final byte [] input, final int inputOffset, final int numSamples, final int mixBits, final int mixRes, final int bytesShifted)
    {
        final int shift = bytesShifted * 8;
        final int mask = (1 << shift) - 1;

        if (mixRes != 0)
        {
            final int m2 = (1 << mixBits) - mixRes;
            for (int j = 0; j < numSamples; j++)
            {
                int left = this.readSample (input, inputOffset, j * 2);
                int right = this.readSample (input, inputOffset, j * 2 + 1);
                if (bytesShifted != 0)
                {
                    this.shiftBufferUV[j * 2] = left & mask;
                    this.shiftBufferUV[j * 2 + 1] = right & mask;
                    left >>= shift;
                    right >>= shift;
                }
                this.mixBufferU[j] = mixRes * left + m2 * right >> mixBits;
                this.mixBufferV[j] = left - right;
            }
        }
        else
            for (int j = 0; j < numSamples; j++)
            {
                int left = this.readSample (input, inputOffset, j * 2);
                int right = this.readSample (input, inputOffset, j * 2 + 1);
                if (bytesShifted != 0)
                {
                    this.shiftBufferUV[j * 2] = left & mask;
                    this.shiftBufferUV[j * 2 + 1] = right & mask;
                    left >>= shift;
                    right >>= shift;
                }
                this.mixBufferU[j] = left;
                this.mixBufferV[j] = right;
            }
    }


    /**
     * Read one sample from the interleaved little-endian input data.
     *
     * @param input The input data
     * @param inputOffset The offset in bytes of the first sample frame
     * @param sampleIndex The index of the sample (frame * channels + channel)
     * @return The sample value
     */
    private int readSample (final byte [] input, final int inputOffset, final int sampleIndex)
    {
        final int offset;
        switch (this.bitDepth)
        {
            case 16:
                offset = inputOffset + sampleIndex * 2;
                return (input[offset] & 0xFF | input[offset + 1] << 8) << 16 >> 16;
            case 24:
                offset = inputOffset + sampleIndex * 3;
                return (input[offset] & 0xFF | (input[offset + 1] & 0xFF) << 8 | input[offset + 2] << 16) << 8 >> 8;
            default:
                offset = inputOffset + sampleIndex * 4;
                return input[offset] & 0xFF | (input[offset + 1] & 0xFF) << 8 | (input[offset + 2] & 0xFF) << 16 | input[offset + 3] << 24;
        }
    }


    /**
     * Run the prediction of one block. This is a port of pc_block() of dp_enc.c (the special cases
     * for 4 and 8 active coefficients are covered by the general case).
     *
     * @param in The input buffer with the samples
     * @param pc1 The output buffer for the prediction errors
     * @param num The number of samples
     * @param coefs The prediction coefficients, updated on the fly
     * @param numactive The number of active coefficients
     * @param chanbits The number of bits of one sample
     * @param denshift The rounding shift
     */
    private static void pcBlock (final int [] in, final int [] pc1, final int num, final short [] coefs, final int numactive, final int chanbits, final int denshift)
    {
        final int chanshift = 32 - chanbits;

        pc1[0] = in[0];

        if (numactive == 0)
        {
            if (num > 1 && in != pc1)
                System.arraycopy (in, 1, pc1, 1, num - 1);
            return;
        }

        if (numactive == 31)
        {
            for (int j = 1; j < num; j++)
            {
                final int del = in[j] - in[j - 1];
                pc1[j] = del << chanshift >> chanshift;
            }
            return;
        }

        for (int j = 1; j <= numactive; j++)
        {
            final int del = in[j] - in[j - 1];
            pc1[j] = del << chanshift >> chanshift;
        }

        final int denhalf = 1 << denshift - 1;
        final int lim = numactive + 1;

        for (int j = lim; j < num; j++)
        {
            int sum1 = 0;
            final int top = in[j - lim];
            final int base = j - 1;
            for (int k = 0; k < numactive; k++)
                sum1 -= coefs[k] * (top - in[base - k]);

            int del = in[j] - top - (sum1 + denhalf >> denshift);
            del = del << chanshift >> chanshift;
            pc1[j] = del;
            int del0 = del;

            final int sg = signOfInt (del);
            if (sg > 0)
                for (int k = numactive - 1; k >= 0; k--)
                {
                    final int dd = top - in[base - k];
                    final int sgn = signOfInt (dd);
                    coefs[k] = (short) (coefs[k] - sgn);
                    del0 -= (numactive - k) * (sgn * dd >> denshift);
                    if (del0 <= 0)
                        break;
                }
            else if (sg < 0)
                for (int k = numactive - 1; k >= 0; k--)
                {
                    final int dd = top - in[base - k];
                    final int sgn = signOfInt (dd);
                    coefs[k] = (short) (coefs[k] + sgn);
                    del0 -= (numactive - k) * (-sgn * dd >> denshift);
                    if (del0 >= 0)
                        break;
                }
        }
    }


    /**
     * Compress one channel with the adaptive Golomb coder. This is a port of dyn_comp() of
     * ag_enc.c.
     *
     * @param pb The already scaled 'pb' parameter
     * @param pc The prediction errors to compress
     * @param out The bit stream to write to
     * @param numSamples The number of samples to compress
     * @param bitSize The maximum number of bits of one value
     * @return The number of written bits
     * @throws IOException The parameters are invalid
     */
    private static long dynComp (final int pb, final int [] pc, final BitWriter out, final int numSamples, final int bitSize) throws IOException
    {
        if (bitSize < 1 || bitSize > 32)
            throw new IOException ("Invalid ALAC bit size: " + bitSize);

        final int startPosition = out.getPosition ();
        final int kb = KB0;
        final int wb = (1 << kb) - 1;

        int mb = MB0;
        int zmode = 0;
        int c = 0;
        int inIndex = 0;

        while (c < numSamples)
        {
            int m = mb >>> QBSHIFT;
            final int k = Math.min (lg3a (m), kb);
            m = (1 << k) - 1;

            final int del = pc[inIndex];
            inIndex++;

            final int n = (Math.abs (del) << 1) - (del >>> 31) - zmode;

            // dyn_code_32bit()
            final int div = Integer.divideUnsigned (n, m);
            boolean escape = div >= MAX_PREFIX_32;
            if (!escape)
            {
                final int mod = n - m * div;
                final int de = mod == 0 ? 1 : 0;
                final int numBits = div + k + 1 - de;
                if (numBits > 25)
                    escape = true;
                else
                    out.write (((1 << div) - 1 << numBits - div) + mod + 1 - de, numBits);
            }
            if (escape)
            {
                out.write ((1 << MAX_PREFIX_32) - 1, MAX_PREFIX_32);
                out.write (n, bitSize);
            }

            c++;

            mb = pb * (n + zmode) + mb - (pb * mb >>> QBSHIFT);

            // Update the mean tracking if it has overflowed
            if (Integer.compareUnsigned (n, N_MAX_MEAN_CLAMP) > 0)
                mb = N_MEAN_CLAMP_VAL;

            zmode = 0;

            if (Integer.compareUnsigned (mb << MMULSHIFT, QB) < 0 && c < numSamples)
            {
                zmode = 1;
                int nz = 0;

                while (c < numSamples && pc[inIndex] == 0)
                {
                    inIndex++;
                    nz++;
                    c++;
                    if (nz >= 65535)
                    {
                        zmode = 0;
                        break;
                    }
                }

                final int kz = Integer.numberOfLeadingZeros (mb) - BITOFF + (mb + MOFF >> MDENSHIFT);
                final int mz = (1 << kz) - 1 & wb;

                // dyn_code()
                final int divz = Integer.divideUnsigned (nz, mz);
                if (divz >= MAX_PREFIX_16)
                    out.write (((1 << MAX_PREFIX_16) - 1 << MAX_DATATYPE_BITS_16) + nz, MAX_PREFIX_16 + MAX_DATATYPE_BITS_16);
                else
                {
                    final int modz = nz % mz;
                    final int dez = modz == 0 ? 1 : 0;
                    int numBits = divz + kz + 1 - dez;
                    int value = ((1 << divz) - 1 << numBits - divz) + modz + 1 - dez;
                    if (numBits > MAX_PREFIX_16 + MAX_DATATYPE_BITS_16)
                    {
                        numBits = MAX_PREFIX_16 + MAX_DATATYPE_BITS_16;
                        value = ((1 << MAX_PREFIX_16) - 1 << MAX_DATATYPE_BITS_16) + nz;
                    }
                    out.write (value, numBits);
                }

                mb = 0;
            }
        }

        return out.getPosition () - (long) startPosition;
    }


    private static void initCoefs (final short [] coefs)
    {
        final int den = 1 << DENSHIFT_DEFAULT;
        coefs[0] = (short) (AINIT * den >> 4);
        coefs[1] = (short) (BINIT * den >> 4);
        coefs[2] = (short) (CINIT * den >> 4);
        for (int k = 3; k < coefs.length; k++)
            coefs[k] = 0;
    }


    private static int signOfInt (final int i)
    {
        return -i >>> 31 | i >> 31;
    }


    private static int lg3a (final int x)
    {
        return 31 - Integer.numberOfLeadingZeros (x + 3);
    }


    private static void writeInt32 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value >> 24);
        data[offset + 1] = (byte) (value >> 16);
        data[offset + 2] = (byte) (value >> 8);
        data[offset + 3] = (byte) value;
    }


    /**
     * A big-endian (most significant bit first) bit stream writer.
     */
    private static final class BitWriter
    {
        private final byte [] buffer;
        private int           position = 0;


        /**
         * Constructor.
         *
         * @param buffer The buffer to write to
         */
        BitWriter (final byte [] buffer)
        {
            this.buffer = buffer;
        }


        /**
         * Write up to 32 bits.
         *
         * @param value The value to write, only the lowest bits are used
         * @param numBits The number of bits to write
         */
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


        /**
         * Pad with zero bits to the next byte boundary.
         */
        void byteAlign ()
        {
            final int bitIndex = this.position & 7;
            if (bitIndex != 0)
                this.position += 8 - bitIndex;
        }


        /**
         * Get the current position.
         *
         * @return The position in bits
         */
        int getPosition ()
        {
            return this.position;
        }


        /**
         * Move the position backwards and clear everything behind it.
         *
         * @param position The new position in bits
         */
        void setPosition (final int position)
        {
            final int byteIndex = position >>> 3;
            final int bitIndex = position & 7;
            Arrays.fill (this.buffer, bitIndex > 0 ? byteIndex + 1 : byteIndex, this.buffer.length, (byte) 0);
            if (bitIndex > 0)
                this.buffer[byteIndex] &= (byte) (0xFF << 8 - bitIndex);
            this.position = position;
        }


        /**
         * Get the buffer.
         *
         * @return The buffer
         */
        byte [] getBuffer ()
        {
            return this.buffer;
        }
    }
}
