// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;


/**
 * Interface to the additional data which depends on the type of a chunk.
 *
 * @author Jürgen Moßgraber
 */
public interface IChunkData
{
    /**
     * Read the additional data.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    void read (InputStream in) throws IOException;
}
