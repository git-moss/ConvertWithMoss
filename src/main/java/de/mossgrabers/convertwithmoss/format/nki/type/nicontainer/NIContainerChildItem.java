// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * A child item in a Native Instruments Container (used by Kontakt 5+ and other NI plugins).
 *
 * @author Jürgen Moßgraber
 */
public class NIContainerChildItem
{
    private String                domainID;
    private int                   chunkTypeID;
    private final NIContainerItem item       = new NIContainerItem ();
    private long                  childIndex = -1;


    /**
     * Read the content of the child item.
     *
     * @param in The input stream to read from
     * @throws IOException Error during reading
     */
    public void read (final InputStream in) throws IOException
    {
        // The index of the child
        this.childIndex = StreamUtils.readUnsigned32 (in, false);

        this.domainID = StreamUtils.readASCII (in, 4);
        this.chunkTypeID = (int) StreamUtils.readUnsigned32 (in, false);

        this.item.read (in);
    }


    /**
     * Write the content of the child item.
     *
     * @param out The output stream to write to
     * @throws IOException Error during writing
     */
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.childIndex, false);

        StreamUtils.writeASCII (out, this.domainID, 4);
        StreamUtils.writeUnsigned32 (out, this.chunkTypeID, false);

        this.item.write (out);
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
        sb.append (StringUtils.padLeftSpaces ("Domain: ", level * 4)).append (this.domainID);
        sb.append (" - Type: ").append (NIContainerChunkType.get (this.chunkTypeID)).append ("\n");
        sb.append (this.item.dump (level + 1));
        return sb.toString ();
    }


    /**
     * Get the domain ID.
     *
     * @return The domain ID
     */
    public String getDomainID ()
    {
        return this.domainID;
    }


    /**
     * Get the chunk type ID.
     *
     * @return the chunkTypeID
     */
    public int getChunkTypeID ()
    {
        return this.chunkTypeID;
    }


    /**
     * Get the item.
     *
     * @return The item
     */
    public NIContainerItem getItem ()
    {
        return this.item;
    }
}
