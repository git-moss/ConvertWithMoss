// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mossgrabers.convertwithmoss.exception.ParseException;
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
    protected final List<IRiffChunk> chunkStack = new ArrayList<> ();
    protected InfoChunk              infoChunk  = null;

    protected final RiffChunkId      topRiffChunkId;


    /**
     * Constructor.
     *
     * @param topRiffChunkId The top RIFF ID of the file
     * @param hasInfo True if the format has an info chunk
     */
    protected AbstractRIFFFile (final RiffChunkId topRiffChunkId, final boolean hasInfo)
    {
        this.topRiffChunkId = topRiffChunkId;

        if (hasInfo)
            this.infoChunk = new InfoChunk ();
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


    /**
     * Get the creation date as a date object.
     *
     * @return The date object
     */
    public Date getParsedCreationDate ()
    {
        if (this.infoChunk != null)
            return this.infoChunk.getCreationDate ();
        return new Date ();
    }


    /**
     * Get the names of any sound designers or engineers responsible for the SoundFont compatible
     * bank (optional).
     *
     * @return The names, empty string if not present
     */
    public String getSoundDesigner ()
    {
        if (this.infoChunk == null)
            return "";
        final String result = this.infoChunk.getInfoField (InfoRiffChunkId.INFO_IENG, InfoRiffChunkId.INFO_IART, InfoRiffChunkId.INFO_ITCH, InfoRiffChunkId.INFO_ISTR, InfoRiffChunkId.INFO_STAR);
        return result == null ? "" : result.trim ();
    }


    /**
     * Get keywords.
     *
     * @return The keywords, empty string if not present
     */
    public String getKeywords ()
    {
        if (this.infoChunk == null)
            return "";
        final String result = this.infoChunk.getInfoField (InfoRiffChunkId.INFO_IKEY);
        return result == null ? "" : result.trim ();
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

        StreamUtils.writeUnsigned32 (out, CommonRiffChunkId.RIFF_ID.getFourCC (), true);
        StreamUtils.writeUnsigned32 (out, this.calculateFileSize (), false);
        StreamUtils.writeUnsigned32 (out, this.topRiffChunkId.getFourCC (), true);
        for (final IRiffChunk chunk: this.chunkStack)
            chunk.write (out);
    }


    /**
     * Remove all chunks which match one of the given IDs.
     *
     * @param identifiers Some IDs
     */
    public void removeChunks (final RiffChunkId... identifiers)
    {
        final Set<Integer> ignore = new HashSet<> ();
        for (final RiffChunkId riffChunkId: identifiers)
            ignore.add (Integer.valueOf (riffChunkId.getFourCC ()));

        final List<IRiffChunk> newChunkStack = new ArrayList<> ();
        for (final IRiffChunk chunk: this.chunkStack)
        {
            if (chunk == null)
                continue;
            final RiffChunkId id = chunk.getId ();
            if (id != null && !ignore.contains (Integer.valueOf (id.getFourCC ())))
                newChunkStack.add (chunk);
        }

        this.chunkStack.clear ();
        this.chunkStack.addAll (newChunkStack);
    }


    /**
     * Format all info values as a string.
     *
     * @param riffChunkIds The IDs of the info RIFFs to format
     * @return The formatted string
     */
    public String formatInfoFields (final RiffChunkId... riffChunkIds)
    {
        final StringBuilder sb = new StringBuilder ();
        if (this.infoChunk != null)
            for (final RiffChunkId riffChunkId: riffChunkIds)
            {
                final String value = this.infoChunk.getInfoField (riffChunkId);
                if (value == null)
                    continue;
                if (!sb.isEmpty ())
                    sb.append ('\n');
                sb.append (riffChunkId.getDescription ()).append (": ").append (value.trim ());
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
        for (final IRiffChunk chunk: this.chunkStack)
        {
            final RiffChunkId id = chunk.getId ();
            sb.append ("* ").append (id.getDescription ()).append (" ('").append (RiffChunkId.toASCII (id.getFourCC ())).append ("')\n");
            sb.append ("    ").append (chunk.infoText ().replace ("\n", "\n    ")).append ('\n');
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
        for (final IRiffChunk chunk: this.chunkStack)
        {
            final long length = chunk.getDataSize ();
            fullSize += 8 + length;
            if (length % 2 == 1)
                fullSize++;
        }
        return fullSize;
    }
}
