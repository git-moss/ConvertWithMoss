// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.wav;

import de.mossgrabers.sampleconverter.exception.CombinationNotPossibleException;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.riff.RIFFChunk;
import de.mossgrabers.sampleconverter.file.riff.RIFFParser;
import de.mossgrabers.sampleconverter.file.riff.RIFFVisitor;
import de.mossgrabers.sampleconverter.file.riff.RIFFWriter;
import de.mossgrabers.sampleconverter.file.riff.RiffID;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;


/**
 * Accessor to a WAV file.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class WaveFile
{
    InstrumentChunk instrumentChunk;
    SampleChunk     sampleChunk;
    FormatChunk     formatChunk;
    DataChunk       dataChunk;


    /**
     * Constructor.
     *
     * @param wavFile The WAV file
     * @param ignoreChunkErrors Ignores unknown or missing chunk errors if true
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public WaveFile (final File wavFile, final boolean ignoreChunkErrors) throws IOException, ParseException
    {
        try (final FileInputStream stream = new FileInputStream (wavFile))
        {
            this.read (stream, ignoreChunkErrors);
        }
    }


    /**
     * Constructor.
     *
     * @param inputStream The input stream which provides the WAV file
     * @param ignoreChunkErrors Ignores unknown or missing chunk errors if true
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public WaveFile (final InputStream inputStream, final boolean ignoreChunkErrors) throws IOException, ParseException
    {
        this.read (inputStream, ignoreChunkErrors);
    }


    private void read (final InputStream inputStream, final boolean ignoreChunkErrors) throws IOException, ParseException
    {
        new RIFFParser ().parse (inputStream, new Visitor (), ignoreChunkErrors);

        if (ignoreChunkErrors)
            return;
        if (this.formatChunk == null)
            throw new ParseException ("No format chunk found in WAV file.");
        if (this.dataChunk == null)
            throw new ParseException ("No data chunk found in WAV file.");
    }


    /**
     * Get the instrument chunk if present in the WAV file.
     *
     * @return The instrument chunk or null if not present
     */
    public InstrumentChunk getInstrumentChunk ()
    {
        return this.instrumentChunk;
    }


    /**
     * Get the sample chunk if present in the wav file.
     *
     * @return The sample chunk or null if not present
     */
    public SampleChunk getSampleChunk ()
    {
        return this.sampleChunk;
    }


    /**
     * Get the format chunk if present in the WAV file.
     *
     * @return The format chunk or null if not present
     */
    public FormatChunk getFormatChunk ()
    {
        return this.formatChunk;
    }


    /**
     * Get the data chunk if present in the WAV file.
     *
     * @return The data chunk or null if not present
     */
    public DataChunk getDataChunk ()
    {
        return this.dataChunk;
    }


    /**
     * Write the wave to a new file.
     *
     * @param file The file to write to
     * @throws IOException Error during write
     */
    public void write (final File file) throws IOException
    {
        try (final FileOutputStream out = new FileOutputStream (file))
        {
            this.write (out);
        }
    }


    /**
     * Write the wave to an output stream.
     *
     * @param out The output stream to write to
     * @throws IOException Error during write
     */
    public void write (final OutputStream out) throws IOException
    {
        int fullSize = 4;
        fullSize += 8 + this.formatChunk.getData ().length;
        fullSize += 8 + this.dataChunk.getData ().length;
        if (this.sampleChunk != null)
            fullSize += 8 + this.sampleChunk.getData ().length;

        final RIFFWriter writer = new RIFFWriter (out);
        writer.writeHeader (fullSize);
        writer.writeFourCC (RiffID.WAVE_ID.getId ());
        writer.write (this.formatChunk);
        writer.write (this.dataChunk);
        if (this.sampleChunk != null)
            writer.write (this.sampleChunk);
    }


    /**
     * Combines two mono files into a stereo file. Format and sample chunks must be identical.
     *
     * @param otherWave The other sample to include
     * @throws CombinationNotPossibleException Could not combine the wave files
     */
    public void combine (final WaveFile otherWave) throws CombinationNotPossibleException
    {
        final FormatChunk otherFormat = otherWave.getFormatChunk ();
        if (!Arrays.equals (this.formatChunk.getData (), otherFormat.getData ()))
            throw new CombinationNotPossibleException ("Format chunks are not identical.");

        final SampleChunk otherSample = otherWave.getSampleChunk ();
        if (!Arrays.equals (this.sampleChunk == null ? null : this.sampleChunk.getData (), otherSample == null ? null : otherSample.getData ()))
            throw new CombinationNotPossibleException ("Sample chunks are not identical.");

        final int numberOfChannels = this.formatChunk.getNumberOfChannels ();
        if (numberOfChannels != 1)
            throw new CombinationNotPossibleException ("Can only combine mono files.");

        this.formatChunk.setNumberOfChannels (2);
        this.formatChunk.setAverageBytesPerSecond (this.formatChunk.getAverageBytesPerSecond () * 2);

        // Interleave left and right channel
        final byte [] leftData = this.dataChunk.getData ();
        final byte [] rightData = otherWave.dataChunk.getData ();
        final byte [] combinedData = new byte [leftData.length * 2];
        final int blockAlign = this.formatChunk.getBlockAlign ();
        for (int count = 0; count < leftData.length; count += blockAlign)
        {
            System.arraycopy (leftData, count, combinedData, count * 2, blockAlign);
            System.arraycopy (rightData, count, combinedData, count * 2 + blockAlign, blockAlign);
        }
        this.dataChunk.setData (combinedData);
    }


    /** Visitor for traversing all chunks. */
    class Visitor implements RIFFVisitor
    {
        /** {@inheritDoc} */
        @Override
        public boolean enteringGroup (final RIFFChunk group)
        {
            // Wave file format has only flat chunks
            return false;
        }


        /** {@inheritDoc} */
        @Override
        public void enterGroup (final RIFFChunk group) throws ParseException
        {
            // Intentionally empty
        }


        /** {@inheritDoc} */
        @Override
        public void leaveGroup (final RIFFChunk group) throws ParseException
        {
            // Intentionally empty
        }


        /** {@inheritDoc} */
        @Override
        public void visitChunk (final RIFFChunk group, final RIFFChunk chunk) throws ParseException
        {
            switch (RiffID.fromId (chunk.getId ()))
            {
                case INST_ID:
                    WaveFile.this.instrumentChunk = new InstrumentChunk (chunk);
                    break;

                case FMT_ID:
                    WaveFile.this.formatChunk = new FormatChunk (chunk);
                    break;

                case DATA_ID:
                    WaveFile.this.dataChunk = new DataChunk (chunk);
                    break;

                case SMPL_ID:
                    WaveFile.this.sampleChunk = new SampleChunk (chunk);
                    break;

                default:
                    // Ignore other chunks
                    break;
            }
        }
    }
}
