// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;


/**
 * Class for a list chunk with info sub-chunks.
 *
 * @author Jürgen Moßgraber
 */
public class ListInfoChunk extends WavChunk
{
    private final Map<String, String> infoList = new TreeMap<> ();
    private boolean                   isDirty  = false;


    /**
     * Constructor.
     *
     * @param chunk The RIFF chunk which contains the data
     */
    public ListInfoChunk (final RIFFChunk chunk)
    {
        super (RiffID.LIST_ID, chunk);
    }


    /**
     * Add an info sub-chunk.
     *
     * @param chunk The info sub-chunk
     */
    public void add (final RIFFChunk chunk)
    {
        final String tag = RiffID.toASCII (chunk.getId ());
        final String content = new String (chunk.getData (), StandardCharsets.US_ASCII);
        this.infoList.put (tag, content);
        this.isDirty = true;
    }


    /** {@inheritDoc} */
    @Override
    public byte [] getData ()
    {
        if (this.isDirty)
        {
            try (final ByteArrayOutputStream out = new ByteArrayOutputStream ())
            {
                out.write ("INFO".getBytes ());
                for (final Map.Entry<String, String> e: this.infoList.entrySet ())
                {
                    out.write (e.getKey ().getBytes ());
                    final String content = e.getValue ();
                    StreamUtils.writeUnsigned32 (out, content.length (), false);
                    out.write (content.getBytes ());
                    if (content.length () % 2 == 1)
                        out.write (0);
                }
                this.chunk.setData (out.toByteArray ());
            }
            catch (final IOException ex)
            {
                // Should never happen
                return new byte [0];
            }
            finally
            {
                this.isDirty = false;
            }
        }

        return this.chunk.getData ();
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ("Info List: ");
        for (final Map.Entry<String, String> e: this.infoList.entrySet ())
            sb.append ("\n- ").append (e.getKey ()).append (": ").append (e.getValue ());
        return sb.toString ();
    }
}
