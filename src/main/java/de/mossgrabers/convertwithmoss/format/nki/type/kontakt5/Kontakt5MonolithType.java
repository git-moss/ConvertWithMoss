// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.ncw.NcwFileSampleData;
import de.mossgrabers.convertwithmoss.format.nki.Magic;
import de.mossgrabers.convertwithmoss.format.nki.type.AbstractKontaktType;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle NKI files in Kontakt 5+ monolith format. WAV and NCW files are supported.
 *
 * @author Jürgen Moßgraber
 */
public class Kontakt5MonolithType extends AbstractKontaktType
{
    private final String       noFileContainerError;
    private final Kontakt5Type kontakt5Type;
    private File               sourceFolder;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public Kontakt5MonolithType (final INotifier notifier)
    {
        super (notifier);

        this.noFileContainerError = Functions.getMessage ("IDS_NKI5_NOT_A_FILE_CONTAINER");
        this.kontakt5Type = new Kontakt5Type (notifier);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readNKI (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        this.sourceFolder = sourceFolder;

        try (final InputStream inputStream = Channels.newInputStream (fileAccess.getChannel ()))
        {
            final Map<Long, MonolithFile> monolithFiles = this.readMonolithFiles (inputStream);
            final MonolithFile mainFile = findMainFile (monolithFiles, sourceFile.getName ().endsWith (".nki") ? ".nki" : ".nkm");
            final InputStream dataInputStream = new ByteArrayInputStream (mainFile.data);
            return this.kontakt5Type.readNKI (this.sourceFolder, sourceFile, dataInputStream, metadataConfig, createSamples (monolithFiles));
        }
    }


    /** {@inheritDoc} */
    @Override
    public void writeNKM (final OutputStream out, final List<String> safeSampleFolderName, final List<IInstrumentSource> instrumentSources, final int sizeOfSamples) throws IOException
    {
        // Not supported
    }


    /** {@inheritDoc} */
    @Override
    public IPerformanceSource readNKM (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        this.sourceFolder = sourceFolder;

        try (final InputStream inputStream = Channels.newInputStream (fileAccess.getChannel ()))
        {
            final Map<Long, MonolithFile> monolithFiles = this.readMonolithFiles (inputStream);
            final InputStream dataInputStream = new ByteArrayInputStream (findMainFile (monolithFiles, ".nkm").data);
            return this.kontakt5Type.readNKM (sourceFolder, sourceFile, dataInputStream, metadataConfig, createSamples (monolithFiles));
        }
    }


    private static MonolithFile findMainFile (final Map<Long, MonolithFile> monolithFiles, final String fileEnding) throws IOException
    {
        for (final MonolithFile file: monolithFiles.values ())
        {
            if (file.name.toLowerCase ().endsWith (fileEnding))
                return file;
        }
        throw new IOException (Functions.getMessage ("IDS_NKI5_NO_NKI_IN_CONTAINER", fileEnding));
    }


    private Map<Long, MonolithFile> readMonolithFiles (final InputStream inputStream) throws IOException
    {
        final long fileCount = this.readHeader (inputStream);
        final Map<Long, MonolithFile> monolithFiles = this.readTableOfContents (inputStream, fileCount);
        readFiles (inputStream, monolithFiles);
        return monolithFiles;
    }


    /** {@inheritDoc} */
    @Override
    public void writeNKI (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples) throws IOException
    {
        // Not yet supported
    }


    /**
     * Read the header of the container file.
     *
     * @param inputStream The stream to read from
     * @return The number of files in the container
     * @throws IOException Could not read the header
     */
    private long readHeader (final InputStream inputStream) throws IOException
    {
        final String magic = StreamUtils.readASCII (inputStream, 16);
        if (!Magic.FILE_CONTAINER_HEADER.equals (magic))
            throw new IOException (this.noFileContainerError);

        // Padding
        inputStream.skipNBytes (248);

        final byte [] markerEnd = inputStream.readNBytes (8);
        if (Arrays.compare (Magic.FILE_CONTAINER_HEADER_END, markerEnd) != 0)
            throw new IOException (this.noFileContainerError);

        final long fileCount = StreamUtils.readUnsigned64 (inputStream, false);

        // Total size
        StreamUtils.readUnsigned64 (inputStream, false);
        return fileCount;
    }


    /**
     * Read the table of contents of the container.
     *
     * @param inputStream The stream to read from
     * @param fileCount The number of files in the table
     * @return The read file metadata
     * @throws IOException Could not read the table
     */
    private Map<Long, MonolithFile> readTableOfContents (final InputStream inputStream, final long fileCount) throws IOException
    {
        final String magicTOC = StreamUtils.readASCII (inputStream, 16);
        if (!Magic.FILE_CONTAINER_TABLE_OF_CONTENTS.equals (magicTOC))
            throw new IOException (this.noFileContainerError);

        // Unknown
        inputStream.skipNBytes (600);

        final Map<Long, MonolithFile> fileDescriptors = new TreeMap<> ();
        for (long i = 0; i < fileCount; i++)
        {
            // 1 based
            final long fileIndex = StreamUtils.readUnsigned64 (inputStream, false);

            // Unknown
            inputStream.skipNBytes (16);

            final byte [] wideStringBytes = inputStream.readNBytes (600);
            final String filename = new String (wideStringBytes, StandardCharsets.UTF_16LE).trim ();

            // Padding?
            StreamUtils.readUnsigned64 (inputStream, false);

            // End of file offset
            final long fileOffset = StreamUtils.readUnsigned64 (inputStream, false);

            final MonolithFile descriptor = new MonolithFile ();
            descriptor.name = filename;
            descriptor.endOffset = fileOffset;
            fileDescriptors.put (Long.valueOf (fileIndex), descriptor);
        }

        final byte [] filesMarkerEnd = inputStream.readNBytes (8);
        if (Arrays.compare (Magic.FILE_CONTAINER_FILES_END, filesMarkerEnd) != 0)
            throw new IOException (this.noFileContainerError);

        // Unknown
        inputStream.skipNBytes (16);

        final String endMagicTOC = StreamUtils.readASCII (inputStream, 16);
        if (!Magic.FILE_CONTAINER_TABLE_OF_CONTENTS.equals (endMagicTOC))
            throw new IOException (this.noFileContainerError);

        // Padding
        inputStream.skipNBytes (592);
        return fileDescriptors;
    }


    /**
     * Read all files into memory.
     *
     * @param inputStream The stream to read from
     * @param monolithFiles The metadata description
     * @throws IOException Could not read the files
     */
    private static void readFiles (final InputStream inputStream, final Map<Long, MonolithFile> monolithFiles) throws IOException
    {
        long previousEnd = 0;
        for (final MonolithFile descriptor: monolithFiles.values ())
        {
            // Note: this implementation only supports files up to 4GB!
            descriptor.data = inputStream.readNBytes ((int) (descriptor.endOffset - previousEnd));
            previousEnd = descriptor.endOffset;
        }
    }


    /**
     * Fill the in-memory files into sample metadata objects.
     *
     * @param monolithFiles The read files
     * @return The converted files
     * @throws IOException Could not convert the files
     */
    private static Map<Long, ISampleZone> createSamples (final Map<Long, MonolithFile> monolithFiles) throws IOException
    {
        final Map<Long, ISampleZone> samples = new HashMap<> ();

        for (final Map.Entry<Long, MonolithFile> entry: monolithFiles.entrySet ())
        {
            final MonolithFile value = entry.getValue ();
            final ByteArrayInputStream in = new ByteArrayInputStream (value.data);

            final String filename = value.name.toLowerCase ();
            final ISampleData sampleData;
            if (filename.endsWith (".wav"))
                sampleData = new WavFileSampleData (in);
            else if (filename.endsWith (".ncw"))
                sampleData = new NcwFileSampleData (in);
            else
                continue;
            samples.put (entry.getKey (), new DefaultSampleZone (value.name, sampleData));
        }

        return samples;
    }


    /** Helper class to manage the file metadata and read file content. */
    private static class MonolithFile
    {
        String  name;
        long    endOffset;
        byte [] data;
    }
}
