// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    /** ID of the chunk which contains the entry list with pointers to the performances. */
    public static final String          ENTRY_LIST_PERFORMANCE       = "EPFM";
    /** ID of the chunk which contains the data of the performances. */
    public static final String          DATA_LIST_PERFORMANCE        = "DPFM";

    /** ID of the chunk which contains the entry list with pointers to the sample metadata. */
    public static final String          ENTRY_LIST_WAVEFORM_METADATA = "EWFM";
    /** ID of the chunk which contains the data of the sample metadata. */
    public static final String          DATA_LIST_WAVEFORM_METADATA  = "DWFM";

    /** ID of the chunk which contains the entry list with pointers to the sample data. */
    public static final String          ENTRY_LIST_WAVEFORM_DATA     = "EWIM";
    /** ID of the chunk which contains the data of the sample. */
    public static final String          DATA_LIST_WAVEFORM_DATA      = "DWIM";

    private static final String         MAGIC_ENTRY                  = "Entr";
    private static final String         MAGIC_DATA                   = "Data";

    private String                      chunkID;
    private int                         chunkLength;
    private int                         numItemsInChunk;
    private final List<YamahaYsfcEntry> entryListEntries             = new ArrayList<> ();
    private final List<byte []>         dataArrays                   = new ArrayList<> ();


    /**
     * Default constructor.
     */
    public YamahaYsfcChunk ()
    {
        // Intentionally empty
    }


    /**
     * Default constructor.
     *
     * @param chunkID The ID of the chunk
     */
    public YamahaYsfcChunk (final String chunkID)
    {
        this.chunkID = chunkID;
    }


    /**
     * Read a chunk from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not read the chunk
     */
    public void read (final InputStream in, final int version) throws IOException
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
                    this.entryListEntries.add (new YamahaYsfcEntry (in, version));
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
     * Write the chunk to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the chunk
     */
    public void write (final OutputStream out) throws IOException
    {
        this.updateChunkLength ();

        StreamUtils.writeASCII (out, this.chunkID, 4);
        StreamUtils.writeUnsigned32 (out, this.chunkLength, true);
        StreamUtils.writeUnsigned32 (out, this.numItemsInChunk, true);

        if (this.entryListEntries.isEmpty ())
            for (final byte [] dataArray: this.dataArrays)
            {
                StreamUtils.writeASCII (out, MAGIC_DATA, 4);
                StreamUtils.writeUnsigned32 (out, dataArray.length, true);
                out.write (dataArray);
            }
        else
            for (final YamahaYsfcEntry entryListChunk: this.entryListEntries)
            {
                StreamUtils.writeASCII (out, MAGIC_ENTRY, 4);
                entryListChunk.write (out);
            }
    }


    private void updateChunkLength () throws IOException
    {
        this.chunkLength = 4;
        if (this.entryListEntries.isEmpty ())
        {
            for (final byte [] array: this.dataArrays)
                this.chunkLength += 8 + array.length;
            this.numItemsInChunk = this.dataArrays.size ();
        }
        else
        {
            for (final YamahaYsfcEntry entryListChunk: this.entryListEntries)
                this.chunkLength += 8 + entryListChunk.getLength ();
            this.numItemsInChunk = this.entryListEntries.size ();
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
     * @throws IOException Could not calculate the length
     */
    public int getChunkLength () throws IOException
    {
        this.updateChunkLength ();
        return this.chunkLength;
    }


    /**
     * Get the entry lists in the chunk if any.
     *
     * @return The entry lists
     */
    public List<YamahaYsfcEntry> getEntryListChunks ()
    {
        return this.entryListEntries;
    }


    /**
     * Get the data arrays in the chunk, if any.
     *
     * @return The data arrays
     */
    public List<byte []> getDataArrays ()
    {
        return this.dataArrays;
    }


    /**
     * Add a data array.
     *
     * @param dataArray The array to add
     */
    public void addDataArray (final byte [] dataArray)
    {
        this.dataArrays.add (dataArray);
    }


    /**
     * Add a list entry to the chunk.
     *
     * @param entry The entry to add
     */
    public void addEntry (final YamahaYsfcEntry entry)
    {
        this.entryListEntries.add (entry);
    }
}
