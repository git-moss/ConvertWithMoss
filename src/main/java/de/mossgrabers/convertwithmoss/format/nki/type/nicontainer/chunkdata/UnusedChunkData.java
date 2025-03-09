package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import de.mossgrabers.tools.StringUtils;


/**
 * An unused dummy chunk.
 *
 * @author Jürgen Moßgraber
 */
public class UnusedChunkData extends AbstractChunkData
{
    private byte [] allBytes = null;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.allBytes = in.readAllBytes ();
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        if (this.allBytes != null)
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
        final UnusedChunkData other = (UnusedChunkData) obj;
        return Arrays.equals (this.allBytes, other.allBytes);
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        final int padding = level * 4;
        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("This Chunk is not used.\n", padding));
        return sb.toString ();
    }
}
