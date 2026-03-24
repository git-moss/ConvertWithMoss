// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Interface to the additional data which depends on the type of a chunk.
 *
 * @author Jürgen Moßgraber
 */
public interface IChunkData
{
    /**
     * Get the format version.
     *
     * @return The version number
     */
    int getVersion ();


    /**
     * Read the chunk data.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    void read (InputStream in) throws IOException;


    /**
     * Write the chunk data.
     *
     * @param out The output stream to write to
     * @throws IOException Error during writing
     */
    void write (OutputStream out) throws IOException;


    /**
     * Dumps all info into a text.
     *
     * @param level The indentation level
     * @return The formatted string
     */
    String dump (int level);
}
