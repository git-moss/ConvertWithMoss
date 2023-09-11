// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

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
    protected void readVersion (final InputStream in) throws IOException
    {
        this.version = (int) StreamUtils.readUnsigned32 (in, false);
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
