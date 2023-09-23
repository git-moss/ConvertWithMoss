package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;


/**
 * Chunk containing information about the authorization.
 *
 * @author Jürgen Moßgraber
 */
public class AuthorizationChunkData extends AbstractChunkData
{
    private String serialNumberPID;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        final int unknownA = (int) StreamUtils.readUnsigned32 (in, false);
        if (unknownA == 1)
        {
            in.skipNBytes (8);
            this.serialNumberPID = StreamUtils.readWithLengthUTF16 (in);

            // We do not support encrypted/protected NKI files
            if (this.serialNumberPID != null && !this.serialNumberPID.isBlank ())
                throw new IOException (Functions.getMessage ("IDS_NKI5_ENCRYPTED_NKI_NOT_SUPPORTED"));
        }
    }


    /**
     * Get the serial number PID.
     *
     * @return The PID if set
     */
    public Optional<String> getSerialNumberPID ()
    {
        return this.serialNumberPID == null || this.serialNumberPID.isBlank () ? Optional.empty () : Optional.of (this.serialNumberPID);
    }
}
