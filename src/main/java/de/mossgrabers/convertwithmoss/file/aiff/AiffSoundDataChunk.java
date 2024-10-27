// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.iff.IffChunk;


/**
 * An AIFF Sound Data Chunk as defined in the AIFF specification.
 *
 * @author Jürgen Moßgraber
 */
public class AiffSoundDataChunk extends AiffChunk
{
    long    offset;
    long    blockSize;
    byte [] soundData = null;


    /**
     * Constructor.
     *
     * @param chunk The IFF chunk
     */
    protected AiffSoundDataChunk (final IffChunk chunk)
    {
        super (chunk);
    }


    /**
     * Read the AIFF Common chunk data.
     *
     * @param chunk The chunk to read from
     * @param sampleDataSize The size of the sample data (stored in the COMMON chunk)
     * @throws IOException Could not read the data
     */
    public void read (final IffChunk chunk, final int sampleDataSize) throws IOException
    {
        try (final InputStream in = chunk.streamData ())
        {
            this.offset = StreamUtils.readUnsigned32 (in, true);
            this.blockSize = StreamUtils.readUnsigned32 (in, true);
            if (this.offset > 0)
                in.skip (this.offset);
            if (sampleDataSize < 0)
                this.soundData = in.readAllBytes ();
            else
                this.soundData = in.readNBytes (sampleDataSize);
        }
    }


    /**
     * Get the sound data.
     *
     * @return The sound data
     */
    public byte [] getSoundData ()
    {
        return this.soundData;
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Block Alignment: ");
        if (this.offset == 0 && this.blockSize == 0)
            sb.append ("No\n");
        else
            sb.append ("Yes (Offset: ").append (this.offset).append (" / Block Size: ").append (this.blockSize).append (")\n");
        sb.append ("Sound Data Size: ").append (this.soundData.length);
        return sb.toString ();
    }
}
