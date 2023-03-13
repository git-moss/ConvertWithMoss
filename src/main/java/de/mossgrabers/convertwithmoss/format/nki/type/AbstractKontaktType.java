// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;


/**
 * Abstract base class for handling NKI files in a specific format.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractKontaktType implements IKontaktType
{
    private static final int        BUFFER_SIZE_INPUT  = 1024;
    private static final int        BUFFER_SIZE_OUTPUT = 2048;

    protected final IMetadataConfig metadataConfig;
    protected final INotifier       notifier;


    /**
     * Constructor.
     *
     * @param metadataConfig Default metadata
     * @param notifier Where to report errors
     */
    protected AbstractKontaktType (final IMetadataConfig metadataConfig, final INotifier notifier)
    {
        this.metadataConfig = metadataConfig;
        this.notifier = notifier;
    }


    /**
     * Uncompresses a ZLIB-compressed file in UTF-8 encoding.
     *
     * @param fileAccess Where to read the ZLIB data from
     * @return The uncompressed text
     * @throws IOException Could not load the file
     */
    protected static String readZLIB (final RandomAccessFile fileAccess) throws IOException
    {
        final Inflater inflater = new Inflater ();

        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream ())
        {
            final byte [] inputBuffer = new byte [BUFFER_SIZE_INPUT];
            final byte [] outputBuffer = new byte [BUFFER_SIZE_OUTPUT];

            // Read and uncompress ZLIB data
            int length;
            do
            {
                length = fileAccess.read (inputBuffer);
                if (length > 0)
                {
                    inflater.setInput (inputBuffer, 0, length);

                    while (!inflater.needsInput () && !inflater.finished ())
                    {
                        final int uncompressed = inflater.inflate (outputBuffer);
                        if (uncompressed > 0)
                            outputStream.write (outputBuffer, 0, uncompressed);
                    }
                }
            } while (length > 0 && !inflater.finished ());

            // Move the file pointer back to the start of the unused bytes (which are not part of
            // the ZLIB section)
            final long remaining = inflater.getRemaining ();
            if (remaining > 0)
                fileAccess.seek (fileAccess.getFilePointer () - remaining);

            return outputStream.toString (StandardCharsets.UTF_8);
        }
        catch (final DataFormatException ex)
        {
            throw new IOException (ex);
        }
    }
}
