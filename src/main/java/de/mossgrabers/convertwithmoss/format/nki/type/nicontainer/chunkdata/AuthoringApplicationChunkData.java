// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Metadata information about the authoring application.
 *
 * @author Jürgen Moßgraber
 */
public class AuthoringApplicationChunkData extends AbstractChunkData
{
    private boolean              isCompressed;
    private String               applicationVersion;
    private AuthoringApplication application;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        this.isCompressed = in.read () > 0;

        final long applicationID = StreamUtils.readUnsigned32 (in, false);
        this.application = AuthoringApplication.get (applicationID);

        // Always 1
        StreamUtils.readUnsigned32 (in, false);

        this.applicationVersion = StreamUtils.readWithLengthUTF16 (in);
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
}
