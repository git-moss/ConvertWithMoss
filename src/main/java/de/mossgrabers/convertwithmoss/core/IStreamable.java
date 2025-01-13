// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Interface to read/write data from a stream.
 *
 * @author Jürgen Moßgraber
 */
public interface IStreamable
{
    /**
     * Read data from the input stream.
     *
     * @param in The input stream
     * @throws IOException Could not read the entry item
     */
    void read (final InputStream in) throws IOException;


    /**
     * Write data to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the entry item
     */
    void write (final OutputStream out) throws IOException;
}
