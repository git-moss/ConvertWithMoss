// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.wav;

import de.mossgrabers.sampleconverter.exception.CompressionNotSupportedException;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.riff.RIFFChunk;
import de.mossgrabers.sampleconverter.file.riff.RiffID;


/**
 * Accessor for a data chunk ("data") in a WAV file.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DataChunk extends WavChunk
{
    /**
     * Constructor. Creates an empty data chunk.
     *
     * @param formatChunk The format chunk, necessary for the calculation (sample size and number of
     *            channels
     * @param lengthInSamples The length of the sample (number of samples)
     */
    public DataChunk (final FormatChunk formatChunk, final int lengthInSamples)
    {
        super (RiffID.DATA_ID, new RIFFChunk (0, RiffID.DATA_ID.getId (), calculateDataSize (formatChunk, lengthInSamples)));

        this.chunk.setData (new byte [calculateDataSize (formatChunk, lengthInSamples)]);
    }


    /**
     * Constructor.
     *
     * @param chunk The RIFF chunk which contains the data
     * @throws ParseException Length of data does not match the expected chunk size
     */
    public DataChunk (final RIFFChunk chunk) throws ParseException
    {
        super (RiffID.DATA_ID, chunk, chunk.getData ().length);
    }


    /**
     * Calculates the length of the data in samples.
     *
     * @param formatChunk The format chunk, necessary for the calculation (sample size and number of
     *            channels
     * @return The length of the audio file in samples
     * @throws CompressionNotSupportedException If the compression/encoding used for the data is not
     *             supported
     */
    public int calculateLength (final FormatChunk formatChunk) throws CompressionNotSupportedException
    {
        final int compressionCode = formatChunk.getCompressionCode ();

        if (compressionCode == FormatChunk.WAVE_FORMAT_PCM || compressionCode == FormatChunk.WAVE_FORMAT_IEEE_FLOAT)
            return calculateLength (formatChunk, this.chunk.getData ());

        if (compressionCode == FormatChunk.WAVE_FORMAT_EXTENSIBLE)
        {
            final int numberOfChannels = formatChunk.getNumberOfChannels ();
            if (numberOfChannels > 2)
                throw new CompressionNotSupportedException ("WAV files in Extensible format are only supported for stereo files.");
            return calculateLength (formatChunk, this.chunk.getData ());
        }

        throw new CompressionNotSupportedException ("Unsupported data compression: " + FormatChunk.getCompression (compressionCode));
    }


    /**
     * Calculates the length of the data in samples.
     *
     * @param chunk The format chunk, necessary for the calculation (sample size and number of
     *            channels
     * @param data The data
     * @return The length of the sample in samples
     */
    private static int calculateLength (final FormatChunk chunk, final byte [] data)
    {
        return data.length / (chunk.getNumberOfChannels () * chunk.getSignicantBitsPerSample () / 8);
    }


    /**
     * Calculates the data size.
     *
     * @param chunk The format chunk, necessary for the calculation (sample size and number of
     *            channels
     * @param lengthInSamples The length of the sample (number of samples)
     * @return The size of the data block
     */
    private static int calculateDataSize (final FormatChunk chunk, final int lengthInSamples)
    {
        return lengthInSamples * (chunk.getNumberOfChannels () * chunk.getSignicantBitsPerSample () / 8);
    }


    /**
     * Sets the data.
     *
     * Note: The array will not be cloned for performance reasons.
     *
     * @param data The data to set
     */
    public void setData (final byte [] data)
    {
        this.chunk.setData (data);
    }
}
