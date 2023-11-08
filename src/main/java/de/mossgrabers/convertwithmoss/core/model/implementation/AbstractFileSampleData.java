// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Sample data contained in a file.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractFileSampleData extends AbstractSampleData
{
    protected String           filename;
    protected File             sampleFile;
    protected final File       zipFile;
    protected final File       zipEntry;
    protected Optional<String> combinedFilename     = Optional.empty ();
    protected Optional<String> filenameWithoutLayer = Optional.empty ();


    /**
     * Constructor for a sample stored in the file system.
     *
     * @param sampleFile The file where the sample is stored
     */
    protected AbstractFileSampleData (final File sampleFile)
    {
        this (sampleFile.getName (), sampleFile, null, null);
    }


    /**
     * Constructor for a sample stored in the file system.
     *
     * @param filename The name of the file where the sample is stored (must not contain any paths!)
     * @param sampleFile The file where the sample is stored
     */
    protected AbstractFileSampleData (final String filename, final File sampleFile)
    {
        this (filename, sampleFile, null, null);
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     */
    protected AbstractFileSampleData (final File zipFile, final File zipEntry)
    {
        this (zipEntry.getName (), null, zipFile, zipEntry);
    }


    /**
     * Constructor.
     */
    protected AbstractFileSampleData ()
    {
        this (null, null, null, null);
    }


    /**
     * Constructor.
     *
     * @param filename The name of the file where the sample is stored (must not contain any paths!)
     * @param sampleFile The file where the sample is stored
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     */
    protected AbstractFileSampleData (final String filename, final File sampleFile, final File zipFile, final File zipEntry)
    {
        this.filename = filename;
        this.sampleFile = sampleFile;
        this.zipFile = zipFile;
        this.zipEntry = zipEntry;
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        if (this.sampleFile != null)
        {
            Files.copy (this.sampleFile.toPath (), outputStream);
            return;
        }

        if (this.zipFile == null)
            return;

        try (final ZipFile zf = new ZipFile (this.zipFile))
        {
            final String path = this.zipEntry.getPath ().replace ('\\', '/');
            final ZipEntry entry = zf.getEntry (path);
            if (entry == null)
                throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_FILE_NOT_FOUND_IN_ZIP", path));

            try (final InputStream in = zf.getInputStream (entry))
            {
                in.transferTo (outputStream);
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        if (this.sampleFile != null)
        {
            this.audioMetadata = AudioFileUtils.getMetadata (this.sampleFile);
            return;
        }

        try (final ZipFile zf = new ZipFile (this.zipFile))
        {
            final String path = this.zipEntry.getPath ().replace ('\\', '/');
            final ZipEntry entry = zf.getEntry (path);
            if (entry == null)
                throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_FILE_NOT_FOUND_IN_ZIP", path));

            try (final InputStream in = zf.getInputStream (entry))
            {
                this.audioMetadata = AudioFileUtils.getMetadata (in);
            }
        }
    }


    /**
     * Get the filename.
     *
     * @return The name of the file
     */
    public String getFilename ()
    {
        return this.filename;
    }
}
