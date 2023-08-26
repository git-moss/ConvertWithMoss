// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import de.mossgrabers.convertwithmoss.file.FastLZ;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerItem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * A chunk which contains additional compressed data.
 *
 * @author Jürgen Moßgraber
 */
public class SubTreeItemChunkData extends AbstractChunkData
{
    private NIContainerItem subTreeItem;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        final boolean isCompressed = in.read () > 0;

        final int sizeUncompressed = StreamUtils.readUnsigned32 (in, false);
        final int sizeCompressed = StreamUtils.readUnsigned32 (in, false);

        final byte [] uncompressedData;
        if (isCompressed)
        {
            final byte [] compressedData = in.readNBytes (sizeCompressed);
            uncompressedData = FastLZ.uncompress (compressedData, sizeUncompressed);
        }
        else
            uncompressedData = in.readNBytes (sizeUncompressed);

        this.subTreeItem = new NIContainerItem ();
        final ByteArrayInputStream childBin = new ByteArrayInputStream (uncompressedData);
        this.subTreeItem.read (childBin);
    }


    /**
     * Get the sub tree item.
     *
     * @return The sub tree item
     */
    public NIContainerItem getSubTree ()
    {
        return this.subTreeItem;
    }
}
