package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Chunk containing information about the authorization.
 *
 * @author Jürgen Moßgraber
 */
public class AuthorizationChunkData extends AbstractChunkData
{
    private final List<String> serialNumberPIDs = new ArrayList<> ();
    private int                pidContent;
    private long               checksum         = 0x8565620D;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        this.pidContent = (int) StreamUtils.readUnsigned32 (in, false);
        if (this.pidContent == 1)
        {
            // Unknown - number of license blocks ?!
            final long numLicenseBlock = StreamUtils.readUnsigned32 (in, false);
            if (numLicenseBlock != 1)
                throw new IOException ("license block != 1");

            final long numPIDs = StreamUtils.readUnsigned32 (in, false);
            for (long i = 0; i < numPIDs; i++)
            {
                final String pid = StreamUtils.readWithLengthUTF16 (in);
                if (pid != null && !pid.isBlank ())
                    this.serialNumberPIDs.add (pid);
            }

            // Unknown - always 0?
            in.skipNBytes (4);
            // Unknown - always 0?
            in.skipNBytes (4);

            this.checksum = StreamUtils.readUnsigned32 (in, false);
            return;
        }

        if (this.pidContent != 2)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_AUTHORIZATION_VALUE", Integer.toString (this.pidContent)));
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);

        StreamUtils.writeUnsigned32 (out, this.pidContent, false);

        if (this.pidContent == 1)
        {
            StreamUtils.writeUnsigned32 (out, 1, false);

            // No PIDs
            StreamUtils.writeUnsigned32 (out, 0, false);

            StreamUtils.writeUnsigned32 (out, 0, false);
            StreamUtils.writeUnsigned32 (out, 0, false);
            StreamUtils.writeUnsigned32 (out, this.checksum, false);
        }
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


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        final int padding = level * 4;
        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("* Serialnumber PIDs: ", padding));
        if (this.serialNumberPIDs.isEmpty ())
            sb.append ("None");
        else
        {
            for (final String pid: this.serialNumberPIDs)
                sb.append (pid).append (' ');
        }
        return sb.append ('\n').toString ();
    }
}
