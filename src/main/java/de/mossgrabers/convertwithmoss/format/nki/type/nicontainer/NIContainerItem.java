// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.SubTreeItemChunkData;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * An item in a Native Instruments Container (used by Kontakt 5+ and other NI plugins). An item
 * contains a header, some data and some child items.
 *
 * @author Jürgen Moßgraber
 */
public class NIContainerItem
{
    private final NIContainerChunk           dataChunk = new NIContainerChunk ();
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
     * Read the content of the item.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    public void read (final InputStream in) throws IOException
    {
        final byte [] itemBlock = StreamUtils.readBlock64 (in, false);
        final ByteArrayInputStream bin = new ByteArrayInputStream (itemBlock);

        final int headerVersion = StreamUtils.readUnsigned32 (bin, false);
        if (headerVersion != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI5_ITEM_HEADER_VERSION", Integer.toString (headerVersion)));

        // Check for Native Instruments sound header
        final String domainID = StreamUtils.readASCII (bin, 4);
        if (!"hsin".equals (domainID))
            throw new IOException (Functions.getMessage ("IDS_NKI5_CORRUPTED_FILE_NO_HSIN"));

        // Unknown
        StreamUtils.readUnsigned32 (bin, false);
        // Flags?
        StreamUtils.readUnsigned32 (bin, false);

        // 16 Byte UUID
        this.uuid = bin.readNBytes (16);

        this.dataChunk.read (bin);

        this.version = StreamUtils.readUnsigned32 (bin, false);

        // Read all child items
        final int numChildren = StreamUtils.readUnsigned32 (bin, false);
        for (int i = 0; i < numChildren; i++)
        {
            final NIContainerChildItem childItem = new NIContainerChildItem ();
            childItem.read (bin);
            this.children.add (childItem);
        }
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
     * Get the data section.
     *
     * @return The data
     */
    public NIContainerChunk getData ()
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
     * Dumps all info into a text.
     *
     * @param level The indentation level
     * @return The formatted string
     */
    public String dump (final int level)
    {
        final int length = level * 4;

        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("Item: hsin\n", length));

        final int childLevel = level + 1;

        sb.append (StringUtils.padLeftSpaces ("Data:\n", length + 4)).append (this.dataChunk.dump (childLevel + 1));

        if (this.children.isEmpty ())
            sb.append (StringUtils.padLeftSpaces ("Children: None\n", length + 4));
        else
        {
            sb.append (StringUtils.padLeftSpaces ("Children:\n", length + 4));
            for (final NIContainerChildItem childItem: this.children)
                sb.append (childItem.dump (childLevel));
        }

        return sb.toString ();
    }


    /**
     * Search for the first chunk which matches the given type. First searches the data of the item,
     * then recursively searches in the child elements.
     *
     * @param type The type of the chunk to look for
     * @return The chunk or null if none is found
     */
    public NIContainerChunk find (final NIContainerChunkType type)
    {
        NIContainerChunk chunk = this.dataChunk;
        while (chunk != null)
        {
            if (chunk.getChunkType () == type)
                return chunk;

            if (chunk.getData () instanceof final SubTreeItemChunkData subTree)
            {
                final NIContainerChunk found = subTree.getSubTree ().find (type);
                if (found != null)
                    return found;
            }

            chunk = chunk.getNextChunk ();
        }

        for (final NIContainerChildItem childItem: this.children)
        {
            final NIContainerChunk found = childItem.getItem ().find (type);
            if (found != null)
                return found;
        }

        return null;
    }
}
