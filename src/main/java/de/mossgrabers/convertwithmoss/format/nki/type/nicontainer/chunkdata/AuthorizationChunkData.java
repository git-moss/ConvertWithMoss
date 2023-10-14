package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * Chunk containing information about the authorization.
 *
 * @author Jürgen Moßgraber
 */
public class AuthorizationChunkData extends AbstractChunkData
{
    private List<String> serialNumberPIDs = new ArrayList<> ();


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        final long hasPID = StreamUtils.readUnsigned32 (in, false);
        if (hasPID != 1)
        {
            // Only found '2' so far
            return;
        }

        // Unknown - number of license blocks ?!
        long numLicenseBlock = StreamUtils.readUnsigned32 (in, false);
        if (numLicenseBlock != 1)
            throw new IOException ("license block != 1");

        final long numPIDs = StreamUtils.readUnsigned32 (in, false);
        for (long i = 0; i < numPIDs; i++)
        {
            final String pid = StreamUtils.readWithLengthUTF16 (in);
            if (pid != null && !pid.isBlank ())
                this.serialNumberPIDs.add (pid);
        }

        // Unknown
        in.skipNBytes (4);

        // Level?
        in.skipNBytes (4);

        // Checksum
        in.skipNBytes (4);
    }


    /**
     * Get the serial number PIDs.
     *
     * @return The PIDs
     */
    public List<String> getSerialNumberPIDs ()
    {
        return this.serialNumberPIDs;
    }
}
