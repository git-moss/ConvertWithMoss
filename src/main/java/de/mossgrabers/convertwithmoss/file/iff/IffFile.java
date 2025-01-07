// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.iff;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Helper functions for handling 'EA IFF 85' Standard for Interchange Format files.
 *
 * @author Jürgen Moßgraber
 */
public class IffFile
{
    /** ID for FORM chunk. */
    public static final String       FORM         = "FORM";
    /** ID for LIST chunk. */
    public static final String       LIST         = "LIST";
    /** ID for a a CAT chunk. */
    public static final String       CAT          = "CAT ";

    private static final Set<String> GROUP_CHUNKS = HashSet.newHashSet (3);
    static
    {
        GROUP_CHUNKS.add (FORM);
        GROUP_CHUNKS.add (LIST);
        GROUP_CHUNKS.add (CAT);
    }


    /**
     * Reads a group or local chunk header and returns the ID and data of the chunk. If it is a
     * group chunk the returned ID is the group type.
     *
     * @param in The input stream to read from
     * @return The data of the chunk wrapped in an input stream and its ID
     * @throws IOException Could not read
     */
    public static IffChunk readChunk (final InputStream in) throws IOException
    {
        String chunkID = StreamUtils.readASCII (in, 4);

        long size = StreamUtils.readUnsigned32 (in, true);

        // If it is a FORM, LIST or CAT chunk return the actual FORM type
        if (GROUP_CHUNKS.contains (chunkID))
        {
            chunkID = StreamUtils.readASCII (in, 4);
            size -= 4;
        }

        final byte [] data = in.readNBytes ((int) size);
        // Chunk length is always 2 aligned!
        if (size % 2 == 1 && in.available () > 0)
            in.skipNBytes (1);
        return new IffChunk (chunkID, data);
    }


    /**
     * Writes a chunk header and returns the data of the chunk.
     *
     * @param out The output stream to write to
     * @param groupID The group ID (FORM, LIST or CAT)
     * @param groupTypeID The type ID of the group
     * @param chunkData The data of the chunk
     * @throws IOException Could not write
     */
    public static void writeGroupChunk (final OutputStream out, final String groupID, final String groupTypeID, final byte [] chunkData) throws IOException
    {
        StreamUtils.writeASCII (out, groupID, 4);
        StreamUtils.writeUnsigned32 (out, chunkData.length + 4L, true);
        StreamUtils.writeASCII (out, groupTypeID, 4);
        writeChunkData (out, chunkData);
    }


    /**
     * Writes a local chunk.
     *
     * @param out The output stream to write to
     * @param localChunkID The ID of the local chunk
     * @param chunkData The data of the chunk
     * @throws IOException Could not write
     */
    public static void writeLocalChunk (final OutputStream out, final String localChunkID, final byte [] chunkData) throws IOException
    {
        StreamUtils.writeASCII (out, localChunkID, 4);
        StreamUtils.writeUnsigned32 (out, chunkData.length, true);
        writeChunkData (out, chunkData);
    }


    private static void writeChunkData (final OutputStream out, final byte [] chunkData) throws IOException
    {
        out.write (chunkData);
        // Chunk length is always 2 aligned!
        if (chunkData.length % 2 == 1)
            out.write (0);
    }


    /**
     * Constructor. Private due to helper class.
     */
    private IffFile ()
    {
        // Intentionally empty
    }
}
