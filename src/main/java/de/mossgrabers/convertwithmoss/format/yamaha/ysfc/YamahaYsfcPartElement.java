// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * An element of a part in a performance.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcPartElement
{
    private byte [] dataBlock;


    /**
     * Constructor which reads the performance from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the YSFC file
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcPartElement (final InputStream in, final YamahaYsfcVersion version) throws IOException
    {
        this.read (in, version);
    }


    /**
     * Read a performance from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the YSFC file
     * @throws IOException Could not read the entry item
     */
    public void read (final InputStream in, final YamahaYsfcVersion version) throws IOException
    {
        // TODO ...
        this.dataBlock = StreamUtils.readDataBlock (in, true);
        final InputStream elementData = new ByteArrayInputStream (dataBlock);
        // TODO ...
    }


    /**
     * Write a performance to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the entry item
     */
    public void write (final OutputStream out) throws IOException
    {
        // TODO
        StreamUtils.writeDataBlock (out, this.dataBlock, true);
    }
}
