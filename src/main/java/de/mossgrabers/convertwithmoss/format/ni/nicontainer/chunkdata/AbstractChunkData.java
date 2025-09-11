// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Abstract base class for chunk data classes.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractChunkData implements IChunkData
{
    private int version = 1;


    /**
     * Read the version number of the chunk format. Currently, no other versions than 1 are known.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    protected void readVersion (final InputStream in) throws IOException
    {
        this.version = (int) StreamUtils.readUnsigned32 (in, false);
        if (this.version != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI5_WRONG_ROOT_VERSION", Integer.toString (this.version)));
    }


    /**
     * Write the version number of the chunk format.
     *
     * @param out The output stream to write to
     * @throws IOException Error during writing
     */
    protected void writeVersion (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.version, false);
    }


    /** {@inheritDoc} */
    @Override
    public int getVersion ()
    {
        return this.version;
    }
}
