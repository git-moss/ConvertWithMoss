// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.caf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * The CAF Audio Description chunk. It is required to be the first chunk of a CAF file and
 * describes the format of the audio data.
 *
 * @author Jürgen Moßgraber
 */
public class CafAudioDescriptionChunk
{
    /** Linear PCM. */
    public static final String        FORMAT_LINEAR_PCM         = "lpcm";
    /** Apple's implementation of IMA 4:1 ADPCM. */
    public static final String        FORMAT_APPLE_IMA4         = "ima4";
    /** MPEG-4 AAC. */
    public static final String        FORMAT_MPEG4_AAC          = "aac ";
    /** MACE 3:1. */
    public static final String        FORMAT_MACE3              = "MAC3";
    /** MACE 6:1. */
    public static final String        FORMAT_MACE6              = "MAC6";
    /** µLaw 2:1. */
    public static final String        FORMAT_ULAW               = "ulaw";
    /** aLaw 2:1. */
    public static final String        FORMAT_ALAW               = "alaw";
    /** MPEG-1 or 2, Layer 1 audio. */
    public static final String        FORMAT_MPEG_LAYER_1       = ".mp1";
    /** MPEG-1 or 2, Layer 2 audio. */
    public static final String        FORMAT_MPEG_LAYER_2       = ".mp2";
    /** MPEG-1 or 2, Layer 3 audio. */
    public static final String        FORMAT_MPEG_LAYER_3       = ".mp3";
    /** Apple Lossless. */
    public static final String        FORMAT_APPLE_LOSSLESS     = "alac";
    /** Opus. */
    public static final String        FORMAT_OPUS               = "opus";
    /** FLAC. */
    public static final String        FORMAT_FLAC               = "flac";

    /** Linear PCM format flag: the data is stored as floating point numbers. */
    public static final long          FLAG_IS_FLOAT             = 1;
    /** Linear PCM format flag: the data is stored in little-endian byte order. */
    public static final long          FLAG_IS_LITTLE_ENDIAN     = 2;

    private static final Map<String, String> FORMAT_NAMES       = new HashMap<> ();
    static
    {
        FORMAT_NAMES.put (FORMAT_LINEAR_PCM, "Linear PCM");
        FORMAT_NAMES.put (FORMAT_APPLE_IMA4, "Apple IMA 4:1 ADPCM");
        FORMAT_NAMES.put (FORMAT_MPEG4_AAC, "MPEG-4 AAC");
        FORMAT_NAMES.put (FORMAT_MACE3, "MACE 3:1");
        FORMAT_NAMES.put (FORMAT_MACE6, "MACE 6:1");
        FORMAT_NAMES.put (FORMAT_ULAW, "µLaw 2:1");
        FORMAT_NAMES.put (FORMAT_ALAW, "aLaw 2:1");
        FORMAT_NAMES.put (FORMAT_MPEG_LAYER_1, "MPEG-1/2 Layer 1");
        FORMAT_NAMES.put (FORMAT_MPEG_LAYER_2, "MPEG-1/2 Layer 2");
        FORMAT_NAMES.put (FORMAT_MPEG_LAYER_3, "MPEG-1/2 Layer 3");
        FORMAT_NAMES.put (FORMAT_APPLE_LOSSLESS, "Apple Lossless");
        FORMAT_NAMES.put (FORMAT_OPUS, "Opus");
        FORMAT_NAMES.put (FORMAT_FLAC, "FLAC");
    }

    double sampleRate;
    String formatID;
    long   formatFlags;
    int    bytesPerPacket;
    int    framesPerPacket;
    int    channelsPerFrame;
    int    bitsPerChannel;


    /**
     * Read the chunk data.
     *
     * @param in The input stream to read from
     * @throws IOException Could not read the data
     */
    public void read (final InputStream in) throws IOException
    {
        this.sampleRate = StreamUtils.readDouble (in, true);
        this.formatID = StreamUtils.readAscii (in, 4);
        this.formatFlags = StreamUtils.readUnsigned32 (in, true);
        this.bytesPerPacket = (int) StreamUtils.readUnsigned32 (in, true);
        this.framesPerPacket = (int) StreamUtils.readUnsigned32 (in, true);
        this.channelsPerFrame = (int) StreamUtils.readUnsigned32 (in, true);
        this.bitsPerChannel = (int) StreamUtils.readUnsigned32 (in, true);
    }


    /**
     * Write the chunk data.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeDouble (out, this.sampleRate, true);
        out.write (this.formatID.getBytes ());
        StreamUtils.writeUnsigned32 (out, this.formatFlags, true);
        StreamUtils.writeUnsigned32 (out, this.bytesPerPacket, true);
        StreamUtils.writeUnsigned32 (out, this.framesPerPacket, true);
        StreamUtils.writeUnsigned32 (out, this.channelsPerFrame, true);
        StreamUtils.writeUnsigned32 (out, this.bitsPerChannel, true);
    }


    /**
     * Get the sample rate.
     *
     * @return The number of sample frames per second
     */
    public double getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * Set the sample rate.
     *
     * @param sampleRate The number of sample frames per second
     */
    public void setSampleRate (final double sampleRate)
    {
        this.sampleRate = sampleRate;
    }


    /**
     * Get the format identifier.
     *
     * @return The four character code which identifies the format of the audio data
     */
    public String getFormatID ()
    {
        return this.formatID;
    }


    /**
     * Set the format identifier.
     *
     * @param formatID The four character code which identifies the format of the audio data
     */
    public void setFormatID (final String formatID)
    {
        this.formatID = formatID;
    }


    /**
     * Get the format flags.
     *
     * @return The flags specific to the format
     */
    public long getFormatFlags ()
    {
        return this.formatFlags;
    }


    /**
     * Set the format flags.
     *
     * @param formatFlags The flags specific to the format
     */
    public void setFormatFlags (final long formatFlags)
    {
        this.formatFlags = formatFlags;
    }


    /**
     * Get the number of bytes in a packet of data. Zero for formats with a variable packet size.
     *
     * @return The number of bytes
     */
    public int getBytesPerPacket ()
    {
        return this.bytesPerPacket;
    }


    /**
     * Set the number of bytes in a packet of data.
     *
     * @param bytesPerPacket The number of bytes
     */
    public void setBytesPerPacket (final int bytesPerPacket)
    {
        this.bytesPerPacket = bytesPerPacket;
    }


    /**
     * Get the number of sample frames in each packet of data. Zero for formats with a variable
     * number of frames per packet.
     *
     * @return The number of sample frames
     */
    public int getFramesPerPacket ()
    {
        return this.framesPerPacket;
    }


    /**
     * Set the number of sample frames in each packet of data.
     *
     * @param framesPerPacket The number of sample frames
     */
    public void setFramesPerPacket (final int framesPerPacket)
    {
        this.framesPerPacket = framesPerPacket;
    }


    /**
     * Get the number of channels in each frame of data.
     *
     * @return The number of channels
     */
    public int getChannelsPerFrame ()
    {
        return this.channelsPerFrame;
    }


    /**
     * Set the number of channels in each frame of data.
     *
     * @param channelsPerFrame The number of channels
     */
    public void setChannelsPerFrame (final int channelsPerFrame)
    {
        this.channelsPerFrame = channelsPerFrame;
    }


    /**
     * Get the number of bits of sample data for each channel in a frame. Zero if the data format
     * does not contain separate samples for each channel (e.g. all compressed formats).
     *
     * @return The number of bits
     */
    public int getBitsPerChannel ()
    {
        return this.bitsPerChannel;
    }


    /**
     * Set the number of bits of sample data for each channel in a frame.
     *
     * @param bitsPerChannel The number of bits
     */
    public void setBitsPerChannel (final int bitsPerChannel)
    {
        this.bitsPerChannel = bitsPerChannel;
    }


    /**
     * Is the audio data stored as linear PCM?
     *
     * @return True if the format is linear PCM
     */
    public boolean isLinearPCM ()
    {
        return FORMAT_LINEAR_PCM.equals (this.formatID);
    }


    /**
     * Are the linear PCM samples stored as floating point numbers?
     *
     * @return True if the samples are floating point numbers
     */
    public boolean isFloat ()
    {
        return (this.formatFlags & FLAG_IS_FLOAT) > 0;
    }


    /**
     * Are the linear PCM samples stored in little-endian byte order?
     *
     * @return True if the samples are stored in little-endian byte order
     */
    public boolean isLittleEndian ()
    {
        return (this.formatFlags & FLAG_IS_LITTLE_ENDIAN) > 0;
    }


    /**
     * Get a human readable name of the audio data format.
     *
     * @return The name, the four character code if the format is unknown
     */
    public String getFormatName ()
    {
        final String name = FORMAT_NAMES.get (this.formatID);
        return name == null ? this.formatID : name;
    }
}
