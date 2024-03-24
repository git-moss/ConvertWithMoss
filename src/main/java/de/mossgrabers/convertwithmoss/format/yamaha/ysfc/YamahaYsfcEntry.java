// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * .
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcEntry
{
    private int     length;
    private byte [] flags;
    private String  itemName;
    private String  itemTitle;
    private byte [] additionalData;


    /**
     * Read an entry item from the input stream.
     *
     * @param in The input stream
     * @throws IOException Could not read the entry item
     */
    public void read (final InputStream in) throws IOException
    {
        // The length of the data to follow
        this.length = (int) StreamUtils.readUnsigned32 (in, true);
        final byte [] content = in.readNBytes (this.length);

        final ByteArrayInputStream contentStream = new ByteArrayInputStream (content);

        // Size of the item corresponding to this entry
        StreamUtils.readUnsigned32 (contentStream, true);
        // Offset of the item chunk within the data block
        StreamUtils.readUnsigned32 (contentStream, true);
        // Type specific - e.g. Program number
        StreamUtils.readUnsigned32 (contentStream, true);

        // Flags - type specific
        this.flags = contentStream.readNBytes (6);

        // Pseudo-timestamp for date-ordering
        StreamUtils.readUnsigned32 (contentStream, true);

        this.itemName = StreamUtils.readNullTerminatedASCII (contentStream);
        this.itemTitle = StreamUtils.readNullTerminatedASCII (contentStream);

        // Optional additional data - type specific, only used by EPFM
        this.additionalData = contentStream.readAllBytes ();
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
     * Get the title of the item to be found in the matching data item (only non-empty for EPFM).
     *
     * @return The title
     */
    public String getTitle ()
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
