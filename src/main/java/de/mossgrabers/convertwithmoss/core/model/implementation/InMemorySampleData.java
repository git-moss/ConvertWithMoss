// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.io.IOException;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Sample data contained in memory.
 *
 * @author Jürgen Moßgraber
 */
public class InMemorySampleData implements ISampleData
{
    private final IAudioMetadata audioMetadata;
    private byte []              sampleData;


    /**
     * Constructor.
     *
     * @param audioMetadata The metadata description of the sample data
     * @param sampleData The raw sample data, channels are interleaved
     */
    public InMemorySampleData (final IAudioMetadata audioMetadata, final byte [] sampleData)
    {
        this.audioMetadata = audioMetadata;
        this.sampleData = sampleData;
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        final WaveFile wavFile = new WaveFile (this.audioMetadata);
        final DataChunk dataChunk = wavFile.getDataChunk ();
        System.arraycopy (this.sampleData, 0, dataChunk.getData (), 0, this.sampleData.length);
        wavFile.write (outputStream);
    }


    /** {@inheritDoc} */
    @Override
    public IAudioMetadata getAudioMetadata () throws IOException
    {
        return this.audioMetadata;
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        // No further info available
    }


    /**
     * Set the sample data.
     *
     * @param sampleData The sample data
     */
    public void setSampleData (final byte [] sampleData)
    {
        this.sampleData = sampleData;
    }
}
