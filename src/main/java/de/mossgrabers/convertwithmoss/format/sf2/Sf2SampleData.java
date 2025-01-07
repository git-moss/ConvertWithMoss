// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.io.IOException;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractSampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2SampleDescriptor;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Data for a Sf2 sample.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2SampleData extends AbstractSampleData
{
    private final Sf2SampleDescriptor sample;
    private Sf2SampleDescriptor       rightSample;
    private final boolean             is24;
    private final long                lengthInSamples;
    private final long                leftLengthInSamples;
    private final long                rightLengthInSamples;


    /**
     * Constructor.
     *
     * @param sample The name of the file where the sample is stored
     * @throws IOException Cannot happen
     */
    public Sf2SampleData (final Sf2SampleDescriptor sample) throws IOException
    {
        this.sample = sample;
        this.rightSample = sample;

        // For 24 bit the data must be present and the length must match
        final byte [] leftSampleData = this.sample.getSampleData ();
        final byte [] leftSample24Data = this.sample.getSample24Data ();
        final byte [] rightSampleData = this.rightSample.getSampleData ();
        final byte [] rightSample24Data = this.rightSample.getSample24Data ();
        this.is24 = leftSample24Data != null && rightSample24Data != null && leftSample24Data.length * 2 == leftSampleData.length && rightSample24Data.length * 2 == rightSampleData.length;

        this.leftLengthInSamples = this.sample.getEnd () - this.sample.getStart ();
        this.rightLengthInSamples = this.rightSample.getEnd () - this.rightSample.getStart ();
        this.lengthInSamples = Math.max (this.leftLengthInSamples, this.rightLengthInSamples);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        final byte [] leftSampleData = this.sample.getSampleData ();
        final byte [] leftSample24Data = this.sample.getSample24Data ();

        final byte [] rightSampleData = this.rightSample.getSampleData ();
        final byte [] rightSample24Data = this.rightSample.getSample24Data ();

        final IAudioMetadata am = this.getAudioMetadata ();
        final int bitsPerSample = am.getBitResolution ();

        // Create an empty wave file with the required resolution and calculated data length
        final WaveFile wavFile = new WaveFile (2, am.getSampleRate (), bitsPerSample, (int) this.lengthInSamples);
        final DataChunk dataChunk = wavFile.getDataChunk ();
        final byte [] data = dataChunk.getData ();

        // Fill in the data
        final int leftStart = (int) this.sample.getStart ();
        final int rightStart = (int) this.rightSample.getStart ();
        if (bitsPerSample == 24)
            // Convert to stereo interleaved format
            for (int i = 0; i < this.lengthInSamples; i++)
            {
                final int dataOffset = 6 * i;
                final int sampleOffset = 2 * i;
                final int leftOffset = 2 * leftStart + sampleOffset;
                final int rightOffset = 2 * rightStart + sampleOffset;

                // Support for different lengths of left/right mono file
                if (i < this.leftLengthInSamples && leftOffset < leftSampleData.length)
                {
                    data[dataOffset] = leftSampleData[leftOffset];
                    data[dataOffset + 1] = leftSampleData[leftOffset + 1];
                    data[dataOffset + 2] = leftSample24Data[leftStart + i];
                }
                if (i < this.rightLengthInSamples && rightOffset < rightSampleData.length)
                {
                    data[dataOffset + 3] = rightSampleData[rightOffset];
                    data[dataOffset + 4] = rightSampleData[rightOffset + 1];
                    data[dataOffset + 5] = rightSample24Data[rightStart + i];
                }
            }
        else
            for (int i = 0; i < this.lengthInSamples; i++)
            {
                final int dataOffset = 4 * i;
                final int sampleOffset = 2 * i;
                final int leftOffset = 2 * leftStart + sampleOffset;
                final int rightOffset = 2 * rightStart + sampleOffset;

                if (i < this.leftLengthInSamples && leftOffset < leftSampleData.length)
                {
                    data[dataOffset] = leftSampleData[leftOffset];
                    data[dataOffset + 1] = leftSampleData[leftOffset + 1];
                }
                if (i < this.rightLengthInSamples && rightOffset < rightSampleData.length)
                {
                    data[dataOffset + 2] = rightSampleData[rightOffset];
                    data[dataOffset + 3] = rightSampleData[rightOffset + 1];
                }
            }

        dataChunk.setData (data);

        wavFile.write (outputStream);
    }


    /**
     * Get the sample description.
     *
     * @return The sample description
     */
    public Sf2SampleDescriptor getSample ()
    {
        return this.sample;
    }


    /**
     * Set the right side for the left side mono sample.
     *
     * @param rightSample The matching right side sample
     */
    public void setRightSample (final Sf2SampleDescriptor rightSample)
    {
        this.rightSample = rightSample;
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        // No further metadata available
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        if (this.audioMetadata == null)
            this.audioMetadata = new DefaultAudioMetadata (2, (int) this.sample.getSampleRate (), this.is24 ? 24 : 16, (int) this.lengthInSamples);
    }
}
