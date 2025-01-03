// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * A part in a performance.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcPerformancePart
{
    String                              name;
    int                                 type;
    private List<YamahaYsfcPartElement> elements = new ArrayList<> ();
    private byte []                     theRest;


    /**
     * Constructor which reads the performance from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the YSFC file
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcPerformancePart (final InputStream in, final YamahaYsfcVersion version) throws IOException
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
        this.name = StreamUtils.readASCII (in, 21).trim ();
        final int pos = this.name.indexOf (0);
        if (pos >= 0)
            this.name = this.name.substring (0, pos);

        this.type = in.read ();
        // TODO ...
        this.theRest = in.readAllBytes ();
    }


    /**
     * Write a performance to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the entry item
     */
    public void write (final OutputStream out) throws IOException
    {
        final ByteArrayOutputStream arrayOut = new ByteArrayOutputStream ();
        StreamUtils.writeASCII (arrayOut, StringUtils.rightPadSpaces (StringUtils.optimizeName (this.name, 20), 20), 21);

        arrayOut.write (this.type);
        // TODO
        arrayOut.write (this.theRest);

        StreamUtils.writeDataBlock (out, arrayOut.toByteArray (), true);
    }


    public void addElement (final YamahaYsfcPartElement element)
    {
        this.elements.add (element);
    }


    public List<YamahaYsfcPartElement> getElements ()
    {
        return this.elements;
    }
}
