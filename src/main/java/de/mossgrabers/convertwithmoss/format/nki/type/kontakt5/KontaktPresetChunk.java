// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.IChunkData;

import java.io.IOException;
import java.io.InputStream;


/**
 * One of several preset chunks in a Kontakt 5+ preset.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktPresetChunk implements IChunkData
{
    private int     chunkID;
    private byte [] data;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.chunkID = StreamUtils.readUnsigned16 (in, false);
        final int size = StreamUtils.readUnsigned32 (in, false);
        this.data = in.readNBytes (size);
    }


    /**
     * @return the chunkID
     */
    public int getChunkID ()
    {
        return this.chunkID;
    }


    public byte [] getData ()
    {
        return this.data;
    }
}
