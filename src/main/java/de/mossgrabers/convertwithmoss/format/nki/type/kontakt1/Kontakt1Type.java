// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt1;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.file.CompressionUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.Magic;
import de.mossgrabers.convertwithmoss.format.nki.type.AbstractKontaktType;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;


/**
 * Can handle NKI files in Kontakt 1 format.
 *
 * @author Jürgen Moßgraber
 */
public class Kontakt1Type extends AbstractKontaktType
{
    private final NiSSMetadataFileHandler handler;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     * @param isBigEndian Larger bytes are first, other wise smaller bytes are first (little-endian)
     */
    public Kontakt1Type (final INotifier notifier, final boolean isBigEndian)
    {
        super (notifier);

        this.isBigEndian = isBigEndian;
        this.handler = new NiSSMetadataFileHandler (notifier);
    }


    /** {@inheritDoc} */
    @Override
    public IPerformanceSource readNKM (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        final long timeSeconds = this.readHeader (fileAccess);
        try
        {
            final String xmlCode = CompressionUtils.readZLIB (fileAccess);
            final IPerformanceSource performanceSource = this.handler.parseMulti (sourceFolder, sourceFile, xmlCode, metadataConfig, Collections.emptyMap ());
            performanceSource.getMetadata ().setCreationDateTime (new Date (timeSeconds * 1000));
            return performanceSource;
        }
        catch (final UnsupportedEncodingException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_ILLEGAL_CHARACTER", ex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }

        return null;
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readNKI (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        final long timeSeconds = this.readHeader (fileAccess);
        try
        {
            final String xmlCode = CompressionUtils.readZLIB (fileAccess);
            final List<IMultisampleSource> multisampleSources = this.handler.parseInstruments (sourceFolder, sourceFile, xmlCode, metadataConfig, Collections.emptyMap ());
            for (final IMultisampleSource multisampleSource: multisampleSources)
                multisampleSource.getMetadata ().setCreationDateTime (new Date (timeSeconds * 1000));
            return multisampleSources;
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
        // Kontakt 1 NKI File ID
        StreamUtils.writeUnsigned32 (out, Magic.KONTAKT1_INSTRUMENT_BE, false);

        // The number of bytes in the file where the ZLIB starts. Always 0x24.
        StreamUtils.writeUnsigned32 (out, 0x24, false);

        // Header version. Always 0x50.
        StreamUtils.writeUnsigned16 (out, 0x50, false);

        // Unknown. Always 0x01 or 0x02.
        StreamUtils.writeUnsigned16 (out, 0x01, false);

        // Unknown. Always 8 empty bytes.
        StreamUtils.writeUnsigned32 (out, 0x00, false);
        StreamUtils.writeUnsigned32 (out, 0x00, false);

        // Unknown. Always 0x01.
        StreamUtils.writeUnsigned32 (out, 0x01, false);

        // Unix-Timestamp UTC+1
        StreamUtils.writeUnsigned32 (out, (int) (multisampleSource.getMetadata ().getCreationDateTime ().getTime () / 1000), false);

        // The sum of the size of all used samples (only the content data block of a WAV without any
        // headers)
        StreamUtils.writeUnsigned32 (out, sizeOfSamples, false);

        // Unknown. Always 4 empty bytes.
        StreamUtils.writeUnsigned32 (out, 0x00, false);

        final Optional<String> result = this.handler.create (safeSampleFolderName, multisampleSource);
        if (result.isPresent ())
            CompressionUtils.writeZLIB (out, result.get (), 6);
    }


    private long readHeader (final RandomAccessFile fileAccess) throws IOException
    {
        this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE_1");

        // The number of bytes in the file where the ZLIB starts. Always 0x24.
        StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        // Header version. Always 0x50.
        StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);

        // Unknown. Always 0x01 or 0x02.
        StreamUtils.readUnsigned16 (fileAccess, this.isBigEndian);

        // Unknown. Always 8 empty bytes.
        StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        // Unknown. Always 0x01.
        StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        // Unix-Timestamp UTC+1
        final long timeSeconds = StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        // The sum of the size of all used samples (only the content data block of a WAV without any
        // headers)
        StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

        // Unknown. Always 4 empty bytes.
        StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
        return timeSeconds;
    }
}
