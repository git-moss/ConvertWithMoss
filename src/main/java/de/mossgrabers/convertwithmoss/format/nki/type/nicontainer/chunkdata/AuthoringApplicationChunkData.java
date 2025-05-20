// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Metadata information about the authoring application.
 *
 * @author Jürgen Moßgraber
 */
public class AuthoringApplicationChunkData extends AbstractChunkData
{
    private boolean              isCompressed = false;
    private AuthoringApplication application  = AuthoringApplication.KONTAKT;
    private String               applicationVersion;
    private int                  unknown      = 1;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        this.isCompressed = in.read () > 0;

        final long applicationID = StreamUtils.readUnsigned32 (in, false);
        this.application = AuthoringApplication.get (applicationID);

        // Always 1
        this.unknown = (int) StreamUtils.readUnsigned32 (in, false);

        this.applicationVersion = StreamUtils.readWithLengthUTF16 (in);

        if (in.available () > 0)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_DATA", "Authoring Application"));
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);

        out.write (this.isCompressed ? 1 : 0);
        StreamUtils.writeUnsigned32 (out, this.application.getID (), false);
        StreamUtils.writeUnsigned32 (out, this.unknown, false);
        StreamUtils.writeWithLengthUTF16 (out, this.applicationVersion);
    }


    /**
     * Is the preset compressed?
     *
     * @return True if compressed
     */
    public boolean isCompressed ()
    {
        return this.isCompressed;
    }


    /**
     * Get the application version that created the preset.
     *
     * @return The application version as a string
     */
    public String getApplicationVersion ()
    {
        return this.applicationVersion;
    }


    /**
     * Get the application.
     *
     * @return The application
     */
    public AuthoringApplication getApplication ()
    {
        return this.application;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return Objects.hash (this.application, this.applicationVersion, Boolean.valueOf (this.isCompressed), Integer.valueOf (this.unknown));
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final AuthoringApplicationChunkData other = (AuthoringApplicationChunkData) obj;
        return this.application == other.application && Objects.equals (this.applicationVersion, other.applicationVersion) && this.isCompressed == other.isCompressed && this.unknown == other.unknown;
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        final int padding = level * 4;
        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("* Is Compressed: ", padding)).append (this.isCompressed).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Application: ", padding)).append (this.application.getName ()).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Application Version: ", padding)).append (this.applicationVersion).append ('\n');
        return sb.toString ();
    }
}
