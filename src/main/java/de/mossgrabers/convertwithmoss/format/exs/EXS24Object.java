package de.mossgrabers.convertwithmoss.format.exs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


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


    protected abstract void read (final InputStream in, final boolean isBigEndian) throws IOException;


    /**
     * Write a block.
     *
     * @return The data of the block
     * @throws IOException Could not write the data
     */
    public EXS24Block write (final boolean isBigEndian) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        this.write (out, isBigEndian);
        final EXS24Block block = new EXS24Block (this.type, out.toByteArray ());
        block.name = this.name;
        return block;
    }


    protected abstract void write (final OutputStream out, final boolean isBigEndian) throws IOException;
}
