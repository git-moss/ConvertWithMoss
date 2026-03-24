// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Base class for EXS24 block types.
 *
 * @author Jürgen Moßgraber
 */
abstract class EXS24Object
{
    private final int type;
    int               id;
    String            name;


    /**
     * Constructor.
     *
     * @param type The block type of the object
     */
    protected EXS24Object (final int type)
    {
        this.type = type;
    }


    /**
     * Read a block with the object.
     *
     * @param block The data of the block
     * @throws IOException Could not read the data
     */
    public void read (final EXS24Block block) throws IOException
    {
        this.id = block.index;
        this.name = block.name;

        final ByteArrayInputStream in = new ByteArrayInputStream (block.content);
        this.read (in, block.isBigEndian);
    }


    /**
     * Read a block.
     *
     * @param isBigEndian True if number values are stored big-endian (otherwise little-endian)
     * @param in Where to read from
     * @throws IOException Could not write the data
     */
    protected abstract void read (final InputStream in, final boolean isBigEndian) throws IOException;


    /**
     * Write a block.
     *
     * @param isBigEndian True if number values are stored big-endian (otherwise little-endian)
     * @return The data of the block
     * @throws IOException Could not write the data
     */
    public EXS24Block write (final boolean isBigEndian) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        this.write (out, isBigEndian);
        final EXS24Block block = new EXS24Block (this.type, out.toByteArray (), isBigEndian);
        block.name = this.name;
        return block;
    }


    /**
     * Write a block.
     *
     * @param isBigEndian True if number values are stored big-endian (otherwise little-endian)
     * @param out Where to write to
     * @throws IOException Could not write the data
     */
    protected abstract void write (final OutputStream out, final boolean isBigEndian) throws IOException;
}
