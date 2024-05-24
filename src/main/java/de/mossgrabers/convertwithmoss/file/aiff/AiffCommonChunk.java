// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.iff.IffChunk;


/**
 * An AIFF Common Chunk as defined in the AIFF specification.
 *
 * @author Jürgen Moßgraber
 */
public class AiffCommonChunk extends AiffChunk
{
    int    numChannels;
    long   numSampleFrames;
    int    sampleSize;
    int    sampleRate;
    String compressionType = null;
    String compressionName = null;


    /**
     * Constructor.
     *
     * @param chunk The IFF chunk
     */
    protected AiffCommonChunk (final IffChunk chunk)
    {
        super (chunk);
    }


    /**
     * Read the AIFF Common chunk data.
     *
     * @param chunk The chunk to read from
     * @throws IOException Could not read the data
     */
    public void read (final IffChunk chunk) throws IOException
    {
        try (final InputStream in = chunk.streamData ())
        {
            this.numChannels = StreamUtils.readUnsigned16 (in, true);
            this.numSampleFrames = StreamUtils.readUnsigned32 (in, true);
            this.sampleSize = StreamUtils.readUnsigned16 (in, true);
            this.sampleRate = (int) readDouble80 (in.readNBytes (10));

            // Additional AIFC attributes
            if (in.available () > 0)
            {
                this.compressionType = StreamUtils.readASCII (in, 4);
                this.compressionName = StreamUtils.readWithLengthAscii (in).trim ();
                if ((this.compressionName.length () + 1) % 2 == 1)
                    in.skipNBytes (1);
            }
        }
    }


    /**
     * Get the number of channels.
     *
     * @return The number of channels
     */
    public int getNumChannels ()
    {
        return this.numChannels;
    }


    /**
     * Get the number of sample frames.
     *
     * @return The number of sample frames
     */
    public long getNumSampleFrames ()
    {
        return this.numSampleFrames;
    }


    /**
     * Get the size of the sample.
     *
     * @return The size
     */
    public int getSampleSize ()
    {
        return this.sampleSize;
    }


    /**
     * Get the sample rate in Hertz.
     *
     * @return The sample rate
     */
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * Get the compression type if is an AIFC file.
     *
     * @return The type or null, if not compressed
     */
    public String getCompressionType ()
    {
        return this.compressionType;
    }


    /**
     * Get the compression name if is an AIFC file.
     *
     * @return The name or null, if not compressed
     */
    public String getCompressionName ()
    {
        return this.compressionName;
    }


    /**
     * Converts an 80 bit IEEE Standard 754 floating point number to a Java double.
     *
     * @param data The 10 bytes (= 80 bit)
     * @return The converted double value
     */
    private static double readDouble80 (final byte [] data)
    {
        // Extract the sign bit.
        final int sign = data[0] >> 7;

        // Extract the exponent. It's stored with a bias of 16383, so subtract that off
        int exponent = data[0] << 8 | data[1] & 0xFF;
        // Strip sign bit
        exponent &= 0X7FFF;
        // 1 is added to the "real" exponent
        exponent -= 16383 + 62;

        // Extract the mantissa. It's 64 bits of unsigned data, but a long is a signed number, so
        // the LSB needs to be discarded. This division by 2 is the reason for adding an extra 1 to
        // the exponent above.
        int shifter = 55;
        long mantissa = 0;
        for (int i = 2; i < 9; i++)
        {
            mantissa |= (data[i] & 0XFFL) << shifter;
            shifter -= 8;
        }
        mantissa |= data[9] >>> 1;

        // Combine into a floating point number
        final double val = Math.pow (2, exponent) * mantissa;
        return sign == 0 ? val : -val;
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Compression: ");
        if (this.compressionType == null)
            sb.append ("None\n");
        else
            sb.append (this.compressionName).append (" (").append (this.compressionType).append (")\n");

        sb.append ("Number of Channels: ").append (this.numChannels).append ('\n');
        sb.append ("Sample Rate: ").append (this.sampleRate).append ('\n');
        sb.append ("Sample Frames: ").append (this.numSampleFrames).append ('\n');
        sb.append ("Sample Size: ").append (this.sampleSize);
        return sb.toString ();
    }
}
