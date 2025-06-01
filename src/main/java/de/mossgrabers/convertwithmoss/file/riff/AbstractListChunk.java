// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.mossgrabers.convertwithmoss.file.IChunk;
import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Class for a list chunk with sub-chunks.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractListChunk extends RawRIFFChunk
{
    protected final List<IChunk> subChunks = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param type The type of the list chunk
     */
    protected AbstractListChunk (final int type)
    {
        super (type, RiffID.LIST_ID.getId (), 0, null);
    }


    /**
     * Add a sub-chunk.
     *
     * @param chunk The info sub-chunk
     */
    public void add (final RawRIFFChunk chunk)
    {
        this.subChunks.add (chunk);
    }


    /**
     * Get all sub-chunks.
     *
     * @return The sub-chunks
     */
    public List<IChunk> getSubChunks ()
    {
        return this.subChunks;
    }


    /** {@inheritDoc} */
    @Override
    public long getSize ()
    {
        final long length = this.getDataSize ();
        return 4 + length + (length % 2);
    }


    /** {@inheritDoc} */
    @Override
    public long getDataSize ()
    {
        int size = 4;
        for (final IChunk chunk: this.subChunks)
        {
            final long dataSize = chunk.getDataSize ();
            size += 8 + dataSize + (dataSize % 2);
        }
        return size;
    }


    /** {@inheritDoc} */
    @Override
    public byte [] getData ()
    {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream ())
        {
            this.writeData (out);
            this.setData (out.toByteArray ());
        }
        catch (final IOException ex)
        {
            // Should never happen
            return new byte [0];
        }

        return super.getData ();
    }


    /** {@inheritDoc} */
    @Override
    public void writeData (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.getType (), true);
        for (final IChunk chunk: this.subChunks)
            chunk.write (out);
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = super.hashCode ();
        result = prime * result + Objects.hash (this.subChunks);
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (!super.equals (obj) || this.getClass () != obj.getClass ())
            return false;
        final AbstractListChunk other = (AbstractListChunk) obj;
        return Objects.equals (this.subChunks, other.subChunks);
    }
}
