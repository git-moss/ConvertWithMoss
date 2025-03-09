// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import de.mossgrabers.tools.StringUtils;


/**
 * The controller assignment chunk.
 *
 * @author Jürgen Moßgraber
 */
public class ControllerAssignmentsChunkData extends AbstractChunkData
{
    private byte [] allBytes;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        this.allBytes = in.readAllBytes ();
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);

        out.write (this.allBytes);
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode (this.allBytes);
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if ((obj == null) || (this.getClass () != obj.getClass ()))
            return false;
        final ControllerAssignmentsChunkData other = (ControllerAssignmentsChunkData) obj;
        return Arrays.equals (this.allBytes, other.allBytes);
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        final int padding = level * 4;
        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("* Data: ", padding)).append (StringUtils.formatArray (this.allBytes)).append ('\n');
        return sb.toString ();
    }
}
