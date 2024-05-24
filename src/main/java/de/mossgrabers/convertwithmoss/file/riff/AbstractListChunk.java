// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.IChunk;
import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Class for a list chunk with sub-chunks.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractListChunk extends RIFFChunk
{
    protected final List<IChunk> subChunks = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param type The type of the list chunk
     */
    public AbstractListChunk (final int type)
    {
        super (type, RiffID.LIST_ID.getId (), 0, null);
    }


    /**
     * Add a sub-chunk.
     *
     * @param chunk The info sub-chunk
     */
    public void add (final RIFFChunk chunk)
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
    public byte [] getData ()
    {
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream ())
        {
            StreamUtils.writeUnsigned32 (out, this.getType (), true);
            for (final IChunk chunk: this.subChunks)
                chunk.write (out);
            this.setData (out.toByteArray ());
        }
        catch (final IOException ex)
        {
            // Should never happen
            return new byte [0];
        }

        return super.getData ();
    }
}
