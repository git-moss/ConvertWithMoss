package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;


/**
 * The final chunk to end a chunk list.
 *
 * @author Jürgen Moßgraber
 */
public class UnusedChunkData extends AbstractChunkData
{
    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        // Don't do anything
    }
}
