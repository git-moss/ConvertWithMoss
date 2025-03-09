// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * The Sound Header chunk.
 *
 * @author Jürgen Moßgraber
 */
public class BNISoundHeaderChunkData extends AbstractChunkData
{
    private byte [] unknownBytes;
    private byte [] unknownBytes2;
    private byte [] uuid;
    private byte [] restBytes;
    private Date    timestamp;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        // Last 4 bytes = 6noK
        this.unknownBytes = in.readNBytes (24);

        this.timestamp = StreamUtils.readTimestamp (in, false);

        this.unknownBytes2 = in.readNBytes (134);
        this.uuid = in.readNBytes (16);
        this.restBytes = in.readAllBytes ();
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        out.write (this.unknownBytes);
        StreamUtils.writeTimestamp (out, this.timestamp, false);
        out.write (this.unknownBytes2);
        out.write (this.uuid);
        out.write (this.restBytes);
    }


    /** {@inheritDoc} */

    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode (this.restBytes);
        result = prime * result + Arrays.hashCode (this.unknownBytes);
        result = prime * result + Arrays.hashCode (this.unknownBytes2);
        result = prime * result + Arrays.hashCode (this.uuid);
        result = prime * result + Objects.hash (this.timestamp);
        return result;
    }


    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if ((obj == null) || (this.getClass () != obj.getClass ()))
            return false;
        final BNISoundHeaderChunkData other = (BNISoundHeaderChunkData) obj;
        return Arrays.equals (this.restBytes, other.restBytes) && Objects.equals (this.timestamp, other.timestamp) && Arrays.equals (this.unknownBytes, other.unknownBytes) && Arrays.equals (this.unknownBytes2, other.unknownBytes2) && Arrays.equals (this.uuid, other.uuid);
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        final int padding = level * 4;
        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("* Unknown: ", padding)).append (StringUtils.formatHexStr (this.unknownBytes)).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Timestamp: ", padding)).append (this.timestamp).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Unknown 2: ", padding)).append (StringUtils.formatHexStr (this.unknownBytes2)).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* UUID: ", padding)).append (StringUtils.formatHexStr (this.uuid)).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Rest: ", padding)).append (StringUtils.formatHexStr (this.restBytes)).append ('\n');
        return sb.toString ();
    }
}
