// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A chunk in an YSFC file.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcChunk
{
    private static final String         MAGIC_ENTRY     = "Entr";
    private static final String         MAGIC_DATA      = "Data";

    private String                      chunkID;
    private int                         chunkLength;
    private int                         numItemsInChunk;
    private final List<YamahaYsfcEntry> entryListChunks = new ArrayList<> ();
    private final List<byte []>         dataArrays      = new ArrayList<> ();


    /**
     * Read a chunk from the input stream.
     *
     * @param in The input stream
     * @throws IOException Could not read the chunk
     */
    public void read (final InputStream in) throws IOException
    {
        this.chunkID = StreamUtils.readASCII (in, 4);
        this.chunkLength = (int) StreamUtils.readUnsigned32 (in, true);
        this.numItemsInChunk = (int) StreamUtils.readUnsigned32 (in, true);

        for (int i = 0; i < this.numItemsInChunk; i++)
        {
            final String magic = StreamUtils.readASCII (in, 4);
            switch (magic)
            {
                case MAGIC_ENTRY:
                    final YamahaYsfcEntry entryChunk = new YamahaYsfcEntry ();
                    entryChunk.read (in);
                    this.entryListChunks.add (entryChunk);
                    break;

                case MAGIC_DATA:
                    final int dataLength = (int) StreamUtils.readUnsigned32 (in, true);
                    final byte [] data = in.readNBytes (dataLength);
                    this.dataArrays.add (data);
                    break;

                default:
                    throw new IOException (Functions.getMessage ("IDS_YSFC_NO_UKNOWN_CHUNK_MAGIC", magic));
            }
        }
    }


    /**
     * Get the chunk ID.
     *
     * @return A four ASCII characters ID of the chunk
     */
    public String getChunkID ()
    {
        return this.chunkID;
    }


    /**
     * Get the length of the chunk.
     *
     * @return The chunk length
     */
    public int getChunkLength ()
    {
        return this.chunkLength;
    }


    /**
     * Get the entry lists in the chunk if any.
     *
     * @return The entry lists
     */
    public List<YamahaYsfcEntry> getEntryListChunks ()
    {
        return this.entryListChunks;
    }


    /**
     * Get the first entry in the chunk, if any.
     *
     * @return The entry
     * @throws IOException If there are none or more than 1 entries
     */
    public YamahaYsfcEntry getEntryListChunk () throws IOException
    {
        if (this.entryListChunks.size () != 1)
            throw new IOException (Functions.getMessage ("IDS_YSFC_LESS_OR_MORE_THAN_1_ENTRY_ITEM"));
        return this.entryListChunks.get (0);
    }


    /**
     * Get the first data array in the chunk, if any.
     *
     * @return The data array
     * @throws IOException If there are none or more than 1 data arrays
     */
    public byte [] getDataArray () throws IOException
    {
        if (this.dataArrays.size () != 1)
            throw new IOException (Functions.getMessage ("IDS_YSFC_LESS_OR_MORE_THAN_1_DATA_ITEM"));
        return this.dataArrays.get (0);
    }
}
