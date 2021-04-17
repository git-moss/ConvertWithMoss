// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.bitwig;

import de.mossgrabers.sampleconverter.core.AbstractSampleMetadata;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.wav.FormatChunk;
import de.mossgrabers.sampleconverter.file.wav.WaveFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Metadata for a sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BitwigSampleMetadata extends AbstractSampleMetadata
{
    private final File zipFile;


    /**
     * Constructor.
     *
     * @param zipFile The ZIP file which contains the WAV files
     * @param filename The name of the samples' file
     */
    public BitwigSampleMetadata (final File zipFile, final String filename)
    {
        super (filename);

        this.zipFile = zipFile;
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        try (final ZipFile zf = new ZipFile (this.zipFile))
        {
            final ZipEntry entry = zf.getEntry (this.filename);
            if (entry == null)
                return;

            try (final InputStream in = zf.getInputStream (entry))
            {
                in.transferTo (outputStream);
            }
        }
    }


    /**
     * Get the sample rate from the sample file.
     *
     * @throws IOException Could not read or parse the wave file
     */
    public void addMissingInfoFromWaveFile () throws IOException
    {
        try (final ZipFile zf = new ZipFile (this.zipFile))
        {
            final ZipEntry entry = zf.getEntry (this.filename);
            if (entry == null)
                return;

            try (final InputStream in = zf.getInputStream (entry))
            {
                final WaveFile waveFile = new WaveFile (in, true);
                final FormatChunk formatChunk = waveFile.getFormatChunk ();
                if (formatChunk != null)
                    this.sampleRate = formatChunk.getSampleRate ();
            }
            catch (final IOException | ParseException ex)
            {
                throw new IOException (ex);
            }
        }
    }
}
