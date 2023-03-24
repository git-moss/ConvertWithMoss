// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.NiSSMetadataFileHandler;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Can handle NKI files in Kontakt 1 format.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Kontakt1Type extends AbstractKontaktType
{
    private final NiSSMetadataFileHandler handler;


    /**
     * Constructor.
     *
     * @param metadataConfig Default metadata
     * @param notifier Where to report errors
     */
    public Kontakt1Type (final IMetadataConfig metadataConfig, final INotifier notifier)
    {
        super (metadataConfig, notifier);

        this.handler = new NiSSMetadataFileHandler (notifier);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readNKI (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess) throws IOException
    {
        this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE_1");

        // Read the offset to the ZLIB part, 8 bytes have already been read
        final int offset = StreamUtils.readDoubleWord (fileAccess, false) - 8;
        if (fileAccess.skipBytes (offset) != offset)
            throw new IOException (Functions.getMessage ("IDS_ERR_FILE_CORRUPTED"));

        try
        {
            final String xmlCode = readZLIB (fileAccess);
            return this.handler.parse (sourceFolder, sourceFile, xmlCode, this.metadataConfig, null);
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


    /** {@inheritDoc} */
    @Override
    public void writeNKI (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples) throws IOException
    {
        this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE_1");

        // Kontakt 1 NKI File ID
        StreamUtils.writeDoubleWord (out, KontaktTypes.ID_KONTAKT1.intValue (), true);

        // The number of bytes in the file where the ZLIB starts. Always 0x24.
        StreamUtils.writeDoubleWord (out, 0x24, false);

        // Unknown. Always 0x50.
        StreamUtils.writeWord (out, 0x50, false);

        // Unknown. Always 0x01 or 0x02.
        StreamUtils.writeWord (out, 0x01, false);

        // Unknown. Always 8 empty bytes.
        StreamUtils.writeDoubleWord (out, 0x00, false);
        StreamUtils.writeDoubleWord (out, 0x00, false);

        // Unknown. Always 0x01.
        StreamUtils.writeDoubleWord (out, 0x01, false);

        // Unix-Timestamp UTC+1
        StreamUtils.writeDoubleWord (out, (int) (System.currentTimeMillis () / 1000), false);

        // The sum of the size of all used samples (only the content data block of a WAV without any
        // headers)
        StreamUtils.writeDoubleWord (out, sizeOfSamples, false);

        // Unknown. Always 4 empty bytes.
        StreamUtils.writeDoubleWord (out, 0x00, false);

        final Optional<String> result = this.handler.create (multisampleSource);
        if (result.isPresent ())
            writeZLIB (out, result.get (), 6);
    }
}
