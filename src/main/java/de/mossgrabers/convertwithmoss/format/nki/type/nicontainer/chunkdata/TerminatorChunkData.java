// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * The final chunk to end a chunk list.
 *
 * @author Jürgen Moßgraber
 */
public class TerminatorChunkData extends AbstractChunkData
{
    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        return "";
    }
}
