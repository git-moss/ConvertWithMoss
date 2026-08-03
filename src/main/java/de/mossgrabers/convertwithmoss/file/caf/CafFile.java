// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.caf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.aac.AacDecoder;
import de.mossgrabers.convertwithmoss.file.alac.AlacDecoder;


/**
 * Apple Core Audio Format (CAF) - a container for storing audio data. A CAF file consists of a file
 * header followed by chunks. Each chunk consists of a four character code, a signed 64-bit length
 * and the chunk data. All fields are stored in big-endian byte order.
 *
 * @author Jürgen Moßgraber
 */
public class CafFile
{
    private static final String       FILE_HEADER_ID          = "caff";

    private static final String       CHUNK_AUDIO_DESCRIPTION = "desc";
    private static final String       CHUNK_AUDIO_DATA        = "data";
    private static final String       CHUNK_INSTRUMENT        = "inst";
    private static final String       CHUNK_MARKER            = "mark";
    private static final String       CHUNK_REGION            = "regn";
    private static final String       CHUNK_INFORMATION       = "info";
    private static final String       CHUNK_PACKET_TABLE      = "pakt";
    private static final String       CHUNK_MAGIC_COOKIE      = "kuki";

    /** The 'artist' key of the Information chunk. */
    public static final String        INFORMATION_ARTIST      = "artist";
    /** The 'comments' key of the Information chunk. */
    public static final String        INFORMATION_COMMENTS    = "comments";
    /** The 'copyright' key of the Information chunk. */
    public static final String        INFORMATION_COPYRIGHT   = "copyright";

    /** The size of an IMA4 packet per channel in bytes. */
    private static final int          IMA4_BYTES_PER_PACKET   = 34;
    /** The number of sample frames encoded in an IMA4 packet. */
    private static final int          IMA4_FRAMES_PER_PACKET  = 64;

    private static final int []       IMA_INDEX_TABLE         =
    {
        -1,
        -1,
        -1,
        -1,
        2,
        4,
        6,
        8,
        -1,
        -1,
        -1,
        -1,
        2,
        4,
        6,
        8
    };

    private static final int []       IMA_STEP_TABLE          =
    {
        7,
        8,
        9,
        10,
        11,
        12,
        13,
        14,
        16,
        17,
        19,
        21,
        23,
        25,
        28,
        31,
        34,
        37,
        41,
        45,
        50,
        55,
        60,
        66,
        73,
        80,
        88,
        97,
        107,
        118,
        130,
        143,
        157,
        173,
        190,
        209,
        230,
        253,
        279,
        307,
        337,
        371,
        408,
        449,
        494,
        544,
        598,
        658,
        724,
        796,
        876,
        963,
        1060,
        1166,
        1282,
        1411,
        1552,
        1707,
        1878,
        2066,
        2272,
        2499,
        2749,
        3024,
        3327,
        3660,
        4026,
        4428,
        4871,
        5358,
        5894,
        6484,
        7132,
        7845,
        8630,
        9493,
        10442,
        11487,
        12635,
        13899,
        15289,
        16818,
        18500,
        20350,
        22385,
        24623,
        27086,
        29794,
        32767
    };

    private CafAudioDescriptionChunk  audioDescriptionChunk   = null;
    private CafInstrumentChunk        instrumentChunk         = null;
    private byte []                   audioData               = null;
    private byte []                   magicCookie             = null;
    private int []                    packetSizes             = null;
    private final List<CafMarker>     markers                 = new ArrayList<> ();
    private final List<CafRegion>     regions                 = new ArrayList<> ();
    private final Map<String, String> information             = new TreeMap<> ();
    private long                      numberOfValidFrames     = -1;
    private int                       primingFrames           = 0;
    private AlacDecoder               alacDecoder             = null;
    private boolean                   alacDecoderFailed       = false;
    private AacDecoder                aacDecoder              = null;
    private boolean                   aacDecoderFailed        = false;


    /**
     * Constructor. Reads the given CAF file.
     *
     * @param cafFile The CAF file
     * @throws IOException Could not read the file
     */
    public CafFile (final File cafFile) throws IOException
    {
        try (final FileInputStream stream = new FileInputStream (cafFile))
        {
            this.read (stream);
        }
    }


    /**
     * Constructor. Use in combination with the read-method to read a CAF file from a stream or with
     * the setters and the write-method to create a CAF file.
     */
    public CafFile ()
    {
        // Intentionally empty
    }


    /**
     * Read a CAF file from a stream.
     *
     * @param inputStream The input stream which provides the CAF file
     * @throws IOException Could not read the file
     */
    public void read (final InputStream inputStream) throws IOException
    {
        final String fileType = StreamUtils.readAscii (inputStream, 4);
        if (!FILE_HEADER_ID.equals (fileType))
            throw new IOException ("Not a CAF file.");
        final int version = StreamUtils.readUnsigned16 (inputStream, true);
        if (version != 1)
            throw new IOException ("Unsupported CAF file version: " + version);
        // The flags field is reserved and must be ignored
        StreamUtils.readUnsigned16 (inputStream, true);

        while (true)
        {
            final byte [] chunkID = inputStream.readNBytes (4);
            if (chunkID.length == 0)
                break;
            if (chunkID.length != 4)
                throw new IOException ("Unexpected end of CAF file.");
            final String chunkType = new String (chunkID, StandardCharsets.US_ASCII);
            final long chunkSize = StreamUtils.readUnsigned64 (inputStream, true);

            // A size of -1 is only allowed for the audio data chunk which must be the last chunk
            // in that case
            if (CHUNK_AUDIO_DATA.equals (chunkType))
            {
                // The edit count field is not used
                StreamUtils.readUnsigned32 (inputStream, true);
                this.audioData = chunkSize < 0 ? inputStream.readAllBytes () : inputStream.readNBytes (checkChunkSize (chunkSize - 4));
                continue;
            }

            if (chunkSize < 0)
                throw new IOException ("Unexpected end of CAF file.");

            switch (chunkType)
            {
                case CHUNK_AUDIO_DESCRIPTION:
                    this.audioDescriptionChunk = new CafAudioDescriptionChunk ();
                    this.audioDescriptionChunk.read (createChunkStream (inputStream, chunkSize));
                    break;

                case CHUNK_INSTRUMENT:
                    this.instrumentChunk = new CafInstrumentChunk ();
                    this.instrumentChunk.read (createChunkStream (inputStream, chunkSize));
                    break;

                case CHUNK_MARKER:
                    final InputStream markerStream = createChunkStream (inputStream, chunkSize);
                    // The SMPTE time type is not used
                    StreamUtils.readUnsigned32 (markerStream, true);
                    final long numberOfMarkers = StreamUtils.readUnsigned32 (markerStream, true);
                    for (long i = 0; i < numberOfMarkers; i++)
                    {
                        final CafMarker marker = new CafMarker ();
                        marker.read (markerStream);
                        this.markers.add (marker);
                    }
                    break;

                case CHUNK_REGION:
                    final InputStream regionStream = createChunkStream (inputStream, chunkSize);
                    // The SMPTE time type is not used
                    StreamUtils.readUnsigned32 (regionStream, true);
                    final long numberOfRegions = StreamUtils.readUnsigned32 (regionStream, true);
                    for (long i = 0; i < numberOfRegions; i++)
                    {
                        final CafRegion region = new CafRegion ();
                        region.read (regionStream);
                        this.regions.add (region);
                    }
                    break;

                case CHUNK_INFORMATION:
                    this.readInformationChunk (createChunkStream (inputStream, chunkSize));
                    break;

                case CHUNK_PACKET_TABLE:
                    final InputStream packetTableStream = createChunkStream (inputStream, chunkSize);
                    final long numberOfPackets = StreamUtils.readUnsigned64 (packetTableStream, true);
                    this.numberOfValidFrames = StreamUtils.readUnsigned64 (packetTableStream, true);
                    this.primingFrames = StreamUtils.readSigned32 (packetTableStream, true);
                    // The remainder frames are not used
                    StreamUtils.readSigned32 (packetTableStream, true);
                    // The packet sizes for formats with a variable packet size, encoded as
                    // big-endian variable-length numbers with 7 bits per byte
                    if (numberOfPackets > 0 && numberOfPackets < Integer.MAX_VALUE)
                    {
                        this.packetSizes = new int [(int) numberOfPackets];
                        for (int i = 0; i < this.packetSizes.length; i++)
                        {
                            int size = 0;
                            int value;
                            do
                            {
                                value = packetTableStream.read ();
                                if (value < 0)
                                    throw new IOException ("Malformed CAF packet table.");
                                size = size << 7 | value & 0x7F;
                            } while ((value & 0x80) > 0);
                            this.packetSizes[i] = size;
                        }
                    }
                    break;

                case CHUNK_MAGIC_COOKIE:
                    this.magicCookie = inputStream.readNBytes (checkChunkSize (chunkSize));
                    break;

                default:
                    inputStream.skipNBytes (chunkSize);
                    break;
            }
        }

        if (this.audioDescriptionChunk == null)
            throw new IOException ("CAF file does not contain an audio description chunk.");
    }


    /**
     * Write the CAF file to a stream. The audio description chunk and the audio data must be set.
     *
     * @param outputStream The output stream to write to
     * @throws IOException Could not write the file
     */
    public void write (final OutputStream outputStream) throws IOException
    {
        outputStream.write (FILE_HEADER_ID.getBytes ());
        StreamUtils.writeUnsigned16 (outputStream, 1, true);
        StreamUtils.writeUnsigned16 (outputStream, 0, true);

        final ByteArrayOutputStream chunkOut = new ByteArrayOutputStream ();
        this.audioDescriptionChunk.write (chunkOut);
        writeChunk (outputStream, CHUNK_AUDIO_DESCRIPTION, chunkOut.toByteArray ());

        if (this.magicCookie != null)
            writeChunk (outputStream, CHUNK_MAGIC_COOKIE, this.magicCookie);

        if (this.packetSizes != null)
        {
            chunkOut.reset ();
            StreamUtils.writeUnsigned64 (chunkOut, this.packetSizes.length, true);
            StreamUtils.writeUnsigned64 (chunkOut, this.numberOfValidFrames, true);
            StreamUtils.writeSigned32 (chunkOut, this.primingFrames, true);
            // The number of unused frames in the last packet
            StreamUtils.writeSigned32 (chunkOut, (int) ((long) this.packetSizes.length * this.audioDescriptionChunk.getFramesPerPacket () - this.primingFrames - this.numberOfValidFrames), true);
            for (final int packetSize: this.packetSizes)
            {
                // Encode as a big-endian variable-length number with 7 bits per byte
                int shift = 28;
                while (shift > 0 && packetSize >> shift == 0)
                    shift -= 7;
                while (shift > 0)
                {
                    chunkOut.write (packetSize >> shift & 0x7F | 0x80);
                    shift -= 7;
                }
                chunkOut.write (packetSize & 0x7F);
            }
            writeChunk (outputStream, CHUNK_PACKET_TABLE, chunkOut.toByteArray ());
        }

        if (this.instrumentChunk != null)
        {
            chunkOut.reset ();
            this.instrumentChunk.write (chunkOut);
            writeChunk (outputStream, CHUNK_INSTRUMENT, chunkOut.toByteArray ());
        }

        if (!this.regions.isEmpty ())
        {
            chunkOut.reset ();
            // No SMPTE time type
            StreamUtils.writeUnsigned32 (chunkOut, 0, true);
            StreamUtils.writeUnsigned32 (chunkOut, this.regions.size (), true);
            for (final CafRegion region: this.regions)
                region.write (chunkOut);
            writeChunk (outputStream, CHUNK_REGION, chunkOut.toByteArray ());
        }

        if (!this.markers.isEmpty ())
        {
            chunkOut.reset ();
            // No SMPTE time type
            StreamUtils.writeUnsigned32 (chunkOut, 0, true);
            StreamUtils.writeUnsigned32 (chunkOut, this.markers.size (), true);
            for (final CafMarker marker: this.markers)
                marker.write (chunkOut);
            writeChunk (outputStream, CHUNK_MARKER, chunkOut.toByteArray ());
        }

        if (!this.information.isEmpty ())
        {
            chunkOut.reset ();
            StreamUtils.writeUnsigned32 (chunkOut, this.information.size (), true);
            for (final Map.Entry<String, String> entry: this.information.entrySet ())
            {
                chunkOut.write (entry.getKey ().getBytes (StandardCharsets.UTF_8));
                chunkOut.write (0);
                chunkOut.write (entry.getValue ().getBytes (StandardCharsets.UTF_8));
                chunkOut.write (0);
            }
            writeChunk (outputStream, CHUNK_INFORMATION, chunkOut.toByteArray ());
        }

        outputStream.write (CHUNK_AUDIO_DATA.getBytes ());
        StreamUtils.writeUnsigned64 (outputStream, this.audioData.length + 4L, true);
        // The edit count
        StreamUtils.writeUnsigned32 (outputStream, 0, true);
        outputStream.write (this.audioData);
    }


    /**
     * Get the audio description chunk.
     *
     * @return The chunk, null if the file was not read
     */
    public CafAudioDescriptionChunk getAudioDescriptionChunk ()
    {
        return this.audioDescriptionChunk;
    }


    /**
     * Set the audio description chunk.
     *
     * @param audioDescriptionChunk The chunk
     */
    public void setAudioDescriptionChunk (final CafAudioDescriptionChunk audioDescriptionChunk)
    {
        this.audioDescriptionChunk = audioDescriptionChunk;
    }


    /**
     * Get the instrument chunk.
     *
     * @return The chunk, null if not present
     */
    public CafInstrumentChunk getInstrumentChunk ()
    {
        return this.instrumentChunk;
    }


    /**
     * Set the instrument chunk.
     *
     * @param instrumentChunk The chunk
     */
    public void setInstrumentChunk (final CafInstrumentChunk instrumentChunk)
    {
        this.instrumentChunk = instrumentChunk;
    }


    /**
     * Get the markers of the marker chunk.
     *
     * @return The markers
     */
    public List<CafMarker> getMarkers ()
    {
        return this.markers;
    }


    /**
     * Get the regions of the region chunk.
     *
     * @return The regions
     */
    public List<CafRegion> getRegions ()
    {
        return this.regions;
    }


    /**
     * Get the region with the given ID.
     *
     * @param regionID The ID of the region to look up
     * @return The region or null if none with the ID is present
     */
    public CafRegion getRegion (final long regionID)
    {
        for (final CafRegion region: this.regions)
            if (region.getRegionID () == regionID)
                return region;
        return null;
    }


    /**
     * Get the key/value pairs of the information chunk.
     *
     * @return The information
     */
    public Map<String, String> getInformation ()
    {
        return this.information;
    }


    /**
     * Get the raw audio data.
     *
     * @return The data of the audio data chunk, null if the file was not read
     */
    public byte [] getAudioData ()
    {
        return this.audioData;
    }


    /**
     * Set the raw audio data.
     *
     * @param audioData The data of the audio data chunk
     */
    public void setAudioData (final byte [] audioData)
    {
        this.audioData = audioData;
    }


    /**
     * Set the magic cookie which contains supplementary data required by certain audio data formats
     * (e.g. the configuration of Apple Lossless).
     *
     * @param magicCookie The data of the magic cookie chunk
     */
    public void setMagicCookie (final byte [] magicCookie)
    {
        this.magicCookie = magicCookie;
    }


    /**
     * Set the packet table for formats with a variable packet size.
     *
     * @param packetSizes The size in bytes of each packet in the audio data
     * @param numberOfValidFrames The number of valid sample frames of the audio data
     * @param primingFrames The number of priming frames (the encoder delay)
     */
    public void setPacketTable (final int [] packetSizes, final long numberOfValidFrames, final int primingFrames)
    {
        this.packetSizes = packetSizes;
        this.numberOfValidFrames = numberOfValidFrames;
        this.primingFrames = primingFrames;
    }


    /**
     * Get the number of sample frames of the audio data.
     *
     * @return The number of frames
     */
    public long getNumberOfFrames ()
    {
        if (this.numberOfValidFrames >= 0)
            return this.numberOfValidFrames;
        if (this.audioData == null || this.audioDescriptionChunk == null)
            return 0;

        final int bytesPerPacket = this.audioDescriptionChunk.getBytesPerPacket ();
        if (bytesPerPacket <= 0)
            return 0;
        final long numberOfPackets = this.audioData.length / bytesPerPacket;
        return numberOfPackets * Math.max (1, this.audioDescriptionChunk.getFramesPerPacket ());
    }


    /**
     * Can the audio data be decoded to PCM? Only linear PCM, IMA4, µLaw, aLaw and Apple Lossless
     * are supported.
     *
     * @return True if the audio data can be decoded
     */
    public boolean canDecodeAudioData ()
    {
        if (this.audioDescriptionChunk == null)
            return false;

        switch (this.audioDescriptionChunk.getFormatID ())
        {
            case CafAudioDescriptionChunk.FORMAT_LINEAR_PCM:
                final int bytesPerSample = this.getBytesPerSample ();
                if (this.audioDescriptionChunk.isFloat ())
                    return bytesPerSample == 4 || bytesPerSample == 8;
                return bytesPerSample >= 1 && bytesPerSample <= 4;

            case CafAudioDescriptionChunk.FORMAT_APPLE_IMA4:
            case CafAudioDescriptionChunk.FORMAT_ULAW:
            case CafAudioDescriptionChunk.FORMAT_ALAW:
                return true;

            case CafAudioDescriptionChunk.FORMAT_APPLE_LOSSLESS:
                final AlacDecoder decoder = this.getAlacDecoder ();
                if (decoder == null || this.packetSizes == null)
                    return false;
                final int bitDepth = decoder.getBitDepth ();
                return bitDepth == 16 || bitDepth == 24 || bitDepth == 32;

            case CafAudioDescriptionChunk.FORMAT_MPEG4_AAC:
                return this.getAacDecoder () != null && this.packetSizes != null;

            default:
                return false;
        }
    }


    /**
     * Decode the audio data to interleaved little-endian PCM data. Integer samples keep their
     * resolution and are signed (including 8-bit ones), 64-bit float samples are converted to
     * 32-bit float ones and the compressed IMA4, µLaw and aLaw formats are decoded to 16-bit. Apple
     * Lossless is decoded to its original resolution.
     *
     * @return The decoded data
     * @throws IOException The format of the audio data is not supported
     */
    public byte [] decodeAudioData () throws IOException
    {
        switch (this.audioDescriptionChunk.getFormatID ())
        {
            case CafAudioDescriptionChunk.FORMAT_LINEAR_PCM:
                return this.decodeLinearPCM ();
            case CafAudioDescriptionChunk.FORMAT_APPLE_IMA4:
                return this.decodeIMA4 ();
            case CafAudioDescriptionChunk.FORMAT_ULAW:
                return this.decodeLaw (false);
            case CafAudioDescriptionChunk.FORMAT_ALAW:
                return this.decodeLaw (true);
            case CafAudioDescriptionChunk.FORMAT_APPLE_LOSSLESS:
                return this.decodeAlac ();
            case CafAudioDescriptionChunk.FORMAT_MPEG4_AAC:
                return this.decodeAac ();
            default:
                throw new IOException ("Unsupported CAF audio data format: " + this.audioDescriptionChunk.getFormatName ());
        }
    }


    /**
     * Get the resolution of the decoded audio data.
     *
     * @return The number of bits of one decoded sample
     */
    public int getDecodedBitsPerSample ()
    {
        if (this.audioDescriptionChunk.isLinearPCM ())
        {
            // 64-bit float samples are converted to 32-bit float ones
            if (this.audioDescriptionChunk.isFloat ())
                return 32;
            return this.getBytesPerSample () * 8;
        }

        if (CafAudioDescriptionChunk.FORMAT_APPLE_LOSSLESS.equals (this.audioDescriptionChunk.getFormatID ()))
        {
            final AlacDecoder decoder = this.getAlacDecoder ();
            if (decoder != null)
                return decoder.getBitDepth ();
        }

        // All other compressed formats are decoded to 16-bit
        return 16;
    }


    /**
     * Is the decoded audio data in 32-bit float format?
     *
     * @return True if the decoded data contains float samples
     */
    public boolean isDecodedFloat ()
    {
        return this.audioDescriptionChunk.isLinearPCM () && this.audioDescriptionChunk.isFloat ();
    }


    /**
     * Get the number of bytes which one sample of one channel occupies.
     *
     * @return The number of bytes
     */
    private int getBytesPerSample ()
    {
        final int channels = this.audioDescriptionChunk.getChannelsPerFrame ();
        if (channels <= 0)
            return 0;
        return this.audioDescriptionChunk.getBytesPerPacket () / Math.max (1, this.audioDescriptionChunk.getFramesPerPacket ()) / channels;
    }


    private byte [] decodeLinearPCM () throws IOException
    {
        final int bytesPerSample = this.getBytesPerSample ();
        final boolean isLittleEndian = this.audioDescriptionChunk.isLittleEndian ();

        if (this.audioDescriptionChunk.isFloat ())
        {
            if (bytesPerSample == 4)
                return isLittleEndian ? this.audioData : swapEndianness (this.audioData, 4);

            if (bytesPerSample == 8)
            {
                // Convert 64-bit float samples to 32-bit float ones
                final int numberOfSamples = this.audioData.length / 8;
                final byte [] result = new byte [numberOfSamples * 4];
                final ByteArrayInputStream in = new ByteArrayInputStream (this.audioData);
                for (int i = 0; i < numberOfSamples; i++)
                {
                    final int bits = Float.floatToIntBits ((float) StreamUtils.readDouble (in, !isLittleEndian));
                    final int offset = i * 4;
                    result[offset] = (byte) bits;
                    result[offset + 1] = (byte) (bits >> 8);
                    result[offset + 2] = (byte) (bits >> 16);
                    result[offset + 3] = (byte) (bits >> 24);
                }
                return result;
            }

            throw new IOException ("Unsupported CAF float sample resolution: " + this.audioDescriptionChunk.getBitsPerChannel () + " bit");
        }

        if (bytesPerSample < 1 || bytesPerSample > 4)
            throw new IOException ("Unsupported CAF sample resolution: " + this.audioDescriptionChunk.getBitsPerChannel () + " bit");

        if (bytesPerSample == 1 || isLittleEndian)
            return this.audioData;
        return swapEndianness (this.audioData, bytesPerSample);
    }


    /**
     * Decode Apple IMA 4:1 ADPCM data to 16-bit PCM. Each packet consists of one 34 byte block per
     * channel: a 2 byte big-endian preamble which contains the predictor and step table index
     * followed by 32 bytes with two 4-bit codes each (the low nibble is the earlier sample). Each
     * block decodes to 64 sample frames. The decoder state carries over from block to block; the
     * preamble is only a checkpoint in which the lowest 7 predictor bits are lost. Therefore, the
     * running state is kept when the checkpoint is consistent with it and only adopted when it is
     * not (e.g. at the start or after a cut).
     *
     * @return The decoded data
     */
    private byte [] decodeIMA4 ()
    {
        final int numberOfChannels = this.audioDescriptionChunk.getChannelsPerFrame ();
        final int bytesPerPacket = IMA4_BYTES_PER_PACKET * numberOfChannels;
        final int numberOfPackets = this.audioData.length / bytesPerPacket;
        final int numberOfFrames = numberOfPackets * IMA4_FRAMES_PER_PACKET;

        final byte [] result = new byte [numberOfFrames * numberOfChannels * 2];
        final int [] predictors = new int [numberOfChannels];
        final int [] stepIndices = new int [numberOfChannels];

        for (int packet = 0; packet < numberOfPackets; packet++)
            for (int channel = 0; channel < numberOfChannels; channel++)
            {
                final int blockOffset = packet * bytesPerPacket + channel * IMA4_BYTES_PER_PACKET;

                final int preamble = (this.audioData[blockOffset] & 0xFF) << 8 | this.audioData[blockOffset + 1] & 0xFF;
                final int checkpointPredictor = (short) (preamble & 0xFF80);
                final int checkpointStepIndex = Math.clamp (preamble & 0x7F, 0, 88);
                if (packet == 0 || checkpointStepIndex != stepIndices[channel] || Math.abs (checkpointPredictor - predictors[channel]) > 0x7F)
                {
                    predictors[channel] = checkpointPredictor;
                    stepIndices[channel] = checkpointStepIndex;
                }

                int predictor = predictors[channel];
                int stepIndex = stepIndices[channel];

                for (int i = 0; i < IMA4_BYTES_PER_PACKET - 2; i++)
                {
                    final int b = this.audioData[blockOffset + 2 + i] & 0xFF;
                    for (int nibbleIndex = 0; nibbleIndex < 2; nibbleIndex++)
                    {
                        final int nibble = nibbleIndex == 0 ? b & 0x0F : b >> 4;

                        final int step = IMA_STEP_TABLE[stepIndex];
                        int difference = step >> 3;
                        if ((nibble & 1) != 0)
                            difference += step >> 2;
                        if ((nibble & 2) != 0)
                            difference += step >> 1;
                        if ((nibble & 4) != 0)
                            difference += step;
                        predictor = Math.clamp ((nibble & 8) != 0 ? predictor - difference : predictor + difference, Short.MIN_VALUE, Short.MAX_VALUE);
                        stepIndex = Math.clamp (stepIndex + (long) IMA_INDEX_TABLE[nibble], 0, 88);

                        final int offset = ((packet * IMA4_FRAMES_PER_PACKET + i * 2 + nibbleIndex) * numberOfChannels + channel) * 2;
                        result[offset] = (byte) predictor;
                        result[offset + 1] = (byte) (predictor >> 8);
                    }
                }

                predictors[channel] = predictor;
                stepIndices[channel] = stepIndex;
            }

        // The last packet might contain less than 64 valid frames
        final long validFrames = this.getNumberOfFrames ();
        final int validLength = (int) Math.min (validFrames * numberOfChannels * 2, result.length);
        if (validLength == result.length)
            return result;
        final byte [] validResult = new byte [validLength];
        System.arraycopy (result, 0, validResult, 0, validLength);
        return validResult;
    }


    /**
     * Decode Apple Lossless (ALAC) data to PCM with the original resolution.
     *
     * @return The decoded data
     * @throws IOException The data is malformed
     */
    private byte [] decodeAlac () throws IOException
    {
        final AlacDecoder decoder = this.getAlacDecoder ();
        if (decoder == null || this.packetSizes == null)
            throw new IOException ("Malformed ALAC data in CAF file.");

        final int numberOfChannels = this.audioDescriptionChunk.getChannelsPerFrame ();
        final int bytesPerSample = decoder.getBitDepth () / 8;
        final int bytesPerFrame = numberOfChannels * bytesPerSample;
        final long totalFrames = this.getNumberOfFrames ();

        final byte [] result = new byte [Math.toIntExact (totalFrames * bytesPerFrame)];
        final byte [] packetResult = new byte [decoder.getFrameLength () * bytesPerFrame];

        int dataOffset = 0;
        long frameOffset = 0;
        for (final int packetSize: this.packetSizes)
        {
            if (frameOffset >= totalFrames)
                break;
            if (dataOffset + packetSize > this.audioData.length)
                throw new IOException ("Malformed ALAC data in CAF file.");

            final int decodedFrames = decoder.decodePacket (this.audioData, dataOffset, packetSize, packetResult, 0);
            final int copyFrames = (int) Math.min (decodedFrames, totalFrames - frameOffset);
            System.arraycopy (packetResult, 0, result, (int) (frameOffset * bytesPerFrame), copyFrames * bytesPerFrame);

            dataOffset += packetSize;
            frameOffset += decodedFrames;
        }

        return result;
    }


    /**
     * Decode MPEG-4 AAC data to 16-bit PCM. The priming frames of the encoder delay are skipped per
     * the packet table.
     *
     * @return The decoded data
     * @throws IOException The data is malformed
     */
    private byte [] decodeAac () throws IOException
    {
        final AacDecoder decoder = this.getAacDecoder ();
        if (decoder == null || this.packetSizes == null)
            throw new IOException ("Malformed AAC data in CAF file.");

        final int numberOfChannels = decoder.getNumberOfChannels ();
        final int bytesPerFrame = numberOfChannels * 2;
        final long totalFrames = this.getNumberOfFrames ();

        final byte [] decoded = new byte [(this.packetSizes.length * AacDecoder.FRAME_LENGTH + this.primingFrames) * bytesPerFrame];
        int dataOffset = 0;
        int frameOffset = 0;
        for (final int packetSize: this.packetSizes)
        {
            if (dataOffset + packetSize > this.audioData.length || (frameOffset + AacDecoder.FRAME_LENGTH) * bytesPerFrame > decoded.length)
                throw new IOException ("Malformed AAC data in CAF file.");
            frameOffset += decoder.decodePacket (this.audioData, dataOffset, packetSize, decoded, frameOffset * bytesPerFrame);
            dataOffset += packetSize;
        }

        // Drop the priming frames and the padding of the last packet
        final int copyFrames = (int) Math.min (totalFrames, frameOffset - (long) this.primingFrames);
        final byte [] result = new byte [Math.max (0, copyFrames) * bytesPerFrame];
        System.arraycopy (decoded, this.primingFrames * bytesPerFrame, result, 0, result.length);
        return result;
    }


    /**
     * Get the decoder for MPEG-4 AAC audio data, created from the magic cookie.
     *
     * @return The decoder or null if the magic cookie is missing or malformed or the profile is not
     *         supported
     */
    private AacDecoder getAacDecoder ()
    {
        if (this.aacDecoder == null && !this.aacDecoderFailed)
        {
            if (this.magicCookie == null)
            {
                this.aacDecoderFailed = true;
                return null;
            }
            try
            {
                this.aacDecoder = new AacDecoder (this.magicCookie);
            }
            catch (final IOException _)
            {
                this.aacDecoderFailed = true;
            }
        }
        return this.aacDecoder;
    }


    /**
     * Get the decoder for Apple Lossless audio data, created from the magic cookie.
     *
     * @return The decoder or null if the magic cookie is missing or malformed
     */
    private AlacDecoder getAlacDecoder ()
    {
        if (this.alacDecoder == null && !this.alacDecoderFailed)
        {
            if (this.magicCookie == null)
            {
                this.alacDecoderFailed = true;
                return null;
            }
            try
            {
                this.alacDecoder = new AlacDecoder (this.magicCookie);
            }
            catch (final IOException _)
            {
                this.alacDecoderFailed = true;
            }
        }
        return this.alacDecoder;
    }


    /**
     * Decode µLaw or aLaw (G.711) data to 16-bit PCM.
     *
     * @param isALaw True to decode aLaw, false to decode µLaw
     * @return The decoded data
     */
    private byte [] decodeLaw (final boolean isALaw)
    {
        final byte [] result = new byte [this.audioData.length * 2];
        for (int i = 0; i < this.audioData.length; i++)
        {
            final int value = isALaw ? decodeALawSample (this.audioData[i] & 0xFF) : decodeULawSample (this.audioData[i] & 0xFF);
            result[i * 2] = (byte) value;
            result[i * 2 + 1] = (byte) (value >> 8);
        }
        return result;
    }


    private static int decodeULawSample (final int sample)
    {
        final int value = ~sample & 0xFF;
        int decoded = ((value & 0x0F) << 3) + 0x84;
        decoded <<= (value & 0x70) >> 4;
        return (value & 0x80) != 0 ? 0x84 - decoded : decoded - 0x84;
    }


    private static int decodeALawSample (final int sample)
    {
        final int value = sample ^ 0x55;
        int decoded = (value & 0x0F) << 4;
        final int segment = (value & 0x70) >> 4;
        switch (segment)
        {
            case 0:
                decoded += 8;
                break;
            case 1:
                decoded += 0x108;
                break;
            default:
                decoded += 0x108;
                decoded <<= segment - 1;
                break;
        }
        return (value & 0x80) != 0 ? decoded : -decoded;
    }


    /**
     * Swap the byte order of all samples.
     *
     * @param data The sample data
     * @param bytesPerSample The number of bytes of one sample
     * @return The swapped data in a new array
     */
    private static byte [] swapEndianness (final byte [] data, final int bytesPerSample)
    {
        final byte [] result = new byte [data.length];
        final int limit = data.length - bytesPerSample;
        for (int offset = 0; offset <= limit; offset += bytesPerSample)
            for (int i = 0; i < bytesPerSample; i++)
                result[offset + i] = data[offset + bytesPerSample - 1 - i];
        return result;
    }


    /**
     * Read the key/value pairs of an information chunk. Both the key and the value are null
     * terminated UTF-8 strings.
     *
     * @param in The chunk data
     * @throws IOException Could not read the data
     */
    private void readInformationChunk (final InputStream in) throws IOException
    {
        final long numberOfEntries = StreamUtils.readUnsigned32 (in, true);
        final byte [] data = in.readAllBytes ();

        int position = 0;
        for (long entry = 0; entry < numberOfEntries && position < data.length; entry++)
        {
            final String key = readNullTerminatedString (data, position);
            position += key.getBytes (StandardCharsets.UTF_8).length + 1;
            if (position >= data.length)
                break;
            final String value = readNullTerminatedString (data, position);
            position += value.getBytes (StandardCharsets.UTF_8).length + 1;
            if (!key.isEmpty ())
                this.information.put (key, value);
        }
    }


    private static String readNullTerminatedString (final byte [] data, final int offset)
    {
        int end = offset;
        while (end < data.length && data[end] != 0)
            end++;
        return new String (data, offset, end - offset, StandardCharsets.UTF_8);
    }


    /**
     * Read the data of one chunk fully and provide it as a stream to prevent that a wrong
     * (application specific) chunk structure de-rails the file parsing.
     *
     * @param inputStream The input stream to read from
     * @param chunkSize The size of the chunk data
     * @return The stream with the chunk data
     * @throws IOException Could not read the data
     */
    private static ByteArrayInputStream createChunkStream (final InputStream inputStream, final long chunkSize) throws IOException
    {
        return new ByteArrayInputStream (inputStream.readNBytes (checkChunkSize (chunkSize)));
    }


    private static int checkChunkSize (final long chunkSize) throws IOException
    {
        if (chunkSize < 0 || chunkSize > Integer.MAX_VALUE)
            throw new IOException ("CAF chunk size out of range: " + chunkSize);
        return (int) chunkSize;
    }


    private static void writeChunk (final OutputStream outputStream, final String chunkType, final byte [] chunkData) throws IOException
    {
        outputStream.write (chunkType.getBytes ());
        StreamUtils.writeUnsigned64 (outputStream, chunkData.length, true);
        outputStream.write (chunkData);
    }
}
