// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A SF2 sample descriptor.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2SampleDescriptor
{
    /** A mono sample. */
    public static final int MONO       = 1;
    /** The right side mono sample of a stereo sample. */
    public static final int RIGHT      = 2;
    /** The left side mono sample of a stereo sample. */
    public static final int LEFT       = 4;
    /** A linked sample. */
    public static final int LINKED     = 8;
    /** A mono sample located in the ROM. */
    public static final int ROM_MONO   = 32769;
    /** The right side mono sample of a stereo sample located in the ROM. */
    public static final int ROM_RIGHT  = 32770;
    /** The left side mono sample of a stereo sample located in the ROM. */
    public static final int ROM_LEFT   = 32772;
    /** A linked sample located in the ROM. */
    public static final int ROM_LINKED = 32776;

    private final int       sampleIndex;
    private final byte []   sampleData;
    private final byte []   sample24Data;

    private String          name;
    private long            start;
    private long            end;
    private long            startLoop;
    private long            endLoop;
    private long            sampleRate;
    private int             originalPitch;
    private int             pitchCorrection;
    private int             sampleLink;
    private int             sampleType;


    /**
     * Constructor.
     *
     * @param sampleIndex The index of the sample
     * @param sampleData The sample data bytes
     * @param sample24Data The additional sample data bytes for 24 bit samples
     */
    public Sf2SampleDescriptor (final int sampleIndex, final byte [] sampleData, final byte [] sample24Data)
    {
        this.sampleIndex = sampleIndex;
        this.sampleData = sampleData;
        this.sample24Data = sample24Data;
    }


    /**
     * Reads the data from a sample header chunk.
     *
     * @param offset The offset to start reading
     * @param chunk The chunk to read
     * @throws ParseException Error parsing the values
     */
    public void readHeader (final int offset, final RIFFChunk chunk) throws ParseException
    {
        final byte [] data = chunk.getData ();

        int pos = 0;
        while (pos < 20 && data[offset + pos] != 0)
            pos++;
        this.name = new String (data, offset, pos, StandardCharsets.US_ASCII).trim ();

        this.start = chunk.getFourBytesAsInt (offset + 20);
        this.end = chunk.getFourBytesAsInt (offset + 24);
        this.startLoop = chunk.getFourBytesAsInt (offset + 28);
        this.endLoop = chunk.getFourBytesAsInt (offset + 32);

        this.sampleRate = chunk.getFourBytesAsInt (offset + 36);

        this.originalPitch = chunk.getByteAsUnsignedInt (offset + 40);
        this.pitchCorrection = chunk.getByteAsSignedInt (offset + 41);

        this.sampleLink = chunk.getByteAsUnsignedInt (offset + 42);

        this.sampleType = chunk.getTwoBytesAsInt (offset + 44);
        if (this.sampleType >= LINKED)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_UNSUPPORTED_SAMPLE_TYPE"));
    }


    /**
     * Write the data to a sample header chunk.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void writeHeader (final ByteArrayOutputStream out) throws IOException
    {
        StreamUtils.writeASCII (out, StringUtils.optimizeName (StringUtils.fixASCII (this.name), 20), 20);
        StreamUtils.writeUnsigned32 (out, this.start, false);
        StreamUtils.writeUnsigned32 (out, this.end, false);
        StreamUtils.writeUnsigned32 (out, this.startLoop, false);
        StreamUtils.writeUnsigned32 (out, this.endLoop, false);
        StreamUtils.writeUnsigned32 (out, this.sampleRate, false);
        out.write (this.originalPitch);
        out.write (this.pitchCorrection);
        StreamUtils.writeUnsigned16 (out, this.sampleLink, false);
        StreamUtils.writeUnsigned16 (out, this.sampleType, false);
    }


    /**
     * Write the terminal sample header chunk.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public static void writeLastHeader (final ByteArrayOutputStream out) throws IOException
    {
        StreamUtils.writeASCII (out, "EOS", 20);
        StreamUtils.writeUnsigned32 (out, 0, false);
        StreamUtils.writeUnsigned32 (out, 0, false);
        StreamUtils.writeUnsigned32 (out, 0, false);
        StreamUtils.writeUnsigned32 (out, 0, false);
        StreamUtils.writeUnsigned32 (out, 0, false);
        out.write (0);
        out.write (0);
        StreamUtils.writeUnsigned16 (out, 0, false);
        StreamUtils.writeUnsigned16 (out, 0, false);
    }


    /**
     * Get the index of the sample.
     *
     * @return The index of the sample
     */
    public int getSampleIndex ()
    {
        return this.sampleIndex;
    }


    /**
     * Get the name of the sample.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Set the name of the sample.
     *
     * @param name The name
     */
    public void setName (final String name)
    {
        this.name = name;
    }


    /**
     * The index, in sample data points, from the beginning of the sample data field to the first
     * data point of this sample.
     *
     * @return The start index
     */
    public long getStart ()
    {
        return this.start;
    }


    /**
     * The index, in sample data points, from the beginning of the sample data field to the first
     * data point of this sample.
     *
     * @param start The start index
     */
    public void setStart (final long start)
    {
        this.start = start;
    }


    /**
     * The index, in sample data points, from the beginning of the sample data field to the first of
     * the set of 46 zero valued data points following this sample.
     *
     * @return The end index
     */
    public long getEnd ()
    {
        return this.end;
    }


    /**
     * Set the index, in sample data points, from the beginning of the sample data field to the
     * first of the set of 46 zero valued data points following this sample.
     *
     * @param end The end index
     */
    public void setEnd (final long end)
    {
        this.end = end;
    }


    /**
     * The index, in sample data points, from the beginning of the sample data field to the first
     * data point in the loop of this sample.
     *
     * @return The start loop index
     */
    public long getLoopStart ()
    {
        return this.startLoop;
    }


    /**
     * Set the index, in sample data points, from the beginning of the sample data field to the
     * first data point in the loop of this sample.
     *
     * @param startLoop The start loop index
     */
    public void setLoopStart (final long startLoop)
    {
        this.startLoop = startLoop;
    }


    /**
     * The index, in sample data points, from the beginning of the sample data field to the first
     * data point following the loop of this sample.
     *
     * @return The end loop index
     */
    public long getLoopEnd ()
    {
        return this.endLoop;
    }


    /**
     * Set the index, in sample data points, from the beginning of the sample data field to the
     * first data point following the loop of this sample.
     *
     * @param endLoop The end loop index
     */
    public void setLoopEnd (final long endLoop)
    {
        this.endLoop = endLoop;
    }


    /**
     * The sample rate, in Hertz, at which this sample was acquired or to which it was most recently
     * converted.
     *
     * @return The sample rate
     */
    public long getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * set the sample rate, in Hertz, at which this sample was acquired or to which it was most
     * recently converted.
     *
     * @param sampleRate The sample rate
     */
    public void setSampleRate (final long sampleRate)
    {
        this.sampleRate = sampleRate;
    }


    /**
     * Get the MIDI key number of the recorded pitch of the sample.
     *
     * @return The MIDI number (0-127)
     */
    public int getOriginalPitch ()
    {
        return this.originalPitch;
    }


    /**
     * Set the MIDI key number of the recorded pitch of the sample.
     *
     * @param pitch The MIDI number (0-127)
     */
    public void setOriginalPitch (final int pitch)
    {
        this.originalPitch = pitch;
    }


    /**
     * Get the pitch correction in cents that should be applied to the sample on playback.
     *
     * @return The pitch correction
     */
    public int getPitchCorrection ()
    {
        return this.pitchCorrection;
    }


    /**
     * Set the pitch correction in cents that should be applied to the sample on playback.
     *
     * @param pitchCorrection The pitch correction
     */
    public void setPitchCorrection (final int pitchCorrection)
    {
        this.pitchCorrection = pitchCorrection;
    }


    /**
     * Get the ID of the linked left or right sample, if any.
     *
     * @return The ID of the linked sample
     */
    public int getLinkedSample ()
    {
        return this.sampleLink;
    }


    /**
     * Set the ID of the linked left or right sample, if any.
     *
     * @param linkedID The ID of the linked sample
     */
    public void setLinkedSample (final int linkedID)
    {
        this.sampleLink = linkedID;
    }


    /**
     * Get the type of the sample.
     *
     * @return monoSample = 1, rightSample = 2, leftSample = 4, all other types are not supported
     *         (linked and ROM)
     */
    public int getSampleType ()
    {
        return this.sampleType;
    }


    /**
     * Set the type of the sample.
     *
     * @param sampleType monoSample = 1, rightSample = 2, leftSample = 4, all other types are not
     *            supported (linked and ROM)
     */
    public void setSampleType (final int sampleType)
    {
        this.sampleType = sampleType;
    }


    /**
     * Get the raw sample data (16 bit).
     *
     * @return The sampleData
     */
    public byte [] getSampleData ()
    {
        return this.sampleData;
    }


    /**
     * Get the additional 8 bit to form 24 bit.
     *
     * @return The additional 8 bit or null if it is a 16 bit sample
     */
    public byte [] getSample24Data ()
    {
        return this.sample24Data;
    }
}
