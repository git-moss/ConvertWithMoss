package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


public class WaldorfQpatParameter
{
    String name;
    String hint;
    float  value;


    public void read (final InputStream in) throws IOException
    {
        this.value = StreamUtils.readFloatLE (in);
        this.name = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();
        this.hint = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();
    }
}
