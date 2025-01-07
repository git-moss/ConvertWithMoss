// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.Pair;


/**
 * An item in an YSFC entry block.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcEntry
{
    private int     length;
    private byte [] flags                   = new byte [6];
    private String  itemName                = "";
    private String  itemTitle               = "";
    private byte [] additionalData          = new byte [0];
    private int     correspondingDataSize   = 0;
    private int     correspondingDataOffset = 0;
    private int     specificValue           = 0;
    private int     entryID                 = 0xFFFFFFFF;


    /**
     * Default constructor.
     */
    public YamahaYsfcEntry ()
    {
        Arrays.fill (this.flags, (byte) 0);
    }


    /**
     * Constructor.
     *
     * @param in The input stream
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcEntry (final InputStream in, final int version) throws IOException
    {
        this.read (in, version);
    }


    /**
     * Read an entry item from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not read the entry item
     */
    public void read (final InputStream in, final int version) throws IOException
    {
        // The length of the data to follow
        this.length = (int) StreamUtils.readUnsigned32 (in, true);
        final byte [] content = in.readNBytes (this.length);

        final ByteArrayInputStream contentStream = new ByteArrayInputStream (content);

        // Size of the item corresponding to this entry
        this.correspondingDataSize = (int) StreamUtils.readUnsigned32 (contentStream, true);
        // Offset of the item chunk within the data block
        this.correspondingDataOffset = (int) StreamUtils.readUnsigned32 (contentStream, true);
        // Type specific - e.g. Program number 0x10001, 0x10002, ...
        this.specificValue = (int) StreamUtils.readUnsigned32 (contentStream, true);

        if (version >= 400)
        {
            // Flags - type specific
            this.flags = contentStream.readNBytes (6);

            // ID of the entry object for ordering
            this.entryID = (int) StreamUtils.readUnsigned32 (contentStream, true);
        }

        this.itemName = StreamUtils.readNullTerminatedASCII (contentStream);

        if (contentStream.available () > 0)
        {
            this.itemTitle = StreamUtils.readNullTerminatedASCII (contentStream);

            // Optional additional data - type specific, only used by EPFM
            this.additionalData = contentStream.readAllBytes ();
        }
    }


    /**
     * Write an entry item to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the entry item
     */
    public void write (final OutputStream out) throws IOException
    {
        final byte [] content = this.createContent ();
        StreamUtils.writeUnsigned32 (out, content.length, true);
        out.write (content);
    }


    private byte [] createContent () throws IOException
    {
        final ByteArrayOutputStream contentStream = new ByteArrayOutputStream ();

        // Size of the item corresponding to this entry
        StreamUtils.writeUnsigned32 (contentStream, this.correspondingDataSize, true);
        // Offset of the item chunk within the data block
        StreamUtils.writeUnsigned32 (contentStream, this.correspondingDataOffset, true);
        // Type specific - e.g. Program number
        StreamUtils.writeUnsigned32 (contentStream, this.specificValue, true);

        // Flags - type specific
        contentStream.write (this.flags);

        // ID of the entry object for ordering
        StreamUtils.writeUnsigned32 (contentStream, this.entryID, true);

        StreamUtils.writeNullTerminatedASCII (contentStream, this.itemName);
        StreamUtils.writeNullTerminatedASCII (contentStream, this.itemTitle);

        // Optional additional data - type specific, only used by EPFM
        contentStream.write (this.additionalData);

        // Finally, write the chunk
        final byte [] content = contentStream.toByteArray ();
        this.length = content.length;
        return content;
    }


    /**
     * Set the entry ID.
     *
     * @param entryID The entry ID
     */
    public void setEntryID (final int entryID)
    {
        this.entryID = entryID;
    }


    /**
     * Set the size of the data block which is referenced from this entry.
     *
     * @param correspondingDataSize The size
     */
    public void setCorrespondingDataSize (final int correspondingDataSize)
    {
        this.correspondingDataSize = correspondingDataSize;
    }


    /**
     * Set the offset to the data block in the data chunk.
     *
     * @param correspondingDataOffset The offset
     */
    public void setCorrespondingDataOffset (final int correspondingDataOffset)
    {
        this.correspondingDataOffset = correspondingDataOffset;
    }


    /**
     * Get the length of the chunk.
     *
     * @return The length
     * @throws IOException Could not calculate the length
     */
    public int getLength () throws IOException
    {
        return this.createContent ().length;
    }


    /**
     * Set the specific value.
     *
     * @param specificValue The specific value
     */
    public void setSpecificValue (final int specificValue)
    {
        this.specificValue = specificValue;
    }


    /**
     * Get the name of the item to be found in the matching data item (normally a file name).
     *
     * @return The filename
     */
    public String getItemName ()
    {
        return this.itemName;
    }


    /**
     * Set the name of the item to be found in the matching data item (normally a file name).
     *
     * @param itemName The filename
     */
    public void setItemName (final String itemName)
    {
        this.itemName = itemName;
    }


    /**
     * Splits the item name into the category/sub-category value and the actual name part.
     * 
     * @return The category/sub-category value and the actual name part
     */
    public Pair<Integer, String> getItemCategoryAndName ()
    {
        final String [] split = this.itemName.split (":");
        if (split.length == 2)
            return new Pair<> (Integer.valueOf (split[0]), split[1]);
        return new Pair<> (Integer.valueOf (-1), this.itemName);
    }


    /**
     * Get the title of the item to be found in the matching data item (only non-empty for EPFM).
     *
     * @return The title
     */
    public String getItemTitle ()
    {
        return this.itemTitle;
    }


    /**
     * Get the flags.
     *
     * @return Six bytes of flags
     */
    public byte [] getFlags ()
    {
        return this.flags;
    }


    /**
     * Get the additional data.
     *
     * @return The additional data
     */
    public byte [] getAdditionalData ()
    {
        return this.additionalData;
    }
}
