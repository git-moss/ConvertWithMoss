// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.FastLZ;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerItem;
import de.mossgrabers.tools.ui.Functions;


/**
 * A chunk which contains additional compressed data.
 *
 * @author Jürgen Moßgraber
 */
public class SubTreeItemChunkData extends AbstractChunkData
{
    private NIContainerItem subTreeItem;
    private boolean         isEncrypted = false;
    private boolean         isCompressed;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        this.subTreeItem = new NIContainerItem ();

        // Compressed?
        this.isCompressed = in.read () > 0;
        if (this.isCompressed)
        {
            final int sizeUncompressed = (int) StreamUtils.readUnsigned32 (in, false);
            final int sizeCompressed = (int) StreamUtils.readUnsigned32 (in, false);
            final byte [] data = in.readNBytes (sizeCompressed);
            byte [] uncompressedData;
            try
            {
                uncompressedData = FastLZ.uncompress (data, sizeUncompressed);
            }
            catch (final IOException ex)
            {
                this.isEncrypted = true;
                return;
            }

            this.subTreeItem.read (new ByteArrayInputStream (uncompressedData));
        }
        else
            this.subTreeItem.read (in);

        if (in.available () > 0)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_DATA", "Sub Tree Item"));
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);

        out.write (this.isCompressed ? 1 : 0);

        if (this.isCompressed)
        {
            final ByteArrayOutputStream bout = new ByteArrayOutputStream ();
            this.subTreeItem.write (bout);
            final byte [] uncompressedData = bout.toByteArray ();
            final byte [] compressedData = FastLZ.compress (uncompressedData);
            StreamUtils.writeUnsigned32 (out, uncompressedData.length, false);
            StreamUtils.writeUnsigned32 (out, compressedData.length, false);
            out.write (compressedData);
        }
        else
            this.subTreeItem.write (out);
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


    /**
     * The sub tree is compressed and could not be extracted, therefore it is assumed that it is
     * encrypted.
     *
     * @return True if encrypted
     */
    public boolean isEncrypted ()
    {
        return this.isEncrypted;
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        return "";
    }
}
