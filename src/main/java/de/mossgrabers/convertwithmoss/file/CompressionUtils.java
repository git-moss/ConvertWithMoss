package de.mossgrabers.convertwithmoss.file;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;


/**
 * Helper class for dealing with compression.
 *
 * @author Jürgen Moßgraber
 */
public class CompressionUtils
{
    private static final int BUFFER_SIZE_INPUT  = 1024;
    private static final int BUFFER_SIZE_OUTPUT = 2048;


    /**
     * Helper class.
     */
    private CompressionUtils ()
    {
        // Intentionally empty
    }


    /**
     * Uncompresses a ZLIB-compressed file in UTF-8 encoding.
     *
     * @param fileAccess Where to read the ZLIB data from
     * @return The uncompressed text
     * @throws IOException Could not load the file
     */
    public static String readZLIB (final RandomAccessFile fileAccess) throws IOException
    {
        try (final Inflater inflater = new Inflater (); final ByteArrayOutputStream outputStream = new ByteArrayOutputStream ())
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


    /**
     * Compresses a file in UTF-8 encoding with ZLIB.
     *
     * @param out Where to write the ZLIB data to
     * @param text The text to write
     * @param level The compression level (0-9)
     * @throws IOException Could not load the file
     */
    public static void writeZLIB (final OutputStream out, final String text, final int level) throws IOException
    {
        final byte [] input = text.getBytes (StandardCharsets.UTF_8);
        try (final Deflater deflater = new Deflater (level))
        {
            deflater.setInput (input);
            deflater.finish ();
            final byte [] compressedData = new byte [input.length];
            final int compressedDataLength = deflater.deflate (compressedData);
            out.write (compressedData, 0, compressedDataLength);
            deflater.end ();
        }
    }
}
