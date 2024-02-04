// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.tools.ui.Functions;

import java.nio.charset.StandardCharsets;


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
    private long            startloop;
    private long            endloop;
    private long            sampleRate;
    private int             originalPitch;
    private int             pitchCorrection;
    private int             sampleLink;
    private int             sampleType;


    /**
     * Constructor.
     *
     * @param sampleIndex The index of the sample
     * @param sample24Data
     * @param sampleData
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

        this.start = chunk.fourBytesAsInt (offset + 20);
        this.end = chunk.fourBytesAsInt (offset + 24);
        this.startloop = chunk.fourBytesAsInt (offset + 28);
        this.endloop = chunk.fourBytesAsInt (offset + 32);

        this.sampleRate = chunk.fourBytesAsInt (offset + 36);

        this.originalPitch = chunk.byteAsUnsignedInt (offset + 40);
        this.pitchCorrection = chunk.byteAsSignedInt (offset + 41);

        this.sampleLink = chunk.byteAsUnsignedInt (offset + 42);

        this.sampleType = chunk.twoBytesAsInt (offset + 44);
        if (this.sampleType >= LINKED)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_UNSUPPORTED_SAMPLE_TYPE"));
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
     * The index, in sample data points, from the beginning of the sample data field to the first
     * data point in the loop of this sample.
     *
     * @return The start loop index
     */
    public long getStartloop ()
    {
        return this.startloop;
    }


    /**
     * The index, in sample data points, from the beginning of the sample data field to the first
     * data point following the loop of this sample.
     *
     * @return The end loop index
     */
    public long getEndloop ()
    {
        return this.endloop;
    }


    /**
     * The sample rate, in hertz, at which this sample was acquired or to which it was most recently
     * converted.
     *
     * @return The sample rate
     */
    public long getSampleRate ()
    {
        return this.sampleRate;
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
     * Get the pitch correction in cents that should be applied to the sample on playback.
     *
     * @return The pitch correction
     */
    public int getPitchCorrection ()
    {
        return this.pitchCorrection;
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
     * The type of the sample.
     *
     * @return monoSample = 1, rightSample = 2, leftSample = 4, all other types are not supported
     *         (linked and ROM)
     */
    public int getSampleType ()
    {
        return this.sampleType;
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
