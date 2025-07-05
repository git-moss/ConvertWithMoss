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

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.file.CompressionUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.Magic;
import de.mossgrabers.convertwithmoss.format.nki.type.AbstractKontaktType;


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
    public void writeNKM (OutputStream out, List<String> safeSampleFolderNames, final List<IInstrumentSource> instrumentSources, int sizeOfSamples) throws IOException
    {
        if (instrumentSources.isEmpty ())
            return;

        final String xmlCode = this.handler.createBank (safeSampleFolderNames, instrumentSources);

        final Date creationDateTime = instrumentSources.get (0).getMetadata ().getCreationDateTime ();
        final long timestamp = creationDateTime == null ? System.currentTimeMillis () : creationDateTime.getTime ();
        writeNKx (out, Magic.KONTAKT1_MULTI_BE, xmlCode, timestamp, sizeOfSamples);
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
        final String xmlCode = this.handler.createProgram (safeSampleFolderName, multisampleSource, 0);
        final long timestamp = multisampleSource.getMetadata ().getCreationDateTime ().getTime ();
        writeNKx (out, Magic.KONTAKT1_INSTRUMENT_BE, xmlCode, timestamp, sizeOfSamples);
    }


    /**
     * Writes a NKI or NKM file to the given output stream.
     * 
     * @param out Where to write to
     * @param type The type of document to write
     * @param xmlCode The XML description text
     * @param timestamp The creation date in milli-seconds
     * @param sizeOfSamples The size of the samples
     * @throws IOException Could not write
     */
    private static void writeNKx (final OutputStream out, final int type, final String xmlCode, final long timestamp, final int sizeOfSamples) throws IOException
    {
        // Kontakt 1 NKI File ID
        StreamUtils.writeUnsigned32 (out, type, false);

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
        StreamUtils.writeUnsigned32 (out, (int) (timestamp / 1000), false);

        // The sum of the size of all used samples (only the content data block of a WAV without any
        // headers)
        StreamUtils.writeUnsigned32 (out, sizeOfSamples, false);

        // Unknown. Always 4 empty bytes.
        StreamUtils.writeUnsigned32 (out, 0x00, false);

        CompressionUtils.writeZLIB (out, xmlCode, 6);
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
