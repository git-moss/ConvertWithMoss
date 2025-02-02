// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.ChunkDataFactory;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.IChunkData;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.SubTreeItemChunkData;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A chunk in a Native Instruments Container (used by Kontakt 5+ and other NI plugins).
 *
 * @author Jürgen Moßgraber
 */
public class NIContainerChunk
{
    private String           domainID;
    private int              chunkTypeID;
    private int              version;
    private NIContainerChunk nextChunk = null;
    private int              dataSize;
    private IChunkData       data;


    /**
     * Read the content of the item chunk.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    public void read (final InputStream in) throws IOException
    {
        final byte [] chunkStackBlock = StreamUtils.readBlock64 (in, false);

        final ByteArrayInputStream bin = new ByteArrayInputStream (chunkStackBlock);

        this.domainID = StreamUtils.readASCII (bin, 4);
        this.chunkTypeID = (int) StreamUtils.readUnsigned32 (bin, false);
        this.version = (int) StreamUtils.readUnsigned32 (bin, false);
        if (this.version != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI5_ITEM_HEADER_VERSION", Integer.toString (this.version)));

        final NIContainerChunkType chunkType = this.getChunkType ();
        if (chunkType == null)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_FRAME_TYPE", Integer.toString (this.getChunkTypeID ())));

        // Is this the last chunk?
        if (chunkType != NIContainerChunkType.TERMINATOR)
        {
            this.nextChunk = new NIContainerChunk ();
            this.nextChunk.read (bin);
        }

        this.data = ChunkDataFactory.createChunkData (chunkType);
        if (this.data == null)
            throw new IOException (Functions.getMessage ("IDS_NKI_UNSUPPORTED_CONTAINER_CHUNK_TYPE"));
        this.dataSize = bin.available ();
        this.data.read (bin);
    }


    /**
     * Write the content of the item chunk.
     *
     * @param out The output stream to write to
     * @throws IOException Error during writing
     */
    public void write (final OutputStream out) throws IOException
    {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream ();

        StreamUtils.writeASCII (bout, this.domainID, 4);
        StreamUtils.writeUnsigned32 (bout, this.chunkTypeID, false);
        StreamUtils.writeUnsigned32 (bout, this.version, false);

        if (this.nextChunk != null)
            this.nextChunk.write (bout);

        this.data.write (bout);
        StreamUtils.writeBlock64 (out, bout.toByteArray (), false);
    }


    /**
     * Get the domain ID, e.g. '4KIN' for Kontakt.
     *
     * @return The domain ID
     */
    public String getDomainID ()
    {
        return this.domainID;
    }


    /**
     * Get the ID of the chunk type.
     *
     * @return The chunk type ID
     */
    public int getChunkTypeID ()
    {
        return this.chunkTypeID;
    }


    /**
     * Get the chunk type.
     *
     * @return The chunk type, might be null if the chunk type is not known
     */
    public NIContainerChunkType getChunkType ()
    {
        return NIContainerChunkType.get (this.chunkTypeID);
    }


    /**
     * Get the version of the chunk types format.
     *
     * @return The version
     */
    public int getVersion ()
    {
        return this.version;
    }


    /**
     * Get the next chunk.
     *
     * @return The next chunk or null if there is none
     */
    public NIContainerChunk getNextChunk ()
    {
        return this.nextChunk;
    }


    /**
     * Get the additional data of the chunk.
     *
     * @return The data
     */
    public IChunkData getData ()
    {
        return this.data;
    }


    /**
     * Dumps all info into a text.
     *
     * @param level The indentation level
     * @return The formatted string
     */
    public String dump (final int level)
    {
        final StringBuilder sb = new StringBuilder ();
        final int padding = level * 4;

        sb.append (StringUtils.padLeftSpaces ("Chunk: ", padding)).append (this.getChunkType ()).append (" (Version: ").append (this.getVersion ()).append (", Size: ").append (this.dataSize).append (" Bytes)\n");
        if (this.data instanceof final SubTreeItemChunkData subTree)
        {
            if (subTree.isEncrypted ())
                sb.append (StringUtils.padLeftSpaces ("Encrypted.\n", (level + 1) * 4));
            else
                sb.append (subTree.getSubTree ().dump (level + 1));
        }
        else if (this.data != null)
            sb.append (this.data.dump (level + 1));

        if (this.nextChunk != null)
            sb.append (this.nextChunk.dump (level));
        return sb.toString ();
    }
}
