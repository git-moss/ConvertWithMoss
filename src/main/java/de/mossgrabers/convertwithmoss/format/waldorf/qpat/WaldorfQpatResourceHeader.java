// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * The header of a resource.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatResourceHeader
{
    WaldorfQpatResourceType type   = WaldorfQpatResourceType.UNUSED;
    int                     offset = 0;
    int                     length = 0;


    /**
     * Read the resource attributes.
     *
     * @param in The input stream
     * @throws IOException Could not read the resource attributes
     */
    public void read (final InputStream in) throws IOException
    {
        final int resourceType = (int) StreamUtils.readUnsigned32 (in, false);
        final WaldorfQpatResourceType [] values = WaldorfQpatResourceType.values ();
        if (resourceType < 0 || resourceType >= values.length)
            throw new IOException (Functions.getMessage ("IDS_QPAT_UNKNOWN_RESOURCE_TYPE"));
        this.type = WaldorfQpatResourceType.values ()[resourceType];
        this.offset = (int) StreamUtils.readUnsigned32 (in, false);
        this.length = (int) StreamUtils.readUnsigned32 (in, false);
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
