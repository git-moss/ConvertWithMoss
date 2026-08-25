// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.alac;

import java.io.IOException;


/**
 * Decoder for the Apple Lossless Audio Codec (ALAC). This is a port of the decoder part of the
 * reference implementation published by Apple at https://github.com/macosforge/alac (Apache License
 * 2.0, (c) 2011 Apple Inc.).
 *
 * @author Jürgen Moßgraber
 */
public class AlacDecoder
{
    /** Single Channel Element. */
    private static final int ID_SCE                = 0;
    /** Channel Pair Element. */
    private static final int ID_CPE                = 1;
    /** Coupling Channel Element. */
    @SuppressWarnings("unused")
    private static final int ID_CCE                = 2;
    /** LFE Channel Element. */
    private static final int ID_LFE                = 3;
    /** Data Stream Element. */
    private static final int ID_DSE                = 4;
    /** Program Config Element. */
    @SuppressWarnings("unused")
    private static final int ID_PCE                = 5;
    /** Fill Element. */
    private static final int ID_FIL                = 6;
    /** Frame End Element. */
    private static final int ID_END                = 7;

    // Constants of the adaptive Golomb decoder
    private static final int QBSHIFT               = 9;
    private static final int QB                    = 1 << QBSHIFT;
    private static final int MMULSHIFT             = 2;
    private static final int MDENSHIFT             = QBSHIFT - MMULSHIFT - 1;
    private static final int MOFF                  = 1 << MDENSHIFT - 2;
    private static final int BITOFF                = 24;
    private static final int MAX_PREFIX_16         = 9;
    private static final int MAX_PREFIX_32         = 9;
    private static final int MAX_DATATYPE_BITS_16  = 16;
    private static final int N_MAX_MEAN_CLAMP      = 0xFFFF;
    private static final int N_MEAN_CLAMP_VAL      = 0xFFFF;

    private static final int MAX_SUPPORTED_VERSION = 0;

    // The ALAC specific configuration from the magic cookie
    private final int        frameLength;
    private final int        bitDepth;
    private final int        pb;
    private final int        mb;
    private final int        kb;
    private final int        numChannels;
    @SuppressWarnings("unused")
    private final int        maxRun;

    // The working buffers
    private final int []     mixBufferU;
    private final int []     mixBufferV;
    private final int []     predictor;
    private final int []     shiftBuffer;
    private final short []   coefsU                = new short [32];
    private final short []   coefsV                = new short [32];


    /**
     * Constructor. Parses the ALAC specific configuration from the magic cookie.
     *
     * @param magicCookie The magic cookie (e.g. from the 'kuki' chunk of a CAF file)
     * @throws IOException The magic cookie is malformed or the version is not supported
     */
    public AlacDecoder (final byte [] magicCookie) throws IOException
    {
        int offset = 0;
        int remaining = magicCookie.length;

        // Skip the optional format ('frma') and 'alac' atoms which older encoders put in front
        // of the configuration
        if (remaining >= 12 && magicCookie[offset + 4] == 'f' && magicCookie[offset + 5] == 'r' && magicCookie[offset + 6] == 'm' && magicCookie[offset + 7] == 'a')
        {
            offset += 12;
            remaining -= 12;
        }
        if (remaining >= 12 && magicCookie[offset + 4] == 'a' && magicCookie[offset + 5] == 'l' && magicCookie[offset + 6] == 'a' && magicCookie[offset + 7] == 'c')
        {
            offset += 12;
            remaining -= 12;
        }

        if (remaining < 24)
            throw new IOException ("ALAC magic cookie is too short.");

        this.frameLength = readInt32 (magicCookie, offset);
        final int compatibleVersion = magicCookie[offset + 4] & 0xFF;
        this.bitDepth = magicCookie[offset + 5] & 0xFF;
        this.pb = magicCookie[offset + 6] & 0xFF;
        this.mb = magicCookie[offset + 7] & 0xFF;
        this.kb = magicCookie[offset + 8] & 0xFF;
        this.numChannels = magicCookie[offset + 9] & 0xFF;
        this.maxRun = (magicCookie[offset + 10] & 0xFF) << 8 | magicCookie[offset + 11] & 0xFF;

        if (compatibleVersion > MAX_SUPPORTED_VERSION)
            throw new IOException ("Unsupported ALAC version: " + compatibleVersion);
        if (this.frameLength <= 0 || this.numChannels <= 0)
            throw new IOException ("ALAC magic cookie is malformed.");

        this.mixBufferU = new int [this.frameLength];
        this.mixBufferV = new int [this.frameLength];
        this.predictor = new int [this.frameLength];
        this.shiftBuffer = new int [this.frameLength * 2];
    }


    /**
     * Get the number of bits of one sample.
     *
     * @return The number of bits (16, 20, 24 or 32)
     */
    public int getBitDepth ()
    {
        return this.bitDepth;
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
     * Get the number of sample frames contained in a full packet.
     *
     * @return The number of frames
     */
    public int getFrameLength ()
    {
        return this.frameLength;
    }


    /**
     * Decode one packet. The decoded samples are written interleaved and in little-endian byte
     * order into the output buffer (2 bytes per sample for 16-bit, 3 for 24-bit, 4 for 32-bit).
     *
     * @param packetData The data which contains the packet
     * @param packetOffset The offset of the first byte of the packet
     * @param packetLength The number of valid bytes of the packet
     * @param output Where to write the decoded samples
     * @param outputOffset The offset in bytes at which to write into the output buffer
     * @return The number of decoded sample frames
     * @throws IOException The packet is malformed
     */
    public int decodePacket (final byte [] packetData, final int packetOffset, final int packetLength, final byte [] output, final int outputOffset) throws IOException
    {
        // The bit reader loads up to 8 bytes beyond the current position, therefore the data is
        // padded
        final byte [] padded = new byte [packetLength + 8];
        System.arraycopy (packetData, packetOffset, padded, 0, packetLength);
        final BitReader bits = new BitReader (padded, packetLength);

        int numSamples = this.frameLength;
        int channelIndex = 0;

        while (true)
        {
            final int tag = bits.readSmall (3);
            switch (tag)
            {
                case ID_SCE, ID_LFE:
                    numSamples = this.decodeSingleChannel (bits, numSamples, channelIndex, output, outputOffset);
                    channelIndex += 1;
                    break;

                case ID_CPE:
                    if (channelIndex + 2 > this.numChannels)
                        throw new IOException ("Too many channel elements in ALAC packet.");
                    numSamples = this.decodeChannelPair (bits, numSamples, channelIndex, output, outputOffset);
                    channelIndex += 2;
                    break;

                case ID_DSE:
                    // Data stream element - parse but ignore
                    bits.readSmall (4);
                    final int alignFlag = bits.readOne ();
                    int dataCount = bits.readSmall (8);
                    if (dataCount == 255)
                        dataCount += bits.readSmall (8);
                    if (alignFlag != 0)
                        bits.byteAlign ();
                    bits.advance (dataCount * 8);
                    break;

                case ID_FIL:
                    // Fill element - parse but ignore
                    int fillCount = bits.readSmall (4);
                    if (fillCount == 15)
                        fillCount += bits.readSmall (8) - 1;
                    bits.advance (fillCount * 8);
                    break;

                case ID_END:
                    bits.byteAlign ();
                    return numSamples;

                default:
                    // ID_CCE and ID_PCE are unsupported
                    throw new IOException ("Unsupported element in ALAC packet: " + tag);
            }

            if (channelIndex >= this.numChannels)
                return numSamples;
        }
    }


    /**
     * Decode a single channel (SCE or LFE) element.
     *
     * @param bits The bit reader
     * @param requestedSamples The number of samples to decode if the frame is not partial
     * @param channelIndex The index of the output channel
     * @param output The output buffer
     * @param outputOffset The offset in bytes at which to write into the output buffer
     * @return The number of decoded sample frames
     * @throws IOException The element is malformed
     */
    private int decodeSingleChannel (final BitReader bits, final int requestedSamples, final int channelIndex, final byte [] output, final int outputOffset) throws IOException
    {
        // The element instance tag
        bits.readSmall (4);

        if (bits.read (12) != 0)
            throw new IOException ("Malformed ALAC element header.");

        final int headerByte = bits.read (4);
        final int partialFrame = headerByte >> 3;
        int bytesShifted = headerByte >> 1 & 3;
        if (bytesShifted == 3)
            throw new IOException ("Malformed ALAC element header.");
        final int escapeFlag = headerByte & 1;

        final int chanBits = this.bitDepth - bytesShifted * 8;

        int numSamples = requestedSamples;
        if (partialFrame != 0)
            numSamples = bits.read (16) << 16 | bits.read (16);
        if (numSamples < 0 || numSamples > this.frameLength)
            throw new IOException ("Malformed ALAC element header.");

        if (escapeFlag == 0)
        {
            // Compressed frame - the mix values are unused for mono
            bits.read (8);
            bits.read (8);

            int header = bits.read (8);
            final int modeU = header >> 4;
            final int denShiftU = header & 0xF;

            header = bits.read (8);
            final int pbFactorU = header >> 5;
            final int numU = header & 0x1F;
            for (int i = 0; i < numU; i++)
                this.coefsU[i] = (short) bits.read (16);

            // If the shift is active, skip the shift buffer but remember where it starts
            int shiftPosition = 0;
            if (bytesShifted != 0)
            {
                shiftPosition = bits.getPosition ();
                bits.advance (bytesShifted * 8 * numSamples);
            }

            this.dynDecomp (bits, this.pb * pbFactorU / 4, numSamples, chanBits);

            if (modeU == 0)
                unpcBlock (this.predictor, this.mixBufferU, numSamples, this.coefsU, numU, chanBits, denShiftU);
            else
            {
                // The special "numActive == 31" mode can be done in-place
                unpcBlock (this.predictor, this.predictor, numSamples, null, 31, chanBits, 0);
                unpcBlock (this.predictor, this.mixBufferU, numSamples, this.coefsU, numU, chanBits, denShiftU);
            }

            // Now read the shifted values into the shift buffer
            if (bytesShifted != 0)
            {
                final BitReader shiftBits = bits.copyAt (shiftPosition);
                final int shift = bytesShifted * 8;
                for (int i = 0; i < numSamples; i++)
                    this.shiftBuffer[i] = shiftBits.read (shift);
            }
        }
        else
        {
            // Uncompressed frame, copy data into the mix buffer to use common output code
            readUncompressed (bits, chanBits, numSamples, this.mixBufferU);
            bytesShifted = 0;
        }

        // Convert the 32-bit integers into the output buffer
        switch (this.bitDepth)
        {
            case 16:
                for (int i = 0; i < numSamples; i++)
                    write16 (output, outputOffset, i * this.numChannels + channelIndex, this.mixBufferU[i]);
                break;
            case 24:
                final int shift = bytesShifted * 8;
                for (int i = 0; i < numSamples; i++)
                {
                    int value = this.mixBufferU[i];
                    if (bytesShifted != 0)
                        value = value << shift | this.shiftBuffer[i];
                    write24 (output, outputOffset, i * this.numChannels + channelIndex, value);
                }
                break;
            case 32:
                final int shift32 = bytesShifted * 8;
                for (int i = 0; i < numSamples; i++)
                {
                    int value = this.mixBufferU[i];
                    if (bytesShifted != 0)
                        value = value << shift32 | this.shiftBuffer[i];
                    write32 (output, outputOffset, i * this.numChannels + channelIndex, value);
                }
                break;
            default:
                throw new IOException ("Unsupported ALAC bit depth: " + this.bitDepth);
        }

        return numSamples;
    }


    /**
     * Decode a channel pair (CPE) element.
     *
     * @param bits The bit reader
     * @param requestedSamples The number of samples to decode if the frame is not partial
     * @param channelIndex The index of the first output channel
     * @param output The output buffer
     * @param outputOffset The offset in bytes at which to write into the output buffer
     * @return The number of decoded sample frames
     * @throws IOException The element is malformed
     */
    private int decodeChannelPair (final BitReader bits, final int requestedSamples, final int channelIndex, final byte [] output, final int outputOffset) throws IOException
    {
        // The element instance tag
        bits.readSmall (4);

        if (bits.read (12) != 0)
            throw new IOException ("Malformed ALAC element header.");

        final int headerByte = bits.read (4);
        final int partialFrame = headerByte >> 3;
        int bytesShifted = headerByte >> 1 & 3;
        if (bytesShifted == 3)
            throw new IOException ("Malformed ALAC element header.");
        final int escapeFlag = headerByte & 1;

        int chanBits = this.bitDepth - bytesShifted * 8 + 1;

        int numSamples = requestedSamples;
        if (partialFrame != 0)
            numSamples = bits.read (16) << 16 | bits.read (16);
        if (numSamples < 0 || numSamples > this.frameLength)
            throw new IOException ("Malformed ALAC element header.");

        int mixBits = 0;
        int mixRes = 0;

        if (escapeFlag == 0)
        {
            // Compressed frame, read the rest of the parameters
            mixBits = bits.read (8);
            mixRes = (byte) bits.read (8);

            int header = bits.read (8);
            final int modeU = header >> 4;
            final int denShiftU = header & 0xF;
            header = bits.read (8);
            final int pbFactorU = header >> 5;
            final int numU = header & 0x1F;
            for (int i = 0; i < numU; i++)
                this.coefsU[i] = (short) bits.read (16);

            header = bits.read (8);
            final int modeV = header >> 4;
            final int denShiftV = header & 0xF;
            header = bits.read (8);
            final int pbFactorV = header >> 5;
            final int numV = header & 0x1F;
            for (int i = 0; i < numV; i++)
                this.coefsV[i] = (short) bits.read (16);

            // If the shift is active, skip the interleaved shifted values but remember where
            // they start
            int shiftPosition = 0;
            if (bytesShifted != 0)
            {
                shiftPosition = bits.getPosition ();
                bits.advance (bytesShifted * 8 * 2 * numSamples);
            }

            // Decompress and run the predictor for the "left" channel
            this.dynDecomp (bits, this.pb * pbFactorU / 4, numSamples, chanBits);
            if (modeU == 0)
                unpcBlock (this.predictor, this.mixBufferU, numSamples, this.coefsU, numU, chanBits, denShiftU);
            else
            {
                unpcBlock (this.predictor, this.predictor, numSamples, null, 31, chanBits, 0);
                unpcBlock (this.predictor, this.mixBufferU, numSamples, this.coefsU, numU, chanBits, denShiftU);
            }

            // Decompress and run the predictor for the "right" channel
            this.dynDecomp (bits, this.pb * pbFactorV / 4, numSamples, chanBits);
            if (modeV == 0)
                unpcBlock (this.predictor, this.mixBufferV, numSamples, this.coefsV, numV, chanBits, denShiftV);
            else
            {
                unpcBlock (this.predictor, this.predictor, numSamples, null, 31, chanBits, 0);
                unpcBlock (this.predictor, this.mixBufferV, numSamples, this.coefsV, numV, chanBits, denShiftV);
            }

            // Now read the interleaved shifted values into the shift buffer
            if (bytesShifted != 0)
            {
                final BitReader shiftBits = bits.copyAt (shiftPosition);
                final int shift = bytesShifted * 8;
                for (int i = 0; i < numSamples * 2; i += 2)
                {
                    this.shiftBuffer[i] = shiftBits.read (shift);
                    this.shiftBuffer[i + 1] = shiftBits.read (shift);
                }
            }
        }
        else
        {
            // Uncompressed frame, copy the data into the mix buffers to use common output code
            chanBits = this.bitDepth;
            final int shift = 32 - chanBits;
            if (chanBits <= 16)
                for (int i = 0; i < numSamples; i++)
                {
                    this.mixBufferU[i] = bits.read (chanBits) << shift >> shift;
                    this.mixBufferV[i] = bits.read (chanBits) << shift >> shift;
                }
            else
            {
                final int extraBits = chanBits - 16;
                for (int i = 0; i < numSamples; i++)
                {
                    this.mixBufferU[i] = bits.read (16) << 16 >> shift | bits.read (extraBits);
                    this.mixBufferV[i] = bits.read (16) << 16 >> shift | bits.read (extraBits);
                }
            }
            bytesShifted = 0;
        }

        // Un-mix the data and convert it to the output format. A mix resolution of zero means
        // conventional separated stereo.
        final int shift = bytesShifted * 8;
        switch (this.bitDepth)
        {
            case 16:
                for (int i = 0; i < numSamples; i++)
                {
                    final int left;
                    final int right;
                    if (mixRes != 0)
                    {
                        left = this.mixBufferU[i] + this.mixBufferV[i] - (mixRes * this.mixBufferV[i] >> mixBits);
                        right = left - this.mixBufferV[i];
                    }
                    else
                    {
                        left = this.mixBufferU[i];
                        right = this.mixBufferV[i];
                    }
                    write16 (output, outputOffset, i * this.numChannels + channelIndex, left);
                    write16 (output, outputOffset, i * this.numChannels + channelIndex + 1, right);
                }
                break;

            case 24:
                for (int i = 0; i < numSamples; i++)
                {
                    int left;
                    int right;
                    if (mixRes != 0)
                    {
                        left = this.mixBufferU[i] + this.mixBufferV[i] - (mixRes * this.mixBufferV[i] >> mixBits);
                        right = left - this.mixBufferV[i];
                    }
                    else
                    {
                        left = this.mixBufferU[i];
                        right = this.mixBufferV[i];
                    }
                    if (bytesShifted != 0)
                    {
                        left = left << shift | this.shiftBuffer[i * 2];
                        right = right << shift | this.shiftBuffer[i * 2 + 1];
                    }
                    write24 (output, outputOffset, i * this.numChannels + channelIndex, left);
                    write24 (output, outputOffset, i * this.numChannels + channelIndex + 1, right);
                }
                break;

            case 32:
                for (int i = 0; i < numSamples; i++)
                {
                    int left;
                    int right;
                    if (mixRes != 0)
                    {
                        left = this.mixBufferU[i] + this.mixBufferV[i] - (mixRes * this.mixBufferV[i] >> mixBits);
                        right = left - this.mixBufferV[i];
                    }
                    else
                    {
                        left = this.mixBufferU[i];
                        right = this.mixBufferV[i];
                    }
                    if (bytesShifted != 0)
                    {
                        left = left << shift | this.shiftBuffer[i * 2];
                        right = right << shift | this.shiftBuffer[i * 2 + 1];
                    }
                    write32 (output, outputOffset, i * this.numChannels + channelIndex, left);
                    write32 (output, outputOffset, i * this.numChannels + channelIndex + 1, right);
                }
                break;

            default:
                throw new IOException ("Unsupported ALAC bit depth: " + this.bitDepth);
        }

        return numSamples;
    }


    /**
     * Read an uncompressed (escaped) mono frame into a mix buffer.
     *
     * @param bits The bit reader
     * @param chanBits The number of bits of one sample
     * @param numSamples The number of samples to read
     * @param mixBuffer Where to store the samples
     */
    private static void readUncompressed (final BitReader bits, final int chanBits, final int numSamples, final int [] mixBuffer)
    {
        final int shift = 32 - chanBits;
        if (chanBits <= 16)
            for (int i = 0; i < numSamples; i++)
                mixBuffer[i] = bits.read (chanBits) << shift >> shift;
        else
        {
            final int extraBits = chanBits - 16;
            for (int i = 0; i < numSamples; i++)
                mixBuffer[i] = bits.read (16) << 16 >> shift | bits.read (extraBits);
        }
    }


    /**
     * Decompress one channel with the adaptive Golomb decoder into the predictor buffer. This is a
     * port of dyn_decomp() of ag_dec.c.
     *
     * @param bits The bit reader
     * @param pbFactor The already scaled 'pb' parameter
     * @param numSamples The number of samples to decode
     * @param maxSize The maximum number of bits of one value
     * @throws IOException The data is malformed
     */
    private void dynDecomp (final BitReader bits, final int pbFactor, final int numSamples, final int maxSize) throws IOException
    {
        final byte [] data = bits.getData ();
        final int maxPosition = bits.getEndPosition ();
        int bitPosition = bits.getPosition ();

        int meanB = this.mb;
        final int wb = (1 << this.kb) - 1;
        int zmode = 0;

        int c = 0;
        while (c < numSamples)
        {
            if (bitPosition >= maxPosition)
                throw new IOException ("Malformed ALAC packet.");

            int m = meanB >>> QBSHIFT;
            final int k = Math.min (lg3a (m), this.kb);
            m = (1 << k) - 1;

            // dyn_get_32bit()
            int n;
            {
                final int streamlong = peek32 (data, bitPosition);
                n = Integer.numberOfLeadingZeros (~streamlong);
                if (n >= MAX_PREFIX_32)
                {
                    if (maxSize > 32)
                        throw new IOException ("Malformed ALAC packet.");
                    n = getStreamBits (data, bitPosition + MAX_PREFIX_32, maxSize);
                    bitPosition += MAX_PREFIX_32 + maxSize;
                }
                else
                {
                    bitPosition += n + 1;
                    if (k != 1)
                    {
                        final int v = streamlong << n + 1 >>> 32 - k;
                        bitPosition += k - 1;
                        n *= m;
                        if (v >= 2)
                        {
                            n += v - 1;
                            bitPosition += 1;
                        }
                    }
                }
            }

            // The least significant bit is the sign bit
            final int ndecode = n + zmode;
            final int multiplier = -(ndecode & 1) | 1;
            this.predictor[c] = (ndecode + 1 >>> 1) * multiplier;
            c++;

            meanB = pbFactor * (n + zmode) + meanB - (pbFactor * meanB >>> QBSHIFT);

            // Update the mean tracking
            if (Integer.compareUnsigned (n, N_MAX_MEAN_CLAMP) > 0)
                meanB = N_MEAN_CLAMP_VAL;

            zmode = 0;

            if (Integer.compareUnsigned (meanB << MMULSHIFT, QB) < 0 && c < numSamples)
            {
                zmode = 1;
                final int kz = Integer.numberOfLeadingZeros (meanB) - BITOFF + (meanB + MOFF >> MDENSHIFT);
                final int mz = (1 << kz) - 1 & wb;

                // dyn_get()
                final int runLength;
                {
                    final int streamlong = peek32 (data, bitPosition);
                    int pre = Integer.numberOfLeadingZeros (~streamlong);
                    if (pre >= MAX_PREFIX_16)
                    {
                        pre = MAX_PREFIX_16;
                        bitPosition += pre;
                        runLength = streamlong << pre >>> 32 - MAX_DATATYPE_BITS_16;
                        bitPosition += MAX_DATATYPE_BITS_16;
                    }
                    else
                    {
                        bitPosition += pre + 1;
                        final int v = kz > 0 ? streamlong << pre + 1 >>> 32 - kz : 0;
                        bitPosition += kz;
                        int result = pre * mz + v - 1;
                        if (Integer.compareUnsigned (v, 2) < 0)
                        {
                            result -= v - 1;
                            bitPosition -= 1;
                        }
                        runLength = result;
                    }
                }

                if (c + runLength > numSamples)
                    throw new IOException ("Malformed ALAC packet.");

                for (int j = 0; j < runLength; j++)
                {
                    this.predictor[c] = 0;
                    c++;
                }

                if (Integer.compareUnsigned (runLength, 65535) >= 0)
                    zmode = 0;

                meanB = 0;
            }
        }

        bits.setPosition (bitPosition);
        if (bitPosition > maxPosition)
            throw new IOException ("Malformed ALAC packet.");
    }


    /**
     * Reverse the prediction of one block. This is a port of unpc_block() of dp_dec.c (the special
     * cases for 4 and 8 active coefficients are covered by the general case).
     *
     * @param pc1 The input buffer with the prediction errors
     * @param out The output buffer, may be the same as the input buffer
     * @param num The number of samples
     * @param coefs The prediction coefficients, updated on the fly
     * @param numactive The number of active coefficients, 0 and 31 are special modes
     * @param chanbits The number of bits of one sample
     * @param denshift The rounding shift
     */
    private static void unpcBlock (final int [] pc1, final int [] out, final int num, final short [] coefs, final int numactive, final int chanbits, final int denshift)
    {
        final int chanshift = 32 - chanbits;

        out[0] = pc1[0];

        if (numactive == 0)
        {
            if (num > 1 && pc1 != out)
                System.arraycopy (pc1, 1, out, 1, num - 1);
            return;
        }

        if (numactive == 31)
        {
            int prev = out[0];
            for (int j = 1; j < num; j++)
            {
                final int del = pc1[j] + prev;
                prev = del << chanshift >> chanshift;
                out[j] = prev;
            }
            return;
        }

        for (int j = 1; j <= numactive; j++)
        {
            final int del = pc1[j] + out[j - 1];
            out[j] = del << chanshift >> chanshift;
        }

        final int denhalf = 1 << denshift - 1;
        final int lim = numactive + 1;

        for (int j = lim; j < num; j++)
        {
            int sum1 = 0;
            final int top = out[j - lim];
            final int base = j - 1;
            for (int k = 0; k < numactive; k++)
                sum1 += coefs[k] * (out[base - k] - top);

            int del = pc1[j];
            int del0 = del;
            final int sg = signOfInt (del);
            del += top + (sum1 + denhalf >> denshift);
            out[j] = del << chanshift >> chanshift;

            if (sg > 0)
                for (int k = numactive - 1; k >= 0; k--)
                {
                    final int dd = top - out[base - k];
                    final int sgn = signOfInt (dd);
                    coefs[k] = (short) (coefs[k] - sgn);
                    del0 -= (numactive - k) * (sgn * dd >> denshift);
                    if (del0 <= 0)
                        break;
                }
            else if (sg < 0)
                for (int k = numactive - 1; k >= 0; k--)
                {
                    final int dd = top - out[base - k];
                    final int sgn = signOfInt (dd);
                    coefs[k] = (short) (coefs[k] + sgn);
                    del0 -= (numactive - k) * (-sgn * dd >> denshift);
                    if (del0 >= 0)
                        break;
                }
        }
    }


    private static int signOfInt (final int i)
    {
        return -i >>> 31 | i >> 31;
    }


    private static int lg3a (final int x)
    {
        return 31 - Integer.numberOfLeadingZeros (x + 3);
    }


    /**
     * Read 32 bits starting at an arbitrary bit position.
     *
     * @param data The data to read from
     * @param bitPosition The position of the first bit to read
     * @return The read bits
     */
    private static int peek32 (final byte [] data, final int bitPosition)
    {
        final int byteOffset = bitPosition >>> 3;
        final long value = (data[byteOffset] & 0xFFL) << 32 | (data[byteOffset + 1] & 0xFFL) << 24 | (data[byteOffset + 2] & 0xFFL) << 16 | (data[byteOffset + 3] & 0xFFL) << 8 | data[byteOffset + 4] & 0xFFL;
        return (int) (value >>> 8 - (bitPosition & 7));
    }


    /**
     * Read up to 32 bits starting at an arbitrary bit position.
     *
     * @param data The data to read from
     * @param bitPosition The position of the first bit to read
     * @param numBits The number of bits to read
     * @return The read bits
     */
    private static int getStreamBits (final byte [] data, final int bitPosition, final int numBits)
    {
        final int result = peek32 (data, bitPosition);
        return numBits == 32 ? result : result >>> 32 - numBits;
    }


    private static int readInt32 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
    }


    private static void write16 (final byte [] output, final int outputOffset, final int sampleIndex, final int value)
    {
        final int offset = outputOffset + sampleIndex * 2;
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >> 8);
    }


    private static void write24 (final byte [] output, final int outputOffset, final int sampleIndex, final int value)
    {
        final int offset = outputOffset + sampleIndex * 3;
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >> 8);
        output[offset + 2] = (byte) (value >> 16);
    }


    private static void write32 (final byte [] output, final int outputOffset, final int sampleIndex, final int value)
    {
        final int offset = outputOffset + sampleIndex * 4;
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >> 8);
        output[offset + 2] = (byte) (value >> 16);
        output[offset + 3] = (byte) (value >> 24);
    }


    /**
     * A big-endian (most significant bit first) bit stream reader.
     */
    private static final class BitReader
    {
        private final byte [] data;
        private final int     endPosition;
        private int           position;


        /**
         * Constructor.
         *
         * @param data The data to read from, padded with at least 8 bytes after the valid data
         * @param validLength The number of valid bytes
         */
        BitReader (final byte [] data, final int validLength)
        {
            this (data, validLength, 0);
        }


        private BitReader (final byte [] data, final int validLength, final int position)
        {
            this.data = data;
            this.endPosition = validLength * 8;
            this.position = position;
        }


        /**
         * Read up to 16 bits.
         *
         * @param numBits The number of bits to read
         * @return The read bits
         */
        int read (final int numBits)
        {
            final int byteOffset = this.position >>> 3;
            int result = (this.data[byteOffset] & 0xFF) << 16 | (this.data[byteOffset + 1] & 0xFF) << 8 | this.data[byteOffset + 2] & 0xFF;
            result = result << (this.position & 7) & 0xFFFFFF;
            this.position += numBits;
            return result >>> 24 - numBits;
        }


        /**
         * Read up to 8 bits.
         *
         * @param numBits The number of bits to read
         * @return The read bits
         */
        int readSmall (final int numBits)
        {
            return this.read (numBits);
        }


        /**
         * Read a single bit.
         *
         * @return The read bit
         */
        int readOne ()
        {
            return this.read (1);
        }


        /**
         * Skip the given number of bits.
         *
         * @param numBits The number of bits to skip
         */
        void advance (final int numBits)
        {
            this.position += numBits;
        }


        /**
         * Advance to the next byte boundary.
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
         * Set the current position.
         *
         * @param position The position in bits
         */
        void setPosition (final int position)
        {
            this.position = position;
        }


        /**
         * Get the position after the last valid bit.
         *
         * @return The end position in bits
         */
        int getEndPosition ()
        {
            return this.endPosition;
        }


        /**
         * Get the underlying data.
         *
         * @return The data
         */
        byte [] getData ()
        {
            return this.data;
        }


        /**
         * Create a copy of this reader at the given position.
         *
         * @param position The position in bits
         * @return The copy
         */
        BitReader copyAt (final int position)
        {
            return new BitReader (this.data, this.endPosition / 8, position);
        }
    }
}
