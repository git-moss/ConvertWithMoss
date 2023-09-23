// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.ncw;

import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Metadata for a NCW compressed sample. Converts the output to a WAV file when writing!
 *
 * @author Jürgen Moßgraber
 */
public class NcwSampleMetadata extends DefaultSampleMetadata
{
    private NcwFile ncwFile;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public NcwSampleMetadata (final File file) throws IOException
    {
        super (file);

        // Ignore non-existing files since it might be in a monolith
        if (file.exists ())
            this.handleNcwFile (new NcwFile (file));
    }


    /**
     * Constructor.
     *
     * @param filename The name of the file
     * @param inputStream The stream from which the file content can be read
     * @throws IOException Could not read the file
     */
    public NcwSampleMetadata (final String filename, final InputStream inputStream) throws IOException
    {
        this.setFilename (filename);
        this.handleNcwFile (new NcwFile (inputStream));
    }


    private void handleNcwFile (final NcwFile ncwFile) throws IOException
    {
        this.ncwFile = ncwFile;

        final int channels = this.ncwFile.getChannels ();
        if (channels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (channels), this.sampleFile.getAbsolutePath ()));
        this.isMonoFile = channels == 1;
        this.start = 0;
        this.stop = this.ncwFile.getNumberOfSamples ();

        // Change filename ending from NCW to WAV
        this.setCombinedName (this.filename.replace (".ncw", ".wav").replace (".NCW", ".wav"));
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        this.ncwFile.writeWAV (outputStream);
    }


    /** {@inheritDoc} */
    @Override
    public void addMissingInfoFromWaveFile (final boolean addRootKey, final boolean addLoops) throws IOException
    {
        // Info not available in AIFF
    }
}
