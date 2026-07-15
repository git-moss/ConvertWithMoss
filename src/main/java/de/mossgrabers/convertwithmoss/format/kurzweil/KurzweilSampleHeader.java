// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * One sample header ('soundfile head') of a Kurzweil sample object. Describes one recording: its
 * root key, loop and the position of its 16-bit big-endian PCM words in the sample data region of
 * the file. A sample object contains one header per root key and for stereo samples one pair of
 * headers (left/right) per root key. All positions are counted in 16-bit words; the end position
 * is inclusive.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilSampleHeader
{
    /** The length of a sample header in bytes. */
    public static final int   LENGTH                 = 32;

    private static final int  FLAG_NOT_LOOPED        = 0x80;
    private static final int  FLAG_DATA_PRESENT      = 0x40;
    /** Loop on, data present, RAM based - the flags KurzFiler writes for imported samples. */
    private static final int  FLAGS_IMPORTED         = 0x70;

    /** The maximum playback rate of the device in Hertz, limits the upward transposition. */
    private static final long MAX_PLAYBACK_RATE      = 96000;

    private static final long NANOS_PER_SECOND       = 1000000000L;

    private int               rootKey;
    private int               flags;
    private int               volumeAdjust;
    private int               altVolumeAdjust;
    private int               maxPitch;
    private int               offsetToName;
    private int               sampleStart;
    private int               altSampleStart;
    private int               loopStart;
    private int               sampleEnd;
    private int               offsetToEnvelope;
    private int               altOffsetToEnvelope;
    private int               samplePeriod;

    private byte []           sampleData;


    /**
     * Default constructor.
     */
    public KurzweilSampleHeader ()
    {
        this.flags = FLAGS_IMPORTED;
    }


    /**
     * Constructor. Reads the header from the stream.
     *
     * @param in The input stream to read from
     * @throws IOException Could not read the header
     */
    public KurzweilSampleHeader (final InputStream in) throws IOException
    {
        this.rootKey = in.read ();
        this.flags = in.read ();
        this.volumeAdjust = (byte) in.read ();
        this.altVolumeAdjust = (byte) in.read ();
        this.maxPitch = StreamUtils.readSigned16 (in, true);
        this.offsetToName = StreamUtils.readSigned16 (in, true);
        this.sampleStart = StreamUtils.readSigned32 (in, true);
        this.altSampleStart = StreamUtils.readSigned32 (in, true);
        this.loopStart = StreamUtils.readSigned32 (in, true);
        this.sampleEnd = StreamUtils.readSigned32 (in, true);
        this.offsetToEnvelope = StreamUtils.readSigned16 (in, true);
        this.altOffsetToEnvelope = StreamUtils.readSigned16 (in, true);
        this.samplePeriod = StreamUtils.readSigned32 (in, true);
    }


    /**
     * Extract the sample data of this header from the sample data region of the file and
     * normalize all positions to be relative to the extracted data.
     *
     * @param fileData The content of the whole file
     * @param dataOffset The offset of the sample data region in the file
     * @return True if the data could be extracted
     */
    public boolean extractSampleData (final byte [] fileData, final int dataOffset)
    {
        if (!this.needsSampleData ())
            return true;

        final long startByte = dataOffset + 2L * this.sampleStart;
        final long endByte = dataOffset + 2L * (this.sampleEnd + 1L);
        if (this.sampleStart < 0 || this.sampleEnd < this.sampleStart || endByte > fileData.length)
            return false;

        this.sampleData = new byte [(int) (endByte - startByte)];
        System.arraycopy (fileData, (int) startByte, this.sampleData, 0, this.sampleData.length);

        this.sampleEnd -= this.sampleStart;
        this.loopStart = Math.clamp (this.loopStart - this.sampleStart, 0, this.sampleEnd);
        this.altSampleStart -= this.sampleStart;
        if (this.altSampleStart < 0 || this.altSampleStart > this.sampleEnd)
            this.altSampleStart = 0;
        this.sampleStart = 0;
        return true;
    }


    /**
     * Write the header to the stream. The positions are written relative to the given word offset
     * at which the sample data of this header will be placed in the sample data region.
     *
     * @param out The output stream to write to
     * @param wordOffset The word offset of the data of this header in the sample data region
     * @throws IOException Could not write the header
     */
    public void write (final OutputStream out, final int wordOffset) throws IOException
    {
        out.write (this.rootKey);
        out.write (this.flags);
        out.write (this.volumeAdjust);
        out.write (this.altVolumeAdjust);
        StreamUtils.writeSigned16 (out, this.maxPitch, true);
        StreamUtils.writeSigned16 (out, this.offsetToName, true);
        StreamUtils.writeSigned32 (out, wordOffset + this.sampleStart, true);
        StreamUtils.writeSigned32 (out, wordOffset + this.altSampleStart, true);
        StreamUtils.writeSigned32 (out, wordOffset + this.loopStart, true);
        StreamUtils.writeSigned32 (out, wordOffset + this.sampleEnd, true);
        StreamUtils.writeSigned16 (out, this.offsetToEnvelope, true);
        StreamUtils.writeSigned16 (out, this.altOffsetToEnvelope, true);
        StreamUtils.writeSigned32 (out, this.samplePeriod, true);
    }


    /**
     * Does the header reference sample data stored in the file? If false the header references
     * device ROM which cannot be converted.
     *
     * @return True if the sample data is stored in the file
     */
    public boolean needsSampleData ()
    {
        return (this.flags & FLAG_DATA_PRESENT) > 0;
    }


    /**
     * Is there extracted/assigned sample data?
     *
     * @return True if sample data is present
     */
    public boolean hasSampleData ()
    {
        return this.sampleData != null;
    }


    /**
     * Is the sample looped? The flag is inverted in the file. A 'loop' which only consists of the
     * last word (as written for one-shots) is not considered a loop.
     *
     * @return True if looped
     */
    public boolean isLooped ()
    {
        return (this.flags & FLAG_NOT_LOOPED) == 0 && this.loopStart < this.sampleEnd;
    }


    /**
     * Get the sample rate calculated from the sample period. Values which are off by rounding are
     * snapped to the matching standard rate.
     *
     * @return The sample rate in Hertz
     */
    public int getSampleRate ()
    {
        if (this.samplePeriod <= 0)
            return 44100;
        final int rate = (int) Math.round ((double) NANOS_PER_SECOND / this.samplePeriod);
        for (final int standardRate: new int []
        {
            8000,
            11025,
            16000,
            22050,
            24000,
            32000,
            44100,
            48000,
            96000
        })
            if (Math.abs (rate - standardRate) <= 2)
                return standardRate;
        return rate;
    }


    /**
     * Set the sample period from a sample rate.
     *
     * @param sampleRate The sample rate in Hertz
     */
    public void setSampleRate (final int sampleRate)
    {
        this.samplePeriod = (int) Math.ceil ((double) NANOS_PER_SECOND / sampleRate);
    }


    /**
     * Get the volume adjustment of the sample. The file stores it in 0.5 dB steps (-64.0 to +63.5
     * dB, as documented on the MISC page of the sample editor).
     *
     * @return The volume adjustment in dB
     */
    public double getVolumeAdjust ()
    {
        return this.volumeAdjust / 2.0;
    }


    /**
     * Set the volume adjustment of the sample.
     *
     * @param volumeAdjustDB The volume adjustment in dB, clamped to -64.0..+63.5
     */
    public void setVolumeAdjust (final double volumeAdjustDB)
    {
        this.volumeAdjust = (int) Math.clamp (Math.round (volumeAdjustDB * 2.0), -128, 127);
    }


    /**
     * Get the root key.
     *
     * @return The MIDI note of the recorded pitch
     */
    public int getRootKey ()
    {
        return this.rootKey;
    }


    /**
     * Set the root key. Updates the maximum pitch which depends on it (the sample can be
     * transposed upwards until the playback rate reaches 96kHz). Set the sample rate first!
     *
     * @param rootKey The MIDI note of the recorded pitch
     */
    public void setRootKey (final int rootKey)
    {
        this.rootKey = rootKey;
        this.maxPitch = (int) Math.ceil (1200.0 * Math.log (MAX_PLAYBACK_RATE / (double) NANOS_PER_SECOND * this.samplePeriod) / Math.log (2.0)) + 100 * rootKey - 1200;
    }


    /**
     * Get the start of the playback in words relative to the sample data.
     *
     * @return The start position
     */
    public int getSampleStart ()
    {
        return this.sampleStart;
    }


    /**
     * Get the start of the loop in words relative to the sample data.
     *
     * @return The loop start position
     */
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /**
     * Set the start of the loop in words relative to the sample data. The loop always ends at the
     * sample end. Setting the loop start to the sample end effectively disables the loop.
     *
     * @param loopStart The loop start position
     */
    public void setLoopStart (final int loopStart)
    {
        this.loopStart = loopStart;
    }


    /**
     * Get the inclusive end of the sample in words relative to the sample data.
     *
     * @return The end position
     */
    public int getSampleEnd ()
    {
        return this.sampleEnd;
    }


    /**
     * Get the number of sample words (= mono frames).
     *
     * @return The number of words
     */
    public int getNumberOfFrames ()
    {
        return this.sampleEnd - this.sampleStart + 1;
    }


    /**
     * Get the 16-bit big-endian PCM sample data.
     *
     * @return The sample data or null if the header references ROM
     */
    public byte [] getSampleData ()
    {
        return this.sampleData;
    }


    /**
     * Set the 16-bit big-endian PCM sample data. Updates the sample end accordingly.
     *
     * @param sampleData The sample data
     */
    public void setSampleData (final byte [] sampleData)
    {
        this.sampleData = sampleData;
        this.sampleStart = 0;
        this.sampleEnd = sampleData.length / 2 - 1;
        this.loopStart = this.sampleEnd;
        this.flags = FLAGS_IMPORTED;
    }


    /**
     * Set the byte offsets from the two envelope offset fields to the natural envelope of the
     * sample object.
     *
     * @param offsetToEnvelope The offset for the normal envelope field
     * @param altOffsetToEnvelope The offset for the alternative envelope field
     */
    public void setEnvelopeOffsets (final int offsetToEnvelope, final int altOffsetToEnvelope)
    {
        this.offsetToEnvelope = offsetToEnvelope;
        this.altOffsetToEnvelope = altOffsetToEnvelope;
    }
}
