package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.IOException;
import java.io.InputStream;


/**
 * Abstract base class for chunk data classes.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractChunkData implements IChunkData
{
    private int version;


    /**
     * Read the version number of the chunk format. Currently, no other versions than 1 are known.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.version = StreamUtils.readUnsigned32 (in, false);
        if (this.version != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI5_WRONG_ROOT_VERSION", Integer.toString (this.version)));
    }


    /**
     * Get the format version.
     * 
     * @return The version number
     */
    public int getVersion ()
    {
        return this.version;
    }
}
