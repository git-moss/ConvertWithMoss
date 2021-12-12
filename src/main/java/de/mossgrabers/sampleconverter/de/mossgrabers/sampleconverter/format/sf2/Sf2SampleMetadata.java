// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sf2;

import de.mossgrabers.sampleconverter.core.model.DefaultSampleMetadata;
import de.mossgrabers.sampleconverter.file.sf2.Sf2SampleDescriptor;
import de.mossgrabers.sampleconverter.file.wav.DataChunk;
import de.mossgrabers.sampleconverter.file.wav.WaveFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;


/**
 * Metadata for a Sf2 sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Sf2SampleMetadata extends DefaultSampleMetadata
{
    private final Sf2SampleDescriptor sample;
    private Sf2SampleDescriptor       rightSample;
    private final Integer             panorama;


    /**
     * Constructor.
     *
     * @param sample The name of the file where the sample is stored
     * @param panorama The panorama setting of the sample
     */
    public Sf2SampleMetadata (final Sf2SampleDescriptor sample, final Integer panorama)
    {
        super (sample.getName ());

        this.sample = sample;
        this.rightSample = sample;
        this.panorama = panorama;
        this.sampleRate = (int) sample.getSampleRate ();
    }


    /** {@inheritDoc} */
    @Override
    public Optional<String> getUpdatedFilename ()
    {
        if (this.combinedFilename.isEmpty ())
        {
            String commonPrefix = commonPrefix (this.sample.getName (), this.rightSample.getName ()).trim ();
            if (commonPrefix.endsWith ("_") || commonPrefix.endsWith ("("))
                commonPrefix = commonPrefix.substring (0, commonPrefix.length () - 1);
            this.combinedFilename = Optional.of (commonPrefix + ".wav");
        }
        return this.combinedFilename;
    }


    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        final byte [] leftSampleData = this.sample.getSampleData ();
        final byte [] leftSample24Data = this.sample.getSample24Data ();

        final byte [] rightSampleData = this.rightSample.getSampleData ();
        final byte [] rightSample24Data = this.rightSample.getSample24Data ();

        final long leftLengthInSamples = this.sample.getEnd () - this.sample.getStart ();
        final long rightLengthInSamples = this.rightSample.getEnd () - this.rightSample.getStart ();
        final long lengthInSamples = Math.max (leftLengthInSamples, rightLengthInSamples);

        // For 24 bit the data must be present and the length must match
        final boolean is24 = leftSample24Data != null && rightSample24Data != null && leftSample24Data.length * 2 == leftSampleData.length && rightSample24Data.length * 2 == rightSampleData.length;
        final int bitsPerSample = is24 ? 24 : 16;

        // Create an empty wave file with the required resolution and calculated data length
        final WaveFile wavFile = new WaveFile (2, this.sampleRate, bitsPerSample, (int) lengthInSamples);
        final DataChunk dataChunk = wavFile.getDataChunk ();
        final byte [] data = dataChunk.getData ();

        // Fill in the data
        final int leftStart = (int) this.sample.getStart ();
        final int rightStart = (int) this.rightSample.getStart ();
        if (is24)
        {
            // Convert to stereo interleaved format
            for (int i = 0; i < lengthInSamples; i++)
            {
                final int dataOffset = 6 * i;
                final int sampleOffset = 2 * i;
                final int leftOffset = 2 * leftStart + sampleOffset;
                final int rightOffset = 2 * rightStart + sampleOffset;

                // Support for different lengths of left/right mono file
                if (leftOffset < leftSampleData.length)
                {
                    data[dataOffset] = leftSampleData[leftOffset];
                    data[dataOffset + 1] = leftSampleData[leftOffset + 1];
                    data[dataOffset + 2] = leftSample24Data[leftStart + i];
                }
                if (rightOffset < rightSampleData.length)
                {
                    data[dataOffset + 3] = rightSampleData[rightOffset];
                    data[dataOffset + 4] = rightSampleData[rightOffset + 1];
                    data[dataOffset + 5] = rightSample24Data[rightStart + i];
                }
            }
        }
        else
        {
            for (int i = 0; i < lengthInSamples; i++)
            {
                final int dataOffset = 4 * i;
                final int sampleOffset = 2 * i;
                final int leftOffset = 2 * leftStart + sampleOffset;
                final int rightOffset = 2 * rightStart + sampleOffset;

                if (leftOffset < leftSampleData.length)
                {
                    data[dataOffset] = leftSampleData[leftOffset];
                    data[dataOffset + 1] = leftSampleData[leftOffset + 1];
                }
                if (rightOffset < rightSampleData.length)
                {
                    data[dataOffset + 2] = rightSampleData[rightOffset];
                    data[dataOffset + 3] = rightSampleData[rightOffset + 1];
                }
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
     * @return the panorama
     */
    public int getPanorama ()
    {
        return this.panorama.intValue ();
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


    private static String commonPrefix (final String a, final String b)
    {
        final int minLength = Math.min (a.length (), b.length ());
        for (int i = 0; i < minLength; i++)
        {
            if (a.charAt (i) != b.charAt (i))
                return a.substring (0, i);
        }
        return a.substring (0, minLength);
    }
}
