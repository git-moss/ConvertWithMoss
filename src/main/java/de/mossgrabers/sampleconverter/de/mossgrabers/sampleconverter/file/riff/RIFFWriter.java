// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.riff;

import java.io.IOException;
import java.io.OutputStream;


/**
 * Parses Resource Interchange File Format (RIFF) data. See the RIFF specification for more info.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class RIFFWriter
{
    private final OutputStream out;


    /**
     * Constructor.
     *
     * @param out The output stream to write to
     */
    public RIFFWriter (final OutputStream out)
    {
        this.out = out;
    }


    /**
     * Write the RIFF header.
     *
     * @param fullSize The size of all data excluding the RIFF header
     * @throws IOException Error during write
     */
    public void writeHeader (final int fullSize) throws IOException
    {
        this.writeFourCC (RiffID.RIFF_ID.getId ());
        this.writeFour (fullSize);
    }


    /**
     * Write a RIFF chunk.
     *
     * @param chunk The chunk to write
     * @throws IOException Error during write
     */
    public void write (final IChunk chunk) throws IOException
    {
        this.writeFourCC (chunk.getId ());
        final byte [] data = chunk.getData ();
        this.writeFour (data.length);
        this.out.write (data);
    }


    /**
     * Write a RIFF id.
     *
     * @param value The RIFF id
     * @throws IOException Error during write
     */
    public void writeFourCC (final int value) throws IOException
    {
        this.out.write ((byte) (value >> 24));
        this.out.write ((byte) (value >> 16));
        this.out.write ((byte) (value >> 8));
        this.out.write ((byte) value);
    }


    /**
     * Write an integer.
     *
     * @param value The value to write
     * @throws IOException Error during write
     */
    public void writeFour (final int value) throws IOException
    {
        this.out.write ((byte) value);
        this.out.write ((byte) (value >> 8));
        this.out.write ((byte) (value >> 16));
        this.out.write ((byte) (value >> 24));
    }
}
