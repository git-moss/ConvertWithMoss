// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * The header of a resource.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatResourceHeader
{
    WaldorfQpatResourceType type    = WaldorfQpatResourceType.UNUSED;
    int                     rawType = 0;
    int                     offset  = 0;
    int                     length  = 0;


    /**
     * Read the resource attributes.
     *
     * @param in The input stream
     * @throws IOException Could not read the resource attributes
     */
    public void read (final InputStream in) throws IOException
    {
        this.rawType = (int) StreamUtils.readUnsigned32 (in, false);
        // Newer firmware versions add resource types - the MK2 stores a parameter sequence in one
        // of them. An unknown type is not an error since only the sample maps are read, therefore
        // it is kept as unused and the caller skips it.
        final WaldorfQpatResourceType [] values = WaldorfQpatResourceType.values ();
        this.type = this.rawType < 0 || this.rawType >= values.length ? WaldorfQpatResourceType.UNUSED : values[this.rawType];
        this.offset = (int) StreamUtils.readUnsigned32 (in, false);
        this.length = (int) StreamUtils.readUnsigned32 (in, false);
    }


    /**
     * Test whether the resource has a type which this application does not know.
     *
     * @return True if the type is unknown
     */
    public boolean isUnknownType ()
    {
        return this.rawType != 0 && this.type == WaldorfQpatResourceType.UNUSED;
    }


    /**
     * Write the resource attributes.
     *
     * @param out The output stream
     * @throws IOException Could not write the resource attributes
     */
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.type.ordinal (), false);
        StreamUtils.writeUnsigned32 (out, this.offset, false);
        StreamUtils.writeUnsigned32 (out, this.length, false);
    }
}
