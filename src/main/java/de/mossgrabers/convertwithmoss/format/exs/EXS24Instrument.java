// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Structure for a EXS24 instrument.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Instrument extends EXS24Object
{
    int numZoneBlocks;
    int numGroupBlocks;
    int numSampleBlocks;
    int numParameterBlocks;


    /**
     * Default constructor.
     */
    public EXS24Instrument ()
    {
        super (EXS24Block.TYPE_INSTRUMENT);
    }


    /**
     * Constructor.
     *
     * @param block The block to read
     * @throws IOException Could not read the block
     */
    public EXS24Instrument (final EXS24Block block) throws IOException
    {
        this ();
        this.read (block);
    }


    /** {@inheritDoc} */
    @Override
    protected void read (final InputStream in, final boolean isBigEndian) throws IOException
    {
        // No idea, always 0
        StreamUtils.readUnsigned32 (in, isBigEndian);

        this.numZoneBlocks = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.numGroupBlocks = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.numSampleBlocks = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.numParameterBlocks = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        // No idea about these values, maybe there are more unknown block types
        StreamUtils.readUnsigned32 (in, isBigEndian);
        StreamUtils.readUnsigned32 (in, isBigEndian);
        StreamUtils.readUnsigned32 (in, isBigEndian);
        // There are files which have this set and then 16 blocks appear with a block type of 8. The
        // content of each block is 4 bytes but they are all 0.
        StreamUtils.readUnsigned32 (in, isBigEndian);

        // No idea about this value, maybe there are more unknown block types
        StreamUtils.readUnsigned32 (in, isBigEndian);

        // There can be more bytes...
    }


    /** {@inheritDoc} */
    @Override
    protected void write (final OutputStream out, final boolean isBigEndian) throws IOException
    {
        // No idea, always 0
        StreamUtils.writeUnsigned32 (out, 0, isBigEndian);

        StreamUtils.writeUnsigned32 (out, this.numZoneBlocks, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.numGroupBlocks, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.numSampleBlocks, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.numParameterBlocks, isBigEndian);

        // No idea about these values, maybe there are more unknown block types
        StreamUtils.writeUnsigned32 (out, 0, isBigEndian);
        StreamUtils.writeUnsigned32 (out, 0, isBigEndian);
        StreamUtils.writeUnsigned32 (out, 0, isBigEndian);
        // There are files which have this set and then 16 blocks appear with a block type of 8. The
        // content of each block is 4 bytes but they are all 0.
        StreamUtils.writeUnsigned32 (out, 0, isBigEndian);

        // No idea about this value, maybe there are more unknown block types
        StreamUtils.writeUnsigned32 (out, 0, isBigEndian);
    }
}
