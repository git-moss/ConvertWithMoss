// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.ui.Functions;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.InflaterInputStream;


/**
 * Detector for Native Instruments Kontakt Instrument (NKI) files. Currently, only the format of the
 * versions before Kontakt 4.2.2 are supported.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 * @author Philip Stolz
 */
public class NkiDetectorTask extends AbstractDetectorTask
{
    private static final int     K2_OFFSET              = 0xAA;
    private static final int     NISS_OFFSET            = 0x24;

    private static final long [] KNOWN_OFFSETS          =
    {
        NISS_OFFSET,
        K2_OFFSET
    };

    private static final byte    FIRST_SIGNATURE_BYTE   = 0x78;
    private static final byte [] SECOND_SIGNATURE_BYTES =
    {
        0x01,
        0x5E,
        (byte) 0x9C,
        (byte) 0xDA,
        0x20,
        0x7D,
        (byte) 0xBB,
        (byte) 0xF9
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    public NkiDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ".nki", ".nkm");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final long offset = this.determineCompressedDataOffset (sourceFile);
        if (offset <= 0)
            return Collections.emptyList ();

        try
        {
            final String content = loadCompressedTextFile (sourceFile, offset);

            if (offset == K2_OFFSET)
                return new K2MetadataFileParser (this.notifier, this.metadata, this.sourceFolder, sourceFile).parseMetadataFile (sourceFile, content);
            // NISS_OFFSET
            return new NiSSMetaDataFileParser (this.notifier, this.metadata, this.sourceFolder, sourceFile).parseMetadataFile (sourceFile, content);
        }
        catch (final UnsupportedEncodingException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_ILLEGAL_CHARACTER", ex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Determines the offset of the compressed XML in the source file.
     *
     * @param sourceFile The source file
     * @return The offset, if ZLIB signature was found, -1 else
     */
    private long determineCompressedDataOffset (final File sourceFile)
    {
        final byte [] buffer = new byte [2];

        try (final InputStream in = new BufferedInputStream (new FileInputStream (sourceFile)))
        {
            for (final long offset: KNOWN_OFFSETS)
            {
                in.mark ((int) sourceFile.length ());

                final long skippedBytes = in.skip (offset);
                if (skippedBytes == offset)
                {
                    final int numBytesRead = in.read (buffer);
                    if (numBytesRead == 2 && isZlibSignature (buffer))
                        return offset;
                }

                in.reset ();
            }
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NKI_UNSUPPORTED_FILE_FORMAT", ex);
        }

        return -1;
    }


    /**
     * Test whether a byte array starts with a ZLIB signature.
     *
     * @param buffer The byte array of size 2, never null
     * @return true If byte array starts with a ZLIB signature, otherwise false
     */
    private static boolean isZlibSignature (final byte [] buffer)
    {
        if (buffer[0] != FIRST_SIGNATURE_BYTE)
            return false;

        for (final byte expected: SECOND_SIGNATURE_BYTES)
        {
            if (buffer[1] == expected)
                return true;
        }
        return false;
    }


    /**
     * Loads a ZIP-compressed file in UTF-8 encoding.
     *
     * @param file The file to load
     * @param offset The offset where the ZIP-compressed part begins
     * @return The uncompressed text
     * @throws IOException Could not load the file
     */
    private static String loadCompressedTextFile (final File file, final long offset) throws IOException
    {
        try (final InputStream fileInputStream = new FileInputStream (file))
        {
            final long skippedBytes = fileInputStream.skip (offset);
            if (skippedBytes != offset)
                throw new IOException (Functions.getMessage ("IDS_NKI_UNCOMPRESS_ERROR"));

            try (final InputStream inputStream = new InflaterInputStream (fileInputStream); final ByteArrayOutputStream outputStream = new ByteArrayOutputStream ())
            {
                final byte [] buffer = new byte [1024];
                int length;
                while ((length = inputStream.read (buffer)) > 0)
                    outputStream.write (buffer, 0, length);
                return outputStream.toString (StandardCharsets.UTF_8);
            }
        }
    }
}
