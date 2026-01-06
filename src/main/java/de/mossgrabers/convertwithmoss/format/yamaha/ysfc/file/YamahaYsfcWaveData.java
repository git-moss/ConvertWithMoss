// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.core.IStreamable;
import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * The raw 16-bit data of a sample.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcWaveData implements IStreamable
{
    private byte [] data;


    /**
     * Default constructor.
     */
    public YamahaYsfcWaveData ()
    {
        // Intentionally empty
    }


    /**
     * Constructor which reads the wave data from the input stream.
     *
     * @param in The input stream
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcWaveData (final InputStream in) throws IOException
    {
        this.read (in);
    }


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        final int size = (int) StreamUtils.readUnsigned32 (in, true);
        this.data = in.readNBytes (size);
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.data.length, true);
        out.write (this.data);
    }


    /**
     * Create a text description of the object.
     *
     * @return The text
     */
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Data Size: ").append (this.data.length).append ('\n');
        return sb.toString ();
    }


    /**
     * Get the data.
     *
     * @return The data
     */
    public byte [] getData ()
    {
        return this.data;
    }


    /**
     * Set the data.
     *
     * @param data The data
     */
    public void setData (final byte [] data)
    {
        this.data = data;
    }
}
