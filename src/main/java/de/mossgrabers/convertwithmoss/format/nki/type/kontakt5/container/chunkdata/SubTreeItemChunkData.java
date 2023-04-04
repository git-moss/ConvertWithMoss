package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.chunkdata;

import de.mossgrabers.convertwithmoss.file.FastLZ;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.NIContainerItem;

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
    private boolean         isCompressed;
    private NIContainerItem subItem;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        super.read (in);

        this.isCompressed = in.read () > 0;

        final int sizeUncompressed = StreamUtils.readUnsigned32 (in, false);
        final int sizeCompressed = StreamUtils.readUnsigned32 (in, false);

        final byte [] uncompressedData;

        if (this.isCompressed)
        {
            try
            {
                final byte [] compressedData = in.readNBytes (sizeCompressed);

                // TODO
                uncompressedData = FastLZ.uncompress (compressedData, sizeUncompressed);
            }
            catch (Exception ex)
            {
                // TODO
                throw new IOException (ex);
            }
        }
        else
            uncompressedData = in.readNBytes (sizeUncompressed);

        // TODO remove
        // Files.write (new File ("C:\\Users\\mos\\Desktop\\decompressed.bin").toPath (),
        // this.uncompressedData);

        this.subItem = new NIContainerItem ();
        final ByteArrayInputStream childBin = new ByteArrayInputStream (uncompressedData);
        this.subItem.read (childBin);

        // TODO remove
        System.out.println ("Sub Tree");
        System.out.println (this.subItem.dump (0));
    }
}
