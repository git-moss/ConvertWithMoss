// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.nicontainer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.SubTreeItemChunkData;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * An item in a Native Instruments Container (used by Kontakt 5+ and other NI plugins). An item
 * contains a header, some data and some child items.
 *
 * @author Jürgen Moßgraber
 */
public class NIContainerItem
{
    private final NIContainerDataChunk       dataChunk = new NIContainerDataChunk ();
    private final List<NIContainerChildItem> children  = new ArrayList<> ();

    private byte []                          uuid;
    private int                              version;


    /**
     * Constructor.
     */
    public NIContainerItem ()
    {
        // Intentionally empty
    }


    /**
     * Constructor which calls read.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    public NIContainerItem (final InputStream in) throws IOException
    {
        this.read (in);
    }


    /**
     * Read the content of the item.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    public void read (final InputStream in) throws IOException
    {
        final byte [] itemBlock = StreamUtils.readBlock64 (in, false);

        final ByteArrayInputStream bin = new ByteArrayInputStream (itemBlock);

        final int headerVersion = (int) StreamUtils.readUnsigned32 (bin, false);
        if (headerVersion != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI5_ITEM_HEADER_VERSION", Integer.toString (headerVersion)));

        // Check for Native Instruments sound header
        final String domainID = StreamUtils.readASCII (bin, 4);
        if (!"hsin".equals (domainID))
            throw new IOException (Functions.getMessage ("IDS_NKI5_CORRUPTED_FILE_NO_HSIN"));

        // Unknown - seems to be always 1
        StreamUtils.readUnsigned32 (bin, false);
        // Unknown - seems to be always 0
        StreamUtils.readUnsigned32 (bin, false);

        // 16 Byte UUID
        this.uuid = bin.readNBytes (16);

        this.dataChunk.read (bin);

        this.version = (int) StreamUtils.readUnsigned32 (bin, false);

        // Read all child items
        final int numChildren = (int) StreamUtils.readUnsigned32 (bin, false);
        for (int i = 0; i < numChildren; i++)
        {
            final NIContainerChildItem childItem = new NIContainerChildItem ();
            this.children.add (childItem);
            childItem.read (bin);
        }
    }


    /**
     * Write the content of the item.
     *
     * @param out The output stream to write to
     * @throws IOException Error during writing
     */
    public void write (final OutputStream out) throws IOException
    {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream ();

        StreamUtils.writeUnsigned32 (bout, 1, false);
        StreamUtils.writeASCII (bout, "hsin", 4);
        StreamUtils.writeUnsigned32 (bout, 1, false);
        StreamUtils.writeUnsigned32 (bout, 0, false);
        bout.write (this.uuid);

        this.dataChunk.write (bout);

        StreamUtils.writeUnsigned32 (bout, this.version, false);

        // Write all child items
        final int numChildren = this.children.size ();
        StreamUtils.writeUnsigned32 (bout, numChildren, false);
        for (int i = 0; i < numChildren; i++)
            this.children.get (i).write (bout);

        StreamUtils.writeBlock64 (out, bout.toByteArray (), false);
    }


    /**
     * Get the UUID bytes.
     *
     * @return The UUID as 16 bytes
     */
    public byte [] getUUID ()
    {
        return this.uuid;
    }


    /**
     * Get the format version.
     *
     * @return The version
     */
    public int getVersion ()
    {
        return this.version;
    }


    /**
     * Get the first data chunk of them item.
     *
     * @return The data chunk
     */
    public NIContainerDataChunk getDataChunk ()
    {
        return this.dataChunk;
    }


    /**
     * Get the child items.
     *
     * @return The children
     */
    public List<NIContainerChildItem> getChildren ()
    {
        return this.children;
    }


    /**
     * Search for the first chunk which matches the given type. First searches the data of the item,
     * then recursively searches in the child elements.
     *
     * @param type The type of the chunk to look for
     * @return The chunk or null if none is found
     */
    public NIContainerDataChunk find (final NIContainerChunkType type)
    {
        NIContainerDataChunk chunk = this.dataChunk;
        while (chunk != null)
        {
            if (chunk.getChunkType () == type)
                return chunk;

            if (chunk.getData () instanceof final SubTreeItemChunkData subTree && !subTree.isEncrypted ())
            {
                final NIContainerDataChunk found = subTree.getSubTree ().find (type);
                if (found != null)
                    return found;
            }

            chunk = chunk.getNextChunk ();
        }

        for (final NIContainerChildItem childItem: this.children)
        {
            final NIContainerDataChunk found = childItem.getItem ().find (type);
            if (found != null)
                return found;
        }

        return null;
    }


    /**
     * Find all chunks which matches the given type. First searches the data of the item, then
     * recursively searches in the child elements.
     *
     * @param type The type of the chunk to look for
     * @return The found chunks
     */
    public List<NIContainerDataChunk> findAll (final NIContainerChunkType type)
    {
        final List<NIContainerDataChunk> foundChunks = new ArrayList<> ();
        this.findAll (type, foundChunks);
        return foundChunks;
    }


    /**
     * Find all chunks which matches the given type. First searches the data of the item, then
     * recursively searches in the child elements.
     *
     * @param type The type of the chunk to look for
     * @param foundChunks Where to add the found chunks
     */
    private void findAll (final NIContainerChunkType type, final List<NIContainerDataChunk> foundChunks)
    {
        NIContainerDataChunk chunk = this.dataChunk;
        while (chunk != null)
        {
            if (chunk.getChunkType () == type)
                foundChunks.add (chunk);

            if (chunk.getData () instanceof final SubTreeItemChunkData subTree && !subTree.isEncrypted ())
                subTree.getSubTree ().findAll (type, foundChunks);

            chunk = chunk.getNextChunk ();
        }

        for (final NIContainerChildItem childItem: this.children)
            childItem.getItem ().findAll (type, foundChunks);
    }


    /**
     * Dumps all info into a text.
     *
     * @param level The indentation level
     * @return The formatted string
     */
    public String dump (final int level)
    {
        final int padding = level * 4;

        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("Item HSIN: Version ", padding)).append (this.version).append ('\n');

        final int childLevel = level + 2;
        final int nextPadding = padding + 4;

        sb.append (StringUtils.padLeftSpaces ("Data:\n", nextPadding)).append (this.dataChunk.dump (childLevel));

        if (!this.children.isEmpty ())
        {
            sb.append (StringUtils.padLeftSpaces ("Children:\n", nextPadding));
            for (final NIContainerChildItem childItem: this.children)
                sb.append (childItem.dump (childLevel));
        }

        return sb.toString ();
    }
}
