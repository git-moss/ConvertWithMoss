package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.IOException;
import java.io.InputStream;


/**
 * The root chunk of all containers.
 *
 * @author Jürgen Moßgraber
 */
public class PresetChunkData extends AbstractChunkData
{
    private boolean              isCompressed;
    private String               applicationVersion;
    private AuthoringApplication application;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        super.read (in);

        this.isCompressed = in.read () > 0;

        final int applicationID = StreamUtils.readUnsigned32 (in, false);
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
