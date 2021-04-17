// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sfz;

import de.mossgrabers.sampleconverter.core.AbstractSampleMetadata;
import de.mossgrabers.sampleconverter.exception.CompressionNotSupportedException;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.wav.DataChunk;
import de.mossgrabers.sampleconverter.file.wav.FormatChunk;
import de.mossgrabers.sampleconverter.file.wav.WaveFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Metadata for a sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzSampleMetadata extends AbstractSampleMetadata
{
    /**
     * Constructor.
     *
     * @param file The sample file
     */
    public SfzSampleMetadata (final File file)
    {
        super (file);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        try (final InputStream in = new FileInputStream (this.file))
        {
            in.transferTo (outputStream);
        }
    }


    /**
     * Check if the play length is set, if not read the sample length from the sample file.
     *
     * @throws IOException Could not read or parse the wave file
     */
    public void addMissingInfoFromWaveFile () throws IOException
    {
        if (this.stop >= 0)
            return;

        this.start = 0;

        final WaveFile waveFile;
        try
        {
            waveFile = new WaveFile (this.file, true);
        }
        catch (final IOException | ParseException ex)
        {
            throw new IOException (ex);
        }

        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final DataChunk dataChunk = waveFile.getDataChunk ();
        if (formatChunk == null || dataChunk == null)
            return;

        try
        {
            this.sampleRate = formatChunk.getSampleRate ();
            this.stop = dataChunk.calculateLength (formatChunk);
        }
        catch (final CompressionNotSupportedException ex)
        {
            throw new IOException (ex);
        }
    }
}
