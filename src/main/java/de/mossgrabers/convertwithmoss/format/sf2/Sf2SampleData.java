// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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
    private int                       rightChannelOffset = 0;
    private boolean                   is24;
    private long                      lengthInSamples;
    private long                      leftLengthInSamples;
    private long                      rightLengthInSamples;


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
        this.updateFormat ();
    }


    private void updateFormat ()
    {
        // For 24 bit the data must be present and the length must match
        final byte [] leftSampleData = this.sample.getSampleData ();
        final byte [] leftSample24Data = this.sample.getSample24Data ();
        final byte [] rightSampleData = this.rightSample.getSampleData ();
        final byte [] rightSample24Data = this.rightSample.getSample24Data ();
        this.is24 = leftSample24Data != null && rightSample24Data != null && leftSample24Data.length * 2 == leftSampleData.length && rightSample24Data.length * 2 == rightSampleData.length;

        this.leftLengthInSamples = this.sample.getEnd () - this.sample.getStart ();
        this.rightLengthInSamples = this.rightSample.getEnd () - this.rightSample.getStart ();
        this.lengthInSamples = Math.max (this.leftLengthInSamples, this.rightLengthInSamples - this.rightChannelOffset);
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
                final int leftOffset = 2 * (leftStart + i);
                // The right channel might be moved by the alignment offset, see setRightSample()
                final int rightIndex = i + this.rightChannelOffset;
                final int rightOffset = 2 * (rightStart + rightIndex);

                // Support for different lengths of left/right mono file
                if (i < this.leftLengthInSamples && leftOffset < leftSampleData.length)
                {
                    data[dataOffset] = leftSample24Data[leftStart + i];
                    data[dataOffset + 1] = leftSampleData[leftOffset];
                    data[dataOffset + 2] = leftSampleData[leftOffset + 1];
                }
                if (rightIndex >= 0 && rightIndex < this.rightLengthInSamples && rightOffset < rightSampleData.length)
                {
                    data[dataOffset + 3] = rightSample24Data[rightStart + rightIndex];
                    data[dataOffset + 4] = rightSampleData[rightOffset];
                    data[dataOffset + 5] = rightSampleData[rightOffset + 1];
                }
            }
        else
            for (int i = 0; i < this.lengthInSamples; i++)
            {
                final int dataOffset = 4 * i;
                final int leftOffset = 2 * (leftStart + i);
                // The right channel might be moved by the alignment offset, see setRightSample()
                final int rightIndex = i + this.rightChannelOffset;
                final int rightOffset = 2 * (rightStart + rightIndex);

                if (i < this.leftLengthInSamples && leftOffset < leftSampleData.length)
                {
                    data[dataOffset] = leftSampleData[leftOffset];
                    data[dataOffset + 1] = leftSampleData[leftOffset + 1];
                }
                if (rightIndex >= 0 && rightIndex < this.rightLengthInSamples && rightOffset < rightSampleData.length)
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
     * Get the sample description of the right side. Identical to the left side one if this is a
     * mono sample.
     *
     * @return The sample description
     */
    public Sf2SampleDescriptor getRightSample ()
    {
        return this.rightSample;
    }


    /**
     * Set the right side for the left side mono sample.
     *
     * @param rightSample The matching right side sample
     */
    public void setRightSample (final Sf2SampleDescriptor rightSample)
    {
        this.rightSample = rightSample;
        this.rightChannelOffset = computeAlignmentOffset (this.sample, rightSample);
        this.updateFormat ();
        // Force re-creation with the updated length
        this.audioMetadata = null;
    }


    /**
     * Calculates the frame offset which aligns the right channel of a stereo pair with the left
     * one. Some conversion tools write one channel with extra frames at its start, which offsets
     * its loop and its length by the same amount (e.g. in the DigitalSoundFactory E-mu E4 banks
     * every right channel sample is 1 frame longer and its loop starts 1 frame later). If both
     * loops have the same length and the loop offset matches the length offset, the pair is
     * aligned by skipping (or delaying by) that many frames of the right channel.
     *
     * @param left The sample of the left channel
     * @param right The sample of the right channel
     * @return The number of frames the right channel must be moved to line up with the left one,
     *         0 if the pair is already aligned or cannot be aligned
     */
    public static int computeAlignmentOffset (final Sf2SampleDescriptor left, final Sf2SampleDescriptor right)
    {
        // Both samples need a valid loop of the same length which lies fully inside of the sample
        final long leftLoopLength = left.getLoopEnd () - left.getLoopStart ();
        final long rightLoopLength = right.getLoopEnd () - right.getLoopStart ();
        if (leftLoopLength <= 0 || leftLoopLength != rightLoopLength)
            return 0;
        if (left.getLoopStart () < left.getStart () || left.getLoopEnd () > left.getEnd () || right.getLoopStart () < right.getStart () || right.getLoopEnd () > right.getEnd ())
            return 0;

        final long loopOffset = right.getLoopStart () - right.getStart () - (left.getLoopStart () - left.getStart ());
        final long lengthOffset = right.getEnd () - right.getStart () - (left.getEnd () - left.getStart ());
        return loopOffset == lengthOffset ? (int) loopOffset : 0;
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
