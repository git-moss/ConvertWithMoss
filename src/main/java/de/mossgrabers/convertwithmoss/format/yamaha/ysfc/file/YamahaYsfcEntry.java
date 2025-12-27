// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.StringUtils;


/**
 * An item in an YSFC entry block.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcEntry
{
    private int     length;
    private int     correspondingDataSize   = 0;
    private int     correspondingDataOffset = 0;
    private int     contentNumber           = 0;
    private byte [] flags                   = new byte [6];
    private String  itemName                = "";
    private String  itemTitle               = "";
    private byte [] additionalData          = new byte [0];
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

        // Reserved
        if (version <= 102)
            contentStream.skipNBytes (4);

        // Size of the item corresponding to this entry
        this.correspondingDataSize = (int) StreamUtils.readUnsigned32 (contentStream, true);

        // Reserved
        if (version <= 102)
            contentStream.skipNBytes (4);

        // Offset of the item chunk within the data block
        this.correspondingDataOffset = (int) StreamUtils.readUnsigned32 (contentStream, true);

        // Type specific - e.g. Program number 0x10001, 0x10002, ...
        this.contentNumber = (int) StreamUtils.readUnsigned32 (contentStream, true);

        if (version <= 102)
            contentStream.skipNBytes (version <= 101 ? 1 : 2);
        else if (version >= 400)
        {
            // Flags - type specific
            this.flags = contentStream.readNBytes (6);

            // ID of the entry object for ordering
            if (version > 402 && version < 410 || version >= 500)
                this.entryID = (int) StreamUtils.readUnsigned32 (contentStream, true);

            if (version >= 410 && version < 500)
            {
                // Additional unknown bytes for Montage M
                // 0F - 0 0 0 0 0 0 - 28 AA
                // FF - 0 0 0 0 0 0 - 28 F7
                // 05 - 0 0 0 0 0 0 - 28 AA
                // 03 - 0 0 0 0 0 0 - 28 AA
                @SuppressWarnings("unused")
                final byte [] unknown = contentStream.readNBytes (9);
            }
        }

        this.itemName = StreamUtils.readNullTerminatedASCII (contentStream);

        if (contentStream.available () > 0)
        {
            this.itemTitle = StreamUtils.readNullTerminatedASCII (contentStream);

            // 32-bit program numbers of non-preset waveforms - only used by the performance
            this.additionalData = contentStream.readAllBytes ();
        }
    }


    /**
     * Write an entry item to the output stream.
     *
     * @param out The output stream
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not write the entry item
     */
    public void write (final OutputStream out, final int version) throws IOException
    {
        final byte [] content = this.createContent (version);
        StreamUtils.writeUnsigned32 (out, content.length, true);
        out.write (content);
    }


    private byte [] createContent (final int version) throws IOException
    {
        final ByteArrayOutputStream contentStream = new ByteArrayOutputStream ();

        if (version <= 102)
            StreamUtils.padBytes (contentStream, 4);

        // Size of the item corresponding to this entry
        StreamUtils.writeUnsigned32 (contentStream, this.correspondingDataSize, true);

        if (version <= 102)
            StreamUtils.padBytes (contentStream, 4);

        // Offset of the item chunk within the data block
        StreamUtils.writeUnsigned32 (contentStream, this.correspondingDataOffset, true);
        // Type specific - e.g. Program number
        StreamUtils.writeUnsigned32 (contentStream, this.contentNumber, true);

        if (version <= 102)
        {
            StreamUtils.padBytes (contentStream, version <= 101 ? 1 : 2);
        }
        else if (version >= 400)
        {
            // Flags - type specific
            contentStream.write (this.flags);

            // ID of the entry object for ordering
            if (version > 402 && version < 410 || version >= 500)
                StreamUtils.writeUnsigned32 (contentStream, this.entryID, true);

            if (version >= 410 && version < 500)
            {
                // Additional 9 unknown bytes for Montage M
                // 0F - 0 0 0 0 0 0 - 28 AA
                // FF - 0 0 0 0 0 0 - 28 F7
                // 05 - 0 0 0 0 0 0 - 28 AA
                // 03 - 0 0 0 0 0 0 - 28 AA

                // TODO implement
            }
        }

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
     * Get the entry ID.
     *
     * @return The entry ID
     */
    public int getEntryID ()
    {
        return this.entryID;
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
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not calculate the length
     */
    public int getLength (final int version) throws IOException
    {
        return this.createContent (version).length;
    }


    /**
     * Set the content number.
     *
     * @return The value
     */
    public int getContentNumber ()
    {
        return this.contentNumber;
    }


    /**
     * Set the content number.
     *
     * @param contentNumber The content number
     */
    public void setContentNumber (final int contentNumber)
    {
        this.contentNumber = contentNumber;
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
        if (split.length >= 2 && !split[0].isBlank ())
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
     * Set the title of the item to be found in the matching data item (only non-empty for EPFM).
     *
     * @param title The title
     */
    public void setItemTitle (final String title)
    {
        this.itemTitle = title;
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


    /**
     * Set the flags.
     *
     * @param flags The flags, a 6 byte array
     */
    public void setFlags (final byte [] flags)
    {
        System.arraycopy (flags, 0, this.flags, 0, flags.length);
    }


    /**
     * Set the additional data.
     *
     * @param additionalData The additional data
     */
    public void setAdditionalData (final byte [] additionalData)
    {
        this.additionalData = additionalData;
    }


    /**
     * Dumps all info into a text.
     *
     * @param level The indentation level
     * @return The formatted string
     */
    public String dump (final int level)
    {
        final int indent = level * 4;
        final int indentNext = indent + 4;

        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("Ysfc Entry", indent)).append ("\n");

        sb.append (StringUtils.padLeftSpaces ("Item Name      : '", indentNext)).append (this.itemName).append ("'\n");
        sb.append (StringUtils.padLeftSpaces ("Item Title     : '", indentNext)).append (this.itemTitle).append ("'\n");
        sb.append (StringUtils.padLeftSpaces ("Size           : ", indentNext)).append (StringUtils.formatDataValue (this.length)).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Data Size      : ", indentNext)).append (StringUtils.formatDataValue (this.correspondingDataSize)).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Data Offset    : ", indentNext)).append (StringUtils.formatDataValue (this.correspondingDataOffset)).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Content Number : ", indentNext)).append (StringUtils.formatDataValue (this.contentNumber)).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Flags          : ", indentNext)).append (StringUtils.formatArray (this.flags)).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Additional Data: ", indentNext)).append (StringUtils.formatArray (this.additionalData)).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Entry ID       : ", indentNext)).append (StringUtils.formatDataValue (this.entryID)).append ("\n");

        return sb.toString ();
    }
}
