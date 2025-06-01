// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.IChunk;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.wav.InfoChunk;


/**
 * Abstract implementation for a RIFF based file. Uncommon methods of the RIFF visitor are
 * implemented empty.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractRIFFFile implements RIFFVisitor
{
    protected final List<IChunk> chunkStack = new ArrayList<> ();
    protected InfoChunk          infoChunk  = null;

    protected final RiffID       topRiffID;


    /**
     * Constructor.
     *
     * @param topRiffID The top RIFF ID of the file
     */
    protected AbstractRIFFFile (final RiffID topRiffID)
    {
        this.topRiffID = topRiffID;
    }


    /**
     * Get the info chunk if present in the RIFF file.
     *
     * @return The info chunk or null if not present
     */
    public InfoChunk getInfoChunk ()
    {
        return this.infoChunk;
    }


    /** {@inheritDoc} */
    @Override
    public boolean enteringGroup (final RawRIFFChunk group)
    {
        // Return true to enter and parse the group
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void enterGroup (final RawRIFFChunk group) throws ParseException
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void leaveGroup (final RawRIFFChunk group) throws ParseException
    {
        // Intentionally empty
    }


    /**
     * Write the wave to a new file.
     *
     * @param file The file to write to
     * @throws IOException Error during write
     */
    public void write (final File file) throws IOException
    {
        try (final FileOutputStream out = new FileOutputStream (file))
        {
            this.write (out);
        }
    }


    /**
     * Write the wave to an output stream.
     *
     * @param out The output stream to write to
     * @throws IOException Error during write
     */
    public void write (final OutputStream out) throws IOException
    {
        this.fillChunkStack ();

        StreamUtils.writeUnsigned32 (out, RiffID.RIFF_ID.getId (), true);
        StreamUtils.writeUnsigned32 (out, this.calculateFileSize (), false);
        StreamUtils.writeUnsigned32 (out, this.topRiffID.getId (), true);
        for (final IChunk chunk: this.chunkStack)
            chunk.write (out);
    }


    /**
     * Remove all chunks which match one of the given IDs.
     *
     * @param identifiers Some IDs
     */
    public void removeChunks (final RiffID... identifiers)
    {
        final Set<Integer> ignore = new HashSet<> ();
        for (final RiffID riffID: identifiers)
            ignore.add (Integer.valueOf (riffID.getId ()));

        final List<IChunk> newChunkStack = new ArrayList<> ();
        for (final IChunk chunk: this.chunkStack)
            if (!ignore.contains (Integer.valueOf (chunk.getId ())))
                newChunkStack.add (chunk);

        this.chunkStack.clear ();
        this.chunkStack.addAll (newChunkStack);
    }


    /**
     * Format all info values as a string.
     *
     * @param riffIDs The IDs of the info RIFFs to format
     * @return The formatted string
     */
    public String formatInfoFields (final RiffID... riffIDs)
    {
        final StringBuilder sb = new StringBuilder ();
        if (this.infoChunk != null)
            for (final RiffID riffID: riffIDs)
            {
                final String value = this.infoChunk.getInfoField (riffID);
                if (value == null)
                    continue;
                if (sb.length () > 0)
                    sb.append ('\n');
                sb.append (riffID.getName ()).append (": ").append (value.trim ());
            }
        return sb.toString ();
    }


    /**
     * Format all values as a string for dumping it out.
     *
     * @return The formatted string
     */
    public String infoText ()
    {
        this.fillChunkStack ();

        final StringBuilder sb = new StringBuilder ();
        for (final IChunk chunk: this.chunkStack)
        {
            final int id = chunk.getId ();
            sb.append ("* ").append (RiffID.fromId (id).getName ()).append (" ('").append (RiffID.toASCII (id)).append ("')\n");
            sb.append ("    " + chunk.infoText ().replace ("\n", "\n    ")).append ('\n');
        }
        return sb.toString ();
    }


    /**
     * Check if the chunk stack is already filled from reading the RIFF file. Fill it if empty.
     */
    protected abstract void fillChunkStack ();


    /**
     * Calculate the file size from all chunks on the chunk stack.
     *
     * @return The size of the whole RIFF file
     */
    private long calculateFileSize ()
    {
        int fullSize = 4;
        for (final IChunk chunk: this.chunkStack)
        {
            final long length = chunk.getDataSize ();
            fullSize += 8 + length;
            if (length % 2 == 1)
                fullSize++;
        }
        return fullSize;
    }
}
