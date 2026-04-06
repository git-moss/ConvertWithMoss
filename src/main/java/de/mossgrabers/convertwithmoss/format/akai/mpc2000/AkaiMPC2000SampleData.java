// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;


/**
 * The data of a SND sample. Converts the output to a WAV file when writing.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC2000SampleData extends AbstractFileSampleData
{
    private final AkaiMPC2000Sound sndFile;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public AkaiMPC2000SampleData (final File file) throws IOException
    {
        super (file);

        this.sndFile = new AkaiMPC2000Sound (file);
    }


    /**
     * Constructor.
     *
     * @param inputStream The stream from which the file content can be read
     * @throws IOException Could not read the file
     */
    public AkaiMPC2000SampleData (final InputStream inputStream) throws IOException
    {
        this.sndFile = new AkaiMPC2000Sound (inputStream);
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        final int channels = this.sndFile.getChannels ();
        if (channels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (channels), this.sampleFile.getAbsolutePath ()));

        this.audioMetadata = new DefaultAudioMetadata (channels, this.sndFile.getSampleRate (), 16, this.sndFile.getDurationInSamples ());
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        if (this.sndFile == null)
            throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", this.sampleFile.getAbsolutePath ()));

        final WaveFile wavFile = new WaveFile (this.sndFile.getChannels (), this.sndFile.getSampleRate (), 16, this.sndFile.getDurationInSamples ());
        final DataChunk dataChunk = wavFile.getDataChunk ();

        final short [] sampleData = this.sndFile.getSampleData ();
        final ByteBuffer buffer = ByteBuffer.allocate (sampleData.length * 2);
        buffer.order (ByteOrder.LITTLE_ENDIAN);
        for (short sample: sampleData)
            buffer.putShort (sample);

        dataChunk.setData (buffer.array ());
        wavFile.write (outputStream);
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        if (zone.getStart () < 0)
            zone.setStart ((int) this.sndFile.getStart ());
        if (zone.getStop () <= 0)
            zone.setStop ((int) this.sndFile.getEnd ());

        if (zone.getTuning () == 0)
            zone.setTuning (Math.clamp (this.sndFile.getTune () / 10.0, -12.0, 12.0));

        if (addLoops && this.sndFile.isLoopEnabled ())
        {
            // Check if a loop is already present
            final List<ISampleLoop> loops = zone.getLoops ();
            if (!loops.isEmpty ())
                return;

            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            final int end = (int) this.sndFile.getLoopEnd ();
            final int start = end - (int) this.sndFile.getLoopLength ();
            if (start < 0 || end <= 0 || start > end)
                return;
            loop.setStart (start);
            loop.setEnd (end);
            loops.add (loop);
        }
    }


    /**
     * Get the sound file.
     * 
     * @return The sound file
     */
    public AkaiMPC2000Sound getSndFile ()
    {
        return this.sndFile;
    }


    /** {@inheritDoc} */
    @Override
    public void updateMetadata (final IMetadata metadata)
    {
        // No metadata available
    }
}
