// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.ncw;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.tools.ui.Functions;


/**
 * The data of a NCW compressed sample. Converts the output to a WAV file when writing.
 *
 * @author Jürgen Moßgraber
 */
public class NcwFileSampleData extends AbstractFileSampleData
{
    private NcwFile ncwFile;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public NcwFileSampleData (final File file) throws IOException
    {
        super (file);

        this.handleNcwFile (new NcwFile (file));
    }


    /**
     * Constructor.
     *
     * @param inputStream The stream from which the file content can be read
     * @throws IOException Could not read the file
     */
    public NcwFileSampleData (final InputStream inputStream) throws IOException
    {
        this.handleNcwFile (new NcwFile (inputStream));
    }


    private void handleNcwFile (final NcwFile ncwFile) throws IOException
    {
        this.ncwFile = ncwFile;

        final int channels = this.ncwFile.getChannels ();
        if (channels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (channels), this.sampleFile.getAbsolutePath ()));

        this.audioMetadata = new DefaultAudioMetadata (channels, this.ncwFile.getSampleRate (), this.ncwFile.getBitsPerSample (), this.ncwFile.getNumberOfSamples ());
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        if (this.ncwFile == null)
            throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", this.sampleFile.getAbsolutePath ()));
        this.ncwFile.writeWAV (outputStream);
    }


    /** {@inheritDoc} */
    @Override
    public void addMetadata (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        if (zone.getStart () < 0)
            zone.setStart (0);
        if (zone.getStop () <= 0)
            zone.setStop (this.ncwFile.getNumberOfSamples ());

        // More info not available in NCW
    }
}
