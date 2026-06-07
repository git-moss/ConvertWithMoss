// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 sample parameter (48 bytes).
 *
 * @author Jürgen Moßgraber
 */
public class S770Sample
{
    private final String          sampleName;
    private final S770SamplePoint startSample;
    private final S770SamplePoint sustainLoopStart;
    private final S770SamplePoint sustainLoopEnd;
    private final S770SamplePoint releaseLoopStart;
    private final S770SamplePoint releaseLoopEnd;
    private final int             loopMode;
    private final int             sustainLoopEnable;
    private final int             sustainLoopTune;
    private final int             releaseLoopTune;
    private final int             segmentTop;
    private final int             segmentLength;
    private final int             sampleFrequency;
    private final int             originalKey;
    private byte []               sampleData;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public S770Sample (final InputStream input) throws IOException
    {
        this.sampleName = StreamUtils.readAscii (input, 16);
        this.startSample = new S770SamplePoint (input);
        this.sustainLoopStart = new S770SamplePoint (input);
        this.sustainLoopEnd = new S770SamplePoint (input);
        this.releaseLoopStart = new S770SamplePoint (input);
        this.releaseLoopEnd = new S770SamplePoint (input);
        this.loopMode = StreamUtils.readUnsigned8 (input);
        this.sustainLoopEnable = StreamUtils.readUnsigned8 (input);
        this.sustainLoopTune = StreamUtils.readSigned8 (input);
        this.releaseLoopTune = StreamUtils.readSigned8 (input);
        this.segmentTop = StreamUtils.readUnsigned16 (input, false);
        this.segmentLength = StreamUtils.readUnsigned16 (input, false);
        this.sampleFrequency = StreamUtils.readUnsigned8 (input);
        this.originalKey = StreamUtils.readUnsigned8 (input);
        input.skipNBytes (2);
    }


    /**
     * Get the name of the sample.
     *
     * @return The name
     */
    public String getSampleName ()
    {
        return this.sampleName;
    }


    /**
     * Get the play-back start of the sample
     *
     * @return The start point
     */
    public S770SamplePoint getStartSample ()
    {
        return this.startSample;
    }


    /**
     * Get the play-back loop start of the sample
     *
     * @return The loop start point
     */
    public S770SamplePoint getSustainLoopStart ()
    {
        return this.sustainLoopStart;
    }


    /**
     * Get the play-back loop end of the sample
     *
     * @return The loop end point
     */
    public S770SamplePoint getSustainLoopEnd ()
    {
        return this.sustainLoopEnd;
    }


    /**
     * Get the play-back release loop start point of the sample
     *
     * @return The release loop start point
     */
    public S770SamplePoint getReleaseLoopStart ()
    {
        return this.releaseLoopStart;
    }


    /**
     * Get the play-back release loop end point of the sample
     *
     * @return The release loop end point
     */
    public S770SamplePoint getReleaseLoopEnd ()
    {
        return this.releaseLoopEnd;
    }


    /**
     * Get the loop mode.
     *
     * @return 0=Forward, 1=Fwd+R, 2=Oneshot, 3=Fwd+One, 4=Alt, 5=Rev One, 6=Rev
     */
    public int getLoopMode ()
    {
        return this.loopMode;
    }


    /**
     * Get if the sustain loop is enabled.
     *
     * @return True if enabled
     */
    public int getSustainLoopEnable ()
    {
        return this.sustainLoopEnable;
    }


    /**
     * Get the loop tuning.
     *
     * @return The loop tuning in the range of -50..50 cents
     */
    public int getSustainLoopTune ()
    {
        return this.sustainLoopTune;
    }


    /**
     * Get the release loop tuning.
     *
     * @return The tuning
     */
    public int getReleaseLoopTune ()
    {
        return this.releaseLoopTune;
    }


    /**
     * Get the segment top.
     *
     * @return The segment top
     */
    public int getSegmentTop ()
    {
        return this.segmentTop;
    }


    /**
     * Get the segment length.
     *
     * @return The segment length
     */
    public int getSegmentLength ()
    {
        return this.segmentLength;
    }


    /**
     * Get the sample frequency.
     *
     * @return 0: 48000, 1: 44100, 2: 24000, 3: 22050, 4: 30000, 5: 15000
     */
    public int getSampleFrequency ()
    {
        return this.sampleFrequency;
    }


    /**
     * Get the original key.
     *
     * @return The original key in the range of 21-108
     */
    public int getOriginalKey ()
    {
        return this.originalKey;
    }


    /**
     * Set the wave data samples.
     *
     * @param sampleData The data
     */
    public void setWaveData (final byte [] sampleData)
    {
        this.sampleData = sampleData;
    }


    /**
     * Get the wave data samples.
     *
     * @return The data
     */
    public byte [] getWaveData ()
    {
        return this.sampleData;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770SampleParameter [\n" + "  sampleName='" + this.sampleName.trim () + "'\n" + "  startSample=" + this.startSample + "\n" + "  sustainLoopStart=" + this.sustainLoopStart + "\n" + "  sustainLoopEnd=" + this.sustainLoopEnd + "\n" + "  releaseLoopStart=" + this.releaseLoopStart + "\n" + "  releaseLoopEnd=" + this.releaseLoopEnd + "\n" + "  loopMode=" + this.loopMode + "\n" + "  sustainLoopEnable=" + this.sustainLoopEnable + "\n" + "  sustainLoopTune=" + this.sustainLoopTune + "\n" + "  releaseLoopTune=" + this.releaseLoopTune + "\n" + "  segTop=" + this.segmentTop + "\n" + "  segLength=" + this.segmentLength + "\n" + "  sampleMode=" + this.sampleFrequency + "\n" + "  originalKey=" + this.originalKey + "\n]";
    }
}