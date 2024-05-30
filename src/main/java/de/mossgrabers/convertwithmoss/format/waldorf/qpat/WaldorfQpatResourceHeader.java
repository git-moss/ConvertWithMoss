package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


public class WaldorfQpatResourceHeader
{
    WaldorfQpatResourceType type;
    int                     offset;
    int                     length;


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
}
